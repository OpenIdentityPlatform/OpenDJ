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

# A self-test of the errors log check of the replication convergence run (#1208), with no
# topology: which records replication_errors takes for errors and which ones a disturbance
# window expects: failed handshakes and the msgIDs or dns:<hostname> tokens it was opened with.
# A green row does not show it - nothing a disturbance expects is logged outside its window
# there, and the base rows have no window - so docker-convergence runs this first.
# docker is a stub that reads a local errors log: mark_logs, window_open and window_close count
# its lines, since_mark reads it, as they do in a container.
#
# Usage: selftest-replication-errors.sh

set -eE -o pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/assert-converged.sh"
ALL_NODES=n0
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
LOG=$OUT/errors

docker() { # exec <node> sh -c <the line count of the errors log> | exec <node> tail -n +<first> <errors log>
  case "$1 $3" in
    "exec sh") wc -l <"$LOG" | tr -d ' ' ;;
    "exec tail") tail -n "$5" "$LOG" ;;
    *) echo "unexpected docker $*" >&2; return 1 ;;
  esac
}

E178='[t] category=SYNC severity=ERROR msgID=178 msg=Directory server 32536 was attempting to connect to replication server 31531 but has disconnected in handshake phase'
E179='[t] category=SYNC severity=ERROR msgID=179 msg=Replication server 1 was attempting to connect to replication server 2 but has disconnected in handshake phase'
E145='[t] category=SYNC severity=ERROR msgID=145 msg=Timed out while trying to acquire the domain lock for domain "dc=example,dc=com". The connection attempt from replication server RS(29892) at cv-c1.convergence/172.18.0.3:52382 to this replication server RS(27915) will be aborted. This is probably benign and a result of a simultaneous cross connection attempt'
W119='[t] category=SYNC severity=WARNING msgID=119 msg=Directory server DS(24329) encountered an unexpected error while connecting to replication server cv-r0:8989 for domain "cn=schema": SSLHandshakeException(Remote host terminated the handshake)'
E119=${W119/severity=WARNING/severity=ERROR}
W119CORE=${W119/category=SYNC/category=CORE}
WBAD='[t] category=SYNC severity=WARNING msgID=119 msg=Directory server DS(1) encountered an unexpected error while connecting: SSLHandshakeException(Received fatal alert: bad_certificate)'
WBADCORE='[t] category=CORE severity=WARNING msgID=1 msg=SSLException(Received fatal alert: bad_certificate)'
E106='[t] category=SYNC severity=ERROR msgID=106 msg=another replication error'
E1450='[t] category=SYNC severity=ERROR msgID=1450 msg=an id that starts like one a window expects'
E45='[t] category=SYNC severity=ERROR msgID=45 msg=an id that ends like one a window expects'
E106ID='[t] category=SYNC severity=ERROR msgID=106 msg=Replication server 32179 lost its connection'
N62='[t] category=SYNC severity=NOTICE msgID=62 msg=Directory server DS(1) has connected'
CORE178='[t] category=CORE severity=ERROR msgID=178 msg=not a replication error'
# What an RS disturbance can leave behind, and ids like its allowances.
E180='[t] category=SYNC severity=ERROR msgID=180 msg=Directory server DS(13411) encountered an error while receiving changes for domain "dc=example,dc=com" from replication server RS(23837) at cv-r0:8989. The connection will be closed, and this directory server will now try to connect to another replication server'
E181='[t] category=SYNC severity=ERROR msgID=181 msg=The connection from this replication server RS(31531) to replication server RS(23837) at cv-r0:8989 for domain "dc=example,dc=com" has failed'
E284="[t] category=SYNC severity=ERROR msgID=284 msg=Log file '/opt/opendj/data/changelogDb/2.dom/13411.server/head.log' was successfully recovered by removing a partially written record"
E211='[t] category=SYNC severity=ERROR msgID=211 msg=The connection from this replication server RS(31531) to directory server DS(24329) at cv-d1:8989 for domain "dc=example,dc=com" has failed'
E66='[t] category=SYNC severity=ERROR msgID=66 msg=monitor reply failed'
E108='[t] category=SYNC severity=ERROR msgID=108 msg=monitor request failed'
E51='[05/Nov/2026:19:18:17 +0000] category=SYNC severity=ERROR msgID=51 msg=The hostname cv-r0 could not be resolved as an IP address'
BASE_DN=dc=example,dc=com
E212='[t] category=SYNC severity=ERROR msgID=212 msg=Directory server DS(23874) timed out while connecting to replication server cv-r1:8989 for domain "dc=example,dc=com"'
E18='[t] category=SYNC severity=ERROR msgID=18 msg=an id that starts the first one a window lists'
E80='[t] category=SYNC severity=ERROR msgID=80 msg=an id that ends the first one a window lists'
E106KILL='[t] category=SYNC severity=ERROR msgID=106 msg=an id a window lists, 181, in the message'
# records the check reads for their certificate alert, under an id a window lists
WBAD181=${WBAD/msgID=119/msgID=181}
WBADCORE181=${WBADCORE/msgID=1/msgID=181}
KILL='[ dns:cv-r0 180 181 284'
# Read the restart allowances from its entry point so the filter cases also detect a missing
# or widened production allowance. Only the Docker lifecycle is replaced here.
RESTART=$(
  RS_ONLY='cv-r0 cv-r1' RS_NODES='cv-r0 cv-r1'
  . "$HERE/disturb-restart-rs.sh"
  down_and_up() { shift; printf '[ %s' "$*"; }
  disturb
)
# Run the DS entry point too: the combined restart receives a DNS token, while a DS-only
# restart does not. Keep the production window arguments and replace only the lifecycle.
ds_restart_window() (
  DS_NODES=$1 RS_NODES=$2 DISTURB_TIMEOUT=900
  . "$HERE/disturb-restart-ds.sh"
  window_open() { printf '[%s' "${*:+ $*}" >"$OUT/ds-restart-window"; }
  window_close() { :; }
  docker() { :; }
  wait_until() { :; }
  sleep() { :; }
  date() { echo 100; }
  disturb >/dev/null
  cat "$OUT/ds-restart-window"
)
RESTART_DS=$(ds_restart_window 'cv-d0 cv-d1' 'cv-r0 cv-r1')
RESTART_COMBINED=$(ds_restart_window 'cv-c0 cv-c1 cv-c2 cv-c3' 'cv-c0 cv-c1 cv-c2 cv-c3')

