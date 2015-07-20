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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.util.promise.Promises.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
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
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.time.TimeService;

import com.forgerock.opendj.util.ReferenceCountedObject;

/**
 * A connection pool implementation which maintains a cache of pooled
 * connections with a configurable core pool size, maximum size, and expiration
 * policy.
 */
final class CachedConnectionPool implements ConnectionPool {

    /**
     * This success handler is invoked when an attempt to add a new connection
     * to the pool completes.
     */
    private final class ConnectionResultHandler implements ResultHandler<Connection> {
        @Override
        public void handleResult(final Connection connection) {
            logger.debug(LocalizableMessage.raw(
                    "Connection attempt succeeded:  availableConnections=%d, maxPoolSize=%d",
                     currentPoolSize(), maxPoolSize));
            pendingConnectionAttempts.decrementAndGet();
            publishConnection(connection);
        }
    }

    /**
     * This failure handler is invoked when an attempt to add a new connection
     * to the pool ended in error.
     */
    private final class ConnectionFailureHandler implements ExceptionHandler<LdapException> {
        @Override
        public void handleException(final LdapException exception) {
            // Connection attempt failed, so decrease the pool size.
            pendingConnectionAttempts.decrementAndGet();
            availableConnections.release();

            logger.debug(LocalizableMessage.raw(
                    "Connection attempt failed: availableConnections=%d, maxPoolSize=%d",
                    currentPoolSize(), maxPoolSize, exception));

            /*
             * There may be many pending promises waiting for a connection
             * attempt to succeed. In some situations the number of pending
             * promises may exceed the pool size and the number of outstanding
             * connection attempts. If only one pending promises is resolved per
             * failed connection attempt then some pending promises will be left
             * unresolved. Therefore, a failed connection attempt must fail all
             * pending promises, even if some of the subsequent connection
             * attempts succeed, which is unlikely (if one fails, then they are
             * all likely to fail).
             */
            final List<QueueElement> waitingPromises = new LinkedList<>();
            synchronized (queue) {
                while (hasWaitingPromises()) {
                    waitingPromises.add(queue.removeFirst());
                }
            }
            for (QueueElement waitingPromise : waitingPromises) {
                waitingPromise.getWaitingPromise().handleException(exception);
            }
        }
    }

    /**
     * A pooled connection is passed to the client. It wraps an underlying
     * "pooled" connection obtained from the underlying factory and lasts until
     * the client application closes this connection. More specifically, pooled
     * connections are not actually stored in the internal queue.
     */
    class PooledConnection implements Connection, ConnectionEventListener {
        private final Connection connection;
        private LdapException error;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);
        private boolean isDisconnectNotification;
        private List<ConnectionEventListener> listeners;
        private final Object stateLock = new Object();

        PooledConnection(final Connection connection) {
            this.connection = connection;
        }

