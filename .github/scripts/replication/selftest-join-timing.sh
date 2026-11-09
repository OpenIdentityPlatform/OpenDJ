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
TIMING="$HERE/../../../opendj-packages/opendj-docker/bootstrap/join-timing.sh"
[ -f "$TIMING" ] || { echo 'missing bootstrap timing helper' >&2; exit 1; }
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
(
  JOIN_TIMING=1
  . "$TIMING"
  join_timing_init 2>"$OUT/timing"
  command_that_reads_stdin() { read -r value; printf '%s\n' "$value"; return 7; }
  rc=0
  output=$(printf 'stdin-value\n' | join_timed probe command_that_reads_stdin secret-argument 2>/dev/null) || rc=$?
  [ "$rc" -eq 7 ] && [ "$output" = stdin-value ] || exit 1
  grep -E 'join-timing event=start .*step=probe' "$OUT/timing" >/dev/null
  grep -E 'join-timing event=end .*step=probe .*status=7' "$OUT/timing" >/dev/null
  if grep -F 'secret-argument' "$OUT/timing" >/dev/null; then echo 'timing leaks command arguments' >&2; exit 1; fi
  join_timed success true
  grep -E 'join-timing event=end .*step=success .*status=0' "$OUT/timing" >/dev/null
)
(
  JOIN_TIMING=0
  . "$TIMING"
  join_timing_init 2>"$OUT/disabled"
  [ "$(join_timed disabled printf '%s' result)" = result ]
  [ ! -s "$OUT/disabled" ]
)
echo 'join timing preserves input, output and exit status'
