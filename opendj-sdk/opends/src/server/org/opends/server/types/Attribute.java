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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.Base64;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for storing and interacting
 * with an attribute that may be used in the Directory Server.
 */
public class Attribute
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.Attribute";



  // The attribute type for this attribute.
  private AttributeType attributeType;

  // The set of values for this attribute.
  private LinkedHashSet<AttributeValue> values;

  // The set of options for this attribute.
  private LinkedHashSet<String> options;

  // The name of this attribute as provided by the end user.
  private String name;



  /**
   * Creates a new attribute with the specified type.  It will not
   * have any values.
   *
   * @param  attributeType  The attribute type for this attribute.
   */
  public Attribute(AttributeType attributeType)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(attributeType));

    this.attributeType = attributeType;
    this.name          = attributeType.getPrimaryName();
    this.options       = new LinkedHashSet<String>(0);
    this.values        = new LinkedHashSet<AttributeValue>();
  }



  /**
   * Creates a new attribute with the specified type and user-provided
   * name.  It * will not have any values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  name           The user-provided name for this attribute.
   */
  public Attribute(AttributeType attributeType, String name)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType),
                            String.valueOf(name));

    this.attributeType = attributeType;
    this.name          = name;
    this.options       = new LinkedHashSet<String>(0);
    this.values        = new LinkedHashSet<AttributeValue>();
  }



  /**
   * Creates a new attribute with the specified type, user-provided
   * name, and set of values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  name           The user-provided name for this attribute.
   * @param  values         The set of values for this attribute.
   */
  public Attribute(AttributeType attributeType, String name,
                   LinkedHashSet<AttributeValue> values)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType),
                            String.valueOf(name),
                            String.valueOf(values));

    this.attributeType = attributeType;
    this.name          = name;
    this.options       = new LinkedHashSet<String>(0);

    if (values == null)
    {
      this.values = new LinkedHashSet<AttributeValue>();
    }
    else
    {
      this.values = values;
    }
  }



  /**
   * Creates a new attribute with the specified type, user-provided
   * name, and set of values.
   *
   * @param  typeString   The String representation of the attribute
   *                      type for this attribute.
   * @param  valueString  The String representation of the attribute
   *                      value
   */
  public Attribute(String typeString, String valueString)
  {
    this.attributeType =
         DirectoryServer.getAttributeType(typeString, true);
    this.name = typeString;
    this.values = new LinkedHashSet<AttributeValue>();
    this.values.add(new AttributeValue(this.attributeType,
                                       valueString));
    this.options = new LinkedHashSet<String>(0);
  }



  /**
   * Creates a new attribute with the specified type, user-provided
   * name, and set of values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  name           The user-provided name for this attribute.
   * @param  options        The set of options for this attribute.
   * @param  values         The set of values for this attribute.
   */
  public Attribute(AttributeType attributeType, String name,
                   LinkedHashSet<String> options,
                   LinkedHashSet<AttributeValue> values)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType),
                            String.valueOf(name),
                            String.valueOf(options),
                            String.valueOf(values));

    this.attributeType = attributeType;
    this.name          = name;

    if (options == null)
    {
      this.options = new LinkedHashSet<String>(0);
    }
    else
    {
      this.options = options;
    }

    if (values == null)
    {
      this.values = new LinkedHashSet<AttributeValue>();
    }
    else
    {
      this.values = values;
    }
  }



  /**
   * Retrieves the attribute type for this attribute.
   *
   * @return  The attribute type for this attribute.
   */
  public AttributeType getAttributeType()
  {
    assert debugEnter(CLASS_NAME, "getAttributeType");

    return attributeType;
  }



  /**
   * Retrieves the user-provided name for this attribute.
   *
   * @return  The user-provided name for this attribute.
   */
  public String getName()
  {
    assert debugEnter(CLASS_NAME, "getName");

    return name;
  }



  /**
   * Retrieves the set of attribute options for this attribute.
   *
   * @return  The set of attribute options for this attribute.
   */
  public LinkedHashSet<String> getOptions()
  {
    assert debugEnter(CLASS_NAME, "getOptions");

    return options;
  }



  /**
   * Indicates whether this attribute has the specified option.
   *
   * @param  option  The option for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this attribute has the specified
   *          option, or <CODE>false</CODE> if not.
   */
  public boolean hasOption(String option)
  {
    assert debugEnter(CLASS_NAME, "hasOption",
                      String.valueOf(option));

    return options.contains(option);
  }



  /**
   * Indicates whether this attribute has any options at all.
   *
   * @return  <CODE>true</CODE> if this attribute has at least one
   *          option, or <CODE>false</CODE> if not.
   */
  public boolean hasOptions()
  {
    assert debugEnter(CLASS_NAME, "hasOptions");

    return ((options != null) && (! options.isEmpty()));
  }



  /**
   * Indicates whether this attribute has all of the options in the
   * provided collection.
   *
   * @param  options  The collection of options for which to make the
   *                  determination.
   *
   * @return  <CODE>true</CODE> if this attribute has all of the
   *          specified options, or <CODE>false</CODE> if it does not
   *          have at least one of them.
   */
  public boolean hasOptions(Collection<String> options)
  {
    assert debugEnter(CLASS_NAME, "hasOptions",
                      String.valueOf(options));

    if (options == null)
    {
      return true;
    }

    for (String option : options)
    {
      if (! this.options.contains(option))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Indicates whether this attribute has exactly the set of options
   * in the provided set.
   *
   * @param  options  The set of options for which to make the
   *                  determination.
   *
   * @return  <CODE>true</CODE> if this attribute has exactly the
   *          specified set of options, or <CODE>false</CODE> if the
   *          set of options is different in any way.
   */
  public boolean optionsEqual(Set<String> options)
  {
    assert debugEnter(CLASS_NAME, "optionsEqual",
                      String.valueOf(options));

    if (options == null)
    {
      return this.options == null || this.options.isEmpty();
    }

    if (options.isEmpty() && this.options.isEmpty())
    {
      return true;
    }

    if (options.size() != this.options.size())
    {
      return false;
    }

    for (String s : options)
    {
      if (! this.options.contains(s))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Retrieves the set of values for this attribute.  The returned set
   * of values may be altered by the caller.
   *
   * @return  The set of values for this attribute.
   */
  public LinkedHashSet<AttributeValue> getValues()
  {
    assert debugEnter(CLASS_NAME, "getValues");

    return values;
  }



  /**
   * Specifies the set of values for this attribute.
   *
   * @param  values  The set of values for this attribute.
   */
  public void setValues(LinkedHashSet<AttributeValue> values)
  {
    assert debugEnter(CLASS_NAME, "setValues",
                      String.valueOf(values));

    if (values == null)
    {
      this.values = new LinkedHashSet<AttributeValue>();
    }
    else
    {
      this.values = values;
    }
  }



  /**
   * Indicates whether this attribute contains one or more values.
   *
   * @return  <CODE>true</CODE> if this attribute contains one or more
   *          values, or <CODE>false</CODE> if it does not.
   */
  public boolean hasValue()
  {
    assert debugEnter(CLASS_NAME, "hasValue");

    return (! values.isEmpty());
  }



  /**
   * Indicates whether this attribute contains the specified value.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this attribute has the specified
   *          value, or <CODE>false</CODE> if not.
   */
  public boolean hasValue(AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "hasValue", String.valueOf(value));

    return values.contains(value);
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
  public boolean hasAllValues(Collection<AttributeValue> values)
  {
    assert debugEnter(CLASS_NAME, "hasAllValues",
                      String.valueOf(values));

    for (AttributeValue value : values)
    {
      if (! this.values.contains(value))
      {
        return false;
      }
    }

    return true;
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
  public boolean hasAnyValue(Collection<AttributeValue> values)
  {
    assert debugEnter(CLASS_NAME, "hasAnyValue",
                      String.valueOf(values));

    for (AttributeValue value : values)
    {
      if (this.values.contains(value))
      {
        return true;
      }
    }

    return false;
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
  public ConditionResult matchesSubstring(ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    assert debugEnter(CLASS_NAME, "matchesSubstring",
                      String.valueOf(subInitial),
                      String.valueOf(subAny),
                      String.valueOf(subFinal));


    SubstringMatchingRule matchingRule =
         attributeType.getSubstringMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }


    ByteString normalizedSubInitial;
    if (subInitial == null)
    {
      normalizedSubInitial = null;
    }
    else
    {
      try
      {
        normalizedSubInitial =
             matchingRule.normalizeSubstring(subInitial);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "matchesSubstring", e);

        // The substring couldn't be normalized.  We have to return
        // "undefined".
        return ConditionResult.UNDEFINED;
      }
    }


    ArrayList<ByteString> normalizedSubAny;
    if (subAny == null)
    {
      normalizedSubAny = null;
    }
    else
    {
      normalizedSubAny =
           new ArrayList<ByteString>(subAny.size());
      for (ByteString subAnyElement : subAny)
      {
        try
        {
          normalizedSubAny.add(matchingRule.normalizeSubstring(
                                                 subAnyElement));
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "matchesSubstring", e);

          // The substring couldn't be normalized.  We have to return
          // "undefined".
          return ConditionResult.UNDEFINED;
        }
      }
    }


    ByteString normalizedSubFinal;
    if (subFinal == null)
    {
      normalizedSubFinal = null;
    }
    else
    {
      try
      {
        normalizedSubFinal =
             matchingRule.normalizeSubstring(subFinal);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "matchesSubstring", e);

        // The substring couldn't be normalized.  We have to return
        // "undefined".
        return ConditionResult.UNDEFINED;
      }
    }


    ConditionResult result = ConditionResult.FALSE;
    for (AttributeValue value : values)
    {
      try
      {
        if (matchingRule.valueMatchesSubstring(
                              value.getNormalizedValue(),
                              normalizedSubInitial,
                              normalizedSubAny,
                              normalizedSubFinal))
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "matchesSubstring", e);

        // The value couldn't be normalized.  If we can't find a
        // definite match, then we should return "undefined".
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
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
  public ConditionResult greaterThanOrEqualTo(AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "greaterThanOrEqualTo",
                      String.valueOf(value));


    OrderingMatchingRule matchingRule =
         attributeType.getOrderingMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    ByteString normalizedValue;
    try
    {
      normalizedValue = value.getNormalizedValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "greaterThanOrEqualTo", e);

      // We couldn't normalize the provided value.  We should return
      // "undefined".
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (AttributeValue v : values)
    {
      try
      {
        ByteString nv = v.getNormalizedValue();
        int comparisonResult =
                 matchingRule.compareValues(nv, normalizedValue);
        if (comparisonResult >= 0)
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "greaterThanOrEqualTo", e);

        // We couldn't normalize one of the attribute values.  If we
        // can't find a definite match, then we should return
        // "undefined".
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
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
  public ConditionResult lessThanOrEqualTo(AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "lessThanOrEqualTo",
                      String.valueOf(value));


    OrderingMatchingRule matchingRule =
         attributeType.getOrderingMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    ByteString normalizedValue;
    try
    {
      normalizedValue = value.getNormalizedValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "lessThanOrEqualTo", e);

      // We couldn't normalize the provided value.  We should return
      // "undefined".
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (AttributeValue v : values)
    {
      try
      {
        ByteString nv = v.getNormalizedValue();
        int comparisonResult =
                 matchingRule.compareValues(nv, normalizedValue);
        if (comparisonResult <= 0)
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "lessThanOrEqualTo", e);

        // We couldn't normalize one of the attribute values.  If we
        // can't find a definite match, then we should return
        // "undefined".
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
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
  public ConditionResult approximatelyEqualTo(AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "approximatelyEqualTo",
                      String.valueOf(value));


    ApproximateMatchingRule matchingRule =
         attributeType.getApproximateMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    ByteString normalizedValue;
    try
    {
      normalizedValue = value.getNormalizedValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "approximatelyEqualTo", e);

      // We couldn't normalize the provided value.  We should return
      // "undefined".
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (AttributeValue v : values)
    {
      try
      {
        ByteString nv = v.getNormalizedValue();
        if (matchingRule.approximatelyMatch(nv, normalizedValue))
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "approximatelyEqualTo", e);

        // We couldn't normalize one of the attribute values.  If we
        // can't find a definite match, then we should return
        // "undefined".
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
  }



  /**
   * Creates a duplicate of this attribute that can be modified
   * without impacting this attribute.
   *
   * @return  A duplicate of this attribute that can be modified
   *          without impacting this attribute.
   */
  public Attribute duplicate()
  {
    assert debugEnter(CLASS_NAME, "duplicate");

    LinkedHashSet<String> optionsCopy =
         new LinkedHashSet<String>(options.size());
    for (String s : options)
    {
      optionsCopy.add(s);
    }

    LinkedHashSet<AttributeValue> valuesCopy =
         new LinkedHashSet<AttributeValue>(values.size());
    for (AttributeValue v : values)
    {
      valuesCopy.add(v);
    }

    return new Attribute(attributeType, name, optionsCopy,
                         valuesCopy);
  }



  /**
   * Indicates whether the provided object is an attribute that is
   * equal to this attribute.  It will be considered equal if the
   * attribute type, set of values, and set of options are equal.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is an attribute
   *          that is equal to this attribute, or <CODE>false</CODE>
   *          if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof Attribute)))
    {
      return false;
    }

    Attribute a = (Attribute) o;
    if (! attributeType.equals(a.attributeType))
    {
      return false;
    }

    if (values.size() != a.values.size())
    {
      return false;
    }

    if (! hasAllValues(a.values))
    {
      return false;
    }

    return optionsEqual(a.options);
  }



  /**
   * Retrieves the hash code for this attribute.  It will be
   * calculated as the sum of the hash code for the attribute type and
   * all values.
   *
   * @return  The hash code for this attribute.
   */
  public int hashCode()
  {
    assert debugEnter(CLASS_NAME, "hashCode");

    int hashCode = attributeType.hashCode();
    for (AttributeValue value : values)
    {
      hashCode += value.hashCode();
    }

    return hashCode;
  }



  /**
   * Retrieves a one-line string representation of this attribute.
   *
   * @return  A one-line string representation of this attribute.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a one-line string representation of this attribute to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

    buffer.append("Attribute(");
    buffer.append(name);
    buffer.append(", {");

    boolean firstValue = true;
    for (AttributeValue value : values)
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



  /**
   * Retrieves a string representation of this attribute in LDIF form.
   *
   * @return  A string representation of this attribute in LDIF form.
   */
  public String toLDIF()
  {
    assert debugEnter(CLASS_NAME, "toLDIF");

    StringBuilder buffer = new StringBuilder();
    toLDIF(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this attribute in LDIF form to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toLDIF(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toLDIF",
                      "java.lang.StringBuilder");

    for (AttributeValue value : values)
    {
      buffer.append(name);

      if (needsBase64Encoding(value.getValueBytes()))
      {
        buffer.append("::");
        buffer.append(Base64.encode(value.getValueBytes()));
      }
      else
      {
        buffer.append(": ");
        buffer.append(value.getStringValue());
      }

      buffer.append(EOL);
    }
  }
}

