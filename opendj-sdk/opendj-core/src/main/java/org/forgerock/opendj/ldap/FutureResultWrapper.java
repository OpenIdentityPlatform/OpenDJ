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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.SuccessHandler;

/**
 * This class is a {@link Promise} wrapper which implements {@link FutureResult} interface.
 *
 * It allows client code to return {@link FutureResult} instance when using {@link Promise}
 * chaining methods (e.g onSuccess(), then(), thenAsync()).
 *
 * Wrapping is specially needed with {@link Promise} method which are not returning
 * the original promise (i.e this) but a new one.
 *
 * It also provides some useful methods to create completed
 * {@link FutureResult} instance.
 *
 *
 * @param <R>
 *            The type of the task's result, or {@link Void} if the task does
 *            not return anything (i.e. it only has side-effects).
 */
public final class FutureResultWrapper<R> {
    private static class LdapPromiseWrapper<R> implements FutureResult<R> {
        private final Promise<R, ErrorResultException> wrappedPromise;
        private final int requestID;

        LdapPromiseWrapper(Promise<R, ErrorResultException> wrappedPromise, int requestID) {
            this.wrappedPromise = wrappedPromise;
            this.requestID = requestID;
        }

        @Override
        public int getRequestID() {
            return wrappedPromise instanceof FutureResult ? ((FutureResult<R>) wrappedPromise).getRequestID()
                : requestID;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return wrappedPromise.cancel(mayInterruptIfRunning);
        }

        @Override
        public R get() throws ExecutionException, InterruptedException {
            return wrappedPromise.get();
        }

        @Override
        public R get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException, InterruptedException {
            return wrappedPromise.get(timeout, unit);
        }

        @Override
        public R getOrThrow() throws InterruptedException, ErrorResultException {
            return wrappedPromise.getOrThrow();
        }

        @Override
        public R getOrThrow(long timeout, TimeUnit unit) throws InterruptedException, ErrorResultException,
            TimeoutException {
            return wrappedPromise.getOrThrow(timeout, unit);
        }

        @Override
        public R getOrThrowUninterruptibly() throws ErrorResultException {
            return wrappedPromise.getOrThrowUninterruptibly();
        }

        @Override
        public R getOrThrowUninterruptibly(long timeout, TimeUnit unit) throws ErrorResultException, TimeoutException {
            return wrappedPromise.getOrThrowUninterruptibly(timeout, unit);
        }

        @Override
        public boolean isCancelled() {
            return wrappedPromise.isCancelled();
        }

        @Override
        public boolean isDone() {
            return wrappedPromise.isDone();
        }

        @Override
        public Promise<R, ErrorResultException> onFailure(FailureHandler<? super ErrorResultException> onFailure) {
            wrappedPromise.onFailure(onFailure);
            return this;
        }

        @Override
        public Promise<R, ErrorResultException> onSuccess(SuccessHandler<? super R> onSuccess) {
            wrappedPromise.onSuccess(onSuccess);
            return this;
        }

        @Override
        public Promise<R, ErrorResultException> onSuccessOrFailure(Runnable onSuccessOrFailure) {
            wrappedPromise.onSuccessOrFailure(onSuccessOrFailure);
            return this;
        }

