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
 */

package com.forgerock.opendj.util;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ResultHandler;

/**
 * An implementation of the {@code FutureResult} interface which can be used to
 * combine a sequence of two asynchronous operations into a single future
 * result. Implementations should override the methods {@link #chainResult} and
 * {@link #chainErrorResult} in order to define the second asynchronous
 * operation.
 *
 * @param <M>
 *            The type of the inner result.
 * @param <N>
 *            The type of the outer result.
 */
public abstract class RecursiveFutureResult<M, N> implements FutureResult<N>, ResultHandler<M> {

    private final class FutureResultImpl extends AsynchronousFutureResult<N, ResultHandler<? super N>> {

        private FutureResultImpl(final ResultHandler<? super N> handler) {
            super(handler);
        }

        @Override
        public int getRequestID() {
            if (innerFuture instanceof FutureResult<?>) {
                final FutureResult<?> tmp = (FutureResult<?>) innerFuture;
                return tmp.getRequestID();
            } else {
                return -1;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected ErrorResultException handleCancelRequest(final boolean mayInterruptIfRunning) {
            innerFuture.cancel(mayInterruptIfRunning);
            if (outerFuture != null) {
                outerFuture.cancel(mayInterruptIfRunning);
            }
            return null;
        }

    }

    private final FutureResultImpl impl;

    private volatile Future<?> innerFuture = null;

    // This does not need to be volatile since the inner future acts as a
    // memory barrier.
    private FutureResult<? extends N> outerFuture = null;

    /**
     * Creates a new asynchronous result chain which will chain an outer
     * asynchronous request once the inner asynchronous request completes.
     *
     * @param handler
     *            The outer result handler.
     */
    protected RecursiveFutureResult(final ResultHandler<? super N> handler) {
        this.impl = new FutureResultImpl(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        return impl.cancel(mayInterruptIfRunning);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final N get() throws ErrorResultException, InterruptedException {
        return impl.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final N get(final long timeout, final TimeUnit unit) throws ErrorResultException,
            TimeoutException, InterruptedException {
        return impl.get(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int getRequestID() {
        return impl.getRequestID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void handleErrorResult(final ErrorResultException error) {
        try {
            outerFuture = chainErrorResult(error, impl);
        } catch (final ErrorResultException e) {
            impl.handleErrorResult(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void handleResult(final M result) {
        try {
            outerFuture = chainResult(result, impl);
        } catch (final ErrorResultException e) {
            impl.handleErrorResult(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isCancelled() {
        return impl.isCancelled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isDone() {
        return impl.isDone();
    }

    /**
     * Sets the inner future for this result chain. This must be done before
     * this future is published.
     *
     * @param future
     *            The inner future.
     */
    public final void setFutureResult(final Future<?> future) {
        this.innerFuture = future;
    }

    /**
     * Invokes the outer request based on the error result of the inner request
     * and returns a future representing the result of the outer request.
     * <p>
     * The default implementation is to terminate further processing by
     * re-throwing the inner error result.
     *
     * @param innerError
     *            The error result of the inner request.
     * @param handler
     *            The result handler to be used for the outer request.
     * @return A future representing the result of the outer request.
     * @throws ErrorResultException
     *             If the outer request could not be invoked and processing
     *             should terminate.
     */
    protected FutureResult<? extends N> chainErrorResult(final ErrorResultException innerError,
            final ResultHandler<? super N> handler) throws ErrorResultException {
        throw innerError;
    }

    /**
     * Invokes the outer request based on the result of the inner request and
     * returns a future representing the result of the outer request.
     *
     * @param innerResult
     *            The result of the inner request.
     * @param handler
     *            The result handler to be used for the outer request.
     * @return A future representing the result of the outer request.
     * @throws ErrorResultException
     *             If the outer request could not be invoked and processing
     *             should terminate.
     */
    protected abstract FutureResult<? extends N> chainResult(M innerResult,
            ResultHandler<? super N> handler) throws ErrorResultException;

}
