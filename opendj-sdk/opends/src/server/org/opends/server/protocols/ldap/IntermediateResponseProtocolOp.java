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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the structures and methods for an LDAP intermediate
 * response protocol op, which is used to provide information to a client before
 * the final response for an operation.
 */
public class IntermediateResponseProtocolOp
       extends ProtocolOp
{



  // The value for this intermediate response.
  private ASN1OctetString value;

  // The OID for this intermediate response.
  private String oid;



  /**
   * Creates a new intermediate protocol op with the specified OID and no
   * value.
   *
   * @param  oid  The OID for this intermediate response.
   */
  public IntermediateResponseProtocolOp(String oid)
  {

    this.oid   = oid;
    this.value = null;
  }



  /**
   * Creates a new intermediate response protocol op with the specified OID and
   * value.
   *
   * @param  oid    The OID for this intermediate response.
   * @param  value  The value for this intermediate response.
   */
  public IntermediateResponseProtocolOp(String oid, ASN1OctetString value)
  {

    this.oid   = oid;
    this.value = value;
  }



  /**
   * Retrieves the OID for this intermediate response.
   *
   * @return  The OID for this intermediate response, or <CODE>null</CODE> if
   *          there is no OID.
   */
  public String getOID()
  {

    return oid;
  }



  /**
   * Specifies the OID for this intermediate response.
   *
   * @param  oid  The OID for this intermediate response.
   */
  public void setOID(String oid)
  {

    this.oid = oid;
  }



  /**
   * Retrieves the value for this intermediate response.
   *
   * @return  The value for this intermediate response, or <CODE>null</CODE> if
   *          there is no value.
   */
  public ASN1OctetString getValue()
  {

    return value;
  }



  /**
   * Specifies the value for this intermediate response.
   *
   * @param  value  The value for this intermediate response.
   */
  public void setValue(ASN1OctetString value)
  {

    this.value = value;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {

    return OP_TYPE_INTERMEDIATE_RESPONSE;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {

    return "Intermediate Response";
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

    if (oid != null)
    {
      elements.add(new ASN1OctetString(TYPE_INTERMEDIATE_RESPONSE_OID, oid));
    }

    if (value != null)
    {
      value.setType(TYPE_INTERMEDIATE_RESPONSE_VALUE);
      elements.add(value);
    }

    return new ASN1Sequence(OP_TYPE_INTERMEDIATE_RESPONSE, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP intermediate response
   * protocol op.
   *
   * @param  element  The ASN.1 element to be decoded.
   *
   * @return  The decoded intermediate response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP intermediate
   *                         response protocol op.
   */
  public static IntermediateResponseProtocolOp
                     decodeIntermediateResponse(ASN1Element element)
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
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements > 2)
    {
      int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    String          oid   = null;
    ASN1OctetString value = null;

    if (elements.size() == 1)
    {
      ASN1Element e = elements.get(0);

      switch (e.getType())
      {
        case TYPE_INTERMEDIATE_RESPONSE_OID:
          try
          {
            oid = e.decodeAsOctetString().stringValue();
          }
          catch (ASN1Exception ae)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, ae);
            }

            int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_OID;
            String message = getMessage(msgID, ae.getMessage());
            throw new LDAPException(PROTOCOL_ERROR, msgID, message);
          }
          break;
        case TYPE_INTERMEDIATE_RESPONSE_VALUE:
          try
          {
            value = e.decodeAsOctetString();
          }
          catch (ASN1Exception ae)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, ae);
            }

            int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_VALUE;
            String message = getMessage(msgID, ae.getMessage());
            throw new LDAPException(PROTOCOL_ERROR, msgID, message);
          }
          break;
        default:
          int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_INVALID_ELEMENT_TYPE;
          String message = getMessage(msgID, byteToHex(e.getType()));
          throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      }
    }
    else if (elements.size() == 2)
    {
      try
      {
        oid = elements.get(0).decodeAsOctetString().stringValue();
      }
      catch (ASN1Exception ae)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, ae);
        }

        int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_OID;
        String message = getMessage(msgID, ae.getMessage());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      }

      try
      {
        value = elements.get(1).decodeAsOctetString();
      }
      catch (ASN1Exception ae)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, ae);
        }

        int msgID = MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_OID;
        String message = getMessage(msgID, ae.getMessage());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      }
    }


    return new IntermediateResponseProtocolOp(oid, value);
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {

    buffer.append("IntermediateResponse(oid=");
    buffer.append(String.valueOf(oid));

    if (value != null)
    {
      buffer.append(", value=");
      value.toString(buffer);
    }

    buffer.append(")");
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
    buffer.append("Intermediate Response");
    buffer.append(EOL);

    if (oid != null)
    {
      buffer.append(indentBuf);
      buffer.append("  OID:  ");
      buffer.append(oid);
      buffer.append(EOL);
    }

    if (value != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Value:");
      buffer.append(EOL);
      value.toString(buffer, indent+4);
    }
  }
}

