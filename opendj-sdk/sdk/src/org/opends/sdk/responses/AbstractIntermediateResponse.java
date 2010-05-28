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

package org.opends.sdk.responses;



import org.opends.sdk.ByteString;

import com.sun.opends.sdk.util.StaticUtils;



/**
 * An abstract Intermediate response which can be used as the basis for
 * implementing new Intermediate responses.
 *
 * @param <S>
 *          The type of Intermediate response.
 */
public abstract class AbstractIntermediateResponse<S extends IntermediateResponse>
    extends AbstractResponseImpl<S> implements IntermediateResponse
{

  /**
   * Creates a new intermediate response.
   */
  protected AbstractIntermediateResponse()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  public abstract String getOID();



  /**
   * {@inheritDoc}
   */
  public abstract ByteString getValue();



  /**
   * {@inheritDoc}
   */
  public abstract boolean hasValue();



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("IntermediateResponse(responseName=");
    builder.append(getOID() == null ? "" : getOID());
    if (hasValue())
    {
      builder.append(", responseValue=");
      StaticUtils.toHexPlusAscii(getValue(), builder, 4);
    }
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  final S getThis()
  {
    return (S) this;
  }
}
