#!/usr/bin/env python3
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

"""Tell whether two PE images differ in anything but the stamp of the toolchain.

Usage: same-pe-code.py COMMITTED REBUILT

Exits 0 when the two files are identical once the build stamp and the header padding
are left out of both, 1 when they differ anywhere else, and 2 when either one cannot be
read as a PE image.

The Windows launchers are linked with /Brepro, so their bytes are a function of the
inputs - and the inputs include the build numbers of cl, link and cvtres. Two runner
images a patch release of Visual Studio apart (14.51.36256 and 14.51.36257, say) turn
out byte-for-byte the same code, data and resources, yet different files: the Rich
header lists those build numbers, the REPRO debug entry holds a hash over them, and
the COFF and debug directory timestamps and the PE checksum are derived from that
hash. GitHub rolls a new image out over days, so the Windows job lands on either one
and a byte comparison refreshes the committed launchers back and forth on every push.

What is left out of the comparison:
- the Rich header;
- the COFF and debug directory timestamps and the PE checksum;
- the REPRO hash, and the PDB GUID and age of an RSDS CodeView entry;
- the zero padding the linker puts after the DOS stub, up to the PE header, and after
  the section table, up to SizeOfHeaders, and e_lfanew, which only says where the first
  of them ends. A patch release may pad differently:
  14.51.36252 put the PE header at 0x100, 14.51.36256 at 0xf0, around the same code.
The DOS stub, the PE headers, the section table and every byte from SizeOfHeaders on
are still compared, so a change of source or of code generation still shows. A Rich
header that gains or loses an entry no longer does on its own: it comes with a change
of the objects linked in, which moves the code as well.
"""

import struct
import sys

DEBUG_TYPE_CODEVIEW = 2
DEBUG_TYPE_REPRO = 16
DEBUG_ENTRY_SIZE = 28


class NotPE(Exception):
    pass


def u16(b, off):
    return struct.unpack_from("<H", b, off)[0]


def u32(b, off):
    return struct.unpack_from("<I", b, off)[0]


def blank(b, off, length):
    if off < 0 or off + length > len(b):
        raise NotPE("field at 0x%x runs past the end of the file" % off)
    b[off:off + length] = bytes(length)


def normalize(data):
    b = bytearray(data)
    try:
        if b[:2] != b"MZ":
            raise NotPE("no MZ signature")
        pe = u32(b, 0x3C)
        if b[pe:pe + 4] != b"PE\0\0":
            raise NotPE("no PE signature")

        # The Rich header sits between the DOS stub and the PE header: "DanS" XOR key,
        # the (prodId, build, count) records XOR key, then "Rich" and the key itself.
        rich = b.find(b"Rich", 0x40, pe)
        if rich >= 0:
            key = b[rich + 4:rich + 8]
            dans = bytes(x ^ y for x, y in zip(b"DanS", key))
            start = b.rfind(dans, 0x40, rich)
            if start < 0:
                raise NotPE("Rich header without its DanS marker")
            blank(b, start, rich + 8 - start)

        coff = pe + 4
        sections = u16(b, coff + 2)
        opt_size = u16(b, coff + 16)
        blank(b, coff + 4, 4)                                    # TimeDateStamp

        opt = coff + 20
        magic = u16(b, opt)
        if magic == 0x10B:
            data_dirs = opt + 96
        elif magic == 0x20B:
            data_dirs = opt + 112
        else:
            raise NotPE("unknown optional header magic 0x%x" % magic)
        blank(b, opt + 64, 4)                                    # CheckSum
        headers_end = u32(b, opt + 60)                           # SizeOfHeaders
        if not pe < headers_end <= len(b):
            raise NotPE("SizeOfHeaders 0x%x out of range" % headers_end)

        table = opt + opt_size
        spans = []
        for i in range(sections):
            s = table + 40 * i
            spans.append((u32(b, s + 12), u32(b, s + 8), u32(b, s + 20)))  # va, vsize, raw

        def file_offset(rva):
            for va, vsize, raw in spans:
                if va <= rva < va + vsize:
                    return raw + rva - va
            raise NotPE("RVA 0x%x lies in no section" % rva)

        debug_rva = u32(b, data_dirs + 6 * 8)
        debug_size = u32(b, data_dirs + 6 * 8 + 4)
        if debug_rva and debug_size:
            base = file_offset(debug_rva)
            for i in range(debug_size // DEBUG_ENTRY_SIZE):
                entry = base + DEBUG_ENTRY_SIZE * i
                blank(b, entry + 4, 4)                           # TimeDateStamp
                kind = u32(b, entry + 12)
                size = u32(b, entry + 16)
                raw = u32(b, entry + 24)
                if kind == DEBUG_TYPE_REPRO:
                    blank(b, raw, size)                          # the hash itself
                elif kind == DEBUG_TYPE_CODEVIEW and b[raw:raw + 4] == b"RSDS":
                    blank(b, raw + 4, 20)                        # PDB GUID and age
    except struct.error as e:
        raise NotPE(str(e))
    # Compare what the padding surrounds, wherever it ends (see above). The Rich header
    # is blanked by now, so it goes with the padding after the DOS stub; e_lfanew goes
    # too, since it only says where that padding ends.
    return (bytes(b[:0x3C]) + bytes(b[0x40:pe]).rstrip(b"\0")
            + bytes(b[pe:headers_end]).rstrip(b"\0") + bytes(b[headers_end:]))


def main(argv):
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[2], file=sys.stderr)
        return 2
    try:
        images = []
        for path in argv[1:]:
            with open(path, "rb") as f:
                images.append(normalize(f.read()))
    except (OSError, NotPE) as e:
        print("same-pe-code: %s" % e, file=sys.stderr)
        return 2
    return 0 if images[0] == images[1] else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
