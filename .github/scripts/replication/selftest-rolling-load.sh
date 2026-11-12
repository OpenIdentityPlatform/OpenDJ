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

# A 2100-second load must fit the per-DS index budget with the default segment size.
(
  DURATION=2100; unset ROLLING_SEGMENT
  . "$HERE/rolling-load.sh"
  [ "$ROLLING_SEGMENT" -eq 50 ] && rolling_index_budget || { echo '2100-second load exceeds the index budget' >&2; exit 1; }
)

# Three shards permit 50-second segments for a one-hour load.
(
  DURATION=3600; unset ROLLING_SEGMENT
  . "$HERE/rolling-load.sh"
  [ "$ROLLING_SEGMENT" -eq 50 ] && rolling_index_budget || { echo 'one-hour load exceeds the index budget' >&2; exit 1; }
  ROLLING_SEGMENT=20; . "$HERE/rolling-load.sh"
  [ "$ROLLING_SEGMENT" -eq 20 ] || { echo 'explicit segment override changed' >&2; exit 1; }
  if rolling_index_budget; then echo 'unsafe one-hour index budget accepted' >&2; exit 1; fi
)

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
# A failed worker must wake a pause immediately instead of consuming the disturbance timeout.
(
  mkdir -p "$OUT/failed-pause/load"; OUT="$OUT/failed-pause"; DISTURB_TIMEOUT=1; unset ROLLING_END
  touch "$OUT/load/d0.failed"
  start=$SECONDS
  if rolling_pause d0 2>"$OUT/error"; then echo 'failed worker accepted as parked' >&2; exit 1; fi
  grep -F "worker d0 failed" "$OUT/error" >/dev/null || { echo 'pause did not report the worker failure' >&2; exit 1; }
)
# Even if the first client failed, collect and check the second before exiting.
(
  mkdir -p "$OUT/failed-pair/load"; OUT="$OUT/failed-pair"; unset ROLLING_END ROLLING_END_MS
  start_clients() { :; }
  docker() { case $1 in wait|rm) : ;; logs) echo csv ;; inspect) echo '0 false' ;; exec) echo 'LDAP result=53 fixture' ;; *) return 1 ;; esac; }
  check_client() {
    echo "$1" >>"$OUT/checked"
    if [ "$1" = load-add-d0 ]; then
      [ -e "$OUT/load/d0.failed" ] || { echo 'worker failure not signalled before collecting pair' >&2; return 1; }
      return 0
    fi
    return 1
  }
  if rolling_worker d0; then echo 'client failure was accepted' >&2; exit 1; fi
  grep -Fx load-add-d0 "$OUT/checked" >/dev/null || { echo 'paired client was not checked' >&2; exit 1; }
  [ -f "$OUT/load/load-add-d0.csv" ] || { echo 'paired client output was not collected' >&2; exit 1; }
  [ -e "$OUT/load/d0.failed" ] || { echo 'worker failure was not signalled' >&2; exit 1; }
  grep -F 'LDAP result=53 fixture' "$OUT/load/d0.recent-errors.log" >/dev/null || { echo 'directory server errors were not collected' >&2; exit 1; }
)
# A checkpoint writer must have two running load clients, not just a stale marker.
(
  mkdir -p "$OUT/active/load"; OUT="$OUT/active"
  docker() { [ "$1" = inspect ] || return 1; [ "$4" != "${STOPPED_CLIENT:-}" ] && echo true || echo false; }
  if rolling_clients_running d0; then echo 'missing load clients were accepted' >&2; exit 1; fi
  printf 'load-mod-d0 load-add-d0\n' >"$OUT/load/d0.active"
  rolling_clients_running d0 || { echo 'running load clients rejected' >&2; exit 1; }
  STOPPED_CLIENT=load-add-d0
  if rolling_clients_running d0; then echo 'stopped load client was accepted' >&2; exit 1; fi
)
# Scale load must produce a meaningful fraction of both requested rates.
(
  . "$HERE/load.sh"
  mkdir -p "$OUT/min-rate/load"; OUT="$OUT/min-rate"
  DISTURBANCE=scale-kept LOAD_MOD_RATE=100 LOAD_ADD_RATE=20 STAT_INTERVAL=10
  echo '0 false' >"$OUT/load/load-mod-d0.exit"; echo 30 >"$OUT/load/load-mod-d0.seconds"
  printf 'Time (seconds),Recent throughput (ops/second),Errors/second\n10,1,0\n20,1,0\n30,1,0\n' >"$OUT/load/load-mod-d0.csv"
  if check_client load-mod-d0 >/dev/null 2>&1; then echo 'almost idle scale writer accepted' >&2; exit 1; fi
  sed 's/,1,0$/,10,0/' "$OUT/load/load-mod-d0.csv" >"$OUT/rate.csv"
  cp "$OUT/rate.csv" "$OUT/load/load-mod-d0.csv"
  check_client load-mod-d0 >/dev/null || { echo 'minimum scale rate rejected' >&2; exit 1; }
)
# The last client must leave time for JVM startup and teardown before the deadline.
(
  mkdir -p "$OUT/final/load"; OUT="$OUT/final"; ROLLING_END=$((SECONDS + 80)); unset ROLLING_END_MS
  ROLLING_SEGMENT=120; STAT_INTERVAL=10
  start_clients() { echo "$3" >"$OUT/length"; touch "$OUT/load/stop"; }
  rolling_worker d0
  [ "$(cat "$OUT/length")" -le 40 ] || { echo 'last segment left less than 40 seconds for startup and teardown' >&2; exit 1; }
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
  start_clients d0 2 40
  start_clients d0 3 40
  start_clients d0 4 40
  start_clients d1 2 40
  grep -E '^load-add-d0 addrate .* --noPurge ' "$OUT/commands" >/dev/null || { echo 'scale addrate still purges at the end of each segment' >&2; exit 1; }
  grep -Fx 'sn: AddRate-d0-1' "$OUT/load/addrate-d0.template" >/dev/null || exit 1
  grep -Fx 'sn: AddRate-d0-2' "$OUT/load/addrate-d0-2.template" >/dev/null || exit 1
  grep -Fx 'sn: AddRate-d0-3' "$OUT/load/addrate-d0-3.template" >/dev/null || exit 1
  grep -Fx 'sn: AddRate-d0-1' "$OUT/load/addrate-d0-4.template" >/dev/null || exit 1
  grep -Fx 'sn: AddRate-d1-2' "$OUT/load/addrate-d1-2.template" >/dev/null || exit 1
  DS_NODES='d0 d1 d2 d3'; . "$HERE/assert-converged.sh"
  for n in $DS_NODES; do for shard in 1 2 3; do case " $INDEXED_FILTERS " in *" (sn=AddRate-$n-$shard) "*) : ;; *) echo "index shard $n-$shard is not checked" >&2; exit 1 ;; esac; done; done
  ROLLING_SEGMENT=40 DURATION=900
  rolling_index_budget || { echo 'safe per-DS segment budget rejected' >&2; exit 1; }
  ROLLING_SEGMENT=20 DURATION=3600
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
    ROLLING_SEGMENT=20; DURATION=3600; DISTURB_AT=1; STAT_INTERVAL=0
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
# Drive the real watchdog with events instead of racing a one-second wall clock.
# It may fire only once the worker has published both active clients and the
# disturbance has released it. A separate bound prevents a broken test hanging.
test_deadline_sleep() {
  local limit=$((SECONDS + 10))
  [ "$1" -gt 0 ] && [ "$1" -le 60 ] || return 1
  while [ ! -s "$OUT/load/d0.active" ] || [ ! -e "$OUT/release-watchdog" ]; do
    [ "$SECONDS" -lt "$limit" ] || return 1
    command sleep 0.01
  done
}
# A disturbance that outlasts the load must fail and both clients must be killed.
if (mkdir -p "$OUT/slow/load"; OUT="$OUT/slow"; DS_NODES=d0; DURATION=60; DISTURB_AT=0
    sleep() { test_deadline_sleep "$@"; }
    rolling_worker() {
      echo 'load-mod-d0 load-add-d0' >"$OUT/load/d0.active"
      while [ ! -e "$OUT/load/stop" ]; do command sleep 0.01; done
    }
    docker() {
      case $1 in inspect) echo true ;; kill) echo "$2" >>"$OUT/killed" ;; *) return 1 ;; esac
    }
    disturb() {
      local limit=$((SECONDS + 10))
      touch "$OUT/release-watchdog"
      while [ ! -f "$OUT/killed" ] || [ "$(wc -l <"$OUT/killed")" -lt 2 ]; do
        [ "$SECONDS" -lt "$limit" ] || return 1
        command sleep 0.01
      done
      SECONDS=$ROLLING_END
    }
    rolling_run_load) 2>"$OUT/slow-error"; then
  echo 'disturbance outlasted the load but passed' >&2; exit 1