passed=0 failed=0
# case <name> <errors> <expected> <record, [, [ <msgID>|dns:<hostname>... or ]>...: the errors log
# holds two errors before mark_logs, which are not the run's, then the records given; [ opens a
# window with the msgIDs or DNS tokens that follow it, and ] closes it. Count replication_errors
# output records and those it sends to logs/n0.expected.
case_() {
  local name=$1 want_errors=$2 want_expected=$3 record errors expected
  shift 3
  rm -rf "$OUT/logs"
  printf '%s\n' "$E178" "$E106" >"$LOG"
  mark_logs
  for record; do
    case $record in
      '[') window_open ;;
      '[ '*) window_open ${record#\[ } ;;
      ']') window_close ;;
      *) echo "$record" >>"$LOG" ;;
    esac
  done
  errors=$(replication_errors n0 | grep -c . || true)
  expected=$( [ ! -f "$OUT/logs/n0.expected" ] || grep -c . "$OUT/logs/n0.expected" || true)
  if [ "$errors" = "$want_errors" ] && [ "${expected:-0}" = "$want_expected" ]; then
    passed=$((passed + 1))
  else
    failed=$((failed + 1))
    echo "::error::$name: $errors errors and ${expected:-0} expected, not $want_errors and $want_expected"
  fi
}

