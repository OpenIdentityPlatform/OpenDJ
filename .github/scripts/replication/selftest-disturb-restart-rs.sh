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

# A self-test of the disturbance restart-rs of the replication convergence run (#1208), with no
# topology: what disturb, settle and run_load call, in which order, and what disturb writes to
# disturbances.txt. The kill opens a window that expects what a kill leaves behind, the restart
# one that does not, each closes 10 s after its server serves again, and the restart waits for
# two thirds of the load unless the kill took longer, then for changelog catch-up. run_load
# leaves the clients of the directory servers be. A restart-rs row shows none of it, unless an error lands where the order moved.
# docker, wait_until, window_open, window_close, date and sleep are stubs that record their calls,
# and the clock moves in the stubs alone: a docker call takes 5 s, the wait for a server SERVE s.
#
# Usage: selftest-disturb-restart-rs.sh

set -eE -o pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
failed=0 checks=0
check() { # <what> <got> <want>
  checks=$((checks + 1))
  [ "$2" = "$3" ] || { echo "::error::$1:$2, not$3"; failed=$((failed + 1)); }
}

# a topology without two replication servers apart from the directory servers is refused
code=0
(RS_ONLY=cv-c0; . "$HERE/disturb-restart-rs.sh") >/dev/null 2>&1 || code=$?
check "with one replication server, sourcing exited with" " $code" " 2"

DS_NODES="d0 d1" RS_ONLY="r0 r1" RS_NODES="r0 r1" DURATION=900 DISTURB_AT=300 DISTURB_TIMEOUT=900
DISTURBANCE=restart-rs STAT_INTERVAL=10 LOAD_START_MS=100000
. "$HERE/disturb-restart-rs.sh"
. "$HERE/load.sh"
check "DISTURBED is" " $DISTURBED" " r0 r1"

# SECONDS without its special properties, so that run_load reads the clock of the stubs
unset SECONDS
SECONDS=0
now= calls=
date() { echo $now; }
sleep() { calls="$calls sleep $1"; now=$((now + $1)); SECONDS=$((SECONDS + $1)); }
docker() { calls="$calls docker $*"; now=$((now + 5)); }
wait_until() {
  calls="$calls wait_until $1 [$2] ${*:3}"
  if [ "$3" = rs_caught_up ]; then
    now=$((now + ${CATCHUP:-0}))
    [ "${CATCHUP_FAIL:-no}" != yes ] || fail "catch-up failed"
  else
    now=$((now + SERVE))
  fi
}
window_open() { calls="$calls window_open${*:+ $*}"; }
window_close() { calls="$calls window_close"; }

# disturb at 300 s into a load that started at 100: the kill takes 3 docker calls and a wait of
# 30 s, the restart waits for 100 + 600
now=400 SERVE=30 calls=
disturb >/dev/null
check "disturb called" "$calls" \
  " window_open 180 181 284 docker kill r0 docker wait r0 docker start r0 wait_until 900 [r0 serves again] rs_serves_since r0 400 sleep 10 window_close sleep 245 wait_until 270 [r0 reaches the changelog checkpoint of r1 (see rs-catchup/)] rs_caught_up r0 r1 window_open docker restart -t 120 r1 wait_until 900 [r1 serves again] rs_serves_since r1 700 sleep 10 window_close"
check "disturbances.txt holds" " $(tr '\n' ' ' <"$OUT/disturbances.txt")" " 400000 445000 r0 kill 700000 735000 r1 restart "

# a kill that serves again after two thirds of the load: the restart follows at once
rm "$OUT/disturbances.txt"
now=400 SERVE=400 calls=
disturb >/dev/null
check "a late kill, disturb called" "$calls" \
  " window_open 180 181 284 docker kill r0 docker wait r0 docker start r0 wait_until 900 [r0 serves again] rs_serves_since r0 400 sleep 10 window_close wait_until 145 [r0 reaches the changelog checkpoint of r1 (see rs-catchup/)] rs_caught_up r0 r1 window_open docker restart -t 120 r1 wait_until 900 [r1 serves again] rs_serves_since r1 825 sleep 10 window_close"

calls=
settle
check "settle called" "$calls" \
  " wait_until 900 [the join of r0 ends] bootstrap_ended r0 wait_until 900 [the join of r1 ends] bootstrap_ended r1"

# serves again: started since, and connected to the other replication server
started_since() { [ "$1 $2" = "r0 400" ] && [ "$started" = yes ]; }
rs_peers() { [ "$1" = r0 ] && echo $peers; }
serves() { if rs_serves_since r0 400; then echo yes; else echo no; fi; }
started=yes peers=1
check "started, 1 peer, serves" " $(serves)" " yes"
started=yes peers=0
check "started, no peer, serves" " $(serves)" " no"
started=yes peers=2
check "started, 2 peers, serves" " $(serves)" " no"
started=no peers=1
check "not started, 1 peer, serves" " $(serves)" " no"

# A slow recovery must not let the second restart run without useful load left. Run each
# probe outside an if/|| condition so errexit still stops at the production fail() call.
fail() { echo "::error::$1"; false; }
budget_case() { # <name> <serve seconds> <sleep delay> <exit code> <steps> [<catch-up seconds>]
  local code
  rm -f "$OUT/disturbances.txt"
  set +e
  (
    set -e
    now=400 SERVE=$2 CATCHUP=${6:-0}
    sleep() {
      now=$((now + $1))
      # A descheduled runner can resume later than sleep requested.
      [ "$1" -le 10 ] || now=$((now + delay))
    }
    delay=$3
    disturb >"$OUT/budget.log"
  )
  code=$?
  set -e
  check "$1 exits with" " $code" " $4"
  check "$1 completed steps" " $(wc -l <"$OUT/disturbances.txt" | tr -d ' ')" " $5"
  if [ "$4" -ne 0 ]; then
    check "$1 reports why" " $(grep -c 'too late to restart r1 under' "$OUT/budget.log" || true)" " 1"
    check "$1 never starts r1" " $(grep -c '^    .* restart r1$' "$OUT/budget.log" || true)" " 0"
  fi
}
budget_case "30 seconds left" 545 0 0 2
budget_case "29 seconds left" 546 0 1 1
budget_case "load already ended" 600 0 1 1
budget_case "scheduled sleep overran" 30 300 1 1
budget_case "catch-up leaves 30 seconds" 30 0 0 2 270
budget_case "catch-up leaves 29 seconds" 30 0 1 1 271

