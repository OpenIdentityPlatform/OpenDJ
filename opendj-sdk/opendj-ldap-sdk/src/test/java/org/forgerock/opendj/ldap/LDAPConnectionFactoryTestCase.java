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
 *     Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.closeSilently;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.TestCaseUtils.findFreeSocketAddress;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.testng.annotations.Test;

/**
 * Tests the {@link LDAPConnectionFactory} class.
 */
@SuppressWarnings({ "javadoc", "unchecked" })
public class LDAPConnectionFactoryTestCase extends SdkTestCase {
    // Test timeout for tests which need to wait for network events.
    private static final long TEST_TIMEOUT = 30L;

    /**
     * Unit test for OPENDJ-1247: a locally timed out bind request will leave a
     * connection in an invalid state since a bind (or startTLS) is in progress
     * and no other operations can be performed. Therefore, a timeout should
     * cause the connection to become invalid and an appropriate connection
     * event sent. In addition, no abandon request should be sent.
     */
    @Test
    public void testClientSideTimeoutForBindRequest() throws Exception {
        final AtomicReference<LDAPClientContext> context = new AtomicReference<LDAPClientContext>();
        final Semaphore latch = new Semaphore(0);

        // The server connection should receive a bind, but no abandon request.
        final ServerConnection<Integer> serverConnection = mock(ServerConnection.class);
        release(latch).when(serverConnection).handleBind(any(Integer.class), anyInt(),
                any(BindRequest.class), any(IntermediateResponseHandler.class),
                any(ResultHandler.class));
        release(latch).when(serverConnection).handleConnectionClosed(any(Integer.class),
                any(UnbindRequest.class));

        final LDAPListener server = createServer(latch, context, serverConnection);
        final ConnectionFactory factory =
                new LDAPConnectionFactory(server.getSocketAddress(), new LDAPOptions().setTimeout(
                        1, TimeUnit.MILLISECONDS));
        Connection connection = null;
        try {
            // Connect to the server.
            connection = factory.getConnection();

            // Wait for the server to accept the connection.
            assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

            /*
             * A bind request timeout should cause the connection to fail, so
             * ensure that event listeners are fired.
             */
            final ConnectionEventListener listener = mock(ConnectionEventListener.class);
            connection.addConnectionEventListener(listener);

            final ResultHandler<BindResult> handler = mock(ResultHandler.class);
            final FutureResult<BindResult> future =
                    connection.bindAsync(newSimpleBindRequest(), null, handler);

            // Wait for the server to receive the bind request.
            assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

            // Wait for the request to timeout.
            try {
                future.get(TEST_TIMEOUT, TimeUnit.SECONDS);
                fail("The bind request succeeded unexpectedly");
            } catch (TimeoutResultException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_TIMEOUT);
                verify(handler).handleErrorResult(same(e));

                // The connection should no longer be valid.
                ArgumentCaptor<ErrorResultException> capturedError =
                        ArgumentCaptor.forClass(ErrorResultException.class);
                verify(listener).handleConnectionError(eq(false), capturedError.capture());
                assertThat(capturedError.getValue().getResult().getResultCode()).isEqualTo(
                        ResultCode.CLIENT_SIDE_TIMEOUT);
                assertThat(connection.isValid()).isFalse();
                connection.close();

                // Wait for the server to receive the close request.
                assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

                /*
                 * Check that the only interactions were the bind and the close
                 * and specifically there was no abandon request.
                 */
                verify(serverConnection).handleBind(any(Integer.class), eq(3),
                        any(BindRequest.class), any(IntermediateResponseHandler.class),
                        any(ResultHandler.class));
                verify(serverConnection).handleConnectionClosed(any(Integer.class),
                        any(UnbindRequest.class));
                verifyNoMoreInteractions(serverConnection);
            }
        } finally {
            closeSilently(connection);
            factory.close();
            server.close();
        }
    }

    /**
     * Unit test for OPENDJ-1247: as per previous test, except this time verify
     * that the connection failure removes the connection from a connection
     * pool.
     */
    @Test
    public void testClientSideTimeoutForBindRequestInConnectionPool() throws Exception {
        final AtomicReference<LDAPClientContext> context = new AtomicReference<LDAPClientContext>();
        final Semaphore latch = new Semaphore(0);

        // The server connection should receive a bind, but no abandon request.
        final ServerConnection<Integer> serverConnection = mock(ServerConnection.class);
        release(latch).when(serverConnection).handleBind(any(Integer.class), anyInt(),
                any(BindRequest.class), any(IntermediateResponseHandler.class),
                any(ResultHandler.class));
        release(latch).when(serverConnection).handleConnectionClosed(any(Integer.class),
                any(UnbindRequest.class));

        final LDAPListener server = createServer(latch, context, serverConnection);
        final ConnectionFactory factory =
                Connections.newFixedConnectionPool(
                        new LDAPConnectionFactory(server.getSocketAddress(), new LDAPOptions()
                                .setTimeout(1, TimeUnit.MILLISECONDS)), 10);
        Connection connection = null;
        try {
            // Get pooled connection to the server.
            connection = factory.getConnection();

            // Wait for the server to accept the connection.
            assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

            /*
             * Sanity check: close the connection and reopen. There should be no
             * interactions with the server due to the pool.
             */
            connection.close();
            connection = factory.getConnection();
            verifyNoMoreInteractions(serverConnection);

            // Now bind with timeout.
            final ResultHandler<BindResult> handler = mock(ResultHandler.class);
            final FutureResult<BindResult> future =
                    connection.bindAsync(newSimpleBindRequest(), null, handler);

            // Wait for the server to receive the bind request.
            assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

            // Wait for the request to timeout.
            try {
                future.get(TEST_TIMEOUT, TimeUnit.SECONDS);
                fail("The bind request succeeded unexpectedly");
            } catch (TimeoutResultException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_TIMEOUT);
                verify(handler).handleErrorResult(same(e));

                // The connection should no longer be valid.
                assertThat(connection.isValid()).isFalse();
                connection.close();

                // Wait for the server to receive the close request.
                assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

                /*
                 * Check that the only interactions were the bind and the close
                 * and specifically there was no abandon request.
                 */
                verify(serverConnection).handleBind(any(Integer.class), eq(3),
                        any(BindRequest.class), any(IntermediateResponseHandler.class),
                        any(ResultHandler.class));
                verify(serverConnection).handleConnectionClosed(any(Integer.class),
                        any(UnbindRequest.class));
                verifyNoMoreInteractions(serverConnection);

                // Now get another connection. This time we should reconnect to the server.
                connection = factory.getConnection();

                // Wait for the server to accept the connection.
                assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            closeSilently(connection);
            factory.close();
            server.close();
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
        final AtomicReference<LDAPClientContext> context = new AtomicReference<LDAPClientContext>();
        final Semaphore latch = new Semaphore(0);

        // The server connection should receive a search and then an abandon.
        final ServerConnection<Integer> serverConnection = mock(ServerConnection.class);
        release(latch).when(serverConnection).handleSearch(any(Integer.class),
                any(SearchRequest.class), any(IntermediateResponseHandler.class),
                any(SearchResultHandler.class));
        release(latch).when(serverConnection).handleAbandon(any(Integer.class),
                any(AbandonRequest.class));

        final LDAPListener server = createServer(latch, context, serverConnection);
        final ConnectionFactory factory =
                new LDAPConnectionFactory(server.getSocketAddress(), new LDAPOptions().setTimeout(
                        1, TimeUnit.MILLISECONDS));
        Connection connection = null;
        try {
            // Connect to the server.
            connection = factory.getConnection();

            // Wait for the server to accept the connection.
            assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

            /*
             * A search request timeout should not cause the connection to fail,
             * so ensure that event listeners are not fired.
             */
            final ConnectionEventListener listener = mock(ConnectionEventListener.class);
            connection.addConnectionEventListener(listener);

            final ResultHandler<SearchResultEntry> handler = mock(ResultHandler.class);
            final FutureResult<SearchResultEntry> future =
                    connection.readEntryAsync(DN.valueOf("cn=test"), null, handler);

            // Wait for the server to receive the search request.
            assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

            // Wait for the request to timeout.
            try {
                future.get(TEST_TIMEOUT, TimeUnit.SECONDS);
                fail("The search request succeeded unexpectedly");
            } catch (TimeoutResultException e) {
                assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_TIMEOUT);
                verify(handler).handleErrorResult(same(e));

                // The connection should still be valid.
                verifyZeroInteractions(listener);
                assertThat(connection.isValid()).isTrue();

                // Wait for the server to receive the abandon request.
                assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            closeSilently(connection);
            factory.close();
            server.close();
        }
    }

    /**
     * This unit test exposes the bug raised in issue OPENDJ-1156: NPE in
     * ReferenceCountedObject after shutting down directory.
     */
    @Test
    public void testResourceManagement() throws Exception {
        final AtomicReference<LDAPClientContext> context = new AtomicReference<LDAPClientContext>();
        final Semaphore latch = new Semaphore(0);
        final LDAPListener server = createServer(latch, context, mock(ServerConnection.class));
        final ConnectionFactory factory = new LDAPConnectionFactory(server.getSocketAddress());
        try {
            for (int i = 0; i < 100; i++) {
                // Connect to the server.
                final Connection connection = factory.getConnection();
                try {
                    // Wait for the server to accept the connection.
                    assertThat(latch.tryAcquire(TEST_TIMEOUT, TimeUnit.SECONDS)).isTrue();

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
        } finally {
            factory.close();
            server.close();
        }
    }

    private LDAPListener createServer(final Semaphore latch,
            final AtomicReference<LDAPClientContext> context,
            final ServerConnection<Integer> serverConnection) throws IOException {
        return new LDAPListener(findFreeSocketAddress(),
                new ServerConnectionFactory<LDAPClientContext, Integer>() {
                    @Override
                    public ServerConnection<Integer> handleAccept(
                            final LDAPClientContext clientContext) throws ErrorResultException {
                        context.set(clientContext);
                        latch.release();
                        return serverConnection;
                    }
                });
    }

    private Stubber release(final Semaphore latch) {
        return doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                latch.release();
                return null;
            }
        });
    }
}
