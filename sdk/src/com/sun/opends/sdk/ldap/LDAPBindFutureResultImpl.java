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

package com.sun.opends.sdk.ldap;



import org.opends.sdk.*;
import org.opends.sdk.requests.BindClient;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.Responses;



/**
 * Bind result future implementation.
 */
final class LDAPBindFutureResultImpl extends
    AbstractLDAPFutureResultImpl<BindResult> implements
    FutureResult<BindResult>
{
  private final BindClient bindClient;



  LDAPBindFutureResultImpl(final int messageID, final BindClient bindClient,
      final ResultHandler<? super BindResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler,
      final AsynchronousConnection connection)
  {
    super(messageID, resultHandler, intermediateResponseHandler, connection);
    this.bindClient = bindClient;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isCancelable() {
    return false;
  }



  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append("LDAPBindFutureResultImpl(");
    sb.append("bindClient = ");
    sb.append(bindClient);
    super.toString(sb);
    sb.append(")");
    return sb.toString();
  }



  BindClient getBindClient()
  {
    return bindClient;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  BindResult newErrorResult(final ResultCode resultCode,
      final String diagnosticMessage, final Throwable cause)
  {
    return Responses.newBindResult(resultCode).setDiagnosticMessage(
        diagnosticMessage).setCause(cause);
  }
}
