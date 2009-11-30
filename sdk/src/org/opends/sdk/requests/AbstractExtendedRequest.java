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



import org.opends.sdk.extensions.ExtendedOperation;
import org.opends.sdk.responses.Result;
import org.opends.sdk.util.ByteString;



/**
 * An abstract Extended request which can be used as the basis for
 * implementing new Extended operations.
 * 
 * @param <R>
 *          The type of extended request.
 * @param <S>
 *          The type of result.
 */
public abstract class AbstractExtendedRequest<R extends ExtendedRequest<S>, S extends Result>
    extends AbstractRequestImpl<R> implements ExtendedRequest<S>
{

  /**
   * Creates a new abstract extended request.
   */
  protected AbstractExtendedRequest()
  {
    // Nothing to do.
  }



  /**
   * Returns the extended operation associated with this extended
   * request.
   * <p>
   * FIXME: this should not be exposed to clients.
   * 
   * @return The extended operation associated with this extended
   *         request.
   */
  public abstract ExtendedOperation<R, S> getExtendedOperation();



  /**
   * {@inheritDoc}
   */
  public abstract String getRequestName();



  /**
   * {@inheritDoc}
   */
  public abstract ByteString getRequestValue();



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("ExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", requestValue=");
    final ByteString value = getRequestValue();
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
  final R getThis()
  {
    return (R) this;
  }

}
