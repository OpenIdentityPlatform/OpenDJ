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
 *      Portions copyright 2011-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.*;
import static java.lang.System.*;

import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.logging.Level;

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
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

import com.forgerock.opendj.util.AsynchronousFutureResult;
import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.ReferenceCountedObject;
import com.forgerock.opendj.util.Validator;

/**
 * An heart beat connection factory can be used to create connections that sends
 * a periodic search request to a Directory Server.
 */
final class HeartBeatConnectionFactory implements ConnectionFactory {
    /**
     * A connection that sends heart beats and supports all operations.
     */
    private final class ConnectionImpl extends AbstractConnectionWrapper<Connection> implements
            ConnectionEventListener, SearchResultHandler {

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

        /*
         * List of pending Bind or StartTLS requests which must be invoked when
         * the current heart beat completes.
         */
        private final Queue<Runnable> pendingRequests = new ConcurrentLinkedQueue<Runnable>();

        /* Coordinates heart-beats with Bind and StartTLS requests. */
        private final Sync sync = new Sync();

        /*
         * Timestamp of last response received (any response, not just heart
         * beats).
         */
        private volatile long timestamp = currentTimeMillis(); // Assume valid at creation.

        private ConnectionImpl(final Connection connection) {
            super(connection);
        }

        @Override
        public Result add(final AddRequest request) throws ErrorResultException {
            try {
                return timestamp(connection.add(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result add(final Entry entry) throws ErrorResultException {
            try {
                return timestamp(connection.add(entry));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result add(final String... ldifLines) throws ErrorResultException {
            try {
                return timestamp(connection.add(ldifLines));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<Result> addAsync(final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return connection.addAsync(request, intermediateResponseHandler,
                    timestamper(resultHandler));
        }

        @Override
        public BindResult bind(final BindRequest request) throws ErrorResultException {
            acquireBindOrStartTLSLock();
            try {
                return timestamp(connection.bind(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            } finally {
                releaseBindOrStartTLSLock();
            }
        }

        @Override
        public BindResult bind(final String name, final char[] password)
                throws ErrorResultException {
            acquireBindOrStartTLSLock();
            try {
                return timestamp(connection.bind(name, password));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            } finally {
                releaseBindOrStartTLSLock();
            }
        }

        @Override
        public FutureResult<BindResult> bindAsync(final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super BindResult> resultHandler) {
            if (sync.tryLockShared()) {
                // Fast path
                return connection.bindAsync(request, intermediateResponseHandler, timestamper(
                        resultHandler, true));
            } else {
                /*
                 * A heart beat must be in progress so create a runnable task
                 * which will be executed when the heart beat completes.
                 */
                final DelayedFuture<BindResult> future =
                        new DelayedFuture<BindResult>(resultHandler) {
                            @Override
                            public FutureResult<BindResult> dispatch() {
                                return connection.bindAsync(request, intermediateResponseHandler,
                                        timestamper(this, true));
                            }
                        };
                /*
                 * Enqueue and flush if the heart beat has completed in the mean
                 * time.
                 */
                pendingRequests.offer(future);
                flushPendingRequests();
                return future;
            }
        }

        @Override
        public CompareResult compare(final CompareRequest request) throws ErrorResultException {
            try {
                return timestamp(connection.compare(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public CompareResult compare(final String name, final String attributeDescription,
                final String assertionValue) throws ErrorResultException {
            try {
                return timestamp(connection.compare(name, attributeDescription, assertionValue));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<CompareResult> compareAsync(final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super CompareResult> resultHandler) {
            return connection.compareAsync(request, intermediateResponseHandler,
                    timestamper(resultHandler));
        }

        @Override
        public Result delete(final DeleteRequest request) throws ErrorResultException {
            try {
                return timestamp(connection.delete(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result delete(final String name) throws ErrorResultException {
            try {
                return timestamp(connection.delete(name));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<Result> deleteAsync(final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return connection.deleteAsync(request, intermediateResponseHandler,
                    timestamper(resultHandler));
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request)
                throws ErrorResultException {
            final boolean isStartTLS = request.getOID().equals(StartTLSExtendedRequest.OID);
            if (isStartTLS) {
                acquireBindOrStartTLSLock();
            }
            try {
                return timestamp(connection.extendedRequest(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            } finally {
                if (isStartTLS) {
                    releaseBindOrStartTLSLock();
                }
            }
        }

        @Override
        public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
                final IntermediateResponseHandler handler) throws ErrorResultException {
            final boolean isStartTLS = request.getOID().equals(StartTLSExtendedRequest.OID);
            if (isStartTLS) {
                acquireBindOrStartTLSLock();
            }
            try {
                return timestamp(connection.extendedRequest(request, handler));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            } finally {
                if (isStartTLS) {
                    releaseBindOrStartTLSLock();
                }
            }
        }

        @Override
        public GenericExtendedResult extendedRequest(final String requestName,
                final ByteString requestValue) throws ErrorResultException {
            final boolean isStartTLS = requestName.equals(StartTLSExtendedRequest.OID);
            if (isStartTLS) {
                acquireBindOrStartTLSLock();
            }
            try {
                return timestamp(connection.extendedRequest(requestName, requestValue));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            } finally {
                if (isStartTLS) {
                    releaseBindOrStartTLSLock();
                }
            }
        }

        @Override
        public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super R> resultHandler) {
            final boolean isStartTLS = request.getOID().equals(StartTLSExtendedRequest.OID);
            if (isStartTLS) {
                if (sync.tryLockShared()) {
                    // Fast path
                    return connection.extendedRequestAsync(request, intermediateResponseHandler,
                            timestamper(resultHandler, true));
                } else {
                    /*
                     * A heart beat must be in progress so create a runnable
                     * task which will be executed when the heart beat
                     * completes.
                     */
                    final DelayedFuture<R> future = new DelayedFuture<R>(resultHandler) {
                        @Override
                        public FutureResult<R> dispatch() {
                            return connection.extendedRequestAsync(request,
                                    intermediateResponseHandler, timestamper(this, true));
                        }
                    };

                    /*
                     * Enqueue and flush if the heart beat has completed in the
                     * mean time.
                     */
                    pendingRequests.offer(future);
                    flushPendingRequests();
                    return future;
                }
            } else {
                return connection.extendedRequestAsync(request, intermediateResponseHandler,
                        timestamper(resultHandler));
            }
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

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            updateTimestamp();
            return true;
        }

        @Override
        public void handleErrorResult(final ErrorResultException error) {
            if (DEBUG_LOG.isLoggable(Level.FINE)) {
                DEBUG_LOG.fine(String.format("Heartbeat failed: %s", error.getMessage()));
            }
            updateTimestamp();
            releaseHeartBeatLock();
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            updateTimestamp();
            return true;
        }

        @Override
        public void handleResult(final Result result) {
            updateTimestamp();
            releaseHeartBeatLock();
        }

        @Override
        public void handleUnsolicitedNotification(final ExtendedResult notification) {
            updateTimestamp();
        }

        @Override
        public boolean isValid() {
            return connection.isValid() && currentTimeMillis() < (timestamp + timeoutMS);
        }

        @Override
        public Result modify(final ModifyRequest request) throws ErrorResultException {
            try {
                return timestamp(connection.modify(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result modify(final String... ldifLines) throws ErrorResultException {
            try {
                return timestamp(connection.modify(ldifLines));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<Result> modifyAsync(final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return connection.modifyAsync(request, intermediateResponseHandler,
                    timestamper(resultHandler));
        }

        @Override
        public Result modifyDN(final ModifyDNRequest request) throws ErrorResultException {
            try {
                return timestamp(connection.modifyDN(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result modifyDN(final String name, final String newRDN) throws ErrorResultException {
            try {
                return timestamp(connection.modifyDN(name, newRDN));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<? super Result> resultHandler) {
            return connection.modifyDNAsync(request, intermediateResponseHandler,
                    timestamper(resultHandler));
        }

        @Override
        public SearchResultEntry readEntry(final DN name, final String... attributeDescriptions)
                throws ErrorResultException {
            try {
                return timestamp(connection.readEntry(name, attributeDescriptions));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public SearchResultEntry readEntry(final String name, final String... attributeDescriptions)
                throws ErrorResultException {
            try {
                return timestamp(connection.readEntry(name, attributeDescriptions));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<SearchResultEntry> readEntryAsync(final DN name,
                final Collection<String> attributeDescriptions,
                final ResultHandler<? super SearchResultEntry> handler) {
            return connection.readEntryAsync(name, attributeDescriptions, timestamper(handler));
        }

        @Override
        public ConnectionEntryReader search(final SearchRequest request) {
            // Ensure that search results update timestamp.
            return new ConnectionEntryReader(this, request);
        }

        @Override
        public Result search(final SearchRequest request,
                final Collection<? super SearchResultEntry> entries) throws ErrorResultException {
            try {
                return timestamp(connection.search(request, entries));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result search(final SearchRequest request,
                final Collection<? super SearchResultEntry> entries,
                final Collection<? super SearchResultReference> references)
                throws ErrorResultException {
            try {
                return timestamp(connection.search(request, entries, references));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public Result search(final SearchRequest request, final SearchResultHandler handler)
                throws ErrorResultException {
            try {
                return connection.search(request, timestamper(handler));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public ConnectionEntryReader search(final String baseObject, final SearchScope scope,
                final String filter, final String... attributeDescriptions) {
            // Ensure that search results update timestamp.
            final SearchRequest request =
                    Requests.newSearchRequest(baseObject, scope, filter, attributeDescriptions);
            return new ConnectionEntryReader(this, request);
        }

        @Override
        public FutureResult<Result> searchAsync(final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler resultHandler) {
            return connection.searchAsync(request, intermediateResponseHandler,
                    timestamper(resultHandler));
        }

        @Override
        public SearchResultEntry searchSingleEntry(final SearchRequest request)
                throws ErrorResultException {
            try {
                return timestamp(connection.searchSingleEntry(request));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public SearchResultEntry searchSingleEntry(final String baseObject,
                final SearchScope scope, final String filter, final String... attributeDescriptions)
                throws ErrorResultException {
            try {
                return timestamp(connection.searchSingleEntry(baseObject, scope, filter,
                        attributeDescriptions));
            } catch (final ErrorResultException e) {
                throw timestamp(e);
            }
        }

        @Override
        public FutureResult<SearchResultEntry> searchSingleEntryAsync(final SearchRequest request,
                final ResultHandler<? super SearchResultEntry> handler) {
            return connection.searchSingleEntryAsync(request, timestamper(handler));
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("HeartBeatConnection(");
            builder.append(connection);
            builder.append(')');
            return builder.toString();
        }

        private void acquireBindOrStartTLSLock() throws ErrorResultException {
            /*
             * Wait for pending heartbeats and prevent new heartbeats from being
             * sent while the bind is in progress.
             */
            try {
                if (!sync.tryLockShared(timeoutMS, TimeUnit.MILLISECONDS)) {
                    // Give up - it looks like the connection is dead.
                    // FIXME: improve error message.
                    throw newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
                }
            } catch (final InterruptedException e) {
                throw newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
            }
        }

        private void flushPendingRequests() {
            if (!pendingRequests.isEmpty()) {
                /*
                 * The pending requests will acquire the shared lock, but we
                 * take it here anyway to ensure that pending requests do not
                 * get blocked.
                 */
                if (sync.tryLockShared()) {
                    try {
                        Runnable pendingRequest;
                        while ((pendingRequest = pendingRequests.poll()) != null) {
                            pendingRequest.run();
                        }
                    } finally {
                        sync.unlockShared();
                    }
                }
            }
        }

        private void notifyClosed() {
            synchronized (activeConnections) {
                connection.removeConnectionEventListener(this);
                activeConnections.remove(this);
                if (activeConnections.isEmpty()) {
                    /*
                     * This is the last active connection, so stop the
                     * heartbeat.
                     */
                    heartBeatFuture.cancel(false);
                }
            }
        }

        private void releaseBindOrStartTLSLock() {
            sync.unlockShared();
        }

        private void releaseHeartBeatLock() {
            sync.unlockExclusively();
            flushPendingRequests();
        }

        private void sendHeartBeat() {
            /*
             * Only send the heartbeat if the connection has been idle for some
             * time.
             */
            if (currentTimeMillis() < (timestamp + minDelayMS)) {
                return;
            }

            /*
             * Don't send a heart beat if there is already a heart beat, bind,
             * or startTLS in progress. Note that the bind/startTLS response
             * will update the timestamp as if it were a heart beat.
             */
            if (sync.tryLockExclusively()) {
                try {
                    connection.searchAsync(heartBeatRequest, null, this);
                } catch (final Exception e) {
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
        }

        private <R> R timestamp(final R response) {
            updateTimestamp();
            return response;
        }

        private <R> ResultHandler<R> timestamper(final ResultHandler<? super R> handler) {
            return timestamper(handler, false);
        }

        private <R> ResultHandler<R> timestamper(final ResultHandler<? super R> handler,
                final boolean isBindOrStartTLS) {
            return new ResultHandler<R>() {
                @Override
                public void handleErrorResult(final ErrorResultException error) {
                    releaseIfNeeded();
                    if (handler != null) {
                        handler.handleErrorResult(timestamp(error));
                    } else {
                        timestamp(error);
                    }
                }

                @Override
                public void handleResult(final R result) {
                    releaseIfNeeded();
                    if (handler != null) {
                        handler.handleResult(timestamp(result));
                    } else {
                        timestamp(result);
                    }
                }

                private void releaseIfNeeded() {
                    if (isBindOrStartTLS) {
                        releaseBindOrStartTLSLock();
                    }
                }
            };
        }

        private SearchResultHandler timestamper(final SearchResultHandler handler) {
            return new SearchResultHandler() {
                @Override
                public boolean handleEntry(final SearchResultEntry entry) {
                    return handler.handleEntry(timestamp(entry));
                }

                @Override
                public void handleErrorResult(final ErrorResultException error) {
                    handler.handleErrorResult(timestamp(error));
                }

                @Override
                public boolean handleReference(final SearchResultReference reference) {
                    return handler.handleReference(timestamp(reference));
                }

                @Override
                public void handleResult(final Result result) {
                    handler.handleResult(timestamp(result));
                }
            };
        }

        private void updateTimestamp() {
            timestamp = currentTimeMillis();
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
        /* Lock states. Positive values indicate that the shared lock is taken. */
        private static final int UNLOCKED = 0; // initial state
        private static final int LOCKED_EXCLUSIVELY = -1;

        // Keep compiler quiet.
        private static final long serialVersionUID = -3590428415442668336L;

        @Override
        protected boolean isHeldExclusively() {
            return getState() == LOCKED_EXCLUSIVELY;
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

        boolean tryLockShared(final long timeout, final TimeUnit unit) throws InterruptedException {
            return tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        void unlockExclusively() {
            release(0 /* unused */);
        }

        void unlockShared() {
            releaseShared(0 /* unused */);
        }

    }

    private static final SearchRequest DEFAULT_SEARCH = Requests.newSearchRequest("",
            SearchScope.BASE_OBJECT, "(objectClass=*)", "1.1");

    private final List<ConnectionImpl> activeConnections;
    private final ConnectionFactory factory;
    private ScheduledFuture<?> heartBeatFuture;
    private final SearchRequest heartBeatRequest;
    private final long interval;
    private final long minDelayMS;
    private final ReferenceCountedObject<ScheduledExecutorService>.Reference scheduler;
    private final long timeoutMS;
    private final TimeUnit unit;
    private AtomicBoolean isClosed = new AtomicBoolean();

    HeartBeatConnectionFactory(final ConnectionFactory factory) {
        this(factory, 10, TimeUnit.SECONDS, DEFAULT_SEARCH, null);
    }

    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final TimeUnit unit) {
        this(factory, interval, unit, DEFAULT_SEARCH, null);
    }

    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final TimeUnit unit, final SearchRequest heartBeat) {
        this(factory, interval, unit, heartBeat, null);
    }

    HeartBeatConnectionFactory(final ConnectionFactory factory, final long interval,
            final TimeUnit unit, final SearchRequest heartBeat,
            final ScheduledExecutorService scheduler) {
        Validator.ensureNotNull(factory, heartBeat, unit);
        Validator.ensureTrue(interval >= 0, "negative timeout");

        this.heartBeatRequest = heartBeat;
        this.interval = interval;
        this.unit = unit;
        this.activeConnections = new LinkedList<ConnectionImpl>();
        this.factory = factory;
        this.scheduler = DEFAULT_SCHEDULER.acquireIfNull(scheduler);
        this.timeoutMS = unit.toMillis(interval) * 2;
        this.minDelayMS = unit.toMillis(interval) / 2;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized (activeConnections) {
                if (!activeConnections.isEmpty()) {
                    if (DEBUG_LOG.isLoggable(Level.FINE)) {
                        DEBUG_LOG.fine(String.format(
                                "HeartbeatConnectionFactory '%s' is closing while %d "
                                        + "active connections remain", toString(),
                                activeConnections.size()));
                    }
                }
            }
            scheduler.release();
            factory.close();
        }
    }

    @Override
    public Connection getConnection() throws ErrorResultException {
        return adaptConnection(factory.getConnection());
    }

    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        final FutureResultTransformer<Connection, Connection> future =
                new FutureResultTransformer<Connection, Connection>(handler) {
                    @Override
                    protected Connection transformResult(final Connection connection)
                            throws ErrorResultException {
                        return adaptConnection(connection);
                    }
                };

        future.setFutureResult(factory.getConnectionAsync(future));
        return future;
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
        final ConnectionImpl heartBeatConnection = new ConnectionImpl(connection);
        synchronized (activeConnections) {
            connection.addConnectionEventListener(heartBeatConnection);
            if (activeConnections.isEmpty()) {
                /* This is the first active connection, so start the heart beat. */
                heartBeatFuture = scheduler.get().scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        final ConnectionImpl[] tmp;
                        synchronized (activeConnections) {
                            tmp = activeConnections.toArray(new ConnectionImpl[0]);
                        }
                        for (final ConnectionImpl connection : tmp) {
                            connection.sendHeartBeat();
                        }
                    }
                }, 0, interval, unit);
            }
            activeConnections.add(heartBeatConnection);
        }
        return heartBeatConnection;
    }
}
