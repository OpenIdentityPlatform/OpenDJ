/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import javax.naming.NamingException;

import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.types.OpenDsException;

/**
 * This is a commodity class used to wrap the SearchResult class of JNDI.
 * Basically it retrieves all the attributes and values on the SearchResult and
 * calculates its DN.  Using it we avoid having to handle the NamingException
 * exceptions that most of the methods in SearchResult throw.
 */
public class CustomSearchResult implements Comparable<CustomSearchResult>
{
  private final Entry entry;

  /**
   * Constructor of an empty search result.  This constructor is used by the
   * LDAP entry editor which 'build' their own CustomSearchResult.  The entry
   * editors use some methods that require CustomSearchResult.
   * @param name the dn of the entry.
   */
  public CustomSearchResult(DN name)
  {
    this.entry = new LinkedHashMapEntry(name);
  }

  /**
   * Constructor of a search result using a SearchResult as basis.
   * @param sr the SearchResult.
   * @throws NamingException if there is an error retrieving the attribute values.
   */
  public CustomSearchResult(SearchResultEntry sr) throws NamingException
  {
    this.entry = sr;
  }

  /**
   * Returns the DN of the entry.
   * @return the DN of the entry.
   */
  public DN getName()
  {
    return entry.getName();
  }

  public Attribute getAttribute(AttributeDescription attributeDescription)
  {
    return entry.getAttribute(attributeDescription);
  }

  public Attribute getAttribute(String attributeDescription)
  {
    return entry.getAttribute(attributeDescription);
  }

  public Iterable<Attribute> getAllAttributes()
  {
    return entry.getAllAttributes();
  }

  public Entry getSdkEntry()
  {
    return entry;
  }

  @Override
  public int compareTo(CustomSearchResult o) {
    if (this.equals(o))
    {
      return 0;
    }
    return toString().compareTo(o.toString());
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
    {
      return true;
    }
    if (o instanceof CustomSearchResult)
    {
      return entry.equals(((CustomSearchResult)o).entry);
    }
    return false;
  }

  @Override
  public String toString() {
    return entry.toString();
  }

  @Override
  public int hashCode() {
    return entry.hashCode();
  }

  /** 
   * Sets the given attribute.
   * @param attr the attribute.
   */
  public void set(final Attribute attr)
  {
    entry.removeAttribute(attr.getAttributeDescription());
    entry.addAttribute(attr);
  }

  /**
   * Gets the Entry object equivalent to this CustomSearchResult.
   * The method assumes that the schema in DirectoryServer has been initialized.
   * @return the Entry object equivalent to this CustomSearchResult.
   * @throws OpenDsException if there is an error parsing the DN or retrieving
   * the attributes definition and objectclasses in the schema of the server.
   */
  public org.opends.server.types.Entry getEntry() throws OpenDsException
  {
    return Converters.to(entry);
  }
}
