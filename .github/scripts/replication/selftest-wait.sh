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

set -e -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"
sleep() { :; }
calls=0
late_success() { calls=$((calls + 1)); [ "$calls" -ge 3 ]; }
# A caller may use wait_until in a condition, disabling errexit for the whole function.
# It must still stop at its deadline, even if the predicate would later succeed.
if wait_until 0 'a late predicate' late_success >/dev/null; then
  echo 'wait_until accepted a predicate after its deadline' >&2
  exit 1
fi
[ "$calls" -eq 1 ] || { echo 'wait_until kept trying after timeout' >&2; exit 1; }
echo 'wait deadline passed'
