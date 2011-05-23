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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap;



import org.opends.sdk.AsynchronousConnection;
import org.opends.sdk.IntermediateResponseHandler;
import org.opends.sdk.ResultCode;
import org.opends.sdk.ResultHandler;
import org.opends.sdk.requests.CompareRequest;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.Responses;



/**
 * Compare result future implementation.
 */
final class LDAPCompareFutureResultImpl extends
    AbstractLDAPFutureResultImpl<CompareResult>
{
  private final CompareRequest request;



  LDAPCompareFutureResultImpl(final int requestID,
      final CompareRequest request,
      final ResultHandler<? super CompareResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler,
      final AsynchronousConnection connection)
  {
    super(requestID, resultHandler, intermediateResponseHandler, connection);
    this.request = request;
  }



  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append("LDAPCompareFutureResultImpl(");
    sb.append("request = ");
    sb.append(request);
    super.toString(sb);
    sb.append(")");
    return sb.toString();
  }



  CompareRequest getRequest()
  {
    return request;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  CompareResult newErrorResult(final ResultCode resultCode,
      final String diagnosticMessage, final Throwable cause)
  {
    return Responses.newCompareResult(resultCode).setDiagnosticMessage(
        diagnosticMessage).setCause(cause);
  }
}