fi
[ -e "$OUT/slow/load/stop" ] || { echo 'workers were not stopped after a slow disturbance' >&2; exit 1; }
[ "$(wc -l <"$OUT/slow/killed")" -eq 2 ] || { echo 'active clients were not stopped at deadline' >&2; exit 1; }
# An early scenario failure must keep the watchdog alive until a blocked worker exits.
if (mkdir -p "$OUT/early/load"; OUT="$OUT/early"; DS_NODES=d0; DURATION=60; DISTURB_AT=0
    sleep() { test_deadline_sleep "$@"; }
    rolling_worker() {
      local limit=$((SECONDS + 10))
      echo 'load-mod-d0 load-add-d0' >"$OUT/load/d0.active"
      while [ ! -e "$OUT/killed" ]; do
        [ "$SECONDS" -lt "$limit" ] || return 1
        command sleep 0.01
      done
    }
    docker() { case $1 in inspect) echo true ;; kill) touch "$OUT/killed" ;; *) return 1 ;; esac; }
    disturb() { touch "$OUT/release-watchdog"; return 1; }
    rolling_run_load) 2>"$OUT/early-error"; then
  echo 'early disturbance failure was accepted' >&2; exit 1
fi
[ -e "$OUT/early/killed" ] || { echo 'watchdog stopped before its worker' >&2; exit 1; }
# A worker failure also aborts a pending checkpoint, without waiting for its timeout.
(
  . "$HERE/lib.sh"
  mkdir -p "$OUT/failed-checkpoint/load"; OUT="$OUT/failed-checkpoint"; SCALE_DEADLINE_ACTIVE=1
  touch "$OUT/load/d0.failed"
  if wait_until 2 'checkpoint' false >"$OUT/result" 2>&1; then echo 'failed worker accepted during checkpoint' >&2; exit 1; fi
  grep -F 'load worker failed' "$OUT/result" >/dev/null || { echo 'checkpoint hid worker failure' >&2; exit 1; }
  if wait_until 2 'ready checkpoint' true >"$OUT/result" 2>&1; then echo 'ready checkpoint hid failed worker' >&2; exit 1; fi
)
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
echo 'rolling load pause, resume and deadline passed'
