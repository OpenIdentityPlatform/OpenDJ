#!/usr/bin/env bash
# Default setup script

echo "Setting up default OpenDJ instance"

# If any optional LDIF files are present load them

/opt/opendj/setup --cli -p $PORT --ldapsPort $LDAPS_PORT --enableStartTLS $OPENDJ_SSL_OPTIONS \
  --baseDN $BASE_DN -h localhost --rootUserDN "$ROOT_USER_DN" --rootUserPassword "$ROOT_PASSWORD" \
  --acceptLicense --no-prompt  $ADD_BASE_ENTRY #--sampleData 1

if [ -d /opt/opendj/bootstrap/schema/ ]; then
  echo "Loading initial schema:"
  for file in /opt/opendj/bootstrap/schema/*;  do
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

  for file in /opt/opendj/bootstrap/data/*;  do
      echo "Loading $file ..."
      /opt/opendj/bin/ldapmodify -D "$ROOT_USER_DN" -h localhost -p $PORT -w $ROOT_PASSWORD -f $file
  done
fi
