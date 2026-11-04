#!/usr/bin/env bash
#
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

# Exercise both independent RS disturbances without Docker. External commands and the clock
# are stubs; the entry points, readiness checks and run_load are the production functions.
set -eE -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

variant() (
  local kind=$1 how=$2 recovery=$3 command=$4 extras=$5
  local code started peers
  failed=0 checks=0
  check() {
    checks=$((checks + 1))
    [ "$2" = "$3" ] || { echo "::error::$kind $1: got '$2', expected '$3'"; failed=$((failed + 1)); }
  }
  for topology in '' r0; do
    code=0
    (RS_ONLY=$topology; . "$HERE/disturb-$kind.sh") >/dev/null 2>&1 || code=$?
    check "refuses fewer than two RS-only servers" "$code" 2
  done
  DS_NODES="d0 d1" RS_ONLY="r0 r1" RS_NODES="r0 r1" DURATION=900 DISTURB_AT=300 DISTURB_TIMEOUT=900
  DISTURBANCE=$kind LOAD_START_MS=100000
  . "$HERE/disturb-$kind.sh"
  . "$HERE/load.sh"
  check "disturbed server" "$DISTURBED" r0
  check "expected step count" "$DISTURB_STEPS" 1
  unset SECONDS
  SECONDS=0 now=400 SERVE=30 calls=
  date() { echo "$now"; }
  sleep() { calls="$calls sleep $1"; now=$((now + $1)); SECONDS=$((SECONDS + $1)); }
  docker() {
    calls="$calls docker $*"
    echo "$*" >>"$OUT/docker.calls"
    [ "${DOCKER_FAIL:-}" != "$1" ] || return 1
    now=$((now + 5))
  }
  wait_until() {
    calls="$calls wait_until $1 [$2] ${*:3}"
    [ "${WAIT_FAIL:-no}" != yes ] || fail "readiness timed out"
    now=$((now + SERVE))
  }
  window_open() { calls="$calls window_open${*:+ $*}"; now=$((now + ${WINDOW_DELAY:-0})); }
  window_close() { calls="$calls window_close"; }
  fail() { echo "::error::$1"; false; }
  rm -f "$OUT/disturbances.txt"
  disturb >"$OUT/disturb.log"
  check "single operation and error window" "$calls" \
    " window_open$extras$command wait_until 900 [r0 serves again] rs_serves_since r0 400 sleep 10 window_close"
  check "single completed step" "$(cat "$OUT/disturbances.txt")" "400000 $((400000 + recovery * 1000)) r0 $how"
  calls=
  settle
  check "settle only the disturbed server" "$calls" " wait_until 900 [the join of r0 ends] bootstrap_ended r0"

  started_since() { [ "$1 $2" = 'r0 400' ] && [ "$started" = yes ]; }
  rs_peers() { [ "$1" = r0 ] && echo "$peers"; }
  serves() { if rs_serves_since r0 400; then echo yes; else echo no; fi; }
  started=yes peers=1
  check "startup and peer present" "$(serves)" yes
  peers=0
  check "missing peer" "$(serves)" no
  peers=2
  check "unexpected peer count" "$(serves)" no
  started=no peers=1
  check "no startup message" "$(serves)" no
  started=yes peers=2 RS_NODES="r0 r1 r2"
  check "three RS topology, both peers present" "$(serves)" yes
  peers=1
  check "three RS topology, one peer absent" "$(serves)" no
  RS_NODES="r0 r1"

  # Run failures outside if/|| so errexit remains active as it is in run.sh.
  budget_case() { # <label> <start time> <window delay> <status> <completed> <docker calls>
    local status lines
    rm -f "$OUT/disturbances.txt"
    : >"$OUT/docker.calls"
    set +e
    (set -e; now=$2 WINDOW_DELAY=$3; disturb >"$OUT/budget.log")
    status=$?
    set -e
    check "$1 status" "$status" "$4"
    lines=0
    [ ! -f "$OUT/disturbances.txt" ] || lines=$(awk 'END {print NR}' "$OUT/disturbances.txt")
    check "$1 completed steps" "$lines" "$5"
    check "$1 docker calls" "$(awk 'END {print NR}' "$OUT/docker.calls")" "$6"
    if [ "$4" -ne 0 ]; then
      check "$1 diagnostic" "$(grep -c 'too late' "$OUT/budget.log" || true)" 1
    fi
  }
  op_calls=1
  [ "$how" != kill ] || op_calls=3
  budget_case '30 seconds after recovery and grace' "$((1000 - recovery - 10 - 30))" 0 0 1 "$op_calls"
  budget_case '29 seconds after recovery and grace' "$((1000 - recovery - 10 - 29))" 0 1 1 "$op_calls"
  budget_case '30 seconds before operation' 970 0 1 1 "$op_calls"
  budget_case '29 seconds before operation' 971 0 1 0 0
  budget_case 'load already ended' 1001 0 1 0 0
  budget_case 'window capture consumes remaining budget' 960 11 1 0 0

  for failure in docker readiness; do
    rm -f "$OUT/disturbances.txt"
    : >"$OUT/docker.calls"
    set +e
    (
      set -e
      now=400
      if [ "$failure" = docker ]; then DOCKER_FAIL=$how; else WAIT_FAIL=yes; fi
      disturb >"$OUT/failure.log"
    )
    code=$?
    set -e
    check "$failure failure propagates" "$code" 1
    check "$failure failure not marked complete" "$([ -f "$OUT/disturbances.txt" ] && echo yes || echo no)" no
  done

  # No DS client is interrupted or segmented for either RS disturbance.
  start_clients() { calls="$calls clients $*"; }
  disturb() { calls="$calls disturb at $SECONDS"; }
  calls= SECONDS=0 CLIENTS=
  run_load
  check "whole-load DS clients" "$calls" ' clients d0 1 900 clients d1 1 900 sleep 300 disturb at 300'
  start_clients() { calls="$calls clients $*"; SECONDS=$((SECONDS + 160)); }
  calls= SECONDS=0
  run_load
  check "late client startup, no negative sleep" "$calls" ' clients d0 1 900 clients d1 1 900 disturb at 320'
  echo "$kind: $((checks - failed)) checks passed, $failed failed"
  [ "$failed" -eq 0 ]
)

# Separate processes keep sourced entry points and fixture overrides from leaking.
variant restart-rs restart 35 ' docker restart -t 120 r0' ''
variant kill-rs kill 45 ' docker kill r0 docker wait r0 docker start r0' ' 180 181 284'
