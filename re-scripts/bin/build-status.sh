#!/bin/sh

# Define absolute paths to all commands we will use in this script.
DIRNAME=/usr/bin/dirname

# Change to the directory that contains this script.
cd `${DIRNAME} $0`

# Explicitly set a minimal PATH so we don't run anything unexpected.
PATH="/bin:/usr/bin"
export PATH

/java/jdk6/bin/java -classpath /export/home/builds/bin/build-status/classes org.opends.tools.BuildStatus
