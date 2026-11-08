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

from pathlib import Path
import subprocess
import sys
import time
import tempfile
import unittest

PROGRAM = Path(__file__).with_name('scale-deadline.py')


class DeadlineTest(unittest.TestCase):
    def test_completes_or_is_stopped_at_the_deadline(self):
        if not PROGRAM.exists():
            self.fail('deadline wrapper is missing')
        deadline = str(int((time.time() + 2) * 1000))
        self.assertEqual(subprocess.run([sys.executable, str(PROGRAM), deadline, sys.executable, '-c', 'pass']).returncode, 0)
        deadline = str(int((time.time() + 0.5) * 1000))
        result = subprocess.run([sys.executable, str(PROGRAM), deadline, sys.executable, '-c', 'import time; time.sleep(10)'], timeout=3)
        self.assertEqual(result.returncode, 124)

    def test_termination_stops_the_child_process_group(self):
        with tempfile.TemporaryDirectory() as tmp:
            marker = str(Path(tmp) / 'survived')
            deadline = str(int((time.time() + 10) * 1000))
            program = 'import pathlib,sys,time; time.sleep(0.8); pathlib.Path(sys.argv[1]).touch()'
            process = subprocess.Popen([sys.executable, str(PROGRAM), deadline, sys.executable, '-c', program, marker])
            time.sleep(0.2)
            process.terminate()
            process.wait(timeout=3)
            time.sleep(1)
            self.assertFalse(Path(marker).exists(), 'child survived wrapper termination')


if __name__ == '__main__':
    unittest.main()
