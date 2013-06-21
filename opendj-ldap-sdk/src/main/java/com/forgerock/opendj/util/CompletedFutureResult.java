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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package com.forgerock.opendj.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;

/**
 * An implementation of {@code FutureResult} which can be used in cases where
 * the result is known in advance, for example, if the result is obtained
 * synchronously.
 *
 * @param <S>
 *            The type of result returned by this future.
 */
public final class CompletedFutureResult<S> implements FutureResult<S> {
    private final S result;

    private final ErrorResultException errorResult;

    private final int requestID;

    /**
     * Creates a new completed future which will throw the provided error result
     * and request ID of {@code -1}.
     *
     * @param errorResult
     *            The error result.
     * @throws NullPointerException
     *             If {@code errorResult} was {@code null}.
     */
    public CompletedFutureResult(final ErrorResultException errorResult) {
        this(errorResult, -1);
    }

    /**
     * Creates a new completed future which will throw the provided error result
     * and request ID.
     *
     * @param errorResult
     *            The error result.
     * @param requestID
     *            The request ID.
     * @throws NullPointerException
     *             If {@code errorResult} was {@code null}.
     */
    public CompletedFutureResult(final ErrorResultException errorResult, final int requestID) {
        Validator.ensureNotNull(errorResult);
        this.result = null;
        this.errorResult = errorResult;
        this.requestID = requestID;
    }

    /**
     * Creates a new completed future which will return the provided result and
     * request ID of {@code -1}.
     *
     * @param result
     *            The result, which may be {@code null}.
     */
    public CompletedFutureResult(final S result) {
        this(result, -1);
    }

    /**
     * Creates a new completed future which will return the provided result and
     * request ID.
     *
     * @param result
     *            The result, which may be {@code null}.
     * @param requestID
     *            The request ID.
     */
    public CompletedFutureResult(final S result, final int requestID) {
        this.result = result;
        this.errorResult = null;
        this.requestID = requestID;
    }

    /**
     * {@inheritDoc}
     */
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public S get() throws ErrorResultException, InterruptedException {
        if (errorResult == null) {
            // May be null.
            return result;
        } else {
            throw errorResult;
        }
    }

    /**
     * {@inheritDoc}
     */
    public S get(final long timeout, final TimeUnit unit) throws ErrorResultException,
            TimeoutException, InterruptedException {
        return get();
    }

    /**
     * {@inheritDoc}
     */
    public int getRequestID() {
        return requestID;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCancelled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDone() {
        return true;
    }

}
