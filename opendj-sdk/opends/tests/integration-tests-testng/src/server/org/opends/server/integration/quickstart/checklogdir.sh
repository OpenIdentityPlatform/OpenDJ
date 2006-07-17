#!/bin/sh
LOGDIR=$1
if [ -d "${LOGDIR}" ]
then
    echo "${LOGDIR} currently exists"
else
    mkdir -p ${LOGDIR}
    echo "${LOGDIR} created"
fi

