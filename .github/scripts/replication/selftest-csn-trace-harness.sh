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
NETWORK=test ROOT_PASSWORD=password SERVER_JAVA_ARGS=-Xmx1g IMAGE=test PEERS=d0,r0 DISTURBANCE=empty-rs
CSN_TRACE=1 DS_NODES=d0 RS_NODES=r0
. "$HERE/topology-up.sh"
. "$HERE/collect.sh"
docker() {
  printf '%s\n' "$*" >>"$OUT/docker-calls"
  [ "${FAIL_RUN:-0}" != 1 ] || [ "$1" != run ]
}
start_server d0 directory
token=$(cat "$OUT/csn-trace-tokens/d0")
grep -F -- "-Dorg.opends.server.replication.csnTraceToken=$token" "$OUT/docker-calls" >/dev/null
grep -Fx "d0 $token" "$OUT/csn-trace-expected.txt" >/dev/null
expect_csn_trace_restart d0
restart_token=$(tail -1 "$OUT/csn-trace-expected.txt" | awk '{print $2}')
[ "$restart_token" != "$token" ] || { echo 'restart reused the previous generation token' >&2; exit 1; }
grep -F "/opt/opendj/data/csn-trace-token-$token" "$OUT/docker-calls" >/dev/null \
  || { echo 'restart token was not copied to the stopped container' >&2; exit 1; }
start_server r0 replication
[ ! -f "$OUT/csn-trace-tokens/r0" ]
if (FAIL_RUN=1; start_server d1 directory); then
  echo 'a failed Docker start was accepted' >&2; exit 1
fi
[ ! -f "$OUT/csn-trace-tokens/d1" ]
mkdir -p "$OUT/changelog"
printf 'CSN allocation traces complete; local allocations may be uncommitted\n' >"$OUT/csn-trace-validation.txt"
cat >"$OUT/changelog/r0.txt" <<'GAPS'
replica 14800 (d0): 2 changes in the load, sequence numbers 18021 to 18023, 1 missing
  missing 18022, between the add 18021 (1) and the modify 18023 (2)
replica 14900: 2 changes in the load, sequence numbers 18031 to 18033, 1 missing
  missing 18032, between the add 18031 (1) and the modify 18033 (2)
GAPS
python3() { printf '%s\n' "$*" >"$OUT/diagnosis-call"; }
diagnose_csn_gaps
grep -F '14800:18022' "$OUT/diagnosis-call" >/dev/null || { echo 'gap was not looked up in the trace' >&2; exit 1; }
grep -F '14900:18032' "$OUT/diagnosis-call" >/dev/null || { echo 'unannotated gap was not looked up in the trace' >&2; exit 1; }
rm "$OUT/diagnosis-call"
printf 'incomplete\n' >"$OUT/csn-trace-validation.txt"
diagnose_csn_gaps
[ ! -e "$OUT/diagnosis-call" ] || { echo 'incomplete trace was used as evidence' >&2; exit 1; }
docker() { [ "$1" != cp ]; }
python3() { echo 'CSN allocation traces complete; local allocations may be uncommitted'; }
if check_csn_traces >/dev/null 2>&1; then
  echo 'failed trace copy was accepted' >&2; exit 1
fi
if grep -Fx 'CSN allocation traces complete; local allocations may be uncommitted' "$OUT/csn-trace-validation.txt" >/dev/null; then
  echo 'failed trace copy left a complete validation marker' >&2; exit 1
fi
DS_NODES='d0 d1'
docker() {
  case $1 in
    inspect) return 0 ;;
    stop) printf '%s\n' "$4" >>"$OUT/stopped" ;;
  esac
}
check_csn_traces() {
  [ "${KEEP:-0}" = 1 ] || [ "$(wc -l <"$OUT/stopped")" -eq 2 ] \
    || { echo 'traces checked before all servers stopped' >&2; return 1; }
  echo checked >>"$OUT/trace-checks"
}
KEEP=0
finalize_failure_csn_traces
[ "$(wc -l <"$OUT/stopped")" -eq 2 ] || { echo 'not all DS were stopped' >&2; exit 1; }
rm "$OUT/stopped"
KEEP=1
finalize_failure_csn_traces
[ ! -e "$OUT/stopped" ] || { echo 'KEEP=1 stopped a DS' >&2; exit 1; }
[ "$(wc -l <"$OUT/trace-checks")" -eq 2 ] || { echo 'traces were not checked' >&2; exit 1; }
echo 'CSN trace generations and Docker failures passed'
