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
    # Upgrade: stop the server if it is running - keyed on the PID file, not
    # on archived-configs, so a freshly set-up instance is stopped too.
    if [ -x "%{_prefix}"/bin/stop-ds ] && [ -f "%{_prefix}"/logs/server.pid ] ; then
        echo "Pre Install - upgrade install"
        # Record that it was running so %post restarts it after the upgrade.
        touch "%{_prefix}"/logs/status
        if [ -d /run/systemd/system ] ; then
            systemctl stop opendj.service >/dev/null 2>&1 || true
        fi
        # Run the tree's own script as the tree owner, never as root: the tree
        # is opendj-writable on installs with the dedicated service account
        # (old root-owned installs keep running the script as root).
        OWNER=$(stat -c '%%U' "%{_prefix}"/bin/stop-ds 2>/dev/null || echo root)
        if [ "$OWNER" != root ] && command -v runuser >/dev/null 2>&1 ; then
            runuser -u "$OWNER" -- "%{_prefix}"/bin/stop-ds || true
        else
            "%{_prefix}"/bin/stop-ds || true
        fi
    fi
fi
