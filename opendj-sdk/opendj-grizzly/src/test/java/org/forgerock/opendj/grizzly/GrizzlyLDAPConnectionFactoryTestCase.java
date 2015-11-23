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
 *     Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.MockConnectionEventListener;
import org.forgerock.opendj.ldap.ProviderNotFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.util.Options;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.Fail.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the {@link LDAPConnectionFactory} class.
 */
@SuppressWarnings({ "javadoc", "unchecked" })
public class GrizzlyLDAPConnectionFactoryTestCase extends SdkTestCase {
    /**
     * The number of test iterations for unit tests which attempt to expose
     * potential race conditions. Manual testing has gone up to 10000
     * iterations.
     */
    private static final int ITERATIONS = 100;

    /** Test timeout for tests which need to wait for network events. */
    private static final long TEST_TIMEOUT = 30L;

    /*
     * It is usually quite a bad code smell to share state between unit tests.
     * However, in this case we want to re-use the same factories and listeners
     * in order to avoid shutting down and restarting the transport for each
     * iteration.
     */

    private final Semaphore abandonLatch = new Semaphore(0);
    private final Semaphore bindLatch = new Semaphore(0);
    private final Semaphore closeLatch = new Semaphore(0);
    private final Semaphore connectLatch = new Semaphore(0);
    private final Semaphore searchLatch = new Semaphore(0);
    private final AtomicReference<LDAPClientContext> context = new AtomicReference<>();
    private final LDAPListener server = createServer();
    private final InetSocketAddress socketAddress = server.getSocketAddress();
    public final ConnectionFactory factory = new LDAPConnectionFactory(socketAddress.getHostName(),
            socketAddress.getPort(), Options.defaultOptions().set(REQUEST_TIMEOUT, duration("1 ms")));
    private final ConnectionFactory pool = Connections.newFixedConnectionPool(factory, 10);
    private volatile ServerConnection<Integer> serverConnection;

    @AfterClass
    public void tearDown() {
        pool.close();
        factory.close();
        server.close();
    }

