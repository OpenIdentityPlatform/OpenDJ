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

import java.io.IOException;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
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
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * Writes LDAP messages to an underlying ASN.1 writer.
 * <p>
 * Methods for creating {@link LDAPWriter}s are provided in the {@link LDAP}
 * class.
 *
 * @param <W>
 *            The type of ASN.1 writer used for encoding elements.
 */
public final class LDAPWriter<W extends ASN1Writer> {
    /** @Checkstyle:ignore AvoidNestedBlocks */

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private final W writer;

    LDAPWriter(final W asn1Writer) {
        this.writer = asn1Writer;
    }

    /**
     * Returns the ASN.1 writer to which LDAP messages will be written.
     *
     * @return The ASN.1 writer to which LDAP messages will be written.
     */
    public W getASN1Writer() {
        return writer;
    }

    /**
     * Writes the provided abandon request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeAbandonRequest(final int messageID, final AbandonRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP ABANDON REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeInteger(LDAP.OP_TYPE_ABANDON_REQUEST, request.getRequestID());
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided add request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeAddRequest(final int messageID, final AddRequest request) throws IOException {
        logger.trace("ENCODE LDAP ADD REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            LDAP.writeEntry(writer, LDAP.OP_TYPE_ADD_REQUEST, request);
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided add result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeAddResult(final int messageID, final Result result) throws IOException {
        logger.trace("ENCODE LDAP ADD RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_ADD_RESPONSE, result);
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided bind request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param version
     *            The requested LDAP protocol version.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeBindRequest(final int messageID, final int version,
            final GenericBindRequest request) throws IOException {
        logger.trace("ENCODE LDAP BIND REQUEST(messageID=%d, auth=0x%x, request=%s)",
            messageID, request.getAuthenticationType(), request);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_BIND_REQUEST);
            {
                writer.writeInteger(version);
                writer.writeOctetString(request.getName());
                writer.writeOctetString(request.getAuthenticationType(), request
                        .getAuthenticationValue());
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided bind result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeBindResult(final int messageID, final BindResult result) throws IOException {
        logger.trace("ENCODE LDAP BIND RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_BIND_RESPONSE, result);
            {
                final ByteString saslCredentials = result.getServerSASLCredentials();
                if (saslCredentials != null && saslCredentials.length() > 0) {
                    writer.writeOctetString(LDAP.TYPE_SERVER_SASL_CREDENTIALS, result
                            .getServerSASLCredentials());
                }
            }
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided compare request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeCompareRequest(final int messageID, final CompareRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP COMPARE REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_COMPARE_REQUEST);
            {
                writer.writeOctetString(request.getName().toString());
                writer.writeStartSequence();
                {
                    writer.writeOctetString(request.getAttributeDescription().toString());
                    writer.writeOctetString(request.getAssertionValue());
                }
                writer.writeEndSequence();
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided compare result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeCompareResult(final int messageID, final CompareResult result)
            throws IOException {
        logger.trace("ENCODE LDAP COMPARE RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_COMPARE_RESPONSE, result);
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided control.
     *
     * @param control
     *            The control.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeControl(final Control control) throws IOException {
        writer.writeStartSequence();
        {
            writer.writeOctetString(control.getOID());
            if (control.isCritical()) {
                writer.writeBoolean(control.isCritical());
            }
            if (control.getValue() != null) {
                writer.writeOctetString(control.getValue());
            }
        }
        writer.writeEndSequence();
    }

    /**
     * Writes the provided delete request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeDeleteRequest(final int messageID, final DeleteRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP DELETE REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeOctetString(LDAP.OP_TYPE_DELETE_REQUEST, request.getName().toString());
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided delete result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeDeleteResult(final int messageID, final Result result) throws IOException {
        logger.trace("ENCODE LDAP DELETE RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_DELETE_RESPONSE, result);
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided extended request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeExtendedRequest(final int messageID, final ExtendedRequest<?> request)
            throws IOException {
        logger.trace("ENCODE LDAP EXTENDED REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_EXTENDED_REQUEST);
            {
                writer.writeOctetString(LDAP.TYPE_EXTENDED_REQUEST_OID, request.getOID());
                final ByteString requestValue = request.getValue();
                if (requestValue != null) {
                    writer.writeOctetString(LDAP.TYPE_EXTENDED_REQUEST_VALUE, requestValue);
                }
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided extended result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeExtendedResult(final int messageID, final ExtendedResult result)
            throws IOException {
        logger.trace("ENCODE LDAP EXTENDED RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_EXTENDED_RESPONSE, result);
            {
                final String responseName = result.getOID();
                if (responseName != null) {
                    writer.writeOctetString(LDAP.TYPE_EXTENDED_RESPONSE_OID, responseName);
                }
                final ByteString responseValue = result.getValue();
                if (responseValue != null) {
                    writer.writeOctetString(LDAP.TYPE_EXTENDED_RESPONSE_VALUE, responseValue);
                }
            }
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided intermediate response.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param response
     *            The response.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeIntermediateResponse(final int messageID, final IntermediateResponse response)
            throws IOException {
        logger.trace("ENCODE LDAP INTERMEDIATE RESPONSE(messageID=%d, response=%s)", messageID, response);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_INTERMEDIATE_RESPONSE);
            {
                final String responseName = response.getOID();
                if (responseName != null) {
                    writer.writeOctetString(LDAP.TYPE_INTERMEDIATE_RESPONSE_OID, response.getOID());
                }
                final ByteString responseValue = response.getValue();
                if (responseValue != null) {
                    writer.writeOctetString(LDAP.TYPE_INTERMEDIATE_RESPONSE_VALUE, response
                            .getValue());
                }
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(response.getControls());
    }

    /**
     * Writes the provided modify DN request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeModifyDNRequest(final int messageID, final ModifyDNRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP MODIFY DN REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_MODIFY_DN_REQUEST);
            {
                writer.writeOctetString(request.getName().toString());
                writer.writeOctetString(request.getNewRDN().toString());
                writer.writeBoolean(request.isDeleteOldRDN());
                final DN newSuperior = request.getNewSuperior();
                if (newSuperior != null) {
                    writer.writeOctetString(LDAP.TYPE_MODIFY_DN_NEW_SUPERIOR, newSuperior
                            .toString());
                }
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided modify DN result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeModifyDNResult(final int messageID, final Result result) throws IOException {
        logger.trace("ENCODE LDAP MODIFY DN RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_MODIFY_DN_RESPONSE, result);
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided modify request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeModifyRequest(final int messageID, final ModifyRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP MODIFY REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_MODIFY_REQUEST);
            {
                writer.writeOctetString(request.getName().toString());
                writer.writeStartSequence();
                {
                    for (final Modification change : request.getModifications()) {
                        writeChange(change);
                    }
                }
                writer.writeEndSequence();
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided extended result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeModifyResult(final int messageID, final Result result) throws IOException {
        logger.trace("ENCODE LDAP MODIFY RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_MODIFY_RESPONSE, result);
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided search request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeSearchRequest(final int messageID, final SearchRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP SEARCH REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_SEARCH_REQUEST);
            {
                writer.writeOctetString(request.getName().toString());
                writer.writeEnumerated(request.getScope().intValue());
                writer.writeEnumerated(request.getDereferenceAliasesPolicy().intValue());
                writer.writeInteger(request.getSizeLimit());
                writer.writeInteger(request.getTimeLimit());
                writer.writeBoolean(request.isTypesOnly());
                LDAP.writeFilter(writer, request.getFilter());
                writer.writeStartSequence();
                {
                    for (final String attribute : request.getAttributes()) {
                        writer.writeOctetString(attribute);
                    }
                }
                writer.writeEndSequence();
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes the provided search result.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The result.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeSearchResult(final int messageID, final Result result) throws IOException {
        logger.trace("ENCODE LDAP SEARCH RESULT(messageID=%d, result=%s)", messageID, result);
        writeMessageHeader(messageID);
        {
            writeResultHeader(LDAP.OP_TYPE_SEARCH_RESULT_DONE, result);
            writeResultFooter(writer);
        }
        writeMessageFooter(result.getControls());
    }

    /**
     * Writes the provided search result entry.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param entry
     *            The entry.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeSearchResultEntry(final int messageID, final SearchResultEntry entry)
            throws IOException {
        logger.trace("ENCODE LDAP SEARCH RESULT ENTRY(messageID=%d, entry=%s)", messageID, entry);
        writeMessageHeader(messageID);
        {
            LDAP.writeEntry(writer, LDAP.OP_TYPE_SEARCH_RESULT_ENTRY, entry);
        }
        writeMessageFooter(entry.getControls());
    }

    /**
     * Writes the provided search result reference.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param reference
     *            The reference.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeSearchResultReference(final int messageID,
            final SearchResultReference reference) throws IOException {
        logger.trace("ENCODE LDAP SEARCH RESULT REFERENCE(messageID=%d, reference=%s)", messageID, reference);
        writeMessageHeader(messageID);
        {
            writer.writeStartSequence(LDAP.OP_TYPE_SEARCH_RESULT_REFERENCE);
            {
                for (final String url : reference.getURIs()) {
                    writer.writeOctetString(url);
                }
            }
            writer.writeEndSequence();
        }
        writeMessageFooter(reference.getControls());
    }

    /**
     * Writes the provided unbind request.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The request.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeUnbindRequest(final int messageID, final UnbindRequest request)
            throws IOException {
        logger.trace("ENCODE LDAP UNBIND REQUEST(messageID=%d, request=%s)", messageID, request);
        writeMessageHeader(messageID);
        {
            writer.writeNull(LDAP.OP_TYPE_UNBIND_REQUEST);
        }
        writeMessageFooter(request.getControls());
    }

    /**
     * Writes a message with the provided id, tag and content bytes.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param messageTag
     *            The LDAP message type.
     * @param messageBytes
     *            The contents of the LDAP message.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void writeUnrecognizedMessage(final int messageID, final byte messageTag,
            final ByteString messageBytes) throws IOException {
        logger.trace("ENCODE LDAP UNKNOWN MESSAGE(messageID=%d, messageTag=%x, messageBytes=%s)",
                messageID, messageTag, messageBytes);
        writeMessageHeader(messageID);
        {
            writer.writeOctetString(messageTag, messageBytes);
        }
        writer.writeEndSequence();
    }

    private void writeChange(final Modification change) throws IOException {
        writer.writeStartSequence();
        {
            writer.writeEnumerated(change.getModificationType().intValue());
            LDAP.writeAttribute(writer, change.getAttribute());
        }
        writer.writeEndSequence();
    }

    private void writeMessageFooter(final List<Control> controls) throws IOException {
        if (!controls.isEmpty()) {
            writer.writeStartSequence(LDAP.TYPE_CONTROL_SEQUENCE);
            {
                for (final Control control : controls) {
                    writeControl(control);
                }
            }
            writer.writeEndSequence();
        }
        writer.writeEndSequence();
    }

    private void writeMessageHeader(final int messageID) throws IOException {
        writer.writeStartSequence();
        writer.writeInteger(messageID);
    }

    private void writeResultFooter(final ASN1Writer writer) throws IOException {
        writer.writeEndSequence();
    }

    private void writeResultHeader(final byte typeTag, final Result rawMessage) throws IOException {
        writer.writeStartSequence(typeTag);
        writer.writeEnumerated(rawMessage.getResultCode().intValue());
        writer.writeOctetString(rawMessage.getMatchedDN());
        writer.writeOctetString(rawMessage.getDiagnosticMessage());
        final List<String> referralURIs = rawMessage.getReferralURIs();
        if (!referralURIs.isEmpty()) {
            writer.writeStartSequence(LDAP.TYPE_REFERRAL_SEQUENCE);
            {
                for (final String s : referralURIs) {
                    writer.writeOctetString(s);
                }
            }
            writer.writeEndSequence();
        }
    }
}
