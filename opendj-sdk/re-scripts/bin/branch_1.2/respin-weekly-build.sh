#!/bin/sh

# Change to the directory that contains this script.
cd `dirname $0`


# Explicitly set a minimal PATH so we won't run anything unexpected.
PATH="/bin:/usr/bin"
export PATH


# Define absolute paths to all commands that we will use in this script.
CAT="/usr/bin/cat"
RM="/usr/bin/rm"
GREP="/usr/bin/grep"


# Find the latest candidate.  If there isn't one, then fail.
SCRIPT_DIR=`/usr/bin/pwd`
BUILDS_DIR="/promoted-builds"
BUILD_NUMBER_FILE="buildnumber.txt"
CANDIDATE_DIR_NAME="candidate"
CANDIDATE_DIR="${BUILDS_DIR}/${CANDIDATE_DIR_NAME}"
if test ! -L "${CANDIDATE_DIR}"
then
  echo "ERROR:  No candidate directory ${CANDIDATE_DIR} could be found"
  echo "Does a candidate build exist?"
  exit 1
fi


# Get the version number for this build.
VERSION_NUMBER_FILE=version.txt
VERSION_NUMBER_STRING=`${CAT} "${CANDIDATE_DIR}/${VERSION_NUMBER_FILE}"`
VERSION_STRING="OpenDS-${VERSION_NUMBER_STRING}"


# Remove the existing candidate and build directory.
cd "${BUILDS_DIR}"
${RM} -rf "${VERSION_NUMBER_STRING}" "${CANDIDATE_DIR_NAME}"


# Kick off the weekly build again.
"${SCRIPT_DIR}/weekly-build.sh"

