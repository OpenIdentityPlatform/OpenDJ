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
trap 'if [ -n "$LIVE_PID" ]; then
        kill -KILL "$LIVE_PID" 2>/dev/null || :
        wait "$LIVE_PID" 2>/dev/null || :
      fi
      rm -rf "$FIXTURES"' EXIT
export TMPDIR="$FIXTURES"

mkdir -p "$FIXTURES/bin" "$FIXTURES/lib"
cp "$REPO/opendj-server-legacy/resource/bin/start-ds" \
   "$REPO/opendj-server-legacy/resource/bin/stop-ds" "$FIXTURES/bin/"
cp "$REPO/opendj-server-legacy/resource/bin/_script-util.sh" "$FIXTURES/lib/script-util-real.sh"
cat > "$FIXTURES/lib/_script-util.sh" <<'UTIL'
# Load the launcher helpers while using fixture Java and a shorter shutdown timer.
SCRIPT_UTIL_CMD=
. "$INSTALL_ROOT/lib/script-util-real.sh"
OPENDJ_JAVA_BIN="$INSTALL_ROOT/java"
dd() {
  if [ "${FIXTURE_CASE:-}" = stop-sparse ]; then
    case " $* " in
      *' count='*) ;;
      *)
        touch "$INSTANCE_ROOT/unbounded-log-read"
        return 1
        ;;
    esac
  fi
  command dd "$@"
}
stat() {
  [ "${FIXTURE_CASE:-}" != stop-no-stat ] || return 1
  command stat "$@"
}
sleep() {
  case "${FIXTURE_CASE:-}:$1" in
    stop-timeout:200) command sleep 1 ;;
    stop-rotate:0.1)
      touch "$INSTANCE_ROOT/waiting-for-shutdown"
      while [ -e "$INSTANCE_ROOT/logs/server.pid" ]; do command sleep 0.05; done
      ;;
    *) command sleep "$@" ;;
  esac
}
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
    if [ "${FIXTURE_CASE#stop-}" != "$FIXTURE_CASE" ]; then
      exit 2
    fi
    exec sleep 30
    ;;
  *' --checkStoppability '*)
    exit "$FIXTURE_STOP_CODE"
    ;;
  *org.opends.server.tools.StopDS*)
    touch "$INSTANCE_ROOT/ldap-stop"
    exit 7
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
grep -Fq "server pid $LIVE_PID, status running" "$INSTANCE_ROOT/logs/start-ds.log"
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

# A later successful start must preserve the earlier failure diagnostics.
rm "$INSTANCE_ROOT/checked" "$INSTANCE_ROOT/final-check"
FIXTURE_PROBE_STATUS=98 run_case failed-probe 3 99 0 --timeout 3
grep -Fxq 'probe output' "$INSTANCE_ROOT/logs/start-ds.log"
kill "$LIVE_PID"
LIVE_PID=

# An unusable diagnostic path must leave the final probe's result intact.
mkdir -p "$FIXTURES/unwritable-diagnostics instance/logs/start-ds.log"
run_case unwritable-diagnostics 3 99 1 --timeout 3
[ -e "$INSTANCE_ROOT/final-check" ]
grep -Fxq 'probe output' "$INSTANCE_ROOT/stderr"
kill "$LIVE_PID"
LIVE_PID=

cat > "$FIXTURES/bin/start-ds" <<'START'
#!/bin/sh
[ ! -e "$INSTANCE_ROOT/logs/server.pid" ] || exit 1
printf '%s\n' "$@" > "$INSTANCE_ROOT/restart-args"
START
chmod +x "$FIXTURES/bin/start-ds"

