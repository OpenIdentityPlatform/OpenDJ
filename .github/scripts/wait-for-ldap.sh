#!/bin/sh
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

# Waits until the OpenDJ instance under /opt/opendj answers a base search on
# localhost:$1 (default 1389) with the CI test credentials. Exits non-zero if
# the server does not come up within ~60 seconds.

PORT="${1:-1389}"
i=0
while [ "$i" -lt 20 ] ; do
    if /opt/opendj/bin/ldapsearch -h localhost -p "$PORT" -D "cn=Directory Manager" -w password \
        -b "dc=example,dc=com" -s base "(objectClass=*)" 1.1 >/dev/null 2>&1 ; then
        exit 0
    fi
    i=$((i + 1))
    sleep 3
done
echo "OpenDJ did not answer on port $PORT within the timeout" >&2
exit 1
