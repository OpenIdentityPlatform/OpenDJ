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

# Check the registry and local replication-server lists. A failed or empty search is not
# proof of either presence or absence.
peer_membership() { # <survivor> <peer host> <present|absent>
  local registry config
  registry=$(admin_search "$1" 'cn=Servers,cn=admin data' one '(objectClass=*)' hostname) || return 1
  config=$(admin_search "$1" cn=config sub \
    '(|(objectClass=ds-cfg-replication-server)(objectClass=ds-cfg-replication-domain))' \
    ds-cfg-replication-server) || return 1
  printf '%s\n' "$registry" | awk -v peer="$2" -v wanted="$3" '
    BEGIN { peer = tolower(peer) }
    tolower($1) == "dn:" { entries++ }
    tolower($1) == "hostname:" {
      names++; host = tolower($2); split(host, parts, ".")
      if (host == peer || parts[1] == peer) found = 1
    }
    END { exit !(entries && names && (wanted == "present" ? found : !found)) }' || return 1
  printf '%s\n' "$config" | awk -v peer="$2" -v wanted="$3" '
    BEGIN { peer = tolower(peer) }
    tolower($1) == "dn:" {
      if (entries && !found) missing++
      entries++; found = 0
    }
    tolower($1) == "ds-cfg-replication-server:" {
      host = tolower($2); sub(/:[0-9]+$/, "", host); split(host, parts, ".")
      if (host == peer || parts[1] == peer) { found = 1; any = 1 }
    }
    END {
      if (entries && !found) missing++
      exit !(entries && (wanted == "present" ? !missing : !any))
    }'
}

peer_removed() { peer_membership "$1" "$2" absent; }
peer_present() { peer_membership "$1" "$2" present; }
