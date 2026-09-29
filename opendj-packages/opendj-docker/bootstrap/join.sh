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
# initialized from a ready peer, or it seeded the topology. A pending server joins and
# initializes only through a ready peer, so two fresh servers that start together never take
# each other's bootstrap data for the topology's; where the list is only MASTER_SERVER, a
# peer that publishes no state (an image before this one) counts as ready, as the old
# replicate.sh trusted its master. The seed is only the first entry of REPLICATION_PEERS: at
# once when every other peer answers and is pending (all of them start from scratch,
# together or not), or once the retries are exhausted without any peer it could trust. The
# residual risk is documented in the README: every other server down *and* the first peer's
# volume lost means the first peer seeds empty.
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
# not replicated: cn=config is this server's alone, and it lives on the volume next to the
# marker, so the two cannot tell different stories
STATE_DN="cn=Docker Join,cn=config"

REPLICATION_RETRY_COUNT=${REPLICATION_RETRY_COUNT:-30}
REPLICATION_RETRY_INTERVAL=${REPLICATION_RETRY_INTERVAL:-10}
# dsreplication enable can hang rather than fail (a peer that stops mid-operation), so no
# enable runs without a bound
REPLICATION_ATTEMPT_TIMEOUT=${REPLICATION_ATTEMPT_TIMEOUT:-120}
# a total update is a full import of BASE_DN, which takes as long as the data needs; a killed
# dsreplication initialize leaves its task running on the server, and the next attempt asks
# for another full import, so by default it runs without a bound
REPLICATION_INITIALIZE_TIMEOUT=${REPLICATION_INITIALIZE_TIMEOUT:-0}

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
# ADMIN_PORT in the name, on every start
PASSWORD_FILE=$(mktemp -p /dev/shm "opendj-join.$ADMIN_PORT.XXXXXX" 2>/dev/null || mktemp) || exit 1
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

ldapmodify_local() {
  ldapmodify --noPropertiesFile --hostname localhost --port "$ADMIN_PORT" --useSsl --trustAll \
    --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" "$@" 2>/dev/null
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

# ready or pending as the peer publishes it, absent when it answers without publishing any,
# down when it does not answer
peer_state() {
  local out state
  out=$(search "$1" --baseDN "$STATE_DN" --searchScope base "(objectClass=*)" description)
  case $? in
    0)
      state=$(printf '%s\n' "$out" | awk 'tolower($1) == "description:" { print $2; exit }')
      case $state in
        ready | pending) echo "$state" ;;
        *) echo absent ;;
      esac
      ;;
    32) echo absent ;;
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

registered_hosts() {
  search localhost --baseDN "cn=Servers,cn=admin data" --searchScope one "(objectClass=*)" hostname \
    | awk 'tolower($1) == "hostname:" { print $2 }'
}

is_registered() {
  local host peer
  peer=$(lower "$1")
  for host in $(registered_hosts); do
    names_match "$peer" "$(lower "$host")" && return 0
  done
  return 1
}

# a member holds the replication domain for BASE_DN in its configuration and is registered
# in cn=admin data - under the name the enable that registered it connected with, which for
# a server that never ran an enable of its own is the name a peer listed it by; both are made
# by one dsreplication enable, so requiring both keeps a half-done enable in the retry loop
# instead of declaring it a success
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

