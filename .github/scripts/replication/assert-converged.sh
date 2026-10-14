#
#
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

# The checks of the replication convergence run (#1090), sourced by run.sh: the replicas hold
# the same data, compared on the data itself rather than on the replication counters.

# Writes to the second directory server only, which the checks must report: the replication
# repair control (ReplicationRepairRequestControl) applies an operation without replicating it
inject_divergence() {
  local n
  n=$(echo $DS_NODES | cut -d' ' -f2)
  printf 'dn: uid=user.0,ou=People,%s\nchangetype: modify\nreplace: description\ndescription: only on %s\n' "$BASE_DN" "$n" \
    | docker exec -i "$n" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 1636 \
        --useSsl --trustAll --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" \
        --control 1.3.6.1.4.1.26027.1.5.2 >/dev/null
  # an entry of its own, which the searches of the other replicas do not return
  printf 'dn: uid=only-on-%s,ou=People,%s\nobjectClass: inetOrgPerson\nuid: only-on-%s\ncn: Diverged\nsn: Diverged\n' "$n" "$BASE_DN" "$n" \
    | docker exec -i "$n" /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 1636 \
        --useSsl --trustAll --bindDN "cn=Directory Manager" --bindPassword "$ROOT_PASSWORD" \
        --control 1.3.6.1.4.1.26027.1.5.2 --defaultAdd >/dev/null
}

# An offline export and verify-index need the servers stopped; docker stop gives them the time
# of a normal shutdown, so what they write on the way down is on their volumes
stop_all() {
  docker stop -t 120 $ALL_NODES >/dev/null
}

# Runs a tool of the image on the volume of a stopped server. run.sh of the image points the
# installation at the volume (instance.loc) when the container starts, which a one-off
# container has to do itself; $OUT is mounted on /out
offline() { # <node> <tool> [<argument>...]
  local node=$1 tool=$2
  shift 2
  docker run --rm -v "vol-$node:/opt/opendj/data" -v "$OUT:/out" --entrypoint sh "$IMAGE" -c \
    'echo /opt/opendj/data >/opt/opendj/instance.loc && exec /opt/opendj/bin/"$0" "$@"' "$tool" "$@"
}

assert_offline() {
  local n first entries failed=
  # the tools run as the user of the image, not as the one running this script
  mkdir -p "$OUT/export" "$OUT/diff"
  chmod 777 "$OUT/export" "$OUT/diff"
  for n in $DS_NODES; do
    # ds-sync-hist is the record of the changes each replica applied, ds-sync-state the position
    # of each replica: both are its own
    # --offline: a tools.properties with connection settings would turn the export into a task
    # of a server that is not running
    offline $n export-ldif --offline --noPropertiesFile --backendID userRoot --ldifFile /out/export/$n.ldif \
      --excludeAttribute ds-sync-hist --excludeAttribute ds-sync-state >"$OUT/export/$n.out" 2>&1 \
      || fail "export-ldif failed on $n, see export/$n.out"
    # two exports that failed alike, or exported another backend, are as identical as two good ones
    entries=$(grep -c '^dn: ' "$OUT/export/$n.ldif" || true)
    [ "${entries:-0}" -gt "$SAMPLE_DATA" ] || fail "the export of $n holds $entries entries, not more than the $SAMPLE_DATA seeded users"
    offline $n verify-index --baseDN "$BASE_DN" --countErrors >"$OUT/diff/$n.verify-index.txt" 2>&1 \
      || { echo "::error::verify-index found errors on $n, see diff/$n.verify-index.txt"; failed=1; }
  done
  first=${DS_NODES%% *}
  for n in ${DS_NODES#* }; do
    # ldifdiff exits with 1 when the files differ
    offline $n ldifdiff --outputLDIF /out/diff/$n.ldif /out/export/$first.ldif /out/export/$n.ldif >/dev/null \
      || { echo "::error::$n holds other data than $first, see diff/$n.ldif"; failed=1; }
  done
  [ -z "$failed" ] || fail "the replicas do not hold the same data"
}

# Equality filters on attributes the default setup indexes (objectClass, sn): the search goes
# through the index even for the Directory Manager, so an entry missing from the index of one
# replica (#1021) is missing from its answer, which an export of the entries does not show
INDEXED_FILTERS="(objectClass=inetOrgPerson) (sn=AddRate) (sn=Diverged)"

# The number of lines each server's errors log held once the topology was up: the joins log
# replication errors of their own while the servers come up one after the other, and only what
# follows is the run's
mark_logs() {
  local n
  mkdir -p "$OUT/logs"
  for n in $ALL_NODES; do
    docker exec $n sh -c 'cat /opt/opendj/data/logs/errors | wc -l' >"$OUT/logs/$n.mark"
  done
}

errors_since_mark() { # <node>
  docker exec $1 tail -n +$(($(cat "$OUT/logs/$1.mark") + 1)) /opt/opendj/data/logs/errors \
    | grep -E "category=SYNC severity=ERROR|SSLHandshakeException|bad_certificate" || true
}

# A directory server is connected to one replication server at a time, so the replication
# servers reach each other only through their own connections: each one names every peer it is
# connected to "Connected replication server RS(<id>) <host:port>" (ReplicationServerHandler)
assert_rs_mesh() {
  local rs want got failed=
  want=$(($(echo $RS_NODES | wc -w) - 1))
  for rs in $RS_NODES; do
    got=$(admin_search $rs "$MONITOR_DOMAIN" sub "(cn=Connected replication server*)" 1.1 | grep -c '^dn:' || true)
    [ "${got:-0}" -eq "$want" ] || { echo "::error::$rs is connected to ${got:-0} replication servers, not $want"; failed=1; }
  done
  [ -z "$failed" ]
}

assert_online() {
  local f i=0 n first lines failed=
  first=${DS_NODES%% *}
  mkdir -p "$OUT/dns"
  for f in $INDEXED_FILTERS; do
    i=$((i + 1))
    for n in $DS_NODES; do
      ldaps_search $n "$BASE_DN" sub "$f" 1.1 | awk '/^dn: /{ print tolower($0) }' | sort >"$OUT/dns/$i.$n.txt"
    done
    for n in ${DS_NODES#* }; do
      cmp -s "$OUT/dns/$i.$first.txt" "$OUT/dns/$i.$n.txt" \
        || { echo "::error::$f returns other entries on $n than on $first, see dns/$i.*"; failed=1; }
    done
  done
  for n in $DS_NODES; do
    # the load makes no naming conflicts: each server adds under a uid prefix of its own
    [ -z "$(ldaps_search $n "$BASE_DN" sub "(ds-sync-conflict=*)" 1.1 | grep '^dn:')" ] \
      || { echo "::error::$n holds ds-sync-conflict entries"; failed=1; }
  done
  for n in $ALL_NODES; do
    lines=$(errors_since_mark $n)
    [ -z "$lines" ] || { printf '%s\n' "$lines" >"$OUT/logs/$n.errors"; echo "::error::$n logged replication errors, see logs/$n.errors"; failed=1; }
  done
  assert_rs_mesh || failed=1
  [ -z "$failed" ] || fail "the replicas do not answer the same"
}
