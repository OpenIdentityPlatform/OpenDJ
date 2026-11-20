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

set -eu
REPO=$(cd "$(dirname "$0")/../.." && pwd)
SHELL_UNDER_TEST=${1:-/bin/sh}
FIXTURES=$(mktemp -d)
LIVE_PID=
trap 'if [ -n "$LIVE_PID" ]; then kill "$LIVE_PID" 2>/dev/null || :; fi; rm -rf "$FIXTURES"' EXIT
export TMPDIR="$FIXTURES"

mkdir -p "$FIXTURES/bin" "$FIXTURES/lib"
cp "$REPO/opendj-server-legacy/resource/bin/start-ds" "$FIXTURES/bin/"
cat > "$FIXTURES/lib/_script-util.sh" <<'UTIL'
OPENDJ_JAVA_BIN="$INSTALL_ROOT/java"
UTIL
cat > "$FIXTURES/java" <<'JAVA'
#!/bin/sh
case " $* " in
  *' --help '*)
    printf 'fixture help\n'
    exit 0
    ;;
  *' --checkStartability '*)
    if [ -f "$INSTANCE_ROOT/checked" ]; then
      touch "$INSTANCE_ROOT/final-check"
      printf 'probe output\n' >&2
      exit "${FIXTURE_PROBE_STATUS:-98}"
    fi
    if [ -n "${FIXTURE_TIMEOUT_ARG:-}" ]; then
      found_timeout=false
      for arg do
        if [ "$arg" = "$FIXTURE_TIMEOUT_ARG" ]; then
          found_timeout=true
          break
        fi
      done
      if [ "$found_timeout" = false ]; then
        printf 'Expected preflight argument %s\n' "$FIXTURE_TIMEOUT_ARG" >&2
        exit 1
      fi
    fi
    touch "$INSTANCE_ROOT/checked"
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --startupTimeoutFile)
          shift
          printf '%s\n' "$FIXTURE_TIMEOUT" > "$1"
          ;;
      esac
      shift
    done
    exit "$FIXTURE_START_CODE"
    ;;
  *org.opends.server.tools.WaitForFileDelete*)
    touch "$INSTANCE_ROOT/waiter-jvm"
    exec sleep 30
    ;;
esac
printf 'first line\n'
case "$FIXTURE_CASE" in
  early|quiet-failure|write-only-early)
    printf 'startup failed\n' >&2
    exit 42
    ;;
  timeout|write-only-timeout)
    exec sleep 30
    ;;
  *)
    sleep 0.2
    printf 'last message'
    rm "$INSTANCE_ROOT/logs/server.starting"
    exec sleep 30
    ;;
esac
JAVA
chmod +x "$FIXTURES/java"

run_case() {
  export FIXTURE_CASE=$1 FIXTURE_TIMEOUT=$2 FIXTURE_START_CODE=$3
  expected=$4
  shift 4
  export INSTANCE_ROOT="$FIXTURES/$FIXTURE_CASE instance"
  mkdir -p "$INSTANCE_ROOT/logs" "$INSTANCE_ROOT/tmp"
  printf 'stale log\n' > "$INSTANCE_ROOT/logs/server.out"
  if [[ "$FIXTURE_CASE" == write-only-* ]]; then
    chmod 200 "$INSTANCE_ROOT/logs/server.out"
  fi
  status=0
  timeout 8 "$SHELL_UNDER_TEST" "$FIXTURES/bin/start-ds" "$@" \
    > "$INSTANCE_ROOT/stdout" 2> "$INSTANCE_ROOT/stderr" || status=$?
  if [ -f "$INSTANCE_ROOT/logs/server.pid" ]; then
    read -r LIVE_PID < "$INSTANCE_ROOT/logs/server.pid"
  fi
  if [ "$status" -ne "$expected" ] || [ -e "$INSTANCE_ROOT/waiter-jvm" ]; then
    echo "$FIXTURE_CASE: expected exit $expected with no waiter JVM, got $status" >&2
    cat "$INSTANCE_ROOT/stderr" >&2
    exit 1
  fi
  [ -z "$(ls -A "$INSTANCE_ROOT/tmp")" ]
}

