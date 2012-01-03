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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;



import java.util.Collection;


import com.forgerock.opendj.util.Iterables;
import com.forgerock.opendj.util.Predicate;
import com.forgerock.opendj.util.Validator;



/**
 * This class provides a skeletal implementation of the {@code Entry} interface,
 * to minimize the effort required to implement this interface.
 */
public abstract class AbstractEntry implements Entry
{

  // Predicate used for findAttributes.
  private static final Predicate<Attribute, AttributeDescription>
    FIND_ATTRIBUTES_PREDICATE = new Predicate<Attribute, AttributeDescription>()
  {

    public boolean matches(final Attribute value, final AttributeDescription p)
    {
      return value.getAttributeDescription().isSubTypeOf(p);
    }

  };



  /**
   * Returns {@code true} if {@code object} is an entry which is equal to
   * {@code entry}. Two entry are considered equal if their distinguished names
   * are equal, they both have the same number of attributes, and every
   * attribute contained in the first entry is also contained in the second
   * entry.
   *
   * @param entry
   *          The entry to be tested for equality.
   * @param object
   *          The object to be tested for equality with the entry.
   * @return {@code true} if {@code object} is an entry which is equal to
   *         {@code entry}, or {@code false} if not.
   */
  static boolean equals(final Entry entry, final Object object)
  {
    if (entry == object)
    {
      return true;
    }

    if (!(object instanceof Entry))
    {
      return false;
    }

    final Entry other = (Entry) object;
    if (!entry.getName().equals(other.getName()))
    {
      return false;
    }

    // Distinguished name is the same, compare attributes.
    if (entry.getAttributeCount() != other.getAttributeCount())
    {
      return false;
    }

    for (final Attribute attribute : entry.getAllAttributes())
    {
      final Attribute otherAttribute = other.getAttribute(attribute
          .getAttributeDescription());

      if (!attribute.equals(otherAttribute))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Returns the hash code for {@code entry}. It will be calculated as the sum
   * of the hash codes of the distinguished name and all of the attributes.
   *
   * @param entry
   *          The entry whose hash code should be calculated.
   * @return The hash code for {@code entry}.
   */
  static int hashCode(final Entry entry)
  {
    int hashCode = entry.getName().hashCode();
    for (final Attribute attribute : entry.getAllAttributes())
    {
      hashCode += attribute.hashCode();
    }
    return hashCode;
  }



  /**
   * Returns a string representation of {@code entry}.
   *
   * @param entry
   *          The entry whose string representation should be returned.
   * @return The string representation of {@code entry}.
   */
  static String toString(final Entry entry)
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("Entry(");
    builder.append(entry.getName());
    builder.append(", {");

    boolean firstValue = true;
    for (final Attribute attribute : entry.getAllAttributes())
    {
      if (!firstValue)
      {
        builder.append(", ");
      }

      builder.append(attribute);
      firstValue = false;
    }

    builder.append("})");
    return builder.toString();
  }



  /**
   * Sole constructor.
   */
  protected AbstractEntry()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public boolean addAttribute(final Attribute attribute)
  {
    return addAttribute(attribute, null);
  }



  /**
   * {@inheritDoc}
   */
  public Entry addAttribute(final String attributeDescription,
      final Object... values)
  {
    addAttribute(new LinkedAttribute(attributeDescription, values), null);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsAttribute(final Attribute attribute,
      final Collection<ByteString> missingValues)
  {
    final Attribute a = getAttribute(attribute.getAttributeDescription());
    if (a == null)
    {
      if (missingValues != null)
      {
        missingValues.addAll(attribute);
      }
      return false;
    }
    else
    {
      boolean result = true;
      for (final ByteString value : attribute)
      {
        if (!a.contains(value))
        {
          if (missingValues != null)
          {
            missingValues.add(value);
          }
          result = false;
        }
      }
      return result;
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsAttribute(final String attributeDescription,
      final Object... values)
  {
    return containsAttribute(new LinkedAttribute(attributeDescription, values),
        null);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object object)
  {
    return equals(this, object);
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> getAllAttributes(
      final AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);

    return Iterables.filteredIterable(getAllAttributes(), FIND_ATTRIBUTES_PREDICATE,
        attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> getAllAttributes(final String attributeDescription)
  {
    return getAllAttributes(AttributeDescription.valueOf(attributeDescription));
  }



  /**
   * {@inheritDoc}
   */
  public Attribute getAttribute(final String attributeDescription)
  {
    return getAttribute(AttributeDescription.valueOf(attributeDescription));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return hashCode(this);
  }



  /**
   * {@inheritDoc}
   */
  public boolean removeAttribute(final AttributeDescription attributeDescription)
  {
    return removeAttribute(
        Attributes.emptyAttribute(attributeDescription), null);
  }



  /**
   * {@inheritDoc}
   */
  public Entry removeAttribute(final String attributeDescription,
      final Object... values)
  {
    removeAttribute(new LinkedAttribute(attributeDescription, values), null);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean replaceAttribute(final Attribute attribute)
  {
    if (attribute.isEmpty())
    {
      return removeAttribute(attribute.getAttributeDescription());
    }
    else
    {
      removeAttribute(attribute.getAttributeDescription());
      addAttribute(attribute, null);
      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public Entry replaceAttribute(final String attributeDescription,
      final Object... values)
  {
    replaceAttribute(new LinkedAttribute(attributeDescription, values));
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public Entry setName(final String dn)
  {
    return setName(DN.valueOf(dn));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return toString(this);
  }

}
