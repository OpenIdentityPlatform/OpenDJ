#!/bin/sh

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
# information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Portions Copyright 2006 Sun Microsystems, Inc.

OLD_DIR=`pwd`

[ -z "${DEBUG}" ] || set -x

die() {
    rc=$1
    shift
    echo $@
    exit $rc
}

# Change to the location of this build script.
cd `dirname $0`

# save the path to the functional tests in a variable
ft_home=`pwd`

# save the path to directory server in a variable
cd ../..
ds_home=`pwd`

# change to the functional test directory
cd ${ft_home}

# check for java
if [ -z "${JAVA_HOME}" ]; then
    JAVA_HOME=`java -cp ${ds_home}/resource FindJavaHome`
    if [ -z "${JAVA_HOME}" ]; then
        die 10 "Please set JAVA_HOME to the root of a Java 5.0 installation."
    else
        export JAVA_HOME
    fi
else
    export JAVA_HOME
fi

# Make sure that the TestNG JAR file is in the CLASSPATH so that ant will be
# able to find it.
testng_jar=${ds_home}/ext/testng/lib/testng-4.7-jdk15.jar
if [ -f "${testng_jar}" ]; then
    CLASSPATH="${ds_home}/ext/testng/lib/testng-4.7-jdk15.jar"
    export CLASSPATH
else
    die 11 "Could not find junit library in ${ds_home}/ext/testng/lib"
fi


ANT_HOME=${ds_home}/ext/ant
if [ -d "${ANT_HOME}" ]; then
    export ANT_HOME
else
    die 12 "Could not find ant in ${ANT_HOME}"
fi

# check if the product is built
# Let's wait on this for now.
if [ ! -d "${ds_home}/build" ]; then
    # Do you want to build directory server ?
    # maybe the question should be asked interactively, I don't know
    echo "Could not find the openDS bits, starting a build of openDS now"
    cd ${ds_home}
    ./build.sh package
    cd ${ft_home}
fi

# generate the testcase from the template
hostname=`uname -n`
template_home="${ft_home}/src/server/org/opends/server/"
integration_home="${template_home}/integration"
testcase_file="DirectoryServerAcceptanceTestCase.java"
template_file="${testcase_file}.template"
#cat ${template_home}/${template_file}|sed "s|<hostname>|${hostname}|"|sed "s|<integration_home>|$integration_home|" >  ${template_home}/${testcase_file} 

echo "Starting the build for the integration test suites"
# Execute the ant script and pass it any additional command-line arguments.
${ANT_HOME}/bin/ant --noconfig ${*}

if [ $? -eq 0 ]; then
    echo "Successfully built the integration test suite"
    echo "To run the integration test suite, please install OpenDS in the location of your choice."
    echo "Remember to set the variables in ${ft_home}/ext/testng/testng.xml"
    echo "To start the integration test suite, execute "
    echo "${ft_home}/test.sh [OpenDS home directory]"
    cat > ${ft_home}/test.sh <<EOF
#!/bin/sh
if [ \$# != 1 ]
then
echo "usage: test.sh [OpenDS home]"
exit
fi
[ -z "\${DEBUG}" ] || set -x
NEW_DIR=\${1}
cd \${NEW_DIR}
echo "OpenDS Integration Tests have started........."
CLASSPATH="/export/dsee7/src/openDS/trunk/opends/tests/integration-tests-testng/ext/testng/lib/testng-4.7-jdk15.jar:/export/dsee7/src/openDS/trunk/opends/tests/integration-tests-testng/built:\${NEW_DIR}/lib/OpenDS.jar:\${NEW_DIR}/lib/je.jar"
java -ea -cp \${CLASSPATH} org.testng.TestNG -d /tmp/testng -listener org.opends.server.OpenDSTestListener ${ft_home}/ext/testng/testng.xml
cd ${OLD_DIR}
EOF
    chmod 755 ${ft_home}/test.sh 
else
    die 14 "Error when running ant"
fi
