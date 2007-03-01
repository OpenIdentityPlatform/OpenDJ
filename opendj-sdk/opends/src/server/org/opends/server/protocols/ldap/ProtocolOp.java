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



import org.opends.server.protocols.asn1.ASN1Element;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;



/**
 * This class defines the structures and methods for an LDAP protocol op, which
 * is the core of an LDAP message.
 */
public abstract class ProtocolOp
{



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public abstract byte getType();



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public abstract String getProtocolOpName();



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public abstract ASN1Element encode();



  /**
   * Decodes the provided ASN.1 element as an LDAP protocol op.
   *
   * @param  element  The ASN.1 element containing the encoded LDAP protocol op.
   *
   * @return  The LDAP protocol op decoded from the provided ASN.1 element.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 element as an LDAP protocol op.
   */
  public static ProtocolOp decode(ASN1Element element)
         throws LDAPException
  {

    if (element == null)
    {
      int    msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_NULL;
      String message = getMessage(msgID);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }

    switch (element.getType())
    {
      case OP_TYPE_UNBIND_REQUEST:                                       // 0x42
        return UnbindRequestProtocolOp.decodeUnbindRequest(element);
      case 0x43:                                                         // 0x43
      case 0x44:                                                         // 0x44
      case 0x45:                                                         // 0x45
      case 0x46:                                                         // 0x46
      case 0x47:                                                         // 0x47
      case 0x48:                                                         // 0x48
      case 0x49:                                                         // 0x49
        int    msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        String message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_DELETE_REQUEST:                                       // 0x4A
        return DeleteRequestProtocolOp.decodeDeleteRequest(element);
      case 0x4B:                                                         // 0x4B
      case 0x4C:                                                         // 0x4C
      case 0x4D:                                                         // 0x4D
      case 0x4E:                                                         // 0x4E
      case 0x4F:                                                         // 0x4F
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_ABANDON_REQUEST:                                      // 0x50
        return AbandonRequestProtocolOp.decodeAbandonRequest(element);
      case 0x51:                                                         // 0x51
      case 0x52:                                                         // 0x52
      case 0x53:                                                         // 0x53
      case 0x54:                                                         // 0x54
      case 0x55:                                                         // 0x55
      case 0x56:                                                         // 0x56
      case 0x57:                                                         // 0x57
      case 0x58:                                                         // 0x58
      case 0x59:                                                         // 0x59
      case 0x5A:                                                         // 0x5A
      case 0x5B:                                                         // 0x5B
      case 0x5C:                                                         // 0x5C
      case 0x5D:                                                         // 0x5D
      case 0x5E:                                                         // 0x5E
      case 0x5F:                                                         // 0x5F
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_BIND_REQUEST:                                         // 0x60
        return BindRequestProtocolOp.decodeBindRequest(element);
      case OP_TYPE_BIND_RESPONSE:                                        // 0x61
        return BindResponseProtocolOp.decodeBindResponse(element);
      case 0x62:                                                         // 0x62
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_SEARCH_REQUEST:                                       // 0x63
        return SearchRequestProtocolOp.decodeSearchRequest(element);
      case OP_TYPE_SEARCH_RESULT_ENTRY:                                  // 0x64
        return SearchResultEntryProtocolOp.decodeSearchEntry(element);
      case OP_TYPE_SEARCH_RESULT_DONE:                                   // 0x65
        return SearchResultDoneProtocolOp.decodeSearchDone(element);
      case OP_TYPE_MODIFY_REQUEST:                                       // 0x66
        return ModifyRequestProtocolOp.decodeModifyRequest(element);
      case OP_TYPE_MODIFY_RESPONSE:                                      // 0x67
        return ModifyResponseProtocolOp.decodeModifyResponse(element);
      case OP_TYPE_ADD_REQUEST:                                          // 0x68
        return AddRequestProtocolOp.decodeAddRequest(element);
      case OP_TYPE_ADD_RESPONSE:                                         // 0x69
        return AddResponseProtocolOp.decodeAddResponse(element);
      case 0x6A:                                                         // 0x6A
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_DELETE_RESPONSE:                                      // 0x6B
        return DeleteResponseProtocolOp.decodeDeleteResponse(element);
      case OP_TYPE_MODIFY_DN_REQUEST:                                    // 0x6C
        return ModifyDNRequestProtocolOp.decodeModifyDNRequest(element);
      case OP_TYPE_MODIFY_DN_RESPONSE:                                   // 0x6D
        return ModifyDNResponseProtocolOp.decodeModifyDNResponse(element);
      case OP_TYPE_COMPARE_REQUEST:                                      // 0x6E
        return CompareRequestProtocolOp.decodeCompareRequest(element);
      case OP_TYPE_COMPARE_RESPONSE:                                     // 0x6F
        return CompareResponseProtocolOp.decodeCompareResponse(element);
      case 0x70:                                                         // 0x70
      case 0x71:                                                         // 0x71
      case 0x72:                                                         // 0x72
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_SEARCH_RESULT_REFERENCE:                              // 0x73
        return SearchResultReferenceProtocolOp.decodeSearchReference(element);
      case 0x74:                                                         // 0x74
      case 0x75:                                                         // 0x75
      case 0x76:                                                         // 0x76
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      case OP_TYPE_EXTENDED_REQUEST:                                     // 0x77
        return ExtendedRequestProtocolOp.decodeExtendedRequest(element);
      case OP_TYPE_EXTENDED_RESPONSE:                                    // 0x78
        return ExtendedResponseProtocolOp.decodeExtendedResponse(element);
      case OP_TYPE_INTERMEDIATE_RESPONSE:                                // 0x79
        return
             IntermediateResponseProtocolOp.decodeIntermediateResponse(element);
      default:
        msgID   = MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE;
        message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }
  }



  /**
   * Retrieves a string representation of this LDAP protocol op.
   *
   * @return  A string representation of this LDAP protocol op.
   */
  public String toString()
  {

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public abstract void toString(StringBuilder buffer);



  /**
   * Appends a multi-line string representation of this LDAP protocol op to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public abstract void toString(StringBuilder buffer, int indent);
}

