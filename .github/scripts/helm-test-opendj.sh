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

set -euo pipefail

image_tag=${1:?Usage: helm-test-opendj.sh <local-image-tag> [all|topology|tls]}
mode=${2:-all}
chart=opendj-packages/opendj-helm
scratch=$(mktemp -d)
namespace=
port_forward_pid=
base_dn=dc=example,dc=com
root_dn='cn=Directory Manager'
wait_timeout=30m
# StatefulSet name and paths match the values installed in each test namespace.
server=opendj-directory
peer_service=$server-headless
root_secret=directory-root
password_path=/tmp/helm-ci-password

stop_forwarding() {
  if [ -n "$port_forward_pid" ]; then
    kill "$port_forward_pid" 2>/dev/null || true
    wait "$port_forward_pid" 2>/dev/null || true
    port_forward_pid=
  fi
}

cleanup() {
  local result=$?
  stop_forwarding
  if [ "$result" -ne 0 ] && [ -n "$namespace" ]; then
    kubectl -n "$namespace" get statefulset,pods,services,endpointslices,pvc -o wide >&2 || true
    kubectl -n "$namespace" get events --sort-by=.lastTimestamp >&2 || true
    kubectl -n "$namespace" logs -l app.kubernetes.io/name=opendj --all-containers --tail=100 >&2 || true
  fi
  rm -rf "$scratch"
}
trap cleanup EXIT

# Both OpenDJ replication and Secret projection converge asynchronously.
wait_for() {
  local seconds=$1 description=$2
  shift 2
  local deadline=$((SECONDS + seconds))
  until "$@"; do
    if (( SECONDS >= deadline )); then
      printf 'Timed out waiting for %s\n' "$description" >&2
      return 1
    fi
    sleep 2
  done
}

k() {
  kubectl -n "$namespace" "$@"
}

new_namespace() {
  namespace="opendj-helm-$1"
  kubectl create namespace "$namespace"
  kubectl label namespace "$namespace" pod-security.kubernetes.io/enforce=restricted
  k create secret generic "$root_secret" --from-file=password="$scratch/password"
}

upgrade() {
  helm upgrade --install directory "$chart" --namespace "$namespace" --wait --timeout "$wait_timeout" \
    --values "$scratch/image-values.yaml" --set-string "auth.existingSecret=$root_secret" "$@"
  k rollout status "statefulset/$server" --timeout="$wait_timeout"
}

ldap() {
  local pod=$1 tool=$2
  shift 2
  k exec "$pod" -- sh -ec '
    umask 077
    printf "%s\n" "$ROOT_PASSWORD" > "$1"
    shift
    exec "$@"
  ' helm-ci "$password_path" "/opt/opendj/bin/$tool" --noPropertiesFile \
    --hostname localhost --port 1389 --bindDN "$root_dn" --bindPasswordFile "$password_path" "$@"
}

add() {
  local pod=$1 ou=$2
  k exec -i "$pod" -- sh -ec '
    umask 077
    printf "%s\n" "$ROOT_PASSWORD" > "$1"
    exec /opt/opendj/bin/ldapmodify --noPropertiesFile --hostname localhost --port 1389 \
      --bindDN "$2" --bindPasswordFile "$1"
  ' helm-ci "$password_path" "$root_dn" <<EOF
dn: ou=$ou,$base_dn
changetype: add
objectClass: top
objectClass: organizationalUnit
ou: $ou

EOF
}

has_dn() {
  local pod=$1 dn=$2
  ldap "$pod" ldapsearch --baseDN "$dn" --searchScope base '(objectClass=*)' dn 2>/dev/null |
    grep -Fqx "dn: $dn"
}

join_ready() {
  ldap "$1" ldapsearch --baseDN 'cn=Docker Join,cn=config' --searchScope base \
    '(objectClass=*)' description 2>/dev/null | grep -Fqx 'description: ready combined'
}

