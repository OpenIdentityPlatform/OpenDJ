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

# Tests the replication of the Docker image (#1086): the background join of
# OPENDJ_REPLICATION_TYPE=simple (bootstrap/join.sh) and the deprecated one-shot sdsr path of
# bootstrap/replicate.sh. Both image jobs of build.yml run it, each with its own image.
#
# Usage: docker-test-replication.sh <image>

set -eE -o pipefail

IMAGE=${1:?usage: $0 <image>}
NODES="dj-0 dj-1 dj-2 dj-x dj-solo dj-bf dj-fi dj-f dj-p0 dj-p1 dj-p2 dj-ipm dj-ipr dj-sdsr dj-shm-probe dj-shm"
VOLUMES="vol-dj-0 vol-dj-1 vol-dj-2 vol-dj-x vol-dj-solo vol-dj-bf vol-dj-fi vol-dj-f vol-dj-ipr vol-dj-p0 vol-dj-p1 vol-dj-p2"
# a bootstrap that imports the base entry and then fails, mounted into dj-bf
FAILING_BOOTSTRAP=$(mktemp)
NETWORK=test_replication
# where dj-sdsr starts: it resolves its own name there, and no other server
NETWORK_ALONE=test_replication_alone

cleanup() {
  rm -f "$FAILING_BOOTSTRAP"
  docker rm -f $NODES >/dev/null 2>&1 || true
  docker network rm $NETWORK $NETWORK_ALONE >/dev/null 2>&1 || true
  docker volume rm -f $VOLUMES >/dev/null 2>&1 || true
}
cleanup
# set -E hands the trap to every $(...) as well, where a failing command would dump the logs
# into the value being captured and remove the containers under the running test: there the
# failure only ends the subshell, and the main shell decides
trap 'code=$?; [ "$BASH_SUBSHELL" -eq 0 ] || exit $code; for c in $NODES; do echo "::group::container logs ($c)"; docker logs $c 2>&1 || true; echo "::endgroup::"; done; cleanup; exit $code' ERR

fail() {
  echo "::error::$1"
  false
}

# grep reads the whole log rather than stopping at the first match (-q), so docker logs never
# writes into a closed pipe, which pipefail would report as a failure of the check
logs_have() { # <container> <text>
  docker logs "$1" 2>&1 | grep -F -- "$2" >/dev/null
}

logs_count() { # <container> <text>
  docker logs "$1" 2>&1 | grep -cF -- "$2" || true
}

# the container logged the text after the last line that holds <since> - after a restart,
# say, which the join of the start before may still have logged into until it was stopped
logs_have_since() { # <container> <since> <text>
  docker logs "$1" 2>&1 | awk -v s="$2" 'index($0, s) { buf = ""; next } { buf = buf $0 "\n" } END { printf "%s", buf }' \
    | grep -F -- "$3" >/dev/null
}

wait_until() { # <seconds> <what> <command> [<argument>...]
  local deadline=$((SECONDS + $1)) what=$2
  shift 2
  until "$@"; do
    [ "$SECONDS" -lt "$deadline" ] || fail "timed out waiting until $what"
    sleep 2
  done
}

health() {
  docker inspect --format='{{.State.Health.Status}}' "$1"
}

is_healthy() {
  [ "$(health "$1")" = healthy ]
}

wait_healthy() {
  wait_until 480 "$1 is healthy" is_healthy "$1"
}

# A container reports itself healthy only after a probe that finds the marker, and the
# probes run every 30 s: a single look right after a failure reads "starting" whatever the
# join did, so the container is watched over more than two probe cycles
stays_unhealthy() { # <container> <why>
  local i
  for i in $(seq 1 15); do
    is_healthy "$1" && fail "$1 reports itself healthy although $2"
    sleep 5
  done
}

ldaps_has() { # <container> <DN>
  docker exec "$1" /opt/opendj/bin/ldapsearch --noPropertiesFile --hostname localhost --port 1636 \
    --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --useSsl --trustAll \
    --baseDN "$2" --searchScope base "(objectClass=*)" 1.1 >/dev/null 2>&1
}

wait_has() { # <container> <DN>
  wait_until 60 "$2 is on $1" ldaps_has "$1" "$2"
}

