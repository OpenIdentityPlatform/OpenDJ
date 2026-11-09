#
# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.

# Bound Docker commands in the scale scenario too: a blocked daemon or a large online
# changelog copy must not make a predicate run past the load deadline.
docker() {
  if [ "${SCALE_DEADLINE_ACTIVE:-0}" = 1 ]; then
    python3 -I "$HERE/scale-deadline.py" "$ROLLING_END_MS" "$(command -v docker)" "$@"
  else
    command docker "$@"
  fi
}

# Each DS gets naturally ending segments. A rolling replacement waits for both clients
# before stopping it; other DS keep writing. Per-DS surnames keep each indexed AddRate
# shard below the index entry limit even with shorter segments.
# At most 24 segments leave fewer than 4000 retained entries per indexed surname.
# The 40-second minimum avoids unnecessary client starts on shorter loads.
if [ -z "${ROLLING_SEGMENT:-}" ]; then
  ROLLING_SEGMENT=$(( (${DURATION:-900} + 23) / 24 ))
  [ "$ROLLING_SEGMENT" -ge 40 ] || ROLLING_SEGMENT=40
fi
# Docker's finish time includes JVM startup and connection setup, unlike --maxDuration.
# A successful run cannot claim the requested duration if a client exited after it.
finished_before_deadline() { # <Docker UTC timestamp> <deadline in epoch milliseconds>
  python3 -I - "$1" "$2" <<'PYTIME'
from datetime import datetime, timezone
import re
import sys
match = re.fullmatch(r"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z", sys.argv[1])
try:
    if not match:
        raise ValueError("invalid Docker finish time")
    seconds = int(datetime.strptime(match[1], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc).timestamp())
    nanoseconds = seconds * 10**9 + int((match[2] or "").ljust(9, "0"))
    sys.exit(0 if nanoseconds <= int(sys.argv[2]) * 10**6 else 1)
except ValueError:
    sys.exit(1)
PYTIME
}

rolling_worker() { # <directory server>
  local n=$1 segment=1 name c length remaining finished
  # The parent reports this worker's failure; do not run run.sh's collection trap here.
  trap - ERR
  while [ ! -e "$OUT/load/stop" ]; do
    if [ -e "$OUT/load/$n.pause" ]; then
      touch "$OUT/load/$n.parked" || return 1
      while [ -e "$OUT/load/$n.pause" ] && [ ! -e "$OUT/load/stop" ]; do sleep 1; done
      rm -f "$OUT/load/$n.parked" || return 1
      continue
    fi
    length=$ROLLING_SEGMENT
    if [ -n "${ROLLING_END:-}" ]; then
      # --maxDuration starts only after the JVM and its connections are ready. Leave
      # a small launch/teardown allowance; FinishedAt still enforces the hard deadline.
      remaining=$((ROLLING_END - SECONDS - 10))
      [ "$remaining" -ge $((3 * STAT_INTERVAL)) ] || break
      [ "$length" -le "$remaining" ] || length=$remaining
    fi
    name=$n
    [ "$segment" -eq 1 ] || name=$n-$segment
    printf 'load-mod-%s load-add-%s\n' "$name" "$name" >"$OUT/load/$n.active" || return 1
    if ! start_clients "$n" "$segment" "$length"; then
      [ ! -e "$OUT/load/deadline" ] || rolling_kill_active "$n"
      return 1
    fi
    if [ -e "$OUT/load/deadline" ]; then
      rolling_kill_active "$n"
      return 1
    fi
    for c in "load-mod-$name" "load-add-$name"; do
      docker wait "$c" >/dev/null || return 1
      if [ -n "${ROLLING_END_MS:-}" ]; then
        finished=$(docker inspect -f '{{.State.FinishedAt}}' "$c") || return 1
        printf '%s\n' "$finished" >"$OUT/load/$c.finished" || return 1
      fi
      docker logs "$c" >"$OUT/load/$c.csv" 2>&1 || return 1
      docker inspect -f '{{.State.ExitCode}} {{.State.OOMKilled}}' "$c" >"$OUT/load/$c.exit" || return 1
      docker rm "$c" >/dev/null || return 1
      if [ -n "${ROLLING_END_MS:-}" ]; then
        finished_before_deadline "$finished" "$ROLLING_END_MS" || {
          echo "::error::$c finished after the load deadline ($finished)" >&2; return 1;
        }
      fi
      check_client "$c" || return 1
    done
    rm "$OUT/load/$n.active" || return 1
    segment=$((segment + 1))
  done
}
# A marker is written before Docker starts the clients; require both containers to be
# running before relying on this DS as the sole load writer during a checkpoint.
rolling_clients_running() { # <directory server>
  local node=$1 mod add extra client state
  [ ! -e "$OUT/load/$node.pause" ] && [ -f "$OUT/load/$node.active" ] || return 1
  read -r mod add extra <"$OUT/load/$node.active" || return 1
  case $mod in "load-mod-$node"|"load-mod-$node-"*) ;; *) return 1 ;; esac
  [ -z "$extra" ] && [ "$add" = "load-add-${mod#load-mod-}" ] || return 1
  for client in "$mod" "$add"; do
    state=$(docker inspect -f '{{.State.Running}}' "$client") || return 1
    [ "$state" = true ] || return 1
  done
}
rolling_parked() { [ -e "$OUT/load/$1.parked" ]; }
rolling_request_pause() { touch "$OUT/load/$1.pause"; }
rolling_pause() { # <directory server>
  local timeout=${DISTURB_TIMEOUT:-900} left
  if [ -n "${ROLLING_END:-}" ]; then
    left=$((ROLLING_END - SECONDS))
    [ "$left" -gt 0 ] || return 1
    [ "$left" -ge "$timeout" ] || timeout=$left
  fi
  rolling_request_pause "$1" || return 1
  wait_until "$timeout" "$1 clients finish" rolling_parked "$1"
}
rolling_resume() { rm -f "$OUT/load/$1.pause"; }

