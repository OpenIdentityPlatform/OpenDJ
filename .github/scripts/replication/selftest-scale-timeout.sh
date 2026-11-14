#!/usr/bin/env bash
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
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/logs" "$OUT/scale-progress"
DS_NODES='d0 d1' RS_ONLY='r0 r1 r2' RS_NODES=$RS_ONLY ALL_NODES="$DS_NODES $RS_ONLY"
TOPOLOGY=rs534 SCALE_VOLUME=kept PEERS=d0,d1,r0,r1,r2 NETWORK=test BASE_DN=dc=example,dc=com
. "$HERE/disturb-scale.sh"
printf '23874 d0\n' >"$OUT/server-ids.txt"
printf '0\n' >"$OUT/logs/d0.mark"
printf '0 dns:r1\n' >"$OUT/logs/d0.window-up-d0"
docker() { echo 172.18.0.9; }
errors_lines() { wc -l <"$OUT/errors"; }
since_mark() { cat "$OUT/errors"; }
E='[t] category=SYNC severity=ERROR msgID=212 msg=Directory server DS(23874) timed out while connecting to replication server r1:8989 for domain "dc=example,dc=com"'
N='[t] category=SYNC severity=NOTICE msgID=62 msg=Directory server DS(23874) has connected to replication server RS(9861) for domain "dc=example,dc=com" at 172.18.0.9:8989 with generation ID 1'
S='[t] category=CORE severity=NOTICE msgID=135 msg=The Directory Server has started successfully'
printf 'other\n%s\n%s\n%s\n' "$E" "$N" "$S" >"$OUT/errors"
scale_record_reconnected_timeouts d0 r1 up
grep -Fx '1 2 timeout:d0:r1' "$OUT/logs/d0.windows" >/dev/null || { echo 'confirmed timeout was not scoped to its line' >&2; exit 1; }
for notice in "${N/172.18.0.9/172.18.0.8}" "${N/DS(23874)/DS(23875)}" "${N/dc=example,dc=com/cn=schema}"; do
  : >"$OUT/logs/d0.windows"
  printf '%s\n%s\n%s\n' "$E" "$notice" "$S" >"$OUT/errors"
  scale_record_reconnected_timeouts d0 r1 up
  [ ! -s "$OUT/logs/d0.windows" ] || { echo 'different reconnection accepted' >&2; exit 1; }
done
: >"$OUT/logs/d0.windows"
printf '%s\n%s\n%s\n' "$N" "$E" "$S" >"$OUT/errors"
scale_record_reconnected_timeouts d0 r1 up
[ ! -s "$OUT/logs/d0.windows" ] || { echo 'earlier connection accepted' >&2; exit 1; }
printf '%s\n%s\n%s\n' "$S" "$E" "$N" >"$OUT/errors"
scale_record_reconnected_timeouts d0 r1 up
[ ! -s "$OUT/logs/d0.windows" ] || { echo 'timeout after startup accepted' >&2; exit 1; }
printf '%s\n%s\n' "$E" "$N" >"$OUT/errors"
scale_record_reconnected_timeouts d0 r1 up
[ ! -s "$OUT/logs/d0.windows" ] || { echo 'missing startup marker accepted' >&2; exit 1; }
echo 'scale startup timeout requires the same later connection'
printf '0 dns:r1\n' >"$OUT/logs/d0.window-down-r0"
D='[t] category=SYNC severity=ERROR msgID=180 msg=Directory server DS(23874) encountered an error while receiving changes for domain "dc=example,dc=com" from replication server RS(9860) at 172.18.0.8:8989. The connection will be closed, and this directory server will now try to connect to another replication server'
: >"$OUT/logs/d0.windows"
printf '%s\n%s\n%s\n%s\n' "$S" "$D" "$E" "$N" >"$OUT/errors"
scale_record_roll_failover d0 r0 r1 down 172.18.0.8
grep -Fx '1 2 disconnect:d0:172.18.0.8' "$OUT/logs/d0.windows" >/dev/null
grep -Fx '2 3 timeout:d0:r1' "$OUT/logs/d0.windows" >/dev/null
for rows in "$S"$'\n'"$E"$'\n'"$N" "$D"$'\n'"$E" "${D/172.18.0.8/172.18.0.7}"$'\n'"$E"$'\n'"$N" "$D"$'\n'"$E"$'\n'"${N/dc=example,dc=com/cn=schema}" "$N"$'\n'"$D"$'\n'"$E"; do
  : >"$OUT/logs/d0.windows"
  printf '%s\n' "$rows" >"$OUT/errors"
  scale_record_roll_failover d0 r0 r1 down 172.18.0.8
  [ ! -s "$OUT/logs/d0.windows" ] || { echo 'unproven rolling failover accepted' >&2; exit 1; }
done
echo 'rolling failover requires the stopped peer and a later connection in the same domain'
