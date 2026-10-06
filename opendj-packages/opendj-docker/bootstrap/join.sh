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
# The contract with the operator: REPLICATION_PEERS lists every server of the topology by DNS
# name, and all of them share BASE_DN, ROOT_USER_DN, ROOT_PASSWORD, ADMIN_PORT and
# REPLICATION_PORT. A StatefulSet chart derives the list from its ordinals
# (<sts>-0.<svc> ... <sts>-N-1.<svc>), plain docker run passes the container names of
# containers whose --hostname is their --name. MASTER_SERVER keeps working as a one-element
# list. This server recognises itself in the list by a name that equals hostname -f, or is
# hostname -f cut at a dot (a StatefulSet pod whose FQDN is <sts>-N.<svc>.<ns>.svc.<domain>
# is listed as <sts>-N.<svc>), or the other way round; and, as the grep of /etc/hosts it
# replaces did, by one of its own addresses or a name /etc/hosts gives one of them. Names
# are compared whole - that grep took opendj-1 for opendj-10, a replica for its master when
# an --add-host named the master, and a master that lost its volume for a replica of itself -
# and cut only at a dot, so opendj-0.opendj.east is not opendj-0.opendj.west.
#
# Membership is decided from what is there, never from an exit code: exit 5 of dsreplication
# enable (REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN) means "no suffix was left to enable",
# which covers a base DN that is already replicated and a base DN that one of the two
# servers does not hold - the race with a peer whose bootstrap has not created the backend
# yet. The step is a member once the replication domain for BASE_DN exists in its cn=config
# and the server is registered in cn=admin data; anything else is tried again, whatever the
# exit code, because a repeated enable is safe exactly when it is decided this way.
#
# Whose data the topology carries follows what each volume went through, not whether BASE_DN
# has entries: every fresh volume has entries (setup.sh imports the base entry or
# SAMPLE_DATA), and two freshly bootstrapped volumes even share a generation ID, so
# replication silently carries nothing that reached one of them by import-ldif. run.sh marks
# a volume it bootstrapped with $INITIALIZE_PENDING, and the join publishes that to its peers
# in $STATE_DN, a local entry of cn=config: "pending" while the marker is there, "ready" once
# the volume holds the data of the topology - it was never bootstrapped by run.sh, it was
# initialized from a ready peer, or it seeded the topology - and "rejoining" while a volume
# that holds it has its replication taken down to enable it anew. A server joins only through
# a ready peer, and a pending one initializes only from one, so two fresh servers that start
# together never take each other's bootstrap data for the topology's, and two servers whose
# replication is down never form a topology of their own; where the list is only
# MASTER_SERVER, a peer that publishes no state (an image before this one) counts as ready, as
# the old replicate.sh trusted its master. The seed is only the first entry of
# REPLICATION_PEERS: at once when every other peer answers and is pending (all of them start
# from scratch, together or not), or once the retries are exhausted while no other peer
# answers that may hold the data - a ready one, one of an image before this one that
# publishes no state but replicates BASE_DN, or one that refuses the bind with ROOT_PASSWORD
# (only a server past its bootstrap can have another root password). The residual risk is
# documented in the README: every other server down *and* the first peer's volume lost means
# the first peer seeds empty.
#
# On a volume that waits for the data of the topology, the health marker is written only
# once the join succeeded or the seed rule fired, so a container that never joined stays
# unhealthy - across restarts too, which the bootstrap-only replicate.sh could not say (a
# restart wrote the marker right after upgrade and a failed join turned into a healthy,
# unreplicated server). A volume that holds the data is healthy as soon as it serves (see
# run.sh) - unless the join takes its replication down to enable it anew: from then until the
# enable registered it again it is not, across restarts too ($REJOIN_PENDING), and the
# backend of BASE_DN refuses the writes of clients, which no longer replicate meanwhile.

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
REJOIN_PENDING=${REJOIN_PENDING:-/opt/opendj/data/.replication-rejoin-pending}
# not replicated: cn=config is this server's alone, and it lives on the volume next to the
# marker, so the two cannot tell different stories
STATE_DN="cn=Docker Join,cn=config"

