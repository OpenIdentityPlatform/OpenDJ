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
# trunk/opends/resource/legal-notices/OpenDS.LICENSE
# or https://OpenDS.dev.java.net/OpenDS.LICENSE.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2008 Sun Microsystems, Inc.

. /lib/svc/share/smf_include.sh

STARTDS="/usr/opends/bin/start-ds --exec"
STOPDS="/usr/opends/bin/stop-ds --exec"
TEST="/usr/bin/test"

case "$1" in
'start')
    OPENDS_JAVA_HOME="${OPENDS_JAVA_HOME}" \
    OPENDS_JAVA_ARGS="${OPENDS_JAVA_ARGS}" ${STARTDS}
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
    OPENDS_JAVA_HOME="${OPENDS_JAVA_HOME}" ${STOPDS}
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
