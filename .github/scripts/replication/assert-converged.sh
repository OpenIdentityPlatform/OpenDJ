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
  [ -z "$failed" ] || fail "the offline checks failed"
}

# The changes of every replica in the changelog of every replication server, one after the
# other: a gap in the sequence numbers may indicate a lost change, an offline message, or
# history that cannot be reconstructed on an empty RS. Missing-changes alone cannot prove
# which happened (#1195). docker cp streams the changelog
# out of the stopped container; changelog-gaps.py reads it
assert_changelogs() {
  local n id code failed=
  local -a evidence=()
  case ${DISTURBANCE:-none} in
    scale-*) evidence=(--offline-snapshots "$OUT/scale-checkpoint") ;;
  esac
  mkdir -p "$OUT/changelog"
  for n in $RS_NODES; do
    code=0
    docker cp "$n:/opt/opendj/data/changelogDb" - \
      | python3 -I "$HERE/changelog-gaps.py" "$BASE_DN" "$LOAD_START_MS" "$LOAD_END_MS" "${evidence[@]}" \
      | awk 'FILENAME == ARGV[1] { name[$1] = $2; next }
             $1 == "replica" { id = $2; sub(/:$/, "", id); if (id in name) $2 = id " (" name[id] "):" }
             { print }' "$OUT/server-ids.txt" - >"$OUT/changelog/$n.txt" || code=$?
    case $code in
      0) ;;
      1) echo "::error::sequence numbers of the load are missing from the changelog of $n, see changelog/$n.txt"; failed=1 ;;
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
case ${DISTURBANCE:-none} in
  scale-*)
    INDEXED_FILTERS="(sn=Diverged)"
    for n in $DS_NODES; do
      for shard in 1 2 3; do INDEXED_FILTERS="$INDEXED_FILTERS (sn=AddRate-$n-$shard)"; done
    done
    ;;
esac

# The searches of INDEXED_FILTERS a directory server ran without an index, or that its access
# log does not hold, as errors. Read rotated logs in chronological order, then the active log:
# connection and operation numbers can be reused after a server restart.
unindexed_searches() { # <node>
  docker exec $1 sh -c 'for file in /opt/opendj/data/logs/access.[0-9]* /opt/opendj/data/logs/access; do
    [ -f "$file" ] || continue
    grep -h " SEARCH " "$file"; status=$?
    [ "$status" -le 1 ] || exit "$status"
  done' \
    | awk -v node=$1 -v base="$BASE_DN" -v filters="$INDEXED_FILTERS" '
      BEGIN { n = split(filters, f, " ") }
      $3 == "SEARCH" && $4 == "REQ" {
        key = $5 " " $6
        delete req[key]
        if (index($0, " base=\"" base "\" scope=sub "))
          for (i = 1; i <= n; i++) if (index($0, " filter=\"" f[i] "\" ")) {
            req[key] = f[i]; seen[f[i]] = 1
          }
      }
      $3 == "SEARCH" && $4 == "RES" {
        key = $5 " " $6
        if (key in req && / unindexed( |$)/) printf "::error::%s ran %s without an index\n", node, req[key]
        delete req[key]
      }
      END {
        for (i = 1; i <= n; i++) if (!(f[i] in seen)) printf "::error::the access log of %s holds no search of %s\n", node, f[i]
      }'
}

# The number of lines each server's errors log held once the topology was up: the joins log
# replication errors of their own while the servers come up one after the other, and only what
# follows is the run's. A log that cannot be read fails the run: a cat that fails in a pipe
# would count 0 lines and exit 0, and window_open would take that for a window from the first
# line, while a redirect that fails ends sh with 2. A replacement RS retains its previous log
# as a prefix on the host so that marks and windows cover both volumes.
errors_lines() { # <node>
  local current previous=0
  # A scale-in captures the stopped node before removing its container. Its log cannot
  # change until it starts again, but the other nodes still open and close windows.
  if [ -f "$OUT/logs/$1.offline" ]; then
    cat "$OUT/logs/$1.offline"
    return
  fi
  current=$(docker exec "$1" sh -c 'wc -l </opt/opendj/data/logs/errors') || return 1
  if [ -f "$OUT/logs/$1.previous" ]; then
    previous=$(wc -l <"$OUT/logs/$1.previous") || return 1
  fi
  echo $((previous + current))
}

mark_logs() {
  local n
  mkdir -p "$OUT/logs"
  for n in $ALL_NODES; do
    errors_lines $n >"$OUT/logs/$n.mark"
  done
}

