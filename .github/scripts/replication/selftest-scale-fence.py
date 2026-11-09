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

import base64
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
path = Path(__file__).with_name('scale-fence.py')
if not path.exists():
    raise SystemExit('scale fence is missing')
spec = importlib.util.spec_from_file_location('scale_fence', path)
fence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fence)


class FenceTest(unittest.TestCase):
    def test_waits_for_the_writers_own_position(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            writer = root / 'writer.ldif'
            current = root / 'rs.ldif'
            writer.write_text('dn: cn=writer\ndescription: scale-50001\n'
                              'ds-sync-hist: description:000000000000000100010000000a:repl:scale-50001\n'
                              'server-id: 1\nserver-state: 0000000000000001000100000009 date\n')
            current.write_text('dn: cn=changelog\ndomain-name: dc=example,dc=com\n'
                               'replicationServer-database: 1\nlast-change: 0000000000000001000100000009\n')
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(fence.main(['fence', 'dc=example,dc=com', str(writer), str(current)]), 1)
            current.write_text(current.read_text().replace('00000009', '0000000b'))
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(fence.main(['fence', 'dc=example,dc=com', str(writer), str(current)]), 0)
            self.assertIn('needs 000000000000000100010000000a, RS has 000000000000000100010000000b', output.getvalue())
            writer.write_text('dn: cn=writer\ndescription: scale-50001\n'
                              'ds-sync-hist: description:000000000000000100010000000a:repl:wrong-value\n')
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(fence.main(['fence', 'dc=example,dc=com', str(writer), str(current)]), 2)

    def test_folded_base64_state_and_duplicate_writer_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            writer = Path(tmp) / 'writer.ldif'
            value = base64.b64encode(b'description:000000000000000100010000000a:repl:scale-50001').decode()
            writer.write_text('dn: cn=writer\ndescription: scale-50001\nds-sync-hist:: ' + value[:16] + '\n ' + value[16:] + '\n')
            self.assertEqual(fence.writer_position(writer), (1, '000000000000000100010000000a'))
            with writer.open('a') as output:
                output.write('ds-sync-hist: description:000000000000000100010000000a:add:scale-50001\n')
            with self.assertRaises(ValueError):
                fence.writer_position(writer)


if __name__ == '__main__':
    unittest.main()