# A failed catch-up must leave r1 running and retain the completed kill in the summary input.
rm -f "$OUT/disturbances.txt"
set +e
(
  set -e
  now=400 SERVE=30 CATCHUP_FAIL=yes
  disturb >"$OUT/catchup-failed.log"
)
code=$?
set -e
check "failed catch-up exits with" " $code" " 1"
check "failed catch-up completed steps" " $(wc -l <"$OUT/disturbances.txt" | tr -d ' ')" " 1"
check "failed catch-up never starts r1" " $(grep -c '^    .* restart r1$' "$OUT/catchup-failed.log" || true)" " 0"

# Exercise the real checkpoint probe; only the external LDAP reads and bootstrap are stubs.
# A later retry must refresh r1, not approve r0 against the old snapshot while writes continue.
BASE_DN=dc=example,dc=com
printf '1 d0\n2 d1\n' >"$OUT/server-ids.txt"
checkpoint_entry() {
  printf 'dn: cn=Changelog for DS(%s),cn=monitor\nreplicationServer-database: %s\ndomain-name: %s\nlast-change: %s\n\n' \
    "$1" "$1" "$BASE_DN" "$2"
}
source_csn=000001a1a1e836e400010000000a
target_csn=000001a1a1e836e4000100000009
bootstrap=yes search_fail= calls=
bootstrap_ended() { [ "$bootstrap" = yes ]; }
admin_search() {
  calls="$calls [$*]"
  local csn=$source_csn
  [ "$1" != r0 ] || csn=$target_csn
  checkpoint_entry 1 "$csn"
  checkpoint_entry 2 000001a1a1e836e400020000000a
  [ "$search_fail" != "$1" ]
}
probe() { if rs_caught_up r0 r1; then echo yes; else echo no; fi; }
check "behind the first checkpoint" " $(probe)" " no"
check "the diagnostic names the missing CSN" " $(grep -c "$source_csn" "$OUT/rs-catchup/check.txt" || true)" " 1"
target_csn=$source_csn source_csn=000001a1a1e836e400010000000b
check "reached only the previous checkpoint" " $(probe)" " no"
target_csn=$source_csn
check "reached the fresh checkpoint" " $(probe)" " yes"
# Direct invocation retains the stub's call trace, unlike the command substitution in probe.
calls=
rs_caught_up r0 r1
check "source is read before target, over all domains" "$calls" \
  " [r1 cn=monitor sub (replicationServer-database=*) replicationServer-database domain-name last-change] [r0 cn=monitor sub (replicationServer-database=*) replicationServer-database domain-name last-change]"
bootstrap=no
check "bootstrap still running" " $(probe)" " no"
bootstrap=yes search_fail=r1
check "failed source read with valid partial output" " $(probe)" " no"
search_fail=r0
check "failed target read with valid partial output" " $(probe)" " no"
search_fail=

# Run the real wait_until and checkpoint probe together: persistent lag must time out,
# not merely pass a stubbed wait and restart the only caught-up RS.
rm -f "$OUT/disturbances.txt"
set +e
(
  set -e
  . "$HERE/lib.sh"
  RS_ONLY="r0 r1" RS_NODES="r0 r1" DURATION=900 DISTURB_TIMEOUT=4
  LOAD_START_MS=100000 STAT_INTERVAL=10 now=400 SECONDS=400
  . "$HERE/disturb-restart-rs.sh"
  rs_serves_since() { return 0; }
  source_csn=000001a1a1e836e400010000000a
  target_csn=000001a1a1e836e4000100000009
  disturb >"$OUT/catchup-timeout.log"
)
code=$?
set -e
check "persistent lag exits with" " $code" " 1"
check "persistent lag completed steps" " $(wc -l <"$OUT/disturbances.txt" | tr -d ' ')" " 1"
check "persistent lag reports timeout" " $(grep -c 'timed out waiting until r0 reaches' "$OUT/catchup-timeout.log" || true)" " 1"
check "persistent lag never starts r1" " $(grep -c '^    .* restart r1$' "$OUT/catchup-timeout.log" || true)" " 0"

# run_load waits for DISTURB_AT and disturbs, with the clients of every directory server
# running the whole load
start_clients() { calls="$calls clients $*"; }
disturb() { calls="$calls disturb at $SECONDS"; }
calls= SECONDS=0
run_load
check "run_load called" "$calls" " clients d0 1 900 clients d1 1 900 sleep 300 disturb at 300"

# Starting the clients can itself take longer than DISTURB_AT: no negative sleep.
start_clients() { calls="$calls clients $*"; SECONDS=$((SECONDS + 160)); }
calls= SECONDS=0
run_load
check "a late start, run_load called" "$calls" " clients d0 1 900 clients d1 1 900 disturb at 320"

python3 -I "$HERE/selftest-changelog-checkpoint.py"

echo "disturb-restart-rs: $((checks - failed)) checks passed, $failed failed"
[ "$failed" -eq 0 ]
