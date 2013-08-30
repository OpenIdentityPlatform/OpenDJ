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

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;
import static com.forgerock.opendj.util.StaticUtils.DEFAULT_SCHEDULER;
import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.ReferenceCountedObject;
import com.forgerock.opendj.util.Validator;

/**
 * An abstract load balancing algorithm providing monitoring and failover
 * capabilities.
 * <p>
 * Implementations should override the method
 * {@code getInitialConnectionFactoryIndex()} in order to provide the policy for
 * selecting the first connection factory to use for each connection request.
 */
abstract class AbstractLoadBalancingAlgorithm implements LoadBalancingAlgorithm {
    private final class MonitoredConnectionFactory implements ConnectionFactory,
            ResultHandler<Connection> {

        private final ConnectionFactory factory;
        private final AtomicBoolean isOperational = new AtomicBoolean(true);
        private volatile FutureResult<?> pendingConnectFuture = null;
        private final int index;

        private MonitoredConnectionFactory(final ConnectionFactory factory, final int index) {
            this.factory = factory;
            this.index = index;
        }

        @Override
        public void close() {
            // Should we cancel the future?
            factory.close();
        }

        @Override
        public Connection getConnection() throws ErrorResultException {
            final Connection connection;
            try {
                connection = factory.getConnection();
            } catch (ErrorResultException e) {
                // Attempt failed - try next factory.
                notifyOffline(e);
                final int nextIndex = (index + 1) % monitoredFactories.size();
                final MonitoredConnectionFactory nextFactory =
                        getMonitoredConnectionFactory(nextIndex);
                return nextFactory.getConnection();
            }
            notifyOnline();
            return connection;
        }

        @Override
        public FutureResult<Connection> getConnectionAsync(
                final ResultHandler<? super Connection> resultHandler) {
            final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future =
                    new AsynchronousFutureResult<Connection, ResultHandler<? super Connection>>(
                            resultHandler);

            final ResultHandler<Connection> failoverHandler = new ResultHandler<Connection>() {
                @Override
                public void handleErrorResult(final ErrorResultException error) {
                    // Attempt failed - try next factory.
                    notifyOffline(error);
                    final int nextIndex = (index + 1) % monitoredFactories.size();
                    try {
                        final MonitoredConnectionFactory nextFactory =
                                getMonitoredConnectionFactory(nextIndex);
                        nextFactory.getConnectionAsync(future);
                    } catch (final ErrorResultException e) {
                        future.handleErrorResult(e);
                    }
                }

                @Override
                public void handleResult(final Connection result) {
                    notifyOnline();
                    future.handleResult(result);
                }
            };

            factory.getConnectionAsync(failoverHandler);
            return future;
        }

        /**
         * Handle monitoring connection request failure.
         */
        @Override
        public void handleErrorResult(final ErrorResultException error) {
            notifyOffline(error);
        }

        /**
         * Handle monitoring connection request success.
         */
        @Override
        public void handleResult(final Connection connection) {
            notifyOnline();

            // The connection is not going to be used, so close it immediately.
            connection.close();
        }

        @Override
        public String toString() {
            return factory.toString();
        }

        /**
         * Attempt to connect to the factory if it is offline and there is no
         * pending monitoring request.
         */
        private synchronized void checkIfAvailable() {
            if (!isOperational.get()
                    && (pendingConnectFuture == null || pendingConnectFuture.isDone())) {
                if (DEBUG_LOG.isLoggable(Level.FINE)) {
                    DEBUG_LOG.fine(String.format("Attempting reconnect to offline factory '%s'",
                            this));
                }
                pendingConnectFuture = factory.getConnectionAsync(this);
            }
        }

        private void notifyOffline(final ErrorResultException error) {
            // Save the error in case the load-balancer is exhausted.
            lastFailure = error;

            if (isOperational.getAndSet(false)) {
                // Transition from online to offline.
                synchronized (listenerLock) {
                    try {
                        listener.handleConnectionFactoryOffline(factory, error);
                    } catch (RuntimeException e) {
                        handleListenerException(e);
                    }
                }

                synchronized (stateLock) {
                    offlineFactoriesCount++;
                    if (offlineFactoriesCount == 1) {
                        // Enable monitoring.
                        if (DEBUG_LOG.isLoggable(Level.FINE)) {
                            DEBUG_LOG.fine(String.format("Starting monitoring thread"));
                        }

                        monitoringFuture =
                                scheduler.get().scheduleWithFixedDelay(new MonitorRunnable(), 0,
                                        monitoringInterval, monitoringIntervalTimeUnit);
                    }
                }
            }
        }

        private void notifyOnline() {
            if (!isOperational.getAndSet(true)) {
                // Transition from offline to online.
                synchronized (listenerLock) {
                    try {
                        listener.handleConnectionFactoryOnline(factory);
                    } catch (RuntimeException e) {
                        handleListenerException(e);
                    }
                }

                synchronized (stateLock) {
                    offlineFactoriesCount--;
                    if (offlineFactoriesCount == 0) {
                        if (DEBUG_LOG.isLoggable(Level.FINE)) {
                            DEBUG_LOG.fine(String.format("Stopping monitoring thread"));
                        }

                        monitoringFuture.cancel(false);
                        monitoringFuture = null;
                    }
                }
            }
        }

        private void handleListenerException(RuntimeException e) {
            if (DEBUG_LOG.isLoggable(Level.SEVERE)) {
                DEBUG_LOG.log(Level.SEVERE,
                        "A run-time error occurred while processing a load-balancer event", e);
            }
        }
    }

