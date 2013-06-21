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

import static com.forgerock.opendj.util.StaticUtils.*;

import static org.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
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
import com.forgerock.opendj.util.Validator;

/**
 * A simple connection pool implementation which maintains a fixed number of
 * connections.
 */
final class FixedConnectionPool implements ConnectionPool {

    /**
     * This result handler is invoked when an attempt to add a new connection to
     * the pool completes.
     */
    private final class ConnectionResultHandler implements ResultHandler<Connection> {

        @Override
        public void handleErrorResult(final ErrorResultException error) {
            // Connection attempt failed, so decrease the pool size.
            currentPoolSize.release();

            if (DEBUG_LOG.isLoggable(Level.FINE)) {
                DEBUG_LOG.fine(String.format(
                        "Connection attempt failed: %s, currentPoolSize=%d, poolSize=%d", error
                                .getMessage(), poolSize - currentPoolSize.availablePermits(),
                        poolSize));
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
                        "Connection attempt succeeded:  currentPoolSize=%d, poolSize=%d", poolSize
                                - currentPoolSize.availablePermits(), poolSize));
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
        public Result applyChange(ChangeRecord request) throws ErrorResultException {
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
                 * currentPoolSize.
                 */
                connection.close();
                factory.getConnectionAsync(connectionResultHandler);

                if (DEBUG_LOG.isLoggable(Level.FINE)) {
                    DEBUG_LOG.fine(String.format(
                            "Connection no longer valid: currentPoolSize=%d, poolSize=%d", poolSize
                                    - currentPoolSize.availablePermits(), poolSize));
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
     * A queue element is either a pending connection request future awaiting an
     * {@code Connection} or it is an unused {@code Connection} awaiting a
     * connection request.
     */
    private static final class QueueElement {
        private final Object value;

        QueueElement(final Connection connection) {
            this.value = connection;
        }

        QueueElement(final ResultHandler<? super Connection> handler) {
            this.value =
                    new AsynchronousFutureResult<Connection, ResultHandler<? super Connection>>(
                            handler);
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

        boolean isWaitingFuture() {
            return value instanceof AsynchronousFutureResult;
        }
    }

    private final ResultHandler<Connection> connectionResultHandler = new ConnectionResultHandler();
    private final Semaphore currentPoolSize;
    private final ConnectionFactory factory;
    private boolean isClosed = false;
    private final int poolSize;
    private final LinkedList<QueueElement> queue = new LinkedList<QueueElement>();

    FixedConnectionPool(final ConnectionFactory factory, final int poolSize) {
        this.factory = factory;
        this.poolSize = poolSize;
        this.currentPoolSize = new Semaphore(poolSize);
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
            }
        }

        if (DEBUG_LOG.isLoggable(Level.FINE)) {
            DEBUG_LOG.fine(String.format(
                    "Connection pool is closing: currentPoolSize=%d, poolSize=%d", poolSize
                            - currentPoolSize.availablePermits(), poolSize));
        }

        // Close the idle connections.
        for (final Connection connection : idleConnections) {
            closeConnection(connection);
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
                    throw new IllegalStateException("FixedConnectionPool is already closed");
                }

                if (hasWaitingConnections()) {
                    holder = queue.removeFirst();
                } else {
                    holder = new QueueElement(handler);
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
                    currentPoolSize.release();

                    if (DEBUG_LOG.isLoggable(Level.FINE)) {
                        DEBUG_LOG.fine(String.format(
                                "Connection no longer valid: currentPoolSize=%d, poolSize=%d",
                                poolSize - currentPoolSize.availablePermits(), poolSize));
                    }
                }
            } else {
                // Grow the pool if needed.
                final FutureResult<Connection> future = holder.getWaitingFuture();
                if (!future.isDone() && currentPoolSize.tryAcquire()) {
                    factory.getConnectionAsync(connectionResultHandler);
                }
                return future;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("FixedConnectionPool(");
        builder.append(String.valueOf(factory));
        builder.append(',');
        builder.append(poolSize);
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

    private void closeConnection(final Connection connection) {
        // The connection will be closed, so decrease the pool size.
        currentPoolSize.release();
        connection.close();

        if (DEBUG_LOG.isLoggable(Level.FINE)) {
            DEBUG_LOG.fine(String.format("Closing connection because connection pool is closing: "
                    + "currentPoolSize=%d, poolSize=%d", poolSize
                    - currentPoolSize.availablePermits(), poolSize));
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
            } else {
                if (isClosed) {
                    connectionPoolIsClosing = true;
                    holder = null;
                } else {
                    holder = new QueueElement(connection);
                    queue.add(holder);
                    return;
                }
            }
        }

        // There was waiting future, so complete it.
        if (connectionPoolIsClosing) {
            closeConnection(connection);

            if (holder != null) {
                final ErrorResultException e =
                        ErrorResultException.newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                                ERR_CONNECTION_POOL_CLOSING.get(toString()).toString());
                holder.getWaitingFuture().handleErrorResult(e);

                if (DEBUG_LOG.isLoggable(Level.FINE)) {
                    DEBUG_LOG.fine(String.format(
                            "Connection attempt failed: %s, currentPoolSize=%d, poolSize=%d", e
                                    .getMessage(), poolSize - currentPoolSize.availablePermits(),
                            poolSize));
                }
            }
        } else {
            holder.getWaitingFuture().handleResult(new PooledConnection(connection));
        }
    }

}
