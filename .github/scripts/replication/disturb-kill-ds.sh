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

# Crash and recover the same directory server as restart-ds (#1208). Its clients finish
# before the kill and resume after recovery; the other directory servers keep writing.
# In combined the container also runs an RS, so this crashes both roles on the same volume.
DISTURBED=$(echo $DS_NODES | cut -d' ' -f2)
DISTURB_STEPS=1

disturb() {
  local from served
  echo "    [$SECONDS s] killing $DISTURBED"
  # A killed DS leaves a broken DS connection (211). A combined server also breaks DS-to-RS
  # (180) and RS-to-RS (181) connections and can recover a torn changelog record (284). Peers
  # resolving it as an RS can log ERROR 51 while its container's name is unavailable.
  # Allow these only during the crash/recovery window; 66/108 and data/gap checks stay strict.
  case " $RS_NODES " in
    *" $DISTURBED "*) window_open "dns:$DISTURBED" 180 181 211 284 ;;
    *) window_open 211 ;;
  esac
  from=$(date +%s)
  docker kill $DISTURBED >/dev/null
  docker wait $DISTURBED >/dev/null
  docker start $DISTURBED >/dev/null
  wait_until $DISTURB_TIMEOUT "$DISTURBED serves again" started_since $DISTURBED $from
  served=$(date +%s)
  echo "    [$SECONDS s] $DISTURBED serves again"
  # Match restart-ds: pending handshakes can finish after the startup message.
  sleep 10
  window_close
  echo "$((from * 1000)) $((served * 1000)) $DISTURBED kill" >>"$OUT/disturbances.txt"
}

# As for restart-ds, finish the join before the final convergence wait.
settle() {
  wait_until $DISTURB_TIMEOUT "the join of $DISTURBED ends" bootstrap_ended $DISTURBED
}