# Sets the variable to its value read as a decimal whole number, or to the default when it
# is not one or is below the least value. $(( )) reads a leading zero as octal and fails on
# 08 - abandoning the whole command it runs in, however far up that is - while test reads 08
# as 8, so a check by test alone lets the value through to the arithmetic.
whole_number() { # <variable> <default> <least>
  local value=${!1}
  case $value in
    '' | *[!0-9]*) value= ;;
    *) value=$((10#$value)) ;;
  esac
  if [ -z "$value" ] || [ "$value" -lt "$3" ]; then
    echo "join: $1 is not a whole number of at least $3, using $2"
    value=$2
  fi
  printf -v "$1" '%s' "$value"
}

REPLICATION_RETRY_COUNT=${REPLICATION_RETRY_COUNT:-30}
whole_number REPLICATION_RETRY_COUNT 30 1
REPLICATION_RETRY_INTERVAL=${REPLICATION_RETRY_INTERVAL:-10}
whole_number REPLICATION_RETRY_INTERVAL 10 0
# dsreplication enable can hang rather than fail (a peer that stops mid-operation), so no
# enable runs without a bound
REPLICATION_ATTEMPT_TIMEOUT=${REPLICATION_ATTEMPT_TIMEOUT:-120}
whole_number REPLICATION_ATTEMPT_TIMEOUT 120 1
# a total update is a full import of BASE_DN, which takes as long as the data needs; a killed
# dsreplication initialize leaves its task running on the server, and the next attempt asks
# for another full import, so by default it runs without a bound
REPLICATION_INITIALIZE_TIMEOUT=${REPLICATION_INITIALIZE_TIMEOUT:-0}
whole_number REPLICATION_INITIALIZE_TIMEOUT 0 0

# Removing servers that left the topology needs the operator's word on who belongs to it:
# with only MASTER_SERVER set, a third server joined by hand would be "not in the list" and
# thrown out. So the cleanup, and the repair of the replication server lists, run only when
# REPLICATION_PEERS itself is set.
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
# name, on every start - in /dev/shm those of its ADMIN_PORT, in /tmp all of them
PASSWORD_FILE=$(mktemp -p /dev/shm "opendj-join.$ADMIN_PORT.XXXXXX" 2>/dev/null \
  || mktemp "/tmp/opendj-join.$ADMIN_PORT.XXXXXX") || exit 1
trap 'rm -f "$PASSWORD_FILE"' EXIT
printf '%s\n' "$ROOT_PASSWORD" >"$PASSWORD_FILE" || exit 1

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

# an IPv4 address is digits and dots, an IPv6 one has a colon
is_address() {
  case $1 in
    *:*) return 0 ;;
    *[!0-9.]* | '') return 1 ;;
    *) return 0 ;;
  esac
}

# Two names (lower case) are one server when they are equal, or when one of them is the
# other cut at a dot; addresses only when they are equal
names_match() {
  [ "$1" = "$2" ] && return 0
  if is_address "$1" || is_address "$2"; then
    return 1
  fi
  case $1 in "$2".*) return 0 ;; esac
  case $2 in "$1".*) return 0 ;; esac
  return 1
}

SELF_NAME=$(lower "$MYHOSTNAME")
# the addresses of this container (loopback aside) and the names /etc/hosts gives them - an
# --add-host or a hostAliases entry for itself - as the base replicate.sh recognised them
SELF_ADDRESSES=()
SELF_ALIASES=()
own_names=" $(lower "$(hostname)") $(lower "$(hostname -f 2>/dev/null)") $SELF_NAME "
for address in $(hostname -i 2>/dev/null) \
    $(awk -v names="$own_names" '!/^[[:space:]]*#/ { for (i = 2; i <= NF; i++) if (index(names, " " tolower($i) " ")) { print $1; break } }' /etc/hosts 2>/dev/null); do
  case $address in 127.* | ::1 | 0.0.0.0) continue ;; esac
  SELF_ADDRESSES+=("$(lower "$address")")
done
if [ "${#SELF_ADDRESSES[@]}" -gt 0 ]; then
  for name in $(awk -v addresses=" ${SELF_ADDRESSES[*]} " '!/^[[:space:]]*#/ && index(addresses, " " tolower($1) " ") { for (i = 2; i <= NF; i++) print tolower($i) }' /etc/hosts 2>/dev/null); do
    SELF_ALIASES+=("$name")
  done
fi

is_self() {
  local peer candidate
  peer=$(lower "$1")
  names_match "$peer" "$SELF_NAME" && return 0
  for candidate in "${SELF_ADDRESSES[@]}" "${SELF_ALIASES[@]}"; do
    [ "$peer" = "$candidate" ] && return 0
  done
  return 1
}

in_peers() {
  local host peer
  host=$(lower "$1")
  for peer in "${PEERS[@]}"; do
    names_match "$(lower "$peer")" "$host" && return 0
  done
  return 1
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

ldapmodify_on() { # <host> [<option>...]
  local host=$1
  shift
  ldapmodify --noPropertiesFile --hostname "$host" --port "$ADMIN_PORT" --useSsl --trustAll \
    --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" "$@" 2>/dev/null
}

ldapmodify_local() {
  ldapmodify_on localhost "$@"
}

dsconfig_local() {
  dsconfig "$@" --hostname localhost --port "$ADMIN_PORT" --bindDN "$ROOT_USER_DN" \
    --bindPasswordFile "$PASSWORD_FILE" --trustAll --no-prompt
}

# runs a command within that many seconds, or without a bound for 0; timeout executes the
# command, so it has to be a program, not a function of this script
bounded() {
  local limit=$1
  shift
  if [ "$limit" -gt 0 ] 2>/dev/null; then
    timeout "$limit" "$@"
  else
    "$@"
  fi
}

server_up() {
  search localhost --baseDN "" --searchScope base "(objectClass=*)" 1.1 >/dev/null
}

publish_state() {
  printf 'dn: %s\nchangetype: modify\nreplace: description\ndescription: %s\n' "$STATE_DN" "$1" \
    | ldapmodify_local >/dev/null && return 0
  printf 'dn: %s\nobjectClass: top\nobjectClass: ds-cfg-branch\nobjectClass: extensibleObject\ncn: Docker Join\ndescription: %s\n' "$STATE_DN" "$1" \
    | ldapmodify_local --defaultAdd >/dev/null && return 0
  echo "join: could not publish the state $1 of this server to its peers"
  return 1
}

# ready, pending or rejoining as the peer publishes it, absent when it answers without
# publishing any, refused when it answers but no longer takes ROOT_PASSWORD, down when it does
# not answer
peer_state() {
  local out state
  out=$(search "$1" --baseDN "$STATE_DN" --searchScope base "(objectClass=*)" description)
  case $? in
    0)
      state=$(printf '%s\n' "$out" | awk 'tolower($1) == "description:" { print $2; exit }')
      case $state in
        ready | pending | rejoining) echo "$state" ;;
        *) echo absent ;;
      esac
      ;;
    32) echo absent ;;
    # 49: invalidCredentials - the root password of a server is changed in its cn=config,
    # which is not replicated, and only a server past its bootstrap has another one
    49) echo refused ;;
    *) echo down ;;
  esac
}

