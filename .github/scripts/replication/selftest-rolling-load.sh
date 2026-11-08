#!/usr/bin/env bash
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

set -e -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/load"
if [ ! -f "$HERE/rolling-load.sh" ]; then
  echo 'rolling load support is missing' >&2
  exit 1
fi
. "$HERE/rolling-load.sh"

# The sampler runs in a subshell: it must inherit the deadline before it is started.
(
  DURATION=60; unset ROLLING_END ROLLING_END_MS SCALE_DEADLINE_ACTIVE
  prepare_rolling_deadline
  ( [ "$SCALE_DEADLINE_ACTIVE" = 1 ] && [ "$ROLLING_END_MS" -gt 0 ] ) || { echo 'sampler cannot inherit deadline' >&2; exit 1; }
)

# Docker reports the process exit in nanoseconds: a client ending beyond the wall-clock
# deadline is an error even when its own --maxDuration was within it.
finished_before_deadline '1970-01-01T00:00:10.000000000Z' 10000 || exit 1
if finished_before_deadline '1970-01-01T00:00:10.000000001Z' 10000; then
  echo 'late client was accepted' >&2; exit 1
fi
if finished_before_deadline invalid 10000; then
  echo 'invalid client finish time was accepted' >&2; exit 1
fi

# A segment has two clients; a pause may stop the node only once both have ended. The
# other node must continue to write while this node is paused.
start_clients() { printf 'start %s %s\n' "$1" "$2" >>"$OUT/events"; }
docker() {
  case $1 in
    wait) sleep 0.1 ;;
    logs) echo 'csv' ;;
    inspect) echo '0 false' ;;
    rm) : ;;
    *) return 1 ;;
  esac
}
check_client() { printf 'checked %s\n' "$1" >>"$OUT/events"; }
fail() { echo "$*" >&2; return 1; }
wait_until() {
  local deadline=$((SECONDS + $1)); shift 2
  until "$@"; do [ "$SECONDS" -lt "$deadline" ] || return 1; sleep 0.1; done
}
ROLLING_SEGMENT=1 STAT_INTERVAL=0
rolling_worker d0 & a=$!
rolling_worker d1 & b=$!
sleep 0.2
rolling_pause d0
before=$(grep -c '^start d1 ' "$OUT/events")
sleep 0.4
after=$(grep -c '^start d1 ' "$OUT/events")
[ "$after" -gt "$before" ] || { echo 'other node stopped writing' >&2; exit 1; }
count=$(grep -c '^start d0 ' "$OUT/events")
sleep 0.3
[ "$(grep -c '^start d0 ' "$OUT/events")" -eq "$count" ] || { echo 'paused node started another segment' >&2; exit 1; }
rolling_resume d0
resumed() { [ "$(grep -c '^start d0 ' "$OUT/events")" -gt "$count" ]; }
wait_until 3 'node resumes' resumed || { echo 'node did not resume' >&2; exit 1; }
touch "$OUT/load/stop"
wait "$a"
wait "$b"
[ "$(grep -c '^checked ' "$OUT/events")" -eq "$((2 * $(grep -c '^start ' "$OUT/events")))" ] || { echo 'not all clients checked' >&2; exit 1; }
# The last client must leave time for JVM startup and teardown before the deadline.
(
  mkdir -p "$OUT/final/load"; OUT="$OUT/final"; ROLLING_END=$((SECONDS + 80)); unset ROLLING_END_MS
  ROLLING_SEGMENT=120; STAT_INTERVAL=10
  start_clients() { echo "$3" >"$OUT/length"; touch "$OUT/load/stop"; }
  rolling_worker d0
  [ "$(cat "$OUT/length")" -le 70 ] || { echo 'last segment used the full remaining wall time' >&2; exit 1; }
)
# Pausing a node cannot wait beyond the overall load deadline.
(
  ROLLING_END=$((SECONDS + 3)); DISTURB_TIMEOUT=900
  wait_until() { [ "$1" -le 3 ] && [ "$1" -gt 0 ]; }
  rolling_pause deadline-node || { echo 'pause exceeded the load deadline' >&2; exit 1; }
)
# Forty-second segments must preserve indexed searches for every per-DS AddRate shard.
(
  . "$HERE/load.sh"
  OUT="$OUT/shards"; mkdir -p "$OUT/load"
  BASE_DN=dc=example,dc=com SAMPLE_DATA=100 LOAD_MOD_RATE=10 LOAD_ADD_RATE=1 ROOT_PASSWORD=x
  DISTURBANCE=scale-rs-kept
  start_client() { printf '%s\n' "$*" >>"$OUT/commands"; }
  start_clients d0 1 40
  start_clients d1 2 40
  grep -E '^load-add-d0 addrate .* --noPurge ' "$OUT/commands" >/dev/null || { echo 'scale addrate still purges at the end of each segment' >&2; exit 1; }
  grep -Fx 'sn: AddRate-d0' "$OUT/load/addrate-d0.template" >/dev/null || { echo 'd0 AddRate index shard missing' >&2; exit 1; }
  grep -Fx 'sn: AddRate-d1' "$OUT/load/addrate-d1-2.template" >/dev/null || { echo 'd1 AddRate index shard missing' >&2; exit 1; }
  DS_NODES='d0 d1 d2 d3'; . "$HERE/assert-converged.sh"
  for n in $DS_NODES; do case " $INDEXED_FILTERS " in *" (sn=AddRate-$n) "*) : ;; *) echo "index shard $n is not checked" >&2; exit 1 ;; esac; done
  ROLLING_SEGMENT=40 DURATION=900
  rolling_index_budget || { echo 'safe per-DS segment budget rejected' >&2; exit 1; }
  ROLLING_SEGMENT=20
  if rolling_index_budget; then echo 'unsafe per-DS index budget accepted' >&2; exit 1; fi
  DISTURBANCE=restart-ds
  start_clients d2 1 40
  grep -Fx 'sn: AddRate' "$OUT/load/addrate-d2.template" >/dev/null || { echo 'ordinary AddRate surname changed' >&2; exit 1; }
  if grep -E '^load-add-d2 addrate .* --noPurge ' "$OUT/commands" >/dev/null; then echo 'ordinary AddRate purge changed' >&2; exit 1; fi
  . "$HERE/assert-converged.sh"
  [ "$INDEXED_FILTERS" = '(sn=AddRate) (sn=Diverged)' ] || { echo 'ordinary index filters changed' >&2; exit 1; }
)
# Too many segments leave enough AddRate entries to exceed the sn index limit.
if (mkdir -p "$OUT/index-limit/load"; OUT="$OUT/index-limit"; DS_NODES='d0 d1 d2 d3'
    ROLLING_SEGMENT=20; DURATION=900; DISTURB_AT=1; STAT_INTERVAL=0
    rolling_worker() { :; }; disturb() { :; }; sleep() { :; }
    rolling_run_load) >/dev/null 2>&1; then
  echo 'rolling load accepted enough retained entries to exceed the sn index limit' >&2; exit 1
