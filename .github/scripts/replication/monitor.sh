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

# The replication monitor of the convergence run (#1090), sourced by run.sh: how far behind
# each directory server is, seen from each replication server, sampled during the load, and
# the wait for the topology to converge once it stops.

# One line per directory server known to each replication server - connected to it, or to a
# peer of it - in the replication domain of BASE_DN: <epoch> <replication server> <server id>
# <missing-changes> <approximate-delay>. Both kinds of entries are named "Connected directory
# server DS(<id>) ..." (DataServerHandler, LightweightServerHandler)
# The replication servers are asked at once: an ldapsearch starts a JVM, which takes tens of
# seconds on a runner the load keeps busy, and asked one after the other they took a sample
# every minute or so instead of every five seconds
sample() {
  local rs now dir pid pids=
  now=$(date +%s)
  dir=$(mktemp -d "$OUT/sample.XXXXXX")
  for rs in $RS_NODES; do
    admin_search $rs "$MONITOR_DOMAIN" sub "(cn=Connected directory server*)" \
        server-id missing-changes approximate-delay \
      | awk -v t=$now -v rs=$rs '
          tolower($1) == "server-id:" { id = $2 }
          tolower($1) == "missing-changes:" { m = $2 }
          tolower($1) == "approximate-delay:" { d = $2 }
          /^$/ && id != "" { print t, rs, id, m, d; id = "" }
          END { if (id != "") print t, rs, id, m, d }' >"$dir/$rs" &
    pids="$pids $!"
  done
  # a replication server that does not answer only leaves its lines out, which
  # converged_once does not take for a zero
  for pid in $pids; do
    wait $pid || true
  done
  for rs in $RS_NODES; do
    cat "$dir/$rs"
  done
  rm -rf "$dir"
}

# Converged once every replication server knows every directory server and none of them misses
# a change. An entry that is not there is not a zero: right after a server connects again its
# entry is missing until the replication server has heard of it
converged_once() {
  local s rs want
  s=$(sample) || return 1
  want=$(echo $DS_NODES | wc -w)
  for rs in $RS_NODES; do
    [ "$(echo "$s" | awk -v rs=$rs '$2 == rs { print $3 }' | sort -u | wc -l)" -eq "$want" ] || return 1
  done
  echo "$s" | awk '$4 != 0 { behind = 1 } END { exit behind }'
}

# Three converged samples in a row: the monitor of a replication server is refreshed now and
# then, not on every change. Sets CONVERGED_IN to the seconds it took
wait_converged() {
  local start=$SECONDS hits=0
  until [ $hits -ge 3 ]; do
    if converged_once; then hits=$((hits + 1)); else hits=0; fi
    if [ $((SECONDS - start)) -ge "$QUIESCE_TIMEOUT" ]; then
      echo "::group::the monitor when the wait timed out (epoch, replication server, server id, missing changes, delay)"
      sample || true
      echo "::endgroup::"
      fail "the topology did not converge within $QUIESCE_TIMEOUT s"
    fi
    sleep 5
  done
  CONVERGED_IN=$((SECONDS - start))
}

# Samples every 5 s in the background into $OUT/samples.txt, for the peaks of the run; a
# sample that fails - a server restarting - is only a gap
sampler_start() {
  ( while :; do sample >>"$OUT/samples.txt" 2>/dev/null || true; sleep 5; done ) &
  SAMPLER=$!
}

sampler_stop() {
  if [ -n "${SAMPLER:-}" ]; then
    # waited for, so that the shell does not report the job it killed
    kill $SAMPLER 2>/dev/null || true
    wait $SAMPLER 2>/dev/null || true
  fi
  SAMPLER=
}
