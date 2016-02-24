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

# ===============================
# RPM Post Install Script (%post)
# ===============================

# The arguments to a %post are 1 and 2 for a new installation
#  and upgrade, respectively. (%pre and %post aren't executed during
#  an uninstallation.)

# Registers the service
/sbin/chkconfig --add opendj

# Symlinks to process ID
test -h "/var/run/opendj.pid" || ln -s /opt/opendj/logs/server.pid /var/run/opendj.pid

if [ "$1" == "1" ] ; then
    echo "Post Install - initial install"
else if [ "$1" == "2" ] ; then
    echo "Post Install - upgrade install"
    # Only if the instance has been configured
    if [ -e "%{_prefix}"/config/buildinfo ] && [ "$(ls -A "%{_prefix}"/config/archived-configs)" ] ; then
        "%{_prefix}"/./upgrade -n --force --acceptLicense
        # If upgrade is ok, checks the server status flag for restart
        if [ "$?" == "0" ] && [ -f "%{_prefix}"/logs/status ] ; then
            echo ""
            echo "Restarting server..."
            "%{_prefix}"/./bin/start-ds
            echo ""
            rm -f "%{_prefix}"/logs/status
        fi

        # Upgrade fails, needs user interaction (eg. manual mode)
        if [ "$?" == "2" ] ; then
            exit "0"
        fi
    else
        echo "Instance is not configured. Upgrade aborted."
        exit -1
    fi
    fi
fi

MAN_CONFIG_FILE=NOT_SET
# Add OpenDJ man pages to MANPATH
if [ -e /etc/man.config ] ; then
    MAN_CONFIG_FILE=/etc/man.config
    MANPATH_DIRECTIVE=MANPATH
elif [ -e /etc/man_db.conf ] ; then
    MAN_CONFIG_FILE=/etc/man_db.conf
    MANPATH_DIRECTIVE=MANDATORY_MANPATH
fi

if [ $MAN_CONFIG_FILE != "NOT_SET" ] ; then
    grep -q "$MANPATH_DIRECTIVE.*opendj" $MAN_CONFIG_FILE 2> /dev/null
    if [ $? -ne 0 ]; then
        echo "$MANPATH_DIRECTIVE %{_prefix}/share/man" >> $MAN_CONFIG_FILE
    fi
fi
