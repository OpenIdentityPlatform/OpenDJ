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

# =================================
# RPM Pre Uninstall Script (%preun)
# =================================

# If the first argument to %preun and %postun is 0, the action is uninstallation.
# If the first argument to %preun and %postun is 1, the action is an upgrade.

if [ "$1" == "0" ] ; then
    echo "Pre Uninstall - uninstall"
    # Unlink the symlink to the process ID.
    test -h "/var/run/opendj.pid" && unlink /var/run/opendj.pid
    # Only if the instance has been configured
    if [ -e "%{_prefix}"/config/buildinfo ] && [ "$(ls -A "%{_prefix}"/config/archived-configs)" ] ; then
	   "%{_prefix}"/bin/./stop-ds
    fi

    if [ -e /etc/init.d/opendj ] ; then
        # Deletes the service.
        /sbin/chkconfig --del opendj
    fi
else if [ "$1" == "1" ] ; then
    echo "Pre Uninstall - upgrade uninstall"
    fi
fi
