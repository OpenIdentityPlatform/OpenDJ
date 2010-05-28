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



import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.opends.sdk.schema.AttributeType;

import com.sun.opends.sdk.util.Function;
import com.sun.opends.sdk.util.Iterables;
import com.sun.opends.sdk.util.Iterators;
import com.sun.opends.sdk.util.Validator;



/**
 * This class contains methods for creating and manipulating attributes,
 * entries, and other types of object.
 */
public final class Types
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
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public void clear() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public boolean contains(final Object value) throws NullPointerException
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
      return Iterators.empty();
    }



    @Override
    public boolean remove(final Object value)
        throws UnsupportedOperationException, NullPointerException
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
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.add(value);
    }



    public boolean add(final Object firstValue, final Object... remainingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.add(firstValue, remainingValues);
    }



    public boolean addAll(final Collection<? extends ByteString> values)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.addAll(values);
    }



    public boolean addAll(final Collection<? extends ByteString> values,
        final Collection<? super ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.addAll(values, duplicateValues);
    }



    public void clear() throws UnsupportedOperationException
    {
      attribute.clear();
    }



    public boolean contains(final Object value) throws NullPointerException
    {
      return attribute.contains(value);
    }



    public boolean containsAll(final Collection<?> values)
        throws NullPointerException
    {
      return attribute.containsAll(values);
    }



    @Override
    public boolean equals(final Object object)
    {
      return AbstractAttribute.equals(this, object);
    }



    public ByteString firstValue() throws NoSuchElementException
    {
      return attribute.firstValue();
    }



    public String firstValueAsString() throws NoSuchElementException
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
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.remove(value);
    }



    public boolean removeAll(final Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.removeAll(values);
    }



    public <T> boolean removeAll(final Collection<T> values,
        final Collection<? super T> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.removeAll(values, missingValues);
    }



    public boolean retainAll(final Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.retainAll(values);
    }



    public <T> boolean retainAll(final Collection<T> values,
        final Collection<? super T> missingValues)
        throws UnsupportedOperationException, NullPointerException
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



    public <T> T[] toArray(final T[] array) throws ArrayStoreException,
        NullPointerException
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
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean add(final Object firstValue, final Object... remainingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean addAll(final Collection<? extends ByteString> values)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean addAll(final Collection<? extends ByteString> values,
        final Collection<? super ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public void clear() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    public boolean contains(final Object value) throws NullPointerException
    {
      return attribute.contains(value);
    }



    public boolean containsAll(final Collection<?> values)
        throws NullPointerException
    {
      return attribute.containsAll(values);
    }



    @Override
    public boolean equals(final Object object)
    {
      return (object == this || attribute.equals(object));
    }



    public ByteString firstValue() throws NoSuchElementException
    {
      return attribute.firstValue();
    }



    public String firstValueAsString() throws NoSuchElementException
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
      return Iterators.unmodifiable(attribute.iterator());
    }



    public boolean remove(final Object value)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean removeAll(final Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public <T> boolean removeAll(final Collection<T> values,
        final Collection<? super T> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean retainAll(final Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public <T> boolean retainAll(final Collection<T> values,
        final Collection<? super T> missingValues)
        throws UnsupportedOperationException, NullPointerException
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



    public <T> T[] toArray(final T[] array) throws ArrayStoreException,
        NullPointerException
    {
      return attribute.toArray(array);
    }



    @Override
    public String toString()
    {
      return attribute.toString();
    }

  }



  private static final class UnmodifiableEntry implements Entry
  {
    private final Entry entry;



    private UnmodifiableEntry(final Entry entry)
    {
      this.entry = entry;
    }



    /**
     * {@inheritDoc}
     */
    public boolean addAttribute(final Attribute attribute)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public boolean addAttribute(final Attribute attribute,
        final Collection<ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry addAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public Entry clearAttributes() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    public boolean containsAttribute(final Attribute attribute,
        final Collection<ByteString> missingValues) throws NullPointerException
    {
      return entry.containsAttribute(attribute, missingValues);
    }



    public boolean containsAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        NullPointerException
    {
      return entry.containsAttribute(attributeDescription, values);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object object)
    {
      return (object == this || entry.equals(object));
    }



    public Iterable<Attribute> getAllAttributes()
    {
      return Iterables.unmodifiable(Iterables.transform(entry
          .getAllAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    public Iterable<Attribute> getAllAttributes(
        final AttributeDescription attributeDescription)
    {
      return Iterables.unmodifiable(Iterables.transform(entry
          .getAllAttributes(attributeDescription),
          UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    /**
     * {@inheritDoc}
     */
    public Iterable<Attribute> getAllAttributes(
        final String attributeDescription)
        throws LocalizedIllegalArgumentException, NullPointerException
    {
      return Iterables.unmodifiable(Iterables.transform(entry
          .getAllAttributes(attributeDescription),
          UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    public Attribute getAttribute(
        final AttributeDescription attributeDescription)
    {
      final Attribute attribute = entry.getAttribute(attributeDescription);
      if (attribute != null)
      {
        return unmodifiableAttribute(attribute);
      }
      else
      {
        return null;
      }
    }



    /**
     * {@inheritDoc}
     */
    public Attribute getAttribute(final String attributeDescription)
        throws LocalizedIllegalArgumentException, NullPointerException
    {
      final Attribute attribute = entry.getAttribute(attributeDescription);
      if (attribute != null)
      {
        return unmodifiableAttribute(attribute);
      }
      else
      {
        return null;
      }
    }



    public int getAttributeCount()
    {
      return entry.getAttributeCount();
    }



    /**
     * {@inheritDoc}
     */
    public DN getName()
    {
      return entry.getName();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
      return entry.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    public boolean removeAttribute(final Attribute attribute,
        final Collection<ByteString> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean removeAttribute(
        final AttributeDescription attributeDescription)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry removeAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public boolean replaceAttribute(final Attribute attribute)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry replaceAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public Entry setName(final DN dn) throws UnsupportedOperationException,
        NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry setName(final String dn)
        throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return entry.toString();
    }

  }



  private static final Function<Attribute, Attribute, Void>
    UNMODIFIABLE_ATTRIBUTE_FUNCTION = new Function<Attribute, Attribute, Void>()
  {

    public Attribute apply(final Attribute value, final Void p)
    {
      return unmodifiableAttribute(value);
    }

  };



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
      throws NullPointerException
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
      throws IllegalArgumentException, NullPointerException
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
      throws NullPointerException
  {
    return new UnmodifiableAttribute(attribute);
  }



  /**
   * Returns a read-only view of {@code entry} and its attributes. Query
   * operations on the returned entry and its attributes"read-through" to the
   * underlying entry or attribute, and attempts to modify the returned entry
   * and its attributes either directly or indirectly via an iterator result in
   * an {@code UnsupportedOperationException}.
   *
   * @param entry
   *          The entry for which a read-only view is to be returned.
   * @return A read-only view of {@code entry}.
   * @throws NullPointerException
   *           If {@code entry} was {@code null}.
   */
  public static final Entry unmodifiableEntry(final Entry entry)
      throws NullPointerException
  {
    return new UnmodifiableEntry(entry);
  }



  // Prevent instantiation.
  private Types()
  {
    // Nothing to do.
  }
}
