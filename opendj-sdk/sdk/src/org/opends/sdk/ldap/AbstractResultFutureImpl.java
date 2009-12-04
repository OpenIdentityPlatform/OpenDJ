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

package org.opends.sdk.ldap;



import java.util.concurrent.*;
import java.util.logging.Level;

import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.ResultFuture;
import org.opends.sdk.ResultHandler;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.util.StaticUtils;



/**
 * Abstract result future implementation.
 */
abstract class AbstractResultFutureImpl<R extends Result, P> implements
    ResultFuture<R>, Runnable
{
  private final LDAPConnection connection;

  private final ResultHandler<? super R, P> handler;

  private final ExecutorService handlerExecutor;

  private final int messageID;

  private final Semaphore invokerLock;

  private final CountDownLatch latch = new CountDownLatch(1);

  private final P p;

  private volatile boolean isCancelled = false;

  private volatile R result = null;



  AbstractResultFutureImpl(int messageID,
      ResultHandler<? super R, P> handler, P p,
      LDAPConnection connection, ExecutorService handlerExecutor)
  {
    this.messageID = messageID;
    this.handler = handler;
    this.p = p;
    this.connection = connection;
    this.handlerExecutor = handlerExecutor;
    if (handlerExecutor == null)
    {
      invokerLock = null;
    }
    else
    {
      invokerLock = new Semaphore(1);
    }
  }



  public synchronized boolean cancel(boolean b)
  {
    if (!isDone())
    {
      isCancelled = true;
      connection.abandon(Requests.newAbandonRequest(messageID));
      latch.countDown();
      return true;
    }
    else
    {
      return false;
    }
  }



  public R get() throws InterruptedException, ErrorResultException
  {
    latch.await();
    return get0();
  }



  public R get(long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException,
      ErrorResultException
  {
    if (!latch.await(timeout, unit))
    {
      throw new TimeoutException();
    }
    return get0();
  }



  public int getMessageID()
  {
    return messageID;
  }



  public boolean isCancelled()
  {
    return isCancelled;
  }



  public boolean isDone()
  {
    return latch.getCount() == 0;
  }



  public void run()
  {
    if (result.getResultCode().isExceptional())
    {
      ErrorResultException e = ErrorResultException.wrap(result);
      handler.handleErrorResult(p, e);
    }
    else
    {
      handler.handleResult(p, result);
    }
  }



  synchronized void handleErrorResult(Result result)
  {
    R errorResult = newErrorResult(result.getResultCode(), result
        .getDiagnosticMessage(), result.getCause());
    handleResult(errorResult);
  }



  abstract R newErrorResult(ResultCode resultCode,
      String diagnosticMessage, Throwable cause);



  void handleResult(R result)
  {
    if (!isDone())
    {
      this.result = result;
      if (handler != null)
      {
        invokeHandler(this);
      }
      latch.countDown();
    }
  }



  protected void invokeHandler(final Runnable runnable)
  {
    try
    {
      if (handlerExecutor == null)
      {
        runnable.run();
      }
      else
      {
        invokerLock.acquire();

        try
        {
          handlerExecutor.submit(new Runnable()
          {
            public void run()
            {
              try
              {
                runnable.run();
              }
              finally
              {
                invokerLock.release();
              }
            }
          });
        }
        catch (Exception e)
        {
          invokerLock.release();
        }
      }
    }
    catch (InterruptedException e)
    {
      // Thread has been interrupted so give up.
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
      {
        StaticUtils.DEBUG_LOG.warning(String.format(
            "Invoke thread interrupted: %s", StaticUtils
                .getExceptionMessage(e)));
      }
    }
  }



  private R get0() throws ErrorResultException
  {
    if (isCancelled())
    {
      throw ErrorResultException.wrap(
          Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED));
    }
    else if (result.getResultCode().isExceptional())
    {
      throw ErrorResultException.wrap(result);
    }
    else
    {
      return result;
    }
  }
}
