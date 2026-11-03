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
DISTURBANCE=restart-rs
. "$HERE/disturb-restart-rs.sh"
check "load not started" "| disturbance | restart-rs at 300 s into the load, not started: the run ended before the load |"
LOAD_START_MS=100000
check "neither step completed" "| disturbance | restart-rs scheduled at 300 s into the load, not completed: 0 of 2 steps completed |"
echo '400000 445000 r0 kill' >"$OUT/disturbances.txt"
check "second step not completed" "| disturbance | kill of r0 300 s into the load, serving again 45 s later |
| disturbance | restart-rs not completed: 1 of 2 steps completed |"
echo '700000 735000 r1 restart' >>"$OUT/disturbances.txt"
check "both steps completed" "| disturbance | kill of r0 300 s into the load, serving again 45 s later |
| disturbance | restart of r1 600 s into the load, serving again 35 s later |"
DISTURBANCE=restart-ds
. "$HERE/disturb-restart-ds.sh"
rm "$OUT/disturbances.txt"
check "directory restart not completed" "| disturbance | restart-ds scheduled at 300 s into the load, not completed: 0 of 1 steps completed |"
echo '400000 435000 d1 restart' >"$OUT/disturbances.txt"
check "directory restart completed" "| disturbance | restart of d1 300 s into the load, serving again 35 s later |"
echo "summary: $((checks - failed)) checks passed, $failed failed"
[ "$failed" -eq 0 ]
