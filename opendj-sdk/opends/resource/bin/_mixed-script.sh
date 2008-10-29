#!/bin/sh
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


# This script is used to invoke processes that might be run on server or
# in client mode (depending on the state of the server and the arguments
# passed).  It should not be invoked directly by end users.
if test -z "${OPENDS_INVOKE_CLASS}"
then
  echo "ERROR:  OPENDS_INVOKE_CLASS environment variable is not set."
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

OLD_SCRIPT_NAME=${SCRIPT_NAME}
SCRIPT_NAME=${OLD_SCRIPT_NAME}.online
export SCRIPT_NAME

# We keep this values to reset the environment before calling _script-util.sh
# for the second time.
ORIGINAL_JAVA_ARGS=${OPENDS_JAVA_ARGS}
ORIGINAL_JAVA_HOME=${OPENDS_JAVA_HOME}
ORIGINAL_JAVA_BIN=${OPENDS_JAVA_BIN}

# Set environment variables
SCRIPT_UTIL_CMD=set-full-environment
export SCRIPT_UTIL_CMD
.  "${INSTALL_ROOT}/lib/_script-util.sh"
RETURN_CODE=$?
if test ${RETURN_CODE} -ne 0
then
  exit ${RETURN_CODE}
fi

MUST_CALL_AGAIN="false"

SCRIPT_NAME_ARG=-Dorg.opends.server.scriptName=${OLD_SCRIPT_NAME}
export SCRIPT_NAME_ARG

# Check whether is local or remote
"${OPENDS_JAVA_BIN}" ${OPENDS_JAVA_ARGS} ${SCRIPT_NAME_ARG} "${OPENDS_INVOKE_CLASS}" \
     --configClass org.opends.server.extensions.ConfigFileHandler \
     --configFile "${INSTANCE_ROOT}/config/config.ldif" --testIfOffline "${@}"  
EC=${?}
if test ${EC} -eq 51
then
  # Set the original values that the user had on the environment in order to be
  # sure that the script works with the proper arguments (in particular
  # if the user specified not to overwrite the environment).
  OPENDS_JAVA_ARGS=${ORIGINAL_JAVA_ARGS}
  OPENDS_JAVA_HOME=${ORIGINAL_JAVA_HOME}
  OPENDS_JAVA_BIN=${ORIGINAL_JAVA_BIN}

  # Set the environment to use the offline properties
  SCRIPT_NAME=${OLD_SCRIPT_NAME}.offline
  export SCRIPT_NAME
  .  "${INSTALL_ROOT}/lib/_script-util.sh"
  RETURN_CODE=$?
  if test ${RETURN_CODE} -ne 0
  then
    exit ${RETURN_CODE}
  fi
  MUST_CALL_AGAIN="true"
else
  if test ${EC} -eq 52
  then
    MUST_CALL_AGAIN="true"
  else
    # This is likely a problem with the provided arguments.
    exit ${EC}
  fi
fi

if test ${MUST_CALL_AGAIN} = "true"
then
  SCRIPT_NAME_ARG=-Dorg.opends.server.scriptName=${OLD_SCRIPT_NAME}
  export SCRIPT_NAME_ARG
  
  # Launch the server utility.
  "${OPENDS_JAVA_BIN}" ${OPENDS_JAVA_ARGS} ${SCRIPT_NAME_ARG} "${OPENDS_INVOKE_CLASS}" \
       --configClass org.opends.server.extensions.ConfigFileHandler \
       --configFile "${INSTANCE_ROOT}/config/config.ldif" "${@}"
fi
