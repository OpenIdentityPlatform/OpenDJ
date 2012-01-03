/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;



import java.util.Collection;
import java.util.Iterator;

import org.forgerock.opendj.ldap.schema.AttributeType;

import com.forgerock.opendj.util.Iterators;
import com.forgerock.opendj.util.Validator;



/**
 * This class contains methods for creating and manipulating attributes.
 */
public final class Attributes
{

  /**
   * Empty attribute.
   */
  private static final class EmptyAttribute extends AbstractAttribute
  {

    private final AttributeDescription attributeDescription;



    private EmptyAttribute(final AttributeDescription attributeDescription)
    {
      this.attributeDescription = attributeDescription;
    }



    @Override
    public boolean add(final ByteString value)
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public void clear()
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public boolean contains(final Object value)
    {
      return false;
    }



    @Override
    public AttributeDescription getAttributeDescription()
    {
      return attributeDescription;
    }



    @Override
    public boolean isEmpty()
    {
      return true;
    }



    @Override
    public Iterator<ByteString> iterator()
    {
      return Iterators.emptyIterator();
    }



    @Override
    public boolean remove(final Object value)
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public int size()
    {
      return 0;
    }

  }



  /**
   * Renamed attribute.
   */
  private static final class RenamedAttribute implements Attribute
  {

    private final Attribute attribute;

    private final AttributeDescription attributeDescription;



    private RenamedAttribute(final Attribute attribute,
        final AttributeDescription attributeDescription)
    {
      this.attribute = attribute;
      this.attributeDescription = attributeDescription;
    }



    public boolean add(final ByteString value)
    {
      return attribute.add(value);
    }



    public boolean add(final Object firstValue, final Object... remainingValues)
    {
      return attribute.add(firstValue, remainingValues);
    }



    public boolean addAll(final Collection<? extends ByteString> values)
    {
      return attribute.addAll(values);
    }



    public boolean addAll(final Collection<? extends ByteString> values,
        final Collection<? super ByteString> duplicateValues)
    {
      return attribute.addAll(values, duplicateValues);
    }



    public void clear()
    {
      attribute.clear();
    }



    public boolean contains(final Object value)
    {
      return attribute.contains(value);
    }



    public boolean containsAll(final Collection<?> values)
    {
      return attribute.containsAll(values);
    }



    @Override
    public boolean equals(final Object object)
    {
      return AbstractAttribute.equals(this, object);
    }



    public ByteString firstValue()
    {
      return attribute.firstValue();
    }



    public String firstValueAsString()
    {
      return attribute.firstValueAsString();
    }



    public AttributeDescription getAttributeDescription()
    {
      return attributeDescription;
    }



    public String getAttributeDescriptionAsString()
    {
      return attributeDescription.toString();
    }



    @Override
    public int hashCode()
    {
      return AbstractAttribute.hashCode(this);
    }



    public boolean isEmpty()
    {
      return attribute.isEmpty();
    }



    public Iterator<ByteString> iterator()
    {
      return attribute.iterator();
    }



    public boolean remove(final Object value)
    {
      return attribute.remove(value);
    }



    public boolean removeAll(final Collection<?> values)
    {
      return attribute.removeAll(values);
    }



    public <T> boolean removeAll(final Collection<T> values,
        final Collection<? super T> missingValues)
    {
      return attribute.removeAll(values, missingValues);
    }



    public boolean retainAll(final Collection<?> values)
    {
      return attribute.retainAll(values);
    }



    public <T> boolean retainAll(final Collection<T> values,
        final Collection<? super T> missingValues)
    {
      return attribute.retainAll(values, missingValues);
    }



    public int size()
    {
      return attribute.size();
    }



    public ByteString[] toArray()
    {
      return attribute.toArray();
    }



    public <T> T[] toArray(final T[] array)
    {
      return attribute.toArray(array);
    }



    @Override
    public String toString()
    {
      return AbstractAttribute.toString(this);
    }

  }



  /**
   * Unmodifiable attribute.
   */
  private static final class UnmodifiableAttribute implements Attribute
  {

    private final Attribute attribute;



    private UnmodifiableAttribute(final Attribute attribute)
    {
      this.attribute = attribute;
    }



    public boolean add(final ByteString value)
    {
      throw new UnsupportedOperationException();
    }



    public boolean add(final Object firstValue, final Object... remainingValues)
    {
      throw new UnsupportedOperationException();
    }



