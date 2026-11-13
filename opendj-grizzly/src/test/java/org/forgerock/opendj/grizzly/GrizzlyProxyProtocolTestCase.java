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
package org.forgerock.opendj.grizzly;

import static com.forgerock.reactive.RxJavaStreams.emptyStream;
import static org.testng.Assert.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.io.ProxyProtocolHeader;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPClientContextEventListener;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.util.Options;
import org.testng.annotations.Test;

/** Tests a PROXY listener with real TCP sockets. */
@SuppressWarnings("javadoc")
public class GrizzlyProxyProtocolTestCase extends SdkTestCase {
    private static final byte[] BIND = { 0x30, 0x0c, 0x02, 0x01, 0x01, 0x60, 0x07,
            0x02, 0x01, 0x03, 0x04, 0x00, (byte) 0x80, 0x00 };

    @Test(timeOut = 15000)
    public void proxyHeaderIsAppliedBeforeARequestInTheSameWrite() throws Exception {
        CountDownLatch requestSeen = new CountDownLatch(1);
        AtomicReference<InetSocketAddress> observedAddress = new AtomicReference<>();
        AtomicReference<InetSocketAddress> tcpPeer = new AtomicReference<>();
        AtomicReference<String> ordering = new AtomicReference<>("");
        Options options = Options.defaultOptions().set(LDAPListener.PROXY_PROTOCOL_ENABLED, true);
        try (LDAPListener listener = new LDAPListener(0, context -> {
            context.addListener(new LDAPClientContextEventListener() {
                @Override
                public void handleProxyProtocolHeader(LDAPClientContext ctx, ProxyProtocolHeader header) {
                    observedAddress.set(ctx.getPeerAddress());
                    tcpPeer.set(ctx.getTcpPeerAddress());
                    ordering.set(ordering.get() + "H");
                }
                @Override public void handleConnectionError(LDAPClientContext ctx, Throwable error) { }
                @Override public void handleConnectionClosed(LDAPClientContext ctx, UnbindRequest req) { }
                @Override public void handleConnectionDisconnected(LDAPClientContext ctx, ResultCode code, String msg) { }
            });
            return (ctx, request) -> {
                ordering.set(ordering.get() + "R");
                requestSeen.countDown();
                return emptyStream();
            };
        }, options); Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.firstSocketAddress().getPort())) {
            socket.getOutputStream().write("PROXY TCP4 192.0.2.4 198.51.100.2 12345 389\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(BIND);
            socket.getOutputStream().flush();
            assertTrue(requestSeen.await(5, TimeUnit.SECONDS), "LDAP request was not processed");
            assertEquals(ordering.get(), "HR");
            assertEquals(observedAddress.get(), new InetSocketAddress(InetAddress.getByName("192.0.2.4"), 12345));
            assertTrue(tcpPeer.get().getAddress().isLoopbackAddress());
        }
    }

    @Test(timeOut = 15000)
    public void sslTlvContributesKnownCipherStrengthWithoutCreatingAnSslSession() throws Exception {
        assertProxyTls("ECDHE-RSA-AES128-GCM-SHA256", true, 128);
        assertProxyTls("ECDHE-RSA-AES128-SHA256", true, 128);
        assertProxyTls("TLS_AES_256_GCM_SHA384", true, 256);
        assertProxyTls("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", true, 256);
        assertProxyTls("NOT-A-KNOWN-CIPHER", true, 0);
        assertProxyTls(null, true, 0);
        assertProxyTls("ECDHE-RSA-AES128-GCM-SHA256", false, 0);
    }

