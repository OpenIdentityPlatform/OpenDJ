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
 */

package org.forgerock.opendj.ldap;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A handle which can be used to retrieve the Result of an asynchronous Request.
 *
 * @param <S>
 *            The type of result returned by this future.
 */
public interface FutureResult<S> extends Future<S> {
    /**
     * Attempts to cancel the request. This attempt will fail if the request has
     * already completed or has already been cancelled. If successful, then
     * cancellation results in an abandon or cancel request (if configured)
     * being sent to the server.
     * <p>
     * After this method returns, subsequent calls to {@link #isDone} will
     * always return {@code true}. Subsequent calls to {@link #isCancelled} will
     * always return {@code true} if this method returned {@code true}.
     *
     * @param mayInterruptIfRunning
     *            {@code true} if the thread executing executing the response
     *            handler should be interrupted; otherwise, in-progress response
     *            handlers are allowed to complete.
     * @return {@code false} if the request could not be cancelled, typically
     *         because it has already completed normally; {@code true}
     *         otherwise.
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Waits if necessary for the request to complete, and then returns the
     * result if the request succeeded. If the request failed (i.e. a
     * non-successful result code was obtained) then the result is thrown as an
     * {@link ErrorResultException}.
     *
     * @return The result, but only if the result code indicates that the
     *         request succeeded.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws InterruptedException
     *             If the current thread was interrupted while waiting.
     */
    S get() throws ErrorResultException, InterruptedException;

    /**
     * Waits if necessary for at most the given time for the request to
     * complete, and then returns the result if the request succeeded. If the
     * request failed (i.e. a non-successful result code was obtained) then the
     * result is thrown as an {@link ErrorResultException}.
     *
     * @param timeout
     *            The maximum time to wait.
     * @param unit
     *            The time unit of the timeout argument.
     * @return The result, but only if the result code indicates that the
     *         request succeeded.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws TimeoutException
     *             If the wait timed out.
     * @throws InterruptedException
     *             If the current thread was interrupted while waiting.
     */
    S get(long timeout, TimeUnit unit) throws ErrorResultException, TimeoutException,
            InterruptedException;

    /**
     * Returns the request ID of the request if appropriate.
     *
     * @return The request ID, or {@code -1} if there is no request ID.
     */
    int getRequestID();

    /**
     * Returns {@code true} if the request was cancelled before it completed
     * normally.
     *
     * @return {@code true} if the request was cancelled before it completed
     *         normally, otherwise {@code false}.
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if the request has completed.
     * <p>
     * Completion may be due to normal termination, an exception, or
     * cancellation. In all of these cases, this method will return {@code true}.
     *
     * @return {@code true} if the request has completed, otherwise
     *         {@code false}.
     */
    boolean isDone();
}
