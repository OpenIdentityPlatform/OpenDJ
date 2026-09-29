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
# Copyright 2026 3A Systems, LLC.

# Joins this server to the replication topology (#1086). run.sh starts it in the background
# next to the server on every start, not as a step of the first bootstrap: a join that could
# not complete is tried again on the next start, and membership that changed while no
# container ran is repaired. It covers OPENDJ_REPLICATION_TYPE=simple; the one-shot srs, sdsr
# and rg paths stay in replicate.sh.
#
# The contract with the operator: REPLICATION_PEERS lists every server of the topology, and
# all of them share BASE_DN, ROOT_USER_DN, ROOT_PASSWORD, ADMIN_PORT and REPLICATION_PORT. A
# StatefulSet chart derives the list from its ordinals (<sts>-0.<svc> ... <sts>-N-1.<svc>),
# plain docker run passes the container names. MASTER_SERVER keeps working as a one-element
# list. This server recognises itself in the list by comparing a peer with hostname -f, or
# the peer's first DNS label with its own: a StatefulSet pod is named <sts>-N but listed as
# <sts>-N.<headless service>. The anchored comparison is deliberate - the grep of /etc/hosts
# it replaces took opendj-1 for opendj-10, and a master that lost its volume for a replica
# of itself.
#
# Membership is decided from what is there, never from an exit code: exit 5 of dsreplication
# enable (REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN) means "no suffix was left to enable",
# which covers a base DN that is already replicated and a base DN that one of the two
# servers does not hold - the race with a peer whose bootstrap has not created the backend
# yet. The step is a member once the replication domain for BASE_DN exists in its cn=config
# and the server is registered in cn=admin data; anything else is tried again, whatever the
# exit code, because a repeated enable is safe exactly when it is decided this way.
#
# Initialization follows what the volume went through, not whether BASE_DN has entries:
# every fresh volume has entries (setup.sh imports the base entry or SAMPLE_DATA), and two
# freshly bootstrapped volumes even share a generation ID, so replication silently carries
# nothing that reached the seed by import-ldif. run.sh marks a volume it bootstrapped with
# $INITIALIZE_PENDING; the join runs dsreplication initialize from a peer while the marker
# is there and removes it once the data of the topology has arrived, so a marker that
# outlived a killed container still gets its initialize on the next start. The seed - only
# the first entry of REPLICATION_PEERS, and only once the retries are exhausted without
# finding a topology - removes the marker instead: its data is what the others initialize
# from. The residual risk is documented in the README: every server down at once and the
# first peer's volume lost means the first peer seeds empty.
#
# The health marker is written only once the join succeeded or the seed rule fired, so a
# container that never joined stays unhealthy - across restarts too, which the bootstrap-only
# replicate.sh could not say (a restart wrote the marker right after upgrade and a failed
# join turned into a healthy, unreplicated server).

cd /opt/opendj || exit 1
export PATH=/opt/opendj/bin:$PATH

BASE_DN=${BASE_DN:-"dc=example,dc=com"}
ROOT_USER_DN=${ROOT_USER_DN:-"cn=Directory Manager"}
ROOT_PASSWORD=${ROOT_PASSWORD:-password}
ADMIN_PORT=${ADMIN_PORT:-4444}
REPLICATION_PORT=${REPLICATION_PORT:-8989}
MYHOSTNAME=${MYHOSTNAME:-$(hostname -f)}
BOOTSTRAP_COMPLETE=${BOOTSTRAP_COMPLETE:-/opt/opendj/.bootstrap-complete}
INITIALIZE_PENDING=${INITIALIZE_PENDING:-/opt/opendj/data/.replication-initialize-pending}

REPLICATION_RETRY_COUNT=${REPLICATION_RETRY_COUNT:-30}
REPLICATION_RETRY_INTERVAL=${REPLICATION_RETRY_INTERVAL:-10}
# dsreplication can hang rather than fail (a peer that stops mid-operation), so no attempt
# runs without a bound
REPLICATION_ATTEMPT_TIMEOUT=${REPLICATION_ATTEMPT_TIMEOUT:-120}

