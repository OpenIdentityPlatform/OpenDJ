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



import org.opends.sdk.schema.ObjectClass;
import org.opends.sdk.util.*;



/**
 * This class provides a skeletal implementation of the {@code Entry}
 * interface, to minimize the effort required to implement this
 * interface.
 */
public abstract class AbstractEntry implements Entry
{

  // Function used for getObjectClasses
  private static final Function<ByteString, String, Void> BYTE_STRING_TO_STRING_FUNCTION = new Function<ByteString, String, Void>()
  {

    public String apply(ByteString value, Void p)
    {
      return value.toString();
    }

  };

  // Predicate used for findAttributes.
  private static final Predicate<Attribute, AttributeDescription> FIND_ATTRIBUTES_PREDICATE = new Predicate<Attribute, AttributeDescription>()
  {

    public boolean matches(Attribute value, AttributeDescription p)
    {
      return value.getAttributeDescription().isSubTypeOf(p);
    }

  };



  /**
   * Returns {@code true} if {@code object} is an entry which is equal
   * to {@code entry}. Two entry are considered equal if their
   * distinguished names are equal, they both have the same number of
   * attributes, and every attribute contained in the first entry is
   * also contained in the second entry.
   *
   * @param entry
   *          The entry to be tested for equality.
   * @param object
   *          The object to be tested for equality with the entry.
   * @return {@code true} if {@code object} is an entry which is equal
   *         to {@code entry}, or {@code false} if not.
   */
  static boolean equals(Entry entry, Object object)
  {
    if (entry == object)
    {
      return true;
    }

    if (!(object instanceof Entry))
    {
      return false;
    }

    Entry other = (Entry) object;
    if (!entry.getName().equals(other.getName()))
    {
      return false;
    }

    // Distinguished name is the same, compare attributes.
    if (entry.getAttributeCount() != other.getAttributeCount())
    {
      return false;
    }

    for (Attribute attribute : entry.getAttributes())
    {
      Attribute otherAttribute = other.getAttribute(attribute
          .getAttributeDescription());

      if (!attribute.equals(otherAttribute))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Returns the hash code for {@code entry}. It will be calculated as
   * the sum of the hash codes of the distinguished name and all of the
   * attributes.
   *
   * @param entry
   *          The entry whose hash code should be calculated.
   * @return The hash code for {@code entry}.
   */
  static int hashCode(Entry entry)
  {
    int hashCode = entry.getName().hashCode();
    for (Attribute attribute : entry.getAttributes())
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
  static String toString(Entry entry)
  {
    StringBuilder builder = new StringBuilder();
    builder.append("Entry(");
    builder.append(entry.getName());
    builder.append(", {");

    boolean firstValue = true;
    for (Attribute attribute : entry.getAttributes())
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
  public boolean addAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException
  {
    return addAttribute(attribute, null);
  }



  /**
   * {@inheritDoc}
   */
  public Entry addAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    addAttribute(new LinkedAttribute(attributeDescription, values), null);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return containsAttribute(AttributeDescription
        .valueOf(attributeDescription));
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsObjectClass(ObjectClass objectClass)
      throws NullPointerException
  {
    return containsObjectClass(objectClass.getOID());
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsObjectClass(String objectClass)
      throws NullPointerException
  {
    Validator.ensureNotNull(objectClass);

    Attribute attribute = getAttribute(AttributeDescription
        .objectClass());
    return attribute != null ? attribute.contains(objectClass) : false;
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
  public Iterable<Attribute> findAttributes(
      AttributeDescription attributeDescription)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);

    return Iterables.filter(getAttributes(), FIND_ATTRIBUTES_PREDICATE,
        attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Attribute> findAttributes(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return findAttributes(AttributeDescription
        .valueOf(attributeDescription));
  }



  /**
   * {@inheritDoc}
   */
  public Attribute getAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return getAttribute(AttributeDescription
        .valueOf(attributeDescription));
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<String> getObjectClasses()
  {
    Attribute attribute = getAttribute(AttributeDescription
        .objectClass());

    if (attribute == null)
    {
      return Iterables.empty();
    }
    else
    {
      return Iterables.transform(attribute,
          BYTE_STRING_TO_STRING_FUNCTION);
    }
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
  public boolean removeAttribute(
      AttributeDescription attributeDescription)
      throws UnsupportedOperationException, NullPointerException
  {
    return removeAttribute(Types.emptyAttribute(attributeDescription),
        null);
  }



  /**
   * {@inheritDoc}
   */
  public Entry removeAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    removeAttribute(new LinkedAttribute(attributeDescription), null);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public Entry removeAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    removeAttribute(new LinkedAttribute(attributeDescription, values),
        null);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public boolean replaceAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException
  {
    if (attribute.isEmpty())
    {
      return removeAttribute(attribute.getAttributeDescription());
    }
    else
    {
      removeAttribute(attribute.getAttributeDescription());
      addAttribute(attribute);
      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public Entry replaceAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    replaceAttribute(new LinkedAttribute(attributeDescription, values));
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public Entry setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    return setName(DN.valueOf(dn));
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return toString(this);
  }

}
