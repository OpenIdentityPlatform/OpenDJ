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
  mkdir "$generation"
  printf '%s\n' "$password" > "$generation/admin-keystore.pin"
  openssl req -new -newkey rsa:2048 -nodes -subj '/CN=OpenDJ admin' \
    -keyout "$work/leaf.key" -out "$work/leaf.csr" 2>/dev/null
  openssl x509 -req -days 2 -set_serial "$serial" -in "$work/leaf.csr" -out "$work/leaf-$serial.pem" \
    -CA "$work/client/ca.pem" -CAkey "$work/ca.key" \
    -extfile /dev/stdin <<< $'subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth'
  openssl pkcs12 -export -name admin-cert -inkey "$work/leaf.key" -in "$work/leaf-$serial.pem" \
    -certfile "$work/client/ca.pem" -out "$generation/admin-keystore" \
    -passout "file:$generation/admin-keystore.pin"
  if [ "$format" = JKS ]; then
    docker run --rm --user "$(id -u):$(id -g)" --entrypoint keytool -v "$generation":/keys "$image" \
      -importkeystore -noprompt -srcalias admin-cert -srcstoretype PKCS12 -srckeystore /keys/admin-keystore \
      -srcstorepass:file /keys/admin-keystore.pin -deststoretype JKS -destkeystore /keys/keystore.jks \
      -deststorepass:file /keys/admin-keystore.pin -destkeypass:file /keys/admin-keystore.pin
    mv -f "$generation/keystore.jks" "$generation/admin-keystore"
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
  docker run -d --memory=512m -e "SECRET_VOLUME_REFRESH=$1" -p 127.0.0.1::4444 --name "$container" \
    -v "$work/secret":/var/secrets/opendj:ro -v "$work/client":/certs:ro \
    -v "$volume":/opt/opendj/data "$image"
  address=$(docker port "$container" 4444/tcp)
  timeout 3m bash -ec '
    until [ "$(docker inspect -f "{{.State.Health.Status}}" "$1")" = healthy ]; do
      [ "$(docker inspect -f "{{.State.Running}}" "$1")" = true ]
      sleep 2
    done
  ' _ "$container"
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
start_container 5
verify_admin_certificate 130701
test "$(docker exec "$container" stat -c %a /opt/opendj/data/config/admin-keystore)" = 600
test "$(docker exec "$container" stat -c %a /opt/opendj/data/config/admin-keystore.pin)" = 600

echo 'Check that the instance restarts with the previous certificate when the PIN arrives first'
make_secret 130702 renewed-password PKCS12
docker stop "$container"
docker rm "$container"
mkdir "$work/secret/..pin-first"
cp "$work/secret/..130701/admin-keystore" "$work/secret/..pin-first/"
cp "$work/secret/..130702/admin-keystore.pin" "$work/secret/..pin-first/"
project_secret pin-first
start_container 5
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
docker stop "$container"
docker rm "$container"
make_secret 130703 replacement-password JKS
project_secret 130703
start_container 0
verify_admin_certificate 130703
