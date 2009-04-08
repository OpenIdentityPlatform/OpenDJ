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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.LDIFException;

import static org.opends.server.util.LDIFWriter.appendLDIFSeparatorAndValue;
import static org.opends.server.util.LDIFWriter.writeLDIFLine;
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

        AttributeValue value =
            AttributeValues.create(t, v.getValue().toString());
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
          values[i] = AttributeValues.create(t, v.getValue().toString());
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
   * Writes this entry in LDIF form.  No filtering will be
   * performed for this entry, nor will any export plugins be invoked.
   *
   * @param  exportConfig  The configuration that specifies how the
   *                       entry should be written.
   *
   * @return  <CODE>true</CODE> if the entry is actually written, or
   *          <CODE>false</CODE> if it is not for some reason.
   *
   * @throws  IOException  If a problem occurs while writing the
   *                       information.
   *
   * @throws  LDIFException  If a problem occurs while trying to
   *                         determine whether to write the entry.
   */
  public boolean toLDIF(LDIFExportConfig exportConfig)
         throws IOException, LDIFException
  {
//  Process all of the attributes for this entry.
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>();
    LinkedHashMap<AttributeType,List<Attribute>> userAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();
    LinkedHashMap<AttributeType,List<Attribute>> operationalAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();
    LinkedHashMap<AttributeType, List<Attribute>> urlAttributes =
         new LinkedHashMap<AttributeType, List<Attribute>>();
    LinkedHashMap<AttributeType, List<Attribute>> base64Attributes =
      new LinkedHashMap<AttributeType, List<Attribute>>();

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
        AttributeBuilder builder = new AttributeBuilder(t, t.getNameOrOID());
        for (TemplateValue v : valueList)
        {
          builder.add(AttributeValues.create(t, v.getValue().toString()));
        }

        ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(builder.toAttribute());
        operationalAttributes.put(t, attrList);
      }
      else
      {
        AttributeBuilder builder = new AttributeBuilder(t, t.getNameOrOID());
        AttributeBuilder urlBuilder = null;
        AttributeBuilder base64Builder = null;
        for (TemplateValue v : valueList)
        {
          AttributeValue value =
            AttributeValues.create(t, v.getValue().toString());
          builder.add(value);
          if (v.getTemplateLine().isURL())
          {
            if (urlBuilder == null)
            {
              urlBuilder = new AttributeBuilder(t, t.getNameOrOID());
            }
            urlBuilder.add(value);
          }
          else if (v.getTemplateLine().isBase64())
          {
            if (base64Builder == null)
            {
              base64Builder = new AttributeBuilder(t, t.getNameOrOID());
            }
            base64Builder.add(value);
          }
        }

        ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(builder.toAttribute());
        userAttributes.put(t, attrList);

        if (urlBuilder != null)
        {
          ArrayList<Attribute> urlAttrList = new ArrayList<Attribute>(1);
          urlAttrList.add(urlBuilder.toAttribute());
          urlAttributes.put(t, urlAttrList);
        }

        if (base64Builder != null)
        {
          ArrayList<Attribute> base64AttrList = new ArrayList<Attribute>(1);
          base64AttrList.add(base64Builder.toAttribute());
          base64Attributes.put(t, base64AttrList);
        }
      }
    }

    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = (wrapColumn > 1);


    // First, write the DN.  It will always be included.
    StringBuilder dnLine = new StringBuilder();
    dnLine.append("dn");
    appendLDIFSeparatorAndValue(dnLine,
        ByteString.valueOf(getDN().toString()));
    writeLDIFLine(dnLine, writer, wrapLines, wrapColumn);


    // Next, the set of objectclasses.
    final boolean typesOnly = exportConfig.typesOnly();
    if (exportConfig.includeObjectClasses())
    {
      if (typesOnly)
      {
        StringBuilder ocLine = new StringBuilder("objectClass:");
        writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
      }
      else
      {
        for (String s : objectClasses.values())
        {
          StringBuilder ocLine = new StringBuilder();
          ocLine.append("objectClass: ");
          ocLine.append(s);
          writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
        }
      }
    }


    // Now the set of user attributes.
    for (AttributeType attrType : userAttributes.keySet())
    {
      if (exportConfig.includeAttribute(attrType))
      {
        List<Attribute> attrList = userAttributes.get(attrType);
        for (Attribute a : attrList)
        {
          if (a.isVirtual() &&
              (! exportConfig.includeVirtualAttributes()))
          {
            continue;
          }

          if (typesOnly)
          {
            StringBuilder attrName = new StringBuilder(a.getName());
            for (String o : a.getOptions())
            {
              attrName.append(";");
              attrName.append(o);
            }
            attrName.append(":");

            writeLDIFLine(attrName, writer, wrapLines, wrapColumn);
          }
          else
          {
            StringBuilder attrName = new StringBuilder(a.getName());
            for (String o : a.getOptions())
            {
              attrName.append(";");
              attrName.append(o);
            }

            List<Attribute> urlAttrList = urlAttributes.get(attrType);
            List<Attribute> base64AttrList = base64Attributes.get(attrType);

            for (AttributeValue v : a)
            {
              StringBuilder attrLine = new StringBuilder();
              attrLine.append(attrName);
              boolean isURLValue = false;
              if (urlAttrList != null)
              {
                for (Attribute urlAttr : urlAttrList)
                {
                  for (AttributeValue urlValue : urlAttr)
                  {
                    if (urlValue.equals(v))
                    {
                      isURLValue = true;
                      break;
                    }
                  }
                  if (isURLValue)
                  {
                    break;
                  }
                }
              }
              boolean isBase64Value = false;
              if (base64AttrList != null)
              {
                for (Attribute base64Attr : base64AttrList)
                {
                  for (AttributeValue base64Value : base64Attr)
                  {
                    if (base64Value.equals(v))
                    {
                      isBase64Value = true;
                      break;
                    }
                  }
                  if (isBase64Value)
                  {
                    break;
                  }
                }
              }
              appendLDIFSeparatorAndValue(attrLine,
                                          v.getValue(),
                                          isURLValue,
                                          isBase64Value);
              writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
            }
          }
        }
      }
    }


    // Next, the set of operational attributes.
    if (exportConfig.includeOperationalAttributes())
    {
      for (AttributeType attrType : operationalAttributes.keySet())
      {
        if (exportConfig.includeAttribute(attrType))
        {
          List<Attribute> attrList =
               operationalAttributes.get(attrType);
          for (Attribute a : attrList)
          {
            if (a.isVirtual() &&
                (! exportConfig.includeVirtualAttributes()))
            {
              continue;
            }

            if (typesOnly)
            {
              StringBuilder attrName = new StringBuilder(a.getName());
              for (String o : a.getOptions())
              {
                attrName.append(";");
                attrName.append(o);
              }
              attrName.append(":");

              writeLDIFLine(attrName, writer, wrapLines, wrapColumn);
            }
            else
            {
              StringBuilder attrName = new StringBuilder(a.getName());
              for (String o : a.getOptions())
              {
                attrName.append(";");
                attrName.append(o);
              }

              for (AttributeValue v : a)
              {
                StringBuilder attrLine = new StringBuilder();
                attrLine.append(attrName);
                appendLDIFSeparatorAndValue(attrLine,
                                            v.getValue());
                writeLDIFLine(attrLine, writer, wrapLines,
                              wrapColumn);
              }
            }
          }
        }
      }
    }

    // Make sure there is a blank line after the entry.
    writer.newLine();


    return true;
  }
}

