#!/usr/bin/env python3
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

"""Validate opt-in CSN allocation traces and look up replica/sequence pairs.

A local allocation is an attempted operation, not proof that it committed. These traces
do not prove that a local allocation committed. The changelog checker may classify an
offline-only sequence only when every trace validates and no local allocation reused that
domain/server/sequence in any generation. A process with an absent, truncated, altered,
duplicated, or unclosed trace fails
validation. The manifest lists every expected server-process generation, including restarts.
"""

import argparse
import base64
from collections import Counter
from pathlib import Path
import re
import sys
import zlib

EVENT = re.compile(rb'([LO]) ([A-Za-z0-9+/]+={0,2}) ([0-9a-f]{28})\n')
START = re.compile(rb'START 1 ([a-zA-Z0-9_-]+)\n')
END = re.compile(rb'END ([0-9]+) ([0-9a-f]{8})\n')


def read_traces(root, manifest, wanted):
    root = Path(root)
    try:
        expected = Counter(tuple(line.split()) for line in Path(manifest).read_text().splitlines())
        if not expected or any(len(item) != 2 for item in expected):
            raise ValueError('empty or invalid expected-generations manifest')
        if any(count != 1 for count in expected.values()) or len({item[1] for item in expected}) != len(expected):
            raise ValueError('generation tokens must be unique')
        found, actual = [], Counter()
        files = sorted(root.glob('*/*.txt'))
        for path in files:
            node = path.parent.name
            with path.open('rb') as stream:
                start = START.fullmatch(stream.readline())
                if not start:
                    raise ValueError('%s: invalid start' % path)
                token = start.group(1).decode('ascii')
                actual[(node, token)] += 1
                count = checksum = 0
                for line in stream:
                    end = END.fullmatch(line)
                    if end:
                        if int(end.group(1)) != count or int(end.group(2), 16) != checksum or stream.read(1):
                            raise ValueError('%s: invalid end, checksum, count or trailing records' % path)
                        break
                    event = EVENT.fullmatch(line)
                    if not event:
                        raise ValueError('%s: invalid or incomplete event' % path)
                    count += 1
                    checksum = zlib.crc32(line, checksum)
                    csn = event.group(3).decode('ascii')
                    sid, seq = int(csn[16:20], 16), int(csn[20:], 16)
                    try:
                        domain = base64.b64decode(event.group(2), validate=True).decode('utf-8')
                    except (ValueError, UnicodeDecodeError) as exc:
                        raise ValueError('%s: invalid domain' % path) from exc
                    if (sid, seq) in wanted:
                        found.append((event.group(1).decode('ascii'), domain, csn, node, token))
                else:
                    raise ValueError('%s: missing end' % path)
        if actual != expected:
            raise ValueError('generation mismatch: expected %s, found %s' % (dict(expected), dict(actual)))
        return found
    except OSError as exc:
        raise ValueError(str(exc)) from exc


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory')
    parser.add_argument('manifest')
    parser.add_argument('pairs', nargs='*', help='replica-id:sequence-number')
    args = parser.parse_args()
    try:
        wanted = {tuple(map(int, pair.split(':'))) for pair in args.pairs}
        if any(len(pair) != 2 for pair in wanted):
            raise ValueError('pairs must be replica-id:sequence-number')
        found = read_traces(args.directory, args.manifest, wanted)
    except ValueError as exc:
        print('incomplete CSN allocation trace: %s' % exc, file=sys.stderr)
        return 1
    for kind, domain, csn, node, token in found:
        print('%s %s %s %s %s' % (node, token, kind, domain, csn))
    print('CSN allocation traces complete; local allocations may be uncommitted')
    return 0


if __name__ == '__main__':
    sys.exit(main())
