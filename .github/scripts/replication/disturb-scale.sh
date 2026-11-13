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

# Scale down one RS (or one combined node), roll the survivors with the shorter peer list,
# and scale up on its retained or a new volume. A DS's load clients finish before it stops.
case $TOPOLOGY in
  rs534) REMOVED=${RS_ONLY##* }; REMOVED_ROLE=replication ;;
  combined) REMOVED=${DS_NODES##* }; REMOVED_ROLE=combined ;;
  *) echo "scale requires rs534 or combined" >&2; exit 2 ;;
esac
case $SCALE_VOLUME in kept|empty) ;; *) echo "unknown scale volume $SCALE_VOLUME" >&2; exit 2 ;; esac
DISTURBED=$REMOVED
DISTURB_STEPS=1
SURVIVORS=
for node in $ALL_NODES; do [ "$node" = "$REMOVED" ] || SURVIVORS="$SURVIVORS $node"; done
SHORT_PEERS=$(echo "$SURVIVORS" | xargs | tr ' ' ',')
FULL_PEERS=$PEERS
SURVIVING_RS=${RS_NODES%% *}

# Freeze the departing RS's positions while waiting for catch-up. Once reached, take a
# fresh sample and verify it before stopping the RS; a stale snapshot must not authorize it.
scale_checkpoint_init() { # <survivor> <departing>
  SCALE_CHECKPOINT_NUMBER=$(( ${SCALE_CHECKPOINT_NUMBER:-0} + 1 ))
  SCALE_CHECKPOINT_DIR="$OUT/scale-checkpoint/$(printf '%03d' "$SCALE_CHECKPOINT_NUMBER")-$1-$2"
  SCALE_CHECKPOINT_ATTEMPT=0
  mkdir -p "$SCALE_CHECKPOINT_DIR"
}

# The administration port is published on loopback for scale rows. A native client
# avoids starting another JVM for every checkpoint and binds only to that loopback port.
scale_monitor_search() { # <node>
  local mapping port
  mapping=$(docker port "$1" 4444/tcp) || return 1
  case $mapping in 127.0.0.1:*) port=${mapping#127.0.0.1:} ;; *) echo 'admin port is not loopback-only' >&2; return 1 ;; esac
  case $port in ''|*[!0-9]*) echo 'invalid admin port' >&2; return 1 ;; esac
  printf '%s' "$ROOT_PASSWORD" | python3 -I "$HERE/scale-monitor.py" "$port" "$ROLLING_END_MS"
}

scale_checkpoint_query() { # <node> <output> <error> <role>
  local node=$1 output=$2 errors=$3 role=$4 rc=0 start=$SECONDS remaining=$((ROLLING_END - SECONDS)) now timeout=none
  now=$(date +%s) || return 1
  scale_monitor_search "$node" >"$output" 2>"$errors" || rc=$?
  if [ "$rc" -eq 124 ]; then
    if grep -F 'timeout=query' "$errors" >/dev/null; then timeout=query
    elif grep -F 'timeout=load' "$errors" >/dev/null || [ "$SECONDS" -ge "$ROLLING_END" ]; then timeout=load
    else timeout=unknown
    fi
  fi
  printf 'attempt=%s role=%s node=%s started=%s elapsed=%s remaining-start=%s remaining-end=%s exit=%s timeout=%s\n' \
    "$SCALE_CHECKPOINT_ATTEMPT" "$role" "$node" "$now" "$((SECONDS - start))" "$remaining" "$((ROLLING_END - SECONDS))" "$rc" "$timeout" \
    >>"$SCALE_CHECKPOINT_DIR/history.txt" || return 1
  return "$rc"
}

