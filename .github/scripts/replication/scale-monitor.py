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

"""Read changelog monitors and check membership over loopback LDAPS without a client JVM.

This implements LDAPv3 simple bind and the searches needed for the scale checks.
A non-success result, referral, malformed response or timeout never yields a snapshot.
The caller supplies a loopback-only published administration port and an absolute deadline;
the bind password is read from standard input, not from the command line.
"""

import base64
import re
import socket
import ssl
import sys
import time

MAX_FRAME = 8 * 1024 * 1024
MAX_SNAPSHOT = 8 * 1024 * 1024


class LdapError(Exception):
    pass


def tlv(tag, value):
    length = len(value)
    if length < 128:
        encoded = bytes([length])
    else:
        raw = length.to_bytes((length.bit_length() + 7) // 8, 'big')
        encoded = bytes([0x80 | len(raw)]) + raw
    return bytes([tag]) + encoded + value


def fields(data):
    position = 0
    while position < len(data):
        if position + 2 > len(data):
            raise LdapError('truncated BER field')
        tag, length = data[position], data[position + 1]
        position += 2
        if length & 0x80:
            size = length & 0x7f
            if not size or size > 4 or position + size > len(data):
                raise LdapError('invalid BER length')
            length = int.from_bytes(data[position:position + size], 'big')
            position += size
        if length > MAX_FRAME or position + length > len(data):
            raise LdapError('truncated or oversized BER value')
        yield tag, data[position:position + length]
        position += length


def receive(sock, length, deadline):
    data = bytearray()
    while len(data) < length:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('LDAP request timed out')
        sock.settimeout(remaining)
        try:
            chunk = sock.recv(length - len(data))
        except socket.timeout as error:
            raise TimeoutError('LDAP request timed out') from error
        if not chunk:
            raise EOFError('LDAP connection closed before the response')
        data.extend(chunk)
    return bytes(data)


def transmit(sock, data, deadline):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise TimeoutError('LDAP request timed out')
    sock.settimeout(remaining)
    try:
        sock.sendall(data)
    except socket.timeout as error:
        raise TimeoutError('LDAP request timed out') from error


def read_frame(sock, deadline):
    header = receive(sock, 2, deadline)
    if header[0] != 0x30:
        raise LdapError('LDAP response is not a sequence')
    length = header[1]
    if length & 0x80:
        size = length & 0x7f
        if not size or size > 4:
            raise LdapError('invalid LDAP message length')
        length = int.from_bytes(receive(sock, size, deadline), 'big')
    if length > MAX_FRAME:
        raise LdapError('LDAP message exceeds size limit')
    return receive(sock, length, deadline)


def message(sock, wanted_id, deadline):
    parts = list(fields(read_frame(sock, deadline)))
    if len(parts) < 2 or parts[0][0] != 0x02 or not parts[0][1]:
        raise LdapError('invalid LDAP message envelope')
    if int.from_bytes(parts[0][1], 'big', signed=True) != wanted_id:
        raise LdapError('unexpected LDAP message ID')
    if len(parts) > 3 or (len(parts) == 3 and parts[2][0] != 0xa0):
        raise LdapError('unexpected LDAP message fields')
    return parts[1]


def require_success(value):
    parts = list(fields(value))
    if len(parts) < 3 or [part[0] for part in parts[:3]] != [0x0a, 0x04, 0x04] or not parts[0][1]:
        raise LdapError('invalid LDAP result')
    code = int.from_bytes(parts[0][1], 'big', signed=True)
    if code != 0:
        raise LdapError('LDAP operation failed with result %s' % code)


def entry_ldif(value):
    parts = list(fields(value))
    if len(parts) != 2 or parts[0][0] != 0x04 or parts[1][0] != 0x30:
        raise LdapError('invalid LDAP search entry')
    lines = ['dn:: ' + base64.b64encode(parts[0][1]).decode('ascii')]
    for tag, attr in fields(parts[1][1]):
        items = list(fields(attr))
        if tag != 0x30 or len(items) != 2 or items[0][0] != 0x04 or items[1][0] != 0x31:
            raise LdapError('invalid LDAP attribute')
        name = items[0][1].decode('ascii')
        if not re.fullmatch(r'[A-Za-z0-9;-]+', name):
            raise LdapError('invalid LDAP attribute name')
        for value_tag, content in fields(items[1][1]):
            if value_tag != 0x04:
                raise LdapError('invalid LDAP attribute value')
            lines.append(name + ':: ' + base64.b64encode(content).decode('ascii'))
    return '\n'.join(lines) + '\n\n'


def search(sock, password, deadline, present=b'replicationServer-database',
           attributes=(b'replicationServer-database', b'domain-name', b'last-change')):
    bind = tlv(0x02, b'\x03') + tlv(0x04, b'cn=Directory Manager') + tlv(0x80, password)
    transmit(sock, tlv(0x30, tlv(0x02, b'\x01') + tlv(0x60, bind)), deadline)
    tag, result = message(sock, 1, deadline)
    if tag != 0x61:
        raise LdapError('expected LDAP bind response')
    require_success(result)
    query = (tlv(0x04, b'cn=monitor') + tlv(0x0a, b'\x02') + tlv(0x0a, b'\x00') +
             tlv(0x02, b'\x00') + tlv(0x02, b'\x00') + tlv(0x01, b'\x00') +
             tlv(0x87, present) + tlv(0x30, b''.join(tlv(0x04, attr) for attr in attributes)))
    transmit(sock, tlv(0x30, tlv(0x02, b'\x02') + tlv(0x63, query)), deadline)
    entries = []
    size = 0
    while True:
        tag, value = message(sock, 2, deadline)
        if tag == 0x64:
            entry = entry_ldif(value)
            size += len(entry)
            if size > MAX_SNAPSHOT:
                raise LdapError('LDAP snapshot exceeds size limit')
            entries.append(entry)
        elif tag == 0x65:
            require_success(value)
            if time.monotonic() >= deadline:
                raise TimeoutError('LDAP response completed after the deadline')
            return 'version: 1\n\n' + ''.join(entries)
        else:
            raise LdapError('unexpected LDAP search response')


def entry_attributes(value):
    parts = list(fields(value))
    if len(parts) != 2 or parts[0][0] != 0x04 or parts[1][0] != 0x30:
        raise LdapError('invalid LDAP search entry')
    parts[0][1].decode('utf-8')
    attributes = {}
    for tag, attribute in fields(parts[1][1]):
        items = list(fields(attribute))
        if tag != 0x30 or len(items) != 2 or items[0][0] != 0x04 or items[1][0] != 0x31:
            raise LdapError('invalid LDAP attribute')
        name = items[0][1].decode('ascii').lower()
        if not re.fullmatch(r'[a-z0-9;-]+', name) or name in attributes:
            raise LdapError('invalid or duplicate LDAP attribute')
        values = []
        for value_tag, content in fields(items[1][1]):
            if value_tag != 0x04:
                raise LdapError('invalid LDAP attribute value')
            values.append(content.decode('utf-8'))
        attributes[name] = values
    return attributes


def membership_search(sock, deadline, message_id, base, scope, ldap_filter, attribute):
    query = (tlv(0x04, base) + tlv(0x0a, bytes([scope])) + tlv(0x0a, b'\x00') +
             tlv(0x02, b'\x00') + tlv(0x02, b'\x00') + tlv(0x01, b'\x00') +
             ldap_filter + tlv(0x30, tlv(0x04, attribute)))
    transmit(sock, tlv(0x30, tlv(0x02, bytes([message_id])) + tlv(0x63, query)), deadline)
    entries, size = [], 0
    while True:
        tag, value = message(sock, message_id, deadline)
        if tag == 0x64:
            size += len(value)
            if size > MAX_SNAPSHOT:
                raise LdapError('LDAP snapshot exceeds size limit')
            entries.append(entry_attributes(value))
        elif tag == 0x65:
            require_success(value)
            if time.monotonic() >= deadline:
                raise TimeoutError('LDAP response completed after the deadline')
            return entries
        else:
            raise LdapError('unexpected LDAP search response')


def membership_matches(registry, config, peer, wanted):
    if wanted not in ('present', 'absent') or not registry or not config:
        return False
    peer = peer.lower()
    def matches(host):
        host = host.lower()
        return host == peer or host.split('.')[0] == peer
    hosts = [host for entry in registry for host in entry.get('hostname', [])]
    if not hosts or any(not host for host in hosts):
        return False
    found = any(matches(host) for host in hosts)
    lists = [any(matches(re.sub(r':[0-9]+$', '', host))
                 for host in entry.get('ds-cfg-replication-server', [])) for entry in config]
    return (found and all(lists)) if wanted == 'present' else (not found and not any(lists))


def membership(sock, password, deadline, peer, wanted):
    bind = tlv(0x02, b'\x03') + tlv(0x04, b'cn=Directory Manager') + tlv(0x80, password)
    transmit(sock, tlv(0x30, tlv(0x02, b'\x01') + tlv(0x60, bind)), deadline)
    tag, result = message(sock, 1, deadline)
    if tag != 0x61:
        raise LdapError('expected LDAP bind response')
    require_success(result)
    registry = membership_search(sock, deadline, 2, b'cn=Servers,cn=admin data', 1,
                                 tlv(0x87, b'objectClass'), b'hostname')
    equal = lambda value: tlv(0xa3, tlv(0x04, b'objectClass') + tlv(0x04, value))
    ldap_filter = tlv(0xa1, equal(b'ds-cfg-replication-server') + equal(b'ds-cfg-replication-domain'))
    config = membership_search(sock, deadline, 3, b'cn=config', 2, ldap_filter,
                               b'ds-cfg-replication-server')
    return membership_matches(registry, config, peer, wanted)


def main(argv):
    if len(argv) not in (3, 6) or (len(argv) == 6 and (argv[3] != 'membership' or argv[5] not in ('present', 'absent'))):
        print('usage: scale-monitor.py <loopback port> <load deadline in milliseconds> [membership <peer> <present|absent>]', file=sys.stderr)
        return 2
    try:
        port, load_deadline = int(argv[1]), int(argv[2]) / 1000
        if not 1 <= port <= 65535:
            raise ValueError('invalid loopback port')
        password = sys.stdin.buffer.read(4097)
        if not password or len(password) > 4096:
            raise ValueError('invalid bind password')
        remaining = min(45.0, load_deadline - time.time())
        if remaining <= 0:
            raise TimeoutError('load deadline reached')
        deadline = time.monotonic() + remaining
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE  # Same trust policy as the existing --trustAll search.
        with socket.create_connection(('127.0.0.1', port), timeout=remaining) as raw:
            with context.wrap_socket(raw, server_hostname='localhost') as connection:
                output = search(connection, password, deadline) if len(argv) == 3 else None
                matched = membership(connection, password, deadline, argv[4], argv[5]) if len(argv) == 6 else True
        if time.monotonic() >= deadline or time.time() >= load_deadline:
            raise TimeoutError('LDAP response completed after the deadline')
        if output is not None:
            sys.stdout.write(output)
        return 0 if matched else 1
    except (TimeoutError, socket.timeout):
        print('timeout=' + ('load' if time.time() >= load_deadline else 'query'), file=sys.stderr)
        return 124
    except (OSError, ValueError, UnicodeError, LdapError, EOFError) as error:
        print('scale monitor failed: %s' % error, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main(sys.argv))
