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
import org.opends.sdk.DecodeOptions;
import org.opends.sdk.ResultCode;
import org.opends.sdk.controls.Control;
import org.opends.sdk.responses.ExtendedResult;
import org.opends.sdk.responses.ExtendedResultDecoder;
import org.opends.sdk.responses.GenericExtendedResult;
import org.opends.sdk.responses.Responses;

import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * Generic extended request implementation.
 */
final class GenericExtendedRequestImpl extends
    AbstractExtendedRequest<GenericExtendedRequest, GenericExtendedResult>
    implements GenericExtendedRequest
{

  static final class RequestDecoder implements
      ExtendedRequestDecoder<GenericExtendedRequest, GenericExtendedResult>
  {
    public GenericExtendedRequest decodeExtendedRequest(
        final ExtendedRequest<?> request, final DecodeOptions options)
        throws DecodeException
    {
      if (request instanceof GenericExtendedRequest)
      {
        return (GenericExtendedRequest) request;
      }
      else
      {
        final GenericExtendedRequest newRequest = new GenericExtendedRequestImpl(
            request.getOID(), request.getValue());

        for (final Control control : request.getControls())
        {
          newRequest.addControl(control);
        }

        return newRequest;
      }
    }
  }



  private static final class GenericExtendedResultDecoder implements
      ExtendedResultDecoder<GenericExtendedResult>
  {

    public GenericExtendedResult adaptExtendedErrorResult(
        final ResultCode resultCode, final String matchedDN,
        final String diagnosticMessage)
    {
      return Responses.newGenericExtendedResult(resultCode).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
    }



    public GenericExtendedResult decodeExtendedResult(
        final ExtendedResult result, final DecodeOptions options)
        throws DecodeException
    {
      if (result instanceof GenericExtendedResult)
      {
        return (GenericExtendedResult) result;
      }
      else
      {
        final GenericExtendedResult newResult = Responses
            .newGenericExtendedResult(result.getResultCode()).setMatchedDN(
                result.getMatchedDN()).setDiagnosticMessage(
                result.getDiagnosticMessage()).setOID(result.getOID())
            .setValue(result.getValue());
        for (final Control control : result.getControls())
        {
          newResult.addControl(control);
        }
        return newResult;
      }
    }
  }



  private static final GenericExtendedResultDecoder RESULT_DECODER =
    new GenericExtendedResultDecoder();

  private ByteString requestValue = ByteString.empty();

  private String requestName;



  /**
   * Creates a new generic extended request using the provided name and optional
   * value.
   *
   * @param requestName
   *          The dotted-decimal representation of the unique OID corresponding
   *          to this extended request.
   * @param requestValue
   *          The content of this generic extended request in a form defined by
   *          the extended operation, or {@code null} if there is no content.
   * @throws NullPointerException
   *           If {@code requestName} was {@code null}.
   */
  GenericExtendedRequestImpl(final String requestName,
      final ByteString requestValue) throws NullPointerException
  {
    this.requestName = requestName;
    this.requestValue = requestValue;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getOID()
  {
    return requestName;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ExtendedResultDecoder<GenericExtendedResult> getResultDecoder()
  {
    return RESULT_DECODER;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getValue()
  {
    return requestValue;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasValue()
  {
    return requestValue != null;
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedRequest setOID(final String oid)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(oid);
    this.requestName = oid;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedRequest setValue(final ByteString bytes)
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
    builder.append(getOID());
    if (hasValue())
    {
      builder.append(", requestValue=");
      StaticUtils.toHexPlusAscii(getValue(), builder, 4);
    }
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
