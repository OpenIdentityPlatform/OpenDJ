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

# Settings and helpers of the replication convergence run (#1090), sourced by run.sh. Every
# setting comes from the environment, so that the same run goes in CI and on a laptop.

TOPOLOGY=${TOPOLOGY:-rs534}
# seconds of load
DURATION=${DURATION:-120}
# users the first directory server imports; the others get them from its initialize
SAMPLE_DATA=${SAMPLE_DATA:-2000}
SERVER_MEMORY=${SERVER_MEMORY:-512m}
SERVER_JAVA_ARGS=${SERVER_JAVA_ARGS:--server -Xmx256m}
# seconds the whole topology is given to come up: the joins take their turns one at a time
UP_TIMEOUT=${UP_TIMEOUT:-3600}
# seconds the topology is given to converge once the load stops
QUIESCE_TIMEOUT=${QUIESCE_TIMEOUT:-900}
# 1: write to one replica only, which the run must report (a self-test of its checks)
INJECT_DIVERGENCE=${INJECT_DIVERGENCE:-0}
# where the summary, the samples, the exports, the differences and a failed run's state go
OUT=${OUT:-$PWD/replication-out}

NETWORK=convergence
ROOT_PASSWORD=password
BASE_DN="dc=example,dc=com"

case $TOPOLOGY in
  # #534: directory servers without a replication server and replication servers without data.
  # The directory servers come first in REPLICATION_PEERS: the first of them seeds
  rs534)
    DS_NODES="cv-d0 cv-d1 cv-d2 cv-d3"
    RS_ONLY="cv-r0 cv-r1"
    RS_NODES=$RS_ONLY
    ;;
  # every server holds the data and runs a replication server, which most deployments do
  combined)
    DS_NODES="cv-c0 cv-c1 cv-c2 cv-c3"
    RS_ONLY=
    RS_NODES=$DS_NODES
    ;;
  *)
    echo "unknown TOPOLOGY $TOPOLOGY: rs534 or combined" >&2
    exit 2
    ;;
esac
ALL_NODES=$(echo $DS_NODES $RS_ONLY)
PEERS=$(echo $ALL_NODES | tr ' ' ',')

cleanup() {
  local n
  docker rm -f $ALL_NODES >/dev/null 2>&1 || true
  docker network rm $NETWORK >/dev/null 2>&1 || true
  for n in $ALL_NODES; do
    docker volume rm -f "vol-$n" >/dev/null 2>&1 || true
  done
}

# Where the time goes: each phase logs when it starts, and the run ends - failed or not - with
# the phases sorted by their duration
PHASE=
PHASE_START=$SECONDS
PHASE_TIMES=
phase() { # <name>
  [ -z "$PHASE" ] || PHASE_TIMES+="$((SECONDS - PHASE_START)) s  $PHASE"$'\n'
  PHASE=$1
  PHASE_START=$SECONDS
  echo "=== [${SECONDS} s] $PHASE"
}

timings() {
  phase "(end)"
  echo "::group::time per phase, longest first"
  sort -rn <<<"${PHASE_TIMES%$'\n'}"
  echo "total: ${SECONDS} s"
  echo "::endgroup::"
}

fail() {
  echo "::error::$1"
  false
}

wait_until() { # <seconds> <what> <command> [<argument>...]
  local deadline=$((SECONDS + $1)) what=$2 start=$SECONDS
  shift 2
  until "$@"; do
    [ "$SECONDS" -lt "$deadline" ] || fail "timed out waiting until $what"
    sleep 2
  done
  [ $((SECONDS - start)) -lt 20 ] || echo "    [$((SECONDS - start)) s] until $what"
}

is_healthy() { # <container>
  [ "$(docker inspect --format='{{.State.Health.Status}}' "$1")" = healthy ]
}

ldaps_search() { # <container> <base DN> <scope> <filter> [<attribute>...]
  local container=$1 base=$2 scope=$3 filter=$4
  shift 4
  docker exec "$container" /opt/opendj/bin/ldapsearch --noPropertiesFile --hostname localhost --port 1636 \
    --useSsl --trustAll --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" \
    --baseDN "$base" --searchScope "$scope" "$filter" "$@"
}

# cn=config and cn=monitor through the administration connector, which every role has
admin_search() { # <container> <base DN> <scope> <filter> [<attribute>...]
  local container=$1 base=$2 scope=$3 filter=$4
  shift 4
  docker exec "$container" /opt/opendj/bin/ldapsearch --noPropertiesFile --hostname localhost --port 4444 \
    --useSsl --trustAll --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" \
    --baseDN "$base" --searchScope "$scope" "$filter" "$@"
}

runs_replication_server() { # <container>
  admin_search "$1" "cn=config" sub "(objectClass=ds-cfg-replication-server)" 1.1 | grep "^dn:" >/dev/null
}