        @Override
        // @Checkstyle:ignore
        public <VOUT> Promise<VOUT, ErrorResultException> then(
                Function<? super R, VOUT, ErrorResultException> onSuccess) {
            return new LdapPromiseWrapper<VOUT>(wrappedPromise.then(onSuccess), getRequestID());
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        // @Checkstyle:ignore
        public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> then(Function<? super R, VOUT, EOUT> onSuccess,
                Function<? super ErrorResultException, VOUT, EOUT> onFailure) {
            return new LdapPromiseWrapper(wrappedPromise.then(onSuccess, onFailure), getRequestID());
        }

        @Override
        public Promise<R, ErrorResultException> then(SuccessHandler<? super R> onSuccess) {
            wrappedPromise.then(onSuccess);
            return this;
        }

        @Override
        public Promise<R, ErrorResultException> then(SuccessHandler<? super R> onSuccess,
            FailureHandler<? super ErrorResultException> onFailure) {
            wrappedPromise.then(onSuccess, onFailure);
            return this;
        }

        @Override
        public Promise<R, ErrorResultException> thenAlways(Runnable onSuccessOrFailure) {
            wrappedPromise.thenAlways(onSuccessOrFailure);
            return this;
        }

        @Override
        // @Checkstyle:ignore
        public <VOUT> Promise<VOUT, ErrorResultException> thenAsync(
                AsyncFunction<? super R, VOUT, ErrorResultException> onSuccess) {
            return new LdapPromiseWrapper<VOUT>(wrappedPromise.thenAsync(onSuccess), getRequestID());
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        // @Checkstyle:ignore
        public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> thenAsync(
                AsyncFunction<? super R, VOUT, EOUT> onSuccess,
                AsyncFunction<? super ErrorResultException, VOUT, EOUT> onFailure) {
            return new LdapPromiseWrapper(wrappedPromise.thenAsync(onSuccess, onFailure), getRequestID());
        }
    }

    /**
     * Returns a {@link FutureResult} representing an asynchronous task which
     * has already succeeded with the provided result. Attempts to get the
     * result will immediately return the result.
     *
     * @param <R>
     *            The type of the task's result, or {@link Void} if the task
     *            does not return anything (i.e. it only has side-effects).
     * @param result
     *            The result of the asynchronous task.
     * @return A {@link FutureResult} representing an asynchronous task which
     *         has already succeeded with the provided result.
     */
    public static <R> FutureResult<R> newSuccessfulFutureResult(final R result) {
        return new LdapPromiseWrapper<R>(Promises.<R, ErrorResultException> newSuccessfulPromise(result), -1);
    }

    /**
     * Returns a {@link FutureResult} representing an asynchronous task,
     * identified by the provided requestID, which has already succeeded with
     * the provided result. Attempts to get the result will immediately return
     * the result.
     *
     * @param <R>
     *            The type of the task's result, or {@link Void} if the task
     *            does not return anything (i.e. it only has side-effects).
     * @param result
     *            The result of the asynchronous task.
     * @param requestID
     *            The request ID of the succeeded task.
     * @return A {@link FutureResult} representing an asynchronous task which
     *         has already succeeded with the provided result.
     */
    public static <R> FutureResult<R> newSuccessfulFutureResult(final R result, int requestID) {
        return new LdapPromiseWrapper<R>(Promises.<R, ErrorResultException> newSuccessfulPromise(result), requestID);
    }

    /**
     * Returns a {@link FutureResult} representing an asynchronous task which
     * has already failed with the provided error.
     *
     * @param <R>
     *            The type of the task's result, or {@link Void} if the task
     *            does not return anything (i.e. it only has side-effects).
     * @param <E>
     *            The type of the exception thrown by the task if it fails.
     * @param error
     *            The exception indicating why the asynchronous task has failed.
     * @return A {@link FutureResult} representing an asynchronous task which
     *         has already failed with the provided error.
     */
    public static <R, E extends ErrorResultException> FutureResult<R> newFailedFutureResult(final E error) {
        return new LdapPromiseWrapper<R>(Promises.<R, ErrorResultException> newFailedPromise(error), -1);
    }

    /**
     * Returns a {@link FutureResult} representing an asynchronous task,
     * identified by the provided requestID, which has already failed with the
     * provided error.
     *
     * @param <R>
     *            The type of the task's result, or {@link Void} if the task
     *            does not return anything (i.e. it only has side-effects).
     * @param <E>
     *            The type of the exception thrown by the task if it fails.
     * @param error
     *            The exception indicating why the asynchronous task has failed.
     * @param requestID
     *            The request ID of the failed task.
     * @return A {@link FutureResult} representing an asynchronous task which
     *         has already failed with the provided error.
     */
    public static <R, E extends ErrorResultException> FutureResult<R> newFailedFutureResult(final E error,
            int requestID) {
        return new LdapPromiseWrapper<R>(Promises.<R, ErrorResultException> newFailedPromise(error), requestID);
    }

    /**
     * Converts a {@link Promise} to a {@link FutureResult}.
     *
     * @param <R>
     *            The type of the task's result, or {@link Void} if the task
     *            does not return anything (i.e. it only has side-effects).
     * @param wrappedPromise
     *            The {@link Promise} to wrap.
     * @return A {@link FutureResult} representing the same asynchronous task as
     *         the {@link Promise} provided.
     */
    public static <R> FutureResult<R> asFutureResult(Promise<R, ErrorResultException> wrappedPromise) {
        return new LdapPromiseWrapper<R>(wrappedPromise, -1);
    }

    private FutureResultWrapper() {
    }
}
