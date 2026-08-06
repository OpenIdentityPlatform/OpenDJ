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

# Own the install tree with the service account (the account itself is created
# in %pre, before the payload lands). On upgrade this also migrates
# installations that were previously owned by root.
chown -R opendj:opendj "%{_prefix}" || true

# Honour the documented admin overrides (OPENDJ_JAVA_HOME / OPENDJ_JAVA_BIN /
# OPENDJ_JAVA_ARGS) for the upgrade tool and the restart below, exactly as the
# service itself does via EnvironmentFile=.
if [ -r /etc/sysconfig/opendj ] ; then
    . /etc/sysconfig/opendj || true
    export OPENDJ_JAVA_HOME OPENDJ_JAVA_BIN OPENDJ_JAVA_ARGS
fi

# Register the service. Enable only on initial install, so an admin's
# "systemctl disable" survives upgrades ("dnf update" must not re-enable).
# systemctl enable works without a booted systemd (chroot/image builds); the
# unit's ConditionPathExists keeps an unconfigured instance from failing at
# boot. chkconfig --add is idempotent and keeps the SysV fallback registered.
if [ "$1" = "1" ] && command -v systemctl >/dev/null 2>&1 ; then
    systemctl enable opendj.service >/dev/null 2>&1 || true
fi
/sbin/chkconfig --add opendj >/dev/null 2>&1 || true
if [ -d /run/systemd/system ] ; then
    systemctl daemon-reload >/dev/null 2>&1 || true
fi

if [ "$1" = "2" ] ; then
    echo "Post Install - upgrade install"
    # Only if the instance has been configured.
    if [ -e "%{_prefix}"/config/buildinfo ] && [ -f "%{_prefix}"/config/config.ldif ] ; then
        if runuser -u opendj -- "%{_prefix}"/upgrade -n --force --acceptLicense ; then
            # If upgrade is ok, check the server status flag for restart.
            if [ -f "%{_prefix}"/logs/status ] ; then
                echo "Restarting server..."
                STARTED=0
                if [ -d /run/systemd/system ] ; then
                    systemctl start opendj.service && STARTED=1 || true
                else
                    runuser -u opendj -- "%{_prefix}"/bin/start-ds && STARTED=1 || true
                fi
                if [ "$STARTED" = 1 ] ; then
                    rm -f "%{_prefix}"/logs/status
                else
                    # Keep the status flag so the next upgrade retries the restart.
                    echo "Server restart failed; see the logs under %{_prefix}/logs and start the service manually."
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
