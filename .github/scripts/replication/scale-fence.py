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

"""Compare the last fill operation CSN with a surviving RS's changelog position."""

import base64
import importlib.util
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('checkpoint', Path(__file__).with_name('changelog-checkpoint.py'))
checkpoint = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checkpoint)


def writer_position(path, expected='scale-50001'):
    lines = []
    for line in Path(path).read_text().splitlines():
        if line.startswith(' '):
            if not lines or not lines[-1]:
                raise ValueError('orphan LDIF continuation')
            lines[-1] += line[1:]
        else:
            lines.append(line)
    descriptions, histories, dns = [], [], 0
    for line in lines:
        name, separator, value = line.partition(':')
        if not separator:
            continue
        name = name.lower()
        if name not in ('dn', 'description', 'ds-sync-hist'):
            continue
        if value.startswith(':'):
            value = base64.b64decode(value[1:].strip(), validate=True).decode('utf-8')
        else:
            value = value.lstrip()
        if name == 'dn':
            dns += 1
        elif name == 'description':
            descriptions.append(value)
        else:
            histories.append(value)
    if dns != 1 or descriptions != [expected]:
        raise ValueError('missing or ambiguous final fill entry')
    matches = []
    for value in histories:
        match = re.fullmatch(r'description:([0-9a-fA-F]{28}):(?:repl|add):' + re.escape(expected), value,
                             re.IGNORECASE)
        if match:
            matches.append(match[1].lower())
    if len(matches) != 1:
        raise ValueError('missing or ambiguous final fill CSN')
    return int(matches[0][16:20], 16), matches[0]


def main(argv):
    if len(argv) != 4:
        print('usage: scale-fence.py <base DN> <fill entry LDIF> <RS monitor LDIF>', file=sys.stderr)
        return 2
    try:
        server_id, wanted = writer_position(argv[2])
        current = checkpoint.changelog_positions(argv[3]).get((argv[1].lower(), server_id))
        if current is None or checkpoint.position(current) < checkpoint.position(wanted):
            print('DS(%s): needs %s, RS has %s' % (server_id, wanted, current or 'no position'))
            return 1
        print('DS(%s): RS reached %s' % (server_id, wanted))
        return 0
    except (OSError, ValueError) as error:
        print('cannot read fence: %s' % error, file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
