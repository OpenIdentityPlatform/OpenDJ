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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.Collection;

import com.sun.opends.sdk.util.Function;
import com.sun.opends.sdk.util.Iterables;



/**
 * This class contains methods for creating and manipulating entries.
 */
public final class Entries
{

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
        return Attributes.unmodifiableAttribute(attribute);
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
        return Attributes.unmodifiableAttribute(attribute);
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
      return Attributes.unmodifiableAttribute(value);
    }

  };



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
  private Entries()
  {
    // Nothing to do.
  }
}
