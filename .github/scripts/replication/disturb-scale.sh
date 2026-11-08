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

scale_checkpoint_query() { # <node> <output> <error> <role>
  local node=$1 output=$2 errors=$3 role=$4 rc=0 start=$SECONDS remaining=$((ROLLING_END - SECONDS)) now timeout=none
  now=$(date +%s) || return 1
  ADMIN_SEARCH_TIMEOUT=45 admin_search "$node" cn=monitor sub '(replicationServer-database=*)' \
    replicationServer-database domain-name last-change >"$output" 2>"$errors" || rc=$?
  if [ "$rc" -eq 124 ]; then
    if grep -F 'timeout: sending signal ' "$errors" >/dev/null; then timeout=query
    elif [ "$SECONDS" -ge "$ROLLING_END" ]; then timeout=load
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
  local node=$1 dir="$OUT/scale-checkpoint"
  admin_search "$node" cn=monitor sub '(replicationServer-database=*)' \
    replicationServer-database domain-name last-change >"$dir/fence-$node.ldif" 2>"$dir/fence-$node.txt" || return 1
  python3 -I "$HERE/scale-fence.py" "$BASE_DN" "$dir/writer.ldif" "$dir/fence-$node.ldif" \
    >"$dir/fence-$node.txt" 2>&1
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
    scale_wait "$node reaches the final fill CSN" scale_fence "$node" || return 1
    scale_wait "$node holds the complete fill" scale_coverage "$node" || return 1
  done
  printf '%s changes while the server was absent\n' "$count" >"$OUT/scale-checkpoint/backlog.txt"
}

is_directory_node() { case " $DS_NODES " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }
scale_wait() { # <what> <predicate> [<argument>...]
  local remaining=$((ROLLING_END - SECONDS))
  [ "$remaining" -gt 0 ] || { fail "scale change exceeded the load"; return 1; }
  [ "$remaining" -le "$DISTURB_TIMEOUT" ] || remaining=$DISTURB_TIMEOUT
  wait_until "$remaining" "$@" || return 1
  [ "$SECONDS" -lt "$ROLLING_END" ] || { fail "scale change exceeded the load"; return 1; }
}

# A successful, unique LDAP add while a node is down proves a write really happened in
# that interval; the final offline comparison proves it reached every replica.
scale_probe() { # <writer DS> <rolling node> <down|up>
  local writer=$1 node=$2 direction=$3 uid="scale-$3-$2"
  printf 'dn: uid=%s,ou=People,%s\nobjectClass: top\nobjectClass: person\nobjectClass: organizationalPerson\nobjectClass: inetOrgPerson\nuid: %s\ncn: Scale Probe\nsn: ScaleProbe\n\n' \
    "$uid" "$BASE_DN" "$uid" \
    | docker exec -i "$writer" /bin/sh -c '
        deadline=$1; password=$2
        left=$((deadline - $(date +%s)))
        [ "$left" -gt 0 ] || exit 1
        exec /usr/bin/timeout "$left" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 1636 \
          --bindDN "cn=Directory Manager" --bindPassword "$password" --useSsl --trustAll --defaultAdd
      ' sh "$((ROLLING_END_MS / 1000))" "$ROOT_PASSWORD" >"$OUT/scale-progress/$direction-$node.txt" 2>&1
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
  case " $RS_NODES " in
    *" $node "*) scale_checkpoint_wait "$observer reaches a checkpoint of $node" "$observer" "$node" || return 1 ;;
  esac
  case " $RS_NODES " in
    *" $node "*) window_open_key "$direction-$node" "dns:$REMOVED" "dns:$node" || return 1 ;;
    *) window_open_key "$direction-$node" "dns:$REMOVED" || return 1 ;;
  esac
  from=$(date +%s) || return 1
  docker stop -t 120 "$node" >/dev/null || return 1
  mkdir -p "$OUT/scale-progress" || return 1
  scale_wait "another replica writes while $node is stopped" scale_probe "$writer" "$node" "$direction" || return 1
  docker rm "$node" >/dev/null || return 1
  PEERS="$peers" start_server "$node" "$role" || return 1
  scale_wait "$node serves again" started_since "$node" "$from" || return 1
  if is_directory_node "$node"; then rolling_resume "$node" || return 1; fi
}

