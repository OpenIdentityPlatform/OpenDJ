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

"""Compare RS changelog positions, not the periodically cached missing-changes monitor.

Usage: changelog-checkpoint.py <base DN> <server-ids.txt> <reference.ldif> <current.ldif>
The LDAP snapshots contain replicationServer-database, domain-name and last-change from
FileReplicaDB's monitors. Each (domain, originating DS) has its own position. Exit 0 once
current reaches every reference position, 1 while behind, and 2 for unusable input.
This checks positions, not holes below them; the final changelog/data checks remain necessary.
"""

import base64
from pathlib import Path
import re
import sys


def changelog_positions(path):
    # Unfold before decoding: ldapsearch may wrap a CSN or a base64 domain name.
    lines = []
    for line in Path(path).read_text().splitlines():
        if line.startswith(" "):
            if not lines or not lines[-1]:
                raise ValueError("orphan LDIF continuation in " + path)
            lines[-1] += line[1:]
        else:
            lines.append(line)
    positions, seen, attributes = {}, set(), {}
    for line in lines + [""]:
        if line.startswith("#") or line == "version: 1":
            continue
        if line:
            name, separator, value = line.partition(":")
            name = name.lower()
            if not separator or name in attributes or value.startswith("<"):
                raise ValueError("invalid or duplicate LDIF attribute in " + path)
            if value.startswith(":"):
                value = base64.b64decode(value[1:].strip(), validate=True).decode("utf-8")
            else:
                value = value.lstrip(" ")
            attributes[name] = value
        elif attributes:
            domain = attributes.get("domain-name", "").lower()
            replica = attributes.get("replicationserver-database", "")
            if not domain or not re.fullmatch(r"[0-9]+", replica) or not attributes.get("dn"):
                raise ValueError("incomplete changelog monitor entry in " + path)
            key = (domain, int(replica))
            if key in seen:
                raise ValueError("duplicate changelog monitor entry in " + path)
            seen.add(key)
            if "last-change" in attributes:
                value = attributes["last-change"].split()
                csn = value[0].lower() if value else ""
                if not re.fullmatch(r"[0-9a-f]{28}", csn) or int(csn[16:20], 16) != key[1]:
                    raise ValueError("invalid last-change for %s DS(%s)" % key)
                positions[key] = csn
            attributes = {}
    return positions


def position(csn):
    # CSN.compare orders by timestamp, then the signed sequence number, then origin.
    # The origin is already the map key. Avoid float conversion of the 64-bit timestamp.
    sequence = int(csn[20:28], 16)
    return int(csn[:16], 16), sequence if sequence < 2**31 else sequence - 2**32


def main(argv):
    if len(argv) != 5:
        print(__doc__, file=sys.stderr)
        return 2
    base, ids_path, reference_path, current_path = argv[1:]
    try:
        ids = [line.split()[0] for line in Path(ids_path).read_text().splitlines() if line.strip()]
        if not ids or any(not re.fullmatch(r"[0-9]+", replica) for replica in ids):
            raise ValueError("missing or invalid workload server IDs")
        reference = changelog_positions(reference_path)
        current = changelog_positions(current_path)
        for replica in ids:
            if (base.lower(), int(replica)) not in reference:
                raise ValueError("reference lacks a workload position for %s DS(%s)" % (base, replica))
        behind = False
        for (domain, replica), wanted in sorted(reference.items()):
            actual = current.get((domain, replica))
            if actual is None or position(actual) < position(wanted):
                print("%s DS(%s): needs %s, has %s" % (domain, replica, wanted, actual or "no position"))
                behind = True
        return 1 if behind else 0
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
