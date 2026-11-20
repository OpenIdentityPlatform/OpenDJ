/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Stateless decoder for a single PROXY protocol v1 or v2 header. */
public final class ProxyProtocolDecoder {
    /** A malformed PROXY header, distinct from an I/O failure on the socket. */
    public static final class InvalidHeaderException extends IOException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates a protocol error with a description that does not include untrusted input.
         * @param message a description of the error
         */
        public InvalidHeaderException(String message) {
            super("Invalid PROXY protocol header: " + message);
        }
    }

    private static final byte[] V1 = { 'P', 'R', 'O', 'X', 'Y', ' ' };
    private static final byte[] V2 = { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 };
    private static final int MAX_V1 = 107;
    private static final ProxyProtocolHeader PEER = new ProxyProtocolHeader(null, false, null);

    private ProxyProtocolDecoder() {
    }

    /**
     * Decodes the next complete header. Incomplete input leaves the position unchanged.
     *
     * @param input available network bytes
     * @return the decoded header, or null when more bytes are needed
     * @throws IOException if the header is malformed
     */
    public static ProxyProtocolHeader decode(ByteBuffer input) throws IOException {
        ByteBuffer bytes = input.slice();
        if (!bytes.hasRemaining()) {
            return null;
        }
        byte[] signature = bytes.get(0) == V1[0] ? V1 : V2;
        int matched = Math.min(bytes.remaining(), signature.length);
        for (int i = 0; i < matched; i++) {
            if (bytes.get(i) != signature[i]) {
                throw invalid("invalid signature");
            }
        }
        if (bytes.remaining() < signature.length) {
            return null;
        }
        ProxyProtocolHeader result = signature == V1 ? decodeV1(bytes) : decodeV2(bytes);
        if (result != null) {
            input.position(input.position() + bytes.position());
        }
        return result;
    }

    private static ProxyProtocolHeader decodeV1(ByteBuffer bytes) throws IOException {
        int end = -1;
        for (int i = 0; i < Math.min(bytes.limit(), MAX_V1); i++) {
            byte b = bytes.get(i);
            if (b == '\n') {
                if (i == 0 || bytes.get(i - 1) != '\r') {
                    throw invalid("line does not end in CRLF");
                }
                end = i + 1;
                break;
            }
            if ((b < 0x20 || b > 0x7e) && b != '\r') {
                throw invalid("invalid text header");
            }
            if (b == '\r' && i + 1 < bytes.limit() && bytes.get(i + 1) != '\n') {
                throw invalid("line does not end in CRLF");
            }
        }
        if (end < 0) {
            if (bytes.limit() >= MAX_V1) {
                throw invalid("text header too long");
            }
            return null;
        }
        byte[] line = new byte[end - 2];
        bytes.get(line);
        bytes.getShort();
        String[] fields = new String(line, StandardCharsets.US_ASCII).split(" ", -1);
        if (fields.length >= 2 && "UNKNOWN".equals(fields[1])) {
            return PEER;
        }
        if (fields.length != 6 || !("TCP4".equals(fields[1]) || "TCP6".equals(fields[1]))) {
            throw invalid("invalid text header fields");
        }
        boolean ipv6 = "TCP6".equals(fields[1]);
        InetAddress source = parseAddress(fields[2], ipv6);
        parseAddress(fields[3], ipv6);
        int sourcePort = parsePort(fields[4]);
        parsePort(fields[5]);
        return new ProxyProtocolHeader(new InetSocketAddress(source, sourcePort), false, null);
    }

    private static InetAddress parseAddress(String text, boolean ipv6) throws IOException {
        if (ipv6) {
            if (!text.contains(":") || !text.matches("[0-9a-fA-F:.]+")) {
                throw invalid("invalid IPv6 address");
            }
            InetAddress address;
            try {
                address = InetAddress.getByName(text);
            } catch (UnknownHostException e) {
                throw invalid("invalid IPv6 address");
            }
            // Java converts IPv4-mapped IPv6 literals to IPv4, keeping access rules and limits consistent.
            return address;
        }
        String[] octets = text.split("\\.", -1);
        if (octets.length != 4) {
            throw invalid("invalid IPv4 address");
        }
        byte[] value = new byte[4];
        for (int i = 0; i < value.length; i++) {
            if (!octets[i].matches("0|[1-9][0-9]{0,2}")) {
                throw invalid("invalid IPv4 address");
            }
            int n;
            try {
                n = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                throw invalid("invalid IPv4 address");
            }
            if (n > 255) {
                throw invalid("invalid IPv4 address");
            }
            value[i] = (byte) n;
        }
        return InetAddress.getByAddress(value);
    }

    private static int parsePort(String text) throws IOException {
        if (!text.matches("0|[1-9][0-9]{0,4}")) {
            throw invalid("invalid port");
        }
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw invalid("invalid port");
        }
        if (port > 65535) {
            throw invalid("invalid port");
        }
        return port;
    }

    private static ProxyProtocolHeader decodeV2(ByteBuffer bytes) throws IOException {
        if (bytes.remaining() < 16) {
            return null;
        }
        int versionCommand = bytes.get(12) & 0xff;
        int family = bytes.get(13) & 0xff;
        int length = ((bytes.get(14) & 0xff) << 8) | (bytes.get(15) & 0xff);
        if ((versionCommand & 0xf0) != 0x20 || (versionCommand & 0x0f) > 1) {
            throw invalid("invalid version or command");
        }
        if (bytes.limit() < 16 + length) {
            return null;
        }
        bytes.position(16);
        bytes.limit(16 + length);
        if ((versionCommand & 0xf) == 0) {
            bytes.position(bytes.limit());
            return PEER;
        }
        int addressFamily = family >>> 4;
        int transport = family & 0xf;
        if (addressFamily > 3 || transport > 2) {
            throw invalid("invalid address family or transport");
        }
        if ((addressFamily == 0) != (transport == 0)) {
            // The missing family or transport leaves the address layout unspecified: ignore the block.
            bytes.position(bytes.limit());
            return PEER;
        }
        int addressLength = addressFamily == 1 ? 12 : addressFamily == 2 ? 36 : addressFamily == 3 ? 216 : 0;
        if (length < addressLength) {
            throw invalid("short address block");
        }
        InetSocketAddress source = null;
        if ((addressFamily == 1 || addressFamily == 2) && transport == 1) {
            int size = addressFamily == 1 ? 4 : 16;
            byte[] address = new byte[size];
            bytes.get(address);
            bytes.position(bytes.position() + size);
            int port = bytes.getShort() & 0xffff;
            bytes.getShort();
            // getByAddress normalizes IPv4-mapped IPv6 addresses to IPv4, as in the v1 parser.
            InetAddress ip = InetAddress.getByAddress(address);
            source = new InetSocketAddress(ip, port);
        } else {
            bytes.position(16 + addressLength);
        }
        boolean sslSeen = false;
        boolean clientSsl = false;
        String cipher = null;
        while (bytes.hasRemaining()) {
            int type = readType(bytes);
            ByteBuffer value = readValue(bytes);
            if (type == 0x20) {
                if (sslSeen || value.remaining() < 5) {
                    throw invalid("invalid SSL TLV");
                }
                sslSeen = true;
                clientSsl = (value.get() & 1) != 0;
                value.getInt();
                while (value.hasRemaining()) {
                    int subType = readType(value);
                    ByteBuffer sub = readValue(value);
                    if (subType == 0x23) {
                        if (cipher != null || !sub.hasRemaining()) {
                            throw invalid("invalid SSL cipher TLV");
                        }
                        byte[] cipherBytes = new byte[sub.remaining()];
                        sub.get(cipherBytes);
                        for (byte b : cipherBytes) {
                            if (b < 0x21 || b > 0x7e) {
                                throw invalid("invalid SSL cipher");
                            }
                        }
                        cipher = new String(cipherBytes, StandardCharsets.US_ASCII);
                    }
                }
            }
        }
        return source != null || addressFamily == 0 && transport == 0
                ? new ProxyProtocolHeader(source, clientSsl, cipher) : PEER;
    }

    private static int readType(ByteBuffer bytes) throws IOException {
        if (bytes.remaining() < 3) {
            throw invalid("truncated TLV");
        }
        return bytes.get() & 0xff;
    }

    private static ByteBuffer readValue(ByteBuffer bytes) throws IOException {
        int length = bytes.getShort() & 0xffff;
        if (length > bytes.remaining()) {
            throw invalid("truncated TLV value");
        }
        ByteBuffer value = bytes.slice();
        value.limit(length);
        bytes.position(bytes.position() + length);
        return value;
    }

    private static InvalidHeaderException invalid(String message) {
        return new InvalidHeaderException(message);
    }
}
