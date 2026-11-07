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

# Gracefully restart one replication-only server under load (#1208). The other RS stays up
# throughout this run. A separate kill-rs run exercises crash recovery on a fresh topology.
. "$HERE/rs-disturbance.sh"

# A graceful RS restart under load can still leave DS receive failures (180), as in run
# 42190249091. Expect those only inside this restart window; RS disconnects (181) and torn
# changelog recovery (284) remain errors. Docker can briefly stop resolving the container's
# name between its exit and start even after an orderly stop; allow ERROR 51 only for that RS.
disturb() {
  down_and_up restart "dns:$DISTURBED" 180
}
