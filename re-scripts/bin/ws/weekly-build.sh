#!/bin/sh

# Define the Java versions that we will use to perform the build.
# These values should correspond to subdirectories below /java
#JAVA_VERSIONS="jdk5 jdk6"

# Define absolute paths to all commands that we will use in this script.
AWK="/usr/bin/awk"
BASENAME=/usr/bin/basename
CAT="/usr/bin/cat"
CHMOD="/usr/bin/chmod"
DATE="/usr/bin/date"
DIGEST="/usr/bin/digest"
DIRNAME="/usr/bin/dirname"
GREP="/usr/bin/grep"
LN="/usr/bin/ln"
MKDIR="/usr/bin/mkdir"
MKTEMP="/usr/bin/mktemp"
MV="/usr/bin/mv"
PS="/usr/bin/ps"
RM="/usr/bin/rm"
SVN="/opt/csw/bin/svn"
SENDMAIL="/export/home/builds/bin/send-mail.sh"
TEST=/usr/bin/test


. ./getParameter.sh $@
if ${TEST} $? -eq 1
  then
  echo " stop there ..."
  exit 1
fi


BUILDS_DIR=${PROMOTED_DIR}
echo "========================="
echo "Product = ${Product}"
echo "PROD = ${PROD}"
echo "BUILDS_DIR = ${BUILDS_DIR}"
echo "SVN_URL = ${SVN_URL}"
echo "RECIPIENT = ${RECIPIENT}"
echo "========================="
exit


# Change to the directory that contains this script.
cd `${DIRNAME} $0`


# Explicitly set a minimal PATH so we don't run anything unexpected.
PATH="/bin:/usr/bin"
export PATH


# Sanity checks
# Removes directory that might be still there if test failed before @AfterClass
# that normaly performs the cleanup
FSCACHE=/tmp/OpenDS.FSCache
if ${TEST} -d FSCACHE
then
  ${RM} -rf ${FSCACHE}
fi
# Checks that no weekly build is running
${PS} | grep `${BASENAME} $0`
if ${TEST} $? -eq 0
  then
  echo "This script is already running. Aborting..."
  exit 1
fi
# Checks that no daily build is running
${PS} | grep daily\.sh
if ${TEST} $? -eq 0
  then
  echo "The daily script is currently running. Aborting..."
  exit 1
fi


# Define paths that will be used during this script.
SCRIPT_DIR=`pwd`



# Start creating a log file that will be used to record progress.
LOG_FILE=`${MKTEMP}`
BUILD_DATE=`${DATE} '+%Y%m%d%H%M%S'`
echo "Beginning weekly build processing at ${BUILD_DATE}" > "${LOG_FILE}"
echo "" >> "${LOG_FILE}"


# Define paths to all directories that we will access in this script.
# These should all be absolute paths.
BUILD_NUMBER_FILE="buildnumber.txt"
REVISION_NUMBER_FILE="revision.txt"
VERSION_NUMBER_FILE="version.txt"


# Look at what is currently the latest weekly build to get information we need
# to bootstrap this build.
LAST_BUILD_DIR="${BUILDS_DIR}/latest"
LAST_BUILD_NUMBER=`cat ${LAST_BUILD_DIR}/${BUILD_NUMBER_FILE}`
LAST_REVISION_NUMBER=`cat ${LAST_BUILD_DIR}/${REVISION_NUMBER_FILE}`
BUILD_NUMBER=`echo "${LAST_BUILD_NUMBER} + 1" | bc`
if test ${BUILD_NUMBER} -le 9
then
  BUILD_NUMBER_STR="00${BUILD_NUMBER}"
else
  if test ${BUILD_NUMBER} -le 99
  then
    BUILD_NUMBER_STR="0${BUILD_NUMBER}"
  else
    BUILD_NUMBER_STR="${BUILD_NUMBER}"
  fi
fi
echo "*** Build Number Information ***" >> "${LOG_FILE}"
echo "LAST_BUILD_DIR=${LAST_BUILD_DIR}" >> "${LOG_FILE}"
echo "LAST_BUILD_NUMBER=${LAST_BUILD_NUMBER}" >> "${LOG_FILE}"
echo "LAST_REVISION_NUMBER=${LAST_REVISION_NUMBER}" >> "${LOG_FILE}"
echo "BUILD_NUMBER=${BUILD_NUMBER}" >> "${LOG_FILE}"
echo "BUILD_NUMBER_STR=${BUILD_NUMBER_STR}" >> "${LOG_FILE}"
echo "" >> "${LOG_FILE}"


# Define information that we will use to access the repository.
PRODUCT_URL="${SVN_URL}/PRODUCT"



