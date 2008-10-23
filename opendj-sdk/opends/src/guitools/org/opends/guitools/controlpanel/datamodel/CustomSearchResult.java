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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;

import org.opends.guitools.controlpanel.util.Utilities;

/**
 * This is a commodity class used to wrap the SearchResult class of JNDI.
 * Basically it retrieves all the attributes and values on the SearchResult and
 * calculates its DN.  Using it we avoid having to handle the NamingException
 * exceptions that most of the methods in SearchResult throw.
 *
 */
public class CustomSearchResult implements Comparable {
  private Name name;
  private String dn;
  private Map<String, Set<Object>> attributes;
  private SortedSet<String> attrNames;

  /**
   * Constructor of an empty search result.  This constructor is used by the
   * LDAP entry editor which 'build' their own CustomSearchResult.  The entry
   * editors use some methods that require CustomSearchResult.
   * @param dn the dn of the entry.
   */
  public CustomSearchResult(String dn)
  {
    this.dn = dn;
    attributes = new HashMap<String, Set<Object>>();
    attrNames = new TreeSet<String>();
  }

  /**
   * Constructor of a search result using a SearchResult as basis.
   * @param sr the SearchResult.
   * @param baseDN the base DN of the search that returned the SearchResult.
   * @throws NamingException if there is an error retrieving the attribute
   * values.
   */
  public CustomSearchResult(SearchResult sr, String baseDN)
  throws NamingException
  {
    String sName = sr.getName();
    if ((baseDN != null) && (baseDN.length() > 0))
    {
      if ((sName != null) && (sName.length() > 0))
      {
        name = new CompositeName(sName);
        name.add(baseDN);

      }
      else {
        name = Utilities.getJNDIName(baseDN);
      }
    }
    else {
      name = new CompositeName(sName);
    }
    StringBuilder buf = new StringBuilder();
    for (int i=0; i<name.size(); i++)
    {
      String n = name.get(i);
      if ((buf.length() != 0) && (n != null) && (n.length() > 0))
      {
        buf.append(",");
      }
      if ((n != null) && (n.length() > 0))
      {
        buf.append(n);
      }
    }
    dn = buf.toString();

    attributes = new HashMap<String, Set<Object>>();
    attrNames = new TreeSet<String>();
    Attributes attrs = sr.getAttributes();
    if (attrs != null)
    {
      NamingEnumeration en = attrs.getAll();
      while (en.hasMore()) {
        Attribute attr = (Attribute)en.next();
        String name = attr.getID();
        attrNames.add(name);
        Set<Object> values = new HashSet<Object>();
        for (int i=0; i<attr.size(); i++)
        {
          Object v = attr.get(i);
          if (!"".equals(v.toString()))
          {
            values.add(v);
          }
        }
        attributes.put(name.toLowerCase(), values);
      }
    }
  }

  /**
   * Returns the DN of the entry.
   * @return the DN of the entry.
   */
  public String getDN() {
    return dn;
  }

  /**
   * Returns the values for a given attribute.  It returns an empty Set if
   * the attribute is not defined.
   * @param name the name of the attribute.
   * @return the values for a given attribute.  It returns an empty Set if
   * the attribute is not defined.
   */
  public Set<Object> getAttributeValues(String name) {
    Set<Object> values = attributes.get(name.toLowerCase());
    if (values == null)
    {
      values = Collections.emptySet();
    }
    return values;
  }

  /**
   * Returns all the attribute names of the entry.
   * @return the attribute names of the entry.
   */
  public SortedSet<String> getAttributeNames() {
    return attrNames;
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(Object o) {
    return this.getDN().compareTo(((CustomSearchResult)o).getDN());
  }

  /**
   * {@inheritDoc}
   */
  public String toString() {
    return "dn: "+dn+"\nattributes: "+attributes;
  }

  /**
   * Sets the values for a given attribute name.
   * @param attrName the name of the attribute.
   * @param values the values for the attribute.
   */
  public void set(String attrName, Set<Object> values)
  {
    attrNames.add(attrName);
    attrName = attrName.toLowerCase();
    attributes.put(attrName, values);
  }
}