scale_caught_up() { # <survivor RS> <departing RS>
  local survivor=$1 departing=$2 dir="$SCALE_CHECKPOINT_DIR" prefix rc reference_new=0
  SCALE_CHECKPOINT_ATTEMPT=$((SCALE_CHECKPOINT_ATTEMPT + 1))
  prefix="$dir/attempt-$(printf '%04d' "$SCALE_CHECKPOINT_ATTEMPT")"
  if [ ! -f "$dir/reference.ldif" ]; then
    if scale_checkpoint_query "$departing" "$prefix.reference.ldif" "$prefix.reference.err" reference; then
      cp "$prefix.reference.ldif" "$dir/reference.ldif" || return 1
      reference_new=1
    else
      rc=$?; printf 'checkpoint query of %s failed (exit %s)\n' "$departing" "$rc" >"$OUT/scale-checkpoint/check.txt"; return 1
    fi
  fi
  if scale_checkpoint_query "$survivor" "$prefix.observer.ldif" "$prefix.observer.err" observer; then :
  else
    rc=$?; printf 'checkpoint query of %s failed (exit %s)\n' "$survivor" "$rc" >"$OUT/scale-checkpoint/check.txt"; return 1
  fi
  rc=0
  python3 -I "$HERE/changelog-checkpoint.py" "$BASE_DN" "$OUT/server-ids.txt" \
    "$dir/reference.ldif" "$prefix.observer.ldif" >"$prefix.compare" 2>&1 || rc=$?
  printf 'attempt=%s comparison-exit=%s\n' "$SCALE_CHECKPOINT_ATTEMPT" "$rc" >>"$dir/history.txt" || return 1
  cp "$prefix.compare" "$OUT/scale-checkpoint/check.txt" || return 1
  [ "$rc" -eq 0 ] || return "$rc"
  # The first successful comparison already sampled the departing node immediately
  # before the observer. Only a reference retained across retries needs refreshing.
  [ "$reference_new" -eq 0 ] || return 0
  # A writer may have advanced while the frozen reference was catching up. Re-sample
  # the departing node, then the survivor, before accepting this checkpoint.
  if scale_checkpoint_query "$departing" "$prefix.fresh-reference.ldif" "$prefix.fresh-reference.err" fresh-reference; then :
  else
    rc=$?; printf 'checkpoint query of %s failed (exit %s)\n' "$departing" "$rc" >"$OUT/scale-checkpoint/check.txt"; return 1
  fi
  if scale_checkpoint_query "$survivor" "$prefix.fresh-observer.ldif" "$prefix.fresh-observer.err" fresh-observer; then :
  else
    rc=$?; printf 'checkpoint query of %s failed (exit %s)\n' "$survivor" "$rc" >"$OUT/scale-checkpoint/check.txt"; return 1
  fi
  rc=0
  python3 -I "$HERE/changelog-checkpoint.py" "$BASE_DN" "$OUT/server-ids.txt" \
    "$prefix.fresh-reference.ldif" "$prefix.fresh-observer.ldif" >"$prefix.fresh-compare" 2>&1 || rc=$?
  printf 'attempt=%s fresh-comparison-exit=%s\n' "$SCALE_CHECKPOINT_ATTEMPT" "$rc" >>"$dir/history.txt" || return 1
  cp "$prefix.fresh-compare" "$OUT/scale-checkpoint/check.txt" || return 1
  [ "$rc" -gt 1 ] || cp "$prefix.fresh-reference.ldif" "$dir/reference.ldif" || return 1
  return "$rc"
}

scale_checkpoint_wait() { # <description> <survivor> <departing>
  local what=$1 survivor=$2 departing=$3
  scale_checkpoint_init "$survivor" "$departing" || return 1
  scale_wait "$what" scale_caught_up "$survivor" "$departing"
}

# A dedicated LDAP client applies 50,001 distinct modifications after the stop. The tool
# exits at the first failed operation, so a zero exit proves the whole generated stream was
# acknowledged. A fresh RS may adopt the generation ID immediately: the backlog alone does
# not establish the mismatched-generation condition of #1080.
backlog_ldif() { # <uid> <base DN> <first> <last>
  awk -v uid="$1" -v base="$2" -v first="$3" -v last="$4" 'BEGIN {
    for (i = first; i <= last; i++) {
      printf "dn: uid=%s,ou=People,%s\nchangetype: modify\nreplace: description\ndescription: scale-%d\n-\n\n", uid, base, i
    }
  }'
}

