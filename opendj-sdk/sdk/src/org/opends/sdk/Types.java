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
import org.opends.sdk.schema.ObjectClass;

import com.sun.opends.sdk.util.*;



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



    private EmptyAttribute(AttributeDescription attributeDescription)
    {
      this.attributeDescription = attributeDescription;
    }



    public boolean add(ByteString value)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public void clear() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    public AttributeDescription getAttributeDescription()
    {
      return attributeDescription;
    }



    public boolean isEmpty()
    {
      return true;
    }



    public Iterator<ByteString> iterator()
    {
      return Iterators.empty();
    }



    public int size()
    {
      return 0;
    }



    public boolean contains(Object value) throws NullPointerException
    {
      return false;
    }



    public boolean remove(Object value)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }

  }



  /**
   * Renamed attribute.
   */
  private static final class RenamedAttribute implements Attribute
  {

    private final Attribute attribute;

    private final AttributeDescription attributeDescription;



    private RenamedAttribute(Attribute attribute,
        AttributeDescription attributeDescription)
    {
      this.attribute = attribute;
      this.attributeDescription = attributeDescription;
    }



    public boolean add(ByteString value)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.add(value);
    }



    public boolean add(Object firstValue, Object... remainingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.add(firstValue, remainingValues);
    }



    public boolean addAll(Collection<? extends ByteString> values)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.addAll(values);
    }



    public boolean addAll(Collection<? extends ByteString> values,
        Collection<? super ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.addAll(values, duplicateValues);
    }



    public void clear() throws UnsupportedOperationException
    {
      attribute.clear();
    }



    public boolean contains(Object value) throws NullPointerException
    {
      return attribute.contains(value);
    }



    public boolean containsAll(Collection<?> values)
        throws NullPointerException
    {
      return attribute.containsAll(values);
    }



    public boolean equals(Object object)
    {
      return AbstractAttribute.equals(this, object);
    }



    public ByteString firstValue() throws NoSuchElementException
    {
      return attribute.firstValue();
    }



    public <T> T firstValueAsObject(
        Function<? super ByteString, T, Void> type)
        throws NoSuchElementException
    {
      return attribute.firstValueAsObject(type);
    }



    public <T, P> T firstValueAsObject(
        Function<? super ByteString, T, P> type, P p)
        throws NoSuchElementException
    {
      return attribute.firstValueAsObject(type, p);
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



    public boolean remove(Object value)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.remove(value);
    }



    public boolean removeAll(Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.removeAll(values);
    }



    public <T> boolean removeAll(Collection<T> values,
        Collection<? super T> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.removeAll(values, missingValues);
    }



    public boolean retainAll(Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      return attribute.retainAll(values);
    }



    public <T> boolean retainAll(Collection<T> values,
        Collection<? super T> missingValues)
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



    public <T> T[] toArray(T[] array) throws ArrayStoreException,
        NullPointerException
    {
      return attribute.toArray(array);
    }



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



    private UnmodifiableAttribute(Attribute attribute)
    {
      this.attribute = attribute;
    }



    public boolean add(ByteString value)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean add(Object firstValue, Object... remainingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean addAll(Collection<? extends ByteString> values)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean addAll(Collection<? extends ByteString> values,
        Collection<? super ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public void clear() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    public boolean contains(Object value) throws NullPointerException
    {
      return attribute.contains(value);
    }



    public boolean containsAll(Collection<?> values)
        throws NullPointerException
    {
      return attribute.containsAll(values);
    }



    public boolean equals(Object object)
    {
      return (object == this || attribute.equals(object));
    }



    public ByteString firstValue() throws NoSuchElementException
    {
      return attribute.firstValue();
    }



    public <T> T firstValueAsObject(
        Function<? super ByteString, T, Void> type)
        throws NoSuchElementException
    {
      return attribute.firstValueAsObject(type);
    }



    public <T, P> T firstValueAsObject(
        Function<? super ByteString, T, P> type, P p)
        throws NoSuchElementException
    {
      return attribute.firstValueAsObject(type, p);
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



    public boolean remove(Object value)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean removeAll(Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public <T> boolean removeAll(Collection<T> values,
        Collection<? super T> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean retainAll(Collection<?> values)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public <T> boolean retainAll(Collection<T> values,
        Collection<? super T> missingValues)
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



    public <T> T[] toArray(T[] array) throws ArrayStoreException,
        NullPointerException
    {
      return attribute.toArray(array);
    }



    public String toString()
    {
      return attribute.toString();
    }

  }



  private static final class UnmodifiableEntry implements Entry
  {
    private final Entry entry;



    private UnmodifiableEntry(Entry entry)
    {
      this.entry = entry;
    }



    /**
     * {@inheritDoc}
     */
    public boolean addAttribute(Attribute attribute,
        Collection<ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public boolean addAttribute(Attribute attribute)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry addAttribute(String attributeDescription,
        Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public Entry clearAttributes() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    public boolean containsAttribute(
        AttributeDescription attributeDescription)
    {
      return entry.containsAttribute(attributeDescription);
    }



    /**
     * {@inheritDoc}
     */
    public boolean containsAttribute(String attributeDescription)
        throws LocalizedIllegalArgumentException, NullPointerException
    {
      return entry.containsAttribute(attributeDescription);
    }



    public boolean containsObjectClass(ObjectClass objectClass)
    {
      return entry.containsObjectClass(objectClass);
    }



    public boolean containsObjectClass(String objectClass)
    {
      return entry.containsObjectClass(objectClass);
    }



    /**
     * {@inheritDoc}
     */
    public boolean equals(Object object)
    {
      return (object == this || entry.equals(object));
    }



    public Iterable<Attribute> findAttributes(
        AttributeDescription attributeDescription)
    {
      return Iterables.unmodifiable(Iterables.transform(entry
          .findAttributes(attributeDescription),
          UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    /**
     * {@inheritDoc}
     */
    public Iterable<Attribute> findAttributes(
        String attributeDescription)
        throws LocalizedIllegalArgumentException, NullPointerException
    {
      return Iterables.unmodifiable(Iterables.transform(entry
          .findAttributes(attributeDescription),
          UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    public Attribute getAttribute(
        AttributeDescription attributeDescription)
    {
      Attribute attribute = entry.getAttribute(attributeDescription);
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
    public Attribute getAttribute(String attributeDescription)
        throws LocalizedIllegalArgumentException, NullPointerException
    {
      Attribute attribute = entry.getAttribute(attributeDescription);
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



    public Iterable<Attribute> getAttributes()
    {
      return Iterables.unmodifiable(Iterables.transform(entry
          .getAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    /**
     * {@inheritDoc}
     */
    public DN getName()
    {
      return entry.getName();
    }



    public Iterable<String> getObjectClasses()
    {
      return Iterables.unmodifiable(entry.getObjectClasses());
    }



    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
      return entry.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    public boolean removeAttribute(Attribute attribute,
        Collection<ByteString> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public boolean removeAttribute(
        AttributeDescription attributeDescription)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry removeAttribute(String attributeDescription)
        throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry removeAttribute(String attributeDescription,
        Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public boolean replaceAttribute(Attribute attribute)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry replaceAttribute(String attributeDescription,
        Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public Entry setName(String dn)
        throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    public Entry setName(DN dn) throws UnsupportedOperationException,
        NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      return entry.toString();
    }

  }



  private static final Function<Attribute, Attribute, Void> UNMODIFIABLE_ATTRIBUTE_FUNCTION = new Function<Attribute, Attribute, Void>()
  {

    public Attribute apply(Attribute value, Void p)
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
      AttributeDescription attributeDescription)
      throws NullPointerException
  {
    return new EmptyAttribute(attributeDescription);
  }



  /**
   * Returns a view of {@code attribute} having a different attribute
   * description. All operations on the returned attribute
   * "pass-through" to the underlying attribute.
   *
   * @param attribute
   *          The attribute to be renamed.
   * @param attributeDescription
   *          The new attribute description for {@code attribute}, which
   *          must be compatible with {@code attribute}'s attribute
   *          description.
   * @return A renamed view of {@code attribute}.
   * @throws IllegalArgumentException
   *           If {@code attributeDescription} does not have the same
   *           attribute type as {@code attribute}'s attribute
   *           description.
   * @throws NullPointerException
   *           If {@code attribute} or {@code attributeDescription} was
   *           {@code null}.
   */
  public static final Attribute renameAttribute(Attribute attribute,
      AttributeDescription attributeDescription)
      throws IllegalArgumentException, NullPointerException
  {
    AttributeType oldType = attribute.getAttributeDescription()
        .getAttributeType();
    AttributeType newType = attributeDescription.getAttributeType();

    // We could relax a bit by ensuring that they are both compatible
    // (e.g. one sub-type of another, or same equality matching rule,
    // etc).
    Validator.ensureTrue(oldType.equals(newType),
        "Old and new attribute type are not the same");

    return new RenamedAttribute(attribute, attributeDescription);
  }



  /**
   * Returns a read-only view of {@code attribute}. Query operations on
   * the returned attribute "read-through" to the underlying attribute,
   * and attempts to modify the returned attribute either directly or
   * indirectly via an iterator result in an {@code
   * UnsupportedOperationException}.
   *
   * @param attribute
   *          The attribute for which a read-only view is to be
   *          returned.
   * @return A read-only view of {@code attribute}.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  public static final Attribute unmodifiableAttribute(
      Attribute attribute) throws NullPointerException
  {
    return new UnmodifiableAttribute(attribute);
  }



  /**
   * Returns a read-only view of {@code entry} and its attributes. Query
   * operations on the returned entry and its attributes"read-through"
   * to the underlying entry or attribute, and attempts to modify the
   * returned entry and its attributes either directly or indirectly via
   * an iterator result in an {@code UnsupportedOperationException}.
   *
   * @param entry
   *          The entry for which a read-only view is to be returned.
   * @return A read-only view of {@code entry}.
   * @throws NullPointerException
   *           If {@code entry} was {@code null}.
   */
  public static final Entry unmodifiableEntry(Entry entry)
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
