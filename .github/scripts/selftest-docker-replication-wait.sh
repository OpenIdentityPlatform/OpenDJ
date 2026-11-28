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

# Exercise the real restart assertions with the log from before and after a restart.
for function in logs_have_since assert_no_replication_reset; do
  sed -n "/^$function() {/,/^}/p" "$HERE/docker-test-replication.sh"
done | sed 's/^assert_no_replication_reset()/check_reset_in_logs()/' >"$OUT/log-functions.sh"
sed -n '/^phase "roles: restarts"$/,/^roles_hold$/p' "$HERE/docker-test-replication.sh" >"$OUT/separate.sh"
sed -n '/^phase "restart of a member"$/,/^phase "seed without a domain"$/p' \
  "$HERE/docker-test-replication.sh" >"$OUT/combined.sh"
run_restart_case() {
  bash -e -o pipefail -c '
    OUT=$1
    topology=$2
    log_status=$3
    . "$OUT/functions.sh"
    . "$OUT/log-functions.sh"
    phase() { :; }
    is_healthy() { :; }
    wait_healthy() { :; }
    roles_hold() { :; }
    recorded_role_of() { echo combined; }
    wait_has() { :; }
    add_ou() { :; }
    assert_no_replication_reset() {
      if [ "$topology/$1" = combined/dj-0 ]; then
        [ -f "$OUT/dj-0-member" ] || fail "checked dj-0 for a reset before it rejoined"
        touch "$OUT/dj-0-reset-checked"
      elif [ "$log_status" -ne 0 ]; then
        touch "$OUT/reset-check-$1"
      fi
      check_reset_in_logs "$@"
    }
    sleep() {
      SECONDS=$((SECONDS + 2))
      if [ "$topology" = combined ] && [ ! -f "$OUT/dj-0-member" ]; then
        echo "join: already a member of the replication topology" >>"$OUT/dj-0.log"
        touch "$OUT/dj-0-member"
      fi
    }
    docker() {
      case "$1" in
        exec|restart|start|stop) : ;;
        logs)
          if [ "$topology" = combined ]; then
            cat "$OUT/$2.log"
          elif [ "$log_status" -ne 0 ] && [ -f "$OUT/reset-check-$2" ]; then
            echo "Docker transport error" >&2
            return "$log_status"
          else
            cat "$OUT/container.log"
          fi
          ;;
        *) return 1 ;;
      esac
    }
    . "$OUT/$topology.sh"
  ' restart "$OUT" "$1" "${2:-0}"
}
cat >"$OUT/container.log" <<'LOG'
Starting OpenDJ
join: taking its replication configuration down to enable it anew
Starting OpenDJ
join: already a member of the replication topology
LOG
run_restart_case separate
rc=0
run_restart_case separate 1 >"$OUT/result" 2>&1 || rc=$?
[ "$rc" -ne 0 ] || { echo 'restart accepted an unreadable Docker log' >&2; exit 1; }
grep -F 'could not read the current startup log for dj-r0' "$OUT/result" >/dev/null

echo 'join: taking its replication configuration down to enable it anew' >>"$OUT/container.log"
rc=0
run_restart_case separate >"$OUT/result" 2>&1 || rc=$?
[ "$rc" -ne 0 ]
grep -F 'dj-r0 reset its replication on a plain restart' "$OUT/result" >/dev/null
echo 'Docker fixture reports resets and unreadable logs from the current start'

cat >"$OUT/dj-0.log" <<'LOG'
Starting OpenDJ
join: already a member of the replication topology
Starting OpenDJ
LOG
sed '$d' "$OUT/container.log" >"$OUT/dj-1.log"
run_restart_case combined
[ -f "$OUT/dj-0-reset-checked" ] || { echo "combined restart did not check dj-0 for a reset" >&2; exit 1; }
echo 'Docker fixture waits for current membership of the restarted combined seed'