scale_fence() { # <surviving RS>
  local node=$1 dir="$OUT/scale-checkpoint" history prefix started start=$SECONDS query_rc=0 compare_rc=not-run line file
  SCALE_FENCE_ATTEMPT=$(( ${SCALE_FENCE_ATTEMPT:-0} + 1 ))
  history="$dir/fence-$node/history.txt"
  mkdir -p "$dir/fence-$node" || return 1
  prefix="$dir/fence-$node/attempt-$(printf '%04d' "$SCALE_FENCE_ATTEMPT")"
  started=$(date +%s) || return 1
  printf 'attempt=%s phase=start started=%s remaining=%s\n' "$SCALE_FENCE_ATTEMPT" "$started" "$((ROLLING_END - SECONDS))" >>"$history" || return 1
  scale_monitor_search "$node" >"$prefix.ldif" 2>"$prefix.query.txt" || query_rc=$?
  if [ "$query_rc" -eq 0 ]; then
    compare_rc=0
    python3 -I "$HERE/scale-fence.py" "$BASE_DN" "$dir/writer.ldif" "$prefix.ldif" >"$prefix.compare.txt" 2>&1 || compare_rc=$?
    cp "$prefix.ldif" "$dir/fence-$node.ldif" || return 1
    cp "$prefix.compare.txt" "$dir/fence-$node.txt" || return 1
  else
    cp "$prefix.query.txt" "$dir/fence-$node.txt" || return 1
  fi
  printf 'attempt=%s phase=end elapsed=%s remaining=%s query_exit=%s compare_exit=%s\n' \
    "$SCALE_FENCE_ATTEMPT" "$((SECONDS - start))" "$((ROLLING_END - SECONDS))" "$query_rc" "$compare_rc" >>"$history" || return 1
  for file in "$prefix.query.txt" "$prefix.compare.txt"; do
    [ -f "$file" ] || continue
    while IFS= read -r line || [ -n "$line" ]; do
      printf 'attempt=%s detail=%s\n' "$SCALE_FENCE_ATTEMPT" "$line" >>"$history" || return 1
    done <"$file"
  done
  [ "$query_rc" -eq 0 ] && [ "$compare_rc" = 0 ]
}

# Copying an online changelog can catch a partially written block: a failed parse or an
# incomplete interval is retried until the load deadline, never accepted as a complete fill.
scale_coverage() { # <surviving RS>
  local node=$1 dir="$OUT/scale-checkpoint"
  docker cp "$node:/opt/opendj/data/changelogDb" - \
    | python3 -I "$HERE/scale-deadline.py" "$ROLLING_END_MS" python3 -I "$HERE/scale-coverage.py" \
      "$BASE_DN" "$dir/first.ldif" "$dir/writer.ldif" >"$dir/coverage-$node.txt" 2>&1
}

run_fill() { # <writer> <uid> <first> <last> <log>
  local writer=$1 uid=$2 first=$3 last=$4 log=$5 remaining=$((ROLLING_END - SECONDS))
  [ "$remaining" -gt 0 ] || return 1
  printf 'load-scale-fill\n' >"$OUT/load/fill.active" || return 1
  [ ! -e "$OUT/load/deadline" ] || return 1
  # The container checks the absolute deadline after Docker has created it; a late
  # launch exits before writing. GNU timeout reaches the Java child as well.
  if ! backlog_ldif "$uid" "$BASE_DN" "$first" "$last" \
    | docker run --rm -i --name load-scale-fill --network "$NETWORK" \
        -e OPENDJ_JAVA_ARGS="$CLIENT_JAVA_ARGS" --entrypoint /bin/sh "$IMAGE" -c '
          deadline=$1; writer=$2; password=$3
          left=$((deadline - $(date +%s)))
          [ "$left" -gt 0 ] || exit 1
          exec /usr/bin/timeout "$left" /opt/opendj/bin/ldapmodify --noPropertiesFile \
            --hostname "$writer" --port 1636 --bindDN "cn=Directory Manager" \
            --bindPassword "$password" --useSsl --trustAll
        ' sh "$((ROLLING_END_MS / 1000))" "$writer" "$ROOT_PASSWORD" \
        >"$log" 2>&1; then
    rolling_kill_active fill || true
    return 1
  fi
  [ ! -e "$OUT/load/deadline" ] || return 1
  rm "$OUT/load/fill.active" || return 1
}

