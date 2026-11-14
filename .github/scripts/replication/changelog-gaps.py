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
adds one to the sequence number of the CSN for every change, whatever its time. A gap can
indicate an unpublished or lost change, but does not prove data loss: an offline message also
consumes a sequence number, and a replica recovering an empty RS from historical information
can reconstruct only changes whose values have not since been replaced. The convergence run
checks every RS so gaps can be investigated alongside replica data and logs.

Usage: changelog-gaps.py <base DN> <from ms> <to ms> [<changelogDb directory>]
       [--offline-snapshots <directory>]

Without a directory it reads the changelogDb as a tar stream on its standard input, as
"docker cp <container>:/opt/opendj/data/changelogDb -" writes it. It reports, for every
replica of the domain of <base DN>, the changes whose CSN time is between <from ms> and
<to ms>, and the sequence numbers missing between them. Captured offline states can identify an
offline message with the same replica and sequence. They do not prove that an update did not later
reuse the sequence after a restart: CSN time is logical and can be ahead of the real clock. The
matching offline message is reported for investigation, and the gap remains an error. With
--csn-traces and --csn-manifest, all expected process traces must validate. Only a sequence
allocated exclusively to offline messages in this domain, with no local allocation in any
generation (even outside the load window), is classified as offline-only. Exit code 1:
a sequence number is missing;
2: the arguments or changelog cannot be read (no such domain, or it is encrypted).

What it cannot tell. A change lost at either end of a replica's changes in the window - its
last ones, or its first ones when no earlier change of the replica is in the changelog - leaves
no gap between two changes that are there; the data of the other replicas shows such a change
unless a later change of another server overwrites it.

A server restart need not leave a gap. A directory server that stops numbers its ReplicaOfflineMsg
like a change, and no log file holds the message - only the offline.state of the replica, the
newest one. But the message does not enter the ServerState of the replica
(ReplicaOfflineMsg.contributesToDomainState()), from whose newest change of its own the server
numbers again once it starts (CSNGenerator), so its first change after the restart takes the
sequence number of the message again, at a later time: the changes of the restarted server had
no gap in run 39834888428 (#1208). A service restart within a server process can behave differently.

The file format is the one of FileReplicaDB: every log file is cut into blocks of 256 bytes,
and every block after the first starts with a 4-byte offset (BlockLogWriter); in between, a
record is its length in 4 bytes and the bytes of its UpdateMsg, whose header is the message
type, the protocol version and the CSN as 28 hexadecimal characters (LDAPUpdateMsg).
"""

import os
import re
import sys
import tarfile
import argparse
import runpy
from pathlib import Path

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


def offline_evidence(directory, base_dn):
    """Read offline CSNs and snapshot times; a matching sequence is diagnostic only."""
    try:
        evidence = {}
        for path in Path(directory).glob('offline-*.txt'):
            lines = path.read_text().splitlines()
            captures = [int(line.split()[1]) for line in lines if line.startswith('capture-start-ms ')]
            if len(captures) != 1:
                continue
            domains = {}
            for line in lines:
                if match := re.fullmatch(r'(\d+):\s*(.+)', line):
                    domains[match.group(1)] = match.group(2).strip().lower()
            for line in lines:
                match = re.fullmatch(r'(\d+)\.dom/(\d+)\.server/offline\.state ([0-9a-f]{28})', line)
                if not match or domains.get(match.group(1)) != base_dn:
                    continue
                sid, csn = int(match.group(2)), match.group(3)
                if int(csn[16:20], 16) != sid:
                    continue
                seq, time = int(csn[20:], 16), int(csn[:16], 16)
                evidence.setdefault((sid, seq), []).append((time, captures[0], csn))
        return evidence
    except (OSError, ValueError, IndexError):
        return {}


def offline_matches(evidence, sid, seq, previous, following):
    matches = set()
    for time, captured, csn in evidence.get((sid, seq), []):
        if previous[1] <= time <= following[1] < captured:
            matches.add(csn)
    return sorted(matches)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base_dn')
    parser.add_argument('start', type=int)
    parser.add_argument('end', type=int)
    parser.add_argument('directory', nargs='?')
    parser.add_argument('--offline-snapshots')
    parser.add_argument('--csn-traces')
    parser.add_argument('--csn-manifest')
    args = parser.parse_args(argv[1:])
    if bool(args.csn_traces) != bool(args.csn_manifest):
        parser.error('--csn-traces and --csn-manifest must be supplied together')
    base_dn, start, end = args.base_dn.lower(), args.start, args.end
    entries = from_directory(args.directory) if args.directory else from_tar(sys.stdin.buffer)
    evidence = {}
    if args.offline_snapshots:
        evidence = offline_evidence(args.offline_snapshots, base_dn)
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
    loads = {}
    for (domain_id, server_id), found in sorted(changes.items()):
        if domain_id != domains[base_dn]:
            continue
        load = sorted((seqnum, time, kind) for seqnum, time, kind in found if start <= time <= end)
        if load:
            loads[server_id] = load
    offline_only = {}
    if args.csn_traces:
        wanted = {(sid, seq) for sid, load in loads.items()
                  for previous, following in zip(load, load[1:])
                  for seq in range(previous[0] + 1, following[0])}
        trace = runpy.run_path(str(Path(__file__).with_name('csn-trace.py')))
        try:
            events = trace['read_traces'](args.csn_traces, args.csn_manifest, wanted)
        except ValueError as exc:
            print('incomplete CSN allocation trace: %s' % exc)
            return 2
        local = set()
        for kind, domain, csn, _, _ in events:
            if domain.strip().lower() != base_dn:
                continue
            pair = (int(csn[16:20], 16), int(csn[20:], 16))
            if kind == 'L':
                local.add(pair)
            else:
                offline_only.setdefault(pair, set()).add(csn)
        for pair in local:
            offline_only.pop(pair, None)
    lost = False
    for server_id, load in loads.items():
        seqnums = [seqnum for seqnum, _, _ in load]
        missing = []
        offline = {}
        for previous, following in zip(load, load[1:]):
            for seqnum in range(previous[0] + 1, following[0]):
                if (server_id, seqnum) in offline_only:
                    continue
                missing.append((seqnum, previous, following))
                matched = offline_matches(evidence, server_id, seqnum, previous, following)
                if matched:
                    offline[seqnum] = matched
        print("replica %d: %d changes in the load, sequence numbers %d to %d, %d missing"
              % (server_id, len(load), seqnums[0], seqnums[-1], len(missing)))
        for (sid, seqnum), csns in sorted(offline_only.items()):
            if sid == server_id:
                print('  offline-only %d: CSN %s; complete traces contain no local allocation'
                      % (seqnum, ', '.join(sorted(csns))))
        for seqnum, csns in sorted(offline.items()):
            print("  offline %d: captured CSN %s; a restart may have reused the sequence" % (seqnum, ', '.join(csns)))
        for seqnum, previous, following in missing[:20]:
            print("  missing %d, between the %s %d (%d) and the %s %d (%d)"
                  % (seqnum, previous[2], previous[0], previous[1], following[2], following[0], following[1]))
        if len(missing) > 20:
            print("  and %d more" % (len(missing) - 20))
        lost = lost or bool(missing)
    return 1 if lost else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