case_ 178-outside-a-window       1 0     "$E178"
case_ 145-outside-a-window       1 0     "$E145"
case_ 178-in-a-window            0 1 '[' "$E178" ']'
case_ 179-in-a-window            0 1 '[' "$E179" ']'
case_ 145-in-a-window            0 1 '[' "$E145" ']'
case_ terminated-handshake       0 1 '[' "$W119" ']'
case_ terminated-handshake-error 1 0 '[' "$E119" ']'
case_ terminated-elsewhere       1 0 '[' "$W119CORE" ']'
case_ bad-certificate            1 0 '[' "$WBAD" ']'
case_ bad-certificate-elsewhere  1 0 '[' "$WBADCORE" ']'
case_ another-error-in-a-window  1 0 '[' "$E106" ']'
case_ ids-like-expected-ones     2 0 '[' "$E1450" "$E45" ']'
case_ an-id-in-the-message       1 0 '[' "$E106ID" ']'
case_ another-category           0 0 '[' "$CORE178" ']'
case_ a-notice                   0 0     "$N62"
case_ before-a-window            1 1     "$E178" '[' "$E178" ']'
case_ after-a-window             1 1 '[' "$N62" "$E178" ']' "$E178"
case_ two-windows                0 2 '[' "$E178" ']' "$N62" '[' "$E179" ']'
case_ between-two-windows        1 2 '[' "$E178" ']' "$E179" '[' "$E178" ']'
case_ mixed                      2 2 '[' "$E178" "$E106" "$W119" "$WBAD" "$N62" ']'
case_ 180-in-a-kill-window       0 1 "$KILL" "$E180" ']'
case_ 181-in-a-kill-window       0 1 "$KILL" "$E181" ']'
case_ 284-in-a-kill-window       0 1 "$KILL" "$E284" ']'
case_ handshakes-in-kill-window  0 2 "$KILL" "$E178" "$W119" ']'
case_ 181-in-another-window      1 0 '[' "$E181" ']'
case_ 181-outside-a-window       1 0     "$E181"
case_ 181-after-a-kill-window    1 1 "$KILL" "$E284" ']' "$E181"
case_ ids-like-listed-ones       2 0 "$KILL" "$E18" "$E80" ']'
case_ listed-id-in-the-message   1 0 "$KILL" "$E106KILL" ']'
case_ another-in-a-kill-window   1 0 "$KILL" "$E106" ']'
case_ listed-id-not-sync-error   2 0 "$KILL" "$WBAD181" "$WBADCORE181" ']'
case_ kill-window-then-another   1 2 "$KILL" "$E180" ']' "$N62" '[' "$E181" "$E145" ']'
case_ another-then-kill-window   0 2 '[' ']' "$N62" "$KILL" "$E181" "$E145" ']'

# DNS failures of only the named RS are expected, and only in its own window.
# Removing the host, message, ID or window checks must expose a failure here.
case_ killed-rs-dns              0 1 "$KILL" "$E51" ']'
case_ dns-before-after-window    2 1 "$E51" "$KILL" "$E51" ']' "$E51"
case_ dns-without-window         1 0 "$E51"
case_ dns-window-without-token   1 0 '[' "$E51" ']'
case_ dns-ds-crash               1 0 '[ 211' "$E51" ']'
case_ dns-combined-without-token 1 0 '[ 180 181 211 284' "$E51" ']'
case_ dns-combined-host          0 1 '[ dns:cv-c1 180 181 211 284' "${E51/cv-r0/cv-c1}" ']'
case_ dns-restarted-rs           0 1 "$RESTART" "$E51" ']'
case_ dns-combined-ds-restart    0 1 "$RESTART_COMBINED" "${E51/cv-r0/cv-c1}" ']'
case_ dns-ds-only-restart        1 0 "$RESTART_DS" "${E51/cv-r0/cv-d1}" ']'
case_ dns-other-host-ds-restart  1 0 "$RESTART_COMBINED" "${E51/cv-r0/cv-c2}" ']'
case_ dns-before-after-ds-restart 2 1 "${E51/cv-r0/cv-c1}" "$RESTART_COMBINED" \
                                   "${E51/cv-r0/cv-c1}" ']' "${E51/cv-r0/cv-c1}"
