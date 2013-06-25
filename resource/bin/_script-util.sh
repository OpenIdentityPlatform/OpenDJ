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
#      Copyright 2008-2010 Sun Microsystems, Inc.
#      Portions Copyright 2010-2013 ForgeRock AS

#
# Display an error message
#
display_java_not_found_error() {
  echo "Please set OPENDJ_JAVA_HOME to the root of a Java 6 update 10 (or higher) installation"
  echo "or edit the java.properties file and then run the dsjavaproperties script to"
  echo "specify the Java version to be used"
}

#
# function that tests the JAVA_HOME env variable.
#
test_java_home() {
  if test -z "${JAVA_HOME}"
  then
    display_java_not_found_error
    exit 1
  else
    OPENDJ_JAVA_BIN="${JAVA_HOME}/bin/java"
    if test -f "${OPENDJ_JAVA_BIN}"
    then
      export OPENDJ_JAVA_BIN
    else
      display_java_not_found_error
      exit 1
    fi
  fi
}

#
# function that tests the JAVA_BIN env variable.
#
test_java_bin() {
  if test -z "${JAVA_BIN}"
  then
    test_java_home
  else
    OPENDJ_JAVA_BIN="${JAVA_BIN}"
    if test -f "${OPENDJ_JAVA_BIN}"
    then
      export OPENDJ_JAVA_BIN
    else
      test_java_home
    fi
  fi
}

#
# function that tests the java executable in the PATH env variable.
#
test_java_path() {
  OPENDJ_JAVA_BIN=`which java 2> /dev/null`
  if test -f "${OPENDJ_JAVA_BIN}"
  then
    export OPENDJ_JAVA_BIN
  else
    test_java_bin
  fi
}

#
# function that tests legacy OPENDS_JAVA_HOME env variable.
#
test_opends_java_home() {
  if test -z "${OPENDS_JAVA_HOME}"
  then
    test_java_path
  else
    OPENDJ_JAVA_BIN="${OPENDS_JAVA_HOME}/bin/java"
    if test -f "${OPENDJ_JAVA_BIN}"
    then
      export OPENDJ_JAVA_BIN
    else
      test_java_path
    fi
  fi
}

#
# function that tests the OPENDJ_JAVA_HOME env variable.
#
test_opendj_java_home() {
  if test -z "${OPENDJ_JAVA_HOME}"
  then
    test_opends_java_home
  else
    OPENDJ_JAVA_BIN="${OPENDJ_JAVA_HOME}/bin/java"
    if test -f "${OPENDJ_JAVA_BIN}"
    then
      export OPENDJ_JAVA_BIN
    else
      test_java_path
    fi
  fi
}

#
# function that tests the OPENDJ_JAVA_BIN env variable.
#
test_opendj_java_bin() {
  if test -z "${OPENDJ_JAVA_BIN}"
  then
    # Check for legacy OPENDS_JAVA_BIN
    if test -z "${OPENDS_JAVA_BIN}"
    then
      test_opendj_java_home
    else
      if test -f "${OPENDS_JAVA_BIN}"
      then
        OPENDJ_JAVA_BIN="${OPENDS_JAVA_BIN}"
        export OPENDJ_JAVA_BIN
      else
        test_opendj_java_home
      fi
    fi
  else
    if test -f "${OPENDJ_JAVA_BIN}"
    then
      export OPENDJ_JAVA_BIN
    else
      test_opends_java_home
    fi
  fi
}

#
# function that sets the java home
#
set_java_home_and_args() {
  if test -f "${INSTANCE_ROOT}/lib/set-java-home"
  then
    . "${INSTANCE_ROOT}/lib/set-java-home"
  fi
  test_opendj_java_bin
}

# Function that sets OPENDJ_JAVA_ARGS if not yet set but OPENDS_JAVA_ARGS is.
test_java_args() {
  if test -z "${OPENDJ_JAVA_ARGS}"
  then
    if test -n "${OPENDS_JAVA_ARGS}"
    then
      OPENDJ_JAVA_ARGS="${OPENDS_JAVA_ARGS}"
      export OPENDJ_JAVA_ARGS
    fi
  fi
}

