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
  public abstract String getResponseName();



  /**
   * {@inheritDoc}
   */
  public abstract ByteString getResponseValue();



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("IntermediateResponse(responseName=");
    builder.append(getResponseName() == null ? "" : getResponseName());
    builder.append(", responseValue=");
    final ByteString value = getResponseValue();
    builder.append(value == null ? ByteString.empty() : value);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  final S getThis()
  {
    return (S) this;
  }
}