case_ dns-other-host             1 0 "$KILL" "${E51/cv-r0/cv-r1}" ']'
case_ dns-host-prefix            1 0 "$KILL" "${E51/cv-r0/cv-r}" ']'
case_ dns-host-suffix            1 0 "$KILL" "${E51/cv-r0/cv-r01}" ']'
case_ dns-other-id               1 0 "$KILL" "${E51/msgID=51/msgID=151}" ']'
case_ dns-other-message          1 0 "$KILL" "${E51/msg=The hostname/msg=Unexpected hostname}" ']'
case_ dns-message-suffix         1 0 "$KILL" "$E51 extra" ']'
case_ dns-embedded-message       1 0 "$KILL" "${E51/msg=The hostname/msg=Unexpected DNS failure msg=The hostname}" ']'
case_ dns-next-window            1 1 "$KILL" "$E51" ']' '[' "$E51" ']'
case_ dns-window-hosts           2 2 "$KILL" "$E51" "${E51/cv-r0/cv-r1}" ']' \
                                   '[ dns:cv-r1' "$E51" "${E51/cv-r0/cv-r1}" ']'
case_ monitors-in-rs-kill        2 0 "$KILL" "$E66" "$E108" ']'

# DS crash allowances do not leak into graceful restarts, later records, or RS-only kills.
case_ ds-kill-disconnect         0 1 '[ 211' "$E211" ']'
case_ ds-kill-without-rs         2 0 '[ 211' "$E180" "$E284" ']'
case_ combined-kill              0 4 '[ dns:cv-c1 180 181 211 284' "$E180" "$E181" "$E211" "$E284" ']'
case_ ds-disconnect-graceful     1 0 '[' "$E211" ']'
case_ 180-in-rs-restart          0 1 "$RESTART" "$E180" ']'
case_ 180-before-after-restart   2 1 "$E180" "$RESTART" "$E180" ']' "$E180"
case_ 180-in-ds-restart          1 0 "$RESTART_DS" "$E180" ']'
case_ combined-ds-restart-strict 6 0 "$RESTART_COMBINED" "$E180" "$E181" "$E211" "$E284" "$E66" "$E108" ']'
case_ rs-restart-strict          5 0 "$RESTART" "$E181" "$E211" "$E284" "$E66" "$E108" ']'
case_ ds-disconnect-rs-kill      1 0 "$KILL" "$E211" ']'
case_ ds-disconnect-before-after 2 1 "$E211" '[ 211' "$E211" ']' "$E211"
case_ monitors-in-combined-kill  2 0 '[ dns:cv-c1 180 181 211 284' "$E66" "$E108" ']'