trusted() {
  case $1 in
    ready) return 0 ;;
    absent) [ "$PEERS_ARE_EXPLICIT" != yes ] ;;
    *) return 1 ;;
  esac
}

# fails when the search does, rather than answering with an empty list
registered_hosts() { # [<host>, localhost by default]
  local out
  out=$(search "${1:-localhost}" --baseDN "cn=Servers,cn=admin data" --searchScope one "(objectClass=*)" hostname) \
    || return 1
  printf '%s\n' "$out" | awk 'tolower($1) == "hostname:" { print $2 }'
}

# a member holds the replication domain for BASE_DN in its configuration and is registered
# in cn=admin data - under the name the enable that registered it connected with, which for
# a server that never ran an enable of its own is the name a peer listed it by; both are made
# by one dsreplication enable, so requiring both keeps a half-done enable from being declared
# a success, and the next round takes it down (see reset_if_unregistered)
replicates_base_dn() { # <host>
  search "$1" --baseDN "cn=config" --searchScope sub \
    "(&(objectClass=ds-cfg-replication-domain)(ds-cfg-base-dn=$BASE_DN))" 1.1 | grep -q "^dn:"
}

is_member() {
  local host
  replicates_base_dn localhost || return 1
  for host in $(registered_hosts); do
    is_self "$host" && return 0
  done
  return 1
}

# cn=admin data is replicated, but a server that was away while the survivors removed it
# from the topology still holds its own entry until that delete reaches it, and it may not
# have yet when the join looks: the other peers that answer and replicate BASE_DN have the
# say. Being unregistered takes this server's replication down (reset_replication), so it is
# concluded only when at least one of them listed its registrations and none of them lists
# this server: a search that failed, or a peer whose copy of cn=admin data still lags behind
# the others, decides nothing while another peer lists it. With no such peer the local view
# stands - nothing else can be asked.
registered_with_peers() {
  local peer host hosts asked=no
  for peer in "${PEERS[@]}"; do
    is_self "$peer" && continue
    replicates_base_dn "$peer" || continue
    hosts=$(registered_hosts "$peer") || continue
    asked=yes
    for host in $hosts; do
      is_self "$host" && return 0
    done
  done
  [ "$asked" = no ]
}

member_of_topology() {
  is_member && registered_with_peers
}

# whether the cn=admin data of this server registers it: 0 when it does, 1 when a search
# answers that it does not - cn=Servers missing altogether included, which an initialize of
# cn=admin data that failed half-way leaves - and 2 when the search failed otherwise, which
# decides nothing
registered_here() {
  local out host
  out=$(search localhost --baseDN "cn=Servers,cn=admin data" --searchScope one "(objectClass=*)" hostname)
  case $? in
    0) ;;
    32) return 1 ;;
    *) return 2 ;;
  esac
  for host in $(printf '%s\n' "$out" | awk 'tolower($1) == "hostname:" { print $2 }'); do
    is_self "$host" && return 0
  done
  return 1
}

# Two dsreplication enable runs through one peer at the same time break each other: both
# create the replication server that a seed does not have yet, the loser fails half-way
# (ManagedObjectAlreadyExistsException, exit 17), and every enable after that exits 5,
# "already replicated", without ever completing its membership. So an enable holds a lock on
# the peer it runs through - an entry of the peer's cn=config, which only one add creates -
# and one on this server: an enable configures both of its servers, and a server that enables
# through a peer may at the same time be the peer another server enables through. A server
# that takes its own replication down holds its own lock for as long.
# A lock whose holder was killed before it removed it is broken once it is older than an
# enable may take, by a delete that asserts the value it read: two joins that break it at
# once cannot remove the lock one of them took right after. One that holds this server's own
# name is broken at once: it was left by a join of an earlier start, and a start runs one.
JOIN_LOCK_DN="cn=Docker Join Lock,cn=config"
JOIN_LOCK_TTL=$((REPLICATION_ATTEMPT_TIMEOUT + 60))
JOIN_LOCK_VALUE=

add_join_lock() { # <host> <value>
  printf 'dn: %s\nobjectClass: top\nobjectClass: ds-cfg-branch\nobjectClass: extensibleObject\ncn: Docker Join Lock\ndescription: %s\n' "$JOIN_LOCK_DN" "$2" \
    | ldapmodify_on "$1" --defaultAdd >/dev/null
}

