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

package org.opends.sdk.requests;



import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.extensions.ExtendedOperation;
import org.opends.sdk.responses.GenericExtendedResult;
import org.opends.sdk.responses.Responses;

import com.sun.opends.sdk.util.Validator;



/**
 * Generic extended request implementation.
 */
final class GenericExtendedRequestImpl
    extends
    AbstractExtendedRequest<GenericExtendedRequest, GenericExtendedResult>
    implements GenericExtendedRequest
{
  /**
   * Generic extended operation singleton.
   */
  private static final class Operation implements
      ExtendedOperation<GenericExtendedRequest, GenericExtendedResult>
  {

    public GenericExtendedRequest decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      return Requests.newGenericExtendedRequest(requestName,
          requestValue);
    }



    public GenericExtendedResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage)
    {
      return Responses.newGenericExtendedResult(resultCode)
          .setMatchedDN(matchedDN).setDiagnosticMessage(
              diagnosticMessage);
    }



    public GenericExtendedResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage,
        String responseName, ByteString responseValue)
        throws DecodeException
    {
      return Responses.newGenericExtendedResult(resultCode)
          .setMatchedDN(matchedDN).setDiagnosticMessage(
              diagnosticMessage).setResponseName(responseName)
          .setResponseValue(responseValue);
    }
  }



  private static final Operation OPERATION = new Operation();

  private ByteString requestValue = ByteString.empty();

  private String requestName;



  /**
   * Creates a new generic extended request using the provided name and
   * optional value.
   * 
   * @param requestName
   *          The dotted-decimal representation of the unique OID
   *          corresponding to this extended request.
   * @param requestValue
   *          The content of this generic extended request in a form
   *          defined by the extended operation, or {@code null} if
   *          there is no content.
   * @throws NullPointerException
   *           If {@code requestName} was {@code null}.
   */
  GenericExtendedRequestImpl(String requestName, ByteString requestValue)
      throws NullPointerException
  {
    this.requestName = requestName;
    this.requestValue = requestValue;
  }



  /**
   * {@inheritDoc}
   */
  public ExtendedOperation<GenericExtendedRequest, GenericExtendedResult> getExtendedOperation()
  {
    return OPERATION;
  }



  /**
   * {@inheritDoc}
   */
  public String getRequestName()
  {
    return requestName;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString getRequestValue()
  {
    return requestValue;
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedRequest setRequestName(String oid)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(oid);
    this.requestName = oid;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedRequest setRequestValue(ByteString bytes)
      throws UnsupportedOperationException
  {
    this.requestValue = bytes;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("GenericExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", requestValue=");
    builder.append(getRequestValue());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
