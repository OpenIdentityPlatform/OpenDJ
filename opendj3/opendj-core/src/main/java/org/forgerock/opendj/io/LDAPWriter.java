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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.io;

import static com.forgerock.opendj.ldap.LDAPConstants.*;
import static com.forgerock.opendj.util.StaticUtils.IO_LOG;
import static com.forgerock.opendj.util.StaticUtils.byteToHex;

import java.io.IOException;
import java.util.List;

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

import com.forgerock.opendj.ldap.LDAPUtils;
import com.forgerock.opendj.util.StaticUtils;

/**
 * Responsible for writing LDAP messages.
 *
 * @param <W>
 *            type of ASN1 writer used to encode elements
 */
public final class LDAPWriter<W extends ASN1Writer> {

    private final W writer;

    /**
     * Creates a writer based on the provided ASN1 writer.
     *
     * @param asn1Writer
     *            writer to encode ASN.1 elements
     */
    public LDAPWriter(W asn1Writer) {
        this.writer = asn1Writer;
    }

    /**
     * Returns the ASN1 writer used to encode elements.
     *
     * @return the ASN1 writer
     */
    public W getASN1Writer() {
        return writer;
    }

    /**
     * Write the provided control.
     *
     * @param control
     *            the control
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeControl(final Control control)
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

    /**
     * Write the provided entry.
     *
     * @param searchResultEntry
     *            entry
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeEntry(final SearchResultEntry searchResultEntry) throws IOException {
        writer.writeStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
        writer.writeOctetString(searchResultEntry.getName().toString());

        writer.writeStartSequence();
        for (final Attribute attr : searchResultEntry.getAllAttributes()) {
            writeAttribute(attr);
        }
        writer.writeEndSequence();
        writer.writeEndSequence();
    }

    private void writeAttribute(final Attribute attribute)
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

    private void writeChange(final Modification change)
            throws IOException {
        writer.writeStartSequence();
        writer.writeEnumerated(change.getModificationType().intValue());
        writeAttribute(change.getAttribute());
        writer.writeEndSequence();
    }

    private void writeMessageFooter(final Request request)
            throws IOException {
        final List<Control> controls = request.getControls();
        if (!controls.isEmpty()) {
            writer.writeStartSequence(TYPE_CONTROL_SEQUENCE);
            for (final Control control : controls) {
                writeControl(control);
            }
            writer.writeEndSequence();
        }

        writer.writeEndSequence();
    }

    private void writeMessageFooter(final Response response)
            throws IOException {
        final List<Control> controls = response.getControls();
        if (!controls.isEmpty()) {
            writer.writeStartSequence(TYPE_CONTROL_SEQUENCE);
            for (final Control control : controls) {
                writeControl(control);
            }
            writer.writeEndSequence();
        }

        writer.writeEndSequence();
    }

    private void writeMessageHeader(final int messageID)
            throws IOException {
        writer.writeStartSequence();
        writer.writeInteger(messageID);
    }

    private void writeResultFooter(final ASN1Writer writer) throws IOException {
        writer.writeEndSequence();
    }

    private void writeResultHeader(final byte typeTag,
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

    /**
     * Write the provided abandon request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeAbandonRequest(final int messageID, final AbandonRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP ABANDON REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeInteger(OP_TYPE_ABANDON_REQUEST, request.getRequestID());
        writeMessageFooter(request);
    }

    /**
     * Write the provided add request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeAddRequest(final int messageID, final AddRequest request)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP ADD REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_ADD_REQUEST);
        writer.writeOctetString(request.getName().toString());

        // Write the attributes
        writer.writeStartSequence();
        for (final Attribute attr : request.getAllAttributes()) {
            writeAttribute(attr);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        writeMessageFooter(request);
    }

    /**
     * Write the provided add result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeAddResult(final int messageID, final Result result)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP ADD RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_ADD_RESPONSE, result);
        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided bind request.
     *
     * @param messageID
     *            identifier of the request message
     * @param version
     *            version of the protocol
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeBindRequest(final int messageID, final int version,
            final GenericBindRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP BIND REQUEST(messageID={}, auth=0x{}, request={})",
                messageID, byteToHex(request.getAuthenticationType()), request);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_BIND_REQUEST);

        writer.writeInteger(version);
        writer.writeOctetString(request.getName());
        writer.writeOctetString(request.getAuthenticationType(), request.getAuthenticationValue());

        writer.writeEndSequence();
        writeMessageFooter(request);
    }

    /**
     * Write the provided bind result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeBindResult(final int messageID, final BindResult result)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP BIND RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_BIND_RESPONSE, result);

        final ByteString saslCredentials = result.getServerSASLCredentials();
        if (saslCredentials != null && saslCredentials.length() > 0) {
            writer.writeOctetString(TYPE_SERVER_SASL_CREDENTIALS, result.getServerSASLCredentials());
        }

        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided compare request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeCompareRequest(final int messageID,
            final CompareRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP COMPARE REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
        writer.writeOctetString(request.getName().toString());

        writer.writeStartSequence();
        writer.writeOctetString(request.getAttributeDescription().toString());
        writer.writeOctetString(request.getAssertionValue());
        writer.writeEndSequence();

        writer.writeEndSequence();
        writeMessageFooter(request);
    }

    /**
     * Write the provided compare result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeCompareResult(final int messageID,
            final CompareResult result) throws IOException {
        IO_LOG.trace("ENCODE LDAP COMPARE RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_COMPARE_RESPONSE, result);
        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided delete request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeDeleteRequest(final int messageID,
            final DeleteRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP DELETE REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeOctetString(OP_TYPE_DELETE_REQUEST, request.getName().toString());
        writeMessageFooter(request);
    }

    /**
     * Write the provided delete result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeDeleteResult(final int messageID, final Result result)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP DELETE RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_DELETE_RESPONSE, result);
        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided extended request.
     *
     * @param <R>
     *            type of extended result returned by the request
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public <R extends ExtendedResult> void writeExtendedRequest(final int messageID,
            final ExtendedRequest<R> request) throws IOException {
        IO_LOG.trace("ENCODE LDAP EXTENDED REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_EXTENDED_REQUEST);
        writer.writeOctetString(TYPE_EXTENDED_REQUEST_OID, request.getOID());

        final ByteString requestValue = request.getValue();
        if (requestValue != null) {
            writer.writeOctetString(TYPE_EXTENDED_REQUEST_VALUE, requestValue);
        }

        writer.writeEndSequence();
        writeMessageFooter(request);
    }

    /**
     * Write the provided extended result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeExtendedResult(final int messageID,
            final ExtendedResult result) throws IOException {
        IO_LOG.trace("ENCODE LDAP EXTENDED RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_EXTENDED_RESPONSE, result);

        final String responseName = result.getOID();
        final ByteString responseValue = result.getValue();

        if (responseName != null) {
            writer.writeOctetString(TYPE_EXTENDED_RESPONSE_OID, responseName);
        }

        if (responseValue != null) {
            writer.writeOctetString(TYPE_EXTENDED_RESPONSE_VALUE, responseValue);
        }

        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided intermediate response.
     *
     * @param messageID
     *            identifier of the result message
     * @param response
     *            the response
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeIntermediateResponse(final int messageID,
            final IntermediateResponse response) throws IOException {
        IO_LOG.trace("ENCODE LDAP INTERMEDIATE RESPONSE(messageID={}, response={})", messageID, response);
        writeMessageHeader(messageID);
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
        writeMessageFooter(response);
    }

    /**
     * Write the provided modify DN request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeModifyDNRequest(final int messageID,
            final ModifyDNRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP MODIFY DN REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
        writer.writeOctetString(request.getName().toString());
        writer.writeOctetString(request.getNewRDN().toString());
        writer.writeBoolean(request.isDeleteOldRDN());

        final DN newSuperior = request.getNewSuperior();
        if (newSuperior != null) {
            writer.writeOctetString(TYPE_MODIFY_DN_NEW_SUPERIOR, newSuperior.toString());
        }

        writer.writeEndSequence();
        writeMessageFooter(request);
    }

    /**
     * Write the provided modify DN result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeModifyDNResult(final int messageID, final Result result)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP MODIFY DN RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_MODIFY_DN_RESPONSE, result);
        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided modify request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeModifyRequest(final int messageID,
            final ModifyRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP MODIFY REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_MODIFY_REQUEST);
        writer.writeOctetString(request.getName().toString());

        writer.writeStartSequence();
        for (final Modification change : request.getModifications()) {
            writeChange(change);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        writeMessageFooter(request);
    }

    /**
     * Write the provided extended result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeModifyResult(final int messageID, final Result result)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP MODIFY RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_MODIFY_RESPONSE, result);
        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided search request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeSearchRequest(final int messageID,
            final SearchRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP SEARCH REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
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
        writeMessageFooter(request);
    }

    /**
     * Write the provided search result.
     *
     * @param messageID
     *            identifier of the result message
     * @param result
     *            the result
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeSearchResult(final int messageID, final Result result)
            throws IOException {
        IO_LOG.trace("ENCODE LDAP SEARCH RESULT(messageID={}, result={})", messageID, result);
        writeMessageHeader(messageID);
        writeResultHeader(OP_TYPE_SEARCH_RESULT_DONE, result);
        writeResultFooter(writer);
        writeMessageFooter(result);
    }

    /**
     * Write the provided search result entry.
     *
     * @param messageID
     *            identifier of the result message
     * @param entry
     *            the entry
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeSearchResultEntry(final int messageID,
            final SearchResultEntry entry) throws IOException {
        IO_LOG.trace("ENCODE LDAP SEARCH RESULT ENTRY(messageID={}, entry={})", messageID, entry);
        writeMessageHeader(messageID);
        writeEntry(entry);
        writeMessageFooter(entry);
    }

    /**
     * Write the provided search result reference.
     *
     * @param messageID
     *            identifier of the result message
     * @param reference
     *            the reference
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeSearchResultReference(final int messageID,
            final SearchResultReference reference) throws IOException {
        IO_LOG.trace("ENCODE LDAP SEARCH RESULT REFERENCE(messageID={}, reference={})", messageID, reference);
        writeMessageHeader(messageID);
        writer.writeStartSequence(OP_TYPE_SEARCH_RESULT_REFERENCE);
        for (final String url : reference.getURIs()) {
            writer.writeOctetString(url);
        }
        writer.writeEndSequence();
        writeMessageFooter(reference);
    }

    /**
     * Write the provided unbind request.
     *
     * @param messageID
     *            identifier of the request message
     * @param request
     *            the request
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeUnbindRequest(final int messageID,
            final UnbindRequest request) throws IOException {
        IO_LOG.trace("ENCODE LDAP UNBIND REQUEST(messageID={}, request={})", messageID, request);
        writeMessageHeader(messageID);
        writer.writeNull(OP_TYPE_UNBIND_REQUEST);
        writeMessageFooter(request);
    }

    /**
     * Write a message with the provided id, tag and content bytes.
     *
     * @param messageID
     *            identifier of the result message
     * @param messageTag
     *            tag identifying the type of message
     * @param messageBytes
     *            content of message
     * @throws IOException
     *             if an error occurs during write operation
     */
    public void writeUnrecognizedMessage(final int messageID,
            final byte messageTag, final ByteString messageBytes) throws IOException {
        IO_LOG.trace("ENCODE LDAP UNKNOWN MESSAGE(messageID={}, messageTag={}, messageBytes={})",
                messageID, StaticUtils.byteToHex(messageTag), messageBytes);
        writeMessageHeader(messageID);
        writer.writeOctetString(messageTag, messageBytes);
        writer.writeEndSequence();
    }
}
