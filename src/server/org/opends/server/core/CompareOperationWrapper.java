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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.core;

import java.util.Set;

import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;

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


  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public String getRawAttributeType()
  {
    return getOperation().getRawAttributeType();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawAttributeType(String rawAttributeType)
  {
    getOperation().setRawAttributeType(rawAttributeType);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeType getAttributeType()
  {
    return getOperation().getAttributeType();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setAttributeType(AttributeType attributeType)
  {
    getOperation().setAttributeType(attributeType);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getAttributeOptions()
  {
    return getOperation().getAttributeOptions();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setAttributeOptions(Set<String> attributeOptions)
  {
    getOperation().setAttributeOptions(attributeOptions);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getAssertionValue()
  {
    return getOperation().getAssertionValue();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setAssertionValue(ByteString assertionValue)
  {
    getOperation().setAssertionValue(assertionValue);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public DN getProxiedAuthorizationDN()
  {
    return getOperation().getProxiedAuthorizationDN();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    getOperation().setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
