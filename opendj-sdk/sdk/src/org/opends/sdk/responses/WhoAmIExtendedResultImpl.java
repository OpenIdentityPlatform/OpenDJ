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

package org.opends.sdk.responses;



import static com.sun.opends.sdk.messages.Messages.ERR_WHOAMI_INVALID_AUTHZID_TYPE;

import org.opends.sdk.ByteString;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.LocalizedIllegalArgumentException;
import org.opends.sdk.ResultCode;



/**
 * Who Am I extended result implementation.
 */
final class WhoAmIExtendedResultImpl extends
    AbstractExtendedResult<WhoAmIExtendedResult> implements
    WhoAmIExtendedResult
{

  // The authorization ID.
  private String authorizationID = null;



  // Instantiation via factory.
  WhoAmIExtendedResultImpl(final ResultCode resultCode)
  {
    super(resultCode);
  }



  /**
   * Creates a new who am I extended result that is an exact copy of the
   * provided result.
   *
   * @param whoAmIExtendedResult
   *          The who am I extended result to be copied.
   * @throws NullPointerException
   *           If {@code whoAmIExtendedResult} was {@code null} .
   */
  WhoAmIExtendedResultImpl(final WhoAmIExtendedResult whoAmIExtendedResult)
      throws NullPointerException
  {
    super(whoAmIExtendedResult);
    this.authorizationID = whoAmIExtendedResult.getAuthorizationID();
  }



  /**
   * {@inheritDoc}
   */
  public String getAuthorizationID()
  {
    return authorizationID;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getOID()
  {
    // No response name defined.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getValue()
  {
    return (authorizationID != null) ? ByteString.valueOf(authorizationID)
        : null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasValue()
  {
    return (authorizationID != null);
  }



  /**
   * {@inheritDoc}
   */
  public WhoAmIExtendedResult setAuthorizationID(final String authorizationID)
      throws LocalizedIllegalArgumentException
  {
    if (authorizationID != null && authorizationID.length() != 0)
    {
      final int colonIndex = authorizationID.indexOf(':');
      if (colonIndex < 0)
      {
        final LocalizableMessage message = ERR_WHOAMI_INVALID_AUTHZID_TYPE
            .get(authorizationID);
        throw new LocalizedIllegalArgumentException(message);
      }
    }

    this.authorizationID = authorizationID;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("WhoAmIExtendedResponse(resultCode=");
    builder.append(getResultCode());
    builder.append(", matchedDN=");
    builder.append(getMatchedDN());
    builder.append(", diagnosticMessage=");
    builder.append(getDiagnosticMessage());
    builder.append(", referrals=");
    builder.append(getReferralURIs());
    builder.append(", authzId=");
    builder.append(authorizationID);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
