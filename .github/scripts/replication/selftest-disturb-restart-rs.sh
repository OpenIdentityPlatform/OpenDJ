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
# two thirds of the load unless the kill took longer; run_load leaves the clients of the directory
# servers be. A restart-rs row shows none of it, unless an error lands where the order moved.
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
wait_until() { calls="$calls wait_until $*"; now=$((now + SERVE)); }
window_open() { calls="$calls window_open${*:+ $*}"; }
window_close() { calls="$calls window_close"; }

# disturb at 300 s into a load that started at 100: the kill takes 3 docker calls and a wait of
# 30 s, the restart waits for 100 + 600
now=400 SERVE=30 calls=
disturb >/dev/null
check "disturb called" "$calls" \
  " window_open 180 181 284 docker kill r0 docker wait r0 docker start r0 wait_until 900 r0 serves again rs_serves_since r0 400 sleep 10 window_close sleep 245 window_open docker restart -t 120 r1 wait_until 900 r1 serves again rs_serves_since r1 700 sleep 10 window_close"
check "disturbances.txt holds" " $(tr '\n' ' ' <"$OUT/disturbances.txt")" " 400000 445000 r0 kill 700000 735000 r1 restart "

# a kill that serves again after two thirds of the load: the restart follows at once
rm "$OUT/disturbances.txt"
now=400 SERVE=400 calls=
disturb >/dev/null
check "a late kill, disturb called" "$calls" \
  " window_open 180 181 284 docker kill r0 docker wait r0 docker start r0 wait_until 900 r0 serves again rs_serves_since r0 400 sleep 10 window_close window_open docker restart -t 120 r1 wait_until 900 r1 serves again rs_serves_since r1 825 sleep 10 window_close"

calls=
settle
check "settle called" "$calls" \
  " wait_until 900 the join of r0 ends bootstrap_ended r0 wait_until 900 the join of r1 ends bootstrap_ended r1"

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

# run_load waits for DISTURB_AT and disturbs, with the clients of every directory server
# running the whole load
start_clients() { calls="$calls clients $*"; }
disturb() { calls="$calls disturb at $SECONDS"; }
calls= SECONDS=0
run_load
check "run_load called" "$calls" " clients d0 1 900 clients d1 1 900 sleep 300 disturb at 300"

echo "disturb-restart-rs: $((checks - failed)) checks passed, $failed failed"
[ "$failed" -eq 0 ]