    public boolean addAll(final Collection<? extends ByteString> values)
    {
      throw new UnsupportedOperationException();
    }



    public boolean addAll(final Collection<? extends ByteString> values,
        final Collection<? super ByteString> duplicateValues)
    {
      throw new UnsupportedOperationException();
    }



    public void clear()
    {
      throw new UnsupportedOperationException();
    }



    public boolean contains(final Object value)
    {
      return attribute.contains(value);
    }



    public boolean containsAll(final Collection<?> values)
    {
      return attribute.containsAll(values);
    }



    @Override
    public boolean equals(final Object object)
    {
      return (object == this || attribute.equals(object));
    }



    public ByteString firstValue()
    {
      return attribute.firstValue();
    }



    public String firstValueAsString()
    {
      return attribute.firstValueAsString();
    }



    public AttributeDescription getAttributeDescription()
    {
      return attribute.getAttributeDescription();
    }



    public String getAttributeDescriptionAsString()
    {
      return attribute.getAttributeDescriptionAsString();
    }



    @Override
    public int hashCode()
    {
      return attribute.hashCode();
    }



    public boolean isEmpty()
    {
      return attribute.isEmpty();
    }



    public Iterator<ByteString> iterator()
    {
      return Iterators.unmodifiableIterator(attribute.iterator());
    }



    public boolean remove(final Object value)
    {
      throw new UnsupportedOperationException();
    }



    public boolean removeAll(final Collection<?> values)
    {
      throw new UnsupportedOperationException();
    }



    public <T> boolean removeAll(final Collection<T> values,
        final Collection<? super T> missingValues)
    {
      throw new UnsupportedOperationException();
    }



    public boolean retainAll(final Collection<?> values)
    {
      throw new UnsupportedOperationException();
    }



    public <T> boolean retainAll(final Collection<T> values,
        final Collection<? super T> missingValues)
    {
      throw new UnsupportedOperationException();
    }



    public int size()
    {
      return attribute.size();
    }



    public ByteString[] toArray()
    {
      return attribute.toArray();
    }



    public <T> T[] toArray(final T[] array)
    {
      return attribute.toArray(array);
    }



    @Override
    public String toString()
    {
      return attribute.toString();
    }

  }



  /**
   * Returns a read-only empty attribute having the specified attribute
   * description.
   *
   * @param attributeDescription
   *          The attribute description.
   * @return The empty attribute.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  public static final Attribute emptyAttribute(
      final AttributeDescription attributeDescription)
  {
    return new EmptyAttribute(attributeDescription);
  }



  /**
   * Returns a view of {@code attribute} having a different attribute
   * description. All operations on the returned attribute "pass-through" to the
   * underlying attribute.
   *
   * @param attribute
   *          The attribute to be renamed.
   * @param attributeDescription
   *          The new attribute description for {@code attribute}, which must be
   *          compatible with {@code attribute}'s attribute description.
   * @return A renamed view of {@code attribute}.
   * @throws IllegalArgumentException
   *           If {@code attributeDescription} does not have the same attribute
   *           type as {@code attribute}'s attribute description.
   * @throws NullPointerException
   *           If {@code attribute} or {@code attributeDescription} was {@code
   *           null}.
   */
  public static final Attribute renameAttribute(final Attribute attribute,
      final AttributeDescription attributeDescription)
  {
    final AttributeType oldType = attribute.getAttributeDescription()
        .getAttributeType();
    final AttributeType newType = attributeDescription.getAttributeType();

    // We could relax a bit by ensuring that they are both compatible
    // (e.g. one sub-type of another, or same equality matching rule,
    // etc).
    Validator.ensureTrue(oldType.equals(newType),
        "Old and new attribute type are not the same");

    return new RenamedAttribute(attribute, attributeDescription);
  }



  /**
   * Returns a read-only view of {@code attribute}. Query operations on the
   * returned attribute "read-through" to the underlying attribute, and attempts
   * to modify the returned attribute either directly or indirectly via an
   * iterator result in an {@code UnsupportedOperationException}.
   *
   * @param attribute
   *          The attribute for which a read-only view is to be returned.
   * @return A read-only view of {@code attribute}.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  public static final Attribute unmodifiableAttribute(final Attribute attribute)
  {
    if (attribute instanceof UnmodifiableAttribute)
    {
      return attribute;
    }
    else
    {
      return new UnmodifiableAttribute(attribute);
    }
  }



  // Prevent instantiation.
  private Attributes()
  {
    // Nothing to do.
  }
}
