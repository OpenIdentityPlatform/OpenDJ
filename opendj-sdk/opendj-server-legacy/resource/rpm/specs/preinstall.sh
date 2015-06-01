#!/bin/sh
#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
# or http://forgerock.org/license/CDDLv1.0.html.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# legal-notices/CDDLv1_0.txt.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#      Copyright 2013-2015 ForgeRock AS.

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
