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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingException;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;

/**
 * This is a commodity class used to wrap the SearchResult class of JNDI.
 * Basically it retrieves all the attributes and values on the SearchResult and
 * calculates its DN.  Using it we avoid having to handle the NamingException
 * exceptions that most of the methods in SearchResult throw.
 */
public class CustomSearchResult implements Comparable<CustomSearchResult>
{
  private final DN dn;
  private Map<String, List<ByteString>> attributes;
  private SortedSet<String> attrNames;
  private String toString;
  private int hashCode;

  /**
   * Constructor of an empty search result.  This constructor is used by the
   * LDAP entry editor which 'build' their own CustomSearchResult.  The entry
   * editors use some methods that require CustomSearchResult.
   * @param dn the dn of the entry.
   */
  public CustomSearchResult(DN dn)
  {
    this.dn = dn;
    attributes = new HashMap<>();
    attrNames = new TreeSet<>();
    toString = calculateToString();
    hashCode = calculateHashCode();
  }

  /**
   * Constructor of a search result using a SearchResult as basis.
   * @param sr the SearchResult.
   * @throws NamingException if there is an error retrieving the attribute values.
   */
  public CustomSearchResult(SearchResultEntry sr) throws NamingException
  {
    dn = sr.getName();

    attributes = new HashMap<>();
    attrNames = new TreeSet<>();
    for (org.forgerock.opendj.ldap.Attribute attr : sr.getAllAttributes())
    {
      String attrName = attr.getAttributeDescriptionAsString();
      attrNames.add(attrName);
      List<ByteString> values = new ArrayList<>();
      for (ByteString v : attr)
      {
        if (!"".equals(v.toString()))
        {
          values.add(v);
        }
      }
      attributes.put(attrName.toLowerCase(), values);
    }
    toString = calculateToString();
    hashCode = calculateHashCode();
  }

  /**
   * Returns the DN of the entry.
   * @return the DN of the entry.
   */
  public DN getDN()
  {
    return dn;
  }

  /**
   * Returns the values for a given attribute.  It returns an empty Set if
   * the attribute is not defined.
   * @param name the name of the attribute.
   * @return the values for a given attribute.  It returns an empty Set if
   * the attribute is not defined.
   */
  public List<ByteString> getAttributeValues(String name) {
    List<ByteString> values = attributes.get(name.toLowerCase());
    return values != null ? values : Collections.<ByteString> emptyList();
  }

  /**
   * Returns all the attribute names of the entry.
   * @return the attribute names of the entry.
   */
  public SortedSet<String> getAttributeNames() {
    return attrNames;
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
      CustomSearchResult sr = (CustomSearchResult)o;
      return getDN().equals(sr.getDN())
          && getAttributeNames().equals(sr.getAttributeNames())
          && attrValuesEqual(sr);
    }
    return false;
  }

  private boolean attrValuesEqual(CustomSearchResult sr)
  {
    for (String attrName : getAttributeNames())
    {
      if (!getAttributeValues(attrName).equals(sr.getAttributeValues(attrName)))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return toString;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  /**
   * Sets the values for a given attribute name.
   * @param attrName the name of the attribute.
   * @param values the values for the attribute.
   */
  public void set(String attrName, List<ByteString> values)
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
    Map<ObjectClass,String> objectClasses = new HashMap<>();
    Map<AttributeType,List<org.opends.server.types.Attribute>> userAttributes = new HashMap<>();
    Map<AttributeType,List<org.opends.server.types.Attribute>> operationalAttributes = new HashMap<>();

    for (String wholeName : getAttributeNames())
    {
      final AttributeDescription attrDesc = LDIFReader.parseAttrDescription(wholeName);
      final AttributeType attrType = attrDesc.getAttributeType();

      // See if this is an objectclass or an attribute.  Then get the
      // corresponding definition and add the value to the appropriate hash.
      if (attrType.isObjectClass())
      {
        for (ByteString value : getAttributeValues(attrType.getNameOrOID()))
        {
          String ocName = value.toString().trim();
          objectClasses.put(DirectoryServer.getSchema().getObjectClass(ocName), ocName);
        }
      }
      else
      {
        AttributeBuilder builder = new AttributeBuilder(attrDesc);
        for (ByteString bs : getAttributeValues(attrType.getNameOrOID()))
        {
          builder.add(bs);
        }

        List<org.opends.server.types.Attribute> attrList = builder.toAttributeList();
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
