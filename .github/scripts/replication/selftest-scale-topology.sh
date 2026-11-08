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
check() (
  TOPOLOGY=$1 DISTURBANCE=$2
  . "$HERE/lib.sh"
  [ "$(echo "$DS_NODES" | wc -w)" -eq 4 ] || exit 1
  [ "$(echo "$RS_NODES" | wc -w)" -eq "$3" ] || exit 1
  [ "$(echo "$ALL_NODES" | wc -w)" -eq "$4" ] || exit 1
)
check rs534 scale-rs-kept 3 7
check rs534 scale-rs-empty 3 7
check rs534 empty-rs 2 6
check rs534 none 2 6
check combined scale-kept 4 4
check combined scale-empty 4 4
echo 'scale topology cases passed'