# Succeeds when this server may go on: it took the lock and left its value in
# JOIN_LOCK_VALUE, or the server could not be asked for one - a peer that does not answer
# fails the enable anyway, and one that refuses the entry for another reason than holding it
# must not keep the join out for good. A lock taken this way is released with
# release_join_lock <host> "$JOIN_LOCK_VALUE".
take_join_lock() { # <host>
  local value rc held holder since age where
  JOIN_LOCK_VALUE=
  value="$SELF_NAME $(date +%s)"
  add_join_lock "$1" "$value"
  rc=$?
  if [ "$rc" -eq 0 ]; then
    JOIN_LOCK_VALUE=$value
    return 0
  fi
  # 68: entryAlreadyExists
  [ "$rc" -eq 68 ] || return 0
  held=$(search "$1" --baseDN "$JOIN_LOCK_DN" --searchScope base "(objectClass=*)" description \
    | awk 'tolower($1) == "description:" { print $2, $3; exit }')
  holder=${held% *}
  since=${held##* }
  age=$(($(date +%s) - ${since:-0}))
  if [ -n "$held" ] && { [ "$holder" = "$SELF_NAME" ] || [ "$age" -gt "$JOIN_LOCK_TTL" ]; }; then
    echo "join: breaking the lock $holder took on $1 $age s ago"
    printf 'dn: %s\nchangetype: delete\n' "$JOIN_LOCK_DN" \
      | ldapmodify_on "$1" --assertionFilter "(description=$held)" >/dev/null
    if add_join_lock "$1" "$value"; then
      JOIN_LOCK_VALUE=$value
      return 0
    fi
  fi
  if [ "$1" = localhost ]; then where="this server"; else where=$1; fi
  # a lock released between the add and the search has no holder left to name
  echo "join: ${holder:-another server} is enabling replication through $where, waiting for it"
  return 1
}

release_join_lock() { # <host> <value>
  [ -n "$2" ] || return 0
  printf 'dn: %s\nchangetype: delete\n' "$JOIN_LOCK_DN" \
    | ldapmodify_on "$1" --assertionFilter "(description=$2)" >/dev/null \
    || echo "join: could not remove the lock on $1, it expires in $JOIN_LOCK_TTL s"
}

# exits 75 without running the enable while another server enables through the peer or
# through this one. Neither lock is waited for, so two servers that each hold their own and
# ask for the other's cannot deadlock; they both give up and try again after pause().
enable_through() {
  local rc own peer_lock
  take_join_lock localhost || return 75
  own=$JOIN_LOCK_VALUE
  if ! take_join_lock "$1"; then
    release_join_lock localhost "$own"
    return 75
  fi
  peer_lock=$JOIN_LOCK_VALUE
  bounded "$REPLICATION_ATTEMPT_TIMEOUT" dsreplication enable \
    --host1 "$1" --port1 "$ADMIN_PORT" --bindDN1 "$ROOT_USER_DN" \
    --bindPasswordFile1 "$PASSWORD_FILE" --replicationPort1 "$REPLICATION_PORT" \
    --host2 "$MYHOSTNAME" --port2 "$ADMIN_PORT" --bindDN2 "$ROOT_USER_DN" \
    --bindPasswordFile2 "$PASSWORD_FILE" --replicationPort2 "$REPLICATION_PORT" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" -X -n
  rc=$?
  release_join_lock "$1" "$peer_lock"
  release_join_lock localhost "$own"
  return $rc
}

# the retry interval and up to as much again, at random: two joins that keep missing each
# other's lock would otherwise try again in step, round after round
pause() {
  local delay=$((REPLICATION_RETRY_INTERVAL + RANDOM % (REPLICATION_RETRY_INTERVAL + 1)))
  echo "$1, trying again in $delay s"
  sleep "$delay"
}

initialize_from() {
  bounded "$REPLICATION_INITIALIZE_TIMEOUT" dsreplication initialize --baseDN "$BASE_DN" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --hostSource "$1" --portSource "$ADMIN_PORT" \
    --hostDestination "$MYHOSTNAME" --portDestination "$ADMIN_PORT" -X -n
}

# the generation ID of the replication domain, from the domain's own monitor entry: a
# replication server also puts domain-name, connected-to and generation-id on the entry of
# every directory server connected to it, but only the domain publishes replayed-updates
generation_id() {
  search "$1" --baseDN "cn=monitor" --searchScope sub \
    "(&(domain-name=$BASE_DN)(replayed-updates=*))" generation-id \
    | awk 'tolower($1) == "generation-id:" { print $2; exit }'
}

cross_check() { # <peer> <what the mismatch means>
  local local_id peer_id
  local_id=$(generation_id localhost)
  peer_id=$(generation_id "$1")
  if [ -n "$local_id" ] && [ -n "$peer_id" ] && [ "$local_id" != "$peer_id" ]; then
    echo "join: generation ID $local_id does not match $peer_id of $1$2"
  fi
}

# the first other peer, in the order of the list, that holds the data of the topology and
# replicates BASE_DN - asked of the peer itself, as cn=admin data registers a server under
# whatever name the enable that registered it used, which need not be the listed one
trusted_source() {
  local peer
  for peer in "${PEERS[@]}"; do
    is_self "$peer" && continue
    trusted "$(peer_state "$peer")" && replicates_base_dn "$peer" && { echo "$peer"; return 0; }
  done
  return 1
}

# every other peer answers and waits for the data of the topology itself; true as well when
# the list names no other server
all_others_pending() {
  local peer
  for peer in "${PEERS[@]}"; do
    is_self "$peer" && continue
    [ "$(peer_state "$peer")" = pending ] || return 1
  done
  return 0
}

# some other peer answers and may hold the data of the topology: it is ready, it is rejoining
# (it holds the data while its replication is down), it refuses ROOT_PASSWORD, or it publishes
# no state and replicates BASE_DN. Unlike an initialize source,
# the last counts here even where the peers are explicit - a server of an image before this
# one, still serving the topology, and seeding next to it would fork it. A server of this
# image publishes no state only while it bootstraps, and it replicates nothing then, so it
# does not hold the seed off. A refusing peer cannot be asked what it replicates, but only a
# server past its bootstrap has a root password other than ROOT_PASSWORD.
any_other_may_hold_data() {
  local peer
  for peer in "${PEERS[@]}"; do
    is_self "$peer" && continue
    case $(peer_state "$peer") in
      ready | rejoining | refused) return 0 ;;
      absent) replicates_base_dn "$peer" && return 0 ;;
    esac
  done
  return 1
}

