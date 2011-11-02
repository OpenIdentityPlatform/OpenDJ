#! /bin/sh
#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/CDDLv1_0.txt
# or http://forgerock.org/license/CDDLv1.0.html.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2008 Sun Microsystems, Inc.
#      Portions Copyright 2011 ForgeRock AS

. /lib/svc/share/smf_include.sh

STARTDS="/usr/opendj/bin/start-ds --exec"
STOPDS="/usr/opendj/bin/stop-ds --exec"
TEST="/usr/bin/test"

case "$1" in
'start')
    OPENDJ_JAVA_HOME="${OPENDJ_JAVA_HOME}" \
    OPENDJ_JAVA_ARGS="${OPENDJ_JAVA_ARGS}" ${STARTDS}
    RES=$?
    if ${TEST} ${RES} -ne 0
    then
        if ${TEST} ${RES} -eq 98
        then
# Already started
            exit ${SMF_EXIT_OK}
        else
            exit ${SMF_EXIT_ERR_FATAL}
        fi
    fi
    ;;

'stop')
    OPENDJ_JAVA_HOME="${OPENDJ_JAVA_HOME}" ${STOPDS}
    if ${TEST} $? -ne 0
    then
        exit ${SMF_EXIT_ERR_FATAL}
    fi
    ;;
*)
    echo "Usage: $0 { start | stop }"
    exit ${SMF_EXIT_ERR_FATAL}
    ;;
esac
exit ${SMF_EXIT_OK}
