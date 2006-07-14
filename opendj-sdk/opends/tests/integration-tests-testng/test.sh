#!/bin/sh
if [ $# != 1 ]
then
echo "usage: test.sh [OpenDS home]"
exit
fi
[ -z "${DEBUG}" ] || set -x
NEW_DIR=${1}
cd ${NEW_DIR}
echo "OpenDS Integration Tests have started........."
CLASSPATH="/export/dsee7/src/openDS/trunk/opends/tests/integration-tests-testng/ext/testng/lib/testng-4.7-jdk15.jar:/export/dsee7/src/openDS/trunk/opends/tests/integration-tests-testng/built:${NEW_DIR}/lib/OpenDS.jar:${NEW_DIR}/lib/je.jar"
java -ea -cp ${CLASSPATH} org.testng.TestNG -d /tmp/testng -listener org.opends.server.OpenDSTestListener /export00/dsee7/src/openDS/trunk/opends/tests/integration-tests-testng/ext/testng/testng.xml
cd /export00/dsee7/src/openDS/trunk/opends/tests/integration-tests-testng
