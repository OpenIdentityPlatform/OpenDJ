#!/bin/sh
# If an option is given to this script, it is assumed as running in
# sanity check mode


# Define the Java versions that we will use to perform the build.
# These values should correspond to subdirectories below /java
JAVA_VERSIONS="jdk5 jdk6"


# Define absolute paths to all commands we will use in this script.
AWK=/usr/bin/awk
NAWK=/usr/bin/nawk
CAT=/usr/bin/cat
CHMOD=/usr/bin/chmod
CUT=/usr/bin/cut
DATE=/usr/bin/date
DIRNAME=/usr/bin/dirname
GREP=/usr/bin/grep
GZIP=/usr/bin/gzip
HEAD=/usr/bin/head
LN=/usr/bin/ln
LS=/usr/bin/ls
MKDIR=/usr/bin/mkdir
MKTEMP=/usr/bin/mktemp
MV=/usr/bin/mv
RM=/usr/bin/rm
SORT=/usr/bin/sort
SVN=/opt/csw/bin/svn
TAR=/usr/bin/tar
TEST=/usr/bin/test
WC=/usr/bin/wc
SENDMAIL=/export/home/builds/bin/send-mail.sh

#If an argument is provided, let's assume we run a sanity check (no mail)
if ${TEST} $# -gt 0
then
  SANITY=1
else
  SANITY=0
fi

# Change to the directory that contains this script.
cd `${DIRNAME} $0`


# Explicitly set a minimal PATH so we don't run anything unexpected.
PATH="/bin:/usr/bin"
export PATH


# Define paths that will be used during this script.
SCRIPT_DIR=`pwd`
BUILDS_DIR=/daily-builds


# Define the URL to use to access the repository.
SVN_URL=file:///svn-mirror/trunk/opends


# Define variables that will be used when sending the e-mail message.
#RECIPIENT="opends-staff@sun.com"
RECIPIENT="dev@opends.dev.java.net"
SENDER="opends@dev.java.net"
SUBJECT_DATE=`${DATE} '+%Y%m%d'`
SUBJECT="OpenDS Daily Build ${SUBJECT_DATE}"


# Create a new directory to hold the build.
BUILD_DATE=`${DATE} '+%Y%m%d%H%M%S'`
BUILD_DIR="${BUILDS_DIR}/${BUILD_DATE}"
${MKDIR} -p ${BUILD_DIR}


# Start generating a log file for the build.
LOG_FILE="${BUILD_DIR}/build-${BUILD_DATE}.log"
#SERVER_NAME="www2.opends.org"
SERVER_NAME="www.opends.org"
BUILD_URL="http://${SERVER_NAME}/${BUILDS_DIR}/${BUILD_DATE}/"
echo "OpenDS Daily Build ${SUBJECT_DATE}" > ${LOG_FILE}
echo "Build Time:  ${BUILD_DATE}" >> ${LOG_FILE}
echo "Build URL:   ${BUILD_URL}" >> ${LOG_FILE}
echo >> ${LOG_FILE}
echo >> ${LOG_FILE}


# Check out the head revision from the repository.
cd ${BUILD_DIR}
echo ${SVN} checkout -q --non-interactive ${SVN_URL} OpenDS >> ${LOG_FILE} 2>&1
${SVN} checkout -q --non-interactive ${SVN_URL} OpenDS >> ${LOG_FILE} 2>&1
echo >> ${LOG_FILE}
echo >> ${LOG_FILE}


# Add information about the checked-out revision to the log.
cd OpenDS
echo ${SVN} info >> ${LOG_FILE} 2>&1
${SVN} info >> ${LOG_FILE} 2>&1
echo >> ${LOG_FILE}
echo >> ${LOG_FILE}


# jp146654 :add path to OpenDMK
cd ${BUILD_DIR}/OpenDS

OLD_BUILDPROP_FILE=build.properties.origin
NEW_BUILDPROP_FILE=build.properties
echo update Opendmk lib path in OpenDS/build.properties>> ${LOG_FILE} 2>&1
echo cp ${NEW_BUILDPROP_FILE} ${NEW_BUILDPROP_FILE}.origin >> ${LOG_FILE} 2>&1
cp ${NEW_BUILDPROP_FILE} ${NEW_BUILDPROP_FILE}.origin

echo add new path   : /export/home/builds/OpenDMK-bin/lib>> ${LOG_FILE} 2>&1
${CAT}  ${OLD_BUILDPROP_FILE} | sed "s/opendmk\.lib\.dir\=/opendmk\.lib\.dir\=\/export\/home\/builds\/OpenDMK-bin\/lib/g" > ${NEW_BUILDPROP_FILE}

echo >> ${LOG_FILE}
echo >> ${LOG_FILE}
# end


