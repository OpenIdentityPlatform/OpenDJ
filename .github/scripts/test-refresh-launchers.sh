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

# Checks same-pe-code.py and refresh-launchers.sh against launchers committed in the
# history of master, so that neither can start keeping a changed launcher, or refreshing
# one that differs only in the toolchain stamp, while CI stays green. Needs the full
# history (fetch-depth: 0). Run from the root of the repository.
set -e
here=$(dirname "$0")
t=$(mktemp -d)
trap 'rm -rf "$t"' EXIT

g() { git show "$1:opendj-server-legacy/lib/$2.exe" > "$t/$2-$3.exe"; }
expect() {
  local rc=0
  python3 "$here/same-pe-code.py" "$t/$2" "$t/$3" || rc=$?
  if [ "$rc" != "$1" ]; then
    echo "::error::same-pe-code.py $2 $3 exited $rc, expected $1 ($4)"
    exit 1
  fi
  echo "ok: $2 $3 -> $rc ($4)"
}

for f in launcher_administrator winlauncher opendj_service; do
  g a611c10c46^ $f 36252
  g a611c10c46 $f 36256
  g a8a18f000d $f 36257
  g 74cb4f5a84 $f old-code
  expect 0 $f-36256.exe $f-36257.exe "the toolchain stamp only"
  expect 0 $f-36257.exe $f-36256.exe "the toolchain stamp only"
  expect 1 $f-old-code.exe $f-36257.exe "before and after a change of the launcher sources"
done
# winlauncher.exe has CheckSum 0 in both builds, launcher_administrator.exe does not.
for f in launcher_administrator winlauncher; do
  expect 0 $f-36252.exe $f-36256.exe "the same code, the PE header at 0x100 and at 0xf0"
done
expect 1 opendj_service-36252.exe opendj_service-36256.exe "a code change of the 05.09 refresh"
expect 1 winlauncher-36257.exe launcher_administrator-36257.exe "two different launchers"
# A real change moves the headers too; this one does not, so the sections must be compared.
python3 - "$t/winlauncher-36257.exe" "$t/winlauncher-text-byte.exe" <<'EOF'
import struct, sys
b = bytearray(open(sys.argv[1], "rb").read())
pe = struct.unpack_from("<I", b, 0x3C)[0]
table = pe + 24 + struct.unpack_from("<H", b, pe + 20)[0]
b[struct.unpack_from("<I", b, table + 20)[0] + 16] ^= 1    # the first section: .text
open(sys.argv[2], "wb").write(b)
EOF
expect 1 winlauncher-36257.exe winlauncher-text-byte.exe "one byte of .text flipped"

# The loop: keeps a launcher that differs only in the stamp, refreshes one whose code
# changed, and adds one that is not committed yet.
mkdir "$t/built" "$t/lib"
cp "$t/winlauncher-36257.exe" "$t/built/winlauncher.exe"
cp "$t/winlauncher-36256.exe" "$t/lib/winlauncher.exe"
cp "$t/launcher_administrator-36257.exe" "$t/built/launcher_administrator.exe"
cp "$t/launcher_administrator-old-code.exe" "$t/lib/launcher_administrator.exe"
cp "$t/opendj_service-36257.exe" "$t/built/opendj_service.exe"
bash "$here/refresh-launchers.sh" "$t/built" "$t/lib"
cmp "$t/lib/winlauncher.exe" "$t/winlauncher-36256.exe"
cmp "$t/lib/launcher_administrator.exe" "$t/built/launcher_administrator.exe"
cmp "$t/lib/opendj_service.exe" "$t/built/opendj_service.exe"
echo "ok: refresh-launchers.sh keeps, refreshes and adds as expected"
