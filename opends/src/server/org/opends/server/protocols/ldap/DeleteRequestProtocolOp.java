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



import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryException;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP delete request
 * protocol op, which is used to remove an entry from the Directory Server.
 */
public class DeleteRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The fully-qualified name of this class to use for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.DeleteRequestProtocolOp";



  // The DN for this delete request.
  private ASN1OctetString dn;



  /**
   * Creates a new delete request protocol op with the specified DN.
   *
   * @param  dn  The DN for this delete request protocol op.
   */
  public DeleteRequestProtocolOp(ASN1OctetString dn)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(dn));

    this.dn = dn;
  }



  /**
   * Retrieves the DN for this delete request.
   *
   * @return  The DN for this delete request.
   */
  public ASN1OctetString getDN()
  {
    assert debugEnter(CLASS_NAME, "getDN");

    return dn;
  }



  /**
   * Specifies the DN for this delete request.
   *
   * @param  dn  The DN for this delete request.
   */
  public void setDN(ASN1OctetString dn)
  {
    assert debugEnter(CLASS_NAME, "setDN", String.valueOf(dn));

    this.dn = dn;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    assert debugEnter(CLASS_NAME, "getType");

    return OP_TYPE_DELETE_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    assert debugEnter(CLASS_NAME, "getProtocolOpName");

    return "Delete Request";
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

    dn.setType(OP_TYPE_DELETE_REQUEST);
    return dn;
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP delete request protocol op.
   *
   * @param  element  The ASN.1 element to be decoded.
   *
   * @return  The decoded delete request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as a delete request protocol op.
   */
  public static DeleteRequestProtocolOp decodeDeleteRequest(ASN1Element element)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeDeleteRequest",
                      String.valueOf(element));

    try
    {
      return new DeleteRequestProtocolOp(element.decodeAsOctetString());
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeDeleteRequest", e);

      int    msgID   = MSGID_LDAP_DELETE_REQUEST_DECODE_DN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }
  }



  /**
   * Converts the provided LDAP message containing a delete request protocol op
   * to a <CODE>DeleteOperation</CODE> object that may be processed by the core
   * server.
   *
   * @param  requestMessage    The LDAP message containing the delete request
   *                           protocol op.
   * @param  clientConnection  The client connection from which the request was
   *                           read.
   *
   * @return  The delete operation created from the provided request message.
   *
   * @throws  DirectoryException  If the provided LDAP message cannot be decoded
   *                              as a delete operation.
   */
  public static DeleteOperation messageToDeleteOperation(
                                     LDAPMessage requestMessage,
                                     LDAPClientConnection clientConnection)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "messageToDeleteOperation",
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

    buffer.append("DeleteRequest(dn=");
    dn.toString(buffer);
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
    buffer.append("Delete Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Entry DN:  ");
    dn.toString(buffer);
    buffer.append(EOL);
  }
}

