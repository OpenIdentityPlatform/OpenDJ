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
if [ ! -f "$HERE/disturb-scale.sh" ]; then
  echo 'scale disturbance is missing' >&2
  exit 1
fi

run_case() (
  local topology=$1 mode=$2 target n
  mkdir -p "$OUT/$topology-$mode/logs" "$OUT/$topology-$mode/scale-checkpoint"
  OUT="$OUT/$topology-$mode"
  if [ "$topology" = combined ]; then
    DS_NODES='d0 d1 d2 d3'; RS_ONLY=; RS_NODES=$DS_NODES; target=d3
  else
    DS_NODES='d0 d1 d2 d3'; RS_ONLY='r0 r1 r2'; RS_NODES=$RS_ONLY; target=r2
  fi
  ALL_NODES="$DS_NODES${RS_ONLY:+ $RS_ONLY}"
  PEERS=$(echo "$ALL_NODES" | tr ' ' ',')
  TOPOLOGY=$topology SCALE_VOLUME=$mode DISTURB_TIMEOUT=900 ROLLING_END=$((SECONDS + 900))
  BASE_DN=dc=example,dc=com ROOT_PASSWORD=password
  . "$HERE/disturb-scale.sh"
  record() { printf '%s\n' "$*" >>"$OUT/calls"; }
  docker() {
    record "docker $*"
    if [ "$1" = stop ] && [ "${*: -1}" = "${FAIL_STOP:-}" ]; then return 1; fi
    case $1 in
      cp) printf 'old log\n' >"$3" ;;
      logs) echo 'old container log' ;;
      inspect) echo 172.18.0.9 ;;
      exec) cat >"$OUT/probe.ldif"; [ "${FAIL_PROBE:-0}" = 0 ] ;;
    esac
  }
  start_server() { record "start $1 $2 $PEERS"; }
  admin_search() { echo 'scale checkpoint must not start an admin-search JVM' >&2; return 1; }
  scale_monitor_search() { record "search $1"; echo 'baseline'; }
  window_open() { record "window $*"; }; window_close() { :; }
  window_open_key() { record "key-open $1"; shift; record "window $*"; }
  window_close_key() { record "window-close $1"; }
  dns_window_open() { record "dns-open $*"; }; dns_window_close() { record dns-close; }
  errors_lines() { echo 10; }
  rolling_request_pause() { record "request-pause $1"; }
  rolling_pause() { record "pause $1"; }
  rolling_resume() { record "resume $1"; }
  record_rejoined_id() { record "identity $1"; }
  scale_backlog() { record "fill $REMOVED"; }
  wait_until() {
    record "wait ${*:3}"
    [ "${FAIL_CHECKPOINT:-0}" != 1 ] || [ "${3:-}" != scale_caught_up ] || return 1
    [ "${FAIL_JOIN:-0}" != 1 ] || [ "${3:-}" != bootstrap_ended ] || return 1
    [ "${FAIL_RS:-0}" != 1 ] || [ "${3:-} ${4:-} ${5:-}" != 'scale_caught_up r1 r0' ] || return 1
    [ "${FAIL_PAIR_PROBE:-0}" != 1 ] || { [ "${3:-}" != scale_probe ] && [ "${3:-}" != scale_probe_batch ]; } || return 1
    [ "${FAIL_ACTIVE:-0}" != 1 ] || [ "${3:-}" != rolling_clients_running ] || return 1
    if [ "${3:-}" = record_rejoined_id ]; then record_rejoined_id "$4"; fi
    return 0
  }
  date() { echo 400; }
  sleep() { record "sleep $*"; }
  fail() { echo "$*" >&2; return 1; }
  # No server is stopped if the surviving RS has not reached the departing RS's checkpoint.
  if (FAIL_CHECKPOINT=1; disturb) >/dev/null 2>&1; then
    echo 'failed checkpoint was accepted' >&2; exit 1
  fi
  if grep '^docker stop' "$OUT/calls" >/dev/null; then
    echo 'stopped a node without a checkpoint' >&2; exit 1
  fi
  : >"$OUT/calls"
  # A failed stop must abort the roll rather than recreate that still-running node.
  if (FAIL_STOP=d0; disturb) >/dev/null 2>&1; then
    echo 'failed node stop was accepted' >&2; exit 1
  fi
  if grep '^start d0 ' "$OUT/calls" >/dev/null; then
    echo 'recreated a node whose stop failed' >&2; exit 1
  fi
  : >"$OUT/calls"
  rm -f "$OUT/logs/$target.offline" "$OUT/logs/$target.previous"
  if [ "$topology" = rs534 ]; then
    # Do not stop either DS of a pair without a responding replication server.
    if (FAIL_RS=1; disturb) >/dev/null 2>&1; then echo 'unavailable RS accepted' >&2; exit 1; fi
    if grep -E '^docker stop .* (d[01]|r0)$' "$OUT/calls" >/dev/null; then echo 'group stopped without an RS' >&2; exit 1; fi
    : >"$OUT/calls"
    rm -f "$OUT/logs/$target.offline" "$OUT/logs/$target.previous"
    # A failed RS stop cannot be followed by any restart in the affected group.
    if (FAIL_STOP=r0; disturb) >/dev/null 2>&1; then echo 'failed RS stop accepted' >&2; exit 1; fi
    if grep -E '^start (d[01]|r0) ' "$OUT/calls" >/dev/null; then echo 'group restarted after failed RS stop' >&2; exit 1; fi
    : >"$OUT/calls"
    rm -f "$OUT/logs/$target.offline" "$OUT/logs/$target.previous"
    # A failed surviving-DS write cannot be followed by a pair restart.
    if (FAIL_PAIR_PROBE=1; disturb) >/dev/null 2>&1; then echo 'failed pair write accepted' >&2; exit 1; fi
    if grep -E '^start d[01] ' "$OUT/calls" >/dev/null; then echo 'pair restarted after failed write' >&2; exit 1; fi
    : >"$OUT/calls"
    rm -f "$OUT/logs/$target.offline" "$OUT/logs/$target.previous"
  fi
  if [ "$topology" = combined ]; then
    # Do not remove the node if the observer has no running load clients.
    if (FAIL_ACTIVE=1; disturb) >/dev/null 2>&1; then echo 'inactive observer accepted' >&2; exit 1; fi
    if grep '^docker stop' "$OUT/calls" >/dev/null; then echo 'stopped node without an active writer' >&2; exit 1; fi
    grep -Fx 'resume d1' "$OUT/calls" >/dev/null || { echo 'first held writer not resumed on failure' >&2; exit 1; }
    grep -Fx 'resume d2' "$OUT/calls" >/dev/null || { echo 'second held writer not resumed on failure' >&2; exit 1; }
    : >"$OUT/calls"
  fi
  # A failed join must prevent membership assertions and backlog creation.
  if (FAIL_JOIN=1; disturb) >/dev/null 2>&1; then
    echo 'failed join was accepted' >&2; exit 1
  fi
  if grep -E '^wait peer_removed |^fill ' "$OUT/calls" >/dev/null; then
    echo 'proceeded after a failed join' >&2; exit 1
  fi
  : >"$OUT/calls"
  rm -f "$OUT/logs/$target.offline" "$OUT/logs/$target.previous"
  disturb
  # Every survivor is serving before the batch waits for the first background join.
  awk -v full="$FULL_PEERS" -v short="$SHORT_PEERS" -v target="$target" '
    $1 == "start" && $2 != target && $4 == short { short_started++ }
    $1 == "start" && $2 != target && $4 == full { full_started++ }
    $1 == "wait" && $2 == "bootstrap_ended" && $3 != target {
      if (!returned && short_started != expected) exit 1
      if (returned && full_started != expected) exit 1
    }
    $1 == "start" && $2 == target { returned = 1 }
    END { if (short_started != expected || full_started != expected) exit 1 }
  ' expected="$(($(echo "$ALL_NODES" | wc -w) - 1))" "$OUT/calls" || { echo 'join blocked a later survivor start' >&2; exit 1; }
  if [ "$topology" = combined ]; then
    # A combined node must serve again before another is stopped.
    awk -v target="$target" '
      $1 == "docker" && $2 == "stop" && $NF != target { if (down) exit 1; down=$NF }
      $1 == "wait" && $2 == "started_since" && $3 == down { down="" }
      END { if (down) exit 1 }
    ' "$OUT/calls" || { echo 'more than one combined survivor stopped at a time' >&2; exit 1; }
  else
    # The first pair and one RS overlap; the other RS must remain up until the first serves.
    awk '
      $1 == "docker" && $2 == "stop" && $NF == "d0" && !first {first=NR}
      $1 == "docker" && $2 == "stop" && $NF == "d1" && !second {second=NR}
      $1 == "docker" && $2 == "stop" && $NF == "r0" && !rs {rs=NR; rsdown=1}
      $1 == "wait" && $2 == "started_since" && $3 == "r0" {rsdown=0}
      $1 == "docker" && $2 == "stop" && $NF == "r1" && rsdown {exit 1}
      $1 == "start" && $2 == "d0" && first && !started {started=NR}
      END {if (!first || !second || !rs || !started || !(first < started && second < started && rs < started)) exit 1}
    ' "$OUT/calls" || { echo 'DS and RS transitions did not overlap safely' >&2; exit 1; }
    grep -Fx 'request-pause d0' "$OUT/calls" >/dev/null || { echo 'first pair pause not requested' >&2; exit 1; }
    grep -Fx 'request-pause d1' "$OUT/calls" >/dev/null || { echo 'second pair pause not requested' >&2; exit 1; }
    awk '
      $0 == "request-pause d0" && !a {a=NR}
      $0 == "request-pause d1" && !b {b=NR}
      $0 == "pause d0" && !c {c=NR}
      END {if (!a || !b || !c || !(a < c && b < c)) exit 1}
    ' "$OUT/calls" || { echo 'pair pauses were not requested together' >&2; exit 1; }
    grep -Fx 'wait scale_probe_batch d2 down d0 d1 r0' "$OUT/calls" >/dev/null || { echo 'group write used no surviving DS' >&2; exit 1; }
    awk '
      $0 == "key-open down-r0" && !opened {opened=NR}
      $0 == "wait scale_caught_up r1 r0" && !checked {checked=NR}
      $0 == "docker stop -t 120 d0" && !stopped {stopped=NR}
      END {if (!opened || !checked || !stopped || !(opened < checked && checked < stopped)) exit 1}
    ' "$OUT/calls" || { echo 'RS checkpoint was not taken after preparing log windows' >&2; exit 1; }
    awk '
      $0 == "key-open down-d0" && !a {a=NR}
      $0 == "key-open down-d1" && !b {b=NR}
      $1 == "docker" && $2 == "stop" && $NF == "d0" && !c {c=NR}
      $1 == "docker" && $2 == "stop" && $NF == "d1" && !d {d=NR}
      $0 == "wait scale_probe_batch d2 down d0 d1 r0" && !p {p=NR}
      END {if (!a || !b || !c || !d || !p || !(a < c && b < c && c < p && d < p)) exit 1}
    ' "$OUT/calls" || { echo 'pair windows or write probe occurred in the wrong order' >&2; exit 1; }
  fi
  expected_windows=4
  [ "$(grep -c '^sleep 10$' "$OUT/calls")" -eq "$expected_windows" ] || { echo 'missing handshake grace after a transition' >&2; exit 1; }
  expected_checkpoints=$((1 + 2 * ($(echo "$RS_NODES" | wc -w) - 1)))
  [ "$(grep -c '^wait scale_caught_up ' "$OUT/calls")" -eq "$expected_checkpoints" ] || { echo 'a rolling RS lacked a checkpoint' >&2; exit 1; }
  grep -F 'wait scale_probe' "$OUT/calls" >/dev/null || { echo 'no confirmed write required while nodes are stopped' >&2; exit 1; }
  grep -Fx "dns-open $target" "$OUT/calls" >/dev/null || { echo 'absence DNS window not opened' >&2; exit 1; }
  grep -Fx 'dns-close' "$OUT/calls" >/dev/null || { echo 'absence DNS window not closed' >&2; exit 1; }
  grep -Fx "fill $target" "$OUT/calls" >/dev/null || { echo "backlog was not generated" >&2; exit 1; }
  awk -v target="$target" -v survivor="${RS_NODES%% *}" '
    $1 == "docker" && $2 == "stop" && $NF == target { stopped = 1 }
    $1 == "search" && $2 == survivor { if (!stopped) exit 1; baseline = 1 }
    END { if (!baseline) exit 1 }' "$OUT/calls" || { echo 'backlog baseline was not captured after stop' >&2; exit 1; }
  [ -s "$OUT/scale-checkpoint/absent-start.ldif" ] || { echo 'backlog baseline missing' >&2; exit 1; }
  awk -v target="$target" -v full="$FULL_PEERS" '
    $1 == "start" && $2 == target { returned = 1 }
    $1 == "start" && $2 != target && $4 == full && !returned { exit 1 }
    END { if (!returned) exit 1 }' "$OUT/calls" || { echo 'survivor rolled before the node returned' >&2; exit 1; }
  [ "$(grep -c '^wait peer_removed ' "$OUT/calls")" -eq "$(($(echo "$ALL_NODES" | wc -w) - 1))" ]
  [ "$(grep -c '^wait peer_present ' "$OUT/calls")" -eq "$(($(echo "$ALL_NODES" | wc -w) - 1))" ] || { echo 'return not checked on every survivor' >&2; exit 1; }
  for n in $ALL_NODES; do
    [ "$n" = "$target" ] && continue
    grep -Fx "window-close down-$n" "$OUT/calls" >/dev/null || { echo "down window for $n left open" >&2; exit 1; }
    grep -Fx "window-close up-$n" "$OUT/calls" >/dev/null || { echo "up window for $n left open" >&2; exit 1; }
    [ "$(grep -c "^start $n " "$OUT/calls")" -eq 2 ] || { echo "$n was not rolled twice" >&2; exit 1; }
  done
  if [ "$mode" = empty ]; then
    grep -Fx "docker volume rm vol-$target" "$OUT/calls" >/dev/null
    [ -s "$OUT/logs/$target.previous" ]
    grep -F "cert:d0:$target" "$OUT/calls" >/dev/null || { echo 'missing exact certificate window' >&2; exit 1; }
    grep -F "cert-peer:$target:172.18.0.9" "$OUT/calls" >/dev/null || { echo 'missing peer certificate window' >&2; exit 1; }
  else
    if grep -F 'docker volume rm' "$OUT/calls" >/dev/null; then echo 'kept volume was removed' >&2; exit 1; fi
  fi
  if [ "$topology" = combined ]; then
    grep -Fx "identity $target" "$OUT/calls" >/dev/null
    grep -Fx "window dns:$target dns:d0" "$OUT/calls" >/dev/null || { echo 'rolling combined node lacks its DNS allowance' >&2; exit 1; }
    grep -Fx "pause $target" "$OUT/calls" >/dev/null
    grep -Fx "resume $target" "$OUT/calls" >/dev/null
    grep -Fx 'wait rolling_clients_running d0' "$OUT/calls" >/dev/null || { echo 'checkpoint writer was not checked' >&2; exit 1; }
    grep -Fx 'wait scale_probe d0 d3 checkpoint-down' "$OUT/calls" >/dev/null || { echo 'checkpoint write was not confirmed' >&2; exit 1; }
    awk '
      $0 == "request-pause d1" && !a {a=NR}
      $0 == "request-pause d2" && !b {b=NR}
      $0 == "docker stop -t 120 d3" && !c {c=NR}
      $0 == "resume d1" && !d {d=NR}
      $0 == "resume d2" && !e {e=NR}
      END {if (!a || !b || !c || !d || !e || !(a < c && b < c && c < d && c < e)) exit 1}
    ' "$OUT/calls" || { echo 'other writers were not held until the node stopped' >&2; exit 1; }
    awk '
      $1 == "start" && $2 == "d3" && !returned {returned=NR}
      returned && $0 == "request-pause d3" && !held {held=NR}
      held && $0 == "docker stop -t 120 d0" && !stopped {stopped=NR}
      stopped && $0 == "resume d3" && !released {released=NR}
      END {if (!returned || !held || !stopped || !released || !(returned < held && held < stopped && stopped < released)) exit 1}
    ' "$OUT/calls" || { echo 'returned writer was not held during scale-out checkpoint' >&2; exit 1; }
  fi
  [ ! -e "$OUT/logs/$target.offline" ]
  # A failed observer query identifies the node and status in the checkpoint diagnostic.
  (
    scale_monitor_search() { [ "$1" != observer ] || return 124; echo baseline; }
    scale_checkpoint_init observer departing
    if scale_caught_up observer departing; then echo 'failed checkpoint query accepted' >&2; exit 1; fi
    grep -F 'observer' "$OUT/scale-checkpoint/check.txt" >/dev/null || { echo 'checkpoint diagnostic omitted observer' >&2; exit 1; }
    grep -F '124' "$OUT/scale-checkpoint/check.txt" >/dev/null || { echo 'checkpoint diagnostic omitted failure status' >&2; exit 1; }
  )
  backlog_ldif "scale-backlog-$target" "$BASE_DN" 1 3 >"$OUT/fill.ldif"
  [ "$(grep -c '^changetype: modify$' "$OUT/fill.ldif")" -eq 3 ] || { echo 'wrong number of fill writes' >&2; exit 1; }
  grep -Fx 'description: scale-3' "$OUT/fill.ldif" >/dev/null || { echo 'fill values are not distinct' >&2; exit 1; }
  scale_probe d0 d1 down || { echo 'confirmed probe was rejected' >&2; exit 1; }
  grep -Fx 'dn: uid=scale-down-d1,ou=People,dc=example,dc=com' "$OUT/probe.ldif" >/dev/null
  if (FAIL_PROBE=1; scale_probe d0 d1 up); then echo 'failed LDAP write accepted' >&2; exit 1; fi
  if [ "$topology" = rs534 ]; then
    scale_probe_batch d2 down d0 d1 r0 || { echo 'confirmed batch probe was rejected' >&2; exit 1; }
    [ "$(grep -c '^dn: uid=scale-down-' "$OUT/probe.ldif")" -eq 3 ] || { echo 'batch lacks a write per stopped node' >&2; exit 1; }
    grep -Fx 'dn: uid=scale-down-r0,ou=People,dc=example,dc=com' "$OUT/probe.ldif" >/dev/null || { echo 'batch lacks the RS write' >&2; exit 1; }
    if (FAIL_PROBE=1; scale_probe_batch d2 up d0 d1 r0); then echo 'failed batch accepted' >&2; exit 1; fi
  fi
)
for topology in rs534 combined; do
  for mode in kept empty; do run_case "$topology" "$mode"; done