enable_through() {
  bounded "$REPLICATION_ATTEMPT_TIMEOUT" dsreplication enable \
    --host1 "$1" --port1 "$ADMIN_PORT" --bindDN1 "$ROOT_USER_DN" \
    --bindPasswordFile1 "$PASSWORD_FILE" --replicationPort1 "$REPLICATION_PORT" \
    --host2 "$MYHOSTNAME" --port2 "$ADMIN_PORT" --bindDN2 "$ROOT_USER_DN" \
    --bindPasswordFile2 "$PASSWORD_FILE" --replicationPort2 "$REPLICATION_PORT" \
    --adminUID admin --adminPasswordFile "$PASSWORD_FILE" \
    --baseDN "$BASE_DN" -X -n
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

any_other_trusted() {
  local peer
  for peer in "${PEERS[@]}"; do
    is_self "$peer" && continue
    trusted "$(peer_state "$peer")" && return 0
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

# Removes every server that is registered in the topology but no longer listed in
# REPLICATION_PEERS: its entry and group membership in cn=admin data (which is replicated,
# so one survivor's delete reaches the others) and its values in every replication server
# list of this server (which are configuration of this server alone, so every survivor
# prunes its own on its next start). dsreplication disable cannot do this for a dead server:
# it changes only the servers it can reach, and OpenDJ 4 has no cleanup subcommand. Every
# step tolerates losing the race to another survivor.
cleanup_departed() {
  local dn host kind cn value
  [ "$PEERS_ARE_EXPLICIT" = yes ] || return 0
  search localhost --baseDN "cn=Servers,cn=admin data" --searchScope one "(objectClass=*)" hostname \
    | awk '/^dn: /{dn=substr($0,5)} tolower($1) == "hostname:" {print dn "\t" $2}' \
    | while IFS=$'\t' read -r dn host; do
      [ -z "$host" ] && continue
      if ! in_peers "$host" && ! is_self "$host"; then
        echo "join: removing departed server $host from cn=admin data"
        printf 'dn: %s\nchangetype: delete\n' "$dn" | ldapmodify_local >/dev/null \
          || echo "join: could not remove $dn (already removed by another survivor?)"
        printf 'dn: cn=all-servers,cn=Server Groups,cn=admin data\nchangetype: modify\ndelete: uniqueMember\nuniqueMember: %s\n' "${dn%%,cn=Servers,cn=admin data}" \
          | ldapmodify_local >/dev/null || true
      fi
    done
  replication_server_lists | while IFS=$'\t' read -r kind cn value; do
    [ -z "$value" ] && continue
    host=${value%:*}
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
    for peer in "${PEERS[@]}"; do
      is_self "$peer" || is_registered "$peer" || waiting="$waiting $peer"
    done
    [ -z "$waiting" ] && break
    [ "$i" -eq "$REPLICATION_RETRY_COUNT" ] && break
    echo "join: waiting for$waiting to register in the topology before the replication server lists are checked"
    sleep "$REPLICATION_RETRY_INTERVAL"
  done
  hosts=$(registered_hosts)
  lists=$(replication_server_lists)
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

joined() {
  cleanup_departed
  touch "$BOOTSTRAP_COMPLETE"
  echo "join: this server is a member of the replication topology, the health check may probe it"
  repair_replication_servers
}

seed() { # <why>
  echo "join: $1, seeding it with this server's data"
  rm -f "$INITIALIZE_PENDING"
  publish_state ready
  touch "$BOOTSTRAP_COMPLETE"
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

FIRST=no
is_self "${PEERS[0]}" && FIRST=yes

if [ ! -f "$INITIALIZE_PENDING" ]; then
  # This volume holds the data of the topology, or is the one that seeds it: it may join
  # through any peer that answers
  publish_state ready
  if is_member; then
    echo "join: already a member of the replication topology"
    joined
    exit
  fi
  for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
    for peer in "${PEERS[@]}"; do
      is_self "$peer" && continue
      echo "join: enabling replication with $peer"
      enable_through "$peer"
      rc=$?
      if is_member; then
        echo "join: joined the replication topology through $peer"
        cross_check "$peer" "; replication will not flow until this server or that one is initialized by hand"
        joined
        exit
      fi
      echo "join: dsreplication enable with $peer exited with $rc and membership is not there"
    done
    # a list without a single other server leaves nothing to retry against, and peers that
    # all wait for data join through this one rather than the other way round
    if [ "$FIRST" = yes ] && all_others_pending; then
      seed "no other peer holds the data of the topology"
      exit 0
    fi
    if [ "$i" -lt "$REPLICATION_RETRY_COUNT" ]; then
      echo "join: no peer joined this server yet, trying again in $REPLICATION_RETRY_INTERVAL s ($i of $REPLICATION_RETRY_COUNT)"
      sleep "$REPLICATION_RETRY_INTERVAL"
    fi
  done
  if [ "$FIRST" = yes ]; then
    seed "no topology found after $REPLICATION_RETRY_COUNT attempts"
    exit 0
  fi
  echo "join: could not join the replication topology after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy"
  exit 1
fi

# This volume was bootstrapped and never received the data of the topology: it joins and
# initializes only through a peer that holds that data, and only the first peer may decide
# that nobody does
publish_state pending
for i in $(seq 1 "$REPLICATION_RETRY_COUNT"); do
  if ! is_member; then
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
      if is_member; then
        echo "join: joined the replication topology through $peer"
        break
      fi
      echo "join: dsreplication enable with $peer exited with $rc and membership is not there"
    done
  fi
  if is_member; then
    source=$(trusted_source)
    if [ -n "$source" ]; then
      echo "join: initializing from $source"
      initialize_from "$source"
      rc=$?
      if [ "$rc" -eq 0 ]; then
        rm -f "$INITIALIZE_PENDING"
        publish_state ready
        cross_check "$source" " after initialize"
        joined
        exit
      fi
      echo "join: initialize from $source exited with $rc"
    elif [ "$FIRST" = yes ] && all_others_pending; then
      seed "every other peer waits for the data of the topology as well"
      joined
      exit 0
    else
      echo "join: no peer holds the data of the topology yet"
    fi
  elif [ "$FIRST" = yes ] && all_others_pending; then
    seed "every other peer waits for the data of the topology as well"
    exit 0
  fi
  if [ "$i" -lt "$REPLICATION_RETRY_COUNT" ]; then
    echo "join: not joined and initialized yet, trying again in $REPLICATION_RETRY_INTERVAL s ($i of $REPLICATION_RETRY_COUNT)"
    sleep "$REPLICATION_RETRY_INTERVAL"
  fi
done

# Only the first peer may decide that there is no topology to join and that its own data
# seeds one, and only while no peer that could hold the topology's data answers; anyone
# else staying unhealthy is what surfaces a lost topology instead of forking it
if [ "$FIRST" = yes ] && ! is_member && ! any_other_trusted; then
  seed "no topology found after $REPLICATION_RETRY_COUNT attempts"
  exit 0
fi

echo "join: could not join the replication topology after $REPLICATION_RETRY_COUNT attempts, this container will not report itself healthy"
exit 1
