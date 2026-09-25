#!/usr/bin/env bash
# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Portions copyright 2026 3A Systems, LLC.

# Default setup script
#
# What the instance is made of - the server itself, the backend, the entries the backend was
# asked to hold - is left to fail this script, which is what run.sh reads to decide whether
# the container may report itself healthy. The optional schema and data LDIFs below keep the
# tolerance they were written with.

echo "Setting up default OpenDJ instance"

# The tools read only the first line of a password file, and a CR ends it as an LF does, so a
# password with a line break would be cut there - silently, as the root password set up would
# not be the one the container was given
cr=$(printf '\r')
nl='
'
case $ROOT_PASSWORD in
  *"$cr"*|*"$nl"*)
    echo "ROOT_PASSWORD must not contain a line break: the tools read only the first line of the password file" >&2
    exit 1 ;;
esac

# The tools read the root password from a file, so that any password setup accepts reaches
# them as one value, and it does not show on the command line of a process while the tool
# runs. mktemp creates the file readable by its owner only. The EXIT trap does not run when the
# container is killed during the bootstrap, so the file goes to the tmpfs of /dev/shm where
# there is one, rather than to the writable layer of the container, and run.sh removes what a
# killed bootstrap left behind before anything else. On Kubernetes /dev/shm is shared by all
# the containers of the pod and outlives a restart of this one, which is what the removal in
# run.sh is for there; the name carries ADMIN_PORT, so that it removes only the file of this
# container. Busybox mktemp replaces only the last six X of the template.
PASSWORD_FILE=$(mktemp -p /dev/shm "opendj-setup-password.$ADMIN_PORT.XXXXXX" 2>/dev/null \
  || mktemp "/tmp/opendj-setup-password.$ADMIN_PORT.XXXXXX") || exit 1
# the trap also removes the base entry template below, which a failed import would leave in /tmp
BASE_TEMPLATE=
trap 'rm -f "$PASSWORD_FILE" ${BASE_TEMPLATE:+"$BASE_TEMPLATE"}' EXIT
printf '%s\n' "$ROOT_PASSWORD" >"$PASSWORD_FILE" || exit 1

# If any optional LDIF files are present load them

# There are multiple types of ldif files.
# This step makes plain copies.
# See below for imports via `ldapmodify`.
if [ -d /opt/opendj/bootstrap/config/schema/ ]; then
  echo "Copying schema:"
  mkdir -p /opt/opendj/template/config/schema
  for file in /opt/opendj/bootstrap/config/schema/*; do
    target_file="/opt/opendj/template/config/schema/$(basename -- "$file")"
    echo "Copying $file to $target_file"
    cp "$file" "$target_file"
  done
fi

/opt/opendj/setup \
  --cli \
  -h localhost \
  --ldapPort $PORT \
  --ldapsPort $LDAPS_PORT \
  --enableStartTLS $OPENDJ_SSL_OPTIONS \
  --adminConnectorPort $ADMIN_PORT \
  --rootUserDN "$ROOT_USER_DN" \
  --rootUserPasswordFile "$PASSWORD_FILE" \
  --acceptLicense \
  --no-prompt \
  --noPropertiesFile \
  $SETUP_ARGS || exit 1

BACKEND_TYPE=${BACKEND_TYPE:-je}
BACKEND_DB_DIRECTORY=${BACKEND_DB_DIRECTORY:-db}
echo "creating backend: $BACKEND_TYPE db-directory: ${BACKEND_DB_DIRECTORY}"

/opt/opendj/bin/dsconfig create-backend -h localhost -p $ADMIN_PORT --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" \
  --backend-name=userRoot --type $BACKEND_TYPE --set base-dn:$BASE_DN --set "db-directory:$BACKEND_DB_DIRECTORY" \
  --set enabled:true --no-prompt --trustAll || exit 1

if [ "$ADD_BASE_ENTRY" = "--addBaseEntry"  ]; then
  BASE_TEMPLATE=$(mktemp)
  if [ ! -z ${SAMPLE_DATA} ]; then
    echo "generating sample data..."
    /opt/opendj/bin/makeldif -o $BASE_TEMPLATE -c suffix="$BASE_DN" -c numusers=$SAMPLE_DATA /opt/opendj/template/config/MakeLDIF/example.template || exit 1
    /opt/opendj/bin/import-ldif --ldifFile $BASE_TEMPLATE \
        --backendID=userRoot --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" || exit 1
  else
    echo "creating base entry..."
    echo "branch: $BASE_DN" > $BASE_TEMPLATE
    /opt/opendj/bin/import-ldif --templateFile $BASE_TEMPLATE \
        --backendID=userRoot --bindDN "$ROOT_USER_DN" --bindPasswordFile "$PASSWORD_FILE" || exit 1
  fi
  rm $BASE_TEMPLATE
fi


# There are multiple types of ldif files.
# The steps below import ldifs via `ldapmodify`.
# See above for plain copying of ldif files.

if [ -d /opt/opendj/bootstrap/schema/ ]; then
  echo "Loading initial schema:"
  for file in /opt/opendj/bootstrap/schema/*; do
    echo "Loading $file ..."
    /opt/opendj/bin/ldapmodify -D "$ROOT_USER_DN" -h localhost -p $PORT -j "$PASSWORD_FILE" -f "$file"
  done
fi

if [ -d /opt/opendj/bootstrap/data/ ]; then
  # allow pre encoded passwords; the port is named, as the tool would otherwise go to 4444
  # whatever ADMIN_PORT the server listens on, and the entries carrying them would be refused
  /opt/opendj/bin/dsconfig \
    set-password-policy-prop \
    -h localhost \
    -p $ADMIN_PORT \
    --bindDN "$ROOT_USER_DN" \
    --bindPasswordFile "$PASSWORD_FILE" \
    --policy-name "Default Password Policy" \
    --set allow-pre-encoded-passwords:true \
    --trustAll \
    --no-prompt

  for file in /opt/opendj/bootstrap/data/*; do
    echo "Loading $file ..."
    /opt/opendj/bin/ldapmodify -D "$ROOT_USER_DN" -h localhost -p $PORT -j "$PASSWORD_FILE" -f "$file" --continueOnError
  done
fi
