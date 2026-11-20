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
# Copyright 2026 3A Systems, LLC.

set -eo pipefail

image=${1:?usage: $0 <image>}
work=$(mktemp -d)
container="test-admin-certificate-${work##*.}"
volume="$container-data"
secret_mount=(-v "$work/secret":/var/secrets/opendj:ro)

cleanup() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    docker logs "$container" >&2 || true
  fi
  docker rm -f "$container" >/dev/null 2>&1 || true
  docker volume rm "$volume" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

mkdir "$work/secret" "$work/client"
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj '/CN=OpenDJ test CA' \
  -addext 'basicConstraints=critical,CA:TRUE' -addext 'keyUsage=critical,keyCertSign' \
  -keyout "$work/ca.key" -out "$work/client/ca.pem" 2>/dev/null
printf '%s\n' changeit > "$work/client/ca.pin"
docker run --rm --user "$(id -u):$(id -g)" --entrypoint keytool -v "$work/client":/certs "$image" \
  -importcert -noprompt -alias test-ca -file /certs/ca.pem -keystore /certs/ca.jks \
  -storetype JKS -storepass:file /certs/ca.pin

make_secret() {
  local serial=$1 password=$2 format=$3 generation="$work/secret/..$1"
  local usage=${4:-extendedKeyUsage=serverAuth}
  mkdir "$generation"
  printf '%s\n' "$password" > "$generation/admin-keystore.pin"
  openssl req -new -newkey rsa:2048 -nodes -subj '/CN=OpenDJ admin' \
    -keyout "$work/leaf.key" -out "$work/leaf.csr" 2>/dev/null
  openssl x509 -req -days 2 -set_serial "$serial" -in "$work/leaf.csr" -out "$work/leaf-$serial.pem" \
    -CA "$work/client/ca.pem" -CAkey "$work/ca.key" \
    -extfile /dev/stdin <<< $'subjectAltName=DNS:localhost\n'"$usage"
  openssl pkcs12 -export -name admin-cert -inkey "$work/leaf.key" -in "$work/leaf-$serial.pem" \
    -certfile "$work/client/ca.pem" -out "$generation/admin-keystore" \
    -passout "file:$generation/admin-keystore.pin"
  if [ "$format" != PKCS12 ]; then
    docker run --rm --user "$(id -u):$(id -g)" --entrypoint keytool -v "$generation":/keys "$image" \
      -importkeystore -noprompt -srcalias admin-cert -srcstoretype PKCS12 -srckeystore /keys/admin-keystore \
      -srcstorepass:file /keys/admin-keystore.pin -deststoretype "$format" -destkeystore /keys/converted-keystore \
      -deststorepass:file /keys/admin-keystore.pin -destkeypass:file /keys/admin-keystore.pin
    mv -f "$generation/converted-keystore" "$generation/admin-keystore"
  fi
  chmod 644 "$generation/admin-keystore"
}

project_secret() {
  # Kubernetes switches both filenames by replacing the shared ..data symlink.
  ln -s "..$1" "$work/secret/..data-new"
  python3 -c 'import os, sys; os.replace(sys.argv[1], sys.argv[2])' \
    "$work/secret/..data-new" "$work/secret/..data"
}

start_container() {
  local refresh=$1
  shift
  docker run -d --memory=512m -e "SECRET_VOLUME_REFRESH=$refresh" -p 127.0.0.1::4444 --name "$container" \
    -v "$work/client":/certs:ro -v "$volume":/opt/opendj/data "$@" "$image"
  address=$(docker port "$container" 4444/tcp)
  timeout 3m bash -ec '
    until [ "$(docker inspect -f "{{.State.Health.Status}}" "$1")" = healthy ]; do
      [ "$(docker inspect -f "{{.State.Running}}" "$1")" = true ]
      sleep 2
    done
  ' _ "$container"
}

stop_container() {
  docker stop "$container"
  docker rm "$container"
}

edit_stopped_volume() {
  docker run --rm --entrypoint bash -v "$volume":/opt/opendj/data \
    -v "$work/secret":/fixtures:ro "$image" -ec "$1"
}

dsconfig() {
  docker exec "$container" /opt/opendj/bin/dsconfig get-administration-connector-prop \
    --hostname "$1" --port 4444 --bindDN 'cn=Directory Manager' --bindPassword password \
    --trustStorePath /certs/ca.jks --trustStorePasswordFile /certs/ca.pin --no-prompt "${@:2}"
}

