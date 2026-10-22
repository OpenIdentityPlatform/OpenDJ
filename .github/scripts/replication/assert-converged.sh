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
    if ! offline $n export-ldif --offline --noPropertiesFile --backendID userRoot --ldifFile /out/export/$n.ldif \
        --excludeAttribute ds-sync-hist --excludeAttribute ds-sync-state >"$OUT/export/$n.out" 2>&1; then
      echo "::error::export-ldif failed on $n, see export/$n.out"
      failed=1
      continue
    fi
    # two exports that failed alike, or exported another backend, are as identical as two good ones
    entries=$(grep -c '^dn: ' "$OUT/export/$n.ldif" || true)
    if [ "${entries:-0}" -le "$SAMPLE_DATA" ]; then
      echo "::error::the export of $n holds $entries entries, not more than the $SAMPLE_DATA seeded users"
      failed=1
    fi
    offline $n verify-index --baseDN "$BASE_DN" --countErrors >"$OUT/diff/$n.verify-index.txt" 2>&1 \
      || { echo "::error::verify-index found errors on $n, see diff/$n.verify-index.txt"; failed=1; }
  done
  first=${DS_NODES%% *}
  for n in ${DS_NODES#* }; do
    # ldifdiff exits with 1 when the files differ
    offline $n ldifdiff --outputLDIF /out/diff/$n.ldif /out/export/$first.ldif /out/export/$n.ldif >/dev/null \
      || { echo "::error::$n holds other data than $first, see diff/$n.ldif"; failed=1; }
  done
  assert_changelogs || failed=1
  [ -z "$failed" ] || fail "the replicas do not hold the same data"
}

# The changes of every replica in the changelog of every replication server, one after the
# other: a gap in the sequence numbers of a replica's changes during the load is a change that
# reached no replication server. The data shows it only while no later change of the same
# attribute overwrites it, and missing-changes never (#1195). docker cp streams the changelog
# out of the stopped container; changelog-gaps.py reads it. A directory server a disturbance
# restarted numbered a ReplicaOfflineMsg in between, which no log file holds: its window goes
# with its server id
assert_changelogs() {
  local n id code offline= failed=
  mkdir -p "$OUT/changelog"
  [ ! -f "$OUT/disturbances.txt" ] \
    || offline=$(awk 'FILENAME == ARGV[1] { id[$2] = $1; next }
                      $4 == "restart" && ($3 in id) { printf "--offline=%s:%s:%s\n", id[$3], $1, $2 }' \
                   "$OUT/server-ids.txt" "$OUT/disturbances.txt")
  for n in $RS_NODES; do
    code=0
    # one word per window
    docker cp "$n:/opt/opendj/data/changelogDb" - \
      | python3 -I "$HERE/changelog-gaps.py" $offline "$BASE_DN" "$LOAD_START_MS" "$LOAD_END_MS" \
      | awk 'FILENAME == ARGV[1] { name[$1] = $2; next }
             $1 == "replica" { id = $2; sub(/:$/, "", id); if (id in name) $2 = id " (" name[id] "):" }
             { print }' "$OUT/server-ids.txt" - >"$OUT/changelog/$n.txt" || code=$?
    case $code in
      0) ;;
      1) echo "::error::changes of the load are missing from the changelog of $n, see changelog/$n.txt"; failed=1 ;;
      *) echo "::error::the changelog of $n could not be read, see changelog/$n.txt"; failed=1; continue ;;
    esac
    # every directory server made changes in the load: a changelog whose records the script
    # no longer reads - another layout, another message type - would otherwise show no gap
    for id in $(awk '{ print $1 }' "$OUT/server-ids.txt"); do
      grep -q "^replica $id (" "$OUT/changelog/$n.txt" \
        || { echo "::error::the changelog of $n holds no change of the load by $(awk -v id=$id '$1 == id { print $2 }' "$OUT/server-ids.txt")"; failed=1; }
    done
  done
  [ -z "$failed" ]
}

# Equality filters on an attribute the default setup indexes (sn), each matching fewer entries
# than the index entry limit (4000 by default): the search goes through the index even for the
# Directory Manager, so an entry missing from the index of one replica (#1021) is missing from
# its answer, which an export of the entries does not show. A key that holds more entries than
# the limit is not indexed, and the search then matches every entry by its filter -
# (objectClass=inetOrgPerson) over the seeded users did - so unindexed_searches checks that
# each search went through the index
INDEXED_FILTERS="(sn=AddRate) (sn=Diverged)"