# A pair of directory-only servers may be recreated together: two other DS and the
# surviving RS remain available. Open both log windows before either container stops.
scale_roll_pair() { # <directory node> <directory node> <peer list>
  local first=$1 second=$2 peers=$3 direction=up node candidate writer= rs= from
  [ "$TOPOLOGY" = rs534 ] || return 1
  [ "$peers" != "$SHORT_PEERS" ] || direction=down
  for candidate in $DS_NODES; do
    [ "$candidate" != "$REMOVED" ] && [ "$candidate" != "$first" ] && [ "$candidate" != "$second" ] || continue
    writer=$candidate; break
  done
  for candidate in $RS_NODES; do
    [ "$candidate" != "$REMOVED" ] || continue
    rs=$candidate; break
  done
  [ -n "$writer" ] && [ -n "$rs" ] || { fail 'directory pair has no surviving writer or replication server'; return 1; }
  scale_wait "$rs remains available" runs_replication_server "$rs" || return 1
  for node in "$first" "$second"; do rolling_request_pause "$node" || return 1; done
  for node in "$first" "$second"; do rolling_pause "$node" || return 1; done
  for node in "$first" "$second"; do window_open_key "$direction-$node" "dns:$REMOVED" || return 1; done
  from=$(date +%s) || return 1
  for node in "$first" "$second"; do docker stop -t 120 "$node" >/dev/null || return 1; done
  mkdir -p "$OUT/scale-progress" || return 1
  for node in "$first" "$second"; do
    scale_wait "another replica writes while $node is stopped" scale_probe "$writer" "$node" "$direction" || return 1
  done
  for node in "$first" "$second"; do docker rm "$node" >/dev/null || return 1; done
  for node in "$first" "$second"; do PEERS="$peers" start_server "$node" directory || return 1; done
  for node in "$first" "$second"; do
    scale_wait "$node serves again" started_since "$node" "$from" || return 1
    rolling_resume "$node" || return 1
  done
}

# Combined and RS-only servers stop one at a time. Directory-only servers in the
# separate topology roll in pairs, and all background joins complete before membership checks.
scale_roll_batch() { # <peer list> <down|up>
  local peers=$1 direction=$2 node pending=
  for node in $SURVIVORS; do
    if [ "$TOPOLOGY" = rs534 ] && is_directory_node "$node"; then
      if [ -n "$pending" ]; then
        scale_roll_pair "$pending" "$node" "$peers" || return 1
        pending=
      else
        pending=$node
      fi
    else
      if [ -n "$pending" ]; then scale_roll "$pending" "$peers" || return 1; pending=; fi
      scale_roll "$node" "$peers" || return 1
    fi
  done
  [ -z "$pending" ] || scale_roll "$pending" "$peers" || return 1
  for node in $SURVIVORS; do
    scale_wait "the join of $node ends" bootstrap_ended "$node" || return 1
  done
  # A handshake begun before any join ended may log its timeout up to eight seconds later.
  sleep 10
  for node in $SURVIVORS; do window_close_key "$direction-$node" || return 1; done
}

disturb() {
  local node from end address
  local -a return_window=("dns:$REMOVED" 180)
  from=$(date +%s) || return 1
  if is_directory_node "$REMOVED"; then rolling_pause "$REMOVED" || return 1; fi
  scale_checkpoint_wait "$SURVIVING_RS reaches the departing RS checkpoint" "$SURVIVING_RS" "$REMOVED" || return 1
  dns_window_open "$REMOVED" || return 1
  window_open "dns:$REMOVED" 180 || return 1
  docker stop -t 120 "$REMOVED" >/dev/null || return 1
  ABSENCE_START=$(date +%s) || return 1
  printf '%s\n' "$ABSENCE_START" >"$OUT/scale-checkpoint/absence-start.txt" || return 1
  # Only changes after the stop returned count toward the backlog while it was absent.
  admin_search "$SURVIVING_RS" cn=monitor sub '(replicationServer-database=*)' \
    replicationServer-database domain-name last-change >"$OUT/scale-checkpoint/absent-start.ldif" || return 1
  # Save a stopped container's complete errors log for window boundaries while it is absent.
  docker cp "$REMOVED:/opt/opendj/data/logs/errors" "$OUT/logs/$REMOVED.offline-log" || return 1
  wc -l <"$OUT/logs/$REMOVED.offline-log" >"$OUT/logs/$REMOVED.offline" || return 1
  docker logs "$REMOVED" >"$OUT/logs/$REMOVED.previous-container" 2>&1 || return 1
  docker rm "$REMOVED" >/dev/null || return 1
  sleep 10
  window_close || return 1
  scale_roll_batch "$SHORT_PEERS" down || return 1
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
  sleep 10
  dns_window_close || return 1
  window_close || return 1
  if is_directory_node "$REMOVED"; then
    scale_wait "the replica id of $REMOVED appears" record_rejoined_id "$REMOVED" || return 1
    rolling_resume "$REMOVED" || return 1
  fi
  scale_roll_batch "$FULL_PEERS" up || return 1
  for node in $SURVIVORS; do scale_wait "$REMOVED returns to $node" peer_present "$node" "$REMOVED" || return 1; done
  end=$(date +%s) || return 1
  echo "$((from * 1000)) $((end * 1000)) $REMOVED scale-$SCALE_VOLUME" >>"$OUT/disturbances.txt" || return 1
}

settle() {
  local node
  for node in $ALL_NODES; do wait_until "$DISTURB_TIMEOUT" "the join of $node ends" bootstrap_ended "$node" || return 1; done
}
