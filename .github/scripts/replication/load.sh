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
# the same image on the network of the topology. Only writes: a read sends nothing to the
# replication servers. A directory server a disturbance takes down (#1208) is written to before
# it and after it, by clients of their own; replication servers it takes down leave the clients
# be, and the directory servers write through the replication server still up.

# <client> <tool> <argument>... : a container that runs one tool of the image against one server
start_client() {
  local name=$1 tool=$2
  shift 2
  docker run -d --name "$name" --network $NETWORK -e OPENDJ_JAVA_ARGS="$CLIENT_JAVA_ARGS" \
    -v "$OUT/load:/load:ro" --entrypoint /opt/opendj/bin/$tool "$IMAGE" "$@" >/dev/null
}

CLIENTS=
# seconds between two lines of the output of a rate tool
STAT_INTERVAL=10

# The clients of one directory server for <seconds> of the load, a modrate and an addrate.
# Segment 1 starts with the load; a later one once a disturbance let the server serve again,
# and it adds entries of its own: the 100 newest of the segment before are still there.
# Plain LDAP: the run is after the changes the replication carries, and TLS between a client
# and its server only takes CPU from them. --noRebind: every client keeps its connections open
# and binds once, as an application does, rather than connecting and binding for every
# operation, which would cost more than the operation itself. --connectTimeout: the clients
# start at once next to the servers, and a runner that is short of CPU for a while accepts
# their connections late
start_clients() { # <node> <segment> <seconds>
  local n=$1 name=$1 c
  [ "$2" -eq 1 ] || name=$1-$2
  # entries of its own on each server, so that the adds and deletes of two servers never meet
  # on one DN: a naming conflict is not what this load is after
  cat >"$OUT/load/addrate-$name.template" <<TEMPLATE
define suffix=$BASE_DN

branch: ou=People,[suffix]
subordinateTemplate: person

template: person
rdnAttr: uid
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
uid: addrate-$name.<sequential:0>
cn: AddRate {uid}
sn: AddRate
userPassword: password
TEMPLATE
  chmod 644 "$OUT/load/addrate-$name.template"
  # the same entries from every server at once: concurrent modifications of one attribute of
  # one entry, applied in the order of their change numbers on every replica (#139). The value
  # starts with the name of the server, and of the segment after the first (cv-d1-2): rand and
  # randstr are seeded alike in every client, so all of them write the same values in the same
  # order - the two segments of one server as well - and a change lost on its way would hide
  # behind the same value written later by another client; the name also tells which server,
  # and which segment, a value in a diff or a changelog comes from (#1206)
  start_client load-mod-$name modrate --hostname $n --port 1389 --connectTimeout 120000 --noRebind \
    --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --maxDuration "$3" \
    --statInterval $STAT_INTERVAL --numConnections 8 --numThreads 1 --scriptFriendly --targetThroughput "$LOAD_MOD_RATE" \
    --targetDN "uid=user.%d,ou=People,$BASE_DN" --argument "rand(0,$((SAMPLE_DATA - 1)))" \
    --argument "randstr(16)" "description:$name-%2\$s"
  # adds, and deletes of the oldest once 100 of them are there: a slow runner gets to the deletes as well
  start_client load-add-$name addrate --hostname $n --port 1389 --connectTimeout 120000 --noRebind \
    --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --maxDuration "$3" \
    --statInterval $STAT_INTERVAL --numConnections 8 --numThreads 1 --scriptFriendly --targetThroughput "$LOAD_ADD_RATE" \
    --deleteMode fifo --deleteSizeThreshold 100 "/load/addrate-$name.template"
  # check_client reads how long each client was to run
  for c in load-mod-$name load-add-$name; do
    echo "$3" >"$OUT/load/$c.seconds"
  done
  CLIENTS="$CLIENTS load-mod-$name load-add-$name"
}

run_load() {
  local n c failed start=$SECONDS left
  mkdir -p "$OUT/load"
  for n in $DS_NODES; do
    if [ "$n" = "${DISTURBED:-}" ]; then
      start_clients $n 1 "$DISTURB_AT"
    else
      start_clients $n 1 "$DURATION"
    fi
  done
  if [ "$DISTURBANCE" != none ]; then
    case " $DS_NODES " in
      *" $DISTURBED "*)
        # the clients of the disturbed server end on their own before the disturbance, which
        # then cuts none of their operations off: one that had its CSN would read as a lost change
        docker wait load-mod-$DISTURBED load-add-$DISTURBED >/dev/null
        disturb
        left=$((DURATION - (SECONDS - start)))
        [ "$left" -ge $((3 * STAT_INTERVAL)) ] \
          || fail "$DISTURBED served again $((SECONDS - start)) s into the load of $DURATION s, too late to take load again"
        start_clients $DISTURBED 2 $left
        ;;
      *)
        left=$((DISTURB_AT - (SECONDS - start)))
        [ "$left" -le 0 ] || sleep $left
        disturb
        ;;
    esac
  fi
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
# server at all exits at once. One whose connection breaks stops at once as well, exits with 0
# too, leaves out the line of its last interval - the one that would count the error - and
# prints the error to its error output, which goes to the same file. So the client has to have
# done work, every interval of its CSV output has to report no errors (the "Errors/second"
# column), the output has to cover the seconds the client was to run - a line every
# STAT_INTERVAL seconds, of which the last may be missing - and hold nothing but the CSV
check_client() { # <client>
  awk -F, -v c=$1 -v exit_state="$(cat "$OUT/load/$1.exit")" -v want=$(($(cat "$OUT/load/$1.seconds") / STAT_INTERVAL - 1)) '
    /^Time \(seconds\)/ { for (i = 1; i <= NF; i++) { if ($i == "Recent throughput (ops/second)") t = i; if ($i == "Errors/second") e = i }; next }
    t && $1 ~ /^[0-9.]+$/ { rows++; ops += $t; errors += $e; last = $0; next }
    NF { other++ }
    END {
      if (!rows || ops == 0) { printf "::error::%s did no work (exit code and OOM kill: %s)\n", c, exit_state; exit 1 }
      if (errors > 0) { printf "::error::%s got errors back\n", c; exit 1 }
      if (exit_state != "0 false") { printf "::error::%s ended with exit code and OOM kill %s\n", c, exit_state; exit 1 }
      if (other) { printf "::error::%s printed %d lines besides its CSV output, see load/%s.csv\n", c, other, c; exit 1 }
      if (rows < want) { printf "::error::%s stopped after %d of %d intervals\n", c, rows, want; exit 1 }
      printf "%s: %d intervals, last: %s\n", c, rows, last
    }' "$OUT/load/$1.csv"
}
