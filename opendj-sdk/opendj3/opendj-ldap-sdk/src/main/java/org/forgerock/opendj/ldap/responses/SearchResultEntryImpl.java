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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;



import java.util.Collection;

import org.forgerock.opendj.ldap.*;



/**
 * Search result entry implementation.
 */
final class SearchResultEntryImpl extends
    AbstractResponseImpl<SearchResultEntry> implements SearchResultEntry
{

  private final Entry entry;



  /**
   * Creates a new search result entry backed by the provided entry.
   * Modifications made to {@code entry} will be reflected in the returned
   * search result entry. The returned search result entry supports updates to
   * its list of controls, as well as updates to the name and attributes if the
   * underlying entry allows.
   *
   * @param entry
   *          The entry.
   * @throws NullPointerException
   *           If {@code entry} was {@code null} .
   */
  SearchResultEntryImpl(final Entry entry)
  {
    this.entry = entry;
  }



  /**
   * Creates a new search result entry that is an exact copy of the provided
   * result.
   *
   * @param searchResultEntry
   *          The search result entry to be copied.
   * @throws NullPointerException
   *           If {@code searchResultEntry} was {@code null} .
   */
  SearchResultEntryImpl(final SearchResultEntry searchResultEntry)
  {
    super(searchResultEntry);
    this.entry = LinkedHashMapEntry.deepCopyOfEntry(searchResultEntry);
  }



  /**
   * {@inheritDoc}
   */
  public boolean addAttribute(final Attribute attribute)
  {
    return entry.addAttribute(attribute);
  }



  /**
   * {@inheritDoc}
   */
  public boolean addAttribute(final Attribute attribute,
      final Collection<ByteString> duplicateValues)
  {
    return entry.addAttribute(attribute, duplicateValues);
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry addAttribute(final String attributeDescription,
      final Object... values)
  {
    entry.addAttribute(attributeDescription, values);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry clearAttributes()
  {
    entry.clearAttributes();
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsAttribute(final Attribute attribute,
      final Collection<ByteString> missingValues)
  {
    return entry.containsAttribute(attribute, missingValues);
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsAttribute(final String attributeDescription,
      final Object... values)
  {
    return entry.containsAttribute(attributeDescription, values);
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> getAllAttributes()
  {
    return entry.getAllAttributes();
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> getAllAttributes(
      final AttributeDescription attributeDescription)
  {
    return entry.getAllAttributes(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> getAllAttributes(final String attributeDescription)
  {
    return entry.getAllAttributes(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public Attribute getAttribute(final AttributeDescription attributeDescription)
  {
    return entry.getAttribute(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public Attribute getAttribute(final String attributeDescription)
  {
    return entry.getAttribute(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
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
  public boolean removeAttribute(final Attribute attribute,
      final Collection<ByteString> missingValues)
  {
    return entry.removeAttribute(attribute, missingValues);
  }



  /**
   * {@inheritDoc}
   */
  public boolean removeAttribute(final AttributeDescription attributeDescription)
  {
    return entry.removeAttribute(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry removeAttribute(final String attributeDescription,
      final Object... values)
  {
    entry.removeAttribute(attributeDescription, values);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean replaceAttribute(final Attribute attribute)
  {
    return entry.replaceAttribute(attribute);
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry replaceAttribute(final String attributeDescription,
      final Object... values)
  {
    entry.replaceAttribute(attributeDescription, values);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry setName(final DN dn)
  {
    entry.setName(dn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultEntry setName(final String dn)
  {
    entry.setName(dn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("SearchResultEntry(name=");
    builder.append(getName());
    builder.append(", attributes=");
    builder.append(getAllAttributes());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  @Override
  SearchResultEntry getThis()
  {
    return this;
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
  public boolean equals(final Object object)
  {
    return entry.equals(object);
  }

}
