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