# Removing servers that left the topology needs the operator's word on who belongs to it:
# with only MASTER_SERVER set, a third server joined by hand would be "not in the list" and
# thrown out. So the cleanup runs only when REPLICATION_PEERS itself is set.
PEERS_ARE_EXPLICIT=${REPLICATION_PEERS:+yes}
REPLICATION_PEERS=${REPLICATION_PEERS:-$MASTER_SERVER}

if [ -z "$REPLICATION_PEERS" ]; then
  echo "join: neither REPLICATION_PEERS nor MASTER_SERVER is set, nothing to join"
  exit 1
fi

IFS=',' read -r -a RAW_PEERS <<<"$REPLICATION_PEERS"
PEERS=()
for peer in "${RAW_PEERS[@]}"; do
  peer=$(echo "$peer" | tr -d '[:space:]')
  [ -n "$peer" ] && PEERS+=("$peer")
done
if [ "${#PEERS[@]}" -eq 0 ]; then
  echo "join: REPLICATION_PEERS holds no peer"
  exit 1
fi

# The tools read the root password from a file (#1084); the file lives on the tmpfs of
# /dev/shm where there is one, and run.sh removes what a killed join left behind, by the
# ADMIN_PORT in the name, on every start
PASSWORD_FILE=$(mktemp -p /dev/shm "opendj-join.$ADMIN_PORT.XXXXXX" 2>/dev/null || mktemp) || exit 1
trap 'rm -f "$PASSWORD_FILE"' EXIT
printf '%s\n' "$ROOT_PASSWORD" >"$PASSWORD_FILE" || exit 1

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

# DNS names compare case-insensitively, and a peer of a StatefulSet is the pod's hostname
# plus the headless service: self when the peer equals hostname -f, or its first label
# equals the first label of hostname -f
is_self() {
  local peer host
  peer=$(lower "$1")
  host=$(lower "$MYHOSTNAME")
  [ "$peer" = "$host" ] || [ "${peer%%.*}" = "${host%%.*}" ]
}

# every LDAP operation goes through the administration connector, which always serves TLS;
# the root user may change its password after the bootstrap, so unlike the health check
# these tools run only while the join still has the bootstrap's ROOT_PASSWORD to work with
search() {
  local host=$1
  shift
  ldapsearch --noPropertiesFile --hostname "$host" --port "$ADMIN_PORT" --useSsl --trustAll \
    --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" "$@" 2>/dev/null
}

server_up() {
  search localhost --baseDN "" --searchScope base "(objectClass=*)" 1.1 >/dev/null
}

# a member holds the replication domain for BASE_DN in its configuration and is registered
# in cn=admin data; both are made by one dsreplication enable, so requiring both keeps a
# half-done enable in the retry loop instead of declaring it a success
is_member() {
  search localhost --baseDN "cn=config" --searchScope sub \
    "(&(objectClass=ds-cfg-replication-domain)(ds-cfg-base-dn=$BASE_DN))" 1.1 | grep -q "^dn:" || return 1
  search localhost --baseDN "cn=Servers,cn=admin data" --searchScope one \
    "(hostname=$MYHOSTNAME)" 1.1 | grep -q "^dn:"
}

enable_through() {
  timeout "$REPLICATION_ATTEMPT_TIMEOUT" dsreplication enable \
    --host1 "$1" --port1 "$ADMIN_PORT" --bindDN1 "$ROOT_USER_DN" \
    --bindPasswordFile1 "$PASSWORD_FILE" --replicationPort1 "$REPLICATION_PORT" \
    --host2 "$MYHOSTNAME" --port2 "$ADMIN_PORT" --bindDN2 "$ROOT_USER_DN" \
    --bindPasswordFile2 "$PASSWORD_FILE" --replicationPort2 "$REPLICATION_PORT" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" -X -n
}

# the generation ID of the replication domain, from the domain's monitor entry (the
# connected-to attribute is what tells it from the entries of a replication server)
generation_id() {
  search "$1" --baseDN "cn=monitor" --searchScope sub \
    "(&(domain-name=$BASE_DN)(connected-to=*))" generation-id \
    | awk '/^generation-id: /{print $2; exit}'
}