# The values of the replication server lists this server holds: one "<kind>\t<cn>" line for
# every list, its replication server entry (kind server) and each of its replication domains
# (kind domain) - BASE_DN, and cn=schema and cn=admin data, which dsreplication enable
# configures next to it - followed by a "<kind>\t<cn>\t<host:port>" line for every value
replication_server_lists() {
  search localhost --baseDN "cn=config" --searchScope sub \
    "(|(objectClass=ds-cfg-replication-server)(objectClass=ds-cfg-replication-domain))" \
    objectClass cn ds-cfg-replication-server \
    | awk -v OFS='\t' '
        function flush(   i) {
          if (kind != "") { print kind, cn; for (i = 1; i <= n; i++) print kind, cn, values[i] }
          kind = ""; cn = ""; n = 0
        }
        /^dn: / { flush() }
        tolower($0) == "objectclass: ds-cfg-replication-domain" { kind = "domain" }
        tolower($0) == "objectclass: ds-cfg-replication-server" { kind = "server" }
        tolower($1) == "cn:" { cn = substr($0, 5) }
        tolower($1) == "ds-cfg-replication-server:" { values[++n] = $2 }
        END { flush() }'
}

# it runs inside loops that read their lines from stdin, which dsconfig is kept away from
change_replication_servers() { # <kind> <cn> --add|--remove <host:port>
  if [ "$1" = server ]; then
    dsconfig_local set-replication-server-prop --provider-name "Multimaster Synchronization" \
      "$3" "replication-server:$4" </dev/null
  else
    dsconfig_local set-replication-domain-prop --provider-name "Multimaster Synchronization" \
      --domain-name "$2" "$3" "replication-server:$4" </dev/null
  fi
}

# "<dn>\t<hostname>" for every server this server finds in its cn=admin data
registrations() {
  search localhost --baseDN "cn=Servers,cn=admin data" --searchScope one "(objectClass=*)" hostname \
    | awk '/^dn: /{dn=substr($0,5)} tolower($1) == "hostname:" {print dn "\t" $2}'
}

# removes a server's entry and its group membership from cn=admin data
unregister() { # <dn>
  printf 'dn: %s\nchangetype: delete\n' "$1" | ldapmodify_local >/dev/null || return 1
  printf 'dn: cn=all-servers,cn=Server Groups,cn=admin data\nchangetype: modify\ndelete: uniqueMember\nuniqueMember: %s\n' "${1%%,cn=Servers,cn=admin data}" \
    | ldapmodify_local >/dev/null || true
}

# A server that the survivors removed from the topology while it was away, and that comes
# back on the volume it kept, still replicates BASE_DN and cn=admin data. dsreplication enable
# registers a server only where the administration data of one of the two is not replicated
# yet: with both replicated and their registries alike - this server's copy lacks it once the
# delete of the survivors reached it - the enable updates the replication server lists and
# leaves cn=admin data as it is, and this server stays unregistered for good. So its own
# replication configuration is taken down first, and the enable after that registers it
# anew. A copy of its entry that the delete has not reached yet is dropped as well: the
# registries would differ, the enable would merge them, and the delete arriving later would
# take this server out again.
# From the first change on, writes this server takes get no change number and reach no other
# server. Removing the health marker is not enough to stop them: new probes fail, but a
# container turns unhealthy only after its probe's retries (a readiness probe after its
# failure threshold), by when the enable has usually run, and connections that are open stay
# open. So before anything changes the backend of BASE_DN refuses the writes of clients (see
# hold_writes), and joined() or seed() lets them in again. $REJOIN_PENDING keeps run.sh from
# reporting a restart healthy until then. A volume that holds the data of the topology
# publishes the state rejoining meanwhile, which keeps the peers from enabling or
# initializing through it while it has no replication of its own, and keeps a first peer
# from seeding next to it; one that waits for that data stays pending. It holds its own join
# lock while its configuration goes, so that no server enables through it then. One attempt:
# a lock another join holds leaves everything as it was, and the next round tries again.
reset_replication() { # <rejoining|pending> <why>
  local dn host own
  echo "join: $2, taking its replication configuration down to enable it anew"
  publish_state "$1" || return 1
  touch "$REJOIN_PENDING"
  rm -f "$BOOTSTRAP_COMPLETE"
  if ! take_join_lock localhost; then
    echo "join: its replication configuration stays as it is until the next round"
    return 1
  fi
  own=$JOIN_LOCK_VALUE
  if ! hold_writes; then
    echo "join: could not stop the writes of clients, its replication configuration stays as it is"
    release_join_lock localhost "$own"
    return 1
  fi
  registrations | while IFS=$'\t' read -r dn host; do
    [ -n "$host" ] && is_self "$host" || continue
    echo "join: dropping the stale entry $dn"
    unregister "$dn" || echo "join: could not remove $dn"
  done
  bounded "$REPLICATION_ATTEMPT_TIMEOUT" dsreplication disable --hostname "$MYHOSTNAME" --port "$ADMIN_PORT" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" --disableAll -X -n \
    || echo "join: dsreplication disable exited with $?"
  release_join_lock localhost "$own"
}

