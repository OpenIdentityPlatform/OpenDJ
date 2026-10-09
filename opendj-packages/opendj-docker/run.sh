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

# A replicate.sh or a setup.sh killed before its EXIT trap ran leaves the root password in
# /dev/shm, and on Kubernetes that outlives the container: the pod keeps its /dev/shm across
# container restarts. It is also shared by all the containers of the pod, and another one may
# be bootstrapping right now, so only the files of this container are removed: they carry
# ADMIN_PORT in their name, which the containers of a pod cannot share as they share one
# network namespace. Containers in distinct network namespaces that share /dev/shm through
# --ipc=host, and listen on the same ADMIN_PORT, are not told apart. /tmp belongs to this
# container alone, so a password file left there is removed whatever its ADMIN_PORT.
rm -f /dev/shm/opendj-replicate."$ADMIN_PORT".* /dev/shm/opendj-join."$ADMIN_PORT".*
rm -f /dev/shm/opendj-setup-password."$ADMIN_PORT".*
rm -f /tmp/opendj-setup-password.* /tmp/opendj-replicate.* /tmp/opendj-join.*

# What the volume went through is recorded next to the instance: a volume this script
# bootstraps carries the marker from before its bootstrap until the join has initialized it
# from the topology (see bootstrap/join.sh), however many restarts that takes
export INITIALIZE_PENDING=${INITIALIZE_PENDING:-/opt/opendj/data/.replication-initialize-pending}
# and a volume whose replication the join took down to enable it anew carries this one until
# the enable registered it again: it holds the data of the topology, but takes writes that
# replicate nowhere meanwhile
export REJOIN_PENDING=${REJOIN_PENDING:-/opt/opendj/data/.replication-rejoin-pending}
# A volume this script bootstraps carries this one from before its bootstrap until the whole
# of it - the bootstrap script, and the replicate.sh of a one-shot replication type - has
# succeeded (#1182). A bootstrap that failed or was killed after setup wrote ./data/config
# leaves the volume looking installed, and the next start would otherwise report a server
# healthy that has no backend or no base entry. Volumes bootstrapped by images before this one
# carry no mark, and start as they did.
export BOOTSTRAP_PENDING=${BOOTSTRAP_PENDING:-/opt/opendj/data/.bootstrap-pending}
# And a volume this script upgrades to another version carries this one from before the
# upgrade until the upgrade succeeded, post-upgrade tasks included (#1185). The upgrade records
# the new version in config/buildinfo before it runs those tasks - the index rebuilds, and the
# verification of the DN equality indexes - so after one of them failed, or the container was
# stopped in the middle of them, the next run of upgrade finds nothing to do and succeeds.
UPGRADE_PENDING=${UPGRADE_PENDING:-/opt/opendj/data/.upgrade-pending}

# major.minor.point of a buildinfo file: the part of the version the upgrade compares
build_version() { # <buildinfo>
  head -n 1 "$1" 2>/dev/null | cut -d. -f1-3
}

# Upgrades the instance to the version of this image, and fails where the upgrade fails or
# where an earlier start left it incomplete: the mark of a volume whose version is already
# the one of this image can only come from an upgrade that recorded it and did not complete.
# One that did not get that far is run again, as before. An instance without a version to read
# is not marked: the upgrade refuses it, and setup never got far enough to call it installed.
# --force performs the tasks that -n alone answers with their default no, such as rebuilding
# indexes: nobody is there to run them by hand afterwards, as the native packages do too
upgrade_instance() {
  local from to
  from=$(build_version ./data/config/buildinfo)
  to=$(build_version ./template/config/buildinfo)
  if [ -n "$from" ] && [ "$from" != "$to" ] && ! printf '%s\n' "$from to $to" >"$UPGRADE_PENDING"; then
    echo "Could not write $UPGRADE_PENDING, the upgrade is not started"
    return 1
  fi
  sh ./upgrade -n --force || return 1
  if [ "$from" = "$to" ] && [ -f "$UPGRADE_PENDING" ]; then
    echo "The upgrade of this volume from $(cat "$UPGRADE_PENDING") did not complete on an earlier start: its post-upgrade tasks failed or were cut off, which /opt/opendj/data/logs/upgrade.log tells. Rebuild the indexes it names with rebuild-index and remove $UPGRADE_PENDING, or restore the backup taken before the upgrade"
    return 1
  fi
  rm -f "$UPGRADE_PENDING"
}

# The background join (bootstrap/join.sh) serves OPENDJ_REPLICATION_TYPE=simple on every
# start; srs, sdsr and rg keep the one-shot replicate.sh of the first bootstrap
join_requested() {
  [ "$OPENDJ_REPLICATION_TYPE" = "simple" ] && [ -n "${REPLICATION_PEERS:-$MASTER_SERVER}" ]
}

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
# holds the private key, a .pin file its password. Succeeds when it copied a file.
copy_secrets() {
  local src dst tmp copied=1
  [ -d "$SECRET_VOLUME" ] || return 1
  for src in "$SECRET_VOLUME"/key* "$SECRET_VOLUME"/trust*; do
    [ -f "$src" ] || continue
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
  while copy_secrets && [ $((passes += 1)) -lt 5 ]; do :; done
}

