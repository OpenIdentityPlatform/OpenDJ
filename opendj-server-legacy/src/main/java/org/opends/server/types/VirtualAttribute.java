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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.util.Utils;
import org.opends.server.api.VirtualAttributeProvider;

/**
 * This class defines a virtual attribute, which is a special kind of
 * attribute whose values do not actually exist in persistent storage
 * but rather are computed or otherwise obtained dynamically.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = false,
    mayInvoke = true)
public final class VirtualAttribute
  extends AbstractAttribute
{
  /** The attribute description. */
  private final AttributeDescription attributeDescription;
  /** The entry with which this virtual attribute is associated. */
  private final Entry entry;
  /** The virtual attribute provider for this virtual attribute. */
  private final VirtualAttributeProvider<?> provider;
  /** The virtual attribute rule for this virtual attribute. */
  private final VirtualAttributeRule rule;



  /**
   * Creates a new virtual attribute with the provided information.
   *
   * @param attributeType
   *          The attribute type for this virtual attribute.
   * @param entry
   *          The entry in which this virtual attribute exists.
   * @param rule
   *          The virtual attribute rule that governs the behavior of
   *          this virtual attribute.
   */
  public VirtualAttribute(AttributeType attributeType, Entry entry,
      VirtualAttributeRule rule)
  {
    this.attributeDescription = AttributeDescription.create(attributeType);
    this.entry = entry;
    this.rule = rule;
    this.provider = rule.getProvider();
  }

  @Override
  public ConditionResult approximatelyEqualTo(ByteString assertionValue)
  {
    return provider.approximatelyEqualTo(entry, rule, assertionValue);
  }

  @Override
  public boolean contains(ByteString value)
  {
    return provider.hasValue(entry, rule, value);
  }

  @Override
  public boolean containsAll(Collection<?> values)
  {
    return provider.hasAllValues(entry, rule, values);
  }

  @Override
  public ConditionResult matchesEqualityAssertion(ByteString assertionValue)
  {
    return provider.matchesEqualityAssertion(entry, rule, assertionValue);
  }

  @Override
  public AttributeDescription getAttributeDescription()
  {
    return attributeDescription;
  }

  /**
   * Retrieves the virtual attribute rule that governs the behavior of
   * this virtual attribute.
   *
   * @return The virtual attribute rule that governs the behavior of
   *         this virtual attribute.
   */
  public VirtualAttributeRule getVirtualAttributeRule()
  {
    return rule;
  }

  @Override
  public ConditionResult greaterThanOrEqualTo(ByteString assertionValue)
  {
    return provider.greaterThanOrEqualTo(entry, rule, assertionValue);
  }

  @Override
  public boolean isEmpty()
  {
    return !provider.hasValue(entry, rule);
  }

  @Override
  public boolean isVirtual()
  {
    return true;
  }

  @Override
  public Iterator<ByteString> iterator()
  {
    return provider.getValues(entry, rule).iterator();
  }

  @Override
  public ConditionResult lessThanOrEqualTo(ByteString assertionValue)
  {
    return provider.lessThanOrEqualTo(entry, rule, assertionValue);
  }

  @Override
  public ConditionResult matchesSubstring(ByteString subInitial,
      List<ByteString> subAny, ByteString subFinal)
  {
    return provider.matchesSubstring(entry, rule, subInitial, subAny, subFinal);
  }

  @Override
  public int size()
  {
    if (provider.isMultiValued())
    {
      return provider.getValues(entry, rule).size();
    }
    return provider.hasValue(entry, rule) ? 1 : 0;
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("VirtualAttribute(");
    buffer.append(getAttributeDescription().getAttributeType().getNameOrOID());
    buffer.append(", {");
    Utils.joinAsString(buffer, ", ", this);
    buffer.append("})");
  }
}
