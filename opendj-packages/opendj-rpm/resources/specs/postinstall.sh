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
        "%{_prefix}"/./upgrade -n --acceptLicense
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
