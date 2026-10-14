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

# The load of the replication convergence run (#1090), sourced by run.sh: every directory
# server is written to at once, for DURATION seconds, by rate tools that run in containers of
# the same image on the network of the topology.

# <client> <tool> <argument>... : a container that runs one tool of the image against one server
start_client() {
  local name=$1 tool=$2
  shift 2
  docker run -d --name "$name" --network $NETWORK --memory=256m -e OPENDJ_JAVA_ARGS="-Xmx128m" \
    -v "$OUT/load:/load:ro" --entrypoint /opt/opendj/bin/$tool "$IMAGE" "$@" >/dev/null
}

CLIENTS=

# --connectTimeout: twelve clients start at once next to the servers, and a runner that is
# short of CPU for a while accepts their connections late
run_load() {
  local n c failed
  mkdir -p "$OUT/load"
  for n in $DS_NODES; do
    # entries of its own on each server, so that the adds and deletes of two servers never meet
    # on one DN: a naming conflict is not what this load is after
    cat >"$OUT/load/addrate-$n.template" <<TEMPLATE
define suffix=$BASE_DN

branch: ou=People,[suffix]
subordinateTemplate: person

template: person
rdnAttr: uid
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
uid: addrate-$n.<sequential:0>
cn: AddRate {uid}
sn: AddRate
userPassword: password
TEMPLATE
    chmod 644 "$OUT/load/addrate-$n.template"
    # the same entries from every server at once: concurrent modifications of one attribute of
    # one entry, applied in the order of their change numbers on every replica (#139)
    start_client load-mod-$n modrate --hostname $n --port 1636 --useSsl --trustAll --connectTimeout 120000 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --maxDuration "$DURATION" \
      --statInterval 10 --numConnections 2 --numThreads 1 --scriptFriendly --targetThroughput 100 \
      --targetDN "uid=user.%d,ou=People,$BASE_DN" --argument "rand(0,$((SAMPLE_DATA - 1)))" \
      --argument "randstr(16)" 'description:%2$s'
    # adds, and deletes of the oldest once 100 of them are there: a slow runner gets to the deletes as well
    start_client load-add-$n addrate --hostname $n --port 1636 --useSsl --trustAll --connectTimeout 120000 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --maxDuration "$DURATION" \
      --statInterval 10 --numConnections 2 --numThreads 1 --scriptFriendly --targetThroughput 20 \
      --deleteMode fifo --deleteSizeThreshold 100 "/load/addrate-$n.template"
    # reads, which keep the work queues of the server busy alongside the writes
    start_client load-search-$n searchrate --hostname $n --port 1636 --useSsl --trustAll --connectTimeout 120000 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --maxDuration "$DURATION" \
      --statInterval 10 --numConnections 2 --numThreads 1 --scriptFriendly --targetThroughput 100 \
      --baseDN "ou=People,$BASE_DN" --searchScope sub --argument "rand(0,$((SAMPLE_DATA - 1)))" \
      "(uid=user.%d)" 1.1
    CLIENTS="$CLIENTS load-mod-$n load-add-$n load-search-$n"
  done
  for c in $CLIENTS; do
    docker wait $c >/dev/null
  done
  # every client's output first, so that one which failed does not hide how the others did
  for c in $CLIENTS; do
    docker logs $c >"$OUT/load/$c.csv" 2>&1
    docker inspect -f '{{.State.ExitCode}} {{.State.OOMKilled}}' $c >"$OUT/load/$c.exit"
    docker rm $c >/dev/null
  done
  failed=
  for c in $CLIENTS; do
    check_client $c || failed=1
  done
  [ -z "$failed" ] || fail "the load did not run as it should, see load/"
}

# A rate tool exits with 0 whatever its requests got back, and one that could not reach its
# server at all exits at once: the client has to have done work, and every interval of its CSV
# output has to report no errors (the "Errors/second" column)
check_client() { # <client>
  awk -F, -v c=$1 -v exit_state="$(cat "$OUT/load/$1.exit")" '
    /^Time \(seconds\)/ { for (i = 1; i <= NF; i++) { if ($i == "Recent throughput (ops/second)") t = i; if ($i == "Errors/second") e = i }; next }
    t && $1 ~ /^[0-9.]+$/ { rows++; ops += $t; errors += $e; last = $0 }
    END {
      if (!rows || ops == 0) { printf "::error::%s did no work (exit code and OOM kill: %s)\n", c, exit_state; exit 1 }
      if (errors > 0) { printf "::error::%s got errors back\n", c; exit 1 }
      printf "%s: %d intervals, last: %s\n", c, rows, last
    }' "$OUT/load/$1.csv"
}
