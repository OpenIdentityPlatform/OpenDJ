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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.Connections.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the connection pool implementation..
 */
@SuppressWarnings("javadoc")
public class ConnectionPoolTestCase extends SdkTestCase {

    /**
     * A connection event listener registered against a pooled connection should
     * be notified when the pooled connection is closed, NOT when the underlying
     * connection is closed.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionEventListenerClose() throws Exception {
        final Connection pooledConnection = mock(Connection.class);
        when(pooledConnection.isValid()).thenReturn(true);
        final ConnectionFactory factory = mockConnectionFactory(pooledConnection);
        final ConnectionPool pool = newFixedConnectionPool(factory, 1);
        final Connection connection = pool.getConnection();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        connection.addConnectionEventListener(listener);
        connection.close();

        verify(listener).handleConnectionClosed();
        verify(listener, times(0)).handleConnectionError(anyBoolean(), any(LdapException.class));
        verify(listener, times(0)).handleUnsolicitedNotification(any(ExtendedResult.class));

        // Get a connection again and make sure that the listener is no longer invoked.
        pool.getConnection().close();
        verifyNoMoreInteractions(listener);
    }

    /**
     * A connection event listener registered against a pooled connection should
     * be notified whenever an error occurs on the underlying connection.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionEventListenerError() throws Exception {
        final List<ConnectionEventListener> listeners = new LinkedList<>();
        final Connection mockConnection = mockConnection(listeners);
        final ConnectionFactory factory = mockConnectionFactory(mockConnection);
        final ConnectionPool pool = newFixedConnectionPool(factory, 1);
        final Connection connection = pool.getConnection();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        connection.addConnectionEventListener(listener);
        assertThat(listeners).hasSize(1);
        listeners.get(0).handleConnectionError(false,
                newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN));
        verify(listener, times(0)).handleConnectionClosed();
        verify(listener).handleConnectionError(eq(false), isA(ConnectionException.class));
        verify(listener, times(0)).handleUnsolicitedNotification(any(ExtendedResult.class));
        connection.close();
        assertThat(listeners).hasSize(0);
    }

    /**
     * A connection event listener registered against a pooled connection should
     * be notified whenever an unsolicited notification is received on the
     * underlying connection.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionEventListenerUnsolicitedNotification() throws Exception {
        final List<ConnectionEventListener> listeners = new LinkedList<>();
        final Connection mockConnection = mockConnection(listeners);
        final ConnectionFactory factory = mockConnectionFactory(mockConnection);
        final ConnectionPool pool = newFixedConnectionPool(factory, 1);
        final Connection connection = pool.getConnection();
        final ConnectionEventListener listener = mock(ConnectionEventListener.class);
        connection.addConnectionEventListener(listener);
        assertThat(listeners).hasSize(1);
        listeners.get(0).handleUnsolicitedNotification(
                Responses.newGenericExtendedResult(ResultCode.OTHER));
        verify(listener, times(0)).handleConnectionClosed();
        verify(listener, times(0)).handleConnectionError(anyBoolean(), any(LdapException.class));
        verify(listener).handleUnsolicitedNotification(any(ExtendedResult.class));
        connection.close();
        assertThat(listeners).hasSize(0);
    }

    /**
     * Test basic pool functionality:
     * <ul>
     * <li>create a pool of size 2
     * <li>get 2 connections and make sure that they are usable
     * <li>close the pooled connections and check that the underlying
     * connections are not closed
     * <li>close the pool and check that underlying connections are closed.
     * </ul>
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionLifeCycle() throws Exception {
        // Setup.
        final BindRequest bind1 =
                Requests.newSimpleBindRequest("cn=test1", "password".toCharArray());
        final Connection connection1 = mock(Connection.class);
        when(connection1.bind(bind1)).thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        when(connection1.isValid()).thenReturn(true);

        final BindRequest bind2 =
                Requests.newSimpleBindRequest("cn=test2", "password".toCharArray());
        final Connection connection2 = mock(Connection.class);
        when(connection2.bind(bind2)).thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        when(connection2.isValid()).thenReturn(true);

        final ConnectionFactory factory = mockConnectionFactory(connection1, connection2);
        final ConnectionPool pool = Connections.newFixedConnectionPool(factory, 2);

        verifyZeroInteractions(factory);
        verifyZeroInteractions(connection1);
        verifyZeroInteractions(connection2);

        /*
         * Obtain connections and verify that the correct underlying connection
         * is used. We can check the returned connection directly since it is a
         * wrapper, so we'll route a bind request instead and check that the
         * underlying connection got it.
         */
        final Connection pc1 = pool.getConnection();
        assertThat(pc1.bind(bind1).getResultCode()).isEqualTo(ResultCode.SUCCESS);
        verify(factory, times(1)).getConnection();
        verify(connection1).bind(bind1);
        verifyZeroInteractions(connection2);