scale_backlog() {
  local writer=${DS_NODES%% *} uid="scale-backlog-$REMOVED" count=50001 node
  [ "$writer" != "$REMOVED" ] || { fail 'no writer for the backlog'; return 1; }
  scale_wait 'backlog entry is created' scale_probe "$writer" "$REMOVED" backlog || return 1
  run_fill "$writer" "$uid" 1 1 "$OUT/scale-checkpoint/fill-first.log" || return 1
  # Save the first modification's own CSN before the remaining values replace it.
  ldaps_search "$writer" "uid=$uid,ou=People,$BASE_DN" base '(objectClass=*)' description ds-sync-hist \
    >"$OUT/scale-checkpoint/first.ldif" || return 1
  run_fill "$writer" "$uid" 2 "$count" "$OUT/scale-checkpoint/fill.log" || return 1
  # A successful response may still sit behind an earlier pending local change. The
  # final value's history carries its own CSN, unlike the DS's potentially stale state.
  ldaps_search "$writer" "uid=$uid,ou=People,$BASE_DN" base '(objectClass=*)' description ds-sync-hist \
    >"$OUT/scale-checkpoint/writer.ldif" || return 1
  for node in $RS_NODES; do
    [ "$node" != "$REMOVED" ] || continue
    SCALE_FENCE_ATTEMPT=0
    scale_wait_fence "$node reaches the final fill CSN" scale_fence "$node" || return 1
    scale_wait "$node holds the complete fill" scale_coverage "$node" || return 1
  done
  printf '%s changes while the server was absent\n' "$count" >"$OUT/scale-checkpoint/backlog.txt"
}

is_directory_node() { case " $DS_NODES " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }
# Record successful and failed waits, including short ones, for the CI time budget.
scale_timed_wait() { # <seconds> <what> <predicate> [<argument>...]
  local start=$SECONDS what=$2 rc=0
  wait_until "$@" || rc=$?
  printf '%s\t%s\t%s\n' "$((SECONDS - start))" "$rc" "$what" >>"$OUT/scale-timings.tsv" || return 1
  return "$rc"
}

scale_wait() { # <what> <predicate> [<argument>...]
  local remaining=$((ROLLING_END - SECONDS))
  [ "$remaining" -gt 0 ] || { fail "scale change exceeded the load"; return 1; }
  [ "$remaining" -le "$DISTURB_TIMEOUT" ] || remaining=$DISTURB_TIMEOUT
  scale_timed_wait "$remaining" "$@" || return 1
  [ "$SECONDS" -lt "$ROLLING_END" ] || { fail "scale change exceeded the load"; return 1; }
}

# Fence catch-up may use all remaining load time; other individual waits stay bounded.
scale_wait_fence() { # <what> <predicate> [<argument>...]
  local remaining=$((ROLLING_END - SECONDS))
  [ "$remaining" -gt 0 ] || { fail "scale change exceeded the load"; return 1; }
  scale_timed_wait "$remaining" "$@" || return 1
  [ "$SECONDS" -lt "$ROLLING_END" ] || { fail "scale change exceeded the load"; return 1; }
}

# A successful, unique LDAP add while a node is down proves a write really happened in
# that interval; the final offline comparison proves it reached every replica.
scale_probe() { # <writer DS> <rolling node> <down|up>
  scale_probe_batch "$1" "$3" "$2"
}

