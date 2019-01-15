#!/usr/bin/env bash
# Replicate to the master server hostname defined in $1
# If that server is ourself this is a no-op

# This is a bit  kludgy.
# The hostname has to be a fully resolvable DNS name in the cluster
# If the service is called

MYHOSTNAME=${MYHOSTNAME:-`hostname -f`}

echo "Setting up replication from $MYHOSTNAME to $MASTER_SERVER"


# For debug


# K8s puts the service name in /etc/hosts
if grep ${MASTER_SERVER} /etc/hosts; then
 echo "We are the master. Skipping replication setup to ourself"
 exit 0
fi

# Comment out
echo "replicate ENV vars:"
env



echo "enabling replication"

# todo: Replace with command to test for master being reachable and up
# This is hacky....
echo "Will sleep for a bit to ensure master is up"

sleep 30


/opt/opendj/bin/dsreplication enable --host1 $MYHOSTNAME --port1 4444 \
  --bindDN1 "cn=directory manager" \
  --bindPassword1 $ROOT_PASSWORD --replicationPort1 8989 \
  --host2 $MASTER_SERVER --port2 4444 --bindDN2 "cn=directory manager" \
  --bindPassword2 $ROOT_PASSWORD --replicationPort2 8989 \
  --adminUID admin --adminPassword $ROOT_PASSWORD --baseDN $BASE_DN -X -n

echo "initializing replication"

/opt/opendj/bin/dsreplication initialize --baseDN $BASE_DN \
  --adminUID admin --adminPassword $ROOT_PASSWORD \
  --hostSource $MYHOSTNAME --portSource 4444 \
  --hostDestination $MASTER_SERVER --portDestination 4444 -X -n
