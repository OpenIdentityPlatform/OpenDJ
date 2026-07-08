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
#
# Compare two OpenDJ Docker images with the LDAP benchmark (.github/benchmark/benchmark.jmx) and
# append the comparison report to $GITHUB_STEP_SUMMARY. Both sides are OpenDJ, so no per-server
# hashing/index setup is needed (identical product => identical default password scheme).
#
# Usage:
#   compare-opendj.sh <A_name> <A_image> <B_name> <B_image>
# Env: THREADS (default 200), DURATION (default 150), JMETER_VERSION (default 5.6.3).
set -euo pipefail

A_NAME="${1:?server A name required}"
A_IMAGE="${2:?server A image required}"
B_NAME="${3:?server B name required}"
B_IMAGE="${4:?server B image required}"

THREADS="${THREADS:-200}"
DURATION="${DURATION:-150}"
JMETER_VERSION="${JMETER_VERSION:-5.6.3}"
BASEDN="dc=example,dc=com"
BENCHPW="benchPass1"
HERE="$(cd "$(dirname "$0")" && pwd)"   # .github/benchmark

# ---------------------------------------------------------------- dependencies
if ! command -v ldapsearch >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq ldap-utils jq
fi
JM="$HOME/jmeter/apache-jmeter-$JMETER_VERSION/bin/jmeter"
if [ ! -x "$JM" ]; then
  mkdir -p "$HOME/jmeter"
  curl -fsSL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$JMETER_VERSION.tgz" -o /tmp/jmeter.tgz
  tar -xzf /tmp/jmeter.tgz -C "$HOME/jmeter"
fi

wait_dj() {  # poll OpenDJ readiness on localhost:1389
  for _ in $(seq 1 90); do
    ldapsearch -x -H ldap://localhost:1389 -D "cn=Directory Manager" -w password \
      -b "$BASEDN" -s base dn >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}

# bench_one <image> <out-slug>  -> prints the captured server version (stdout only)
bench_one() {
  local image="$1" out="$2" ver=""
  docker rm -f opendj-bench >/dev/null 2>&1 || true
  if docker run -d --name opendj-bench -p 1389:1389 \
       -e ROOT_PASSWORD=password -e BASE_DN="$BASEDN" -e ADD_BASE_ENTRY=--addBaseEntry \
       "$image" >/dev/null 2>&1; then
    wait_dj || echo "WARN: $image not ready in time" >&2
    ldapadd -x -H ldap://localhost:1389 -D "cn=Directory Manager" -w password \
      -f "$HERE/people.ldif" >/dev/null 2>&1 || true
    ver="$( { ldapsearch -x -LLL -H ldap://localhost:1389 -D 'cn=Directory Manager' -w password \
              -b '' -s base fullVendorVersion 2>/dev/null || true; } | sed -n 's/^fullVendorVersion: //p')"
    rm -rf "$out" "$out.jtl"
    HEAP="-Xms1g -Xmx2g" "$JM" -n -t "$HERE/benchmark.jmx" \
      -Jhost=localhost -Jport=1389 -Jbasedn="$BASEDN" \
      -Jadminbinddn="cn=Directory Manager" -Jadminbindpw=password -Jbenchpw="$BENCHPW" \
      -Jthreads="$THREADS" -Jduration="$DURATION" -Jrampup=0 \
      -Jjmeter.reportgenerator.sample_filter='^(?!ADMIN_CONNECT).*' \
      -l "$out.jtl" -e -o "$out" > "$out.jmeter.out" 2>&1 || true
    docker logs opendj-bench > "$out.docker.log" 2>&1 || true
    # surface distinct error messages to the step log (stderr; stdout carries the version)
    if [ -f "$out.jtl" ]; then
      local errs
      errs="$(awk -F',' 'NR==1{for(i=1;i<=NF;i++)h[$i]=i; next}
                         tolower($h["success"])=="false"{print $h["label"]" | "$h["responseCode"]" | "$h["responseMessage"]}' \
              "$out.jtl" 2>/dev/null | sort | uniq -c | sort -rn | head -10)"
      [ -z "$errs" ] || { echo "[$out] errors (count | op | code | message):" >&2; echo "$errs" >&2; }
    fi
  else
    echo "ERROR: failed to start image $image" >&2
  fi
  docker rm -f opendj-bench >/dev/null 2>&1 || true
  [ -n "$ver" ] || ver="$image"
  printf '%s' "$ver"
}

echo "Benchmarking ${A_NAME} (${A_IMAGE}) @ ${THREADS} threads / ${DURATION}s ..."
A_VER="$(bench_one "$A_IMAGE" a)"
echo "Benchmarking ${B_NAME} (${B_IMAGE}) @ ${THREADS} threads / ${DURATION}s ..."
B_VER="$(bench_one "$B_IMAGE" b)"

{
  bash "$HERE/summary.sh" \
    "$A_NAME" a/statistics.json "$A_VER" "$A_IMAGE" \
    "$B_NAME" b/statistics.json "$B_VER" "$B_IMAGE"
  echo ""
  echo "### Notes"
  echo ""
  echo "- **${A_NAME}** = freshly built image; **${B_NAME}** = latest released image. Both are"
  echo "  OpenDJ, so they share the same default password storage scheme (hashing parity is automatic)."
  echo "- The admin connection bind (\`ADMIN_CONNECT\`) is cached per thread and excluded; \`BIND\` is"
  echo "  the measured user authentication (\`test=sbind\`, single bind/unbind)."
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"
