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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.*;

import org.opends.sdk.schema.AttributeType;
import org.opends.sdk.schema.MatchingRule;

import com.sun.opends.sdk.util.Validator;



/**
 * This class provides a skeletal implementation of the {@code
 * Attribute} interface, to minimize the effort required to implement
 * this interface.
 */
public abstract class AbstractAttribute extends AbstractSet<ByteString>
    implements Attribute
{

  /**
   * Returns {@code true} if {@code object} is an attribute which is
   * equal to {@code attribute}. Two attributes are considered equal if
   * their attribute descriptions are equal, they both have the same
   * number of attribute values, and every attribute value contained in
   * the first attribute is also contained in the second attribute.
   *
   * @param attribute
   *          The attribute to be tested for equality.
   * @param object
   *          The object to be tested for equality with the attribute.
   * @return {@code true} if {@code object} is an attribute which is
   *         equal to {@code attribute}, or {@code false} if not.
   */
  static boolean equals(Attribute attribute, Object object)
  {
    if (attribute == object)
    {
      return true;
    }

    if (!(object instanceof Attribute))
    {
      return false;
    }

    Attribute other = (Attribute) object;
    if (!attribute.getAttributeDescription().equals(
        other.getAttributeDescription()))
    {
      return false;
    }

    // Attribute description is the same, compare values.
    if (attribute.size() != other.size())
    {
      return false;
    }

    return attribute.containsAll(other);
  }



  /**
   * Returns the hash code for {@code attribute}. It will be calculated
   * as the sum of the hash codes of the attribute description and all
   * of the attribute values.
   *
   * @param attribute
   *          The attribute whose hash code should be calculated.
   * @return The hash code for {@code attribute}.
   */
  static int hashCode(Attribute attribute)
  {
    int hashCode = attribute.getAttributeDescription().hashCode();
    for (ByteString value : attribute)
    {
      hashCode += normalizeValue(attribute, value).hashCode();
    }
    return hashCode;
  }



  /**
   * Returns the normalized form of {@code value} normalized using
   * {@code attribute}'s equality matching rule.
   *
   * @param attribute
   *          The attribute whose equality matching rule should be used
   *          for normalization.
   * @param value
   *          The attribute value to be normalized.
   * @return The normalized form of {@code value} normalized using
   *         {@code attribute}'s equality matching rule.
   */
  static ByteString normalizeValue(Attribute attribute, ByteString value)
  {
    AttributeDescription attributeDescription = attribute
        .getAttributeDescription();
    AttributeType attributeType = attributeDescription
        .getAttributeType();
    MatchingRule matchingRule = attributeType.getEqualityMatchingRule();

    try
    {
      return matchingRule.normalizeAttributeValue(value);
    }
    catch (Exception e)
    {
      // Fall back to provided value.
      return value;
    }
  }



  /**
   * Returns a string representation of {@code attribute}.
   *
   * @param attribute
   *          The attribute whose string representation should be
   *          returned.
   * @return The string representation of {@code attribute}.
   */
  static String toString(Attribute attribute)
  {
    StringBuilder builder = new StringBuilder();
    builder.append("Attribute(");
    builder.append(attribute.getAttributeDescriptionAsString());
    builder.append(", {");

    boolean firstValue = true;
    for (ByteString value : attribute)
    {
      if (!firstValue)
      {
        builder.append(", ");
      }

      builder.append(value);
      firstValue = false;
    }

    builder.append("})");
    return builder.toString();
  }



  /**
   * Sole constructor.
   */
  protected AbstractAttribute()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public abstract boolean add(ByteString value)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  public boolean add(Object firstValue, Object... remainingValues)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(firstValue);

    boolean modified = add(ByteString.valueOf(firstValue));
    if (remainingValues != null)
    {
      for (Object value : remainingValues)
      {
        modified |= add(ByteString.valueOf(value));
      }
    }
    return modified;
  }



  /**
   * {@inheritDoc}
   */
  public boolean addAll(Collection<? extends ByteString> values)
      throws UnsupportedOperationException, NullPointerException
  {
    return addAll(values, null);
  }



  /**
   * {@inheritDoc}
   */
  public boolean addAll(Collection<? extends ByteString> values,
      Collection<? super ByteString> duplicateValues)
      throws UnsupportedOperationException, NullPointerException
  {
    boolean modified = false;
    for (ByteString value : values)
    {
      if (add(value))
      {
        modified = true;
      }
      else if (duplicateValues != null)
      {
        duplicateValues.add(value);
      }
    }
    return modified;
  }



  /**
   * {@inheritDoc}
   */
  public abstract boolean contains(Object value)
      throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  public boolean containsAll(Collection<?> values)
      throws NullPointerException
  {
    for (Object value : values)
    {
      if (!contains(value))
      {
        return false;
      }
    }
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(Object object)
  {
    return equals(this, object);
  }



  /**
   * {@inheritDoc}
   */
  public ByteString firstValue() throws NoSuchElementException
  {
    return iterator().next();
  }



  /**
   * {@inheritDoc}
   */
  public String firstValueAsString() throws NoSuchElementException
  {
    return firstValue().toString();
  }



  /**
   * {@inheritDoc}
   */
  public abstract AttributeDescription getAttributeDescription();



  /**
   * {@inheritDoc}
   */
  public String getAttributeDescriptionAsString()
  {
    return getAttributeDescription().toString();
  }



  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode(this);
  }



  /**
   * {@inheritDoc}
   */
  public abstract Iterator<ByteString> iterator();



  /**
   * {@inheritDoc}
   */
  public abstract boolean remove(Object value)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  public boolean removeAll(Collection<?> values)
      throws UnsupportedOperationException, NullPointerException
  {
    return removeAll(values, null);
  }



  /**
   * {@inheritDoc}
   */
  public <T> boolean removeAll(Collection<T> values,
      Collection<? super T> missingValues)
      throws UnsupportedOperationException, NullPointerException
  {
    boolean modified = false;
    for (T value : values)
    {
      if (remove(value))
      {
        modified = true;
      }
      else if (missingValues != null)
      {
        missingValues.add(value);
      }
    }
    return modified;
  }



  /**
   * {@inheritDoc}
   */
  public boolean retainAll(Collection<?> values)
      throws UnsupportedOperationException, NullPointerException
  {
    return retainAll(values, null);
  }



  /**
   * {@inheritDoc}
   */
  public <T> boolean retainAll(Collection<T> values,
      Collection<? super T> missingValues)
      throws UnsupportedOperationException, NullPointerException
  {
    if (values.isEmpty())
    {
      if (isEmpty())
      {
        return false;
      }
      else
      {
        clear();
        return true;
      }
    }

    if (isEmpty())
    {
      if (missingValues != null)
      {
        for (T value : values)
        {
          missingValues.add(value);
        }
      }
      return false;
    }

    Map<ByteString, T> valuesToRetain = new HashMap<ByteString, T>(
        values.size());
    for (T value : values)
    {
      valuesToRetain.put(
          normalizeValue(this, ByteString.valueOf(value)), value);
    }

    boolean modified = false;
    Iterator<ByteString> iterator = iterator();
    while (iterator.hasNext())
    {
      ByteString value = iterator.next();
      ByteString normalizedValue = normalizeValue(this, value);
      if (valuesToRetain.remove(normalizedValue) == null)
      {
        modified = true;
        iterator.remove();
      }
    }

    if (missingValues != null)
    {
      missingValues.addAll(valuesToRetain.values());
    }

    return modified;
  }



  /**
   * {@inheritDoc}
   */
  public abstract int size();



  /**
   * {@inheritDoc}
   */
  public ByteString[] toArray()
  {
    return toArray(new ByteString[size()]);
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return toString(this);
  }

}
