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

set -eE -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/logs"
if [ ! -f "$HERE/disturb-empty-rs.sh" ]; then
  echo '::error::empty-rs disturbance is missing'
  exit 1
fi
DS_NODES='d0 d1 d2 d3' RS_ONLY='r0 r1' RS_NODES=$RS_ONLY ALL_NODES="$DS_NODES $RS_ONLY"
DISTURB_TIMEOUT=900 QUIESCE_TIMEOUT=4500 DURATION=1800 LOAD_START_MS=100000 STAT_INTERVAL=10 CONVERGED_IN=10 NETWORK=fixture
. "$HERE/disturb-empty-rs.sh"
record() { printf '%s\n' "$*" >>"$OUT/calls"; }
docker() {
  record "docker $*"
  case $1 in cp) printf 'old log\n' >"$3" ;; inspect) echo 172.18.0.9 ;; esac
}
start_server() { record "start_server $*"; }
window_open() { record "window_open $*"; }
window_close() { record window_close; }
errors_lines() { echo 2; }
wait_until() {
  record "wait $2: ${*:3}"
  [ "${FAIL_WAIT:-}" != "${3:-}" ]
}
wait_converged() { record convergence; }
sleep() { :; }
date() { echo 400; }
fail() { echo "::error::$1"; return 1; }

disturb
settle
after_convergence
cat >"$OUT/want" <<'WANT'
window_open dns:r1 cert:d1:r1 180
stop d1
stop r1
archive r1
remove r1
remove-volume r1
start r1
rs-ready r1
start d1
ds-ready d1
window_close
settle d1
settle r1
checkpoint r1 r0
window_open dns:r0 180
stop r0
connected d0 r1
connected d1 r1
connected d2 r1
connected d3 r1
start r0
rs-ready r0
settle r0
window_close
convergence
checkpoint r0 r1
window_open dns:r1 180
stop r1
connected d0 r0
connected d1 r0
connected d2 r0
connected d3 r0
start r1
rs-ready r1
settle r1
window_close
convergence
WANT
# Normalize only timings and wait descriptions; preserve operation order and targets.
awk '
/^window_open/ {print; next}
/^window_close$/ {print; next}
/^docker stop/ {print "stop", $NF; next}
/^docker start/ {print "start", $NF; next}
/^docker cp/ {print "archive", substr($3,1,index($3,":")-1); next}
/^docker rm/ {print "remove", $NF; next}
/^docker volume rm/ {sub(/^vol-/,"",$NF); print "remove-volume",$NF; next}
/^start_server/ {print "start",$2; next}
/^wait .*rs_serves_since/ {print "rs-ready",$(NF-1); next}
/^wait .*started_since/ {print "ds-ready",$(NF-1); next}
/^wait .*bootstrap_ended/ {print "settle",$NF; next}
/^wait .*rs_caught_up/ {print "checkpoint",$(NF-1),$NF; next}
/^wait .*connected_to/ {print "connected",$(NF-1),$NF; next}
/^convergence$/ {print}
' "$OUT/calls" >"$OUT/got"
diff -u "$OUT/want" "$OUT/got"
[ -s "$OUT/logs/r1.previous" ] || { echo '::error::old RS log was not retained'; exit 1; }
grep -Fx '2 2 cert-peer:r1:172.18.0.9' "$OUT/logs/r1.windows" >/dev/null \
  || { echo '::error::DS address missing from RS certificate window'; exit 1; }
# A failed checkpoint must not stop the only other RS.
: >"$OUT/calls"
set +e
(set -e; FAIL_WAIT=rs_caught_up; after_convergence) >"$OUT/failure" 2>&1
code=$?
set -e
if [ "$code" -eq 0 ]; then
  echo '::error::failed checkpoint was accepted'; exit 1
fi
if grep '^docker stop' "$OUT/calls" >/dev/null; then
  echo '::error::RS stopped before its peer caught up'; exit 1
fi
# Exercise the real monitor predicate: a missing address, failed search or another RS must
# never count as the DS having connected to the intended peer.
NETWORK=fixture MONITOR_DOMAIN='cn=dc_example_dc_com,cn=Replication,cn=monitor'
docker() { [ "$1" = inspect ] || return 1; [ "${NO_ADDRESS:-0}" = 1 ] || echo 172.18.0.9; }
admin_search() { [ "${SEARCH_FAILED:-0}" = 0 ] || return 1; printf '%s\n' "${MONITOR_LINE:-connected-to: 172.18.0.9:8989}"; }
connected_to d0 r1 || { echo '::error::matching RS was rejected'; exit 1; }
if (NO_ADDRESS=1; connected_to d0 r1) || (SEARCH_FAILED=1; connected_to d0 r1) || \
   (MONITOR_LINE='connected-to: 172.18.0.10:8989'; connected_to d0 r1) || \
   (MONITOR_LINE='connected-to: 172.18.0.9:8990'; connected_to d0 r1); then
  echo '::error::an absent or different RS was accepted'; exit 1
fi
# Check the actual monitor-to-comparator wiring, including failed and stale reads.
BASE_DN=dc=example,dc=com
printf '1 d0\n' >"$OUT/server-ids.txt"
cat >"$OUT/reference.ldif" <<'LDIF'
dn: cn=changelog,cn=monitor
replicationServer-database: 1
domain-name: dc=example,dc=com
last-change: 000001a1a1e836e400010000000a
LDIF
cp "$OUT/reference.ldif" "$OUT/current.ldif"
admin_search() {
  [ "${SEARCH_FAILED:-}" != "$1" ] || return 1
  if [ "$1" = r0 ]; then cat "$OUT/reference.ldif"; else cat "$OUT/current.ldif"; fi
}
bootstrap_ended() { [ "${NOT_JOINED:-}" != "$1" ]; }
rs_caught_up r1 r0 || { echo '::error::matching checkpoint was rejected'; exit 1; }
sed 's/0000000a/00000009/' "$OUT/reference.ldif" >"$OUT/current.ldif"
if rs_caught_up r1 r0; then echo '::error::lagging RS was accepted'; exit 1; fi
cp "$OUT/reference.ldif" "$OUT/current.ldif"
if (SEARCH_FAILED=r0; rs_caught_up r1 r0) || (NOT_JOINED=r1; rs_caught_up r1 r0); then
  echo '::error::failed checkpoint read or incomplete join was accepted'; exit 1
fi
echo 'empty-rs lifecycle: passed'
