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

# An orderly stop ends connections with StopMsg. Unexpected disconnects and recovery of a
# torn changelog record remain errors; only the existing handshake allowances apply.
disturb() {
  down_and_up restart
}
