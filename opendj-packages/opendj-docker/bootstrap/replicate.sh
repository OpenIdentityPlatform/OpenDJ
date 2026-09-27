#!/usr/bin/env bash
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
# Portions Copyright 2026 3A Systems, LLC.

# Replicate to the master server hostname defined in $1
# If that server is ourself this is a no-op

# This is a bit  kludgy.
# The hostname has to be a fully resolvable DNS name in the cluster
# If the service is called

MYHOSTNAME=${MYHOSTNAME:-$(hostname -f)}
export PATH=/opt/opendj/bin:$PATH

echo "Setting up replication from $MYHOSTNAME to $MASTER_SERVER"

# For debug

# K8s puts the service name in /etc/hosts
if grep ${MASTER_SERVER} /etc/hosts; then
  echo "We are the master. Skipping replication setup to ourself"
  exit 0
fi

echo "Replication type: $OPENDJ_REPLICATION_TYPE, base DN: $BASE_DN"

# The tools read the root password from a file, so that it shows neither in the container log
# nor on the command line of a process while the tool runs. mktemp creates the file readable by
# its owner only. It goes to the tmpfs of /dev/shm where there is one: the EXIT trap does not
# run if the script is killed, and a file left in /tmp would stay in the writable layer of the
# container, where no later start removes it, since replicate.sh runs only on the first one.
# On Kubernetes /dev/shm belongs to the pod: it is shared by all its containers and outlives a
# restart of this one, so run.sh removes a file left there by name on every start.
PASSWORD_FILE=$(mktemp -p /dev/shm opendj-replicate.XXXXXX 2>/dev/null || mktemp) || exit 1
trap 'rm -f "$PASSWORD_FILE"' EXIT
printf '%s\n' "$ROOT_PASSWORD" >"$PASSWORD_FILE" || exit 1

# The master stops the server its bootstrap started and starts it again in the foreground once
# that bootstrap is done (run.sh), so a replica started together with it can reach it while it
# is down. A tool that fails that way is run again, every 10 s for up to 5 minutes.
# dsreplication enable exits 8 (ERROR_CONNECTING) when it cannot connect or bind to one of the two
# servers, which it checks before it changes anything, and also when another server of the topology
# cannot be reached after it has written to the first two. Only that exit code is tried again: an
# enable that failed otherwise may have replicated the base DN already, and a second one then fails.
# A wrong root DN or password also exits 8, so it fails only once the 5 minutes are over.
# initialize and dsconfig set-* can be run again whatever made them fail.
# retry <exit code to try again on, or "any"> <command> [<argument>...]
retry() {
  local on=$1 rc i
  shift
  for i in $(seq 1 30); do
    "$@" && return 0
    rc=$?
    if [ "$on" != any ] && [ "$rc" -ne "$on" ] || [ "$i" -eq 30 ]; then
      return $rc
    fi
    echo "$(basename "$1") $2 exited with $rc, trying again in 10 s"
    sleep 10
  done
}

# todo: Replace with command to test for master being reachable and up
# This is hacky....
echo "Will sleep for a bit to ensure master is up"

sleep 5

if [ "$OPENDJ_REPLICATION_TYPE" == "simple" ]; then
  echo "Enabling Standard Replication..."
  retry 8 /opt/opendj/bin/dsreplication \
    enable \
    --host1 $MASTER_SERVER \
    --port1 4444 \
    --bindDN1 "$ROOT_USER_DN" \
    --bindPasswordFile1 "$PASSWORD_FILE" --replicationPort1 8989 \
    --host2 $MYHOSTNAME --port2 4444 --bindDN2 "$ROOT_USER_DN" \
    --bindPasswordFile2 "$PASSWORD_FILE" --replicationPort2 8989 \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" -X -n || exit

  echo "initializing replication"

  # replicating data in MASTER_SERVER to MYHOSTNAME:
  retry any /opt/opendj/bin/dsreplication initialize --baseDN "$BASE_DN" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --hostSource $MASTER_SERVER --portSource 4444 \
    --hostDestination $MYHOSTNAME --portDestination 4444 -X -n

elif [ "$OPENDJ_REPLICATION_TYPE" == "srs" ]; then
  echo "Enabling Standalone Replication Servers..."
  retry 8 dsreplication enable \
    --adminUID admin \
    --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" \
    --host1 $MYHOSTNAME \
    --port1 4444 \
    --bindDN1 "$ROOT_USER_DN" \
    --bindPasswordFile1 "$PASSWORD_FILE" \
    --noReplicationServer1 \
    --host2 $MASTER_SERVER \
    --port2 4444 \
    --bindDN2 "$ROOT_USER_DN" \
    --bindPasswordFile2 "$PASSWORD_FILE" \
    --replicationPort2 8989 \
    --onlyReplicationServer2 \
    --trustAll \
    --no-prompt || exit

  echo "initializing replication"

  retry any dsreplication \
    initialize-all \
    --adminUID admin \
    --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" \
    --hostname $MYHOSTNAME \
    --port 4444 \
    --trustAll \
    --no-prompt

elif [ "$OPENDJ_REPLICATION_TYPE" == "sdsr" ]; then
  echo "Enabling Standalone Directory Server Replicas...."
  retry 8 dsreplication \
    enable \
    --adminUID admin \
    --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" \
    --host1 $MASTER_SERVER \
    --port1 4444 \
    --bindDN1 "$ROOT_USER_DN" \
    --bindPasswordFile1 "$PASSWORD_FILE" \
    --host2 $MYHOSTNAME \
    --port2 4444 \
    --bindDN2 "$ROOT_USER_DN" \
    --bindPasswordFile2 "$PASSWORD_FILE" \
    --noReplicationServer2 \
    --trustAll \
    --no-prompt || exit

  echo "initializing replication"

  retry any dsreplication \
    initialize \
    --adminUID admin \
    --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" \
    --hostSource $MASTER_SERVER \
    --portSource 4444 \
    --hostDestination $MYHOSTNAME \
    --portDestination 4444 \
    --trustAll \
    --no-prompt

elif [ "$OPENDJ_REPLICATION_TYPE" == "rg" ]; then
  echo "Enabling Replication Groups..."

  dsconfig \
    set-replication-domain-prop \
    --port 4444 \
    --hostname $MYHOSTNAME \
    --bindDN "$ROOT_USER_DN" \
    --bindPasswordFile "$PASSWORD_FILE" \
    --provider-name "Multimaster Synchronization" \
    --domain-name "$BASE_DN" \
    --set group-id:$OPENDJ_REPLICATION_GROUP_ID \
    --trustAll \
    --no-prompt

  retry any dsconfig \
    set-replication-server-prop \
    --port 4444 \
    --hostname $MASTER_SERVER \
    --bindDN "$ROOT_USER_DN" \
    --bindPasswordFile "$PASSWORD_FILE" \
    --provider-name "Multimaster Synchronization" \
    --set group-id:$OPENDJ_REPLICATION_GROUP_ID \
    --trustAll \
    --no-prompt

else
  echo "Unknown replication type, skipping replication..."
fi
