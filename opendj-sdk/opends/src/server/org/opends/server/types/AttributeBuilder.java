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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.util.Validator;


/**
 * This class provides an interface for creating new non-virtual
 * {@link Attribute}s, or "real" attributes.
 * <p>
 * An attribute can be created incrementally using either
 * {@link #AttributeBuilder(AttributeType)} or
 * {@link #AttributeBuilder(AttributeType, String)}. The caller is
 * then free to add new options using {@link #setOption(String)} and
 * new values using {@link #add(AttributeValue)} or
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
public final class AttributeBuilder
  implements Iterable<AttributeValue>
{

  /**
   * A real attribute - options handled by sub-classes.
   */
  private static abstract class RealAttribute
    extends AbstractAttribute
  {

    /**
     * The tracer object for the debug logger.
     */
    private static final DebugTracer TRACER = getTracer();

    // The attribute type for this attribute.
    private final AttributeType attributeType;

    // The name of this attribute as provided by the end user.
    private final String name;

    // The unmodifiable set of values for this attribute.
    private final Set<AttributeValue> values;



    /**
     * Creates a new real attribute.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     */
    private RealAttribute(
        AttributeType attributeType,
        String name,
        Set<AttributeValue> values)
    {
      this.attributeType = attributeType;
      this.name = name;
      this.values = values;
    }



    /**
     * {@inheritDoc}
     */
    public final ConditionResult approximatelyEqualTo(
        AttributeValue value)
    {
      ApproximateMatchingRule matchingRule = attributeType
          .getApproximateMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      ByteString normalizedValue;
      try
      {
        normalizedValue =
          matchingRule.normalizeValue(value.getValue());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // We couldn't normalize the provided value. We should return
        // "undefined".
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue v : values)
      {
        try
        {
          ByteString nv = matchingRule.normalizeValue(v.getValue());
          if (matchingRule.approximatelyMatch(nv, normalizedValue))
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // We couldn't normalize one of the attribute values. If we
          // can't find a definite match, then we should return
          // "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /**
     * {@inheritDoc}
     */
    public final boolean contains(AttributeValue value)
    {
      return values.contains(value);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean containsAll(
        Collection<AttributeValue> values)
    {
      return this.values.containsAll(values);
    }



    /**
     * {@inheritDoc}
     */
    public final AttributeType getAttributeType()
    {
      return attributeType;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final String getName()
    {
      return name;
    }



    /**
     * {@inheritDoc}
     */
    public final ConditionResult greaterThanOrEqualTo(
        AttributeValue value)
    {
      OrderingMatchingRule matchingRule = attributeType
          .getOrderingMatchingRule();
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // We couldn't normalize the provided value. We should return
        // "undefined".
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue v : values)
      {
        try
        {
          ByteString nv = v.getNormalizedValue();
          int comparisonResult = matchingRule
              .compareValues(nv, normalizedValue);
          if (comparisonResult >= 0)
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // We couldn't normalize one of the attribute values. If we
          // can't find a definite match, then we should return
          // "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /**
     * {@inheritDoc}
     */
    public final boolean isVirtual()
    {
      return false;
    }



    /**
     * {@inheritDoc}
     */
    public final Iterator<AttributeValue> iterator()
    {
      return values.iterator();
    }



    /**
     * {@inheritDoc}
     */
    public final ConditionResult lessThanOrEqualTo(
        AttributeValue value)
    {
      OrderingMatchingRule matchingRule = attributeType
          .getOrderingMatchingRule();
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // We couldn't normalize the provided value. We should return
        // "undefined".
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (AttributeValue v : values)
      {
        try
        {
          ByteString nv = v.getNormalizedValue();
          int comparisonResult = matchingRule
              .compareValues(nv, normalizedValue);
          if (comparisonResult <= 0)
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // We couldn't normalize one of the attribute values. If we
          // can't find a definite match, then we should return
          // "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /**
     * {@inheritDoc}
     */
    public final ConditionResult matchesSubstring(
        ByteString subInitial,
        List<ByteString> subAny, ByteString subFinal)
    {
      SubstringMatchingRule matchingRule = attributeType
          .getSubstringMatchingRule();
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // The substring couldn't be normalized. We have to return
          // "undefined".
          return ConditionResult.UNDEFINED;
        }
      }

      ArrayList<ByteSequence> normalizedSubAny;
      if (subAny == null)
      {
        normalizedSubAny = null;
      }
      else
      {
        normalizedSubAny = new ArrayList<ByteSequence>(subAny.size());
        for (ByteString subAnyElement : subAny)
        {
          try
          {
            normalizedSubAny
                .add(matchingRule.normalizeSubstring(subAnyElement));
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // The substring couldn't be normalized. We have to return
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // The substring couldn't be normalized. We have to return
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // The value couldn't be normalized. If we can't find a
          // definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /**
     * {@inheritDoc}
     */
    public final int size()
    {
      return values.size();
    }



    /**
     * {@inheritDoc}
     */
    public final void toString(StringBuilder buffer)
    {
      buffer.append("Attribute(");
      buffer.append(getNameWithOptions());
      buffer.append(", {");

      boolean firstValue = true;
      for (AttributeValue value : values)
      {
        if (!firstValue)
        {
          buffer.append(", ");
        }

        value.toString(buffer);
        firstValue = false;
      }

      buffer.append("})");
    }

  }



  /**
   * A real attribute with a many options.
   */
  private static final class RealAttributeManyOptions
    extends RealAttribute
  {

    // The normalized options.
    private final SortedSet<String> normalizedOptions;

    // The options.
    private final Set<String> options;



    /**
     * Creates a new real attribute that has multiple options.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     * @param options
     *          The attribute options.
     * @param normalizedOptions
     *          The normalized attribute options.
     */
    private RealAttributeManyOptions(
        AttributeType attributeType,
        String name,
        Set<AttributeValue> values,
        Set<String> options,
        SortedSet<String> normalizedOptions)
    {
      super(attributeType, name, values);
      this.options = options;
      this.normalizedOptions = normalizedOptions;
    }



    /**
     * {@inheritDoc}
     */
    public Set<String> getOptions()
    {
      return options;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOption(String option)
    {
      String s = toLowerCase(option);
      return normalizedOptions.contains(s);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOptions()
    {
      return true;
    }

  }



  /**
   * A real attribute with no options.
   */
  private static final class RealAttributeNoOptions
    extends RealAttribute
  {

    /**
     * Creates a new real attribute that has no options.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     */
    private RealAttributeNoOptions(
        AttributeType attributeType,
        String name,
        Set<AttributeValue> values)
    {
      super(attributeType, name, values);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getNameWithOptions()
    {
      return getName();
    }



    /**
     * {@inheritDoc}
     */
    public Set<String> getOptions()
    {
      return Collections.emptySet();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAllOptions(Collection<String> options)
    {
      return (options == null || options.isEmpty());
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOption(String option)
    {
      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOptions()
    {
      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean optionsEqual(Set<String> options)
    {
      return (options == null || options.isEmpty());
    }

  }



  /**
   * A real attribute with a single option.
   */
  private static final class RealAttributeSingleOption
    extends RealAttribute
  {

    // The normalized single option.
    private final String normalizedOption;

    // A singleton set containing the single option.
    private final Set<String> option;



    /**
     * Creates a new real attribute that has a single option.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     * @param option
     *          The attribute option.
     */
    private RealAttributeSingleOption(
        AttributeType attributeType,
        String name,
        Set<AttributeValue> values,
        String option)
    {
      super(attributeType, name, values);
      this.option = Collections.singleton(option);
      this.normalizedOption = toLowerCase(option);
    }



    /**
     * {@inheritDoc}
     */
    public Set<String> getOptions()
    {
      return option;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOption(String option)
    {
      String s = toLowerCase(option);
      return normalizedOption.equals(s);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOptions()
    {
      return true;
    }

  }



  /**
   * A small set of values. This set implementation is optimized to
   * use as little memory as possible in the case where there zero or
   * one elements. In addition, any normalization of elements is
   * delayed until the second element is added (normalization may be
   * triggered by invoking {@link Object#hashCode()} or
   * {@link Object#equals(Object)}.
   *
   * @param <T>
   *          The type of elements to be contained in this small set.
   */
  private static final class SmallSet<T>
    extends AbstractSet<T>
    implements Set<T>
  {

    // The set of elements if there are more than one.
    private LinkedHashSet<T> elements = null;

    // The first element.
    private T firstElement = null;



    /**
     * Creates a new small set which is initially empty.
     */
    public SmallSet()
    {
      // No implementation required.
    }



    /**
     * Creates a new small set using the content of the provided
     * collection.
     *
     * @param c
     *          The collection whose elements are to be placed into
     *          this set.
     */
    public SmallSet(Collection<T> c)
    {
      addAll(c);
    }



    /**
     * Creates a new small set with the specified initial capacity.
     *
     * @param initialCapacity
     *          The initial capacity for this set.
     */
    public SmallSet(int initialCapacity)
    {
      setInitialCapacity(initialCapacity);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(T e)
    {
      // Special handling for the first value. This avoids potentially
      // expensive normalization.
      if (firstElement == null && elements == null)
      {
        firstElement = e;
        return true;
      }

      // Create the value set if necessary.
      if (elements == null)
      {
        if (firstElement.equals(e))
        {
          return false;
        }

        elements = new LinkedHashSet<T>(2);

        // Move the first value into the set.
        elements.add(firstElement);
        firstElement = null;
      }

      return elements.add(e);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addAll(Collection<? extends T> c)
    {
      if (elements != null)
      {
        return elements.addAll(c);
      }

      if (firstElement != null)
      {
        elements = new LinkedHashSet<T>(1 + c.size());
        elements.add(firstElement);
        firstElement = null;
        return elements.addAll(c);
      }

      // Initially empty.
      switch (c.size())
      {
      case 0:
        // Do nothing.
        return false;
      case 1:
        firstElement = c.iterator().next();
        return true;
      default:
        elements = new LinkedHashSet<T>(c);
        return true;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
      firstElement = null;
      elements = null;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<T> iterator()
    {
      if (elements != null)
      {
        return elements.iterator();
      }
      else if (firstElement != null)
      {
        return new Iterator<T>()
        {

          private boolean hasNext = true;



          public boolean hasNext()
          {
            return hasNext;
          }



          public T next()
          {
            if (!hasNext)
            {
              throw new NoSuchElementException();
            }

            hasNext = false;
            return firstElement;
          }



          public void remove()
          {
            if (hasNext || firstElement == null)
            {
              throw new IllegalStateException();
            }

            firstElement = null;
          }

        };
      }
      else
      {
        return Collections.<T> emptySet().iterator();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(Object o)
    {
      if (elements != null)
      {
        // Note: if there is one or zero values left we could stop
        // using the set. However, lets assume that if the set
        // was multi-valued before then it may become multi-valued
        // again.
        return elements.remove(o);
      }

      if (firstElement != null && firstElement.equals(o))
      {
        firstElement = null;
        return true;
      }

      return false;
    }



    /**
     * Sets the initial capacity of this small set. If this small set
     * already contains elements or if its capacity has already been
     * defined then an {@link IllegalStateException} is thrown.
     *
     * @param initialCapacity
     *          The initial capacity of this small set.
     * @throws IllegalStateException
     *           If this small set already contains elements or if its
     *           capacity has already been defined.
     */
    public void setInitialCapacity(int initialCapacity)
        throws IllegalStateException
    {
      Validator.ensureTrue(initialCapacity >= 0);

      if (elements != null)
      {
        throw new IllegalStateException();
      }

      if (initialCapacity > 1)
      {
        elements = new LinkedHashSet<T>(initialCapacity);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
      if (elements != null)
      {
        return elements.size();
      }
      else if (firstElement != null)
      {
        return 1;
      }
      else
      {
        return 0;
      }
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
      Set<AttributeValue> values)
  {
    return new RealAttributeNoOptions(attributeType, name, values);
  }



  /**
   * Gets the named attribute type, creating a default attribute if
   * necessary.
   *
   * @param attributeName
   *          The name of the attribute type.
   * @return The attribute type associated with the provided attribute
   *         name.
   */
  private static AttributeType getAttributeType(String attributeName)
  {
    String lc = toLowerCase(attributeName);
    AttributeType type = DirectoryServer.getAttributeType(lc);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(attributeName);
    }
    return type;
  }

  // The attribute type for this attribute.
  private AttributeType attributeType = null;

  // The name of this attribute as provided by the end user.
  private String name = null;

  // The normalized set of options if there are more than one.
  private SortedSet<String> normalizedOptions = null;

  // The set of options.
  private final SmallSet<String> options = new SmallSet<String>();

  // The set of values for this attribute.
  private final SmallSet<AttributeValue> values =
    new SmallSet<AttributeValue>();



  /**
   * Creates a new attribute builder with an undefined attribute type
   * and user-provided name. The attribute type, and optionally the
   * user-provided name, must be defined using
   * {@link #setAttributeType(AttributeType)} before the attribute
   * builder can be converted to an {@link Attribute}. Failure to do
   * so will yield an {@link IllegalStateException}.
   */
  public AttributeBuilder()
  {
    // No implementation required.
  }



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
    this(attribute, false);
  }



  /**
   * Creates a new attribute builder from an existing attribute,
   * optionally omitting the values contained in the provided
   * attribute.
   * <p>
   * Modifications to the attribute builder will not impact the
   * provided attribute.
   *
   * @param attribute
   *          The attribute to be copied.
   * @param omitValues
   *          <CODE>true</CODE> if the values should be omitted.
   */
  public AttributeBuilder(Attribute attribute, boolean omitValues)
  {
    this(attribute.getAttributeType(), attribute.getName());

    for (String option : attribute.getOptions())
    {
      setOption(option);
    }

    if (!omitValues)
    {
      addAll(attribute);
    }
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
    this(attributeType, attributeType.getNameOrOID());
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
    Validator.ensureNotNull(attributeType, name);

    this.attributeType = attributeType;
    this.name = name;
  }



  /**
   * Creates a new attribute builder with the specified attribute name
   * and no options and no values.
   * <p>
   * If the attribute name cannot be found in the schema, a new
   * attribute type is created using the default attribute syntax.
   *
   * @param attributeName
   *          The attribute name for this attribute builder.
   */
  public AttributeBuilder(String attributeName)
  {
    this(getAttributeType(attributeName), attributeName);
  }



  /**
   * Adds the specified attribute value to this attribute builder if
   * it is not already present.
   *
   * @param value
   *          The attribute value to be added to this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder did not
   *         already contain the specified attribute value.
   */
  public boolean add(AttributeValue value)
  {
    return values.add(value);
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
    AttributeValue value =
        AttributeValues.create(attributeType, valueString);
    return add(value);
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
    for (AttributeValue v : attribute)
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
   *          The attribute values to be added to this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean addAll(Collection<AttributeValue> values)
  {
    return this.values.addAll(values);
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
  public boolean contains(AttributeValue value)
  {
    return values.contains(value);
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
  public boolean containsAll(Collection<AttributeValue> values)
  {
    return this.values.containsAll(values);
  }



  /**
   * Retrieves the attribute type for this attribute builder.
   *
   * @return The attribute type for this attribute builder, or
   *         <code>null</code> if one has not yet been specified.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
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
   * @return An iterator over the attribute values in this attribute
   *         builder.
   */
  public Iterator<AttributeValue> iterator()
  {
    return values.iterator();
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
  public boolean remove(AttributeValue value)
  {
    return values.remove(value);
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
    AttributeValue value =
        AttributeValues.create(attributeType, valueString);
    return remove(value);
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
    for (AttributeValue v : attribute)
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
  public boolean removeAll(Collection<AttributeValue> values)
  {
    return this.values.removeAll(values);
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute value.
   *
   * @param value
   *          The attribute value to replace all existing values.
   */
  public void replace(AttributeValue value)
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
    AttributeValue value =
        AttributeValues.create(attributeType, valueString);
    replace(value);
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
  public void replaceAll(Collection<AttributeValue> values)
  {
    clear();
    addAll(values);
  }



  /**
   * Sets the attribute type associated with this attribute builder.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   */
  public void setAttributeType(AttributeType attributeType)
  {
    setAttributeType(attributeType, attributeType.getNameOrOID());
  }



  /**
   * Sets the attribute type and user-provided name associated with
   * this attribute builder.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   * @param name
   *          The user-provided name for this attribute builder.
   */
  public void setAttributeType(
      AttributeType attributeType,
      String name)
  {
    Validator.ensureNotNull(attributeType, name);

    this.attributeType = attributeType;
    this.name = name;
  }



  /**
   * Sets the attribute type associated with this attribute builder
   * using the provided attribute type name.
   * <p>
   * If the attribute name cannot be found in the schema, a new
   * attribute type is created using the default attribute syntax.
   *
   * @param attributeName
   *          The attribute name for this attribute builder.
   */
  public void setAttributeType(String attributeName)
  {
    setAttributeType(getAttributeType(attributeName), attributeName);
  }



  /**
   * Sets the initial capacity of this attribute builders internal set
   * of attribute values.
   * <p>
   * The initial capacity of an attribute builder defaults to one.
   * Applications should override this default if the attribute being
   * built is expected to contain many values.
   * <p>
   * This method should only be called before any attribute values
   * have been added to this attribute builder. If it is called
   * afterwards an {@link IllegalStateException} will be thrown.
   *
   * @param initialCapacity
   *          The initial capacity of this attribute builder.
   * @return This attribute builder.
   * @throws IllegalStateException
   *           If this attribute builder already contains attribute
   *           values.
   */
  public AttributeBuilder setInitialCapacity(int initialCapacity)
      throws IllegalStateException
  {
    values.setInitialCapacity(initialCapacity);
    return this;
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
    switch (options.size())
    {
    case 0:
      return options.add(option);
    case 1:
      // Normalize and add the first option to normalized set.
      normalizedOptions = new TreeSet<String>();
      normalizedOptions.add(toLowerCase(options.firstElement));

      if (normalizedOptions.add(toLowerCase(option)))
      {
        options.add(option);
        return true;
      }
      break;
    default:
      if (normalizedOptions.add(toLowerCase(option)))
      {
        options.add(option);
        return true;
      }
      break;
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
  public boolean setOptions(Collection<String> options)
  {
    boolean isModified = false;

    for (String option : options)
    {
      isModified |= setOption(option);
    }

    return isModified;
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



  /**
   * Returns an attribute representing the content of this attribute
   * builder.
   * <p>
   * For efficiency purposes this method resets the content of this
   * attribute builder so that it no longer contains any options or
   * values and its attribute type is <code>null</code>.
   *
   * @return An attribute representing the content of this attribute
   *         builder.
   * @throws IllegalStateException
   *           If this attribute builder has an undefined attribute
   *           type or name.
   */
  public Attribute toAttribute() throws IllegalStateException
  {
    if (attributeType == null)
    {
      throw new IllegalStateException(
          "Undefined attribute type or name");
    }

    // First determine the minimum representation required for the set
    // of values.
    Set<AttributeValue> newValues;
    if (values.elements != null)
    {
      newValues = Collections.unmodifiableSet(values.elements);
    }
    else if (values.firstElement != null)
    {
      newValues = Collections.singleton(values.firstElement);
    }
    else
    {
      newValues = Collections.emptySet();
    }

    // Now create the appropriate attribute based on the options.
    Attribute attribute;
    switch (options.size())
    {
    case 0:
      attribute =
        new RealAttributeNoOptions(attributeType, name, newValues);
      break;
    case 1:
      attribute =
        new RealAttributeSingleOption(attributeType, name, newValues,
          options.firstElement);
      break;
    default:
      attribute =
        new RealAttributeManyOptions(attributeType, name, newValues,
          Collections.unmodifiableSet(options.elements), Collections
              .unmodifiableSortedSet(normalizedOptions));
      break;
    }

    // Reset the state of this builder.
    attributeType = null;
    name = null;
    normalizedOptions = null;
    options.clear();
    values.clear();

    return attribute;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString()
  {
    StringBuilder builder = new StringBuilder();

    builder.append("AttributeBuilder(");
    builder.append(String.valueOf(name));

    for (String option : options)
    {
      builder.append(';');
      builder.append(option);
    }

    builder.append(", {");

    boolean firstValue = true;
    for (AttributeValue value : values)
    {
      if (!firstValue)
      {
        builder.append(", ");
      }

      value.toString(builder);
      firstValue = false;
    }

    builder.append("})");

    return builder.toString();
  }
}