help=$(TMPDIR="$FIXTURES/unavailable" INSTANCE_ROOT="$FIXTURES/missing instance" \
  "$SHELL_UNDER_TEST" "$FIXTURES/bin/start-ds" --help)
[ "$help" = "fixture help" ]

run_case early 0 99 1 --timeout 0
[ -e "$INSTANCE_ROOT/logs/server.starting" ]
[ ! -e "$INSTANCE_ROOT/final-check" ]
grep -Fxq 'startup failed' "$INSTANCE_ROOT/stdout"
grep -Eq 'server pid [0-9]+, status 42' "$INSTANCE_ROOT/stderr"
grep -Fq 'status 42' "$INSTANCE_ROOT/logs/start-ds.log"
LIVE_PID=

run_case success 200 99 0
[ -e "$INSTANCE_ROOT/final-check" ]
printf 'first line\nlast message' | cmp - "$INSTANCE_ROOT/stdout"
kill "$LIVE_PID"
LIVE_PID=

run_case quiet 0 103 0 --quiet --timeout=0
[ -e "$INSTANCE_ROOT/final-check" ]
[ ! -s "$INSTANCE_ROOT/stdout" ]
kill "$LIVE_PID"
LIVE_PID=

run_case quiet-failure 60 103 1 --quiet --timeout=60
[ ! -e "$INSTANCE_ROOT/final-check" ]
[ ! -s "$INSTANCE_ROOT/stdout" ]
grep -Fxq 'startup failed' "$INSTANCE_ROOT/logs/server.out"
grep -Fq 'status 42' "$INSTANCE_ROOT/stderr"
LIVE_PID=

FIXTURE_TIMEOUT_ARG=-t1 run_case timeout 1 99 2 -t1
[ ! -e "$INSTANCE_ROOT/final-check" ]
[ -e "$INSTANCE_ROOT/logs/server.starting" ]
kill -0 "$LIVE_PID"
grep -Fq 'timed out after 1' "$INSTANCE_ROOT/stderr"
kill "$LIVE_PID"
LIVE_PID=

touch "$FIXTURES/write-only"
chmod 200 "$FIXTURES/write-only"
if [ -r "$FIXTURES/write-only" ]; then
  echo 'Skipping write-only log scenarios because the test user can read mode 0200 files'
else
  run_case write-only-success 200 99 0
  [ -e "$INSTANCE_ROOT/final-check" ]
  [ ! -e "$INSTANCE_ROOT/logs/server.starting" ]
  [ ! -s "$INSTANCE_ROOT/stdout" ]
  kill "$LIVE_PID"
  LIVE_PID=

  run_case write-only-early 0 99 1 --timeout 0
  [ ! -e "$INSTANCE_ROOT/final-check" ]
  [ -e "$INSTANCE_ROOT/logs/server.starting" ]
  grep -Eq 'server pid [0-9]+, status 42' "$INSTANCE_ROOT/stderr"
  LIVE_PID=

  FIXTURE_TIMEOUT_ARG=-t1 run_case write-only-timeout 1 99 2 -t1
  [ ! -e "$INSTANCE_ROOT/final-check" ]
  [ -e "$INSTANCE_ROOT/logs/server.starting" ]
  kill -0 "$LIVE_PID"
  grep -Fq 'timed out after 1' "$INSTANCE_ROOT/stderr"
  kill "$LIVE_PID"
  LIVE_PID=
fi

export FIXTURE_PROBE_STATUS=99
run_case failed-probe 3 99 1 --timeout 3
[ -e "$INSTANCE_ROOT/final-check" ]
grep -Fq 'checkStartability exited with status 99' "$INSTANCE_ROOT/stderr"
grep -Fxq 'probe output' "$INSTANCE_ROOT/logs/start-ds.log"
kill "$LIVE_PID"
LIVE_PID=

echo "Detached startup scenarios passed with $SHELL_UNDER_TEST"
