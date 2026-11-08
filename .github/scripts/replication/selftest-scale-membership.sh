#!/usr/bin/env bash
#
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

set -eu -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
if [ ! -f "$HERE/scale-membership.sh" ]; then
  echo 'scale membership checks are missing' >&2
  exit 1
fi
. "$HERE/scale-membership.sh"

admin_search() {
  case "$2" in
    'cn=Servers,cn=admin data') printf '%s\n' "$REGISTRY" ;;
    cn=config) printf '%s\n' "$CONFIG" ;;
    *) return 1 ;;
  esac
  [ "${SEARCH_FAIL:-0}" = 0 ]
}
REGISTRY=$'dn: cn=one,cn=Servers,cn=admin data\nhostname: cv-d0.convergence\n'
CONFIG=$'dn: cn=domain,cn=config\nds-cfg-replication-server: cv-r0.convergence:8989\n'
peer_removed survivor cv-r1 || { echo 'complete snapshots with no departed peer must pass' >&2; exit 1; }
REGISTRY+=$'dn: cn=two,cn=Servers,cn=admin data\nhostname: CV-R1.convergence\n'
if peer_removed survivor cv-r1; then echo 'registered peer passed' >&2; exit 1; fi
REGISTRY=$'dn: cn=one,cn=Servers,cn=admin data\nhostname: cv-d0.convergence\n'
CONFIG+=$'ds-cfg-replication-server: cv-r1.convergence:8989\n'
if peer_removed survivor cv-r1; then echo 'peer in a replication list passed' >&2; exit 1; fi
CONFIG=$'dn: cn=domain,cn=config\nds-cfg-replication-server: cv-r10.convergence:8989\n'
peer_removed survivor cv-r1 || { echo 'a distinct hostname was treated as the removed peer' >&2; exit 1; }
CONFIG='dn: cn=server,cn=config'
peer_removed survivor cv-r1 || { echo 'an empty replication list is valid for the only remaining RS' >&2; exit 1; }
CONFIG=
if peer_removed survivor cv-r1; then echo 'empty configuration passed' >&2; exit 1; fi
CONFIG=$'dn: cn=domain,cn=config\nds-cfg-replication-server: cv-r0.convergence:8989\n'
REGISTRY=
if peer_removed survivor cv-r1; then echo 'empty registry passed' >&2; exit 1; fi
REGISTRY=$'dn: cn=one,cn=Servers,cn=admin data\nhostname: cv-d0.convergence\n'
SEARCH_FAIL=1
if peer_removed survivor cv-r1; then echo 'failed search passed' >&2; exit 1; fi
# Scale-out must restore both the registration and the local RS list.
SEARCH_FAIL=0
REGISTRY=$'dn: cn=one,cn=Servers,cn=admin data\nhostname: cv-r1.convergence\n'
CONFIG=$'dn: cn=domain,cn=config\nds-cfg-replication-server: CV-R1.convergence:8989\n'
peer_present survivor cv-r1 || { echo 'returned peer was not recognized' >&2; exit 1; }
CONFIG+=$'dn: cn=another,cn=config\nds-cfg-replication-server: cv-r0.convergence:8989\n'
if peer_present survivor cv-r1; then echo 'a list without the peer was accepted' >&2; exit 1; fi
CONFIG=$'dn: cn=domain,cn=config\nds-cfg-replication-server: CV-R1.convergence:8989\n'
REGISTRY=$'dn: cn=one,cn=Servers,cn=admin data\nhostname: cv-r0.convergence\n'
if peer_present survivor cv-r1; then echo 'missing registration was accepted' >&2; exit 1; fi
REGISTRY=$'dn: cn=one,cn=Servers,cn=admin data\nhostname: cv-r1.convergence\n'
CONFIG=$'dn: cn=domain,cn=config\nds-cfg-replication-server: cv-r0.convergence:8989\n'
if peer_present survivor cv-r1; then echo 'missing RS list value was accepted' >&2; exit 1; fi
echo 'scale membership checks passed'