replicates() {
  local from=$1 to=$2 ou=$3
  add "$from" "$ou"
  wait_for 150 "$to to receive $ou" has_dn "$to" "ou=$ou,$base_dn"
}

uid() {
  k get "$1" "$2" -o jsonpath='{.metadata.uid}' 2>/dev/null
}

pod_recreated() {
  local pod=$1 old_uid=$2 current_uid
  current_uid=$(uid pod "$pod") || return 1
  [ -n "$current_uid" ] && [ "$current_uid" != "$old_uid" ]
}

replace_pod() {
  local pod=$1 old_uid
  old_uid=$(uid pod "$pod")
  k delete pod "$pod" --wait=false
  wait_for 180 "$pod replacement" pod_recreated "$pod" "$old_uid"
  k wait --for=condition=Ready "pod/$pod" --timeout="$wait_timeout"
}

client_ready() {
  k get endpointslices -l "kubernetes.io/service-name=$server" -o json |
    jq -e --arg pod "$1" 'any(.items[].endpoints[]?; .targetRef.name == $pod and .conditions.ready == true)' >/dev/null
}

peer_unregistered() {
  local node=$1 peer=$2 registrations lists
  registrations=$(ldap "$node" ldapsearch --baseDN 'cn=Servers,cn=admin data' \
    "(hostname=$peer)" dn 2>/dev/null) || return 1
  lists=$(ldap "$node" ldapsearch --baseDN cn=config '(ds-cfg-replication-server=*)' \
    ds-cfg-replication-server 2>/dev/null) || return 1
  ! grep -q '^dn:' <<<"$registrations" && ! grep -Fq "$peer:8989" <<<"$lists"
}

grow() {
  local from=$1 to=$2
  upgrade --set replication.enabled=true --set "replicaCount=$from,replication.peerCount=$to"
  upgrade --set replication.enabled=true --set "replicaCount=$to"
}

shrink() {
  local from=$1 to=$2 i j
  # This fixture is the only client. Every add in the suite has already been read back.
  upgrade --set replication.enabled=true --set "replicaCount=$to,replication.peerCount=$from"
  for ((i = to; i < from; i++)); do
    k wait --for=delete "pod/$server-$i" --timeout="$wait_timeout"
  done
  upgrade --set replication.enabled=true --set "replicaCount=$to"
  for ((i = to; i < from; i++)); do
    for ((j = 0; j < to; j++)); do
      wait_for 180 "peer $i to leave the lists of peer $j" peer_unregistered \
        "$server-$j" "$server-$i.$peer_service"
    done
  done
}

password_guard_failed() {
  k logs "$server-0" 2>/dev/null | grep -Fq 'root password Secret must contain a non-empty'
}