since_mark() { # <node>
  local first=$(($(cat "$OUT/logs/$1.mark") + 1))
  if [ -f "$OUT/logs/$1.previous" ]; then
    { cat "$OUT/logs/$1.previous" || exit 1; docker exec "$1" tail -n +1 /opt/opendj/data/logs/errors; } | tail -n +"$first"
  else
    docker exec "$1" tail -n +"$first" /opt/opendj/data/logs/errors
  fi
}

# A disturbance window over the errors log of every server (#1208): window_open before the
# disturbance, with the msgIDs of the errors it expects besides the failed handshakes,
# dns:<hostname> for ERROR 51 naming exactly that temporarily unavailable host, or
# cert:<DS>:<RS> for that DS's transient bad-certificate handshake with the rebuilt RS, or
# cert-peer:<RS>:<DS IP> for the corresponding RS-side warning after the DS restarts.
# timeout:<DS>:<RS> is recorded on one startup error line only after that DS reconnects to
# that RS in the same domain, as confirmed by scale_record_reconnected_timeouts.
# window_close records "<lines before> <lines after> [<allowance>...]" in logs/<node>.windows.
window_open() { window_open_key '' "$@"; }
window_close() { window_close_key ''; }

# Separate keys permit overlapping joins while retaining the exact window for each node.
window_open_key() { # <key> [<msgID>|dns:<hostname>...]
  local key=$1 n lines file
  shift
  case $key in *[!a-zA-Z0-9_-]*) return 1 ;; esac
  for n in $ALL_NODES; do
    file="$OUT/logs/$n.window${key:+-$key}"
    [ ! -e "$file" ] || return 1
    lines=$(errors_lines "$n") || return 1
    echo "$lines${*:+ $*}" >"$file" || return 1
  done
}

window_close_key() { # <key>
  local key=$1 n lines before ids file
  case $key in *[!a-zA-Z0-9_-]*) return 1 ;; esac
  for n in $ALL_NODES; do
    file="$OUT/logs/$n.window${key:+-$key}"
    lines=$(errors_lines "$n") || return 1
    read -r before ids <"$file" || return 1
    echo "$before $lines${ids:+ $ids}" >>"$OUT/logs/$n.windows" || return 1
    rm "$file" || return 1
  done
}

# A server removed during a scale-in has no Docker DNS name between individual rolling
# windows. Allow only that hostname for this whole interval, not other handshake failures.
dns_window_open() { # <hostname>
  local n lines
  for n in $ALL_NODES; do
    lines=$(errors_lines "$n") || return 1
    printf '%s %s\n' "$lines" "$1" >"$OUT/logs/$n.dns-window" || return 1
  done
}

dns_window_close() {
  local n lines before host
  for n in $ALL_NODES; do
    lines=$(errors_lines "$n") || return 1
    read -r before host <"$OUT/logs/$n.dns-window" || return 1
    printf '%s %s dns-only dns:%s\n' "$before" "$lines" "$host" >>"$OUT/logs/$n.windows" || return 1
    rm "$OUT/logs/$n.dns-window" || return 1
  done
}

