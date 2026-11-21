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

import importlib.util
import io
from pathlib import Path
import sys
import subprocess
import tarfile
import tempfile
import unittest

sys.dont_write_bytecode = True
path = Path(__file__).with_name('scale-coverage.py')
if not path.exists():
    raise SystemExit('scale changelog coverage is missing')
spec = importlib.util.spec_from_file_location('scale_coverage', path)
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)


class CoverageTest(unittest.TestCase):
    def test_requires_both_boundary_records_and_all_intervening_sequences(self):
        first = (10, 1000)
        last = (50010, 2000)
        records = [(seq, 1000 if seq == 10 else 2000 if seq == 50010 else 1500, 'modify')
                   for seq in range(10, 50011)]
        self.assertTrue(coverage.complete(records, first, last))
        self.assertFalse(coverage.complete(records[1:], first, last))
        self.assertFalse(coverage.complete(records[:-1], first, last))
        self.assertFalse(coverage.complete(records[:100] + records[101:], first, last))
        self.assertFalse(coverage.complete(records, (10, 999), last))

    def test_streamed_changelog_checks_the_exact_fill(self):
        def make_archive(omitted=None):
            stream = bytearray()
            for seq in range(10, 50011):
                if seq == omitted:
                    continue
                timestamp = 1000 if seq == 10 else 2000 if seq == 50010 else 1500
                csn = '%016x%04x%08x' % (timestamp, 1, seq)
                message = b'\x16\x00' + csn.encode('ascii')
                stream.extend(len(message).to_bytes(4, 'big') + message)
            blocks = bytearray(stream[:256])
            for pos in range(256, len(stream), 252):
                blocks.extend(b'\0' * 4 + stream[pos:pos + 252])
            output = io.BytesIO()
            with tarfile.open(fileobj=output, mode='w') as tar:
                for name, data in [('changelogDb/domains.state', b'1:dc=example,dc=com\n'),
                                   ('changelogDb/1.dom/1.server/head.log', bytes(blocks))]:
                    entry = tarfile.TarInfo(name)
                    entry.size = len(data)
                    tar.addfile(entry, io.BytesIO(data))
            return output.getvalue()

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            first = root / 'first.ldif'
            last = root / 'last.ldif'
            first.write_text('dn: cn=fill\ndescription: scale-1\nds-sync-hist: '
                             'description:00000000000003e800010000000a:repl:scale-1\n')
            last.write_text('dn: cn=fill\ndescription: scale-50001\nds-sync-hist: '
                            'description:00000000000007d000010000c35a:repl:scale-50001\n')
            command = [sys.executable, str(path), 'dc=example,dc=com', str(first), str(last)]
            for omitted, expected in [(None, 0), (10, 1), (100, 1), (50010, 1)]:
                result = subprocess.run(command, input=make_archive(omitted), capture_output=True, timeout=30)
                self.assertEqual(result.returncode, expected, result.stderr.decode())

    def test_rejects_too_short_or_reversed_interval(self):
        with self.assertRaises(ValueError):
            coverage.complete([], (10, 1000), (100, 2000))
        with self.assertRaises(ValueError):
            coverage.complete([], (50010, 1000), (10, 2000))


if __name__ == '__main__':
    unittest.main()
