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
 *      Portions copyright 2013-2014 ForgeRock AS.
 */

package com.forgerock.opendj.util;

import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.forgerock.opendj.ldap.CancelledResultException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;

/**
 * This class provides a skeletal implementation of the {@code FutureResult}
 * interface, to minimize the effort required to implement this interface.
 * <p>
 * This {@code FutureResult} implementation provides the following features:
 * <ul>
 * <li>The {@link #get} methods throw {@link ErrorResultException}s instead of
 * the more generic {@code ExecutionException}s.
 * <li>The {@link #get} methods never throw {@code CancellationException} since
 * requests in this SDK can usually be cancelled via other external means (e.g.
 * the {@code Cancel} extended operation) for which there are well defined error
 * results. Therefore cancellation is always signalled by throwing a
 * {@link CancelledResultException} in order to be consistent with other error
 * results.
 * <li>A {@link ResultHandler} can be provided to the constructor. The result
 * handler will be invoked immediately after the result or error is received but
 * before threads blocked on {@link #get} are released. More specifically,
 * result handler invocation <i>happens-before</i> a call to {@link #get}.
 * <b>NOTE:</b> a result handler which attempts to call {@link #get} will
 * deadlock.
 * <li>Sub-classes may choose to implement specific cancellation cleanup by
 * implementing the {@link #handleCancelRequest} method.
 * </ul>
 *
 * @param <M>
 *          The type of result returned by this future.
 * @param <H>
 *          The type of {@link ResultHandler} associated to this future.
 */
