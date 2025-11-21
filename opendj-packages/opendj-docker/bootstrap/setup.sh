#!/usr/bin/env bash
# Default setup script

echo "Setting up default OpenDJ instance"

# If any optional LDIF files are present load them

# There are multiple types of ldif files.
# This step makes plain copies.
# See below for imports via `ldapmodify`.
if [ -d /opt/opendj/bootstrap/config/schema/ ]; then
  echo "Copying schema:"
  mkdir -p /opt/opendj/template/config/schema
  for file in /opt/opendj/bootstrap/config/schema/*; do
    target_file="/opt/opendj/template/config/schema/$(basename -- $file)"
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
  --rootUserPassword "$ROOT_PASSWORD" \
  --acceptLicense \
  --no-prompt \
  --noPropertiesFile \
  $SETUP_ARGS

BACKEND_TYPE=${BACKEND_TYPE:-je}
BACKEND_DB_DIRECTORY=${BACKEND_DB_DIRECTORY:-db}
echo "creating backend: $BACKEND_TYPE db-directory: ${BACKEND_DB_DIRECTORY}"

/opt/opendj/bin/dsconfig create-backend -h localhost -p $ADMIN_PORT --bindDN "$ROOT_USER_DN" --bindPassword "$ROOT_PASSWORD" \
  --backend-name=userRoot --type $BACKEND_TYPE --set base-dn:$BASE_DN --set "db-directory:$BACKEND_DB_DIRECTORY" \
  --set enabled:true --no-prompt --trustAll

if [ "$ADD_BASE_ENTRY" = "--addBaseEntry"  ]; then
  BASE_TEMPLATE=$(mktemp)
  if [ ! -z ${SAMPLE_DATA} ]; then
    echo "generating sample data..."
    /opt/opendj/bin/makeldif -o $BASE_TEMPLATE -c suffix="$BASE_DN" -c numusers=$SAMPLE_DATA /opt/opendj/template/config/MakeLDIF/example.template
    /opt/opendj/bin/import-ldif --ldifFile $BASE_TEMPLATE \
        --backendID=userRoot --bindDN "$ROOT_USER_DN" --bindPassword "$ROOT_PASSWORD"
  else
    echo "creating base entry..."
    BASE_TEMPLATE=$(mktemp)
    echo "branch: $BASE_DN" > $BASE_TEMPLATE
    /opt/opendj/bin/import-ldif --templateFile $BASE_TEMPLATE \
        --backendID=userRoot --bindDN "$ROOT_USER_DN" --bindPassword "$ROOT_PASSWORD"
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
