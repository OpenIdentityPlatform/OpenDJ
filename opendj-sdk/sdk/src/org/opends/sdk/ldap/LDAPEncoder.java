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



import static com.sun.opends.sdk.ldap.LDAPConstants.*;

import java.io.IOException;
import java.util.logging.Level;

import org.opends.sdk.Attribute;
import org.opends.sdk.ByteString;
import org.opends.sdk.Change;
import org.opends.sdk.DN;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.controls.Control;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.sasl.SASLBindRequest;

import com.sun.opends.sdk.util.StaticUtils;



/**
 * Static methods for encoding LDAP messages.
 */
public final class LDAPEncoder
{
  public static void encodeControl(ASN1Writer writer, Control control)
      throws IOException
  {
    writer.writeStartSequence();
    writer.writeOctetString(control.getOID());
    if (control.isCritical())
    {
      writer.writeBoolean(control.isCritical());
    }
    if (control.getValue() != null)
    {
      writer.writeOctetString(control.getValue());
    }
    writer.writeEndSequence();
  }



  public static void encodeEntry(ASN1Writer writer,
      SearchResultEntry searchResultEntry) throws IOException
  {
    writer.writeStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
    writer.writeOctetString(searchResultEntry.getName().toString());

    writer.writeStartSequence();
    for (Attribute attr : searchResultEntry.getAttributes())
    {
      encodeAttribute(writer, attr);
    }
    writer.writeEndSequence();
    writer.writeEndSequence();
  }



  public static void encodeAbandonRequest(ASN1Writer writer, int messageID,
      AbandonRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP ABANDON REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer
        .writeInteger(OP_TYPE_ABANDON_REQUEST, request.getMessageID());
    encodeMessageFooter(writer, request);
  }



  public static void encodeAddRequest(ASN1Writer writer, int messageID,
      AddRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP ADD REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_ADD_REQUEST);
    writer.writeOctetString(request.getName().toString());

