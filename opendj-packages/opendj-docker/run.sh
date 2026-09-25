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

# A replicate.sh killed before its EXIT trap ran leaves the root password in /dev/shm, and on
# Kubernetes that outlives the container: the pod keeps its /dev/shm across container restarts
rm -f /dev/shm/opendj-replicate.*

# Keystores and truststores mounted as a volume (a Kubernetes Secret, say) are copied into
# the instance on every start, not only on the one that bootstraps it: the instance lives on
# a persistent volume, and a renewed certificate in the Secret has to reach it.
SECRET_VOLUME=${SECRET_VOLUME:-/var/secrets/opendj}
# Kubernetes updates a mounted Secret in place, so while the server runs the volume is
# checked again every that many seconds; 0 checks it on start only
SECRET_VOLUME_REFRESH=${SECRET_VOLUME_REFRESH:-60}

# Copies the key* and trust* files of the secret volume that differ from those in
# ./data/config. Each one is written next to its target and renamed over it, so the server
# never reads a file half copied, and it is readable by the server's user only: a keystore
# holds the private key, a .pin file its password. With "stores", the .pin files are left
# alone. Succeeds when it copied a file.
copy_secrets() {
  local src dst tmp copied=1
  [ -d "$SECRET_VOLUME" ] || return 1
  for src in "$SECRET_VOLUME"/key* "$SECRET_VOLUME"/trust*; do
    [ -f "$src" ] || continue
    [ "$1" = stores ] && [[ $src == *.pin ]] && continue
    dst=./data/config/$(basename -- "$src")
    cmp -s "$src" "$dst" && continue
    if tmp=$(mktemp "$dst.XXXXXX") && cp "$src" "$tmp" && chmod 600 "$tmp" && mv -f "$tmp" "$dst"; then
      echo "Copied $(basename -- "$src") from the secret volume"
      copied=0
    else
      rm -f "$tmp"
      echo "Could not copy $(basename -- "$src") from the secret volume"
    fi
  done
  return $copied
}

# The files are compared one at a time, so a Secret updated in the middle of a pass can leave
# a keystore of one version next to the .pin of the other. Passes are repeated until one finds
# nothing left to copy, which puts the files of a single version back together.
sync_secrets() {
  local passes=0
  while copy_secrets "$@" && [ $((passes += 1)) -lt 5 ]; do :; done
}

# The server reads a .pin file when it starts and keeps that password, but it opens the keystore
# file again whenever a connection handler checks a change to its configuration: a keystore it can
# no longer open with that password makes the next dsconfig change to the LDAPS handler disable
# the handler. So while the server runs, the .pin files in ./data/config stay those it started
# with, and the stores are not copied as long as a password on the volume differs from them;
# the next start copies the new files.
pins_unchanged() {
  local src
  for src in "$SECRET_VOLUME"/key*.pin "$SECRET_VOLUME"/trust*.pin; do
    [ -f "$src" ] || continue
    cmp -s "$src" "./data/config/$(basename -- "$src")" || return 1
  done
}

watch_secrets() {
  local held=false
  while sleep "$SECRET_VOLUME_REFRESH"; do
    if pins_unchanged; then
      held=false
      sync_secrets stores
    elif [ "$held" = false ]; then
      held=true
      echo "A password on the secret volume changed, the secret volume is copied again on the next start"
    fi
  done
}

# Both kinds of start end here, with the server as PID 1 of the container: that is the
# process the container runtime sends SIGTERM to, and the server stops cleanly on it.
start_server() {
  if [ -d "$SECRET_VOLUME" ]; then
    echo "Secret volume is present. Will copy any keystores and truststore"
    sync_secrets
    if [[ $SECRET_VOLUME_REFRESH =~ ^[0-9]+$ ]] && [ "$SECRET_VOLUME_REFRESH" -gt 0 ]; then
      watch_secrets &
    elif ! [[ $SECRET_VOLUME_REFRESH =~ ^0+$ ]]; then
      echo "SECRET_VOLUME_REFRESH=$SECRET_VOLUME_REFRESH is not a whole number of seconds above 0, the secret volume is copied on start only"
    fi
  fi
  echo "Starting OpenDJ"
  exec ./bin/start-ds --nodetach
}

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
  start_server
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

# Setup started the server in the background, and it cannot stay that way: the container's
# PID 1 would be this script, which the kernel delivers no SIGTERM to, and the server would
# keep the certificate it was set up with rather than the one on the secret volume. So it is
# stopped here and started again in the foreground, the way every later start runs it. It is
# stopped before the marker below is written, so that the health check never reports the
# server of the bootstrap healthy just before it goes down. stop-ds exits 0 when the server
# is not running; a server still up when it gives up would make start-ds below refuse to start.
./bin/stop-ds || { echo "The server the bootstrap started did not stop (stop-ds exited $?)"; exit 1; }

# Everything the instance was asked to be set up with - its backend, its base entry, its
# replication - is in place from here on, so the health check may start probing the server
if [ "$BOOTSTRAPPED" = true ]; then
  touch "$BOOTSTRAP_COMPLETE"
  echo "The instance is bootstrapped, the health check may probe it"
fi

start_server
