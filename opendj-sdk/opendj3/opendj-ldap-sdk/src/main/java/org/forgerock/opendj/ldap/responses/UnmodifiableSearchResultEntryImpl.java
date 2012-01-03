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

import com.forgerock.opendj.util.Function;
import com.forgerock.opendj.util.Iterables;



/**
 * Unmodifiable Search result entry implementation.
 */
class UnmodifiableSearchResultEntryImpl extends
    AbstractUnmodifiableResponseImpl<SearchResultEntry> implements
    SearchResultEntry
{
  private static final Function<Attribute, Attribute, Void>
    UNMODIFIABLE_ATTRIBUTE_FUNCTION = new Function<Attribute, Attribute, Void>()
  {

    public Attribute apply(final Attribute value, final Void p)
    {
      return Attributes.unmodifiableAttribute(value);
    }

  };



  UnmodifiableSearchResultEntryImpl(SearchResultEntry impl)
  {
    super(impl);
  }



  public boolean addAttribute(Attribute attribute)
  {
    throw new UnsupportedOperationException();
  }



  public boolean addAttribute(Attribute attribute,
      Collection<ByteString> duplicateValues)
  {
    throw new UnsupportedOperationException();
  }



  public SearchResultEntry addAttribute(String attributeDescription,
      Object... values)
  {
    throw new UnsupportedOperationException();
  }



  public SearchResultEntry clearAttributes()
  {
    throw new UnsupportedOperationException();
  }



  public boolean containsAttribute(Attribute attribute,
      Collection<ByteString> missingValues)
  {
    return impl.containsAttribute(attribute, missingValues);
  }



  public boolean containsAttribute(String attributeDescription,
      Object... values)
  {
    return impl.containsAttribute(attributeDescription, values);
  }



  public Iterable<Attribute> getAllAttributes()
  {
    return Iterables.unmodifiableIterable(Iterables.transformedIterable(
        impl.getAllAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
  }



  public Iterable<Attribute> getAllAttributes(
      AttributeDescription attributeDescription)
  {
    return Iterables.unmodifiableIterable(Iterables.transformedIterable(
        impl.getAllAttributes(attributeDescription),
        UNMODIFIABLE_ATTRIBUTE_FUNCTION));
  }



  public Iterable<Attribute> getAllAttributes(String attributeDescription)
  {
    return Iterables.unmodifiableIterable(Iterables.transformedIterable(
        impl.getAllAttributes(attributeDescription),
        UNMODIFIABLE_ATTRIBUTE_FUNCTION));
  }



  public Attribute getAttribute(AttributeDescription attributeDescription)
  {
    final Attribute attribute = impl.getAttribute(attributeDescription);
    if (attribute != null)
    {
      return Attributes.unmodifiableAttribute(attribute);
    }
    else
    {
      return null;
    }
  }



  public Attribute getAttribute(String attributeDescription)
  {
    final Attribute attribute = impl.getAttribute(attributeDescription);
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
    return impl.getAttributeCount();
  }



  public DN getName()
  {
    return impl.getName();
  }



  public boolean removeAttribute(Attribute attribute,
      Collection<ByteString> missingValues)
  {
    throw new UnsupportedOperationException();
  }



  public boolean removeAttribute(AttributeDescription attributeDescription)
  {
    throw new UnsupportedOperationException();
  }



  public SearchResultEntry removeAttribute(String attributeDescription,
      Object... values)
  {
    throw new UnsupportedOperationException();
  }



  public boolean replaceAttribute(Attribute attribute)
  {
    throw new UnsupportedOperationException();
  }



  public SearchResultEntry replaceAttribute(String attributeDescription,
      Object... values)
  {
    throw new UnsupportedOperationException();
  }



  public SearchResultEntry setName(DN dn)
  {
    throw new UnsupportedOperationException();
  }



  public SearchResultEntry setName(String dn)
  {
    throw new UnsupportedOperationException();
  }
}