# Only the restarted DS may temporarily reject the new RS certificate during its window.
WBAD_RS='[t] category=SYNC severity=WARNING msgID=119 msg=Directory server DS(1) encountered an unexpected error while connecting to replication server cv-r1:8989 for domain "dc=example,dc=com": SSLHandshakeException((bad_certificate) PKIX path validation failed)'
case_ new-cert-exact            0 1 '[ cert:n0:cv-r1' "$WBAD_RS" ']'
case_ new-cert-wrong-rs         1 0 '[ cert:n0:cv-r0' "$WBAD_RS" ']'
case_ new-cert-wrong-ds         1 0 '[ cert:n1:cv-r1' "$WBAD_RS" ']'
case_ new-cert-outside          2 1 "$WBAD_RS" '[ cert:n0:cv-r1' "$WBAD_RS" ']' "$WBAD_RS"
case_ new-cert-unknown          1 0 '[ cert:n0:cv-r1' "${WBAD_RS/bad_certificate/other_error}" ']'
case_ new-cert-other-category   1 0 '[ cert:n0:cv-r1' "${WBAD_RS/category=SYNC/category=CORE}" ']'
# The RS also reports the rejected handshake, identifying the remote DS by IP and port.
WBAD_PEER='[t] category=SYNC severity=WARNING msgID=105 msg=Replication server accepted a connection from /172.18.0.9:43210 to local address /172.18.0.2:8989 but the SSL handshake failed. The error was: Received fatal alert: bad_certificate'
case_ new-cert-rs-side          0 1 '[ cert-peer:n0:172.18.0.9' "$WBAD_PEER" ']'
case_ new-cert-rs-jdk-prefix    0 1 '[ cert-peer:n0:172.18.0.9' "${WBAD_PEER/The error was: Received/The error was: (bad_certificate) Received}" ']'
case_ new-cert-rs-other-ip      1 0 '[ cert-peer:n0:172.18.0.8' "$WBAD_PEER" ']'
case_ new-cert-rs-other-node    1 0 '[ cert-peer:n1:172.18.0.9' "$WBAD_PEER" ']'
case_ new-cert-rs-outside       1 0 "$WBAD_PEER" '[ cert-peer:n0:172.18.0.9' ']'
case_ new-cert-rs-other-fault   1 0 '[ cert-peer:n0:172.18.0.9' "${WBAD_PEER/Received fatal alert: bad_certificate/unexpected bad_certificate}" ']'
case_ startup-timeout-exact     0 1 '[ timeout:n0:cv-r1' "$E212" ']'
case_ startup-timeout-other-ds  1 0 '[ timeout:n1:cv-r1' "$E212" ']'
case_ startup-timeout-other-rs  1 0 '[ timeout:n0:cv-r0' "$E212" ']'
case_ startup-timeout-domain    1 0 '[ timeout:n0:cv-r1' "${E212/dc=example,dc=com/cn=schema}" ']'
case_ startup-timeout-outside   1 0 "$E212" '[ timeout:n0:cv-r1' ']'

# A long DNS-only window must not also hide the normally expected handshake errors.
case_ dns-only-name           0 1 '[ dns-only dns:cv-r0' "$E51" ']'
case_ dns-only-handshake      1 0 '[ dns-only dns:cv-r0' "$E178" ']'

# Real long DNS window overlapping no ordinary restart window.
rm -rf "$OUT/logs"
printf '%s\n' "$E106" >"$LOG"
mark_logs
dns_window_open cv-r0
printf '%s\n' "$E51" "$E178" >>"$LOG"
dns_window_close
errors=$(replication_errors n0 | grep -c . || true)
expected=$(grep -c . "$OUT/logs/n0.expected" || true)
if [ "$errors" = 1 ] && [ "$expected" = 1 ]; then
  passed=$((passed + 1))
else
  failed=$((failed + 1)); echo '::error::DNS-only window accepted unrelated handshake or missed DNS error'
fi

# A log that cannot be read stops window_open, which would otherwise open a window from its first
# line. In the background: errexit is off in a subshell on the left of ||, and on in a job
code=0
rm -f "$LOG"
(window_open 180 181 284) 2>/dev/null &
wait $! || code=$?
if [ "$code" -ne 0 ]; then
  passed=$((passed + 1))
else
  failed=$((failed + 1))
  echo "::error::window_open went on over an errors log that cannot be read"
fi

# The same failure must be fatal when the caller handles the return code itself: Bash
# disables errexit throughout a function called from an if/|| condition.
if window_open 180 >/dev/null 2>&1; then
  failed=$((failed + 1)); echo '::error::conditional window_open accepted an unreadable log'
else
  passed=$((passed + 1))
fi
printf '0 180\n' >"$OUT/logs/n0.window"
if window_close >/dev/null 2>&1; then
  failed=$((failed + 1)); echo '::error::conditional window_close accepted an unreadable log'
else
  passed=$((passed + 1))
fi

