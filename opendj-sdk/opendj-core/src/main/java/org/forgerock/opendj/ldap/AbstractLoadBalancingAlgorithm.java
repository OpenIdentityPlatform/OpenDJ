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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.util.ReferenceCountedObject;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.util.promise.Promises.*;

import static com.forgerock.opendj.util.StaticUtils.*;

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
            LdapResultHandler<Connection> {

        private final ConnectionFactory factory;
        private final AtomicBoolean isOperational = new AtomicBoolean(true);
        private volatile Promise<?, LdapException> pendingConnectPromise;
        private final int index;

        private MonitoredConnectionFactory(final ConnectionFactory factory, final int index) {
            this.factory = factory;
            this.index = index;
        }

        @Override
        public void close() {
            // Should we cancel the promise?
            factory.close();
        }

        @Override
        public Connection getConnection() throws LdapException {
            final Connection connection;
            try {
                connection = factory.getConnection();
            } catch (LdapException e) {
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
        public Promise<Connection, LdapException> getConnectionAsync() {
            return factory.getConnectionAsync().thenAsync(
                new AsyncFunction<Connection, Connection, LdapException>() {
                    @Override
                    public Promise<Connection, LdapException> apply(Connection value) throws LdapException {
                        notifyOnline();
                        return newResultPromise(value);
                    }
                },
                new AsyncFunction<LdapException, Connection, LdapException>() {
                    @Override
                    public Promise<Connection, LdapException> apply(LdapException error) throws LdapException {
                        // Attempt failed - try next factory.
                        notifyOffline(error);
                        final int nextIndex = (index + 1) % monitoredFactories.size();
                        return getMonitoredConnectionFactory(nextIndex).getConnectionAsync();
                    }
                });
        }



        /**
         * Handle monitoring connection request failure.
         */
        @Override
        public void handleException(final LdapException exception) {
            notifyOffline(exception);
        }

        /**
         * Handle monitoring connection request success.
         */
        @Override
        public void handleResult(final Connection connection) {
            // The connection is not going to be used, so close it immediately.
            connection.close();
            notifyOnline();
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
            if (!isOperational.get() && (pendingConnectPromise == null || pendingConnectPromise.isDone())) {
                logger.debug(LocalizableMessage.raw("Attempting reconnect to offline factory '%s'", this));
                pendingConnectPromise = factory.getConnectionAsync().thenOnResult(this).thenOnException(this);
            }
        }

        private void notifyOffline(final LdapException error) {
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
                        logger.debug(LocalizableMessage.raw("Starting monitoring thread"));
                        monitoringFuture =
                                scheduler.get().scheduleWithFixedDelay(new MonitorRunnable(), 0,
                                        monitoringIntervalMS, TimeUnit.MILLISECONDS);
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
                        logger.debug(LocalizableMessage.raw("Stopping monitoring thread"));

                        monitoringFuture.cancel(false);
                        monitoringFuture = null;
                    }
                }
            }
        }

        private void handleListenerException(RuntimeException e) {
            // TODO: I18N
            logger.error(LocalizableMessage.raw("A run-time error occurred while processing a load-balancer event", e));
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

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    private final List<MonitoredConnectionFactory> monitoredFactories;
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;
    private final Object stateLock = new Object();

    /**
     * The last connection failure which caused a connection factory to be
     * marked offline. This is used in order to help diagnose problems when the
     * load-balancer has exhausted all of its factories.
     */
    private volatile LdapException lastFailure;

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
    private int offlineFactoriesCount;
    private final long monitoringIntervalMS;

    /**
     * Guarded by stateLock.
     */
    private ScheduledFuture<?> monitoringFuture;
    private final AtomicBoolean isClosed = new AtomicBoolean();

    AbstractLoadBalancingAlgorithm(final Collection<? extends ConnectionFactory> factories, final Options options) {
        Reject.ifNull(factories, options);

        this.monitoredFactories = new ArrayList<>(factories.size());
        int i = 0;
        for (final ConnectionFactory f : factories) {
            this.monitoredFactories.add(new MonitoredConnectionFactory(f, i++));
        }
        this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(options.get(LOAD_BALANCER_SCHEDULER));
        this.monitoringIntervalMS = options.get(LOAD_BALANCER_MONITORING_INTERVAL).to(TimeUnit.MILLISECONDS);
        this.listener = options.get(LOAD_BALANCER_EVENT_LISTENER);
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
    public final ConnectionFactory getConnectionFactory() throws LdapException {
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

    /** Return the first factory after index which is operational. */
    private MonitoredConnectionFactory getMonitoredConnectionFactory(final int initialIndex) throws LdapException {
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
        throw newLdapException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
                "No operational connection factories available", lastFailure);
    }
}
