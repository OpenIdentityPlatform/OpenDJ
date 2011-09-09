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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.guitools.controlpanel.datamodel;

import static org.opends.server.util.StaticUtils.toLowerCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;

/**
 * This is a commodity class used to wrap the SearchResult class of JNDI.
 * Basically it retrieves all the attributes and values on the SearchResult and
 * calculates its DN.  Using it we avoid having to handle the NamingException
 * exceptions that most of the methods in SearchResult throw.
 *
 */
public class CustomSearchResult implements Comparable<CustomSearchResult>
{
  private String dn;
  private Map<String, List<Object>> attributes;
  private SortedSet<String> attrNames;
  private String toString;
  private int hashCode;

  /**
   * Constructor of an empty search result.  This constructor is used by the
   * LDAP entry editor which 'build' their own CustomSearchResult.  The entry
   * editors use some methods that require CustomSearchResult.
   * @param dn the dn of the entry.
   */
  public CustomSearchResult(String dn)
  {
    this.dn = dn;
    attributes = new HashMap<String, List<Object>>();
    attrNames = new TreeSet<String>();
    toString = calculateToString();
    hashCode = calculateHashCode();
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
    Name name;
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

    attributes = new HashMap<String, List<Object>>();
    attrNames = new TreeSet<String>();
    Attributes attrs = sr.getAttributes();
    if (attrs != null)
    {
      NamingEnumeration<?> en = attrs.getAll();
      try
      {
        while (en.hasMore()) {
          Attribute attr = (Attribute)en.next();
          String attrName = attr.getID();
          attrNames.add(attrName);
          List<Object> values = new ArrayList<Object>();
          for (int i=0; i<attr.size(); i++)
          {
            Object v = attr.get(i);
            if (!"".equals(v.toString()))
            {
              values.add(v);
            }
          }
          attributes.put(attrName.toLowerCase(), values);
        }
      }
      finally
      {
        en.close();
      }
    }
    toString = calculateToString();
    hashCode = calculateHashCode();
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
  public List<Object> getAttributeValues(String name) {
    List<Object> values = attributes.get(name.toLowerCase());
    if (values == null)
    {
      values = Collections.emptyList();
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
  public int compareTo(CustomSearchResult o) {
    int compareTo;
    if (this.equals(o))
    {
      compareTo = 0;
    }
    else
    {
      compareTo = toString().compareTo(o.toString());
    }
    return compareTo;
  }

  /**
   * {@inheritDoc}
   */
  public CustomSearchResult duplicate()
  {
    CustomSearchResult sr = new CustomSearchResult(dn);
    sr.attributes = new HashMap<String, List<Object>>(attributes);
    sr.attrNames = new TreeSet<String>(attrNames);
    sr.toString = toString;
    sr.hashCode = hashCode;
    return sr;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = false;
    if (o != null)
    {
      equals = o == this;
      if (!equals && (o instanceof CustomSearchResult))
      {
        CustomSearchResult sr = (CustomSearchResult)o;
        equals = getDN().equals(sr.getDN());
        if (equals)
        {
          equals = getAttributeNames().equals(sr.getAttributeNames());
          if (equals)
          {
            for (String attrName : getAttributeNames())
            {
              equals = getAttributeValues(attrName).equals(
                  sr.getAttributeValues(attrName));
              if (!equals)
              {
                break;
              }
            }
          }
        }
      }
    }
    return equals;
  }

  /**
   * {@inheritDoc}
   */
  public String toString() {
    return toString;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode() {
    return hashCode;
  }

  /**
   * Sets the values for a given attribute name.
   * @param attrName the name of the attribute.
   * @param values the values for the attribute.
   */
  public void set(String attrName, List<Object> values)
  {
    attrNames.add(attrName);
    attrName = attrName.toLowerCase();
    attributes.put(attrName, values);
    toString = calculateToString();
    hashCode = calculateHashCode();
  }

  private String calculateToString()
  {
    return "dn: "+dn+"\nattributes: "+attributes;
  }

  private int calculateHashCode()
  {
    return 23 + toString.hashCode();
  }

  /**
   * Gets the Entry object equivalent to this CustomSearchResult.
   * The method assumes that the schema in DirectoryServer has been initialized.
   * @return the Entry object equivalent to this CustomSearchResult.
   * @throws OpenDsException if there is an error parsing the DN or retrieving
   * the attributes definition and objectclasses in the schema of the server.
   */
  public Entry getEntry() throws OpenDsException
  {
    DN dn = DN.decode(this.getDN());
    Map<ObjectClass,String> objectClasses = new HashMap<ObjectClass,String>();
    Map<AttributeType,List<org.opends.server.types.Attribute>> userAttributes =
      new HashMap<AttributeType,List<org.opends.server.types.Attribute>>();
    Map<AttributeType,List<org.opends.server.types.Attribute>>
    operationalAttributes =
      new HashMap<AttributeType,List<org.opends.server.types.Attribute>>();

    for (String wholeName : this.getAttributeNames())
    {
      final org.opends.server.types.Attribute attribute =
        LDIFReader.parseAttrDescription(wholeName);
      final String attrName = attribute.getName();
      final String lowerName = toLowerCase(attrName);

      // See if this is an objectclass or an attribute.  Then get the
      // corresponding definition and add the value to the appropriate hash.
      if (lowerName.equals("objectclass"))
      {
        for (Object value : this.getAttributeValues(attrName))
        {
          String ocName = value.toString().trim();
          String lowerOCName = toLowerCase(ocName);

          ObjectClass objectClass =
            DirectoryServer.getObjectClass(lowerOCName);
          if (objectClass == null)
          {
            objectClass = DirectoryServer.getDefaultObjectClass(ocName);
          }

          objectClasses.put(objectClass, ocName);
        }
      }
      else
      {
        AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attrName);
        }

        AttributeBuilder builder = new AttributeBuilder(attribute, true);
        for (Object value : this.getAttributeValues(attrName))
        {
          ByteString bs;
          if (value instanceof byte[])
          {
            bs = ByteString.wrap((byte[])value);
          }
          else
          {
            bs = ByteString.valueOf(value.toString());
          }
          AttributeValue attributeValue =
            AttributeValues.create(attrType, bs);
          builder.add(attributeValue);
        }
        List<org.opends.server.types.Attribute> attrList =
          new ArrayList<org.opends.server.types.Attribute>(1);
        attrList.add(builder.toAttribute());

        if (attrType.isOperational())
        {
          operationalAttributes.put(attrType, attrList);
        }
        else
        {
          userAttributes.put(attrType, attrList);
        }
      }
    }

    return new Entry(dn, objectClasses, userAttributes, operationalAttributes);
  }
}
