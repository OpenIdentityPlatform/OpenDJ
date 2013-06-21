/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.Fail.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.StaticUtils;

/**
 * Tests the LDAPListener class.
 */
public class LDAPListenerTestCase extends SdkTestCase {

    private static class MockServerConnection implements ServerConnection<Integer> {
        final AsynchronousFutureResult<Throwable, ResultHandler<Throwable>> connectionError =
                new AsynchronousFutureResult<Throwable, ResultHandler<Throwable>>(null);
        final AsynchronousFutureResult<LDAPClientContext, ResultHandler<LDAPClientContext>> context =
                new AsynchronousFutureResult<LDAPClientContext, ResultHandler<LDAPClientContext>>(
                        null);
        final CountDownLatch isClosed = new CountDownLatch(1);

        MockServerConnection() {
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleAbandon(final Integer requestContext, final AbandonRequest request)
                throws UnsupportedOperationException {
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleAdd(final Integer requestContext, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleBind(final Integer requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<BindResult> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleCompare(final Integer requestContext, final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<CompareResult> resultHandler)
                throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newCompareResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleConnectionClosed(final Integer requestContext, final UnbindRequest request) {
            isClosed.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleConnectionDisconnected(final ResultCode resultCode, final String message) {
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleConnectionError(final Throwable error) {
            connectionError.handleResult(error);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleDelete(final Integer requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<R> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleErrorResult(ErrorResultException.newErrorResult(request
                    .getResultDecoder().newExtendedErrorResult(ResultCode.PROTOCOL_ERROR, "",
                            "Extended operation " + request.getOID() + " not supported")));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModify(final Integer requestContext, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModifyDN(final Integer requestContext, final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleSearch(final Integer requestContext, final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler resultHandler) throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

    }

    private static class MockServerConnectionFactory implements
            ServerConnectionFactory<LDAPClientContext, Integer> {

        private final MockServerConnection serverConnection;

        private MockServerConnectionFactory(final MockServerConnection serverConnection) {
            this.serverConnection = serverConnection;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ServerConnection<Integer> handleAccept(final LDAPClientContext clientContext)
                throws ErrorResultException {
            serverConnection.context.handleResult(clientContext);
            return serverConnection;
        }
    }

    /**
     * Disables logging before the tests.
     */
    @BeforeClass()
    public void disableLogging() {
        StaticUtils.DEBUG_LOG.setLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass()
    public void enableLogging() {
        StaticUtils.DEBUG_LOG.setLevel(Level.INFO);
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
        final LDAPListener listener =
                new LDAPListener(new InetSocketAddress(0), serverConnectionFactory);
        try {
            // Connect and close.
            final Connection connection =
                    new LDAPConnectionFactory(listener.getSocketAddress()).getConnection();
            assertThat(serverConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
            assertThat(serverConnection.isClosed.getCount()).isEqualTo(1);
            connection.close();
            assertThat(serverConnection.isClosed.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            listener.close();
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
    @Test(enabled = false)
    public void testLDAPListenerLoadBalanceDuringHandleAccept() throws Exception {
        // Online server listener.
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener(findFreeSocketAddress(), onlineServerConnectionFactory);

        try {
            // Connection pool and load balancing tests.
            final ConnectionFactory offlineServer1 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            findFreeSocketAddress()), "offline1");
            final ConnectionFactory offlineServer2 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            findFreeSocketAddress()), "offline2");
            final ConnectionFactory onlineServer =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            onlineServerListener.getSocketAddress()), "online");

            // Round robin.
            final ConnectionFactory loadBalancer =
                    Connections.newLoadBalancer(new RoundRobinLoadBalancingAlgorithm(Arrays
                            .<ConnectionFactory> asList(Connections.newFixedConnectionPool(
                                    offlineServer1, 10), Connections.newFixedConnectionPool(
                                    offlineServer2, 10), Connections.newFixedConnectionPool(
                                    onlineServer, 10))));

            final MockServerConnection proxyServerConnection = new MockServerConnection();
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection) {

                        @Override
                        public ServerConnection<Integer> handleAccept(
                                final LDAPClientContext clientContext) throws ErrorResultException {
                            // Get connection from load balancer, this should
                            // fail over twice before getting connection to
                            // online server.
                            loadBalancer.getConnection().close();
                            return super.handleAccept(clientContext);
                        }

                    };

            final LDAPListener proxyListener =
                    new LDAPListener(findFreeSocketAddress(), proxyServerConnectionFactory);
            try {
                // Connect and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();

                assertThat(proxyServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(onlineServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();

                // Wait for connect/close to complete.
                connection.close();

                proxyServerConnection.isClosed.await();
            } finally {
                proxyListener.close();
            }
        } finally {
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
    @Test(timeOut = 10000)
    public void testLDAPListenerLoadBalanceDuringHandleBind() throws Exception {
        // Online server listener.
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener(new InetSocketAddress(0), onlineServerConnectionFactory);

        try {
            // Connection pool and load balancing tests.
            final ConnectionFactory offlineServer1 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            findFreeSocketAddress()), "offline1");
            final ConnectionFactory offlineServer2 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            findFreeSocketAddress()), "offline2");
            final ConnectionFactory onlineServer =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory(
                            onlineServerListener.getSocketAddress()), "online");

            // Round robin.
            final ConnectionFactory loadBalancer =
                    Connections.newLoadBalancer(new RoundRobinLoadBalancingAlgorithm(Arrays
                            .<ConnectionFactory> asList(Connections.newFixedConnectionPool(
                                    offlineServer1, 10), Connections.newFixedConnectionPool(
                                    offlineServer2, 10), Connections.newFixedConnectionPool(
                                    onlineServer, 10))));

            final MockServerConnection proxyServerConnection = new MockServerConnection() {

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void handleBind(final Integer requestContext, final int version,
                        final BindRequest request,
                        final IntermediateResponseHandler intermediateResponseHandler,
                        final ResultHandler<BindResult> resultHandler)
                        throws UnsupportedOperationException {
                    // Get connection from load balancer, this should fail over
                    // twice before getting connection to online server.
                    try {
                        loadBalancer.getConnection().close();
                        resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
                    } catch (final Exception e) {
                        // Unexpected.
                        resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
                                ResultCode.OTHER,
                                "Unexpected exception when connecting to load balancer", e));
                    }
                }

            };
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection);

            final LDAPListener proxyListener =
                    new LDAPListener(new InetSocketAddress(0), proxyServerConnectionFactory);
            try {
                // Connect, bind, and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();
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
                proxyListener.close();
            }
        } finally {
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
    @Test(enabled = false)
    public void testLDAPListenerProxyDuringHandleAccept() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener(findFreeSocketAddress(), onlineServerConnectionFactory);

        try {
            final MockServerConnection proxyServerConnection = new MockServerConnection();
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection) {

                        @Override
                        public ServerConnection<Integer> handleAccept(
                                final LDAPClientContext clientContext) throws ErrorResultException {
                            // First attempt offline server.
                            LDAPConnectionFactory lcf =
                                    new LDAPConnectionFactory(findFreeSocketAddress());
                            try {
                                // This is expected to fail.
                                lcf.getConnection().close();
                                throw ErrorResultException.newErrorResult(ResultCode.OTHER,
                                        "Connection to offline server succeeded unexpectedly");
                            } catch (final ConnectionException ce) {
                                // This is expected - so go to online server.
                                try {
                                    lcf =
                                            new LDAPConnectionFactory(onlineServerListener
                                                    .getSocketAddress());
                                    lcf.getConnection().close();
                                } catch (final Exception e) {
                                    // Unexpected.
                                    throw ErrorResultException
                                            .newErrorResult(
                                                    ResultCode.OTHER,
                                                    "Unexpected exception when connecting to online server",
                                                    e);
                                }
                            } catch (final Exception e) {
                                // Unexpected.
                                throw ErrorResultException
                                        .newErrorResult(
                                                ResultCode.OTHER,
                                                "Unexpected exception when connecting to offline server",
                                                e);
                            }

                            return super.handleAccept(clientContext);
                        }

                    };
            final LDAPListener proxyListener =
                    new LDAPListener(findFreeSocketAddress(), proxyServerConnectionFactory);
            try {
                // Connect and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();

                assertThat(proxyServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(onlineServerConnection.context.get(10, TimeUnit.SECONDS)).isNotNull();

                connection.close();

                // Wait for connect/close to complete.
                proxyServerConnection.isClosed.await();
            } finally {
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
    @Test(timeOut = 10000)
    public void testLDAPListenerProxyDuringHandleBind() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener(findFreeSocketAddress(), onlineServerConnectionFactory);

        try {
            final MockServerConnection proxyServerConnection = new MockServerConnection() {

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void handleBind(final Integer requestContext, final int version,
                        final BindRequest request,
                        final IntermediateResponseHandler intermediateResponseHandler,
                        final ResultHandler<BindResult> resultHandler)
                        throws UnsupportedOperationException {
                    // First attempt offline server.
                    LDAPConnectionFactory lcf = new LDAPConnectionFactory(findFreeSocketAddress());
                    try {
                        // This is expected to fail.
                        lcf.getConnection().close();
                        resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
                                ResultCode.OTHER,
                                "Connection to offline server succeeded unexpectedly"));
                    } catch (final ConnectionException ce) {
                        // This is expected - so go to online server.
                        try {
                            lcf =
                                    new LDAPConnectionFactory(onlineServerListener
                                            .getSocketAddress());
                            lcf.getConnection().close();
                            resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
                        } catch (final Exception e) {
                            // Unexpected.
                            resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
                                    ResultCode.OTHER,
                                    "Unexpected exception when connecting to online server", e));
                        }
                    } catch (final Exception e) {
                        // Unexpected.
                        resultHandler.handleErrorResult(ErrorResultException.newErrorResult(
                                ResultCode.OTHER,
                                "Unexpected exception when connecting to offline server", e));
                    }
                }

            };
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection);
            final LDAPListener proxyListener =
                    new LDAPListener(findFreeSocketAddress(), proxyServerConnectionFactory);
            try {
                // Connect, bind, and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();
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
    @Test
    public void testMaxRequestSize() throws Exception {
        final MockServerConnection serverConnection = new MockServerConnection();
        final MockServerConnectionFactory factory =
                new MockServerConnectionFactory(serverConnection);
        final LDAPListenerOptions options = new LDAPListenerOptions().setMaxRequestSize(2048);
        final LDAPListener listener = new LDAPListener(findFreeSocketAddress(), factory, options);

        Connection connection = null;
        try {
            connection = new LDAPConnectionFactory(listener.getSocketAddress()).getConnection();

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
            } catch (final ErrorResultException e) {
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
        final MockServerConnectionFactory factory =
                new MockServerConnectionFactory(serverConnection);
        final LDAPListener listener = new LDAPListener(findFreeSocketAddress(), factory);

        final Connection connection;
        try {
            // Connect and bind.
            connection = new LDAPConnectionFactory(listener.getSocketAddress()).getConnection();
            try {
                connection.bind("cn=test", "password".toCharArray());
            } catch (final ErrorResultException e) {
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
                    new LDAPConnectionFactory(listener.getSocketAddress()).getConnection();
            failedConnection.close();
            connection.close();
            fail("Connection attempt to closed listener succeeded unexpectedly");
        } catch (final ConnectionException e) {
            // Expected.
        }

        try {
            connection.bind("cn=test", "password".toCharArray());
            fail("Bind attempt on closed connection succeeded unexpectedly");
        } catch (final ErrorResultException e) {
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
