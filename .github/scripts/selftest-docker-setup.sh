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
export FIXTURE_ROOT="$OUT/instance" CALLS="$OUT/calls"
mkdir -p "$FIXTURE_ROOT/bin"
# Run the actual bootstrap with only its external installation path redirected.
python3 - "$HERE/../../opendj-packages/opendj-docker/bootstrap/setup.sh" "$OUT/bootstrap.sh" "$FIXTURE_ROOT" <<'PYTHON'
from pathlib import Path
import sys
Path(sys.argv[2]).write_text(Path(sys.argv[1]).read_text().replace('/opt/opendj', sys.argv[3]))
PYTHON
cat >"$FIXTURE_ROOT/setup" <<'SETUP'
#!/bin/sh
printf 'setup\n' >>"$CALLS"
[ "${SETUP_RC:-0}" -eq 0 ] || exit "$SETUP_RC"
touch "$FIXTURE_ROOT/setup-running"
no_start=false
for arg; do [ "$arg" != --doNotStart ] || no_start=true; done
if [ "$no_start" = false ]; then "$FIXTURE_ROOT/bin/start-ds" || exit $?; fi
rm "$FIXTURE_ROOT/setup-running"
printf 'setup-exited\n' >>"$CALLS"
SETUP
cat >"$FIXTURE_ROOT/bin/start-ds" <<'START'
#!/bin/sh
printf 'start\n' >>"$CALLS"
if [ -e "$FIXTURE_ROOT/setup-running" ]; then
  echo 'overlapping setup and server JVMs exhaust the memory budget' >&2
  exit 137
fi
[ "${START_RC:-0}" -eq 0 ] || exit "$START_RC"
touch "$FIXTURE_ROOT/server-ready"
START
cat >"$FIXTURE_ROOT/bin/dsconfig" <<'CONFIG'
#!/bin/sh
printf 'configure\n' >>"$CALLS"
[ -f "$FIXTURE_ROOT/server-ready" ]
CONFIG
chmod +x "$FIXTURE_ROOT/setup" "$FIXTURE_ROOT/bin/"*
export ROOT_PASSWORD=password ROOT_USER_DN='cn=Directory Manager' ADMIN_PORT=4444 PORT=1389 LDAPS_PORT=1636
export BASE_DN='dc=example,dc=com' OPENDJ_SSL_OPTIONS=--generateSelfSignedCertificate
unset INITIALIZE_PENDING SETUP_ARGS ADD_BASE_ENTRY SAMPLE_DATA
sh "$OUT/bootstrap.sh" >"$OUT/result" 2>&1 || { cat "$OUT/result" >&2; exit 1; }
printf 'setup\nsetup-exited\nstart\nconfigure\n' >"$OUT/expected"
diff -u "$OUT/expected" "$CALLS"
for failure in setup start; do
  rm -f "$CALLS" "$FIXTURE_ROOT/server-ready"
  rc=0
  if [ "$failure" = setup ]; then
    SETUP_RC=8 sh "$OUT/bootstrap.sh" >"$OUT/result" 2>&1 || rc=$?
    printf 'setup\n' >"$OUT/expected"
  else
    START_RC=1 sh "$OUT/bootstrap.sh" >"$OUT/result" 2>&1 || rc=$?
    printf 'setup\nsetup-exited\nstart\n' >"$OUT/expected"
  fi
  [ "$rc" -ne 0 ] || { echo "failed $failure reported success" >&2; exit 1; }
  diff -u "$OUT/expected" "$CALLS"
done
echo 'Docker bootstrap separates setup from startup and preserves both failures'
