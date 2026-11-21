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
SOURCE="$HERE/../../../opendj-packages/opendj-docker/bootstrap/join.sh"
sed -n '/^reset_replication() {/,/^}/p' "$SOURCE" >"$OUT/reset.sh"
sed -n '/^reset_if_unregistered() {/,/^}/p' "$SOURCE" >>"$OUT/reset.sh"
. "$OUT/reset.sh"
ROLE=combined ROOT_USER_DN='cn=Directory Manager' MYHOSTNAME=d1 ADMIN_PORT=4444
REPLICATION_ATTEMPT_TIMEOUT=120 PASSWORD_FILE="$OUT/password"
REJOIN_PENDING="$OUT/rejoin" BOOTSTRAP_COMPLETE="$OUT/ready" RESET_PENDING="$OUT/reset-pending"
publish_state() { printf '%s\n' "$1" >"$OUT/state"; }
take_join_lock() { JOIN_LOCK_VALUE=ours; }
release_join_lock() { printf '%s\n' "$*" >>"$OUT/releases"; return "${RELEASE_RC:-0}"; }
hold_writes() { touch "$OUT/read-only"; }
registrations() { :; }
bounded() {
  printf '%s\n' "$@" >"$OUT/disable-arguments"
  return "${DISABLE_RC:-0}"
}
search() { printf '%s' "${REMAINING_CONFIG:-}"; return "${SEARCH_RC:-0}"; }
assert_reset_fails() {
  local rc=0
  touch "$BOOTSTRAP_COMPLETE"
  : >"$OUT/releases"
  reset_replication pending fixture >"$OUT/result" || rc=$?
  [ "$rc" -ne 0 ] || { echo 'failed reset reported success' >&2; exit 1; }
  [ -f "$REJOIN_PENDING" ] && [ -f "$OUT/read-only" ] && [ ! -f "$BOOTSTRAP_COMPLETE" ]
  [ "$(cat "$OUT/releases")" = 'localhost ours' ] || { echo 'reset did not release its lock' >&2; exit 1; }
}
DISABLE_RC=8
assert_reset_fails
DISABLE_RC=0 REMAINING_CONFIG=$'dn: cn=admin data,cn=domains,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config\n'
assert_reset_fails
REMAINING_CONFIG= SEARCH_RC=81
assert_reset_fails
SEARCH_RC=0 RELEASE_RC=1
assert_reset_fails
RELEASE_RC=0
reset_replication pending fixture >/dev/null
# The supported local bind must work before a replicated global admin exists.
grep -Fx -- --bindDN "$OUT/disable-arguments" >/dev/null
grep -Fx -- "$ROOT_USER_DN" "$OUT/disable-arguments" >/dev/null
! grep -Fx -- --adminUID "$OUT/disable-arguments" >/dev/null
[ ! -f "$BOOTSTRAP_COMPLETE" ] && [ -f "$OUT/read-only" ]
# The caller must not enable after a failed reset, on either membership road.
holds_own_part() { return 0; }
registered_with_peers() { return "${PEER_RC:-0}"; }
registered_here() { return 1; }
DISABLE_RC=8
for PEER_RC in 0 1; do
  if reset_if_unregistered pending >"$OUT/result"; then
    echo 'failed reset admitted enable' >&2; exit 1
  fi
done
DISABLE_RC=0
reset_if_unregistered pending >/dev/null
reset_if_unregistered pending >/dev/null
# A failed reset may already have removed BASE_DN. The next round must finish
# that cleanup rather than concluding there is no replication left to reset.
RESET_PENDING="$OUT/reset-pending"
DISABLE_RC=11
assert_reset_fails
holds_own_part() { return 1; }
if reset_if_unregistered pending >"$OUT/result"; then
  echo 'partial reset was forgotten after its data domain disappeared' >&2; exit 1
fi
DISABLE_RC=0
reset_if_unregistered pending >/dev/null
[ ! -f "$RESET_PENDING" ] || { echo 'completed reset stayed pending' >&2; exit 1; }
echo 'join reset preserves errors, authenticates locally, verifies cleanup and keeps writes gated'

# The actual background-join wrapper publishes a terminal result while preserving
# the child status. Stub only the external child executable.
sed -n '/^run_join() {/,/^}/p' "$HERE/../../../opendj-packages/opendj-docker/run.sh" >"$OUT/run-join.sh"
. "$OUT/run-join.sh"
function /opt/opendj/bootstrap/join.sh { return "$JOIN_RESULT"; }
BOOTSTRAP_FAILED="$OUT/bootstrap-failed" BOOTSTRAP_FINISHED="$OUT/bootstrap-finished" JOIN_RESULT=8
rc=0; run_join || rc=$?
[ "$rc" -eq 8 ] || { echo "failed join returned $rc, expected 8" >&2; exit 1; }
[ "$(cat "$BOOTSTRAP_FAILED")" = join:8 ] || { echo 'failed join did not publish join:8' >&2; exit 1; }
[ ! -f "$BOOTSTRAP_FINISHED" ] || { echo 'failed join published completion' >&2; exit 1; }
rm "$BOOTSTRAP_FAILED"
JOIN_RESULT=0
run_join
[ -f "$BOOTSTRAP_FINISHED" ] || { echo 'successful join did not publish completion' >&2; exit 1; }
[ ! -f "$BOOTSTRAP_FAILED" ]
echo 'background join preserves its exit status and publishes failure'

# Exercise the production release helper too: a diagnostic echo must not turn a
# failed LDAP delete into success and erase the persistent reset marker.
sed -n '/^release_join_lock() {/,/^}/p' "$SOURCE" >"$OUT/release.sh"
. "$OUT/release.sh"
JOIN_LOCK_DN='cn=join-lock,cn=config' JOIN_LOCK_TTL=120
ldapmodify_on() { cat >"$OUT/lock-delete.ldif"; return "$LOCK_DELETE_RC"; }
LOCK_DELETE_RC=8
rc=0; reset_replication pending fixture >"$OUT/result" || rc=$?
[ "$rc" -ne 0 ] || { echo 'failed real lock release reported success' >&2; exit 1; }
[ -f "$RESET_PENDING" ] || { echo 'failed real lock release erased reset marker' >&2; exit 1; }
LOCK_DELETE_RC=0
reset_if_unregistered pending >/dev/null
[ ! -f "$RESET_PENDING" ]
echo 'real lock release preserves LDAP failure and permits a later successful retry'
