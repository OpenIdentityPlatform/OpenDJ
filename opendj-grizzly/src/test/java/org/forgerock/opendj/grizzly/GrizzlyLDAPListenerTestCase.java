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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2017-2026 3A Systems, LLC.
 */
package org.forgerock.opendj.grizzly;

import static java.util.Arrays.asList;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.LDAPListener.*;
import static org.forgerock.opendj.ldap.Connections.*;
import static org.forgerock.opendj.ldap.LDAPListener.REQUEST_MAX_SIZE_IN_BYTES;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.Mockito.mock;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ProviderNotFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.Options;
import org.forgerock.util.promise.PromiseImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.reactive.ServerConnectionFactoryAdapter;

/** Tests the LDAPListener class. */
@SuppressWarnings("javadoc")
public class GrizzlyLDAPListenerTestCase extends SdkTestCase {

    private static class MockServerConnection implements ServerConnection<Integer> {
        final PromiseImpl<Throwable, LdapException> connectionError = PromiseImpl.create();
        final PromiseImpl<LDAPClientContext, LdapException> context = PromiseImpl.create();
        final CountDownLatch isClosed = new CountDownLatch(1);

        MockServerConnection() {
            // Do nothing.
        }

        @Override
        public void handleAbandon(final Integer requestContext, final AbandonRequest request)
                throws UnsupportedOperationException {
            // Do nothing.
        }

        @Override
        public void handleAdd(final Integer requestContext, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        @Override
        public void handleBind(final Integer requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<BindResult> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
        }

        @Override
        public void handleCompare(final Integer requestContext, final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<CompareResult> resultHandler)
                throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newCompareResult(ResultCode.SUCCESS));
        }

        @Override
        public void handleConnectionClosed(final Integer requestContext, final UnbindRequest request) {
            isClosed.countDown();
        }

        @Override
        public void handleConnectionDisconnected(final ResultCode resultCode, final String message) {
            // Do nothing.
        }

        @Override
        public void handleConnectionError(final Throwable error) {
            connectionError.handleResult(error);
        }

        @Override
        public void handleDelete(final Integer requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<R> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleException(newLdapException(request
                    .getResultDecoder().newExtendedErrorResult(ResultCode.PROTOCOL_ERROR, "",
                            "Extended operation " + request.getOID() + " not supported")));
        }