verify_admin_certificate() {
  local expected served
  expected=$(openssl x509 -in "$work/leaf-$1.pem" -noout -serial)
  served=$(timeout 10 openssl s_client -connect "$address" -servername localhost -verify_hostname localhost \
    -verify_return_error -CAfile "$work/client/ca.pem" </dev/null | openssl x509 -noout -serial)
  if [ "$served" != "$expected" ]; then
    echo "Expected $expected on port 4444, got $served" >&2
    return 1
  fi
  dsconfig localhost
}

ln -s ..data/admin-keystore "$work/secret/admin-keystore"
ln -s ..data/admin-keystore.pin "$work/secret/admin-keystore.pin"
echo 'Check a fresh instance with the mounted administration certificate'
make_secret 130701 initial-password JKS
project_secret 130701
start_container 5 "${secret_mount[@]}"
verify_admin_certificate 130701
test "$(docker exec "$container" stat -c %a /opt/opendj/data/config/admin-keystore)" = 600
test "$(docker exec "$container" stat -c %a /opt/opendj/data/config/admin-keystore.pin)" = 600

echo 'Check that the instance restarts with the previous certificate when the PIN arrives first'
make_secret 130702 renewed-password PKCS12
stop_container
mkdir "$work/secret/..pin-first"
cp "$work/secret/..130701/admin-keystore" "$work/secret/..pin-first/"
cp "$work/secret/..130702/admin-keystore.pin" "$work/secret/..pin-first/"
project_secret pin-first
start_container 5 "${secret_mount[@]}"
verify_admin_certificate 130701

echo 'Check that the running instance serves a renewed certificate with a new password'
project_secret 130702
timeout 1m bash -c 'until docker exec "$1" sh -c "
  cd /opt/opendj/data/config &&
  cmp -s /var/secrets/opendj/admin-keystore admin-keystore &&
  cmp -s /var/secrets/opendj/admin-keystore.pin admin-keystore.pin"; do sleep 2; done' _ "$container"
verify_admin_certificate 130702
if dsconfig 127.0.0.1 --quiet; then
  echo 'dsconfig accepted a certificate without a SAN for 127.0.0.1' >&2
  exit 1
fi

echo 'Check a replacement container with a JKS keystore, the existing data volume and the watcher disabled'
stop_container
make_secret 130703 replacement-password JKS
project_secret 130703
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130703

echo 'Check that an incompatible JCEKS preserves the installed pair on a reused volume'
stop_container
make_secret 130704 incompatible-password JCEKS
project_secret 130704
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130703

echo 'Check that a second private key with a different PIN preserves the installed pair'
stop_container
make_secret 130706 shared-password JKS
docker run --rm --user "$(id -u):$(id -g)" --entrypoint keytool \
  -v "$work/secret/..130706":/keys "$image" \
  -genkeypair -alias auxiliary -keyalg RSA -dname CN=auxiliary -storetype JKS \
  -keystore /keys/admin-keystore -storepass:file /keys/admin-keystore.pin -keypass different-password
project_secret 130706
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130703

echo 'Check that a zero-byte PIN preserves the installed pair'
stop_container
make_secret 130707 '' PKCS12
mkdir "$work/secret/..empty-pin"
cp "$work/secret/..130707/admin-keystore" "$work/secret/..empty-pin/"
: > "$work/secret/..empty-pin/admin-keystore.pin"
project_secret empty-pin
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130703

echo 'Check that an empty first PIN line still works'
stop_container
project_secret 130707
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130707

echo 'Check that an auxiliary key without signature support permits installation'
stop_container
make_secret 130708 shared-password JKS
openssl genpkey -algorithm X25519 -out "$work/auxiliary.key"
openssl pkey -in "$work/auxiliary.key" -pubout -out "$work/auxiliary.pub"
openssl x509 -req -days 2 -set_serial 130709 -in "$work/leaf.csr" \
  -force_pubkey "$work/auxiliary.pub" -CA "$work/client/ca.pem" -CAkey "$work/ca.key" \
  -out "$work/auxiliary.pem"
openssl pkcs12 -export -name auxiliary -inkey "$work/auxiliary.key" -in "$work/auxiliary.pem" \
  -out "$work/secret/..130708/auxiliary.p12" -passout "file:$work/secret/..130708/admin-keystore.pin"
docker run --rm --user "$(id -u):$(id -g)" --entrypoint keytool \
  -v "$work/secret/..130708":/keys "$image" \
  -importkeystore -noprompt -srcalias auxiliary -srcstoretype PKCS12 -srckeystore /keys/auxiliary.p12 \
  -srcstorepass:file /keys/admin-keystore.pin -deststoretype JKS -destkeystore /keys/admin-keystore \
  -deststorepass:file /keys/admin-keystore.pin -destkeypass:file /keys/admin-keystore.pin
