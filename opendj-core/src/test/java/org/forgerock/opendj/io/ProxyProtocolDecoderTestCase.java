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

import static org.testng.Assert.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import org.forgerock.opendj.ldap.SdkTestCase;
import org.testng.annotations.Test;

/** Tests parsing PROXY headers without consuming subsequent LDAP or TLS bytes. */
@SuppressWarnings("javadoc")
public class ProxyProtocolDecoderTestCase extends SdkTestCase {
    @Test
    public void fragmentedV1HeaderLeavesBufferUnchangedUntilComplete() throws Exception {
        byte[] frame = "PROXY TCP4 192.0.2.1 198.51.100.2 12345 389\r\n".getBytes(StandardCharsets.US_ASCII);
        for (int length = 0; length < frame.length; length++) {
            ByteBuffer input = ByteBuffer.wrap(frame, 0, length);
            assertNull(ProxyProtocolDecoder.decode(input), "prefix length " + length);
            assertEquals(input.position(), 0);
        }
        ByteBuffer input = ByteBuffer.allocate(frame.length + 2).put(frame).put((byte) 0x30).put((byte) 0x0c);
        input.flip();
        ProxyProtocolHeader header = ProxyProtocolDecoder.decode(input);
        assertEquals(header.getSourceAddress(), new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 12345));
        assertEquals(input.remaining(), 2);
        assertEquals(input.get(), (byte) 0x30);
    }

    @Test
    public void v1Ipv6AndUnknownUseCorrectSource() throws Exception {
        ProxyProtocolHeader ipv6 = decode("PROXY TCP6 2001:db8::1 2001:db8::2 443 389\r\n");
        assertEquals(ipv6.getSourceAddress(), new InetSocketAddress(InetAddress.getByName("2001:db8::1"), 443));
        assertNull(decode("PROXY UNKNOWN\r\n").getSourceAddress());
        assertNull(decode("PROXY UNKNOWN ignored information\r\n").getSourceAddress());
    }

    @Test
    public void v2SourceAndTrailingBytesSurviveEverySplit() throws Exception {
        byte[] payload = { (byte) 192, 0, 2, 1, (byte) 198, 51, 100, 2, 0x30, 0x39, 1, (byte) 0x85 };
        byte[] frame = v2(0x21, 0x11, payload);
        for (int length = 0; length < frame.length; length++) {
            ByteBuffer input = ByteBuffer.wrap(frame, 0, length);
            assertNull(ProxyProtocolDecoder.decode(input), "prefix length " + length);
            assertEquals(input.position(), 0);
        }
        ByteBuffer input = ByteBuffer.allocate(frame.length + 1).put(frame).put((byte) 0x30);
        input.flip();
        ProxyProtocolHeader header = ProxyProtocolDecoder.decode(input);
        assertEquals(header.getSourceAddress(), new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 12345));
        assertEquals(input.get(), (byte) 0x30);
    }

    @Test
    public void localAndUnspecUseActualPeerAndConsumeAnnouncedLength() throws Exception {
        ByteBuffer local = ByteBuffer.wrap(v2(0x20, 0xff, new byte[] { 1, 2, 3 }));
        assertNull(ProxyProtocolDecoder.decode(local).getSourceAddress());
        assertEquals(local.remaining(), 0);
        assertNull(ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0, new byte[0]))).getSourceAddress());
        assertNull(ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0x10, new byte[8]))).getSourceAddress());
        assertNull(ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0x01, new byte[0]))).getSourceAddress());
    }

    @Test
    public void rejectsMalformedHeaders() throws Exception {
        for (String text : new String[] {
                "PROXY TCP4 192.0.2.01 198.51.100.2 123 389\r\n",
                "PROXY TCP4 hostname 198.51.100.2 123 389\r\n",
                "PROXY TCP4 192.0.2.1 198.51.100.2 65536 389\r\n",
                "PROXY TCP4 192.0.2.1 198.51.100.2 123 389\n",
                "PROXY TCP4 " + "a".repeat(100) }) {
            try {
                decode(text);
                fail("accepted " + text);
            } catch (IOException expected) {
                // Rejected before any LDAP payload can be processed.
            }
        }
        for (byte[] frame : new byte[][] {
                v2(0x31, 0x11, new byte[12]), v2(0x22, 0, new byte[0]),
                v2(0x21, 0x41, new byte[0]), v2(0x21, 0x11, new byte[11]),
                v2(0x21, 0, new byte[] { 0x20, 0, 5, 1 }) }) {
            try {
                ProxyProtocolDecoder.decode(ByteBuffer.wrap(frame));
                fail("accepted invalid v2 frame");
            } catch (IOException expected) {
                // Expected.
            }
        }
    }

    @Test
    public void validatedSslTlvCarriesFlagAndCipher() throws Exception {
        byte[] address = { (byte) 192, 0, 2, 1, (byte) 198, 51, 100, 2, 0x30, 0x39, 1, (byte) 0x85 };
        byte[] cipher = "AES128-GCM-SHA256".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer payload = ByteBuffer.allocate(address.length + 3 + 5 + 3 + cipher.length);
        payload.put(address).put((byte) 0x20).putShort((short) (5 + 3 + cipher.length));
        payload.put((byte) 1).putInt(1).put((byte) 0x23).putShort((short) cipher.length).put(cipher);
        ProxyProtocolHeader header = ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0x11, payload.array())));
        assertTrue(header.isClientSsl());
        assertEquals(header.getClientCipher(), "AES128-GCM-SHA256");
    }

    @Test
    public void v1Ipv4MappedIpv6UsesTheIpv4Address() throws Exception {
        ProxyProtocolHeader header = decode("PROXY TCP6 ::ffff:192.0.2.1 ::1 123 389\r\n");
        assertEquals(header.getSourceAddress(), new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 123));
    }

    @Test
    public void v2Ipv4MappedIpv6UsesTheIpv4Address() throws Exception {
        byte[] payload = new byte[36];
        payload[10] = (byte) 0xff;
        payload[11] = (byte) 0xff;
        payload[12] = (byte) 192;
        payload[14] = 2;
        payload[15] = 1;
        payload[31] = 1;
        payload[32] = 0x30;
        payload[33] = 0x39;
        payload[34] = 1;
        payload[35] = (byte) 0x85;
        ProxyProtocolHeader header = ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0x21, payload)));
        assertEquals(header.getSourceAddress(), new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 12345));
    }

    @Test
    public void unspecifiedFamilyPreservesSslMetadata() throws Exception {
        byte[] cipher = "AES128-GCM-SHA256".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer payload = ByteBuffer.allocate(3 + 5 + 3 + cipher.length);
        payload.put((byte) 0x20).putShort((short) (5 + 3 + cipher.length));
        payload.put((byte) 1).putInt(0).put((byte) 0x23).putShort((short) cipher.length).put(cipher);
        ProxyProtocolHeader header = ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0, payload.array())));
        assertNull(header.getSourceAddress());
        assertTrue(header.isClientSsl());
        assertEquals(header.getClientCipher(), "AES128-GCM-SHA256");
        ProxyProtocolHeader local = ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x20, 0, payload.array())));
        assertFalse(local.isClientSsl());
        assertNull(local.getClientCipher());
    }

    @Test
    public void maximumLengthFramesAreConsumedExactly() throws Exception {
        String line = "PROXY UNKNOWN " + "x".repeat(91) + "\r\n";
        assertEquals(line.length(), 107);
        assertNull(decode(line).getSourceAddress());
        byte[] payload = new byte[65535];
        payload[0] = 4;
        payload[1] = (byte) 0xff;
        payload[2] = (byte) 0xfc;
        ByteBuffer input = ByteBuffer.allocate(16 + payload.length + 1).put(v2(0x21, 0, payload)).put((byte) 0x30);
        input.flip();
        assertNull(ProxyProtocolDecoder.decode(input).getSourceAddress());
        assertEquals(input.get(), (byte) 0x30);
    }

    @Test
    public void rejectsDuplicateSslTlvAndMalformedNestedTlv() throws Exception {
        byte[] address = { (byte) 192, 0, 2, 1, (byte) 198, 51, 100, 2, 0x30, 0x39, 1, (byte) 0x85 };
        byte[] ssl = { 0x20, 0, 5, 1, 0, 0, 0, 0 };
        ByteBuffer duplicate = ByteBuffer.allocate(address.length + 2 * ssl.length).put(address).put(ssl).put(ssl);
        ByteBuffer malformed = ByteBuffer.allocate(address.length + 10).put(address)
                .put(new byte[] { 0x20, 0, 7, 1, 0, 0, 0, 0, 0x23, 0 });
        for (byte[] payload : new byte[][] { duplicate.array(), malformed.array() }) {
            try {
                ProxyProtocolDecoder.decode(ByteBuffer.wrap(v2(0x21, 0x11, payload)));
                fail("accepted invalid SSL TLV");
            } catch (IOException expected) {
                // Expected.
            }
        }
    }

    @Test
    public void malformedHeaderHasAnIdentifiableProtocolError() throws Exception {
        try {
            decode("PROXY TCP4 bad 198.51.100.2 123 389\r\n");
            fail("invalid address was accepted");
        } catch (IOException e) {
            assertTrue(e instanceof ProxyProtocolDecoder.InvalidHeaderException);
            assertTrue(e.getMessage().startsWith("Invalid PROXY protocol header:"));
        }
    }

    private static ProxyProtocolHeader decode(String text) throws IOException {
        return ProxyProtocolDecoder.decode(ByteBuffer.wrap(text.getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] v2(int command, int family, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + payload.length);
        buffer.put(new byte[] { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 });
        buffer.put((byte) command).put((byte) family).putShort((short) payload.length).put(payload);
        return buffer.array();
    }
}
