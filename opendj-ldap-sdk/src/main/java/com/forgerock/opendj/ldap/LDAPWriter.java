/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.LDAPConstants.*;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import org.forgerock.opendj.asn1.ASN1Writer;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Static methods for encoding LDAP messages.
 */
final class LDAPWriter implements LDAPMessageHandler<ASN1Writer> {
    public static void encodeControl(final ASN1Writer writer, final Control control)
            throws IOException {
        writer.writeStartSequence();
        writer.writeOctetString(control.getOID());
        if (control.isCritical()) {
            writer.writeBoolean(control.isCritical());
        }
        if (control.getValue() != null) {
            writer.writeOctetString(control.getValue());
        }
        writer.writeEndSequence();
    }

    public static void encodeEntry(final ASN1Writer writer,
            final SearchResultEntry searchResultEntry) throws IOException {
        writer.writeStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
        writer.writeOctetString(searchResultEntry.getName().toString());

        writer.writeStartSequence();
        for (final Attribute attr : searchResultEntry.getAllAttributes()) {
            encodeAttribute(writer, attr);
        }
        writer.writeEndSequence();
        writer.writeEndSequence();
    }

    private static void encodeAttribute(final ASN1Writer writer, final Attribute attribute)
            throws IOException {
        writer.writeStartSequence();
        writer.writeOctetString(attribute.getAttributeDescriptionAsString());

        writer.writeStartSet();
        for (final ByteString value : attribute) {
            writer.writeOctetString(value);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
    }

    private static void encodeChange(final ASN1Writer writer, final Modification change)
            throws IOException {
        writer.writeStartSequence();
        writer.writeEnumerated(change.getModificationType().intValue());
        encodeAttribute(writer, change.getAttribute());
        writer.writeEndSequence();
    }

    private static void encodeMessageFooter(final ASN1Writer writer, final Request request)
            throws IOException {
        final List<Control> controls = request.getControls();
        if (!controls.isEmpty()) {
            writer.writeStartSequence(TYPE_CONTROL_SEQUENCE);
            for (final Control control : controls) {
                encodeControl(writer, control);
            }
            writer.writeEndSequence();
        }

        writer.writeEndSequence();
    }

    private static void encodeMessageFooter(final ASN1Writer writer, final Response response)
            throws IOException {
        final List<Control> controls = response.getControls();
        if (!controls.isEmpty()) {
            writer.writeStartSequence(TYPE_CONTROL_SEQUENCE);
            for (final Control control : controls) {
                encodeControl(writer, control);
            }
            writer.writeEndSequence();
        }

        writer.writeEndSequence();
    }

    private static void encodeMessageHeader(final ASN1Writer writer, final int messageID)
            throws IOException {
        writer.writeStartSequence();
        writer.writeInteger(messageID);
    }

    private static void encodeResultFooter(final ASN1Writer writer) throws IOException {
        writer.writeEndSequence();
    }

    private static void encodeResultHeader(final ASN1Writer writer, final byte typeTag,
            final Result rawMessage) throws IOException {
        writer.writeStartSequence(typeTag);
        writer.writeEnumerated(rawMessage.getResultCode().intValue());
        writer.writeOctetString(rawMessage.getMatchedDN());
        writer.writeOctetString(rawMessage.getDiagnosticMessage());

        final List<String> referralURIs = rawMessage.getReferralURIs();
        if (!referralURIs.isEmpty()) {
            writer.writeStartSequence(TYPE_REFERRAL_SEQUENCE);
            for (final String s : referralURIs) {
                writer.writeOctetString(s);
            }
            writer.writeEndSequence();
        }
    }

    public void abandonRequest(final ASN1Writer writer, final int messageID,
            final AbandonRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP ABANDON REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeInteger(OP_TYPE_ABANDON_REQUEST, request.getRequestID());
        encodeMessageFooter(writer, request);
    }

    public void addRequest(final ASN1Writer writer, final int messageID, final AddRequest request)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP ADD REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_ADD_REQUEST);
        writer.writeOctetString(request.getName().toString());

