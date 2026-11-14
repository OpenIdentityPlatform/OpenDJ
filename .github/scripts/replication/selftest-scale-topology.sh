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
check() (
  TOPOLOGY=$1 DISTURBANCE=$2
  . "$HERE/lib.sh"
  [ "$(echo "$DS_NODES" | wc -w)" -eq 4 ] || exit 1
  [ "$(echo "$RS_NODES" | wc -w)" -eq "$3" ] || exit 1
  [ "$(echo "$ALL_NODES" | wc -w)" -eq "$4" ] || exit 1
)
check rs534 scale-rs-kept 3 7
check rs534 scale-rs-empty 3 7
check rs534 empty-rs 2 6
check rs534 none 2 6
check combined scale-kept 4 4
check combined scale-empty 4 4
# Scale uses a 2700 s load ceiling and reduced rates; other rows and explicit settings retain theirs.
for disturbance in scale-rs-kept scale-rs-empty scale-kept scale-empty; do
  ( unset DISTURB_AT DURATION LOAD_MOD_RATE LOAD_ADD_RATE; TOPOLOGY=combined DISTURBANCE=$disturbance
    . "$HERE/lib.sh"
    [ "$DURATION" -eq 2700 ] && [ "$DISTURB_AT" -eq 120 ] && [ "$LOAD_MOD_RATE" -eq 100 ] && [ "$LOAD_ADD_RATE" -eq 20 ] ) || exit 1
done
( unset DISTURB_AT DURATION LOAD_MOD_RATE LOAD_ADD_RATE; TOPOLOGY=rs534 DISTURBANCE=empty-rs
  . "$HERE/lib.sh"
  [ "$DISTURB_AT" -eq 300 ] && [ "$LOAD_MOD_RATE" -eq 423 ] && [ "$LOAD_ADD_RATE" -eq 85 ] ) || exit 1
( TOPOLOGY=combined DISTURBANCE=scale-kept DISTURB_AT=77 DURATION=900 LOAD_MOD_RATE=42 LOAD_ADD_RATE=7
  . "$HERE/lib.sh"
  [ "$DISTURB_AT" -eq 77 ] && [ "$LOAD_MOD_RATE" -eq 42 ] && [ "$LOAD_ADD_RATE" -eq 7 ] ) || exit 1
# Only scale rows publish the admin port, and only on loopback for the native monitor.
(
  OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
  . "$HERE/topology-up.sh"
  docker() { printf '%s\n' "$*" >>"$OUT/calls"; }
  NETWORK=test ROOT_PASSWORD=password SERVER_JAVA_ARGS=-Xmx1g IMAGE=test PEERS=d0,d1
  DISTURBANCE=scale-kept; start_server d0 combined
  grep -F -- '-p 127.0.0.1::4444' "$OUT/calls" >/dev/null || { echo 'scale admin port is not loopback-published' >&2; exit 1; }
  grep -F -- '-e JOIN_TIMING=1' "$OUT/calls" >/dev/null || { echo 'scale bootstrap timing is not enabled' >&2; exit 1; }
  : >"$OUT/calls"; DISTURBANCE=restart-ds; start_server d0 combined
  if grep -F -- '-p 127.0.0.1::4444' "$OUT/calls" >/dev/null; then echo 'ordinary topology published an admin port' >&2; exit 1; fi
  if grep -F -- 'JOIN_TIMING=' "$OUT/calls" >/dev/null; then echo 'ordinary topology enabled bootstrap timing' >&2; exit 1; fi
)
echo 'scale topology cases passed'
