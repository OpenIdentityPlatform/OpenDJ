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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

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

  @Override
  public void addObjectClass(ObjectClass objectClass, String name)
  {
    getOperation().addObjectClass(objectClass, name);
  }

  @Override
  public void addRawAttribute(RawAttribute rawAttribute)
  {
    getOperation().addRawAttribute(rawAttribute);
  }

  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }

  @Override
  public Map<ObjectClass, String> getObjectClasses()
  {
    return getOperation().getObjectClasses();
  }

  @Override
  public Map<AttributeType, List<Attribute>> getOperationalAttributes()
  {
    return getOperation().getOperationalAttributes();
  }

  @Override
  public List<RawAttribute> getRawAttributes()
  {
    return getOperation().getRawAttributes();
  }

  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }

  @Override
  public Map<AttributeType, List<Attribute>> getUserAttributes()
  {
    return getOperation().getUserAttributes();
  }

  @Override
  public void removeAttribute(AttributeType attributeType)
  {
    getOperation().removeAttribute(attributeType);
  }

  @Override
  public void removeObjectClass(ObjectClass objectClass)
  {
    getOperation().removeObjectClass(objectClass);
  }

  @Override
  public void setAttribute(AttributeType attributeType,
      List<Attribute> attributeList)
  {
    getOperation().setAttribute(attributeType, attributeList);
  }

  @Override
  public void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    getOperation().setRawAttributes(rawAttributes);
  }

  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  @Override
  public String toString()
  {
    return getOperation().toString();
  }
}
