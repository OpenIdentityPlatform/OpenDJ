#
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

# Starts the topology of the replication convergence run (#1090), sourced by run.sh. Every
# server joins through the background join of the image (OPENDJ_REPLICATION_TYPE=simple) in
# its REPLICATION_ROLE, as a StatefulSet would start it.

start_server() { # <name> <role> [<docker run option>...]
  local name=$1 role=$2 java_args=$SERVER_JAVA_ARGS token=
  local -a monitor_port=() timing=()
  shift 2
  # Scale checkpoints use a lightweight host client; the admin listener remains loopback-only.
  case ${DISTURBANCE:-none} in scale-*) monitor_port=(-p 127.0.0.1::4444); timing=(-e JOIN_TIMING=1) ;; esac
  if [ "${CSN_TRACE:-0}" = 1 ] && [ "$role" != replication ]; then
    token=$(python3 -c 'import uuid; print(uuid.uuid4().hex)') || return 1
    java_args="$java_args -Dorg.opends.server.replication.csnTraceDirectory=/opt/opendj/data/csn-trace -Dorg.opends.server.replication.csnTraceToken=$token -Dorg.opends.server.replication.csnTraceTokenFile=/opt/opendj/data/csn-trace-token-$token"
  fi
  docker volume create "vol-$name" >/dev/null || return 1
  docker run -d --network $NETWORK --name "$name" --hostname "$name" "${monitor_port[@]}" "${timing[@]}" \
    -v "vol-$name:/opt/opendj/data" \
    -e ROOT_PASSWORD="$ROOT_PASSWORD" -e ADD_BASE_ENTRY="--addBaseEntry" \
    -e OPENDJ_JAVA_ARGS="$java_args" \
    -e OPENDJ_REPLICATION_TYPE=simple -e REPLICATION_ROLE="$role" -e REPLICATION_PEERS="$PEERS" \
    -e REPLICATION_RETRY_INTERVAL=3 \
    "$@" "$IMAGE" >/dev/null || return 1
  if [ -n "$token" ]; then
    mkdir -p "$OUT/csn-trace-tokens" || return 1
    printf '%s\n' "$token" >"$OUT/csn-trace-tokens/$name" || return 1
    printf '%s %s\n' "$name" "$token" >>"$OUT/csn-trace-expected.txt" || return 1
  fi
}

topology_up() {
  local n deadline role=directory first=${DS_NODES%% *}
  [ "$TOPOLOGY" != combined ] || role=combined
  docker network create $NETWORK >/dev/null
  # only the first server imports the users; the others are initialized from it when they join
  start_server "$first" $role -e SAMPLE_DATA="$SAMPLE_DATA"
  for n in ${DS_NODES#* }; do
    start_server "$n" $role
  done
  for n in $RS_ONLY; do
    start_server "$n" replication
  done
  # one deadline for all of them: the joins take their turns, so the last server may wait for
  # every other one's
  deadline=$((SECONDS + UP_TIMEOUT))
  for n in $ALL_NODES; do
    wait_until $((deadline > SECONDS ? deadline - SECONDS : 1)) "$n is healthy" is_healthy "$n"
  done
  # what a join still changes would otherwise fall into the errors and reconnections of the run
  for n in $ALL_NODES; do
    wait_until $((deadline > SECONDS ? deadline - SECONDS : 1)) "the join of $n ends" bootstrap_ended "$n"
  done
}
