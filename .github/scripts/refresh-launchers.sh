#!/bin/bash
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

# Usage: refresh-launchers.sh BUILT LIB
#
# Copies each BUILT/*.exe over its namesake in LIB, unless the two differ only in the
# toolchain stamp (same-pe-code.py). The Package/Deploy workflow runs it before it
# commits LIB; the Build workflow runs it on known launcher pairs before merge.
set -e
here=$(dirname "$0")
for built in "$1"/*.exe; do
  committed=$2/$(basename "$built")
  # Exit 1 (the code differs) and 2 (not readable as a PE image) both take the rebuilt
  # file: when in doubt, refresh.
  if [ -f "$committed" ] && python3 "$here/same-pe-code.py" "$committed" "$built"; then
    if ! cmp -s "$committed" "$built"; then
      echo "$committed differs from the rebuilt one only in the toolchain stamp - keeping it."
    fi
    continue
  fi
  cp "$built" "$committed"
done
