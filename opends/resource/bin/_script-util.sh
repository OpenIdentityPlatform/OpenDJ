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
#      Copyright 2008-2009 Sun Microsystems, Inc.

#
# function that sets the java home
#
set_java_home_and_args() {
  if test -f "${INSTANCE_ROOT}/lib/set-java-home"
  then
    . "${INSTANCE_ROOT}/lib/set-java-home"
  fi
  if test -z "${OPENDS_JAVA_BIN}"
  then
    if test -z "${OPENDS_JAVA_HOME}"
    then
      if test -z "${JAVA_BIN}"
      then
        if test -z "${JAVA_HOME}"
        then
          OPENDS_JAVA_BIN=`which java 2> /dev/null`
          if test ${?} -eq 0
          then
            export OPENDS_JAVA_BIN
          else
            echo "Please set OPENDS_JAVA_HOME to the root of a Java 5 (or later) installation"
            echo "or edit the java.properties file and then run the dsjavaproperties script to"
            echo "specify the java version to be used"
            exit 1
          fi
        else
          OPENDS_JAVA_BIN="${JAVA_HOME}/bin/java"
          export OPENDS_JAVA_BIN
        fi
      else
        OPENDS_JAVA_BIN="${JAVA_BIN}"
        export OPENDS_JAVA_BIN
      fi
    else
      OPENDS_JAVA_BIN="${OPENDS_JAVA_HOME}/bin/java"
      export OPENDS_JAVA_BIN
    fi
  fi
}

# Determine whether the detected Java environment is acceptable for use.
test_java() {
  if test -z "${OPENDS_JAVA_ARGS}"
  then
    "${OPENDS_JAVA_BIN}" org.opends.server.tools.InstallDS -t 2> /dev/null
    RESULT_CODE=${?}
    if test ${RESULT_CODE} -eq 13
    then
      # This is a particular error code that means that the Java version is 5
      # but not supported.  Let InstallDS to display the localized error message
      "${OPENDS_JAVA_BIN}" org.opends.server.tools.InstallDS -t
      exit 1
    elif test ${RESULT_CODE} -ne 0
    then
      echo "ERROR:  The detected Java version could not be used.  The detected"
      echo "java binary is:"
      echo "${OPENDS_JAVA_BIN}"
      echo "You must specify the path to a valid Java 5.0 or higher version."
      echo "The procedure to follow is:"
      echo "1. Delete the file ${INSTANCE_ROOT}/lib/set-java-home" if it exists.
      echo "2. Set the environment variable OPENDS_JAVA_HOME to the root of a valid "
      echo "Java 5.0 installation."
      echo "If you want to have specific java  settings for each command line you must"
      echo "follow the steps 3 and 4."
      echo "3. Edit the properties file specifying the java binary and the java arguments"
      echo "for each command line.  The java properties file is located in:"
      echo "${INSTANCE_ROOT}/config/java.properties."
      echo "4. Run the command-line ${INSTANCE_ROOT}/bin/dsjavaproperties"
      exit 1
    fi
  else
    "${OPENDS_JAVA_BIN}" ${OPENDS_JAVA_ARGS} org.opends.server.tools.InstallDS -t 2> /dev/null
    RESULT_CODE=${?}
    if test ${RESULT_CODE} -eq 13
    then
      # This is a particular error code that means that the Java version is 5
      # but not supported.  Let InstallDS to display the localized error message
      "${OPENDS_JAVA_BIN}" org.opends.server.tools.InstallDS -t
      exit 1
    elif test ${RESULT_CODE} -ne 0
    then
      echo "ERROR:  The detected Java version could not be used with the set of java"
      echo "arguments ${OPENDS_JAVA_ARGS}."
      echo "The detected java binary is:"
      echo "${OPENDS_JAVA_BIN}"
      echo "You must specify the path to a valid Java 5.0 or higher version."
      echo "The procedure to follow is:"
      echo "1. Delete the file ${INSTANCE_ROOT}/lib/set-java-home" if it exists.
      echo "2. Set the environment variable OPENDS_JAVA_HOME to the root of a valid "
      echo "Java 5.0 installation."
      echo "If you want to have specific java  settings for each command line you must"
      echo "follow the steps 3 and 4."
      echo "3. Edit the properties file specifying the java binary and the java arguments"
      echo "for each command line.  The java properties file is located in:"
      echo "${INSTANCE_ROOT}/config/java.properties."
      echo "4. Run the command-line ${INSTANCE_ROOT}/bin/dsjavaproperties"
      exit 1
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
  SCRIPT_NAME_ARG=-Dorg.opends.server.scriptName=${SCRIPT_NAME}
	export SCRIPT_NAME_ARG
}

