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
import org.opends.sdk.requests.CompareRequest;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.Responses;



/**
 * Compare result future implementation.
 */
public final class CompareResultFutureImpl extends
    AbstractResultFutureImpl<CompareResult> implements
    ResultFuture<CompareResult>
{
  private final CompareRequest request;



  CompareResultFutureImpl(int messageID, CompareRequest request,
      ResultHandler<? super CompareResult> handler,
      LDAPConnection connection, ExecutorService handlerExecutor)
  {
    super(messageID, handler, connection, handlerExecutor);
    this.request = request;
  }



  /**
   * {@inheritDoc}
   */
  CompareResult newErrorResult(ResultCode resultCode,
      String diagnosticMessage, Throwable cause)
  {
    return Responses.newCompareResult(resultCode).setDiagnosticMessage(
        diagnosticMessage).setCause(cause);
  }



  CompareRequest getRequest()
  {
    return request;
  }
}
