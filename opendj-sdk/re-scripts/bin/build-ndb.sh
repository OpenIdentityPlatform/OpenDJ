#!/bin/sh
# If an option is given to this script, it is assumed as running in
# sanity check mode

# do not checkout the code ; use the same as nightly builds
ARG1=$1
ARG2=$2
echo "Building NDB Backend "
echo "BUILD_DATE=$ARG1"
echo "BUILDS_DIR=$ARG2"


# Define the Java versions that we will use to perform the build.
# These values should correspond to subdirectories below /java
JAVA_VERSIONS="jdk6"


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
#JPIBUILDS_DIR=/daily-builds


# Define the URL to use to access the repository.
#JPISVN_URL=file:///svn-mirror/trunk/opends


# Define variables that will be used when sending the e-mail message.
#RECIPIENT="opends-staff@sun.com"
RECIPIENT="dir-release-dev@sun.com"
SENDER="opends@dev.java.net"
SUBJECT_DATE=`${DATE} '+%Y%m%d'`
SUBJECT="NDB Backend OpenDS Daily Build ${SUBJECT_DATE}"


# Create a new directory to hold the build.
BUILD_DATE=`${DATE} '+%Y%m%d%H%M%S'`
#JPIBUILD_DIR="${BUILDS_DIR}/${BUILD_DATE}"
#JPI${MKDIR} -p ${BUILD_DIR}
BUILD_DATE="${ARG1}"
BUILDS_DIR="${ARG2}"
BUILD_DIR="${BUILDS_DIR}/${BUILD_DATE}"


# Start generating a log file for the build.
LOG_FILE="${BUILD_DIR}/buildNDBBackend-${BUILD_DATE}.log"
SERVER_NAME="www.opends.org"
BUILD_URL="http://${SERVER_NAME}/${BUILDS_DIR}/${BUILD_DATE}/"
echo "OpenDS Daily Build ${SUBJECT_DATE}" > ${LOG_FILE}
echo "Build Time:  ${BUILD_DATE}" >> ${LOG_FILE}
echo "Build URL:   ${BUILD_URL}" >> ${LOG_FILE}
echo >> ${LOG_FILE}
echo >> ${LOG_FILE}


# Check out the head revision from the repository.
# JPI no ; use the same code source as the daily build
cd ${BUILD_DIR}
#JPIecho ${SVN} checkout -q --non-interactive ${SVN_URL} OpenDS >> ${LOG_FILE} 2>&1
#JPI${SVN} checkout -q --non-interactive ${SVN_URL} OpenDS >> ${LOG_FILE} 2>&1
#JPIecho >> ${LOG_FILE}
#JPIecho >> ${LOG_FILE}


# Add information about the checked-out revision to the log.
cd OpenDS
echo ${SVN} info >> ${LOG_FILE} 2>&1
${SVN} info >> ${LOG_FILE} 2>&1
echo >> ${LOG_FILE}
echo >> ${LOG_FILE}


# jp146654 :add path to OpenDMK
cd ${BUILD_DIR}/OpenDS

#JPIOLD_BUILDPROP_FILE=build.properties.origin
#JPINEW_BUILDPROP_FILE=build.properties
#JPIecho update Opendmk lib path in OpenDS/build.properties>> ${LOG_FILE} 2>&1
#JPIecho cp ${NEW_BUILDPROP_FILE} ${NEW_BUILDPROP_FILE}.origin >> ${LOG_FILE} 2>&1
#JPIcp ${NEW_BUILDPROP_FILE} ${NEW_BUILDPROP_FILE}.origin

#JPIecho add new path   : /export/home/builds/OpenDMK-bin/lib>> ${LOG_FILE} 2>&1
#JPI${CAT}  ${OLD_BUILDPROP_FILE} | sed "s/opendmk\.lib\.dir\=/opendmk\.lib\.dir\=\/export\/home\/builds\/OpenDMK-bin\/lib/g" > ${NEW_BUILDPROP_FILE}
#
#JPIecho >> ${LOG_FILE}
#JPIecho >> ${LOG_FILE}
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

  echo "Building NDB Backend with Java version ${VERSION}" >> ${LOG_FILE}
#JPI  echo ./build.sh nightlybuild -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1
#JPI  ./build.sh nightlybuild -Ddisable.test.help=true -DMEM=512M >> ${LOG_FILE} 2>&1
  echo  ./build.sh -Dmysql.lib.dir=/usr/local/mysql/share/java package >> ${LOG_FILE} 2>&1
   ./build.sh -Dmysql.lib.dir=/usr/local/mysql/share/java package >> ${LOG_FILE} 2>&1



  if ${TEST} $? -ne 0
  then
    ANY_BUILD_FAILED=1
  fi


  # Create the Java Web Start install archive.
  ADDRESS="www.opends.org"
  export ADDRESS