# The server loads a keystore or truststore again, together with its .pin file, on the first
# handshake after either file has changed (#1095), so a store copied while the server runs is
# served without a restart, a new password included - a keystore as long as it keeps an alias of
# the one before it, else from the next start. A handshake that comes between the copy of a store
# and that of its .pin finds a pair it cannot open: the server keeps the store it loaded last,
# logs that it could not load the new one, and loads it once the second file is copied.
watch_secrets() {
  while sleep "$SECRET_VOLUME_REFRESH"; do
    sync_secrets
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
  if [ -f "$BOOTSTRAP_PENDING" ]; then
    # neither the marker nor the join: the join would enable a volume that lacks what the
    # bootstrap was to set up - a volume of the background join too, whose initialize would
    # bring the data of the topology but nothing else the bootstrap was to configure - and
    # the server is only there to be looked at. The upgrade still runs, whatever it says:
    # start-ds refuses an instance of another version, and the setup could then not be
    # finished by hand under this image
    upgrade_instance || echo "The upgrade did not complete"
    echo "The bootstrap of this volume did not complete, this container will not report itself healthy: remove the volume and start over, or finish the setup by hand and remove $BOOTSTRAP_PENDING"
  elif upgrade_instance; then
    # A server whose volume holds the data of the topology is ready as soon as it serves:
    # gating it on its peers would deadlock a whole-cluster restart under OrderedReady,
    # where -0 would wait for peers the StatefulSet starts only once -0 is ready - and so
    # would gating it on the join, which binds with the bootstrap's ROOT_PASSWORD and can
    # no longer do anything once that password was changed. A volume that never received
    # that data - its bootstrap failed or was killed, its join or its initialize never
    # completed - still carries the marker and waits for the join, so none of these turns
    # into a healthy server with bootstrap data only, the way they did when the marker
    # followed the upgrade alone. A seed that no peer joined yet holds the data without a
    # replication domain, which is why the domain is not what decides. Nor does a volume
    # whose replication the join took down turn healthy before the enable that follows
    # registered it again: the join could bind with ROOT_PASSWORD when it took it down, and
    # the peer it enables through answered then.
    if ! join_requested || { [ ! -f "$INITIALIZE_PENDING" ] && [ ! -f "$REJOIN_PENDING" ]; }; then
      # the hold on the writes is configuration, and only the join lets them in again
      if ! join_requested && [ -s "$REJOIN_PENDING" ]; then
        echo "The backend $(cat "$REJOIN_PENDING") may still refuse the writes of clients from a rejoin that did not complete, and no join runs to let them in again: once its replication is settled, set its writability-mode back to enabled with dsconfig set-backend-prop"
      fi
      touch "$BOOTSTRAP_COMPLETE"
    elif [ -f "$INITIALIZE_PENDING" ]; then
      echo "This instance never joined its replication topology or never received its data, the join decides whether it is healthy"
    else
      echo "This instance was taken out of its replication topology to join it anew, the join decides whether it is healthy"
    fi
    # the join also repairs membership that changed while no container ran, on every start
    if join_requested; then
      /opt/opendj/bootstrap/join.sh &
    fi
  else
    echo "The upgrade did not complete, this container will not report itself healthy"
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
# marked before the bootstrap, so that one that fails or is killed half-way never leaves a
# volume that counts as holding the data of the topology; a bootstrapped volume has entries
# whether or not it was asked for any, so it is initialized from the topology (see
# bootstrap/join.sh)
touch "$BOOTSTRAP_PENDING"
if join_requested; then
  touch "$INITIALIZE_PENDING"
fi
if ! sh "${BOOTSTRAP}"; then
  BOOTSTRAPPED=false
  echo "$BOOTSTRAP failed, this container will not report itself healthy"
fi

# Check if OPENDJ_REPLICATION_TYPE var is set. If it is - replicate to that server; the
# background join runs next to the server, below
if ! join_requested && [ -n "${MASTER_SERVER}" ] && [ -n "${OPENDJ_REPLICATION_TYPE}" ]; then
  if ! /opt/opendj/bootstrap/replicate.sh; then
    BOOTSTRAPPED=false
    echo "Replication setup failed, this container will not report itself healthy"
  fi
fi

# Setup has usually left the server running in the background. It is stopped here and
# started again below with exec, so that the server is PID 1 on the first start just as
# on a restart: a shell as PID 1 without a SIGTERM handler never receives the signal, and
# the container would then be killed at the end of the stop timeout instead of stopping
# the server. Left running, it would also keep the certificate it was set up with rather
# than the one on the secret volume, which start_server copies in before it starts the
# server. It is stopped before the marker below is written, so that the health check
# never reports the server of the bootstrap healthy just before it goes down. stop-ds
# exits 0 when the server is not running. When it fails the server may still be stopping,
# so the start below is tried anyway: it either runs the server or fails on the lock of
# the one still there.
echo "Stopping the server started by the bootstrap"
if ! ./bin/stop-ds; then
  echo "Could not stop the server started by the bootstrap, starting OpenDJ may fail"
fi

# Everything the instance was asked to be set up with - its backend, its base entry, its
# replication - is in place from here on, so the health check may start probing the server.
# With the background join, replication is the one thing still outstanding: the join writes
# the health marker once this server is a member of its topology, and not before.
if [ "$BOOTSTRAPPED" = true ] && ! rm -f "$BOOTSTRAP_PENDING"; then
  BOOTSTRAPPED=false
  echo "Could not remove $BOOTSTRAP_PENDING, this container will not report itself healthy"
fi
if [ "$BOOTSTRAPPED" = true ]; then
  if join_requested; then
    /opt/opendj/bootstrap/join.sh &
    echo "The instance is bootstrapped, joining the replication topology decides whether it is healthy"
  else
    touch "$BOOTSTRAP_COMPLETE"
    echo "The instance is bootstrapped, the health check may probe it"
  fi
fi

start_server
