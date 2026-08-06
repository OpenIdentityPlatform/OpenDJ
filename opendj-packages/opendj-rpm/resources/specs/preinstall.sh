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

# The instance root may have been relocated with instance.loc (split layout):
# resolve it the way the server scripts (_script-util.sh) do. Empty-file reads
# are tolerated; the result then simply fails the file checks below.
resolve_instance_root() {
    INSTANCE_ROOT="%{_prefix}"
    if [ -f /etc/opendj/instance.loc ] ; then
        read INSTANCE_ROOT < /etc/opendj/instance.loc || true
    elif [ -f "%{_prefix}"/instance.loc ] ; then
        read _loc < "%{_prefix}"/instance.loc || true
        case "$_loc" in
            /*) INSTANCE_ROOT=$_loc ;;
            *)  INSTANCE_ROOT="%{_prefix}"/$_loc ;;
        esac
    fi
}

# Create the dedicated system user/group that runs the service.
getent group opendj >/dev/null || groupadd -r opendj
getent passwd opendj >/dev/null || \
    useradd -r -g opendj -d "%{_prefix}" -s /sbin/nologin -c "OpenDJ Directory Server" opendj

# Record whether the previous package was pre-systemd (shipped no native
# unit): %post then migrates the chkconfig enable state to the unit exactly
# once. Decided here, before the new payload installs the unit file.
rm -f /run/opendj-systemd-migration 2>/dev/null || true
if [ "$1" = "2" ] && [ ! -f /usr/lib/systemd/system/opendj.service ] ; then
    touch /run/opendj-systemd-migration 2>/dev/null || true
fi

if [ "$1" = "2" ] ; then
    resolve_instance_root
    # Upgrade: stop the server if it is running - keyed on a live PID, not on
    # archived-configs, so a freshly set-up instance is stopped too (and a
    # stale pid file does not block the upgrade).
    SERVER_PID=$(cat "$INSTANCE_ROOT/logs/server.pid" 2>/dev/null || true)
    if [ -x "%{_prefix}"/bin/stop-ds ] && [ -n "$SERVER_PID" ] && [ -d "/proc/$SERVER_PID" ] ; then
        echo "Pre Install - upgrade install"
        # Record that it was running so %post restarts it after the upgrade.
        touch "$INSTANCE_ROOT/logs/status"
        if [ -d /run/systemd/system ] ; then
            systemctl stop opendj.service >/dev/null 2>&1 || true
        fi
        if [ -d "/proc/$SERVER_PID" ] ; then
            # Run the tree's own script as the owner of the server *process*
            # (the owner of the files says nothing about who started the
            # server), so the stop is neither an EPERM kill nor a root
            # execution of an opendj-writable script.
            OWNER=$(stat -c '%%U' "/proc/$SERVER_PID" 2>/dev/null || echo root)
            if [ "$OWNER" != root ] && command -v runuser >/dev/null 2>&1 ; then
                runuser -u "$OWNER" -- "%{_prefix}"/bin/stop-ds || true
            else
                "%{_prefix}"/bin/stop-ds || true
            fi
        fi
        # The stop errors above are deliberately swallowed, but the new payload
        # must not be unpacked over a live JVM: verify the stop happened.
        for _i in 1 2 3 4 5 6 7 8 9 10 ; do
            [ -d "/proc/$SERVER_PID" ] || break
            sleep 2
        done
        if [ -d "/proc/$SERVER_PID" ] ; then
            echo "Unable to stop the running OpenDJ server (pid $SERVER_PID); stop it manually and retry the upgrade." >&2
            exit 1
        fi
    else
        # Not running: drop the restart flag a previously failed restart may
        # have left behind, so this upgrade does not start a server the
        # administrator deliberately stopped.
        rm -f "$INSTANCE_ROOT/logs/status"
    fi
fi