initialize_from() {
  timeout "$REPLICATION_ATTEMPT_TIMEOUT" dsreplication initialize --baseDN "$BASE_DN" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --hostSource "$1" --portSource "$ADMIN_PORT" \
    --hostDestination "$MYHOSTNAME" --portDestination "$ADMIN_PORT" -X -n
}

# initialize while the marker of a bootstrapped, never initialized volume is there; without
# it only cross-check the generation IDs, so a volume that already carried data is never
# overwritten by a peer
ensure_initialized() {
  local source=$1 peer local_id peer_id rc i
  if [ ! -f "$INITIALIZE_PENDING" ]; then
    if [ -n "$source" ]; then
      local_id=$(generation_id localhost)
      peer_id=$(generation_id "$source")
      if [ -n "$local_id" ] && [ -n "$peer_id" ] && [ "$local_id" != "$peer_id" ]; then
        echo "join: generation ID $local_id does not match $peer_id of $source; replication will not flow until this server or that one is initialized by hand"
      fi
    fi
    return 0
  fi
  if [ -z "$source" ]; then
    for peer in "${PEERS[@]}"; do
      is_self "$peer" && continue
      if [ -n "$(generation_id "$peer")" ]; then
        source=$peer
        break
      fi
    done
    source=${source:-}
  fi
  if [ -z "$source" ]; then
    echo "join: no peer to initialize from"
    return 1
  fi
  for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
    echo "join: initializing from $source"
    if initialize_from "$source"; then
      rm -f "$INITIALIZE_PENDING"
      local_id=$(generation_id localhost)
      peer_id=$(generation_id "$source")
      if [ -n "$local_id" ] && [ -n "$peer_id" ] && [ "$local_id" != "$peer_id" ]; then
        echo "join: generation ID $local_id does not match $peer_id of $source after initialize"
      fi
      return 0
    fi
    rc=$?
    [ "$i" -eq "$REPLICATION_RETRY_COUNT" ] && return $rc
    echo "join: initialize from $source exited with $rc, trying again in $REPLICATION_RETRY_INTERVAL s"
    sleep "$REPLICATION_RETRY_INTERVAL"
  done
}

ldapmodify_local() {
  ldapmodify --noPropertiesFile --hostname localhost --port "$ADMIN_PORT" --useSsl --trustAll \
    --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" 2>/dev/null
}

in_peers() {
  local host peer
  host=$(lower "${1%%.*}")
  for peer in "${PEERS[@]}"; do
    [ "$(lower "${peer%%.*}")" = "$host" ] && return 0
  done
  return 1
}

