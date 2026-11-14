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
OUT=$(mktemp -d)
trap 'join_search_close; rm -rf "$OUT"' EXIT
. "$HERE/../../../opendj-packages/opendj-docker/bootstrap/join-search.sh"
mkdir -p "$OUT/lib"
cat >"$OUT/lib/_script-util.sh" <<'UTIL'
printf 'start\n' >>"$INSTALL_ROOT/starts"
OPENDJ_JAVA_BIN="$INSTALL_ROOT/worker"
UTIL
cat >"$OUT/worker" <<'WORKER'
#!/usr/bin/env python3
import sys, base64, time
for line in sys.stdin:
    args = base64.b64decode(line).decode().split('\0')[:-1]
    if args == ['die']:
        sys.exit(1)
    if args == ['hang']:
        time.sleep(30)
    if args == ['bad-frame']:
        print('0 cGFydGlhbA==!', flush=True)
        continue
    status = 49 if args == ['refused'] else 0
    print(str(status) + ' ' + base64.b64encode('|'.join(args).encode()).decode(), flush=True)
WORKER
chmod +x "$OUT/worker"
JOIN_INSTALL_ROOT=$OUT
# OpenDJ itself has a different base64 CLI at the front of the bootstrap PATH.
mkdir -p "$OUT/bin"
printf '#!/bin/sh\nexit 42\n' >"$OUT/bin/base64"
chmod +x "$OUT/bin/base64"
PATH="$OUT/bin:$PATH"
join_search_init
ldapsearch() { printf 'fallback'; }
[ "$(join_search 'a b' '' $'unicode-тест\nnext')" = $'a b||unicode-тест\nnext' ]
rc=0
result=$(join_search refused) || rc=$?
[ "$rc" = 49 ] && [ "$result" = refused ]
[ "$(join_search next)" = next ]
[ "$(wc -l <"$OUT/starts" | tr -d ' ')" = 1 ] || { echo 'worker started more than once' >&2; exit 1; }
[ "$(join_search die)" = fallback ]
[ "$(join_search after-death)" = fallback ]
join_search_close
[ ! -d "${JOIN_SEARCH_DIR:-/path-which-must-not-exist}" ]
for failure in hang bad-frame; do
  join_search_init
  REPLICATION_ATTEMPT_TIMEOUT=1
  [ "$(join_search "$failure" 2>/dev/null)" = fallback ]
  [ "$(join_search after-failure)" = fallback ]
  join_search_close
done
printf '#!/bin/sh\nexit 1\n' >"$OUT/worker"
join_search_init
[ "$(join_search after-startup-failure 2>/dev/null)" = fallback ]
join_search_close
echo 'join search reuses the process, preserves statuses and falls back after worker death'