# Parse the PRODUCT file to get the version information.
SHORT_NAME=`${GREP} SHORT_NAME PRODUCT | ${CUT} -d= -f2`
MAJOR_VERSION=`${GREP} MAJOR_VERSION PRODUCT | ${CUT} -d= -f2`
MINOR_VERSION=`${GREP} MINOR_VERSION PRODUCT | ${CUT} -d= -f2`
POINT_VERSION=`${GREP} POINT_VERSION PRODUCT | ${CUT} -d= -f2`
VERSION_QUALIFIER=`${GREP} VERSION_QUALIFIER PRODUCT | ${CUT} -d= -f2`
VERSION_NUMBER_STRING="${MAJOR_VERSION}.${MINOR_VERSION}.${POINT_VERSION}${VERSION_QUALIFIER}"
VERSION_STRING="${SHORT_NAME}-${VERSION_NUMBER_STRING}"


# Loop through the JDK versions and build usin gthe "nightly" target.
# We'll set JAVA_HOME each time, and when we're done, we can continue using
# the last one.
ANY_BUILD_FAILED=0
ALL_BUILD_FAILED=1
TEST_FAILED=0
for VERSION in ${JAVA_VERSIONS}
do
  # Removes directory that might be still there if test failed before
  # @AfterClass that normaly performs the cleanup
  FSCACHE=/tmp/OpenDS.FSCache
  if ${TEST} -d FSCACHE
  then
    ${RM} -rf ${FSCACHE}
  fi


  JAVA_HOME=/java/${VERSION}
  export JAVA_HOME

  echo "Building with Java version ${VERSION}" >> ${LOG_FILE}
  echo ./build.sh nightlybuild -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1
  ./build.sh nightlybuild -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1

  if ${TEST} $? -eq 0
  then
    ALL_BUILD_FAILED=0
    echo ./build.sh nightlytests -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1
    ./build.sh nightlytests -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1
    if ${TEST} $? -ne 0
    then
      TEST_FAILED=1
    fi
  else
    ANY_BUILD_FAILED=1
  fi

  echo ./build.sh svr4 -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1
  ./build.sh svr4  -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1

  if ${TEST} $? -ne 0
  then
    ANY_BUILD_FAILED=1
  fi


  # Create the Java Web Start install archive.
  ADDRESS="www.opends.org"
  export ADDRESS
  BASE_PATH=/${BUILDS_DIR}/latest/OpenDS/build/webstart/install
  export BASE_PATH
  echo resource/webstart/create-webstart-standalone.sh >> ${LOG_FILE} 2>&1
  resource/webstart/create-webstart-standalone.sh >> ${LOG_FILE} 2>&1
  # fix to make create-webstart-standalone.sh support jdk5 and jdk6
  NEW_QUICKSETUP_FILE=`${MKTEMP}`
  JWS_QUICKSETUP_FILE=build/webstart/install/QuickSetup.jnlp
  cat ${JWS_QUICKSETUP_FILE} | sed "s/\/build\//\/build.${VERSION}\//g" > ${NEW_QUICKSETUP_FILE}
  mv ${JWS_QUICKSETUP_FILE} ${JWS_QUICKSETUP_FILE}.moved
  mv ${NEW_QUICKSETUP_FILE} ${JWS_QUICKSETUP_FILE}
  ${CHMOD} +r ${JWS_QUICKSETUP_FILE}

  echo >> ${LOG_FILE}
  echo >> ${LOG_FILE}

  ${MV} build build.${VERSION}
done

#If this is a sanity check, exit
if ${TEST} ${SANITY} -eq 1
then
  exit 1
fi

# Create a README.html file in for this build.
cat > ${BUILD_DIR}/README.html <<ENDOFREADME
<TABLE BORDER="0">
  <TR>
    <TD>
      <IMG SRC="http://${SERVER_NAME}/images/opends_logo_welcome.png" ALT="OpenDS Logo" WIDTH="197" HEIGHT="57">
    </TD>

    <TD>
      <H2>OpenDS Daily Build ${SUBJECT_DATE}</H2>
      <A HREF="${BUILD_URL}build-${BUILD_DATE}.log">Build Log File</A>
ENDOFREADME

for VERSION in ${JAVA_VERSIONS}
do
cat >> ${BUILD_DIR}/README.html <<ENDOFREADME
      <BR>
      <B>Build with Java Version ${VERSION}</B>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/package/${VERSION_STRING}.zip">${VERSION_STRING}.zip Build Package File</A><BR>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/package/${VERSION_STRING}-DSML.war">${VERSION_STRING}-DSML.war DSML Gateway</A><BR>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/webstart/install/QuickSetup.jnlp">QuickSetup Java Web Start Installer</A><BR>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/javadoc/">Javadoc Documentation</A><BR>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/src.zip">src.zip Source Archive</A><BR>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/unit-tests/report/results.txt">Unit Test Report</A><BR>
      <A HREF="${BUILD_URL}OpenDS/build.${VERSION}/coverage/reports/unit/">Unit Test Code Goverage Report</A><BR>
