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

import java.util.List;
import java.util.Map;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RawAttribute;


/**
 * This abstract class wraps/decorates a given add operation.
 * This class will be extended by sub-classes to enhance the
 * functionnality of the AddOperationBasis.
 */
public abstract class AddOperationWrapper extends OperationWrapper
       implements AddOperation
{
  // The wrapped operation.
  private AddOperation add;

  /**
   * Creates a new add operation based on the provided add operation.
   *
   * @param add The add operation to wrap
   */
  public AddOperationWrapper(AddOperation add)
  {
    super(add);
    this.add = add;
  }

  /**
   * {@inheritDoc}
   */
  public void addObjectClass(ObjectClass objectClass, String name)
  {
    add.addObjectClass(objectClass, name);
  }

  /**
   * {@inheritDoc}
   */
  public void addRawAttribute(RawAttribute rawAttribute)
  {
    add.addRawAttribute(rawAttribute);
  }

  /**
   * {@inheritDoc}
   */
  public long getChangeNumber()
  {
    return add.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  public DN getEntryDN()
  {
    return add.getEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public Map<ObjectClass, String> getObjectClasses()
  {
    return add.getObjectClasses();
  }

  /**
   * {@inheritDoc}
   */
  public Map<AttributeType, List<Attribute>> getOperationalAttributes()
  {
    return add.getOperationalAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public List<RawAttribute> getRawAttributes()
  {
    return add.getRawAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString getRawEntryDN()
  {
    return add.getRawEntryDN();
  }

  /**
   * {@inheritDoc}
   */
  public Map<AttributeType, List<Attribute>> getUserAttributes()
  {
    return add.getUserAttributes();
  }

  /**
   * {@inheritDoc}
   */
  public void removeAttribute(AttributeType attributeType)
  {
    add.removeAttribute(attributeType);
  }

  /**
   * {@inheritDoc}
   */
  public void removeObjectClass(ObjectClass objectClass)
  {
    add.removeObjectClass(objectClass);
  }

  /**
   * {@inheritDoc}
   */
  public void setAttribute(AttributeType attributeType,
      List<Attribute> attributeList)
  {
    add.setAttribute(attributeType, attributeList);
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber)
  {
    add.setChangeNumber(changeNumber);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    add.setRawAttributes(rawAttributes);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    add.setRawEntryDN(rawEntryDN);
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return add.toString();
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return add.getProxiedAuthorizationDN();
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    add.setProxiedAuthorizationDN(proxiedAuthorizationDN);
  }

}
