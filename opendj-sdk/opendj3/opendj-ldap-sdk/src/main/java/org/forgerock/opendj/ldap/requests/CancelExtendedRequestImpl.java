/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;



import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_CANCEL_NO_REQUEST_VALUE;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.asn1.ASN1;
import org.forgerock.opendj.asn1.ASN1Reader;
import org.forgerock.opendj.asn1.ASN1Writer;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.Responses;



/**
 * Cancel extended request implementation.
 */
final class CancelExtendedRequestImpl extends
    AbstractExtendedRequest<CancelExtendedRequest, ExtendedResult> implements
    CancelExtendedRequest
{
  static final class RequestDecoder implements
      ExtendedRequestDecoder<CancelExtendedRequest, ExtendedResult>
  {
    public CancelExtendedRequest decodeExtendedRequest(
        final ExtendedRequest<?> request, final DecodeOptions options)
        throws DecodeException
    {
      final ByteString requestValue = request.getValue();
      if ((requestValue == null) || (requestValue.length() <= 0))
      {
        throw DecodeException.error(ERR_EXTOP_CANCEL_NO_REQUEST_VALUE.get());
      }

      try
      {
        final ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();
        final int idToCancel = (int) reader.readInteger();
        reader.readEndSequence();

        final CancelExtendedRequest newRequest = new CancelExtendedRequestImpl(
            idToCancel);

        for (final Control control : request.getControls())
        {
          newRequest.addControl(control);
        }

        return newRequest;
      }
      catch (final IOException e)
      {
        final LocalizableMessage message = ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }
    }
  }



  private static final class ResultDecoder extends
      AbstractExtendedResultDecoder<ExtendedResult>
  {
    public ExtendedResult newExtendedErrorResult(final ResultCode resultCode,
        final String matchedDN, final String diagnosticMessage)
    {
      return Responses.newGenericExtendedResult(resultCode).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
    }



    public ExtendedResult decodeExtendedResult(final ExtendedResult result,
        final DecodeOptions options) throws DecodeException
    {
      // TODO: Should we check to make sure OID and value is null?
      return result;
    }
  }



  private int requestID;

  // No need to expose this.
  private static final ExtendedResultDecoder<ExtendedResult> RESULT_DECODER = new ResultDecoder();



  // Instantiation via factory.
  CancelExtendedRequestImpl(final int requestID)
  {
    this.requestID = requestID;
  }



  /**
   * Creates a new cancel extended request that is an exact copy of the provided
   * request.
   *
   * @param cancelExtendedRequest
   *          The cancel extended request to be copied.
   * @throws NullPointerException
   *           If {@code cancelExtendedRequest} was {@code null} .
   */
  CancelExtendedRequestImpl(final CancelExtendedRequest cancelExtendedRequest)
  {
    super(cancelExtendedRequest);
    this.requestID = cancelExtendedRequest.getRequestID();
  }



  /**
   * {@inheritDoc}
   */
  public int getRequestID()
  {
    return requestID;
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
  public ExtendedResultDecoder<ExtendedResult> getResultDecoder()
  {
    return RESULT_DECODER;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getValue()
  {
    final ByteStringBuilder buffer = new ByteStringBuilder(6);
    final ASN1Writer writer = ASN1.getWriter(buffer);

    try
    {
      writer.writeStartSequence();
      writer.writeInteger(requestID);
      writer.writeEndSequence();
    }
    catch (final IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }

    return buffer.toByteString();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasValue()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public CancelExtendedRequest setRequestID(final int id)
  {
    this.requestID = id;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("CancelExtendedRequest(requestName=");
    builder.append(getOID());
    builder.append(", requestID=");
    builder.append(requestID);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