admin_search() { # <container> <base DN> <scope> <filter> [<attribute>...]
  local container=$1 base=$2 scope=$3 filter=$4
  shift 4
  docker exec "$container" /opt/opendj/bin/ldapsearch --noPropertiesFile --hostname localhost --port 4444 \
    --useSsl --trustAll --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" \
    --baseDN "$base" --searchScope "$scope" "$filter" "$@" 2>/dev/null
}

add_ou() { # <container> <ou>
  printf 'dn: ou=%s,dc=example,dc=com\nobjectClass: organizationalUnit\nou: %s\n' "$2" "$2" \
    | docker exec -i "$1" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 1636 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --useSsl --trustAll --defaultAdd >/dev/null
}

# the replication server lists of a container: the one of its replication server and one per
# replication domain (BASE_DN, cn=schema, cn=admin data)
replication_lists() {
  admin_search "$1" "cn=config" sub \
    "(|(objectClass=ds-cfg-replication-server)(objectClass=ds-cfg-replication-domain))" ds-cfg-replication-server
}

lists_lack() { # <container> <name>
  ! replication_lists "$1" | grep -F -- "$2" >/dev/null
}

admin_data_lacks() { # <container> <name>
  ! admin_search "$1" "cn=Servers,cn=admin data" one "(objectClass=*)" hostname | grep -F -- "$2" >/dev/null
}

# the join lock of a server, held by hand as a join of a server named dj-held would hold it
hold_lock() { # <container>
  printf 'dn: cn=Docker Join Lock,cn=config\nobjectClass: top\nobjectClass: ds-cfg-branch\nobjectClass: extensibleObject\ncn: Docker Join Lock\ndescription: dj-held %s\n' "$(date +%s)" \
    | docker exec -i "$1" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 4444 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --useSsl --trustAll --defaultAdd >/dev/null
}

drop_lock() { # <container>
  printf 'dn: cn=Docker Join Lock,cn=config\nchangetype: delete\n' \
    | docker exec -i "$1" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 4444 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --useSsl --trustAll >/dev/null
}

# hands a lock held by hand over to another holder at once, as a join of that server took it now
relabel_lock() { # <container> <holder>
  printf 'dn: cn=Docker Join Lock,cn=config\nchangetype: modify\nreplace: description\ndescription: %s %s\n' "$2" "$(date +%s)" \
    | docker exec -i "$1" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 4444 \
      --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" --useSsl --trustAll >/dev/null
}

# the state the join of the container publishes to its peers
published_state() { # <container>
  admin_search "$1" "cn=Docker Join,cn=config" base "(objectClass=*)" description \
    | awk 'tolower($1) == "description:" { print $2 }'
}

# the container holds the replication domain of dc=example,dc=com
replicates_example() { # <container>
  admin_search "$1" "cn=config" sub "(&(objectClass=ds-cfg-replication-domain)(ds-cfg-base-dn=dc=example,dc=com))" 1.1 \
    | grep "^dn:" >/dev/null
}

# a client's write to the container is refused with Unwilling to Perform (53), as the backend
# of a server whose replication is down refuses it
refuses_writes() { # <container> <ou>
  local rc=0
  add_ou "$1" "$2" 2>/dev/null || rc=$?
  [ "$rc" -eq 53 ]
}

# the replication server of the container lists every one of the other names
server_lists() { # <container> <name>...
  local container=$1 list name
  shift
  list=$(admin_search "$container" "cn=config" sub "(objectClass=ds-cfg-replication-server)" ds-cfg-replication-server)
  for name in "$@"; do
    grep -F -- "ds-cfg-replication-server: $name:" <<<"$list" >/dev/null || return 1
  done
}

# every tool reads the root password from a file (#1084, #1092); dsreplication run with -n
# prints no command line, so a password put back on one would pass every check below
rc=0
docker run --rm --entrypoint grep "$IMAGE" -nE -- '(^|[[:space:]])(-w|--(bindPassword[12]?|adminPassword|rootUserPassword))([[:space:]=]|$)' \
  /opt/opendj/bootstrap/setup.sh /opt/opendj/bootstrap/replicate.sh /opt/opendj/bootstrap/join.sh || rc=$?
[ "$rc" -eq 1 ] || fail "a bootstrap script passes the root password on a command line, or grep could not read them"
# the password files go to /dev/shm, off the writable layer of the container, and the mktemp
# of the image puts them there
docker run --rm --entrypoint grep "$IMAGE" -qF -- 'mktemp -p /dev/shm "opendj-join.$ADMIN_PORT.' /opt/opendj/bootstrap/join.sh \
  || fail "join.sh no longer puts the password file on /dev/shm"