# "<backend-id> <writability-mode>" of the backend that holds BASE_DN; the mode is left out
# where the entry does not set one, which means enabled
data_backend() {
  search localhost --baseDN "cn=Backends,cn=config" --searchScope one \
    "(&(objectClass=ds-cfg-backend)(ds-cfg-base-dn=$BASE_DN))" ds-cfg-backend-id ds-cfg-writability-mode \
    | awk 'tolower($1) == "ds-cfg-backend-id:" { id = $2 } tolower($1) == "ds-cfg-writability-mode:" { mode = $2 }
           END { if (id != "") print id, mode }'
}

# The backend of BASE_DN takes internal and replication writes only - dsreplication enable
# and initialize go through those, a client's write is refused (Unwilling to Perform). Only
# that backend: internal-only on the whole server would refuse the writes dsreplication
# enable makes to cn=config and cn=admin data. The mode is configuration, so it outlasts a
# restart; $REJOIN_PENDING names the backend, so that release_writes lets the writes in
# again on the start that rejoins, and only where this join stopped them: a backend that an
# operator made read-only already stays as it is.
hold_writes() {
  local backend mode
  read -r backend mode <<<"$(data_backend)"
  if [ -z "$backend" ]; then
    echo "join: found no backend that holds $BASE_DN"
    return 1
  fi
  [ "${mode:-enabled}" = enabled ] || return 0
  echo "join: backend $backend refuses the writes of clients until this server rejoins"
  # named before the mode changes: a container killed in between lets the writes in again
  # once it rejoined, rather than leaving them refused for good
  printf '%s\n' "$backend" >"$REJOIN_PENDING" || return 1
  dsconfig_local set-backend-prop --backend-name "$backend" --set writability-mode:internal-only </dev/null >/dev/null
}

release_writes() {
  local backend
  backend=$(cat "$REJOIN_PENDING" 2>/dev/null)
  [ -n "$backend" ] || return 0
  dsconfig_local set-backend-prop --backend-name "$backend" --set writability-mode:enabled </dev/null >/dev/null || return 1
  echo "join: backend $backend takes the writes of clients again"
}

# Removes every server that is registered in the topology but no longer listed in
# REPLICATION_PEERS: its entry and group membership in cn=admin data (which is replicated,
# so one survivor's delete reaches the others) and its values in every replication server
# list of this server (which are configuration of this server alone, so every survivor
# prunes its own on its next start). dsreplication disable cannot do this for a dead server:
# it changes only the servers it can reach, and OpenDJ 4 has no cleanup subcommand. Every
# step tolerates losing the race to another survivor. A server registered by an address - a
# master that MASTER_SERVER named by one, whose replicas enabled through that address - is
# left alone: nothing tells it from a server listed by name, and the list moving from
# MASTER_SERVER to REPLICATION_PEERS would otherwise throw the live master out.
cleanup_departed() {
  local dn host kind cn value
  [ "$PEERS_ARE_EXPLICIT" = yes ] || return 0
  registrations | while IFS=$'\t' read -r dn host; do
    [ -z "$host" ] && continue
    is_address "$host" && continue
    if ! in_peers "$host" && ! is_self "$host"; then
      echo "join: removing departed server $host from cn=admin data"
      unregister "$dn" || echo "join: could not remove $dn (already removed by another survivor?)"
    fi
  done
  replication_server_lists | while IFS=$'\t' read -r kind cn value; do
    [ -z "$value" ] && continue
    host=${value%:*}
    is_address "$host" && continue
    if ! in_peers "$host" && ! is_self "$host"; then
      echo "join: removing departed replication server $value from the $kind list of $cn"
      change_replication_servers "$kind" "$cn" --remove "$value" || true
    fi
  done
}