# The replication errors <node> logged after mark_logs. A disturbance window expects the
# handshakes that a server starting on a loaded runner fails, the other end giving up first: a
# directory server (178) or a replication server (179) that disconnected in the handshake, both
# at ERROR on the replication server it connected to, a replication server whose connection
# crossed one the other way and lost the domain lock to it (145, a warning that abortStart logs
# at ERROR, as it does 178 and 179: #1241), and the SSLHandshakeException of a server whose peer
# closed the handshake once its read timed out. In run 39909337627 a restarted directory server
# timed out connecting (212) and its replication server logged 178, and a replication server
# logged 311 with SocketTimeoutException when the restarted server logged 119 with "Remote host
# terminated the handshake"; in run 40463887505 a replication server logged 145 on the
# connection of the restarted one. A window opened with msgIDs expects the SYNC errors of those
# msgIDs as well. A dns:<hostname> token expects only ERROR 51's exact resolution-failure
# message for that hostname, not other DNS errors inside the window. This covers the stopped
# RS container's name temporarily disappearing during kill-rs, restart-rs or a combined
# kill-ds or restart-ds. The certificate allowance is limited to the named DS and rebuilt RS;
# its later forced connection to that RS must succeed. Other hosts and DS remain errors.
# The other allowances cover what one disturbance leaves behind besides the handshakes, such
# as the connections a killed replication server breaks (disturb-kill-rs.sh), which a window of
# another one does not expect. Those go to logs/<node>.expected; outside a window, and anything
# else inside one, they are errors. A window expects them on any server, not only on the
# connections of the disturbed one: 178 and 179 name the servers by id alone, and a server has
# one in each of its domains and another for its replication server. A handshake that fails is
# followed by a connection elsewhere, and a change it lost the checks of the data find
replication_errors() { # <node>
  local windows=
  [ ! -f "$OUT/logs/$1.windows" ] || windows=$(tr '\n' ';' <"$OUT/logs/$1.windows")
  since_mark $1 | awk -v first=$(($(cat "$OUT/logs/$1.mark") + 1)) -v windows="$windows" \
                      -v expected="$OUT/logs/$1.expected" -v node="$1" -v base_dn="$BASE_DN" '
    BEGIN {
      n = split(windows, w, ";")
      for (i = 1; i <= n; i++) {
        k = split(w[i], f, " ")
        from[i] = f[1]; to[i] = f[2]; ids[i] = " "
        for (j = 3; j <= k; j++) ids[i] = ids[i] f[j] " "
      }
    }
    !/category=SYNC severity=ERROR|SSLHandshakeException|bad_certificate/ { next }
    {
      line = first + NR - 1
      handshake = /category=SYNC severity=ERROR msgID=(145|178|179) / \
                  || /category=SYNC severity=WARNING .*SSLHandshakeException\(Remote host terminated the handshake\)/
      id = ""
      if (match($0, /category=SYNC severity=ERROR msgID=[0-9]+ /)) {
        id = substr($0, RSTART, RLENGTH)
        sub(/.*msgID=/, "", id)
        sub(/ $/, "", id)
      }
      dns = ""
      if (id == "51") {
        message = substr($0, index($0, " msg=") + 5)
        if (message ~ /^The hostname [^ ]+ could not be resolved as an IP address$/) {
          split(message, parts, " ")
          dns = " dns:" parts[3] " "
        }
      }
      for (i = 1; i <= n; i++) {
        cert = timeout = 0
        if (id == "212" && index($0, "msg=Directory server DS(")) {
          count = split(ids[i], tokens, " ")
          for (j = 1; j <= count; j++) {
            if (split(tokens[j], part, ":") == 3 && part[1] == "timeout" && part[2] == node \
                && index($0, " timed out while connecting to replication server " part[3] ":8989 for domain \"" base_dn "\"")) timeout = 1
          }
        }
        if (/category=SYNC severity=WARNING msgID=119 / && /SSLHandshakeException/ && /bad_certificate/) {
          count = split(ids[i], tokens, " ")
          for (j = 1; j <= count; j++) {
            if (split(tokens[j], part, ":") == 3 && part[1] == "cert" && part[2] == node \
                && index($0, " connecting to replication server " part[3] ":8989 for domain ")) cert = 1
          }
        }
        if (/category=SYNC severity=WARNING msgID=105 / && /The error was: (\(bad_certificate\) )?Received fatal alert: bad_certificate$/) {
          count = split(ids[i], tokens, " ")
          for (j = 1; j <= count; j++) {
            if (split(tokens[j], part, ":") != 3 || part[1] != "cert-peer" || part[2] != node) continue
            if (split($0, sides, " from ") != 2 || split(sides[2], ends, " to local address ") != 2) continue
            if (split(ends[1], remote, ":") != 2 || remote[2] !~ /^[0-9]+$/) continue
            suffix = "/" part[3]
            if (length(remote[1]) >= length(suffix) \
                && substr(remote[1], length(remote[1]) - length(suffix) + 1) == suffix) cert = 1
          }
        }
        if (line > from[i] + 0 && line <= to[i] + 0 \
            && ((handshake && !index(ids[i], " dns-only ")) || cert || timeout || index(ids[i], " " id " ") || (dns != "" && index(ids[i], dns)))) {
          print >expected
          next
        }
      }
      print
    }'
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
# connected to "Connected replication server RS(<id>) <host:port>" (ReplicationServerHandler).
# The number of them, 0 where the search fails
rs_peers() { # <replication server>
  admin_search $1 "$MONITOR_DOMAIN" sub "(cn=Connected replication server*)" 1.1 | grep -c '^dn:' || true
}

assert_rs_mesh() {
  local rs want got failed=
  want=$(($(echo $RS_NODES | wc -w) - 1))
  for rs in $RS_NODES; do
    got=$(rs_peers $rs)
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
    if ! lines=$(replication_errors $n); then
      echo "::error::the errors log of $n could not be read"; failed=1; continue
    fi
    [ -z "$lines" ] || { printf '%s\n' "$lines" >"$OUT/logs/$n.errors"; echo "::error::$n logged replication errors, see logs/$n.errors"; failed=1; }
  done
  assert_rs_mesh || failed=1
  [ -z "$failed" ] || fail "the replicas do not answer the same"
}
