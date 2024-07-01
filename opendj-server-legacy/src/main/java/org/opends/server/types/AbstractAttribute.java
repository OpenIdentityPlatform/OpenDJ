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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Collection;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;

/** An abstract base class for implementing new types of {@link Attribute}. */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = false,
    mayExtend = false,
    mayInvoke = true)
public abstract class AbstractAttribute implements Attribute
{
  /** Creates a new abstract attribute. */
  protected AbstractAttribute()
  {
    // No implementation required.
  }

  @Override
  public boolean containsAll(Collection<?> values)
  {
    for (Object value : values)
    {
      if (!contains(ByteString.valueOfObject(value)))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public final boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof Attribute))
    {
      return false;
    }

    Attribute a = (Attribute) o;
    return getAttributeDescription().equals(a.getAttributeDescription()) && valuesEqual(a);
  }

  private boolean valuesEqual(Attribute a)
  {
    if (size() != a.size())
    {
      return false;
    }

    for (ByteString v : a)
    {
      if (!contains(v))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode()
  {
    AttributeType attrType = getAttributeDescription().getAttributeType();
    int hashCode = attrType.hashCode();
    for (ByteString value : this)
    {
      try
      {
        MatchingRule eqRule = attrType.getEqualityMatchingRule();
        hashCode += eqRule.normalizeAttributeValue(value).hashCode();
      }
      catch (DecodeException e)
      {
        hashCode += value.hashCode();
      }
    }
    return hashCode;
  }

  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns <code>true</code> if the
   * {@link #size()} of this attribute is zero.
   */
  @Override
  public boolean isEmpty()
  {
    return size() == 0;
  }

  @Override
  public boolean isReal()
  {
    return !isVirtual();
  }

  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }
}
