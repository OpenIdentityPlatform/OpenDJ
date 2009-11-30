/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



/**
 * A handle which can be used to retrieve a requested {@code
 * AsynchronousConnection}.
 * <p>
 * TODO: Do we want to throw an ErrorResultException? I think we do
 * because exceptions are not limited to connection related errors. For
 * example, a transacted connection would already have a physical
 * connection; an error could occur when sending the start txn extended
 * op.
 *
 * @param <C>
 *          The type of asynchronous connection returned by this
 *          connection future.
 */
public interface ConnectionFuture<C extends AsynchronousConnection>
    extends Future<C>
{
  /**
   * Attempts to cancel the request. This attempt will fail if the
   * request has already completed or has already been cancelled. If
   * successful, then cancellation results in an abandon or cancel
   * request (if configured) being sent to the server.
   * <p>
   * After this method returns, subsequent calls to {@link #isDone} will
   * always return {@code true}. Subsequent calls to
   * {@link #isCancelled} will always return {@code true} if this method
   * returned {@code true}.
   *
   * @param mayInterruptIfRunning
   *          {@code true} if the thread executing executing the
   *          response handler should be interrupted; otherwise,
   *          in-progress response handlers are allowed to complete.
   * @return {@code false} if the request could not be cancelled,
   *         typically because it has already completed normally;
   *         {@code true} otherwise.
   */
  boolean cancel(boolean mayInterruptIfRunning);



  /**
   * Waits if necessary for the connection request to complete, and then
   * returns the asynchronous connection if the connection request
   * succeeded. If the connection request failed (i.e. a non-successful
   * result code was obtained) then a {@link ErrorResultException} is
   * thrown.
   *
   * @return The asynchronous connection, but only if the the connection
   *         request succeeded.
   * @throws CancellationException
   *           If the connection request was cancelled using a call to
   *           {@link #cancel}.
   * @throws ErrorResultException
   *           If the connection request failed for some reason.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting.
   */
  C get() throws InterruptedException, ErrorResultException;



  /**
   * Waits if necessary for at most the given time for the connection
   * request to complete, and then returns the asynchronous connection
   * if the connection request succeeded. If the connection request
   * failed (i.e. a non-successful result code was obtained) then a
   * {@link ErrorResultException} is thrown.
   *
   * @param timeout
   *          The maximum time to wait.
   * @param unit
   *          The time unit of the timeout argument.
   * @return The asynchronous connection, but only if the the connection
   *         request succeeded.
   * @throws CancellationException
   *           If the connection request was cancelled using a call to
   *           {@link #cancel}.
   * @throws ErrorResultException
   *           If the connection request failed for some reason.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting.
   * @throws TimeoutException
   *           If the wait timed out.
   */
  C get(long timeout, TimeUnit unit) throws InterruptedException,
      TimeoutException, ErrorResultException;



  /**
   * Returns {@code true} if the connection request was cancelled before
   * it completed normally.
   *
   * @return {@code true} if the connection request was cancelled before
   *         it completed normally, otherwise {@code false}.
   */
  boolean isCancelled();



  /**
   * Returns {@code true} if the connection request has completed.
   * <p>
   * Completion may be due to normal termination, an exception, or
   * cancellation. In all of these cases, this method will return
   * {@code true}.
   *
   * @return {@code true} if the connection request has completed,
   *         otherwise {@code false}.
   */
  boolean isDone();
}
