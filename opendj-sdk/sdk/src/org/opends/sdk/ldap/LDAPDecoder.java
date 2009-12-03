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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldap;



import static com.sun.opends.sdk.util.Messages.*;
import static org.opends.sdk.asn1.ASN1Constants.*;
import static org.opends.sdk.ldap.LDAPConstants.*;

import java.io.IOException;
import java.util.logging.Level;

import org.opends.sdk.*;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.controls.Control;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.sasl.GenericSASLBindRequest;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.StaticUtils;



/**
 * Static methods for decoding LDAP messages.
 */
class LDAPDecoder
{

  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * message.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle a
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  static void decode(ASN1Reader reader, LDAPMessageHandler handler)
      throws IOException
  {
    reader.readStartSequence();
    try
    {
      int messageID = (int) reader.readInteger();
      decodeProtocolOp(reader, messageID, handler);
    }
    finally
    {
      reader.readEndSequence();
    }
  }



  static SearchResultEntry decodeEntry(ASN1Reader reader, Schema schema)
      throws IOException
  {
    SearchResultEntry message;

    reader.readStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
    try
    {
      String dnString = reader.readOctetStringAsString();
      DN dn;
      try
      {
        dn = DN.valueOf(dnString, schema);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }
      message = Responses.newSearchResultEntry(dn);

      reader.readStartSequence();
      try
      {
        while (reader.hasNextElement())
        {
          reader.readStartSequence();
          try
          {
            String ads = reader.readOctetStringAsString();
            AttributeDescription ad;
            try
            {
              ad = AttributeDescription.valueOf(ads, schema);
            }
            catch (LocalizedIllegalArgumentException e)
            {
              throw DecodeException.error(e.getMessageObject());
            }
            Attribute attribute = new LinkedAttribute(ad);

            reader.readStartSet();
            try
            {
              while (reader.hasNextElement())
              {
                attribute.add(reader.readOctetString());
              }
              message.addAttribute(attribute);
            }
            finally
            {
              reader.readEndSet();
            }
          }
          finally
          {
            reader.readEndSequence();
          }
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    return message;
  }



  /**
   * Decodes the elements from the provided ASN.1 read as an LDAP
   * abandon request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeAbandonRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    int msgToAbandon = (int) reader
        .readInteger(OP_TYPE_ABANDON_REQUEST);
    AbandonRequest message = Requests.newAbandonRequest(msgToAbandon);

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP ABANDON REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleAbandonRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP add
   * request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeAddRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    AddRequest message;
    ResolvedSchema resolvedSchema;

    reader.readStartSequence(OP_TYPE_ADD_REQUEST);
    try
    {
      String dnString = reader.readOctetStringAsString();
      resolvedSchema = handler.resolveSchema(dnString);
      DN dn = resolvedSchema.getInitialDN();
      message = Requests.newAddRequest(dn);

      reader.readStartSequence();
      try
      {
        while (reader.hasNextElement())
        {
          reader.readStartSequence();
          try
          {
            String ads = reader.readOctetStringAsString();
            AttributeDescription ad = resolvedSchema
                .decodeAttributeDescription(ads);
            Attribute attribute = new LinkedAttribute(ad);

            reader.readStartSet();
            try
            {
              while (reader.hasNextElement())
              {
                attribute.add(reader.readOctetString());
              }
              message.addAttribute(attribute);
            }
            finally
            {
              reader.readEndSet();
            }
          }
          finally
          {
            reader.readEndSequence();
          }
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP ADD REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleAddRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an add
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeAddResult(ASN1Reader reader, int messageID,
      LDAPMessageHandler handler) throws IOException
  {
    Result message;

    reader.readStartSequence(OP_TYPE_ADD_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP ADD RESULT(messageID=%d, result=%s)", messageID,
          message));
    }

    handler.handleAddResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 read as an LDAP bind
   * request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeBindRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    reader.readStartSequence(OP_TYPE_BIND_REQUEST);
    try
    {
      int protocolVersion = (int) reader.readInteger();

      String dnString = reader.readOctetStringAsString();
      ResolvedSchema resolvedSchema = handler.resolveSchema(dnString);
      DN dn = resolvedSchema.getInitialDN();

      byte type = reader.peekType();

      switch (type)
      {
      case TYPE_AUTHENTICATION_SIMPLE:
        ByteString simplePassword = reader
            .readOctetString(TYPE_AUTHENTICATION_SIMPLE);

        SimpleBindRequest simpleBindMessage = Requests.newSimpleBindRequest(dn, simplePassword);

        decodeControls(reader, simpleBindMessage, messageID, handler,
            resolvedSchema.getSchema());

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
        {
          StaticUtils.DEBUG_LOG
              .finer(String
                  .format(
                      "DECODE LDAP BIND REQUEST(messageID=%d, auth=simple, request=%s)",
                      messageID, simpleBindMessage));
        }

        handler.handleBindRequest(messageID, protocolVersion,
            simpleBindMessage);
        break;
      case TYPE_AUTHENTICATION_SASL:
        String saslMechanism;
        ByteString saslCredentials;

        reader.readStartSequence(TYPE_AUTHENTICATION_SASL);
        try
        {
          saslMechanism = reader.readOctetStringAsString();
          if (reader.hasNextElement()
              && (reader.peekType() == UNIVERSAL_OCTET_STRING_TYPE))
          {
            saslCredentials = reader.readOctetString();
          }
          else
          {
            saslCredentials = ByteString.empty();
          }
        }
        finally
        {
          reader.readEndSequence();
        }

        GenericSASLBindRequest rawSASLBindMessage = new GenericSASLBindRequest(
            saslMechanism, saslCredentials);

        // TODO: we can ignore the bind DN for SASL bind requests
        // according to the RFC.
        //
        // rawSASLBindMessage.setName(dn);

        decodeControls(reader, rawSASLBindMessage, messageID, handler,
            resolvedSchema.getSchema());

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
        {
          StaticUtils.DEBUG_LOG
              .finer(String
                  .format(
                      "DECODE LDAP BIND REQUEST(messageID=%d, auth=SASL, request=%s)",
                      messageID, rawSASLBindMessage));
        }

        handler.handleBindRequest(messageID, protocolVersion,
            rawSASLBindMessage);
        break;
      default:
        ByteString unknownAuthBytes = reader.readOctetString(type);

        GenericBindRequest rawUnknownBindMessage = Requests.newGenericBindRequest(dn, type, unknownAuthBytes);

        decodeControls(reader, rawUnknownBindMessage, messageID,
            handler, resolvedSchema.getSchema());

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
        {
          StaticUtils.DEBUG_LOG
              .finer(String
                  .format(
                      "DECODE LDAP BIND REQUEST(messageID=%d, auth=0x%x, request=%s)",
                      messageID, rawUnknownBindMessage
                          .getAuthenticationType(),
                      rawUnknownBindMessage));
        }

        handler.handleBindRequest(messageID, protocolVersion,
            rawUnknownBindMessage);
      }
    }
    finally
    {
      reader.readEndSequence();
    }
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a bind
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeBindResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    BindResult message;

    reader.readStartSequence(OP_TYPE_BIND_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newBindResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_SERVER_SASL_CREDENTIALS))
      {
        message.setServerSASLCredentials(reader
            .readOctetString(TYPE_SERVER_SASL_CREDENTIALS));
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP BIND RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleBindResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * compare request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeCompareRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    DN dn;
    AttributeDescription ad;
    ByteString assertionValue;
    ResolvedSchema resolvedSchema;

    reader.readStartSequence(OP_TYPE_COMPARE_REQUEST);
    try
    {
      String dnString = reader.readOctetStringAsString();
      resolvedSchema = handler.resolveSchema(dnString);
      dn = resolvedSchema.getInitialDN();

      reader.readStartSequence();
      try
      {
        String ads = reader.readOctetStringAsString();
        ad = resolvedSchema.decodeAttributeDescription(ads);
        assertionValue = reader.readOctetString();
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    CompareRequest message = Requests.newCompareRequest(dn, ad, assertionValue);
    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP COMPARE REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleCompareRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a compare
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeCompareResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    CompareResult message;

    reader.readStartSequence(OP_TYPE_COMPARE_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newCompareResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP COMPARE RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleCompareResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * control.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param request
   *          The decoded request to decode controls for.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will decode the
   *          control.
   * @param schema
   *          The schema to use when decoding control.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeControl(ASN1Reader reader, Request request,
      int messageID, LDAPMessageHandler handler, Schema schema)
      throws IOException
  {
    String oid;
    boolean isCritical;
    ByteString value;

    reader.readStartSequence();
    try
    {
      oid = reader.readOctetStringAsString();
      isCritical = false;
      value = null;
      if (reader.hasNextElement()
          && (reader.peekType() == UNIVERSAL_BOOLEAN_TYPE))
      {
        isCritical = reader.readBoolean();
      }
      if (reader.hasNextElement()
          && (reader.peekType() == UNIVERSAL_OCTET_STRING_TYPE))
      {
        value = reader.readOctetString();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    try
    {
      Control c = handler.decodeRequestControl(messageID, oid,
          isCritical, value, schema);
      request.addControl(c);
    }
    catch (DecodeException e)
    {
      if (isCritical)
      {
        if (e.isFatal())
        {
          throw DecodeException.error(e.getMessageObject(), e
              .getCause());
        }
        else
        {
          // Exceptions encountered when decoding controls are never
          // fatal.
          throw e;
        }
      }
    }
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * control.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param response
   *          The decoded message to decode controls for.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will decode the
   *          control.
   * @param schema
   *          The schema to use when decoding control.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeControl(ASN1Reader reader,
      Response response, int messageID, LDAPMessageHandler handler,
      Schema schema) throws IOException
  {
    String oid;
    boolean isCritical;
    ByteString value;

    reader.readStartSequence();
    try
    {
      oid = reader.readOctetStringAsString();
      isCritical = false;
      value = null;
      if (reader.hasNextElement()
          && (reader.peekType() == UNIVERSAL_BOOLEAN_TYPE))
      {
        isCritical = reader.readBoolean();
      }
      if (reader.hasNextElement()
          && (reader.peekType() == UNIVERSAL_OCTET_STRING_TYPE))
      {
        value = reader.readOctetString();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    try
    {
      Control c = handler.decodeResponseControl(messageID, oid,
          isCritical, value, schema);
      response.addControl(c);
    }
    catch (DecodeException e)
    {
      if (isCritical)
      {
        if (e.isFatal())
        {
          throw DecodeException.error(e.getMessageObject(), e
              .getCause());
        }
        else
        {
          // Exceptions encountered when decoding controls are never
          // fatal.
          throw e;
        }
      }
    }
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a set of
   * controls using the default schema.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param request
   *          The decoded message to decode controls for.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will decode the
   *          controls.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeControls(ASN1Reader reader,
      Request request, int messageID, LDAPMessageHandler handler)
      throws IOException
  {
    decodeControls(reader, request, messageID, handler, handler
        .getDefaultSchema());
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a set of
   * controls.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param request
   *          The decoded message to decode controls for.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will decode the
   *          controls.
   * @param schema
   *          The schema to use when decoding controls.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeControls(ASN1Reader reader,
      Request request, int messageID, LDAPMessageHandler handler,
      Schema schema) throws IOException
  {
    if (reader.hasNextElement()
        && (reader.peekType() == TYPE_CONTROL_SEQUENCE))
    {
      reader.readStartSequence(TYPE_CONTROL_SEQUENCE);
      try
      {
        while (reader.hasNextElement())
        {
          decodeControl(reader, request, messageID, handler, schema);
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a set of
   * controls using the default schema.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param response
   *          The decoded message to decode controls for.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will decode the
   *          controls.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeControls(ASN1Reader reader,
      Response response, int messageID, LDAPMessageHandler handler)
      throws IOException
  {
    decodeControls(reader, response, messageID, handler, handler
        .getDefaultSchema());
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a set of
   * controls.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param response
   *          The decoded message to decode controls for.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will decode the
   *          controls.
   * @param schema
   *          The schema to use when decoding control.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeControls(ASN1Reader reader,
      Response response, int messageID, LDAPMessageHandler handler,
      Schema schema) throws IOException
  {
    if (reader.hasNextElement()
        && (reader.peekType() == TYPE_CONTROL_SEQUENCE))
    {
      reader.readStartSequence(TYPE_CONTROL_SEQUENCE);
      try
      {
        while (reader.hasNextElement())
        {
          decodeControl(reader, response, messageID, handler, schema);
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * delete request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeDeleteRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    String dnString = reader
        .readOctetStringAsString(OP_TYPE_DELETE_REQUEST);
    ResolvedSchema resolvedSchema = handler.resolveSchema(dnString);
    DN dn = resolvedSchema.getInitialDN();
    DeleteRequest message = Requests.newDeleteRequest(dn);

    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP DELETE REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleDeleteRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a delete
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeDeleteResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    Result message;

    reader.readStartSequence(OP_TYPE_DELETE_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP DELETE RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleDeleteResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * extended request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeExtendedRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    String oid;
    ByteString value;

    reader.readStartSequence(OP_TYPE_EXTENDED_REQUEST);
    try
    {
      oid = reader.readOctetStringAsString(TYPE_EXTENDED_REQUEST_OID);
      value = null;
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_EXTENDED_REQUEST_VALUE))
      {
        value = reader.readOctetString(TYPE_EXTENDED_REQUEST_VALUE);
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    GenericExtendedRequest message = Requests.newGenericExtendedRequest(oid, value);

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP EXTENDED REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleExtendedRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a extended
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeExtendedResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {

    GenericExtendedResult message;

    reader.readStartSequence(OP_TYPE_EXTENDED_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newGenericExtendedResult(resultCode).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_EXTENDED_RESPONSE_OID))
      {
        message.setResponseName(reader
            .readOctetStringAsString(TYPE_EXTENDED_RESPONSE_OID));
      }
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_EXTENDED_RESPONSE_VALUE))
      {
        message.setResponseValue(reader
            .readOctetString(TYPE_EXTENDED_RESPONSE_VALUE));
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP EXTENDED RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleExtendedResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * intermediate response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeIntermediateResponse(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    GenericIntermediateResponse message;

    reader.readStartSequence(OP_TYPE_INTERMEDIATE_RESPONSE);
    try
    {
      message = Responses.newGenericIntermediateResponse();
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_INTERMEDIATE_RESPONSE_OID))
      {
        message.setResponseName(reader
            .readOctetStringAsString(TYPE_INTERMEDIATE_RESPONSE_OID));
      }
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_INTERMEDIATE_RESPONSE_VALUE))
      {
        message.setResponseValue(reader
            .readOctetString(TYPE_INTERMEDIATE_RESPONSE_VALUE));
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "DECODE LDAP INTERMEDIATE RESPONSE(messageID=%d, response=%s)",
                  messageID, message));
    }

    handler.handleIntermediateResponse(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a modify DN
   * request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeModifyDNRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    ModifyDNRequest message;
    ResolvedSchema resolvedSchema;

    reader.readStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
    try
    {
      String dnString = reader.readOctetStringAsString();
      resolvedSchema = handler.resolveSchema(dnString);
      DN dn = resolvedSchema.getInitialDN();

      String newRDNString = reader.readOctetStringAsString();
      RDN newRDN = resolvedSchema.decodeRDN(newRDNString);

      message = Requests.newModifyDNRequest(dn, newRDN);

      message.setDeleteOldRDN(reader.readBoolean());

      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_MODIFY_DN_NEW_SUPERIOR))
      {
        String newSuperiorString = reader
            .readOctetStringAsString(TYPE_MODIFY_DN_NEW_SUPERIOR);
        DN newSuperior = resolvedSchema.decodeDN(newSuperiorString);
        message.setNewSuperior(newSuperior);
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP MODIFY DN REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleModifyDNRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a modify DN
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeModifyDNResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    Result message;

    reader.readStartSequence(OP_TYPE_MODIFY_DN_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP MODIFY DN RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleModifyDNResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * modify request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeModifyRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    ModifyRequest message;
    ResolvedSchema resolvedSchema;

    reader.readStartSequence(OP_TYPE_MODIFY_REQUEST);
    try
    {
      String dnString = reader.readOctetStringAsString();
      resolvedSchema = handler.resolveSchema(dnString);
      DN dn = resolvedSchema.getInitialDN();
      message = Requests.newModifyRequest(dn);

      reader.readStartSequence();
      try
      {
        while (reader.hasNextElement())
        {
          reader.readStartSequence();
          try
          {
            int typeIntValue = reader.readEnumerated();
            ModificationType type = ModificationType
                .valueOf(typeIntValue);
            if (type == null)
            {
              throw DecodeException
                  .error(ERR_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE
                      .get(typeIntValue));
            }
            reader.readStartSequence();
            try
            {
              String attributeDescription = reader
                  .readOctetStringAsString();
              Attribute attribute = new LinkedAttribute(resolvedSchema
                  .decodeAttributeDescription(attributeDescription));

              reader.readStartSet();
              try
              {
                while (reader.hasNextElement())
                {
                  attribute.add(reader.readOctetString());
                }
                message.addChange(new Change(type, attribute));
              }
              finally
              {
                reader.readEndSet();
              }
            }
            finally
            {
              reader.readEndSequence();
            }
          }
          finally
          {
            reader.readEndSequence();
          }
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP MODIFY REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleModifyRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a modify
   * response protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeModifyResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    Result message;

    reader.readStartSequence(OP_TYPE_MODIFY_RESPONSE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP MODIFY RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleModifyResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeProtocolOp(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    byte type = reader.peekType();

    switch (type)
    {
    case OP_TYPE_UNBIND_REQUEST: // 0x42
      decodeUnbindRequest(reader, messageID, handler);
      break;
    case 0x43: // 0x43
    case 0x44: // 0x44
    case 0x45: // 0x45
    case 0x46: // 0x46
    case 0x47: // 0x47
    case 0x48: // 0x48
    case 0x49: // 0x49
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_DELETE_REQUEST: // 0x4A
      decodeDeleteRequest(reader, messageID, handler);
      break;
    case 0x4B: // 0x4B
    case 0x4C: // 0x4C
    case 0x4D: // 0x4D
    case 0x4E: // 0x4E
    case 0x4F: // 0x4F
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_ABANDON_REQUEST: // 0x50
      decodeAbandonRequest(reader, messageID, handler);
      break;
    case 0x51: // 0x51
    case 0x52: // 0x52
    case 0x53: // 0x53
    case 0x54: // 0x54
    case 0x55: // 0x55
    case 0x56: // 0x56
    case 0x57: // 0x57
    case 0x58: // 0x58
    case 0x59: // 0x59
    case 0x5A: // 0x5A
    case 0x5B: // 0x5B
    case 0x5C: // 0x5C
    case 0x5D: // 0x5D
    case 0x5E: // 0x5E
    case 0x5F: // 0x5F
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_BIND_REQUEST: // 0x60
      decodeBindRequest(reader, messageID, handler);
      break;
    case OP_TYPE_BIND_RESPONSE: // 0x61
      decodeBindResult(reader, messageID, handler);
      break;
    case 0x62: // 0x62
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_SEARCH_REQUEST: // 0x63
      decodeSearchRequest(reader, messageID, handler);
      break;
    case OP_TYPE_SEARCH_RESULT_ENTRY: // 0x64
      decodeSearchResultEntry(reader, messageID, handler);
      break;
    case OP_TYPE_SEARCH_RESULT_DONE: // 0x65
      decodeSearchResult(reader, messageID, handler);
      break;
    case OP_TYPE_MODIFY_REQUEST: // 0x66
      decodeModifyRequest(reader, messageID, handler);
      break;
    case OP_TYPE_MODIFY_RESPONSE: // 0x67
      decodeModifyResult(reader, messageID, handler);
      break;
    case OP_TYPE_ADD_REQUEST: // 0x68
      decodeAddRequest(reader, messageID, handler);
      break;
    case OP_TYPE_ADD_RESPONSE: // 0x69
      decodeAddResult(reader, messageID, handler);
      break;
    case 0x6A: // 0x6A
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_DELETE_RESPONSE: // 0x6B
      decodeDeleteResult(reader, messageID, handler);
      break;
    case OP_TYPE_MODIFY_DN_REQUEST: // 0x6C
      decodeModifyDNRequest(reader, messageID, handler);
      break;
    case OP_TYPE_MODIFY_DN_RESPONSE: // 0x6D
      decodeModifyDNResult(reader, messageID, handler);
      break;
    case OP_TYPE_COMPARE_REQUEST: // 0x6E
      decodeCompareRequest(reader, messageID, handler);
      break;
    case OP_TYPE_COMPARE_RESPONSE: // 0x6F
      decodeCompareResult(reader, messageID, handler);
      break;
    case 0x70: // 0x70
    case 0x71: // 0x71
    case 0x72: // 0x72
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_SEARCH_RESULT_REFERENCE: // 0x73
      decodeSearchResultReference(reader, messageID, handler);
      break;
    case 0x74: // 0x74
    case 0x75: // 0x75
    case 0x76: // 0x76
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    case OP_TYPE_EXTENDED_REQUEST: // 0x77
      decodeExtendedRequest(reader, messageID, handler);
      break;
    case OP_TYPE_EXTENDED_RESPONSE: // 0x78
      decodeExtendedResult(reader, messageID, handler);
      break;
    case OP_TYPE_INTERMEDIATE_RESPONSE: // 0x79
      decodeIntermediateResponse(reader, messageID, handler);
      break;
    default:
      handler.handleUnrecognizedMessage(messageID, type, reader
          .readOctetString(type));
      break;
    }
  }



  private static void decodeResponseReferrals(ASN1Reader reader,
      Result message) throws IOException
  {
    if (reader.hasNextElement()
        && (reader.peekType() == TYPE_REFERRAL_SEQUENCE))
    {
      reader.readStartSequence(TYPE_REFERRAL_SEQUENCE);
      try
      {
        // Should have at least 1.
        do
        {
          message.addReferralURI((reader.readOctetStringAsString()));
        } while (reader.hasNextElement());
      }
      finally
      {
        reader.readEndSequence();
      }
    }
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a search
   * result done protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeSearchResult(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {

    Result message;

    reader.readStartSequence(OP_TYPE_SEARCH_RESULT_DONE);
    try
    {
      ResultCode resultCode = ResultCode.valueOf(reader
          .readEnumerated());
      String matchedDN = reader.readOctetStringAsString();
      String diagnosticMessage = reader.readOctetStringAsString();
      message = Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
      decodeResponseReferrals(reader, message);
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP SEARCH RESULT(messageID=%d, result=%s)",
          messageID, message));
    }

    handler.handleSearchResult(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * search result entry protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeSearchResultEntry(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    SearchResultEntry message;
    ResolvedSchema resolvedSchema;

    reader.readStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
    try
    {
      String dnString = reader.readOctetStringAsString();
      resolvedSchema = handler.resolveSchema(dnString);
      DN dn = resolvedSchema.getInitialDN();
      message = Responses.newSearchResultEntry(dn);

      reader.readStartSequence();
      try
      {
        while (reader.hasNextElement())
        {
          reader.readStartSequence();
          try
          {
            String ads = reader.readOctetStringAsString();
            AttributeDescription ad = resolvedSchema
                .decodeAttributeDescription(ads);
            Attribute attribute = new LinkedAttribute(ad);

            reader.readStartSet();
            try
            {
              while (reader.hasNextElement())
              {
                attribute.add(reader.readOctetString());
              }
              message.addAttribute(attribute);
            }
            finally
            {
              reader.readEndSet();
            }
          }
          finally
          {
            reader.readEndSequence();
          }
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP SEARCH RESULT ENTRY(messageID=%d, entry=%s)",
          messageID, message));
    }

    handler.handleSearchResultEntry(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as a search
   * result reference protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeSearchResultReference(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    SearchResultReference message;

    reader.readStartSequence(OP_TYPE_SEARCH_RESULT_REFERENCE);
    try
    {
      message = Responses.newSearchResultReference(reader
          .readOctetStringAsString());
      while (reader.hasNextElement())
      {
        message.addURI(reader.readOctetStringAsString());
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "DECODE LDAP SEARCH RESULT REFERENCE(messageID=%d, reference=%s)",
                  messageID, message));
    }

    handler.handleSearchResultReference(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 reader as an LDAP
   * search request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeSearchRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    SearchRequest message;
    ResolvedSchema resolvedSchema;

    reader.readStartSequence(OP_TYPE_SEARCH_REQUEST);
    try
    {
      String baseDNString = reader.readOctetStringAsString();
      resolvedSchema = handler.resolveSchema(baseDNString);
      DN baseDN = resolvedSchema.getInitialDN();

      int scopeIntValue = reader.readEnumerated();
      SearchScope scope = SearchScope.valueOf(scopeIntValue);
      if (scope == null)
      {
        throw DecodeException
            .error(ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE
                .get(scopeIntValue));
      }

      int dereferencePolicyIntValue = reader.readEnumerated();
      DereferenceAliasesPolicy dereferencePolicy = DereferenceAliasesPolicy
          .valueOf(dereferencePolicyIntValue);
      if (dereferencePolicy == null)
      {
        throw DecodeException
            .error(ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF
                .get(dereferencePolicyIntValue));
      }

      int sizeLimit = (int) reader.readInteger();
      int timeLimit = (int) reader.readInteger();
      boolean typesOnly = reader.readBoolean();
      Filter filter = LDAPUtils.decodeFilter(reader);

      message = Requests.newSearchRequest(baseDN, scope, filter);
      message.setDereferenceAliasesPolicy(dereferencePolicy);
      try
      {
        message.setTimeLimit(timeLimit);
        message.setSizeLimit(sizeLimit);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }
      message.setTypesOnly(typesOnly);

      reader.readStartSequence();
      try
      {
        while (reader.hasNextElement())
        {
          message.addAttribute(reader.readOctetStringAsString());
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    decodeControls(reader, message, messageID, handler, resolvedSchema
        .getSchema());

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP SEARCH REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleSearchRequest(messageID, message);
  }



  /**
   * Decodes the elements from the provided ASN.1 read as an LDAP unbind
   * request protocol op.
   *
   * @param reader
   *          The ASN.1 reader.
   * @param messageID
   *          The decoded message ID for this message.
   * @param handler
   *          The <code>LDAPMessageHandler</code> that will handle this
   *          decoded message.
   * @throws IOException
   *           If an error occurred while reading bytes to decode.
   */
  private static void decodeUnbindRequest(ASN1Reader reader,
      int messageID, LDAPMessageHandler handler) throws IOException
  {
    UnbindRequest message;
    reader.readNull(OP_TYPE_UNBIND_REQUEST);
    message = Requests.newUnbindRequest();

    decodeControls(reader, message, messageID, handler);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "DECODE LDAP UNBIND REQUEST(messageID=%d, request=%s)",
          messageID, message));
    }

    handler.handleUnbindRequest(messageID, message);
  }
}