# Adds every listed peer that is registered in the topology to each replication server list
# of this server that lacks it, under the name it is registered by - the name dsreplication
# enable writes as well, so no server is listed twice. Joins that run at the same time each
# configure the topology as they read it, and two of them can leave a pair of replication
# servers that know nothing of each other, cutting the updates of one off from the other; a
# replication server connects to a server added to its list at once. A peer that is not
# registered yet is waited for, as long as the retries last, after this server already
# reports itself healthy.
repair_replication_servers() {
  local i peer waiting hosts host lists kind cn value found
  [ "$PEERS_ARE_EXPLICIT" = yes ] || return 0
  for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
    waiting=
    # one search a round: every ldapsearch starts a JVM
    hosts=$(registered_hosts)
    for peer in "${PEERS[@]}"; do
      is_self "$peer" && continue
      found=no
      for host in $hosts; do
        names_match "$(lower "$peer")" "$(lower "$host")" && { found=yes; break; }
      done
      [ "$found" = yes ] || waiting="$waiting $peer"
    done
    [ -z "$waiting" ] && break
    [ "$i" -eq "$REPLICATION_RETRY_COUNT" ] && break
    echo "join: waiting for$waiting to register in the topology before the replication server lists are checked"
    sleep "$REPLICATION_RETRY_INTERVAL"
  done
  hosts=$(registered_hosts)
  lists=$(replication_server_lists)
  # a here-string of nothing still reads as one empty line
  [ -n "$lists" ] || return 0
  while IFS=$'\t' read -r kind cn value; do
    [ -n "$value" ] && continue
    for host in $hosts; do
      is_self "$host" && continue
      in_peers "$host" || continue
      found=no
      while IFS=$'\t' read -r k c v; do
        [ "$k" = "$kind" ] && [ "$c" = "$cn" ] && [ -n "$v" ] || continue
        names_match "$(lower "${v%:*}")" "$(lower "$host")" && { found=yes; break; }
      done <<<"$lists"
      if [ "$found" = no ]; then
        echo "join: adding replication server $host:$REPLICATION_PORT to the $kind list of $cn"
        change_replication_servers "$kind" "$cn" --add "$host:$REPLICATION_PORT" || true
      fi
    done
  done <<<"$lists"
}

# the backend of BASE_DN refuses the writes of clients because this join stopped them: an
# operator's read-only backend leaves $REJOIN_PENDING empty, and a hold whose dsconfig failed
# named the backend without changing its mode
writes_held() {
  [ -s "$REJOIN_PENDING" ] && [ "$(data_backend | awk '{ print $2 }')" = internal-only ]
}

# a server that rejoins keeps $REJOIN_PENDING, and so stays unready on its next start too,
# until its clients may write again and its peers see it ready. A dsconfig or a publish that
# fails is tried again here, as long as the retries last: the join that got this far would
# otherwise end, and only a later start would enable, or initialize, all over again
leave_rejoin() {
  local i
  [ -f "$REJOIN_PENDING" ] || return 0
  for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
    if release_writes && publish_state ready; then
      rm -f "$REJOIN_PENDING"
      return 0
    fi
    if [ "$i" -lt "$REPLICATION_RETRY_COUNT" ]; then
      pause "join: this server is not out of its rejoin yet ($i of $REPLICATION_RETRY_COUNT)"
    fi
  done
  if writes_held; then
    echo "join: could not let the writes of clients in again after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy and serves its data read-only until a later start does"
  else
    echo "join: could not publish this server ready after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy until a later start does"
  fi
  return 1
}

joined() {
  cleanup_departed
  leave_rejoin || exit 1
  touch "$BOOTSTRAP_COMPLETE"
  echo "join: this server is a member of the replication topology, the health check may probe it"
  repair_replication_servers
}

# the peers learn that this server holds the data before anything else changes: a seed whose
# state never reached them would be healthy while every pending peer skips it
seed() { # <why>
  echo "join: $1, seeding it with this server's data"
  publish_state ready || return 1
  leave_rejoin || return 1
  rm -f "$INITIALIZE_PENDING"
  touch "$BOOTSTRAP_COMPLETE"
}

# what giving up means depends on whether the container already reports itself healthy: a
# volume that holds the data of the topology does so from the start, until a reset of its
# replication took that back - and the writes of its clients, where the reset stopped them
give_up() {
  if [ -f "$BOOTSTRAP_COMPLETE" ]; then
    echo "join: could not join the replication topology after $REPLICATION_RETRY_COUNT attempts, this server keeps serving its data unreplicated until a later start joins it"
  elif writes_held; then
    echo "join: could not join the replication topology after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy and serves its data read-only until a later start joins it"
  else
    echo "join: could not join the replication topology after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy"
  fi
  exit 1
}

echo "join: waiting for the server on the administration connector"
for i in $(seq 1 150); do
  server_up && break
  if [ "$i" -eq 150 ]; then
    echo "join: the server did not come up, or does not take ROOT_PASSWORD any more, giving up"
    exit 1
  fi
  sleep 2
done

FIRST=no
is_self "${PEERS[0]}" && FIRST=yes

# Every round on both roads starts here: a replication configuration that the peers no longer
# register is taken down (see reset_replication). So is one that the peers register but the
# cn=admin data of this server does not: an enable that stopped half-way - its initialize of
# cn=admin data failed, say - leaves BASE_DN replicated and this server registered at the
# peer, and every enable after it exits 5, "already replicated", without registering it here.
# Fails when a reset is due and could not be done in this round, which then enables nothing -
# an enable registers nobody before it.
reset_if_unregistered() { # <the state to publish meanwhile>
  local rc=0
  replicates_base_dn localhost || return 0
  if ! registered_with_peers; then
    reset_replication "$1" "the peers no longer register this server"
    return
  fi
  registered_here || rc=$?
  if [ "$rc" -eq 1 ]; then
    reset_replication "$1" "the peers register this server but its own cn=admin data does not"
  fi
}

