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



import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.ErrorResultException;
import org.opends.sdk.FutureResult;
import org.opends.sdk.ResultHandler;



/**
 * An implementation of the {@code FutureResult} interface which
 * transforms the result of an asynchronous operation from one type to
 * another. The implementation ensures that the transformed is computed
 * only once.
 *
 * @param <M>
 *          The type of the inner result.
 * @param <N>
 *          The type of the outer result.
 */
public abstract class FutureResultTransformer<M, N> implements
    FutureResult<N>, ResultHandler<M>
{

  private final ResultHandler<? super N> handler;

  private volatile FutureResult<M> future = null;

  // These do not need to be volatile since the future acts as a memory
  // barrier.
  private N transformedResult = null;

  private ErrorResultException transformedErrorResult = null;



  /**
   * Creates a new result transformer which will transform the results
   * of an inner asynchronous request.
   *
   * @param handler
   *          The outer result handler.
   */
  protected FutureResultTransformer(ResultHandler<? super N> handler)
  {
    this.handler = handler;
  }



  /**
   * {@inheritDoc}
   */
  public final boolean cancel(boolean mayInterruptIfRunning)
  {
    return future.cancel(mayInterruptIfRunning);
  }



  /**
   * {@inheritDoc}
   */
  public final N get() throws ErrorResultException,
      InterruptedException
  {
    future.get();

    // The handlers are guaranteed to have been invoked at this point.
    return get0();
  }



  /**
   * {@inheritDoc}
   */
  public final N get(long timeout, TimeUnit unit)
      throws ErrorResultException, TimeoutException,
      InterruptedException
  {
    future.get(timeout, unit);

    // The handlers are guaranteed to have been invoked at this point.
    return get0();
  }



  /**
   * {@inheritDoc}
   */
  public final int getRequestID()
  {
    return future.getRequestID();
  }



  /**
   * {@inheritDoc}
   */
  public final void handleErrorResult(ErrorResultException error)
  {
    transformedErrorResult = transformErrorResult(error);
    if (handler != null)
    {
      handler.handleErrorResult(transformedErrorResult);
    }
  }



  /**
   * {@inheritDoc}
   */
  public final void handleResult(M result)
  {
    try
    {
      transformedResult = transformResult(result);
      if (handler != null)
      {
        handler.handleResult(transformedResult);
      }
    }
    catch (final ErrorResultException e)
    {
      transformedErrorResult = e;
      if (handler != null)
      {
        handler.handleErrorResult(transformedErrorResult);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public final boolean isCancelled()
  {
    return future.isCancelled();
  }



  /**
   * {@inheritDoc}
   */
  public final boolean isDone()
  {
    return future.isDone();
  }



  /**
   * Sets the inner future for this result transformer. This must be
   * done before this future is published.
   *
   * @param future
   *          The inner future.
   */
  public final void setFutureResult(FutureResult<M> future)
  {
    this.future = future;
  }



  private N get0() throws ErrorResultException
  {
    if (transformedErrorResult != null)
    {
      throw transformedErrorResult;
    }
    else
    {
      return transformedResult;
    }
  }



  /**
   * Transforms the inner error result to an outer error result. The
   * default implementation is to return the inner error result.
   *
   * @param errorResult
   *          The inner error result.
   * @return The outer error result.
   */
  protected ErrorResultException transformErrorResult(
      ErrorResultException errorResult)
  {
    return errorResult;
  }



  /**
   * Transforms the inner result to an outer result, possibly throwing
   * an {@code ErrorResultException} if the transformation fails for
   * some reason.
   *
   * @param result
   *          The inner result.
   * @return The outer result.
   * @throws ErrorResultException
   *           If the transformation fails for some reason.
   */
  protected abstract N transformResult(M result)
      throws ErrorResultException;

}
