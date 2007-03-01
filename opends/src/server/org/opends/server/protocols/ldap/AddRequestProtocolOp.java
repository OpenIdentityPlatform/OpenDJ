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
import java.util.List;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.util.Base64;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This class defines the structures and methods for an LDAP add request
 * protocol op, which is used to add a new entry to the Directory Server.
 */
public class AddRequestProtocolOp
       extends ProtocolOp
{



  // The set of attributes for this add request.
  private List<LDAPAttribute> attributes;

  // The DN for this add request.
  private ASN1OctetString dn;



  /**
   * Creates a new LDAP add request protocol op with the specified DN and no
   * attributes.
   *
   * @param  dn  The DN for this add request.
   */
  public AddRequestProtocolOp(ASN1OctetString dn)
  {
    this.dn         = dn;
    this.attributes = new ArrayList<LDAPAttribute>();
  }



  /**
   * Creates a new LDAP add request protocol op with the specified DN and set of
   * attributes.
   *
   * @param  dn          The DN for this add request.
   * @param  attributes  The set of attributes for this add request.
   */
  public AddRequestProtocolOp(ASN1OctetString dn,
                              ArrayList<LDAPAttribute> attributes)
  {
    this.dn = dn;

    if (attributes == null)
    {
      this.attributes = new ArrayList<LDAPAttribute>();
    }
    else
    {
      this.attributes = attributes;
    }
  }



  /**
   * Retrieves the DN for this add request.
   *
   * @return  The DN for this add request.
   */
  public ASN1OctetString getDN()
  {
    return dn;
  }



  /**
   * Specifies the DN for this add request.
   *
   * @param  dn  The DN for this add request.
   */
  public void setDN(ASN1OctetString dn)
  {
    this.dn = dn;
  }



  /**
   * Retrieves the set of attributes for this add request.  The returned list
   * may be altered by the caller.
   *
   * @return  The set of attributes for this add request.
   */
  public List<LDAPAttribute> getAttributes()
  {
    return attributes;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_ADD_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Add Request";
  }



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public ASN1Element encode()
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(dn);


    ArrayList<ASN1Element> attrElements =
         new ArrayList<ASN1Element>(attributes.size());
    for (LDAPAttribute attr : attributes)
    {
      attrElements.add(attr.encode());
    }
    elements.add(new ASN1Sequence(attrElements));


    return new ASN1Sequence(OP_TYPE_ADD_REQUEST, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP add request protocol op.
   *
   * @param  element  The ASN.1 element to be decoded.
   *
   * @return  The decoded add request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP add request protocol op.
   */
  public static AddRequestProtocolOp decodeAddRequest(ASN1Element element)
         throws LDAPException
  {
    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_ADD_REQUEST_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 2)
    {
      int    msgID   = MSGID_LDAP_ADD_REQUEST_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    ASN1OctetString dn;
    try
    {
      dn = elements.get(0).decodeAsOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_ADD_REQUEST_DECODE_DN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }



    ArrayList<LDAPAttribute> attributes;
    try
    {
      ArrayList<ASN1Element> attrElements =
           elements.get(1).decodeAsSequence().elements();
      attributes = new ArrayList<LDAPAttribute>(attrElements.size());
      for (ASN1Element e : attrElements)
      {
        attributes.add(LDAPAttribute.decode(e));
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_ADD_REQUEST_DECODE_ATTRS;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new AddRequestProtocolOp(dn, attributes);
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AddRequest(dn=");
    dn.toString(buffer);
    buffer.append(", attrs={");

    if (! attributes.isEmpty())
    {
      Iterator<LDAPAttribute> iterator = attributes.iterator();
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
   * Appends a multi-line string representation of this LDAP protocol op to the
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
    buffer.append("Add Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  DN:  ");
    dn.toString(buffer);
    buffer.append(EOL);

    buffer.append("  Attributes:");
    buffer.append(EOL);

    for (LDAPAttribute attribute : attributes)
    {
      attribute.toString(buffer, indent+4);
    }
  }



  /**
   * Appends an LDIF representation of the entry to the provided buffer.
   *
   * @param  buffer      The buffer to which the entry should be appended.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   */
  public void toLDIF(StringBuilder buffer, int wrapColumn)
  {
    // Add the DN to the buffer.
    String dnString;
    int    colsRemaining;
    if (needsBase64Encoding(dn.value()))
    {
      dnString = Base64.encode(dn.value());
      buffer.append("dn:: ");

      colsRemaining = wrapColumn - 5;
    }
    else
    {
      dnString = dn.stringValue();
      buffer.append("dn: ");

      colsRemaining = wrapColumn - 4;
    }

    int dnLength = dnString.length();
    if ((dnLength <= colsRemaining) || (colsRemaining <= 0))
    {
      buffer.append(dnString);
      buffer.append(EOL);
    }
    else
    {
      buffer.append(dnString.substring(0, colsRemaining));
      buffer.append(EOL);

      int startPos = colsRemaining;
      while ((dnLength - startPos) > (wrapColumn - 1))
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos, (startPos+wrapColumn-1)));
        buffer.append(EOL);

        startPos += (wrapColumn-1);
      }

      if (startPos < dnLength)
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos));
        buffer.append(EOL);
      }
    }


    // Add the attributes to the buffer.
    for (LDAPAttribute a : attributes)
    {
      String name       = a.getAttributeType();
      int    nameLength = name.length();

      for (ASN1OctetString v : a.getValues())
      {
        String valueString;
        if (needsBase64Encoding(v.value()))
        {
          valueString = Base64.encode(v.value());
          buffer.append(name);
          buffer.append(":: ");

          colsRemaining = wrapColumn - nameLength - 3;
        }
        else
        {
          valueString = v.stringValue();
          buffer.append(name);
          buffer.append(": ");

          colsRemaining = wrapColumn - nameLength - 2;
        }

        int valueLength = valueString.length();
        if ((valueLength <= colsRemaining) || (colsRemaining <= 0))
        {
          buffer.append(valueString);
          buffer.append(EOL);
        }
        else
        {
          buffer.append(valueString.substring(0, colsRemaining));
          buffer.append(EOL);

          int startPos = colsRemaining;
          while ((valueLength - startPos) > (wrapColumn - 1))
          {
            buffer.append(" ");
            buffer.append(valueString.substring(startPos,
                                                (startPos+wrapColumn-1)));
            buffer.append(EOL);

            startPos += (wrapColumn-1);
          }

          if (startPos < valueLength)
          {
            buffer.append(" ");
            buffer.append(valueString.substring(startPos));
            buffer.append(EOL);
          }
        }
      }
    }


    // Make sure to add an extra blank line to ensure that there will be one
    // between this entry and the next.
    buffer.append(EOL);
  }
}

