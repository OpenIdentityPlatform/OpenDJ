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



import java.util.concurrent.ExecutorService;

import org.opends.sdk.ResultCode;
import org.opends.sdk.ResultFuture;
import org.opends.sdk.ResultHandler;
import org.opends.sdk.requests.Request;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;



/**
 * Result future implementation.
 */
public final class ResultFutureImpl<P> extends
    AbstractResultFutureImpl<Result, P> implements ResultFuture<Result>
{
  private final Request request;



  ResultFutureImpl(int messageID, Request request,
      ResultHandler<Result, P> handler, P p, LDAPConnection connection,
      ExecutorService handlerExecutor)
  {
    super(messageID, handler, p, connection, handlerExecutor);
    this.request = request;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  Result newErrorResult(ResultCode resultCode,
      String diagnosticMessage, Throwable cause)
  {
    return Responses.newResult(resultCode).setDiagnosticMessage(
        diagnosticMessage).setCause(cause);
  }



  Request getRequest()
  {
    return request;
  }
}
