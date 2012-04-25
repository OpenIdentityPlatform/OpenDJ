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
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

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
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Tests the LDAPListener class.
 */
public class LDAPListenerTestCase extends SdkTestCase {

    private static class MockConnectionEventListener implements ConnectionEventListener {
        final CountDownLatch closeLatch = new CountDownLatch(1);
        String errorMessage = null;

        @Override
        public void handleConnectionClosed() {
            errorMessage = "Unexpected call to handleConnectionClosed";
            closeLatch.countDown();
        }

        @Override
        public void handleConnectionError(final boolean isDisconnectNotification,
                final ErrorResultException error) {
            errorMessage = "Unexpected call to handleConnectionError";
            closeLatch.countDown();
        }

        @Override
        public void handleUnsolicitedNotification(final ExtendedResult notification) {
            errorMessage = "Unexpected call to handleUnsolicitedNotification";
            closeLatch.countDown();
        }
    }

    private static class MockServerConnection implements ServerConnection<Integer> {
        volatile LDAPClientContext context = null;
        final CountDownLatch isConnected = new CountDownLatch(1);
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
                final ResultHandler<? super Result> resultHandler)
                throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleBind(final Integer requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super BindResult> resultHandler)
                throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleCompare(final Integer requestContext, final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super CompareResult> resultHandler)
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
            // Do nothing.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleDelete(final Integer requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler)
                throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final Integer requestContext,
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super R> resultHandler) throws UnsupportedOperationException {
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
                final ResultHandler<? super Result> resultHandler)
                throws UnsupportedOperationException {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModifyDN(final Integer requestContext, final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler)
                throws UnsupportedOperationException {
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
            serverConnection.context = clientContext;
            serverConnection.isConnected.countDown();
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
     * Tests connection event listener.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionEventListenerClose() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        final Connection connection;
        try {
            // Connect and bind.
            connection =
                    new LDAPConnectionFactory(onlineServerListener.getSocketAddress())
                            .getConnection();

            final MockConnectionEventListener listener = new MockConnectionEventListener() {

                @Override
                public void handleConnectionClosed() {
                    closeLatch.countDown();
                }
            };

            connection.addConnectionEventListener(listener);
            Assert.assertEquals(listener.closeLatch.getCount(), 1);
            connection.close();
            listener.closeLatch.await();
            Assert.assertNull(listener.errorMessage);
        } finally {
            onlineServerListener.close();
        }
    }

    /**
     * Tests connection event listener.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(enabled = false)
    public void testConnectionEventListenerDisconnect() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        final Connection connection;
        try {
            // Connect and bind.
            connection =
                    new LDAPConnectionFactory(onlineServerListener.getSocketAddress())
                            .getConnection();

            final MockConnectionEventListener listener = new MockConnectionEventListener() {

                @Override
                public void handleConnectionError(final boolean isDisconnectNotification,
                        final ErrorResultException error) {
                    if (isDisconnectNotification) {
                        errorMessage = "Unexpected disconnect notification";
                    }
                    closeLatch.countDown();
                }
            };

            connection.addConnectionEventListener(listener);
            Assert.assertEquals(listener.closeLatch.getCount(), 1);
            Assert.assertTrue(onlineServerConnection.isConnected.await(10, TimeUnit.SECONDS));
            onlineServerConnection.context.disconnect();
            listener.closeLatch.await();
            Assert.assertNull(listener.errorMessage);
            connection.close();
        } finally {
            onlineServerListener.close();
        }
    }

    /**
     * Tests connection event listener.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionEventListenerDisconnectNotification() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        final Connection connection;
        try {
            // Connect and bind.
            connection =
                    new LDAPConnectionFactory(onlineServerListener.getSocketAddress())
                            .getConnection();

            final MockConnectionEventListener listener = new MockConnectionEventListener() {

                @Override
                public void handleConnectionError(final boolean isDisconnectNotification,
                        final ErrorResultException error) {
                    if (!isDisconnectNotification
                            || !error.getResult().getResultCode().equals(ResultCode.BUSY)
                            || !error.getResult().getDiagnosticMessage().equals("test")) {
                        errorMessage = "Missing disconnect notification: " + error;
                    }
                    closeLatch.countDown();
                }
            };

            connection.addConnectionEventListener(listener);
            Assert.assertEquals(listener.closeLatch.getCount(), 1);
            Assert.assertTrue(onlineServerConnection.isConnected.await(10, TimeUnit.SECONDS));
            onlineServerConnection.context.disconnect(ResultCode.BUSY, "test");
            listener.closeLatch.await();
            Assert.assertNull(listener.errorMessage);
            connection.close();
        } finally {
            onlineServerListener.close();
        }
    }

    /**
     * Tests connection event listener.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionEventListenerUnbind() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        final Connection connection;
        try {
            // Connect and bind.
            connection =
                    new LDAPConnectionFactory(onlineServerListener.getSocketAddress())
                            .getConnection();

            final MockConnectionEventListener listener = new MockConnectionEventListener() {

                @Override
                public void handleConnectionClosed() {
                    closeLatch.countDown();
                }
            };

            connection.addConnectionEventListener(listener);
            Assert.assertEquals(listener.closeLatch.getCount(), 1);
            connection.close(Requests.newUnbindRequest(), "called from unit test");
            listener.closeLatch.await();
            Assert.assertNull(listener.errorMessage);
        } finally {
            onlineServerListener.close();
        }
    }

    /**
     * Tests basic LDAP listener functionality.
     *
     * @throws Exception
     *             If an unexpected exception occurred.
     */
    @Test
    public void testLDAPListenerBasic() throws Exception {
        final MockServerConnection serverConnection = new MockServerConnection();
        final MockServerConnectionFactory serverConnectionFactory =
                new MockServerConnectionFactory(serverConnection);
        final LDAPListener listener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(), serverConnectionFactory);
        try {
            // Connect and close.
            final Connection connection =
                    new LDAPConnectionFactory(listener.getSocketAddress()).getConnection();

            Assert.assertTrue(serverConnection.isConnected.await(10, TimeUnit.SECONDS));
            Assert.assertEquals(serverConnection.isClosed.getCount(), 1);

            connection.close();

            Assert.assertTrue(serverConnection.isClosed.await(10, TimeUnit.SECONDS));
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
        final int onlineServerPort = TestCaseUtils.findFreePort();
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", onlineServerPort, onlineServerConnectionFactory);

        try {
            // Connection pool and load balancing tests.
            final ConnectionFactory offlineServer1 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory("localhost",
                            TestCaseUtils.findFreePort()), "offline1");
            final ConnectionFactory offlineServer2 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory("localhost",
                            TestCaseUtils.findFreePort()), "offline2");
            final ConnectionFactory onlineServer =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory("localhost",
                            onlineServerPort), "online");

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
                    new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                            proxyServerConnectionFactory);
            try {
                // Connect and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();

                Assert.assertTrue(proxyServerConnection.isConnected.await(10, TimeUnit.SECONDS));
                Assert.assertTrue(onlineServerConnection.isConnected.await(10, TimeUnit.SECONDS));

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
    @Test
    public void testLDAPListenerLoadBalanceDuringHandleBind() throws Exception {
        // Online server listener.
        final int onlineServerPort = TestCaseUtils.findFreePort();
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", onlineServerPort, onlineServerConnectionFactory);

        try {
            // Connection pool and load balancing tests.
            final ConnectionFactory offlineServer1 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory("localhost",
                            TestCaseUtils.findFreePort()), "offline1");
            final ConnectionFactory offlineServer2 =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory("localhost",
                            TestCaseUtils.findFreePort()), "offline2");
            final ConnectionFactory onlineServer =
                    Connections.newNamedConnectionFactory(new LDAPConnectionFactory("localhost",
                            onlineServerPort), "online");

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
                        final ResultHandler<? super BindResult> resultHandler)
                        throws UnsupportedOperationException {
                    // Get connection from load balancer, this should fail over
                    // twice
                    // before getting connection to online server.
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
                    new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                            proxyServerConnectionFactory);
            try {
                // Connect, bind, and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();
                try {
                    connection.bind("cn=test", "password".toCharArray());

                    Assert.assertTrue(proxyServerConnection.isConnected.await(10, TimeUnit.SECONDS));
                    Assert.assertTrue(onlineServerConnection.isConnected
                            .await(10, TimeUnit.SECONDS));
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
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        try {
            final int offlineServerPort = TestCaseUtils.findFreePort();

            final MockServerConnection proxyServerConnection = new MockServerConnection();
            final MockServerConnectionFactory proxyServerConnectionFactory =
                    new MockServerConnectionFactory(proxyServerConnection) {

                        @Override
                        public ServerConnection<Integer> handleAccept(
                                final LDAPClientContext clientContext) throws ErrorResultException {
                            // First attempt offline server.
                            LDAPConnectionFactory lcf =
                                    new LDAPConnectionFactory("localhost", offlineServerPort);
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
                    new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                            proxyServerConnectionFactory);
            try {
                // Connect and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();

                Assert.assertTrue(proxyServerConnection.isConnected.await(10, TimeUnit.SECONDS));
                Assert.assertTrue(onlineServerConnection.isConnected.await(10, TimeUnit.SECONDS));

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
    @Test
    public void testLDAPListenerProxyDuringHandleBind() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        try {
            final int offlineServerPort = TestCaseUtils.findFreePort();

            final MockServerConnection proxyServerConnection = new MockServerConnection() {

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void handleBind(final Integer requestContext, final int version,
                        final BindRequest request,
                        final IntermediateResponseHandler intermediateResponseHandler,
                        final ResultHandler<? super BindResult> resultHandler)
                        throws UnsupportedOperationException {
                    // First attempt offline server.
                    LDAPConnectionFactory lcf =
                            new LDAPConnectionFactory("localhost", offlineServerPort);
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
                    new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                            proxyServerConnectionFactory);
            try {
                // Connect, bind, and close.
                final Connection connection =
                        new LDAPConnectionFactory(proxyListener.getSocketAddress()).getConnection();
                try {
                    connection.bind("cn=test", "password".toCharArray());

                    Assert.assertTrue(proxyServerConnection.isConnected.await(10, TimeUnit.SECONDS));
                    Assert.assertTrue(onlineServerConnection.isConnected
                            .await(10, TimeUnit.SECONDS));
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
     * Tests server-side disconnection.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testServerDisconnect() throws Exception {
        final MockServerConnection onlineServerConnection = new MockServerConnection();
        final MockServerConnectionFactory onlineServerConnectionFactory =
                new MockServerConnectionFactory(onlineServerConnection);
        final LDAPListener onlineServerListener =
                new LDAPListener("localhost", TestCaseUtils.findFreePort(),
                        onlineServerConnectionFactory);

        final Connection connection;
        try {
            // Connect and bind.
            connection =
                    new LDAPConnectionFactory(onlineServerListener.getSocketAddress())
                            .getConnection();
            try {
                connection.bind("cn=test", "password".toCharArray());
            } catch (final ErrorResultException e) {
                connection.close();
                throw e;
            }
        } finally {
            onlineServerConnection.context.disconnect();
            onlineServerListener.close();
        }

        try {
            // Connect and bind.
            final Connection failedConnection =
                    new LDAPConnectionFactory(onlineServerListener.getSocketAddress())
                            .getConnection();
            failedConnection.close();
            connection.close();
            Assert.fail("Connection attempt to closed listener succeeded unexpectedly");
        } catch (final ConnectionException e) {
            // Expected.
        }

        try {
            connection.bind("cn=test", "password".toCharArray());
            Assert.fail("Bind attempt on closed connection succeeded unexpectedly");
        } catch (final ErrorResultException e) {
            // Expected.
            Assert.assertFalse(connection.isValid());
            Assert.assertFalse(connection.isClosed());
        } finally {
            connection.close();
            Assert.assertFalse(connection.isValid());
            Assert.assertTrue(connection.isClosed());
        }
    }
}
