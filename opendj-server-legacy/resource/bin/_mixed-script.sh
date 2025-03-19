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
# Copyright 2008-2010 Sun Microsystems, Inc.
# Portions Copyright 2011-2016 ForgeRock AS.


# This script is used to invoke processes that might be run on server or
# in client mode (depending on the state of the server and the arguments
# passed).  It should not be invoked directly by end users.
if test -z "${OPENDJ_INVOKE_CLASS}"
then
  echo "ERROR:  OPENDJ_INVOKE_CLASS environment variable is not set."
  exit 1
fi


# Capture the current working directory so that we can change to it later.
# Then capture the location of this script and the Directory Server install
# and instance  root so that we can use them to create appropriate paths.
WORKING_DIR=`pwd`

cd "`dirname "${0}"`"
SCRIPT_DIR=`pwd`

cd ..
INSTALL_ROOT=`pwd`
export INSTALL_ROOT

cd "${WORKING_DIR}"

ORIGIN_SCRIPT_NAME=${SCRIPT_NAME}
SCRIPT_NAME=${ORIGIN_SCRIPT_NAME}.online
for opt in `echo $*`
do
  `echo ${opt}|grep -iq -- --offline`
  ret_code=$?
  if test ${ret_code} -eq 0
  then
    SCRIPT_NAME=${ORIGIN_SCRIPT_NAME}.offline
  fi
done
export SCRIPT_NAME

# Set environment variables
SCRIPT_UTIL_CMD=set-full-environment
export SCRIPT_UTIL_CMD
.  "${INSTALL_ROOT}/lib/_script-util.sh"
RETURN_CODE=$?
if test ${RETURN_CODE} -ne 0
then
  exit ${RETURN_CODE}
fi

SCRIPT_NAME_ARG=-Dorg.opends.server.scriptName=${ORIGIN_SCRIPT_NAME}
export SCRIPT_NAME_ARG

"${OPENDJ_JAVA_BIN}" ${OPENDJ_JAVA_ARGS} ${SCRIPT_ARGS} ${SCRIPT_NAME_ARG} "${OPENDJ_INVOKE_CLASS}" \
   --configFile "${INSTANCE_ROOT}/config/config.ldif" "${@}"