# At the hard limit, kill only the load clients of this run which are still active.
# A killed client makes the run fail; it is never counted as a successful segment.
rolling_kill_active() { # <directory server>
  local c clients
  [ -f "$OUT/load/$1.active" ] || return 0
  clients=$(cat "$OUT/load/$1.active") || return 1
  for c in $clients; do
    if [ "$(SCALE_DEADLINE_ACTIVE=0 docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null || :)" = true ]; then
      touch "$OUT/load/cutoff" || return 1
      SCALE_DEADLINE_ACTIVE=0 docker kill "$c" >/dev/null 2>&1 || true
    fi
  done
}

rolling_deadline() { # <seconds>
  local n sleep_pid
  sleep "$1" & sleep_pid=$!
  trap 'kill "$sleep_pid" 2>/dev/null || true; exit 1' TERM INT
  wait "$sleep_pid" || return 1
  trap - TERM INT
  touch "$OUT/load/deadline" "$OUT/load/stop" || return 1
  for n in $DS_NODES fill; do rolling_kill_active "$n" || return 1; done
}

# The sampler is a subshell, so establish the same cutoff before starting it.
prepare_rolling_deadline() {
  ROLLING_END=$((SECONDS + DURATION))
  ROLLING_END_MS=$(( ${LOAD_START_MS:-$(($(date +%s) * 1000))} + DURATION * 1000 ))
  SCALE_DEADLINE_ACTIVE=1
}

# Used in place of run_load for a rolling scale change. The deadline covers the complete
# disturbance: a late return is a failed run, even if the topology later converges.
rolling_index_budget() {
  local segments
  [ "$ROLLING_SEGMENT" -gt 0 ] || return 1
  segments=$(((DURATION + ROLLING_SEGMENT - 1) / ROLLING_SEGMENT))
  # The FIFO threshold is 100; allow for concurrent in-flight adds on eight
  # connections. Each DS uses its own indexed surname.
  [ $((segments * 160)) -lt 4000 ]
}

rolling_run_load() {
  local n pid watchdog pids= failed= left
  rolling_index_budget || { fail "rolling segments exceed the sn index entry limit"; return 1; }
  mkdir -p "$OUT/load" || return 1
  [ -n "${ROLLING_END_MS:-}" ] || prepare_rolling_deadline
  left=$((ROLLING_END - SECONDS))
  [ "$left" -gt 0 ] || { SCALE_DEADLINE_ACTIVE=0; fail 'load deadline already passed'; return 1; }
  ( trap - ERR; rolling_deadline "$left" ) &
  watchdog=$!
  for n in $DS_NODES; do
    ( rolling_worker "$n" ) &
    pids="$pids $!"
  done
  left=$((DISTURB_AT - (SECONDS - (ROLLING_END - DURATION))))
  [ "$left" -le 0 ] || sleep "$left"
  disturb || failed=1
  left=$((ROLLING_END - SECONDS))
  if [ "$left" -lt $((3 * STAT_INTERVAL)) ] || [ "$left" -le 0 ]; then
    echo "::error::scale change left too little of the $DURATION s load to write after it" >&2
    failed=1
  fi
  if [ -n "$failed" ]; then
    # Workers stop after their current segment. Keep the deadline guard alive while
    # waiting: a client or docker wait may be stuck until the deadline kills it.
    touch "$OUT/load/stop" || true
    for pid in $pids; do wait "$pid" || true; done
    kill "$watchdog" 2>/dev/null || true
    wait "$watchdog" 2>/dev/null || true
  else
    wait "$watchdog" 2>/dev/null || failed=1
    touch "$OUT/load/stop" || failed=1
    for pid in $pids; do wait "$pid" || failed=1; done
  fi
  [ ! -e "$OUT/load/cutoff" ] || failed=1
  SCALE_DEADLINE_ACTIVE=0
  [ -z "$failed" ] || fail "the rolling load or scale change failed"
}