# Parse the contents of the PRODUCT file to get the version information.
echo "*** Product Version Information ***" >> "${LOG_FILE}"
PRODUCT_FILE=`${MKTEMP}`
echo ${SVN} cat --non-interactive "${PRODUCT_URL}" \> "${PRODUCT_FILE}" >> "${LOG_FILE}"
${SVN} cat --non-interactive "${PRODUCT_URL}" > "${PRODUCT_FILE}"
SHORT_NAME=`${GREP} SHORT_NAME "${PRODUCT_FILE}" | cut -d= -f2`
MAJOR_VERSION=`${GREP} MAJOR_VERSION "${PRODUCT_FILE}" | cut -d= -f2`
MINOR_VERSION=`${GREP} MINOR_VERSION "${PRODUCT_FILE}" | cut -d= -f2`
POINT_VERSION=`${GREP} POINT_VERSION "${PRODUCT_FILE}" | cut -d= -f2`
VERSION_QUALIFIER=`${GREP} VERSION_QUALIFIER "${PRODUCT_FILE}" | cut -d= -f2`
VERSION_NUMBER_STRING="${MAJOR_VERSION}.${MINOR_VERSION}.${POINT_VERSION}${VERSION_QUALIFIER}-build${BUILD_NUMBER_STR}"
VERSION_STRING="${SHORT_NAME}-${VERSION_NUMBER_STRING}"
${RM} "${PRODUCT_FILE}"
echo "SHORT_NAME=${SHORT_NAME}" >> "${LOG_FILE}"
echo "MAJOR_VERSION=${MAJOR_VERSION}" >> "${LOG_FILE}"
echo "MINOR_VERSION=${MINOR_VERSION}" >> "${LOG_FILE}"
echo "POINT_VERSION=${POINT_VERSION}" >> "${LOG_FILE}"
echo "VERSION_QUALIFIER=${VERSION_QUALIFIER}" >> "${LOG_FILE}"
echo "VERSION_STRING=${VERSION_STRING}" >> "${LOG_FILE}"
echo "" >> "${LOG_FILE}"


# Define variables that will be used when sending the e-mail message.
SENDER="opends@dev.java.net"
SUBJECT="${PROD} OpenDS Weekly Build Candidate ${VERSION_NUMBER_STRING}"


# Create a new directory to hold the build.
echo "*** Checking Out the Server ***" >> "${LOG_FILE}"
BUILD_DIR="${BUILDS_DIR}/${VERSION_NUMBER_STRING}"
if test -d "${BUILD_DIR}"
then
  echo "ERROR:  Target build directory ${BUILD_DIR} already exists"
  echo "ERROR:  Aborting the weekly build attempt."
  exit 1
fi
echo ${MKDIR} -p "${BUILDS_DIR}/${VERSION_NUMBER_STRING}" >> "${LOG_FILE}"
${MKDIR} -p "${BUILDS_DIR}/${VERSION_NUMBER_STRING}"
echo cd "${BUILDS_DIR}/${VERSION_NUMBER_STRING}" >> "${LOG_FILE}"
cd "${BUILD_DIR}"
echo ${MV} "${LOG_FILE}" "${VERSION_STRING}.log" >> "${LOG_FILE}"
${MV} "${LOG_FILE}" "${VERSION_STRING}.log" && LOG_FILE="${BUILD_DIR}/${VERSION_STRING}.log"
${CHMOD} 0644 "${LOG_FILE}"
echo "LOG_FILE=${LOG_FILE}" >> "${LOG_FILE}"


# Check out the head revision from the repository.
echo ${SVN} checkout -q --non-interactive ${SVN_URL} OpenDS >> "${LOG_FILE}" 2>&1
${SVN} checkout -q --non-interactive ${SVN_URL} OpenDS >> ${LOG_FILE} 2>&1
cd OpenDS
REVISION_NUMBER=`${SVN} info | ${GREP} '^Revision:' | ${AWK} '{print $2}'`
echo "REVISION_NUMBER=${REVISION_NUMBER}" >> "${LOG_FILE}"
echo "" >> "${LOG_FILE}"


# Add information about the checked out revision to the log file.
echo ${SVN} info >> "${LOG_FILE}" 2>&1
${SVN} info >> "${LOG_FILE}" 2>&1
echo "" >> "${LOG_FILE}"

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
# end jp146654


JAVA_HOME=/java/jdk5
export JAVA_HOME

# Perform a build of the server using the "weekly" target.
echo "*** Building the Server ***" >> "${LOG_FILE}"
echo ./build.sh weekly svr4 -DBUILD_NUMBER=${BUILD_NUMBER} -Ddisable.test.help=true -DMEM=512M >> "${LOG_FILE}" 2>&1
./build.sh weekly svr4 -DBUILD_NUMBER=${BUILD_NUMBER} -Ddisable.test.help=true -DMEM=512M >> "${LOG_FILE}" 2>&1
if test $? -eq 0
then
  STATUS="Successful"
