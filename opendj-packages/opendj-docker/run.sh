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
# Portions copyright 2026 3A Systems, LLC.

# Run the OpenDJ server
# The idea is to consolidate all of the writable DJ directories to
# a single instance directory root, and update DJ's instance.loc file to point to that root
# This allows us to to mount a data volume on that root which gives us
# persistence across restarts of OpenDJ.
# For Docker - mount a data volume on /opt/opendj/data
# For Kubernetes mount a PV

cd /opt/opendj

# The health check probes the server only once this marker is there, so that "healthy"
# means the instance is bootstrapped rather than merely listening: setup starts the
# server in the middle of the bootstrap, before the backend holding BASE_DN has been
# created. Nothing below writes it unless the step it stands for reported success, so a
# bootstrap that failed leaves the container running to be looked at, but never healthy.
# It is kept outside ./data because it records what this container has done, not what
# the volume holds - and a restart of a container replays this script over the writable
# layer the previous run left behind, so it is cleared before anything else.
BOOTSTRAP_COMPLETE=${BOOTSTRAP_COMPLETE:-/opt/opendj/.bootstrap-complete}
rm -f "$BOOTSTRAP_COMPLETE"

#if default data folder exists do not change it
if [ ! -d ./db ]; then
  echo "/opt/opendj/data" >/opt/opendj/instance.loc && \
  mkdir -p /opt/opendj/data/lib/extensions
fi

# Instance dir does exist? We start opendj without detach
if [ -d ./data/config ]; then
  # nothing is bootstrapped here, the instance is already there - but a half-migrated one
  # is not ready to serve either, so the marker follows the upgrade
  if sh ./upgrade -n; then
    touch "$BOOTSTRAP_COMPLETE"
  else
    echo "Upgrade failed, this container will not report itself healthy"
  fi
  exec ./bin/start-ds --nodetach
  exit
fi

# If we are here, opendj is not installed & we need to run setup
echo "Instance data Directory is empty. Creating new DJ instance"

export BASE_DN=${BASE_DN:-"dc=example,dc=com"}
echo "BASE DN is ${BASE_DN}"

export ROOT_PASSWORD=${ROOT_PASSWORD:-password}

BOOTSTRAP=${BOOTSTRAP:-/opt/opendj/bootstrap/setup.sh}
echo "Running $BOOTSTRAP"
BOOTSTRAPPED=true
if ! sh "${BOOTSTRAP}"; then
  BOOTSTRAPPED=false
  echo "$BOOTSTRAP failed, this container will not report itself healthy"
fi

# Check if OPENDJ_REPLICATION_TYPE var is set. If it is - replicate to that server
if [ -n "${MASTER_SERVER}" ] && [ -n "${OPENDJ_REPLICATION_TYPE}" ]; then
  if ! /opt/opendj/bootstrap/replicate.sh; then
    BOOTSTRAPPED=false
    echo "Replication setup failed, this container will not report itself healthy"
  fi
fi

# Check if keystores are mounted as a volume, and if so
# Copy any keystores over
SECRET_VOLUME=${SECRET_VOLUME:-/var/secrets/opendj}

if [ -d "${SECRET_VOLUME}" ]; then
  echo "Secret volume is present. Will copy any keystores and truststore"
  # We send errors to /dev/null in case no data exists.
  cp -f ${SECRET_VOLUME}/key* ${SECRET_VOLUME}/trust* ./data/config 2>/dev/null
fi

# Everything the instance was asked to be set up with - its backend, its base entry, its
# replication - is in place from here on, so the health check may start probing the server
if [ "$BOOTSTRAPPED" = true ]; then
  touch "$BOOTSTRAP_COMPLETE"
fi

# Opendj is probably already started in detach mode at the install
if (bin/status -n | grep Started); then
  echo "OpenDJ is started"
  
  # Use tail instead of sleep to allow the container to be stopped with SIGTERM
  tail -f /dev/null
fi

echo "Starting OpenDJ"
exec ./bin/start-ds --nodetach
