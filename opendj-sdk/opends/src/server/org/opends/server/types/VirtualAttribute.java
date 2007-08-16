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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class VirtualAttribute
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
   * Retrieves the set of values for this attribute.  The returned set
   * of values may be altered by the caller.
   *
   * @return  The set of values for this attribute.
   */
  @Override()
  public LinkedHashSet<AttributeValue> getValues()
  {
    return provider.getValues(entry, rule);
  }



  /**
   * Indicates whether this attribute contains one or more values.
   *
   * @return  <CODE>true</CODE> if this attribute contains one or more
   *          values, or <CODE>false</CODE> if it does not.
   */
  @Override()
  public boolean hasValue()
  {
    return provider.hasValue(entry, rule);
  }



  /**
   * Indicates whether this attribute contains the specified value.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this attribute has the specified
   *          value, or <CODE>false</CODE> if not.
   */
  @Override()
  public boolean hasValue(AttributeValue value)
  {
    return provider.hasValue(entry, rule, value);
  }



  /**
   * Indicates whether this attribute contains all the values in the
   * collection.
   *
   * @param  values  The set of values for which to make the
   *                 determination.
   *
   * @return  <CODE>true</CODE> if this attribute contains all the
   *          values in the provided collection, or <CODE>false</CODE>
   *          if it does not contain at least one of them.
   */
  @Override()
  public boolean hasAllValues(Collection<AttributeValue> values)
  {
    return provider.hasAllValues(entry, rule, values);
  }



  /**
   * Indicates whether this attribute contains any of the values in
   * the collection.
   *
   * @param  values  The set of values for which to make the
   *                 determination.
   *
   * @return  <CODE>true</CODE> if this attribute contains at least
   *          one of the values in the provided collection, or
   *          <CODE>false</CODE> if it does not contain any of the
   *          values.
   */
  @Override()
  public boolean hasAnyValue(Collection<AttributeValue> values)
  {
    return provider.hasAnyValue(entry, rule, values);
  }



  /**
   * Indicates whether this attribute has any value(s) that match the
   * provided substring.
   *
   * @param  subInitial  The subInitial component to use in the
   *                     determination.
   * @param  subAny      The subAny components to use in the
   *                     determination.
   * @param  subFinal    The subFinal component to use in the
   *                     determination.
   *
   * @return  <CODE>UNDEFINED</CODE> if this attribute does not have a
   *          substring matching rule, <CODE>TRUE</CODE> if at least
   *          one value matches the provided substring, or
   *          <CODE>FALSE</CODE> otherwise.
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
   * Indicates whether this attribute has any value(s) that are
   * greater than or equal to the provided value.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>UNDEFINED</CODE> if this attribute does not have
   *          an ordering matching rule, <CODE>TRUE</CODE> if at least
   *          one value is greater than or equal to the provided
   *          value, or <CODE>false</CODE> otherwise.
   */
  @Override()
  public ConditionResult greaterThanOrEqualTo(AttributeValue value)
  {
    return provider.greaterThanOrEqualTo(entry, rule, value);
  }



  /**
   * Indicates whether this attribute has any value(s) that are less
   * than or equal to the provided value.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>UNDEFINED</CODE> if this attribute does not have
   *          an ordering matching rule, <CODE>TRUE</CODE> if at least
   *          one value is less than or equal to the provided value,
   *          or <CODE>false</CODE> otherwise.
   */
  @Override()
  public ConditionResult lessThanOrEqualTo(AttributeValue value)
  {
    return provider.lessThanOrEqualTo(entry, rule, value);
  }



  /**
   * Indicates whether this attribute has any value(s) that are
   * approximately equal to the provided value.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>UNDEFINED</CODE> if this attribute does not have
   *          an approximate matching rule, <CODE>TRUE</CODE> if at
   *          least one value is approximately equal to the provided
   *          value, or <CODE>false</CODE> otherwise.
   */
  @Override()
  public ConditionResult approximatelyEqualTo(AttributeValue value)
  {
    return provider.approximatelyEqualTo(entry, rule, value);
  }



  /**
   * Indicates whether this is a virtual attribute rather than a real
   * attribute.
   *
   * @return  {@code true} if this is a virtual attribute, or
   *          {@code false} if it is a real attribute.
   */
  @Override()
  public boolean isVirtual()
  {
    return true;
  }



  /**
   * Creates a duplicate of this attribute that can be modified
   * without impacting this attribute.
   *
   * @param omitValues <CODE>true</CODE> if the values should be
   *        omitted.
   *
   * @return  A duplicate of this attribute that can be modified
   *          without impacting this attribute.
   */
  @Override()
  public Attribute duplicate(boolean omitValues)
  {
    return new VirtualAttribute(getAttributeType(), entry, rule);
  }



  /**
   * Appends a one-line string representation of this attribute to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
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

