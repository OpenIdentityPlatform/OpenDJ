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

set -e -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

# Exercise the actual roll, pause/resume, bounded wait and bootstrap-marker predicate.
# Only Docker transport and unrelated checkpoint/probe collection are replaced.
run_case() (
  local shape=$1 scenario=$2 pending=${3:-} node rc=0 writer=d2
  OUT="$OUT/$shape-$scenario${pending:+-$pending}"
  mkdir -p "$OUT/load" "$OUT/logs" "$OUT/markers"
  TOPOLOGY=rs534 DISTURBANCE=scale-rs-kept
  . "$HERE/lib.sh"
  . "$HERE/rolling-load.sh"
  DS_NODES='d0 d1 d2 d3' RS_ONLY='r0 r1 r2' RS_NODES=$RS_ONLY
  ALL_NODES="$DS_NODES $RS_NODES" PEERS=d0,d1,d2,d3,r0,r1,r2 SCALE_VOLUME=kept
  . "$HERE/disturb-scale.sh"
  ROLLING_END=$((SECONDS + 20)) DISTURB_TIMEOUT=8 SCALE_DEADLINE_ACTIVE=1
  ROLLING_END_MS=$((($(date +%s) + 20) * 1000))
  local group='r0 d0 d1'
  if [ "$shape" = single ]; then group=d0; writer=d1; fi
  for node in $group; do
    mkdir -p "$OUT/markers/$node"
    case $node in d*) touch "$OUT/load/$node.parked" ;; esac
  done
  printf 'load-mod-%s load-add-%s\n' "$writer" "$writer" >"$OUT/load/$writer.active"
  scale_checkpoint_wait() { :; }
  scale_probe_batch() { [ "$1" = "$writer" ]; }
  scale_capture_bootstrap() { :; }
  window_open_key() { :; }
  start_server() {
    local node=$1
    touch "$OUT/markers/$node/started"
    if [ -n "$pending" ] && [ "$node" != "$pending" ]; then touch "$OUT/markers/$node/finished"; fi
    case $scenario in
      failed)
        touch "$OUT/markers/$node/finished"
        [ "$node" != "${pending:-d0}" ] || touch "$OUT/markers/$node/failed"
        ;;
    esac
  }
  docker() {
    local node
    case $1 in
      stop|rm) : ;;
      inspect)
        case ${*: -1} in load-*) echo true ;; *) echo 172.18.0.8 ;; esac
        ;;
      logs)
        [ -f "$OUT/markers/${*: -1}/started" ] || return 1
        echo '[t] category=CORE severity=NOTICE msgID=135 msg=The Directory Server has started successfully'
        ;;
      exec)
        node=$2; shift 2
        printf '%s\n' "$node" >>"$OUT/marker-reads"
        if [ "$scenario" = transport ] && [ ! -f "$OUT/markers/$node/transport-retried" ]; then
          touch "$OUT/markers/$node/transport-retried"
          return 125
        fi
        BOOTSTRAP_FAILED="$OUT/markers/$node/failed" BOOTSTRAP_FINISHED="$OUT/markers/$node/finished" "$@"
        ;;
      *) echo "unexpected Docker call: $*" >&2; return 1 ;;
    esac
  }
  sleep() {
    local node
    SECONDS=$((SECONDS + $1))
    # A measured segment must not start on either restarted DS until the whole group
    # has finished joining. The untouched writer remains available throughout the wait.
    for node in $group; do
      case $node in
        d*) [ -f "$OUT/load/$node.pause" ] || { echo "resumed $node while the group was joining" >&2; return 200; } ;;
      esac
    done
    rolling_clients_running "$writer" || { echo 'surviving load writer was paused' >&2; return 200; }
    printf '%s\n' "$writer" >>"$OUT/surviving-writer"
    case $scenario in
      complete|transport)
        # Complete just one member per tick so an early per-node resume is observable.
        for node in $group; do
          [ ! -f "$OUT/markers/$node/finished" ] || continue
          touch "$OUT/markers/$node/finished"
          break
        done
        ;;
      worker) touch "$OUT/load/$writer.failed" ;;
      deadline) SECONDS=$ROLLING_END ;;
      late-completion)
        for node in $group; do touch "$OUT/markers/$node/finished"; done
        SECONDS=$ROLLING_END
        ;;
      incomplete) : ;;
      *) echo "unexpected sleep for $scenario" >&2; return 200 ;;
    esac
  }
  if [ "$shape" = pair ]; then
    scale_roll_pair d0 d1 "$SHORT_PEERS" r0 >"$OUT/result" 2>&1 || rc=$?
  else
    scale_roll d0 "$SHORT_PEERS" >"$OUT/result" 2>&1 || rc=$?
  fi
  case $scenario in
    complete|transport)
      [ "$rc" -eq 0 ] || { cat "$OUT/result" >&2; exit 1; }
      [ -s "$OUT/surviving-writer" ] || { echo 'load resumed without waiting for bootstrap' >&2; exit 1; }
      for node in $group; do
        [ -f "$OUT/markers/$node/finished" ] || { echo "bootstrap of $node did not complete before resume" >&2; exit 1; }
        case $node in d*) [ ! -f "$OUT/load/$node.pause" ] || { echo "$node remained paused" >&2; exit 1; } ;; esac
      done
      ;;
    *)
      [ "$rc" -ne 0 ] || { echo "$shape accepted $scenario bootstrap" >&2; exit 1; }
      for node in $group; do
        case $node in d*) [ -f "$OUT/load/$node.pause" ] || { echo "$shape resumed after $scenario bootstrap" >&2; exit 1; } ;; esac
      done
      case $scenario in
        failed) grep -F 'terminal failure' "$OUT/result" >/dev/null ;;
        incomplete) grep -F 'timed out waiting' "$OUT/result" >/dev/null ;;
        worker) grep -F 'load worker failed' "$OUT/result" >/dev/null ;;
        deadline|late-completion) [ "$SECONDS" -ge "$ROLLING_END" ] ;;
      esac
      ;;
  esac
  [ ! -f "$OUT/load/$writer.pause" ] || { echo 'surviving writer was left paused' >&2; exit 1; }
)

for shape in pair single; do
  for scenario in complete transport failed incomplete worker deadline late-completion; do
    run_case "$shape" "$scenario"
  done
done
# Each member independently blocks the group: completion of the other two cannot
# authorize resuming its writers, and each terminal bootstrap failure aborts the roll.
for node in r0 d0 d1; do
  run_case pair complete "$node"
  run_case pair failed "$node"
done
echo 'rolling bootstrap gates preserve surviving writers, positive completion, failures and deadlines'
