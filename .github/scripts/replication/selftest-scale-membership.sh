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

set -eu -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
. "$HERE/scale-membership.sh"
ROOT_PASSWORD=secret ROLLING_END_MS=2000000000000
TEST_MAPPING=127.0.0.1:14444
docker() { [ "$1" = port ] || return 1; printf '%s\n' "$TEST_MAPPING"; }
python3() { printf '%s\n' "$*" >"$OUT/args"; cat >"$OUT/password"; return "${RESULT:-0}"; }
peer_removed survivor cv-r1 || { echo 'absent membership rejected' >&2; exit 1; }
[ "$(cat "$OUT/password")" = secret ] || { echo 'password did not arrive on stdin' >&2; exit 1; }
grep -F '14444 2000000000000 membership cv-r1 absent' "$OUT/args" >/dev/null || { echo 'membership request has wrong arguments' >&2; exit 1; }
peer_present survivor cv-r1 || exit 1
grep -F 'membership cv-r1 present' "$OUT/args" >/dev/null || exit 1
RESULT=1
if peer_present survivor cv-r1; then echo 'failed membership search passed' >&2; exit 1; fi
rm -f "$OUT/args"; TEST_MAPPING=0.0.0.0:14444
if peer_removed survivor cv-r1; then echo 'non-loopback admin port accepted' >&2; exit 1; fi
[ ! -e "$OUT/args" ] || { echo 'query sent to non-loopback port' >&2; exit 1; }
echo 'scale membership wiring passed'
