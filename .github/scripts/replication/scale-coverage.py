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

"""Check both fill boundaries and every intervening CSN in a streamed RS changelog."""

import importlib.util
from pathlib import Path
import sys
import tarfile

sys.dont_write_bytecode = True


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


fence = load('scale_fence', 'scale-fence.py')
gaps = load('changelog_gaps', 'changelog-gaps.py')


def complete(records, first, last):
    first_seq, first_time = first
    last_seq, last_time = last
    if last_seq - first_seq < 50000 or last_time < first_time:
        raise ValueError('fill boundaries do not span 50001 changes')
    present = {(seq, timestamp) for seq, timestamp, _ in records
               if first_seq <= seq <= last_seq and first_time <= timestamp <= last_time}
    if first not in present or last not in present:
        return False
    return len({seq for seq, _ in present}) == last_seq - first_seq + 1


def main(argv):
    if len(argv) != 4:
        print('usage: scale-coverage.py <base DN> <first fill LDIF> <last fill LDIF>', file=sys.stderr)
        return 2
    try:
        first_id, first_csn = fence.writer_position(argv[2], 'scale-1')
        last_id, last_csn = fence.writer_position(argv[3], 'scale-50001')
        if first_id != last_id:
            raise ValueError('fill changed originating replica')
        domains, changes = gaps.read_changelog(gaps.from_tar(sys.stdin.buffer))
        domain = domains.get(argv[1].lower())
        if domain is None:
            raise ValueError('base DN missing from changelog')
        first = (int(first_csn[20:28], 16), int(first_csn[:16], 16))
        last = (int(last_csn[20:28], 16), int(last_csn[:16], 16))
        if not complete(changes.get((domain, first_id), []), first, last):
            print('fill boundary or intervening change is missing')
            return 1
        print('RS changelog contains both boundaries and every intervening change')
        return 0
    except (OSError, ValueError, tarfile.TarError, gaps.EncryptedChangelog) as error:
        print('cannot verify RS changelog: %s' % error, file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
