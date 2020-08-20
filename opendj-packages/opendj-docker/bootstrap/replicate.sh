#!/usr/bin/env bash
# Replicate to the master server hostname defined in $1
# If that server is ourself this is a no-op

# This is a bit  kludgy.
# The hostname has to be a fully resolvable DNS name in the cluster
# If the service is called

MYHOSTNAME=${MYHOSTNAME:-`hostname -f`}
export PATH=/opt/opendj/bin:$PATH

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

# todo: Replace with command to test for master being reachable and up
# This is hacky....
echo "Will sleep for a bit to ensure master is up"

sleep 5

if [ "$OPENDJ_REPLICATION_TYPE" == "simple" ]; then
  echo "Enabling Standard Replication..."
  /opt/opendj/bin/dsreplication enable --host1 $MASTER_SERVER --port1 4444 \
    --bindDN1 "$ROOT_USER_DN" \
    --bindPassword1 $ROOT_PASSWORD --replicationPort1 8989 \
    --host2 $MYHOSTNAME --port2 4444 --bindDN2 "$ROOT_USER_DN" \
    --bindPassword2 $ROOT_PASSWORD --replicationPort2 8989 \
    --adminUID admin --adminPassword $ROOT_PASSWORD --baseDN $BASE_DN -X -n

  echo "initializing replication"

  # replicating data in MASTER_SERVER to MYHOSTNAME:
  /opt/opendj/bin/dsreplication initialize --baseDN $BASE_DN \
    --adminUID admin --adminPassword $ROOT_PASSWORD \
    --hostSource $MASTER_SERVER --portSource 4444 \
    --hostDestination $MYHOSTNAME --portDestination 4444 -X -n

elif [ "$OPENDJ_REPLICATION_TYPE" == "srs" ]; then
  echo "Enabling Standalone Replication Servers..."
  dsreplication enable \
   --adminUID admin \
   --adminPassword $ROOT_PASSWORD \
   --baseDN $BASE_DN \
   --host1 $MYHOSTNAME \
   --port1 4444 \
   --bindDN1 "$ROOT_USER_DN" \
   --bindPassword1 $ROOT_PASSWORD \
   --noReplicationServer1 \
   --host2 $MASTER_SERVER \
   --port2 4444 \
   --bindDN2 "$ROOT_USER_DN" \
   --bindPassword2 $ROOT_PASSWORD \
   --replicationPort2 8989 \
   --onlyReplicationServer2 \
   --trustAll \
   --no-prompt;

  echo "initializing replication"

  dsreplication \
   initialize-all \
   --adminUID admin \
   --adminPassword $ROOT_PASSWORD \
   --baseDN $BASE_DN \
   --hostname $MYHOSTNAME \
   --port 4444 \
   --trustAll \
   --no-prompt

elif [ "$OPENDJ_REPLICATION_TYPE" == "sdsr" ]; then
  echo "Enabling Standalone Directory Server Replicas...."
  dsreplication \
   enable \
   --adminUID admin \
   --adminPassword $ROOT_PASSWORD \
   --baseDN $BASE_DN \
   --host1 $MASTER_SERVER \
   --port1 4444 \
   --bindDN1 "$ROOT_USER_DN" \
   --bindPassword1 $ROOT_PASSWORD \
   --host2 $MYHOSTNAME \
   --port2 4444 \
   --bindDN2 "$ROOT_USER_DN" \
   --bindPassword2 $ROOT_PASSWORD \
   --noReplicationServer2 \
   --trustAll \
   --no-prompt

 echo "initializing replication"

 dsreplication \
   initialize \
   --adminUID admin \
   --adminPassword $ROOT_PASSWORD \
   --baseDN $BASE_DN \
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
   --bindPassword $ROOT_PASSWORD \
   --provider-name "Multimaster Synchronization" \
   --domain-name $BASE_DN \
   --set group-id:$OPENDJ_REPLICATION_GROUP_ID \
   --trustAll \
   --no-prompt

   dsconfig \
    set-replication-server-prop \
    --port 4444 \
    --hostname $MASTER_SERVER \
    --bindDN "$ROOT_USER_DN" \
    --bindPassword $ROOT_PASSWORD \
    --provider-name "Multimaster Synchronization" \
    --set group-id:$OPENDJ_REPLICATION_GROUP_ID \
    --trustAll \
    --no-prompt

else
  echo "Unknown replication type, skiping replication..."
fi