# Determine whether the detected Java environment is acceptable for use.
test_java() {
  if test -z "${OPENDJ_JAVA_ARGS}"
  then
    "${OPENDJ_JAVA_BIN}" org.opends.server.tools.InstallDS -t 2> /dev/null
    RESULT_CODE=${?}
    if test ${RESULT_CODE} -eq 13
    then
      # This is a particular error code that means that the Java version is 6
      # but not supported.  Let InstallDS to display the localized error message
      "${OPENDJ_JAVA_BIN}" org.opends.server.tools.InstallDS -t
      exit 1
    elif test ${RESULT_CODE} -ne 0
    then
      echo "ERROR:  The detected Java version could not be used.  The detected"
      echo "Java binary is:"
      echo "${OPENDJ_JAVA_BIN}"
      echo "You must specify the path to a valid Java 6.0 update 10 or higher version."
      echo "The procedure to follow is:"
      echo "1. Delete the file ${INSTANCE_ROOT}/lib/set-java-home" if it exists.
      echo "2. Set the environment variable OPENDJ_JAVA_HOME to the root of a valid "
      echo "Java 6.0 installation."
      echo "If you want to have specific Java settings for each command line you must"
      echo "follow the steps 3 and 4."
      echo "3. Edit the properties file specifying the Java binary and the Java arguments"
      echo "for each command line.  The Java properties file is located in:"
      echo "${INSTANCE_ROOT}/config/java.properties."
      echo "4. Run the command-line ${INSTANCE_ROOT}/bin/dsjavaproperties"
      exit 1
    fi
  else
    "${OPENDJ_JAVA_BIN}" ${OPENDJ_JAVA_ARGS} org.opends.server.tools.InstallDS -t 2> /dev/null
    RESULT_CODE=${?}
    if test ${RESULT_CODE} -eq 13
    then
      # This is a particular error code that means that the Java version is 6
      # but not supported.  Let InstallDS to display the localized error message
      "${OPENDJ_JAVA_BIN}" org.opends.server.tools.InstallDS -t
      exit 1
    elif test ${RESULT_CODE} -ne 0
    then
      echo "ERROR:  The detected Java version could not be used with the set of Java"
      echo "arguments ${OPENDJ_JAVA_ARGS}."
      echo "The detected Java binary is:"
      echo "${OPENDJ_JAVA_BIN}"
      echo "You must specify the path to a valid Java 6.0 update 10 or higher version."
      echo "The procedure to follow is:"
      echo "1. Delete the file ${INSTANCE_ROOT}/lib/set-java-home" if it exists.
      echo "2. Set the environment variable OPENDJ_JAVA_HOME to the root of a valid "
      echo "Java 6.0 installation."
      echo "If you want to have specific Java settings for each command line you must"
      echo "follow the steps 3 and 4."
      echo "3. Edit the properties file specifying the Java binary and the Java arguments"
      echo "for each command line.  The Java properties file is located in:"
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
  CLASSPATH="${INSTANCE_ROOT}/classes"
  for JAR in "${INSTALL_ROOT}/resources/"*.jar
  do
    CLASSPATH=${CLASSPATH}:${JAR}
  done
  CLASSPATH="${CLASSPATH}:${INSTALL_ROOT}/lib/bootstrap.jar"
  if [ "${INSTALL_ROOT}" != "${INSTANCE_ROOT}" ]
  then
    for JAR in "${INSTANCE_ROOT}/lib/"*.jar
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
  if [ -f "${INSTALL_ROOT}/configure" ]
  then
    if [ -f /etc/opendj/instance.loc ]
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
      read INSTANCE_ROOT <  /etc/opendj/instance.loc
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
    if [ -f "${INSTALL_ROOT}/instance.loc" ]
    then
      read location < ${INSTALL_ROOT}/instance.loc
      case `echo ${location}` in
           /*)
              INSTANCE_ROOT=${location}
              ;;
           *)
              INSTANCE_ROOT=${INSTALL_ROOT}/${location}
              ;;
      esac
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
  test_java_args
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

