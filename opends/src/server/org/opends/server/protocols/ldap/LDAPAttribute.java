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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Set;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;




/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP attribute, which is the basic unit of information in an LDAP
 * entry.
 */
public class LDAPAttribute
{
  /**
   * The fully-qualified name of this class to use for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.LDAPAttribute";



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
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType));

    this.attributeType = attributeType;

    values = new ArrayList<ASN1OctetString>(0);
  }



  /**
   * Creates a new LDAP attribute with the provided type and values.
   *
   * @param  attributeType  The attribute type for this attribute.
   * @param  values         The set of values for this attribute.
   */
  public LDAPAttribute(String attributeType, ArrayList<ASN1OctetString> values)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType));

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
    assert debugConstructor(CLASS_NAME, String.valueOf(attribute));

    StringBuilder attrName = new StringBuilder(attribute.getName());
    for (String o : attribute.getOptions())
    {
      attrName.append(";");
      attrName.append(o);
    }

    this.attributeType = attrName.toString();

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
    assert debugEnter(CLASS_NAME, "getAttributeType");

    return attributeType;
  }



  /**
   * Specifies the attribute type for this attribute.
   *
   * @param  attributeType  The attribute type for this attribute.
   */
  public void setAttributeType(String attributeType)
  {
    assert debugEnter(CLASS_NAME, "setAttributeType",
                      String.valueOf(attributeType));

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
    assert debugEnter(CLASS_NAME, "getValues");

    return values;
  }



  /**
   * Encodes this attribute to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded attribute.
   */
  public ASN1Element encode()
  {
    assert debugEnter(CLASS_NAME, "encode");

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1OctetString(attributeType));

    if ((values == null) || values.isEmpty())
    {
      elements.add(new ASN1Set());
    }
    else
    {
      ArrayList<ASN1Element> valueElements =
           new ArrayList<ASN1Element>(values.size());
      for (ASN1OctetString s : values)
      {
        valueElements.add(s);
      }

      elements.add(new ASN1Set(valueElements));
    }

    return new ASN1Sequence(elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP attribute.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP attribute.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 element as an LDAP attribute.
   */
  public static LDAPAttribute decode(ASN1Element element)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decode", String.valueOf(element));

    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decode", e);

      int msgID = MSGID_LDAP_ATTRIBUTE_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 2)
    {
      int msgID = MSGID_LDAP_ATTRIBUTE_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    String attributeType;
    try
    {
      attributeType = elements.get(0).decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decode", e);

      int msgID = MSGID_LDAP_ATTRIBUTE_DECODE_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ArrayList<ASN1OctetString> values;
    try
    {
      ArrayList<ASN1Element> valueElements =
           elements.get(1).decodeAsSet().elements();
      values = new ArrayList<ASN1OctetString>(valueElements.size());
      for (ASN1Element e : valueElements)
      {
        values.add(e.decodeAsOctetString());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decode", e);

      int msgID = MSGID_LDAP_ATTRIBUTE_DECODE_VALUES;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }

    return new LDAPAttribute(attributeType, values);
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
    assert debugEnter(CLASS_NAME, "toAttribute");

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
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder",
                      String.valueOf(indent));

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

