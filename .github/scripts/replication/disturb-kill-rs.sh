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

# Kill and recover one replication-only server under load (#1208). The other RS stays up
# throughout this run, including while the recovered server catches up after the load.
. "$HERE/rs-disturbance.sh"

# A kill leaves DS disconnects (180), RS disconnects (181), and possibly recovery of an
# incomplete changelog record (284). These SYNC errors are expected only inside its window;
# monitor-send errors 66/108 and all other unexpected errors remain failures.
disturb() {
  down_and_up kill 180 181 284
}
