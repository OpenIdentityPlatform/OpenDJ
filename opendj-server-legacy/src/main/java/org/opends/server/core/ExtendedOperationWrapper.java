/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.ByteString;

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

  @Override
  public String getRequestOID()
  {
    return getOperation().getRequestOID();
  }

  @Override
  public String getResponseOID()
  {
    return getOperation().getResponseOID();
  }

  @Override
  public ByteString getRequestValue()
  {
    return getOperation().getRequestValue();
  }

  @Override
  public ByteString getResponseValue()
  {
    return getOperation().getResponseValue();
  }

  @Override
  public void setResponseOID(String responseOID)
  {
    getOperation().setResponseOID(responseOID);
  }

  @Override
  public void setResponseValue(ByteString responseValue)
  {
    getOperation().setResponseValue(responseValue);
  }
}