    private final class MonitorRunnable implements Runnable {
        private MonitorRunnable() {
            // Nothing to do.
        }

        @Override
        public void run() {
            for (final MonitoredConnectionFactory factory : monitoredFactories) {
                factory.checkIfAvailable();
            }
        }
    }

    /**
     * A default event listener which just logs the event.
     */
    private static final LoadBalancerEventListener DEFAULT_LISTENER =
            new LoadBalancerEventListener() {

                @Override
                public void handleConnectionFactoryOnline(ConnectionFactory factory) {
                    // Transition from offline to online.
                    if (DEBUG_LOG.isLoggable(Level.INFO)) {
                        DEBUG_LOG.info(String.format("Connection factory'%s' is now operational",
                                factory));
                    }
                }

                @Override
                public void handleConnectionFactoryOffline(ConnectionFactory factory,
                        ErrorResultException error) {
                    if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                        DEBUG_LOG.warning(String.format(
                                "Connection factory '%s' is no longer operational: %s", factory,
                                error.getMessage()));
                    }
                }
            };

    private final List<MonitoredConnectionFactory> monitoredFactories;
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;
    private final Object stateLock = new Object();

    /**
     * The last connection failure which caused a connection factory to be
     * marked offline. This is used in order to help diagnose problems when the
     * load-balancer has exhausted all of its factories.
     */
    private volatile ErrorResultException lastFailure = null;

    /**
     * The event listener which should be notified when connection factories go
     * on or off-line.
     */
    private final LoadBalancerEventListener listener;

    /**
     * Ensures that events are notified one at a time.
     */
    private final Object listenerLock = new Object();

    /**
     * Guarded by stateLock.
     */
    private int offlineFactoriesCount = 0;
    private final long monitoringInterval;
    private final TimeUnit monitoringIntervalTimeUnit;
    /**
     * Guarded by stateLock.
     */
    private ScheduledFuture<?> monitoringFuture;
    private AtomicBoolean isClosed = new AtomicBoolean();

    AbstractLoadBalancingAlgorithm(final Collection<ConnectionFactory> factories,
            final LoadBalancerEventListener listener, final long interval, final TimeUnit unit,
            final ScheduledExecutorService scheduler) {
        Validator.ensureNotNull(factories, unit);

        this.monitoredFactories = new ArrayList<MonitoredConnectionFactory>(factories.size());
        int i = 0;
        for (final ConnectionFactory f : factories) {
            this.monitoredFactories.add(new MonitoredConnectionFactory(f, i++));
        }
        this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(scheduler);
        this.monitoringInterval = interval;
        this.monitoringIntervalTimeUnit = unit;
        this.listener = listener != null ? listener : DEFAULT_LISTENER;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized (stateLock) {
                if (monitoringFuture != null) {
                    monitoringFuture.cancel(false);
                    monitoringFuture = null;
                }
            }
            for (ConnectionFactory factory : monitoredFactories) {
                factory.close();
            }
            scheduler.release();
        }
    }

    @Override
    public final ConnectionFactory getConnectionFactory() throws ErrorResultException {
        final int index = getInitialConnectionFactoryIndex();
        return getMonitoredConnectionFactory(index);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getAlgorithmName());
        builder.append('(');
        boolean isFirst = true;
        for (final ConnectionFactory factory : monitoredFactories) {
            if (!isFirst) {
                builder.append(',');
            } else {
                isFirst = false;
            }
            builder.append(factory);
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Returns the name of this load balancing algorithm.
     *
     * @return The name of this load balancing algorithm.
     */
    abstract String getAlgorithmName();

    /**
     * Returns the index of the first connection factory which should be used in
     * order to satisfy the next connection request.
     *
     * @return The index of the first connection factory which should be used in
     *         order to satisfy the next connection request.
     */
    abstract int getInitialConnectionFactoryIndex();

    // Return the first factory after index which is operational.
    private MonitoredConnectionFactory getMonitoredConnectionFactory(final int initialIndex)
            throws ErrorResultException {
        int index = initialIndex;
        final int maxIndex = monitoredFactories.size();
        do {
            final MonitoredConnectionFactory factory = monitoredFactories.get(index);
            if (factory.isOperational.get()) {
                return factory;
            }
            index = (index + 1) % maxIndex;
        } while (index != initialIndex);

        /*
         * All factories are offline so give up. We could have a configurable
         * policy here such as waiting indefinitely, or for a configurable
         * timeout period.
         */
        throw newErrorResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                "No operational connection factories available", lastFailure);
    }
}
