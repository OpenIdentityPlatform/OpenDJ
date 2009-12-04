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

package org.opends.sdk.extensions;



import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.ResultCode;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.responses.AbstractExtendedResult;



public class GetConnectionIDResult extends
    AbstractExtendedResult<GetConnectionIDResult>
{
  private int connectionID;



  public GetConnectionIDResult(ResultCode resultCode, int connectionID)
  {
    super(resultCode);
    this.connectionID = connectionID;
  }



  /**
   * {@inheritDoc}
   */
  public String getResponseName()
  {
    return GetConnectionIDRequest.OID_GET_CONNECTION_ID_EXTOP;
  }



  public int getConnectionID()
  {
    return connectionID;
  }



  public ByteString getResponseValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder(6);
    ASN1Writer writer = ASN1.getWriter(buffer);

    try
    {
      writer.writeInteger(connectionID);
    }
    catch (IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }

    return buffer.toByteString();
  }



  public GetConnectionIDResult setConnectionID(int connectionID)
  {
    this.connectionID = connectionID;
    return this;
  }



  public StringBuilder toString(StringBuilder builder)
  {
    builder.append("GetConnectionIDExtendedResponse(resultCode=");
    builder.append(getResultCode());
    builder.append(", matchedDN=");
    builder.append(getMatchedDN());
    builder.append(", diagnosticMessage=");
    builder.append(getDiagnosticMessage());
    builder.append(", referrals=");
    builder.append(getReferralURIs());
    builder.append(", responseName=");
    builder.append(getResponseName());
    builder.append(", connectionID=");
    builder.append(connectionID);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder;
  }
}
