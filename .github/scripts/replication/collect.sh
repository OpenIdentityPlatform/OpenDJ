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
    echo "| load | $DURATION s on each directory server, at most $LOAD_MOD_RATE modifications and $LOAD_ADD_RATE adds a second |"
    echo "| seeded users | $SAMPLE_DATA |"
    echo "| converged | ${CONVERGED_IN:--} s after the load |"
    if [ -s "$OUT/samples.txt" ] && [ -s "$OUT/server-ids.txt" ]; then
      echo
      echo "Peaks during the load, over every replication server (a restart resets the counters, so these are maxima of the samples, not sums):"
      echo
      echo "| directory server | peak missing-changes | peak approximate-delay (s) |"
      echo "|---|---|---|"
      awk 'NR == FNR { name[$1] = $2; next }
           ($3 in name) { if ($4 > m[$3]) m[$3] = $4; if ($5 > d[$3]) d[$3] = $5; seen[$3] = 1 }
           END { for (id in seen) printf "| %s | %d | %d |\n", name[id], m[id], d[id] }' \
        "$OUT/server-ids.txt" "$OUT/samples.txt" | sort
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
