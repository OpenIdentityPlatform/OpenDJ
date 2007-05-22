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
package org.opends.server.protocols.ldap;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawAttribute;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;




/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP attribute, which is the basic unit of information in an LDAP
 * entry.
 */
public class LDAPAttribute
       extends RawAttribute
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of values for this attribute.
  private ArrayList<ASN1OctetString> values;

  // The attribute type for this attribute.
  private String attributeType;



  /**
   * Creates a new LDAP attribute with the provided type and no values.
   *
   * @param  attributeType  The attribute type for this attribute.
   */
  public LDAPAttribute(String attributeType)
  {
    this.attributeType = attributeType;

    values = new ArrayList<ASN1OctetString>(0);
  }



  /**
   * Creates a new LDAP attribute with the provided type and no values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  value          The value to use for this attribute.
   */
  public LDAPAttribute(String attributeType, String value)
  {
    this.attributeType = attributeType;

    values = new ArrayList<ASN1OctetString>(1);
    values.add(new ASN1OctetString(value));
  }



  /**
   * Creates a new LDAP attribute with the provided type and no values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  value          The value to use for this attribute.
   */
  public LDAPAttribute(String attributeType, ASN1OctetString value)
  {
    this.attributeType = attributeType;

    values = new ArrayList<ASN1OctetString>(1);
    values.add(value);
  }



  /**
   * Creates a new LDAP attribute with the provided type and values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  values         The set of values for this attribute.
   */
  public LDAPAttribute(String attributeType, List<String> values)
  {
    this.attributeType = attributeType;

    if (values == null)
    {
      this.values = new ArrayList<ASN1OctetString>(0);
    }
    else
    {
      this.values = new ArrayList<ASN1OctetString>(values.size());
      for (String value : values)
      {
        this.values.add(new ASN1OctetString(value));
      }
    }
  }



  /**
   * Creates a new LDAP attribute with the provided type and values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  values         The set of values for this attribute.
   */
  public LDAPAttribute(String attributeType, ArrayList<ASN1OctetString> values)
  {
    this.attributeType = attributeType;

    if (values == null)
    {
      this.values = new ArrayList<ASN1OctetString>(0);
    }
    else
    {
      this.values = values;
    }
  }



  /**
   * Creates a new LDAP attribute from the provided attribute.
   *
   * @param  attribute  The attribute to use to create this LDAP attribute.
   */
  public LDAPAttribute(Attribute attribute)
  {
    if (attribute.hasOptions())
    {
      StringBuilder attrName = new StringBuilder(attribute.getName());
      for (String o : attribute.getOptions())
      {
        attrName.append(";");
        attrName.append(o);
      }

      this.attributeType = attrName.toString();
    }
    else
    {
      this.attributeType = attribute.getName();
    }

    LinkedHashSet<AttributeValue> attrValues = attribute.getValues();
    if ((attrValues == null) || attrValues.isEmpty())
    {
      values = new ArrayList<ASN1OctetString>(0);
      return;
    }

    values = new ArrayList<ASN1OctetString>(attrValues.size());
    for (AttributeValue v : attrValues)
    {
      values.add(v.getValue().toASN1OctetString());
    }
  }



  /**
   * Retrieves the attribute type for this attribute.
   *
   * @return  The attribute type for this attribute.
   */
  public String getAttributeType()
  {
    return attributeType;
  }



  /**
   * Specifies the attribute type for this attribute.
   *
   * @param  attributeType  The attribute type for this attribute.
   */
  public void setAttributeType(String attributeType)
  {
    this.attributeType = attributeType;
  }



  /**
   * Retrieves the set of values for this attribute.  The returned list may be
   * modified by the caller.
   *
   * @return  The set of values for this attribute.
   */
  public ArrayList<ASN1OctetString> getValues()
  {
    return values;
  }



  /**
   * Retrieves a core attribute containing the information for this LDAP
   * attribute.
   *
   * @return  A core attribute containing the information for this LDAP
   *          attribute.
   *
   * @throws  LDAPException  If the provided value is invalid according to the
   *                         attribute syntax.
   */
  public Attribute toAttribute()
         throws LDAPException
  {
    String baseName;
    String lowerBaseName;
    int    semicolonPos = attributeType.indexOf(';');
    LinkedHashSet<String> options;
    if (semicolonPos > 0)
    {
      baseName = attributeType.substring(0, semicolonPos);
      options = new LinkedHashSet<String>();
      int nextPos = attributeType.indexOf(';', semicolonPos+1);
      while (nextPos > 0)
      {
        String option = attributeType.substring(semicolonPos+1, nextPos);
        if (option.length() > 0)
        {
          options.add(option);
        }

        semicolonPos = nextPos;
        nextPos = attributeType.indexOf(';', semicolonPos+1);
      }

      String option = attributeType.substring(semicolonPos+1);
      if (option.length() > 0)
      {
        options.add(option);
      }
    }
    else
    {
      baseName = attributeType;
      options = new LinkedHashSet<String>(0);
    }
    lowerBaseName = toLowerCase(baseName);

    AttributeType attrType = DirectoryServer.getAttributeType(lowerBaseName);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(lowerBaseName);
    }


    LinkedHashSet<AttributeValue> attributeValues =
         new LinkedHashSet<AttributeValue>(values.size());
    for (ASN1OctetString value : values)
    {
      if (! attributeValues.add(new AttributeValue(attrType, value)))
      {
        int    msgID   = MSGID_LDAP_ATTRIBUTE_DUPLICATE_VALUES;
        String message = getMessage(msgID, attributeType);
        throw new LDAPException(LDAPResultCode.ATTRIBUTE_OR_VALUE_EXISTS, msgID,
                                message);
      }
    }


    return new Attribute(attrType, baseName, options, attributeValues);
  }



  /**
   * Retrieves a string representation of this attribute.
   *
   * @return  A string representation of this attribute.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this attribute to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPAttribute(type=");
    buffer.append(attributeType);
    buffer.append(", values={");

    if (! values.isEmpty())
    {
      Iterator<ASN1OctetString> iterator = values.iterator();
      iterator.next().toString(buffer);
      while (iterator.hasNext())
      {
        buffer.append(", ");
        iterator.next().toString(buffer);
      }
    }

    buffer.append("})");
  }



  /**
   * Appends a multi-line string representation of this LDAP attribute to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("LDAP Attribute");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Attribute Type:  ");
    buffer.append(attributeType);
    buffer.append(EOL);

    buffer.append("  Attribute Values:");
    buffer.append(EOL);

    for (ASN1OctetString value : values)
    {
      value.toString(buffer, indent+4);
    }
  }
}

