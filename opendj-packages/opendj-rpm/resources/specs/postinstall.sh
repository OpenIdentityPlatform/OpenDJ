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

# ===============================
# RPM Post Install Script (%post)
# ===============================

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
resolve_instance_root

# Own the install tree - and a split-layout instance - with the service
# account (the account itself is created in %pre, before the payload lands).
# On upgrade this also migrates installations previously owned by root.
chown -R opendj:opendj "%{_prefix}" || true
if [ "$INSTANCE_ROOT" != "%{_prefix}" ] && [ -d "$INSTANCE_ROOT" ] ; then
    chown -R opendj:opendj "$INSTANCE_ROOT" || true
fi

# Honour the documented admin overrides (OPENDJ_JAVA_HOME / OPENDJ_JAVA_BIN /
# OPENDJ_JAVA_ARGS) for the upgrade tool and the restart below, exactly as the
# service itself does via EnvironmentFile=. systemd's EnvironmentFile syntax
# is not shell (no expansion, optional quotes), so extract the known keys
# instead of sourcing the file.
if [ -r /etc/sysconfig/opendj ] ; then
    for _key in OPENDJ_JAVA_HOME OPENDJ_JAVA_BIN OPENDJ_JAVA_ARGS ; do
        _val=$(sed -n "s/^$_key=//p" /etc/sysconfig/opendj | tail -n 1 \
            | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/")
        [ -n "$_val" ] && export "$_key=$_val" || true
    done
fi

# Register the service. Enable only on initial install, so an admin's
# "systemctl disable" survives upgrades ("dnf update" must not re-enable) -
# except on the first upgrade from a pre-systemd package (%pre left a marker):
# there the enable state lives in the chkconfig rc links, which the native
# unit now shadows, so an enabled SysV service is carried over exactly once.
# This must run before "chkconfig --add" below creates fresh rc links.
# systemctl enable works without a booted systemd (chroot/image builds); the
# unit's start condition keeps an unconfigured instance from failing at boot.
if command -v systemctl >/dev/null 2>&1 ; then
    if [ "$1" = "1" ] ; then
        systemctl enable opendj.service >/dev/null 2>&1 || true
    elif [ -f /run/opendj-systemd-migration ] ; then
        if ls /etc/rc.d/rc[2345].d/S??opendj >/dev/null 2>&1 \
            || ls /etc/rc[2345].d/S??opendj >/dev/null 2>&1 ; then
            systemctl enable opendj.service >/dev/null 2>&1 || true
        fi
    fi
fi
rm -f /run/opendj-systemd-migration 2>/dev/null || true
/sbin/chkconfig --add opendj >/dev/null 2>&1 || true
if [ -d /run/systemd/system ] ; then
    systemctl daemon-reload >/dev/null 2>&1 || true
fi

if [ "$1" = "2" ] ; then
    echo "Post Install - upgrade install"
    # Only if the instance has been configured.
    if [ -e "$INSTANCE_ROOT/config/buildinfo" ] && [ -f "$INSTANCE_ROOT/config/config.ldif" ] ; then
        if runuser -u opendj -- "%{_prefix}"/upgrade -n --force --acceptLicense ; then
            # If upgrade is ok, check the server status flag for restart.
            if [ -f "$INSTANCE_ROOT/logs/status" ] ; then
                echo "Restarting server..."
                STARTED=0
                if [ -d /run/systemd/system ] ; then
                    systemctl start opendj.service && STARTED=1 || true
                    # Trust the observable unit state, not just the exit code.
                    if [ "$STARTED" = 1 ] && ! systemctl is-active --quiet opendj.service ; then
                        STARTED=0
                    fi
                else
                    runuser -u opendj -- "%{_prefix}"/bin/start-ds && STARTED=1 || true
                fi
                if [ "$STARTED" = 1 ] ; then
                    rm -f "$INSTANCE_ROOT/logs/status"
                else
                    # Keep the status flag so the next upgrade retries the restart.
                    echo "Server restart failed; see the logs under $INSTANCE_ROOT/logs and start the service manually."
                fi
            fi
        else
            # Upgrade failed; may need manual interaction. Do not fail the transaction.
            echo "Upgrade failed; manual interaction may be required."
            exit 0
        fi
    else
        echo "Instance is not configured."
    fi
else
    echo "Post Install - initial install"
fi

# Add OpenDJ man pages to MANPATH.
MAN_CONFIG_FILE=NOT_SET
if [ -e /etc/man.config ] ; then
    MAN_CONFIG_FILE=/etc/man.config
    MANPATH_DIRECTIVE=MANPATH
elif [ -e /etc/man_db.conf ] ; then
    MAN_CONFIG_FILE=/etc/man_db.conf
    MANPATH_DIRECTIVE=MANDATORY_MANPATH
fi

if [ "$MAN_CONFIG_FILE" != "NOT_SET" ] ; then
    if ! grep -q "$MANPATH_DIRECTIVE.*opendj" "$MAN_CONFIG_FILE" 2>/dev/null ; then
        echo "$MANPATH_DIRECTIVE %{_prefix}/share/man" >> "$MAN_CONFIG_FILE"
    fi
fi
