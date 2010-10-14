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
import java.util.Iterator;

import org.opends.sdk.requests.ModifyRequest;
import org.opends.sdk.requests.Requests;

import com.sun.opends.sdk.util.Function;
import com.sun.opends.sdk.util.Iterables;
import com.sun.opends.sdk.util.Validator;



/**
 * This class contains methods for creating and manipulating entries.
 *
 * @see Entry
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
    @Override
    public boolean addAttribute(final Attribute attribute)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addAttribute(final Attribute attribute,
        final Collection<ByteString> duplicateValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Entry addAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public Entry clearAttributes() throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public boolean containsAttribute(final Attribute attribute,
        final Collection<ByteString> missingValues) throws NullPointerException
    {
      return entry.containsAttribute(attribute, missingValues);
    }



    @Override
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



    @Override
    public Iterable<Attribute> getAllAttributes()
    {
      return Iterables.unmodifiable(Iterables.transform(
          entry.getAllAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    @Override
    public Iterable<Attribute> getAllAttributes(
        final AttributeDescription attributeDescription)
    {
      return Iterables.unmodifiable(Iterables.transform(
          entry.getAllAttributes(attributeDescription),
          UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Attribute> getAllAttributes(
        final String attributeDescription)
        throws LocalizedIllegalArgumentException, NullPointerException
    {
      return Iterables.unmodifiable(Iterables.transform(
          entry.getAllAttributes(attributeDescription),
          UNMODIFIABLE_ATTRIBUTE_FUNCTION));
    }



    @Override
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
    @Override
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



    @Override
    public int getAttributeCount()
    {
      return entry.getAttributeCount();
    }



    /**
     * {@inheritDoc}
     */
    @Override
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
    @Override
    public boolean removeAttribute(final Attribute attribute,
        final Collection<ByteString> missingValues)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public boolean removeAttribute(
        final AttributeDescription attributeDescription)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Entry removeAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean replaceAttribute(final Attribute attribute)
        throws UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Entry replaceAttribute(final String attributeDescription,
        final Object... values) throws LocalizedIllegalArgumentException,
        UnsupportedOperationException, NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    @Override
    public Entry setName(final DN dn) throws UnsupportedOperationException,
        NullPointerException
    {
      throw new UnsupportedOperationException();
    }



    /**
     * {@inheritDoc}
     */
    @Override
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



  private static final Function<Attribute, Attribute, Void> UNMODIFIABLE_ATTRIBUTE_FUNCTION =
    new Function<Attribute, Attribute, Void>()
  {

    @Override
    public Attribute apply(final Attribute value, final Void p)
    {
      return Attributes.unmodifiableAttribute(value);
    }

  };



  /**
   * Creates a new modify request containing a list of modifications which can
   * be used to transform {@code fromEntry} into entry {@code toEntry}.
   * <p>
   * The modify request is reversible: it will contain only modifications of
   * type {@link ModificationType#ADD ADD} and {@link ModificationType#DELETE
   * DELETE}.
   * <p>
   * Finally, the modify request will use the distinguished name taken from
   * {@code fromEntry}. Moreover, this method will not check to see if both
   * {@code fromEntry} and {@code toEntry} have the same distinguished name.
   * <p>
   * This method is equivalent to:
   *
   * <pre>
   * ModifyRequest request = Requests.newModifyRequest(fromEntry, toEntry);
   * </pre>
   *
   * @param fromEntry
   *          The source entry.
   * @param toEntry
   *          The destination entry.
   * @return A modify request containing a list of modifications which can be
   *         used to transform {@code fromEntry} into entry {@code toEntry}.
   * @throws NullPointerException
   *           If {@code fromEntry} or {@code toEntry} were {@code null}.
   * @see Requests#newModifyRequest(Entry, Entry)
   */
  public static final ModifyRequest diffEntries(final Entry fromEntry,
      final Entry toEntry) throws NullPointerException
  {
    Validator.ensureNotNull(fromEntry, toEntry);

    final ModifyRequest request = Requests
        .newModifyRequest(fromEntry.getName());

    TreeMapEntry tfrom;
    if (fromEntry instanceof TreeMapEntry)
    {
      tfrom = (TreeMapEntry) fromEntry;
    }
    else
    {
      tfrom = new TreeMapEntry(fromEntry);
    }

    TreeMapEntry tto;
    if (toEntry instanceof TreeMapEntry)
    {
      tto = (TreeMapEntry) toEntry;
    }
    else
    {
      tto = new TreeMapEntry(toEntry);
    }

    final Iterator<Attribute> ifrom = tfrom.getAllAttributes().iterator();
    final Iterator<Attribute> ito = tto.getAllAttributes().iterator();

    Attribute afrom = ifrom.hasNext() ? ifrom.next() : null;
    Attribute ato = ito.hasNext() ? ito.next() : null;

    while (afrom != null && ato != null)
    {
      final AttributeDescription adfrom = afrom.getAttributeDescription();
      final AttributeDescription adto = ato.getAttributeDescription();

      final int cmp = adfrom.compareTo(adto);
      if (cmp == 0)
      {
        // Attribute is in both entries. Compute the set of values to be added
        // and removed. We won't replace the attribute because this is not
        // reversible.
        final Attribute addedValues = new LinkedAttribute(ato);
        addedValues.removeAll(afrom);
        if (!addedValues.isEmpty())
        {
          request.addModification(new Modification(ModificationType.ADD,
              addedValues));
        }

        final Attribute deletedValues = new LinkedAttribute(afrom);
        deletedValues.removeAll(ato);
        if (!deletedValues.isEmpty())
        {
          request.addModification(new Modification(ModificationType.DELETE,
              deletedValues));
        }

        afrom = ifrom.hasNext() ? ifrom.next() : null;
        ato = ito.hasNext() ? ito.next() : null;
      }
      else if (cmp < 0)
      {
        // afrom in source, but not destination.
        request
            .addModification(new Modification(ModificationType.DELETE, afrom));
        afrom = ifrom.hasNext() ? ifrom.next() : null;
      }
      else
      {
        // ato in destination, but not in source.
        request.addModification(new Modification(ModificationType.ADD, ato));
        ato = ito.hasNext() ? ito.next() : null;
      }
    }

    // Additional attributes in source entry: these must be deleted.
    if (afrom != null)
    {
      request.addModification(new Modification(ModificationType.DELETE, afrom));
    }

    while (ifrom.hasNext())
    {
      final Attribute a = ifrom.next();
      request.addModification(new Modification(ModificationType.DELETE, a));
    }

    // Additional attributes in destination entry: these must be added.
    if (ato != null)
    {
      request.addModification(new Modification(ModificationType.ADD, ato));
    }

    while (ito.hasNext())
    {
      final Attribute a = ito.next();
      request.addModification(new Modification(ModificationType.ADD, a));
    }

    return request;
  }



  /**
   * Returns a read-only view of {@code entry} and its attributes. Query
   * operations on the returned entry and its attributes "read-through" to the
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
