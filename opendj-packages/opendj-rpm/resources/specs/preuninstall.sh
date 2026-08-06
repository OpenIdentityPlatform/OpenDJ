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

# =================================
# RPM Pre Uninstall Script (%preun)
# =================================

# $1 is 0 for an uninstallation and 1 for an upgrade.

if [ "$1" = "0" ] ; then
    echo "Pre Uninstall - uninstall"
    # Stop and unregister the service.
    if [ -d /run/systemd/system ] ; then
        systemctl stop opendj.service >/dev/null 2>&1 || true
        systemctl disable opendj.service >/dev/null 2>&1 || true
    fi
    # Stop a still-running instance directly (non-systemd hosts) - keyed on
    # the PID file, so an instance that was never upgraded is stopped too.
    # Run the tree's own script as the tree owner, never as root.
    if [ -x "%{_prefix}"/bin/stop-ds ] && [ -f "%{_prefix}"/logs/server.pid ] ; then
        OWNER=$(stat -c '%%U' "%{_prefix}"/bin/stop-ds 2>/dev/null || echo root)
        if [ "$OWNER" != root ] && command -v runuser >/dev/null 2>&1 ; then
            runuser -u "$OWNER" -- "%{_prefix}"/bin/stop-ds || true
        else
            "%{_prefix}"/bin/stop-ds || true
        fi
    fi
    if [ -e /etc/init.d/opendj ] ; then
        /sbin/chkconfig --del opendj || true
    fi
    # Clean up the legacy PID symlink created by the SysV init script.
    [ -h /run/opendj.pid ] && rm -f /run/opendj.pid || true
    [ -h /var/run/opendj.pid ] && rm -f /var/run/opendj.pid || true
fi