# One LDAP session confirms a distinct write for each node while the whole group is down.
scale_probe_batch() { # <writer DS> <down|up> <rolling node>...
  local writer=$1 direction=$2 node uid name
  shift 2
  [ "$#" -gt 0 ] || return 1
  name=$1
  [ "$#" -eq 1 ] || name="batch-$name"
  {
    for node in "$@"; do
      uid="scale-$direction-$node"
      printf 'dn: uid=%s,ou=People,%s\nobjectClass: top\nobjectClass: person\nobjectClass: organizationalPerson\nobjectClass: inetOrgPerson\nuid: %s\ncn: Scale Probe\nsn: ScaleProbe\n\n' \
        "$uid" "$BASE_DN" "$uid"
    done
  } | docker exec -i "$writer" /bin/sh -c '
        deadline=$1; password=$2
        left=$((deadline - $(date +%s)))
        [ "$left" -gt 0 ] || exit 1
        exec /usr/bin/timeout "$left" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 1636 \
          --bindDN "cn=Directory Manager" --bindPassword "$password" --useSsl --trustAll --defaultAdd
      ' sh "$((ROLLING_END_MS / 1000))" "$ROOT_PASSWORD" >"$OUT/scale-progress/$direction-$name.txt" 2>&1
}

# During a combined-node checkpoint, pause other load writers while keeping the
# observer's clients running. This makes their high-water marks finite while an actual
# write still succeeds. Held writers remain paused until the departing node is stopped.
scale_hold_writers() { # <observer DS> <departing DS> <down|up>
  local observer=$1 departing=$2 direction=$3 node
  SCALE_HELD_WRITERS=
  for node in $DS_NODES; do
    [ "$node" != "$observer" ] && [ "$node" != "$departing" ] || continue
    # The removed node is absent during scale-in, but is a writer again during scale-out.
    [ "$direction" != down ] || [ "$node" != "$REMOVED" ] || continue
    rolling_request_pause "$node" || return 1
    SCALE_HELD_WRITERS="$SCALE_HELD_WRITERS $node"
  done
  for node in $SCALE_HELD_WRITERS; do rolling_pause "$node" || return 1; done
  scale_wait "$observer has active load clients" rolling_clients_running "$observer" || return 1
  mkdir -p "$OUT/scale-progress" || return 1
  scale_wait "$observer accepts a checkpoint write" scale_probe "$observer" "$departing" "checkpoint-$direction" || return 1
}

scale_release_writers() {
  local node failed=
  for node in ${SCALE_HELD_WRITERS:-}; do rolling_resume "$node" || failed=1; done
  SCALE_HELD_WRITERS=
  [ -z "$failed" ]
}

# A restart removes the old container and with it its bootstrap timings. Keep the
# Docker timestamp on each line so events from different processes can be correlated.
scale_capture_bootstrap() { # <node> <generation>
  mkdir -p "$OUT/bootstrap" || return 0
  docker logs --timestamps "$1" >"$OUT/bootstrap/$2-$1.log" 2>&1 || :
}

# Save the stopped RS before its container or volume is replaced, so an eventual
# gap can be compared with its pre-restart changelog in the uploaded artifact.
scale_capture_changelog() { # <node> <generation>
  local node=$1 generation=$2 start=$SECONDS
  local snapshot="$OUT/scale-checkpoint/$generation-$node-changelog.tar"
  docker cp "$node:/opt/opendj/data/changelogDb" - >"$snapshot.partial" || {
    fail "cannot save the $generation changelog of $node"; return 1;
  }
  mv "$snapshot.partial" "$snapshot" || return 1
  printf '%s\t0\t%s\n' "$((SECONDS - start))" "save $generation changelog of $node" >>"$OUT/scale-timings.tsv" || return 1
}

collect_scale_bootstrap() {
  local node
  for node in $ALL_NODES; do scale_capture_bootstrap "$node" final; done
}