docker run --rm --entrypoint grep "$IMAGE" -qF -- 'mktemp -p /dev/shm "opendj-replicate.$ADMIN_PORT.' /opt/opendj/bootstrap/replicate.sh \
  || fail "replicate.sh no longer puts the password file on /dev/shm"
docker run --rm --entrypoint sh "$IMAGE" -c 'f=$(mktemp -p /dev/shm "opendj-join.$ADMIN_PORT.XXXXXX") && rm -f "$f" && case $f in /dev/shm/opendj-join.4444.*) ;; *) exit 1;; esac' \
  || fail "mktemp in the image does not create the password file on /dev/shm"
# a bounded dsreplication has to take its JVM down with it: the timeout of BusyBox signals
# only the shell script that starts java, that of coreutils the whole process group
docker run --rm --entrypoint sh "$IMAGE" -c 'timeout --version 2>&1 | grep -q "GNU coreutils"' \
  || fail "the image has no timeout of coreutils"

# a password with a space in it reaches every tool as one value
ROOT_PASSWORD='replication secret'
# a subnet of its own, so that a container can be given a known address
MASTER_ADDRESS=172.30.99.50
docker network create --subnet 172.30.99.0/24 $NETWORK >/dev/null

# small retry values keep the seed decision and the failed-join case quick; the volume holds
# the instance so that a container can be replaced with or without its data surviving
start_node() { # <name> <peers> [<docker run option>...]
  local name=$1 peers=$2
  shift 2
  docker volume create "vol-$name" >/dev/null
  docker run -d --memory="512m" --network $NETWORK --name "$name" --hostname "$name" \
    -v "vol-$name:/opt/opendj/data" \
    -e ROOT_PASSWORD="$ROOT_PASSWORD" -e ADD_BASE_ENTRY="--addBaseEntry" \
    -e OPENDJ_REPLICATION_TYPE=simple -e REPLICATION_PEERS="$peers" \
    -e REPLICATION_RETRY_COUNT=10 -e REPLICATION_RETRY_INTERVAL=3 -e REPLICATION_ATTEMPT_TIMEOUT=90 \
    "$@" "$IMAGE" >/dev/null
}

# the first peer of REPLICATION_PEERS, and only it, seeds a topology its retries could not
# find; it imports entries no other server ever gets but from an initialize
start_node dj-0 dj-0,dj-1 -e SAMPLE_DATA=10
wait_healthy dj-0
logs_have dj-0 "seeding it with this server's data" || fail "dj-0 did not seed the topology"

# a joining server tries again while its peer is unreachable
docker network disconnect $NETWORK dj-0
start_node dj-1 dj-0,dj-1
wait_until 300 "dj-1 tries again" logs_have dj-1 "trying again in"
docker network connect --alias dj-0 $NETWORK dj-0
wait_healthy dj-1
logs_have dj-1 "joined the replication topology through dj-0" || fail "dj-1 did not join through dj-0"
# the bootstrapped volume of dj-1 was initialized from the topology although its BASE_DN held
# the imported base entry - entries in BASE_DN say nothing about who holds the data. The
# sample entries reached dj-0 by import-ldif, never through the changelog, so only an
# initialize carries them
logs_have dj-1 "initializing from dj-0" || fail "dj-1 did not initialize from the topology"
ldaps_has dj-1 "uid=user.0,ou=People,dc=example,dc=com" || fail "dj-1 lacks the entries only an initialize from dj-0 brings"

# a change made on the seed reaches the replica
add_ou dj-0 replicated
wait_has dj-1 "ou=replicated,dc=example,dc=com"

# a member is ready again right after a restart, without waiting for its peers - gating it
# on them would deadlock a whole-cluster restart under OrderedReady - and replication still
# flows
docker stop dj-1 >/dev/null
docker restart dj-0 >/dev/null
wait_until 120 "dj-0 is healthy while dj-1 is down" is_healthy dj-0
docker start dj-1 >/dev/null
wait_healthy dj-1
add_ou dj-1 replicated2
wait_has dj-0 "ou=replicated2,dc=example,dc=com"
# and neither of them took its replication down to enable it anew: only a server that no
# peer registers any more does that, and a reset followed by a new enable passes every
# check above
for n in dj-0 dj-1; do
  if logs_have $n "the peers no longer register this server"; then fail "$n reset its replication on a plain restart"; fi