else
  STATUS="FAILED"
fi
echo "STATUS=${STATUS}" >> "${LOG_FILE}"
echo "" >> "${LOG_FILE}"


# Create the Java Web Start Install Archive.
ADDRESS="www.opends.org"
export ADDRESS
echo "*** Creating Java Web Start Install Archive ***" >> "${LOG_FILE}"
BASE_PATH="/promoted-builds/${VERSION_NUMBER_STRING}/install"
export BASE_PATH
echo "BASE_PATH=${BASE_PATH}" >> "${LOG_FILE}"
echo resource/webstart/create-webstart-standalone.sh >> "${LOG_FILE}" 2>&1
resource/webstart/create-webstart-standalone.sh  >> "${LOG_FILE}" 2>&1
echo "" >> "${LOG_FILE}"


# Symlink all of the appropriate files into place in the base build directory.
#${LN} -s "${BUILD_DIR}/OpenDS/build/package/${VERSION_STRING}.zip" "${BUILD_DIR}/${VERSION_STRING}.zip"
#${LN} -s "${BUILD_DIR}/OpenDS/build/package/${VERSION_STRING}-DSML.war" "${BUILD_DIR}/${VERSION_STRING}-DSML.war"
#${LN} -s "${BUILD_DIR}/OpenDS/build/src.zip" "${BUILD_DIR}/src.zip"
#${LN} -s "${BUILD_DIR}/OpenDS/build/webstart/install" "${BUILD_DIR}/install"
#${LN} -s "${BUILD_DIR}/OpenDS/build/javadoc" "${BUILD_DIR}/javadoc"
${LN} -s "OpenDS/build/package/${VERSION_STRING}.zip" "${BUILD_DIR}/${VERSION_STRING}.zip"
${LN} -s "OpenDS/build/package/${VERSION_STRING}-DSML.war" "${BUILD_DIR}/${VERSION_STRING}-DSML.war"
${LN} -s "OpenDS/build/src.zip" "${BUILD_DIR}/src.zip"
${LN} -s "OpenDS/build/webstart/install" "${BUILD_DIR}/install"
${LN} -s "OpenDS/build/javadoc" "${BUILD_DIR}/javadoc"


# Create MD5 digests of the appropriate files.
echo "*** Calculating MD5 Digests of Build Files ***" >> "${LOG_FILE}"
${DIGEST} -a md5 "${BUILD_DIR}/OpenDS/build/package/${VERSION_STRING}.zip" > "${BUILD_DIR}/${VERSION_STRING}.zip.md5"
${DIGEST} -a md5 "${BUILD_DIR}/OpenDS/build/package/${VERSION_STRING}-DSML.war" > "${BUILD_DIR}/${VERSION_STRING}-DSML.war.md5"
${DIGEST} -a md5 "${BUILD_DIR}/OpenDS/build/src.zip" > "${BUILD_DIR}/src.zip.md5"
CORE_SERVER_MD5=`${CAT} "${BUILD_DIR}/${VERSION_STRING}.zip.md5"`
DSML_GATEWAY_MD5=`${CAT} "${BUILD_DIR}/${VERSION_STRING}-DSML.war.md5"`
SRC_ZIP_MD5=`${CAT} "${BUILD_DIR}/src.zip.md5"`
echo "CORE_SERVER_MD5=${CORE_SERVER_MD5}" >> "${LOG_FILE}"
echo "DSML_GATEWAY_MD5=${DSML_GATEWAY_MD5}" >> "${LOG_FILE}"
echo "SRC_ZIP_MD5=${SRC_ZIP_MD5}" >> "${LOG_FILE}"
echo "" >> "${LOG_FILE}"


# Write the build number and revision numbers to text files.
echo "${BUILD_NUMBER}" > "${BUILD_DIR}/${BUILD_NUMBER_FILE}"
echo "${REVISION_NUMBER}" > "${BUILD_DIR}/${REVISION_NUMBER_FILE}"
echo "${VERSION_NUMBER_STRING}" > "${BUILD_DIR}/${VERSION_NUMBER_FILE}"


# Get a log of changes since the last build.
echo "*** Getting changelog data ***" >> "${LOG_FILE}"
LAST_REV_PLUS_ONE=`echo "${LAST_REVISION_NUMBER}+1" | bc`
echo ${SVN} log -r ${LAST_REV_PLUS_ONE}:${REVISION_NUMBER} >> "${LOG_FILE}" 2>&1
${SVN} log -r ${LAST_REV_PLUS_ONE}:${REVISION_NUMBER} > "${BUILD_DIR}/changes.log" 2>&1
echo "" >> "${LOG_FILE}"

