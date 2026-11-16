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
# Execute the fixture's actual wait functions with only Docker and time replaced.
for function in fail wait_until container_running wait_running; do
  sed -n "/^$function() {/,/^}/p" "$HERE/docker-test-replication.sh"
done >"$OUT/functions.sh"
. "$OUT/functions.sh"
sleep() { SECONDS=$((SECONDS + 2)); }
docker() {
  [ "$1" = inspect ] || return 1
  [ "${INSPECT_RC:-0}" -eq 0 ] || return "$INSPECT_RC"
  case "$*" in *'.State.Status'*) echo "$STATE";; *) echo '{"Status":"exited","ExitCode":137,"OOMKilled":true}';; esac
}
calls=0
pending() { calls=$((calls + 1)); return 1; }
for STATE in exited dead; do
  calls=0
  if wait_running node 300 'node rejoins' pending >"$OUT/result" 2>&1; then
    echo 'stopped container accepted' >&2; exit 1
  fi
  [ "$calls" -eq 0 ] || { echo 'stopped container was retried' >&2; exit 1; }
  grep -F 'ExitCode' "$OUT/result" >/dev/null || { cat "$OUT/result"; echo 'exit state not reported' >&2; exit 1; }
done
# A ready old log cannot make an exited container pass either.
if wait_running node 300 'old log matches' true >"$OUT/result" 2>&1; then
  echo 'old log hid a stopped container' >&2; exit 1
fi
STATE=running
wait_running node 300 'running node is ready' true
# Missing/created/restarting containers and Docker transport errors are retryable,
# but must never let a successful predicate through before running is verified.
for STATE in created restarting paused; do
  if wait_running node 0 'node starts' true >"$OUT/result" 2>&1; then exit 1; fi
  grep -F 'timed out' "$OUT/result" >/dev/null
done
if INSPECT_RC=1 wait_running node 0 'Docker recovers' true >"$OUT/result" 2>&1; then exit 1; fi
grep -F 'timed out' "$OUT/result" >/dev/null
# Deliberately stopped peers are outside this wait's explicit target set.
STATE=exited
wait_until 0 'a check which needs no running container' true
calls=0
if wait_until 0 'a false predicate' pending >"$OUT/result" 2>&1; then exit 1; fi
[ "$calls" -eq 1 ] || { echo 'wait continued past its deadline' >&2; exit 1; }
echo 'Docker fixture waits stop on exited targets, retry transport failures and respect their deadline'