done

# a seed that no peer joined holds the data without a replication domain, and its join binds
# with the ROOT_PASSWORD of the bootstrap: once the root password is changed, a restart is
# still healthy, as it is without replication
start_node dj-solo dj-solo
wait_healthy dj-solo
logs_have dj-solo "seeding it with this server's data" || fail "dj-solo did not seed its topology"
docker exec dj-solo /opt/opendj/bin/ldappasswordmodify --noPropertiesFile --hostname localhost --port 1636 \
  --useSsl --trustAll --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" \
  --authzID "dn:cn=Directory Manager" --currentPassword "$ROOT_PASSWORD" --newPassword "changed $ROOT_PASSWORD" >/dev/null
docker restart dj-solo >/dev/null
wait_until 180 "dj-solo is healthy again with its root password changed" is_healthy dj-solo
# and a peer that refuses the bind with ROOT_PASSWORD is past its bootstrap: it may hold the
# data of the topology, so a first peer that finds no other one gives up beside it rather
# than seed a topology of its own
start_node dj-f dj-f,dj-solo -e REPLICATION_RETRY_COUNT=2 -e REPLICATION_RETRY_INTERVAL=5
wait_until 600 "dj-f gives up beside a peer that refuses the bind" logs_have dj-f "could not join the replication topology after 2 attempts"
if logs_have dj-f "seeding it with this server's data"; then fail "dj-f seeded next to a peer past its bootstrap"; fi
docker rm -f dj-f >/dev/null
docker volume rm vol-dj-f >/dev/null
docker rm -f dj-solo >/dev/null
docker volume rm vol-dj-solo >/dev/null

# a Kubernetes pod keeps its /dev/shm across container restarts, shared by all its
# containers: a starting container removes the password files a killed join or replicate.sh
# left there - only those of its own ADMIN_PORT, the files of the other containers of the pod
# are not its to remove
docker run -d --memory="64m" --ipc=shareable --name dj-shm --entrypoint sleep "$IMAGE" 600 >/dev/null
docker exec dj-shm sh -c ': >/dev/shm/opendj-join.4444.killed && : >/dev/shm/opendj-replicate.4444.killed && : >/dev/shm/opendj-join.5444.other'
docker run -d --memory="512m" --network $NETWORK --ipc=container:dj-shm --name dj-shm-probe --hostname dj-shm-probe \
  -e ROOT_PASSWORD="$ROOT_PASSWORD" "$IMAGE" >/dev/null
# the planted files only: the probe's own bootstrap keeps a password file of its ADMIN_PORT
# there while it runs
shm_cleared() { ! docker exec dj-shm sh -c 'test -e /dev/shm/opendj-join.4444.killed || test -e /dev/shm/opendj-replicate.4444.killed'; }
wait_until 60 "run.sh removes the password files of its ADMIN_PORT" shm_cleared
docker exec dj-shm test -e /dev/shm/opendj-join.5444.other || fail "run.sh removed the password file of another container"
docker rm -f dj-shm-probe dj-shm >/dev/null

# a join that cannot succeed keeps the container from reporting itself healthy - across a
# restart too, where the health marker used to follow the upgrade and a failed join turned
# into a healthy, unreplicated server. dj-x may not seed: it is not the first peer of its list
start_node dj-x dj-absent,dj-x
wait_until 300 "the join of dj-x gives up" logs_have dj-x "could not join the replication topology"
stays_unhealthy dj-x "its join failed"
docker restart dj-x >/dev/null
wait_until 300 "the restarted dj-x waits for its join" logs_have dj-x "never joined its replication topology"
second_failure() { [ "$(logs_count dj-x "could not join the replication topology")" -ge 2 ]; }
wait_until 300 "the second join of dj-x gives up" second_failure
stays_unhealthy dj-x "it never joined"
docker rm -f dj-x >/dev/null
docker volume rm vol-dj-x >/dev/null

