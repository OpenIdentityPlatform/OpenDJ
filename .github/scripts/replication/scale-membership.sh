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

# Check both complete snapshots over one authenticated LDAPS connection. A failed or
# incomplete search must not count as proof of either presence or absence.
peer_membership() { # <survivor> <peer host> <present|absent>
  local mapping port
  mapping=$(docker port "$1" 4444/tcp) || return 1
  case $mapping in 127.0.0.1:*) port=${mapping#127.0.0.1:} ;; *) echo 'admin port is not loopback-only' >&2; return 1 ;; esac
  case $port in ''|*[!0-9]*) echo 'invalid admin port' >&2; return 1 ;; esac
  printf '%s' "$ROOT_PASSWORD" | python3 -I "$HERE/scale-monitor.py" "$port" "$ROLLING_END_MS" membership "$2" "$3"
}

peer_removed() { peer_membership "$1" "$2" absent; }
peer_present() { peer_membership "$1" "$2" present; }