test_topology() {
  local old_pod old_pvc third_claim_uid
  printf '\nCheck that an empty root Secret stops setup\n'
  new_namespace empty-password
  k create secret generic empty-root --from-literal=password=''
  helm install directory "$chart" --namespace "$namespace" --values "$scratch/image-values.yaml" \
    --set-string auth.existingSecret=empty-root
  wait_for 90 'the empty password diagnostic' password_guard_failed
  if client_ready "$server-0"; then
    echo 'A pod with an empty password became a ready client endpoint' >&2
    exit 1
  fi
  helm uninstall directory --namespace "$namespace"

  printf '\nCheck standalone, a retained claim, and the last replica\n'
  new_namespace last-replica
  upgrade
  helm test directory --namespace "$namespace" --timeout 2m
  has_dn "$server-0" "$base_dn"
  add "$server-0" standalone
  replace_pod "$server-0"
  has_dn "$server-0" "ou=standalone,$base_dn"
  upgrade --set replication.enabled=true
  wait_for 180 'the standalone server to publish its replication seed state' join_ready "$server-0"
  grow 1 2
  has_dn "$server-1" "ou=standalone,$base_dn"
  replicates "$server-1" "$server-0" first-join
  shrink 2 1
  has_dn "$server-0" "ou=first-join,$base_dn"
  add "$server-0" while-alone
  grow 1 2
  wait_for 180 'peer 1 to catch up after scale-out from one replica' \
    has_dn "$server-1" "ou=while-alone,$base_dn"
  replicates "$server-1" "$server-0" last-peer-returned
  helm uninstall directory --namespace "$namespace" --wait --timeout "$wait_timeout"

  printf '\nCheck two-way replication and PVC recovery\n'
  new_namespace resize
  upgrade --set replication.enabled=true,replicaCount=2,pdb.enabled=true
  replicates "$server-0" "$server-1" from-zero
  replicates "$server-1" "$server-0" from-one
  old_pvc=$(uid pvc "data-$server-0")
  replace_pod "$server-0"
  [ "$(uid pvc "data-$server-0")" = "$old_pvc" ]
  has_dn "$server-0" "ou=from-one,$base_dn"

  old_pod=$(uid pod "$server-0")
  k delete pvc "data-$server-0" --wait=false
  k delete pod "$server-0" --wait=false
  wait_for 180 'replacement of pod 0 after PVC removal' pod_recreated "$server-0" "$old_pod"
  wait_for 180 'DNS for the recovering peer' k exec "$server-1" -- getent hosts "$server-0.$peer_service"
  if client_ready "$server-0"; then
    echo 'A pod with an empty PVC was routed before initialization' >&2
    exit 1
  fi
  k wait --for=condition=Ready "pod/$server-0" --timeout="$wait_timeout"
  [ "$(uid pvc "data-$server-0")" != "$old_pvc" ]
  has_dn "$server-0" "ou=from-zero,$base_dn"
  has_dn "$server-0" "ou=from-one,$base_dn"
  replicates "$server-0" "$server-1" recovered

  printf '\nCheck upgrade, scale-out, scale-in and rejoin from the retained PVC\n'
  upgrade --set replication.enabled=true,replicaCount=2,terminationGracePeriodSeconds=65,pdb.enabled=true
  has_dn "$server-1" "ou=recovered,$base_dn"
  grow 2 3
  has_dn "$server-2" "ou=recovered,$base_dn"
  replicates "$server-2" "$server-0" from-third
  third_claim_uid=$(uid pvc "data-$server-2")
  shrink 3 2
  [ "$(uid pvc "data-$server-2")" = "$third_claim_uid" ]
  if client_ready "$server-2"; then
    echo 'A removed replica still has a ready endpoint' >&2
    exit 1
  fi
  replicates "$server-0" "$server-1" after-shrink
  replicates "$server-1" "$server-0" while-third-away
  grow 2 3
  [ "$(uid pvc "data-$server-2")" = "$third_claim_uid" ]
  wait_for 180 'the retained third replica to receive updates missed while down' \
    has_dn "$server-2" "ou=while-third-away,$base_dn"
  replicates "$server-0" "$server-2" after-regrowth
  replicates "$server-2" "$server-1" from-rejoined
  helm test directory --namespace "$namespace" --timeout 2m
  helm uninstall directory --namespace "$namespace" --wait --timeout "$wait_timeout"
}

