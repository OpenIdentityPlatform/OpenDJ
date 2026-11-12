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
HELPER="$HERE/../../../opendj-packages/opendj-docker/bootstrap/join-batch.sh"
[ -f "$HELPER" ] || { echo 'missing join batch helper' >&2; exit 1; }
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
. "$HELPER"
dsconfig_local() { [ "$1" = --batch ] || return 1; cat >"$OUT/batch"; return "${BATCH_RESULT:-0}"; }
change_replication_servers() { printf '%s\n' "$*" >>"$OUT/individual"; [ "${INDIVIDUAL_FAIL:-}" != "$2" ]; }
join_batch_changes domain 'cn=a b' --remove "rs'o:8989" server ignored --remove rs2:8989
[ ! -e "$OUT/individual" ] || { echo 'successful batch fell back to individual commands' >&2; exit 1; }
python3 - "$OUT/batch" <<'PY'
import pathlib,shlex,sys
lines=[line for line in pathlib.Path(sys.argv[1]).read_text().splitlines() if line.strip()]
a,b=map(shlex.split,lines)
assert a==['set-replication-domain-prop','--provider-name','Multimaster Synchronization','--domain-name','cn=a b','--remove',"replication-server:rs'o:8989"],a
assert b==['set-replication-server-prop','--provider-name','Multimaster Synchronization','--remove','replication-server:rs2:8989'],b
PY
BATCH_RESULT=1 INDIVIDUAL_FAIL='cn=a b'
join_batch_changes domain 'cn=a b' --remove rs1:8989 domain cn=other --remove rs2:8989
[ "$(wc -l <"$OUT/individual" | tr -d ' ')" = 2 ] || { echo 'batch failure did not attempt every individual command' >&2; exit 1; }
grep -F 'domain cn=other --remove rs2:8989' "$OUT/individual" >/dev/null || exit 1
rm -f "$OUT/batch" "$OUT/individual"
BATCH_RESULT=0 INDIVIDUAL_FAIL=
join_batch_changes domain $'unsafe\nname' --remove rs1:8989 server ignored --remove rs2:8989
[ ! -e "$OUT/batch" ] || { echo 'newline allowed in batch command' >&2; exit 1; }
grep -F 'server ignored --remove rs2:8989' "$OUT/individual" >/dev/null || exit 1
# Exercise the production cleanup function, including collecting multiple removals.
sed -n '/^cleanup_departed() {/,/^}/p' "$HERE/../../../opendj-packages/opendj-docker/bootstrap/join.sh" >"$OUT/cleanup.sh"
. "$OUT/cleanup.sh"
PEERS_ARE_EXPLICIT=yes
registrations() { :; }
replication_server_lists() { printf 'domain\tcn=one\trs1:8989\nserver\tprovider\trs1:8989\n'; }
is_address() { return 1; }; in_peers() { return 1; }; is_self() { return 1; }
rm -f "$OUT/batch" "$OUT/individual"
cleanup_departed >/dev/null
[ "$(grep -c '^set-replication-' "$OUT/batch")" -eq 2 ] || { echo 'cleanup did not batch both removals' >&2; exit 1; }
[ ! -e "$OUT/individual" ] || { echo 'cleanup used separate commands after batch success' >&2; exit 1; }
# Exercise the production repair function, including collecting additions for distinct lists.
sed -n '/^repair_replication_servers() {/,/^}/p' "$HERE/../../../opendj-packages/opendj-docker/bootstrap/join.sh" >"$OUT/repair.sh"
. "$OUT/repair.sh"
PEERS=(rs1); REPLICATION_RETRY_COUNT=1 REPLICATION_RETRY_INTERVAL=0 REPLICATION_PORT=8989
registered_hosts() { echo query >>"$OUT/host-queries"; echo rs1; }
replication_server_lists() { printf 'domain\tcn=one\nserver\tprovider\n'; }
in_peers() { return 0; }; names_match() { [ "$1" = "$2" ]; }; lower() { printf '%s' "$1"; }
lacked_by_a_list() { return 0; }; list_holds() { return 1; }; replication_config() { echo server; }
rm -f "$OUT/batch" "$OUT/individual"
repair_replication_servers >/dev/null
[ "$(grep -c '^set-replication-' "$OUT/batch")" -eq 2 ] || { echo 'repair did not batch both additions' >&2; exit 1; }
[ ! -e "$OUT/individual" ] || { echo 'repair used separate commands after batch success' >&2; exit 1; }
[ "$(wc -l <"$OUT/host-queries" | tr -d ' ')" = 1 ] || { echo 'repair repeated a successful registration search' >&2; exit 1; }
# If retries expire with a peer still missing, refresh once: it may register just
# after the last wait, and its replication-server list entry must not be missed.
rm -f "$OUT/host-queries" "$OUT/batch" "$OUT/individual"
PEERS=(rs1 rs2)
registered_hosts() {
  echo query >>"$OUT/host-queries"
  echo rs1
  [ "$(wc -l <"$OUT/host-queries" | tr -d ' ')" -lt 2 ] || echo rs2
}
repair_replication_servers >/dev/null
[ "$(wc -l <"$OUT/host-queries" | tr -d ' ')" = 2 ] || { echo 'repair did not refresh after the last wait' >&2; exit 1; }
grep -F 'replication-server:rs2:8989' "$OUT/batch" >/dev/null || { echo 'repair missed a peer registered after the last wait' >&2; exit 1; }
# A successful reset check already established both parts of membership. Reuse it
# once, but never reuse a failed, unknown, or reset result on the next check.
sed -n '/^reset_if_unregistered() {/,/^}/p' "$HERE/../../../opendj-packages/opendj-docker/bootstrap/join.sh" >"$OUT/reset.sh"
sed -n '/^is_member() {/,/^}/p' "$HERE/../../../opendj-packages/opendj-docker/bootstrap/join.sh" >"$OUT/member.sh"
. "$OUT/reset.sh"; . "$OUT/member.sh"
holds_own_part() { echo own >>"$OUT/member-queries"; return "${OWN_RESULT:-0}"; }
registered_with_peers() { return "${PEERS_RESULT:-0}"; }
registered_here() { echo here >>"$OUT/member-queries"; return "${HERE_RESULT:-0}"; }
registered_hosts() { echo hosts >>"$OUT/member-queries"; echo self; }
is_self() { [ "$1" = self ]; }
reset_replication() { echo reset >>"$OUT/member-queries"; }
reset_if_unregistered pending
is_member || { echo 'confirmed membership rejected' >&2; exit 1; }
[ "$(wc -l <"$OUT/member-queries" | tr -d ' ')" = 2 ] || { echo 'confirmed membership was queried twice' >&2; exit 1; }
is_member || { echo 'one-use membership was not refreshed' >&2; exit 1; }
grep -Fx hosts "$OUT/member-queries" >/dev/null || { echo 'membership cache was used more than once' >&2; exit 1; }
HERE_RESULT=2
reset_if_unregistered pending
is_member || { echo 'unknown local registration was not rechecked' >&2; exit 1; }
HERE_RESULT=1
reset_if_unregistered pending
[ "$(tail -1 "$OUT/member-queries")" = reset ] || { echo 'missing registration did not trigger reset' >&2; exit 1; }
is_member || { echo 'membership after reset was not rechecked' >&2; exit 1; }
echo 'join batch quoting and failure fallback passed'