public class AsynchronousFutureResult<M, H extends ResultHandler<? super M>> implements
    FutureResult<M>, ResultHandler<M> {

    @SuppressWarnings("serial")
    private final class Sync extends AbstractQueuedSynchronizer {
        // State value representing the initial state before a result has
        // been received.
        private static final int WAITING = 0;

        // State value representing that a result has been received and is
        // being processed.
        private static final int PENDING = 1;

        // State value representing that the request was cancelled.
        private static final int CANCELLED = 2;

        // State value representing that the request has failed.
        private static final int FAIL = 3;

        // State value representing that the request has succeeded.
        private static final int SUCCESS = 4;

        // These do not need to be volatile since their values are published
        // by updating the state after they are set and reading the state
        // immediately before they are read.
        private ErrorResultException errorResult = null;

        private M result = null;

        /**
         * Allow all threads to acquire if future has completed.
         */
        @Override
        protected int tryAcquireShared(final int ignore) {
            return innerIsDone() ? 1 : -1;
        }

        /**
         * Signal that the future has completed and threads waiting on get() can
         * be released.
         */
        @Override
        protected boolean tryReleaseShared(final int finalState) {
            // Ensures that errorResult/result is published.
            setState(finalState);
            return true;
        }

        boolean innerCancel(final boolean mayInterruptIfRunning) {
            if (!isCancelable() || !setStatePending()) {
                return false;
            }

            // Perform implementation defined cancellation.
            ErrorResultException errorResult = handleCancelRequest(mayInterruptIfRunning);
            if (errorResult == null) {
                errorResult = newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED);
            }
            this.errorResult = errorResult;

            try {
                // Invoke error result completion handler.
                if (handler != null) {
                    handler.handleErrorResult(errorResult);
                }
            } finally {
                releaseShared(CANCELLED); // Publishes errorResult.
            }

            return true;
        }

        M innerGet() throws ErrorResultException, InterruptedException {
            acquireSharedInterruptibly(0);
            return get0();
        }

        M innerGet(final long nanosTimeout) throws ErrorResultException, TimeoutException,
                InterruptedException {
            if (!tryAcquireSharedNanos(0, nanosTimeout)) {
                throw new TimeoutException();
            } else {
                return get0();
            }
        }

        boolean innerIsCancelled() {
            return getState() == CANCELLED;
        }

        boolean innerIsDone() {
            return getState() > 1;
        }

        boolean innerSetErrorResult(final ErrorResultException errorResult) {
            if (!setStatePending()) {
                return false;
            }
            this.errorResult = errorResult;
            try {
                // Invoke error result completion handler.
                if (handler != null) {
                    handler.handleErrorResult(errorResult);
                }
            } finally {
                releaseShared(FAIL); // Publishes errorResult.
            }
            return true;
        }

        boolean innerSetResult(final M result) {
            if (!setStatePending()) {
                return false;
            }
            this.result = result;
            try {
                // Invoke result completion handler.
                if (handler != null) {
                    handler.handleResult(result);
                }
            } finally {
                releaseShared(SUCCESS); // Publishes result.
            }
            return true;
        }

        private M get0() throws ErrorResultException {
            if (errorResult != null) {
                // State must be FAILED or CANCELLED.
                throw errorResult;
            } else {
                // State must be SUCCESS.
                return result;
            }
        }

        private boolean setStatePending() {
            for (;;) {
                final int s = getState();
                if (s != WAITING) {
                    return false;
                }
                if (compareAndSetState(s, PENDING)) {
                    return true;
                }
            }
        }
    }

    private final Sync sync = new Sync();

    private final H handler;

    private final int requestID;

    /**
     * Creates a new asynchronous future result with the provided result handler
     * and a request ID of -1.
     *
     * @param handler
     *            A result handler which will be forwarded the result or error
     *            when it arrives, may be {@code null}.
     */
    public AsynchronousFutureResult(final H handler) {
        this(handler, -1);
    }

    /**
     * Creates a new asynchronous future result with the provided result handler
     * and request ID.
     *
     * @param handler
     *            A result handler which will be forwarded the result or error
     *            when it arrives, may be {@code null}.
     * @param requestID
     *            The request ID which will be returned by the default
     *            implementation of {@link #getRequestID}.
     */
    public AsynchronousFutureResult(final H handler, final int requestID) {
        this.handler = handler;
        this.requestID = requestID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        return sync.innerCancel(mayInterruptIfRunning);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final M get() throws ErrorResultException, InterruptedException {
        return sync.innerGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final M get(final long timeout, final TimeUnit unit) throws ErrorResultException,
            TimeoutException, InterruptedException {
        return sync.innerGet(unit.toNanos(timeout));
    }

    /**
     * Returns the result handler associated to this FutureResult.
     *
     * @return the result handler associated to this FutureResult.
     */
    public H getResultHandler() {
        return handler;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default implementation returns the request ID passed in during
     * construction, or -1 if none was provided.
     */
    @Override
    public int getRequestID() {
        return requestID;
    }

    /**
     * Sets the error result associated with this future. If ({@code isDone() ==
     * true}) then the error result will be ignored, otherwise the result
     * handler will be invoked if one was provided and, on return, any threads
     * waiting on {@link #get} will be released and the provided error result
     * will be thrown.
     *
     * @param errorResult
     *            The error result.
     */
    @Override
    public final void handleErrorResult(final ErrorResultException errorResult) {
        sync.innerSetErrorResult(errorResult);
    }

    /**
     * Sets the result associated with this future. If ({@code isDone() == true}
     * ) then the result will be ignored, otherwise the result handler will be
     * invoked if one was provided and, on return, any threads waiting on
     * {@link #get} will be released and the provided result will be returned.
     *
     * @param result
     *            The result.
     */
    @Override
    public final void handleResult(final M result) {
        sync.innerSetResult(result);
    }

    /**
     * Attempts to set the error result associated with this future. If (i.e.
     * {@code isDone() == true}) then the error result will be ignored and
     * {@code false} will be returned, otherwise the result handler will be
     * invoked if one was provided and, on returning {@code true}, any threads
     * waiting on {@link #get} will be released and the provided error result
     * will be thrown.
     *
     * @param errorResult
     *            The error result.
     * @return {@code false} if this future has already been completed, either
     *         due to normal termination, an exception, or cancellation (i.e.
     *         {@code isDone() == true}).
     */
    public final boolean tryHandleErrorResult(final ErrorResultException errorResult) {
        return sync.innerSetErrorResult(errorResult);
    }

    /**
     * Attempts to set the result associated with this future. If (i.e.
     * {@code isDone() == true}) then the result will be ignored and
     * {@code false} will be returned, otherwise the result handler will be
     * invoked if one was provided and, on returning {@code true}, any threads
     * waiting on {@link #get} will be released and the provided result will be
     * returned.
     *
     * @param result
     *            The result.
     * @return {@code false} if this future has already been completed, either
     *         due to normal termination, an exception, or cancellation (i.e.
     *         {@code isDone() == true}).
     */
    public final boolean tryHandleResult(final M result) {
        return sync.innerSetResult(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isCancelled() {
        return sync.innerIsCancelled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isDone() {
        return sync.innerIsDone();
    }

    /**
     * Invoked when {@link #cancel} is called and {@code isDone() == false} and
     * immediately before any threads waiting on {@link #get} are released.
     * Implementations may choose to return a custom error result if needed or
     * return {@code null} if the following default error result is acceptable:
     *
     * <pre>
     * Result result = Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED);
     * </pre>
     *
     * In addition, implementations may perform other cleanup, for example, by
     * issuing an LDAP abandon request. The default implementation is to do
     * nothing.
     *
     * @param mayInterruptIfRunning
     *            {@code true} if the thread executing executing the response
     *            handler should be interrupted; otherwise, in-progress response
     *            handlers are allowed to complete.
     * @return The custom error result, or {@code null} if the default is
     *         acceptable.
     */
    protected ErrorResultException handleCancelRequest(final boolean mayInterruptIfRunning) {
        // Do nothing by default.
        return null;
    }

    /**
     * Indicates whether this future result can be canceled.
     *
     * @return {@code true} if this future result is cancelable or {@code false}
     *         otherwise.
     */
    protected boolean isCancelable() {
        // Return true by default.
        return true;
    }

    /**
     * Appends a string representation of this future's state to the provided
     * builder.
     *
     * @param sb
     *            The string builder.
     */
    protected void toString(final StringBuilder sb) {
        sb.append(" state = ");
        sb.append(sync);
    }

}
