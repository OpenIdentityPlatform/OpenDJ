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

# Check the real summary for absent, partial and complete disturbances, without Docker.
set -eE -o pipefail
# The fixture summaries must not be appended to the real GitHub Actions job summary.
unset GITHUB_STEP_SUMMARY
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
. "$HERE/collect.sh"
TOPOLOGY=rs534 ALL_NODES="d0 d1 r0 r1" DS_NODES="d0 d1" RS_ONLY="r0 r1"
DURATION=900 DISTURB_AT=300 LOAD_MOD_RATE=250 LOAD_ADD_RATE=50 SAMPLE_DATA=10000 PHASE_TIMES=
failed=0 checks=0
check() { # <case> <expected disturbance rows>
  summarize failed
  checks=$((checks + 1))
  got=$(sed -n '/^| disturbance |/p' "$OUT/summary.md")
  [ "$got" = "$2" ] || { echo "::error::$1: got '$got', expected '$2'"; failed=$((failed + 1)); }
}
DISTURBANCE=none
check "no disturbance" ""
for DISTURBANCE in restart-rs kill-rs; do
  . "$HERE/disturb-$DISTURBANCE.sh"
  rm -f "$OUT/disturbances.txt"
  unset LOAD_START_MS
  check "$DISTURBANCE load not started" "| disturbance | $DISTURBANCE at 300 s into the load, not started: the run ended before the load |"
  LOAD_START_MS=100000
  check "$DISTURBANCE not completed" "| disturbance | $DISTURBANCE scheduled at 300 s into the load, not completed: 0 of 1 steps completed |"
  how=restart
  [ "$DISTURBANCE" != kill-rs ] || how=kill
  echo "400000 445000 r0 $how" >"$OUT/disturbances.txt"
  check "$DISTURBANCE completed" "| disturbance | $how of r0 300 s into the load, serving again 45 s later |"
done
for DISTURBANCE in restart-ds kill-ds; do
  . "$HERE/disturb-$DISTURBANCE.sh"
  rm "$OUT/disturbances.txt"
  check "$DISTURBANCE not completed" "| disturbance | $DISTURBANCE scheduled at 300 s into the load, not completed: 0 of 1 steps completed |"
  how=restart
  [ "$DISTURBANCE" != kill-ds ] || how=kill
  echo "400000 435000 d1 $how" >"$OUT/disturbances.txt"
  check "$DISTURBANCE completed" "| disturbance | $how of d1 300 s into the load, serving again 35 s later |"
done
# The post-load failover must remain visible even if only one RS was checked.
DISTURBANCE=empty-rs DISTURB_STEPS=1 CONVERGED_IN=10
printf 'r0 r1 450\n' >"$OUT/failover.txt"
summarize failed
checks=$((checks + 1))
if ! grep -Fx '| failover | stopped r0; all directory servers connected to r1 after 450 s |' "$OUT/summary.md" >/dev/null \
   || ! grep -Fx '| failover | incomplete: 1 of 2 replication servers checked |' "$OUT/summary.md" >/dev/null; then
  echo '::error::partial failover is absent from the summary'
  failed=$((failed + 1))
fi
# The measured backlog belongs in the CI summary, not only the uploaded artifact.
DISTURBANCE=scale-rs-empty DISTURB_STEPS=1
printf '400000 900000 r1 scale-empty\n' >"$OUT/disturbances.txt"
check 'scale completed' '| disturbance | scale-empty of r1 300 s into the load, completed 500 s later |'
mkdir -p "$OUT/scale-checkpoint"
echo '50001 changes while the server was absent' >"$OUT/scale-checkpoint/backlog.txt"
summarize passed
checks=$((checks + 1))
if ! grep -Fx '| scale backlog | 50001 changes while the server was absent |' "$OUT/summary.md" >/dev/null; then
  echo '::error::scale backlog is absent from the summary'; failed=$((failed + 1))
fi

echo "summary: $((checks - failed)) checks passed, $failed failed"
[ "$failed" -eq 0 ]