# the seed lost its volume: behind the same name, a fresh dj-0 finds the topology at dj-1 and
# takes its data from it instead of seeding an empty one next to it
docker rm -f dj-0 >/dev/null
docker volume rm vol-dj-0 >/dev/null
start_node dj-0 dj-0,dj-1
wait_healthy dj-0
logs_have dj-0 "initializing from dj-1" || fail "the reborn dj-0 did not initialize from dj-1"
if logs_have dj-0 "seeding it with this server's data"; then fail "the reborn dj-0 seeded a topology although dj-1 held it"; fi
ldaps_has dj-0 "ou=replicated,dc=example,dc=com" || fail "the reborn dj-0 lacks the data of the topology"
ldaps_has dj-0 "uid=user.0,ou=People,dc=example,dc=com" || fail "the reborn dj-0 lacks the entries of the seed"

# a bootstrap that failed after it imported the base entry leaves a volume that never counts
# as holding the data of the topology: once restarted, dj-bf initializes from dj-0 rather
# than joining with what its bootstrap left
printf 'sh /opt/opendj/bootstrap/setup.sh\nexit 1\n' >"$FAILING_BOOTSTRAP"
chmod 644 "$FAILING_BOOTSTRAP"
start_node dj-bf dj-0,dj-1,dj-bf -v "$FAILING_BOOTSTRAP:/opt/opendj/failing-bootstrap.sh:ro" \
  -e BOOTSTRAP=/opt/opendj/failing-bootstrap.sh
wait_until 300 "the bootstrap of dj-bf fails" logs_have dj-bf "failing-bootstrap.sh failed"
docker restart dj-bf >/dev/null
wait_healthy dj-bf
logs_have dj-bf "initializing from dj-0" || fail "dj-bf joined with the data of its failed bootstrap"
ldaps_has dj-bf "uid=user.0,ou=People,dc=example,dc=com" || fail "dj-bf lacks the entries only an initialize from dj-0 brings"
docker rm -f dj-bf >/dev/null
docker volume rm vol-dj-bf >/dev/null

# an initialize that fails keeps the volume waiting for the data of the topology: a
# one-second bound cuts the dsreplication JVM off before it connects
start_node dj-fi dj-0,dj-fi -e REPLICATION_INITIALIZE_TIMEOUT=1 -e REPLICATION_RETRY_COUNT=2
wait_until 300 "the initialize of dj-fi is cut off" logs_have dj-fi "initialize from dj-0 exited with 124"
stays_unhealthy dj-fi "its initialize failed"
docker exec dj-fi test -f /opt/opendj/data/.replication-initialize-pending \
  || fail "dj-fi dropped its pending marker after a failed initialize"
docker rm -f dj-fi >/dev/null
docker volume rm vol-dj-fi >/dev/null

# scale up to three - the peer list is configuration, so the running servers are replaced
# with the longer list before the third one starts, as a rolling update would
docker rm -f dj-0 dj-1 >/dev/null
start_node dj-0 dj-0,dj-1,dj-2
start_node dj-1 dj-0,dj-1,dj-2
wait_healthy dj-0
wait_healthy dj-1
start_node dj-2 dj-0,dj-1,dj-2
wait_healthy dj-2
ldaps_has dj-2 "ou=replicated,dc=example,dc=com" || fail "dj-2 lacks the data of the topology"

# scale down to two: the survivors, restarted with the shorter list, remove dj-2 from
# cn=admin data and from every replication server list they hold - that of BASE_DN, and
# those of cn=schema and cn=admin data that dsreplication enable configures next to it.
# dsreplication disable cannot, dj-2 being already gone. dj-2 keeps its volume, for the
# scale-up below - and on it, in its cn=config, a join lock held by hand on dj-2 itself
hold_lock dj-2
docker rm -f dj-2 >/dev/null
docker rm -f dj-0 dj-1 >/dev/null
start_node dj-0 dj-0,dj-1
start_node dj-1 dj-0,dj-1
wait_healthy dj-0
wait_healthy dj-1
# whichever survivor's join ran first deletes the cn=admin data entry, the delete replicates
# to the other; each survivor prunes its own replication server lists
removed_dj2() { logs_have dj-0 "removing departed server dj-2" || logs_have dj-1 "removing departed server dj-2"; }
wait_until 180 "a survivor removes dj-2" removed_dj2
for c in dj-0 dj-1; do
  wait_until 120 "dj-2 leaves cn=admin data of $c" admin_data_lacks $c dj-2
  wait_until 120 "dj-2 leaves the replication server lists of $c" lists_lack $c dj-2
done
# replication between the survivors is intact
add_ou dj-0 replicated3
wait_has dj-1 "ou=replicated3,dc=example,dc=com"

