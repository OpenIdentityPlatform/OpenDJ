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
. "$HERE/lib.sh"
sleep() { :; }
calls=0
late_success() { calls=$((calls + 1)); [ "$calls" -ge 3 ]; }
# A caller may use wait_until in a condition, disabling errexit for the whole function.
# It must still stop at its deadline, even if the predicate would later succeed.
if wait_until 0 'a late predicate' late_success >/dev/null; then
  echo 'wait_until accepted a predicate after its deadline' >&2
  exit 1
fi
[ "$calls" -eq 1 ] || { echo 'wait_until kept trying after timeout' >&2; exit 1; }
. "$HERE/monitor.sh"
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
DS_NODES=d0 RS_NODES=r0 QUIESCE_TIMEOUT=100 MONITORING_PERIOD=0
sample() {
  local count=0
  [ ! -f "$OUT/count" ] || count=$(cat "$OUT/count")
  count=$((count + 1)); echo "$count" >"$OUT/count"
  if [ "$count" -eq 1 ]; then echo '100 r0 123 42 3'; else echo '101 r0 123 0 0'; fi
}
sleep() { SECONDS=$((SECONDS + 5)); }
wait_converged
grep -Fx '100 r0 123 42 3' "$OUT/convergence-samples.txt" >/dev/null \
  || { echo 'missing nonzero convergence sample' >&2; exit 1; }
grep -Fx '101 r0 123 0 0' "$OUT/convergence-samples.txt" >/dev/null \
  || { echo 'missing converged sample' >&2; exit 1; }
echo 'wait deadline passed'
calls=0
incomplete_checkpoint() { calls=$((calls + 1)); [ "$calls" -ge 2 ] || return 2; }
wait_until 100 'a checkpoint which is not complete yet' incomplete_checkpoint >"$OUT/retryable" \
  || { echo 'ordinary predicate status 2 aborted a retryable wait' >&2; exit 1; }
[ "$calls" -eq 2 ]
# Status 200 is a terminal predicate failure, not a state which more waiting fixes.
calls=0
terminal_failure() { calls=$((calls + 1)); return 200; }
if wait_until 100 'a failed bootstrap' terminal_failure >"$OUT/terminal"; then
  echo 'terminal bootstrap failure accepted' >&2; exit 1
fi
[ "$calls" -eq 1 ] || { echo 'terminal bootstrap failure retried until timeout' >&2; exit 1; }
# Run the predicate shell against real marker files; only Docker transport is replaced.
docker() {
  case $1 in
    inspect) echo healthy; return "${INSPECT_RC:-0}" ;;
    exec) [ "${EXEC_RC:-0}" -eq 0 ] || return "$EXEC_RC"; shift 2; BOOTSTRAP_FAILED="$OUT/bootstrap-failed" "$@" ;;
    *) return 1 ;;
  esac
}
is_healthy node
printf 'join:8\n' >"$OUT/bootstrap-failed"
rc=0; is_healthy node || rc=$?
[ "$rc" -eq 200 ] || { echo 'failed join counted as healthy' >&2; exit 1; }
rc=0; bootstrap_ended node || rc=$?
[ "$rc" -eq 200 ] || { echo 'failed join counted as successfully ended' >&2; exit 1; }
rm "$OUT/bootstrap-failed"
for code in 1 2 124 137; do
  if EXEC_RC=$code is_healthy node; then
    echo 'failed Docker marker read counted as healthy' >&2; exit 1
  fi
done
if INSPECT_RC=1 is_healthy node; then
  echo 'failed Docker inspect counted as healthy' >&2; exit 1
fi
echo 'terminal bootstrap failures abort waits immediately'