rm "$work/secret/..130708/auxiliary.p12"
project_secret 130708
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130708

echo 'Check that an administration key without signature support preserves the installed pair'
stop_container
cp -R "$work/secret/..130708" "$work/secret/..non-signing-admin"
docker run --rm --user "$(id -u):$(id -g)" --entrypoint sh \
  -v "$work/secret/..non-signing-admin":/keys "$image" -ec '
    cd /keys
    keytool -changealias -alias admin-cert -destalias rsa-backup -storetype JKS \
      -keystore admin-keystore -storepass:file admin-keystore.pin -keypass:file admin-keystore.pin
    keytool -changealias -alias auxiliary -destalias admin-cert -storetype JKS \
      -keystore admin-keystore -storepass:file admin-keystore.pin -keypass:file admin-keystore.pin
  '
project_secret non-signing-admin
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130708

echo 'Check that an intact pending pair is validated before recovery'
stop_container
edit_stopped_volume '
  cd /opt/opendj/data/config
  mkdir -m 700 .admin-secret-pending
  install -m 600 /fixtures/..non-signing-admin/admin-keystore /fixtures/..non-signing-admin/admin-keystore.pin .admin-secret-pending/
'
start_container 0
verify_admin_certificate 130708
docker exec "$container" test ! -e /opt/opendj/data/config/.admin-secret-pending

echo 'Check that a certificate without digital signature usage preserves the installed pair'
stop_container
make_secret 130710 shared-password PKCS12 $'keyUsage=keyAgreement\nextendedKeyUsage=serverAuth'
project_secret 130710
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130708

echo 'Check that a client certificate preserves the installed pair'
stop_container
make_secret 130711 shared-password PKCS12 'extendedKeyUsage=clientAuth'
project_secret 130711
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130708

echo 'Check that different private key and certificate algorithms preserve the installed pair'
stop_container
make_secret 130712 shared-password JKS
java "$(dirname -- "${BASH_SOURCE[0]}")/ReencodeAdminSecretKey.java" \
  "$work/secret/..130712/admin-keystore" "$work/secret/..130712/admin-keystore.pin"
project_secret 130712
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130708

echo 'Check interrupted installation with a queued change and without a Secret mount'
stop_container
make_secret 130705 recovered-password JKS
edit_stopped_volume '
  cd /opt/opendj/data/config
  mkdir -m 700 .admin-secret-pending .admin-secret.stale1
  install -m 600 /fixtures/..130705/admin-keystore admin-keystore
  install -m 600 /fixtures/..130705/admin-keystore.pin .admin-secret-pending/admin-keystore.pin
  install -m 600 /fixtures/..130704/admin-keystore /fixtures/..130704/admin-keystore.pin .admin-secret.stale1/
  cat > config-changes.ldif <<EOF
dn: cn=Administration Connector,cn=config
changetype: modify
replace: ds-cfg-ssl-cert-nickname
ds-cfg-ssl-cert-nickname: admin-cert
-
EOF
'
start_container 0
verify_admin_certificate 130705
docker exec "$container" sh -ec '
  cd /opt/opendj/data/config
  test ! -e .admin-secret-pending
  test ! -e .admin-secret.stale1
  test ! -e config-changes.ldif
'

echo 'Check that a queued administration certificate nickname change preserves the installed pair'
stop_container
edit_stopped_volume '
  cd /opt/opendj/data/config
  keytool -changealias -alias admin-cert -destalias custom-admin -storetype JKS \
    -keystore admin-keystore -storepass:file admin-keystore.pin -keypass:file admin-keystore.pin
  mkdir -m 700 .admin-secret-pending
  install -m 600 /fixtures/..130703/admin-keystore /fixtures/..130703/admin-keystore.pin .admin-secret-pending/
  cat > config-changes.ldif <<EOF
dn: cn=Administration Connector,cn=config
changetype: modify
replace: ds-cfg-ssl-cert-nickname
ds-cfg-ssl-cert-nickname: custom-admin
-
EOF
'
project_secret 130703
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130705

docker exec "$container" sh -ec '
  cd /opt/opendj/data/config
  test ! -e config-changes.ldif
  test ! -e .admin-secret-pending
'

echo 'Check that a reused volume keeps its configured administration certificate nickname'
stop_container
start_container 0 "${secret_mount[@]}"
verify_admin_certificate 130705
