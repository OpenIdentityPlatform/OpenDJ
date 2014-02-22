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
 *      Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.DEBUG_LOG;
import static com.forgerock.opendj.util.StaticUtils.DEFAULT_SCHEDULER;
import static org.forgerock.opendj.ldap.CoreMessages.HBCF_CONNECTION_CLOSED_BY_CLIENT;
import static org.forgerock.opendj.ldap.CoreMessages.HBCF_HEARTBEAT_FAILED;
import static org.forgerock.opendj.ldap.CoreMessages.HBCF_HEARTBEAT_TIMEOUT;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.logging.Level;

import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

import com.forgerock.opendj.ldap.ConnectionState;
import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.RecursiveFutureResult;
import com.forgerock.opendj.util.ReferenceCountedObject;
import com.forgerock.opendj.util.TimeSource;
import com.forgerock.opendj.util.Validator;

/**
 * An heart beat connection factory can be used to create connections that sends
 * a periodic search request to a Directory Server.
 * <p>
 * Before returning new connections to the application this factory will first
 * send an initial heart beat in order to determine that the remote server is
 * responsive. If the heart beat fails or times out then the connection is
 * closed immediately and an error returned to the client.
 * <p>
 * Once a connection has been established successfully (including the initial
 * heart beat), this factory will periodically send heart beats on the
 * connection based on the configured heart beat interval. If the heart beat
 * times out then the server is assumed to be down and an appropriate
 * {@link ConnectionException} generated and published to any registered
 * {@link ConnectionEventListener}s. Note however, that heart beats will only be
 * sent when the connection is determined to be reasonably idle: there is no
 * point in sending heart beats if the connection has recently received a
 * response. A connection is deemed to be idle if no response has been received
 * during a period equivalent to half the heart beat interval.
 * <p>
 * The LDAP protocol specifically precludes clients from performing operations
 * while bind or startTLS requests are being performed. Likewise, a bind or
 * startTLS request will cause active operations to be aborted. This factory
 * coordinates heart beats with bind or startTLS requests, ensuring that they
 * are not performed concurrently. Specifically, bind and startTLS requests are
 * queued up while a heart beat is pending, and heart beats are not sent at all
 * while there are pending bind or startTLS requests.
 */
final class HeartBeatConnectionFactory implements ConnectionFactory {

    /**
     * This class is responsible for performing an initial heart beat once a new
     * connection has been obtained and, if the heart beat succeeds, adapting it
     * to a {@code ConnectionImpl} and registering it in the table of valid
     * connections.
     */
    private final class ConnectionFutureResultImpl {

        /**
         * This class handles the initial heart beat result notification or
         * timeout. We need to take care to avoid processing multiple results,
         * which may occur when the heart beat is timed out and a result follows
         * soon after, or vice versa.
         */
        private final class InitialHeartBeatResultHandler implements SearchResultHandler, Runnable {
            private final ResultHandler<? super Result> handler;

            /**
             * Due to a potential race between the heart beat timing out and the
             * heart beat completing this atomic ensures that notification only
             * occurs once.
             */
            private final AtomicBoolean isComplete = new AtomicBoolean();

            private InitialHeartBeatResultHandler(final ResultHandler<? super Result> handler) {
                this.handler = handler;
            }

            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                /*
                 * Depending on the configuration, a heartbeat may return some
                 * entries. However, we can just ignore them.
                 */
                return true;
            }

            @Override
            public void handleErrorResult(final ErrorResultException error) {
                if (isComplete.compareAndSet(false, true)) {
                    handler.handleErrorResult(error);
                }
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                /*
                 * Depending on the configuration, a heartbeat may return some
                 * references. However, we can just ignore them.
                 */
                return true;
            }

            @Override
            public void handleResult(final Result result) {
                if (isComplete.compareAndSet(false, true)) {
                    handler.handleResult(result);
                }
            }

            /*
             * Invoked by the scheduler when the heart beat times out.
             */
            @Override
            public void run() {
                handleErrorResult(newHeartBeatTimeoutError());
            }
        }

        private Connection connection;
        private final RecursiveFutureResult<Connection, Result> futureConnectionResult;
        private final FutureResultTransformer<Result, Connection> futureSearchResult;

