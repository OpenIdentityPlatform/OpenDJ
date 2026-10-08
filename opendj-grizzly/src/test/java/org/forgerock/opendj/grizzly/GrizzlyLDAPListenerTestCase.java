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
 * Portions Copyright 2017-2026 3A Systems, LLC
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

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
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.TestCaseUtils;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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

    /**
     * Fails over the way a proxy does: connects to a free port, which must refuse the connection,
     * then to the online server.
     */
    private static void failOverToOnlineServer(final InetSocketAddress onlineAddress) throws LdapException {
        // The numeric host avoids a reverse lookup of the loopback address.
        final InetSocketAddress offlineAddress = findFreeSocketAddress();
        final LDAPConnectionFactory offlineFactory =
                new LDAPConnectionFactory(offlineAddress.getHostString(), offlineAddress.getPort());
        try {
            offlineFactory.getConnection().close();
        } catch (final ConnectionException expected) {
            // This is expected - so go to online server.
            final LDAPConnectionFactory onlineFactory =
                    new LDAPConnectionFactory(onlineAddress.getHostName(), onlineAddress.getPort());
            try {
                onlineFactory.getConnection().close();
                return;
            } catch (final Exception e) {
                throw newLdapException(ResultCode.OTHER,
                        "Unexpected exception when connecting to online server", e);
            } finally {
                onlineFactory.close();
            }
        } catch (final Exception e) {
            throw newLdapException(ResultCode.OTHER,
                    "Unexpected exception when connecting to offline server", e);
        } finally {
            offlineFactory.close();
        }
        throw newLdapException(ResultCode.OTHER, "Connection to offline server succeeded unexpectedly");
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
                    new LDAPConnectionFactory(proxyAddr.getHostName(), proxyAddr.getPort());
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
                        // result as a bind response, and would then never answer the bind.
                        resultHandler.handleException(newLdapException(Responses.newBindResult(ResultCode.OTHER)
                                .setDiagnosticMessage(e.getResult().getDiagnosticMessage()).setCause(e)));
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
