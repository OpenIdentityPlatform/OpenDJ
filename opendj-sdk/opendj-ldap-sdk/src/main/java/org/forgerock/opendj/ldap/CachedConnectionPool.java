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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;
import static com.forgerock.opendj.util.StaticUtils.DEFAULT_SCHEDULER;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_CONNECTION_POOL_CLOSING;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

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

import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.ReferenceCountedObject;
import com.forgerock.opendj.util.Validator;

/**
 * A connection pool implementation which maintains a cache of pooled
 * connections with a configurable core pool size, maximum size, and expiration
 * policy.
 */
final class CachedConnectionPool implements ConnectionPool {

    /**
     * This result handler is invoked when an attempt to add a new connection to
     * the pool completes.
     */
    private final class ConnectionResultHandler implements ResultHandler<Connection> {

        @Override
        public void handleErrorResult(final ErrorResultException error) {
            // Connection attempt failed, so decrease the pool size.
            availableConnections.release();

            if (DEBUG_LOG.isLoggable(Level.FINE)) {
                DEBUG_LOG.fine(String.format(
                        "Connection attempt failed: %s, availableConnections=%d, maxPoolSize=%d",
                        error.getMessage(), currentPoolSize(), maxPoolSize));
            }

            QueueElement holder;
            synchronized (queue) {
                if (hasWaitingFutures()) {
                    holder = queue.removeFirst();
                } else {
                    // No waiting futures.
                    return;
                }
            }

            // There was waiting future, so close it.
            holder.getWaitingFuture().handleErrorResult(error);
        }

        @Override
        public void handleResult(final Connection connection) {
            if (DEBUG_LOG.isLoggable(Level.FINE)) {
                DEBUG_LOG.fine(String.format(
                        "Connection attempt succeeded:  availableConnections=%d, maxPoolSize=%d",
                        currentPoolSize(), maxPoolSize));
            }
            publishConnection(connection);
        }
    }

    /**
     * A pooled connection is passed to the client. It wraps an underlying
     * "pooled" connection obtained from the underlying factory and lasts until
     * the client application closes this connection. More specifically, pooled
     * connections are not actually stored in the internal queue.
     */
    private final class PooledConnection implements Connection, ConnectionEventListener {
        private final Connection connection;
        private ErrorResultException error = null;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);
        private boolean isDisconnectNotification = false;
        private List<ConnectionEventListener> listeners = null;
        private final Object stateLock = new Object();

        private PooledConnection(final Connection connection) {
            this.connection = connection;
        }

        @Override
        public FutureResult<Void> abandonAsync(final AbandonRequest request) {
            return checkState().abandonAsync(request);
        }

        @Override
        public Result add(final AddRequest request) throws ErrorResultException {
            return checkState().add(request);
        }

        @Override
        public Result add(final Entry entry) throws ErrorResultException {
            return checkState().add(entry);
        }

        @Override
        public Result add(final String... ldifLines) throws ErrorResultException {
            return checkState().add(ldifLines);
        }

