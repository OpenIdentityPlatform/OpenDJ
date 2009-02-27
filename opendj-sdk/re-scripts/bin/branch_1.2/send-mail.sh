#!/bin/sh

JAVA_HOME=/java/jdk6
export JAVA_HOME

SCRIPT_DIR=`dirname ${0}`
CLASSPATH=${SCRIPT_DIR}/lib/OpenDS.jar:${SCRIPT_DIR}/lib/mail.jar:${SCRIPT_DIR}/lib/activation.jar
export CLASSPATH

${JAVA_HOME}/bin/java org.opends.server.util.EMailMessage --host global-zone "${@}"

