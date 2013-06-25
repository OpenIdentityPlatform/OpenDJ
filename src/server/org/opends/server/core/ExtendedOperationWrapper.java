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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.core;

import org.opends.server.types.ByteString;

/**
 * This abstract class wraps/decorates a given extended operation. This class
 * will be extended by sub-classes to enhance the functionality of the
 * ExtendedOperationBasis.
 */
public abstract class ExtendedOperationWrapper extends
    OperationWrapper<ExtendedOperation> implements ExtendedOperation
{

  /**
   * Creates a new extended operation wrapper based on the provided extended
   * operation.
   *
   * @param extended
   *          The extended operation to wrap
   */
  public ExtendedOperationWrapper(ExtendedOperation extended)
  {
    super(extended);
  }

  /** {@inheritDoc} */
  @Override
  public String getRequestOID()
  {
    return getOperation().getRequestOID();
  }

  /** {@inheritDoc} */
  @Override
  public String getResponseOID()
  {
    return getOperation().getResponseOID();
  }

  /** {@inheritDoc} */
  @Override
  public ByteString getRequestValue()
  {
    return getOperation().getRequestValue();
  }

  /** {@inheritDoc} */
  @Override
  public ByteString getResponseValue()
  {
    return getOperation().getResponseValue();
  }

  /** {@inheritDoc} */
  @Override
  public void setResponseOID(String responseOID)
  {
    getOperation().setResponseOID(responseOID);
  }

  /** {@inheritDoc} */
  @Override
  public void setResponseValue(ByteString responseValue)
  {
    getOperation().setResponseValue(responseValue);
  }

}
