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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.grizzly;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Connections.newFailoverLoadBalancer;
import static org.forgerock.opendj.ldap.Connections.newFixedConnectionPool;
import static org.forgerock.opendj.ldap.Connections.newRoundRobinLoadBalancer;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.TestCaseUtils.findFreeSocketAddress;
import static org.forgerock.opendj.ldap.TestCaseUtils.getServerSocketAddress;
import static org.forgerock.opendj.ldap.requests.Requests.newCRAMMD5SASLBindRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newDigestMD5SASLBindRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.util.Options.defaultOptions;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ConnectionPool;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPServer;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.MockConnectionEventListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CRAMMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.DigestMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.util.Options;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the {@code ConnectionFactory} classes.
 */
@SuppressWarnings("javadoc")
public class ConnectionFactoryTestCase extends SdkTestCase {
    /** Test timeout in ms for tests which need to wait for network events. */
    private static final long TEST_TIMEOUT = 30L;
    private static final long TEST_TIMEOUT_MS = TEST_TIMEOUT * 1000L;

    /**
     * Ensures that the LDAP Server is running.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
    }

    @AfterClass
    public void stopServer() throws Exception {
        TestCaseUtils.stopServer();
    }

    /**
     * Disables logging before the tests.
     */
    @BeforeClass
    public void disableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass
    public void enableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.INFO);
    }

    @DataProvider
    Object[][] connectionFactories() throws Exception {
        Object[][] factories = new Object[21][1];

        // HeartBeatConnectionFactory
        // Use custom search request.
        SearchRequest request = Requests.newSearchRequest(
            "uid=user.0,ou=people,o=test", SearchScope.BASE_OBJECT, "(objectclass=*)", "cn");

        InetSocketAddress serverAddress = getServerSocketAddress();
        factories[0][0] = new LDAPConnectionFactory(serverAddress.getHostName(),
                                                    serverAddress.getPort(),
                                                    defaultOptions()
                                                           .set(HEARTBEAT_ENABLED, true)
                                                           .set(HEARTBEAT_INTERVAL, duration("1000 ms"))
                                                           .set(HEARTBEAT_TIMEOUT, duration("2000 ms"))
                                                           .set(HEARTBEAT_SEARCH_REQUEST, request));

        // InternalConnectionFactory
        factories[1][0] = Connections.newInternalConnectionFactory(LDAPServer.getInstance(), null);

        // AuthenticatedConnectionFactory
        final SimpleBindRequest anon = newSimpleBindRequest("", new char[0]);
        factories[2][0] = new LDAPConnectionFactory(serverAddress.getHostName(),
                                                    serverAddress.getPort(),
                                                    defaultOptions().set(AUTHN_BIND_REQUEST, anon));

        // AuthenticatedConnectionFactory with multi-stage SASL
        final CRAMMD5SASLBindRequest crammd5 = newCRAMMD5SASLBindRequest("id:user", "password".toCharArray());
        factories[3][0] = new LDAPConnectionFactory(serverAddress.getHostName(),
                                                    serverAddress.getPort(),
                                                    defaultOptions().set(AUTHN_BIND_REQUEST, crammd5));

        // LDAPConnectionFactory with default options
        factories[4][0] = new LDAPConnectionFactory(serverAddress.getHostName(), serverAddress.getPort());

        // LDAPConnectionFactory with startTLS
        SSLContext sslContext = new SSLContextBuilder().setTrustManager(TrustManagers.trustAll()).getSSLContext();
        final Options startTlsOptions = defaultOptions()
                                   .set(SSL_CONTEXT, sslContext)
                                   .set(SSL_USE_STARTTLS, true)
                                   .set(SSL_ENABLED_CIPHER_SUITES,
                                        asList("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                                                      "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
                                                      "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
                                                      "SSL_DH_anon_WITH_DES_CBC_SHA",
                                                      "SSL_DH_anon_WITH_RC4_128_MD5",
                                                      "TLS_DH_anon_WITH_AES_128_CBC_SHA",
                                                      "TLS_DH_anon_WITH_AES_256_CBC_SHA"));
        factories[5][0] = new LDAPConnectionFactory(serverAddress.getHostName(),
                                                    serverAddress.getPort(),
                                                    startTlsOptions);

        // startTLS + SASL confidentiality
        // Use IP address here so that DIGEST-MD5 host verification works if
        // local host name is not localhost (e.g. on some machines it might be
        // localhost.localdomain).
        // FIXME: enable QOP once OPENDJ-514 is fixed.
        final DigestMD5SASLBindRequest digestmd5 = newDigestMD5SASLBindRequest("id:user", "password".toCharArray())
                .setCipher(DigestMD5SASLBindRequest.CIPHER_LOW);
        factories[6][0] = new LDAPConnectionFactory(serverAddress.getHostName(),
                                                    serverAddress.getPort(),
                                                    Options.copyOf(startTlsOptions).set(AUTHN_BIND_REQUEST, digestmd5));

        // Connection pool and load balancing tests.
        InetSocketAddress offlineSocketAddress1 = findFreeSocketAddress();
        ConnectionFactory offlineServer1 =
                Connections.newNamedConnectionFactory(
                        new LDAPConnectionFactory(offlineSocketAddress1.getHostName(),
                                offlineSocketAddress1.getPort()), "offline1");
        InetSocketAddress offlineSocketAddress2 = findFreeSocketAddress();
        ConnectionFactory offlineServer2 =
                Connections.newNamedConnectionFactory(
                        new LDAPConnectionFactory(offlineSocketAddress2.getHostName(),
                                offlineSocketAddress2.getPort()), "offline2");
        ConnectionFactory onlineServer =
                Connections.newNamedConnectionFactory(
                        new LDAPConnectionFactory(serverAddress.getHostName(),
                                serverAddress.getPort()), "online");

        // Connection pools.
        factories[7][0] = newFixedConnectionPool(onlineServer, 10);

        // Round robin.
        factories[8][0] = newRoundRobinLoadBalancer(asList(onlineServer, offlineServer1), defaultOptions());
        factories[9][0] = factories[8][0];
        factories[10][0] = factories[8][0];
        factories[11][0] = newRoundRobinLoadBalancer(asList(offlineServer1, onlineServer), defaultOptions());
        factories[12][0] = newRoundRobinLoadBalancer(asList(offlineServer1, offlineServer2, onlineServer),
                                                     defaultOptions());
        factories[13][0] = newRoundRobinLoadBalancer(asList(newFixedConnectionPool(offlineServer1, 10),
                                                            newFixedConnectionPool(onlineServer, 10)),
                                                     defaultOptions());

        // Fail-over.
        factories[14][0] = newFailoverLoadBalancer(asList(onlineServer, offlineServer1), defaultOptions());
        factories[15][0] = factories[14][0];
        factories[16][0] = factories[14][0];
        factories[17][0] = newFailoverLoadBalancer(asList(offlineServer1, onlineServer), defaultOptions());
        factories[18][0] = newFailoverLoadBalancer(asList(offlineServer1, offlineServer2, onlineServer),
                                                   defaultOptions());
        factories[19][0] = newFailoverLoadBalancer(asList(newFixedConnectionPool(offlineServer1, 10),
                                                          newFixedConnectionPool(onlineServer, 10)),
                                                   defaultOptions());

        factories[20][0] = newFixedConnectionPool(onlineServer, 10);

        return factories;
    }

    /**
     * Tests the async connection in the blocking mode. This is not fully async as it blocks on the promise.
     *
     * @throws Exception
     */
    @Test(dataProvider = "connectionFactories", timeOut = TEST_TIMEOUT_MS)
    public void testBlockingPromiseNoHandler(ConnectionFactory factory) throws Exception {
        final Promise<? extends Connection, LdapException> promise = factory.getConnectionAsync();
        final Connection con = promise.get();
        // quickly check if it is a valid connection.
        // Don't use a result handler.
        assertNotNull(con.readEntryAsync(DN.rootDN(), null).getOrThrow());
        con.close();
    }

    /**
     * Tests the non-blocking fully async connection using a handler.
     *
     * @throws Exception
     */
    @Test(dataProvider = "connectionFactories", timeOut = TEST_TIMEOUT_MS)
    public void testNonBlockingPromiseWithHandler(ConnectionFactory factory) throws Exception {
        // Use the promise to get the result asynchronously.
        final PromiseImpl<Connection, LdapException> promise = PromiseImpl.create();

        factory.getConnectionAsync().thenOnResult(new ResultHandler<Connection>() {
            @Override
            public void handleResult(Connection con) {
                con.close();
                promise.handleResult(con);
            }
        }).thenOnException(new ExceptionHandler<LdapException>() {
            @Override
            public void handleException(LdapException exception) {
                promise.handleException(exception);
            }

        });

        // Since we don't have anything to do, we would rather
        // be notified by the promise when the other thread calls our handler.
        // should do a timed wait rather?
        promise.getOrThrow();
    }

    /**
     * Tests the synchronous connection.
     *
     * @throws Exception
     */
    @Test(dataProvider = "connectionFactories", timeOut = TEST_TIMEOUT_MS)
    public void testSynchronousConnection(ConnectionFactory factory) throws Exception {
        final Connection con = factory.getConnection();
        assertNotNull(con);
        // quickly check if it is a valid connection.
        assertTrue(con.readEntry("").getName().isRootDN());
        con.close();
    }

    /**
     * Verifies that LDAP connections take into consideration changes to the default schema post creation. See
     * OPENDJ-159.
     * <p/>
     * This test is disabled because it cannot be run in parallel with rest of the test suite, because it modifies the
     * global default schema.
     *
     * @throws Exception
     *         If an unexpected error occurred.
     */
    @Test(enabled = false)
    public void testSchemaUsage() throws Exception {
        // Create a connection factory: this should always use the default
        // schema, even if it is updated.
        InetSocketAddress socketAddress = getServerSocketAddress();
        final ConnectionFactory factory = new LDAPConnectionFactory(socketAddress.getHostName(),
                socketAddress.getPort());
        final Schema defaultSchema = Schema.getDefaultSchema();

        final Connection connection = factory.getConnection();
        try {
            // Simulate a client which reads the schema from the server and then
            // sets it as the application default. We then want subsequent
            // operations to use this schema, not the original default.
            final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
            builder.addAttributeType("( 0.9.2342.19200300.100.1.3 NAME 'mail' EQUALITY "
                    + "caseIgnoreIA5Match SUBSTR caseIgnoreIA5SubstringsMatch "
                    + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26{256} )", false);
            final Schema testSchema = builder.toSchema().asNonStrictSchema();
            assertThat(testSchema.getWarnings()).isEmpty();
            Schema.setDefaultSchema(testSchema);

            // Read an entry containing the mail attribute.
            final SearchResultEntry e = connection.readEntry("uid=user.0,ou=people,o=test");

            assertThat(e.getAttribute("mail")).isNotNull();
            assertThat(e.getAttribute(AttributeDescription.valueOf("mail", testSchema)))
                    .isNotNull();
        } finally {
            // Restore original schema.
            Schema.setDefaultSchema(defaultSchema);

            // Close connection.
            connection.close();
        }
    }

    /**
     * Tests connection pool closure.
     *
     * @throws Exception
     *         If an unexpected exception occurred.
     */
    @Test
    public void testConnectionPoolClose() throws Exception {
        // We'll use a pool of 4 connections.
        final int size = 4;

        // Count number of real connections which are open.
        final AtomicInteger realConnectionCount = new AtomicInteger();
        final boolean[] realConnectionIsClosed = new boolean[size];
        Arrays.fill(realConnectionIsClosed, true);

        // Mock underlying connection factory which always succeeds.
        final ConnectionFactory mockFactory = mock(ConnectionFactory.class);
        when(mockFactory.getConnectionAsync()).thenAnswer(new Answer<LdapPromise<Connection>>() {

            @Override
            public LdapPromise<Connection> answer(InvocationOnMock invocation) throws Throwable {
                // Update state.
                final int connectionID = realConnectionCount.getAndIncrement();
                realConnectionIsClosed[connectionID] = false;

                // Mock connection decrements counter on close.
                Connection mockConnection = mock(Connection.class);
                doAnswer(new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        realConnectionCount.decrementAndGet();
                        realConnectionIsClosed[connectionID] = true;
                        return null;
                    }
                }).when(mockConnection).close();
                when(mockConnection.isValid()).thenReturn(true);
                when(mockConnection.toString()).thenReturn("Mock connection " + connectionID);

                return newSuccessfulLdapPromise(mockConnection);
            }
        });

        ConnectionPool pool = newFixedConnectionPool(mockFactory, size);
        Connection[] pooledConnections = new Connection[size];
        for (int i = 0; i < size; i++) {
            pooledConnections[i] = pool.getConnection();
        }

        // Pool is fully utilized.
        assertThat(realConnectionCount.get()).isEqualTo(size);
        assertThat(pooledConnections[0].isClosed()).isFalse();
        assertThat(pooledConnections[1].isClosed()).isFalse();
        assertThat(pooledConnections[2].isClosed()).isFalse();
        assertThat(pooledConnections[3].isClosed()).isFalse();
        assertThat(realConnectionIsClosed[0]).isFalse();
        assertThat(realConnectionIsClosed[1]).isFalse();
        assertThat(realConnectionIsClosed[2]).isFalse();
        assertThat(realConnectionIsClosed[3]).isFalse();

        // Release two connections.
        pooledConnections[0].close();
        pooledConnections[1].close();
        assertThat(realConnectionCount.get()).isEqualTo(4);
        assertThat(pooledConnections[0].isClosed()).isTrue();
        assertThat(pooledConnections[1].isClosed()).isTrue();
        assertThat(pooledConnections[2].isClosed()).isFalse();
        assertThat(pooledConnections[3].isClosed()).isFalse();
        assertThat(realConnectionIsClosed[0]).isFalse();
        assertThat(realConnectionIsClosed[1]).isFalse();
        assertThat(realConnectionIsClosed[2]).isFalse();
        assertThat(realConnectionIsClosed[3]).isFalse();

        // Close the pool closing the two connections immediately.
        pool.close();
        assertThat(realConnectionCount.get()).isEqualTo(2);
        assertThat(pooledConnections[0].isClosed()).isTrue();
        assertThat(pooledConnections[1].isClosed()).isTrue();
        assertThat(pooledConnections[2].isClosed()).isFalse();
        assertThat(pooledConnections[3].isClosed()).isFalse();
        assertThat(realConnectionIsClosed[0]).isTrue();
        assertThat(realConnectionIsClosed[1]).isTrue();
        assertThat(realConnectionIsClosed[2]).isFalse();
        assertThat(realConnectionIsClosed[3]).isFalse();

        // Release two remaining connections and check that they get closed.
        pooledConnections[2].close();
        pooledConnections[3].close();
        assertThat(realConnectionCount.get()).isEqualTo(0);
        assertThat(pooledConnections[0].isClosed()).isTrue();
        assertThat(pooledConnections[1].isClosed()).isTrue();
        assertThat(pooledConnections[2].isClosed()).isTrue();
        assertThat(pooledConnections[3].isClosed()).isTrue();
        assertThat(realConnectionIsClosed[0]).isTrue();
        assertThat(realConnectionIsClosed[1]).isTrue();
        assertThat(realConnectionIsClosed[2]).isTrue();
        assertThat(realConnectionIsClosed[3]).isTrue();
    }

    private static final class CloseNotify {
        private final boolean closeOnAccept;
        private final boolean doBindFirst;
        private final boolean useEventListener;
        private final boolean sendDisconnectNotification;

        private CloseNotify(boolean closeOnAccept, boolean doBindFirst, boolean useEventListener,
                boolean sendDisconnectNotification) {
            this.closeOnAccept = closeOnAccept;
            this.doBindFirst = doBindFirst;
            this.useEventListener = useEventListener;
            this.sendDisconnectNotification = sendDisconnectNotification;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            if (closeOnAccept) {
                builder.append(" closeOnAccept");
            }
            if (doBindFirst) {
                builder.append(" doBindFirst");
            }
            if (useEventListener) {
                builder.append(" useEventListener");
            }
            if (sendDisconnectNotification) {
                builder.append(" sendDisconnectNotification");
            }
            builder.append(" ]");
            return builder.toString();
        }
    }

    @DataProvider
    Object[][] closeNotifyConfig() {
        // @formatter:off
        return new Object[][] {
            // closeOnAccept, doBindFirst, useEventListener, sendDisconnectNotification

            // Close on accept.
            { new CloseNotify(true,  false, false, false) },
            { new CloseNotify(true,  false, true,  false) },

            // Use disconnect.
            { new CloseNotify(false, false, false, false) },
            { new CloseNotify(false, false, false, true) },
            { new CloseNotify(false, false, true,  false) },
            { new CloseNotify(false, false, true,  true) },
            { new CloseNotify(false, true,  false, false) },
            { new CloseNotify(false, true,  false, true) },
            { new CloseNotify(false, true,  true,  false) },
            { new CloseNotify(false, true,  true,  true) },
        };
        // @formatter:on
    }

    @SuppressWarnings("unchecked")
    @Test(dataProvider = "closeNotifyConfig")
    public void testCloseNotify(final CloseNotify config) throws Exception {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final AtomicReference<LDAPClientContext> contextHolder = new AtomicReference<>();

        final ServerConnectionFactory<LDAPClientContext, Integer> mockServer =
                mock(ServerConnectionFactory.class);
        when(mockServer.handleAccept(any(LDAPClientContext.class))).thenAnswer(
                new Answer<ServerConnection<Integer>>() {

                    @Override
                    public ServerConnection<Integer> answer(InvocationOnMock invocation)
                            throws Throwable {
                        // Allow the context to be accessed from outside the mock.
                        contextHolder.set((LDAPClientContext) invocation.getArguments()[0]);
                        connectLatch.countDown(); /* is this needed? */
                        if (config.closeOnAccept) {
                            throw newLdapException(ResultCode.UNAVAILABLE);
                        } else {
                            // Return a mock connection which always succeeds for binds.
                            ServerConnection<Integer> mockConnection = mock(ServerConnection.class);
                            doAnswer(new Answer<Void>() {
                                @Override
                                public Void answer(InvocationOnMock invocation) throws Throwable {
                                    LdapResultHandler<? super BindResult> resultHandler =
                                            (LdapResultHandler<? super BindResult>) invocation
                                                    .getArguments()[4];
                                    resultHandler.handleResult(Responses
                                            .newBindResult(ResultCode.SUCCESS));
                                    return null;
                                }
                            }).when(mockConnection).handleBind(anyInt(), anyInt(),
                                    any(BindRequest.class), any(IntermediateResponseHandler.class),
                                    any(LdapResultHandler.class));
                            return mockConnection;
                        }
                    }
                });

        LDAPListener listener = new LDAPListener(findFreeSocketAddress(), mockServer);
        try {
            LDAPConnectionFactory clientFactory =
                    new LDAPConnectionFactory(listener.getHostName(), listener.getPort());
            final Connection client = clientFactory.getConnection();
            connectLatch.await(TEST_TIMEOUT, TimeUnit.SECONDS);
            MockConnectionEventListener mockListener = null;
            try {
                if (config.useEventListener) {
                    mockListener = new MockConnectionEventListener();
                    client.addConnectionEventListener(mockListener);
                }
                if (config.doBindFirst) {
                    client.bind("cn=test", "password".toCharArray());
                }
                if (!config.closeOnAccept) {
                    // Disconnect using client context.
                    LDAPClientContext context = contextHolder.get();
                    assertThat(context).isNotNull();
                    assertThat(context.isClosed()).isFalse();
                    if (config.sendDisconnectNotification) {
                        context.disconnect(ResultCode.BUSY, "busy");
                    } else {
                        context.disconnect();
                    }
                    assertThat(context.isClosed()).isTrue();
                }
                // Block until remote close is signalled.
                if (mockListener != null) {
                    // Block using listener.
                    mockListener.awaitError(TEST_TIMEOUT, TimeUnit.SECONDS);
                    assertThat(mockListener.getInvocationCount()).isEqualTo(1);
                    assertThat(mockListener.isDisconnectNotification()).isEqualTo(
                            config.sendDisconnectNotification);
                    assertThat(mockListener.getError()).isNotNull();
                } else {
                    // Block by spinning on isValid.
                    waitForCondition(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return !client.isValid();
                        }
                    });
                }
                assertThat(client.isValid()).isFalse();
                assertThat(client.isClosed()).isFalse();
            } finally {
                client.close();
            }
            // Check state after remote close and local close.
            assertThat(client.isValid()).isFalse();
            assertThat(client.isClosed()).isTrue();
            if (mockListener != null) {
                mockListener.awaitClose(TEST_TIMEOUT, TimeUnit.SECONDS);
                assertThat(mockListener.getInvocationCount()).isEqualTo(2);
            }
        } finally {
            listener.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUnsolicitedNotifications() throws Exception {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final AtomicReference<LDAPClientContext> contextHolder = new AtomicReference<>();

        final ServerConnectionFactory<LDAPClientContext, Integer> mockServer =
                mock(ServerConnectionFactory.class);
        when(mockServer.handleAccept(any(LDAPClientContext.class))).thenAnswer(
                new Answer<ServerConnection<Integer>>() {

                    @Override
                    public ServerConnection<Integer> answer(InvocationOnMock invocation)
                            throws Throwable {
                        // Allow the context to be accessed from outside the mock.
                        contextHolder.set((LDAPClientContext) invocation.getArguments()[0]);
                        connectLatch.countDown(); /* is this needed? */
                        return mock(ServerConnection.class);
                    }
                });

        LDAPListener listener = new LDAPListener(findFreeSocketAddress(), mockServer);
        try {
            LDAPConnectionFactory clientFactory =
                    new LDAPConnectionFactory(listener.getHostName(),
                            listener.getPort());
            final Connection client = clientFactory.getConnection();
            connectLatch.await(TEST_TIMEOUT, TimeUnit.SECONDS);
            try {
                MockConnectionEventListener mockListener = new MockConnectionEventListener();
                client.addConnectionEventListener(mockListener);

                // Send notification.
                LDAPClientContext context = contextHolder.get();
                assertThat(context).isNotNull();
                context.sendUnsolicitedNotification(Responses.newGenericExtendedResult(
                        ResultCode.OTHER).setOID("1.2.3.4"));
                assertThat(context.isClosed()).isFalse();

                // Block using listener.
                mockListener.awaitNotification(TEST_TIMEOUT, TimeUnit.SECONDS);
                assertThat(mockListener.getInvocationCount()).isEqualTo(1);
                assertThat(mockListener.getNotification()).isNotNull();
                assertThat(mockListener.getNotification().getResultCode()).isEqualTo(
                        ResultCode.OTHER);
                assertThat(mockListener.getNotification().getOID()).isEqualTo("1.2.3.4");
                assertThat(client.isValid()).isTrue();
                assertThat(client.isClosed()).isFalse();
            } finally {
                client.close();
            }
        } finally {
            listener.close();
        }
    }

    @Test(description = "Test for OPENDJ-1121: Closing a connection after "
            + "closing the connection factory causes NPE")
    public void testFactoryCloseBeforeConnectionClose() throws Exception {
        InetSocketAddress socketAddress = getServerSocketAddress();
        final LDAPConnectionFactory ldap = new LDAPConnectionFactory(socketAddress.getHostName(),
                                                                     socketAddress.getPort(),
                                                                     defaultOptions()
                                                                            .set(HEARTBEAT_ENABLED, true));
        final ConnectionPool pool = newFixedConnectionPool(ldap, 2);
        final ConnectionFactory factory = newFailoverLoadBalancer(singletonList(pool), defaultOptions());
        Connection conn = null;
        try {
            conn = factory.getConnection();
        } finally {
            factory.close();
            if (conn != null) {
                conn.close();
            }
        }
    }

    private void waitForCondition(Callable<Boolean> condition) throws Exception {
        long timeout = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (!condition.call()) {
            Thread.yield();
            if (System.currentTimeMillis() > timeout) {
                throw new TimeoutException("Test timed out after " + TEST_TIMEOUT + " seconds");
            }
        }
    }
}
