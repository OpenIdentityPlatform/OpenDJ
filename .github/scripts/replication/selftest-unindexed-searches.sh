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

# Exercise the real access-log reader against files standing in for the container's logs.
set -eE -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/assert-converged.sh"
BASE_DN=dc=example,dc=com
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

docker() {
  [ "$1 $2 $3 $4" = 'exec cv-d1 sh -c' ] || return 1
  local command=${5//\/opt\/opendj\/data\/logs/$OUT}
  sh -c "$command"
}

cat >"$OUT/access.20261107161156Z" <<'LOG'
[07/Nov/2026:15:52:52 +0000] SEARCH REQ conn=70 op=1 msgID=2 base="ou=People,dc=example,dc=com" scope=one filter="(objectClass=inetOrgPerson)" attrs="1.1"
[07/Nov/2026:15:52:54 +0000] SEARCH RES conn=70 op=1 msgID=2 result=0 nentries=10000 unindexed etime=1994
LOG
cat >"$OUT/access" <<'LOG'
[07/Nov/2026:16:28:32 +0000] SEARCH REQ conn=70 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=AddRate)" attrs="1.1"
[07/Nov/2026:16:28:32 +0000] SEARCH RES conn=70 op=1 msgID=2 result=0 nentries=0 etime=0
[07/Nov/2026:16:28:33 +0000] SEARCH REQ conn=71 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=Diverged)" attrs="1.1"
[07/Nov/2026:16:28:33 +0000] SEARCH RES conn=71 op=1 msgID=2 result=0 nentries=0 etime=0
LOG
out=$(unindexed_searches cv-d1)
if [ -n "$out" ]; then
  echo "FAIL: unrelated search before restart was paired with a later request: $out" >&2
  exit 1
fi

# A target request and response may straddle rotation. A real unindexed response must
# still be reported even when the same connection and operation are reused later.
cat >"$OUT/access.20261107161156Z" <<'LOG'
[07/Nov/2026:15:52:52 +0000] SEARCH REQ conn=70 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=AddRate)" attrs="1.1"
LOG
cat >"$OUT/access" <<'LOG'
[07/Nov/2026:15:52:54 +0000] SEARCH RES conn=70 op=1 msgID=2 result=0 nentries=0 unindexed etime=10
[07/Nov/2026:16:28:32 +0000] SEARCH REQ conn=70 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=AddRate)" attrs="1.1"
[07/Nov/2026:16:28:32 +0000] SEARCH RES conn=70 op=1 msgID=2 result=0 nentries=0 etime=0
[07/Nov/2026:16:28:33 +0000] SEARCH REQ conn=71 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=Diverged)" attrs="1.1"
[07/Nov/2026:16:28:33 +0000] SEARCH RES conn=71 op=1 msgID=2 result=0 nentries=0 etime=0
LOG
out=$(unindexed_searches cv-d1)
[ "$out" = '::error::cv-d1 ran (sn=AddRate) without an index' ] || {
  echo "FAIL: missed an unindexed response across rotation: $out" >&2; exit 1;
}

# An interrupted old request cannot claim an unrelated response after restart.
cat >"$OUT/access.20261107161156Z" <<'LOG'
[07/Nov/2026:15:52:52 +0000] SEARCH REQ conn=70 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=AddRate)" attrs="1.1"
LOG
cat >"$OUT/access" <<'LOG'
[07/Nov/2026:16:28:32 +0000] SEARCH REQ conn=70 op=1 msgID=2 base="ou=People,dc=example,dc=com" scope=one filter="(objectClass=inetOrgPerson)" attrs="1.1"
[07/Nov/2026:16:28:32 +0000] SEARCH RES conn=70 op=1 msgID=2 result=0 nentries=10000 unindexed etime=10
[07/Nov/2026:16:28:33 +0000] SEARCH REQ conn=71 op=1 msgID=2 base="dc=example,dc=com" scope=sub filter="(sn=Diverged)" attrs="1.1"
[07/Nov/2026:16:28:33 +0000] SEARCH RES conn=71 op=1 msgID=2 result=0 nentries=0 etime=0
LOG
out=$(unindexed_searches cv-d1)
[ -z "$out" ] || { echo "FAIL: interrupted request claimed another response: $out" >&2; exit 1; }

# An absent target search must remain an error.
rm "$OUT/access.20261107161156Z"
out=$(unindexed_searches cv-d1)
[ "$out" = '::error::the access log of cv-d1 holds no search of (sn=AddRate)' ] || {
  echo "FAIL: missing target search was accepted: $out" >&2; exit 1;
}

echo 'unindexed-searches: 4 checks passed'
