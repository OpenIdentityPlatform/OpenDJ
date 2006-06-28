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

# Make sure that the JUnit JAR file is in the CLASSPATH so that ant will be
# able to find it.
junit_jar=${ds_home}/ext/junit.jar
if [ -f "${junit_jar}" ]; then
    CLASSPATH="${ds_home}/ext/junit.jar"
    export CLASSPATH
else
    die 11 "Could not find junit library in ${ds_home}/ext"
fi


ANT_HOME=${ds_home}/ext/ant
if [ -d "${ANT_HOME}" ]; then
    export ANT_HOME
else
    die 12 "Could not find ant in ${ANT_HOME}"
fi

# check if the product is built
if [ ! -d "${ds_home}/build" ]; then
    # Do you want to build directory server ?
    # maybe the question should be asked interactively, I don't know
    echo "Could not find the bits, starting a build now"
    cd ${ds_home}
    ./build.sh package
fi

# generate the testcase from the template
hostname=`uname -n`
template_home="${ft_home}/src/server/org/opends/server/"
acceptance_home="${template_home}/acceptance"
testcase_file="DirectoryServerAcceptanceTestCase.java"
template_file="${testcase_file}.template"
cat ${template_home}/${template_file}|sed "s|<hostname>|${hostname}|"|sed "s|<acceptance_home>|$acceptance_home|" >  ${template_home}/${testcase_file} 

# Execute the ant script and pass it any additional command-line arguments.
${ANT_HOME}/bin/ant --noconfig ${*}

if [ $? -eq 0 ]; then
    echo "Successfully built the acceptance"
    echo "to run the tests now, simply run ${ft_home}/test.sh"
    cat > ${ft_home}/test.sh <<EOF
#!/bin/sh
[ -z "\${DEBUG}" ] || set -x
cd ${ft_home}
CLASSPATH="\${CLASSPATH}:${ds_home}/lib/je.jar:${ds_home}/ext/junit.jar:${ds_home}/build/classes:${ft_home}/built"
java -cp \${CLASSPATH} org.opends.server.DirectoryServerAcceptanceTestSuite
EOF
    chmod 755 ${ft_home}/test.sh 
else
    die 14 "Error when running ant"
fi
