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
# information: "Portions Copyright [year] [name of copyright owner]".
#
# Copyright 2013-2015 ForgeRock AS.

# =============================
# RPM Pre Install Script (%pre)
# =============================

# If the first argument to %pre is 1, the RPM operation is an initial installation.
# If the argument to %pre is 2, the operation is an upgrade from an existing version to a new one.

if [ "$1" == "1" ]; then
    echo "Pre Install - initial install"
else if [ "$1" == "2" ] ; then
    # Only if the instance has been configured
    if [ -e "%{_prefix}"/config/buildinfo ] && [ "$(ls -A "%{_prefix}"/config/archived-configs)" ] ; then
        echo "Pre Install - upgrade install"
        # If the server is running before upgrade, creates a file flag
        if [ -f "%{_prefix}"/logs/server.pid ] ; then
            touch "%{_prefix}"/logs/status
        fi
        "%{_prefix}"/bin/./stop-ds
        fi
    fi
fi
