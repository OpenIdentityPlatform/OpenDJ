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
export FIXTURE="$OUT"
mkdir "$OUT/bin"
# A stopped daemon target supports logs/cp, but every exec fails. Include a secret
# file and a symlink next to the CLI log: neither belongs in the exported evidence.
python3 - "$OUT/tool-logs.tar" <<'PYTHON'
import io, sys, tarfile
with tarfile.open(sys.argv[1], "w") as archive:
    for name, data in [("opendj-replication-one.log", b"replication failed\n"),
                       ("password", b"must not escape\n")]:
        entry = tarfile.TarInfo(name); entry.size = len(data)
        archive.addfile(entry, io.BytesIO(data))
    link = tarfile.TarInfo("opendj-setup-link.log")
    link.type = tarfile.SYMTYPE; link.linkname = "/etc/passwd"
    archive.addfile(link)
PYTHON
cat >"$OUT/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
case "$1" in
  inspect) printf '%s\n' '{"Status":"exited","ExitCode":137,"OOMKilled":true}' ;;
  logs) echo 'server stopped' ;;
  exec) echo 'container is not running' >&2; exit 1 ;;
  cp)
    case "$2" in
      */data/logs) mkdir -p "$3/logs"; echo 'fatal server failure' >"$3/logs/errors" ;;
      */data/tmp/.) cat "$FIXTURE/tool-logs.tar" ;;
      */.bootstrap-failed) echo 'join:8' >"$3" ;;
      *) exit 1 ;;
    esac ;;
  *) exit 1 ;;
esac
DOCKER
chmod +x "$OUT/bin/docker"
PATH="$OUT/bin:$PATH" bash "$HERE/docker-startup-diagnostics.sh" node "$OUT/evidence" >"$OUT/output"
grep -F '"OOMKilled":true' "$OUT/evidence/state.json" >/dev/null
grep -Fx 'fatal server failure' "$OUT/evidence/logs/errors" >/dev/null
grep -Fx 'replication failed' "$OUT/evidence/tool-logs/opendj-replication-one.log" >/dev/null
grep -Fx 'join:8' "$OUT/evidence/bootstrap-failed" >/dev/null
[ "$(ls "$OUT/evidence/tool-logs" | wc -l | tr -d ' ')" = 1 ]
grep -Fx 'fatal server failure' "$OUT/output" >/dev/null
# Diagnostics and cleanup failing must not replace the test's original exit code.
# Extract the real trap registration and execute it with failing collector commands.
sed -n '/^trap .* ERR$/p' "$HERE/docker-test-replication.sh" >"$OUT/trap.sh"
rc=0
bash -c 'set -eE; dump_logs() { return 9; }; timings() { return 10; }; cleanup() { return 11; }; . "$1"; (exit 42)' bash "$OUT/trap.sh" >"$OUT/trap-output" 2>&1 || rc=$?
[ "$rc" -eq 42 ] || { echo "diagnostics replaced failure 42 with $rc" >&2; exit 1; }
echo 'Stopped-container diagnostics retain exit state and logs, exclude secrets and preserve the original error'