update_tls_secret() {
  local connector=$1 serial=$2 host="$server-0.$peer_service"
  local alias=server-cert name=keystore key=keystore.p12
  if [ "$connector" = admin ]; then
    alias=admin-cert
    name=admin-keystore
    key=keystore.jks
  fi
  printf 'opendj-ci-pin-%s\n' "$serial" > "$scratch/$name.pin"
  printf 'extendedKeyUsage=serverAuth\nsubjectAltName=DNS:%s' "$host" > "$scratch/leaf.ext"
  # Make this certificate alone larger than the handshake flight that failed in 5.2.0.
  for ((i = 0; i < 170; i++)); do
    printf ',DNS:san-%d.an-additional-hostname-for-opendj-helm.test' "$i" >> "$scratch/leaf.ext"
  done
  printf '\n' >> "$scratch/leaf.ext"
  openssl req -new -key "$scratch/leaf.key" -subj "/CN=$host" -out "$scratch/leaf.csr"
  openssl x509 -req -days 2 -set_serial "$serial" -in "$scratch/leaf.csr" \
    -CA "$scratch/ca.pem" -CAkey "$scratch/ca.key" -extfile "$scratch/leaf.ext" -out "$scratch/leaf.pem"
  [ "$(openssl x509 -in "$scratch/leaf.pem" -outform DER | wc -c)" -gt 6144 ]
  openssl pkcs12 -export -name "$alias" -inkey "$scratch/leaf.key" -in "$scratch/leaf.pem" \
    -certfile "$scratch/ca.pem" -out "$scratch/leaf.p12" -passout "file:$scratch/$name.pin"
  if [ "$connector" = admin ]; then
    rm -f "$scratch/$name"
    keytool -importkeystore -noprompt -srcalias "$alias" -srcstoretype PKCS12 \
      -srckeystore "$scratch/leaf.p12" -srcstorepass:file "$scratch/$name.pin" \
      -deststoretype JKS -destkeystore "$scratch/$name" \
      -deststorepass:file "$scratch/$name.pin" -destkeypass:file "$scratch/$name.pin"
  else
    mv "$scratch/leaf.p12" "$scratch/$name"
  fi
  k create secret generic "$connector-tls" --from-file="$key=$scratch/$name" \
    --from-file="password=$scratch/$name.pin" --dry-run=client -o yaml | k apply -f -
}

projected_tls_pairs_match() {
  local file
  for file in keystore keystore.pin admin-keystore admin-keystore.pin; do
    k exec "$server-0" -- cat "/var/secrets/opendj/$file" | cmp -s "$scratch/$file" - || return 1
  done
}

forward_ports() {
  stop_forwarding
  k port-forward --address 127.0.0.1 "pod/$server-0" :1636 :4444 >"$scratch/forward.log" 2>&1 &
  port_forward_pid=$!
  wait_for 60 'port forwards for LDAP and administration' forwards_ready
}

forwards_ready() {
  ldap_port=$(sed -n 's/^Forwarding from 127[.]0[.]0[.]1:\([0-9]*\) -> 1636$/\1/p' "$scratch/forward.log")
  admin_port=$(sed -n 's/^Forwarding from 127[.]0[.]0[.]1:\([0-9]*\) -> 4444$/\1/p' "$scratch/forward.log")
  [ -n "$ldap_port" ] && [ -n "$admin_port" ]
}

serves_serial() {
  local port=$1 serial
  serial=$(timeout 12s openssl s_client -connect "127.0.0.1:$port" \
    -servername "$server-0.$peer_service" -verify_hostname "$server-0.$peer_service" \
    -verify_return_error -CAfile "$scratch/ca.pem" </dev/null 2>/dev/null |
    openssl x509 -noout -serial 2>/dev/null) || return 1
  [ "$serial" = "serial=$(printf '%02X' "$2")" ]
}

verify_admin() {
  local pod=$server-0
  k exec -i "$pod" -- sh -ec 'umask 077; cat > /tmp/helm-ci-ca.jks' < "$scratch/ca.jks"
  k exec -i "$pod" -- sh -ec 'umask 077; cat > /tmp/helm-ci-ca.pin' < "$scratch/ca.pin"
  k exec "$pod" -- sh -ec '
    umask 077
    printf "%s\n" "$ROOT_PASSWORD" > "$1"
    exec /opt/opendj/bin/dsconfig get-administration-connector-prop \
      --hostname "$2" --port 4444 --bindDN "$3" --bindPasswordFile "$1" \
      --trustStorePath /tmp/helm-ci-ca.jks --trustStorePasswordFile /tmp/helm-ci-ca.pin --no-prompt
  ' helm-ci "$password_path" "$pod.$peer_service" "$root_dn"
}