# FileReplicaDB keeps only the newest ReplicaOfflineMsg per replica. Save these
# small state files between transitions, before a later stop overwrites them.
scale_capture_offline_states() { # <phase> <RS>...
  local phase=$1 node target captured
  shift
  for node in "$@"; do
    target="$OUT/scale-checkpoint/offline-$phase-$node.txt"
    captured=$(date +%s) || return 1
    printf 'capture-start-ms %s000\n' "$captured" >"$target.partial" || return 1
    docker exec "$node" sh -c '
      set -e
      cd /opt/opendj/data/changelogDb
      echo domains.state
      cat domains.state
      for file in *.dom/*.server/offline.state; do
        [ -f "$file" ] || continue
        printf "%s " "$file"
        cat "$file"
        echo
      done
    ' >>"$target.partial" || { fail "could not save offline states of $node after $phase"; return 1; }
    mv "$target.partial" "$target" || return 1
  done
}

scale_roll() { # <node> <peer list>
  local node=$1 peers=$2 role=directory from observer= writer= candidate direction=up
  [ "$peers" != "$SHORT_PEERS" ] || direction=down
  for candidate in $RS_NODES; do
    [ "$candidate" != "$REMOVED" ] && [ "$candidate" != "$node" ] || continue
    observer=$candidate; break
  done
  [ -n "$observer" ] || { fail "no replication server remains while rolling $node"; return 1; }
  for candidate in $DS_NODES; do
    [ "$candidate" != "$REMOVED" ] && [ "$candidate" != "$node" ] || continue
    writer=$candidate; break
  done
  [ -n "$writer" ] || { fail "no directory server remains while rolling $node"; return 1; }
  [ "$TOPOLOGY" != combined ] || role=combined
  case " $RS_ONLY " in *" $node "*) role=replication ;; esac
  if is_directory_node "$node"; then rolling_pause "$node" || return 1; fi
  SCALE_HELD_WRITERS=
  if [ "$TOPOLOGY" = combined ]; then
    scale_hold_writers "$observer" "$node" "$direction" || { scale_release_writers || :; return 1; }
  fi
  case " $RS_NODES " in
    *" $node "*) scale_checkpoint_wait "$observer reaches a checkpoint of $node" "$observer" "$node" || { scale_release_writers || :; return 1; } ;;
  esac
  case " $RS_NODES " in
    *" $node "*) window_open_key "$direction-$node" "dns:$REMOVED" "dns:$node" || { scale_release_writers || :; return 1; } ;;
    *) window_open_key "$direction-$node" "dns:$REMOVED" || { scale_release_writers || :; return 1; } ;;
  esac
  from=$(date +%s) || { scale_release_writers || :; return 1; }
  docker stop -t 120 "$node" >/dev/null || { scale_release_writers || :; return 1; }
  scale_release_writers || return 1
  mkdir -p "$OUT/scale-progress" || return 1
  scale_wait "another replica writes while $node is stopped" scale_probe "$writer" "$node" "$direction" || return 1
  scale_capture_bootstrap "$node" "$direction"
  docker rm "$node" >/dev/null || return 1
  PEERS="$peers" start_server "$node" "$role" || return 1
  scale_wait "$node serves again" started_since "$node" "$from" || return 1
  if is_directory_node "$node"; then rolling_resume "$node" || return 1; fi
}

# Launch every stop before waiting: slow graceful shutdowns share the same window.
# Wait for all jobs even after a failure so none survives a failed transition.
scale_stop_group() { # <node>...
  local node pid failed=
  local -a pids=()
  for node in "$@"; do
    ( trap - ERR; docker stop -t 120 "$node" >/dev/null ) &
    pids+=("$!")
  done
  for pid in "${pids[@]}"; do wait "$pid" || failed=1; done
  [ -z "$failed" ]
}

# In the separate topology, roll two directory servers together with one RS. The
# other RS and a directory writer remain available; its checkpoint must cover the RS
# before stopping, and a single LDAP batch confirms one write per stopped node.
scale_roll_pair() { # <directory node> <directory node> <peer list> <RS>
  local first=$1 second=$2 peers=$3 rolling_rs=$4 direction=up node candidate writer= observer= from
  [ "$TOPOLOGY" = rs534 ] || return 1
  [ "$peers" != "$SHORT_PEERS" ] || direction=down
  for candidate in $DS_NODES; do
    [ "$candidate" != "$REMOVED" ] && [ "$candidate" != "$first" ] && [ "$candidate" != "$second" ] || continue
    writer=$candidate; break
  done
  for candidate in $RS_NODES; do
    [ "$candidate" != "$REMOVED" ] && [ "$candidate" != "$rolling_rs" ] || continue
    observer=$candidate; break
  done
  [ -n "$writer" ] && [ -n "$observer" ] || { fail 'rolling group has no surviving writer or replication server'; return 1; }
  for node in "$first" "$second"; do rolling_request_pause "$node" || return 1; done
  for node in "$first" "$second"; do rolling_pause "$node" || return 1; done
  for node in "$first" "$second"; do window_open_key "$direction-$node" "dns:$REMOVED" || return 1; done
  window_open_key "$direction-$rolling_rs" "dns:$REMOVED" "dns:$rolling_rs" || return 1
  # Capture the checkpoint after opening all windows, immediately before the stops.
  scale_checkpoint_wait "$observer reaches a checkpoint of $rolling_rs" "$observer" "$rolling_rs" || return 1
  from=$(date +%s) || return 1
  scale_stop_group "$first" "$second" "$rolling_rs" || return 1
  if [ "$SCALE_VOLUME" = empty ] && [ "$direction" = down ]; then
    scale_capture_changelog "$rolling_rs" down || return 1
  fi
  mkdir -p "$OUT/scale-progress" || return 1
  scale_wait "another replica writes while the group is stopped" scale_probe_batch "$writer" "$direction" "$first" "$second" "$rolling_rs" || return 1
  for node in "$first" "$second" "$rolling_rs"; do scale_capture_bootstrap "$node" "$direction"; done
  for node in "$first" "$second" "$rolling_rs"; do docker rm "$node" >/dev/null || return 1; done
  PEERS="$peers" start_server "$rolling_rs" replication || return 1
  for node in "$first" "$second"; do PEERS="$peers" start_server "$node" directory || return 1; done
  scale_wait "$rolling_rs serves again" started_since "$rolling_rs" "$from" || return 1
  for node in "$first" "$second"; do
    scale_wait "$node serves again" started_since "$node" "$from" || return 1
    rolling_resume "$node" || return 1
  done
}

# Combined nodes stop one at a time. The separate topology overlaps the roll of
# directory pairs and one RS, never stopping both surviving replication servers.
scale_roll_batch() { # <peer list> <down|up>
  local peers=$1 direction=$2 node index=0 next_rs=0
  local -a directories=() replication=()
  if [ "$TOPOLOGY" = rs534 ]; then
    for node in $SURVIVORS; do
      if is_directory_node "$node"; then directories+=("$node"); else replication+=("$node"); fi
    done
    while [ "$index" -lt "${#directories[@]}" ]; do
      if [ $((index + 1)) -lt "${#directories[@]}" ] && [ "$next_rs" -lt "${#replication[@]}" ]; then
        scale_roll_pair "${directories[$index]}" "${directories[$((index + 1))]}" "$peers" "${replication[$next_rs]}" || return 1
        index=$((index + 2)); next_rs=$((next_rs + 1))
      else
        scale_roll "${directories[$index]}" "$peers" || return 1
        index=$((index + 1))
      fi
    done
    while [ "$next_rs" -lt "${#replication[@]}" ]; do
      scale_roll "${replication[$next_rs]}" "$peers" || return 1
      next_rs=$((next_rs + 1))
    done
  else
    for node in $SURVIVORS; do scale_roll "$node" "$peers" || return 1; done
  fi
  for node in $SURVIVORS; do
    scale_wait "the join of $node ends" bootstrap_ended "$node" || return 1
  done
  # A handshake begun before any join ended may log its timeout up to eight seconds later.
  sleep 10
  for node in $SURVIVORS; do window_close_key "$direction-$node" || return 1; done
}

disturb() {
  local node from end address surviving_rs=
  local -a return_window=("dns:$REMOVED" 180)
  from=$(date +%s) || return 1
  if is_directory_node "$REMOVED"; then rolling_pause "$REMOVED" || return 1; fi
  SCALE_HELD_WRITERS=
  if [ "$TOPOLOGY" = combined ]; then
    scale_hold_writers "$SURVIVING_RS" "$REMOVED" down || { scale_release_writers || :; return 1; }
  fi
  scale_checkpoint_wait "$SURVIVING_RS reaches the departing RS checkpoint" "$SURVIVING_RS" "$REMOVED" || { scale_release_writers || :; return 1; }
  dns_window_open "$REMOVED" || { scale_release_writers || :; return 1; }
  window_open "dns:$REMOVED" 180 || { scale_release_writers || :; return 1; }
  docker stop -t 120 "$REMOVED" >/dev/null || { scale_release_writers || :; return 1; }
  scale_release_writers || return 1
  ABSENCE_START=$(date +%s) || return 1
  printf '%s\n' "$ABSENCE_START" >"$OUT/scale-checkpoint/absence-start.txt" || return 1
  # Only changes after the stop returned count toward the backlog while it was absent.
  scale_monitor_search "$SURVIVING_RS" >"$OUT/scale-checkpoint/absent-start.ldif" || return 1
  # Save a stopped container's complete errors log for window boundaries while it is absent.
  docker cp "$REMOVED:/opt/opendj/data/logs/errors" "$OUT/logs/$REMOVED.offline-log" || return 1
  wc -l <"$OUT/logs/$REMOVED.offline-log" >"$OUT/logs/$REMOVED.offline" || return 1
  docker logs "$REMOVED" >"$OUT/logs/$REMOVED.previous-container" 2>&1 || return 1
  scale_capture_bootstrap "$REMOVED" removed
  # The empty-volume case destroys this RS's original changelog. Preserve it so a
  # gap after failover can be traced back to the departing RS or a surviving peer.
  if [ "$TOPOLOGY" = rs534 ] && [ "$SCALE_VOLUME" = empty ]; then
    scale_capture_changelog "$REMOVED" removed || return 1
  fi
  docker rm "$REMOVED" >/dev/null || return 1
  sleep 10
  window_close || return 1
  scale_roll_batch "$SHORT_PEERS" down || return 1
  for node in $RS_NODES; do [ "$node" = "$REMOVED" ] || surviving_rs="$surviving_rs $node"; done
  scale_capture_offline_states down $surviving_rs || return 1
  for node in $SURVIVORS; do scale_wait "$REMOVED leaves $node" peer_removed "$node" "$REMOVED" || return 1; done
  scale_backlog || return 1
  # A join repairs only peers that are registered while it runs. Bring the removed node
  # back before rolling survivors to the longer list, or an early join can finish before
  # it exists and leave it out of that survivor's local lists.
  if [ "$SCALE_VOLUME" = empty ]; then
    for node in $DS_NODES; do
      [ "$node" != "$REMOVED" ] || continue
      address=$(docker inspect -f "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$node") || return 1
      [ -n "$address" ] || return 1
      return_window+=("cert:$node:$REMOVED" "cert-peer:$REMOVED:$address")
    done
  fi
  window_open "${return_window[@]}" || return 1
  if [ "$SCALE_VOLUME" = empty ]; then
    cp "$OUT/logs/$REMOVED.offline-log" "$OUT/logs/$REMOVED.previous" || return 1
    docker volume rm "vol-$REMOVED" >/dev/null || return 1
  fi
  PEERS="$FULL_PEERS" start_server "$REMOVED" "$REMOVED_ROLE" || return 1
  scale_wait "$REMOVED serves again" started_since "$REMOVED" "$from" || return 1
  rm "$OUT/logs/$REMOVED.offline" || return 1
  scale_wait "the join of $REMOVED ends" bootstrap_ended "$REMOVED" || return 1
  scale_capture_offline_states return $RS_NODES || return 1
  sleep 10
  dns_window_close || return 1
  window_close || return 1
  if is_directory_node "$REMOVED"; then
    scale_wait "the replica id of $REMOVED appears" record_rejoined_id "$REMOVED" || return 1
    rolling_resume "$REMOVED" || return 1
  fi
  scale_roll_batch "$FULL_PEERS" up || return 1
  scale_capture_offline_states up $RS_NODES || return 1
  for node in $SURVIVORS; do scale_wait "$REMOVED returns to $node" peer_present "$node" "$REMOVED" || return 1; done
  end=$(date +%s) || return 1
  echo "$((from * 1000)) $((end * 1000)) $REMOVED scale-$SCALE_VOLUME" >>"$OUT/disturbances.txt" || return 1
}

settle() {
  local node
  for node in $ALL_NODES; do wait_until "$DISTURB_TIMEOUT" "the join of $node ends" bootstrap_ended "$node" || return 1; done
}
