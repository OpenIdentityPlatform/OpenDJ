#!/bin/sh
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

set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/opendj-script-util.XXXXXX")
trap 'rm -rf "$test_dir"' 0

INSTALL_ROOT=$test_dir
INSTANCE_ROOT=$test_dir
SCRIPT_UTIL_CMD=
SCRIPT_NAME=setup
. "$root/opendj-server-legacy/resource/bin/_script-util.sh"

mkdir "$test_dir/tmp" "$test_dir/home"
OPENDJ_TMP_DIR=$test_dir/tmp
HOME=$test_dir/home
OPENDJ_JAVA_ARGS=

# On an executable filesystem, the real probe must succeed without shell errors
# or leaking its scratch variables into the caller.
res=caller
DIR_TO_TEST=$test_dir/caller
TMP_FILE=$test_dir/caller-file
remove_dir=caller
if ! set_bc_dir >"$test_dir/output" 2>&1; then
  cat "$test_dir/output" >&2
  exit 1
fi
if [ -s "$test_dir/output" ]; then
  cat "$test_dir/output" >&2
  exit 1
fi
test -z "$OPENDJ_JAVA_ARGS"
test "$res:$DIR_TO_TEST:$TMP_FILE:$remove_dir" = "caller:$test_dir/caller:$test_dir/caller-file:caller"
test -z "$(ls -A "$OPENDJ_TMP_DIR")"

# Prevent the probe file in the temporary directory from becoming executable,
# while allowing the real probe to make and execute its file in HOME.
chmod() {
  case $2 in
    "$OPENDJ_TMP_DIR"/*) ;;
    *) command chmod "$@" ;;
  esac
}
set_bc_dir >"$test_dir/output" 2>&1
unset -f chmod
test "$OPENDJ_JAVA_ARGS" = " -Dorg.bouncycastle.native.loader.install_dir=$HOME"
test -z "$(ls -A "$OPENDJ_TMP_DIR")"
test -z "$(ls -A "$HOME")"
OPENDJ_JAVA_ARGS=

# A directory created by the real probe must be removed afterwards.
check_noexec "$test_dir/missing"
test ! -e "$test_dir/missing"

# Simulate a noexec mount: the production selection logic still runs, while
# the probe returns the result of attempting to execute from each directory.
check_noexec() { [ "$1" = "$HOME" ]; }
set_bc_dir >"$test_dir/output" 2>&1
test "$OPENDJ_JAVA_ARGS" = " -Dorg.bouncycastle.native.loader.install_dir=$HOME"
grep -F "WARNING: $OPENDJ_TMP_DIR is mounted as noexec" "$test_dir/output" >/dev/null
if grep -F 'the OpenDJ installation could cause errors' "$test_dir/output" >/dev/null; then
  cat "$test_dir/output" >&2
  exit 1
fi

# An explicit BC installation directory must be preserved without probing.
OPENDJ_JAVA_ARGS='-Dorg.bouncycastle.native.loader.install_dir=/chosen'
check_noexec() { echo 'unexpected probe'; return 1; }
set_bc_dir >"$test_dir/output" 2>&1
test ! -s "$test_dir/output"
test "$OPENDJ_JAVA_ARGS" = '-Dorg.bouncycastle.native.loader.install_dir=/chosen'

# If neither directory permits execution, report it and do not set a fallback.
OPENDJ_JAVA_ARGS=
check_noexec() { return 1; }
set_bc_dir >"$test_dir/output" 2>&1
test -z "$OPENDJ_JAVA_ARGS"
grep -F 'the OpenDJ installation could cause errors' "$test_dir/output" >/dev/null

echo 'script-util checks passed'
