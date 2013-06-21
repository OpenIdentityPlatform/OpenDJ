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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.LDAPConstants.*;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE;

import java.io.IOException;
import java.util.logging.Level;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.asn1.ASN1;
import org.forgerock.opendj.asn1.ASN1Reader;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.GenericExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericIntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.Schema;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Static methods for decoding LDAP messages.
 */
final class LDAPReader {
    static SearchResultEntry decodeEntry(final ASN1Reader reader, final DecodeOptions options)
            throws IOException {
        Entry entry;

        reader.readStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            DN dn;
            try {
                dn = DN.valueOf(dnString, schema);
            } catch (final LocalizedIllegalArgumentException e) {
                throw DecodeException.error(e.getMessageObject());
            }

            entry = options.getEntryFactory().newEntry(dn);
            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final String ads = reader.readOctetStringAsString();
                        AttributeDescription ad;
                        try {
                            ad = AttributeDescription.valueOf(ads, schema);
                        } catch (final LocalizedIllegalArgumentException e) {
                            throw DecodeException.error(e.getMessageObject());
                        }

                        final Attribute attribute = options.getAttributeFactory().newAttribute(ad);

                        reader.readStartSet();
                        try {
                            while (reader.hasNextElement()) {
                                attribute.add(reader.readOctetString());
                            }
                            entry.addAttribute(attribute);
                        } finally {
                            reader.readEndSet();
                        }
                    } finally {
                        reader.readEndSequence();
                    }
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        return Responses.newSearchResultEntry(entry);
    }

    private final DecodeOptions options;

    LDAPReader(final DecodeOptions options) {
        this.options = options;
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP message.
     *
     * @param <P>
     *            The type of {@code param}.
     * @param reader
     *            The ASN.1 reader.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle a decoded
     *            message.
     * @param param
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    <P> void decode(final ASN1Reader reader, final LDAPMessageHandler<P> handler, final P param)
            throws IOException {
        reader.readStartSequence();
        try {
            final int messageID = (int) reader.readInteger();
            decodeProtocolOp(reader, messageID, handler, param);
        } finally {
            reader.readEndSequence();
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 read as an LDAP abandon
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeAbandonRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        final int msgToAbandon = (int) reader.readInteger(OP_TYPE_ABANDON_REQUEST);
        final AbandonRequest message = Requests.newAbandonRequest(msgToAbandon);

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP ABANDON REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.abandonRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP add
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeAddRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        Entry entry;

        reader.readStartSequence(OP_TYPE_ADD_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = decodeDN(dnString, schema);
            entry = options.getEntryFactory().newEntry(dn);

            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final String ads = reader.readOctetStringAsString();
                        final AttributeDescription ad = decodeAttributeDescription(ads, schema);
                        final Attribute attribute = options.getAttributeFactory().newAttribute(ad);

                        reader.readStartSet();
                        try {
                            while (reader.hasNextElement()) {
                                attribute.add(reader.readOctetString());
                            }
                            entry.addAttribute(attribute);
                        } finally {
                            reader.readEndSet();
                        }
                    } finally {
                        reader.readEndSequence();
                    }
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        final AddRequest message = Requests.newAddRequest(entry);
        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP ADD REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.addRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an add response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeAddResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_ADD_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            decodeResponseReferrals(reader, message);
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP ADD RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.addResult(p, messageID, message);
    }

    private AttributeDescription decodeAttributeDescription(final String attributeDescription,
            final Schema schema) throws DecodeException {
        try {
            return AttributeDescription.valueOf(attributeDescription, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 read as an LDAP bind request
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeBindRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        reader.readStartSequence(OP_TYPE_BIND_REQUEST);
        try {
            final int protocolVersion = (int) reader.readInteger();
            final String authName = reader.readOctetStringAsString();
            final byte authType = reader.peekType();
            final byte[] authBytes = reader.readOctetString(authType).toByteArray();

            final GenericBindRequest request =
                    Requests.newGenericBindRequest(authName, authType, authBytes);

            decodeControls(reader, request);

            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
                StaticUtils.DEBUG_LOG.finer(String.format(
                        "DECODE LDAP BIND REQUEST(messageID=%d, auth=0x%x, request=%s)", messageID,
                        request.getAuthenticationType(), request));
            }

            handler.bindRequest(p, messageID, protocolVersion, request);
        } finally {
            reader.readEndSequence();
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a bind response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeBindResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        BindResult message;

        reader.readStartSequence(OP_TYPE_BIND_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newBindResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            decodeResponseReferrals(reader, message);
            if (reader.hasNextElement() && (reader.peekType() == TYPE_SERVER_SASL_CREDENTIALS)) {
                message.setServerSASLCredentials(reader
                        .readOctetString(TYPE_SERVER_SASL_CREDENTIALS));
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP BIND RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.bindResult(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP compare
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeCompareRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        CompareRequest message;

        reader.readStartSequence(OP_TYPE_COMPARE_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = decodeDN(dnString, schema);

            reader.readStartSequence();
            try {
                final String ads = reader.readOctetStringAsString();
                final AttributeDescription ad = decodeAttributeDescription(ads, schema);
                final ByteString assertionValue = reader.readOctetString();
                message = Requests.newCompareRequest(dn, ad, assertionValue);
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP COMPARE REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.compareRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a compare response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeCompareResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        CompareResult message;

        reader.readStartSequence(OP_TYPE_COMPARE_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newCompareResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            decodeResponseReferrals(reader, message);
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP COMPARE RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.compareResult(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP control.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param request
     *            The decoded request to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void decodeControl(final ASN1Reader reader, final Request request) throws IOException {
        String oid;
        boolean isCritical;
        ByteString value;

        reader.readStartSequence();
        try {
            oid = reader.readOctetStringAsString();
            isCritical = false;
            value = null;
            if (reader.hasNextElement() && (reader.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE)) {
                isCritical = reader.readBoolean();
            }
            if (reader.hasNextElement() && (reader.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE)) {
                value = reader.readOctetString();
            }
        } finally {
            reader.readEndSequence();
        }

        final Control c = GenericControl.newControl(oid, isCritical, value);
        request.addControl(c);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP control.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param response
     *            The decoded message to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void decodeControl(final ASN1Reader reader, final Response response) throws IOException {
        String oid;
        boolean isCritical;
        ByteString value;

        reader.readStartSequence();
        try {
            oid = reader.readOctetStringAsString();
            isCritical = false;
            value = null;
            if (reader.hasNextElement() && (reader.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE)) {
                isCritical = reader.readBoolean();
            }
            if (reader.hasNextElement() && (reader.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE)) {
                value = reader.readOctetString();
            }
        } finally {
            reader.readEndSequence();
        }

        final Control c = GenericControl.newControl(oid, isCritical, value);
        response.addControl(c);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a set of controls.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param request
     *            The decoded message to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void decodeControls(final ASN1Reader reader, final Request request) throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == TYPE_CONTROL_SEQUENCE)) {
            reader.readStartSequence(TYPE_CONTROL_SEQUENCE);
            try {
                while (reader.hasNextElement()) {
                    decodeControl(reader, request);
                }
            } finally {
                reader.readEndSequence();
            }
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a set of controls.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param response
     *            The decoded message to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void decodeControls(final ASN1Reader reader, final Response response)
            throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == TYPE_CONTROL_SEQUENCE)) {
            reader.readStartSequence(TYPE_CONTROL_SEQUENCE);
            try {
                while (reader.hasNextElement()) {
                    decodeControl(reader, response);
                }
            } finally {
                reader.readEndSequence();
            }
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP delete
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeDeleteRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        final String dnString = reader.readOctetStringAsString(OP_TYPE_DELETE_REQUEST);
        final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
        final DN dn = decodeDN(dnString, schema);
        final DeleteRequest message = Requests.newDeleteRequest(dn);

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP DELETE REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.deleteRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a delete response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeDeleteResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_DELETE_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            decodeResponseReferrals(reader, message);
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP DELETE RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.deleteResult(p, messageID, message);
    }

    private DN decodeDN(final String dn, final Schema schema) throws DecodeException {
        try {
            return DN.valueOf(dn, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP extended
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeExtendedRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        String oid;
        ByteString value;

        reader.readStartSequence(OP_TYPE_EXTENDED_REQUEST);
        try {
            oid = reader.readOctetStringAsString(TYPE_EXTENDED_REQUEST_OID);
            value = null;
            if (reader.hasNextElement() && (reader.peekType() == TYPE_EXTENDED_REQUEST_VALUE)) {
                value = reader.readOctetString(TYPE_EXTENDED_REQUEST_VALUE);
            }
        } finally {
            reader.readEndSequence();
        }

        final GenericExtendedRequest message = Requests.newGenericExtendedRequest(oid, value);

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP EXTENDED REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.extendedRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a extended
     * response protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeExtendedResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {

        GenericExtendedResult message;

        reader.readStartSequence(OP_TYPE_EXTENDED_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            decodeResponseReferrals(reader, message);
            if (reader.hasNextElement() && (reader.peekType() == TYPE_EXTENDED_RESPONSE_OID)) {
                message.setOID(reader.readOctetStringAsString(TYPE_EXTENDED_RESPONSE_OID));
            }
            if (reader.hasNextElement() && (reader.peekType() == TYPE_EXTENDED_RESPONSE_VALUE)) {
                message.setValue(reader.readOctetString(TYPE_EXTENDED_RESPONSE_VALUE));
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP EXTENDED RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.extendedResult(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP
     * intermediate response protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeIntermediateResponse(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        GenericIntermediateResponse message;

        reader.readStartSequence(OP_TYPE_INTERMEDIATE_RESPONSE);
        try {
            message = Responses.newGenericIntermediateResponse();
            if (reader.hasNextElement() && (reader.peekType() == TYPE_INTERMEDIATE_RESPONSE_OID)) {
                message.setOID(reader.readOctetStringAsString(TYPE_INTERMEDIATE_RESPONSE_OID));
            }
            if (reader.hasNextElement() && (reader.peekType() == TYPE_INTERMEDIATE_RESPONSE_VALUE)) {
                message.setValue(reader.readOctetString(TYPE_INTERMEDIATE_RESPONSE_VALUE));
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP INTERMEDIATE RESPONSE(messageID=%d, response=%s)", messageID,
                    message));
        }

        handler.intermediateResponse(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a modify DN
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeModifyDNRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        ModifyDNRequest message;

        reader.readStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = decodeDN(dnString, schema);

            final String newRDNString = reader.readOctetStringAsString();
            final RDN newRDN = decodeRDN(newRDNString, schema);

            message = Requests.newModifyDNRequest(dn, newRDN);

            message.setDeleteOldRDN(reader.readBoolean());

            if (reader.hasNextElement() && (reader.peekType() == TYPE_MODIFY_DN_NEW_SUPERIOR)) {
                final String newSuperiorString =
                        reader.readOctetStringAsString(TYPE_MODIFY_DN_NEW_SUPERIOR);
                final DN newSuperior = decodeDN(newSuperiorString, schema);
                message.setNewSuperior(newSuperior);
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP MODIFY DN REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.modifyDNRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a modify DN
     * response protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeModifyDNResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_MODIFY_DN_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            decodeResponseReferrals(reader, message);
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP MODIFY DN RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.modifyDNResult(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP modify
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeModifyRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        ModifyRequest message;

        reader.readStartSequence(OP_TYPE_MODIFY_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = decodeDN(dnString, schema);
            message = Requests.newModifyRequest(dn);

            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final int typeIntValue = reader.readEnumerated();
                        final ModificationType type = ModificationType.valueOf(typeIntValue);
                        if (type == null) {
                            throw DecodeException
                                    .error(ERR_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE
                                            .get(typeIntValue));
                        }
                        reader.readStartSequence();
                        try {
                            final String ads = reader.readOctetStringAsString();
                            final AttributeDescription ad = decodeAttributeDescription(ads, schema);
                            final Attribute attribute =
                                    options.getAttributeFactory().newAttribute(ad);

                            reader.readStartSet();
                            try {
                                while (reader.hasNextElement()) {
                                    attribute.add(reader.readOctetString());
                                }
                                message.addModification(new Modification(type, attribute));
                            } finally {
                                reader.readEndSet();
                            }
                        } finally {
                            reader.readEndSequence();
                        }
                    } finally {
                        reader.readEndSequence();
                    }
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP MODIFY REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.modifyRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a modify response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeModifyResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_MODIFY_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            decodeResponseReferrals(reader, message);
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP MODIFY RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.modifyResult(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP protocol
     * op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeProtocolOp(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        final byte type = reader.peekType();

        switch (type) {
        case OP_TYPE_UNBIND_REQUEST: // 0x42
            decodeUnbindRequest(reader, messageID, handler, p);
            break;
        case 0x43: // 0x43
        case 0x44: // 0x44
        case 0x45: // 0x45
        case 0x46: // 0x46
        case 0x47: // 0x47
        case 0x48: // 0x48
        case 0x49: // 0x49
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_DELETE_REQUEST: // 0x4A
            decodeDeleteRequest(reader, messageID, handler, p);
            break;
        case 0x4B: // 0x4B
        case 0x4C: // 0x4C
        case 0x4D: // 0x4D
        case 0x4E: // 0x4E
        case 0x4F: // 0x4F
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_ABANDON_REQUEST: // 0x50
            decodeAbandonRequest(reader, messageID, handler, p);
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
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_BIND_REQUEST: // 0x60
            decodeBindRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_BIND_RESPONSE: // 0x61
            decodeBindResult(reader, messageID, handler, p);
            break;
        case 0x62: // 0x62
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_SEARCH_REQUEST: // 0x63
            decodeSearchRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_SEARCH_RESULT_ENTRY: // 0x64
            decodeSearchResultEntry(reader, messageID, handler, p);
            break;
        case OP_TYPE_SEARCH_RESULT_DONE: // 0x65
            decodeSearchResult(reader, messageID, handler, p);
            break;
        case OP_TYPE_MODIFY_REQUEST: // 0x66
            decodeModifyRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_MODIFY_RESPONSE: // 0x67
            decodeModifyResult(reader, messageID, handler, p);
            break;
        case OP_TYPE_ADD_REQUEST: // 0x68
            decodeAddRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_ADD_RESPONSE: // 0x69
            decodeAddResult(reader, messageID, handler, p);
            break;
        case 0x6A: // 0x6A
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_DELETE_RESPONSE: // 0x6B
            decodeDeleteResult(reader, messageID, handler, p);
            break;
        case OP_TYPE_MODIFY_DN_REQUEST: // 0x6C
            decodeModifyDNRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_MODIFY_DN_RESPONSE: // 0x6D
            decodeModifyDNResult(reader, messageID, handler, p);
            break;
        case OP_TYPE_COMPARE_REQUEST: // 0x6E
            decodeCompareRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_COMPARE_RESPONSE: // 0x6F
            decodeCompareResult(reader, messageID, handler, p);
            break;
        case 0x70: // 0x70
        case 0x71: // 0x71
        case 0x72: // 0x72
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_SEARCH_RESULT_REFERENCE: // 0x73
            decodeSearchResultReference(reader, messageID, handler, p);
            break;
        case 0x74: // 0x74
        case 0x75: // 0x75
        case 0x76: // 0x76
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_EXTENDED_REQUEST: // 0x77
            decodeExtendedRequest(reader, messageID, handler, p);
            break;
        case OP_TYPE_EXTENDED_RESPONSE: // 0x78
            decodeExtendedResult(reader, messageID, handler, p);
            break;
        case OP_TYPE_INTERMEDIATE_RESPONSE: // 0x79
            decodeIntermediateResponse(reader, messageID, handler, p);
            break;
        default:
            handler.unrecognizedMessage(p, messageID, type, reader.readOctetString(type));
            break;
        }
    }

    private RDN decodeRDN(final String rdn, final Schema schema) throws DecodeException {
        try {
            return RDN.valueOf(rdn, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    private void decodeResponseReferrals(final ASN1Reader reader, final Result message)
            throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == TYPE_REFERRAL_SEQUENCE)) {
            reader.readStartSequence(TYPE_REFERRAL_SEQUENCE);
            try {
                // Should have at least 1.
                do {
                    message.addReferralURI((reader.readOctetStringAsString()));
                } while (reader.hasNextElement());
            } finally {
                reader.readEndSequence();
            }
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP search
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeSearchRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        SearchRequest message;

        reader.readStartSequence(OP_TYPE_SEARCH_REQUEST);
        try {
            final String baseDNString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(baseDNString);
            final DN baseDN = decodeDN(baseDNString, schema);

            final int scopeIntValue = reader.readEnumerated();
            final SearchScope scope = SearchScope.valueOf(scopeIntValue);
            if (scope == null) {
                throw DecodeException.error(ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE
                        .get(scopeIntValue));
            }

            final int dereferencePolicyIntValue = reader.readEnumerated();
            final DereferenceAliasesPolicy dereferencePolicy =
                    DereferenceAliasesPolicy.valueOf(dereferencePolicyIntValue);
            if (dereferencePolicy == null) {
                throw DecodeException.error(ERR_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF
                        .get(dereferencePolicyIntValue));
            }

            final int sizeLimit = (int) reader.readInteger();
            final int timeLimit = (int) reader.readInteger();
            final boolean typesOnly = reader.readBoolean();
            final Filter filter = LDAPUtils.decodeFilter(reader);

            message = Requests.newSearchRequest(baseDN, scope, filter);
            message.setDereferenceAliasesPolicy(dereferencePolicy);
            try {
                message.setTimeLimit(timeLimit);
                message.setSizeLimit(sizeLimit);
            } catch (final LocalizedIllegalArgumentException e) {
                throw DecodeException.error(e.getMessageObject());
            }
            message.setTypesOnly(typesOnly);

            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    message.addAttribute(reader.readOctetStringAsString());
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP SEARCH REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.searchRequest(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a search result
     * done protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeSearchResult(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {

        Result message;

        reader.readStartSequence(OP_TYPE_SEARCH_RESULT_DONE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            decodeResponseReferrals(reader, message);
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP SEARCH RESULT(messageID=%d, result=%s)", messageID, message));
        }

        handler.searchResult(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP search
     * result entry protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeSearchResultEntry(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        Entry entry;

        reader.readStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = decodeDN(dnString, schema);
            entry = options.getEntryFactory().newEntry(dn);

            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final String ads = reader.readOctetStringAsString();
                        final AttributeDescription ad = decodeAttributeDescription(ads, schema);
                        final Attribute attribute = options.getAttributeFactory().newAttribute(ad);

                        reader.readStartSet();
                        try {
                            while (reader.hasNextElement()) {
                                attribute.add(reader.readOctetString());
                            }
                            entry.addAttribute(attribute);
                        } finally {
                            reader.readEndSet();
                        }
                    } finally {
                        reader.readEndSequence();
                    }
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        final SearchResultEntry message = Responses.newSearchResultEntry(entry);
        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP SEARCH RESULT ENTRY(messageID=%d, entry=%s)", messageID, message));
        }

        handler.searchResultEntry(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a search result
     * reference protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeSearchResultReference(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        SearchResultReference message;

        reader.readStartSequence(OP_TYPE_SEARCH_RESULT_REFERENCE);
        try {
            message = Responses.newSearchResultReference(reader.readOctetStringAsString());
            while (reader.hasNextElement()) {
                message.addURI(reader.readOctetStringAsString());
            }
        } finally {
            reader.readEndSequence();
        }

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP SEARCH RESULT REFERENCE(messageID=%d, reference=%s)", messageID,
                    message));
        }

        handler.searchResultReference(p, messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 read as an LDAP unbind
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader.
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @param p
     *            The parameter to pass into the <code>LDAPMessageHandler</code>
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private <P> void decodeUnbindRequest(final ASN1Reader reader, final int messageID,
            final LDAPMessageHandler<P> handler, final P p) throws IOException {
        UnbindRequest message;
        reader.readNull(OP_TYPE_UNBIND_REQUEST);
        message = Requests.newUnbindRequest();

        decodeControls(reader, message);

        if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINER)) {
            StaticUtils.DEBUG_LOG.finer(String.format(
                    "DECODE LDAP UNBIND REQUEST(messageID=%d, request=%s)", messageID, message));
        }

        handler.unbindRequest(p, messageID, message);
    }
}
