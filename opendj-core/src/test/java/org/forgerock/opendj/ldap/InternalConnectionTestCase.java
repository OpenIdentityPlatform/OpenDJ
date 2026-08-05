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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

/** Tests the connection life cycle of the pseudo-connection returned by {@link Connections#newInternalConnection}. */
@SuppressWarnings("javadoc")
public class InternalConnectionTestCase extends SdkTestCase {

    @SuppressWarnings("unchecked")
    private static ServerConnection<Integer> mockServerConnection() {
        return mock(ServerConnection.class);
    }

    /** Asserts that the close was forwarded exactly once, carrying the very request the caller provided. */
    private static void verifyClosedOnce(final ServerConnection<Integer> serverConnection,
            final UnbindRequest request) {
        verify(serverConnection, times(1)).handleConnectionClosed(anyInt(), same(request));
    }

    /** Asserts that the close was forwarded exactly once, for callers which let {@code close()} build the request. */
    private static void verifyClosedOnce(final ServerConnection<Integer> serverConnection) {
        verify(serverConnection, times(1)).handleConnectionClosed(anyInt(), any(UnbindRequest.class));
    }

    @Test
    public void testCloseNotifiesListeners() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        final UnbindRequest request = Requests.newUnbindRequest();
        connection.close(request, null);

        assertThat(listener.getInvocationCount()).isEqualTo(1);
        verifyClosedOnce(serverConnection, request);
    }

    @Test
    public void testCloseIsIdempotent() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        final UnbindRequest request = Requests.newUnbindRequest();
        connection.close(request, null);
        connection.close(request, null);
        connection.close();

        assertThat(listener.getInvocationCount()).isEqualTo(1);
        verifyClosedOnce(serverConnection, request);
        verifyClosedOnce(serverConnection);
    }

    @Test
    public void testRemovedListenerIsNotNotified() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);
        connection.removeConnectionEventListener(listener);

        connection.close();

        assertThat(listener.getInvocationCount()).isEqualTo(0);
        verifyClosedOnce(serverConnection);
    }

    /** A listener registered after the connection was closed is told about it straight away. */
    @Test
    public void testListenerAddedAfterCloseIsNotifiedImmediately() throws Exception {
        final Connection connection = Connections.newInternalConnection(mockServerConnection());
        connection.close();

        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        assertThat(listener.getInvocationCount()).isEqualTo(1);
    }

    @Test
    public void testConnectionStateReflectsClose() throws Exception {
        final Connection connection = Connections.newInternalConnection(mockServerConnection());
        assertThat(connection.isClosed()).isFalse();
        assertThat(connection.isValid()).isTrue();

        connection.close();

        assertThat(connection.isClosed()).isTrue();
        assertThat(connection.isValid()).isFalse();
    }

    /**
     * De-registering a listener from within its own close notification is a common idiom, and it must neither skip
     * the remaining listeners nor break the hand-over to the server connection.
     */
    @Test
    public void testSelfDeregisteringListenerDoesNotStopNotification() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final ConnectionEventListener selfDeregistering = mock(ConnectionEventListener.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) {
                connection.removeConnectionEventListener(selfDeregistering);
                return null;
            }
        }).when(selfDeregistering).handleConnectionClosed();
        connection.addConnectionEventListener(selfDeregistering);

        // Three more listeners: with a list which does not tolerate mutation while being iterated, two of them
        // would go unnotified and the third would raise a ConcurrentModificationException out of close().
        final List<MockConnectionEventListener> listeners = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final MockConnectionEventListener listener = new MockConnectionEventListener();
            listeners.add(listener);
            connection.addConnectionEventListener(listener);
        }

        final UnbindRequest request = Requests.newUnbindRequest();
        connection.close(request, null);

        verify(selfDeregistering, times(1)).handleConnectionClosed();
        for (final MockConnectionEventListener listener : listeners) {
            assertThat(listener.getInvocationCount()).isEqualTo(1);
        }
        verifyClosedOnce(serverConnection, request);
    }

    /**
     * A misbehaving listener must not prevent the server connection from releasing its resources: the forward is the
     * only cleanup an internal connection performs, and a second {@code close()} would be a no-op.
     */
    @Test
    public void testListenerFailureDoesNotPreventServerClose() throws Exception {
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        doThrow(new IllegalStateException("listener failure")).when(listener).handleConnectionClosed();
        connection.addConnectionEventListener(listener);

        final UnbindRequest request = Requests.newUnbindRequest();
        try {
            connection.close(request, null);
            fail("close() should have passed on the listener failure");
        } catch (final IllegalStateException expected) {
            // Expected: no notification site in the SDK guards individual listener callbacks.
        }

        verifyClosedOnce(serverConnection, request);
        assertThat(connection.isClosed()).isTrue();
    }

    /** Concurrent closes notify the listeners and reach the server connection exactly once. */
    @Test
    public void testConcurrentCloseNotifiesAndForwardsOnce() throws Exception {
        final int threadCount = 16;
        final ServerConnection<Integer> serverConnection = mockServerConnection();
        final Connection connection = Connections.newInternalConnection(serverConnection);
        final MockConnectionEventListener listener = new MockConnectionEventListener();
        connection.addConnectionEventListener(listener);

        final CountDownLatch startLatch = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            final List<Future<Void>> futures = new ArrayList<>(threadCount);
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        startLatch.await();
                        connection.close();
                        return null;
                    }
                }));
            }
            startLatch.countDown();
            for (final Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(listener.getInvocationCount()).isEqualTo(1);
        verifyClosedOnce(serverConnection);
    }
}
