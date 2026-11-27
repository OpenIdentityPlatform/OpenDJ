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
"""Small LDAP client used in the nginx integration test's separate network namespace."""

import socket
import ssl
import sys
import time

BIND = bytes.fromhex("30 0c 02 01 01 60 07 02 01 03 04 00 80 00")
SEARCH = bytes.fromhex(
    "30 25 02 01 02 63 20 04 00 0a 01 00 0a 01 00 02 01 00 02 01 00 "
    "01 01 00 87 0b 6f 62 6a 65 63 74 43 6c 61 73 73 30 00"
)
STARTTLS = bytes.fromhex("30 1d 02 01 03 77 18 80 16") + b"1.3.6.1.4.1.1466.20037"


def read_exact(connection, count):
    chunks = []
    while count:
        chunk = connection.recv(count)
        if not chunk:
            raise EOFError("connection closed before the LDAP response was complete")
        chunks.append(chunk)
        count -= len(chunk)
    return b"".join(chunks)


def read_message(connection):
    tag, length = read_exact(connection, 2)
    if tag != 0x30:
        raise ValueError("invalid LDAP message sequence")
    if length & 0x80:
        length = int.from_bytes(read_exact(connection, length & 0x7f), "big")
    return read_exact(connection, length)


def operation(message):
    if message[:2] != b"\x02\x01":
        raise ValueError("unexpected message ID encoding")
    tag, length = message[3:5]
    position = 5
    if length & 0x80:
        size = length & 0x7f
        length = int.from_bytes(message[position:position + size], "big")
        position += size
    return message[2], tag, message[position:position + length]


def require_success(message, message_id, expected_tag):
    result_id, tag, content = operation(message)
    if result_id != message_id or tag != expected_tag or content[:3] != b"\x0a\x01\x00":
        raise ValueError("unexpected LDAP result: " + message.hex())


def coalesced_handshake(connection, hostname, source, destination_port):
    """Send the PROXY header and a genuine TLS ClientHello in the same write."""
    context = ssl.create_default_context(cafile="/tmp/server-cert.pem")
    incoming = ssl.MemoryBIO()
    outgoing = ssl.MemoryBIO()
    tls = context.wrap_bio(incoming, outgoing, server_hostname=hostname)
    try:
        tls.do_handshake()
        raise ValueError("the TLS client did not request a server response")
    except ssl.SSLWantReadError:
        pass
    hello = outgoing.read()
    if not hello:
        raise ValueError("TLS client did not produce a ClientHello")
    header = f"PROXY TCP4 {source} 127.0.0.1 12345 {destination_port}\r\n".encode("ascii")
    connection.sendall(header + hello)
    while True:
        try:
            tls.do_handshake()
            break
        except ssl.SSLWantReadError:
            pending = outgoing.read()
            if pending:
                connection.sendall(pending)
            data = connection.recv(16384)
            if not data:
                raise EOFError("server closed during the TLS handshake")
            incoming.write(data)
    pending = outgoing.read()
    if pending:
        connection.sendall(pending)
    print("tls=success", flush=True)
    time.sleep(2)


def main():
    mode, host, port = sys.argv[1:4]
    connection = socket.create_connection((host, int(port)), timeout=10)
    with connection:
        if mode == "coalesced":
            coalesced_handshake(connection, sys.argv[4], sys.argv[5], port)
            return
        if mode == "reject":
            try:
                connection.sendall(BIND)
                if connection.recv(1):
                    raise ValueError("unexpected LDAP response on a rejected connection")
            except (ConnectionResetError, BrokenPipeError):
                pass
            print("rejected")
            return
        if mode == "starttls":
            connection.sendall(STARTTLS)
            require_success(read_message(connection), 3, 0x78)
        if mode in ("ldaps", "starttls"):
            context = ssl.create_default_context(cafile="/tmp/server-cert.pem")
            connection = context.wrap_socket(connection, server_hostname=sys.argv[4])
        connection.sendall(BIND)
        require_success(read_message(connection), 1, 0x61)
        connection.sendall(SEARCH)
        found_entry = False
        while True:
            message = read_message(connection)
            message_id, tag, _ = operation(message)
            if message_id != 2:
                raise ValueError("unexpected LDAP response ID")
            if tag == 0x64:
                found_entry = True
            elif tag == 0x65:
                require_success(message, 2, 0x65)
                break
            else:
                raise ValueError("unexpected LDAP search response")
        if not found_entry:
            raise ValueError("root DSE search returned no entry")
        print("bind=success search=success", flush=True)
        time.sleep(2)
        connection.close()


if __name__ == "__main__":
    main()
