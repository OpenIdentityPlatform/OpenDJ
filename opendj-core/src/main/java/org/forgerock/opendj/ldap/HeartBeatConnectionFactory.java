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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;



import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

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
import org.forgerock.opendj.ldap.spi.ConnectionState;
import org.forgerock.opendj.ldap.spi.LdapPromiseImpl;
import org.forgerock.util.Reject;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;

import com.forgerock.opendj.util.ReferenceCountedObject;
import org.forgerock.util.time.TimeService;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.spi.LdapPromiseImpl.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;
import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

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
     * A connection that sends heart beats and supports all operations.
     */
    private final class ConnectionImpl extends AbstractAsynchronousConnection implements ConnectionEventListener {

        private class LdapPromiseImplWrapper<R> extends LdapPromiseImpl<R> {
            protected LdapPromiseImplWrapper(final LdapPromise<R> wrappedPromise) {
                super(new PromiseImpl<R, LdapException>() {
                    @Override
                    protected LdapException tryCancel(boolean mayInterruptIfRunning) {
                        /*
                         * FIXME: if the inner cancel succeeds then this promise will be
                         * completed and we can never indicate that this cancel request
                         * has succeeded.
                         */
                        wrappedPromise.cancel(mayInterruptIfRunning);
                        return null;
                    }
                }, wrappedPromise.getRequestID());
            }
        }

        /** The wrapped connection. */
        private final Connection connection;

        /**
         * List of pending Bind or StartTLS requests which must be invoked once
         * the current heart beat completes.
         */
        private final Queue<Runnable> pendingBindOrStartTLSRequests =
                new ConcurrentLinkedQueue<>();

        /**
         * List of pending responses for all active operations. These will be
         * signaled if no heart beat is detected within the permitted timeout period.
         */
        private final Queue<LdapResultHandler<?>> pendingResults = new ConcurrentLinkedQueue<>();

        /** Internal connection state. */
        private final ConnectionState state = new ConnectionState();

        /** Coordinates heart-beats with Bind and StartTLS requests. */
        private final Sync sync = new Sync();

        /**
         * Timestamp of last response received (any response, not just heart
         * beats).
         */
        private volatile long lastResponseTimestamp = timeService.now(); // Assume valid at creation.

        private ConnectionImpl(final Connection connection) {
            this.connection = connection;
            connection.addConnectionEventListener(this);
        }

        @Override
        public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
            return connection.abandonAsync(request);
        }

        @Override
        public LdapPromise<Result> addAsync(final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                return timestampPromise(connection.addAsync(request, intermediateResponseHandler));
            } else {
                return newConnectionErrorPromise();
            }
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            state.addConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<BindResult> bindAsync(final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                if (sync.tryLockShared()) {
                    // Fast path
                    return timestampBindOrStartTLSPromise(connection.bindAsync(request, intermediateResponseHandler));
                } else {
                    return enqueueBindOrStartTLSPromise(new AsyncFunction<Void, BindResult, LdapException>() {
                        @Override
                        public Promise<BindResult, LdapException> apply(Void value) throws LdapException {
                            return timestampBindOrStartTLSPromise(connection.bindAsync(request,
                                    intermediateResponseHandler));
                        }
                    });
                }
            } else {
                return newConnectionErrorPromise();
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
        public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                return timestampPromise(connection.compareAsync(request, intermediateResponseHandler));
            } else {
                return newConnectionErrorPromise();
            }
        }

        @Override
        public LdapPromise<Result> deleteAsync(final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                return timestampPromise(connection.deleteAsync(request, intermediateResponseHandler));
            } else {
                return newConnectionErrorPromise();
            }
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                if (isStartTLSRequest(request)) {
                    if (sync.tryLockShared()) {
                        // Fast path
                        return timestampBindOrStartTLSPromise(connection.extendedRequestAsync(request,
                                intermediateResponseHandler));
                    } else {
                        return enqueueBindOrStartTLSPromise(new AsyncFunction<Void, R, LdapException>() {
                            @Override
                            public Promise<R, LdapException> apply(Void value) throws LdapException {
                                return timestampBindOrStartTLSPromise(connection.extendedRequestAsync(
                                        request, intermediateResponseHandler));
                            }
                        });
                    }
                } else {
                    return timestampPromise(connection.extendedRequestAsync(request, intermediateResponseHandler));
                }
            } else {
                return newConnectionErrorPromise();
            }
        }

        @Override
        public void handleConnectionClosed() {
            if (state.notifyConnectionClosed()) {
                failPendingResults(newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                        HBCF_CONNECTION_CLOSED_BY_CLIENT.get()));
                synchronized (validConnections) {
                    connection.removeConnectionEventListener(this);
                    validConnections.remove(this);
                    if (validConnections.isEmpty()) {
                        // This is the last active connection, so stop the heartbeat.
                        heartBeatFuture.cancel(false);
                    }
                }
                releaseScheduler();
            }
        }

        @Override
        public void handleConnectionError(final boolean isDisconnectNotification, final LdapException error) {
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
        public LdapPromise<Result> modifyAsync(final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                return timestampPromise(connection.modifyAsync(request, intermediateResponseHandler));
            } else {
                return newConnectionErrorPromise();
            }
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler) {
            if (checkState()) {
                return timestampPromise(connection.modifyDNAsync(request, intermediateResponseHandler));
            } else {
                return newConnectionErrorPromise();
            }
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            state.removeConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<Result> searchAsync(final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler searchHandler) {
            if (checkState()) {
                final AtomicBoolean searchDone = new AtomicBoolean();
                final SearchResultHandler entryHandler = new SearchResultHandler() {
                    @Override
                    public synchronized boolean handleEntry(SearchResultEntry entry) {
                        if (!searchDone.get()) {
                            timestamp(entry);
                            if (searchHandler != null) {
                                searchHandler.handleEntry(entry);
                            }
                        }
                        return true;
                    }

                    @Override
                    public synchronized boolean handleReference(SearchResultReference reference) {
                        if (!searchDone.get()) {
                            timestamp(reference);
                            if (searchHandler != null) {
                                searchHandler.handleReference(reference);
                            }
                        }
                        return true;
                    }
                };

                return timestampPromise(connection.searchAsync(request,
                        intermediateResponseHandler, entryHandler).thenOnResultOrException(new Runnable() {
                            @Override
                            public void run() {
                                searchDone.getAndSet(true);
                            }
                        }));
            } else {
                return newConnectionErrorPromise();
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
                final long currentTimeMillis = timeService.now();
                if (lastResponseTimestamp < (currentTimeMillis - timeoutMS)) {
                    logger.warn(LocalizableMessage.raw("No heartbeat detected for connection '%s'", connection));
                    handleConnectionError(false, newHeartBeatTimeoutError());
                }
            }
        }

        private boolean checkState() {
            return state.getConnectionError() == null;
        }

        private void failPendingResults(final LdapException error) {
            /*
             * Peek instead of pool because notification is responsible for
             * removing the element from the queue.
             */
            LdapResultHandler<?> pendingResult;
            while ((pendingResult = pendingResults.peek()) != null) {
                pendingResult.handleException(error);
            }
        }

        private void flushPendingBindOrStartTLSRequests() {
            if (!pendingBindOrStartTLSRequests.isEmpty()) {
                /*
                 * The pending requests will acquire the shared lock, but we take
                 * it here anyway to ensure that pending requests do not get blocked.
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

        private <R> LdapPromise<R> newConnectionErrorPromise() {
            return newFailedLdapPromise(state.getConnectionError());
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
         * @return {@code true} if a heart beat was sent, otherwise {@code false}.
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
            final long currentTimeMillis = timeService.now();
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
                    connection.searchAsync(heartBeatRequest, new SearchResultHandler() {
                        @Override
                        public boolean handleEntry(final SearchResultEntry entry) {
                            timestamp(entry);
                            return true;
                        }

                        @Override
                        public boolean handleReference(final SearchResultReference reference) {
                            timestamp(reference);
                            return true;
                        }
                    }).thenOnResult(new org.forgerock.util.promise.ResultHandler<Result>() {
                        @Override
                        public void handleResult(Result result) {
                            timestamp(result);
                            releaseHeartBeatLock();
                        }
                    }).thenOnException(new ExceptionHandler<LdapException>() {
                        @Override
                        public void handleException(LdapException exception) {
                            /*
                             * Connection failure will be handled by connection
                             * event listener. Ignore cancellation errors since
                             * these indicate that the heart beat was aborted by
                             * a client-side close.
                             */
                            if (!(exception instanceof CancelledResultException)) {
                                /*
                                 * Log at debug level to avoid polluting the
                                 * logs with benign password policy related
                                 * errors. See OPENDJ-1168 and OPENDJ-1167.
                                 */
                                logger.debug(LocalizableMessage.raw("Heartbeat failed for connection factory '%s'",
                                        factory, exception));
                                timestamp(exception);
                            }
                            releaseHeartBeatLock();
                        }
                    });
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
                lastResponseTimestamp = timeService.now();
            }
            return response;
        }

        private <R extends Result> LdapPromise<R> enqueueBindOrStartTLSPromise(
                AsyncFunction<Void, R, LdapException> doRequest) {
            /*
             * A heart beat must be in progress so create a runnable task which
             * will be executed when the heart beat completes.
             */
            final LdapPromiseImpl<Void> promise = newLdapPromiseImpl();
            final LdapPromise<R> result = promise.thenAsync(doRequest);

            // Enqueue and flush if the heart beat has completed in the mean
            // time.
            pendingBindOrStartTLSRequests.offer(new Runnable() {
                @Override
                public void run() {
                    // FIXME: Handle cancel chaining.
                    if (!result.isCancelled()) {
                        sync.lockShared(); // Will not block.
                        promise.handleResult((Void) null);
                    }
                }
            });
            flushPendingBindOrStartTLSRequests();
            return result;
        }

        private <R extends Result> LdapPromise<R> timestampPromise(LdapPromise<R> wrappedPromise) {
            final LdapPromiseImpl<R> outerPromise = new LdapPromiseImplWrapper<>(wrappedPromise);
            pendingResults.add(outerPromise);
            wrappedPromise.thenOnResult(new ResultHandler<R>() {
                @Override
                public void handleResult(R result) {
                    outerPromise.handleResult(result);
                    timestamp(result);
                }
            }).thenOnException(new ExceptionHandler<LdapException>() {
                @Override
                public void handleException(LdapException exception) {
                    outerPromise.handleException(exception);
                    timestamp(exception);
                }
            });
            outerPromise.thenOnResultOrException(new Runnable() {
                @Override
                public void run() {
                    pendingResults.remove(outerPromise);
                }
            });
            if (!checkState()) {
                outerPromise.handleException(state.getConnectionError());
            }
            return outerPromise;
        }

        private <R extends Result> LdapPromise<R> timestampBindOrStartTLSPromise(LdapPromise<R> wrappedPromise) {
            return timestampPromise(wrappedPromise).thenOnResultOrException(new Runnable() {
                @Override
                public void run() {
                    releaseBindOrStartTLSLock();
                }
            });
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
        /** Keep compiler quiet. */
        private static final long serialVersionUID = -3590428415442668336L;

        /** Lock states. Positive values indicate that the shared lock is taken. */
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

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
    TimeService timeService = TimeService.SYSTEM;

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

    /** List of valid connections to which heartbeats will be sent. */
    private final List<ConnectionImpl> validConnections = new LinkedList<>();

    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final long timeout, final TimeUnit unit, final SearchRequest heartBeat,
            final ScheduledExecutorService scheduler) {
        Reject.ifNull(factory, unit);
        Reject.ifFalse(interval >= 0, "negative interval");
        Reject.ifFalse(timeout >= 0, "negative timeout");

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
                    logger.debug(LocalizableMessage.raw(
                            "HeartbeatConnectionFactory '%s' is closing while %d active connections remain",
                            this, validConnections.size()));
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
    public Connection getConnection() throws LdapException {
        /*
         * Immediately send a heart beat in order to determine if the connected
         * server is responding.
         */
        acquireScheduler(); // Protect scheduler.
        Connection connection = null;
        try {
            connection = factory.getConnection();
            connection.searchAsync(heartBeatRequest, null).getOrThrow(timeoutMS, TimeUnit.MILLISECONDS);
            return registerConnection(new ConnectionImpl(connection));
        } catch (final Exception e) {
            closeSilently(connection);
            releaseScheduler();
            throw adaptHeartBeatError(e);
        }
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        acquireScheduler(); // Protect scheduler.

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        final PromiseImpl<Connection, LdapException> promise = PromiseImpl.create();

        // Request a connection and return the promise representing the heartbeat.
        factory.getConnectionAsync().thenAsync(new AsyncFunction<Connection, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(final Connection connection) {
                // Save the connection for later once the heart beat completes.
                connectionHolder.set(connection);
                scheduler.get().schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (promise.tryHandleException(newHeartBeatTimeoutError())) {
                            closeSilently(connectionHolder.get());
                            releaseScheduler();
                        }
                    }
                }, timeoutMS, TimeUnit.MILLISECONDS);
                return connection.searchAsync(heartBeatRequest, null);
            }
        }).thenOnResult(new ResultHandler<Result>() {
            @Override
            public void handleResult(Result result) {
                final Connection connection = connectionHolder.get();
                final ConnectionImpl connectionImpl = new ConnectionImpl(connection);
                if (!promise.tryHandleResult(registerConnection(connectionImpl))) {
                    connectionImpl.close();
                }
            }
        }).thenOnException(new ExceptionHandler<LdapException>() {
            @Override
            public void handleException(LdapException exception) {
                if (promise.tryHandleException(adaptHeartBeatError(exception))) {
                    closeSilently(connectionHolder.get());
                    releaseScheduler();
                }
            }
        });

        return promise;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("HeartBeatConnectionFactory(");
        builder.append(factory);
        builder.append(')');
        return builder.toString();
    }

    private Connection registerConnection(final ConnectionImpl heartBeatConnection) {
        synchronized (validConnections) {
            if (validConnections.isEmpty()) {
                /* This is the first active connection, so start the heart beat. */
                heartBeatFuture =
                        scheduler.get().scheduleWithFixedDelay(sendHeartBeatRunnable, 0, interval, intervalUnit);
            }
            validConnections.add(heartBeatConnection);
            return heartBeatConnection;
        }
    }

    private LdapException adaptHeartBeatError(final Exception error) {
        if (error instanceof ConnectionException) {
            return (LdapException) error;
        } else if (error instanceof TimeoutResultException || error instanceof TimeoutException) {
            return newHeartBeatTimeoutError();
        } else if (error instanceof InterruptedException) {
            return newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, error);
        } else {
            return newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN, HBCF_HEARTBEAT_FAILED.get(), error);
        }
    }

    private ConnectionImpl[] getValidConnections() {
        final ConnectionImpl[] tmp;
        synchronized (validConnections) {
            tmp = validConnections.toArray(new ConnectionImpl[0]);
        }
        return tmp;
    }

    private LdapException newHeartBeatTimeoutError() {
        return newLdapException(ResultCode.CLIENT_SIDE_SERVER_DOWN, HBCF_HEARTBEAT_TIMEOUT
                .get(timeoutMS));
    }
}
