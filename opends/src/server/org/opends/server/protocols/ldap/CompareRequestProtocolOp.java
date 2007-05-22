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

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the structures and methods for an LDAP compare request
 * protocol op, which is used to determine whether a particular entry contains
 * a specified attribute value.
 */
public class CompareRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The assertion value for this compare request.
  private ASN1OctetString assertionValue;

  // The DN for this compare request.
  private ASN1OctetString dn;

  // The attribute type for this compare request.
  private String attributeType;



  /**
   * Creates a new compare request protocol op with the provided information.
   *
   * @param  dn              The DN for this compare request.
   * @param  attributeType   The attribute type for this compare request.
   * @param  assertionValue  The assertion value for this compare request.
   */
  public CompareRequestProtocolOp(ASN1OctetString dn, String attributeType,
                                  ASN1OctetString assertionValue)
  {
    this.dn             = dn;
    this.attributeType  = attributeType;
    this.assertionValue = assertionValue;
  }



  /**
   * Retrieves the DN for this compare request.
   *
   * @return  The DN for this compare request.
   */
  public ASN1OctetString getDN()
  {
    return dn;
  }



  /**
   * Specifies the DN for this compare request.
   *
   * @param  dn  The DN for this compare request.
   */
  public void setDN(ASN1OctetString dn)
  {
    this.dn = dn;
  }



  /**
   * Retrieves the attribute type for this compare request.
   *
   * @return  The attribute type for this compare request.
   */
  public String getAttributeType()
  {
    return attributeType;
  }



  /**
   * Specifies the attribute type for this compare request.
   *
   * @param  attributeType  The attribute type for this compare request.
   */
  public void setAttributeType(String attributeType)
  {
    this.attributeType = attributeType;
  }



  /**
   * Retrieves the assertion value for this compare request.
   *
   * @return  The assertion value for this compare request.
   */
  public ASN1OctetString getAssertionValue()
  {
    return assertionValue;
  }



  /**
   * Specifies the assertion value for this compare request.
   *
   * @param  assertionValue  The assertion value for this compare request.
   */
  public void setAssertionValue(ASN1OctetString assertionValue)
  {
    this.assertionValue = assertionValue;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_COMPARE_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Compare Request";
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

    ArrayList<ASN1Element> avaElements = new ArrayList<ASN1Element>(2);
    avaElements.add(new ASN1OctetString(attributeType));
    avaElements.add(assertionValue);
    elements.add(new ASN1Sequence(avaElements));

    return new ASN1Sequence(OP_TYPE_COMPARE_REQUEST, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP compare request protocol op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP compare request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element as a compare request protocol op.
   */
  public static CompareRequestProtocolOp decodeCompareRequest(ASN1Element
                                                                   element)
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_COMPARE_REQUEST_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 2)
    {
      int    msgID   = MSGID_LDAP_COMPARE_REQUEST_DECODE_INVALID_ELEMENT_COUNT;
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_COMPARE_REQUEST_DECODE_DN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ArrayList<ASN1Element> avaElements;
    try
    {
      avaElements = elements.get(1).decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_COMPARE_REQUEST_DECODE_AVA;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    numElements = avaElements.size();
    if (numElements != 2)
    {
      int    msgID   = MSGID_LDAP_COMPARE_REQUEST_DECODE_AVA_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    String attributeType;
    try
    {
      attributeType = avaElements.get(0).decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_COMPARE_REQUEST_DECODE_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ASN1OctetString assertionValue;
    try
    {
      assertionValue = avaElements.get(1).decodeAsOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_COMPARE_REQUEST_DECODE_VALUE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new CompareRequestProtocolOp(dn, attributeType, assertionValue);
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("CompareRequest(dn=");
    dn.toString(buffer);
    buffer.append(", attribute=");
    buffer.append(attributeType);
    buffer.append(", value=");
    assertionValue.toString(buffer);
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
    buffer.append("Compare Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Target DN:  ");
    dn.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Attribute Type:  ");
    buffer.append(attributeType);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Assertion Value:");
    buffer.append(EOL);
    assertionValue.toString(buffer, indent+4);
  }
}

