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

# The container health check
#
# The marker comes first: run.sh writes it only once the bootstrap has succeeded, and testing
# it also keeps the probe from launching a JVM every interval until then.
#
# The probe then reads the root DSE with the attribute list 1.1, which needs no bind. It must
# not bind as the root user: ROOT_PASSWORD is only the initial root password, so the probe
# would turn the container unhealthy for good once an operator changes it, and it would put
# the password on a command line every interval. An instance configured to reject
# unauthenticated requests answers the anonymous search with 53 (Unwilling to Perform); for
# such an instance HEALTHCHECK_BIND_DN names an account to bind with, and its password is
# read from HEALTHCHECK_BIND_PASSWORD_FILE, never passed on a command line. --noPropertiesFile
# keeps a tools.properties in the user's home from turning the probe into a bind of its own.
#
# Docker reserves exit code 2, so whatever failed is reported as 1.

test -f "${BOOTSTRAP_COMPLETE:-/opt/opendj/.bootstrap-complete}" || exit 1

BIND_ARGS=()
if [ -n "${HEALTHCHECK_BIND_DN}" ]; then
  if [ ! -r "${HEALTHCHECK_BIND_PASSWORD_FILE}" ]; then
    echo "HEALTHCHECK_BIND_DN is set, but HEALTHCHECK_BIND_PASSWORD_FILE '${HEALTHCHECK_BIND_PASSWORD_FILE}' is not a readable file"
    exit 1
  fi
  BIND_ARGS=(--bindDN "${HEALTHCHECK_BIND_DN}" --bindPasswordFile "${HEALTHCHECK_BIND_PASSWORD_FILE}")
fi

/opt/opendj/bin/ldapsearch --noPropertiesFile --hostname localhost --port "${LDAPS_PORT:-1636}" --useSsl --trustAll \
  "${BIND_ARGS[@]}" --baseDN "" --searchScope base "(objectClass=*)" 1.1 || exit 1
