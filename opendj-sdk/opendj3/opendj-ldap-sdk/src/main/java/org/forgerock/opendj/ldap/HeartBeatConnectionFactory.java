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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

import com.forgerock.opendj.util.ConnectionDecorator;
import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * An heart beat connection factory can be used to create connections that sends
 * a periodic search request to a Directory Server.
 */
final class HeartBeatConnectionFactory implements ConnectionFactory {
    /**
     * A connection that sends heart beats and supports all operations.
     */
    private final class ConnectionImpl extends ConnectionDecorator implements
            ConnectionEventListener, SearchResultHandler {
        private long lastSuccessfulPing;

        private FutureResult<Result> lastPingFuture;

        private ConnectionImpl(final Connection connection) {
            super(connection);
        }

        @Override
        public void handleConnectionClosed() {
            notifyClosed();
        }

        @Override
        public void handleConnectionError(final boolean isDisconnectNotification,
                final ErrorResultException error) {
            notifyClosed();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            // Ignore.
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleErrorResult(final ErrorResultException error) {
            connection.close(Requests.newUnbindRequest(), "Heartbeat retured error: " + error);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean handleReference(final SearchResultReference reference) {
            // Ignore.
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleResult(final Result result) {
            lastSuccessfulPing = System.currentTimeMillis();
        }

        @Override
        public void handleUnsolicitedNotification(final ExtendedResult notification) {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isValid() {
            return connection.isValid()
                    && (lastSuccessfulPing <= 0 || System.currentTimeMillis() - lastSuccessfulPing < unit
                            .toMillis(interval) * 2);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("HeartBeatConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

        private void notifyClosed() {
            synchronized (activeConnections) {
                connection.removeConnectionEventListener(this);
                activeConnections.remove(this);

                if (activeConnections.isEmpty()) {
                    // This is the last active connection, so stop the heart
                    // beat.
                    heartBeatFuture.cancel(false);
                }
            }
        }
    }

    private final class FutureResultImpl extends FutureResultTransformer<Connection, Connection>
            implements ResultHandler<Connection> {

        private FutureResultImpl(final ResultHandler<? super Connection> handler) {
            super(handler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Connection transformResult(final Connection connection)
                throws ErrorResultException {
            return adaptConnection(connection);
        }

    }

    private final class HeartBeatRunnable implements Runnable {
        private HeartBeatRunnable() {
            // Nothing to do.
        }

        @Override
        public void run() {
            synchronized (activeConnections) {
                for (final ConnectionImpl connection : activeConnections) {
                    if (connection.lastPingFuture == null || connection.lastPingFuture.isDone()) {
                        connection.lastPingFuture =
                                connection.searchAsync(heartBeat, null, connection);
                    }
                }
            }
        }
    }

    private final SearchRequest heartBeat;

    private final long interval;

    private final ScheduledExecutorService scheduler;

    private final TimeUnit unit;

    private final List<ConnectionImpl> activeConnections;

    private final ConnectionFactory factory;

    private static final SearchRequest DEFAULT_SEARCH = Requests.newSearchRequest("",
            SearchScope.BASE_OBJECT, "(objectClass=*)", "1.1");

    private ScheduledFuture<?> heartBeatFuture;

    /**
     * Creates a new heart-beat connection factory which will create connections
     * using the provided connection factory and periodically ping any created
     * connections in order to detect that they are still alive every 10 seconds
     * using the default scheduler.
     *
     * @param factory
     *            The connection factory to use for creating connections.
     */
    HeartBeatConnectionFactory(final ConnectionFactory factory) {
        this(factory, 10, TimeUnit.SECONDS, DEFAULT_SEARCH, StaticUtils.getDefaultScheduler());
    }

    /**
     * Creates a new heart-beat connection factory which will create connections
     * using the provided connection factory and periodically ping any created
     * connections in order to detect that they are still alive using the
     * specified frequency and the default scheduler.
     *
     * @param factory
     *            The connection factory to use for creating connections.
     * @param interval
     *            The interval between keepalive pings.
     * @param unit
     *            The time unit for the interval between keepalive pings.
     */
    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final TimeUnit unit) {
        this(factory, interval, unit, DEFAULT_SEARCH, StaticUtils.getDefaultScheduler());
    }

    /**
     * Creates a new heart-beat connection factory which will create connections
     * using the provided connection factory and periodically ping any created
     * connections using the specified search request in order to detect that
     * they are still alive.
     *
     * @param factory
     *            The connection factory to use for creating connections.
     * @param interval
     *            The interval between keepalive pings.
     * @param unit
     *            The time unit for the interval between keepalive pings.
     * @param heartBeat
     *            The search request to use for keepalive pings.
     */
    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final TimeUnit unit, final SearchRequest heartBeat) {
        this(factory, interval, unit, heartBeat, StaticUtils.getDefaultScheduler());
    }

    /**
     * Creates a new heart-beat connection factory which will create connections
     * using the provided connection factory and periodically ping any created
     * connections using the specified search request in order to detect that
     * they are still alive.
     *
     * @param factory
     *            The connection factory to use for creating connections.
     * @param interval
     *            The interval between keepalive pings.
     * @param unit
     *            The time unit for the interval between keepalive pings.
     * @param heartBeat
     *            The search request to use for keepalive pings.
     * @param scheduler
     *            The scheduler which should for periodically sending keepalive
     *            pings.
     */
    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final TimeUnit unit, final SearchRequest heartBeat,
            final ScheduledExecutorService scheduler) {
        Validator.ensureNotNull(factory, heartBeat, unit, scheduler);
        Validator.ensureTrue(interval >= 0, "negative timeout");

        this.heartBeat = heartBeat;
        this.interval = interval;
        this.unit = unit;
        this.activeConnections = new LinkedList<ConnectionImpl>();
        this.factory = factory;
        this.scheduler = scheduler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() throws ErrorResultException {
        return adaptConnection(factory.getConnection());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        final FutureResultImpl future = new FutureResultImpl(handler);
        future.setFutureResult(factory.getConnectionAsync(future));
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("HeartBeatConnectionFactory(");
        builder.append(String.valueOf(factory));
        builder.append(')');
        return builder.toString();
    }

    private Connection adaptConnection(final Connection connection) {
        final ConnectionImpl heartBeatConnection = new ConnectionImpl(connection);
        synchronized (activeConnections) {
            connection.addConnectionEventListener(heartBeatConnection);
            if (activeConnections.isEmpty()) {
                // This is the first active connection, so start the heart beat.
                heartBeatFuture =
                        scheduler
                                .scheduleWithFixedDelay(new HeartBeatRunnable(), 0, interval, unit);
            }
            activeConnections.add(heartBeatConnection);
        }
        return heartBeatConnection;
    }
}
