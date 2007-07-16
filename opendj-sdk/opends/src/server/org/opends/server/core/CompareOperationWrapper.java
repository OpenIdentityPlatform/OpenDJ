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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;


/**
 * This abstract class wraps/decorates a given compare operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the CompareOperationBasis.
 */
public abstract class CompareOperationWrapper
  extends OperationWrapper
  implements CompareOperation
{
  // The wrapped operation
  private CompareOperation compare;


  /**
   * Creates a new compare operation based on the provided compare operation.
   *
   * @param compare The compare operation to wrap
   */
  public CompareOperationWrapper(CompareOperation compare)
  {
    super(compare);
    this.compare = compare;
  }


  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN()
  {
    return compare.getRawEntryDN();
  }


  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    compare.setRawEntryDN(rawEntryDN);
  }


  /**
   * {@inheritDoc}
   */
  public DN getEntryDN()
  {
    return compare.getEntryDN();
  }


  /**
   * {@inheritDoc}
   */
  public String getRawAttributeType()
  {
    return compare.getRawAttributeType();
  }


  /**
   * {@inheritDoc}
   */
  public void setRawAttributeType(String rawAttributeType)
  {
    compare.setRawAttributeType(rawAttributeType);
  }


  /**
   * {@inheritDoc}
   */
  public AttributeType getAttributeType()
  {
    return compare.getAttributeType();
  }


  /**
   * {@inheritDoc}
   */
  public void setAttributeType(AttributeType attributeType)
  {
    compare.setAttributeType(attributeType);
  }


  /**
   * {@inheritDoc}
   */
  public ByteString getAssertionValue()
  {
    return compare.getAssertionValue();
  }


  /**
   * {@inheritDoc}
   */
  public void setAssertionValue(ByteString assertionValue)
  {
    compare.setAssertionValue(assertionValue);
  }


  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return compare.getProxiedAuthorizationDN();
  }


  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    compare.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
