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
export FIXTURE_ROOT="$OUT/instance"
mkdir -p "$FIXTURE_ROOT/bin" "$FIXTURE_ROOT/bootstrap" "$FIXTURE_ROOT/data/config" "$FIXTURE_ROOT/template/config"
python3 - "$HERE/../../opendj-packages/opendj-docker/run.sh" "$OUT/run.sh" "$FIXTURE_ROOT" <<'PYTHON'
from pathlib import Path
import sys
Path(sys.argv[2]).write_text(Path(sys.argv[1]).read_text().replace('/opt/opendj', sys.argv[3]))
PYTHON
cat >"$FIXTURE_ROOT/bootstrap/setup.sh" <<'SETUP'
#!/bin/sh
# At this point join.sh has not even started; stale success must already be gone.
[ ! -e "$FIXTURE_ROOT/.bootstrap-finished" ] || exit 91
mkdir -p "$FIXTURE_ROOT/data/config"
printf '1.0.0\n' >"$FIXTURE_ROOT/data/config/buildinfo"
exit "${SETUP_RC:-0}"
SETUP
cat >"$FIXTURE_ROOT/bootstrap/join.sh" <<'JOIN'
#!/bin/sh
[ ! -e "$FIXTURE_ROOT/.bootstrap-finished" ] || exit 92
# Retained volumes stay health-ready independently of this unfinished join.
if [ "$CASE" = retained ]; then [ -f "$FIXTURE_ROOT/.bootstrap-complete" ] || exit 93; fi
printf 'join was called\n'
exit "${JOIN_RC:-0}"
JOIN
printf '#!/bin/sh\nexit 0\n' >"$FIXTURE_ROOT/bin/start-ds"
cp "$FIXTURE_ROOT/bin/start-ds" "$FIXTURE_ROOT/bin/stop-ds"
cp "$FIXTURE_ROOT/bin/start-ds" "$FIXTURE_ROOT/upgrade"
printf '1.0.0\n' >"$FIXTURE_ROOT/template/config/buildinfo"
chmod +x "$FIXTURE_ROOT/bootstrap/join.sh" "$FIXTURE_ROOT/bin/"* "$FIXTURE_ROOT/upgrade"
export ADMIN_PORT=14444 OPENDJ_REPLICATION_TYPE=simple REPLICATION_PEERS=seed ROOT_PASSWORD=fixture
unset BOOTSTRAP_COMPLETE BOOTSTRAP_FINISHED BOOTSTRAP_FAILED BOOTSTRAP_PENDING REJOIN_PENDING INITIALIZE_PENDING BOOTSTRAP
for CASE in fresh retained failed-join failed-setup no-join; do
  export CASE
  rm -rf "$FIXTURE_ROOT/data"
  mkdir "$FIXTURE_ROOT/data"
  export SETUP_RC=0 JOIN_RC=0
  OPENDJ_REPLICATION_TYPE=simple
  case "$CASE" in
    retained) mkdir "$FIXTURE_ROOT/data/config"; cp "$FIXTURE_ROOT/template/config/buildinfo" "$FIXTURE_ROOT/data/config/" ;;
    failed-join) JOIN_RC=8 ;;
    failed-setup) SETUP_RC=7 ;;
    no-join) OPENDJ_REPLICATION_TYPE= ;;
  esac
  # All starts, including docker restart reusing the writable layer, clear old results.
  touch "$FIXTURE_ROOT/.bootstrap-finished" "$FIXTURE_ROOT/.bootstrap-failed"
  # The command substitution keeps the background wrapper's stdout open until it
  # exits too, so assertions below observe the published result without sleeping.
  result=$(bash "$OUT/run.sh" 2>&1)
  case "$CASE" in
    failed-*)
      [ ! -f "$FIXTURE_ROOT/.bootstrap-finished" ] || { echo "$CASE kept stale success" >&2; exit 1; }
      [ -f "$FIXTURE_ROOT/.bootstrap-failed" ] || exit 1 ;;
    *)
      [ -f "$FIXTURE_ROOT/.bootstrap-finished" ] || { echo "$CASE did not finish: $result" >&2; exit 1; }
      [ ! -f "$FIXTURE_ROOT/.bootstrap-failed" ] || exit 1 ;;
  esac
done
echo 'Every container start clears completion; only successful setup and join republish it'
