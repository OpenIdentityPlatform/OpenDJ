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

# The disturbance restart-ds of the replication convergence run (#1208), sourced by run.sh: a
# directory server restarts DISTURB_AT seconds into the load, as docker restart or a rolling
# update restarts it, while the others go on taking the load. run_load stops its clients
# before and starts others once it serves again. It is the second directory server, the one
# inject_divergence writes to: in rs534 it runs no replication server, in combined the one it
# runs restarts with it.

DISTURBED=$(echo $DS_NODES | cut -d' ' -f2)

# docker restart sends SIGTERM and kills the server once its timeout is over: 10 s by default,
# less than a server under load may need to stop, and a killed server is another disturbance.
# stop_all gives it 120 s as well. The restart goes to disturbances.txt for the summary, from
# the restart until the server serves again
disturb() {
  local from
  echo "    [$SECONDS s] restarting $DISTURBED"
  from=$(date +%s)
  docker restart -t 120 $DISTURBED >/dev/null
  wait_until $DISTURB_TIMEOUT "$DISTURBED serves again" started_since $DISTURBED $from
  echo "$((from * 1000)) $(($(date +%s) * 1000)) $DISTURBED restart" >>"$OUT/disturbances.txt"
  echo "    [$SECONDS s] $DISTURBED serves again"
}

# After the load, before the wait for convergence: the join of the restarted server, which runs
# again on every start, has ended, as topology_up waits for it. Its container is not waited to
# be healthy again: the probe may not get through while the replicas catch up either
settle() {
  wait_until $DISTURB_TIMEOUT "the join of $DISTURBED ends" bootstrap_ended $DISTURBED
}
