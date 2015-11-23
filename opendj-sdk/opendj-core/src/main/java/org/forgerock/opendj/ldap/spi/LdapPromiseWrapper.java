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
 *      Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;

import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

/**
 * Provides a {@link Promise} wrapper and a {@link LdapPromise} implementation.
 *
 *
 * This wrapper allows client code to return {@link LdapPromise} instance when
 * using {@link Promise} chaining methods (e.g thenOnResult(), then(), thenAsync()).
 * Wrapping is specially needed with {@link Promise} method which are not returning the original promise.
 *
 * @param <R>
 *       The type of the task's result.
 * @param <P>
 *       The wrapped promise.
 */
class LdapPromiseWrapper<R, P extends Promise<R, LdapException>> implements LdapPromise<R> {
    private final P wrappedPromise;
    private final int requestID;

    public LdapPromiseWrapper(P wrappedPromise, int requestID) {
        this.wrappedPromise = wrappedPromise;
        this.requestID = requestID;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int getRequestID() {
        return wrappedPromise instanceof LdapPromise ? ((LdapPromise<R>) wrappedPromise).getRequestID()
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
    public R getOrThrow() throws InterruptedException, LdapException {
        return wrappedPromise.getOrThrow();
    }

    @Override
    public R getOrThrow(long timeout, TimeUnit unit) throws InterruptedException, LdapException, TimeoutException {
        return wrappedPromise.getOrThrow(timeout, unit);
    }

    @Override
    public R getOrThrowUninterruptibly() throws LdapException {
        return wrappedPromise.getOrThrowUninterruptibly();
    }

    @Override
    public R getOrThrowUninterruptibly(long timeout, TimeUnit unit) throws LdapException, TimeoutException {
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
    public LdapPromise<R> thenOnException(ExceptionHandler<? super LdapException> onException) {
        wrappedPromise.thenOnException(onException);
        return this;
    }

    @Override
    public LdapPromise<R> thenOnRuntimeException(RuntimeExceptionHandler onRuntimeException) {
        wrappedPromise.thenOnRuntimeException(onRuntimeException);
        return this;
    }

    @Override
    public LdapPromise<R> thenOnResult(ResultHandler<? super R> onResult) {
        wrappedPromise.thenOnResult(onResult);
        return this;
    }

    @Override
    public LdapPromise<R> thenOnResultOrException(Runnable onResultOrException) {
        wrappedPromise.thenOnResultOrException(onResultOrException);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT> LdapPromise<VOUT> then(Function<? super R, VOUT, LdapException> onResult) {
        return wrap(wrappedPromise.then(onResult), getRequestID());
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> then(Function<? super R, VOUT, EOUT> onResult,
            Function<? super LdapException, VOUT, EOUT> onException) {
        return wrappedPromise.then(onResult, onException);
    }

    @Override
    public LdapPromise<R> thenOnResultOrException(ResultHandler<? super R> onResult,
                                                  ExceptionHandler<? super LdapException> onException) {
        wrappedPromise.thenOnResultOrException(onResult, onException);
        return this;
    }

    @Override
    public LdapPromise<R> thenAlways(Runnable onResultOrException) {
        wrappedPromise.thenAlways(onResultOrException);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT> LdapPromise<VOUT> thenAsync(AsyncFunction<? super R, VOUT, LdapException> onResult) {
        return wrap(wrappedPromise.thenAsync(onResult), getRequestID());
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> thenAsync(
            AsyncFunction<? super R, VOUT, EOUT> onResult,
            AsyncFunction<? super LdapException, VOUT, EOUT> onException) {
        return wrappedPromise.thenAsync(onResult, onException);
    }

    @Override
    // @Checkstyle:ignore
    public <EOUT extends Exception> Promise<R, EOUT> thenCatch(Function<? super LdapException, R, EOUT> onException) {
        return wrappedPromise.thenCatch(onException);
    }

    @Override
    public LdapPromise<R> thenFinally(Runnable onResultOrException) {
        wrappedPromise.thenFinally(onResultOrException);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <EOUT extends Exception> Promise<R, EOUT> thenCatchAsync(
            AsyncFunction<? super LdapException, R, EOUT> onException) {
        return wrappedPromise.thenCatchAsync(onException);
    }

    public P getWrappedPromise() {
        return wrappedPromise;
    }

}
