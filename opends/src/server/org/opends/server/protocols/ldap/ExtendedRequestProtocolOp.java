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

import org.opends.server.core.DirectoryException;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP extended request
 * protocol op, which is used to request some special type of processing defined
 * in an extension to the LDAP protocol.
 */
public class ExtendedRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The fully-qualified name of this class to use for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.ExtendedRequestProtocolOp";



  // The value for this extended request.
  private ASN1OctetString value;

  // The OID for this extended request.
  private String oid;



  /**
   * Creates a new extended request protocol op with the specified OID and no
   * value.
   *
   * @param  oid  The OID for this extended request.
   */
  public ExtendedRequestProtocolOp(String oid)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(oid));

    this.oid   = oid;
    this.value = null;
  }



  /**
   * Creates a new extended request protocol op with the specified OID and
   * value.
   *
   * @param  oid    The OID for this extended request.
   * @param  value  The value for this extended request.
   */
  public ExtendedRequestProtocolOp(String oid, ASN1OctetString value)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(value));

    this.oid   = oid;
    this.value = value;
  }



  /**
   * Retrieves the OID for this extended request.
   *
   * @return  The OID for this extended request.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return oid;
  }



  /**
   * Specifies the OID for this extended request.
   *
   * @param  oid  The OID for this extended request.
   */
  public void setOID(String oid)
  {
    assert debugEnter(CLASS_NAME, "setOID", String.valueOf(oid));

    this.oid = oid;
  }



  /**
   * Retrieves the value for this extended request.
   *
   * @return  The value for this extended request, or <CODE>null</CODE> if there
   *          is no value.
   */
  public ASN1OctetString getValue()
  {
    assert debugEnter(CLASS_NAME, "getValue");

    return value;
  }



  /**
   * Specifies the value for this extended request.
   *
   * @param  value  The value for this extended request.
   */
  public void setValue(ASN1OctetString value)
  {
    assert debugEnter(CLASS_NAME, "setValue");

    this.value = value;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    assert debugEnter(CLASS_NAME, "getType");

    return OP_TYPE_EXTENDED_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    assert debugEnter(CLASS_NAME, "getProtocolOpName");

    return "Extended Request";
  }



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public ASN1Element encode()
  {
    assert debugEnter(CLASS_NAME, "encode");

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1OctetString(TYPE_EXTENDED_REQUEST_OID, oid));

    if (value != null)
    {
      value.setType(TYPE_EXTENDED_REQUEST_VALUE);
      elements.add(value);
    }

    return new ASN1Sequence(OP_TYPE_EXTENDED_REQUEST, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP extended request protocol op.
   *
   * @param  element  The ASN.1 element to be decoded.
   *
   * @return  The decoded extended request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP extended request
   *                         protocol op.
   */
  public static ExtendedRequestProtocolOp decodeExtendedRequest(ASN1Element
                                                                     element)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeExtendedRequest",
                      String.valueOf(element));

    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeExtendedRequest", e);

      int    msgID   = MSGID_LDAP_EXTENDED_REQUEST_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if ((numElements < 1) || (numElements > 2))
    {
      int    msgID   = MSGID_LDAP_EXTENDED_REQUEST_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    String oid;
    try
    {
      oid = elements.get(0).decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeExtendedRequest", e);

      int    msgID   = MSGID_LDAP_EXTENDED_REQUEST_DECODE_OID;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ASN1OctetString value;
    if (numElements == 2)
    {
      try
      {
        value = elements.get(1).decodeAsOctetString();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "decodeExtendedRequest", e);

        int    msgID   = MSGID_LDAP_EXTENDED_REQUEST_DECODE_VALUE;
        String message = getMessage(msgID, String.valueOf(e));
        throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
      }
    }
    else
    {
      value = null;
    }


    return new ExtendedRequestProtocolOp(oid, value);
  }



  /**
   * Converts the provided LDAP message containing an extended request protocol
   * op to a <CODE>ExtendedOperation</CODE> object that may be processed by the
   * core server.
   *
   * @param  requestMessage    The LDAP message containing the extended request
   *                           protocol op.
   * @param  clientConnection  The client connection from which the request was
   *                           read.
   *
   * @return  The extended operation created from the provided request message.
   *
   * @throws  DirectoryException  If the provided LDAP message cannot be decoded
   *                              as an extended operation.
   */
  public static ExtendedOperation messageToExtendedOperation(
                                       LDAPMessage requestMessage,
                                       LDAPClientConnection clientConnection)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "messageToExtendedOperation",
                      String.valueOf(requestMessage),
                      String.valueOf(clientConnection));

    // NYI
    return null;
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("ExtendedRequest(oid=");
    buffer.append(oid);

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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder",
                      String.valueOf(indent));

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Extended Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  OID:  ");
    buffer.append(oid);
    buffer.append(EOL);

    if (value != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Value:");
      buffer.append(EOL);
      value.toString(buffer, indent+4);
    }
  }
}