# The searches of INDEXED_FILTERS a directory server ran without an index, or that its access
# log does not hold, as errors. Every access log file is read: the load rotates them
unindexed_searches() { # <node>
  docker exec $1 sh -c 'grep -h " SEARCH " /opt/opendj/data/logs/access*; [ $? -le 1 ]' \
    | awk -v node=$1 -v base="$BASE_DN" -v filters="$INDEXED_FILTERS" '
      BEGIN { n = split(filters, f, " ") }
      $3 == "SEARCH" && $4 == "REQ" && index($0, " base=\"" base "\" scope=sub ") {
        for (i = 1; i <= n; i++) if (index($0, " filter=\"" f[i] "\" ")) req[$5 " " $6] = f[i]
      }
      $3 == "SEARCH" && $4 == "RES" && / unindexed( |$)/ { unindexed[$5 " " $6] = 1 }
      END {
        for (k in req) { seen[req[k]] = 1; if (k in unindexed) printf "::error::%s ran %s without an index\n", node, req[k] }
        for (i = 1; i <= n; i++) if (!(f[i] in seen)) printf "::error::the access log of %s holds no search of %s\n", node, f[i]
      }'
}

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

since_mark() { # <node>
  docker exec $1 tail -n +$(($(cat "$OUT/logs/$1.mark") + 1)) /opt/opendj/data/logs/errors
}

# How often each directory server connected to a replication server again after the topology
# came up, whatever made it: a switch the broker decided on (msgID 188, or 213 to spread the
# load), a replication server that closed the connection (63), a connection that broke. Each
# connection ends in msgID 62 (96 where the generation IDs differ), so a switch that went back
# to the same replication server (#1215) counts as well. Changes made around a reconnection
# under load were lost in #1195, so the summary shows how many a run went through
record_reconnections() {
  local n lines
  for n in $DS_NODES; do
    # a log that cannot be read leaves the node out, which the summary shows as -
    lines=$(since_mark $n) || continue
    echo "$n $(echo "$lines" | grep -E "msgID=(62|96) " | grep -cF "for domain \"$BASE_DN\"" || true)"
  done >"$OUT/reconnections.txt"
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

# run.sh calls this in a condition, where neither errexit nor the ERR trap stops a command
# that fails: every search and every read of a log checks its own status, since a search that
# failed alike on every replica gives the same empty answer everywhere
assert_online() {
  local f i=0 n first out lines failed=
  first=${DS_NODES%% *}
  mkdir -p "$OUT/dns"
  record_reconnections
  for f in $INDEXED_FILTERS; do
    i=$((i + 1))
    for n in $DS_NODES; do
      ldaps_search $n "$BASE_DN" sub "$f" 1.1 >"$OUT/dns/$i.$n.ldif" 2>&1 \
        || { echo "::error::$f failed on $n, see dns/$i.$n.ldif"; failed=1; }
      awk '/^dn: /{ print tolower($0) }' "$OUT/dns/$i.$n.ldif" | sort >"$OUT/dns/$i.$n.txt"
    done
    for n in ${DS_NODES#* }; do
      cmp -s "$OUT/dns/$i.$first.txt" "$OUT/dns/$i.$n.txt" \
        || { echo "::error::$f returns other entries on $n than on $first, see dns/$i.*"; failed=1; }
    done
  done
  for n in $DS_NODES; do
    if ! out=$(unindexed_searches $n); then
      echo "::error::the access log of $n could not be read"; failed=1
    elif [ -n "$out" ]; then
      echo "$out"; failed=1
    fi
  done
  for n in $DS_NODES; do
    # the load makes no naming conflicts: each server adds under a uid prefix of its own
    if ! out=$(ldaps_search $n "$BASE_DN" sub "(ds-sync-conflict=*)" 1.1); then
      echo "::error::the ds-sync-conflict search failed on $n"; failed=1
    elif grep -q '^dn:' <<<"$out"; then
      echo "::error::$n holds ds-sync-conflict entries"; failed=1
    fi
  done
  for n in $ALL_NODES; do
    if ! lines=$(since_mark $n); then
      echo "::error::the errors log of $n could not be read"; failed=1; continue
    fi
    lines=$(grep -E "category=SYNC severity=ERROR|SSLHandshakeException|bad_certificate" <<<"$lines" || true)
    [ -z "$lines" ] || { printf '%s\n' "$lines" >"$OUT/logs/$n.errors"; echo "::error::$n logged replication errors, see logs/$n.errors"; failed=1; }
  done
  assert_rs_mesh || failed=1
  [ -z "$failed" ] || fail "the replicas do not answer the same"
}
