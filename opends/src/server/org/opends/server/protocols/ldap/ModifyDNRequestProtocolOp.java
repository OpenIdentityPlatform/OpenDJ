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

import org.opends.server.protocols.asn1.ASN1Boolean;
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
 * This class defines the structures and methods for an LDAP modify DN request
 * protocol op, which is used to move or rename an entry or subtree within the
 * Directory Server.
 */
public class ModifyDNRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The fully-qualified name of this class to use for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp";



  // The current entry DN for this modify DN request.
  private ASN1OctetString entryDN;

  // The new RDN for this modify DN request.
  private ASN1OctetString newRDN;

  // The new superior DN for this modify DN request.
  private ASN1OctetString newSuperior;

  // Indicates whether to delete the current RDN value(s).
  private boolean deleteOldRDN;



  /**
   * Creates a new modify DN request protocol op with the provided information.
   *
   * @param  entryDN       The current entry DN for this modify DN request.
   * @param  newRDN        The new RDN for this modify DN request.
   * @param  deleteOldRDN  Indicates whether to delete the current RDN value(s).
   */
  public ModifyDNRequestProtocolOp(ASN1OctetString entryDN,
                                   ASN1OctetString newRDN, boolean deleteOldRDN)
  {
    assert debugEnter(CLASS_NAME, String.valueOf(entryDN),
                      String.valueOf(newRDN), String.valueOf(deleteOldRDN));

    this.entryDN      = entryDN;
    this.newRDN       = newRDN;
    this.deleteOldRDN = deleteOldRDN;
    this.newSuperior  = null;
  }



  /**
   * Creates a new modify DN request protocol op with the provided information.
   *
   * @param  entryDN       The current entry DN for this modify DN request.
   * @param  newRDN        The new RDN for this modify DN request.
   * @param  deleteOldRDN  Indicates whether to delete the current RDN value(s).
   * @param  newSuperior   The new superior DN for this modify DN request.
   */
  public ModifyDNRequestProtocolOp(ASN1OctetString entryDN,
                                   ASN1OctetString newRDN, boolean deleteOldRDN,
                                   ASN1OctetString newSuperior)
  {
    assert debugEnter(CLASS_NAME, String.valueOf(entryDN),
                      String.valueOf(newRDN), String.valueOf(deleteOldRDN),
                      String.valueOf(newSuperior));

    this.entryDN      = entryDN;
    this.newRDN       = newRDN;
    this.deleteOldRDN = deleteOldRDN;
    this.newSuperior  = newSuperior;
  }



  /**
   * Retrieves the current entry DN for this modify DN request.
   *
   * @return  The current entry DN for this modify DN request.
   */
  public ASN1OctetString getEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getEntryDN");

    return entryDN;
  }



  /**
   * Specifies the current entry DN for this modify DN request.
   *
   * @param  entryDN  The current entry DN for this modify DN request.
   */
  public void setEntryDN(ASN1OctetString entryDN)
  {
    assert debugEnter(CLASS_NAME, "setEntryDN", String.valueOf(entryDN));

    this.entryDN = entryDN;
  }



  /**
   * Retrieves the new RDN for this modify DN request.
   *
   * @return  The new RDN for this modify DN request.
   */
  public ASN1OctetString getNewRDN()
  {
    assert debugEnter(CLASS_NAME, "getNewRDN");

    return newRDN;
  }



  /**
   * Specifies the new RDN for this modify DN request.
   *
   * @param  newRDN  The new RDN for this modify DN request.
   */
  public void setNewRDN(ASN1OctetString newRDN)
  {
    assert debugEnter(CLASS_NAME, "setNewRDN", String.valueOf(newRDN));

    this.newRDN = newRDN;
  }



  /**
   * Indicates whether the current RDN value(s) should be deleted.
   *
   * @return  <CODE>true</CODE> if the current RDN value(s) should be deleted,
   *          or <CODE>false</CODE> if not.
   */
  public boolean deleteOldRDN()
  {
    assert debugEnter(CLASS_NAME, "deleteOldRDN");

    return deleteOldRDN;
  }



  /**
   * Specifies whether the current RDN value(s) should be deleted.
   *
   * @param  deleteOldRDN  Specifies whether the current RDN value(s) should be
   *                       deleted.
   */
  public void setDeleteOldRDN(boolean deleteOldRDN)
  {
    assert debugEnter(CLASS_NAME, "setDeleteOldRDN",
                      String.valueOf(deleteOldRDN));

    this.deleteOldRDN = deleteOldRDN;
  }



  /**
   * Retrieves the new superior DN for this modify DN request.
   *
   * @return  The new superior DN for this modify DN request, or
   *          <CODE>null</CODE> if none was provided.
   */
  public ASN1OctetString getNewSuperior()
  {
    assert debugEnter(CLASS_NAME, "getNewSuperior");

    return newSuperior;
  }



  /**
   * Specifies the new superior DN for this modify DN request.
   *
   * @param  newSuperior  The new superior DN for this modify DN request.
   */
  public void setNewSuperior(ASN1OctetString newSuperior)
  {
    assert debugEnter(CLASS_NAME, "setNewSuperior",
                      String.valueOf(newSuperior));

    this.newSuperior = newSuperior;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    assert debugEnter(CLASS_NAME, "getType");

    return OP_TYPE_MODIFY_DN_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    assert debugEnter(CLASS_NAME, "getProtocolOpName");

    return "Modify DN Request";
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

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(4);
    elements.add(entryDN);
    elements.add(newRDN);
    elements.add(new ASN1Boolean(deleteOldRDN));

    if (newSuperior != null)
    {
      newSuperior.setType(TYPE_MODIFY_DN_NEW_SUPERIOR);
      elements.add(newSuperior);
    }

    return new ASN1Sequence(OP_TYPE_MODIFY_DN_REQUEST, elements);
  }



  /**
   * Decodes the provided ASN.1 element as a modify DN request protocol op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded modify DN request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 element as an LDAP modify DN request
   *                         protocol op.
   */
  public static ModifyDNRequestProtocolOp decodeModifyDNRequest(ASN1Element
                                                                     element)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeModifyDNRequest",
                      String.valueOf(element));

    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeModifyDNRequest", e);

      int    msgID   = MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if ((numElements < 3) || (numElements > 4))
    {
      int msgID = MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    ASN1OctetString entryDN;
    try
    {
      entryDN = elements.get(0).decodeAsOctetString();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeModifyDNRequest", e);

      int    msgID   = MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_DN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ASN1OctetString newRDN;
    try
    {
      newRDN = elements.get(1).decodeAsOctetString();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeModifyDNRequest", e);

      int    msgID   = MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_RDN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    boolean deleteOldRDN;
    try
    {
      deleteOldRDN = elements.get(2).decodeAsBoolean().booleanValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeModifyDNRequest", e);

      int    msgID   = MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_DELETE_OLD_RDN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ASN1OctetString newSuperior;
    if (numElements == 4)
    {
      try
      {
        newSuperior = elements.get(3).decodeAsOctetString();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "decodeModifyDNRequest", e);

        int    msgID   = MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_SUPERIOR;
        String message = getMessage(msgID, String.valueOf(e));
        throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
      }
    }
    else
    {
      newSuperior = null;
    }


    return new ModifyDNRequestProtocolOp(entryDN, newRDN, deleteOldRDN,
                                         newSuperior);
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

    buffer.append("ModifyDNRequest(dn=");
    entryDN.toString(buffer);
    buffer.append(", newRDN=");
    newRDN.toString(buffer);
    buffer.append(", deleteOldRDN=");
    buffer.append(deleteOldRDN);

    if (newSuperior != null)
    {
      buffer.append(", newSuperior=");
      newSuperior.toString(buffer);
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
    buffer.append("Modify DN Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Entry DN:  ");
    entryDN.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  New RDN:  ");
    newRDN.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Delete Old RDN:  ");
    buffer.append(deleteOldRDN);
    buffer.append(EOL);

    if (newSuperior != null)
    {
      buffer.append(indentBuf);
      buffer.append("  New Superior:  ");
      newSuperior.toString(buffer);
      buffer.append(EOL);
    }
  }
}