ENDOFREADME
done

cat >> ${BUILD_DIR}/README.html <<ENDOFREADME
    </TD>
  </TR>
</TABLE>
ENDOFREADME


# Remove the "latest" symlink (if it exists) and recreate it pointing to
# the new build.
cd ${BUILDS_DIR}
${RM} -f latest
${LN} -s ${BUILD_DIR} ${BUILDS_DIR}/latest


# Remove an old directory if appropriate.  We want to keep up to 30 builds.
COUNT=`${LS} | ${SORT} | ${GREP} -v latest | ${WC} -l | ${AWK} '{print $1}'`
if ${TEST} ${COUNT} -gt 30
then
  OLDEST=`${LS} | ${SORT} | ${GREP} -v latest | ${HEAD} -1`
  ${RM} -rf ${OLDEST}
fi

#JPI : to be able to access unit tests logs wia http
if test -d "/${BUILDS_DIR}/latest/OpenDS/build.jdk5/unit-tests/package-instance/logs"
then
   chmod -R 777 /${BUILDS_DIR}/latest/OpenDS/build.jdk5/unit-tests/package-instance/logs
fi
if test -d "/${BUILDS_DIR}/latest/OpenDS/build.jdk6/unit-tests/package-instance/logs"
then
   chmod -R 777 /${BUILDS_DIR}/latest/OpenDS/build.jdk6/unit-tests/package-instance/logs
fi
####


# Send an e-mail message to indicate that the build is complete.
BODY_FILE=`${MKTEMP}`
echo "OpenDS Daily Build ${SUBJECT_DATE}" > ${BODY_FILE}
echo "Java Versions Tested:  ${JAVA_VERSIONS}" >> ${BODY_FILE}

if ${TEST} ${ANY_BUILD_FAILED} -eq 0
then
  echo "Build Status:  Succeeded for all Java versions" >> ${BODY_FILE}
  if ${TEST} ${TEST_FAILED} -eq 0
  then
    SUBJECT_WITH_STATUS="${SUBJECT} (success)"
  else
    RESULT5_FILE=/$BUILD_DIR/OpenDS/build.jdk5/unit-tests/report/results.txt
    RESULT6_FILE=/$BUILD_DIR/OpenDS/build.jdk6/unit-tests/report/results.txt
    if ${TEST} -f ${RESULT5_FILE} && ${TEST} -f ${RESULT6_FILE}
    then
      RES=`cat ${RESULT5_FILE} ${RESULT6_FILE} | ${NAWK} ' BEGIN {F=0;T=0} /# Tests failed/ {F=F+$4} /# Test classes:/ {T=T+$4}  END {printf("%.2f\n",100-F/T*100)}`
      SUBJECT_WITH_STATUS="${SUBJECT} (build successful - ${RES}% tests passed)"
    elif ${TEST} -f ${RESULT5_FILE}
    then
      RES=`cat ${RESULT5_FILE} | ${NAWK} ' BEGIN {F=0;T=0} /# Tests failed/ {F=F+$4} /# Test classes:/ {T=T+$4}  END {printf("%.2f\n",100-F/T*100)}`
      SUBJECT_WITH_STATUS="${SUBJECT} (build successful - JDK5: ${RES}% tests passed - JDK6: no test result)"
    elif ${TEST} -f ${RESULT6_FILE}
    then
      RES=`cat ${RESULT6_FILE} | ${NAWK} ' BEGIN {F=0;T=0} /# Tests failed/ {F=F+$4} /# Test classes:/ {T=T+$4}  END {printf("%.2f\n",100-F/T*100)}`
      SUBJECT_WITH_STATUS="${SUBJECT} (build successful - JDK5: no test result - JDK6: ${RES}% tests passed)"
    else
      SUBJECT_WITH_STATUS="${SUBJECT} (build successful - No test result)"
    fi
  fi	
else
  if ${TEST} ${ALL_BUILD_FAILED} -eq 0
  then
    echo "Build Status:  Failed for some Java versions" >> ${BODY_FILE}
  else
    echo "Build Status:  Failed for all Java versions" >> ${BODY_FILE}
  fi
  SUBJECT_WITH_STATUS="${SUBJECT} (failure)"
fi

echo "Build URL:  ${BUILD_URL}" >> ${BODY_FILE}

${SENDMAIL} --from "${SENDER}" --to "${RECIPIENT}" --subject "${SUBJECT_WITH_STATUS}" \
            --body "${BODY_FILE}" --attach "${BUILD_DIR}/README.html" \
            --attach "${LOG_FILE}"

${RM} ${BODY_FILE}



# build NDB Backend
/export/home/builds/bin/build-ndb.sh ${BUILD_DATE} ${BUILDS_DIR}