    // Write the attributes
    writer.writeStartSequence();
    for (Attribute attr : request.getAttributes())
    {
      encodeAttribute(writer, attr);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeCompareRequest(ASN1Writer writer, int messageID,
      CompareRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP COMPARE REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
    writer.writeOctetString(request.getName().toString());

    writer.writeStartSequence();
    writer.writeOctetString(request.getAttributeDescription()
        .toString());
    writer.writeOctetString(request.getAssertionValue());
    writer.writeEndSequence();

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeDeleteRequest(ASN1Writer writer, int messageID,
      DeleteRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP DELETE REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeOctetString(OP_TYPE_DELETE_REQUEST, request.getName()
        .toString());
    encodeMessageFooter(writer, request);
  }



  public static void encodeExtendedRequest(ASN1Writer writer, int messageID,
      ExtendedRequest<?> request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP EXTENDED REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_EXTENDED_REQUEST);
    writer.writeOctetString(TYPE_EXTENDED_REQUEST_OID, request
        .getRequestName());

    ByteString requestValue = request.getRequestValue();
    if (requestValue != null)
    {
      writer
          .writeOctetString(TYPE_EXTENDED_REQUEST_VALUE, requestValue);
    }

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeBindRequest(ASN1Writer writer, int messageID,
      int version, GenericBindRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "ENCODE LDAP BIND REQUEST(messageID=%d, auth=0x%x, request=%s)",
                  messageID, request.getAuthenticationType(), request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_BIND_REQUEST);

    writer.writeInteger(version);
    writer.writeOctetString(request.getName().toString());

    writer.writeOctetString(request.getAuthenticationType(), request
        .getAuthenticationValue());

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeBindRequest(ASN1Writer writer, int messageID,
      int version, SASLBindRequest<?> request,
      ByteString saslCredentials) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "ENCODE LDAP BIND REQUEST(messageID=%d, auth=SASL, request=%s)",
                  messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_BIND_REQUEST);

    writer.writeInteger(version);
    writer.writeOctetString(request.getName().toString());

    writer.writeStartSequence(TYPE_AUTHENTICATION_SASL);
    writer.writeOctetString(request.getSASLMechanism());
    if (saslCredentials != null)
    {
      writer.writeOctetString(saslCredentials);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeBindRequest(ASN1Writer writer, int messageID,
      int version, SimpleBindRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "ENCODE LDAP BIND REQUEST(messageID=%d, auth=simple, request=%s)",
                  messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_BIND_REQUEST);

    writer.writeInteger(version);
    writer.writeOctetString(request.getName().toString());
    writer.writeOctetString(TYPE_AUTHENTICATION_SIMPLE, request
        .getPassword());

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeModifyDNRequest(ASN1Writer writer, int messageID,
      ModifyDNRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP MODIFY DN REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
    writer.writeOctetString(request.getName().toString());
    writer.writeOctetString(request.getNewRDN().toString());
    writer.writeBoolean(request.isDeleteOldRDN());

    DN newSuperior = request.getNewSuperior();
    if (newSuperior != null)
    {
      writer.writeOctetString(TYPE_MODIFY_DN_NEW_SUPERIOR, newSuperior
          .toString());
    }

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeModifyRequest(ASN1Writer writer, int messageID,
      ModifyRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP MODIFY REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_MODIFY_REQUEST);
    writer.writeOctetString(request.getName().toString());

    writer.writeStartSequence();
    for (Change change : request.getChanges())
    {
      encodeChange(writer, change);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeSearchRequest(ASN1Writer writer, int messageID,
      SearchRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP SEARCH REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(request.getName().toString());
    writer.writeEnumerated(request.getScope().intValue());
    writer.writeEnumerated(request.getDereferenceAliasesPolicy()
        .intValue());
    writer.writeInteger(request.getSizeLimit());
    writer.writeInteger(request.getTimeLimit());
    writer.writeBoolean(request.isTypesOnly());
    LDAPUtils.encodeFilter(writer, request.getFilter());

    writer.writeStartSequence();
    for (String attribute : request.getAttributes())
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
    encodeMessageFooter(writer, request);
  }



  public static void encodeUnbindRequest(ASN1Writer writer, int messageID,
      UnbindRequest request) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP UNBIND REQUEST(messageID=%d, request=%s)",
          messageID, request));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeNull(OP_TYPE_UNBIND_REQUEST);
    encodeMessageFooter(writer, request);
  }



  public static void encodeAddResult(ASN1Writer writer, int messageID,
      Result result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP ADD RESULT(messageID=%d, result=%s)", messageID,
          result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_ADD_RESPONSE, result);
    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeBindResult(ASN1Writer writer, int messageID,
      BindResult result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP BIND RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_BIND_RESPONSE, result);

    if (result.getServerSASLCredentials().length() > 0)
    {
      writer.writeOctetString(TYPE_SERVER_SASL_CREDENTIALS, result
          .getServerSASLCredentials());
    }

    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeCompareResult(ASN1Writer writer, int messageID,
      CompareResult result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP COMPARE RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_COMPARE_RESPONSE, result);
    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeDeleteResult(ASN1Writer writer, int messageID,
      Result result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP DELETE RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_DELETE_RESPONSE, result);
    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeExtendedResult(ASN1Writer writer, int messageID,
      ExtendedResult result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP EXTENDED RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_EXTENDED_RESPONSE, result);

    String responseName = result.getResponseName();
    ByteString responseValue = result.getResponseValue();

    if (responseName != null)
    {
      writer.writeOctetString(TYPE_EXTENDED_RESPONSE_OID, responseName);
    }

    if (responseValue != null)
    {
      writer.writeOctetString(TYPE_EXTENDED_RESPONSE_VALUE,
          responseValue);
    }

    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeIntermediateResponse(ASN1Writer writer,
      int messageID, IntermediateResponse response) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "ENCODE LDAP INTERMEDIATE RESPONSE(messageID=%d, response=%s)",
                  messageID, response));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_INTERMEDIATE_RESPONSE);

    String responseName = response.getResponseName();
    ByteString responseValue = response.getResponseValue();

    if (responseName != null)
    {
      writer.writeOctetString(TYPE_INTERMEDIATE_RESPONSE_OID, response
          .getResponseName());
    }

    if (responseValue != null)
    {
      writer.writeOctetString(TYPE_INTERMEDIATE_RESPONSE_VALUE,
          response.getResponseValue());
    }

    writer.writeEndSequence();
    encodeMessageFooter(writer, response);
  }



  public static void encodeModifyDNResult(ASN1Writer writer, int messageID,
      Result result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP MODIFY DN RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_MODIFY_DN_RESPONSE, result);
    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeModifyResult(ASN1Writer writer, int messageID,
      Result result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP MODIFY RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_MODIFY_RESPONSE, result);
    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeSearchResult(ASN1Writer writer, int messageID,
      Result result) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP SEARCH RESULT(messageID=%d, result=%s)",
          messageID, result));
    }
    encodeMessageHeader(writer, messageID);
    encodeResultHeader(writer, OP_TYPE_SEARCH_RESULT_DONE, result);
    encodeResultFooter(writer);
    encodeMessageFooter(writer, result);
  }



  public static void encodeSearchResultEntry(ASN1Writer writer, int messageID,
      SearchResultEntry entry) throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG.finer(String.format(
          "ENCODE LDAP SEARCH RESULT ENTRY(messageID=%d, entry=%s)",
          messageID, entry));
    }
    encodeMessageHeader(writer, messageID);
    encodeEntry(writer, entry);
    encodeMessageFooter(writer, entry);
  }



  public static void encodeSearchResultReference(ASN1Writer writer,
      int messageID, SearchResultReference reference)
      throws IOException
  {
    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER))
    {
      StaticUtils.DEBUG_LOG
          .finer(String
              .format(
                  "ENCODE LDAP SEARCH RESULT REFERENCE(messageID=%d, reference=%s)",
                  messageID, reference));
    }
    encodeMessageHeader(writer, messageID);
    writer.writeStartSequence(OP_TYPE_SEARCH_RESULT_REFERENCE);
    for (String url : reference.getURIs())
    {
      writer.writeOctetString(url);
    }
    writer.writeEndSequence();
    encodeMessageFooter(writer, reference);
  }



  private static void encodeAttribute(ASN1Writer writer, Attribute attribute)
      throws IOException
  {
    writer.writeStartSequence();
    writer
        .writeOctetString(attribute.getAttributeDescriptionAsString());

    writer.writeStartSet();
    for (ByteString value : attribute)
    {
      writer.writeOctetString(value);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }



  private static void encodeChange(ASN1Writer writer, Change change)
      throws IOException
  {
    writer.writeStartSequence();
    writer.writeEnumerated(change.getModificationType().intValue());
    encodeAttribute(writer, change.getAttribute());
    writer.writeEndSequence();
  }



  private static void encodeMessageFooter(ASN1Writer writer,
      Request request) throws IOException
  {
    if (request.hasControls())
    {
      writer.writeStartSequence(TYPE_CONTROL_SEQUENCE);
      for (Control control : request.getControls())
      {
        encodeControl(writer, control);
      }
      writer.writeEndSequence();
    }

    writer.writeEndSequence();
  }



  private static void encodeMessageFooter(ASN1Writer writer,
      Response response) throws IOException
  {
    if (response.hasControls())
    {
      writer.writeStartSequence(TYPE_CONTROL_SEQUENCE);
      for (Control control : response.getControls())
      {
        encodeControl(writer, control);
      }
      writer.writeEndSequence();
    }

    writer.writeEndSequence();
  }



  private static void encodeMessageHeader(ASN1Writer writer,
      int messageID) throws IOException
  {
    writer.writeStartSequence();
    writer.writeInteger(messageID);
  }



  private static void encodeResultFooter(ASN1Writer writer)
      throws IOException
  {
    writer.writeEndSequence();
  }



  private static void encodeResultHeader(ASN1Writer writer,
      byte typeTag, Result rawMessage) throws IOException
  {
    writer.writeStartSequence(typeTag);
    writer.writeEnumerated(rawMessage.getResultCode().intValue());
    writer.writeOctetString(rawMessage.getMatchedDN());
    writer.writeOctetString(rawMessage.getDiagnosticMessage());

    if (rawMessage.hasReferralURIs())
    {
      writer.writeStartSequence(TYPE_REFERRAL_SEQUENCE);
      for (String s : rawMessage.getReferralURIs())
      {
        writer.writeOctetString(s);
      }
      writer.writeEndSequence();
    }
  }
}
