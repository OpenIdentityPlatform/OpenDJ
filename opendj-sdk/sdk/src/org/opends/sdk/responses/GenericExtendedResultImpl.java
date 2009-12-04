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
 * generic extended the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.responses;



import org.opends.sdk.ByteString;
import org.opends.sdk.ResultCode;



/**
 * Generic extended result implementation.
 */
final class GenericExtendedResultImpl extends
    AbstractResultImpl<GenericExtendedResult> implements
    ExtendedResult, GenericExtendedResult
{

  private String responseName = null;

  private ByteString responseValue = null;



  /**
   * Creates a new generic extended result using the provided result
   * code.
   * 
   * @param resultCode
   *          The result code.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  GenericExtendedResultImpl(ResultCode resultCode)
      throws NullPointerException
  {
    super(resultCode);
  }



  /**
   * {@inheritDoc}
   */
  public String getResponseName()
  {
    return responseName;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString getResponseValue()
  {
    return responseValue;
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedResult setResponseName(String oid)
      throws UnsupportedOperationException
  {
    this.responseName = oid;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public GenericExtendedResult setResponseValue(ByteString bytes)
      throws UnsupportedOperationException
  {
    this.responseValue = bytes;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("ExtendedResult(resultCode=");
    builder.append(getResultCode());
    builder.append(", matchedDN=");
    builder.append(getMatchedDN());
    builder.append(", diagnosticMessage=");
    builder.append(getDiagnosticMessage());
    builder.append(", referrals=");
    builder.append(getReferralURIs());
    builder.append(", responseName=");
    builder.append(getResponseName() == null ? "" : getResponseName());
    builder.append(", responseValue=");
    final ByteString value = getResponseValue();
    builder.append(value == null ? ByteString.empty() : value);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  GenericExtendedResult getThis()
  {
    return this;
  }
}
