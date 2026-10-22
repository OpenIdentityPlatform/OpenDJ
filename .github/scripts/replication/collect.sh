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

# What a replication convergence run (#1090) leaves behind, sourced by run.sh: the summary of
# every run, and the state of every server of a failed one - what #534 could not get from its
# reporter.

# The server id each directory server has in the replication domain of BASE_DN, which the
# samples name the servers by: "Directory server DS(<id>) <host:port>" (ReplicationMonitor)
record_server_ids() {
  local n
  for n in $DS_NODES; do
    admin_search $n "$MONITOR_DOMAIN" one "(objectClass=*)" 1.1 \
      | sed -n 's/^dn: cn=Directory server DS(\([0-9]*\)).*/\1/p' | sed "s/\$/ $n/"
  done >"$OUT/server-ids.txt"
  # the changelog check looks for the changes of each of them
  [ "$(wc -l <"$OUT/server-ids.txt")" -eq "$(echo $DS_NODES | wc -w)" ] \
    || fail "the server id of a directory server is missing, see server-ids.txt"
}

# Every server's container log, logs/, config/ and changelogDb, packed into state.tgz; docker cp
# reads a stopped or killed container as well. Nothing here may fail the run a second time
collect() {
  local n
  for n in $ALL_NODES; do
    docker inspect $n >/dev/null 2>&1 || continue
    mkdir -p "$OUT/state/$n"
    docker logs $n >"$OUT/state/$n/container.log" 2>&1 || true
    docker cp $n:/opt/opendj/data/logs "$OUT/state/$n/" >/dev/null 2>&1 || true
    docker cp $n:/opt/opendj/data/config "$OUT/state/$n/" >/dev/null 2>&1 || true
    docker cp $n:/opt/opendj/data/changelogDb "$OUT/state/$n/" >/dev/null 2>&1 || true
  done
  [ ! -d "$OUT/state" ] || { tar czf "$OUT/state.tgz" -C "$OUT" state 2>/dev/null && rm -rf "$OUT/state"; } || true
}

# The summary of the run, into $OUT/summary.md and, in GitHub Actions, the job summary
summarize() { # <result>
  {
    echo "### Replication convergence ($TOPOLOGY): $1"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| servers | $ALL_NODES |"
    echo "| load | $DURATION s on each directory server, at most $LOAD_MOD_RATE modifications and $LOAD_ADD_RATE adds or deletes a second |"
    echo "| seeded users | $SAMPLE_DATA |"
    if [ -s "$OUT/disturbances.txt" ]; then
      awk -v start="${LOAD_START_MS:-0}" '{ printf "| disturbance | %s of %s %d s into the load, serving again %d s later |\n",
                                            $4, $3, ($1 - start) / 1000, ($2 - $1) / 1000 }' "$OUT/disturbances.txt"
    elif [ "$DISTURBANCE" != none ]; then
      echo "| disturbance | $DISTURBANCE at $DISTURB_AT s into the load, not completed |"
    fi
    echo "| converged | ${CONVERGED_IN:--} s after the load |"
    if [ -s "$OUT/samples.txt" ]; then
      awk '{ t[$1] = 1 } END { for (s in t) { n++; if (!min || s < min) min = s; if (s > max) max = s }
             printf "| samples of the monitor | %d, one every %d s |\n", n, (n > 1 ? (max - min) / (n - 1) : 0) }' "$OUT/samples.txt"
    fi
    if [ -s "$OUT/samples.txt" ] && [ -s "$OUT/server-ids.txt" ]; then
      echo
      echo "Peaks during the load, over every replication server (a restart resets the counters, so these are maxima of the samples, not sums), and the connections to a replication server again after the topology came up, whatever made them:"
      echo
      echo "| directory server | peak missing-changes | peak approximate-delay (s) | reconnections |"
      echo "|---|---|---|---|"
      awk 'FILENAME == ARGV[1] { reconnections[$1] = $2; next }
           FILENAME == ARGV[2] { name[$1] = $2; next }
           ($3 in name) { if ($4 > m[$3]) m[$3] = $4; if ($5 > d[$3]) d[$3] = $5; seen[$3] = 1 }
           END { for (id in seen) printf "| %s | %d | %d | %s |\n", name[id], m[id], d[id], \
                   (name[id] in reconnections) ? reconnections[name[id]] : "-" }' \
        "$([ -f "$OUT/reconnections.txt" ] && echo "$OUT/reconnections.txt" || echo /dev/null)" \
        "$OUT/server-ids.txt" "$OUT/samples.txt" | sort
    fi
    if ls "$OUT"/changelog/*.txt >/dev/null 2>&1; then
      echo
      echo "Changes of the load missing from the changelog of each replication server (gaps in the sequence numbers of a replica; which ones in changelog/):"
      echo
      echo '```'
      for f in "$OUT"/changelog/*.txt; do
        grep -E ' [1-9][0-9]* missing$|cannot be read|encrypted|no domain' "$f" | sed "s/^/$(basename "$f" .txt): /"
      done | grep . || echo "none"
      echo '```'
    fi
    if ls "$OUT"/load/*.csv >/dev/null 2>&1; then
      echo
      echo "| client | average ops/s | intervals |"
      echo "|---|---|---|"
      for f in "$OUT"/load/*.csv; do
        awk -F, -v c="$(basename "$f" .csv)" '$1 ~ /^[0-9.]+$/ { rows++; avg = $3 } END { printf "| %s | %s | %d |\n", c, avg, rows }' "$f"
      done
    fi
    echo
    echo '```'
    sort -rn <<<"${PHASE_TIMES%$'\n'}"
    echo '```'
  } >"$OUT/summary.md" 2>/dev/null || true
  [ -z "${GITHUB_STEP_SUMMARY:-}" ] || cat "$OUT/summary.md" >>"$GITHUB_STEP_SUMMARY" || true
}
