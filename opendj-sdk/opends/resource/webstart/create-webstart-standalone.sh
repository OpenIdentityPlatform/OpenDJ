#!/bin/sh

GREP="/usr/bin/grep"

# Determine the location to this script so that we know where we are in the
# OpenDS source tree.
cd "`dirname $0`"
SCRIPT_DIR=`pwd`
cd ../..
ROOT_DIR=`pwd`
cd "${SCRIPT_DIR}"
echo "ROOT_DIR:       ${ROOT_DIR}"

if test -z "${PRODUCT_FILE}"
then 
  PRODUCT_FILE="${ROOT_DIR}/PRODUCT"
fi
echo "PRODUCT_FILE:   ${PRODUCT_FILE}"

if test -z "${PRODUCT_NAME}"
then
  PRODUCT_NAME=`${GREP} SHORT_NAME "${PRODUCT_FILE}" | cut -d= -f2`
fi
echo "PRODUCT_NAME:   ${PRODUCT_NAME}"

# Make sure that a few constants are defined that will be needed to build the
# web start archive.
if test -z "${PROTOCOL}"
then
  PROTOCOL="http"
fi
echo "PROTOCOL:       ${PROTOCOL}"

if test -z "${ADDRESS}"
then
  ADDRESS="www.forgerock.org"
fi
echo "ADDRESS:        ${ADDRESS}"
echo "PORT:           ${PORT}"

if test -z "${BASE_PATH}"
then
  BASE_PATH="/downloads/opendj/latest/install"
fi
echo "BASE_PATH:      ${BASE_PATH}"

if test -z "${INSTALL_JNLP_FILENAME}"
then
  INSTALL_JNLP_FILENAME="QuickSetup.jnlp"
fi
echo "INSTALL_JNLP_FILENAME:  ${INSTALL_JNLP_FILENAME}"

if test -z "${PORT}"
then
  INSTALLER_URI="${PROTOCOL}://${ADDRESS}${BASE_PATH}"
else
  INSTALLER_URI="${PROTOCOL}://${ADDRESS}:${PORT}${BASE_PATH}"
fi

VENDOR="http://www.forgerock.com/"
HOMEPAGE="http://www.forgerock.com/opendj.html"

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

# Make sure that the OpenDS build directory exists.  If not, then create it.

if test -z "${BUILD_DIR}"
then
  BUILD_DIR="${ROOT_DIR}/build"
fi
echo "BUILD_DIR: ${BUILD_DIR}"

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
ZIP_FILEPATH=`ls ${BUILD_DIR}/package/${PRODUCT_NAME}*.zip`
ZIP_FILENAME=`basename ${ZIP_FILEPATH}`
ZIP_FILENAME_BASE=`echo ${ZIP_FILENAME} | sed -e 's/\.zip//'`


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
find "${INSTALL_DIR}/images" -type d -name '.svn' -exec rm -rf {} \;


# Copy the appropriate OpenDS library files and make sure they are signed.
PKG_LIB_DIR="${BUILD_DIR}/package/${ZIP_FILENAME_BASE}/lib"
CERT_KEYSTORE="${ROOT_DIR}/tests/unit-tests-testng/resource/server.keystore"
CERT_KEYSTORE_PIN="password"
CERT_ALIAS="server-cert"
for LIBFILE in ${PRODUCT_NAME}.jar je.jar quicksetup.jar 
do
  echo "Signing ${LIBFILE} ..."
  cp "${PKG_LIB_DIR}/${LIBFILE}" "${INSTALL_DIR}/lib"
  "${JARSIGNER}" -keystore "${CERT_KEYSTORE}" -keypass "${CERT_KEYSTORE_PIN}" \
                 -storepass "${CERT_KEYSTORE_PIN}" \
                 "${INSTALL_DIR}/lib/${LIBFILE}" "${CERT_ALIAS}"
done


# Create and sign the zipped.jar file.
echo "Creating zipped.jar ..."
cd "${BUILD_DIR}/package"
"${JAR}" -cf "${INSTALL_DIR}/lib/zipped.jar" "${ZIP_FILENAME_BASE}.zip"
cd "${INSTALL_DIR}/lib"
echo "Signing zipped.jar ..."
"${JARSIGNER}" -keystore "${CERT_KEYSTORE}" -keypass "${CERT_KEYSTORE_PIN}" \
               -storepass "${CERT_KEYSTORE_PIN}" zipped.jar "${CERT_ALIAS}"


# Create the Setup JNLP file with the appropriate contents.
echo "Creating Setup JNLP file ${INSTALL_JNLP_FILENAME} ..."
cd ..
cat > "${INSTALL_JNLP_FILENAME}" <<ENDOFINSTALLJNLP
<?xml version="1.0" encoding="utf-8"?>
<!-- JNLP File for ${PRODUCT_NAME} QuickSetup Application -->
<jnlp spec="1.5+"
  codebase="${INSTALLER_URI}" href="${INSTALL_JNLP_FILENAME}">
  <information>
    <title>${PRODUCT_NAME} QuickSetup Application</title>
    <vendor>${VENDOR}</vendor>
    <homepage href="${HOMEPAGE}"/>
    <description>${PRODUCT_NAME} QuickSetup Application</description>
    <description kind="short">${PRODUCT_NAME} Web Start Installer</description>
    <icon href="images/opendshref.png" height="128" width="128"/>
    <icon kind="splash" href="images/opendssplash.png" height="114" width="479"/>
  </information>

  <security>
    <all-permissions/>
  </security>

  <resources>
    <j2se version="1.6+" java-vm-args="-client"/>
    <jar href="lib/quicksetup.jar" download="eager" main="true"/>
    <jar href="lib/${PRODUCT_NAME}.jar" download="lazy"/>
    <jar href="lib/je.jar" download="lazy"/>
    <jar href="lib/zipped.jar" download="lazy"/>
    <property name="org.opends.quicksetup.iswebstart" value="true" />
    <property name="org.opends.quicksetup.Application.class" value="org.opends.quicksetup.installandupgrader.InstallAndUpgrader"/>
    <property name="org.opends.quicksetup.lazyjarurls" value="${INSTALLER_URI}/lib/${PRODUCT_NAME}.jar ${INSTALLER_URI}/lib/zipped.jar ${INSTALLER_URI}/lib/je.jar" />
    <property name="org.opends.quicksetup.zipfilename" value="${ZIP_FILENAME_BASE}.zip"/>
  </resources>
  
  <resources os="AIX">
    <j2se version="1.6+"/>
  </resources>
  
  <application-desc main-class="org.opends.quicksetup.SplashScreen"/>
</jnlp>
ENDOFINSTALLJNLP


# Tell the user where the files are.
echo "The deployable content may be found in ${BUILD_DIR}/webstart"
echo "It is intended for deployment at ${INSTALLER_URI}"

