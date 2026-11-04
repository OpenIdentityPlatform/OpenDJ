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

# Shared lifecycle for the independent kill-rs and restart-rs convergence scenarios (#1208).
# Each run takes down one RS-only server while the other continues serving every DS. The
# combined topology also takes down a DS and belongs to a different disturbance.
[ $(echo $RS_ONLY | wc -w) -ge 2 ] \
  || { echo "$DISTURBANCE needs at least two replication servers without data: TOPOLOGY=rs534" >&2; exit 2; }
DISTURBED=$(echo $RS_ONLY | cut -d' ' -f1)
DISTURB_STEPS=1

# Startup after <seconds> and connections to every other RS establish availability. Changelog
# catch-up is checked by the final convergence/data checks after the load; no second RS is
# stopped in this run, so the recovered RS need not catch up before another disturbance.
rs_serves_since() { # <rs> <seconds>
  started_since $1 $2 && [ "$(rs_peers $1)" -eq $(($(echo $RS_NODES | wc -w) - 1)) ]
}

# <kill|restart> [<msgID>...]: open the expected-error window, recover the one RS, and retain
# its actual downtime. A kill needs docker wait before start; a graceful restart gets 120 s
# to stop. The window includes 10 s after startup/peer connections for pending handshakes.
# DS load balancing may move connections later or not at all, so it does not end the window.
down_and_up() {
  local how=$1 rs=$DISTURBED from served left
  shift
  window_open "$@"
  from=$(date +%s)
  # Opening the window reads each server's log and can itself be slow. Check the remaining
  # load immediately before the operation, including when client startup delayed disturb().
  left=$((LOAD_START_MS / 1000 + DURATION - from))
  [ "$left" -ge $((3 * STAT_INTERVAL)) ] \
    || fail "only $left s left in the load of $DURATION s, too late to $how $rs under it"
  echo "    [$SECONDS s] $how $rs"
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
  # Record a completed recovery even if it was too slow, but fail rather than silently
  # accepting a restart that left no useful load. Match restart-ds's three sample intervals.
  left=$((LOAD_START_MS / 1000 + DURATION - $(date +%s)))
  [ "$left" -ge $((3 * STAT_INTERVAL)) ] \
    || fail "$rs recovered too late: only $left s left in the load of $DURATION s"
}

# The image runs its join on every start. Leave the disturbed server as topology_up does
# before the final convergence wait, without requiring catch-up while writes are still active.
settle() {
  wait_until $DISTURB_TIMEOUT "the join of $DISTURBED ends" bootstrap_ended $DISTURBED
}
