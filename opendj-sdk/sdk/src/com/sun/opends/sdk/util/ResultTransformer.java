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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ResultFuture;
import org.opends.sdk.ResultHandler;



/**
 * A base class which can be used to transform the result of an inner
 * asynchronous request to another result type.
 *
 * @param <M>
 *          The type of the inner result.
 * @param <N>
 *          The type of the outer result.
 */
public abstract class ResultTransformer<M, N> implements
    ResultFuture<N>, ResultHandler<M>
{

  private final ResultHandler<? super N> handler;

  private volatile ResultFuture<M> future = null;



  /**
   * Sets the inner future for this result transformer. This must be
   * done before this future is published.
   *
   * @param future
   *          The inner future.
   */
  public final void setResultFuture(ResultFuture<M> future)
  {
    this.future = future;
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



  /**
   * Creates a new result transformer which will transform the results
   * of an inner asynchronous request.
   *
   * @param handler
   *          The outer result handler.
   */
  protected ResultTransformer(ResultHandler<? super N> handler)
  {
    this.handler = handler;
  }



  /**
   * {@inheritDoc}
   */
  public final void handleErrorResult(ErrorResultException error)
  {
    if (handler != null)
    {
      handler.handleErrorResult(error);
    }
  }



  /**
   * {@inheritDoc}
   */
  public final void handleResult(M result)
  {
    if (handler != null)
    {
      try
      {
        handler.handleResult(transformResult(result));
      }
      catch (ErrorResultException e)
      {
        handler.handleErrorResult(e);
      }
    }
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
      CancellationException, InterruptedException
  {
    return transformResult(future.get());
  }



  /**
   * {@inheritDoc}
   */
  public final N get(long timeout, TimeUnit unit)
      throws ErrorResultException, TimeoutException,
      CancellationException, InterruptedException
  {
    return transformResult(future.get(timeout, unit));
  }



  /**
   * {@inheritDoc}
   */
  public final int getMessageID()
  {
    return future.getMessageID();
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

}
