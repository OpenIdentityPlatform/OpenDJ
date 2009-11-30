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
import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.sdk.requests.Requests;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.Validator;



/**
 * An implementation of the {@code Entry} interface which uses a {@code
 * SortedMap} for storing attributes. Attributes are returned in
 * ascending order of attribute description, with {@code objectClass}
 * first, then all user attributes, and finally any operational
 * attributes. All operations are supported by this implementation.
 */
public final class SortedEntry extends AbstractEntry
{
  private final SortedMap<AttributeDescription, Attribute> attributes = new TreeMap<AttributeDescription, Attribute>();

  private DN name;



  /**
   * Creates an empty sorted entry and an empty (root) distinguished
   * name.
   */
  public SortedEntry()
  {
    this(DN.rootDN());
  }



  /**
   * Creates an empty sorted entry using the provided distinguished
   * name.
   *
   * @param name
   *          The distinguished name of this entry.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public SortedEntry(DN name) throws NullPointerException
  {
    Validator.ensureNotNull(name);
    this.name = name;
  }



  /**
   * Creates an empty sorted entry using the provided distinguished name
   * decoded using the default schema.
   *
   * @param name
   *          The distinguished name of this entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public SortedEntry(String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    this(DN.valueOf(name));
  }



  /**
   * Creates a sorted entry having the same distinguished name,
   * attributes, and object classes of the provided entry.
   *
   * @param entry
   *          The entry to be copied.
   * @throws NullPointerException
   *           If {@code entry} was {@code null}.
   */
  public SortedEntry(Entry entry)
  {
    Validator.ensureNotNull(entry);

    this.name = entry.getName();
    for (Attribute attribute : entry.getAttributes())
    {
      addAttribute(attribute);
    }
  }



  /**
   * Creates a new sorted entry using the provided lines of LDIF decoded
   * using the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing the an LDIF add change record or
   *          an LDIF entry record.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid
   *           LDIF, or could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public SortedEntry(String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    this(Requests.newAddRequest(ldifLines));
  }



  /**
   * {@inheritDoc}
   */
  public boolean addAttribute(Attribute attribute,
      Collection<ByteString> duplicateValues)
      throws NullPointerException
  {
    Validator.ensureNotNull(attribute);

    if (!attribute.isEmpty())
    {
      AttributeDescription attributeDescription = attribute
          .getAttributeDescription();
      Attribute oldAttribute = attributes.get(attributeDescription);
      if (oldAttribute != null)
      {
        return oldAttribute.addAll(attribute, duplicateValues);
      }
      else
      {
        attributes.put(attributeDescription, attribute);
        return true;
      }
    }
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public Entry clearAttributes()
  {
    attributes.clear();
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsAttribute(
      AttributeDescription attributeDescription)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);

    return attributes.containsKey(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public Attribute getAttribute(
      AttributeDescription attributeDescription)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);

    return attributes.get(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public int getAttributeCount()
  {
    return attributes.size();
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> getAttributes()
  {
    return attributes.values();
  }



  /**
   * {@inheritDoc}
   */
  public DN getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public boolean removeAttribute(Attribute attribute,
      Collection<ByteString> missingValues) throws NullPointerException
  {
    Validator.ensureNotNull(attribute);

    AttributeDescription attributeDescription = attribute
        .getAttributeDescription();

    if (attribute.isEmpty())
    {
      return attributes.remove(attributeDescription) != null;
    }
    else
    {
      Attribute oldAttribute = attributes.get(attributeDescription);
      if (oldAttribute != null)
      {
        boolean modified = oldAttribute.removeAll(attribute,
            missingValues);
        if (oldAttribute.isEmpty())
        {
          attributes.remove(attributeDescription);
          return true;
        }
        return modified;
      }
      else
      {
        return false;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public Entry setName(DN dn) throws NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }

}
