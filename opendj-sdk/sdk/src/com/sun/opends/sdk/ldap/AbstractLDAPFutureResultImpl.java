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

package com.sun.opends.sdk.ldap;



import org.opends.sdk.*;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.responses.IntermediateResponse;
import org.opends.sdk.responses.Result;

import com.sun.opends.sdk.util.AbstractFutureResult;



/**
 * Abstract future result implementation.
 *
 * @param <S>
 *          The type of result returned by this future.
 */
abstract class AbstractLDAPFutureResultImpl<S extends Result> extends
    AbstractFutureResult<S> implements FutureResult<S>,
    IntermediateResponseHandler
{
  private final AsynchronousConnection connection;

  private final int messageID;

  private IntermediateResponseHandler intermediateResponseHandler;

  private volatile long timestamp;



  AbstractLDAPFutureResultImpl(final int messageID,
      final ResultHandler<? super S> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler,
      final AsynchronousConnection connection)
  {
    super(resultHandler);
    this.messageID = messageID;
    this.connection = connection;
    this.intermediateResponseHandler = intermediateResponseHandler;
    this.timestamp = System.currentTimeMillis();
  }



  /**
   * {@inheritDoc}
   */
  public final int getRequestID()
  {
    return messageID;
  }



  public final boolean handleIntermediateResponse(
      final IntermediateResponse response)
  {
    // FIXME: there's a potential race condition here - the future could
    // get cancelled between the isDone() call and the handler
    // invocation. We'd need to add support for intermediate handlers in
    // the synchronizer.
    if (!isDone())
    {
      updateTimestamp();
      if (intermediateResponseHandler != null)
      {
        if (!intermediateResponseHandler.handleIntermediateResponse(response))
        {
          intermediateResponseHandler = null;
        }
      }
    }
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected final ErrorResultException handleCancelRequest(
      final boolean mayInterruptIfRunning)
  {
    connection.abandon(Requests.newAbandonRequest(messageID));
    return null;
  }



  @Override
  protected void toString(final StringBuilder sb)
  {
    sb.append(" messageID = ");
    sb.append(messageID);
    sb.append(" timestamp = ");
    sb.append(timestamp);
    super.toString(sb);
  }



  final void adaptErrorResult(final Result result)
  {
    final S errorResult = newErrorResult(result.getResultCode(), result
        .getDiagnosticMessage(), result.getCause());
    setResultOrError(errorResult);
  }



  final long getTimestamp()
  {
    return timestamp;
  }



  abstract S newErrorResult(ResultCode resultCode, String diagnosticMessage,
      Throwable cause);



  final void setResultOrError(final S result)
  {
    if (result.getResultCode().isExceptional())
    {
      handleErrorResult(ErrorResultException.wrap(result));
    }
    else
    {
      handleResult(result);
    }
  }



  final void updateTimestamp()
  {
    timestamp = System.currentTimeMillis();
  }

}
