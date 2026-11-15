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
ROOT="$OUT/instance with spaces"
mkdir -p "$ROOT/bin" "$ROOT/lib" "$ROOT/logs" "$ROOT/config"
cp "$HERE/../../opendj-server-legacy/resource/bin/start-ds" "$ROOT/bin/start-ds"
cat >"$ROOT/lib/_script-util.sh" <<'UTIL'
INSTANCE_ROOT=$INSTALL_ROOT
OPENDJ_JAVA_BIN="$INSTALL_ROOT/java"
export INSTANCE_ROOT
UTIL
cat >"$ROOT/java" <<'JAVA'
#!/bin/sh
case " $* " in
  *' --checkStartability '*)
    if [ ! -f "$INSTANCE_ROOT/probed" ]; then
      touch "$INSTANCE_ROOT/probed"
      exit 99
    fi
    echo fixture-probe-diagnostic >&2
    exit "$PROBE_RESULT"
    ;;
  *org.opends.server.tools.WaitForFileDelete*)
    # The server child has exited before the final probe reports its failure.
    while kill -0 "$(cat "$INSTANCE_ROOT/logs/server.pid")" 2>/dev/null; do sleep 0.01; done
    exit 0 ;;
  *) exit 42 ;;
esac
JAVA
chmod +x "$ROOT/java"
PROBE_RESULT=137; export PROBE_RESULT
rc=0
sh "$ROOT/bin/start-ds" >"$OUT/output" 2>&1 || rc=$?
[ "$rc" -eq 1 ] || { echo 'launcher changed its failure contract' >&2; exit 1; }
grep -q fixture-probe-diagnostic "$ROOT/logs/start-ds.log"   || { echo 'failed startup probe diagnostics lost' >&2; exit 1; }
grep -q '137' "$ROOT/logs/start-ds.log"   || { echo 'failed startup probe status lost' >&2; exit 1; }
grep -q 'server pid [0-9][0-9]*, status 42' "$ROOT/logs/start-ds.log" \
  || { echo 'exited server child status lost' >&2; exit 1; }
# A subsequent successful start must not erase the cause of the failed one.
rm "$ROOT/probed"
PROBE_RESULT=98
sh "$ROOT/bin/start-ds" >"$OUT/output" 2>&1
grep -q fixture-probe-diagnostic "$ROOT/logs/start-ds.log"
# Diagnostic storage failures must not skip the probe or change its result.
rm -f "$ROOT/probed" "$ROOT/logs/start-ds.log"
mkdir "$ROOT/logs/start-ds.log"
sh "$ROOT/bin/start-ds" >"$OUT/output" 2>&1
rm "$ROOT/probed"
PROBE_RESULT=99
rc=0; sh "$ROOT/bin/start-ds" >"$OUT/output" 2>&1 || rc=$?
[ "$rc" -eq 1 ]
echo 'launcher preserves failed probe output/status without changing start results'