fi
# A worker cannot launch new clients after the watchdog has set the deadline marker.
(
  . "$HERE/load.sh"
  OUT="$OUT/deadline"; mkdir -p "$OUT/load"; touch "$OUT/load/deadline"
  BASE_DN=dc=example,dc=com SAMPLE_DATA=100 LOAD_MOD_RATE=10 LOAD_ADD_RATE=1 ROOT_PASSWORD=x
  start_client() { echo launched >>"$OUT/launched"; }
  if start_clients d0 1 60; then echo 'clients launched after deadline' >&2; exit 1; fi
  [ ! -e "$OUT/launched" ] || { echo 'deadline allowed a client launch' >&2; exit 1; }
)
# A late Docker launch must not start work after the cutoff even if the host watchdog
# scanned before the container was created: the container enforces the deadline itself.
(
  . "$HERE/load.sh"
  OUT="$OUT/client-wrapper"; mkdir -p "$OUT/load"
  NETWORK=fixture IMAGE=fixture CLIENT_JAVA_ARGS=-Xmx128m ROLLING_END_MS=10000
  docker() { printf '%s\n' "$*" >"$OUT/command"; }
  start_client load-test modrate --hostname example
  grep -F -- '--entrypoint /bin/sh' "$OUT/command" >/dev/null || { echo 'missing in-container deadline guard' >&2; exit 1; }
  grep -F '/usr/bin/timeout' "$OUT/command" >/dev/null || { echo 'missing in-container cutoff' >&2; exit 1; }
)
# A slow disturbance must fail when it outlasts the load, and all workers must be stopped.
if (mkdir -p "$OUT/slow/load"; OUT="$OUT/slow"; DS_NODES=d0; DURATION=1; DISTURB_AT=0
    rolling_worker() {
      echo 'load-mod-d0 load-add-d0' >"$OUT/load/d0.active"
      while [ ! -e "$OUT/load/stop" ]; do sleep 0.1; done
    }
    docker() {
      case $1 in inspect) echo true ;; kill) echo "$2" >>"$OUT/killed" ;; *) return 1 ;; esac
    }
    disturb() { sleep 2; }
    rolling_run_load) 2>"$OUT/slow-error"; then
  echo 'disturbance outlasted the load but passed' >&2; exit 1
