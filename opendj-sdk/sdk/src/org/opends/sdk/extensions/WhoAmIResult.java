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



import org.opends.sdk.ByteString;
import org.opends.sdk.ResultCode;
import org.opends.sdk.responses.AbstractExtendedResult;



public class WhoAmIResult extends AbstractExtendedResult<WhoAmIResult>
{
  private String authzId;



  public WhoAmIResult(ResultCode resultCode)
  {
    super(resultCode);
  }



  /**
   * {@inheritDoc}
   */
  public String getResponseName()
  {
    // No response name defined.
    return null;
  }



  /**
   * Get the authzId to return or <code>null</code> if it is not
   * available.
   * 
   * @return The authzID or <code>null</code>.
   */
  public String getAuthzId()
  {
    return authzId;
  }



  public ByteString getResponseValue()
  {
    if (authzId != null)
    {
      ByteString.valueOf(authzId);
    }
    return null;
  }



  public WhoAmIResult setAuthzId(String authzId)
  {
    this.authzId = authzId;
    return this;
  }



  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("WhoAmIExtendedResponse(resultCode=");
    builder.append(getResultCode());
    builder.append(", matchedDN=");
    builder.append(getMatchedDN());
    builder.append(", diagnosticMessage=");
    builder.append(getDiagnosticMessage());
    builder.append(", referrals=");
    builder.append(getReferralURIs());
    builder.append(", authzId=");
    builder.append(authzId);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
