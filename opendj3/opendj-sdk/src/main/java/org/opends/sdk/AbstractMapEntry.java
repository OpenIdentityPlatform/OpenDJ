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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.Collection;
import java.util.Map;

import com.forgerock.opendj.util.Validator;



/**
 * Abstract implementation for {@code Map} based entries.
 */
abstract class AbstractMapEntry extends AbstractEntry
{
  private final Map<AttributeDescription, Attribute> attributes;

  private DN name;



  /**
   * Creates an empty entry using the provided distinguished name and {@code
   * Map}.
   *
   * @param name
   *          The distinguished name of this entry.
   * @param attributes
   *          The attribute map.
   */
  AbstractMapEntry(final DN name,
      final Map<AttributeDescription, Attribute> attributes)
      throws NullPointerException
  {
    this.name = name;
    this.attributes = attributes;
  }



  /**
   * {@inheritDoc}
   */
  public final boolean addAttribute(final Attribute attribute,
      final Collection<ByteString> duplicateValues) throws NullPointerException
  {
    Validator.ensureNotNull(attribute);

    final AttributeDescription attributeDescription = attribute
        .getAttributeDescription();
    final Attribute oldAttribute = attributes.get(attributeDescription);
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



  /**
   * {@inheritDoc}
   */
  public final Entry clearAttributes()
  {
    attributes.clear();
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public final Iterable<Attribute> getAllAttributes()
  {
    return attributes.values();
  }



  /**
   * {@inheritDoc}
   */
  public final Attribute getAttribute(
      final AttributeDescription attributeDescription)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);

    return attributes.get(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public final int getAttributeCount()
  {
    return attributes.size();
  }



  /**
   * {@inheritDoc}
   */
  public final DN getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public final boolean removeAttribute(final Attribute attribute,
      final Collection<ByteString> missingValues) throws NullPointerException
  {
    Validator.ensureNotNull(attribute);

    final AttributeDescription attributeDescription = attribute
        .getAttributeDescription();

    if (attribute.isEmpty())
    {
      return attributes.remove(attributeDescription) != null;
    }
    else
    {
      final Attribute oldAttribute = attributes.get(attributeDescription);
      if (oldAttribute != null)
      {
        final boolean modified = oldAttribute.removeAll(attribute,
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
        if (missingValues != null)
        {
          missingValues.addAll(attribute);
        }
        return false;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public final Entry setName(final DN dn) throws NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }

}
