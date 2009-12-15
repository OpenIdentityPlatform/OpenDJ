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



import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.FutureResult;
import org.opends.sdk.ResultHandler;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.responses.Result;

import com.sun.opends.sdk.util.AbstractFutureResult;



/**
 * Abstract result future implementation.
 *
 * @param <S>
 *          The type of result returned by this future.
 */
abstract class AbstractResultFutureImpl<S extends Result> extends
    AbstractFutureResult<S> implements FutureResult<S>
{
  private final LDAPConnection connection;

  private final int messageID;



  /**
   * Creates a new LDAP result future.
   *
   * @param messageID
   *          The request message ID.
   * @param handler
   *          The result handler, maybe {@code null}.
   * @param connection
   *          The client connection.
   */
  AbstractResultFutureImpl(int messageID,
      ResultHandler<? super S> handler, LDAPConnection connection)
  {
    super(handler);
    this.messageID = messageID;
    this.connection = connection;
  }



  /**
   * {@inheritDoc}
   */
  protected final ErrorResultException handleCancelRequest()
  {
    connection.abandon(Requests.newAbandonRequest(messageID));
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public final int getRequestID()
  {
    return messageID;
  }



  final void adaptErrorResult(Result result)
  {
    S errorResult = newErrorResult(result.getResultCode(), result
        .getDiagnosticMessage(), result.getCause());
    setResultOrError(errorResult);
  }



  final void setResultOrError(S result)
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



  abstract S newErrorResult(ResultCode resultCode,
      String diagnosticMessage, Throwable cause);

}
