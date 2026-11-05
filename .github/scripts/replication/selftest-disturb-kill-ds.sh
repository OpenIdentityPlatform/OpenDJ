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

# Exercise a DS crash in both topologies without Docker. Keep the real disturbance,
# load segmentation and summary; only container commands, readiness and time are stubs.
set -eE -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
failed=0 checks=0
check() {
  checks=$((checks + 1))
  [ "$2" = "$3" ] || { echo "::error::$1: got '$2', expected '$3'"; failed=$((failed + 1)); }
}

for TOPOLOGY in rs534 combined; do
  DISTURBANCE=kill-ds DURATION=900 DISTURB_AT=300 DISTURB_TIMEOUT=900
  if [ "$TOPOLOGY" = rs534 ]; then
    DS_NODES="d0 d1 d2 d3" RS_NODES="r0 r1" extras="211" target=d1
  else
    DS_NODES="c0 c1 c2 c3" RS_NODES="$DS_NODES" extras="180 181 211 284" target=c1
  fi
  [ -f "$HERE/disturb-kill-ds.sh" ] || { echo "::error::kill-ds is not implemented"; exit 1; }
  . "$HERE/disturb-kill-ds.sh"
  check "$TOPOLOGY target" "$DISTURBED" "$target"
  check "$TOPOLOGY steps" "$DISTURB_STEPS" 1
  unset SECONDS
  SECONDS=0 now=400 calls=
  date() { echo "$now"; }
  sleep() { calls="$calls sleep $1"; now=$((now + $1)); }
  docker() {
    calls="$calls docker $*"
    echo "$*" >>"$OUT/docker.calls"
    [ "${DOCKER_FAIL:-}" != "$1" ] || return 1
    now=$((now + 5))
  }
  wait_until() {
    calls="$calls wait_until $1 [$2] ${*:3}"
    [ "${WAIT_FAIL:-no}" != yes ] || return 1
    now=$((now + 30))
  }
  window_open() { calls="$calls window_open $*"; }
  window_close() { calls="$calls window_close"; }
  rm -f "$OUT/disturbances.txt"
  disturb >/dev/null
  check "$TOPOLOGY crash lifecycle and error window" "$calls" \
    " window_open $extras docker kill $DISTURBED docker wait $DISTURBED docker start $DISTURBED wait_until 900 [$DISTURBED serves again] started_since $DISTURBED 400 sleep 10 window_close"
  check "$TOPOLOGY completed crash" "$(cat "$OUT/disturbances.txt")" "400000 445000 $DISTURBED kill"
  calls=
  settle
  check "$TOPOLOGY bootstrap" "$calls" " wait_until 900 [the join of $DISTURBED ends] bootstrap_ended $DISTURBED"

  # Failures run outside if/|| so errexit has the same effect as in run.sh.
  for failure in kill wait start readiness; do
    rm -f "$OUT/disturbances.txt"
    : >"$OUT/docker.calls"
    set +e
    (
      set -e
      if [ "$failure" = readiness ]; then WAIT_FAIL=yes; else DOCKER_FAIL=$failure; fi
      disturb >/dev/null
    )
    status=$?
    set -e
    check "$TOPOLOGY $failure failure" "$status" 1
    check "$TOPOLOGY $failure leaves no completed step" "$(test -e "$OUT/disturbances.txt" && echo yes || echo no)" no
    if [ "$failure" = kill ] || [ "$failure" = wait ]; then
      check "$TOPOLOGY $failure must not start" "$(grep -c '^start ' "$OUT/docker.calls" || true)" 0
    fi
  done
done

# The killed DS's clients finish before the crash and resume only after recovery.
# The other DS keeps writing throughout. A slow recovery must still leave useful load.
. "$HERE/load.sh"
DS_NODES="d0 d1" DISTURBED=d1
start_clients() { calls="$calls clients $*"; }
docker() { calls="$calls docker $*"; }
disturb() { calls="$calls disturb"; SECONDS=$((SECONDS + RECOVERY)); }
fail() { echo "::error::$1"; false; }
for RECOVERY in 870 871; do
  calls= SECONDS=0
  set +e
  (set -e; run_load; echo "$calls" >"$OUT/load.calls") >"$OUT/load.log"
  status=$?
  set -e
  if [ "$RECOVERY" = 870 ]; then
    check "30 s of resumed load" "$status" 0
    check "clients around the crash" "$(cat "$OUT/load.calls")" \
      " clients d0 1 900 clients d1 1 300 docker wait load-mod-d1 load-add-d1 disturb clients d1 2 30"
  else
    check "29 s is too late to resume" "$status" 1
    check "slow recovery diagnostic" "$(grep -c 'too late to take load again' "$OUT/load.log" || true)" 1
  fi
done

echo "disturb-kill-ds: $((checks - failed)) checks passed, $failed failed"
[ "$failed" -eq 0 ]