run_stop_case() {
  export FIXTURE_CASE=$1 FIXTURE_STOP_CODE=$2
  expected=$3
  shift 3
  export INSTANCE_ROOT="$FIXTURES/$FIXTURE_CASE instance"
  mkdir -p "$INSTANCE_ROOT/logs"
  if [ "$FIXTURE_CASE" = stop-sparse ]; then
    # Model a 64 GiB log without allocating or reading 64 GiB in the fixture.
    printf x | dd of="$INSTANCE_ROOT/logs/errors" bs=1 seek=68719476742 count=1 2>/dev/null
  else
    awk 'BEGIN { for (i = 0; i < 8250; i++) printf "x"; print ""; }' > "$INSTANCE_ROOT/logs/errors"
  fi
  if [ "$FIXTURE_CASE" = stop-write-only ]; then
    chmod 200 "$INSTANCE_ROOT/logs/errors"
  fi
  (
    trap 'printf "shutdown in progress\n" >> "$INSTANCE_ROOT/logs/errors"
          if [ "$FIXTURE_CASE" != stop-timeout ]; then
            if [ "$FIXTURE_CASE" = stop-rotate ]; then
              while [ ! -e "$INSTANCE_ROOT/waiting-for-shutdown" ]; do sleep 0.05; done
            else
              sleep 0.2
            fi
            printf "shutdown complete" >> "$INSTANCE_ROOT/logs/errors"
            if [ "$FIXTURE_CASE" = stop-rotate ]; then
              mv "$INSTANCE_ROOT/logs/errors" "$INSTANCE_ROOT/logs/errors.1"
              : > "$INSTANCE_ROOT/logs/errors"
            fi
            touch "$INSTANCE_ROOT/shutdown-complete"
            rm "$INSTANCE_ROOT/logs/server.pid"
            exit 0
          fi' TERM
    touch "$INSTANCE_ROOT/ready"
    while :; do sleep 0.1; done
  ) &
  LIVE_PID=$!
  printf '%s\n' "$LIVE_PID" > "$INSTANCE_ROOT/logs/server.pid"
  timeout 5 sh -c 'while [ ! -e "$INSTANCE_ROOT/ready" ]; do sleep 0.1; done'

  status=0
  timeout 8 "$SHELL_UNDER_TEST" "$FIXTURES/bin/stop-ds" "$@" \
    > "$INSTANCE_ROOT/stdout" 2> "$INSTANCE_ROOT/stderr" || status=$?
  if [ "$status" -ne "$expected" ] || [ -e "$INSTANCE_ROOT/waiter-jvm" ]; then
    echo "$FIXTURE_CASE: expected exit $expected with no waiter JVM, got $status" >&2
    cat "$INSTANCE_ROOT/stderr" >&2
    exit 1
  fi
  if [ -e "$INSTANCE_ROOT/unbounded-log-read" ]; then
    echo "$FIXTURE_CASE: stop-ds attempted a full scan of the existing log" >&2
    exit 1
  fi
  if [ "$FIXTURE_CASE" = stop-timeout ]; then
    kill -KILL "$LIVE_PID"
    wait "$LIVE_PID" 2>/dev/null || :
  else
    [ ! -e "$INSTANCE_ROOT/logs/server.pid" ]
    [ -e "$INSTANCE_ROOT/shutdown-complete" ]
    wait "$LIVE_PID"
    [ ! -e "$INSTANCE_ROOT/ldap-stop" ]
  fi
  LIVE_PID=
}

run_stop_case stop-success 100 0
printf 'shutdown in progress\nshutdown complete' | cmp - "$INSTANCE_ROOT/stdout"

run_stop_case stop-rotate 100 0
printf 'shutdown in progress\nshutdown complete' | cmp - "$INSTANCE_ROOT/stdout"

run_stop_case stop-sparse 100 0
printf 'shutdown in progress\nshutdown complete' | cmp - "$INSTANCE_ROOT/stdout"

run_stop_case stop-no-stat 100 0
[ ! -s "$INSTANCE_ROOT/stdout" ]

run_stop_case stop-restart 106 0 --restart --quiet
[ ! -s "$INSTANCE_ROOT/stdout" ]
grep -Fxq -- '--quiet' "$INSTANCE_ROOT/restart-args"

run_stop_case stop-timeout 100 7
[ -e "$INSTANCE_ROOT/ldap-stop" ]
[ -e "$INSTANCE_ROOT/logs/server.pid" ]
grep -Fq 'timed out after 200' "$INSTANCE_ROOT/stderr"

if [ ! -r "$FIXTURES/write-only" ]; then
  run_stop_case stop-write-only 100 0
  [ ! -s "$INSTANCE_ROOT/stdout" ]
fi

echo "Unix startup and shutdown scenarios passed with $SHELL_UNDER_TEST"
