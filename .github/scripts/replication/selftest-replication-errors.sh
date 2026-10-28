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
# window expects. A green row does not show it - nothing a disturbance expects is logged outside
# its window there, and the base rows have no window - so docker-convergence runs this first.
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
WBAD='[t] category=SYNC severity=WARNING msgID=119 msg=Directory server DS(1) encountered an unexpected error while connecting: SSLHandshakeException(Received fatal alert: bad_certificate)'
WBADCORE='[t] category=CORE severity=WARNING msgID=1 msg=SSLException(Received fatal alert: bad_certificate)'
E106='[t] category=SYNC severity=ERROR msgID=106 msg=another replication error'
E1450='[t] category=SYNC severity=ERROR msgID=1450 msg=an id that starts like one a window expects'
E45='[t] category=SYNC severity=ERROR msgID=45 msg=an id that ends like one a window expects'
N62='[t] category=SYNC severity=NOTICE msgID=62 msg=Directory server DS(1) has connected'
CORE178='[t] category=CORE severity=ERROR msgID=178 msg=not a replication error'

passed=0 failed=0
# case <name> <errors> <expected> <record, [ or ]>...: the errors log holds two errors before
# mark_logs, which are not the run's, then the records given; [ opens a window and ] closes it.
# The number of records replication_errors prints, and of those it sends to logs/n0.expected
case_() {
  local name=$1 want_errors=$2 want_expected=$3 record errors expected
  shift 3
  rm -rf "$OUT/logs"
  printf '%s\n' "$E178" "$E106" >"$LOG"
  mark_logs
  for record; do
    case $record in
      '[') window_open ;;
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
case_ bad-certificate            1 0 '[' "$WBAD" ']'
case_ bad-certificate-elsewhere  1 0 '[' "$WBADCORE" ']'
case_ another-error-in-a-window  1 0 '[' "$E106" ']'
case_ ids-like-expected-ones     2 0 '[' "$E1450" "$E45" ']'
case_ another-category           0 0 '[' "$CORE178" ']'
case_ a-notice                   0 0     "$N62"
case_ before-a-window            1 1     "$E178" '[' "$E178" ']'
case_ after-a-window             1 1 '[' "$N62" "$E178" ']' "$E178"
case_ two-windows                0 2 '[' "$E178" ']' "$N62" '[' "$E179" ']'
case_ between-two-windows        1 2 '[' "$E178" ']' "$E179" '[' "$E178" ']'
case_ mixed                      2 2 '[' "$E178" "$E106" "$W119" "$WBAD" "$N62" ']'

echo "replication_errors: $passed cases passed, $failed failed"
[ "$failed" -eq 0 ]
