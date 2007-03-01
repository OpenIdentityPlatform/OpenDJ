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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an entry that is generated using a MakeLDIF branch or
 * template.
 */
public class TemplateEntry
{
  // The branch used to generate this entry (if it is associated with a branch).
  private Branch branch;

  // The DN for this template entry, if it is known.
  private DN dn;

  // The DN of the parent entry for this template entry, if it is available.
  private DN parentDN;

  // The set of attributes associated with this template entry, mapped from the
  // lowercase name of the attribute to the list of generated values.
  private LinkedHashMap<AttributeType,ArrayList<TemplateValue>> attributes;

  // The template used to generate this entry (if it is associated with a
  // template).
  private Template template;



  /**
   * Creates a new template entry that will be associated with the provided
   * branch.
   *
   * @param  branch  The branch to use when creating this template entry.
   */
  public TemplateEntry(Branch branch)
  {
    this.branch = branch;

    dn         = branch.getBranchDN();
    template   = null;
    parentDN   = null;
    attributes = new LinkedHashMap<AttributeType,ArrayList<TemplateValue>>();
  }



  /**
   * Creates a new template entry that will be associated with the provided
   * template.
   *
   * @param  template  The template used to generate this entry.
   * @param  parentDN  The DN of the parent entry for this template entry.
   */
  public TemplateEntry(Template template, DN parentDN)
  {
    this.template = template;
    this.parentDN = parentDN;

    dn         = null;
    branch     = null;
    attributes = new LinkedHashMap<AttributeType,ArrayList<TemplateValue>>();
  }



  /**
   * Retrieves the branch used to generate this entry.
   *
   * @return  The branch used to generate this entry, or <CODE>null</CODE> if it
   *          is associated with a template instead of a branch.
   */
  public Branch getBranch()
  {
    return branch;
  }



  /**
   * Retrieves the template used to generate this entry.
   *
   * @return  The template used to generate this entry, or <CODE>null</CODE> if
   *          it is associated with a branch instead of a template.
   */
  public Template getTemplate()
  {
    return template;
  }



  /**
   * Retrieves the DN of the parent entry for this template entry.
   *
   * @return  The DN of the parent entry for this template entry, or
   *          <CODE>null</CODE> if there is no parent DN.
   */
  public DN getParentDN()
  {
    return parentDN;
  }



  /**
   * Retrieves the DN for this template entry, if it is known.
   *
   * @return  The DN for this template entry if it is known, or
   *          <CODE>null</CODE> if it cannot yet be determined.
   */
  public DN getDN()
  {
    if (dn == null)
    {
      RDN rdn;
      AttributeType[] rdnAttrs = template.getRDNAttributes();
      if (rdnAttrs.length == 1)
      {
        AttributeType t = rdnAttrs[0];
        TemplateValue v = getValue(t);
        if (v == null)
        {
          return null;
        }

        AttributeValue value = new AttributeValue(t, v.getValue().toString());
        rdn = new RDN(t, value);
      }
      else
      {
        String[]         names  = new String[rdnAttrs.length];
        AttributeValue[] values = new AttributeValue[rdnAttrs.length];
        for (int i=0; i < rdnAttrs.length; i++)
        {
          AttributeType t = rdnAttrs[i];
          TemplateValue v = getValue(t);
          if (v == null)
          {
            return null;
          }

          names[i]  = t.getPrimaryName();
          values[i] = new AttributeValue(t, v.getValue().toString());
        }

        rdn = new RDN(rdnAttrs, names, values);
      }

      dn = parentDN.concat(rdn);
    }

    return dn;
  }



  /**
   * Indicates whether this entry contains one or more values for the specified
   * attribute type.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if this entry contains one or more values for
   *          the specified attribute type, or <CODE>false</CODE> if not.
   */
  public boolean hasAttribute(AttributeType attributeType)
  {
    return attributes.containsKey(attributeType);
  }



  /**
   * Retrieves the value for the specified attribute, if defined.  If the
   * specified attribute has multiple values, then the first will be returned.
   *
   * @param  attributeType  The attribute type for which to retrieve the value.
   *
   * @return  The value for the specified attribute, or <CODE>null</CODE> if
   *          there are no values for that attribute type.
   */
  public TemplateValue getValue(AttributeType attributeType)
  {
    ArrayList<TemplateValue> valueList = attributes.get(attributeType);
    if ((valueList == null) || valueList.isEmpty())
    {
      return null;
    }
    else
    {
      return valueList.get(0);
    }
  }



  /**
   * Retrieves the set of values for the specified attribute, if defined.
   *
   * @param  attributeType  The attribute type for which to retrieve the set of
   *                        values.
   *
   * @return  The set of values for the specified attribute, or
   *          <CODE>null</CODE> if there are no values for that attribute type.
   */
  public List<TemplateValue> getValues(AttributeType attributeType)
  {
    ArrayList<TemplateValue> valueList = attributes.get(attributeType);
    return valueList;
  }



  /**
   * Adds the provided template value to this entry.
   *
   * @param  value  The value to add to this entry.
   */
  public void addValue(TemplateValue value)
  {
    ArrayList<TemplateValue> valueList =
         attributes.get(value.getAttributeType());
    if (valueList == null)
    {
      valueList = new ArrayList<TemplateValue>();
      valueList.add(value);
      attributes.put(value.getAttributeType(), valueList);
    }
    else
    {
      valueList.add(value);
    }
  }



  /**
   * Retrieves this template entry as an <CODE>Entry</CODE> object.
   *
   * @return  The <CODE>Entry</CODE> object for this template entry.
   */
  public Entry toEntry()
  {
    // Process all of the attributes for this entry.
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>();
    LinkedHashMap<AttributeType,List<Attribute>> userAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();
    LinkedHashMap<AttributeType,List<Attribute>> operationalAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    for (AttributeType t : attributes.keySet())
    {
      ArrayList<TemplateValue> valueList = attributes.get(t);
      if (t.isObjectClassType())
      {
        for (TemplateValue v : valueList)
        {
          String ocName = toLowerCase(v.getValue().toString());
          ObjectClass oc = DirectoryServer.getObjectClass(ocName, true);
          objectClasses.put(oc, ocName);
        }
      }
      else if (t.isOperational())
      {
        LinkedHashSet<AttributeValue> values =
             new LinkedHashSet<AttributeValue>();
        for (TemplateValue v : valueList)
        {
          values.add(new AttributeValue(t, v.getValue().toString()));
        }

        ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(new Attribute(t, t.getNameOrOID(), values));
        operationalAttributes.put(t, attrList);
      }
      else
      {
        LinkedHashSet<AttributeValue> values =
             new LinkedHashSet<AttributeValue>();
        for (TemplateValue v : valueList)
        {
          values.add(new AttributeValue(t, v.getValue().toString()));
        }

        ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(new Attribute(t, t.getNameOrOID(), values));
        userAttributes.put(t, attrList);
      }
    }

    return new Entry(getDN(), objectClasses, userAttributes,
                     operationalAttributes);
  }
}