# Configure the appropriate CLASSPATH.
set_classpath() {
  CLASSPATH=${INSTANCE_ROOT}/classes
  for JAR in ${INSTALL_ROOT}/resources/*.jar
  do
    CLASSPATH=${CLASSPATH}:${JAR}
  done
  for JAR in ${INSTALL_ROOT}/lib/*.jar
  do
    CLASSPATH=${CLASSPATH}:${JAR}
  done
  if [ "${INSTANCE_ROOT}" != "${INSTANCE_ROOT}" ]
  then
    for JAR in ${INSTANCE_ROOT}/lib/*.jar
    do
      CLASSPATH=${CLASSPATH}:${JAR}
    done
  fi
  export CLASSPATH
}

isVersionOrHelp() {
  for opt in `echo $*`
  do
	if [ $opt = "-V" ] || [ $opt = "--version" ] ||
		[ $opt = "-H" ] || [ $opt = "--help" ] ||
                [ $opt = "-F" ] || [ $opt = "--fullversion" ]
	then
		return 0
	fi
  done
  return 1
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

if test "${INSTANCE_ROOT}" = ""
then
  if [ -f ${INSTALL_ROOT}/configure ]
  then
    if [ -f /etc/opends/instance.loc ]
    then
      if [ "${SCRIPT_NAME}" = "configure" ]
      then
        isVersionOrHelp $*
	if [ $? -eq 1 ]
	then
          echo "${INSTALL_ROOT}/configure has already been run. Exiting."
          exit 0
	fi
      fi
      INSTANCE_ROOT=`cat /etc/opends/instance.loc`
    else
      if [ "${SCRIPT_NAME}" != "configure" ]
      then
        isVersionOrHelp $*
        if [ $? -eq 1 ]
        then
          echo "No instance found. Run ${INSTALL_ROOT}/configure to create it."
          exit 1
        fi
      fi
    fi
  else
    if [ -f ${INSTALL_ROOT}/instance.loc ]
    then
      if cat ${INSTALL_ROOT}/instance.loc | grep '^/' > /dev/null
      then
         INSTANCE_ROOT=`cat ${INSTALL_ROOT}/instance.loc`
      else
         INSTANCE_ROOT=${INSTALL_ROOT}/`cat ${INSTALL_ROOT}/instance.loc`
      fi
    else
         INSTANCE_ROOT=${INSTALL_ROOT}
    fi
  fi
  if [ -d "${INSTANCE_ROOT}" ]
  then
      CURRENT_DIR=`pwd`
      cd "${INSTANCE_ROOT}"
      INSTANCE_ROOT=`pwd`
      export INSTANCE_ROOT
      cd "${CURRENT_DIR}"
  fi
fi

if test "${SCRIPT_UTIL_CMD}" = "set-full-environment-and-test-java"
then
  set_java_home_and_args
  set_environment_vars
  set_classpath
  test_java
elif test "${SCRIPT_UTIL_CMD}" = "set-full-environment"
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
elif test "${SCRIPT_UTIL_CMD}" = "test-java"
then
  test_java
fi

current_user()
{
USER=`id`
CURRENT_IFS=${IFS}
IFS="()"
set -- ${USER}
echo $2
IFS=${CURRENT_IFS}
}

if [ "${SCRIPT_NAME}" != "configure" ] &&  [ "${SCRIPT_NAME}" != "unconfigure" ]
then
  # Perform check unless it is specified not to do it
  if [ -z "$NO_CHECK" ]
  then
     NO_CHECK=0
  fi
  if [ ${NO_CHECK} -eq 0 ]
  then
      # No check for --version or --help option
      isVersionOrHelp $*
      if [ $? -eq 0 ]
      then
        NO_CHECK=1
      fi
  fi
  if [ ${NO_CHECK} -eq 0 ]
  then
    set_classpath
  # Check instance
      CURRENT_USER="`current_user`"
      if [ "${CHECK_VERSION}" = "yes" ]
      then
	  OPT_CHECK_VERSION="--checkVersion"
      else
	  OPT_CHECK_VERSION=""
      fi
  # Launch the CheckInstance process.
      "${OPENDS_JAVA_BIN}" ${SCRIPT_NAME_ARG} -DINSTALL_ROOT=${INSTALL_ROOT} -DINSTANCE_ROOT=${INSTANCE_ROOT} org.opends.server.tools.configurator.CheckInstance --currentUser ${CURRENT_USER} ${OPT_CHECK_VERSION}
  # return part
      RETURN_CODE=$?
      if [ ${RETURN_CODE} -ne 0 ]
      then
	  exit 1
      fi
  fi
fi
