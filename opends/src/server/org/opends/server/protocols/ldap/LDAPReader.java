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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;

import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.types.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.messages.Message;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;


/**
 * Utility class used to decode LDAP messages from an ASN1Reader.
 */
public class LDAPReader
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP message.
   *
   * @param reader The ASN.1 reader.
   *
   * @return  The decoded LDAP message.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         LDAP message.
   */
  public static LDAPMessage readMessage(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch(Exception e)
    {
      Message message = ERR_LDAP_MESSAGE_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    int messageID;
    try
    {
      messageID = (int)reader.readInteger();
    }
    catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MESSAGE_DECODE_MESSAGE_ID.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ProtocolOp protocolOp;
    try
    {
      protocolOp = readProtocolOp(reader);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MESSAGE_DECODE_PROTOCOL_OP.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<Control> controls = null;
    try
    {
      if(reader.hasNextElement())
      {
        controls = readControls(reader);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MESSAGE_DECODE_CONTROLS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch(Exception e)
    {
      Message message = ERR_LDAP_MESSAGE_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    return new LDAPMessage(messageID, protocolOp, controls);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * protocol op.
   *
   * @param reader The ASN.1 reader.
   *
   * @return  The LDAP protocol op decoded from the provided ASN.1 element.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 elements as an LDAP protocol op.
   */
  public static ProtocolOp readProtocolOp(ASN1Reader reader)
      throws LDAPException
  {
    byte type;
    try
    {
      type = reader.peekType();
    }
    catch(Exception e)
    {
      Message message = ERR_LDAP_PROTOCOL_OP_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    switch(type)
    {
      case OP_TYPE_UNBIND_REQUEST:                                       // 0x42
        return readUnbindRequest(reader);
      case 0x43:                                                         // 0x43
      case 0x44:                                                         // 0x44
      case 0x45:                                                         // 0x45
      case 0x46:                                                         // 0x46
      case 0x47:                                                         // 0x47
      case 0x48:                                                         // 0x48
      case 0x49:                                                         // 0x49
        Message message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_DELETE_REQUEST:                                       // 0x4A
        return readDeleteRequest(reader);
      case 0x4B:                                                         // 0x4B
      case 0x4C:                                                         // 0x4C
      case 0x4D:                                                         // 0x4D
      case 0x4E:                                                         // 0x4E
      case 0x4F:                                                         // 0x4F
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_ABANDON_REQUEST:                                      // 0x50
        return readAbandonRequest(reader);
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
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_BIND_REQUEST:                                         // 0x60
        return readBindRequest(reader);
      case OP_TYPE_BIND_RESPONSE:                                        // 0x61
        return readBindResponse(reader);
      case 0x62:                                                         // 0x62
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_SEARCH_REQUEST:                                       // 0x63
        return readSearchRequest(reader);
      case OP_TYPE_SEARCH_RESULT_ENTRY:                                  // 0x64
        return readSearchEntry(reader);
      case OP_TYPE_SEARCH_RESULT_DONE:                                   // 0x65
        return readSearchDone(reader);
      case OP_TYPE_MODIFY_REQUEST:                                       // 0x66
        return readModifyRequest(reader);
      case OP_TYPE_MODIFY_RESPONSE:                                      // 0x67
        return readModifyResponse(reader);
      case OP_TYPE_ADD_REQUEST:                                          // 0x68
        return readAddRequest(reader);
      case OP_TYPE_ADD_RESPONSE:                                         // 0x69
        return readAddResponse(reader);
      case 0x6A:                                                         // 0x6A
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_DELETE_RESPONSE:                                      // 0x6B
        return readDeleteResponse(reader);
      case OP_TYPE_MODIFY_DN_REQUEST:                                    // 0x6C
        return readModifyDNRequest(reader);
      case OP_TYPE_MODIFY_DN_RESPONSE:                                   // 0x6D
        return readModifyDNResponse(reader);
      case OP_TYPE_COMPARE_REQUEST:                                      // 0x6E
        return readCompareRequest(reader);
      case OP_TYPE_COMPARE_RESPONSE:                                     // 0x6F
        return readCompareResponse(reader);
      case 0x70:                                                         // 0x70
      case 0x71:                                                         // 0x71
      case 0x72:                                                         // 0x72
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_SEARCH_RESULT_REFERENCE:                              // 0x73
        return readSearchReference(reader);
      case 0x74:                                                         // 0x74
      case 0x75:                                                         // 0x75
      case 0x76:                                                         // 0x76
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
      case OP_TYPE_EXTENDED_REQUEST:                                     // 0x77
        return readExtendedRequest(reader);
      case OP_TYPE_EXTENDED_RESPONSE:                                    // 0x78
        return readExtendedResponse(reader);
      case OP_TYPE_INTERMEDIATE_RESPONSE:                                // 0x79
        return
            readIntermediateResponse(reader);
      default:
        message =
            ERR_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
    }
  }


  /**
   * Decodes the elements from the provided ASN.1 read as an LDAP
   *  abandon request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded abandon request protocol op.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be decoded as
   *                         an abandon request protocol op.
   */
  private static AbandonRequestProtocolOp readAbandonRequest(ASN1Reader reader)
      throws LDAPException
  {
    long idToAbandon;
    try
    {
      idToAbandon = reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_ABANDON_REQUEST_DECODE_ID.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new AbandonRequestProtocolOp((int)idToAbandon);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * add request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded add request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP add request protocol op.
   */
  private static AddRequestProtocolOp readAddRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_ADD_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString dn;
    try
    {
      dn = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_ADD_REQUEST_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }



    ArrayList<RawAttribute> attributes;
    try
    {
      reader.readStartSequence();
      attributes = new ArrayList<RawAttribute>();
      while(reader.hasNextElement())
      {
        attributes.add(LDAPAttribute.decode(reader));
      }
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_ADD_REQUEST_DECODE_ATTRS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_ADD_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new AddRequestProtocolOp(dn, attributes);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an
   * add response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded add response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static AddResponseProtocolOp readAddResponse(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;

    try
    {
      if (reader.hasNextElement())
      {
        reader.readStartSequence();
        referralURLs = new ArrayList<String>();

        while(reader.hasNextElement())
        {
          referralURLs.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new AddResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 read as an LDAP bind
   * request protocol op.
   *
   * @param  reader The ASN.1 reader
   *
   * @return  The decoded LDAP bind request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 element as an LDAP bind request.
   */
  private static BindRequestProtocolOp readBindRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_BIND_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int protocolVersion;
    try
    {
      protocolVersion = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_BIND_REQUEST_DECODE_VERSION.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString dn;
    try
    {
      dn = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_BIND_REQUEST_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    byte type;
    try
    {
      type = reader.peekType();

    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_BIND_REQUEST_DECODE_CREDENTIALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ByteString simplePassword  = null;
    String     saslMechanism   = null;
    ByteString saslCredentials = null;
    switch (type)
    {
      case TYPE_AUTHENTICATION_SIMPLE:
        try
        {
          simplePassword =
              reader.readOctetString();
        }
        catch (Exception e)
        {
          Message message =
              ERR_LDAP_BIND_REQUEST_DECODE_PASSWORD.get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
        break;
      case TYPE_AUTHENTICATION_SASL:
        try
        {
          reader.readStartSequence();
          saslMechanism = reader.readOctetStringAsString();
          if (reader.hasNextElement())
          {
            saslCredentials =
                reader.readOctetString();
          }
          reader.readEndSequence();
        }
        catch (Exception e)
        {
          Message message =
              ERR_LDAP_BIND_REQUEST_DECODE_SASL_INFO.get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
        break;
      default:
        Message message = ERR_LDAP_BIND_REQUEST_DECODE_INVALID_CRED_TYPE.get(
            type);
        throw new LDAPException(AUTH_METHOD_NOT_SUPPORTED, message);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_BIND_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    if(type == TYPE_AUTHENTICATION_SIMPLE)
    {
      return new BindRequestProtocolOp(dn, protocolVersion, simplePassword);
    }
    else
    {
      return new BindRequestProtocolOp(dn, saslMechanism, saslCredentials);
    }
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a bind
   * response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded bind response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static BindResponseProtocolOp readBindResponse(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;
    ByteString   serverSASLCredentials = null;

    try
    {
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_REFERRAL_SEQUENCE)
      {
        try
        {
          reader.readStartSequence();
          referralURLs = new ArrayList<String>();

          // Should have at least 1.
          do
          {
            referralURLs.add(reader.readOctetStringAsString());
          }
          while(reader.hasNextElement());
          reader.readEndSequence();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
      }
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_SERVER_SASL_CREDENTIALS)
      {
        try
        {
          serverSASLCredentials =
              reader.readOctetString();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_BIND_RESULT_DECODE_SERVER_SASL_CREDENTIALS.
                  get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
      }
    }
    catch(ASN1Exception asn1e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, asn1e);
      }
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new BindResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs, serverSASLCredentials);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * compare request protocol op.
   *
   * @param  reader The ASN.1 reader
   *
   * @return  The decoded LDAP compare request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element as a compare request protocol op.
   */
  private static CompareRequestProtocolOp readCompareRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString dn;
    try
    {
      dn = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_AVA.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    String attributeType;
    try
    {
      attributeType = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_TYPE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString assertionValue;
    try
    {
      assertionValue = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_VALUE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_AVA.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_COMPARE_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new CompareRequestProtocolOp(dn, attributeType, assertionValue);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * compare response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded compare response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static CompareResponseProtocolOp readCompareResponse(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;

    try
    {
      if (reader.hasNextElement())
      {
        reader.readStartSequence();
        referralURLs = new ArrayList<String>();

        while(reader.hasNextElement())
        {
          referralURLs.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new CompareResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP delete
   * request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded delete request protocol op.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be decoded as
   *                         an unbind request protocol op.
   */
  private static DeleteRequestProtocolOp readDeleteRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      return new DeleteRequestProtocolOp(reader.readOctetString());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_DELETE_REQUEST_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }
  }

  /**
   * Decodes the provided ASN.1 element as a delete response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded delete response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static DeleteResponseProtocolOp readDeleteResponse(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;

    try
    {
      if (reader.hasNextElement())
      {
        reader.readStartSequence();
        referralURLs = new ArrayList<String>();

        while(reader.hasNextElement())
        {
          referralURLs.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new DeleteResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an
   * LDAP extended request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded extended request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP extended request
   *                         protocol op.
   */
  private static ExtendedRequestProtocolOp readExtendedRequest(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_EXTENDED_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    String oid;
    try
    {
      oid = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_EXTENDED_REQUEST_DECODE_OID.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString value = null;
    try
    {
      if(reader.hasNextElement())
      {
        value = reader.readOctetString();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_EXTENDED_REQUEST_DECODE_VALUE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_EXTENDED_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new ExtendedRequestProtocolOp(oid, value);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * extended response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded extended response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static ExtendedResponseProtocolOp readExtendedResponse(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;
    String            oid          = null;
    ByteString   value        = null;

    try
    {
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_REFERRAL_SEQUENCE)
      {
        try
        {
          reader.readStartSequence();
          referralURLs = new ArrayList<String>();

          while(reader.hasNextElement())
          {
            referralURLs.add(reader.readOctetStringAsString());
          }
          reader.readEndSequence();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
      }
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_EXTENDED_RESPONSE_OID)
      {
        try
        {
          oid = reader.readOctetStringAsString();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_EXTENDED_RESULT_DECODE_OID.get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
      }
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_EXTENDED_RESPONSE_VALUE)
      {
        try
        {
          value = reader.readOctetString();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_EXTENDED_RESULT_DECODE_VALUE.get(String.valueOf(e));
          throw new LDAPException(PROTOCOL_ERROR, message, e);
        }
      }
    }
    catch(ASN1Exception asn1e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, asn1e);
      }
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new ExtendedResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs, oid, value);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * intermediate response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded intermediate response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP intermediate
   *                         response protocol op.
   */
  private static IntermediateResponseProtocolOp
  readIntermediateResponse(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_INTERMEDIATE_RESPONSE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    String          oid   = null;
    ByteString value = null;

    try
    {
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_INTERMEDIATE_RESPONSE_OID)
      {
        try
        {
          if(reader.hasNextElement())
          {
            oid = reader.readOctetStringAsString();
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_OID.get(
                  e.getMessage());
          throw new LDAPException(PROTOCOL_ERROR, message);
        }
      }
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_INTERMEDIATE_RESPONSE_VALUE)
      {
        try
        {
          value = reader.readOctetString();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              ERR_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_VALUE.
                  get(e.getMessage());
          throw new LDAPException(PROTOCOL_ERROR, message);
        }
      }
    }
    catch(ASN1Exception asn1e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, asn1e);
      }
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_INTERMEDIATE_RESPONSE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new IntermediateResponseProtocolOp(oid, value);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * modify DN request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded modify DN request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 element as an LDAP modify DN request
   *                         protocol op.
   */
  private static ModifyDNRequestProtocolOp readModifyDNRequest(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_DN_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString entryDN;
    try
    {
      entryDN = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_DN_REQUEST_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString newRDN;
    try
    {
      newRDN = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_RDN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    boolean deleteOldRDN;
    try
    {
      deleteOldRDN = reader.readBoolean();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_MODIFY_DN_REQUEST_DECODE_DELETE_OLD_RDN.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString newSuperior = null;
    try
    {
      if(reader.hasNextElement())
      {
        newSuperior = reader.readOctetString();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_SUPERIOR.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_DN_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new ModifyDNRequestProtocolOp(entryDN, newRDN, deleteOldRDN,
        newSuperior);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * modify DN response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded modify DN response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static ModifyDNResponseProtocolOp readModifyDNResponse(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;

    try
    {
      if (reader.hasNextElement())
      {
        reader.readStartSequence();
        referralURLs = new ArrayList<String>();

        while(reader.hasNextElement())
        {
          referralURLs.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new ModifyDNResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * modify request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded modify request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP modify request protocol
   *                         op.
   */
  private static ModifyRequestProtocolOp readModifyRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ByteString dn;
    try
    {
      dn = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_REQUEST_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }



    ArrayList<RawModification> modifications;
    try
    {
      reader.readStartSequence();
      modifications = new ArrayList<RawModification>();
      while(reader.hasNextElement())
      {
        modifications.add(LDAPModification.decode(reader));
      }
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_REQUEST_DECODE_MODS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFY_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new ModifyRequestProtocolOp(dn, modifications);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a modify
   * response protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded modify response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static ModifyResponseProtocolOp readModifyResponse(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;

    try
    {
      if (reader.hasNextElement())
      {
        reader.readStartSequence();
        referralURLs = new ArrayList<String>();

        while(reader.hasNextElement())
        {
          referralURLs.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new ModifyResponseProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP search
   * request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP search request protocol
   *                         op.
   */
  private static SearchRequestProtocolOp readSearchRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ByteString baseDN;
    try
    {
      baseDN = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_BASE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    SearchScope scope;
    try
    {
      int scopeValue = (int)reader.readInteger();
      switch (scopeValue)
      {
        case SCOPE_BASE_OBJECT:
          scope = SearchScope.BASE_OBJECT;
          break;
        case SCOPE_SINGLE_LEVEL:
          scope = SearchScope.SINGLE_LEVEL;
          break;
        case SCOPE_WHOLE_SUBTREE:
          scope = SearchScope.WHOLE_SUBTREE;
          break;
        case SCOPE_SUBORDINATE_SUBTREE:
          scope = SearchScope.SUBORDINATE_SUBTREE;
          break;
        default:
          Message message =
              ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE.get(scopeValue);
          throw new LDAPException(PROTOCOL_ERROR, message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_SCOPE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DereferencePolicy dereferencePolicy;
    try
    {
      int derefValue = (int)reader.readInteger();
      switch (derefValue)
      {
        case DEREF_NEVER:
          dereferencePolicy = DereferencePolicy.NEVER_DEREF_ALIASES;
          break;
        case DEREF_IN_SEARCHING:
          dereferencePolicy = DereferencePolicy.DEREF_IN_SEARCHING;
          break;
        case DEREF_FINDING_BASE:
          dereferencePolicy = DereferencePolicy.DEREF_FINDING_BASE_OBJECT;
          break;
        case DEREF_ALWAYS:
          dereferencePolicy = DereferencePolicy.DEREF_ALWAYS;
          break;
        default:
          Message message =
              ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF.get(derefValue);
          throw new LDAPException(PROTOCOL_ERROR, message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_DEREF.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    int sizeLimit;
    try
    {
      sizeLimit = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_SIZE_LIMIT.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    int timeLimit;
    try
    {
      timeLimit = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_TIME_LIMIT.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    boolean typesOnly;
    try
    {
      typesOnly = reader.readBoolean();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_TYPES_ONLY.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    RawFilter filter;
    try
    {
      filter = RawFilter.decode(reader);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_FILTER.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    LinkedHashSet<String> attributes;
    try
    {
      reader.readStartSequence();
      attributes = new LinkedHashSet<String>();
      while(reader.hasNextElement())
      {
        attributes.add(reader.readOctetStringAsString());
      }
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_ATTRIBUTES.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REQUEST_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new SearchRequestProtocolOp(baseDN, scope, dereferencePolicy,
        sizeLimit, timeLimit, typesOnly, filter,
        attributes);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a search
   * result done protocol op.
   *
   * @param  reader The ASN.1 reader
   *
   * @return  The decoded search result done protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  private static SearchResultDoneProtocolOp readSearchDone(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    int resultCode;
    try
    {
      resultCode = (int)reader.readInteger();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_RESULT_CODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = reader.readOctetStringAsString();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_MATCHED_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    Message errorMessage;
    try
    {
      errorMessage = Message.raw(reader.readOctetStringAsString());
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_ERROR_MESSAGE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    ArrayList<String> referralURLs = null;

    try
    {
      if (reader.hasNextElement())
      {
        reader.readStartSequence();
        referralURLs = new ArrayList<String>();

        while(reader.hasNextElement())
        {
          referralURLs.add(reader.readOctetStringAsString());
        }
        reader.readEndSequence();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_RESULT_DECODE_REFERRALS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_RESULT_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new SearchResultDoneProtocolOp(resultCode, errorMessage, matchedDN,
        referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP search
   * result entry protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded search result entry protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP search result entry
   *                         protocol op.
   */
  public static SearchResultEntryProtocolOp readSearchEntry(ASN1Reader
      reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_ENTRY_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    DN dn;
    try
    {
      dn = DN.decode(reader.readOctetStringAsString());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_SEARCH_ENTRY_DECODE_DN.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }



    LinkedList<LDAPAttribute> attributes;
    try
    {
      reader.readStartSequence();
      attributes = new LinkedList<LDAPAttribute>();
      while(reader.hasNextElement())
      {
        attributes.add(LDAPAttribute.decode(reader));
      }
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_ENTRY_DECODE_ATTRS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_ENTRY_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new SearchResultEntryProtocolOp(dn, attributes);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a search
   * result reference protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded search result reference protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP search result reference
   *                         protocol op.
   */
  private static SearchResultReferenceProtocolOp
  readSearchReference(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REFERENCE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ArrayList<String> referralURLs = new ArrayList<String>();
    try
    {
      // Should have atleast 1 URL.
      do
      {
        referralURLs.add(reader.readOctetStringAsString());
      }
      while(reader.hasNextElement());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REFERENCE_DECODE_URLS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REFERENCE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new SearchResultReferenceProtocolOp(referralURLs);
  }

  /**
   * Decodes the elements from the provided ASN.1 read as an LDAP unbind
   * request protocol op.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP unbind request protocol op.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be decoded as
   *                         an unbind request protocol op.
   */
  private static UnbindRequestProtocolOp readUnbindRequest(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readNull();
      return new UnbindRequestProtocolOp();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_UNBIND_DECODE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a set of controls.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded set of controls.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         controls.
   */
  private static ArrayList<Control> readControls(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
      ArrayList<Control> controls =
          new ArrayList<Control>();
      while(reader.hasNextElement())
      {
        controls.add(readControl(reader));
      }

      reader.readEndSequence();
      return controls;
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDAP_CONTROL_DECODE_CONTROLS_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP control.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP control.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP control.
   */
  public static LDAPControl readControl(ASN1Reader reader)
      throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_CONTROL_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    String oid;
    try
    {
      oid = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_CONTROL_DECODE_OID.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    boolean isCritical = false;
    ByteString value = null;
    try
    {
      if(reader.hasNextElement() &&
          reader.peekType() == UNIVERSAL_BOOLEAN_TYPE)
      {
        try
        {
          isCritical = reader.readBoolean();
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }

          Message message =
              ERR_LDAP_CONTROL_DECODE_CRITICALITY.get(String.valueOf(e2));
          throw new LDAPException(PROTOCOL_ERROR, message, e2);
        }
      }
      if(reader.hasNextElement() &&
          reader.peekType() == UNIVERSAL_OCTET_STRING_TYPE)
      {
        try
        {
          value = reader.readOctetString();
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }

          Message message =
              ERR_LDAP_CONTROL_DECODE_VALUE.get(String.valueOf(e2));
          throw new LDAPException(PROTOCOL_ERROR, message, e2);
        }
      }
    }
    catch(ASN1Exception asn1e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, asn1e);
      }
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_CONTROL_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new LDAPControl(oid, isCritical, value);
  }
}