test_tls() {
  local original_pod original_claim
  printf '\nCheck the CA and DNS name for both listeners and rotate the projected Secrets\n'
  new_namespace tls
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj '/CN=OpenDJ Helm CI' \
    -addext 'basicConstraints=critical,CA:TRUE' -addext 'keyUsage=critical,keyCertSign' \
    -keyout "$scratch/ca.key" -out "$scratch/ca.pem" > /dev/null 2>&1
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$scratch/leaf.key" 2>/dev/null
  printf 'opendj-ci-trust\n' > "$scratch/ca.pin"
  keytool -importcert -noprompt -alias helm-ci -file "$scratch/ca.pem" -keystore "$scratch/ca.jks" \
    -storetype JKS -storepass:file "$scratch/ca.pin"
  cat > "$scratch/tls-values.yaml" <<'EOF'
tls:
  refreshSeconds: 3
  ldap:
    enabled: true
    keystoreSecret:
      name: ldap-tls
    passwordSecret:
      name: ldap-tls
  admin:
    enabled: true
    keystoreSecret:
      name: admin-tls
    passwordSecret:
      name: admin-tls
EOF
  update_tls_secret ldap 101
  update_tls_secret admin 102
  upgrade --values "$scratch/tls-values.yaml"
  original_claim=$(uid pvc "data-$server-0")
  original_pod=$(uid pod "$server-0")
  forward_ports
  wait_for 60 'the initial LDAPS certificate' serves_serial "$ldap_port" 101
  wait_for 60 'the initial admin certificate' serves_serial "$admin_port" 102
  verify_admin
  update_tls_secret ldap 111
  update_tls_secret admin 112
  wait_for 240 'the renewed LDAPS certificate and PIN' serves_serial "$ldap_port" 111
  wait_for 240 'the renewed admin certificate and PIN' serves_serial "$admin_port" 112
  [ "$(uid pod "$server-0")" = "$original_pod" ]
  verify_admin

  stop_forwarding
  upgrade --values "$scratch/tls-values.yaml" --set tls.refreshSeconds=0
  original_pod=$(uid pod "$server-0")
  forward_ports
  wait_for 60 'the LDAPS certificate after rollout' serves_serial "$ldap_port" 111
  wait_for 60 'the admin certificate after rollout' serves_serial "$admin_port" 112
  update_tls_secret ldap 121
  update_tls_secret admin 122
  wait_for 240 'both replacement keystore and PIN pairs to be projected' projected_tls_pairs_match
  [ "$(uid pod "$server-0")" = "$original_pod" ]
  if ! k exec "$server-0" -- sh -ec 'test "${SECRET_VOLUME_REFRESH-}" = 0'; then
    echo 'Expected SECRET_VOLUME_REFRESH=0 in the OpenDJ container' >&2
    exit 1
  fi
  serves_serial "$ldap_port" 111
  serves_serial "$admin_port" 112
  stop_forwarding
  replace_pod "$server-0"
  [ "$(uid pvc "data-$server-0")" = "$original_claim" ]
  forward_ports
  wait_for 60 'the LDAPS certificate loaded on restart' serves_serial "$ldap_port" 121
  wait_for 60 'the admin certificate loaded on restart' serves_serial "$admin_port" 122
  verify_admin
  stop_forwarding
  helm uninstall directory --namespace "$namespace" --wait --timeout "$wait_timeout"
}

umask 077
printf '%s' "$(openssl rand -hex 20)" > "$scratch/password"
cat > "$scratch/image-values.yaml" <<EOF
image:
  repository: opendj-helm-ci
  tag: "$image_tag"
  pullPolicy: Never
EOF
if [ "$mode" = all ] || [ "$mode" = topology ]; then
  test_topology
fi
if [ "$mode" = all ] || [ "$mode" = tls ]; then
  test_tls
fi
