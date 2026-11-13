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

import io
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('changelog-gaps.py')


def changelog(records=((1, 1000000), (3, 1000010))):
    stream = bytearray()
    for sequence, timestamp in records:
        csn = '%016x%04x%08x' % (timestamp, 1, sequence)
        message = b'\x16\x00' + csn.encode('ascii')
        stream += len(message).to_bytes(4, 'big') + message
    result = io.BytesIO()
    with tarfile.open(fileobj=result, mode='w') as tar:
        for name, data in [('changelogDb/domains.state', b'1:dc=example,dc=com\n'),
                           ('changelogDb/1.dom/1.server/head.log', bytes(stream))]:
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    return result.getvalue()


class ChangelogGapTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.snapshots = self.root / 'snapshots'
        self.snapshots.mkdir()

    def snapshot(self, sequence=2, timestamp=1000005, server=1, domain='dc=example,dc=com',
                 captured=1010000):
        csn = '%016x%04x%08x' % (timestamp, server, sequence)
        (self.snapshots / 'offline-return-r0.txt').write_text(
            'capture-start-ms %d\ndomains.state\n1:%s\n1.dom/1.server/offline.state %s\n'
            % (captured, domain, csn))

    def run_check(self, evidence=True, records=((1, 1000000), (3, 1000010))):
        args = [sys.executable, str(SCRIPT), 'dc=example,dc=com', '900000', '1100000']
        if evidence:
            args += ['--offline-snapshots', str(self.snapshots)]
        return subprocess.run(args, input=changelog(records), capture_output=True, text=False, timeout=10)

    def test_reports_an_exact_offline_csn_without_waiving_the_gap(self):
        self.assertEqual(self.run_check().returncode, 1)
        self.snapshot()
        result = self.run_check()
        self.assertEqual(result.returncode, 1, result.stdout.decode() + result.stderr.decode())
        self.assertIn(b'offline 2: captured CSN', result.stdout)
        self.assertIn(b'missing 2,', result.stdout)
        self.assertEqual(self.run_check(evidence=False).returncode, 1)

    def test_a_confirmed_marker_does_not_hide_another_missing_change(self):
        self.snapshot()
        result = self.run_check(records=((1, 1000000), (3, 1000010), (5, 1000020)))
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'offline 2:', result.stdout)
        self.assertIn(b'missing 4,', result.stdout)

    def test_does_not_excuse_a_different_number_domain_id_or_time(self):
        for kwargs in ({'sequence': 4}, {'server': 2}, {'domain': 'cn=other'},
                       {'timestamp': 1000020}, {'captured': 1000006}):
            with self.subTest(kwargs=kwargs):
                self.snapshot(**kwargs)
                result = self.run_check()
                self.assertEqual(result.returncode, 1)
                self.assertNotIn(b'offline 2:', result.stdout)

    def test_reused_or_future_logical_time_never_suppresses_a_gap(self):
        self.snapshot()
        result = self.run_check()
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'a restart may have reused the sequence', result.stdout)


if __name__ == '__main__':
    unittest.main()