        @Override
        public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
            return checkState().abandonAsync(request);
        }

        @Override
        public Result add(final AddRequest request) throws LdapException {
            return checkState().add(request);
        }

        @Override
        public Result add(final Entry entry) throws LdapException {
            return checkState().add(entry);
        }

        @Override
        public Result add(final String... ldifLines) throws LdapException {
            return checkState().add(ldifLines);
        }

        @Override
        public LdapPromise<Result> addAsync(AddRequest request) {
            return addAsync(request, null);
        }

        @Override
        public LdapPromise<Result> addAsync(final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().addAsync(request, intermediateResponseHandler);
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            Reject.ifNull(listener);
            final boolean notifyClose;
            final boolean notifyErrorOccurred;
            synchronized (stateLock) {
                notifyClose = isClosed.get();
                notifyErrorOccurred = error != null;
                if (!notifyClose) {
                    if (listeners == null) {
                        /*
                         * Create and register first listener. If an error has
                         * already occurred on the underlying connection, then
                         * the listener may be immediately invoked so ensure
                         * that it is already in the list.
                         */
                        listeners = new CopyOnWriteArrayList<>();
                        listeners.add(listener);
                        connection.addConnectionEventListener(this);
                    } else {
                        listeners.add(listener);
                    }
                }
            }
            if (notifyErrorOccurred) {
                listener.handleConnectionError(isDisconnectNotification, error);
            }
            if (notifyClose) {
                listener.handleConnectionClosed();
            }
        }

        @Override
        public Result applyChange(final ChangeRecord request) throws LdapException {
            return checkState().applyChange(request);
        }

        @Override
        public LdapPromise<Result> applyChangeAsync(final ChangeRecord request) {
            return checkState().applyChangeAsync(request, null);
        }

        @Override
        public LdapPromise<Result> applyChangeAsync(final ChangeRecord request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().applyChangeAsync(request, intermediateResponseHandler);
        }

        @Override
        public BindResult bind(final BindRequest request) throws LdapException {
            return checkState().bind(request);
        }

        @Override
        public BindResult bind(final String name, final char[] password) throws LdapException {
            return checkState().bind(name, password);
        }

        @Override
        public LdapPromise<BindResult> bindAsync(BindRequest request) {
            return bindAsync(request, null);
        }

        @Override
        public LdapPromise<BindResult> bindAsync(final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().bindAsync(request, intermediateResponseHandler);
        }

        @Override
        public void close() {
            final List<ConnectionEventListener> tmpListeners;
            synchronized (stateLock) {
                if (!isClosed.compareAndSet(false, true)) {
                    // Already closed.
                    return;
                }
                tmpListeners = listeners;
            }

            /*
             * Remove underlying listener if needed and do this before
             * subsequent connection events may occur.
             */
            if (tmpListeners != null) {
                connection.removeConnectionEventListener(this);
            }

            // Don't put invalid connections back in the pool.
            if (connection.isValid()) {
                publishConnection(connection);
            } else {
                /*
                 * The connection may have been disconnected by the remote
                 * server, but the server may still be available. In order to
                 * avoid leaving pending promises hanging indefinitely, we should
                 * try to reconnect immediately. No need to release/acquire
                 * availableConnections.
                 */
                connection.close();
                pendingConnectionAttempts.incrementAndGet();
                factory.getConnectionAsync().thenOnResult(connectionResultHandler)
                                            .thenOnException(connectionFailureHandler);

                logger.debug(LocalizableMessage.raw(
                        "Connection no longer valid: availableConnections=%d, maxPoolSize=%d",
                        currentPoolSize(), maxPoolSize));
            }

            // Invoke listeners.
            if (tmpListeners != null) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    listener.handleConnectionClosed();
                }
            }
        }

        @Override
        public void close(final UnbindRequest request, final String reason) {
            close();
        }

        @Override
        public CompareResult compare(final CompareRequest request) throws LdapException {
            return checkState().compare(request);
        }

        @Override
        public CompareResult compare(final String name, final String attributeDescription,
                final String assertionValue) throws LdapException {
            return checkState().compare(name, attributeDescription, assertionValue);
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(CompareRequest request) {
            return compareAsync(request, null);
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().compareAsync(request, intermediateResponseHandler);
        }

        @Override
        public Result delete(final DeleteRequest request) throws LdapException {
            return checkState().delete(request);
        }

        @Override
        public Result delete(final String name) throws LdapException {
            return checkState().delete(name);
        }

        @Override
        public LdapPromise<Result> deleteAsync(DeleteRequest request) {
            return deleteAsync(request, null);
        }

        @Override
        public LdapPromise<Result> deleteAsync(final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().deleteAsync(request, intermediateResponseHandler);
        }

        @Override
        public Result deleteSubtree(final String name) throws LdapException {
            return checkState().deleteSubtree(name);
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request) throws LdapException {
            return checkState().extendedRequest(request);
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
                final IntermediateResponseHandler handler) throws LdapException {
            return checkState().extendedRequest(request, handler);
        }

        @Override
        public GenericExtendedResult extendedRequest(final String requestName,
                final ByteString requestValue) throws LdapException {
            return checkState().extendedRequest(requestName, requestValue);
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request) {
            return extendedRequestAsync(request, null);
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().extendedRequestAsync(request, intermediateResponseHandler);
        }

        @Override
        public void handleConnectionClosed() {
            /*
             * The underlying connection was closed by the client. This can only
             * occur when the pool is being shut down and the underlying
             * connection is not in use.
             */
            throw new IllegalStateException(
                    "Pooled connection received unexpected close notification");
        }

        @Override
        public void handleConnectionError(final boolean isDisconnectNotification, final LdapException error) {
            final List<ConnectionEventListener> tmpListeners;
            synchronized (stateLock) {
                tmpListeners = listeners;
                this.isDisconnectNotification = isDisconnectNotification;
                this.error = error;
            }
            if (tmpListeners != null) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    listener.handleConnectionError(isDisconnectNotification, error);
                }
            }
        }

        @Override
        public void handleUnsolicitedNotification(final ExtendedResult notification) {
            final List<ConnectionEventListener> tmpListeners;
            synchronized (stateLock) {
                tmpListeners = listeners;
            }
            if (tmpListeners != null) {
                for (final ConnectionEventListener listener : tmpListeners) {
                    listener.handleUnsolicitedNotification(notification);
                }
            }
        }

        @Override
        public boolean isClosed() {
            return isClosed.get();
        }

        @Override
        public boolean isValid() {
            return connection.isValid() && !isClosed();
        }

        @Override
        public Result modify(final ModifyRequest request) throws LdapException {
            return checkState().modify(request);
        }

        @Override
        public Result modify(final String... ldifLines) throws LdapException {
            return checkState().modify(ldifLines);
        }

        @Override
        public LdapPromise<Result> modifyAsync(ModifyRequest request) {
            return modifyAsync(request, null);
        }

        @Override
        public LdapPromise<Result> modifyAsync(final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().modifyAsync(request, intermediateResponseHandler);
        }

        @Override
        public Result modifyDN(final ModifyDNRequest request) throws LdapException {
            return checkState().modifyDN(request);
        }

        @Override
        public Result modifyDN(final String name, final String newRDN) throws LdapException {
            return checkState().modifyDN(name, newRDN);
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(ModifyDNRequest request) {
            return modifyDNAsync(request, null);
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            return checkState().modifyDNAsync(request, intermediateResponseHandler);
        }

        @Override
        public SearchResultEntry readEntry(final DN name, final String... attributeDescriptions)
                throws LdapException {
            return checkState().readEntry(name, attributeDescriptions);
        }

        @Override
        public SearchResultEntry readEntry(final String name, final String... attributeDescriptions)
                throws LdapException {
            return checkState().readEntry(name, attributeDescriptions);
        }

        @Override
        public LdapPromise<SearchResultEntry> readEntryAsync(final DN name,
                final Collection<String> attributeDescriptions) {
            return checkState().readEntryAsync(name, attributeDescriptions);
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            Reject.ifNull(listener);
            synchronized (stateLock) {
                if (listeners != null) {
                    listeners.remove(listener);
                }
            }
        }

        @Override
        public ConnectionEntryReader search(final SearchRequest request) {
            return checkState().search(request);
        }

        @Override
        public Result search(final SearchRequest request, final Collection<? super SearchResultEntry> entries)
                throws LdapException {
            return checkState().search(request, entries);
        }

        @Override
        public Result search(final SearchRequest request, final Collection<? super SearchResultEntry> entries,
                final Collection<? super SearchResultReference> references) throws LdapException {
            return checkState().search(request, entries, references);
        }

        @Override
        public Result search(final SearchRequest request, final SearchResultHandler handler)
                throws LdapException {
            return checkState().search(request, handler);
        }

        @Override
        public ConnectionEntryReader search(final String baseObject, final SearchScope scope, final String filter,
                final String... attributeDescriptions) {
            return checkState().search(baseObject, scope, filter, attributeDescriptions);
        }

        @Override
        public LdapPromise<Result> searchAsync(SearchRequest request, SearchResultHandler resultHandler) {
            return searchAsync(request, null, resultHandler);
        }

        @Override
        public LdapPromise<Result> searchAsync(final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
            return checkState().searchAsync(request, intermediateResponseHandler, entryHandler);
        }

        @Override
        public SearchResultEntry searchSingleEntry(final SearchRequest request) throws LdapException {
            return checkState().searchSingleEntry(request);
        }

        @Override
        public SearchResultEntry searchSingleEntry(final String baseObject, final SearchScope scope,
                final String filter, final String... attributeDescriptions) throws LdapException {
            return checkState().searchSingleEntry(baseObject, scope, filter, attributeDescriptions);
        }

        @Override
        public LdapPromise<SearchResultEntry> searchSingleEntryAsync(final SearchRequest request) {
            return checkState().searchSingleEntryAsync(request);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("PooledConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

        /** Checks that this pooled connection has not been closed. */
        private Connection checkState() {
            if (isClosed()) {
                throw new IllegalStateException();
            }
            return connection;
        }
    }

    /**
     * Scheduled task responsible for purging non-core pooled connections which
     * have been idle for longer than the idle timeout limit.
     */
    private final class PurgeIdleConnectionsTask implements Runnable {
        @Override
        public void run() {
            final List<Connection> idleConnections;
            synchronized (queue) {
                if (isClosed) {
                    return;
                }

                /*
                 * Obtain a list of expired connections but don't close them yet
                 * since we don't want to hold the lock too long.
                 */
                idleConnections = new LinkedList<>();
                final long timeoutMillis = timeService.now() - idleTimeoutMillis;
                int nonCoreConnectionCount = currentPoolSize() - corePoolSize;
                for (QueueElement holder = queue.peek(); nonCoreConnectionCount > 0
                        && isTimedOutQueuedConnection(holder, timeoutMillis); holder = queue.peek()) {
                    idleConnections.add(holder.getWaitingConnection());
                    queue.poll();
                    availableConnections.release();
                    nonCoreConnectionCount--;
                }
            }

            // Close the idle connections.
            if (!idleConnections.isEmpty()) {
                logger.debug(LocalizableMessage.raw(
                        "Closing %d idle pooled connections: availableConnections=%d, maxPoolSize=%d",
                        idleConnections.size(), currentPoolSize(), maxPoolSize));
                for (final Connection connection : idleConnections) {
                    connection.close();
                }
            }
        }

        private boolean isTimedOutQueuedConnection(final QueueElement holder, final long timeoutMillis) {
            return holder != null && !holder.isWaitingPromise() && holder.hasTimedOut(timeoutMillis);
        }
    }

    private final class DebugEnabledPooledConnection extends PooledConnection {
        private final StackTraceElement[] stackTrace;

        private DebugEnabledPooledConnection(final Connection connection,
                final StackTraceElement[] stackTrace) {
            super(connection);
            this.stackTrace = stackTrace;
        }

        @Override
        protected void finalize() throws Throwable {
            if (!isClosed()) {
                logIfDebugEnabled("CONNECTION POOL: connection leaked! It was allocated here: ", stackTrace);
            }
        }
    }

    /**
     * A queue element is either a pending connection request promise awaiting an
     * {@code Connection} or it is an unused {@code Connection} awaiting a
     * connection request.
     */
    private static final class QueueElement {
        private final long timestampMillis;
        private final Object value;
        private final StackTraceElement[] stack;

        QueueElement(final Connection connection, final long timestampMillis) {
            this.value = connection;
            this.timestampMillis = timestampMillis;
            this.stack = null;
        }

        QueueElement(final long timestampMillis, final StackTraceElement[] stack) {
            this.value = PromiseImpl.create();
            this.timestampMillis = timestampMillis;
            this.stack = stack;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        StackTraceElement[] getStackTrace() {
            return stack;
        }

        Connection getWaitingConnection() {
            if (value instanceof Connection) {
                return (Connection) value;
            } else {
                throw new IllegalStateException();
            }
        }

        @SuppressWarnings("unchecked")
        PromiseImpl<Connection, LdapException> getWaitingPromise() {
            return (PromiseImpl<Connection, LdapException>) value;
        }

        boolean hasTimedOut(final long timeLimitMillis) {
            return timestampMillis < timeLimitMillis;
        }

        boolean isWaitingPromise() {
            return value instanceof PromiseImpl;
        }
    }

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * This is package private in order to allow unit tests to inject fake time
     * stamps.
     */
    TimeService timeService = TimeService.SYSTEM;

    private final Semaphore availableConnections;
    private final ResultHandler<Connection> connectionResultHandler = new ConnectionResultHandler();
    private final ExceptionHandler<LdapException> connectionFailureHandler = new ConnectionFailureHandler();
    private final int corePoolSize;
    private final ConnectionFactory factory;
    private boolean isClosed;
    private final ScheduledFuture<?> idleTimeoutFuture;
    private final long idleTimeoutMillis;
    private final int maxPoolSize;
    private final LinkedList<QueueElement> queue = new LinkedList<>();
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;

    /**
     * The number of new connections which are in the process of being
     * established.
     */
    private final AtomicInteger pendingConnectionAttempts = new AtomicInteger();

    CachedConnectionPool(final ConnectionFactory factory, final int corePoolSize,
            final int maximumPoolSize, final long idleTimeout, final TimeUnit unit,
            final ScheduledExecutorService scheduler) {
        Reject.ifNull(factory);
        Reject.ifFalse(corePoolSize >= 0, "corePoolSize < 0");
        Reject.ifFalse(maximumPoolSize > 0, "maxPoolSize <= 0");
        Reject.ifFalse(corePoolSize <= maximumPoolSize, "corePoolSize > maxPoolSize");
        Reject.ifFalse(idleTimeout >= 0, "idleTimeout < 0");
        Reject.ifFalse(idleTimeout == 0 || unit != null, "time unit is null");

        this.factory = factory;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maximumPoolSize;
        this.availableConnections = new Semaphore(maximumPoolSize);

        if (corePoolSize < maximumPoolSize && idleTimeout > 0) {
            // Dynamic pool.
            this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(scheduler);
            this.idleTimeoutMillis = unit.toMillis(idleTimeout);
            this.idleTimeoutFuture =
                    this.scheduler.get().scheduleWithFixedDelay(new PurgeIdleConnectionsTask(),
                            idleTimeout, idleTimeout, unit);
        } else {
            // Fixed pool.
            this.scheduler = null;
            this.idleTimeoutMillis = 0;
            this.idleTimeoutFuture = null;
        }
    }

    @Override
    public void close() {
        final LinkedList<Connection> idleConnections;
        synchronized (queue) {
            if (isClosed) {
                return;
            }
            isClosed = true;

            /*
             * Remove any connections which are waiting in the queue as these
             * can be closed immediately.
             */
            idleConnections = new LinkedList<>();
            while (hasWaitingConnections()) {
                final QueueElement holder = queue.removeFirst();
                idleConnections.add(holder.getWaitingConnection());
                availableConnections.release();
            }
        }

        logger.debug(LocalizableMessage.raw(
                "Connection pool is closing: availableConnections=%d, maxPoolSize=%d",
                currentPoolSize(), maxPoolSize));

        if (idleTimeoutFuture != null) {
            idleTimeoutFuture.cancel(false);
            scheduler.release();
        }

        // Close all idle connections.
        for (final Connection connection : idleConnections) {
            connection.close();
        }

        // Close the underlying factory.
        factory.close();
    }

    @Override
    public Connection getConnection() throws LdapException {
        try {
            return getConnectionAsync().getOrThrow();
        } catch (final InterruptedException e) {
            throw newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
        }
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        // Loop while iterating through stale connections (see OPENDJ-590).
        for (;;) {
            final QueueElement holder;
            synchronized (queue) {
                if (isClosed) {
                    throw new IllegalStateException("CachedConnectionPool is already closed");
                } else if (hasWaitingConnections()) {
                    holder = queue.removeFirst();
                } else {
                    holder = new QueueElement(timeService.now(), getStackTraceIfDebugEnabled());
                    queue.add(holder);
                }
            }

            if (holder.isWaitingPromise()) {
                // Grow the pool if needed.
                final Promise<Connection, LdapException> promise = holder.getWaitingPromise();
                if (!promise.isDone() && availableConnections.tryAcquire()) {
                    pendingConnectionAttempts.incrementAndGet();
                    factory.getConnectionAsync().thenOnResult(connectionResultHandler)
                                                .thenOnException(connectionFailureHandler);
                }
                return promise;
            }

            // There was a completed connection attempt.
            final Connection connection = holder.getWaitingConnection();
            if (connection.isValid()) {
                final Connection pooledConnection = newPooledConnection(connection, getStackTraceIfDebugEnabled());
                return newResultPromise(pooledConnection);
            } else {
                // Close the stale connection and try again.
                connection.close();
                availableConnections.release();

                logger.debug(LocalizableMessage.raw("Connection no longer valid: availableConnections=%d, poolSize=%d",
                        currentPoolSize(), maxPoolSize));
            }
        }
    }

    @Override
    public String toString() {
        final int size = currentPoolSize();
        final int pending = pendingConnectionAttempts.get();
        int in = 0;
        int blocked = 0;
        synchronized (queue) {
            for (QueueElement qe : queue) {
                if (qe.isWaitingPromise()) {
                    blocked++;
                } else {
                    in++;
                }
            }
        }
        final int out = size - in - pending;
        return String.format("CachedConnectionPool(size=%d[in:%d + out:%d + "
                + "pending:%d], maxSize=%d, blocked=%d, factory=%s)", size, in, out, pending,
                maxPoolSize, blocked, String.valueOf(factory));
    }

    /**
     * Provide a finalizer because connection pools are expensive resources to
     * accidentally leave around. Also, since they won't be created all that
     * frequently, there's little risk of overloading the finalizer.
     */
    @Override
    protected void finalize() throws Throwable {
        close();
    }

    /** Package private for unit testing. */
    int currentPoolSize() {
        return maxPoolSize - availableConnections.availablePermits();
    }

    private boolean hasWaitingConnections() {
        return !queue.isEmpty() && !queue.getFirst().isWaitingPromise();
    }

    private boolean hasWaitingPromises() {
        return !queue.isEmpty() && queue.getFirst().isWaitingPromise();
    }

    private void publishConnection(final Connection connection) {
        final QueueElement holder;
        boolean connectionPoolIsClosing = false;

        synchronized (queue) {
            if (hasWaitingPromises()) {
                connectionPoolIsClosing = isClosed;
                holder = queue.removeFirst();
            } else if (isClosed) {
                connectionPoolIsClosing = true;
                holder = null;
            } else {
                holder = new QueueElement(connection, timeService.now());
                queue.add(holder);
                return;
            }
        }

        // There was waiting promise, so complete it.
        if (connectionPoolIsClosing) {
            // The connection will be closed, so decrease the pool size.
            availableConnections.release();
            connection.close();

            logger.debug(LocalizableMessage.raw(
                    "Closing connection because connection pool is closing: availableConnections=%d, maxPoolSize=%d",
                    currentPoolSize(), maxPoolSize));

            if (holder != null) {
                final LdapException e =
                        newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                                ERR_CONNECTION_POOL_CLOSING.get(toString()).toString());
                holder.getWaitingPromise().handleException(e);

                logger.debug(LocalizableMessage.raw(
                        "Connection attempt failed: availableConnections=%d, maxPoolSize=%d",
                        currentPoolSize(), maxPoolSize, e));
            }
        } else {
            holder.getWaitingPromise().handleResult(
                    newPooledConnection(connection, holder.getStackTrace()));
        }
    }

    private PooledConnection newPooledConnection(final Connection connection,
            final StackTraceElement[] stack) {
        if (!DEBUG_ENABLED) {
            return new PooledConnection(connection);
        } else {
            return new DebugEnabledPooledConnection(connection, stack);
        }
    }

}
