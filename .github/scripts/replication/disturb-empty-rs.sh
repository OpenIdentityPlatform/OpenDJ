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

# Rebuild an RS on an empty volume while a DS is down (#1208, #1188). Its clients are
# segmented by run_load; all the other DS keep writing. Once the load has ended and the
# topology converged, force every DS to connect to each RS in turn, including the DS that
# missed the new instance key. Run only with two independent replication servers.
[ "$(echo $RS_ONLY | wc -w)" -eq 2 ] && [ "$(echo $RS_NODES | wc -w)" -eq 2 ] \
  || { echo "empty-rs needs exactly two replication-only servers: TOPOLOGY=rs534" >&2; exit 2; }
DISTURBED=$(echo $DS_NODES | cut -d' ' -f2)
SURVIVOR=$(echo $RS_ONLY | cut -d' ' -f1)
REBUILT=$(echo $RS_ONLY | cut -d' ' -f2)
DISTURB_STEPS=1

rs_serves_since() { # <RS> <start time>
  started_since "$1" "$2" && bootstrap_ended "$1" && [ "$(rs_peers "$1")" -eq 1 ]
}

# Positions are checked before stopping either RS. The monitor snapshots and comparison
# diagnostics are retained; equality of the last positions does not prove older gaps absent,
# so the offline changelog and data checks still run after this scenario.
rs_caught_up() { # <RS to leave up> <RS about to stop>
  local rs=$1 reference=$2 dir="$OUT/rs-catchup"
  mkdir -p "$dir" || return 1
  bootstrap_ended "$rs" || return 1
  admin_search "$reference" cn=monitor sub '(replicationServer-database=*)' \
    replicationServer-database domain-name last-change >"$dir/$reference.ldif" 2>"$dir/check.txt" || return 1
  admin_search "$rs" cn=monitor sub '(replicationServer-database=*)' \
    replicationServer-database domain-name last-change >"$dir/$rs.ldif" 2>>"$dir/check.txt" || return 1
  python3 -I "$HERE/changelog-checkpoint.py" "$BASE_DN" "$OUT/server-ids.txt" \
    "$dir/$reference.ldif" "$dir/$rs.ldif" >"$dir/check.txt" 2>&1
}

connected_to() { # <DS> <RS>
  local address
  address=$(docker inspect -f "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$2") || return 1
  [ -n "$address" ] || return 1
  admin_search "$1" "$MONITOR_DOMAIN" one '(connected-to=*)' connected-to \
    | grep -Fx "connected-to: $address:8989" >/dev/null
}

disturb() {
  local from served cert_start cert_end address
  window_open "dns:$REBUILT" "cert:$DISTURBED:$REBUILT" 180
  from=$(date +%s)
  echo "    [$SECONDS s] stopping $DISTURBED and rebuilding $REBUILT"
  docker stop -t 120 "$DISTURBED" >/dev/null
  docker stop -t 120 "$REBUILT" >/dev/null
  # Keep the old log before removing its volume. errors_lines and since_mark treat it and
  # the new log as one sequence, so neither pre-replacement errors nor window bounds vanish.
  [ ! -e "$OUT/logs/$REBUILT.previous" ] || fail "the old errors log of $REBUILT already exists"
  docker logs "$REBUILT" >"$OUT/logs/$REBUILT.previous-container" 2>&1
  docker cp "$REBUILT:/opt/opendj/data/logs/errors" "$OUT/logs/$REBUILT.previous"
  docker rm "$REBUILT" >/dev/null
  docker volume rm "vol-$REBUILT" >/dev/null
  start_server "$REBUILT" replication
  wait_until "$DISTURB_TIMEOUT" "$REBUILT serves again" rs_serves_since "$REBUILT" "$from"
  # The RS may log the same transient rejection on its side as warning 105. Restrict that
  # allowance to this DS's actual Docker address, and to the period after it restarts.
  cert_start=$(errors_lines "$REBUILT")
  docker start "$DISTURBED" >/dev/null
  address=$(docker inspect -f "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$DISTURBED")
  [ -n "$address" ] || fail "cannot determine the address of $DISTURBED"
  wait_until "$DISTURB_TIMEOUT" "$DISTURBED serves again" started_since "$DISTURBED" "$from"
  served=$(date +%s)
  sleep 10
  cert_end=$(errors_lines "$REBUILT")
  printf '%s %s cert-peer:%s:%s\n' "$cert_start" "$cert_end" "$REBUILT" "$address" >>"$OUT/logs/$REBUILT.windows"
  window_close
  echo "$((from * 1000)) $((served * 1000)) $DISTURBED rebuild-$REBUILT" >>"$OUT/disturbances.txt"
}

settle() {
  wait_until "$DISTURB_TIMEOUT" "the join of $DISTURBED ends" bootstrap_ended "$DISTURBED"
  wait_until "$DISTURB_TIMEOUT" "the join of $REBUILT ends" bootstrap_ended "$REBUILT"
}

after_convergence() {
  local stopped active n from saved_converged=$CONVERGED_IN
  for stopped in "$SURVIVOR" "$REBUILT"; do
    if [ "$stopped" = "$SURVIVOR" ]; then active=$REBUILT; else active=$SURVIVOR; fi
    wait_until "$QUIESCE_TIMEOUT" "$active reaches the checkpoint of $stopped" rs_caught_up "$active" "$stopped"
    window_open "dns:$stopped" 180
    from=$(date +%s)
    docker stop -t 120 "$stopped" >/dev/null
    for n in $DS_NODES; do
      wait_until "$DISTURB_TIMEOUT" "$n connects to $active" connected_to "$n" "$active"
    done
    echo "$stopped $active $(($(date +%s) - from))" >>"$OUT/failover.txt"
    docker start "$stopped" >/dev/null
    wait_until "$DISTURB_TIMEOUT" "$stopped serves again" rs_serves_since "$stopped" "$from"
    wait_until "$DISTURB_TIMEOUT" "the join of $stopped ends" bootstrap_ended "$stopped"
    sleep 10
    window_close
    wait_converged
  done
  CONVERGED_IN=$saved_converged
}