# A replaced server has a new errors file. Preserve the old file as a prefix so marks and
# window boundaries cover records on both volumes without accepting errors outside the window.
rm -rf "$OUT/logs"
printf '%s\n' "$E178" "$E106" >"$LOG"
mark_logs
printf '%s\n' "$E106" >>"$LOG"
window_open 180
printf '%s\n' "$E180" >>"$LOG"
cp "$LOG" "$OUT/logs/n0.previous"
printf '%s\n' "$E180" >"$LOG"
window_close
printf '%s\n' "$E106" >>"$LOG"
errors=$(replication_errors n0 | grep -c . || true)
expected=$(grep -c . "$OUT/logs/n0.expected" || true)
if [ "$errors" = 2 ] && [ "$expected" = 2 ]; then
  passed=$((passed + 1))
else
  failed=$((failed + 1))
  echo "::error::replacement logs: $errors errors and $expected expected, wanted 2 each"
fi

# While a scaled-down container is absent, its captured line count keeps later windows
# aligned; a different unreadable node still fails.
printf '7\n' >"$OUT/logs/n0.offline"
rm -f "$LOG"
if [ "$(errors_lines n0 2>/dev/null || :)" = 7 ]; then
  passed=$((passed + 1))
else
  failed=$((failed + 1)); echo '::error::offline node lost its captured line count'
fi
rm "$OUT/logs/n0.offline"
if errors_lines n0 >/dev/null 2>&1; then
  failed=$((failed + 1)); echo '::error::unreadable online log was accepted'
else
  passed=$((passed + 1))
fi

# Overlapping transitions retain separate bounds and exact hostname allowances.
rm -rf "$OUT/logs"
printf '%s\n' "$N62" >"$LOG"
mark_logs
window_open_key first dns:first
printf '%s\n' "$E178" >>"$LOG"
window_open_key second dns:second
printf '%s\n' "$E180" >>"$LOG"
window_close_key first
printf '%s\n' "$E179" >>"$LOG"
window_close_key second
printf '%s\n' "$E178" >>"$LOG"
errors=$(replication_errors n0 | grep -c . || true)
expected=$(grep -c . "$OUT/logs/n0.expected" || true)
[ "$errors" = 2 ] && [ "$expected" = 2 ] || { echo "overlapping windows misclassified records: $errors/$expected" >&2; exit 1; }
[ ! -e "$OUT/logs/n0.window-first" ] && [ ! -e "$OUT/logs/n0.window-second" ] || { echo 'overlapping window left open' >&2; exit 1; }

# DNS allowances stay scoped to their own overlapping node windows.
rm -rf "$OUT/logs"
printf '%s\n' "$N62" >"$LOG"
mark_logs
E51R1=${E51//cv-r0/cv-r1}
window_open_key first dns:cv-r0
printf '%s\n' "$E51" >>"$LOG"
window_open_key second dns:cv-r1
printf '%s\n' "$E51R1" >>"$LOG"
window_close_key first
printf '%s\n' "$E51" "$E51R1" >>"$LOG"
window_close_key second
printf '%s\n' "$E51R1" >>"$LOG"
errors=$(replication_errors n0 | grep -c . || true)
expected=$(grep -c . "$OUT/logs/n0.expected" || true)
[ "$errors" = 2 ] && [ "$expected" = 3 ] || { echo "overlapping DNS windows misclassified records: $errors/$expected" >&2; exit 1; }

D180=${E180/cv-r0/172.18.0.8}
case_ scoped-failover 0 1 '[ disconnect:n0:172.18.0.8' "$D180" ']'
case_ failover-outside-window 2 1 "$D180" '[ disconnect:n0:172.18.0.8' "$D180" ']' "$D180"
case_ failover-wrong-node 1 0 '[ disconnect:n1:172.18.0.8' "$D180" ']'
case_ failover-wrong-peer 1 0 '[ disconnect:n0:172.18.0.9' "$D180" ']'
case_ failover-wrong-domain 1 0 '[ disconnect:n0:172.18.0.8' "${D180/dc=example,dc=com/cn=schema}" ']'
case_ failover-wrong-id 1 0 '[ disconnect:n0:172.18.0.8' "${D180/msgID=180 /msgID=1800 }" ']'

echo "replication_errors: $passed cases passed, $failed failed"
[ "$failed" -eq 0 ]