    @Test(description = "OPENDJ-1197")
    public void testClientSideConnectTimeout() throws Exception {
        // Use an non-local unreachable network address.
        final ConnectionFactory factory = new LDAPConnectionFactory("10.20.30.40", 1389,
            Options.defaultOptions().set(CONNECT_TIMEOUT, duration("1 ms")));
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                final PromiseImpl<LdapException, NeverThrowsException> promise = PromiseImpl.create();
                final Promise<? extends Connection, LdapException> connectionPromise = factory.getConnectionAsync();
                connectionPromise.thenOnException(getExceptionHandler(promise));

                ConnectionException e = (ConnectionException) promise.getOrThrow(TEST_TIMEOUT, TimeUnit.SECONDS);
                assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_CONNECT_ERROR);
                // Wait for the connect to timeout.
                try {
                    connectionPromise.getOrThrow(TEST_TIMEOUT, TimeUnit.SECONDS);
                    fail("The connect request succeeded unexpectedly");
                } catch (ConnectionException ce) {
                    assertThat(ce.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_CONNECT_ERROR);
                }
            }
        } finally {
            factory.close();
        }
    }

    /**
     * Unit test for OPENDJ-1247: a locally timed out bind request will leave a
     * connection in an invalid state since a bind (or startTLS) is in progress
     * and no other operations can be performed. Therefore, a timeout should
     * cause the connection to become invalid and an appropriate connection
     * event sent. In addition, no abandon request should be sent.
     */
    @Test
    public void testClientSideTimeoutForBindRequest() throws Exception {
        resetState();
        registerBindEvent();
        registerCloseEvent();

        final Connection connection = factory.getConnection();
        try {
            waitForConnect();
            final MockConnectionEventListener listener = new MockConnectionEventListener();
            connection.addConnectionEventListener(listener);
            final PromiseImpl<LdapException, NeverThrowsException> promise = PromiseImpl.create();
            final LdapPromise<BindResult> bindPromise = connection.bindAsync(newSimpleBindRequest());
            bindPromise.thenOnException(getExceptionHandler(promise));
            waitForBind();

            TimeoutResultException e = (TimeoutResultException) promise.getOrThrow(TEST_TIMEOUT, TimeUnit.SECONDS);
            verifyResultCodeIsClientSideTimeout(e);

            // Wait for the request to timeout.
            try {
                bindPromise.getOrThrow(TEST_TIMEOUT, TimeUnit.SECONDS);
                fail("The bind request succeeded unexpectedly");
            } catch (TimeoutResultException te) {
                verifyResultCodeIsClientSideTimeout(te);
            }

            /*
             * The connection should no longer be valid, the event listener
             * should have been notified, but no abandon should have been sent.
             */
            listener.awaitError(TEST_TIMEOUT, TimeUnit.SECONDS);
            assertThat(connection.isValid()).isFalse();
            verifyResultCodeIsClientSideTimeout(listener.getError());
            connection.close();
            waitForClose();
            verifyNoAbandonSent();
        } finally {
            connection.close();
        }
    }

    /**
     * Unit test for OPENDJ-1247: as per previous test, except this time verify
     * that the connection failure removes the connection from a connection
     * pool.
     */
    @Test
    public void testClientSideTimeoutForBindRequestInConnectionPool() throws Exception {
        resetState();
        registerBindEvent();
        registerCloseEvent();

        for (int i = 0; i < ITERATIONS; i++) {
            final Connection connection = pool.getConnection();
            try {
                waitForConnect();
                final MockConnectionEventListener listener = new MockConnectionEventListener();
                connection.addConnectionEventListener(listener);

                // Now bind with timeout.
                final PromiseImpl<LdapException, NeverThrowsException> promise = PromiseImpl.create();
                final LdapPromise<BindResult> bindPromise = connection.bindAsync(newSimpleBindRequest());
                bindPromise.thenOnException(getExceptionHandler(promise));
                waitForBind();

                // Wait for the request to timeout and check the handler was invoked.
                TimeoutResultException e = (TimeoutResultException) promise.getOrThrow(5, TimeUnit.SECONDS);
                verifyResultCodeIsClientSideTimeout(e);

                // Now check the promise was completed as expected.
                try {
                    bindPromise.getOrThrow(5, TimeUnit.SECONDS);
                    fail("The bind request succeeded unexpectedly");
                } catch (TimeoutResultException te) {
                    verifyResultCodeIsClientSideTimeout(te);
                }

                /*
                 * The connection should no longer be valid, the event listener
                 * should have been notified, but no abandon should have been
                 * sent.
                 */
                listener.awaitError(TEST_TIMEOUT, TimeUnit.SECONDS);
                assertThat(connection.isValid()).isFalse();
                verifyResultCodeIsClientSideTimeout(listener.getError());
                connection.close();
                waitForClose();
                verifyNoAbandonSent();
            } finally {
                connection.close();
            }
        }
    }


    /**
     * Unit test for OPENDJ-1247: a locally timed out request which is not a
     * bind or startTLS should result in a client side timeout error, but the
     * connection should remain valid. In addition, no abandon request should be
     * sent.
     */
    @Test
    public void testClientSideTimeoutForSearchRequest() throws Exception {
        resetState();
        registerSearchEvent();
        registerAbandonEvent();

        for (int i = 0; i < ITERATIONS; i++) {
            final Connection connection = factory.getConnection();
            try {
                waitForConnect();
                final ConnectionEventListener listener = mock(ConnectionEventListener.class);
                connection.addConnectionEventListener(listener);
                final PromiseImpl<LdapException, NeverThrowsException> promise = PromiseImpl.create();
                final LdapPromise<SearchResultEntry> connectionPromise =
                        connection.readEntryAsync(DN.valueOf("cn=test"), null);
                connectionPromise.thenOnException(getExceptionHandler(promise));
                waitForSearch();

                LdapException e = promise.getOrThrow(TEST_TIMEOUT, TimeUnit.SECONDS);
                verifyResultCodeIsClientSideTimeout(e);
                // Wait for the request to timeout.
                try {
                    connectionPromise.getOrThrow(TEST_TIMEOUT, TimeUnit.SECONDS);
                    fail("The search request succeeded unexpectedly");
                } catch (TimeoutResultException te) {
                    verifyResultCodeIsClientSideTimeout(te);
                }

                // The connection should still be valid.
                assertThat(connection.isValid()).isTrue();
                verifyZeroInteractions(listener);
                /*
                 * FIXME: The search should have been abandoned (see comment in
                 * LDAPConnection for explanation).
                 */
                // waitForAbandon();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    public void testCreateLDAPConnectionFactory() throws Exception {
        // test no exception is thrown, which means transport provider is correctly loaded
        InetSocketAddress socketAddress = findFreeSocketAddress();
        LDAPConnectionFactory factory = new LDAPConnectionFactory(socketAddress.getHostName(), socketAddress.getPort());
        factory.close();
    }

    @Test(expectedExceptions = { ProviderNotFoundException.class },
            expectedExceptionsMessageRegExp = "^The requested provider 'unknown' .*")
    public void testCreateLDAPConnectionFactoryFailureProviderNotFound() throws Exception {
        Options options = Options.defaultOptions().set(TRANSPORT_PROVIDER, "unknown");
        InetSocketAddress socketAddress = findFreeSocketAddress();
        LDAPConnectionFactory factory = new LDAPConnectionFactory(socketAddress.getHostName(),
                socketAddress.getPort(), options);
        factory.close();
    }

    @Test
    public void testCreateLDAPConnectionFactoryWithCustomClassLoader() throws Exception {
        // test no exception is thrown, which means transport provider is correctly loaded
        Options options = Options.defaultOptions()
                                 .set(TRANSPORT_PROVIDER_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
        InetSocketAddress socketAddress = findFreeSocketAddress();
        LDAPConnectionFactory factory = new LDAPConnectionFactory(socketAddress.getHostName(),
                socketAddress.getPort(), options);
        factory.close();
    }

    /**
     * This unit test exposes the bug raised in issue OPENDJ-1156: NPE in
     * ReferenceCountedObject after shutting down directory.
     */
    @Test
    public void testResourceManagement() throws Exception {
        resetState();

        for (int i = 0; i < ITERATIONS; i++) {
            final Connection connection = factory.getConnection();
            try {
                waitForConnect();
                final MockConnectionEventListener listener = new MockConnectionEventListener();
                connection.addConnectionEventListener(listener);

                // Perform remote disconnect which will trigger a client side connection error.
                context.get().disconnect();

                // Wait for the error notification to reach the client.
                listener.awaitError(TEST_TIMEOUT, TimeUnit.SECONDS);
            } finally {
                connection.close();
            }
        }
    }

    private LDAPListener createServer() {
        try {
            return new LDAPListener(findFreeSocketAddress(),
                    new ServerConnectionFactory<LDAPClientContext, Integer>() {
                        @Override
                        public ServerConnection<Integer> handleAccept(
                                final LDAPClientContext clientContext) throws LdapException {
                            context.set(clientContext);
                            connectLatch.release();
                            return serverConnection;
                        }
                    });
        } catch (IOException e) {
            fail("Unable to create LDAP listener", e);
            return null;
        }
    }

    private ExceptionHandler<LdapException> getExceptionHandler(
            final PromiseImpl<LdapException, NeverThrowsException> promise) {
        return new ExceptionHandler<LdapException>() {
            @Override
            public void handleException(LdapException exception) {
                promise.handleResult(exception);
            }
        };
    }

    private Stubber notifyEvent(final Semaphore latch) {
        return doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                latch.release();
                return null;
            }
        });
    }

    private void registerAbandonEvent() {
        notifyEvent(abandonLatch).when(serverConnection).handleAbandon(any(Integer.class),
                any(AbandonRequest.class));
    }

    private void registerBindEvent() {
        notifyEvent(bindLatch).when(serverConnection).handleBind(any(Integer.class), anyInt(),
                any(BindRequest.class), any(IntermediateResponseHandler.class),
                any(LdapResultHandler.class));
    }

    private void registerCloseEvent() {
        notifyEvent(closeLatch).when(serverConnection).handleConnectionClosed(any(Integer.class),
                any(UnbindRequest.class));
    }

    private void registerSearchEvent() {
        notifyEvent(searchLatch).when(serverConnection).handleSearch(any(Integer.class), any(SearchRequest.class),
            any(IntermediateResponseHandler.class), any(SearchResultHandler.class), any(LdapResultHandler.class));
    }

    private void resetState() {
        connectLatch.drainPermits();
        abandonLatch.drainPermits();
        bindLatch.drainPermits();
        searchLatch.drainPermits();
        closeLatch.drainPermits();
        context.set(null);
        serverConnection = mock(ServerConnection.class);
    }

    private void verifyNoAbandonSent() {
        verify(serverConnection, never()).handleAbandon(any(Integer.class),
                any(AbandonRequest.class));
    }

    private void verifyResultCodeIsClientSideTimeout(LdapException error) {
        assertThat(error.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_TIMEOUT);
    }

    @SuppressWarnings("unused")
    private void waitForAbandon() throws InterruptedException {
        waitForEvent(abandonLatch);
    }

    private void waitForBind() throws InterruptedException {
        waitForEvent(bindLatch);
    }

    private void waitForClose() throws InterruptedException {
        waitForEvent(closeLatch);
    }

    private void waitForConnect() throws InterruptedException {
        waitForEvent(connectLatch);
    }

    private void waitForEvent(final Semaphore latch) throws InterruptedException {
        assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    private void waitForSearch() throws InterruptedException {
        waitForEvent(searchLatch);
    }
}
