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

# The disturbance restart-rs of the replication convergence run (#1208), sourced by run.sh: the
# replication servers go down in turn while every directory server goes on taking the load, as
# a crash and a rolling update take them down. The first is killed DISTURB_AT seconds into the
# load, the second stopped two thirds of the way in, and each starts again at once. A directory
# server whose replication server goes down connects to the other one and sends it the changes
# it has not got (#1195); the killed one starts on a changelog it did not close, and each one
# catches up from the other. Only in rs534 does a replication server go down alone: in combined
# it takes its directory server down with it, which restart-ds does by a stop, and a kill there
# is another disturbance.

[ $(echo $RS_ONLY | wc -w) -ge 2 ] \
  || { echo "restart-rs takes down replication servers without data, one while another serves the directory servers: TOPOLOGY=rs534" >&2; exit 2; }
KILLED=$(echo $RS_ONLY | cut -d' ' -f1)
RESTARTED=$(echo $RS_ONLY | cut -d' ' -f2)
DISTURBED="$KILLED $RESTARTED"

# The replication server <rs> logged that it started (msgID 135) after the Unix time <seconds>,
# and is connected to every other replication server again, from which the changes it missed
# reach it
rs_serves_since() { # <rs> <seconds>
  started_since $1 $2 && [ "$(rs_peers $1)" -eq $(($(echo $RS_NODES | wc -w) - 1)) ]
}

# <kill|restart> <rs> [<msgID>...]: <rs> goes down and starts again within a window of the errors
# logs that expects the errors of those msgIDs as well, and goes to disturbances.txt from then
# until it serves again. docker kill leaves the container to stop, which docker start has to
# wait for, and docker restart gives the server 120 s to stop, as restart-ds does. The window
# closes 10 s after the server serves again, as the one of restart-ds does. It does not wait
# for directory servers to connect to the server again: they stay with the other one for as
# long as the server misses changes of theirs (filterServersWithAllLocalDSChanges in
# ReplicationBroker), which under the load it does
down_and_up() {
  local how=$1 rs=$2 from served
  shift 2
  echo "    [$SECONDS s] $how $rs"
  window_open "$@"
  from=$(date +%s)
  if [ "$how" = kill ]; then
    docker kill $rs >/dev/null
    docker wait $rs >/dev/null
    docker start $rs >/dev/null
  else
    docker restart -t 120 $rs >/dev/null
  fi
  wait_until $DISTURB_TIMEOUT "$rs serves again" rs_serves_since $rs $from
  served=$(date +%s)
  echo "    [$SECONDS s] $rs serves again"
  sleep 10
  window_close
  echo "$((from * 1000)) $((served * 1000)) $rs $how" >>"$OUT/disturbances.txt"
}

# What a kill leaves behind in the errors logs, all of it at ERROR: the other replication server
# logs 181 for each connection to the killed one that broke (ServerReader, ServerWriter), each
# directory server connected to the killed one 180 for each of its domains (a warning that
# ReplicationBroker.receive logs at ERROR), and the killed one, as it starts, 284 for the
# changelog file it recovers (an info that LogFile logs at ERROR). A stop leaves none of them:
# the server ends each connection with a StopMsg (Session.close), on which a directory server
# logs a warning
disturb() {
  local left
  down_and_up kill $KILLED 180 181 284
  left=$((LOAD_START_MS / 1000 + 2 * DURATION / 3 - $(date +%s)))
  [ "$left" -le 0 ] || sleep $left
  down_and_up restart $RESTARTED
}

# After the load, before the wait for convergence: the joins of both, which run again on every
# start, have ended, as topology_up waits for them
settle() {
  local rs
  for rs in $DISTURBED; do
    wait_until $DISTURB_TIMEOUT "the join of $rs ends" bootstrap_ended $rs
  done
}