        // Write the attributes
        writer.writeStartSequence();
        for (final Attribute attr : request.getAllAttributes()) {
            encodeAttribute(writer, attr);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void addResult(final ASN1Writer writer, final int messageID, final Result result)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP ADD RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_ADD_RESPONSE, result);
        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void bindRequest(final ASN1Writer writer, final int messageID, final int version,
            final GenericBindRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP BIND REQUEST(messageID=%d, auth=0x%x, request=%s)", messageID,
                    request.getAuthenticationType(), request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_BIND_REQUEST);

        writer.writeInteger(version);
        writer.writeOctetString(request.getName());
        writer.writeOctetString(request.getAuthenticationType(), request.getAuthenticationValue());

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void bindResult(final ASN1Writer writer, final int messageID, final BindResult result)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP BIND RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_BIND_RESPONSE, result);

        final ByteString saslCredentials = result.getServerSASLCredentials();
        if (saslCredentials != null && saslCredentials.length() > 0) {
            writer.writeOctetString(TYPE_SERVER_SASL_CREDENTIALS, result.getServerSASLCredentials());
        }

        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void compareRequest(final ASN1Writer writer, final int messageID,
            final CompareRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP COMPARE REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
        writer.writeOctetString(request.getName().toString());

        writer.writeStartSequence();
        writer.writeOctetString(request.getAttributeDescription().toString());
        writer.writeOctetString(request.getAssertionValue());
        writer.writeEndSequence();

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void compareResult(final ASN1Writer writer, final int messageID,
            final CompareResult result) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP COMPARE RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_COMPARE_RESPONSE, result);
        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void deleteRequest(final ASN1Writer writer, final int messageID,
            final DeleteRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP DELETE REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeOctetString(OP_TYPE_DELETE_REQUEST, request.getName().toString());
        encodeMessageFooter(writer, request);
    }

    public void deleteResult(final ASN1Writer writer, final int messageID, final Result result)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP DELETE RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_DELETE_RESPONSE, result);
        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public <R extends ExtendedResult> void extendedRequest(final ASN1Writer writer,
            final int messageID, final ExtendedRequest<R> request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP EXTENDED REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_EXTENDED_REQUEST);
        writer.writeOctetString(TYPE_EXTENDED_REQUEST_OID, request.getOID());

        final ByteString requestValue = request.getValue();
        if (requestValue != null) {
            writer.writeOctetString(TYPE_EXTENDED_REQUEST_VALUE, requestValue);
        }

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void extendedResult(final ASN1Writer writer, final int messageID,
            final ExtendedResult result) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP EXTENDED RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_EXTENDED_RESPONSE, result);

        final String responseName = result.getOID();
        final ByteString responseValue = result.getValue();

        if (responseName != null) {
            writer.writeOctetString(TYPE_EXTENDED_RESPONSE_OID, responseName);
        }

        if (responseValue != null) {
            writer.writeOctetString(TYPE_EXTENDED_RESPONSE_VALUE, responseValue);
        }

        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void intermediateResponse(final ASN1Writer writer, final int messageID,
            final IntermediateResponse response) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP INTERMEDIATE RESPONSE(messageID=%d, response=%s)", messageID,
                    response));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_INTERMEDIATE_RESPONSE);

        final String responseName = response.getOID();
        final ByteString responseValue = response.getValue();

        if (responseName != null) {
            writer.writeOctetString(TYPE_INTERMEDIATE_RESPONSE_OID, response.getOID());
        }

        if (responseValue != null) {
            writer.writeOctetString(TYPE_INTERMEDIATE_RESPONSE_VALUE, response.getValue());
        }

        writer.writeEndSequence();
        encodeMessageFooter(writer, response);
    }

    public void modifyDNRequest(final ASN1Writer writer, final int messageID,
            final ModifyDNRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP MODIFY DN REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
        writer.writeOctetString(request.getName().toString());
        writer.writeOctetString(request.getNewRDN().toString());
        writer.writeBoolean(request.isDeleteOldRDN());

        final DN newSuperior = request.getNewSuperior();
        if (newSuperior != null) {
            writer.writeOctetString(TYPE_MODIFY_DN_NEW_SUPERIOR, newSuperior.toString());
        }

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void modifyDNResult(final ASN1Writer writer, final int messageID, final Result result)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP MODIFY DN RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_MODIFY_DN_RESPONSE, result);
        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void modifyRequest(final ASN1Writer writer, final int messageID,
            final ModifyRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP MODIFY REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_MODIFY_REQUEST);
        writer.writeOctetString(request.getName().toString());

        writer.writeStartSequence();
        for (final Modification change : request.getModifications()) {
            encodeChange(writer, change);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void modifyResult(final ASN1Writer writer, final int messageID, final Result result)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP MODIFY RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_MODIFY_RESPONSE, result);
        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void searchRequest(final ASN1Writer writer, final int messageID,
            final SearchRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP SEARCH REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
        writer.writeOctetString(request.getName().toString());
        writer.writeEnumerated(request.getScope().intValue());
        writer.writeEnumerated(request.getDereferenceAliasesPolicy().intValue());
        writer.writeInteger(request.getSizeLimit());
        writer.writeInteger(request.getTimeLimit());
        writer.writeBoolean(request.isTypesOnly());
        LDAPUtils.encodeFilter(writer, request.getFilter());

        writer.writeStartSequence();
        for (final String attribute : request.getAttributes()) {
            writer.writeOctetString(attribute);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        encodeMessageFooter(writer, request);
    }

    public void searchResult(final ASN1Writer writer, final int messageID, final Result result)
            throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP SEARCH RESULT(messageID=%d, result=%s)", messageID, result));
        }
        encodeMessageHeader(writer, messageID);
        encodeResultHeader(writer, OP_TYPE_SEARCH_RESULT_DONE, result);
        encodeResultFooter(writer);
        encodeMessageFooter(writer, result);
    }

    public void searchResultEntry(final ASN1Writer writer, final int messageID,
            final SearchResultEntry entry) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP SEARCH RESULT ENTRY(messageID=%d, entry=%s)", messageID, entry));
        }
        encodeMessageHeader(writer, messageID);
        encodeEntry(writer, entry);
        encodeMessageFooter(writer, entry);
    }

    public void searchResultReference(final ASN1Writer writer, final int messageID,
            final SearchResultReference reference) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP SEARCH RESULT REFERENCE(messageID=%d, reference=%s)", messageID,
                    reference));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeStartSequence(OP_TYPE_SEARCH_RESULT_REFERENCE);
        for (final String url : reference.getURIs()) {
            writer.writeOctetString(url);
        }
        writer.writeEndSequence();
        encodeMessageFooter(writer, reference);
    }

    public void unbindRequest(final ASN1Writer writer, final int messageID,
            final UnbindRequest request) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP UNBIND REQUEST(messageID=%d, request=%s)", messageID, request));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeNull(OP_TYPE_UNBIND_REQUEST);
        encodeMessageFooter(writer, request);
    }

    public void unrecognizedMessage(final ASN1Writer writer, final int messageID,
            final byte messageTag, final ByteString messageBytes) throws IOException {
        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "ENCODE LDAP UNKNOWN MESSAGE(messageID=%d, messageTag=%s, "
                            + "messageBytes=%s)", messageID, StaticUtils.byteToHex(messageTag),
                    messageBytes.toString()));
        }
        encodeMessageHeader(writer, messageID);
        writer.writeOctetString(messageTag, messageBytes);
        writer.writeEndSequence();
    }
}
