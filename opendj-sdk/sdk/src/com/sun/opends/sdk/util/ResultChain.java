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

package com.sun.opends.sdk.util;



import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ResultFuture;
import org.opends.sdk.ResultHandler;



/**
 * A base class which can be used to transform the result of an inner
 * asynchronous request to another result type.
 * <p>
 * FIXME: I don't think that this is right. There's too much locking for
 * a start.
 *
 * @param <M>
 *          The type of the inner result.
 * @param <N>
 *          The type of the outer result.
 */
public abstract class ResultChain<M, N> implements ResultFuture<N>,
    ResultHandler<M>
{

  private ErrorResultException errorResult;

  private final ResultHandler<? super N> handler;

  private final Object stateLock = new Object();

  private final CountDownLatch latch = new CountDownLatch(1);

  private ResultFuture<N> outerFuture = null;

  private boolean cancelled = false;

  private volatile ResultFuture<M> innerFuture = null;



  /**
   * Creates a new asynchronous result chain which will chain an outer
   * asynchronous request once the inner asynchronous request completes.
   *
   * @param handler
   *          The outer result handler.
   */
  protected ResultChain(ResultHandler<? super N> handler)
  {
    this.handler = handler;
  }



  /**
   * {@inheritDoc}
   */
  public boolean cancel(boolean mayInterruptIfRunning)
  {
    synchronized (stateLock)
    {
      if (!isDone())
      {
        cancelled = true;
        innerFuture.cancel(mayInterruptIfRunning);
        if (outerFuture != null)
        {
          outerFuture.cancel(mayInterruptIfRunning);
        }
        latch.countDown();
        return true;
      }
      else
      {
        return false;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public N get() throws ErrorResultException, CancellationException,
      InterruptedException
  {
    latch.await();
    return get0();
  }



  /**
   * {@inheritDoc}
   */
  public N get(long timeout, TimeUnit unit)
      throws ErrorResultException, TimeoutException,
      CancellationException, InterruptedException
  {
    if (!latch.await(timeout, unit))
    {
      throw new TimeoutException();
    }
    return get0();
  }



  /**
   * {@inheritDoc}
   */
  public int getMessageID()
  {
    // Best effort.
    synchronized (stateLock)
    {
      if (outerFuture != null)
      {
        return outerFuture.getMessageID();
      }
      else
      {
        return innerFuture.getMessageID();
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public void handleErrorResult(ErrorResultException error)
  {
    synchronized (stateLock)
    {
      try
      {
        outerFuture = chainErrorResult(error, handler);
      }
      catch (ErrorResultException e)
      {
        errorResult = e;
        if (handler != null)
        {
          handler.handleErrorResult(errorResult);
        }
        latch.countDown();
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public void handleResult(M result)
  {
    synchronized (stateLock)
    {
      try
      {
        outerFuture = chainResult(result, handler);
      }
      catch (ErrorResultException e)
      {
        errorResult = e;
        if (handler != null)
        {
          handler.handleErrorResult(errorResult);
        }
        latch.countDown();
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isCancelled()
  {
    return isDone() && cancelled;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isDone()
  {
    return latch.getCount() == 0;
  }



  /**
   * Sets the inner future for this result chain. This must be done
   * before this future is published.
   *
   * @param future
   *          The inner future.
   */
  public final void setInnerResultFuture(ResultFuture<M> future)
  {
    innerFuture = future;
  }



  private N get0() throws ErrorResultException, CancellationException,
      InterruptedException
  {
    synchronized (stateLock)
    {
      if (cancelled)
      {
        throw new CancellationException();
      }

      if (errorResult != null)
      {
        throw errorResult;
      }

      return outerFuture.get();
    }
  }



  /**
   * Invokes the outer request based on the error result of the inner
   * request and returns a future representing the result of the outer
   * request.
   * <p>
   * The default implementation is to terminate further processing by
   * re-throwing the inner error result.
   *
   * @param innerError
   *          The error result of the inner request.
   * @param handler
   *          The result handler to be used for the outer request.
   * @return A future representing the result of the outer request.
   * @throws ErrorResultException
   *           If the outer request could not be invoked and processing
   *           should terminate.
   */
  protected ResultFuture<N> chainErrorResult(
      ErrorResultException innerError, ResultHandler<? super N> handler)
      throws ErrorResultException
  {
    throw innerError;
  }



  /**
   * Invokes the outer request based on the result of the inner request
   * and returns a future representing the result of the outer request.
   *
   * @param innerResult
   *          The result of the inner request.
   * @param handler
   *          The result handler to be used for the outer request.
   * @return A future representing the result of the outer request.
   * @throws ErrorResultException
   *           If the outer request could not be invoked and processing
   *           should terminate.
   */
  protected abstract ResultFuture<N> chainResult(M innerResult,
      ResultHandler<? super N> handler) throws ErrorResultException;

}
