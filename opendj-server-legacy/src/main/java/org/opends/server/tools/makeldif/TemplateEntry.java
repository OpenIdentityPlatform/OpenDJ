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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;

import static org.opends.server.util.LDIFWriter.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.LDIFException;

/**
 * This class defines an entry that is generated using a MakeLDIF branch or
 * template.
 */
public class TemplateEntry
{
  /** The branch used to generate this entry (if it is associated with a branch). */
  private final Branch branch;
  /** The DN for this template entry, if it is known. */
  private DN dn;
  /** The DN of the parent entry for this template entry, if it is available. */
  private final DN parentDN;

  /**
   * The set of attributes associated with this template entry, mapped from the
   * lowercase name of the attribute to the list of generated values.
   */
  private final LinkedHashMap<AttributeType, ArrayList<TemplateValue>> attributes = new LinkedHashMap<>();

  /** The template used to generate this entry (if it is associated with a template). */
  private final Template template;


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
    template = null;
    parentDN = null;
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
    this.branch = null;
    dn = null;
    this.template = template;
    this.parentDN = parentDN;
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
      AttributeType[] rdnAttrs = template.getRDNAttributes();
      AVA[] avas = new AVA[rdnAttrs.length];
      for (int i = 0; i < rdnAttrs.length; i++)
      {
        AttributeType t = rdnAttrs[i];
        TemplateValue v = getValue(t);
        if (v == null)
        {
          return null;
        }
        avas[i] = new AVA(t, v.getValue());
      }

      dn = parentDN.child(new RDN(avas));
    }

    return dn;
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
    if (valueList != null && !valueList.isEmpty())
    {
      return valueList.get(0);
    }
    return null;
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
    return attributes.get(attributeType);
  }



  /**
   * Adds the provided template value to this entry.
   *
   * @param  value  The value to add to this entry.
   */
  public void addValue(TemplateValue value)
  {
    ArrayList<TemplateValue> valueList = attributes.get(value.getAttributeType());
    if (valueList == null)
    {
      valueList = new ArrayList<>();
      attributes.put(value.getAttributeType(), valueList);
    }
    valueList.add(value);
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
    // Process all of the attributes for this entry.
    LinkedHashMap<ObjectClass,String> objectClasses = new LinkedHashMap<>();
    LinkedHashMap<AttributeType,List<Attribute>> userAttributes = new LinkedHashMap<>();
    LinkedHashMap<AttributeType,List<Attribute>> operationalAttributes = new LinkedHashMap<>();
    LinkedHashMap<AttributeType, List<Attribute>> urlAttributes = new LinkedHashMap<>();
    LinkedHashMap<AttributeType, List<Attribute>> base64Attributes = new LinkedHashMap<>();

    for (AttributeType t : attributes.keySet())
    {
      ArrayList<TemplateValue> valueList = attributes.get(t);
      if (t.isObjectClass())
      {
        for (TemplateValue v : valueList)
        {
          String ocName = v.getValue().toString();
          objectClasses.put(DirectoryServer.getSchema().getObjectClass(ocName), ocName);
        }
      }
      else if (t.isOperational())
      {
        AttributeBuilder builder = new AttributeBuilder(t);
        for (TemplateValue v : valueList)
        {
          builder.add(v.getValue().toString());
        }

        operationalAttributes.put(t, builder.toAttributeList());
      }
      else
      {
        AttributeBuilder builder = new AttributeBuilder(t);
        AttributeBuilder urlBuilder = null;
        AttributeBuilder base64Builder = null;
        for (TemplateValue v : valueList)
        {
          ByteString value = ByteString.valueOfUtf8(v.getValue().toString());
          builder.add(value);
          if (v.getTemplateLine().isURL())
          {
            if (urlBuilder == null)
            {
              urlBuilder = new AttributeBuilder(t);
            }
            urlBuilder.add(value);
          }
          else if (v.getTemplateLine().isBase64())
          {
            if (base64Builder == null)
            {
              base64Builder = new AttributeBuilder(t);
            }
            base64Builder.add(value);
          }
        }

        userAttributes.put(t, builder.toAttributeList());
        if (urlBuilder != null)
        {
          urlAttributes.put(t, urlBuilder.toAttributeList());
        }
        if (base64Builder != null)
        {
          base64Attributes.put(t, base64Builder.toAttributeList());
        }
      }
    }

    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // First, write the DN.  It will always be included.
    StringBuilder dnLine = new StringBuilder("dn");
    appendLDIFSeparatorAndValue(dnLine,
        ByteString.valueOfUtf8(getDN().toString()));
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
          StringBuilder ocLine = new StringBuilder("objectClass: ").append(s);
          writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
        }
      }
    }


    // Now the set of user attributes.
    for (AttributeType attrType : userAttributes.keySet())
    {
      if (exportConfig.includeAttribute(attrType))
      {
        for (Attribute a : userAttributes.get(attrType))
        {
          if (a.isVirtual() && !exportConfig.includeVirtualAttributes())
          {
            continue;
          }

          String attrName = a.getAttributeDescription().toString();
          if (typesOnly)
          {
            StringBuilder attrLine = new StringBuilder(attrName);
            attrLine.append(":");

            writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
          }
          else
          {
            List<Attribute> urlAttrList = urlAttributes.get(attrType);
            List<Attribute> base64AttrList = base64Attributes.get(attrType);

            for (ByteString v : a)
            {
              StringBuilder attrLine = new StringBuilder(attrName);
              boolean isURLValue = contains(urlAttrList, v);
              boolean isBase64Value = contains(base64AttrList, v);
              appendLDIFSeparatorAndValue(attrLine,
                                          v,
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
          for (Attribute a : operationalAttributes.get(attrType))
          {
            if (a.isVirtual() && !exportConfig.includeVirtualAttributes())
            {
              continue;
            }

            String attrName = a.getAttributeDescription().toString();
            if (typesOnly)
            {
              StringBuilder attrLine = new StringBuilder(attrName);
              attrLine.append(":");

              writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
            }
            else
            {
              for (ByteString v : a)
              {
                StringBuilder attrLine = new StringBuilder(attrName);
                appendLDIFSeparatorAndValue(attrLine, v);
                writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
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

  private boolean contains(List<Attribute> urlAttrList, ByteString v)
  {
    if (urlAttrList != null)
    {
      for (Attribute urlAttr : urlAttrList)
      {
        for (ByteString urlValue : urlAttr)
        {
          if (urlValue.equals(v))
          {
            return true;
          }
        }
      }
    }
    return false;
  }
}