        final Connection pc2 = pool.getConnection();
        assertThat(pc2.bind(bind2).getResultCode()).isEqualTo(ResultCode.SUCCESS);
        verify(factory, times(2)).getConnection();
        verifyNoMoreInteractions(connection1);
        verify(connection2).bind(bind2);

        // Release pooled connections (should not close underlying connection).
        pc1.close();
        assertThat(pc1.isValid()).isFalse();
        assertThat(pc1.isClosed()).isTrue();
        verify(connection1, times(0)).close();

        pc2.close();
        assertThat(pc2.isValid()).isFalse();
        assertThat(pc2.isClosed()).isTrue();
        verify(connection2, times(0)).close();

        // Close the pool (should close underlying connections).
        pool.close();
        verify(connection1).close();
        verify(connection2).close();
    }

    /**
     * Test behavior of pool at capacity.
     * <ul>
     * <li>create a pool of size 2
     * <li>get 2 connections and make sure that they are usable
     * <li>attempt to get third connection asynchronously and verify that no
     * connection was available
     * <li>release (close) a pooled connection
     * <li>verify that third attempt now completes.
     * </ul>
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testGetConnectionAtCapacity() throws Exception {
        // Setup.
        final Connection connection1 = mock(Connection.class);
        when(connection1.isValid()).thenReturn(true);

        final BindRequest bind2 =
                Requests.newSimpleBindRequest("cn=test2", "password".toCharArray());
        final Connection connection2 = mock(Connection.class);
        when(connection2.bind(bind2)).thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        when(connection2.isValid()).thenReturn(true);

        final ConnectionFactory factory = mockConnectionFactory(connection1, connection2);
        final ConnectionPool pool = Connections.newFixedConnectionPool(factory, 2);

        // Fully utilize the pool.
        final Connection pc1 = pool.getConnection();
        final Connection pc2 = pool.getConnection();

        /*
         * Grab another connection and check that this attempt blocks (if there
         * is a connection available immediately then the promise will be
         * completed immediately).
         */
        final Promise<? extends Connection, LdapException> promise = pool.getConnectionAsync();
        assertThat(promise.isDone()).isFalse();

        // Release a connection and verify that it is immediately redeemed by the promise.
        pc2.close();
        assertThat(promise.isDone()).isTrue();

        // Check that returned connection routes request to released connection.
        final Connection pc3 = promise.get();
        assertThat(pc3.bind(bind2).getResultCode()).isEqualTo(ResultCode.SUCCESS);
        verify(factory, times(2)).getConnection();
        verify(connection2).bind(bind2);

        pc1.close();
        pc2.close();
        pc3.close();
        pool.close();
    }

    /**
     * Verifies that stale connections which have become invalid while in use
     * are not placed back in the pool after being closed.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testSkipStaleConnectionsOnClose() throws Exception {
        // Setup.
        final Connection connection1 = mock(Connection.class);
        when(connection1.isValid()).thenReturn(true);

        final BindRequest bind2 =
                Requests.newSimpleBindRequest("cn=test2", "password".toCharArray());
        final Connection connection2 = mock(Connection.class);
        when(connection2.bind(bind2)).thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        when(connection2.isValid()).thenReturn(true);

        final ConnectionFactory factory = mockConnectionFactory(connection1, connection2);
        final ConnectionPool pool = Connections.newFixedConnectionPool(factory, 2);

        /*
         * Simulate remote disconnect of connection1 while application is using
         * the pooled connection. The pooled connection should be recycled
         * immediately on release.
         */
        final Connection pc1 = pool.getConnection();
        when(connection1.isValid()).thenReturn(false);
        assertThat(connection1.isValid()).isFalse();
        assertThat(pc1.isValid()).isFalse();
        pc1.close();
        assertThat(connection1.isValid()).isFalse();
        verify(connection1).close();

        // Now get another connection and check that it is ok.
        final Connection pc2 = pool.getConnection();
        assertThat(pc2.isValid()).isTrue();
        assertThat(pc2.bind(bind2).getResultCode()).isEqualTo(ResultCode.SUCCESS);
        verify(factory, times(2)).getConnection();
        verify(connection2).bind(bind2);

        pc2.close();
        pool.close();
    }

    /**
     * Verifies that stale connections which have become invalid while cached in
     * the internal pool are not returned to the caller. This may occur when an
     * idle pooled connection is disconnect by the remote server after a
     * timeout. The connection pool must detect that the pooled connection is
     * invalid in order to avoid returning it to the caller.
     * <p>
     * See issue OPENDJ-590.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testSkipStaleConnectionsOnGet() throws Exception {
        // Setup.
        final Connection connection1 = mock(Connection.class);
        when(connection1.isValid()).thenReturn(true);

        final BindRequest bind2 =
                Requests.newSimpleBindRequest("cn=test2", "password".toCharArray());
        final Connection connection2 = mock(Connection.class);
        when(connection2.bind(bind2)).thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        when(connection2.isValid()).thenReturn(true);

        final ConnectionFactory factory = mockConnectionFactory(connection1, connection2);
        final ConnectionPool pool = Connections.newFixedConnectionPool(factory, 2);

        // Get and release a single connection.
        pool.getConnection().close();

        /*
         * Simulate remote disconnect of connection1. The next connection
         * attempt should return connection2.
         */
        when(connection1.isValid()).thenReturn(false);
        assertThat(connection1.isValid()).isFalse();
        assertThat(connection2.isValid()).isTrue();
        final Connection pc = pool.getConnection();

        // Check that returned connection routes request to connection2.
        assertThat(pc.isValid()).isTrue();
        verify(connection1).close();
        assertThat(pc.bind(bind2).getResultCode()).isEqualTo(ResultCode.SUCCESS);
        verify(factory, times(2)).getConnection();
        verify(connection2).bind(bind2);

        pc.close();
        pool.close();
    }

    /**
     * Verifies that a fully allocated pool whose connections are stale due to
     * idle timeouts still allocates new connections.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testSkipStaleConnectionsOnGetWhenAtCapacity() throws Exception {
        // Setup.
        final Connection connection1 = mock(Connection.class);
        when(connection1.isValid()).thenReturn(true);

        final Connection connection2 = mock(Connection.class);
        when(connection2.isValid()).thenReturn(true);

        final BindRequest bind3 =
                Requests.newSimpleBindRequest("cn=test2", "password".toCharArray());
        final Connection connection3 = mock(Connection.class);
        when(connection3.bind(bind3)).thenReturn(Responses.newBindResult(ResultCode.SUCCESS));
        when(connection3.isValid()).thenReturn(true);

        final ConnectionFactory factory =
                mockConnectionFactory(connection1, connection2, connection3);
        final ConnectionPool pool = Connections.newFixedConnectionPool(factory, 2);

        // Fully allocate the pool.
        final Connection pc1 = pool.getConnection();
        final Connection pc2 = pool.getConnection();
        pc1.close();
        pc2.close();

        /*
         * Simulate remote disconnect of connection1 and connection2. The next
         * connection attempt should return connection3.
         */
        when(connection1.isValid()).thenReturn(false);
        when(connection2.isValid()).thenReturn(false);
        final Connection pc3 = pool.getConnection();

        // Check that returned connection routes request to connection3.
        assertThat(pc3.isValid()).isTrue();
        verify(connection1).close();
        verify(connection2).close();
        assertThat(pc3.bind(bind3).getResultCode()).isEqualTo(ResultCode.SUCCESS);
        verify(factory, times(3)).getConnection();
        verify(connection3).bind(bind3);

        pc3.close();
        pool.close();
    }

    /**
     * Verifies that a pool with connection keep alive correctly purges idle
     * connections after the keepalive period has expired.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testConnectionKeepAliveExpiration() throws Exception {
        final Connection pooledConnection1 = mock(Connection.class, "pooledConnection1");
        final Connection pooledConnection2 = mock(Connection.class, "pooledConnection2");
        final Connection pooledConnection3 = mock(Connection.class, "pooledConnection3");
        final Connection pooledConnection4 = mock(Connection.class, "pooledConnection4");
        final Connection pooledConnection5 = mock(Connection.class, "pooledConnection5");
        final Connection pooledConnection6 = mock(Connection.class, "pooledConnection6");

        when(pooledConnection1.isValid()).thenReturn(true);
        when(pooledConnection2.isValid()).thenReturn(true);
        when(pooledConnection3.isValid()).thenReturn(true);
        when(pooledConnection4.isValid()).thenReturn(true);
        when(pooledConnection5.isValid()).thenReturn(true);
        when(pooledConnection6.isValid()).thenReturn(true);

        final ConnectionFactory factory =
                mockConnectionFactory(pooledConnection1, pooledConnection2, pooledConnection3,
                        pooledConnection4, pooledConnection5, pooledConnection6);
        final MockScheduler scheduler = new MockScheduler();
        final CachedConnectionPool pool =
                new CachedConnectionPool(factory, 2, 4, 100, TimeUnit.MILLISECONDS, scheduler);
        assertThat(scheduler.isScheduled()).isTrue();

        // First populate the pool with idle connections at time 0.
        pool.timeService = mockTimeService(0);

        assertThat(pool.currentPoolSize()).isEqualTo(0);
        Connection c1 = pool.getConnection();
        Connection c2 = pool.getConnection();
        Connection c3 = pool.getConnection();
        Connection c4 = pool.getConnection();
        assertThat(pool.currentPoolSize()).isEqualTo(4);
        c1.close();
        c2.close();
        c3.close();
        c4.close();
        assertThat(pool.currentPoolSize()).isEqualTo(4);

        // First purge at time 50 is no-op because no connections have expired.
        when(pool.timeService.now()).thenReturn(50L);
        scheduler.runFirstTask();
        assertThat(pool.currentPoolSize()).isEqualTo(4);

        // Second purge at time 150 should remove 2 non-core connections.
        when(pool.timeService.now()).thenReturn(150L);
        scheduler.runFirstTask();
        assertThat(pool.currentPoolSize()).isEqualTo(2);

        verify(pooledConnection1, times(1)).close();
        verify(pooledConnection2, times(1)).close();
        verify(pooledConnection3, times(0)).close();
        verify(pooledConnection4, times(0)).close();

        // Regrow the pool at time 200.
        when(pool.timeService.now()).thenReturn(200L);
        Connection c5 = pool.getConnection(); // pooledConnection3
        Connection c6 = pool.getConnection(); // pooledConnection4
        Connection c7 = pool.getConnection(); // pooledConnection5
        Connection c8 = pool.getConnection(); // pooledConnection6
        assertThat(pool.currentPoolSize()).isEqualTo(4);
        c5.close();
        c6.close();
        c7.close();
        c8.close();
        assertThat(pool.currentPoolSize()).isEqualTo(4);

        // Third purge at time 250 should not remove any connections.
        when(pool.timeService.now()).thenReturn(250L);
        scheduler.runFirstTask();
        assertThat(pool.currentPoolSize()).isEqualTo(4);

        // Fourth purge at time 350 should remove 2 non-core connections.
        when(pool.timeService.now()).thenReturn(350L);
        scheduler.runFirstTask();
        assertThat(pool.currentPoolSize()).isEqualTo(2);

        verify(pooledConnection3, times(1)).close();
        verify(pooledConnection4, times(1)).close();
        verify(pooledConnection5, times(0)).close();
        verify(pooledConnection6, times(0)).close();

        pool.close();
        verify(pooledConnection5, times(1)).close();
        verify(pooledConnection6, times(1)).close();
        assertThat(scheduler.isScheduled()).isFalse();
    }

    /**
     * Test that all outstanding pending connection promises are completed when a
     * connection request fails.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test(description = "OPENDJ-1348", timeOut = 10000)
    public void testNewConnectionFailureFlushesAllPendingPromises() throws Exception {
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        final int poolSize = 2;
        final ConnectionPool pool = Connections.newFixedConnectionPool(factory, poolSize);
        doAnswer(new Answer<Promise<Connection, LdapException>>() {
            @Override
            public Promise<Connection, LdapException> answer(final InvocationOnMock invocation)
                    throws Throwable {
                return PromiseImpl.create();
            }
        }).when(factory).getConnectionAsync();

        List<Promise<? extends Connection, LdapException>> promises = new ArrayList<>();
        for (int i = 0; i < poolSize + 1; i++) {
            promises.add(pool.getConnectionAsync());
        }
        // factory.getConnectionAsync() has been called by the pool poolSize times
        verify(factory, times(poolSize)).getConnectionAsync();
        final LdapException connectError = newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR);
        for (Promise<? extends Connection, LdapException> promise : promises) {
            // Simulate that an error happened with the created connections
            ((PromiseImpl) promise).handleException(connectError);

            try {
                // Before the fix for OPENDJ-1348 the third promise.get() would hang.
                promise.getOrThrow();
                Assert.fail("Expected an exception to be thrown");
            } catch (LdapException e) {
                assertThat(e).isSameAs(connectError);
            }
        }
    }

}