        private ConnectionFutureResultImpl(final ResultHandler<? super Connection> handler) {
            // Create a future which will handle the initial heart beat result.
            this.futureSearchResult = new FutureResultTransformer<Result, Connection>(handler) {

                @Override
                protected ErrorResultException transformErrorResult(
                        final ErrorResultException errorResult) {
                    // Ensure that the connection is closed.
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                    releaseScheduler();
                    return adaptHeartBeatError(errorResult);
                }

                @Override
                protected Connection transformResult(final Result result)
                        throws ErrorResultException {
                    return adaptConnection(connection);
                }

            };

            // Create a future which will handle connection result.
            this.futureConnectionResult =
                    new RecursiveFutureResult<Connection, Result>(futureSearchResult) {
                        @Override
                        protected FutureResult<? extends Result> chainResult(
                                final Connection innerResult,
                                final ResultHandler<? super Result> handler)
                                throws ErrorResultException {
                            // Save the connection for later once the heart beat completes.
                            connection = innerResult;

                            /*
                             * Send the initial heart beat and schedule a client
                             * side timeout notification.
                             */
                            final InitialHeartBeatResultHandler wrappedHandler =
                                    new InitialHeartBeatResultHandler(handler);
                            scheduler.get().schedule(wrappedHandler, timeoutMS,
                                    TimeUnit.MILLISECONDS);
                            return connection.searchAsync(heartBeatRequest, null, wrappedHandler);
                        }
                    };

            // Link the two futures.
            futureSearchResult.setFutureResult(futureConnectionResult);
        }

    }

    /**
     * A connection that sends heart beats and supports all operations.
     */
    private final class ConnectionImpl extends AbstractAsynchronousConnection implements
            ConnectionEventListener {

        /**
         * A result handler wrapper for operations which timestamps the
         * connection for each response received. The wrapper ensures that
         * completed requests are removed from the {@code pendingResults} queue,
         * as well as ensuring that requests are only completed once.
         */
        private abstract class AbstractWrappedResultHandler<R, H extends ResultHandler<? super R>>
                implements ResultHandler<R>, FutureResult<R> {
            /** The user provided result handler. */
            protected final H handler;

            private final CountDownLatch completed = new CountDownLatch(1);
            private ErrorResultException error;
            private FutureResult<R> innerFuture;
            private R result;

            AbstractWrappedResultHandler(final H handler) {
                this.handler = handler;
            }

            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                return innerFuture.cancel(mayInterruptIfRunning);
            }

            @Override
            public R get() throws ErrorResultException, InterruptedException {
                completed.await();
                return get0();
            }

            @Override
            public R get(final long timeout, final TimeUnit unit) throws ErrorResultException,
                    TimeoutException, InterruptedException {
                if (completed.await(timeout, unit)) {
                    return get0();
                } else {
                    throw new TimeoutException();
                }
            }

            @Override
            public int getRequestID() {
                return innerFuture.getRequestID();
            }

            @Override
            public void handleErrorResult(final ErrorResultException error) {
                if (tryComplete(null, error)) {
                    if (handler != null) {
                        handler.handleErrorResult(timestamp(error));
                    } else {
                        timestamp(error);
                    }
                }
            }

            @Override
            public void handleResult(final R result) {
                if (tryComplete(result, null)) {
                    if (handler != null) {
                        handler.handleResult(timestamp(result));
                    } else {
                        timestamp(result);
                    }
                }
            }

            @Override
            public boolean isCancelled() {
                return innerFuture.isCancelled();
            }

            @Override
            public boolean isDone() {
                return completed.getCount() == 0;
            }

            abstract void releaseBindOrStartTLSLockIfNeeded();

            FutureResult<R> setInnerFuture(final FutureResult<R> innerFuture) {
                this.innerFuture = innerFuture;
                return this;
            }

            private R get0() throws ErrorResultException {
                if (result != null) {
                    return result;
                } else {
                    throw error;
                }
            }

            /**
             * Attempts to complete this request, returning true if successful.
             * This method is synchronized in order to avoid race conditions
             * with search result processing.
             */
            private synchronized boolean tryComplete(final R result,
                    final ErrorResultException error) {
                if (pendingResults.remove(this)) {
                    this.result = result;
                    this.error = error;
                    completed.countDown();
                    releaseBindOrStartTLSLockIfNeeded();
                    return true;
                } else {
                    return false;
                }
            }

        }

        /**
         * Runs pending request once the shared lock becomes available (when no
         * heart beat is in progress).
         *
         * @param <R>
         *            The type of result returned by the request.
         */
        private abstract class DelayedFuture<R extends Result> extends
                AsynchronousFutureResult<R, ResultHandler<? super R>> implements Runnable {
            private volatile FutureResult<R> innerFuture = null;

            protected DelayedFuture(final ResultHandler<? super R> handler) {
                super(handler);
            }

            @Override
            public final int getRequestID() {
                return innerFuture != null ? innerFuture.getRequestID() : -1;
            }

            @Override
            public final void run() {
                if (!isCancelled()) {
                    sync.lockShared(); // Will not block.
                    innerFuture = dispatch();
                    if (isCancelled() && !innerFuture.isCancelled()) {
                        innerFuture.cancel(false);
                    }
                }
            }

            protected abstract FutureResult<R> dispatch();

            @Override
            protected final ErrorResultException handleCancelRequest(
                    final boolean mayInterruptIfRunning) {
                if (innerFuture != null) {
                    innerFuture.cancel(mayInterruptIfRunning);
                }
                return null;
            }
        }

        /**
         * A result handler wrapper for bind or startTLS requests which releases
         * the bind/startTLS lock on completion.
         */
        private final class WrappedBindOrStartTLSResultHandler<R> extends
                AbstractWrappedResultHandler<R, ResultHandler<? super R>> {
            WrappedBindOrStartTLSResultHandler(final ResultHandler<? super R> handler) {
                super(handler);
            }

            @Override
            void releaseBindOrStartTLSLockIfNeeded() {
                releaseBindOrStartTLSLock();
            }
        }

        /**
         * A result handler wrapper for normal requests which does not release
         * the bind/startTLS lock on completion.
         */
        private final class WrappedResultHandler<R> extends
                AbstractWrappedResultHandler<R, ResultHandler<? super R>> {
            WrappedResultHandler(final ResultHandler<? super R> handler) {
                super(handler);
            }

            @Override
            void releaseBindOrStartTLSLockIfNeeded() {
                // No-op for normal operations.
            }
        }

        /**
         * A result handler wrapper for search operations. Ensures that search
         * results are not sent once the request has been completed (see
         * markComplete()).
         */
        private final class WrappedSearchResultHandler extends
                AbstractWrappedResultHandler<Result, SearchResultHandler> implements
                SearchResultHandler {
            WrappedSearchResultHandler(final SearchResultHandler handler) {
                super(handler);
            }

            @Override
            public synchronized boolean handleEntry(final SearchResultEntry entry) {
                if (!isDone()) {
                    if (handler != null) {
                        handler.handleEntry(timestamp(entry));
                    } else {
                        timestamp(entry);
                    }
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public synchronized boolean handleReference(final SearchResultReference reference) {
                if (!isDone()) {
                    if (handler != null) {
                        handler.handleReference(timestamp(reference));
                    } else {
                        timestamp(reference);
                    }
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            void releaseBindOrStartTLSLockIfNeeded() {
                // No-op for normal operations.
            }
        }

        /** The wrapped connection. */
        private final Connection connection;

        /**
         * Search result handler for processing heart beat responses.
         */
        private final SearchResultHandler heartBeatHandler = new SearchResultHandler() {
            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                timestamp(entry);
                return true;
            }

            @Override
            public void handleErrorResult(final ErrorResultException error) {
                /*
                 * Connection failure will be handled by connection event
                 * listener. Ignore cancellation errors since these indicate
                 * that the heart beat was aborted by a client-side close.
                 */
                if (!(error instanceof CancelledResultException)) {
                    if (DEBUG_LOG.isLoggable(Level.FINE)) {
                        /*
                         * Log at fine level to avoid polluting the logs with
                         * benign password policy related errors. See OPENDJ-1168
                         * and OPENDJ-1167.
                         */
                        DEBUG_LOG.fine(String.format(
                                "Heartbeat failed for connection factory '%s': %s", factory, error
                                        .getMessage()));
                    }
                    timestamp(error);
                }
                releaseHeartBeatLock();
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                timestamp(reference);
                return true;
            }

            @Override
            public void handleResult(final Result result) {
                timestamp(result);
                releaseHeartBeatLock();
            }
        };

        /**
         * List of pending Bind or StartTLS requests which must be invoked once
         * the current heart beat completes.
         */
        private final Queue<Runnable> pendingBindOrStartTLSRequests =
                new ConcurrentLinkedQueue<Runnable>();

        /**
         * List of pending responses for all active operations. These will be
         * signalled if no heart beat is detected within the permitted timeout
         * period.
         */
        private final Queue<AbstractWrappedResultHandler<?, ?>> pendingResults =
                new ConcurrentLinkedQueue<AbstractWrappedResultHandler<?, ?>>();

        /** Internal connection state. */
        private final ConnectionState state = new ConnectionState();

        /** Coordinates heart-beats with Bind and StartTLS requests. */
        private final Sync sync = new Sync();

        /**
         * Timestamp of last response received (any response, not just heart
         * beats).
         */
        private volatile long lastResponseTimestamp = timeSource.currentTimeMillis(); // Assume valid at creation.

        private ConnectionImpl(final Connection connection) {
            this.connection = connection;
            connection.addConnectionEventListener(this);
        }

        @Override
        public FutureResult<Void> abandonAsync(final AbandonRequest request) {
            return connection.abandonAsync(request);
        }

        @Override
        public FutureResult<Result> addAsync(final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            if (checkState(resultHandler)) {
                final WrappedResultHandler<Result> h = wrap(resultHandler);
                return checkState(connection.addAsync(request, intermediateResponseHandler, h), h);
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            state.addConnectionEventListener(listener);
        }

        @Override
        public FutureResult<BindResult> bindAsync(final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super BindResult> resultHandler) {
            if (checkState(resultHandler)) {
                if (sync.tryLockShared()) {
                    // Fast path
                    final WrappedBindOrStartTLSResultHandler<BindResult> h =
                            wrapForBindOrStartTLS(resultHandler);
                    return checkState(
                            connection.bindAsync(request, intermediateResponseHandler, h), h);
                } else {
                    /*
                     * A heart beat must be in progress so create a runnable
                     * task which will be executed when the heart beat
                     * completes.
                     */
                    final DelayedFuture<BindResult> future =
                            new DelayedFuture<BindResult>(resultHandler) {
                                @Override
                                public FutureResult<BindResult> dispatch() {
                                    final WrappedBindOrStartTLSResultHandler<BindResult> h =
                                            wrapForBindOrStartTLS(this);
                                    return checkState(connection.bindAsync(request,
                                            intermediateResponseHandler, h), h);
                                }
                            };
                    /*
                     * Enqueue and flush if the heart beat has completed in the
                     * mean time.
                     */
                    pendingBindOrStartTLSRequests.offer(future);
                    flushPendingBindOrStartTLSRequests();
                    return future;
                }
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public void close() {
            handleConnectionClosed();
            connection.close();
        }

        @Override
        public void close(final UnbindRequest request, final String reason) {
            handleConnectionClosed();
            connection.close(request, reason);
        }

        @Override
        public FutureResult<CompareResult> compareAsync(final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super CompareResult> resultHandler) {
            if (checkState(resultHandler)) {
                final WrappedResultHandler<CompareResult> h = wrap(resultHandler);
                return checkState(connection.compareAsync(request, intermediateResponseHandler, h),
                        h);
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public FutureResult<Result> deleteAsync(final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            if (checkState(resultHandler)) {
                final WrappedResultHandler<Result> h = wrap(resultHandler);
                return checkState(connection.deleteAsync(request, intermediateResponseHandler, h),
                        h);
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super R> resultHandler) {
            if (checkState(resultHandler)) {
                if (isStartTLSRequest(request)) {
                    if (sync.tryLockShared()) {
                        // Fast path
                        final WrappedBindOrStartTLSResultHandler<R> h =
                                wrapForBindOrStartTLS(resultHandler);
                        return checkState(connection.extendedRequestAsync(request,
                                intermediateResponseHandler, h), h);
                    } else {
                        /*
                         * A heart beat must be in progress so create a runnable
                         * task which will be executed when the heart beat
                         * completes.
                         */
                        final DelayedFuture<R> future = new DelayedFuture<R>(resultHandler) {
                            @Override
                            public FutureResult<R> dispatch() {
                                final WrappedBindOrStartTLSResultHandler<R> h =
                                        wrapForBindOrStartTLS(this);
                                return checkState(connection.extendedRequestAsync(request,
                                        intermediateResponseHandler, h), h);
                            }
                        };

                        /*
                         * Enqueue and flush if the heart beat has completed in
                         * the mean time.
                         */
                        pendingBindOrStartTLSRequests.offer(future);
                        flushPendingBindOrStartTLSRequests();
                        return future;
                    }
                } else {
                    final WrappedResultHandler<R> h = wrap(resultHandler);
                    return checkState(connection.extendedRequestAsync(request,
                            intermediateResponseHandler, h), h);
                }
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public void handleConnectionClosed() {
            if (state.notifyConnectionClosed()) {
                failPendingResults(newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                        HBCF_CONNECTION_CLOSED_BY_CLIENT.get()));
                synchronized (validConnections) {
                    connection.removeConnectionEventListener(this);
                    validConnections.remove(this);
                    if (validConnections.isEmpty()) {
                        /*
                         * This is the last active connection, so stop the
                         * heartbeat.
                         */
                        heartBeatFuture.cancel(false);
                    }
                }
                releaseScheduler();
            }
        }

        @Override
        public void handleConnectionError(final boolean isDisconnectNotification,
                final ErrorResultException error) {
            if (state.notifyConnectionError(isDisconnectNotification, error)) {
                failPendingResults(error);
            }
        }

        @Override
        public void handleUnsolicitedNotification(final ExtendedResult notification) {
            timestamp(notification);
            state.notifyUnsolicitedNotification(notification);
        }

        @Override
        public boolean isClosed() {
            return state.isClosed();
        }

        @Override
        public boolean isValid() {
            return state.isValid() && connection.isValid();
        }

        @Override
        public FutureResult<Result> modifyAsync(final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            if (checkState(resultHandler)) {
                final WrappedResultHandler<Result> h = wrap(resultHandler);
                return checkState(connection.modifyAsync(request, intermediateResponseHandler, h),
                        h);
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            if (checkState(resultHandler)) {
                final WrappedResultHandler<Result> h = wrap(resultHandler);
                return checkState(
                        connection.modifyDNAsync(request, intermediateResponseHandler, h), h);
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            state.removeConnectionEventListener(listener);
        }

        @Override
        public FutureResult<Result> searchAsync(final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler resultHandler) {
            if (checkState(resultHandler)) {
                final WrappedSearchResultHandler h = wrap(resultHandler);
                return checkState(connection.searchAsync(request, intermediateResponseHandler, h),
                        h);
            } else {
                return newConnectionErrorFuture();
            }
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("HeartBeatConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

        private void checkForHeartBeat() {
            if (sync.isHeld()) {
                /*
                 * A heart beat or bind/startTLS is still in progress, but it
                 * should have completed by now. Let's avoid aggressively
                 * terminating the connection, because the heart beat may simply
                 * have been delayed by a sudden surge of activity. Therefore,
                 * only flag the connection as failed if no activity has been
                 * seen on the connection since the heart beat was sent.
                 */
                final long currentTimeMillis = timeSource.currentTimeMillis();
                if (lastResponseTimestamp < (currentTimeMillis - timeoutMS)) {
                    if (DEBUG_LOG.isLoggable(Level.WARNING)) {
                        DEBUG_LOG.warning(String.format(
                                "No heartbeat detected for connection '%s'", connection));
                    }
                    handleConnectionError(false, newHeartBeatTimeoutError());
                }
            }
        }

        private <R> FutureResult<R> checkState(final FutureResult<R> future,
                final AbstractWrappedResultHandler<R, ? extends ResultHandler<? super R>> h) {
            h.setInnerFuture(future);
            checkState(h);
            return h;
        }

        private boolean checkState(final ResultHandler<?> h) {
            final ErrorResultException error = state.getConnectionError();
            if (error != null) {
                h.handleErrorResult(error);
                return false;
            } else {
                return true;
            }
        }

        private void failPendingResults(final ErrorResultException error) {
            /*
             * Peek instead of pool because notification is responsible for
             * removing the element from the queue.
             */
            AbstractWrappedResultHandler<?, ?> pendingResult;
            while ((pendingResult = pendingResults.peek()) != null) {
                pendingResult.handleErrorResult(error);
            }
        }

        private void flushPendingBindOrStartTLSRequests() {
            if (!pendingBindOrStartTLSRequests.isEmpty()) {
                /*
                 * The pending requests will acquire the shared lock, but we
                 * take it here anyway to ensure that pending requests do not
                 * get blocked.
                 */
                if (sync.tryLockShared()) {
                    try {
                        Runnable pendingRequest;
                        while ((pendingRequest = pendingBindOrStartTLSRequests.poll()) != null) {
                            // Dispatch the waiting request. This will not block.
                            pendingRequest.run();
                        }
                    } finally {
                        sync.unlockShared();
                    }
                }
            }
        }

        private boolean isStartTLSRequest(final ExtendedRequest<?> request) {
            return request.getOID().equals(StartTLSExtendedRequest.OID);
        }

        private <R> CompletedFutureResult<R> newConnectionErrorFuture() {
            return new CompletedFutureResult<R>(state.getConnectionError());
        }

        private void releaseBindOrStartTLSLock() {
            sync.unlockShared();
        }

        private void releaseHeartBeatLock() {
            sync.unlockExclusively();
            flushPendingBindOrStartTLSRequests();
        }

        /**
         * Sends a heart beat on this connection if required to do so.
         *
         * @return {@code true} if a heart beat was sent, otherwise
         *         {@code false}.
         */
        private boolean sendHeartBeat() {
            /*
             * Don't attempt to send a heart beat if the connection has already
             * failed.
             */
            if (!state.isValid()) {
                return false;
            }

            /*
             * Only send the heart beat if the connection has been idle for some
             * time.
             */
            final long currentTimeMillis = timeSource.currentTimeMillis();
            if (currentTimeMillis < (lastResponseTimestamp + minDelayMS)) {
                return false;
            }

            /*
             * Don't send a heart beat if there is already a heart beat, bind,
             * or startTLS in progress. Note that the bind/startTLS response
             * will update the lastResponseTimestamp as if it were a heart beat.
             */
            if (sync.tryLockExclusively()) {
                try {
                    connection.searchAsync(heartBeatRequest, null, heartBeatHandler);
                } catch (final IllegalStateException e) {
                    /*
                     * This may happen when we attempt to send the heart beat
                     * just after the connection is closed but before we are
                     * notified.
                     */

                    /*
                     * Release the lock because we're never going to get a
                     * response.
                     */
                    releaseHeartBeatLock();
                }
            }
            /*
             * Indicate that a the heartbeat should be checked even if a
             * bind/startTLS is in progress, since these operations will
             * effectively act as the heartbeat.
             */
            return true;
        }

        private <R> R timestamp(final R response) {
            if (!(response instanceof ConnectionException)) {
                lastResponseTimestamp = timeSource.currentTimeMillis();
            }
            return response;
        }

        private <R> WrappedResultHandler<R> wrap(final ResultHandler<? super R> handler) {
            final WrappedResultHandler<R> h = new WrappedResultHandler<R>(handler);
            pendingResults.add(h);
            return h;
        }

        private WrappedSearchResultHandler wrap(final SearchResultHandler handler) {
            final WrappedSearchResultHandler h = new WrappedSearchResultHandler(handler);
            pendingResults.add(h);
            return h;
        }

        private <R> WrappedBindOrStartTLSResultHandler<R> wrapForBindOrStartTLS(
                final ResultHandler<? super R> handler) {
            final WrappedBindOrStartTLSResultHandler<R> h =
                    new WrappedBindOrStartTLSResultHandler<R>(handler);
            pendingResults.add(h);
            return h;
        }
    }

    /**
     * This synchronizer prevents Bind or StartTLS operations from being
     * processed concurrently with heart-beats. This is required because the
     * LDAP protocol specifically states that servers receiving a Bind operation
     * should either wait for existing operations to complete or abandon them.
     * The same presumably applies to StartTLS operations. Note that concurrent
     * bind/StartTLS operations are not permitted.
     * <p>
     * This connection factory only coordinates Bind and StartTLS requests with
     * heart-beats. It does not attempt to prevent or control attempts to send
     * multiple concurrent Bind or StartTLS operations, etc.
     * <p>
     * This synchronizer can be thought of as cross between a read-write lock
     * and a semaphore. Unlike a read-write lock there is no requirement that a
     * thread releasing a lock must hold it. In addition, this synchronizer does
     * not support reentrancy. A thread attempting to acquire exclusively more
     * than once will deadlock, and a thread attempting to acquire shared more
     * than once will succeed and be required to release an equivalent number of
     * times.
     * <p>
     * The synchronizer has three states:
     * <ul>
     * <li>UNLOCKED(0) - the synchronizer may be acquired shared or exclusively
     * <li>LOCKED_EXCLUSIVELY(-1) - the synchronizer is held exclusively and
     * cannot be acquired shared or exclusively. An exclusive lock is held while
     * a heart beat is in progress
     * <li>LOCKED_SHARED(>0) - the synchronizer is held shared and cannot be
     * acquired exclusively. N shared locks are held while N Bind or StartTLS
     * operations are in progress.
     * </ul>
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final int LOCKED_EXCLUSIVELY = -1;
        // Keep compiler quiet.
        private static final long serialVersionUID = -3590428415442668336L;

        /* Lock states. Positive values indicate that the shared lock is taken. */
        private static final int UNLOCKED = 0; // initial state

        @Override
        protected boolean isHeldExclusively() {
            return getState() == LOCKED_EXCLUSIVELY;
        }

        boolean isHeld() {
            return getState() != 0;
        }

        @Override
        protected boolean tryAcquire(final int ignored) {
            if (compareAndSetState(UNLOCKED, LOCKED_EXCLUSIVELY)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected int tryAcquireShared(final int readers) {
            for (;;) {
                final int state = getState();
                if (state == LOCKED_EXCLUSIVELY) {
                    return LOCKED_EXCLUSIVELY; // failed
                }
                final int newState = state + readers;
                if (compareAndSetState(state, newState)) {
                    return newState; // succeeded + more readers allowed
                }
            }
        }

        @Override
        protected boolean tryRelease(final int ignored) {
            if (getState() != LOCKED_EXCLUSIVELY) {
                throw new IllegalMonitorStateException();
            }
            setExclusiveOwnerThread(null);
            setState(UNLOCKED);
            return true;
        }

        @Override
        protected boolean tryReleaseShared(final int ignored) {
            for (;;) {
                final int state = getState();
                if (state == UNLOCKED || state == LOCKED_EXCLUSIVELY) {
                    throw new IllegalMonitorStateException();
                }
                final int newState = state - 1;
                if (compareAndSetState(state, newState)) {
                    /*
                     * We could always return true here, but since there cannot
                     * be waiting readers we can specialize for waiting writers.
                     */
                    return newState == UNLOCKED;
                }
            }
        }

        void lockShared() {
            acquireShared(1);
        }

        boolean tryLockExclusively() {
            return tryAcquire(0 /* unused */);
        }

        boolean tryLockShared() {
            return tryAcquireShared(1) > 0;
        }

        void unlockExclusively() {
            release(0 /* unused */);
        }

        void unlockShared() {
            releaseShared(0 /* unused */);
        }

    }

    /**
     * Default heart beat which will target the root DSE but not return any
     * results.
     */
    private static final SearchRequest DEFAULT_SEARCH = Requests.newSearchRequest("",
            SearchScope.BASE_OBJECT, "(objectClass=*)", "1.1");

    /**
     * This is package private in order to allow unit tests to inject fake time
     * stamps.
     */
    TimeSource timeSource = TimeSource.DEFAULT;

    /**
     * Scheduled task which checks that all heart beats have been received
     * within the timeout period.
     */
    private final Runnable checkHeartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            for (final ConnectionImpl connection : getValidConnections()) {
                connection.checkForHeartBeat();
            }
        }
    };

    /**
     * Underlying connection factory.
     */
    private final ConnectionFactory factory;

    /**
     * The heartbeat scheduled future - which may be null if heartbeats are not
     * being sent (no valid connections).
     */
    private ScheduledFuture<?> heartBeatFuture;

    /**
     * The heartbeat search request.
     */
    private final SearchRequest heartBeatRequest;

    /**
     * The interval between successive heartbeats.
     */
    private final long interval;
    private final TimeUnit intervalUnit;

    /**
     * Flag which indicates whether this factory has been closed.
     */
    private final AtomicBoolean isClosed = new AtomicBoolean();

    /**
     * The minimum amount of time the connection should remain idle (no
     * responses) before starting to send heartbeats.
     */
    private final long minDelayMS;

    /**
     * Prevents the scheduler being released when there are remaining references
     * (this factory or any connections). It is initially set to 1 because this
     * factory has a reference.
     */
    private final AtomicInteger referenceCount = new AtomicInteger(1);

    /**
     * The heartbeat scheduler.
     */
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;


    /**
     * Scheduled task which sends heart beats for all valid idle connections.
     */
    private final Runnable sendHeartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            boolean heartBeatSent = false;
            for (final ConnectionImpl connection : getValidConnections()) {
                heartBeatSent |= connection.sendHeartBeat();
            }
            if (heartBeatSent) {
                scheduler.get().schedule(checkHeartBeatRunnable, timeoutMS, TimeUnit.MILLISECONDS);
            }
        }
    };

    /**
     * The heartbeat timeout in milli-seconds. The connection will be marked as
     * failed if no heartbeat response is received within the timeout.
     */
    private final long timeoutMS;

    /**
     * List of valid connections to which heartbeats will be sent.
     */
    private final List<ConnectionImpl> validConnections = new LinkedList<ConnectionImpl>();

    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final long timeout, final TimeUnit unit, final SearchRequest heartBeat,
            final ScheduledExecutorService scheduler) {
        Validator.ensureNotNull(factory, unit);
        Validator.ensureTrue(interval >= 0, "negative interval");
        Validator.ensureTrue(timeout >= 0, "negative timeout");

        this.heartBeatRequest = heartBeat != null ? heartBeat : DEFAULT_SEARCH;
        this.interval = interval;
        this.intervalUnit = unit;
        this.factory = factory;
        this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(scheduler);
        this.timeoutMS = unit.toMillis(timeout);
        this.minDelayMS = unit.toMillis(interval) / 2;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized (validConnections) {
                if (!validConnections.isEmpty()) {
                    if (DEBUG_LOG.isLoggable(Level.FINE)) {
                        DEBUG_LOG.fine(String.format(
                                "HeartbeatConnectionFactory '%s' is closing while %d "
                                        + "active connections remain", toString(), validConnections
                                        .size()));
                    }
                }
            }
            releaseScheduler();
            factory.close();
        }
    }

    private void releaseScheduler() {
        if (referenceCount.decrementAndGet() == 0) {
            scheduler.release();
        }
    }

    private void acquireScheduler() {
        /*
         * If the factory is not closed then we need to prevent the scheduler
         * from being released while the connection attempt is in progress.
         */
        referenceCount.incrementAndGet();
        if (isClosed.get()) {
            releaseScheduler();
            throw new IllegalStateException("Attempted to get a connection after factory close");
        }
    }

    @Override
    public Connection getConnection() throws ErrorResultException {
        /*
         * Immediately send a heart beat in order to determine if the connected
         * server is responding.
         */
        acquireScheduler(); // Protect scheduler.
        boolean succeeded = false;
        try {
            final Connection connection = factory.getConnection();
            try {
                connection.searchAsync(heartBeatRequest, null, null).get(timeoutMS,
                        TimeUnit.MILLISECONDS);
                succeeded = true;
                return adaptConnection(connection);
            } catch (final Exception e) {
                throw adaptHeartBeatError(e);
            } finally {
                if (!succeeded) {
                    connection.close();
                }
            }
        } finally {
            if (!succeeded) {
                releaseScheduler();
            }
        }
    }

    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        acquireScheduler(); // Protect scheduler.

        // Create a future responsible for chaining the initial heartbeat search.
        final ConnectionFutureResultImpl compositeFuture = new ConnectionFutureResultImpl(handler);

        // Request a connection.
        final FutureResult<Connection> connectionFuture =
                factory.getConnectionAsync(compositeFuture.futureConnectionResult);

        // Set the connection future in the composite so that the returned search future can delegate.
        compositeFuture.futureConnectionResult.setFutureResult(connectionFuture);

        // Return the future representing the heartbeat.
        return compositeFuture.futureSearchResult;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("HeartBeatConnectionFactory(");
        builder.append(String.valueOf(factory));
        builder.append(')');
        return builder.toString();
    }

    private Connection adaptConnection(final Connection connection) {
        synchronized (validConnections) {
            final ConnectionImpl heartBeatConnection = new ConnectionImpl(connection);
            if (validConnections.isEmpty()) {
                /* This is the first active connection, so start the heart beat. */
                heartBeatFuture =
                        scheduler.get().scheduleWithFixedDelay(sendHeartBeatRunnable, 0, interval,
                                intervalUnit);
            }
            validConnections.add(heartBeatConnection);
            return heartBeatConnection;
        }
    }

    private ErrorResultException adaptHeartBeatError(final Exception error) {
        if (error instanceof ConnectionException) {
            return (ErrorResultException) error;
        } else if (error instanceof TimeoutResultException || error instanceof TimeoutException) {
            return newHeartBeatTimeoutError();
        } else if (error instanceof InterruptedException) {
            return newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED, error);
        } else {
            return newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN, HBCF_HEARTBEAT_FAILED.get(),
                    error);
        }
    }

    private ConnectionImpl[] getValidConnections() {
        final ConnectionImpl[] tmp;
        synchronized (validConnections) {
            tmp = validConnections.toArray(new ConnectionImpl[0]);
        }
        return tmp;
    }

    private ErrorResultException newHeartBeatTimeoutError() {
        return newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN, HBCF_HEARTBEAT_TIMEOUT
                .get(timeoutMS));
    }
}
