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
# Portions Copyright 2010-2016 ForgeRock AS.
# Portions Copyright 2019-2024 3A Systems, LLC.
#
# Display an error message
#
display_java_not_found_error() {
  echo "Please set OPENDJ_JAVA_HOME to the root of a Java 7 (or higher) installation"
  echo "or edit the java.properties file to specify the Java version to be used"
}

get_property() {
  # Read standard variables from config/java.properties file
  if test -f "${INSTANCE_ROOT}/config/java.properties"
  then
    PROPERTY_VALUE=
    PROPERTY_VALUE=`grep "^\s*${1}=" "${INSTANCE_ROOT}/config/java.properties" |cut -d'=' -f2- 2> /dev/null`
  fi
}

#
# Function that sets the OPENDJ_JAVA_BIN to the java path on the running machine according to the following order:
# 1 - use OPENDJ_JAVA_BIN if defined and points to an existing regular file
# 2 - use OPENDJ_JAVA_HOME if defined and OPENDJ_JAVA_HOME/bin/java points to an existing regular file
# 3 - use the 'SCRIPT_NAME.java-home' property from the config/java.properties file
#                                      is defined and 'SCRIPT_NAME.java-home'/bin/java points to a regular file
# 4 - use the 'default.java-home' property from the config/java.properties file
#                                      is defined and 'default.java-home'/bin/java points to a regular file
# 5 - use `which java` command to find java path
# 6 - use JAVA_BIN if defined and points to an existing regular file
# 7 - use JAVA_HOME if defined and JAVA_HOME/bin/java points to a regural file
# 8 - Displays an error message which says that java was not found on the running machine
set_opendj_java_bin() {
  if test ! -z "${OPENDJ_JAVA_BIN}" -a -f "${OPENDJ_JAVA_BIN}"
  then
    export OPENDJ_JAVA_BIN
  elif test ! -z "${OPENDJ_JAVA_HOME}" -a -f "${OPENDJ_JAVA_HOME}/bin/java"
  then
    OPENDJ_JAVA_BIN=${OPENDJ_JAVA_HOME}/bin/java
  else
    eval JAVA_HOME_PROPERTY=${SCRIPT_NAME}.java-home
    get_property ${JAVA_HOME_PROPERTY}
    if test ! -z "${PROPERTY_VALUE}" -a -f "${PROPERTY_VALUE}/bin/java"
    then
      OPENDJ_JAVA_BIN=${PROPERTY_VALUE}/bin/java
    else
      get_property default.java-home
      if test ! -z "${PROPERTY_VALUE}" -a -f "${PROPERTY_VALUE}/bin/java"
      then
        OPENDJ_JAVA_BIN=${PROPERTY_VALUE}/bin/java
      else
        TEST_JAVA_PATH=`which java 2> /dev/null`
        if test ! -z ${TEST_JAVA_PATH} -a -f ${TEST_JAVA_PATH}
        then
          OPENDJ_JAVA_BIN=${TEST_JAVA_PATH}
        elif test ! -z "${JAVA_BIN}" -a -f "${JAVA_BIN}"
        then
          OPENDJ_JAVA_BIN=${JAVA_BIN}
        elif test ! -z "${JAVA_HOME}" -a -f "${JAVA_HOME}/bin/java"
        then
          OPENDJ_JAVA_BIN=${JAVA_HOME}/bin/java
        else
          display_java_not_found_error
          exit 1
        fi
      fi
    fi
  fi
  export OPENDJ_JAVA_BIN
}

#
# function that sets the java home
#
set_java_home_and_args() {
  # See if the environment variables for arguments are set.
  if test -z "${OPENDJ_JAVA_ARGS}"
  then
    # Use java.properties file to set java args for the specific script
    SCRIPT_NAME_ARGS_PROPERTY="${SCRIPT_NAME}".java-args
    get_property ${SCRIPT_NAME_ARGS_PROPERTY}
    if test ! -z "${PROPERTY_VALUE}"
    then
      OPENDJ_JAVA_ARGS="${PROPERTY_VALUE}"
    else
      get_property default.java-args
      OPENDJ_JAVA_ARGS="${PROPERTY_VALUE}"
    fi
  fi
  set_opendj_java_bin
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

print_error_message() {
  if test -z "${OPENDJ_JAVA_BIN}"
  then
    echo "No Java binary found on your machine."
  else
    echo "The detected Java binary is: ${OPENDJ_JAVA_BIN}"
  fi
  echo "You must specify the path to a valid Java 7 or higher version."
  echo "The procedure to follow is to set the environment variable OPENDJ_JAVA_HOME"
  echo "to the root of a valid Java 7 installation."
  echo "If you want to have specific Java settings for each command line you must"
  echo "edit the properties file specifying the Java binary and/or the Java arguments"
  echo "for each command line.  The Java properties file is located in:"
  echo "${INSTANCE_ROOT}/config/java.properties."
}

# Determine whether the detected Java environment is acceptable for use.
test_java() {
  if test -z "${OPENDJ_JAVA_ARGS}"
  then
    "${OPENDJ_JAVA_BIN}" org.opends.server.tools.CheckJVMVersion
    RESULT_CODE=${?}
    if test ${RESULT_CODE} -eq 13
    then
      # This is a particular error code that means that the Java version is 6
      # but not supported.  Let TestJVM to display the localized error message
      "${OPENDJ_JAVA_BIN}" org.opends.server.tools.CheckJVMVersion
      exit 1
    elif test ${RESULT_CODE} -ne 0
    then
      echo "ERROR:  The detected Java version could not be used."
      print_error_message
      exit 1
    fi
  else
    "${OPENDJ_JAVA_BIN}" ${OPENDJ_JAVA_ARGS} org.opends.server.tools.CheckJVMVersion 
    RESULT_CODE=${?}
    if test ${RESULT_CODE} -eq 13
    then
      # This is a particular error code that means that the Java version is 7
      # but not supported.  Let TestJVM to display the localized error message
      "${OPENDJ_JAVA_BIN}" org.opends.server.tools.CheckJVMVersion
      exit 1
    elif test ${RESULT_CODE} -ne 0
    then
      echo "ERROR:  The detected Java version could not be used with the set of Java"
      echo "arguments ${OPENDJ_JAVA_ARGS}."
      print_error_message
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
	
  "${OPENDJ_JAVA_BIN}" --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --version > /dev/null 2>&1
  RESULT_CODE=${?}
  if test ${RESULT_CODE} -eq 0
  then
  	export OPENDJ_JAVA_ARGS="$OPENDJ_JAVA_ARGS --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED"
  fi
}

# Configure the appropriate CLASSPATH for server, using Opend DJ logger.
set_opendj_logger_classpath() {
  CLASSPATH="${INSTANCE_ROOT}/classes"
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

# Configure the appropriate CLASSPATH for client, using java.util.logging logger.
set_classpath() {
  CLASSPATH="${INSTANCE_ROOT}/classes"
  CLASSPATH="${CLASSPATH}:${INSTALL_ROOT}/lib/bootstrap-client.jar"
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
elif test "${SCRIPT_UTIL_CMD}" = "set-full-server-environment-and-test-java"
then
  set_java_home_and_args
  set_environment_vars
  set_opendj_logger_classpath
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