# scaled up again on the volume it kept: dj-2 still replicates, but the survivors no longer
# register it, and an enable between two servers whose cn=admin data is replicated registers
# nobody - so dj-2 takes its replication configuration down, enables again and registers.
# The join locks are held by hand meanwhile, and dj-2 must wait for each of them rather than
# go on: first its own, which it takes before its configuration goes, then those of both
# survivors, which its enables take. With its replication down it must not report itself
# ready, publish itself ready to its peers, or take the writes of clients, not even after a
# restart. Its enables are bounded generously, so that it does not break the held locks as
# left behind by a killed join - the one on dj-2 is held since the scale-down
hold_lock dj-0
hold_lock dj-1
start_node dj-2 dj-0,dj-1,dj-2 -e REPLICATION_RETRY_COUNT=30 -e REPLICATION_ATTEMPT_TIMEOUT=1800
wait_until 300 "dj-2 finds that the peers no longer register it" logs_have dj-2 "the peers no longer register this server"
wait_until 120 "dj-2 waits for its own lock" logs_have dj-2 "dj-held is enabling replication through this server, waiting for it"
docker exec dj-2 test ! -e /opt/opendj/.bootstrap-complete || fail "dj-2 reports itself ready while it waits to take its replication down"
[ "$(published_state dj-2)" = rejoining ] || fail "dj-2 does not publish that it is rejoining"
# two rounds at most: start_node passes REPLICATION_RETRY_INTERVAL=3, and a round waits up to
# twice as long
sleep 12
replicates_example dj-2 || fail "dj-2 took its replication down while its own lock was held"
drop_lock dj-2
wait_until 120 "dj-2 waits for dj-0" logs_have dj-2 "dj-held is enabling replication through dj-0, waiting for it"
wait_until 120 "dj-2 waits for dj-1" logs_have dj-2 "dj-held is enabling replication through dj-1, waiting for it"
docker exec dj-2 test ! -e /opt/opendj/.bootstrap-complete || fail "dj-2 reports itself ready while its replication is down"
refuses_writes dj-2 unreplicated || fail "dj-2 takes the writes of clients while its replication is down"
sleep 12
admin_data_lacks dj-0 dj-2 || fail "dj-2 enabled while the locks of both peers were held"
docker restart dj-2 >/dev/null
wait_until 300 "the restarted dj-2 leaves its health to the join" logs_have dj-2 "was taken out of its replication topology"
waits_again() { logs_have_since dj-2 "was taken out of its replication topology" "dj-held is enabling replication through dj-0, waiting for it"; }
wait_until 300 "the restarted dj-2 waits for dj-0 again" waits_again
docker exec dj-2 test ! -e /opt/opendj/.bootstrap-complete || fail "the restarted dj-2 reports itself ready while its replication is down"
[ "$(published_state dj-2)" = rejoining ] || fail "the restarted dj-2 publishes itself ready while its replication is down"
refuses_writes dj-2 unreplicated || fail "the restarted dj-2 takes the writes of clients while its replication is down"
# its own lock once more, now with both survivors free: an enable configures both of its
# servers, so it waits for this one as well. The lock is taken as soon as no round of dj-2
# holds it
wait_until 120 "the lock of dj-2 is held by hand" hold_lock dj-2
own_waits=$(logs_count dj-2 "dj-held is enabling replication through this server, waiting for it")
drop_lock dj-0
drop_lock dj-1
own_waits_again() { [ "$(logs_count dj-2 "dj-held is enabling replication through this server, waiting for it")" -gt "$own_waits" ]; }
wait_until 120 "dj-2 waits for its own lock with both survivors free" own_waits_again
sleep 12
admin_data_lacks dj-0 dj-2 || fail "dj-2 enabled while its own lock was held"
# a lock under its own name was left by a join of an earlier start of dj-2, a start running
# one join: it is broken at once, not once it is older than an enable may take
self=$(docker exec dj-2 hostname -f | tr '[:upper:]' '[:lower:]')
relabel_lock dj-2 "$self"
wait_until 120 "dj-2 breaks the lock left under its own name" logs_have dj-2 "breaking the lock $self took on localhost"
wait_healthy dj-2
registered_dj2() { ! admin_data_lacks dj-0 dj-2; }
wait_until 300 "dj-2 registers in the topology again" registered_dj2
[ "$(published_state dj-2)" = ready ] || fail "the rejoined dj-2 does not publish itself ready"
add_ou dj-2 rejoined
wait_has dj-0 "ou=rejoined,dc=example,dc=com"

