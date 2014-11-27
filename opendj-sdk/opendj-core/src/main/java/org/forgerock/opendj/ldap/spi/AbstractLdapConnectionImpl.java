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
 *      Copyright 2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap.spi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.AbstractAsynchronousConnection;
import org.forgerock.opendj.ldap.CancelledResultException;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.TimeoutEventListener;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.SuccessHandler;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.Connections.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.responses.Responses.*;
import static org.forgerock.opendj.ldap.spi.LdapPromiseImpl.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;



/**
 * An abstract implementation of a LDAP {@link AbstractAsynchronousConnection}.
 *
 * @param <F>
 *            The associated connection factory.
 */
public abstract class AbstractLdapConnectionImpl<F extends AbstractLdapConnectionFactoryImpl>
    extends AbstractAsynchronousConnection implements TimeoutEventListener {
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
    private static final class HeartBeatSynchronizer extends AbstractQueuedSynchronizer {
        /** Keep compiler quiet. */
        private static final long serialVersionUID = -3590428415442668336L;

        /** Lock states. Positive values indicate that the shared lock is taken. */
        private static final int LOCKED_EXCLUSIVELY = -1;
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
     * A request writer which is use to write different kind of {@link Request}.
     *
     * @param <R> The {@link Request} to write.
     */
    protected static abstract interface RequestWriter<R extends Request> {
        /**
         * Use the given {@link LDAPWriter} to write the given {@link Request}.
         *
         * @param messageID
         *            Identifier of the request.
         * @param writer
         *            {@link LDAPWriter} associated to the request.
         * @param request
         *            The request to send to the server.
         * @throws IOException
         *             If any writes problems occurs.
         */
        void writeRequest(int messageID, LDAPWriter<?> writer, R request) throws IOException;
    };

    private static final RequestWriter<AddRequest> ADD_WRITER = new RequestWriter<AddRequest>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, AddRequest request)  throws IOException {
            writer.writeAddRequest(messageID, request);
        };
    };

    private static final RequestWriter<CompareRequest> COMPARE_WRITER = new RequestWriter<CompareRequest>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, CompareRequest request)  throws IOException {
            writer.writeCompareRequest(messageID, request);
        };
    };

    private static final RequestWriter<DeleteRequest> DELETE_WRITER = new RequestWriter<DeleteRequest>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, DeleteRequest request)  throws IOException {
            writer.writeDeleteRequest(messageID, request);
        };
    };

    private static final RequestWriter<ExtendedRequest<?>> EXTENDED_WRITER = new RequestWriter<ExtendedRequest<?>>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, ExtendedRequest<?> request)  throws IOException {
            writer.writeExtendedRequest(messageID, request);
        };
    };

    private static final RequestWriter<ModifyRequest> MODIFY_WRITER = new RequestWriter<ModifyRequest>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, ModifyRequest request)  throws IOException {
            writer.writeModifyRequest(messageID, request);
        };
    };

    private static final RequestWriter<ModifyDNRequest> MODIFY_DN_WRITER = new RequestWriter<ModifyDNRequest>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, ModifyDNRequest request)  throws IOException {
            writer.writeModifyDNRequest(messageID, request);
        };
    };

    private static final RequestWriter<SearchRequest> SEARCH_WRITER = new RequestWriter<SearchRequest>() {
        @Override
        public void writeRequest(int messageID, LDAPWriter<?> writer, SearchRequest request)  throws IOException {
            writer.writeSearchRequest(messageID, request);
        };
    };

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** List of pending Bind or StartTLS requests which must be invoked once the current heart beat completes. */
    private final Queue<Runnable> pendingBindOrStartTLSRequests = new ConcurrentLinkedQueue<Runnable>();

    /** Coordinates heart-beats with Bind and StartTLS requests. */
    private final HeartBeatSynchronizer sync = new HeartBeatSynchronizer();

    /** Timestamp of last response received (any response, not just heart beats). */
    private volatile long lastResponseTimestamp;

    private final AtomicBoolean bindOrStartTLSInProgress = new AtomicBoolean(false);

    private final AtomicInteger msgIDGenerator = new AtomicInteger(1);

    /** Subclasses are responsible for adding and removing pending results. */
    private final ConcurrentHashMap<Integer, ResultLdapPromiseImpl<?, ?>> pendingResults =
            new ConcurrentHashMap<Integer, ResultLdapPromiseImpl<?, ?>>();

    private final F factory;

    /** Internal connection state. */
    protected final ConnectionState state = new ConnectionState();

    /**
     * Creates a connection which was produced by the given factory.
     *
     * @param attachedFactory
     *            The factory of this connection.
     */
    protected AbstractLdapConnectionImpl(F attachedFactory) {
        this.factory = attachedFactory;
        this.lastResponseTimestamp = attachedFactory.getTimeService().now(); // Assume valid at creation.
    }

    /**
     * Returns {@link LDAPOptions} associated to the factory which has produced
     * this connection.
     *
     * @return {@link LDAPOptions} associated to the factory which has produced
     *         this connection.
     */
    public LDAPOptions getLdapOptions() {
        return factory.getLdapOptions();
    }

    @Override
    public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
        /*
         * Need to be careful here since both abandonAsync and Promise.cancel can
         * be called separately by the client application. Therefore
         * promise.cancel() should abandon the request, and abandonAsync should
         * cancel the promise. In addition, bind or StartTLS requests cannot be
         * abandoned.
         */
        try {
            checkConnectionIsValid();
            /*
             * If there is a bind or startTLS in progress then it must be
             * this request which is being abandoned. The following check
             * will prevent it from happening.
             */
            checkBindOrStartTLSInProgress();
        } catch (final LdapException e) {
            return newFailedLdapPromise(e);
        }

        // Remove the promise associated with the request to be abandoned.
        final ResultLdapPromiseImpl<?, ?> pendingRequest = removePendingResult(request.getRequestID());
        if (pendingRequest == null) {
            /*
             * There has never been a request with the specified message ID or
             * the response has already been received and handled. We can ignore
             * this abandon request.
             */
            return newSuccessfulLdapPromise((Void) null);
        }

        /*
         * This will cancel the promise, but will also recursively invoke this
         * method. Since the pending request has been removed, there is no risk
         * of an infinite loop.
         */
        pendingRequest.cancel(false);

        /*
         * FIXME: there's a potential race condition here if a bind or startTLS
         * is initiated just after we removed the pending request.
         */
        return abandonAsync0(newMessageID(), request);
    }

    @Override
    public LdapPromise<Result> addAsync(final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return sendRequest(request, intermediateResponseHandler, ADD_WRITER);
    }

    @Override
    public LdapPromise<BindResult> bindAsync(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        try {
            checkConnectionIsValid();
            if (!isHeartBeatConnection() || sync.tryLockShared()) {
                // Fast path
                return performBindRequest(request, intermediateResponseHandler);
            } else {
                return enqueueBindOrStartTLSPromise(new AsyncFunction<Void, BindResult, LdapException>() {
                    @Override
                    public Promise<BindResult, LdapException> apply(Void value) throws LdapException {
                        return performBindRequest(request, intermediateResponseHandler);
                    }
                });
            }
        } catch (final LdapException e) {
            return newFailedLdapPromise(e);
        }
    }

    @Override
    public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return sendRequest(request, intermediateResponseHandler, COMPARE_WRITER);
    }

    @Override
    public LdapPromise<Result> deleteAsync(final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return sendRequest(request, intermediateResponseHandler, DELETE_WRITER);
    }

    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        try {
            checkConnectionIsValid();
            if (isStartTLSRequest(request)) {
                if (!isHeartBeatConnection() && sync.tryLockShared()) {
                    // Fast path
                    return performStartTLSRequest(request, intermediateResponseHandler);
                } else {
                    return enqueueBindOrStartTLSPromise(new AsyncFunction<Void, R, LdapException>() {
                        @Override
                        public Promise<R, LdapException> apply(Void value) throws LdapException {
                            startBindOrStartTLS();
                            return performStartTLSRequest(request, intermediateResponseHandler);
                        }
                    });
                }
            } else {
                return sendExtendedRequest(request, intermediateResponseHandler);
            }
        } catch (LdapException e) {
            return newFailedLdapPromise(e);
        }
    }

    @Override
    public LdapPromise<Result> modifyAsync(final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return sendRequest(request, intermediateResponseHandler, MODIFY_WRITER);
    }

    @Override
    public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        return sendRequest(request, intermediateResponseHandler, MODIFY_DN_WRITER);
    }

    @Override
    public LdapPromise<Result> searchAsync(final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
        return sendRequest(request, intermediateResponseHandler, entryHandler, SEARCH_WRITER);
    }

    @Override
    public long handleTimeout(final long currentTime) {
        final long timeout = getLdapOptions().getTimeout(MILLISECONDS);
        if (timeout <= 0) {
            return 0;
        }

        long delay = timeout;
        for (final ResultLdapPromiseImpl<?, ?> promise : pendingResults.values()) {
            if (promise == null || !promise.checkForTimeout()) {
                continue;
            }
            final long diff = (promise.getTimestamp() + timeout) - currentTime;
            if (diff > 0) {
                // Will expire in diff milliseconds.
                delay = Math.min(delay, diff);
            } else if (removePendingResult(promise.getRequestID()) == null) {
                // Result arrived at the same time.
                continue;
            } else if (promise.isBindOrStartTLS()) {
                //TODO: set diagnostic messages for timeout (from opendj-grizzly)
                /*
                 * No other operations can be performed while a bind or StartTLS
                 * request is active, so we cannot time out the request. We
                 * therefore have a choice: either ignore timeouts for these
                 * operations, or enforce them but doing so requires
                 * invalidating the connection. We'll do the latter, since
                 * ignoring timeouts could cause the application to hang.
                 */
                logger.debug(LocalizableMessage.raw("Failing bind or StartTLS request due to timeout %s"
                        + "(connection will be invalidated): ", promise));
                final Result result = newResult(CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                    LDAP_CONNECTION_BIND_OR_START_TLS_REQUEST_TIMEOUT.get(timeout).toString());
                promise.adaptErrorResult(result);

                // Fail the connection.
                final Result errorResult = newResult(CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                    LDAP_CONNECTION_BIND_OR_START_TLS_CONNECTION_TIMEOUT.get(timeout).toString());
                connectionErrorOccurred(false, errorResult);
            } else {
                logger.debug(LocalizableMessage.raw("Failing request due to timeout: %s", promise));
                final Result result = newResult(CLIENT_SIDE_TIMEOUT).setDiagnosticMessage(
                    LDAP_CONNECTION_REQUEST_TIMEOUT.get(timeout).toString());

                promise.adaptErrorResult(result);

                /*
                 * FIXME: there's a potential race condition here if a bind or
                 * startTLS is initiated just after we check the boolean. It
                 * seems potentially even more dangerous to send the abandon
                 * request while holding the state lock, since a blocking write
                 * could hang the application.
                 */
                // if (!bindOrStartTLSInProgress.get()) {
                // sendAbandonRequest(newAbandonRequest(promise.getRequestID()));
                // }
            }
        }
        return delay;
    }

    @Override
    public long getTimeout() {
        return getLdapOptions().getTimeout(MILLISECONDS);
    }

    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        state.addConnectionEventListener(listener);
    }

    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        state.removeConnectionEventListener(listener);
    }

    @Override
    public boolean isClosed() {
        return state.isClosed();
    }

    @Override
    public boolean isValid() {
        return state.isValid();
    }

    /**
     * Set this connection state as performing a bind or a startTLS request
     * according to the given boolean.
     *
     * @param state
     *            true if this connection is performing a bind or a startTLS
     *            request.
     */
    public void setBindOrStartTLSInProgress(final boolean state) {
        bindOrStartTLSInProgress.set(state);
    }

    @Override
    public void close(final UnbindRequest request, final String reason) {
        if (state.notifyConnectionClosed()) {
            failPendingResults(newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED,
                                HBCF_CONNECTION_CLOSED_BY_CLIENT.get()));
            factory.deregisterConnection(this);
            close0(newMessageID(), request, reason);
            factory.releaseResources();
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("(").append(getClass().getSimpleName()).append(", ")
               .append("attachedFactory: ").append(factory).append(")");
        return builder.toString();
    };

    /**
     * Returns the factory which has produced this connection.
     *
     * @return The factory which has produced this connection.
     */
    protected F getFactory() {
        return  factory;
    }

    /**
     * Sends the given {@link AbandonRequest} to the server.
     *
     * @param messageID
     *            Identifier of the request.
     * @param request
     *            The request identifying the operation to be abandoned.
     * @return A promise whose result is Void.
     */
    protected abstract LdapPromise<Void> abandonAsync0(final int messageID, final AbandonRequest request);

    /**
     * Sends the given {@link BindRequest} to the server.
     *
     * @param messageID
     *            Identifier of the request.
     * @param request
     *            The bind request.
     * @param bindClient
     *            Bind client which can be used to perform the authentication
     *            process.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @throws LdapException
     *             If a LDAP error occurred.
     */
    protected abstract void bindAsync0(final int messageID, final BindRequest request, final BindClient bindClient,
            final IntermediateResponseHandler intermediateResponseHandler) throws LdapException;

    /**
     * Sends the given {@link UnbindRequest} and closes the underlying socket.
     *
     * @param messageID
     *            Identifier of the request.
     * @param request
     *            The unbind request to use in the case where a physical
     *            connection is closed.
     * @param reason
     *            A reason describing why the connection was closed.
     */
    protected abstract void close0(final int messageID, UnbindRequest request, String reason);

    /**
     * Indicates whether or not TLS is enabled on this connection.
     *
     * @return {@code true} if TLS is enabled on this connection, otherwise {@code false}.
     */
    protected abstract boolean isTLSEnabled();

    /**
     * Sends the given request to the server.
     *
     * @param <R> The request type.
     *
     * @param messageID
     *            Identifier of the request.
     * @param request
     *            The request to send.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @param requestWriter
     *            The writer associated to the request.
     * @throws LdapException
     *             If a LDAP error occurred.
     */
    protected abstract <R extends Request> void writeRequest(final int messageID, final R request,
            final IntermediateResponseHandler intermediateResponseHandler, final RequestWriter<R> requestWriter)
            throws LdapException;

    /**
     * Mark this connection as failed, invoking event listeners as needed.
     *
     * @param isDisconnectNotification
     *            {@code true} if this is a connection failure signaled by a
     *            server disconnect notification.
     * @param reason
     *            The result indicating why the connection has failed.
     */
    protected void connectionErrorOccurred(final boolean isDisconnectNotification, final Result reason) {
        LdapException error = newLdapException(reason);
        if (state.notifyConnectionError(isDisconnectNotification, error)) {
            failPendingResults(error);
        }
    }

    /**
     * Checks the connection state. If the connection is no longer valid, throws
     * the connection {@link LdapException}.
     *
     * @throws LdapException
     *             The connection error.
     */
    protected void checkConnectionIsValid() throws LdapException {
        if (!state.isValid()) {
            /*
             * Connection termination was triggered remotely. We don't want to
             * blindly pass on the result code to requests since it could be
             * confused for a genuine response. For example, if the disconnect
             * contained the invalidCredentials result code then this could be
             * misinterpreted as a genuine authentication failure for subsequent
             * bind requests.
             */
            throw state.getConnectionError() != null ? state.getConnectionError() : newLdapException(
                    ResultCode.CLIENT_SIDE_SERVER_DOWN, "Connection closed by server");
        }
    }

    /**
     * Returns true of the given {@link ExtendedRequest} is a startTLS request.
     *
     * @param request
     *            The extended request to test.
     * @return true of the given {@link ExtendedRequest} is a startTLS request.
     */
    protected boolean isStartTLSRequest(final ExtendedRequest<?> request) {
        return request.getOID().equals(StartTLSExtendedRequest.OID);
    }

    /**
     * Returns true if heart beat is enabled on this connection.
     *
     * @return true if heart beat is enabled on this connection.
     */
    protected boolean isHeartBeatConnection() {
        return factory.isHeartBeatEnabled();
    }

    /**
     * Returns the next pending result identifier available for this connection.
     *
     * @return The next pending result identifier available for this connection.
     */
    protected int newMessageID() {
        return msgIDGenerator.getAndIncrement();
    }

    /**
     * Adds the given pending result to the pending results of this
     * connection.
     *
     * @param messageID
     *            The pending result identifier.
     * @param promise
     *            The pending results.
     */
    protected void addPendingResult(final int messageID, final ResultLdapPromiseImpl<?, ?> promise) {
        if (pendingResults.put(messageID, promise) != null)  {
            throw new IllegalStateException("There is already a pending request with ID: " + messageID);
        }

        promise.onSuccessOrFailure(new Runnable() {
            @Override
            public void run() {
                removePendingResult(messageID);
            }
        });
    }

    /**
     * Returns the pending results of this connection.
     *
     * @return A {@link Map} which represents the pending results of this connection.
     */
    protected Map<Integer, ResultLdapPromiseImpl<?, ?>> getPendingResults() {
        return pendingResults;
    }

    /**
     * Returns the pending result associated to the given identifier, or
     * {@code null} if there is no pending result mapped for the given
     * identifier.
     *
     * @param messageID
     *            The request identifier.
     * @return The pending result associated to the given identifier, or
     *         {@code null} if there is no pending result mapped for the given
     *         identifier.
     */
    protected ResultLdapPromiseImpl<?, ?> getPendingResult(int messageID) {
        return pendingResults.get(messageID);
    }

    /**
     * Removes the pending result for the given identifier, if present.
     *
     * @param messageID
     *            The pending result identifier.
     * @param <R>
     *            The associated request type.
     * @param <S>
     *            The pending result type.
     * @return The pending result associated with the specified identifier, or
     *         {@code null} if absent.
     */
    @SuppressWarnings("unchecked")
    protected <R extends Request, S extends Result> ResultLdapPromiseImpl<R, S> removePendingResult(
            final Integer messageID) {
        return (ResultLdapPromiseImpl<R, S>) pendingResults.remove(messageID);
    }

    /**
     * Notifies connection event listeners that this connection has just
     * received the provided unsolicited notification from the server.
     *
     * @param notification
     *            The unsolicited notification.
     */
    protected void handleUnsolicitedNotification(ExtendedResult notification) {
        if (isHeartBeatConnection()) {
            timestamp(notification);
        }
        state.notifyUnsolicitedNotification(notification);
    }

    /**
     * Sends a heart beat on this connection if required to do so.
     *
     * @return {@code true} if a heart beat was sent, otherwise {@code false}.
     */
    boolean sendHeartBeat() {
        // Don't attempt to send a heart beat if the connection has already failed.
        if (!state.isValid()) {
            return false;
        }

        // Only send the heart beat if the connection has been idle for some time.
        final long currentTimeMillis = factory.getTimeService().now();
        if (currentTimeMillis < (lastResponseTimestamp + factory.getMinimumDelay(MILLISECONDS))) {
            return false;
        }

        /*
         * Don't send a heart beat if there is already a heart beat, bind,
         * or startTLS in progress. Note that the bind/startTLS response
         * will update the lastResponseTimestamp as if it were a heart beat.
         */
        if (sync.tryLockExclusively()) {
            try {
                searchAsync(factory.getLdapOptions().getHeartBeatSearchRequest(), null,
                    new SearchResultHandler() {
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
                    }
                ).onSuccess(new SuccessHandler<Result>() {
                    @Override
                    public void handleResult(final Result result) {
                        timestamp(result);
                        releaseHeartBeatLock();
                    }
                }).onFailure(new FailureHandler<LdapException>() {
                    @Override
                    public void handleError(final LdapException error) {
                        /*
                         * Connection failure will be handled by connection
                         * event listener. Ignore cancellation errors since
                         * these indicate that the heart beat was aborted by a
                         * client-side close.
                         */
                        if (!(error instanceof CancelledResultException)) {
                            /*
                             * Log at debug level to avoid polluting the logs
                             * with benign password policy related errors. See
                             * OPENDJ-1168 and OPENDJ-1167.
                             */
                            logger.debug(LocalizableMessage.raw(
                                    "Heartbeat failed for connection factory '%s'", factory, error));
                            timestamp(error);
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

    void checkForHeartBeat() {
        if (sync.isHeld()) {
            /*
             * A heart beat or bind/startTLS is still in progress, but it
             * should have completed by now. Let's avoid aggressively
             * terminating the connection, because the heart beat may simply
             * have been delayed by a sudden surge of activity. Therefore,
             * only flag the connection as failed if no activity has been
             * seen on the connection since the heart beat was sent.
             */
            final long currentTimeMillis = factory.getTimeService().now();
            final long timeoutMS = factory.getLdapOptions().getTimeout(MILLISECONDS);
            if (lastResponseTimestamp < (currentTimeMillis - timeoutMS)) {
                logger.warn(LocalizableMessage.raw("No heartbeat detected for connection '%s'", this));
                connectionErrorOccurred(false, factory.newHeartBeatTimeoutError().getResult());
            }
        }
    }

    private void releaseHeartBeatLock() {
        sync.unlockExclusively();
        flushPendingBindOrStartTLSRequests();
    }

    private LdapPromise<BindResult> performBindRequest(final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler) throws LdapException {
        startBindOrStartTLS();
        int messageID = newMessageID();
        BindClient context = null;
        try {
            InetSocketAddress socketAddress = factory.getSocketAddress();
            if (socketAddress != null) {
                context = request.createBindClient(getHostString(socketAddress));
            }
        } catch (final LdapException e) {
            releaseBindOrStartTLSLock();
            setBindOrStartTLSInProgress(false);
            return newFailedLdapPromise(e, messageID);
        }

        final BindResultLdapPromiseImpl bindPromise =
                newBindLdapPromise(messageID, request, context, intermediateResponseHandler, this);
        addPendingResult(messageID, bindPromise);
        try {
            bindAsync0(messageID, request, context, intermediateResponseHandler);
            return timestampBindOrStartTLSPromise(bindPromise);
        } catch (LdapException e) {
            removePendingResult(messageID).setResultOrError(e.getResult());
            setBindOrStartTLSInProgress(false);
            releaseBindOrStartTLSLock();
            return bindPromise;
        }
    }

    private <R extends ExtendedResult> LdapPromise<R> performStartTLSRequest(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) throws LdapException {
        if (isTLSEnabled()) {
            return newFailedLdapPromise(newLdapException(request.getResultDecoder().newExtendedErrorResult(
                    ResultCode.OPERATIONS_ERROR, "", "This connection is already TLS enabled")));
        }
        startBindOrStartTLS();
        return sendExtendedRequest(request, intermediateResponseHandler);
    }

    private <R extends ExtendedResult> LdapPromise<R> sendExtendedRequest(final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler) throws LdapException {
        final int messageID = msgIDGenerator.getAndIncrement();
        final ExtendedResultLdapPromiseImpl<R> promise =
                newExtendedLdapPromise(messageID, request, intermediateResponseHandler, this);
        addPendingResult(messageID, promise);
        writeRequest(messageID, request, intermediateResponseHandler, EXTENDED_WRITER);
        return isStartTLSRequest(request) ? timestampBindOrStartTLSPromise(promise) : timestampPromise(promise);
    }

    private <R extends Result, Q extends Request> LdapPromise<R> sendRequest(Q request,
            IntermediateResponseHandler intermediateResponseHandler, RequestWriter<Q> requestWriter) {
        return sendRequest(request, intermediateResponseHandler, null, requestWriter);
    }

    @SuppressWarnings("unchecked")
    private <R extends Result, Q extends Request> LdapPromise<R> sendRequest(Q request,
            IntermediateResponseHandler intermediateResponseHandler, SearchResultHandler entryHandler,
            RequestWriter<Q> requestWriter) {
        final int messageID = newMessageID();
        final ResultLdapPromiseImpl<?, ?> promise =
            createResultPromise(messageID, request, intermediateResponseHandler, entryHandler);
        try {
            checkConnectionIsValid();
            checkBindOrStartTLSInProgress();
            addPendingResult(messageID, promise);
            // Will rollback if the pending request was missed during the close then terminate is here.
            checkConnectionIsValid();

            writeRequest(messageID, request, intermediateResponseHandler, requestWriter);
            return (LdapPromise<R>) timestampPromise(promise);
        } catch (final LdapException e) {
            removePendingResult(messageID);
            return newFailedLdapPromise(e);
        }
    }

    private ResultLdapPromiseImpl<?, ?> createResultPromise(final int messageID, final Request request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler) {
        if (request instanceof CompareRequest) {
            return newCompareLdapPromise(messageID, (CompareRequest) request, intermediateResponseHandler, this);
        } else if (request instanceof SearchRequest) {
            return newSearchLdapPromise(messageID, (SearchRequest) request, entryHandler,
                    intermediateResponseHandler, this);
        }

        return newResultLdapPromise(messageID, request, intermediateResponseHandler, this);
    }

    private <R> R timestamp(final R response) {
        if (!(response instanceof ConnectionException)) {
            lastResponseTimestamp = factory.getTimeService().now();
        }
        return response;
    }

    private <R extends Result> LdapPromise<R> timestampPromise(final LdapPromise<R> promise) {
        if (!isHeartBeatConnection()) {
            //Fast path.
            return promise;
        }

        return promise.onSuccess(new SuccessHandler<R>() {
            @Override
            public void handleResult(R result) {
                if (!(result instanceof ConnectionException)) {
                    lastResponseTimestamp = factory.getTimeService().now();
                }
            }
        }).onFailure(new FailureHandler<LdapException>() {
            @Override
            public void handleError(LdapException error) {
                if (!(error instanceof ConnectionException)) {
                    lastResponseTimestamp = factory.getTimeService().now();
                }
            }
        });
    }

    private <R extends Result> LdapPromise<R> timestampBindOrStartTLSPromise(final LdapPromise<R> wrappedPromise) {
        return timestampPromise(wrappedPromise).onSuccessOrFailure(new Runnable() {
            @Override
            public void run() {
                setBindOrStartTLSInProgress(false);
                releaseBindOrStartTLSLock();
            }
        });
    }

    private void failPendingResults(final LdapException error) {
        // Notification is responsible for removing the element from the map.
        while (!pendingResults.isEmpty()) {
            pendingResults.values().iterator().next().handleError(error);
        }
    }

    private void startBindOrStartTLS() throws LdapException {
        if (!pendingResults.isEmpty()) {
            throw newLdapException(newBindResult(ResultCode.OPERATIONS_ERROR)
                    .setDiagnosticMessage("There are other operations pending on this connection"));
        }

        if (!bindOrStartTLSInProgress.compareAndSet(false, true)) {
            throw newLdapException(newBindResult(ResultCode.OPERATIONS_ERROR)
                    .setDiagnosticMessage("Bind or Start TLS operation in progress"));
        }
    }

    private void checkBindOrStartTLSInProgress() throws LdapException {
        if (bindOrStartTLSInProgress.get()) {
            throw newLdapException(ResultCode.OPERATIONS_ERROR, "Bind or Start TLS operation in progress");
        }
    }

    private <R extends Result> LdapPromise<R> enqueueBindOrStartTLSPromise(
            final AsyncFunction<Void, R, LdapException> doRequest) {
        /*
         * A heart beat must be in progress so create a runnable task which
         * will be executed when the heart beat completes.
         */
        final LdapPromiseImpl<Void> promise = newLdapPromiseImpl();
        final LdapPromise<R> result = promise.thenAsync(doRequest);

        // Enqueue and flush if the heart beat has completed in the mean time.
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

    private void releaseBindOrStartTLSLock() {
        if (isHeartBeatConnection()) {
            sync.unlockShared();
        }
    }

}
