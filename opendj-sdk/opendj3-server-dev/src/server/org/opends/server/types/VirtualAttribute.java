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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The attribute type. */
  private final AttributeType attributeType;

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
    this.attributeType = attributeType;
    this.entry = entry;
    this.rule = rule;
    this.provider = rule.getProvider();
  }



  /** {@inheritDoc} */
  @Override
  public ConditionResult approximatelyEqualTo(ByteString assertionValue)
  {
    return provider.approximatelyEqualTo(entry, rule, assertionValue);
  }



  /** {@inheritDoc} */
  @Override
  public boolean contains(ByteString value)
  {
    return provider.hasValue(entry, rule, value);
  }



  /** {@inheritDoc} */
  @Override
  public boolean containsAll(Collection<ByteString> values)
  {
    return provider.hasAllValues(entry, rule, values);
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult matchesEqualityAssertion(ByteString assertionValue)
  {
    return provider.matchesEqualityAssertion(entry, rule, assertionValue);
  }


  /** {@inheritDoc} */
  @Override
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /** {@inheritDoc} */
  @Override
  public String getNameWithOptions()
  {
    return getName();
  }



  /** {@inheritDoc} */
  @Override
  public Set<String> getOptions()
  {
    return Collections.emptySet();
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



  /** {@inheritDoc} */
  @Override
  public ConditionResult greaterThanOrEqualTo(ByteString assertionValue)
  {
    return provider.greaterThanOrEqualTo(entry, rule, assertionValue);
  }



  /** {@inheritDoc} */
  @Override
  public boolean hasAllOptions(Collection<String> options)
  {
    return options == null || options.isEmpty();
  }



  /** {@inheritDoc} */
  @Override
  public boolean hasOption(String option)
  {
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public boolean hasOptions()
  {
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isEmpty()
  {
    return !provider.hasValue(entry, rule);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isVirtual()
  {
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public Iterator<ByteString> iterator()
  {
    return provider.getValues(entry, rule).iterator();
  }



  /** {@inheritDoc} */
  @Override
  public ConditionResult lessThanOrEqualTo(ByteString assertionValue)
  {
    return provider.lessThanOrEqualTo(entry, rule, assertionValue);
  }



  /** {@inheritDoc} */
  @Override
  public ConditionResult matchesSubstring(ByteString subInitial,
      List<ByteString> subAny, ByteString subFinal)
  {
    return provider.matchesSubstring(entry, rule, subInitial, subAny, subFinal);
  }



  /** {@inheritDoc} */
  @Override
  public boolean optionsEqual(Set<String> options)
  {
    return options == null || options.isEmpty();
  }



  /** {@inheritDoc} */
  @Override
  public int size()
  {
    if (provider.isMultiValued())
    {
      return provider.getValues(entry, rule).size();
    }
    return provider.hasValue(entry, rule) ? 1 : 0;
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("VirtualAttribute(");
    buffer.append(getAttributeType().getNameOrOID());
    buffer.append(", {");
    buffer.append(Utils.joinAsString(", ", this));
    buffer.append("})");
  }

}
