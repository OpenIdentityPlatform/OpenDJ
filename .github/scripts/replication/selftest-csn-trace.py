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

import importlib.util
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('csn_trace', Path(__file__).with_name('csn-trace.py'))
trace = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trace)


class TraceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.addCleanup(self.tmp.cleanup)
        self.manifest = self.root / 'expected.txt'
        self.manifest.write_text('d0 token0\n')
        self.file = self.root / 'd0' / 'trace-1.txt'
        self.file.parent.mkdir()
        self.events = [b'O ZGM9ZXhhbXBsZQ== 000001a1d4733f4439d000004666\n',
                       b'L ZGM9ZXhhbXBsZQ== 000001a1d474069539d000004666\n']
        self.write_trace()

    def write_trace(self):
        body = b''.join(self.events)
        self.file.write_bytes(b'START 1 token0\n' + body
                              + ('END %d %08x\n' % (len(self.events), zlib.crc32(body))).encode())

    def test_reused_sequence_reports_local_and_offline_allocations(self):
        events = trace.read_traces(self.root, self.manifest, {(14800, 18022)})
        self.assertEqual([e[0] for e in events], ['O', 'L'])
        self.assertNotEqual(events[0][2], events[1][2])

    def test_truncated_and_modified_traces_fail_closed(self):
        data = self.file.read_bytes()
        for bad in (data[:-2], data.replace(b'L ', b'O '), data + b'L bad\n'):
            self.file.write_bytes(bad)
            with self.assertRaises(ValueError):
                trace.read_traces(self.root, self.manifest, set())

    def test_missing_generation_and_missing_file_fail_closed(self):
        self.manifest.write_text('d0 token0\nd0 token1\n')
        with self.assertRaises(ValueError):
            trace.read_traces(self.root, self.manifest, set())
        self.file.unlink()
        with self.assertRaises(ValueError):
            trace.read_traces(self.root, self.manifest, set())

    def test_repeated_expected_token_fails_even_if_two_files_exist(self):
        self.manifest.write_text('d0 token0\nd0 token0\n')
        (self.file.parent / 'trace-2.txt').write_bytes(self.file.read_bytes())
        with self.assertRaises(ValueError):
            trace.read_traces(self.root, self.manifest, set())

    def test_unknown_generation_fails_closed(self):
        self.manifest.write_text('d0 other\n')
        with self.assertRaises(ValueError):
            trace.read_traces(self.root, self.manifest, set())


if __name__ == '__main__':
    unittest.main()
