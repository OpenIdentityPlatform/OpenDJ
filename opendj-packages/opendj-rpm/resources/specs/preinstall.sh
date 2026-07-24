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
# Portions Copyright 2026 3A Systems, LLC

# =============================
# RPM Pre Install Script (%pre)
# =============================

# $1 is 1 for an initial installation and 2 for an upgrade.

# Create the dedicated system user/group that runs the service.
getent group opendj >/dev/null || groupadd -r opendj
getent passwd opendj >/dev/null || \
    useradd -r -g opendj -d "%{_prefix}" -s /sbin/nologin -c "OpenDJ Directory Server" opendj

if [ "$1" = "2" ] ; then
    # Upgrade: stop a running, configured instance and record state for restart.
    if [ -e "%{_prefix}"/config/buildinfo ] && [ "$(ls -A "%{_prefix}"/config/archived-configs 2>/dev/null)" ] ; then
        echo "Pre Install - upgrade install"
        if [ -f "%{_prefix}"/logs/server.pid ] ; then
            touch "%{_prefix}"/logs/status
        fi
        if [ -d /run/systemd/system ] ; then
            systemctl stop opendj.service >/dev/null 2>&1 || true
        fi
        [ -x "%{_prefix}"/bin/stop-ds ] && "%{_prefix}"/bin/stop-ds || true
    fi
fi
