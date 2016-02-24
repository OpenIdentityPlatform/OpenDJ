#! /bin/sh
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
# Copyright 2008 Sun Microsystems, Inc.
# Portions Copyright 2011 ForgeRock AS.

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