#JPI  BASE_PATH=/${BUILDS_DIR}/latest/OpenDS/build/webstart/install
#JPI  export BASE_PATH
#JPI  echo resource/webstart/create-webstart-standalone.sh >> ${LOG_FILE} 2>&1
#JPI  resource/webstart/create-webstart-standalone.sh >> ${LOG_FILE} 2>&1
#JPI  # fix to make create-webstart-standalone.sh support jdk5 and jdk6
#JPI  NEW_QUICKSETUP_FILE=`${MKTEMP}`
#JPI  JWS_QUICKSETUP_FILE=build/webstart/install/QuickSetup.jnlp
#JPI  cat ${JWS_QUICKSETUP_FILE} | sed "s/\/build\//\//buildNDBBackend.${VERSION}\//g" > ${NEW_QUICKSETUP_FILE}
#JPI  mv ${JWS_QUICKSETUP_FILE} ${JWS_QUICKSETUP_FILE}.moved
#JPI  mv ${NEW_QUICKSETUP_FILE} ${JWS_QUICKSETUP_FILE}
#JPI  ${CHMOD} +r ${JWS_QUICKSETUP_FILE}

  echo >> ${LOG_FILE}
  echo >> ${LOG_FILE}

  ${MV} build ./buildNDBBackend.${VERSION}
done


echo "end README  ${BUILD_DIR}/README_NDBBackend.html "

# Create a README_NDBBackend.html file in for this build.
cat > ${BUILD_DIR}/README_NDBBackend.html <<ENDOFREADME
<TABLE BORDER="0">
  <TR>
    <TD>
      <IMG SRC="http://${SERVER_NAME}/images/opends_logo_welcome.png" ALT="OpenDS Logo" WIDTH="197" HEIGHT="57">
    </TD>

    <TD>
      <H2>OpenDS Daily Build ${SUBJECT_DATE}</H2>
      <A HREF="${BUILD_URL}/buildNDBBackend-${BUILD_DATE}.log">Build Log File</A>
ENDOFREADME

for VERSION in ${JAVA_VERSIONS}
do
cat >> ${BUILD_DIR}/README_NDBBackend.html <<ENDOFREADME
      <BR>
      <B>Build with Java Version ${VERSION}</B>
      <A HREF="${BUILD_URL}OpenDS//buildNDBBackend.${VERSION}/package/${VERSION_STRING}.zip">${VERSION_STRING}.zip Build Package File</A><BR>
ENDOFREADME
done

cat >> ${BUILD_DIR}/README_NDBBackend.html <<ENDOFREADME
    </TD>
  </TR>
</TABLE>
ENDOFREADME


echo "end README  ${BUILD_DIR}/README_NDBBackend.html "

# Remove the "latest" symlink (if it exists) and recreate it pointing to
# the new build.
#JPIcd ${BUILDS_DIR} 
#JPI${RM} -f latest
#JPI${LN} -s ${BUILD_DIR} ${BUILDS_DIR}/latest


# Remove an old directory if appropriate.  We want to keep up to 30 builds.
#JPICOUNT=`${LS} | ${SORT} | ${GREP} -v latest | ${WC} -l | ${AWK} '{print $1}'`
#JPIif ${TEST} ${COUNT} -gt 30
#JPIthen
#JPI  OLDEST=`${LS} | ${SORT} | ${GREP} -v latest | ${HEAD} -1`
#JPI  ${RM} -rf ${OLDEST}
#JPIfi

#JPI : to be able to access unit tests logs wia http
#JPIif test -d "/${BUILDS_DIR}/latest/OpenDS/build.jdk5/unit-tests/package-instance/logs"
#JPIthen
#JPI   chmod -R 777 /${BUILDS_DIR}/latest/OpenDS/build.jdk5/unit-tests/package-instance/logs
#JPIfi
#JPIif test -d "/${BUILDS_DIR}/latest/OpenDS/build.jdk6/unit-tests/package-instance/logs"
#JPIthen
#JPI   chmod -R 777 /${BUILDS_DIR}/latest/OpenDS/build.jdk6/unit-tests/package-instance/logs
#JPIfi
####


# Send an e-mail message to indicate that the build is complete.
BODY_FILE=`${MKTEMP}`
echo "NDB Backend OpenDS Build ${SUBJECT_DATE}" > ${BODY_FILE}
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

echo '
${SENDMAIL} --from "${SENDER}" --to "${RECIPIENT}" --subject "${SUBJECT_WITH_STATUS}" \
            --body "${BODY_FILE}" --attach "${BUILD_DIR}/README_NDBBackend.html" \
            --attach "${LOG_FILE}" '

${SENDMAIL} --from "${SENDER}" --to "${RECIPIENT}" --subject "${SUBJECT_WITH_STATUS}" \
            --body "${BODY_FILE}" --attach "${BUILD_DIR}/README_NDBBackend.html" \
            --attach "${LOG_FILE}"

${RM} ${BODY_FILE}

