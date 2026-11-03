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

"""Fixture tests of the restart-rs changelog checkpoint, without a Docker topology."""

import base64
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("changelog-checkpoint.py")
BASE = "dc=example,dc=com"
CSN = "000001a1a1e836e400010000000a"


def entry(domain=BASE, replica=1, csn=CSN):
    return ("dn: cn=Changelog for DS(%s),cn=%s,cn=monitor\n"
            "replicationServer-database: %s\ndomain-name: %s\n%s\n"
            % (replica, domain, replica, domain,
               "last-change: " + csn + " Tue Nov 03 14:55:12 UTC 2026\n" if csn else ""))


class CheckpointTest(unittest.TestCase):
    def run_check(self, reference, current, ids="1 d0\n"):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ("ids", "reference", "current")]
            for path, contents in zip(paths, (ids, reference, current)):
                path.write_text(contents)
            result = subprocess.run([sys.executable, "-I", str(SCRIPT), BASE,
                                     *map(str, paths)], capture_output=True, text=True)
            return result.returncode, result.stdout + result.stderr

    def test_equal_or_newer_position_passes(self):
        # Dropping equality or comparing sequence alone rejects a valid caught-up RS.
        for csn in (CSN, "000001a1a1e836e400010000000b", "000001a1a1e836e5000100000000"):
            with self.subTest(csn=csn):
                code, output = self.run_check(entry(), entry(csn=csn))
                self.assertEqual(code, 0, output)

    def test_older_position_blocks_restart(self):
        for csn in ("000001a1a1e836e4000100000009", "000001a1a1e836e300017fffffff"):
            with self.subTest(csn=csn):
                code, output = self.run_check(entry(), entry(csn=csn))
                self.assertEqual(code, 1, output)
                self.assertIn(BASE, output)
                self.assertIn(CSN, output)

    def test_every_domain_and_origin_must_reach_the_checkpoint(self):
        schema = entry("cn=schema")
        second = entry(replica=2, csn="000001a1a1e836e400020000000a")
        reference = entry() + schema + second
        for current in (entry() + second, entry() + schema,
                        entry() + second + entry("cn=schema", csn="000001a1a1e836e4000100000009")):
            with self.subTest(current=current):
                self.assertEqual(self.run_check(reference, current, "1 d0\n2 d1\n")[0], 1)
        self.assertEqual(self.run_check(reference, reference, "1 d0\n2 d1\n")[0], 0)

    def test_missing_workload_origin_invalidates_reference(self):
        code, output = self.run_check(entry(), entry(), "1 d0\n2 d1\n")
        self.assertEqual(code, 2, output)
        self.assertIn("2", output)

    def test_empty_or_incomplete_reads_never_pass(self):
        for reference, current in (("", entry()), (entry(), ""),
                                   (entry(csn=None), entry()), (entry(), entry(csn=None))):
            with self.subTest(reference=reference, current=current):
                self.assertNotEqual(self.run_check(reference, current)[0], 0)

    def test_unused_empty_logs_do_not_block(self):
        reference = entry() + entry("cn=schema", 2, None)
        self.assertEqual(self.run_check(reference, entry())[0], 0)

    def test_malformed_or_duplicate_positions_are_rejected(self):
        bad = [entry(csn="bad"), entry(csn="000001a1a1e836e400020000000a"),
               entry().replace("domain-name: " + BASE + "\n", ""),
               entry() + entry(), entry().replace("last-change:", "last-change:<"),
               entry().replace("replicationServer-database: 1", "replicationServer-database: nope"),
               entry().replace("domain-name: " + BASE, "domain-name:: invalid!"),
               entry() + "ldapsearch failed\n"]
        for malformed in bad:
            for reference, current in ((malformed, entry()), (entry(), malformed)):
                with self.subTest(reference=reference, current=current):
                    self.assertEqual(self.run_check(reference, current)[0], 2)

    def test_ldif_folding_base64_and_case_are_supported(self):
        encoded = base64.b64encode(BASE.upper().encode()).decode()
        reference = entry().replace("domain-name: " + BASE, "DOMAIN-NAME:: " + encoded)
        reference = reference.replace(CSN, CSN[:12] + "\n " + CSN[12:])
        self.assertEqual(self.run_check(reference, entry())[0], 0)

    def test_empty_origin_list_is_invalid(self):
        self.assertEqual(self.run_check(entry(), entry(), "")[0], 2)


if __name__ == "__main__":
    unittest.main()
