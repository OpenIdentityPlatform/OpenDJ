/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.List;
import java.util.Map;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;

/**
 * This abstract class wraps/decorates a given add operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the AddOperationBasis.
 */
public abstract class AddOperationWrapper extends
    OperationWrapper<AddOperation> implements AddOperation
{

  /**
   * Creates a new add operation based on the provided add operation.
   *
   * @param add The add operation to wrap
   */
  public AddOperationWrapper(AddOperation add)
  {
    super(add);
  }

  /** {@inheritDoc} */
  @Override
  public void addObjectClass(ObjectClass objectClass, String name)
  {
    getOperation().addObjectClass(objectClass, name);
  }

  /** {@inheritDoc} */
  @Override
  public void addRawAttribute(RawAttribute rawAttribute)
  {
    getOperation().addRawAttribute(rawAttribute);
  }

  /** {@inheritDoc} */
  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }

  /** {@inheritDoc} */
  @Override
  public Map<ObjectClass, String> getObjectClasses()
  {
    return getOperation().getObjectClasses();
  }

  /** {@inheritDoc} */
  @Override
  public Map<AttributeType, List<Attribute>> getOperationalAttributes()
  {
    return getOperation().getOperationalAttributes();
  }

  /** {@inheritDoc} */
  @Override
  public List<RawAttribute> getRawAttributes()
  {
    return getOperation().getRawAttributes();
  }

  /** {@inheritDoc} */
  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }

  /** {@inheritDoc} */
  @Override
  public Map<AttributeType, List<Attribute>> getUserAttributes()
  {
    return getOperation().getUserAttributes();
  }

  /** {@inheritDoc} */
  @Override
  public void removeAttribute(AttributeType attributeType)
  {
    getOperation().removeAttribute(attributeType);
  }

  /** {@inheritDoc} */
  @Override
  public void removeObjectClass(ObjectClass objectClass)
  {
    getOperation().removeObjectClass(objectClass);
  }

  /** {@inheritDoc} */
  @Override
  public void setAttribute(AttributeType attributeType,
      List<Attribute> attributeList)
  {
    getOperation().setAttribute(attributeType, attributeList);
  }

  /** {@inheritDoc} */
  @Override
  public void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    getOperation().setRawAttributes(rawAttributes);
  }

  /** {@inheritDoc} */
  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getOperation().toString();
  }

}