        @Override
        public FutureResult<Result> addAsync(final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return checkState().addAsync(request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            Validator.ensureNotNull(listener);
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
                        listeners = new CopyOnWriteArrayList<ConnectionEventListener>();
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
        public Result applyChange(final ChangeRecord request) throws ErrorResultException {
            return checkState().applyChange(request);
        }

        @Override
        public FutureResult<Result> applyChangeAsync(final ChangeRecord request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return checkState().applyChangeAsync(request, intermediateResponseHandler,
                    resultHandler);
        }

        @Override
        public BindResult bind(final BindRequest request) throws ErrorResultException {
            return checkState().bind(request);
        }

        @Override
        public BindResult bind(final String name, final char[] password)
                throws ErrorResultException {
            return checkState().bind(name, password);
        }

        @Override
        public FutureResult<BindResult> bindAsync(final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super BindResult> resultHandler) {
            return checkState().bindAsync(request, intermediateResponseHandler, resultHandler);
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
                 * avoid leaving pending futures hanging indefinitely, we should
                 * try to reconnect immediately. No need to release/acquire
                 * availableConnections.
                 */
                connection.close();
                factory.getConnectionAsync(connectionResultHandler);

                if (DEBUG_LOG.isLoggable(Level.FINE)) {
                    DEBUG_LOG.fine(String.format(
                            "Connection no longer valid: availableConnections=%d, maxPoolSize=%d",
                            currentPoolSize(), maxPoolSize));
                }
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
        public CompareResult compare(final CompareRequest request) throws ErrorResultException {
            return checkState().compare(request);
        }

        @Override
        public CompareResult compare(final String name, final String attributeDescription,
                final String assertionValue) throws ErrorResultException {
            return checkState().compare(name, attributeDescription, assertionValue);
        }

        @Override
        public FutureResult<CompareResult> compareAsync(final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super CompareResult> resultHandler) {
            return checkState().compareAsync(request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public Result delete(final DeleteRequest request) throws ErrorResultException {
            return checkState().delete(request);
        }

        @Override
        public Result delete(final String name) throws ErrorResultException {
            return checkState().delete(name);
        }

        @Override
        public FutureResult<Result> deleteAsync(final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return checkState().deleteAsync(request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public Result deleteSubtree(final String name) throws ErrorResultException {
            return checkState().deleteSubtree(name);
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request)
                throws ErrorResultException {
            return checkState().extendedRequest(request);
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
                final IntermediateResponseHandler handler) throws ErrorResultException {
            return checkState().extendedRequest(request, handler);
        }

        @Override
        public GenericExtendedResult extendedRequest(final String requestName,
                final ByteString requestValue) throws ErrorResultException {
            return checkState().extendedRequest(requestName, requestValue);
        }

        @Override
        public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super R> resultHandler) {
            return checkState().extendedRequestAsync(request, intermediateResponseHandler,
                    resultHandler);
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
        public void handleConnectionError(final boolean isDisconnectNotification,
                final ErrorResultException error) {
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
        public Result modify(final ModifyRequest request) throws ErrorResultException {
            return checkState().modify(request);
        }

        @Override
        public Result modify(final String... ldifLines) throws ErrorResultException {
            return checkState().modify(ldifLines);
        }

        @Override
        public FutureResult<Result> modifyAsync(final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return checkState().modifyAsync(request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public Result modifyDN(final ModifyDNRequest request) throws ErrorResultException {
            return checkState().modifyDN(request);
        }

        @Override
        public Result modifyDN(final String name, final String newRDN) throws ErrorResultException {
            return checkState().modifyDN(name, newRDN);
        }

        @Override
        public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return checkState().modifyDNAsync(request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public SearchResultEntry readEntry(final DN name, final String... attributeDescriptions)
                throws ErrorResultException {
            return checkState().readEntry(name, attributeDescriptions);
        }

        @Override
        public SearchResultEntry readEntry(final String name, final String... attributeDescriptions)
                throws ErrorResultException {
            return checkState().readEntry(name, attributeDescriptions);
        }

        @Override
        public FutureResult<SearchResultEntry> readEntryAsync(final DN name,
                final Collection<String> attributeDescriptions,
                final ResultHandler<? super SearchResultEntry> handler) {
            return checkState().readEntryAsync(name, attributeDescriptions, handler);
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            Validator.ensureNotNull(listener);
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
        public Result search(final SearchRequest request,
                final Collection<? super SearchResultEntry> entries) throws ErrorResultException {
            return checkState().search(request, entries);
        }

        @Override
        public Result search(final SearchRequest request,
                final Collection<? super SearchResultEntry> entries,
                final Collection<? super SearchResultReference> references)
                throws ErrorResultException {
            return checkState().search(request, entries, references);
        }

        @Override
        public Result search(final SearchRequest request, final SearchResultHandler handler)
                throws ErrorResultException {
            return checkState().search(request, handler);
        }

        @Override
        public ConnectionEntryReader search(final String baseObject, final SearchScope scope,
                final String filter, final String... attributeDescriptions) {
            return checkState().search(baseObject, scope, filter, attributeDescriptions);
        }

        @Override
        public FutureResult<Result> searchAsync(final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler resultHandler) {
            return checkState().searchAsync(request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public SearchResultEntry searchSingleEntry(final SearchRequest request)
                throws ErrorResultException {
            return checkState().searchSingleEntry(request);
        }

        @Override
        public SearchResultEntry searchSingleEntry(final String baseObject,
                final SearchScope scope, final String filter, final String... attributeDescriptions)
                throws ErrorResultException {
            return checkState().searchSingleEntry(baseObject, scope, filter, attributeDescriptions);
        }

        @Override
        public FutureResult<SearchResultEntry> searchSingleEntryAsync(final SearchRequest request,
                final ResultHandler<? super SearchResultEntry> handler) {
            return checkState().searchSingleEntryAsync(request, handler);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("PooledConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

        // Checks that this pooled connection has not been closed.
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
                idleConnections = new LinkedList<Connection>();
                final long timeoutMillis = currentTimeMillis() - idleTimeoutMillis;
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
                if (DEBUG_LOG.isLoggable(Level.FINE)) {
                    DEBUG_LOG.fine(String.format("Closing %d idle pooled connections: "
                            + "availableConnections=%d, maxPoolSize=%d", idleConnections.size(),
                            currentPoolSize(), maxPoolSize));
                }
                for (final Connection connection : idleConnections) {
                    connection.close();
                }
            }
        }

        private boolean isTimedOutQueuedConnection(final QueueElement holder,
                final long timeoutMillis) {
            return holder != null && !holder.isWaitingFuture() && holder.hasTimedOut(timeoutMillis);
        }
    }

    /**
     * A queue element is either a pending connection request future awaiting an
     * {@code Connection} or it is an unused {@code Connection} awaiting a
     * connection request.
     */
    private static final class QueueElement {
        private final long timestampMillis;
        private final Object value;

        QueueElement(final Connection connection, final long timestampMillis) {
            this.value = connection;
            this.timestampMillis = timestampMillis;
        }

        QueueElement(final ResultHandler<? super Connection> handler, final long timestampMillis) {
            this.value =
                    new AsynchronousFutureResult<Connection, ResultHandler<? super Connection>>(
                            handler);
            this.timestampMillis = timestampMillis;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        Connection getWaitingConnection() {
            if (value instanceof Connection) {
                return (Connection) value;
            } else {
                throw new IllegalStateException();
            }
        }

        @SuppressWarnings("unchecked")
        AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> getWaitingFuture() {
            return (AsynchronousFutureResult<Connection, ResultHandler<? super Connection>>) value;
        }

        boolean hasTimedOut(final long timeLimitMillis) {
            return timestampMillis < timeLimitMillis;
        }

        boolean isWaitingFuture() {
            return value instanceof AsynchronousFutureResult;
        }
    }

    /**
     * This is intended for unit testing only in order to inject fake time
     * stamps. Use System.currentTimeMillis() when null.
     */
    Callable<Long> testTimeSource = null;

    private final Semaphore availableConnections;
    private final ResultHandler<Connection> connectionResultHandler = new ConnectionResultHandler();
    private final int corePoolSize;

    private final ConnectionFactory factory;
    private boolean isClosed = false;
    private final ScheduledFuture<?> idleTimeoutFuture;
    private final long idleTimeoutMillis;
    private final int maxPoolSize;
    private final LinkedList<QueueElement> queue = new LinkedList<QueueElement>();
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;

    CachedConnectionPool(final ConnectionFactory factory, final int corePoolSize,
            final int maximumPoolSize, final long idleTimeout, final TimeUnit unit,
            final ScheduledExecutorService scheduler) {
        Validator.ensureNotNull(factory);
        Validator.ensureTrue(corePoolSize >= 0, "corePoolSize < 0");
        Validator.ensureTrue(maximumPoolSize > 0, "maxPoolSize <= 0");
        Validator.ensureTrue(corePoolSize <= maximumPoolSize, "corePoolSize > maxPoolSize");
        Validator.ensureTrue(idleTimeout >= 0, "idleTimeout < 0");
        Validator.ensureTrue(idleTimeout == 0 || unit != null, "time unit is null");

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
            idleConnections = new LinkedList<Connection>();
            while (hasWaitingConnections()) {
                final QueueElement holder = queue.removeFirst();
                idleConnections.add(holder.getWaitingConnection());
                availableConnections.release();
            }
        }

        if (DEBUG_LOG.isLoggable(Level.FINE)) {
            DEBUG_LOG.fine(String.format(
                    "Connection pool is closing: availableConnections=%d, maxPoolSize=%d",
                    currentPoolSize(), maxPoolSize));
        }

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
    public Connection getConnection() throws ErrorResultException {
        try {
            return getConnectionAsync(null).get();
        } catch (final InterruptedException e) {
            throw newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
        }
    }

    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        // Loop while iterating through stale connections (see OPENDJ-590).
        for (;;) {
            final QueueElement holder;
            synchronized (queue) {
                if (isClosed) {
                    throw new IllegalStateException("CachedConnectionPool is already closed");
                } else if (hasWaitingConnections()) {
                    holder = queue.removeFirst();
                } else {
                    holder = new QueueElement(handler, currentTimeMillis());
                    queue.add(holder);
                }
            }

            if (!holder.isWaitingFuture()) {
                // There was a completed connection attempt.
                final Connection connection = holder.getWaitingConnection();
                if (connection.isValid()) {
                    final PooledConnection pooledConnection = new PooledConnection(connection);
                    if (handler != null) {
                        handler.handleResult(pooledConnection);
                    }
                    return new CompletedFutureResult<Connection>(pooledConnection);
                } else {
                    // Close the stale connection and try again.
                    connection.close();
                    availableConnections.release();

                    if (DEBUG_LOG.isLoggable(Level.FINE)) {
                        DEBUG_LOG.fine(String.format(
                                "Connection no longer valid: availableConnections=%d, poolSize=%d",
                                currentPoolSize(), maxPoolSize));
                    }
                }
            } else {
                // Grow the pool if needed.
                final FutureResult<Connection> future = holder.getWaitingFuture();
                if (!future.isDone() && availableConnections.tryAcquire()) {
                    factory.getConnectionAsync(connectionResultHandler);
                }
                return future;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("CachedConnectionPool(");
        builder.append(String.valueOf(factory));
        builder.append(',');
        builder.append(maxPoolSize);
        builder.append(')');
        return builder.toString();
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

    // Package private for unit testing.
    int currentPoolSize() {
        return maxPoolSize - availableConnections.availablePermits();
    }

    /*
     * This method delegates to System.currentTimeMillis() except in unit tests
     * where we use injected times.
     */
    private long currentTimeMillis() {
        if (testTimeSource == null) {
            return System.currentTimeMillis();
        } else {
            try {
                return testTimeSource.call();
            } catch (final Exception e) {
                // Should not happen.
                throw new RuntimeException(e);
            }
        }
    }

    private boolean hasWaitingConnections() {
        return !queue.isEmpty() && !queue.getFirst().isWaitingFuture();
    }

    private boolean hasWaitingFutures() {
        return !queue.isEmpty() && queue.getFirst().isWaitingFuture();
    }

    private void publishConnection(final Connection connection) {
        final QueueElement holder;
        boolean connectionPoolIsClosing = false;

        synchronized (queue) {
            if (hasWaitingFutures()) {
                connectionPoolIsClosing = isClosed;
                holder = queue.removeFirst();
            } else if (isClosed) {
                connectionPoolIsClosing = true;
                holder = null;
            } else {
                holder = new QueueElement(connection, currentTimeMillis());
                queue.add(holder);
                return;
            }
        }

        // There was waiting future, so complete it.
        if (connectionPoolIsClosing) {
            // The connection will be closed, so decrease the pool size.
            availableConnections.release();
            connection.close();

            if (DEBUG_LOG.isLoggable(Level.FINE)) {
                DEBUG_LOG.fine(String.format(
                        "Closing connection because connection pool is closing: "
                                + "availableConnections=%d, maxPoolSize=%d", currentPoolSize(),
                        maxPoolSize));
            }

            if (holder != null) {
                final ErrorResultException e =
                        ErrorResultException.newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                                ERR_CONNECTION_POOL_CLOSING.get(toString()).toString());
                holder.getWaitingFuture().handleErrorResult(e);

                if (DEBUG_LOG.isLoggable(Level.FINE)) {
                    DEBUG_LOG.fine(String.format(
                            "Connection attempt failed: %s, availableConnections=%d, poolSize=%d",
                            e.getMessage(), currentPoolSize(), maxPoolSize));
                }
            }
        } else {
            holder.getWaitingFuture().handleResult(new PooledConnection(connection));
        }
    }

}
