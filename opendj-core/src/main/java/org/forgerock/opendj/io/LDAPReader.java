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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.io;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
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

/**
 * Reads LDAP messages from an underlying ASN.1 reader.
 *
 * @param <R>
 *            The type of ASN.1 reader used for decoding elements.
 */
public final class LDAPReader<R extends ASN1Reader> {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    private final DecodeOptions options;
    private final R reader;

    LDAPReader(final R asn1Reader, final DecodeOptions options) {
        this.reader = asn1Reader;
        this.options = options;
    }

    /**
     * Returns the ASN.1 reader from which LDAP messages will be read.
     *
     * @return The ASN.1 reader from which LDAP messages will be read.
     */
    public R getASN1Reader() {
        return reader;
    }

    /**
     * Returns {@code true} if the next LDAP message can be read without
     * blocking.
     *
     * @return {@code true} if the next LDAP message can be read without
     *         blocking or {@code false} otherwise.
     * @throws DecodeException
     *             If the available data was not a valid LDAP message.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public boolean hasMessageAvailable() throws DecodeException, IOException {
        return reader.elementAvailable();
    }

    /**
     * Reads the next LDAP message from the underlying ASN.1 reader.
     *
     * @param handler
     *            The message handler which will handle the decoded LDAP
     *            message.
     * @throws DecodeException
     *             If the available data was not a valid LDAP message.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public void readMessage(final LDAPMessageHandler handler) throws DecodeException, IOException {
        reader.readStartSequence();
        try {
            final int messageID = (int) reader.readInteger();
            readProtocolOp(messageID, handler);
        } finally {
            reader.readEndSequence();
        }
    }

    private void readAbandonRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        final int msgToAbandon = (int) reader.readInteger(LDAP.OP_TYPE_ABANDON_REQUEST);
        final AbandonRequest message = Requests.newAbandonRequest(msgToAbandon);
        readControls(message);
        logger.trace("DECODE LDAP ABANDON REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.abandonRequest(messageID, message);
    }

    private void readAddRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        final Entry entry = LDAP.readEntry(reader, LDAP.OP_TYPE_ADD_REQUEST, options);
        final AddRequest message = Requests.newAddRequest(entry);
        readControls(message);
        logger.trace("DECODE LDAP ADD REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.addRequest(messageID, message);
    }

    private void readAddResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_ADD_RESPONSE);
        final Result message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            readResponseReferrals(message);
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP ADD RESULT(messageID=%d, result=%s)", messageID, message);
        handler.addResult(messageID, message);
    }

    private void readBindRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_BIND_REQUEST);
        try {
            final int protocolVersion = (int) reader.readInteger();
            final String authName = reader.readOctetStringAsString();
            final byte authType = reader.peekType();
            final byte[] authBytes = reader.readOctetString(authType).toByteArray();
            final GenericBindRequest request =
                    Requests.newGenericBindRequest(authName, authType, authBytes);
            readControls(request);
            logger.trace("DECODE LDAP BIND REQUEST(messageID=%d, auth=0x%x, request=%s)",
                messageID, request.getAuthenticationType(), request);

            handler.bindRequest(messageID, protocolVersion, request);
        } finally {
            reader.readEndSequence();
        }
    }

    private void readBindResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_BIND_RESPONSE);
        final BindResult message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newBindResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            readResponseReferrals(message);
            if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_SERVER_SASL_CREDENTIALS)) {
                message.setServerSASLCredentials(reader
                        .readOctetString(LDAP.TYPE_SERVER_SASL_CREDENTIALS));
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP BIND RESULT(messageID=%d, result=%s)", messageID, message);
        handler.bindResult(messageID, message);
    }

    private void readCompareRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_COMPARE_REQUEST);
        final CompareRequest message;
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = LDAP.readDN(dnString, schema);
            reader.readStartSequence();
            try {
                final String ads = reader.readOctetStringAsString();
                final AttributeDescription ad = LDAP.readAttributeDescription(ads, schema);
                final ByteString assertionValue = reader.readOctetString();
                message = Requests.newCompareRequest(dn, ad, assertionValue);
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP COMPARE REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.compareRequest(messageID, message);
    }

    private void readCompareResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_COMPARE_RESPONSE);
        final CompareResult message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newCompareResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            readResponseReferrals(message);
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP COMPARE RESULT(messageID=%d, result=%s)", messageID, message);
        handler.compareResult(messageID, message);
    }

    private Control readControl() throws IOException {
        reader.readStartSequence();
        try {
            final String oid = reader.readOctetStringAsString();
            final boolean isCritical;
            if (reader.hasNextElement() && (reader.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE)) {
                isCritical = reader.readBoolean();
            } else {
                isCritical = false;
            }
            final ByteString value;
            if (reader.hasNextElement() && (reader.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE)) {
                value = reader.readOctetString();
            } else {
                value = null;
            }
            return GenericControl.newControl(oid, isCritical, value);
        } finally {
            reader.readEndSequence();
        }
    }

    private void readControls(final Request request) throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_CONTROL_SEQUENCE)) {
            reader.readStartSequence(LDAP.TYPE_CONTROL_SEQUENCE);
            try {
                while (reader.hasNextElement()) {
                    request.addControl(readControl());
                }
            } finally {
                reader.readEndSequence();
            }
        }
    }

    private void readControls(final Response response) throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_CONTROL_SEQUENCE)) {
            reader.readStartSequence(LDAP.TYPE_CONTROL_SEQUENCE);
            try {
                while (reader.hasNextElement()) {
                    response.addControl(readControl());
                }
            } finally {
                reader.readEndSequence();
            }
        }
    }

    private void readDeleteRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        final String dnString = reader.readOctetStringAsString(LDAP.OP_TYPE_DELETE_REQUEST);
        final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
        final DN dn = LDAP.readDN(dnString, schema);
        final DeleteRequest message = Requests.newDeleteRequest(dn);
        readControls(message);
        logger.trace("DECODE LDAP DELETE REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.deleteRequest(messageID, message);
    }

    private void readDeleteResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_DELETE_RESPONSE);
        final Result message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            readResponseReferrals(message);
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP DELETE RESULT(messageID=%d, result=%s)", messageID, message);
        handler.deleteResult(messageID, message);
    }

    private void readExtendedRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_EXTENDED_REQUEST);
        final GenericExtendedRequest message;
        try {
            final String oid = reader.readOctetStringAsString(LDAP.TYPE_EXTENDED_REQUEST_OID);
            if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_EXTENDED_REQUEST_VALUE)) {
                final ByteString value = reader.readOctetString(LDAP.TYPE_EXTENDED_REQUEST_VALUE);
                message = Requests.newGenericExtendedRequest(oid, value);
            } else {
                message = Requests.newGenericExtendedRequest(oid, null);
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP EXTENDED REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.extendedRequest(messageID, message);
    }

    private void readExtendedResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_EXTENDED_RESPONSE);
        final GenericExtendedResult message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            readResponseReferrals(message);
            if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_EXTENDED_RESPONSE_OID)) {
                message.setOID(reader.readOctetStringAsString(LDAP.TYPE_EXTENDED_RESPONSE_OID));
            }
            if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_EXTENDED_RESPONSE_VALUE)) {
                message.setValue(reader.readOctetString(LDAP.TYPE_EXTENDED_RESPONSE_VALUE));
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP EXTENDED RESULT(messageID=%d, result=%s)", messageID, message);
        handler.extendedResult(messageID, message);
    }

    private void readIntermediateResponse(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_INTERMEDIATE_RESPONSE);
        final GenericIntermediateResponse message;
        try {
            message = Responses.newGenericIntermediateResponse();
            if (reader.hasNextElement()
                    && (reader.peekType() == LDAP.TYPE_INTERMEDIATE_RESPONSE_OID)) {
                message.setOID(reader.readOctetStringAsString(LDAP.TYPE_INTERMEDIATE_RESPONSE_OID));
            }
            if (reader.hasNextElement()
                    && (reader.peekType() == LDAP.TYPE_INTERMEDIATE_RESPONSE_VALUE)) {
                message.setValue(reader.readOctetString(LDAP.TYPE_INTERMEDIATE_RESPONSE_VALUE));
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP INTERMEDIATE RESPONSE(messageID=%d, response=%s)", messageID, message);
        handler.intermediateResponse(messageID, message);
    }

    private void readModifyDNRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_MODIFY_DN_REQUEST);
        final ModifyDNRequest message;
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = LDAP.readDN(dnString, schema);
            final String newRDNString = reader.readOctetStringAsString();
            final RDN newRDN = readRDN(newRDNString, schema);
            message = Requests.newModifyDNRequest(dn, newRDN);
            message.setDeleteOldRDN(reader.readBoolean());
            if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_MODIFY_DN_NEW_SUPERIOR)) {
                final String newSuperiorString =
                        reader.readOctetStringAsString(LDAP.TYPE_MODIFY_DN_NEW_SUPERIOR);
                final DN newSuperior = LDAP.readDN(newSuperiorString, schema);
                message.setNewSuperior(newSuperior);
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP MODIFY DN REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.modifyDNRequest(messageID, message);
    }

    private void readModifyDNResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_MODIFY_DN_RESPONSE);
        final Result message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            readResponseReferrals(message);
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP MODIFY DN RESULT(messageID=%d, result=%s)", messageID, message);
        handler.modifyDNResult(messageID, message);
    }

    private void readModifyRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_MODIFY_REQUEST);
        final ModifyRequest message;
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = LDAP.readDN(dnString, schema);
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
                            final AttributeDescription ad =
                                    LDAP.readAttributeDescription(ads, schema);
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
        readControls(message);
        logger.trace("DECODE LDAP MODIFY REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.modifyRequest(messageID, message);
    }

    private void readModifyResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_MODIFY_RESPONSE);
        final Result message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            readResponseReferrals(message);
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP MODIFY RESULT(messageID=%d, result=%s)", messageID, message);
        handler.modifyResult(messageID, message);
    }

    private void readProtocolOp(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        final byte type = reader.peekType();
        switch (type) {
        case LDAP.OP_TYPE_UNBIND_REQUEST: // 0x42
            readUnbindRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_DELETE_REQUEST: // 0x4A
            readDeleteRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_ABANDON_REQUEST: // 0x50
            readAbandonRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_BIND_REQUEST: // 0x60
            readBindRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_BIND_RESPONSE: // 0x61
            readBindResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_SEARCH_REQUEST: // 0x63
            readSearchRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_SEARCH_RESULT_ENTRY: // 0x64
            readSearchResultEntry(messageID, handler);
            break;
        case LDAP.OP_TYPE_SEARCH_RESULT_DONE: // 0x65
            readSearchResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_MODIFY_REQUEST: // 0x66
            readModifyRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_MODIFY_RESPONSE: // 0x67
            readModifyResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_ADD_REQUEST: // 0x68
            readAddRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_ADD_RESPONSE: // 0x69
            readAddResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_DELETE_RESPONSE: // 0x6B
            readDeleteResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_MODIFY_DN_REQUEST: // 0x6C
            readModifyDNRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_MODIFY_DN_RESPONSE: // 0x6D
            readModifyDNResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_COMPARE_REQUEST: // 0x6E
            readCompareRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_COMPARE_RESPONSE: // 0x6F
            readCompareResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_SEARCH_RESULT_REFERENCE: // 0x73
            readSearchResultReference(messageID, handler);
            break;
        case LDAP.OP_TYPE_EXTENDED_REQUEST: // 0x77
            readExtendedRequest(messageID, handler);
            break;
        case LDAP.OP_TYPE_EXTENDED_RESPONSE: // 0x78
            readExtendedResult(messageID, handler);
            break;
        case LDAP.OP_TYPE_INTERMEDIATE_RESPONSE: // 0x79
            readIntermediateResponse(messageID, handler);
            break;
        default:
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        }
    }

    private RDN readRDN(final String rdn, final Schema schema) throws DecodeException {
        try {
            return RDN.valueOf(rdn, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    private void readResponseReferrals(final Result message) throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_REFERRAL_SEQUENCE)) {
            reader.readStartSequence(LDAP.TYPE_REFERRAL_SEQUENCE);
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

    private void readSearchRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_SEARCH_REQUEST);
        final SearchRequest message;
        try {
            final String baseDNString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(baseDNString);
            final DN baseDN = LDAP.readDN(baseDNString, schema);
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
            final Filter filter = LDAP.readFilter(reader);
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
        readControls(message);
        logger.trace("DECODE LDAP SEARCH REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.searchRequest(messageID, message);
    }

    private void readSearchResult(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_SEARCH_RESULT_DONE);
        final Result message;
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newResult(resultCode).setMatchedDN(matchedDN).setDiagnosticMessage(
                            diagnosticMessage);
            readResponseReferrals(message);
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP SEARCH RESULT(messageID=%d, result=%s)", messageID, message);
        handler.searchResult(messageID, message);
    }

    private void readSearchResultEntry(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        final Entry entry = LDAP.readEntry(reader, LDAP.OP_TYPE_SEARCH_RESULT_ENTRY, options);
        final SearchResultEntry message = Responses.newSearchResultEntry(entry);
        readControls(message);
        logger.trace("DECODE LDAP SEARCH RESULT ENTRY(messageID=%d, entry=%s)", messageID, message);
        handler.searchResultEntry(messageID, message);
    }

    private void readSearchResultReference(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence(LDAP.OP_TYPE_SEARCH_RESULT_REFERENCE);
        final SearchResultReference message;
        try {
            message = Responses.newSearchResultReference(reader.readOctetStringAsString());
            while (reader.hasNextElement()) {
                message.addURI(reader.readOctetStringAsString());
            }
        } finally {
            reader.readEndSequence();
        }
        readControls(message);
        logger.trace("DECODE LDAP SEARCH RESULT REFERENCE(messageID=%d, reference=%s)", messageID, message);
        handler.searchResultReference(messageID, message);
    }

    private void readUnbindRequest(final int messageID, final LDAPMessageHandler handler)
            throws IOException {
        reader.readNull(LDAP.OP_TYPE_UNBIND_REQUEST);
        final UnbindRequest message = Requests.newUnbindRequest();
        readControls(message);
        logger.trace("DECODE LDAP UNBIND REQUEST(messageID=%d, request=%s)", messageID, message);
        handler.unbindRequest(messageID, message);
    }
}
