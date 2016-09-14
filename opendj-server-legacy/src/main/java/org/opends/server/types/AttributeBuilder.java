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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.forgerock.util.Reject.checkNotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.opends.server.types.Attribute.RemoveOnceSwitchingAttributes;
import org.opends.server.util.CollectionUtils;

import com.forgerock.opendj.util.SmallSet;

/**
 * This class provides an interface for creating new non-virtual
 * {@link Attribute}s, or "real" attributes.
 * <p>
 * An attribute can be created incrementally using either
 * {@link #AttributeBuilder(AttributeType)} or
 * {@link #AttributeBuilder(AttributeType, String)}. The caller is
 * then free to add new options using {@link #setOption(String)} and
 * new values using {@link #add(ByteString)} or
 * {@link #addAll(Collection)}. Once all the options and values have
 * been added, the attribute can be retrieved using the
 * {@link #toAttribute()} method.
 * <p>
 * A real attribute can also be created based on the values taken from
 * another attribute using the {@link #AttributeBuilder(Attribute)}
 * constructor. The caller is then free to modify the values within
 * the attribute before retrieving the updated attribute using the
 * {@link #toAttribute()} method.
 * <p>
 * The {@link org.opends.server.types.Attributes} class contains
 * convenience factory methods,
 * e.g. {@link org.opends.server.types.Attributes#empty(String)} for
 * creating empty attributes, and
 * {@link org.opends.server.types.Attributes#create(String, String)}
 * for  creating single-valued attributes.
 * <p>
 * <code>AttributeBuilder</code>s can be re-used. Once an
 * <code>AttributeBuilder</code> has been converted to an
 * {@link Attribute} using {@link #toAttribute()}, its state is reset
 * so that its attribute type, user-provided name, options, and values
 * are all undefined:
 *
 * <pre>
 * AttributeBuilder builder = new AttributeBuilder();
 * for (int i = 0; i &lt; 10; i++)
 * {
 *   builder.setAttributeType(&quot;myAttribute&quot; + i);
 *   builder.setOption(&quot;an-option&quot;);
 *   builder.add(&quot;a value&quot;);
 *   Attribute attribute = builder.toAttribute();
 *   // Do something with attribute...
 * }
 * </pre>
 * <p>
 * <b>Implementation Note:</b> this class is optimized for the common
 * case where there is only a single value. By doing so, we avoid
 * using unnecessary storage space and also performing any unnecessary
 * normalization. In addition, this class is optimized for the common
 * cases where there are zero or one attribute type options.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = true,
    mayExtend = false,
    mayInvoke = true)
@RemoveOnceSwitchingAttributes
public final class AttributeBuilder implements Iterable<ByteString>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A real attribute */
  private static class RealAttribute extends AbstractAttribute
  {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The attribute description for this attribute. */
    private final AttributeDescription attributeDescription;
    /**
     * The unmodifiable set of attribute values, which are lazily normalized.
     * <p>
     * When required, the attribute values are normalized according to the equality matching rule.
     */
    private final Set<AttributeValue> values;

    private RealAttribute(AttributeDescription attributeDescription, Set<AttributeValue> values)
    {
      this.attributeDescription = attributeDescription;
      this.values = values;
    }

    private AttributeType getAttributeType()
    {
      return getAttributeDescription().getAttributeType();
    }

    @Override
    public final ConditionResult approximatelyEqualTo(ByteString assertionValue)
    {
      MatchingRule matchingRule = getAttributeType().getApproximateMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      Assertion assertion = null;
      try
      {
        assertion = matchingRule.getAssertion(assertionValue);
      }
      catch (Exception e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue v : values)
      {
        try
        {
          result = assertion.matches(matchingRule.normalizeAttributeValue(v.getValue()));
        }
        catch (Exception e)
        {
          logger.traceException(e);
          // We could not normalize one of the attribute values.
          // If we cannot find a definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    @Override
    public final boolean contains(ByteString value)
    {
      return values.contains(createAttributeValue(attributeDescription, value));
    }

    @Override
    public ConditionResult matchesEqualityAssertion(ByteString assertionValue)
    {
      try
      {
        MatchingRule eqRule = getAttributeType().getEqualityMatchingRule();
        final Assertion assertion = eqRule.getAssertion(assertionValue);
        for (AttributeValue value : values)
        {
          if (assertion.matches(value.getNormalizedValue()).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        return ConditionResult.FALSE;
      }
      catch (DecodeException e)
      {
        return ConditionResult.UNDEFINED;
      }
    }

    @Override
    public AttributeDescription getAttributeDescription()
    {
      return attributeDescription;
    }

    @Override
    public final ConditionResult greaterThanOrEqualTo(ByteString assertionValue)
    {
      MatchingRule matchingRule = getAttributeType().getOrderingMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      Assertion assertion;
      try
      {
        assertion = matchingRule.getGreaterOrEqualAssertion(assertionValue);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue v : values)
      {
        try
        {
          if (assertion.matches(matchingRule.normalizeAttributeValue(v.getValue())).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
          // We could not normalize one of the attribute values.
          // If we cannot find a definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }

    @Override
    public final boolean isVirtual()
    {
      return false;
    }


    @Override
    public final Iterator<ByteString> iterator()
    {
      return getUnmodifiableIterator(values);
    }

    @Override
    public final ConditionResult lessThanOrEqualTo(ByteString assertionValue)
    {
      MatchingRule matchingRule = getAttributeType().getOrderingMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      Assertion assertion;
      try
      {
        assertion = matchingRule.getLessOrEqualAssertion(assertionValue);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue v : values)
      {
        try
        {
          if (assertion.matches(matchingRule.normalizeAttributeValue(v.getValue())).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // We could not normalize one of the attribute values.
          // If we cannot find a definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    @Override
    public final ConditionResult matchesSubstring(ByteString subInitial, List<ByteString> subAny, ByteString subFinal)
    {
      MatchingRule matchingRule = getAttributeType().getSubstringMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }


      Assertion assertion;
      try
      {
        assertion = matchingRule.getSubstringAssertion(subInitial, subAny, subFinal);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue value : values)
      {
        try
        {
          if (assertion.matches(matchingRule.normalizeAttributeValue(value.getValue())).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // The value could not be normalized.
          // If we cannot find a definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }

    @Override
    public final int size()
    {
      return values.size();
    }

    @Override
    public int hashCode()
    {
      int hashCode = getAttributeType().hashCode();
      for (AttributeValue value : values)
      {
        hashCode += value.hashCode();
      }
      return hashCode;
    }

    @Override
    public final void toString(StringBuilder buffer)
    {
      buffer.append("Attribute(");
      buffer.append(attributeDescription);
      buffer.append(", {");
      Utils.joinAsString(buffer, ", ", values);
      buffer.append("})");
    }
  }

  /**
   * An attribute value which is lazily normalized.
   * <p>
   * Stores the value in user-provided form and a reference to the associated
   * attribute type. The normalized form of the value will be initialized upon
   * first request. The normalized form of the value should only be used in
   * cases where equality matching between two values can be performed with
   * byte-for-byte comparisons of the normalized values.
   */
  private static final class AttributeValue
  {
    private final AttributeDescription attributeDescription;

    /** User-provided value. */
    private final ByteString value;

    /** Normalized value, which is {@code null} until computation is required. */
    private ByteString normalizedValue;

    /**
     * Construct a new attribute value.
     *
     * @param attributeDescription
     *          The attribute description.
     * @param value
     *          The value of the attribute.
     */
    private AttributeValue(AttributeDescription attributeDescription, ByteString value)
    {
      this.attributeDescription = attributeDescription;
      this.value = value;
    }

    /**
     * Retrieves the normalized form of this attribute value.
     *
     * @return The normalized form of this attribute value.
     */
    public ByteString getNormalizedValue()
    {
      if (normalizedValue == null)
      {
        normalizedValue = normalize(attributeDescription, value);
      }
      return normalizedValue;
    }

    boolean isNormalized()
    {
      return normalizedValue != null;
    }

    /**
     * Retrieves the user-defined form of this attribute value.
     *
     * @return The user-defined form of this attribute value.
     */
    public ByteString getValue()
    {
      return value;
    }

    /**
     * Indicates whether the provided object is an attribute value that is equal
     * to this attribute value. It will be considered equal if the normalized
     * representations of both attribute values are equal.
     *
     * @param o
     *          The object for which to make the determination.
     * @return <CODE>true</CODE> if the provided object is an attribute value
     *         that is equal to this attribute value, or <CODE>false</CODE> if
     *         not.
     */
    @Override
    public boolean equals(Object o)
    {
      if (this == o)
      {
        return true;
      }
      else if (o instanceof AttributeValue)
      {
        AttributeValue attrValue = (AttributeValue) o;
        try
        {
          return getNormalizedValue().equals(attrValue.getNormalizedValue());
        }
        catch (Exception e)
        {
          logger.traceException(e);
          return value.equals(attrValue.getValue());
        }
      }
      return false;
    }

    /**
     * Retrieves the hash code for this attribute value. It will be calculated
     * using the normalized representation of the value.
     *
     * @return The hash code for this attribute value.
     */
    @Override
    public int hashCode()
    {
      try
      {
        return getNormalizedValue().hashCode();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        return value.hashCode();
      }
    }

    @Override
    public String toString()
    {
      return value != null ? value.toString() : "null";
    }
  }

  /**
   * Creates an attribute that has no options.
   * <p>
   * This method is only intended for use by the {@link Attributes}
   * class.
   *
   * @param attributeType
   *          The attribute type.
   * @param name
   *          The user-provided attribute name.
   * @param values
   *          The attribute values.
   * @return The new attribute.
   */
  static Attribute create(AttributeType attributeType, String name,
      Set<ByteString> values)
  {
    final AttributeBuilder builder = new AttributeBuilder(attributeType, name);
    builder.addAll(values);
    return builder.toAttribute();
  }

  /** The attribute description for this attribute. */
  private AttributeDescription attributeDescription;
  /** The set of attribute values, which are lazily normalized. */
  private SmallSet<AttributeValue> values = new SmallSet<>();

  /**
   * Creates a new attribute builder from an existing attribute.
   * <p>
   * Modifications to the attribute builder will not impact the
   * provided attribute.
   *
   * @param attribute
   *          The attribute to be copied.
   */
  public AttributeBuilder(Attribute attribute)
  {
    this(attribute.getAttributeDescription());
    addAll(attribute);
  }

  /**
   * Creates a new attribute builder with the specified description.
   *
   * @param attributeDescription
   *          The attribute description for this attribute builder.
   */
  public AttributeBuilder(AttributeDescription attributeDescription)
  {
    this.attributeDescription = checkNotNull(attributeDescription);
  }

  /**
   * Creates a new attribute builder with the specified type and no
   * options and no values.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   */
  public AttributeBuilder(AttributeType attributeType)
  {
    this(AttributeDescription.create(attributeType));
  }



  /**
   * Creates a new attribute builder with the specified type and
   * user-provided name and no options and no values.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   * @param name
   *          The user-provided name for this attribute builder.
   */
  public AttributeBuilder(AttributeType attributeType, String name)
  {
    Reject.ifNull(attributeType, name);

    this.attributeDescription = AttributeDescription.create(name, attributeType);
  }



  /**
   * Creates a new attribute builder with the specified attribute description and no values.
   * <p>
   * If the attribute name cannot be found in the schema, a new attribute type is created using the
   * default attribute syntax.
   *
   * @param attributeDescription
   *          The attribute description for this attribute builder.
   */
  public AttributeBuilder(String attributeDescription)
  {
    this(AttributeDescription.valueOf(attributeDescription));
  }



  /**
   * Adds the specified attribute value to this attribute builder if
   * it is not already present.
   *
   * @param valueString
   *          The string representation of the attribute value to be
   *          added to this attribute builder.
   * @return <code>true</code> if this attribute builder did not
   *         already contain the specified attribute value.
   */
  public boolean add(String valueString)
  {
    return add(ByteString.valueOfUtf8(valueString));
  }



  /**
   * Adds the specified attribute value to this attribute builder if it is not
   * already present.
   *
   * @param attributeValue
   *          The {@link ByteString} representation of the attribute value to be
   *          added to this attribute builder.
   * @return <code>true</code> if this attribute builder did not already contain
   *         the specified attribute value.
   */
  public boolean add(ByteString attributeValue)
  {
    AttributeValue value = createAttributeValue(attributeDescription, attributeValue);
    boolean isNewValue = values.add(value);
    if (!isNewValue)
    {
      // AttributeValue is already present, but the user-provided value may be different
      // There is no direct way to check this, so remove and add to ensure
      // the last user-provided value is recorded
      values.addOrReplace(value);
    }
    return isNewValue;
  }

  /** Creates an attribute value with delayed normalization. */
  private static AttributeValue createAttributeValue(AttributeDescription attributeDescription,
      ByteString attributeValue)
  {
    return new AttributeValue(attributeDescription, attributeValue);
  }

  private static ByteString normalize(AttributeDescription attributeDescription, ByteString attributeValue)
  {
    try
    {
      if (attributeDescription != null)
      {
        final MatchingRule eqRule = attributeDescription.getAttributeType().getEqualityMatchingRule();
        return eqRule.normalizeAttributeValue(attributeValue);
      }
    }
    catch (DecodeException e)
    {
      // nothing to do here
    }
    return attributeValue;
  }

  /**
   * Adds all the values from the specified attribute to this
   * attribute builder if they are not already present.
   *
   * @param attribute
   *          The attribute containing the values to be added to this
   *          attribute builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean addAll(Attribute attribute)
  {
    boolean wasModified = false;
    for (ByteString v : attribute)
    {
      wasModified |= add(v);
    }
    return wasModified;
  }

  /**
   * Adds the specified attribute values to this attribute builder if
   * they are not already present.
   *
   * @param values
   *          The attribute values to be added to this attribute builder.
   * @return <code>true</code> if this attribute builder was modified.
   */
  public boolean addAll(Collection<ByteString> values)
  {
    boolean wasModified = false;
    for (ByteString v : values)
    {
      wasModified |= add(v);
    }
    return wasModified;
  }

  /**
   * Adds the specified attribute values to this attribute builder
   * if they are not already present.
   *
   * @param values
   *          The attribute values to be added to this attribute builder.
   * @return <code>true</code> if this attribute builder was modified.
   * @throws NullPointerException if any of the values is null
   */
  public boolean addAllStrings(Collection<? extends Object> values)
  {
    boolean wasModified = false;
    for (Object v : values)
    {
      wasModified |= add(v.toString());
    }
    return wasModified;
  }

  /**
   * Removes all attribute values from this attribute builder.
   */
  public void clear()
  {
    values.clear();
  }



  /**
   * Indicates whether this attribute builder contains the specified
   * value.
   *
   * @param value
   *          The value for which to make the determination.
   * @return <CODE>true</CODE> if this attribute builder has the
   *         specified value, or <CODE>false</CODE> if not.
   */
  public boolean contains(ByteString value)
  {
    return values.contains(createAttributeValue(attributeDescription, value));
  }

  /**
   * Indicates whether this attribute builder contains all the values
   * in the collection.
   *
   * @param values
   *          The set of values for which to make the determination.
   * @return <CODE>true</CODE> if this attribute builder contains
   *         all the values in the provided collection, or
   *         <CODE>false</CODE> if it does not contain at least one
   *         of them.
   */
  public boolean containsAll(Collection<?> values)
  {
    for (Object v : values)
    {
      if (!contains(ByteString.valueOfObject(v)))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns <code>true</code> if this attribute builder contains no
   * attribute values.
   *
   * @return <CODE>true</CODE> if this attribute builder contains no
   *         attribute values.
   */
  public boolean isEmpty()
  {
    return values.isEmpty();
  }



  /**
   * Returns an iterator over the attribute values in this attribute
   * builder. The attribute values are returned in the order in which
   * they were added to this attribute builder. The returned iterator
   * supports attribute value removals via its <code>remove</code>
   * method.
   *
   * @return An iterator over the attribute values in this attribute builder.
   */
  @Override
  public Iterator<ByteString> iterator()
  {
    return getUnmodifiableIterator(values);
  }



  /**
   * Removes the specified attribute value from this attribute builder
   * if it is present.
   *
   * @param value
   *          The attribute value to be removed from this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder contained
   *         the specified attribute value.
   */
  public boolean remove(ByteString value)
  {
    return values.remove(createAttributeValue(attributeDescription, value));
  }

  /**
   * Removes the specified attribute value from this attribute builder
   * if it is present.
   *
   * @param valueString
   *          The string representation of the attribute value to be
   *          removed from this attribute builder.
   * @return <code>true</code> if this attribute builder contained
   *         the specified attribute value.
   */
  public boolean remove(String valueString)
  {
    return remove(ByteString.valueOfUtf8(valueString));
  }



  /**
   * Removes all the values from the specified attribute from this
   * attribute builder if they are not already present.
   *
   * @param attribute
   *          The attribute containing the values to be removed from
   *          this attribute builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean removeAll(Attribute attribute)
  {
    boolean wasModified = false;
    for (ByteString v : attribute)
    {
      wasModified |= remove(v);
    }
    return wasModified;
  }



  /**
   * Removes the specified attribute values from this attribute
   * builder if they are present.
   *
   * @param values
   *          The attribute values to be removed from this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean removeAll(Collection<ByteString> values)
  {
    boolean wasModified = false;
    for (ByteString v : values)
    {
      wasModified |= remove(v);
    }
    return wasModified;
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute value.
   *
   * @param value
   *          The attribute value to replace all existing values.
   */
  public void replace(ByteString value)
  {
    clear();
    add(value);
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute value.
   *
   * @param valueString
   *          The string representation of the attribute value to
   *          replace all existing values.
   */
  public void replace(String valueString)
  {
    replace(ByteString.valueOfUtf8(valueString));
  }



  /**
   * Replaces all the values in this attribute value with the
   * attributes from the specified attribute.
   *
   * @param attribute
   *          The attribute containing the values to replace all
   *          existing values.
   */
  public void replaceAll(Attribute attribute)
  {
    clear();
    addAll(attribute);
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute values.
   *
   * @param values
   *          The attribute values to replace all existing values.
   */
  public void replaceAll(Collection<ByteString> values)
  {
    clear();
    addAll(values);
  }

  /**
   * Adds the specified option to this attribute builder if it is not
   * already present.
   *
   * @param option
   *          The option to be added to this attribute builder.
   * @return <code>true</code> if this attribute builder did not
   *         already contain the specified option.
   */
  public boolean setOption(String option)
  {
    AttributeDescription newAD = attributeDescription.withOption(option);
    if (attributeDescription != newAD)
    {
      attributeDescription = newAD;
      return true;
    }
    return false;
  }



  /**
   * Adds the specified options to this attribute builder if they are
   * not already present.
   *
   * @param options
   *          The options to be added to this attribute builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean setOptions(Iterable<String> options)
  {
    boolean isModified = false;

    for (String option : options)
    {
      isModified |= setOption(option);
    }

    return isModified;
  }

  /**
   * Indicates whether this attribute builder has exactly the specified set of options.
   *
   * @param attributeDescription
   *          The attribute description containing the set of options for which to make the
   *          determination
   * @return <code>true</code> if this attribute builder has exactly the specified set of options.
   */
  public boolean optionsEqual(AttributeDescription attributeDescription)
  {
    return toAttribute0().getAttributeDescription().equals(attributeDescription);
  }

  /**
   * Returns the number of attribute values in this attribute builder.
   *
   * @return The number of attribute values in this attribute builder.
   */
  public int size()
  {
    return values.size();
  }

  /** Returns an iterator on values corresponding to the provided attribute values set. */
  private static Iterator<ByteString> getUnmodifiableIterator(Set<AttributeValue> set)
  {
    final Iterator<AttributeValue> iterator = set.iterator();
    return new Iterator<ByteString>()
    {
      @Override
      public boolean hasNext()
      {
        return iterator.hasNext();
      }

      @Override
      public ByteString next()
      {
         return iterator.next().getValue();
      }

      @Override
      public void remove()
      {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Indicates if the values for this attribute have been normalized.
   * <p>
   * This method is intended for tests.
   */
  boolean isNormalized()
  {
    for (AttributeValue attrValue : values)
    {
      if (attrValue.isNormalized())
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns an attribute representing the content of this attribute builder.
   * <p>
   * For efficiency purposes this method resets the content of this
   * attribute builder so that it no longer contains any options or
   * values and its attribute type is <code>null</code>.
   *
   * @return An attribute representing the content of this attribute builder.
   * @throws IllegalStateException
   *           If this attribute builder has an undefined attribute type or name.
   */
  public Attribute toAttribute() throws IllegalStateException
  {
    Attribute attribute = toAttribute0();
    // Reset the state of this builder.
    values = new SmallSet<>();
    return attribute;
  }

  private Attribute toAttribute0()
  {
    return new RealAttribute(attributeDescription, values);
  }

  /**
   * Returns a List with a single attribute representing the content of this attribute builder.
   * <p>
   * For efficiency purposes this method resets the content of this
   * attribute builder so that it no longer contains any options or
   * values and its attribute type is <code>null</code>.
   *
   * @return A List with a single attribute representing the content of this attribute builder.
   * @throws IllegalStateException
   *           If this attribute builder has an undefined attribute type or name.
   */
  public List<Attribute> toAttributeList() throws IllegalStateException
  {
    return CollectionUtils.newArrayList(toAttribute());
  }

  @Override
  public final String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("AttributeBuilder(");
    builder.append(attributeDescription);
    builder.append(", {");
    Utils.joinAsString(builder, ", ", values);
    builder.append("})");
    return builder.toString();
  }
}