fi
[ -e "$OUT/slow/load/stop" ] || { echo 'workers were not stopped after a slow disturbance' >&2; exit 1; }
[ "$(wc -l <"$OUT/slow/killed")" -eq 2 ] || { echo 'active clients were not stopped at deadline' >&2; exit 1; }
# On an early scenario failure the watchdog must remain until a blocked worker exits.
if (mkdir -p "$OUT/early/load"; OUT="$OUT/early"; DS_NODES=d0; DURATION=1; DISTURB_AT=0
    rolling_worker() {
      echo 'load-mod-d0 load-add-d0' >"$OUT/load/d0.active"
      while [ ! -e "$OUT/killed" ]; do sleep 0.1; done
    }
    docker() { case $1 in inspect) echo true ;; kill) touch "$OUT/killed" ;; *) return 1 ;; esac; }
    disturb() { sleep 0.1; return 1; }
    rolling_run_load) 2>"$OUT/early-error"; then
  echo 'early disturbance failure was accepted' >&2; exit 1
fi
[ -e "$OUT/early/killed" ] || { echo 'watchdog stopped before its worker' >&2; exit 1; }
# A Docker error must not look like a completed bootstrap.
(
  . "$HERE/lib.sh"
  docker() { return "$DOCKER_RESULT"; }
  DOCKER_RESULT=124
  if bootstrap_ended example; then echo 'docker timeout accepted as a completed join' >&2; exit 1; fi
  DOCKER_RESULT=1
  if bootstrap_ended example; then echo 'running join accepted as complete' >&2; exit 1; fi
  DOCKER_RESULT=0
  bootstrap_ended example || { echo 'completed join rejected' >&2; exit 1; }
)
# Checkpoint queries have an in-container timeout; normal admin searches remain unchanged.
(
  . "$HERE/lib.sh"
  docker() { printf '%s\n' "$*" >"$OUT/admin-command"; }
  ADMIN_SEARCH_TIMEOUT=45 admin_search node cn=monitor sub '(objectClass=*)' 1.1
  grep -F 'exec node /usr/bin/timeout --verbose 45 /opt/opendj/bin/ldapsearch' "$OUT/admin-command" >/dev/null || { echo 'checkpoint query timeout missing' >&2; exit 1; }
  admin_search node cn=monitor sub '(objectClass=*)' 1.1
  grep -F 'exec node /opt/opendj/bin/ldapsearch' "$OUT/admin-command" >/dev/null || { echo 'ordinary search changed' >&2; exit 1; }
)
echo 'rolling load pause, resume and deadline passed'
