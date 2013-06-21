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
# file and include the License file at legal-notices/CDDLv1_0.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2006-2008 Sun Microsystems, Inc.


# This script is used to invoke various client-side processes.  It should not
# be invoked directly by end users.
if test -z "${OPENDJ_INVOKE_CLASS}"
then
  echo "ERROR:  OPENDJ_INVOKE_CLASS environment variable is not set."
  exit 1
fi


# Capture the current working directory so that we can change to it later.
# Then capture the location of this script and the Directory Server install
# root so that we can use them to create appropriate paths.
WORKING_DIR=`pwd`

cd "`dirname "${0}"`"
SCRIPT_DIR=`pwd`

cd ..
INSTALL_ROOT=`pwd`
export INSTALL_ROOT

cd "${WORKING_DIR}"


# Set environment variables
SCRIPT_UTIL_CMD=set-full-environment
export SCRIPT_UTIL_CMD

.  "${INSTALL_ROOT}/lib/_script-util.sh"
RETURN_CODE=$?
if test ${RETURN_CODE} -ne 0
then
  exit ${RETURN_CODE}
fi

# Launch the appropriate client utility.
"${OPENDJ_JAVA_BIN}" ${OPENDJ_JAVA_ARGS} ${SCRIPT_NAME_ARG} "${OPENDJ_INVOKE_CLASS}" "${@}"
