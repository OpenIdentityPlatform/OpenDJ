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

import org.opends.server.core.AbandonOperation;
import org.opends.server.core.DirectoryException;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.types.Control;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP abandon request
 * protocol op, which is used to indicate that the server should stop processing
 * a previously requested operation.
 */
public class AbandonRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The fully-qualified name of this class to use for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.AbandonRequestProtocolOp";



  // The message ID of the operation to abandon.
  private int idToAbandon;



  /**
   * Creates a new abandon request protocol op to abandon the specified
   * operation.
   *
   * @param  idToAbandon  The message ID of the operation to abandon.
   */
  public AbandonRequestProtocolOp(int idToAbandon)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(idToAbandon));

    this.idToAbandon = idToAbandon;
  }



  /**
   * Retrieves the message ID of the operation to abandon.
   *
   * @return  The message ID of the operation to abandon.
   */
  public int getIDToAbandon()
  {
    assert debugEnter(CLASS_NAME, "getIDToAbandon");

    return idToAbandon;
  }



  /**
   * Specifies the message ID of the operation to abandon.
   *
   * @param  idToAbandon  The message ID of the operation to abandon.
   */
  public void setIDToAbandon(int idToAbandon)
  {
    assert debugEnter(CLASS_NAME, "setIDToAbandon",
                      String.valueOf(idToAbandon));

    this.idToAbandon = idToAbandon;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    assert debugEnter(CLASS_NAME, "getType");

    return OP_TYPE_ABANDON_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    assert debugEnter(CLASS_NAME, "getProtocolOpName");

    return "Abandon Request";
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

    return new ASN1Integer(OP_TYPE_ABANDON_REQUEST, idToAbandon);
  }



  /**
   * Decodes the provided ASN.1 element as an abandon request protocol op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded abandon request protocol op.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be decoded as
   *                         an abandon request protocol op.
   */
  public static AbandonRequestProtocolOp decodeAbandonRequest(ASN1Element
                                                                   element)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeAbandonRequest",
                      String.valueOf(element));

    int idToAbandon;
    try
    {
      idToAbandon = element.decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeAbandonRequest", e);

      int msgID = MSGID_LDAP_ABANDON_REQUEST_DECODE_ID;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }

    return new AbandonRequestProtocolOp(idToAbandon);
  }



  /**
   * Converts the provided LDAP message containing an abandon request protocol
   * op to an <CODE>AbandonOperation</CODE> object that may be processed by the
   * core server.
   *
   * @param  requestMessage    The LDAP message containing the abandon request
   *                           protocol op.
   * @param  clientConnection  The client connection from which the request was
   *                           read.
   *
   * @return  The abandon operation created from the provided request message.
   *
   * @throws  DirectoryException  If the provided LDAP message cannot be decoded
   *                              as an abandon operation.
   */
  public static AbandonOperation messageToAbandonOperation(
                                      LDAPMessage requestMessage,
                                      LDAPClientConnection clientConnection)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "messageToAbandonOperation",
                      String.valueOf(requestMessage),
                      String.valueOf(clientConnection));

    AbandonRequestProtocolOp abandonRequest;
    try
    {
      abandonRequest =
           (AbandonRequestProtocolOp) requestMessage.getProtocolOp();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "messageToAbandonOperation", e);

      int msgID = MSGID_LDAP_ABANDON_INVALID_MESSAGE_TYPE;
      String message = getMessage(msgID, String.valueOf(requestMessage),
                                  String.valueOf(e));
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, msgID,
                                   e);
    }

    ArrayList<Control> controls;
    ArrayList<LDAPControl> ldapControls = requestMessage.getControls();
    if ((ldapControls == null) || ldapControls.isEmpty())
    {
      controls = null;
    }
    else
    {
      controls = new ArrayList<Control>(ldapControls.size());
      for (LDAPControl c : ldapControls)
      {
        controls.add(c.getControl());
      }
    }

    return new AbandonOperation(clientConnection,
                                clientConnection.nextOperationID(),
                                requestMessage.getMessageID(),
                                controls, abandonRequest.getIDToAbandon());
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

    buffer.append("AbandonRequest(idToAbandon=");
    buffer.append(idToAbandon);
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
    buffer.append("Abandon Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  ID to Abandon:  ");
    buffer.append(idToAbandon);
    buffer.append(EOL);
  }
}

