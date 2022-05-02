#!/usr/bin/env bash
# Run the OpenDJ server
# The idea is to consolidate all of the writable DJ directories to
# a single instance directory root, and update DJ's instance.loc file to point to that root
# This allows us to to mount a data volume on that root which  gives us
# persistence across restarts of OpenDJ.
# For Docker - mount a data volume on /opt/opendj/data
# For Kubernetes mount a PV

cd /opt/opendj

#if default data folder exists do not change it
if [ ! -d ./db ]; then
  echo "/opt/opendj/data" >/opt/opendj/instance.loc && \
  mkdir -p /opt/opendj/data/lib/extensions
fi

# Instance dir does not exist? Then we need to run setup
if [ ! -d ./data/config ]; then
  echo "Instance data Directory is empty. Creating new DJ instance"

  export BASE_DN=${BASE_DN:-"dc=example,dc=com"}
  echo "BASE DN is ${BASE_DN}"

  export PASSWORD=${ROOT_PASSWORD:-password}
  echo "Password set to $PASSWORD"

  BOOTSTRAP=${BOOTSTRAP:-/opt/opendj/bootstrap/setup.sh}
  echo "Running $BOOTSTRAP"
  sh "${BOOTSTRAP}"

  # Check if OPENDJ_REPLICATION_TYPE var is set. If it is - replicate to that server
  if [ ! -z ${MASTER_SERVER} ] && [ ! -z ${OPENDJ_REPLICATION_TYPE} ]; then
    /opt/opendj/bootstrap/replicate.sh
  fi
else
  sh ./upgrade -n
  exec ./bin/start-ds --nodetach
  return
fi

# Check if keystores are mounted as a volume, and if so
# Copy any keystores over
SECRET_VOLUME=${SECRET_VOLUME:-/var/secrets/opendj}

if [ -d "${SECRET_VOLUME}" ]; then
  echo "Secret volume is present. Will copy any keystores and truststore"
  # We send errors to /dev/null in case no data exists.
  cp -f ${SECRET_VOLUME}/key* ${SECRET_VOLUME}/trust* ./data/config 2>/dev/null
fi

# todo: Check /opt/opendj/data/config/buildinfo
# Run upgrade if the server is older

if (bin/status -n | grep Started); then
  echo "OpenDJ is started"
  # We cant exit because we are pid 1
  while true; do sleep 100000; done
fi

echo "Try to upgrade OpenDJ"
sh ./upgrade -n

echo "Starting OpenDJ"
exec ./bin/start-ds --nodetach
