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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.types.*;


/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP attribute, which is the basic unit of information in an LDAP
 * entry.
 */
public class LDAPAttribute
       extends RawAttribute
{
  // The set of values for this attribute.
  private ArrayList<ByteString> values;

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

    values = new ArrayList<ByteString>(0);
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

    values = new ArrayList<ByteString>(1);
    values.add(ByteString.valueOf(value));
  }



  /**
   * Creates a new LDAP attribute with the provided type and no values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  value          The value to use for this attribute.
   */
  public LDAPAttribute(String attributeType, ByteString value)
  {
    this.attributeType = attributeType;

    values = new ArrayList<ByteString>(1);
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
      this.values = new ArrayList<ByteString>(0);
    }
    else
    {
      this.values = new ArrayList<ByteString>(values.size());
      for (String value : values)
      {
        this.values.add(ByteString.valueOf(value));
      }
    }
  }



  /**
   * Creates a new LDAP attribute with the provided type and values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  values         The set of values for this attribute.
   */
  public LDAPAttribute(String attributeType, ArrayList<ByteString> values)
  {
    this.attributeType = attributeType;

    if (values == null)
    {
      this.values = new ArrayList<ByteString>(0);
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

    if (attribute.isEmpty())
    {
      values = new ArrayList<ByteString>(0);
      return;
    }

    values = new ArrayList<ByteString>(attribute.size());
    for (AttributeValue v : attribute)
    {
      values.add(v.getValue());
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
  public ArrayList<ByteString> getValues()
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
    AttributeBuilder builder;
    int semicolonPos = attributeType.indexOf(';');
    if (semicolonPos > 0)
    {
      builder = new AttributeBuilder(attributeType.substring(0, semicolonPos));
      int nextPos = attributeType.indexOf(';', semicolonPos+1);
      while (nextPos > 0)
      {
        String option = attributeType.substring(semicolonPos+1, nextPos);
        if (option.length() > 0)
        {
          builder.setOption(option);
        }

        semicolonPos = nextPos;
        nextPos = attributeType.indexOf(';', semicolonPos+1);
      }

      String option = attributeType.substring(semicolonPos+1);
      if (option.length() > 0)
      {
        builder.setOption(option);
      }
    }
    else
    {
      builder = new AttributeBuilder(attributeType);
    }

    AttributeType attrType = builder.getAttributeType();
    for (ByteString value : values)
    {
      if (!builder.add(
          AttributeValues.create(attrType, value)))
      {
        Message message =
            ERR_LDAP_ATTRIBUTE_DUPLICATE_VALUES.get(attributeType);
        throw new LDAPException(
                LDAPResultCode.ATTRIBUTE_OR_VALUE_EXISTS, message);
      }
    }

    return builder.toAttribute();
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
      Iterator<ByteString> iterator = values.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
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

    for (ByteString value : values)
    {
      value.toHexPlusAscii(buffer, indent+4);
    }
  }
}

