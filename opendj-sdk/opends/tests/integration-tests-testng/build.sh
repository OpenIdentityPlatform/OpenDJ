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
testng_jar=${ds_home}/tests/integration-tests-testng/ext/testng/lib/testng-5.0.2-jdk15.jar
if [ -f "${testng_jar}" ]; then
    CLASSPATH="${ds_home}/tests/integration-tests-testng/ext/testng/lib/testng-5.0.2-jdk15.jar"
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
    echo " "
    echo "There are two options for running the integration test suites."
    echo " "
    echo "Option 1 - You must have OpenDS freshly installed and started."
    echo "You must also set the variables in ${ft_home}/ext/testng/testng.xml"
    echo "For option 1, execute"
    echo "${ft_home}/test.sh [OpenDS home directory]"
    echo " "
    echo "Option 2 (recommended) - Let the test.sh script install and start OpenDS."
    echo "The file, ${ft_home}/ext/testng/testng.xml,"
    echo "will also be automatically generated."
    echo "For option 2, execute"
    echo "${ft_home}/test.sh installOpenDS [OpenDS installation directory] [port number]"
    echo " "
    cat > ${ft_home}/test.sh <<EOF
#!/bin/sh
if [ \$# != 1 -a \$# != 3 ]
then
echo "If you already have an OpenDS installed and started,"
echo "usage: ${ft_home}/test.sh [OpenDS home]"
echo " "
echo "If you wish the test.sh script to install OpenDS, start OpenDS, generate a TestNG xml file, and start the integration tests,"
echo "usage: ${ft_home}/test.sh installOpenDS [OpenDS install directory] [port number]" 
exit
fi
[ -z "\${DEBUG}" ] || set -x
if [ \$# = 1 ]
then
OPENDS_HOME=\${1}
cd \${OPENDS_HOME}
echo "OpenDS Integration Tests have started........."
CLASSPATH="${ds_home}/tests/integration-tests-testng/ext/testng/lib/testng-5.0.2-jdk15.jar:${ds_home}/build/integration-tests:\${OPENDS_HOME}/lib/OpenDS.jar:\${OPENDS_HOME}/lib/je.jar"
java -ea -cp \${CLASSPATH} org.testng.TestNG -d /tmp/testng -listener org.opends.server.OpenDSTestListener ${ft_home}/ext/testng/testng.xml
else
OPENDS_INSTALL_DIR=\${2}
OPENDS_PORT=\${3}
OPENDS_HOME=\${OPENDS_INSTALL_DIR}/OpenDS-0.1
HOSTNAME=\`hostname\`
INTEG_TEST_DIR=`pwd`
if [ -d \${OPENDS_INSTALL_DIR} ]
then
echo "Directory, \${OPENDS_INSTALL_DIR} currently exists"
else
echo "Directory, \${OPENDS_INSTALL_DIR} does not exist, creating it......"
mkdir -p \${OPENDS_INSTALL_DIR}
fi

if [ -d \${INTEG_TEST_DIR}/opends/logs ]
then
echo "Directory, \${INTEG_TEST_DIR}/opends/logs currently exists"
else
echo "Directory, \${INTEG_TEST_DIR}/opends/logs does not exist, creating it......"
mkdir -p \${INTEG_TEST_DIR}/opends/logs
fi

if [ -d \${INTEG_TEST_DIR}/opends/backup ]
then
echo "Directory, \${INTEG_TEST_DIR}/opends/backup currently exists"
else
echo "Directory, \${INTEG_TEST_DIR}/opends/backup does not exist, creating it......"
mkdir -p \${INTEG_TEST_DIR}/opends/backup
fi

if [ -d \${INTEG_TEST_DIR}/opends/reports ]
then
echo "Directory, \${INTEG_TEST_DIR}/opends/reports currently exists"
else
echo "Directory, \${INTEG_TEST_DIR}/opends/reports does not exist, creating it......"
mkdir -p \${INTEG_TEST_DIR}/opends/reports
fi

cp \${INTEG_TEST_DIR}/ext/testng/testng.xml \${INTEG_TEST_DIR}/ext/testng/testng.xml.save

cat > \${INTEG_TEST_DIR}/ext/testng/testng.xml <<EOF2
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd" >
<suite name="OpenDS"   verbose="1" >
    <parameter name="hostname" value="\${HOSTNAME}"/>
    <parameter name="port" value="\${OPENDS_PORT}"/>
    <parameter name="sport" value="636"/>
    <parameter name="bindDN" value="cn=Directory Manager"/>
    <parameter name="bindPW" value="password"/>
    <parameter name="integration_test_home" value="\${INTEG_TEST_DIR}/src/server/org/opends/server/integration"/>
    <parameter name="logDir" value="\${INTEG_TEST_DIR}/opends/logs"/>
    <parameter name="dsee_home" value="\${OPENDS_HOME}"/>
    <parameter name="backupDir" value="\${INTEG_TEST_DIR}/opends/backup"/>

    <packages>
        <package name="org.opends.server.integration.quickstart"/>
        <package name="org.opends.server.integration.bob"/>
        <package name="org.opends.server.integration.core"/>
        <package name="org.opends.server.integration.frontend"/>
        <package name="org.opends.server.integration.schema"/>
        <package name="org.opends.server.integration.security"/>
    </packages>
    
    <test name="precommit">
        <groups>
            <run>
                <include name="precommit"/>
                <exclude name="broken"/>
            </run>
        </groups>
    </test>
    
    <test name="integration-tests">
          <groups>
	      <define name="all">
                  <include name="quickstart"/>
                  <include name="bob"/>
                  <include name="core"/>
                  <include name="frontend"/>
                  <include name="schema"/>
                  <include name="security"/>
 	      </define>

	      <define name="quickstart">
		  <include name="quickstart"/>
  	      </define>

	      <define name="bob">
		  <include name="bob"/>
  	      </define>

	      <define name="core">
		  <include name="core"/>
  	      </define>

	      <define name="frontend">
		  <include name="frontend"/>
  	      </define>

	      <define name="schema">
		  <include name="schema"/>
	      </define>

	      <define name="security">
		  <include name="security"/>
	      </define>

	      <run>
		  <include name="all"/>
              </run>
          </groups>
    </test>
    
</suite>
EOF2

cp ${ds_home}/build/package/OpenDS-0.1.zip \${OPENDS_INSTALL_DIR}
cd \${OPENDS_INSTALL_DIR}
unzip OpenDS-0.1.zip
echo "OpenDS has been installed in \${OPENDS_INSTALL_DIR}"

echo "Configuring OpenDS to use port \${OPENDS_PORT}"
\${OPENDS_HOME}/bin/configure-ds.sh -p \${OPENDS_PORT}

echo "Starting OpenDS and the OpenDS Integration Tests...."
\${OPENDS_HOME}/bin/start-ds.sh -nodetach&
sleep 30

echo "OpenDS Integration Tests have started........."
CLASSPATH="${ds_home}/tests/integration-tests-testng/ext/testng/lib/testng-5.0.2-jdk15.jar:${ds_home}/build/integration-tests:\${OPENDS_HOME}/lib/OpenDS.jar:\${OPENDS_HOME}/lib/je.jar"
java -ea -cp \${CLASSPATH} org.testng.TestNG -d /tmp/testng -listener org.opends.server.OpenDSTestListener ${ft_home}/ext/testng/testng.xml

echo "The output from OpenDS is in \${INTEG_TEST_DIR}/opends/logs"
fi
cd ${ds_home}/tests/integration-tests-testng
EOF
    chmod 755 ${ft_home}/test.sh 
else
    die 14 "Error when running ant"
fi