# the deprecated one-shot sdsr path of replicate.sh still bootstraps a replica. On a first
# start a server stops the server its bootstrap started and starts it again, and a replica
# can reach it in between: replicate.sh tries a dsreplication enable that could not connect
# again (exit 8). The replica starts on a network of its own, where dj-0 does not resolve, and
# moves to the network of the topology only once it has said it will try again: taking dj-0
# off the network for as long instead would leave its replication server holding the dead
# connection of dj-0's own directory server, and route the initialize of the replica into it
docker network create $NETWORK_ALONE >/dev/null
docker run -d --memory="512m" --network $NETWORK_ALONE --name dj-sdsr --hostname dj-sdsr \
  -e ROOT_PASSWORD="$ROOT_PASSWORD" -e MASTER_SERVER=dj-0 -e OPENDJ_REPLICATION_TYPE=sdsr "$IMAGE" >/dev/null
wait_until 420 "dj-sdsr tries again" logs_have dj-sdsr "exited with 8, trying again"
docker network connect $NETWORK dj-sdsr
docker network disconnect $NETWORK_ALONE dj-sdsr
wait_healthy dj-sdsr
wait_has dj-sdsr "ou=replicated3,dc=example,dc=com"
# dj-sdsr publishes no state, as a server of an image before this one does, and it
# replicates BASE_DN, so it may hold the data of the topology: a fresh first peer whose only
# other peer it is gives up rather than seed next to it
start_node dj-f dj-f,dj-sdsr -e REPLICATION_RETRY_COUNT=2 -e REPLICATION_RETRY_INTERVAL=5
wait_until 300 "dj-f gives up" logs_have dj-f "could not join the replication topology after 2 attempts"
if logs_have dj-f "seeding it with this server's data"; then fail "dj-f seeded next to a member that publishes no state"; fi
stays_unhealthy dj-f "a peer that may hold the data answers"
docker rm -f dj-f >/dev/null
docker volume rm vol-dj-f >/dev/null
# replicate.sh tries dsreplication enable again only when it exits 8, and a failed enable
# ends it: run once more on the replica for a base DN neither server holds, the enable exits
# 5 (REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN) and nothing is tried again or initialized
rc=0
out=$(docker exec -e BASE_DN=dc=absent,dc=com -e ROOT_USER_DN="cn=Directory Manager" dj-sdsr \
  timeout 90 /opt/opendj/bootstrap/replicate.sh 2>&1) || rc=$?
if [ "$rc" -ne 5 ] || grep -E "trying again|initializing replication" <<<"$out" >/dev/null; then
  echo "$out"
  fail "replicate.sh for a base DN nobody holds exited with $rc, not with the 5 of its dsreplication enable, or went on after it"
fi

# the root password shows in no container log, and the files the tools read it from are gone
for c in dj-0 dj-1 dj-2 dj-sdsr; do
  if docker logs $c 2>&1 | grep -F -- "$ROOT_PASSWORD"; then fail "the root password is in the log of $c"; fi
  left=$(docker exec $c grep -rlsF -- "$ROOT_PASSWORD" /tmp /dev/shm || true)
  [ -z "$left" ] || fail "the root password is left in $left of $c"
done
docker rm -f dj-0 dj-1 dj-2 dj-sdsr >/dev/null
docker volume rm vol-dj-0 vol-dj-1 vol-dj-2 >/dev/null

# MASTER_SERVER may name the master by its address, as the grep of /etc/hosts in the base
# replicate.sh allowed: a master given its own address recognises itself and seeds at once,
# and a replica joins and initializes through that address
for n in dj-ipm dj-ipr; do
  if [ $n = dj-ipm ]; then extra="--ip $MASTER_ADDRESS -e SAMPLE_DATA=10"; else extra="-v vol-dj-ipr:/opt/opendj/data"; fi
  docker run -d --memory="512m" --network $NETWORK --name $n --hostname $n $extra \
    -e ROOT_PASSWORD="$ROOT_PASSWORD" -e ADD_BASE_ENTRY="--addBaseEntry" \
    -e OPENDJ_REPLICATION_TYPE=simple -e MASTER_SERVER=$MASTER_ADDRESS \
    -e REPLICATION_RETRY_COUNT=10 -e REPLICATION_RETRY_INTERVAL=3 "$IMAGE" >/dev/null
  wait_healthy $n
