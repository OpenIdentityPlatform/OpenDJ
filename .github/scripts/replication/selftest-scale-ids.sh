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
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
. "$HERE/collect.sh"
MONITOR_DOMAIN=monitor
admin_search() { [ "${BROKEN:-0}" = 0 ] || return 1; printf '%s\n' "$RESPONSE"; }
fail() { echo "$*" >&2; return 1; }
printf '1 d0\n2 d1\n' >"$OUT/server-ids.txt"
RESPONSE='dn: cn=Directory server DS(2) test'
record_rejoined_id d1 || exit 1
[ "$(wc -l <"$OUT/server-ids.txt")" -eq 2 ] || { echo 'same id appended twice' >&2; exit 1; }
RESPONSE='dn: cn=Directory server DS(3) test'
record_rejoined_id d1 || exit 1
grep -Fx '3 d1' "$OUT/server-ids.txt" >/dev/null
RESPONSE='dn: cn=Directory server DS(1) test'
if record_rejoined_id d1; then echo 'duplicate id of another node accepted' >&2; exit 1; fi
RESPONSE=''
if record_rejoined_id d1; then echo 'missing id accepted' >&2; exit 1; fi
RESPONSE=$'dn: cn=Directory server DS(4) test\ndn: cn=Directory server DS(5) test'
if record_rejoined_id d1; then echo 'multiple ids accepted' >&2; exit 1; fi
BROKEN=1
if record_rejoined_id d1; then echo 'failed monitor search accepted' >&2; exit 1; fi
echo 'scale replica identities passed'
