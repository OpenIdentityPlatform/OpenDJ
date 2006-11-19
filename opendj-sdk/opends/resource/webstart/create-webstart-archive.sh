#!/bin/sh


# Determine the location to this script so that we know where we are in the
# OpenDS source tree.
cd `dirname $0`
SCRIPT_DIR=`pwd`
cd ../..
ROOT_DIR=`pwd`
cd "${SCRIPT_DIR}"


# Make sure that a few constants are defined that will be needed to build the
# web start archive.
if test -z "${PROTOCOL}"
then
  PROTOCOL="http"
fi
echo "PROTOCOL:       ${PROTOCOL}"

if test -z "${ADDRESS}"
then
  ADDRESS="builds.opends.org"
fi
echo "ADDRESS:        ${ADDRESS}"
echo "PORT:           ${PORT}"

if test -z "${BASE_PATH}"
then
  BASE_PATH="/install"
fi
echo "BASE_PATH:      ${BASE_PATH}"

if test -z "${JNLP_FILENAME}"
then
  JNLP_FILENAME="QuickSetup.jnlp"
fi
echo "JNLP_FILENAME:  ${JNLP_FILENAME}"

if test -z "${PORT}"
then
  INSTALLER_URI="${PROTOCOL}://${ADDRESS}${BASE_PATH}"
else
  INSTALLER_URI="${PROTOCOL}://${ADDRESS}:${PORT}${BASE_PATH}"
fi


# See if we can find the location of the dependencies in the Java environment.
# If not, then fail.
if test -z "${JAVA_HOME}"
then
  JAVA_HOME=`java -cp ${ROOT_DIR}/resource FindJavaHome 2> /dev/null`
  if test -z "${JAVA_HOME}"
  then
    echo "Please set JAVA_HOME to the root of a Java 5.0 installation."
    exit 1
  else
    export JAVA_HOME
  fi
fi

JAR="${JAVA_HOME}/bin/jar"
if test ! -x "${JAR}"
then
  echo "ERROR:  Cannot find the ${JAR} utility."
  echo "        Is JAVA_HOME set correctly?"
  echo "        It should point to the root of a JDK (not a JRE) installation"
  exit 1
fi

JARSIGNER="${JAVA_HOME}/bin/jarsigner"
if test ! -x "${JARSIGNER}"
then
  echo "ERROR:  Cannot find the ${JARSIGNER} utility."
  echo "        Is JAVA_HOME set correctly?"
  echo "        It should point to the root of a JDK (not a JRE) installation"
  exit 1
fi

PACK200="${JAVA_HOME}/bin/pack200"
if test ! -x "${PACK200}"
then
  echo "ERROR:  Cannot find the ${PACK200} utility."
  echo "        Is JAVA_HOME set correctly?"
  echo "        It should point to the root of a JDK (not a JRE) installation"
  exit 1
fi

JNLP_SERVLET_FILE="${JAVA_HOME}/sample/jnlp/servlet/jnlp-servlet.jar"
if test ! -f "${JNLP_SERVLET_FILE}"
then
  echo "ERROR:  Cannot find the ${JNLP_SERVLET_FILE} JAR file."
  echo "        Is JAVA_HOME set correctly?"
  echo "        It should point to the root of a JDK (not a JRE) installation"
  exit 1
fi


# Make sure that the OpenDS build directory exists.  If not, then create it.
BUILD_DIR="${ROOT_DIR}/build"
if test ! -d "${BUILD_DIR}"
then
  echo "WARNING:  ${BUILD_DIR} does not exist.  Building the server ..."
  "${ROOT_DIR}/build.sh"
  EXIT_CODE=$?
  if test "${EXIT_CODE}" -ne 0
  then
    echo "ERROR:  Build failed.  Aborting creation of web start archive."
    exit ${EXIT_CODE}
  else
    echo "The server was built successfully."
  fi
fi


# Determine what the name should be for the OpenDS zip file name, but without
# the ".zip" extension.
SHORT_NAME=`grep SHORT_NAME ${ROOT_DIR}/PRODUCT | cut -d = -f 2`
MAJOR_VERSION=`grep MAJOR_VERSION ${ROOT_DIR}/PRODUCT | cut -d = -f 2`
MINOR_VERSION=`grep MINOR_VERSION ${ROOT_DIR}/PRODUCT | cut -d = -f 2`
VERSION_QUALIFIER=`grep VERSION_QUALIFIER ${ROOT_DIR}/PRODUCT | cut -d = -f 2`
ZIP_FILENAME_BASE="${SHORT_NAME}-${MAJOR_VERSION}.${MINOR_VERSION}"
if test ! -z "${VERSION_QUALIFIER}"
then
  ZIP_FILENAME_BASE="${ZIP_FILENAME_BASE}${VERSION_QUALIFIER}"
fi