done
# The fast monitor connects only to a numeric port published on IPv4 loopback.
(
  OUT="$OUT/loopback"; mkdir -p "$OUT/scale-checkpoint"
  TOPOLOGY=combined SCALE_VOLUME=kept DS_NODES='d0 d1 d2' RS_NODES=$DS_NODES RS_ONLY=
  ALL_NODES=$DS_NODES PEERS=d0,d1,d2 ROOT_PASSWORD=secret ROLLING_END_MS=2000000000000
  . "$HERE/disturb-scale.sh"
  docker() { [ "$1" = port ] || return 1; printf '%s\n' "$MAPPING"; }
  python3() { printf '%s\n' "$*" >"$OUT/arguments"; cat >/dev/null; printf 'snapshot\n'; }
  MAPPING='127.0.0.1:12345'
  [ "$(scale_monitor_search d0)" = snapshot ] || { echo 'loopback monitor rejected' >&2; exit 1; }
  grep -F '12345 2000000000000' "$OUT/arguments" >/dev/null || { echo 'port or deadline not passed' >&2; exit 1; }
  if grep -F 'secret' "$OUT/arguments" >/dev/null; then echo 'password appeared in process arguments' >&2; exit 1; fi
  for MAPPING in '0.0.0.0:12345' '127.0.0.1:not-a-port' '127.0.0.1:12345 extra'; do
    if scale_monitor_search d0 >/dev/null 2>&1; then echo "unsafe port mapping accepted: $MAPPING" >&2; exit 1; fi
  done
)
# Repeated comparisons must use one immutable reference, and retain every result.
(
  OUT="$OUT/checkpoint-history"; mkdir -p "$OUT/scale-checkpoint"
  TOPOLOGY=combined SCALE_VOLUME=kept DS_NODES='d0 d1 d2' RS_NODES=$DS_NODES RS_ONLY=
  ALL_NODES=$DS_NODES PEERS=d0,d1,d2 BASE_DN=dc=example,dc=com
  DISTURB_TIMEOUT=900 ROLLING_END=$((SECONDS + 900))
  . "$HERE/disturb-scale.sh"
  echo '1 d0' >"$OUT/server-ids.txt"
  scale_monitor_search() {
    echo "$1" >>"$OUT/queries"
    local csn=000001a1a1e836e400010000000a
    if [ "$1" = observer ] && [ ! -f "$OUT/first-observer" ]; then
      csn=000001a1a1e836e4000100000009; touch "$OUT/first-observer"
    fi
    printf 'dn: cn=one,cn=monitor\ndomain-name: %s\nreplicationServer-database: 1\nlast-change: %s\n\n' "$BASE_DN" "$csn"
  }
  scale_checkpoint_init observer departing
  if scale_caught_up observer departing; then echo 'older position accepted' >&2; exit 1; fi
  scale_caught_up observer departing || { echo 'caught-up position rejected' >&2; exit 1; }
  [ "$(grep -c '^departing$' "$OUT/queries")" -eq 2 ] || { echo 'reference was resampled before catch-up or not refreshed after it' >&2; exit 1; }
  [ "$(grep -c '^observer$' "$OUT/queries")" -eq 3 ] || { echo 'missing observer retry or fresh confirmation' >&2; exit 1; }
  grep -F 'needs' "$SCALE_CHECKPOINT_DIR/attempt-0001.compare" >/dev/null || { echo 'first comparison was not retained' >&2; exit 1; }
  [ -f "$SCALE_CHECKPOINT_DIR/attempt-0002.compare" ] || { echo 'second comparison was not retained' >&2; exit 1; }
  [ "$(grep -c 'observer' "$SCALE_CHECKPOINT_DIR/history.txt")" -ge 2 ] || { echo 'query history missing' >&2; exit 1; }
  scale_monitor_search() { echo 'timeout=query' >&2; return 124; }
  if scale_caught_up observer departing; then echo 'timed-out query accepted' >&2; exit 1; fi
  grep -F 'timeout=query' "$SCALE_CHECKPOINT_DIR/history.txt" >/dev/null || { echo 'query timeout not distinguished' >&2; exit 1; }
  scale_monitor_search() { echo 'timeout=load' >&2; return 124; }
  if scale_caught_up observer departing; then echo 'reported load deadline accepted' >&2; exit 1; fi
  grep -F 'timeout=load' "$SCALE_CHECKPOINT_DIR/history.txt" >/dev/null || { echo 'reported load deadline not distinguished' >&2; exit 1; }
  scale_monitor_search() { return 124; }
  ROLLING_END=$SECONDS
  if scale_caught_up observer departing; then echo 'load deadline accepted' >&2; exit 1; fi
  grep -F 'timeout=load' "$SCALE_CHECKPOINT_DIR/history.txt" >/dev/null || { echo 'load deadline not distinguished' >&2; exit 1; }
)
# A caught-up old checkpoint cannot authorize stopping a server whose current position advanced.
(
  OUT="$OUT/checkpoint-freshness"; mkdir -p "$OUT/scale-checkpoint"
  TOPOLOGY=combined SCALE_VOLUME=kept DS_NODES='d0 d1 d2' RS_NODES=$DS_NODES RS_ONLY=
  ALL_NODES=$DS_NODES PEERS=d0,d1,d2 BASE_DN=dc=example,dc=com
  DISTURB_TIMEOUT=900 ROLLING_END=$((SECONDS + 900))
  . "$HERE/disturb-scale.sh"
  echo '1 d0' >"$OUT/server-ids.txt"
  scale_monitor_search() {
    local csn=000001a1a1e836e400010000000a
    if [ "$1" = departing ]; then
      if [ -f "$OUT/reference-read" ]; then csn=000001a1a1e836e400010000000b; else touch "$OUT/reference-read"; fi
    elif [ ! -f "$OUT/observer-read" ]; then csn=000001a1a1e836e4000100000009; touch "$OUT/observer-read"
    elif [ -f "$OUT/observer-caught-up" ]; then csn=000001a1a1e836e400010000000b
    fi
    printf 'dn: cn=one,cn=monitor\ndomain-name: %s\nreplicationServer-database: 1\nlast-change: %s\n\n' "$BASE_DN" "$csn"
  }
  scale_checkpoint_init observer departing
  if scale_caught_up observer departing; then echo 'lagging observer accepted' >&2; exit 1; fi
  if scale_caught_up observer departing; then echo 'stale checkpoint accepted' >&2; exit 1; fi
  touch "$OUT/observer-caught-up"
  scale_caught_up observer departing || { echo 'fresh checkpoint rejected' >&2; exit 1; }
  # If the first two searches already match, the reference is fresh and must not be queried twice.
  scale_monitor_search() {
    echo "$1" >>"$OUT/fast-queries"
    printf 'dn: cn=one,cn=monitor\ndomain-name: %s\nreplicationServer-database: 1\nlast-change: 000001a1a1e836e400010000000b\n\n' "$BASE_DN"
  }
  scale_checkpoint_init observer departing
  scale_caught_up observer departing || { echo 'fresh fast checkpoint rejected' >&2; exit 1; }
  [ "$(wc -l <"$OUT/fast-queries")" -eq 2 ] || { echo 'fresh checkpoint made redundant searches' >&2; exit 1; }
)
# All group stops start before any returns, and any failed stop fails the group.
(
  OUT="$OUT/parallel-stops"; mkdir -p "$OUT"
  TOPOLOGY=rs534 SCALE_VOLUME=kept DS_NODES='d0 d1 d2' RS_ONLY='r0 r1 r2' RS_NODES=$RS_ONLY
  ALL_NODES="$DS_NODES $RS_ONLY" PEERS=d0,d1,d2,r0,r1,r2
  . "$HERE/disturb-scale.sh"
  docker() {
    printf '%s\n' "$4" >>"$OUT/started"
    local end=$((SECONDS + 5))
    while [ "$(wc -l <"$OUT/started")" -lt 3 ]; do [ "$SECONDS" -lt "$end" ] || return 1; sleep 0.05; done
    [ "$4" != "${FAIL_NODE:-}" ]
  }
  scale_stop_group d0 d1 r0 || { echo 'group stops were not concurrent' >&2; exit 1; }
  : >"$OUT/started"
  if (FAIL_NODE=d1; scale_stop_group d0 d1 r0); then echo 'failed group stop was accepted' >&2; exit 1; fi
  [ "$(wc -l <"$OUT/started")" -eq 3 ] || { echo 'failed stop abandoned another operation' >&2; exit 1; }
)
# Exercise the real fill pipeline: all 50,001 records reach one client, and a failed
# client must not produce a successful backlog report.
(
  OUT="$OUT/fill-test"; mkdir -p "$OUT/scale-progress" "$OUT/scale-checkpoint" "$OUT/load"
  TOPOLOGY=combined SCALE_VOLUME=kept DS_NODES='d0 d1 d2 d3' RS_NODES=$DS_NODES RS_ONLY=
  ALL_NODES=$DS_NODES PEERS=d0,d1,d2,d3 NETWORK=fixture IMAGE=fixture
  BASE_DN=dc=example,dc=com ROOT_PASSWORD=password CLIENT_JAVA_ARGS=-Xmx128m
  DISTURB_TIMEOUT=900 ROLLING_END=$((SECONDS + 900)) ROLLING_END_MS=2000000000000
  . "$HERE/disturb-scale.sh"
  scale_wait() { shift; "$@"; }
  admin_search() { echo writer; }
  ldaps_search() { printf '%s\n' "$2" >"$OUT/writer-dn"; echo writer; }
  scale_fence() { [ "${FAIL_FENCE:-0}" = 0 ]; }
  scale_coverage() { [ "${FAIL_COVERAGE:-0}" = 0 ]; }
  rolling_kill_active() { :; }
  docker() {
    case $1 in
      exec) cat >/dev/null ;;
      run) cat >"$OUT/fill.ldif"; [ "${FAIL_FILL:-0}" = 0 ] ;;
      *) return 1 ;;
    esac
  }
  scale_backlog
  grep -Fx 'uid=scale-backlog-d3,ou=People,dc=example,dc=com' "$OUT/writer-dn" >/dev/null || { echo 'final fill entry was not read' >&2; exit 1; }
  [ "$(grep -c '^changetype: modify$' "$OUT/fill.ldif")" -eq 50000 ]
  grep -Fx 'description: scale-50001' "$OUT/fill.ldif" >/dev/null
  grep -Fx '50001 changes while the server was absent' "$OUT/scale-checkpoint/backlog.txt" >/dev/null
  rm "$OUT/scale-checkpoint/backlog.txt"
  if (FAIL_FILL=1; scale_backlog); then echo 'failed fill was accepted' >&2; exit 1; fi
  [ ! -e "$OUT/scale-checkpoint/backlog.txt" ] || { echo 'failed fill wrote a success report' >&2; exit 1; }
  if (FAIL_FENCE=1; scale_backlog); then echo 'lagging RS fence was accepted' >&2; exit 1; fi
  [ ! -e "$OUT/scale-checkpoint/backlog.txt" ] || { echo 'lagging RS wrote a success report' >&2; exit 1; }
  if (FAIL_COVERAGE=1; scale_backlog); then echo 'incomplete RS changelog was accepted' >&2; exit 1; fi
  [ ! -e "$OUT/scale-checkpoint/backlog.txt" ] || { echo 'missing changes wrote a success report' >&2; exit 1; }
)
echo 'scale lifecycle cases passed'
