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

package org.forgerock.opendj.ldap.spi;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.SuccessHandler;

import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

/**
 * Provides a {@link Promise} wrapper and a {@link LdapPromise} implementation.
 *
 *
 * This wrapper allows client code to return {@link LdapPromise} instance when
 * using {@link Promise} chaining methods (e.g onSuccess(), then(), thenAsync()).
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
    public LdapPromise<R> onFailure(FailureHandler<? super LdapException> onFailure) {
        wrappedPromise.onFailure(onFailure);
        return this;
    }

    @Override
    public LdapPromise<R> onSuccess(SuccessHandler<? super R> onSuccess) {
        wrappedPromise.onSuccess(onSuccess);
        return this;
    }

    @Override
    public LdapPromise<R> onSuccessOrFailure(Runnable onSuccessOrFailure) {
        wrappedPromise.onSuccessOrFailure(onSuccessOrFailure);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT> LdapPromise<VOUT> then(Function<? super R, VOUT, LdapException> onSuccess) {
        return wrap(wrappedPromise.then(onSuccess), getRequestID());
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> then(Function<? super R, VOUT, EOUT> onSuccess,
            Function<? super LdapException, VOUT, EOUT> onFailure) {
        return wrappedPromise.then(onSuccess, onFailure);
    }

    @Override
    public LdapPromise<R> then(SuccessHandler<? super R> onSuccess) {
        wrappedPromise.then(onSuccess);
        return this;
    }

    @Override
    public LdapPromise<R> then(SuccessHandler<? super R> onSuccess,
            FailureHandler<? super LdapException> onFailure) {
        wrappedPromise.then(onSuccess, onFailure);
        return this;
    }

    @Override
    public LdapPromise<R> thenAlways(Runnable onSuccessOrFailure) {
        wrappedPromise.thenAlways(onSuccessOrFailure);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT> LdapPromise<VOUT> thenAsync(AsyncFunction<? super R, VOUT, LdapException> onSuccess) {
        return wrap(wrappedPromise.thenAsync(onSuccess), getRequestID());
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> thenAsync(
            AsyncFunction<? super R, VOUT, EOUT> onSuccess,
            AsyncFunction<? super LdapException, VOUT, EOUT> onFailure) {
        return wrappedPromise.thenAsync(onSuccess, onFailure);
    }

    public P getWrappedPromise() {
        return wrappedPromise;
    }

}