# Removes every server that is registered in the topology but no longer listed in
# REPLICATION_PEERS: its entry and group membership in cn=admin data (which is replicated,
# so one survivor's delete reaches the others) and its values in this server's
# replication-server lists (which are configuration of this server alone, so every survivor
# prunes its own on its next start). dsreplication disable cannot do this for a dead server:
# it changes only the servers it can reach, and OpenDJ 4 has no cleanup subcommand. Every
# step tolerates losing the race to another survivor.
cleanup_departed() {
  local dn host value domain
  [ "$PEERS_ARE_EXPLICIT" = yes ] || return 0
  search localhost --baseDN "cn=Servers,cn=admin data" --searchScope one "(objectClass=*)" hostname \
    | awk '/^dn: /{dn=substr($0,5)} /^hostname: /{print dn "\t" $2}' \
    | while IFS=$'\t' read -r dn host; do
      [ -z "$host" ] && continue
      if ! in_peers "$host" && ! is_self "$host"; then
        echo "join: removing departed server $host from cn=admin data"
        printf 'dn: %s\nchangetype: delete\n' "$dn" | ldapmodify_local \
          || echo "join: could not remove $dn (already removed by another survivor?)"
        printf 'dn: cn=all-servers,cn=Server Groups,cn=admin data\nchangetype: modify\ndelete: uniqueMember\nuniqueMember: %s\n' "${dn%%,cn=Servers,cn=admin data}" \
          | ldapmodify_local || true
      fi
    done
  search localhost --baseDN "cn=config" --searchScope sub \
    "(objectClass=ds-cfg-replication-server)" ds-cfg-replication-server \
    | awk '/^ds-cfg-replication-server: /{print $2}' \
    | while read -r value; do
      host=${value%:*}
      if ! in_peers "$host" && ! is_self "$host"; then
        echo "join: removing departed replication server $value from the replication server configuration"
        dsconfig set-replication-server-prop --provider-name "Multimaster Synchronization" \
          --remove "replication-server:$value" \
          --hostname localhost --port "$ADMIN_PORT" --bindDN "$ROOT_USER_DN" \
          --bindPasswordFile "$PASSWORD_FILE" --trustAll --no-prompt || true
      fi
    done
  domain=$(search localhost --baseDN "cn=config" --searchScope sub \
    "(&(objectClass=ds-cfg-replication-domain)(ds-cfg-base-dn=$BASE_DN))" cn \
    | awk '/^cn: /{print substr($0,5); exit}')
  [ -z "$domain" ] && return 0
  search localhost --baseDN "cn=config" --searchScope sub \
    "(&(objectClass=ds-cfg-replication-domain)(ds-cfg-base-dn=$BASE_DN))" ds-cfg-replication-server \
    | awk '/^ds-cfg-replication-server: /{print $2}' \
    | while read -r value; do
      host=${value%:*}
      if ! in_peers "$host" && ! is_self "$host"; then
        echo "join: removing departed replication server $value from the replication domain configuration"
        dsconfig set-replication-domain-prop --provider-name "Multimaster Synchronization" \
          --domain-name "$domain" --remove "replication-server:$value" \
          --hostname localhost --port "$ADMIN_PORT" --bindDN "$ROOT_USER_DN" \
          --bindPasswordFile "$PASSWORD_FILE" --trustAll --no-prompt || true
      fi
    done
}

joined() {
  ensure_initialized "$1" || return 1
  cleanup_departed
  touch "$BOOTSTRAP_COMPLETE"
  echo "join: this server is a member of the replication topology, the health check may probe it"
}

echo "join: waiting for the server on the administration connector"
for i in $(seq 1 150); do
  server_up && break
  if [ "$i" -eq 150 ]; then
    echo "join: the server did not come up, giving up"
    exit 1
  fi
  sleep 2
done

if is_member; then
  echo "join: already a member of the replication topology"
  joined ""
  exit
fi

# a list without a single other server - MASTER_SERVER pointing at this server, the way the
# old replicate.sh master recognised itself - leaves nothing to retry against, so the seed
# rule below decides at once instead of sitting out the retries
OTHERS=no
for peer in "${PEERS[@]}"; do
  is_self "$peer" || OTHERS=yes
done

[ "$OTHERS" = yes ] && for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
  for peer in "${PEERS[@]}"; do
    is_self "$peer" && continue
    echo "join: enabling replication with $peer"
    enable_through "$peer"
    rc=$?
    if is_member; then
      echo "join: joined the replication topology through $peer"
      joined "$peer"
      exit
    fi
    echo "join: dsreplication enable with $peer exited with $rc and membership is not there"
  done
  if [ "$i" -lt "$REPLICATION_RETRY_COUNT" ]; then
    echo "join: no peer joined this server yet, trying again in $REPLICATION_RETRY_INTERVAL s ($i of $REPLICATION_RETRY_COUNT)"
    sleep "$REPLICATION_RETRY_INTERVAL"
  fi
done

# Only the first peer of the list may decide that there is no topology to join and that its
# own data seeds one; anyone else staying unhealthy is what surfaces a lost topology instead
# of forking it. The seed keeps its bootstrap data, so the initialize marker goes away.
if is_self "${PEERS[0]}"; then
  echo "join: no topology found after $REPLICATION_RETRY_COUNT attempts, seeding it with this server's data"
  rm -f "$INITIALIZE_PENDING"
  touch "$BOOTSTRAP_COMPLETE"
  exit 0
fi

echo "join: could not join the replication topology after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy"
exit 1
