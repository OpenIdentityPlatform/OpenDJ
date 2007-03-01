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

import org.opends.server.api.ProtocolElement;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1Sequence;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP message, which is the basic envelope used to hold LDAP requests
 * and responses.
 */
public class LDAPMessage
       implements ProtocolElement
{



  // The set of controls for this LDAP message.
  private ArrayList<LDAPControl> controls;

  // The message ID for this LDAP message.
  private int messageID;

  // The protocol op for this LDAP message.
  private ProtocolOp protocolOp;



  /**
   * Creates a new LDAP message with the provided message ID and protocol op but
   * no controls.
   *
   * @param  messageID   The message ID for this LDAP message.
   * @param  protocolOp  The protocol op for this LDAP message.
   */
  public LDAPMessage(int messageID, ProtocolOp protocolOp)
  {
    this.messageID  = messageID;
    this.protocolOp = protocolOp;

    controls = new ArrayList<LDAPControl>(0);
  }



  /**
   * Creates a new LDAP message with the provided message ID, protocol op, and
   * set of controls.
   *
   * @param  messageID   The message ID for this LDAP message.
   * @param  protocolOp  The protocol op for this LDAP message.
   * @param  controls    The set of controls for this LDAP message.
   */
  public LDAPMessage(int messageID, ProtocolOp protocolOp,
                     ArrayList<LDAPControl> controls)
  {
    this.messageID  = messageID;
    this.protocolOp = protocolOp;

    if (controls == null)
    {
      this.controls = new ArrayList<LDAPControl>(0);
    }
    else
    {
      this.controls = controls;
    }
  }



  /**
   * Retrieves the message ID for this LDAP message.
   *
   * @return  The message ID for this LDAP message.
   */
  public int getMessageID()
  {
    return messageID;
  }



  /**
   * Specifies the message ID for this LDAP message.
   *
   * @param  messageID  The message ID for this LDAP message.
   */
  public void setMessageID(int messageID)
  {
    this.messageID = messageID;
  }



  /**
   * Retrieves the protocol op for this LDAP message.
   *
   * @return  The protocol op for this LDAP message.
   */
  public ProtocolOp getProtocolOp()
  {
    return protocolOp;
  }



  /**
   * Retrieves the protocol op type for this LDAP message.
   *
   * @return  The protocol op type for this LDAP message.
   */
  public byte getProtocolOpType()
  {
    return protocolOp.getType();
  }



  /**
   * Retrieves the protocol op name for this LDAP message.
   *
   * @return  The protocol op name for this LDAP message.
   */
  public String getProtocolOpName()
  {
    return protocolOp.getProtocolOpName();
  }



  /**
   * Retrieves the protocol op for this LDAP message as an abandon request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as an abandon request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not an abandon request
   *                              protocol op.
   */
  public AbandonRequestProtocolOp getAbandonRequestProtocolOp()
         throws ClassCastException
  {
    return (AbandonRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as an add request protocol
   * op.
   *
   * @return  The protocol op for this LDAP message as an add request protocol
   *          op.
   *
   * @throws  ClassCastException  If the protocol op is not an add request
   *                              protocol op.
   */
  public AddRequestProtocolOp getAddRequestProtocolOp()
         throws ClassCastException
  {
    return (AddRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as an add response protocol
   * op.
   *
   * @return  The protocol op for this LDAP message as an add response protocol
   *          op.
   *
   * @throws  ClassCastException  If the protocol op is not an add response
   *                              protocol op.
   */
  public AddResponseProtocolOp getAddResponseProtocolOp()
         throws ClassCastException
  {
    return (AddResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a bind request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a bind request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a bind request
   *                              protocol op.
   */
  public BindRequestProtocolOp getBindRequestProtocolOp()
         throws ClassCastException
  {
    return (BindRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a bind response
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a bind response
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a bind response
   *                              protocol op.
   */
  public BindResponseProtocolOp getBindResponseProtocolOp()
         throws ClassCastException
  {
    return (BindResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a compare request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a compare request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a compare request
   *                              protocol op.
   */
  public CompareRequestProtocolOp getCompareRequestProtocolOp()
         throws ClassCastException
  {
    return (CompareRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a compare response
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a compare response
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a compare response
   *                              protocol op.
   */
  public CompareResponseProtocolOp getCompareResponseProtocolOp()
         throws ClassCastException
  {
    return (CompareResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a delete request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a delete request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a delete request
   *                              protocol op.
   */
  public DeleteRequestProtocolOp getDeleteRequestProtocolOp()
         throws ClassCastException
  {
    return (DeleteRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a delete response
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a delete response
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a delete response
   *                              protocol op.
   */
  public DeleteResponseProtocolOp getDeleteResponseProtocolOp()
         throws ClassCastException
  {
    return (DeleteResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as an extended request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as an extended request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not an extended request
   *                              protocol op.
   */
  public ExtendedRequestProtocolOp getExtendedRequestProtocolOp()
         throws ClassCastException
  {
    return (ExtendedRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as an extended response
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as an extended response
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not an extended response
   *                              protocol op.
   */
  public ExtendedResponseProtocolOp getExtendedResponseProtocolOp()
         throws ClassCastException
  {
    return (ExtendedResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a modify request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a modify request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a modify request
   *                              protocol op.
   */
  public ModifyRequestProtocolOp getModifyRequestProtocolOp()
         throws ClassCastException
  {
    return (ModifyRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a modify response
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a modify response
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a modify response
   *                              protocol op.
   */
  public ModifyResponseProtocolOp getModifyResponseProtocolOp()
         throws ClassCastException
  {
    return (ModifyResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a modify DN request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a modify DN request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a modify DN request
   *                              protocol op.
   */
  public ModifyDNRequestProtocolOp getModifyDNRequestProtocolOp()
         throws ClassCastException
  {
    return (ModifyDNRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a modify DN response
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a modify DN response
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a modify DN response
   *                              protocol op.
   */
  public ModifyDNResponseProtocolOp getModifyDNResponseProtocolOp()
         throws ClassCastException
  {
    return (ModifyDNResponseProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a search request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a search request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a search request
   *                              protocol op.
   */
  public SearchRequestProtocolOp getSearchRequestProtocolOp()
         throws ClassCastException
  {
    return (SearchRequestProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a search result done
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a search result done
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a search result done
   *                              protocol op.
   */
  public SearchResultDoneProtocolOp getSearchResultDoneProtocolOp()
         throws ClassCastException
  {
    return (SearchResultDoneProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a search result entry
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as a search result entry
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a search result
   *                              entry protocol op.
   */
  public SearchResultEntryProtocolOp getSearchResultEntryProtocolOp()
         throws ClassCastException
  {
    return (SearchResultEntryProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as a search result
   * reference protocol op.
   *
   * @return  The protocol op for this LDAP message as a search result reference
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not a search result
   *                              reference protocol op.
   */
  public SearchResultReferenceProtocolOp getSearchResultReferenceProtocolOp()
         throws ClassCastException
  {
    return (SearchResultReferenceProtocolOp) protocolOp;
  }



  /**
   * Retrieves the protocol op for this LDAP message as an unbind request
   * protocol op.
   *
   * @return  The protocol op for this LDAP message as an unbind request
   *          protocol op.
   *
   * @throws  ClassCastException  If the protocol op is not an unbind request
   *                              protocol op.
   */
  public UnbindRequestProtocolOp getUnbindRequestProtocolOp()
         throws ClassCastException
  {
    return (UnbindRequestProtocolOp) protocolOp;
  }



  /**
   * Specifies the protocol op for this LDAP message.
   *
   * @param  protocolOp  The protocol op for this LDAP message.
   */
  public void setProtocolOp(ProtocolOp protocolOp)
  {
    this.protocolOp = protocolOp;
  }



  /**
   * Retrieves the set of controls for this LDAP message.  It may be modified by
   * the caller.
   *
   * @return  The set of controls for this LDAP message.
   */
  public ArrayList<LDAPControl> getControls()
  {
    return controls;
  }



  /**
   * Encodes this LDAP message to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded LDAP message.
   */
  public ASN1Element encode()
  {
    ArrayList<ASN1Element> messageElements = new ArrayList<ASN1Element>(3);
    messageElements.add(new ASN1Integer(messageID));
    messageElements.add(protocolOp.encode());

    if (! controls.isEmpty())
    {
      messageElements.add(LDAPControl.encodeControls(controls));
    }

    return new ASN1Sequence(messageElements);
  }



  /**
   * Decodes the provided ASN.1 sequence as an LDAP message.
   *
   * @param  messageSequence  The ASN.1 sequence to decode as an LDAP message.
   *
   * @return  The decoded LDAP message.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         LDAP message.
   */
  public static LDAPMessage decode(ASN1Sequence messageSequence)
         throws LDAPException
  {
    if (messageSequence == null)
    {
      int    msgID   = MSGID_LDAP_MESSAGE_DECODE_NULL;
      String message = getMessage(msgID);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }

    ArrayList<ASN1Element> elements = messageSequence.elements();
    int numElements = elements.size();
    if ((numElements < 2) || (numElements > 3))
    {
      int    msgID   = MSGID_LDAP_MESSAGE_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    int messageID;
    try
    {
      messageID = elements.get(0).decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MESSAGE_DECODE_MESSAGE_ID;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ProtocolOp protocolOp;
    try
    {
      protocolOp = ProtocolOp.decode(elements.get(1));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MESSAGE_DECODE_PROTOCOL_OP;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ArrayList<LDAPControl> controls;
    if (numElements == 3)
    {
      try
      {
        controls = LDAPControl.decodeControls(elements.get(2));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_LDAP_MESSAGE_DECODE_CONTROLS;
        String message = getMessage(msgID, String.valueOf(e));
        throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
      }
    }
    else
    {
      controls = new ArrayList<LDAPControl>(0);
    }


    return new LDAPMessage(messageID, protocolOp, controls);
  }



  /**
   * Retrieves the name of the protocol associated with this protocol element.
   *
   * @return  The name of the protocol associated with this protocol element.
   */
  public String getProtocolElementName()
  {
    return "LDAP";
  }



  /**
   * Retrieves a string representation of this LDAP message.
   *
   * @return  A string representation of this LDAP message.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this protocol element to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which the string representation should be
   *                 written.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPMessage(msgID=");
    buffer.append(messageID);
    buffer.append(", protocolOp=");
    protocolOp.toString(buffer);

    if (! controls.isEmpty())
    {
      buffer.append(", controls={ ");

      Iterator<LDAPControl> iterator = controls.iterator();
      iterator.next().toString(buffer);

      while (iterator.hasNext())
      {
        buffer.append(", ");
        iterator.next().toString(buffer);
      }

      buffer.append(" }");
    }

    buffer.append(")");
  }



  /**
   * Appends a string representation of this protocol element to the provided
   * buffer.
   *
   * @param  buffer  The buffer into which the string representation should be
   *                 written.
   * @param  indent  The number of spaces that should be used to indent the
   *                 resulting string representation.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("LDAP Message");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Message ID:  ");
    buffer.append(messageID);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Protocol Op:");
    buffer.append(EOL);
    protocolOp.toString(buffer, indent+4);

    if (! controls.isEmpty())
    {
      buffer.append(indentBuf);
      buffer.append("  Controls:");

      for (LDAPControl c : controls)
      {
        c.toString(buffer, indent+4);
      }
    }
  }
}

