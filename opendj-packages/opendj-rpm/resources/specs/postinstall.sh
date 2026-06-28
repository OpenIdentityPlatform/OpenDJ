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

# Ensure the service account exists and owns the install tree (also migrates
# installations that were previously owned by root on upgrade).
getent group opendj >/dev/null || groupadd -r opendj
getent passwd opendj >/dev/null || \
    useradd -r -g opendj -d "%{_prefix}" -s /sbin/nologin -c "OpenDJ Directory Server" opendj
chown -R opendj:opendj "%{_prefix}" || true

# Pin Java for the service via OpenDJ's own config, using a STABLE symlink (not a
# version-specific path) so a JRE upgrade/reinstall does not break the service.
# Only touch the shipped placeholder, never an admin-edited value.
JAVA_PROPS="%{_prefix}"/config/java.properties
JH=/usr/lib/jvm/jre
[ -x "$JH/bin/java" ] || JH=$(dirname "$(dirname "$(command -v java 2>/dev/null)")" 2>/dev/null)
if [ -n "$JH" ] && [ -x "$JH/bin/java" ] && grep -q '^default.java-home=\$JAVA_HOME' "$JAVA_PROPS" 2>/dev/null ; then
    sed -i "s|^default.java-home=.*|default.java-home=$JH|" "$JAVA_PROPS"
fi

# Register the service: prefer systemd, fall back to chkconfig/SysV.
if [ -d /run/systemd/system ] ; then
    systemctl daemon-reload >/dev/null 2>&1 || true
    systemctl enable opendj.service >/dev/null 2>&1 || true
else
    /sbin/chkconfig --add opendj || true
fi

if [ "$1" = "2" ] ; then
    echo "Post Install - upgrade install"
    # Only if the instance has been configured.
    if [ -e "%{_prefix}"/config/buildinfo ] && [ "$(ls -A "%{_prefix}"/config/archived-configs 2>/dev/null)" ] ; then
        if runuser -u opendj -- "%{_prefix}"/upgrade -n --force --acceptLicense ; then
            # If upgrade is ok, check the server status flag for restart.
            if [ -f "%{_prefix}"/logs/status ] ; then
                echo "Restarting server..."
                if [ -d /run/systemd/system ] ; then
                    systemctl start opendj.service || true
                else
                    runuser -u opendj -- "%{_prefix}"/bin/start-ds || true
                fi
                rm -f "%{_prefix}"/logs/status
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
