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
docker() { case ${@: -1} in r0) echo 172.18.0.8 ;; r1) echo 172.18.0.9 ;; r2) echo 172.18.0.10 ;; *) return 1 ;; esac; }
errors_lines() { if [ -n "${WINDOW_AFTER:-}" ]; then echo "$WINDOW_AFTER"; else wc -l <"$OUT/errors"; fi; }
since_mark() { cat "$OUT/errors"; }
bootstrap_ended() { return "${BOOTSTRAP_STATUS:-0}"; }
E='[t] category=SYNC severity=ERROR msgID=212 msg=Directory server DS(23874) timed out while connecting to replication server r1:8989 for domain "dc=example,dc=com"'
N='[t] category=SYNC severity=NOTICE msgID=62 msg=Directory server DS(23874) has connected to replication server RS(9861) for domain "dc=example,dc=com" at 172.18.0.9:8989 with generation ID 1'
S='[t] category=CORE severity=NOTICE msgID=135 msg=The Directory Server has started successfully'
printf 'other\n%s\n%s\n%s\n' "$E" "$N" "$S" >"$OUT/errors"
scale_record_reconnected_timeouts d0 r1 up
grep -Fx '1 2 startup-timeout:d0' "$OUT/logs/d0.windows" >/dev/null || { echo 'confirmed timeout was not scoped to its line' >&2; exit 1; }
for notice in "${N/172.18.0.9/172.18.0.77}" "${N/DS(23874)/DS(23875)}" "${N/dc=example,dc=com/cn=schema}"; do
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

# After return, either live RS can receive a surviving writer. The absent third RS
# is not a candidate during scale-in, even if its address still appears in a log.
docker() { case ${@: -1} in r1) echo 172.18.0.9 ;; r2) echo 172.18.0.10 ;; *) return 1 ;; esac; }
printf '0 dns:r0\n' >"$OUT/logs/d0.window-up-r0"
: >"$OUT/logs/d0.windows"
printf '%s\n%s\n' "$D" "${N/172.18.0.9/172.18.0.10}" >"$OUT/errors"
scale_record_roll_failover d0 r0 r1 up 172.18.0.8
grep -Fx '0 1 disconnect:d0:172.18.0.8' "$OUT/logs/d0.windows" >/dev/null || { echo 'returned third RS failover rejected' >&2; exit 1; }
for direction in down up; do
  for address in 172.18.0.8 172.18.0.77 $([ "$direction" != down ] || echo 172.18.0.10); do
    : >"$OUT/logs/d0.windows"
    printf '%s\n%s\n' "$D" "${N/172.18.0.9/$address}" >"$OUT/errors"
    scale_record_roll_failover d0 r0 r1 "$direction" 172.18.0.8
    [ ! -s "$OUT/logs/d0.windows" ] || { echo 'stopped or unknown RS failover accepted' >&2; exit 1; }
  done
done
echo 'returned RS is accepted only after return; stopped and unknown peers remain errors'

# Exercise the recorder and the error filter together. The admin-data domain has
# its own DS ID, and recovery may choose another currently available RS.
. "$HERE/assert-converged.sh"
errors_lines() { if [ -n "${WINDOW_AFTER:-}" ]; then echo "$WINDOW_AFTER"; else wc -l <"$OUT/errors"; fi; }
since_mark() { cat "$OUT/errors"; }
docker() {
  case ${@: -1} in
    r0) echo 172.18.0.8 ;; r1) echo 172.18.0.9 ;; r2) echo 172.18.0.10 ;;
    *) return 1 ;;
  esac
}
REMOVED=r2
A='[t] category=SYNC severity=ERROR msgID=212 msg=Directory server DS(31955) timed out while connecting to replication server r1:8989 for domain "cn=admin data"'
B='[t] category=SYNC severity=NOTICE msgID=62 msg=Directory server DS(31955) has connected to replication server RS(9860) for domain "cn=admin data" at 172.18.0.8:8989 with generation ID 1'
check_startup() { # <description> <direction> <expected error count> <records...>
  local description=$1 direction=$2 expected=$3; shift 3
  : >"$OUT/logs/d0.windows"
  rm -f "$OUT/logs/d0.startup-timeouts"
  printf '%s dns:r1\n' "${WINDOW_BEFORE:-0}" >"$OUT/logs/d0.window-$direction-d0"
  printf '%s\n' "$@" >"$OUT/errors"
  scale_record_reconnected_timeouts d0 r1 "$direction"
  replication_errors d0 >"$OUT/rejected"
  [ "$(wc -l <"$OUT/rejected" | tr -d ' ')" = "$expected" ] \
    || { echo "startup: $description (expected $expected errors)" >&2; cat "$OUT/rejected" >&2; exit 1; }
}
check_startup 'admin domain recovers on another eligible RS' down 0 "$A" "$B" "$S"
check_startup 'schema domain recovers' down 0 "${A/cn=admin data/cn=schema}" "${B/cn=admin data/cn=schema}" "$S"
check_startup 'returning RS is available during scale-out' up 0 "$A" "${B/172.18.0.8/172.18.0.10}" "$S"
check_startup 'removed RS is unavailable during scale-in' down 1 "$A" "${B/172.18.0.8/172.18.0.10}" "$S"
check_startup 'unknown RS' up 1 "$A" "${B/172.18.0.8/172.18.0.77}" "$S"
check_startup 'different domain' down 1 "$A" "${B/cn=admin data/cn=schema}" "$S"
check_startup 'different DS' down 1 "$A" "${B/DS(31955)/DS(31956)}" "$S"
check_startup 'connection precedes error' down 1 "$B" "$A" "$S"
check_startup 'connection follows startup before successful bootstrap' down 0 "$A" "$S" "$B"
check_startup 'unknown timeout peer' down 1 "${A/r1:8989/unknown:8989}" "$S" "$B"
check_startup 'unknown matching domain' down 1 "${A/cn=admin data/dc=unknown}" "$S" "${B/cn=admin data/dc=unknown}"
check_startup 'later unrelated restart' down 1 "$A" "$S" "$S" "$B"
BOOTSTRAP_STATUS=1 check_startup 'bootstrap incomplete' down 1 "$A" "$S" "$B"
BOOTSTRAP_STATUS=200 check_startup 'bootstrap failed' down 1 "$A" "$S" "$B"
WINDOW_BEFORE=1 check_startup 'timeout before rolling window' down 1 "$A" "$S" "$B"
WINDOW_AFTER=2 check_startup 'connection after captured bootstrap bound' down 1 "$A" "$S" "$B"
check_startup 'startup incomplete' down 1 "$A" "$B"
check_startup 'timeout outside startup window' down 1 "$S" "$A" "$B"
check_startup 'unrecovered second domain' down 1 "$A" "${A/cn=admin data/cn=schema}" "$B" "$S"
check_startup 'only the recorded line is accepted' down 1 "$A" "$B" "$S" "$A"
check_startup 'original evidence' down 0 "$A" "$B" "$S"
printf '%s\n%s\n%s\n' "${A/cn=admin data/cn=schema}" "$B" "$S" >"$OUT/errors"
[ "$(replication_errors d0 | wc -l | tr -d ' ')" = 1 ] || { echo 'changed log accepted with stale evidence' >&2; exit 1; }
echo 'startup timeouts require exact evidence, matching domain/DS, an eligible RS and completed bootstrap in one bounded rolling startup'
