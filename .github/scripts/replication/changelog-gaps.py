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

"""Finds the changes a replication server's changelog lacks (#1090, #1195).

A directory server numbers the changes it makes one after the other: CSNGenerator.newCSN()
adds one to the sequence number of the CSN for every change, whatever its time. So the
changes of one replica in a changelog have no gap in their sequence numbers unless a change
was never published - an operation that failed after it had its CSN, or one that was lost on
its way to the replication servers. During the load of the convergence run no operation fails
(load.sh checks that), so a gap there is a lost change: a change the data of the other
replicas misses, while their ServerState and missing-changes say nothing, since both only
know the newest CSN.

Usage: changelog-gaps.py <base DN> <from ms> <to ms> [<changelogDb directory>]

Without a directory it reads the changelogDb as a tar stream on its standard input, as
"docker cp <container>:/opt/opendj/data/changelogDb -" writes it. It reports, for every
replica of the domain of <base DN>, the changes whose CSN time is between <from ms> and
<to ms>, and the sequence numbers missing between them. Exit code 1: a change is missing;
2: the changelog cannot be read (no such domain, or it is encrypted).

What it cannot tell. A change lost at either end of a replica's changes in the window - its
last ones, or its first ones when no earlier change of the replica is in the changelog - leaves
no gap between two changes that are there; the data of the other replicas shows such a change
unless a later change of another server overwrites it.

A restart leaves no gap of its own. A directory server that stops numbers its ReplicaOfflineMsg
like a change, and no log file holds the message - only the offline.state of the replica, the
newest one. But the message does not enter the ServerState of the replica
(ReplicaOfflineMsg.contributesToDomainState()), from whose newest change of its own the server
numbers again once it starts (CSNGenerator), so its first change after the restart takes the
sequence number of the message again, at a later time: the changes of the restarted server had
no gap in run 39834888428 (#1208). A gap around a restart is a lost change, the kind #1195 lost.

The file format is the one of FileReplicaDB: every log file is cut into blocks of 256 bytes,
and every block after the first starts with a 4-byte offset (BlockLogWriter); in between, a
record is its length in 4 bytes and the bytes of its UpdateMsg, whose header is the message
type, the protocol version and the CSN as 28 hexadecimal characters (LDAPUpdateMsg).
"""

import os
import re
import sys
import tarfile

BLOCK_SIZE = 256
SIZE_OF_BLOCK_OFFSET = 4
# ReplicationMsg: MSG_TYPE_MODIFY, ADD, DELETE, MODIFYDN of the protocol versions after 1
UPDATE_TYPES = {22: "modify", 23: "add", 24: "delete", 25: "modifydn"}
DISK_ENCODING = 0xFF
CSN = re.compile(rb"[0-9a-f]{28}")
LOG_FILE = re.compile(r"(?:^|/)(\d+)\.dom/(\d+)\.server/[^/.][^/]*\.log$")


def records(data):
    """The UpdateMsg bytes of the records of one log file."""
    stream = bytearray(data[:BLOCK_SIZE])
    for start in range(BLOCK_SIZE, len(data), BLOCK_SIZE):
        stream += data[start + SIZE_OF_BLOCK_OFFSET:start + BLOCK_SIZE]
    pos = 0
    while pos + 4 <= len(stream):
        length = int.from_bytes(stream[pos:pos + 4], "big")
        if length <= 0 or pos + 4 + length > len(stream):
            raise ValueError("record of %d bytes at %d of %d" % (length, pos, len(stream)))
        yield bytes(stream[pos + 4:pos + 4 + length])
        pos += 4 + length


def read_changelog(entries):
    """(domains, changes): domain id by base DN, and for each (domain id, server id) a
    list of (sequence number, time in ms, type)."""
    domains = {}
    changes = {}
    for name, data in entries:
        if os.path.basename(name) == "domains.state":
            for line in data.decode("utf-8").splitlines():
                if ":" in line:
                    domain_id, dn = line.split(":", 1)
                    domains[dn.strip().lower()] = domain_id
            continue
        match = LOG_FILE.search(name)
        if not match:
            continue
        found = changes.setdefault((match.group(1), int(match.group(2))), [])
        for message in records(data):
            if message[0] == DISK_ENCODING:
                raise EncryptedChangelog(name)
            if message[0] not in UPDATE_TYPES or not CSN.fullmatch(message[2:30]):
                continue
            csn = message[2:30].decode("ascii")
            found.append((int(csn[20:28], 16), int(csn[0:16], 16), UPDATE_TYPES[message[0]]))
    return domains, changes


class EncryptedChangelog(Exception):
    pass


def from_directory(root):
    for directory, _, files in os.walk(root):
        for file in files:
            path = os.path.join(directory, file)
            with open(path, "rb") as f:
                yield os.path.relpath(path, root), f.read()


def from_tar(stream):
    with tarfile.open(fileobj=stream, mode="r|") as tar:
        for member in tar:
            if member.isfile():
                yield member.name, tar.extractfile(member).read()


def main(argv):
    if len(argv) not in (4, 5):
        sys.stderr.write(__doc__)
        return 2
    base_dn, start, end = argv[1].lower(), int(argv[2]), int(argv[3])
    entries = from_directory(argv[4]) if len(argv) == 5 else from_tar(sys.stdin.buffer)
    try:
        domains, changes = read_changelog(entries)
    except EncryptedChangelog as e:
        print("the changelog is encrypted (%s): its changes cannot be read" % e)
        return 2
    except (tarfile.TarError, ValueError, OSError) as e:
        print("the changelog cannot be read: %s" % e)
        return 2
    if base_dn not in domains:
        print("no domain %s in the changelog, which holds %s" % (base_dn, sorted(domains)))
        return 2
    lost = False
    for (domain_id, server_id), found in sorted(changes.items()):
        if domain_id != domains[base_dn]:
            continue
        load = sorted((seqnum, time, kind) for seqnum, time, kind in found if start <= time <= end)
        if not load:
            continue
        seqnums = [seqnum for seqnum, _, _ in load]
        missing = []
        for previous, following in zip(load, load[1:]):
            missing += [(seqnum, previous, following) for seqnum in range(previous[0] + 1, following[0])]
        print("replica %d: %d changes in the load, sequence numbers %d to %d, %d missing"
              % (server_id, len(load), seqnums[0], seqnums[-1], len(missing)))
        for seqnum, previous, following in missing[:20]:
            print("  missing %d, between the %s %d (%d) and the %s %d (%d)"
                  % (seqnum, previous[2], previous[0], previous[1], following[2], following[0], following[1]))
        if len(missing) > 20:
            print("  and %d more" % (len(missing) - 20))
        lost = lost or bool(missing)
    return 1 if lost else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