if [ ! -f "$INITIALIZE_PENDING" ]; then
  # This volume holds the data of the topology, or is the one that seeds it: it publishes
  # that - unless a reset took its replication down and it has not rejoined yet, also on an
  # earlier start
  if [ -f "$REJOIN_PENDING" ]; then
    publish_state rejoining
  else
    publish_state ready
  fi
  for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
    if ! reset_if_unregistered rejoining; then
      : # the next round tries the reset again
    elif is_member; then
      echo "join: already a member of the replication topology"
      joined
      exit
    else
      # only through a peer that holds the topology, as on the pending road: a rejoining peer
      # has no replication of its own, a pending one or one that still bootstraps none yet, and
      # an enable with any of them registers the two in a registry of their own - which counts
      # as membership, a peer of the pair lists this server - beside the survivors, whom
      # nothing ever adds to it
      for peer in "${PEERS[@]}"; do
        is_self "$peer" && continue
        state=$(peer_state "$peer")
        if ! trusted "$state"; then
          echo "join: $peer is $state, not a peer to join through"
          continue
        fi
        echo "join: enabling replication with $peer"
        enable_through "$peer"
        rc=$?
        if member_of_topology; then
          echo "join: joined the replication topology through $peer"
          cross_check "$peer" "; replication will not flow until this server or that one is initialized by hand"
          joined
          exit
        fi
        [ "$rc" -eq 75 ] || echo "join: dsreplication enable with $peer exited with $rc and membership is not there"
      done
    fi
    # a list without a single other server leaves nothing to retry against, and peers that
    # all wait for data join through this one rather than the other way round
    if [ "$FIRST" = yes ] && all_others_pending; then
      seed "no other peer holds the data of the topology" || exit 1
      exit 0
    fi
    if [ "$i" -lt "$REPLICATION_RETRY_COUNT" ]; then
      pause "join: no peer joined this server yet ($i of $REPLICATION_RETRY_COUNT)"
    fi
  done
  # the same rule as at the end of the pending road below
  if [ "$FIRST" = yes ] && ! any_other_may_hold_data; then
    seed "no topology found after $REPLICATION_RETRY_COUNT attempts" || exit 1
    exit 0
  fi
  give_up
fi

# This volume was bootstrapped and never received the data of the topology: it joins and
# initializes only through a peer that holds that data, and only the first peer may decide
# that nobody does. Its membership is asked of the peers as on the road above: a volume that
# enabled once but never completed its initialize may have been removed by the survivors
# while it was away, and its own cn=admin data still registers it until their delete reaches
# it - after which every enable registers nobody, as reset_replication describes.
publish_state pending
member=no
for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
  member=no
  reset_due=no
  if ! reset_if_unregistered pending; then
    reset_due=yes
  elif is_member; then
    member=yes
  fi
  if [ "$member" = no ] && [ "$reset_due" = no ]; then
    for peer in "${PEERS[@]}"; do
      is_self "$peer" && continue
      state=$(peer_state "$peer")
      if ! trusted "$state"; then
        echo "join: $peer is $state, not a peer to join through"
        continue
      fi
      echo "join: enabling replication with $peer"
      enable_through "$peer"
      rc=$?
      if member_of_topology; then
        member=yes
        echo "join: joined the replication topology through $peer"
        break
      fi
      [ "$rc" -eq 75 ] || echo "join: dsreplication enable with $peer exited with $rc and membership is not there"
    done
  fi
  if [ "$member" = yes ]; then
    source=$(trusted_source)
    if [ -n "$source" ]; then
      echo "join: initializing from $source"
      initialize_from "$source"
      rc=$?
      if [ "$rc" -eq 0 ]; then
        # published before the marker goes, so that the marker is never gone while the peers
        # still see this server pending; the next start initializes it again
        publish_state ready || exit 1
        rm -f "$INITIALIZE_PENDING"
        cross_check "$source" " after initialize"
        joined
        exit
      fi
      echo "join: initialize from $source exited with $rc"
    elif [ "$FIRST" = yes ] && all_others_pending; then
      seed "every other peer waits for the data of the topology as well" || exit 1
      joined
      exit 0
    else
      echo "join: no peer holds the data of the topology yet"
    fi
  elif [ "$FIRST" = yes ] && all_others_pending; then
    seed "every other peer waits for the data of the topology as well" || exit 1
    exit 0
  fi
  if [ "$i" -lt "$REPLICATION_RETRY_COUNT" ]; then
    pause "join: not joined and initialized yet ($i of $REPLICATION_RETRY_COUNT)"
  fi
done

# Only the first peer may decide that there is no topology to join and that its own data
# seeds one, and only while no peer that could hold the topology's data answers; anyone
# else staying unhealthy is what surfaces a lost topology instead of forking it
if [ "$FIRST" = yes ] && [ "$member" = no ] && ! any_other_may_hold_data; then
  seed "no topology found after $REPLICATION_RETRY_COUNT attempts" || exit 1
  exit 0
fi

give_up
