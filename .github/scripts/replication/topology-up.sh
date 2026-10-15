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
  local name=$1 role=$2
  shift 2
  docker volume create "vol-$name" >/dev/null
  docker run -d --network $NETWORK --name "$name" --hostname "$name" \
    -v "vol-$name:/opt/opendj/data" \
    -e ROOT_PASSWORD="$ROOT_PASSWORD" -e ADD_BASE_ENTRY="--addBaseEntry" \
    -e OPENDJ_JAVA_ARGS="$SERVER_JAVA_ARGS" \
    -e OPENDJ_REPLICATION_TYPE=simple -e REPLICATION_ROLE="$role" -e REPLICATION_PEERS="$PEERS" \
    -e REPLICATION_RETRY_INTERVAL=3 \
    "$@" "$IMAGE" >/dev/null
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
}
