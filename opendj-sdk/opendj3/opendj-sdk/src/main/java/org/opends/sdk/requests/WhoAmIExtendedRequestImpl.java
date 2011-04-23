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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.responses.*;



/**
 * Who Am I extended request implementation.
 */
final class WhoAmIExtendedRequestImpl extends
    AbstractExtendedRequest<WhoAmIExtendedRequest, WhoAmIExtendedResult>
    implements WhoAmIExtendedRequest
{

  static final class RequestDecoder implements
      ExtendedRequestDecoder<WhoAmIExtendedRequest, WhoAmIExtendedResult>
  {

    public WhoAmIExtendedRequest decodeExtendedRequest(
        final ExtendedRequest<?> request, final DecodeOptions options)
        throws DecodeException
    {
      // TODO: Check the OID and that the value is not present.
      final WhoAmIExtendedRequest newRequest = new WhoAmIExtendedRequestImpl();
      for (final Control control : request.getControls())
      {
        newRequest.addControl(control);
      }
      return newRequest;
    }
  }



  private static final class ResultDecoder extends
      AbstractExtendedResultDecoder<WhoAmIExtendedResult>
  {
    public WhoAmIExtendedResult newExtendedErrorResult(
        final ResultCode resultCode, final String matchedDN,
        final String diagnosticMessage)
    {
      return Responses.newWhoAmIExtendedResult(resultCode).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
    }



    public WhoAmIExtendedResult decodeExtendedResult(
        final ExtendedResult result, final DecodeOptions options)
        throws DecodeException
    {
      if (result instanceof WhoAmIExtendedResult)
      {
        return (WhoAmIExtendedResult) result;
      }
      else
      {
        final WhoAmIExtendedResult newResult = Responses
            .newWhoAmIExtendedResult(result.getResultCode()).setMatchedDN(
                result.getMatchedDN()).setDiagnosticMessage(
                result.getDiagnosticMessage());

        final ByteString responseValue = result.getValue();
        if (responseValue != null)
        {
          try
          {
            newResult.setAuthorizationID(responseValue.toString());
          }
          catch (final LocalizedIllegalArgumentException e)
          {
            throw DecodeException.error(e.getMessageObject());
          }
        }

        for (final Control control : result.getControls())
        {
          newResult.addControl(control);
        }

        return newResult;
      }
    }
  }



  // No need to expose this.
  private static final ExtendedResultDecoder<WhoAmIExtendedResult>
    RESULT_DECODER = new ResultDecoder();



  // Prevent instantiation.
  WhoAmIExtendedRequestImpl()
  {
    // Nothing to do.
  }



  /**
   * Creates a new Who Am I extended request that is an exact copy of the
   * provided request.
   *
   * @param whoAmIExtendedRequest
   *          The who Am I extended request to be copied.
   * @throws NullPointerException
   *           If {@code whoAmIExtendedRequest} was {@code null} .
   */
  WhoAmIExtendedRequestImpl(
      final WhoAmIExtendedRequest whoAmIExtendedRequest)
      throws NullPointerException
  {
    super(whoAmIExtendedRequest);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getOID()
  {
    return OID;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ExtendedResultDecoder<WhoAmIExtendedResult> getResultDecoder()
  {
    return RESULT_DECODER;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getValue()
  {
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasValue()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("WhoAmIExtendedRequest(requestName=");
    builder.append(getOID());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