        @Override
        public void handleModify(final Integer requestContext, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        @Override
        public void handleModifyDN(final Integer requestContext, final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        @Override
        public void handleSearch(final Integer requestContext, final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler,
            final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }
    }

    private static class MockServerConnectionFactory implements
            ServerConnectionFactory<LDAPClientContext, Integer> {
        private final MockServerConnection serverConnection;

        private MockServerConnectionFactory(final MockServerConnection serverConnection) {
            this.serverConnection = serverConnection;
        }

        @Override
        public ServerConnection<Integer> handleAccept(final LDAPClientContext clientContext) throws LdapException {
            serverConnection.context.handleResult(clientContext);
            return serverConnection;
        }
    }

    /**
     * Binds a server socket on a free loopback port and keeps it bound so that the port cannot be
     * handed out again to a listener which is created later. The socket must be closed before
     * connection attempts which expect a connection failure are performed against its address.
     */
    private static ServerSocket reserveSocketAddress() throws IOException {
        final ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(loopbackWithDynamicPort());
        return socket;
    }

    /** How many free ports {@link #connectToOfflineServer()} tries before it gives up. */
    private static final int OFFLINE_CONNECT_ATTEMPTS = 3;

    /**
     * Fails over the way a proxy does: connects to a free port, which must refuse the connection,
     * then to the online server.
     */
    private static void failOverToOnlineServer(final InetSocketAddress onlineAddress) throws LdapException {
        connectToOfflineServer();
        final LDAPConnectionFactory onlineFactory =
                new LDAPConnectionFactory(onlineAddress.getHostString(), onlineAddress.getPort());
        try {
            onlineFactory.getConnection().close();
        } catch (final Exception e) {
            throw newLdapException(ResultCode.OTHER,
                    "Unexpected exception when connecting to online server", e);
        } finally {
            onlineFactory.close();
        }
    }

    /**
     * Connects to a free port, which must refuse the connection.
     * <p>
     * A free port can still accept it: when the client socket gets that same port as its own
     * ephemeral port, it connects to itself (TCP simultaneous open). Linux and Windows allow such
     * a connection, macOS fails it. The connection is then closed and another free port is tried,
     * a bounded number of times.
     */
    private static void connectToOfflineServer() throws LdapException {
        final List<Integer> acceptingPorts = new ArrayList<>();
        do {
            // The numeric host avoids a forward lookup of "localhost".
            final InetSocketAddress offlineAddress = findFreeSocketAddress();
            final LDAPConnectionFactory offlineFactory =
                    new LDAPConnectionFactory(offlineAddress.getAddress().getHostAddress(), offlineAddress.getPort());
            try {
                offlineFactory.getConnection().close();
                acceptingPorts.add(offlineAddress.getPort());
            } catch (final ConnectionException expected) {
                return;
            } catch (final Exception e) {
                throw newLdapException(ResultCode.OTHER,
                        "Unexpected exception when connecting to offline server", e);
            } finally {
                offlineFactory.close();
            }
        } while (acceptingPorts.size() < OFFLINE_CONNECT_ATTEMPTS);
        throw newLdapException(ResultCode.OTHER,
                "Connection to offline server succeeded unexpectedly on ports " + acceptingPorts);
    }

    /** Disables logging before the tests. */
    @BeforeClass
    public void disableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.SEVERE);
    }

    /** Re-enable logging after the tests. */
    @AfterClass
    public void enableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.INFO);
    }

    /** Test creation of LDAP listener with default transport provider. */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateLDAPListener() throws Exception {
        // test no exception is thrown, which means transport provider is
        // correctly loaded
        LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                        mock(ServerConnectionFactory.class)));
        listener.close();
    }

    /** Test creation of LDAP listener with default transport provider and custom class loader. */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateLDAPListenerWithCustomClassLoader() throws Exception {
        // test no exception is thrown, which means transport provider is correctly loaded
        Options options = defaultOptions().set(TRANSPORT_PROVIDER_CLASS_LOADER,
                                                       Thread.currentThread().getContextClassLoader());
        LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS),
                        mock(ServerConnectionFactory.class)),
                options);
        listener.close();
    }

    /** Test creation of LDAP listener with unknown transport provider. */
    @SuppressWarnings({ "unchecked" })
    @Test(expectedExceptions = ProviderNotFoundException.class,
        expectedExceptionsMessageRegExp = "^The requested provider 'unknown' .*")
    public void testCreateLDAPListenerFailureProviderNotFound() throws Exception {
        Options options = defaultOptions().set(TRANSPORT_PROVIDER, "unknown");
        LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS),
                        mock(ServerConnectionFactory.class)),
                options);
        listener.close();
    }

    /**
     * Tests basic LDAP listener functionality.
     *
     * @throws Exception
     *             If an unexpected exception occurred.
     */
    @Test(timeOut = 10000)
    public void testLDAPListenerBasic() throws Exception {
        final MockServerConnection serverConnection = new MockServerConnection();
        final MockServerConnectionFactory serverConnectionFactory =
                new MockServerConnectionFactory(serverConnection);
        final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                        serverConnectionFactory));
        final InetSocketAddress addr = listener.firstSocketAddress();
        try {
            // Connect and close.
            final Connection connection =
                    new LDAPConnectionFactory(addr.getHostName(), addr.getPort()).getConnection();
            assertThat(serverConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
            assertThat(serverConnection.isClosed.getCount()).isEqualTo(1);
            connection.close();
            assertThat(serverConnection.isClosed.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            listener.close();
        }
    }

    /**
     * A listener given a transport with {@link GrizzlyLDAPListener#GRIZZLY_TRANSPORT} serves its connections with that
     * transport, and leaves it running when it is closed.
     */
    @Test(timeOut = 10000)
    public void testLDAPListenerWithProvidedTransport() throws Exception {
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.start();
        try {
            final MockServerConnection serverConnection = new MockServerConnection();
            final Options options = defaultOptions().set(GrizzlyLDAPListener.GRIZZLY_TRANSPORT, transport);
            final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                    new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS),
                            new MockServerConnectionFactory(serverConnection)),
                    options);
            try {
                final InetSocketAddress addr = listener.firstSocketAddress();
                final Connection connection =
                        new LDAPConnectionFactory(addr.getHostName(), addr.getPort()).getConnection();
                try {
                    final LDAPClientContext context = serverConnection.context.get(10, TimeUnit.SECONDS);
                    final Field field = context.getClass().getDeclaredField("connection");
                    field.setAccessible(true);
                    assertThat(((org.glassfish.grizzly.Connection<?>) field.get(context)).getTransport())
                            .isSameAs(transport);
                } finally {
                    connection.close();
                }
            } finally {
                listener.close();
            }
            assertThat(transport.isStopped()).isFalse();
        } finally {
            transport.shutdownNow();
        }
    }

    @DataProvider
    public Object[][] tlsProtocols() {
        return new Object[][] { { "TLSv1.2", false, 180 }, { "TLSv1.3", false, 180 },
                { "TLSv1.2", true, 180 }, { "TLSv1.3", true, 180 },
                { "TLSv1.2", false, 600 }, { "TLSv1.3", true, 600 } };
    }

    /** A TLS flight larger than the configured write buffer must reach the client before it waits for a reply. */
    @Test(dataProvider = "tlsProtocols", timeOut = 20000)
    public void testLargeCertificateHandshakeWithSmallWriteBuffer(final String protocol, final boolean startTls,
            final int nameCount) throws Exception {
        final SSLContext serverSSLContext = largeCertificateContext(nameCount);
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        try {
            final MockServerConnection serverConnection = new MockServerConnection() {
                @Override
                public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                        final ExtendedRequest<R> request, final IntermediateResponseHandler intermediateResponseHandler,
                        final LdapResultHandler<R> resultHandler) {
                    try {
                        enableServerTLS(context.get(10, TimeUnit.SECONDS), serverSSLContext, protocol, true);
                        resultHandler.handleResult(request.getResultDecoder().newExtendedErrorResult(
                                ResultCode.SUCCESS, "", ""));
                    } catch (Exception e) {
                        resultHandler.handleException(newLdapException(ResultCode.OTHER, e.getMessage(), e));
                    }
                }
            };
            final MockServerConnectionFactory serverFactory = new MockServerConnectionFactory(serverConnection) {
                @Override
                public ServerConnection<Integer> handleAccept(final LDAPClientContext context) throws LdapException {
                    if (!startTls) {
                        enableServerTLS(context, serverSSLContext, protocol, false);
                    }
                    return super.handleAccept(context);
                }
            };
            final Options options = defaultOptions().set(GrizzlyLDAPListener.GRIZZLY_TRANSPORT, transport);
            final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                    new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS), serverFactory), options);
            try {
                final InetSocketAddress address = listener.firstSocketAddress();
                final SSLContext clientContext = new SSLContextBuilder().setTrustManager(TrustManagers.trustAll())
                        .getSSLContext();
                try (Socket plain = new Socket(address.getAddress(), address.getPort())) {
                    plain.setSoTimeout(7000);
                    if (startTls) {
                        startTLS(plain, (byte) 1);
                    }
                    try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket(
                            plain, "localhost", address.getPort(), false)) {
                        socket.setEnabledProtocols(new String[] { protocol });
                        socket.setSoTimeout(7000);
                        socket.startHandshake();
                        bindAnonymously(socket);
                    }
                }
            } finally {
                listener.close();
            }
        } finally {
            transport.shutdownNow();
        }
    }

    private static boolean enableServerTLS(LDAPClientContext context, SSLContext sslContext, String protocol,
            boolean startTls) {
        final SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledProtocols(new String[] { protocol });
        return context.enableTLS(engine, startTls);
    }

    /** The asynchronous handshake must not leave a client which sends only part of its ClientHello connected. */
    @Test(timeOut = 20000)
    public void testIncompleteHandshakeIsClosedAfterTheConfiguredTimeout() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "1200");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            final long connected = System.nanoTime();
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                serverConnection.context.get(5, TimeUnit.SECONDS);
                // A complete TLS record containing an incomplete ClientHello.
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x04, 0x01, 0x00, 0x01, 0x00 });
                socket.setSoTimeout(5000);
                org.testng.Assert.assertEquals(socket.getInputStream().read(), -1, "incomplete handshake stayed open");
                org.testng.Assert.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connected) >= 800,
                        "the handshake was closed before its timeout");
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    /** The timeout must not start before the first complete TLS record is received. */
    @Test(timeOut = 20000)
    public void testDelayedClientHelloCanCompleteHandshake() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "1200");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            try (Socket plain = new Socket(address.getAddress(), address.getPort())) {
                serverConnection.context.get(5, TimeUnit.SECONDS);
                Thread.sleep(2000);
                final SSLContext client = new SSLContextBuilder().setTrustManager(TrustManagers.trustAll())
                        .getSSLContext();
                try (SSLSocket socket = (SSLSocket) client.getSocketFactory().createSocket(
                        plain, address.getHostString(), address.getPort(), true)) {
                    socket.setSoTimeout(5000);
                    socket.startHandshake();
                    bindAnonymously(socket);
                }
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    /** A partially received first record does not start Grizzly's handshake read timeout. */
    @Test(timeOut = 20000)
    public void testPartialFirstRecordDoesNotStartHandshakeTimeout() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "1200");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                serverConnection.context.get(5, TimeUnit.SECONDS);
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x04, 0x01 });
                Thread.sleep(2000);
                socket.getOutputStream().write(new byte[] { 0x00, 0x01, 0x00 });
                socket.setSoTimeout(5000);
                final long lastWrite = System.nanoTime();
                org.testng.Assert.assertEquals(socket.getInputStream().read(), -1, "idle handshake stayed open");
                org.testng.Assert.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastWrite) >= 800,
                        "the timeout started before the first complete TLS record");
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    /** Progress on a fragmented handshake renews the timeout for the next read. */
    @Test(timeOut = 20000)
    public void testHandshakeTimeoutIsRenewedByIncomingRecords() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "2000");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                serverConnection.context.get(5, TimeUnit.SECONDS);
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x04, 0x01, 0x00, 0x01, 0x00 });
                Thread.sleep(750);
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x01, 0x03 });
                Thread.sleep(750);
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x01, 0x03 });
                socket.setSoTimeout(1000);
                try {
                    socket.getInputStream().read();
                    fail("the progressing handshake was closed prematurely");
                } catch (SocketTimeoutException expected) {
                    // The total elapsed time exceeds the timeout, but the last read was recent.
                }
                socket.setSoTimeout(5000);
                org.testng.Assert.assertEquals(socket.getInputStream().read(), -1, "idle handshake stayed open");
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    @DataProvider
    public Object[][] disabledHandshakeTimeouts() {
        return new Object[][] { { "0" }, { "-1" } };
    }

    /** Grizzly's zero and negative timeout values leave a pending handshake without a deadline. */
    @Test(dataProvider = "disabledHandshakeTimeouts", timeOut = 20000)
    public void testDisabledHandshakeTimeoutKeepsAnIncompleteHandshake(final String timeout) throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, timeout);
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                serverConnection.context.get(5, TimeUnit.SECONDS);
                // A complete record with an incomplete ClientHello reaches the deadline code.
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x04, 0x01, 0x00, 0x01, 0x00 });
                socket.setSoTimeout(1500);
                try {
                    socket.getInputStream().read();
                    fail("the incomplete handshake closed with timeout " + timeout);
                } catch (SocketTimeoutException expected) {
                    // The connection is still open.
                }
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    @DataProvider
    public Object[][] completedHandshakeProtocols() {
        return new Object[][] { { "TLSv1.2" }, { "TLSv1.3" } };
    }

    /** Completing the handshake cancels its timeout, so it cannot close a subsequently active LDAP connection. */
    @Test(dataProvider = "completedHandshakeProtocols", timeOut = 30000)
    public void testCompletedHandshakeIsNotClosedByItsFormerTimeout(final String protocol) throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection, protocol);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "5000");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            final SSLContext clientContext = new SSLContextBuilder().setTrustManager(TrustManagers.trustAll())
                    .getSSLContext();
            try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket(
                    address.getAddress(), address.getPort())) {
                socket.setSoTimeout(8000);
                socket.startHandshake();
                socket.setSoTimeout(6000);
                try {
                    socket.getInputStream().read();
                    fail("the completed connection was closed by the handshake timeout");
                } catch (SocketTimeoutException expected) {
                    // The connection remained open past the deadline without any LDAP traffic.
                }
                bindAnonymously(socket);
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    /** A TLS upgrade must not be accepted after the client connection has closed. */
    @Test(timeOut = 20000)
    public void testEnableTlsRefusesAClosedConnection() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final Options options = defaultOptions().set(GrizzlyLDAPListener.GRIZZLY_TRANSPORT, transport);
        final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS),
                        new MockServerConnectionFactory(serverConnection)), options);
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            final LDAPClientContext clientContext;
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                clientContext = serverConnection.context.get(5, TimeUnit.SECONDS);
            }
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!clientContext.isClosed() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            org.testng.Assert.assertTrue(clientContext.isClosed(), "the server did not see the client close");
            org.testng.Assert.assertFalse(enableServerTLS(clientContext, serverContext, "TLSv1.2", true),
                    "enableTLS accepted a closed connection");
        } finally {
            listener.close();
            transport.shutdownNow();
        }
    }

    /** A stalled TLS 1.2 renegotiation must retain the configured Grizzly read timeout. */
    @Test(timeOut = 30000)
    public void testIncompleteRenegotiationTimesOut() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection();
        final LDAPListener listener = secureListener(transport, serverContext, serverConnection);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "1200");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            final SSLContext clientContext = new SSLContextBuilder().setTrustManager(TrustManagers.trustAll())
                    .getSSLContext();
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                socket.setSoTimeout(5000);
                final SSLEngine engine = clientContext.createSSLEngine("localhost", address.getPort());
                engine.setUseClientMode(true);
                engine.setEnabledProtocols(new String[] { "TLSv1.2" });
                finishClientHandshake(engine, socket);

                engine.beginHandshake();
                final ByteBuffer hello = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
                final SSLEngineResult result = engine.wrap(ByteBuffer.allocate(0), hello);
                org.testng.Assert.assertEquals(result.getStatus(), SSLEngineResult.Status.OK);
                org.testng.Assert.assertTrue(hello.position() > 0, "no renegotiation ClientHello was produced");
                final long started = System.nanoTime();
                socket.getOutputStream().write(hello.array(), 0, hello.position());
                // Drain the server's flight, but do not send the rest of the client's handshake.
                int received = 0;
                while (socket.getInputStream().read() != -1) {
                    received++;
                }
                org.testng.Assert.assertTrue(received > 0, "the server did not start renegotiation");
                org.testng.Assert.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 800,
                        "the server closed before the renegotiation timeout");
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    private static void finishClientHandshake(SSLEngine engine, Socket socket) throws Exception {
        final ByteBuffer input = ByteBuffer.allocate(65536);
        final ByteBuffer output = ByteBuffer.allocate(65536);
        final ByteBuffer application = ByteBuffer.allocate(65536);
        final ByteBuffer empty = ByteBuffer.allocate(0);
        final DataInputStream in = new DataInputStream(socket.getInputStream());
        engine.beginHandshake();
        while (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (engine.getHandshakeStatus()) {
            case NEED_TASK:
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
                break;
            case NEED_WRAP:
                output.clear();
                final SSLEngineResult wrapped = engine.wrap(empty, output);
                org.testng.Assert.assertEquals(wrapped.getStatus(), SSLEngineResult.Status.OK);
                socket.getOutputStream().write(output.array(), 0, output.position());
                break;
            case NEED_UNWRAP:
                final byte[] header = new byte[5];
                in.readFully(header);
                final int length = (header[3] & 0xff) * 256 + (header[4] & 0xff);
                input.put(header);
                in.readFully(input.array(), input.position(), length);
                input.position(input.position() + length);
                input.flip();
                final SSLEngineResult unwrapped = engine.unwrap(input, application);
                input.compact();
                org.testng.Assert.assertTrue(unwrapped.getStatus() == SSLEngineResult.Status.OK
                        || unwrapped.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW);
                break;
            default:
                throw new AssertionError("Unexpected handshake status: " + engine.getHandshakeStatus());
            }
        }
    }

    /** StartTLS after an LDAP request still needs a timeout once the TLS handshake has started. */
    @Test(timeOut = 20000)
    public void testStartTlsHandshakeTimesOutAfterAnEarlierLdapRequest() throws Exception {
        final SSLContext serverContext = largeCertificateContext();
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(SameThreadIOStrategy.getInstance()).setWriteBufferSize(4096).build();
        transport.start();
        final MockServerConnection serverConnection = new MockServerConnection() {
            @Override
            public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                    final ExtendedRequest<R> request, final IntermediateResponseHandler intermediateResponseHandler,
                    final LdapResultHandler<R> resultHandler) {
                try {
                    final LDAPClientContext clientContext = context.get(5, TimeUnit.SECONDS);
                    org.testng.Assert.assertTrue(enableServerTLS(clientContext, serverContext, "TLSv1.2", true));
                    org.testng.Assert.assertFalse(enableServerTLS(clientContext, serverContext, "TLSv1.2", true),
                            "a second StartTLS was accepted while the first was pending");
                    resultHandler.handleResult(request.getResultDecoder().newExtendedErrorResult(
                            ResultCode.SUCCESS, "", ""));
                } catch (Exception e) {
                    resultHandler.handleException(newLdapException(ResultCode.OTHER, e.getMessage(), e));
                }
            }
        };
        final Options options = defaultOptions().set(GrizzlyLDAPListener.GRIZZLY_TRANSPORT, transport);
        final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS),
                        new MockServerConnectionFactory(serverConnection)), options);
        final String property = "org.forgerock.opendj.grizzly.handshakeTimeout";
        final String previous = System.getProperty(property);
        System.setProperty(property, "1200");
        try {
            final InetSocketAddress address = listener.firstSocketAddress();
            try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
                socket.setSoTimeout(5000);
                bindAnonymously(socket);
                startTLS(socket, (byte) 2);
                socket.getOutputStream().write(new byte[] { 0x16, 0x03, 0x03, 0x00, 0x04, 0x01, 0x00, 0x01, 0x00 });
                org.testng.Assert.assertEquals(socket.getInputStream().read(), -1, "StartTLS handshake stayed open");
            }
        } finally {
            restoreProperty(property, previous);
            listener.close();
            transport.shutdownNow();
        }
    }

    private static void bindAnonymously(Socket socket) throws IOException {
        socket.getOutputStream().write(new byte[] {
                0x30, 0x0c, 0x02, 0x01, 0x01, 0x60, 0x07, 0x02, 0x01, 0x03, 0x04, 0x00, (byte) 0x80, 0x00 });
        final byte[] response = new byte[14];
        new DataInputStream(socket.getInputStream()).readFully(response);
        org.testng.Assert.assertEquals(response, new byte[] {
                0x30, 0x0c, 0x02, 0x01, 0x01, 0x61, 0x07, 0x0a, 0x01, 0x00, 0x04, 0x00, 0x04, 0x00 });
    }

    private static void startTLS(Socket socket, byte messageId) throws IOException {
        socket.getOutputStream().write(new byte[] {
                0x30, 0x1d, 0x02, 0x01, messageId, 0x77, 0x18, (byte) 0x80, 0x16 });
        socket.getOutputStream().write("1.3.6.1.4.1.1466.20037".getBytes(StandardCharsets.US_ASCII));
        final byte[] response = new byte[14];
        new DataInputStream(socket.getInputStream()).readFully(response);
        org.testng.Assert.assertEquals(response, new byte[] {
                0x30, 0x0c, 0x02, 0x01, messageId, 0x78, 0x07, 0x0a, 0x01, 0x00, 0x04, 0x00, 0x04, 0x00 });
    }

    private static LDAPListener secureListener(TCPNIOTransport transport, SSLContext serverContext,
            MockServerConnection serverConnection) throws IOException {
        return secureListener(transport, serverContext, serverConnection, "TLSv1.2");
    }

    private static LDAPListener secureListener(TCPNIOTransport transport, SSLContext serverContext,
            MockServerConnection serverConnection, String protocol) throws IOException {
        final MockServerConnectionFactory factory = new MockServerConnectionFactory(serverConnection) {
            @Override
            public ServerConnection<Integer> handleAccept(final LDAPClientContext context) throws LdapException {
                enableServerTLS(context, serverContext, protocol, false);
                return super.handleAccept(context);
            }
        };
        final Options options = defaultOptions().set(GrizzlyLDAPListener.GRIZZLY_TRANSPORT, transport);
        return new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS), factory), options);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static SSLContext largeCertificateContext() throws Exception {
        return largeCertificateContext(180);
    }

    private static SSLContext largeCertificateContext(int nameCount) throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final KeyPair pair = generator.generateKeyPair();
        final X500Principal subject = new X500Principal("CN=localhost");
        final Instant now = Instant.now();
        final JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)), subject,
                pair.getPublic());
        final GeneralName[] names = new GeneralName[nameCount];
        for (int i = 0; i < names.length; i++) {
            names[i] = new GeneralName(GeneralName.dNSName, String.format("host-%03d.diagnostic-1287.example.test", i));
        }
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
        final X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        org.testng.Assert.assertTrue(certificate.getEncoded().length > 6144, "certificate is too small for this test");
        if (nameCount >= 600) {
            org.testng.Assert.assertTrue(certificate.getEncoded().length > 16384,
                    "certificate does not exceed one TLS record");
        }
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        final char[] password = "test-password".toCharArray();
        keyStore.setKeyEntry("server", pair.getPrivate(), password, new X509Certificate[] { certificate });
        final KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, password);
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(factory.getKeyManagers(), null, null);
        return context;
    }

    /**
     * Tests LDAP listener which attempts to open a connection to a remote
     * offline server at the point when the listener accepts the client
     * connection.
     *
     * @throws Exception
     *             If an unexpected exception occurred.
     */
    @Test
    public void testLDAPListenerLoadBalanceDuringHandleAccept() throws Exception {
        // Online server listener.
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                        onlineServerConnectionFactory));
        final InetSocketAddress onlineAddr = onlineServerListener.firstSocketAddress();

        // Reserve the offline server ports, keeping them bound until the proxy listener is
        // created: a port released too early could be handed out to the proxy listener itself,
        // making the load balancer connect back to the proxy instead of the online server.
        final ServerSocket offlineServerSocket1 = reserveSocketAddress();
        final ServerSocket offlineServerSocket2 = reserveSocketAddress();
        try {
            // Connection pool and load balancing tests.
            InetSocketAddress offlineAddress1 = (InetSocketAddress) offlineServerSocket1.getLocalSocketAddress();
            final ConnectionFactory offlineServer1 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            offlineAddress1.getHostName(),
                            offlineAddress1.getPort()), "offline1");
            InetSocketAddress offlineAddress2 = (InetSocketAddress) offlineServerSocket2.getLocalSocketAddress();
            final ConnectionFactory offlineServer2 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            offlineAddress2.getHostName(),
                            offlineAddress2.getPort()), "offline2");
            final ConnectionFactory onlineServer =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            onlineAddr.getHostName(),
                            onlineAddr.getPort()), "online");

            // Round robin.
            final ConnectionFactory loadBalancer =
                    newRoundRobinLoadBalancer(asList(newFixedConnectionPool(offlineServer1, 10),
                                                     newFixedConnectionPool(offlineServer2, 10),
                                                     newFixedConnectionPool(onlineServer, 10)),
                                              defaultOptions());

            final MockServerConnection proxyServerConnection = new MockServerConnection();
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection) {

                        @Override
                        public ServerConnection<Integer> handleAccept(
                                final LDAPClientContext clientContext) throws LdapException {
                            // Get connection from load balancer, this should
                            // fail over twice before getting connection to
                            // online server.
                            loadBalancer.getConnection().close();
                            return super.handleAccept(clientContext);
                        }

                    };

            final LDAPListener proxyListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                    new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                            proxyServerConnectionFactory));
            final InetSocketAddress proxyAddr = proxyListener.firstSocketAddress();
            // Release the reserved ports now that every listener is bound: connection attempts
            // to the offline addresses must fail with a connection error.
            offlineServerSocket1.close();
            offlineServerSocket2.close();
            final LDAPConnectionFactory proxyClientFactory =
                    new LDAPConnectionFactory(proxyAddr.getHostName(), proxyAddr.getPort());
            try {
                // Connect and close.
                final Connection connection = proxyClientFactory.getConnection();

                assertThat(proxyServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(onlineServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();

                // Wait for connect/close to complete.
                connection.close();

                proxyServerConnection.isClosed.await();
            } finally {
                proxyClientFactory.close();
                loadBalancer.close();
                proxyListener.close();
            }
        } finally {
            offlineServerSocket1.close();
            offlineServerSocket2.close();
            onlineServerListener.close();
        }
    }

    /**
     * Tests LDAP listener which attempts to open a connection to a load
     * balancing pool at the point when the listener handles a bind request.
     *
     * @throws Exception
     *             If an unexpected exception occurred.
     */
    @Test // (timeOut = 10000)
    public void testLDAPListenerLoadBalanceDuringHandleBind() throws Exception {
        // Online server listener.
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                        onlineServerConnectionFactory));
        final InetSocketAddress onlineServerAddr = onlineServerListener.firstSocketAddress();

        // Reserve the offline server ports, keeping them bound until the proxy listener is
        // created: a port released too early could be handed out to the proxy listener itself,
        // making the load balancer connect back to the proxy instead of the online server.
        final ServerSocket offlineServerSocket1 = reserveSocketAddress();
        final ServerSocket offlineServerSocket2 = reserveSocketAddress();
        try {
            // Connection pool and load balancing tests.
            InetSocketAddress offlineAddress1 = (InetSocketAddress) offlineServerSocket1.getLocalSocketAddress();
            final ConnectionFactory offlineServer1 =
                    Connections.newNamedConnectionFactory(
                            new LDAPConnectionFactory(offlineAddress1.getHostName(),
                                    offlineAddress1.getPort()), "offline1");
            InetSocketAddress offlineAddress2 = (InetSocketAddress) offlineServerSocket2.getLocalSocketAddress();
            final ConnectionFactory offlineServer2 =
                    Connections.newNamedConnectionFactory(
                            new LDAPConnectionFactory(offlineAddress2.getHostName(),
                                    offlineAddress2.getPort()), "offline2");
            final ConnectionFactory onlineServer =
                    Connections.newNamedConnectionFactory(
                            new LDAPConnectionFactory(onlineServerAddr.getHostName(),
                                                      onlineServerAddr.getPort()), "online");

            // Round robin.
            final ConnectionFactory loadBalancer =
                    newRoundRobinLoadBalancer(asList(newFixedConnectionPool(offlineServer1, 10),
                                                     newFixedConnectionPool(offlineServer2, 10),
                                                     newFixedConnectionPool(onlineServer, 10)),
                                              defaultOptions());

            final MockServerConnection proxyServerConnection = new MockServerConnection() {

                @Override
                public void handleBind(final Integer requestContext, final int version,
                        final BindRequest request,
                        final IntermediateResponseHandler intermediateResponseHandler,
                        final LdapResultHandler<BindResult> resultHandler)
                        throws UnsupportedOperationException {
                    // Get connection from load balancer, this should fail over
                    // twice before getting connection to online server.
                    try {
                        loadBalancer.getConnection().close();
                        resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
                    } catch (final Exception e) {
                        // Unexpected.
                        resultHandler.handleException(newLdapException(ResultCode.OTHER,
                                "Unexpected exception when connecting to load balancer", e));
                    }
                }

            };
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection);

            final LDAPListener proxyListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                    new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                            proxyServerConnectionFactory));
            final InetSocketAddress proxyAddr = (InetSocketAddress) proxyListener.firstSocketAddress();

            // Release the reserved ports now that every listener is bound: connection attempts
            // to the offline addresses must fail with a connection error.
            offlineServerSocket1.close();
            offlineServerSocket2.close();
            final LDAPConnectionFactory proxyClientFactory =
                    new LDAPConnectionFactory(proxyAddr.getHostName(), proxyAddr.getPort());
            try {
                // Connect, bind, and close.
                final Connection connection = proxyClientFactory.getConnection();
                try {
                    connection.bind("cn=test", "password".toCharArray());

                    assertThat(proxyServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
                    assertThat(onlineServerConnection.context.get(10, TimeUnit.SECONDS))
                            .isNotNull();
                } finally {
                    connection.close();
                }

                // Wait for connect/close to complete.
                proxyServerConnection.isClosed.await();
            } finally {
                proxyClientFactory.close();
                loadBalancer.close();
                proxyListener.close();
            }
        } finally {
            offlineServerSocket1.close();
            offlineServerSocket2.close();
            onlineServerListener.close();
        }
    }

    /**
     * Tests LDAP listener which attempts to open a connection to a remote
     * offline server at the point when the listener accepts the client
     * connection.
     *
     * @throws Exception
     *             If an unexpected exception occurred.
     */
    @Test(timeOut = 60000)
    public void testLDAPListenerProxyDuringHandleAccept() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                        onlineServerConnectionFactory));
        final InetSocketAddress onlineServerAddr = onlineServerListener.firstSocketAddress();

        try {
            final MockServerConnection proxyServerConnection = new MockServerConnection();
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection) {

                        @Override
                        public ServerConnection<Integer> handleAccept(
                                final LDAPClientContext clientContext) throws LdapException {
                            try {
                                failOverToOnlineServer(onlineServerAddr);
                            } catch (final LdapException e) {
                                // The listener only closes the client connection when handleAccept
                                // fails: report the failure through the context the test waits for,
                                // instead of leaving that wait to time out without a cause.
                                proxyServerConnection.context.handleException(e);
                                throw e;
                            } catch (final RuntimeException e) {
                                proxyServerConnection.context.handleException(
                                        newLdapException(ResultCode.OTHER, e));
                                throw e;
                            }
                            return super.handleAccept(clientContext);
                        }

                    };

            final LDAPListener proxyListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                    new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                            proxyServerConnectionFactory));
            // Connect to the proxy listener (not to the online server
            // directly, otherwise the proxy handleAccept is never invoked)
            // and close.
            final InetSocketAddress proxyAddr = proxyListener.firstSocketAddress();
            final LDAPConnectionFactory proxyClientFactory =
                    new LDAPConnectionFactory(proxyAddr.getHostString(), proxyAddr.getPort());
            try {
                final Connection connection = proxyClientFactory.getConnection();
                try {
                    // handleAccept makes both fail-over connections before it resolves the context,
                    // and each of them may take up to the default connect timeout (10 seconds):
                    // wait longer than that, so that a failed attempt reports its own cause.
                    assertThat(proxyServerConnection.context.get(30, TimeUnit.SECONDS)).isNotNull();
                    assertThat(onlineServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
                } finally {
                    connection.close();
                }

                // Wait for connect/close to complete.
                assertThat(proxyServerConnection.isClosed.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                proxyClientFactory.close();
                proxyListener.close();
            }
        } finally {
            onlineServerListener.close();
        }
    }

    /**
     * Tests LDAP listener which attempts to open a connection to a remote
     * offline server at the point when the listener handles a bind request.
     *
     * @throws Exception
     *             If an unexpected exception occurred.
     */
    @Test(timeOut = 60000)
    public void testLDAPListenerProxyDuringHandleBind() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                        onlineServerConnectionFactory));
        final InetSocketAddress onlineServerAddr = onlineServerListener.firstSocketAddress();

        try {
            final MockServerConnection proxyServerConnection = new MockServerConnection() {

                @Override
                public void handleBind(final Integer requestContext, final int version,
                        final BindRequest request,
                        final IntermediateResponseHandler intermediateResponseHandler,
                        final LdapResultHandler<BindResult> resultHandler)
                        throws UnsupportedOperationException {
                    try {
                        failOverToOnlineServer(onlineServerAddr);
                        resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
                    } catch (final LdapException e) {
                        // A bind must fail with a bind result: the listener cannot encode any other
                        // result as a bind response, and would then never answer the bind. Only the
                        // diagnostic message reaches the client, so it carries the nested cause.
                        final Throwable cause = e.getCause();
                        final String message = cause != null
                                ? e.getResult().getDiagnosticMessage() + ": " + cause
                                : e.getResult().getDiagnosticMessage();
                        resultHandler.handleException(newLdapException(Responses.newBindResult(ResultCode.OTHER)
                                .setDiagnosticMessage(message).setCause(e)));
                    } catch (final RuntimeException e) {
                        resultHandler.handleException(newLdapException(Responses.newBindResult(ResultCode.OTHER)
                                .setDiagnosticMessage(e.toString()).setCause(e)));
                    }
                }

            };
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection);
            final LDAPListener proxyListener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                    new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS),
                            proxyServerConnectionFactory));
            final InetSocketAddress proxyAddr = proxyListener.firstSocketAddress();
            final LDAPConnectionFactory proxyClientFactory =
                    new LDAPConnectionFactory(proxyAddr.getHostString(), proxyAddr.getPort());
            try {
                // Connect, bind, and close.
                final Connection connection = proxyClientFactory.getConnection();
                try {
                    connection.bind("cn=test", "password".toCharArray());

                    assertThat(proxyServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
                    assertThat(onlineServerConnection.context.get(10, TimeUnit.SECONDS))
                            .isNotNull();
                } finally {
                    connection.close();
                }

                // Wait for connect/close to complete.
                proxyServerConnection.isClosed.await();
            } finally {
                proxyClientFactory.close();
                proxyListener.close();
            }
        } finally {
            onlineServerListener.close();
        }
    }

    /**
     * Tests that an incoming request which is too big triggers the connection
     * to be closed and an error notification to occur.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(expectedExceptions = LdapException.class)
    public void testMaxRequestSize() throws Exception {
        final MockServerConnection serverConnection = new MockServerConnection();
        final MockServerConnectionFactory factory =
                new MockServerConnectionFactory(serverConnection);
        final Options options = defaultOptions().set(REQUEST_MAX_SIZE_IN_BYTES, 2048);
        final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(options.get(LDAP_DECODE_OPTIONS), factory), options);
        final InetSocketAddress addr = listener.firstSocketAddress();

        Connection connection = null;
        try {
            connection = new LDAPConnectionFactory(addr.getHostName(), addr.getPort()).
                    getConnection();

            // Small request
            connection.bind("cn=test", "password".toCharArray());
            assertThat(serverConnection.context.get().isClosed()).isFalse();
            assertThat(serverConnection.connectionError.isDone()).isFalse();

            // Big but valid request.
            final char[] password1 = new char[2000];
            Arrays.fill(password1, 'a');
            connection.bind("cn=test", password1);
            assertThat(serverConnection.context.get().isClosed()).isFalse();
            assertThat(serverConnection.connectionError.isDone()).isFalse();

            // Big invalid request.
            final char[] password2 = new char[2048];
            Arrays.fill(password2, 'a');
            try {
                connection.bind("cn=test", password2);
                fail("Big bind unexpectedly succeeded");
            } catch (final LdapException e) {
                // Expected exception.
                assertThat(e.getResult().getResultCode()).isEqualTo(
                        ResultCode.CLIENT_SIDE_SERVER_DOWN);

                assertThat(serverConnection.connectionError.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(serverConnection.connectionError.get()).isInstanceOf(
                        DecodeException.class);
                assertThat(((DecodeException) serverConnection.connectionError.get()).isFatal())
                        .isTrue();
                assertThat(serverConnection.isClosed.getCount()).isEqualTo(1);
                assertThat(serverConnection.context.get().isClosed()).isTrue();
                throw e;
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
            listener.close();
        }
    }

    /**
     * Tests server-side disconnection.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testServerDisconnect() throws Exception {
        final MockServerConnection serverConnection = new MockServerConnection();
        final MockServerConnectionFactory factory = new MockServerConnectionFactory(serverConnection);
        final LDAPListener listener = new LDAPListener(Collections.singleton(loopbackWithDynamicPort()),
                new ServerConnectionFactoryAdapter(Options.defaultOptions().get(LDAP_DECODE_OPTIONS), factory));
        final InetSocketAddress listenerAddr = listener.firstSocketAddress();

        final Connection connection;
        try {
            // Connect and bind.
            connection = new LDAPConnectionFactory(listenerAddr.getHostName(), listenerAddr.getPort()).getConnection();
            try {
                connection.bind("cn=test", "password".toCharArray());
            } catch (final LdapException e) {
                connection.close();
                throw e;
            }
        } finally {
            serverConnection.context.get().disconnect();
            listener.close();
        }

        try {
            // Connect and bind.
            final Connection failedConnection =
                    new LDAPConnectionFactory(listenerAddr.getHostName(),
                        listenerAddr.getPort()).getConnection();
            failedConnection.close();
            failedConnection.bind("cn=test", "password".toCharArray());
            failedConnection.close();
            fail("Connection attempt to closed listener succeeded unexpectedly");
        } catch (final Exception e) {
            // Expected.
        }

        try {
            connection.bind("cn=test", "password".toCharArray());
            fail("Bind attempt on closed connection succeeded unexpectedly");
        } catch (final LdapException e) {
            // Expected.
            assertThat(connection.isValid()).isFalse();
            assertThat(connection.isClosed()).isFalse();
        } finally {
            connection.close();
            assertThat(connection.isValid()).isFalse();
            assertThat(connection.isClosed()).isTrue();
        }
    }
}