    @Test(timeOut = 15000)
    public void fragmentedHeaderIsConsumedBeforeTheLdapRequest() throws Exception {
        CountDownLatch requestSeen = new CountDownLatch(1);
        AtomicReference<InetSocketAddress> peer = new AtomicReference<>();
        Options options = Options.defaultOptions().set(LDAPListener.PROXY_PROTOCOL_ENABLED, true);
        try (LDAPListener listener = new LDAPListener(0, ctx -> (c, request) -> {
            peer.set(c.getPeerAddress());
            requestSeen.countDown();
            return emptyStream();
        }, options); Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.firstSocketAddress().getPort())) {
            byte[] header = "PROXY TCP4 192.0.2.4 198.51.100.2 12345 389\r\n".getBytes(StandardCharsets.US_ASCII);
            for (byte b : header) {
                socket.getOutputStream().write(b);
                socket.getOutputStream().flush();
            }
            socket.getOutputStream().write(BIND);
            socket.getOutputStream().flush();
            assertTrue(requestSeen.await(5, TimeUnit.SECONDS));
            assertEquals(peer.get(), new InetSocketAddress(InetAddress.getByName("192.0.2.4"), 12345));
        }
    }

    @Test(timeOut = 15000)
    public void maximumBinaryHeaderCanArriveInFragmentsWithAnLdapRequest() throws Exception {
        CountDownLatch requestSeen = new CountDownLatch(1);
        Options options = Options.defaultOptions().set(LDAPListener.PROXY_PROTOCOL_ENABLED, true);
        try (LDAPListener listener = new LDAPListener(0, ctx -> (c, request) -> {
            requestSeen.countDown();
            return emptyStream();
        }, options); Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.firstSocketAddress().getPort())) {
            ByteBuffer frame = ByteBuffer.allocate(16 + 65535 + BIND.length);
            frame.put(new byte[] { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 });
            frame.put((byte) 0x21).put((byte) 0).putShort((short) 65535);
            frame.put((byte) 4).putShort((short) 65532).put(new byte[65532]).put(BIND);
            byte[] bytes = frame.array();
            for (int i = 0; i < bytes.length; i += 128) {
                socket.getOutputStream().write(bytes, i, Math.min(128, bytes.length - i));
                socket.getOutputStream().flush();
            }
            assertTrue(requestSeen.await(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeOut = 15000)
    public void invalidBinaryPrefixIsRejectedWithoutWaitingForClaimedPayload() throws Exception {
        Options options = Options.defaultOptions().set(LDAPListener.PROXY_PROTOCOL_ENABLED, true);
        byte[][] invalid = { { 0x31, 0x11, (byte) 0xff, (byte) 0xff },
                { 0x22, 0x11, (byte) 0xff, (byte) 0xff },
                { 0x21, 0x41, (byte) 0xff, (byte) 0xff }, { 0x21, 0x11, 0, 0 } };
        try (LDAPListener listener = new LDAPListener(0, ctx -> (c, request) -> emptyStream(), options)) {
            for (byte[] prefix : invalid) {
                try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.firstSocketAddress().getPort())) {
                    socket.setSoTimeout(5000);
                    socket.getOutputStream().write(new byte[] { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 });
                    socket.getOutputStream().write(prefix);
                    socket.getOutputStream().flush();
                    assertEquals(socket.getInputStream().read(), -1, "invalid binary prefix was not rejected");
                }
            }
        }
    }

    @Test(timeOut = 15000)
    public void disconnectDuringHeaderCallbackPreventsDeliveryOfCoalescedRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        Options options = Options.defaultOptions().set(LDAPListener.PROXY_PROTOCOL_ENABLED, true);
        try (LDAPListener listener = new LDAPListener(0, ctx -> {
            ctx.addListener(new LDAPClientContextEventListener() {
                @Override public void handleProxyProtocolHeader(LDAPClientContext c, ProxyProtocolHeader h) {
                    c.disconnect();
                }
                @Override public void handleConnectionError(LDAPClientContext c, Throwable error) { }
                @Override public void handleConnectionClosed(LDAPClientContext c, UnbindRequest r) { }
                @Override public void handleConnectionDisconnected(LDAPClientContext c, ResultCode r, String m) { }
            });
            return (c, request) -> {
                requests.incrementAndGet();
                return emptyStream();
            };
        }, options); Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.firstSocketAddress().getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write("PROXY UNKNOWN\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(BIND);
            socket.getOutputStream().flush();
            assertEquals(socket.getInputStream().read(), -1);
            assertEquals(requests.get(), 0);
        }
    }

    private void assertProxyTls(String cipher, boolean ssl, int expectedStrength) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<LDAPClientContext> context = new AtomicReference<>();
        Options options = Options.defaultOptions().set(LDAPListener.PROXY_PROTOCOL_ENABLED, true);
        try (LDAPListener listener = new LDAPListener(0, ctx -> (c, request) -> {
            context.set(c);
            received.countDown();
            return emptyStream();
        }, options); Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.firstSocketAddress().getPort())) {
            byte[] name = cipher == null ? null : cipher.getBytes(StandardCharsets.US_ASCII);
            int extra = name == null ? 0 : 3 + name.length;
            ByteBuffer bytes = ByteBuffer.allocate(16 + 12 + 3 + 5 + extra + BIND.length);
            bytes.put(new byte[] { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 });
            bytes.put((byte) 0x21).put((byte) 0x11).putShort((short) (12 + 3 + 5 + extra));
            bytes.put(new byte[] { (byte) 192, 0, 2, 4, (byte) 198, 51, 100, 2, 0x30, 0x39, 1, (byte) 0x85 });
            bytes.put((byte) 0x20).putShort((short) (5 + extra)).put((byte) (ssl ? 1 : 0)).putInt(1);
            if (name != null) {
                bytes.put((byte) 0x23).putShort((short) name.length).put(name);
            }
            bytes.put(BIND);
            socket.getOutputStream().write(bytes.array());
            socket.getOutputStream().flush();
            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertEquals(context.get().isProxyClientSecure(), ssl);
            assertEquals(context.get().getSecurityStrengthFactor(), expectedStrength);
            assertNull(context.get().getSSLSession());
        }
    }
}
