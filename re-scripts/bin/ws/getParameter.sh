#!/bin/sh

TEST=/usr/bin/test
#Default Values
PROD="TEST : "
BUILDS_DIR=test-builds
SVN_URL=file:///svn-mirror/trunk/opends
RECIPIENT="jeanine.pikus@sun.com,carole.hebrard@sun.com"

SANITY=1
TEST_MODE=0

if ${TEST} $# -gt 0
then
  SANITY=0

  if ${TEST} $# -eq 1
  then
      Product=$@
  else
      if ${TEST} $# -eq 2
      then
         Product=$1
         TEST_MODE=1
      else
         echo "too much parameters ..."
         exit
      fi
  fi

  # Config files
  echo ${Product}
  if  [ ! -d ./product/${Product} ]
  then
      . ./product/${Product}
  else
      echo "the file ./product/${Product} doesn't exist .."
      exit
  fi
  if ${TEST} ${TEST_MODE} -eq 1
  then
      echo "MODE TEST..."
      BUILDS_DIR=${BUILDS_DIR}/tests
      RECIPIENT="jeanine.pikus@sun.com,carole.hebrard@sun.com"
  fi

else
  SANITY=1
  Product=""
# need to be dev : I would like to add a mode test
      echo "not enough parameters ..."
      echo "you must specify either trunk , either branch12 see ./product/"
      exit
fi

 export Product
export PROD
export BUILDS_DIR
export SVN_URL
export RECIPIENT
export TEST_MODE 
export SANITY 
echo "========================="
echo "Product = ${Product}"
echo "PROD = ${PROD}"
echo "BUILDS_DIR = ${BUILDS_DIR}"
echo "SVN_URL = ${SVN_URL}"
echo "RECIPIENT = ${RECIPIENT}"
echo "========================="

