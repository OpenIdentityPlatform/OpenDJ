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
package org.opends.server.types;



import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.admin.std.server.VirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;



/**
 * This class defines a virtual attribute, which is a special kind of
 * attribute whose values do not actually exist in persistent storage
 * but rather are computed or otherwise obtained dynamically.
 */
public class VirtualAttribute
       extends Attribute
{
  // The entry with which this virtual attribute is associated.
  private final Entry entry;

  // The virtual attribute provider for this virtual attribute.
  private final VirtualAttributeProvider<
                     ? extends VirtualAttributeCfg> provider;

  // The virtual attribute rule for this virtual attribute.
  private final VirtualAttributeRule rule;



  /**
   * Creates a new virtual attribute with the provided information.
   *
   * @param  attributeType  The attribute type for this virtual
   *                        attribute.
   * @param  entry          The entry in which this virtual attribute
   *                        exists.
* @param  rule           The virutal attribute rule that governs
   *                        the behavior of this virtual attribute.
   */
  public VirtualAttribute(AttributeType attributeType, Entry entry,
                          VirtualAttributeRule rule)
  {
    super(attributeType);

    this.entry = entry;
    this.rule  = rule;

    provider = rule.getProvider();
  }



  /**
   * Retrieves the entry in which this virtual attribute exists.
   *
   * @return  The entry in which this virtual attribute exists.
   */
  public Entry getEntry()
  {
    return entry;
  }



  /**
   * Retrieves the virtual attribute rule that governs the behavior of
   * this virtual attribute.
   *
   * @return  The virtual attribute rule that governs the behavior of
   *          this virtual attribute.
   */
  public VirtualAttributeRule getVirtualAttributeRule()
  {
    return rule;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public LinkedHashSet<AttributeValue> getValues()
  {
    return provider.getValues(entry, rule);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue()
  {
    return provider.hasValue(entry, rule);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasValue(AttributeValue value)
  {
    return provider.hasValue(entry, rule, value);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasAllValues(Collection<AttributeValue> values)
  {
    return provider.hasAllValues(entry, rule, values);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasAnyValue(Collection<AttributeValue> values)
  {
    return provider.hasAnyValue(entry, rule, values);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult matchesSubstring(ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    return provider.matchesSubstring(entry, rule, subInitial, subAny,
                                     subFinal);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult greaterThanOrEqualTo(AttributeValue value)
  {
    return provider.greaterThanOrEqualTo(entry, rule, value);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult lessThanOrEqualTo(AttributeValue value)
  {
    return provider.lessThanOrEqualTo(entry, rule, value);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult approximatelyEqualTo(AttributeValue value)
  {
    return provider.approximatelyEqualTo(entry, rule, value);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isVirtual()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Attribute duplicate(boolean omitValues)
  {
    return new VirtualAttribute(getAttributeType(), entry, rule);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toString(StringBuilder buffer)
  {
    buffer.append("VirtualAttribute(");
    buffer.append(getAttributeType().getNameOrOID());
    buffer.append(", {");

    boolean firstValue = true;
    for (AttributeValue value : getValues())
    {
      if (! firstValue)
      {
        buffer.append(", ");
      }

      value.toString(buffer);
      firstValue = false;
    }

    buffer.append("})");
  }
}