#If this is a sanity check, exit
if ${TEST} ${SANITY} -eq 1
then
  echo "Sanity check done."
  exit 1
fi

# Create an index.html in the build directory.
${CAT} > "${BUILD_DIR}/index.html" <<ENDOFHTML
<!DOCTYPE html PUBLIC "-//Tigris//DTD XHTML 1.0 Transitional//EN" "http://style.tigris.org/nonav/tigris_transitional.dtd">

<HTML>
  <HEAD>
    <TITLE>OpenDS Weekly Build ${VERSION_NUMBER_STRING}</TITLE>
    <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=utf-8">
    <LINK REL="SHORTCUT ICON" HREF="/public/images/opends_favicon.gif">
    <LINK REL="stylesheet" HREF="/public/css/opends.css" TYPE="text/css">
    <STYLE>
      tr { text-align:left; vertical-align:top; padding:5px 10px; width:180px; background-color:#f5f5f5 }
    </STYLE>
  </HEAD>

  <BODY>
    <TABLE BORDER="0" CELLPADDING="10">
      <TR STYLE="background-color:#ffffff">
        <TD VALIGN="MIDDLE">
          <IMG SRC="/images/opends_logo_welcome.png" ALT="OpenDS Logo" WIDTH="197" HEIGHT="57">
        </TD>
        <TD VALIGN="MIDDLE">
          <H1>OpenDS Weekly Build ${VERSION_NUMBER_STRING}</H1>
        </TD>
      </TR>
      <TR>
        <TD>Weekly Build Number</TD>
        <TD>${BUILD_NUMBER}</TD>
      </TR>
      <TR>
        <TD>Subversion Revision Number</TD>
        <TD>${REVISION_NUMBER}</TD>
      </TR>
      <TR>
        <TD>QuickSetup Installer</TD>
        <TD><A HREF="install/QuickSetup.jnlp">install/QuickSetup.jnlp</A></TD>
      </TR>
      <TR>
        <TD>Core Server Zip File</TD>
        <TD><A HREF="${VERSION_STRING}.zip">${VERSION_STRING}.zip</A></TD>
      </TR>
      <TR>
        <TD>Core Server MD5 Checksum</TD>
        <TD>${CORE_SERVER_MD5}</TD>
      </TR>
      <TR>
        <TD>DSML Gateway WAR File</TD>
        <TD><A HREF="${VERSION_STRING}-DSML.war">${VERSION_STRING}-DSML.war</A></TD>
      </TR>
      <TR>
        <TD>DSML Gateway MD5 Checksum</TD>
        <TD>${DSML_GATEWAY_MD5}</TD>
      </TR>
      <TR>
        <TD>src.zip Server Source Archive</TD>
        <TD><A HREF="src.zip">src.zip</A></TD>
      </TR>
      <TR>
        <TD>src.zip MD5 Checksum</TD>
        <TD>${SRC_ZIP_MD5}</TD>
      </TR>
      <TR>
        <TD>Javadoc Documentation</TD>
        <TD><A HREF="javadoc/index.html">javadoc/index.html</A></TD>
      </TR>
      <TR>
        <TD>Changelog</TD>
        <TD><A HREF="changes.log">changes.log</A></TD>
      </TR>
      <TR>
        <TD>Build Log</TD>
        <TD><A HREF="${VERSION_STRING}.log">${VERSION_STRING}.log</A></TD>
      </TR>
    </TABLE>

    <BR>

    <A HREF="/promoted-builds/">See All Weekly Builds</A><BR>
    <A HREF="http://www.opends.org/">http://www.opends.org/</A><BR>
    <!-- Omniture -->
    <script src="https://www.opends.org/wiki/scripts/s_code_remote.js" language="JavaScript"/>
  </BODY>
</HTML>
ENDOFHTML


# Create a "candidate" symbolic link to this build.
${RM} -f "${BUILDS_DIR}/candidate"
${LN} -s "${BUILD_DIR}" "${BUILDS_DIR}/candidate"


# Create a temporary file to use for the e-mail message.
BODY_FILE=`${MKTEMP}`
echo "A new OpenDS weekly build candidate is available at" > ${BODY_FILE}
#echo "http://www2.opends.org/promoted-builds/candidate/" >> ${BODY_FILE}
echo "http://www.opends.org/promoted-builds/candidate/" >> ${BODY_FILE}

# Send the message and delete the temporary file.
${SENDMAIL} --from "${SENDER}" --to "${RECIPIENT}" --subject "${STATUS} ${SUBJECT}" \
            --body "${BODY_FILE}" --attach "${BUILD_DIR}/index.html" \
            --attach "${LOG_FILE}"

${RM} "${BODY_FILE}"
