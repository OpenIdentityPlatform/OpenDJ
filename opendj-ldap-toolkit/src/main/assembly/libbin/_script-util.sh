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
# Copyright 2008-2009 Sun Microsystems, Inc.

#
# function that sets the java home
#
set_java_home_and_args() {
  if test -f "${INSTANCE_ROOT}/lib/set-java-home"
  then
    . "${INSTANCE_ROOT}/lib/set-java-home"
  fi
  if test -z "${OPENDJ_JAVA_BIN}"
  then
    if test -z "${OPENDJ_JAVA_HOME}"
    then
      if test -z "${JAVA_BIN}"
      then
        if test -z "${JAVA_HOME}"
        then
          OPENDJ_JAVA_BIN=`which java 2> /dev/null`
          if test ${?} -eq 0
          then
            export OPENDJ_JAVA_BIN
          else
            echo "Please set OPENDJ_JAVA_HOME to the root of a Java 5 (or later) installation"
            echo "or edit the java.properties file and then run the dsjavaproperties script to"
            echo "specify the Java version to be used"
            exit 1
          fi
        else
          OPENDJ_JAVA_BIN="${JAVA_HOME}/bin/java"
          export OPENDJ_JAVA_BIN
        fi
      else
        OPENDJ_JAVA_BIN="${JAVA_BIN}"
        export OPENDJ_JAVA_BIN
      fi
    else
      OPENDJ_JAVA_BIN="${OPENDJ_JAVA_HOME}/bin/java"
      export OPENDJ_JAVA_BIN
    fi
  fi
}

# Explicitly set the PATH, LD_LIBRARY_PATH, LD_PRELOAD, and other important
# system environment variables for security and compatibility reasons.
set_environment_vars() {
  PATH=/bin:/usr/bin
  LD_LIBRARY_PATH=
  LD_LIBRARY_PATH_32=
  LD_LIBRARY_PATH_64=
  LD_PRELOAD=
  LD_PRELOAD_32=
  LD_PRELOAD_64=
  export PATH LD_LIBRARY_PATH LD_LIBRARY_PATH_32 LD_LIBRARY_PATH_64 \
       LD_PRELOAD LD_PRELOAD_32 LD_PRELOAD_64
  SCRIPT_NAME_ARG=-Dcom.forgerock.opendj.ldap.tools.scriptName=${SCRIPT_NAME}
	export SCRIPT_NAME_ARG
}

# Configure the appropriate CLASSPATH.
set_classpath() {
  CLASSPATH=${INSTANCE_ROOT}/classes
  for JAR in "${INSTALL_ROOT}/lib/"*.jar
  do
    CLASSPATH=${CLASSPATH}:${JAR}
  done
  if [ "${INSTALL_ROOT}" != "${INSTANCE_ROOT}" ]
  then
    for JAR in "${INSTANCE_ROOT}/lib/"*.jar
    do
      CLASSPATH=${CLASSPATH}:${JAR}
    done
  fi
  export CLASSPATH
}

if test "${INSTALL_ROOT}" = ""
then
  # Capture the current working directory so that we can change to it later.
  # Then capture the location of this script and the Directory Server instance
  # root so that we can use them to create appropriate paths.
  WORKING_DIR=`pwd`

  cd "`dirname "${0}"`"
  cd ..
  INSTALL_ROOT=`pwd`
  cd "${WORKING_DIR}"
fi

if test "${SCRIPT_UTIL_CMD}" = "set-full-environment"
then
  set_java_home_and_args
  set_environment_vars
  set_classpath
elif test "${SCRIPT_UTIL_CMD}" = "set-java-home-and-args"
then
  set_java_home_and_args
elif test "${SCRIPT_UTIL_CMD}" = "set-environment-vars"
then
  set_environment_vars
elif test "${SCRIPT_UTIL_CMD}" = "set-classpath"
then
  set_classpath
fi