done
logs_have dj-ipm "seeding it with this server's data" || fail "dj-ipm did not recognise itself by its address"
logs_have dj-ipr "initializing from $MASTER_ADDRESS" || fail "dj-ipr did not initialize from the master's address"
ldaps_has dj-ipr "uid=user.0,ou=People,dc=example,dc=com" || fail "dj-ipr lacks the entries of the master"
# moved from MASTER_SERVER to a REPLICATION_PEERS of names, the replica keeps the master that
# is registered by its address - in cn=admin data and in its replication server lists -
# rather than taking it for a server that left the topology
docker rm -f dj-ipr >/dev/null
docker run -d --memory="512m" --network $NETWORK --name dj-ipr --hostname dj-ipr -v vol-dj-ipr:/opt/opendj/data \
  -e ROOT_PASSWORD="$ROOT_PASSWORD" -e OPENDJ_REPLICATION_TYPE=simple -e REPLICATION_PEERS=dj-ipm,dj-ipr \
  -e REPLICATION_RETRY_COUNT=2 -e REPLICATION_RETRY_INTERVAL=3 "$IMAGE" >/dev/null
wait_until 300 "the moved dj-ipr has looked for departed servers" logs_have dj-ipr "the health check may probe it"
if logs_have dj-ipr "removing departed server $MASTER_ADDRESS" || logs_have dj-ipr "removing departed replication server $MASTER_ADDRESS:"; then
  fail "dj-ipr removed the master registered by its address"
fi
if admin_data_lacks dj-ipr "$MASTER_ADDRESS"; then fail "the master registered by its address left cn=admin data of dj-ipr"; fi
docker rm -f dj-ipm dj-ipr >/dev/null
docker volume rm vol-dj-ipr >/dev/null

# three fresh servers started together, as Compose or a StatefulSet with
# podManagementPolicy: Parallel start them: none of them takes the bootstrap data of another
# fresh one for the topology's. The first peer seeds, the others initialize from it - its
# sample entries reach them only that way - and the replication servers know each other
# although the joins ran at the same time
for n in dj-p0 dj-p1 dj-p2; do
  if [ $n = dj-p0 ]; then extra="-e SAMPLE_DATA=10"; else extra=; fi
  start_node $n dj-p0,dj-p1,dj-p2 -e REPLICATION_RETRY_COUNT=20 $extra
done
for n in dj-p0 dj-p1 dj-p2; do
  wait_healthy $n
done
logs_have dj-p0 "seeding it with this server's data" || fail "dj-p0 did not seed the topology"
for n in dj-p1 dj-p2; do
  if logs_have $n "seeding it with this server's data"; then fail "$n seeded a topology although it is not the first peer"; fi
  logs_have $n "initializing from dj-p0" || fail "$n did not initialize from dj-p0"
  if logs_have $n "initializing from dj-p1" || logs_have $n "initializing from dj-p2"; then
    fail "$n initialized from a server that holds only bootstrap data"
  fi
  ldaps_has $n "uid=user.0,ou=People,dc=example,dc=com" || fail "$n lacks the entries of the seed"
done
wait_until 120 "the replication server of dj-p0 knows dj-p1 and dj-p2" server_lists dj-p0 dj-p1 dj-p2
wait_until 120 "the replication server of dj-p1 knows dj-p0 and dj-p2" server_lists dj-p1 dj-p0 dj-p2
wait_until 120 "the replication server of dj-p2 knows dj-p0 and dj-p1" server_lists dj-p2 dj-p0 dj-p1
add_ou dj-p1 parallel
wait_has dj-p0 "ou=parallel,dc=example,dc=com"
wait_has dj-p2 "ou=parallel,dc=example,dc=com"
# dj-p1 and dj-p2 enabled through dj-p0 one at a time, and each removed its lock after
if admin_search dj-p0 "cn=Docker Join Lock,cn=config" base "(objectClass=*)" 1.1 | grep "^dn:" >/dev/null; then
  fail "a join left its lock on dj-p0"
fi
for c in dj-p0 dj-p1 dj-p2; do
  if docker logs $c 2>&1 | grep -F -- "$ROOT_PASSWORD"; then fail "the root password is in the log of $c"; fi
done

cleanup
echo "Docker replication test passed"
