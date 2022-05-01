#!/usr/bin/env bash
# Default setup script

echo "Setting up default OpenDJ instance"

# If any optional LDIF files are present load them

/opt/opendj/setup \
  --cli \
  -h localhost \
  --baseDN $BASE_DN \
  --ldapPort $PORT \
  --ldapsPort $LDAPS_PORT \
  --enableStartTLS $OPENDJ_SSL_OPTIONS \
  --adminConnectorPort $ADMIN_PORT \
  --rootUserDN "$ROOT_USER_DN" \
  --rootUserPassword "$ROOT_PASSWORD" \
  --acceptLicense \
  --no-prompt \
  --noPropertiesFile \
  --doNotStart \
  $ADD_BASE_ENTRY #--sampleData 1

# There are multiple types of ldif files.
# This step makes plain copies.
# See below for imports via `ldapmodify`.
if [ -d /opt/opendj/bootstrap/config/schema/ ]; then
  echo "Copying schema:"
  for file in /opt/opendj/bootstrap/config/schema/*; do
    target_file="/opt/opendj/config/schema/$(basename -- $file)"
    echo "Copying $file to $target_file"
    cp $file $target_file
  done
fi

/opt/opendj/bin/start-ds

# There are multiple types of ldif files.
# The steps below import ldifs via `ldapmodify`.
# See above for plain copying of ldif files.

if [ -d /opt/opendj/bootstrap/schema/ ]; then
  echo "Loading initial schema:"
  for file in /opt/opendj/bootstrap/schema/*; do
    echo "Loading $file ..."
    /opt/opendj/bin/ldapmodify -D "$ROOT_USER_DN" -h localhost -p $PORT -w $ROOT_PASSWORD -f $file
  done
fi

if [ -d /opt/opendj/bootstrap/data/ ]; then
  #allow pre encoded passwords
  /opt/opendj/bin/dsconfig \
    set-password-policy-prop \
    --bindDN "$ROOT_USER_DN" \
    --bindPassword "$ROOT_PASSWORD" \
    --policy-name "Default Password Policy" \
    --set allow-pre-encoded-passwords:true \
    --trustAll \
    --no-prompt

  for file in /opt/opendj/bootstrap/data/*; do
    echo "Loading $file ..."
    /opt/opendj/bin/ldapmodify -D "$ROOT_USER_DN" -h localhost -p $PORT -w $ROOT_PASSWORD -f $file
  done
fi
