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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

/**
 * This abstract class wraps/decorates a given compare operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the CompareOperationBasis.
 */
public abstract class CompareOperationWrapper extends
    OperationWrapper<CompareOperation> implements CompareOperation
{

  /**
   * Creates a new compare operation based on the provided compare operation.
   *
   * @param compare The compare operation to wrap
   */
  public CompareOperationWrapper(CompareOperation compare)
  {
    super(compare);
  }

  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }

  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }

  @Override
  public String getRawAttributeType()
  {
    return getOperation().getRawAttributeType();
  }

  @Override
  public void setRawAttributeType(String rawAttributeType)
  {
    getOperation().setRawAttributeType(rawAttributeType);
  }

  @Override
  public AttributeDescription getAttributeDescription()
  {
    return getOperation().getAttributeDescription();
  }

  @Override
  public ByteString getAssertionValue()
  {
    return getOperation().getAssertionValue();
  }

  @Override
  public void setAssertionValue(ByteString assertionValue)
  {
    getOperation().setAssertionValue(assertionValue);
  }
}