# Create the directory structure into which we will place the archive.
echo "Creating the initial directory structure ..."
WEBSTART_DIR="${BUILD_DIR}/webstart"
INSTALL_DIR="${WEBSTART_DIR}/install"
rm -rf "${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}/lib"


# Copy the static files from the script directory into the appropriate places
# in the archive.
echo "Copying static content into place ..."
cp -Rp "${SCRIPT_DIR}/images" "${INSTALL_DIR}"
cp -Rp "${SCRIPT_DIR}/WEB-INF" "${INSTALL_DIR}"


# Copy the jnlp-servlet.jar file into the WEB-INF/lib directory.
cp "${JNLP_SERVLET_FILE}" "${INSTALL_DIR}/WEB-INF/lib/"


# Copy the appropriate OpenDS library files and make sure they are signed and
# packed.
PKG_LIB_DIR="${BUILD_DIR}/package/${ZIP_FILENAME_BASE}/lib"
CERT_KEYSTORE="${ROOT_DIR}/tests/unit-tests-testng/resource/server.keystore"
CERT_KEYSTORE_PIN="password"
CERT_ALIAS="server-cert"
for LIBFILE in OpenDS.jar je.jar quicksetup.jar
do
  echo "Signing and packing ${LIBFILE} ..."
  cp "${PKG_LIB_DIR}/${LIBFILE}" "${INSTALL_DIR}/lib"
  "${PACK200}" --repack "${INSTALL_DIR}/lib/${LIBFILE}"
  "${JARSIGNER}" -keystore "${CERT_KEYSTORE}" -keypass "${CERT_KEYSTORE_PIN}" \
                 -storepass "${CERT_KEYSTORE_PIN}" \
                 "${INSTALL_DIR}/lib/${LIBFILE}" "${CERT_ALIAS}"
  "${PACK200}" "${INSTALL_DIR}/lib/${LIBFILE}.pack.gz" \
               "${INSTALL_DIR}/lib/${LIBFILE}"
done


# Create the zipped.jar file (and the corresponding packed version)
echo "Creating zipped.jar ..."
cd "${BUILD_DIR}/package"
"${JAR}" -cf "${INSTALL_DIR}/lib/zipped.jar" "${ZIP_FILENAME_BASE}.zip"
cd "${INSTALL_DIR}/lib"
echo "Signing and packing zipped.jar ..."
"${PACK200}" --repack zipped.jar
"${JARSIGNER}" -keystore "${CERT_KEYSTORE}" -keypass "${CERT_KEYSTORE_PIN}" \
               -storepass "${CERT_KEYSTORE_PIN}" zipped.jar "${CERT_ALIAS}"
"${PACK200}" zipped.jar.pack.gz zipped.jar


# Create the JNLP file with the appropriate contents.
echo "Creating JNLP file ${JNLP_FILENAME} ..."
cd ..
cat > "${JNLP_FILENAME}" <<ENDOFJNLP
<?xml version="1.0" encoding="utf-8"?>
<!-- JNLP File for OpenDS Quick Setup Application -->
<jnlp spec="1.0+"
  codebase="${INSTALLER_URI}" href="${JNLP_FILENAME}">
  <information>
    <title>OpenDS Quick Setup Application</title>
    <vendor>http://www.opends.org/</vendor>
    <homepage href="http://www.opends.org"/>
    <description>OpenDS Quick Setup Application</description>
    <description kind="short">OpenDS Web Start Installer</description>
    <icon href="images/opendshref.png" height="128" width="128"/>
    <icon kind="splash" href="images/opendssplash.png" height="114" width="479"/>
    <offline-allowed/>
  </information>

  <security>
    <all-permissions/>
  </security>

  <resources>
    <j2se version="1.5+"/>
    <jar href="lib/quicksetup.jar" download="eager" main="true"/>
    <jar href="lib/OpenDS.jar" download="lazy"/>
    <jar href="lib/je.jar" download="lazy"/>
    <jar href="lib/zipped.jar" download="lazy"/>
    <property name="org.opends.quicksetup.iswebstart" value="true" />
    <property name="org.opends.quicksetup.lazyjarurls" value="${INSTALLER_URI}/lib/OpenDS.jar ${INSTALLER_URI}/lib/zipped.jar ${INSTALLER_URI}/lib/je.jar" />
    <property name="org.opends.quicksetup.zipfilename" value="${ZIP_FILENAME_BASE}.zip"/>
  </resources>
  <application-desc main-class="org.opends.quicksetup.SplashScreen"/>
</jnlp>
ENDOFJNLP


# Create a WAR file with the appropriate contents.
echo "Creating WAR file install.war ..."
${JAR} -cf ../install.war *


# Tell the user where the files are.
echo "The deployable content may be found in ${ROOT_DIR}/build/webstart"
echo "It is intended for deployment at ${INSTALLER_URI}"

