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

# A self-test of the disturbance restart-ds of the replication convergence run (#1208), with no
# topology: what disturb, settle and run_load call, in which order, and what disturb writes to
# disturbances.txt. The window of the errors logs opens before the restart and closes 10 s after
# the server serves again, and the restart ends when the server served, not after the grace. A
# restart-ds row shows neither, unless a handshake record lands where the order moved. docker,
# wait_until, window_open, window_close, date and sleep are stubs that record their calls, and
# the clock moves in the stubs alone: the restart takes 5 s, the wait for the server 30 s.
#
# Usage: selftest-disturb-restart-ds.sh

set -eE -o pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
DISTURB_TIMEOUT=900
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
date() { echo $now; }
sleep() { calls="$calls sleep $1"; now=$((now + $1)); }
docker() { calls="$calls docker $*"; now=$((now + 5)); }
wait_until() { calls="$calls wait_until $1 [$2] ${*:3}"; now=$((now + 30)); }
window_open() { calls="$calls window_open${*:+ $*}"; }
window_close() { calls="$calls window_close"; }

failed=0
check() { # <what> <got> <want>
  [ "$2" = "$3" ] || { echo "::error::$1:$2, not$3"; failed=$((failed + 1)); }
}

for TOPOLOGY in rs534 combined similar-host; do
  case $TOPOLOGY in
    rs534) DS_NODES="d0 d1" RS_NODES="r0 r1" target=d1 extras= ;;
    combined) DS_NODES="c0 c1" RS_NODES="$DS_NODES" target=c1 extras=" dns:c1" ;;
    similar-host) DS_NODES="d0 d1" RS_NODES="d10 d11" target=d1 extras= ;;
  esac
  . "$HERE/disturb-restart-ds.sh"
  now=100 calls=
  rm -f "$OUT/disturbances.txt"
  disturb >/dev/null
  check "$TOPOLOGY disturb called" "$calls" \
    " window_open$extras docker restart -t 120 $target wait_until 900 [$target serves again] started_since $target 100 sleep 10 window_close"
  check "$TOPOLOGY disturbances.txt holds" " $(cat "$OUT/disturbances.txt")" " 100000 135000 $target restart"
  calls=
  settle
  check "$TOPOLOGY settle called" "$calls" " wait_until 900 [the join of $target ends] bootstrap_ended $target"
done

# run_load stops the clients of the disturbed server at DISTURB_AT, waits for them to end before
# it disturbs, and has clients of its own write to it for the rest of the load. SECONDS without
# its special properties, so that run_load reads the clock of the stubs: the restart takes 200 s
DURATION=900 DISTURB_AT=300 DISTURBANCE=restart-ds
DS_NODES="d0 d1" DISTURBED=d1
. "$HERE/load.sh"
unset SECONDS
start_clients() { calls="$calls clients $*"; }
disturb() { calls="$calls disturb"; SECONDS=$((SECONDS + 200)); }
calls= SECONDS=0
run_load
check "run_load called" "$calls" \
  " clients d0 1 900 clients d1 1 300 docker wait load-mod-d1 load-add-d1 disturb clients d1 2 700"

echo "disturb-restart-ds: $((10 - failed)) checks passed, $failed failed"
[ "$failed" -eq 0 ]
