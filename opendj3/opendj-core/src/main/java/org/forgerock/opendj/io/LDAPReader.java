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
import static com.forgerock.opendj.ldap.LDAPConstants.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
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
import org.forgerock.opendj.ldap.spi.LDAPMessageHandler;

import com.forgerock.opendj.ldap.LDAPUtils;

/**
 * Responsible for reading LDAP messages.
 *
 * @param <R>
 *            type of ASN1 reader used to decode elements
 */
public final class LDAPReader<R extends ASN1Reader> {

    private final R reader;

    private final DecodeOptions options;

    /**
     * Creates a reader with the provided ASN1 reader and decoding options.
     *
     * @param asn1Reader
     *            reader capable of decoding ASN1 elements
     * @param options
     *            allow to control how responses and requests are decoded
     */
    public LDAPReader(final R asn1Reader, final DecodeOptions options) {
        this.reader = asn1Reader;
        this.options = options;
    }

    /**
     * Returns the ASN1 reader used to decode elements.
     *
     * @return ASN1 reader
     */
    public R getASN1Reader() {
        return reader;
    }

    /**
     * Read a LDAP message by decoding the elements from the provided ASN.1 asn1Reader.
     *
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle a decoded
     *            message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    public void readMessage(final LDAPMessageHandler handler)
            throws IOException {
        reader.readStartSequence();
        try {
            final int messageID = (int) reader.readInteger();
            readProtocolOp(messageID, handler);
        } finally {
            reader.readEndSequence();
        }
    }

    /**
     * Indicates whether or not the next message can be read without blocking.
     *
     * @return {@code true} if a complete element is available or {@code false}
     *         otherwise.
     * @throws DecodeException
     *             If the available data was not valid ASN.1.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public boolean hasMessageAvailable() throws DecodeException, IOException {
        return reader.elementAvailable();
    }

    /**
     * Decodes the elements from the provided ASN.1 read as an LDAP abandon
     * request protocol op.
     *
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readAbandonRequest(final int messageID, final LDAPMessageHandler handler) throws IOException {
        final int msgToAbandon = (int) reader.readInteger(OP_TYPE_ABANDON_REQUEST);
        final AbandonRequest message = Requests.newAbandonRequest(msgToAbandon);

        readControls(message);

        IO_LOG.trace("DECODE LDAP ABANDON REQUEST(messageID={}, request={})", messageID, message);

        handler.abandonRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP add
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readAddRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        Entry entry;

        reader.readStartSequence(OP_TYPE_ADD_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = readDN(dnString, schema);
            entry = options.getEntryFactory().newEntry(dn);

            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final String ads = reader.readOctetStringAsString();
                        final AttributeDescription ad = readAttributeDescription(ads, schema);
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
        readControls(message);

        IO_LOG.trace("DECODE LDAP ADD REQUEST(messageID={}, request={})", messageID, message);

        handler.addRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an add response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readAddResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_ADD_RESPONSE);
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

        IO_LOG.trace("DECODE LDAP ADD RESULT(messageID={}, result={})", messageID, message);

        handler.addResult(messageID, message);
    }

    private AttributeDescription readAttributeDescription(final String attributeDescription,
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
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readBindRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        reader.readStartSequence(OP_TYPE_BIND_REQUEST);
        try {
            final int protocolVersion = (int) reader.readInteger();
            final String authName = reader.readOctetStringAsString();
            final byte authType = reader.peekType();
            final byte[] authBytes = reader.readOctetString(authType).toByteArray();

            final GenericBindRequest request =
                    Requests.newGenericBindRequest(authName, authType, authBytes);

            readControls(request);

            IO_LOG.trace("DECODE LDAP BIND REQUEST(messageID={}, auth=0x{}, request={})", messageID,
                    byteToHex(request.getAuthenticationType()), request);

            handler.bindRequest(messageID, protocolVersion, request);
        } finally {
            reader.readEndSequence();
        }
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a bind response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readBindResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        BindResult message;

        reader.readStartSequence(OP_TYPE_BIND_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newBindResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            readResponseReferrals(message);
            if (reader.hasNextElement() && (reader.peekType() == TYPE_SERVER_SASL_CREDENTIALS)) {
                message.setServerSASLCredentials(reader
                        .readOctetString(TYPE_SERVER_SASL_CREDENTIALS));
            }
        } finally {
            reader.readEndSequence();
        }

        readControls(message);

        IO_LOG.trace("DECODE LDAP BIND RESULT(messageID={}, result={})", messageID, message);

        handler.bindResult(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP compare
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readCompareRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        CompareRequest message;

        reader.readStartSequence(OP_TYPE_COMPARE_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = readDN(dnString, schema);

            reader.readStartSequence();
            try {
                final String ads = reader.readOctetStringAsString();
                final AttributeDescription ad = readAttributeDescription(ads, schema);
                final ByteString assertionValue = reader.readOctetString();
                message = Requests.newCompareRequest(dn, ad, assertionValue);
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        readControls(message);

        IO_LOG.trace("DECODE LDAP COMPARE REQUEST(messageID={}, request={})", messageID, message);

        handler.compareRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a compare response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readCompareResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        CompareResult message;

        reader.readStartSequence(OP_TYPE_COMPARE_RESPONSE);
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

        IO_LOG.trace("DECODE LDAP COMPARE RESULT(messageID={}, result={})", messageID, message);

        handler.compareResult(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP control.
     *
     * @param reader
     *            The ASN.1 reader
     * @param request
     *            The decoded request to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readControl(final Request request) throws IOException {
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
     *            The ASN.1 reader
     * @param response
     *            The decoded message to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readControl(final Response response) throws IOException {
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
     *            The ASN.1 reader
     * @param request
     *            The decoded message to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readControls(final Request request) throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == TYPE_CONTROL_SEQUENCE)) {
            reader.readStartSequence(TYPE_CONTROL_SEQUENCE);
            try {
                while (reader.hasNextElement()) {
                    readControl(request);
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
     *            The ASN.1 reader
     * @param response
     *            The decoded message to decode controls for.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readControls(final Response response)
            throws IOException {
        if (reader.hasNextElement() && (reader.peekType() == TYPE_CONTROL_SEQUENCE)) {
            reader.readStartSequence(TYPE_CONTROL_SEQUENCE);
            try {
                while (reader.hasNextElement()) {
                    readControl(response);
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
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readDeleteRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        final String dnString = reader.readOctetStringAsString(OP_TYPE_DELETE_REQUEST);
        final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
        final DN dn = readDN(dnString, schema);
        final DeleteRequest message = Requests.newDeleteRequest(dn);

        readControls(message);

        IO_LOG.trace("DECODE LDAP DELETE REQUEST(messageID={}, request={})", messageID, message);

        handler.deleteRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a delete response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readDeleteResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_DELETE_RESPONSE);
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

        IO_LOG.trace("DECODE LDAP DELETE RESULT(messageID={}, result={})", messageID, message);

        handler.deleteResult(messageID, message);
    }

    private DN readDN(final String dn, final Schema schema) throws DecodeException {
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
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readExtendedRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
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

        readControls(message);

        IO_LOG.trace("DECODE LDAP EXTENDED REQUEST(messageID={}, request={})", messageID, message);

        handler.extendedRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a extended
     * response protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readExtendedResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {

        GenericExtendedResult message;

        reader.readStartSequence(OP_TYPE_EXTENDED_RESPONSE);
        try {
            final ResultCode resultCode = ResultCode.valueOf(reader.readEnumerated());
            final String matchedDN = reader.readOctetStringAsString();
            final String diagnosticMessage = reader.readOctetStringAsString();
            message =
                    Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                            .setDiagnosticMessage(diagnosticMessage);
            readResponseReferrals(message);
            if (reader.hasNextElement() && (reader.peekType() == TYPE_EXTENDED_RESPONSE_OID)) {
                message.setOID(reader.readOctetStringAsString(TYPE_EXTENDED_RESPONSE_OID));
            }
            if (reader.hasNextElement() && (reader.peekType() == TYPE_EXTENDED_RESPONSE_VALUE)) {
                message.setValue(reader.readOctetString(TYPE_EXTENDED_RESPONSE_VALUE));
            }
        } finally {
            reader.readEndSequence();
        }

        readControls(message);

        IO_LOG.trace("DECODE LDAP EXTENDED RESULT(messageID={}, result={})", messageID, message);

        handler.extendedResult(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP
     * intermediate response protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readIntermediateResponse(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
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

        readControls(message);

        IO_LOG.trace("DECODE LDAP INTERMEDIATE RESPONSE(messageID={}, response={})",
                messageID, message);

        handler.intermediateResponse(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a modify DN
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readModifyDNRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        ModifyDNRequest message;

        reader.readStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = readDN(dnString, schema);

            final String newRDNString = reader.readOctetStringAsString();
            final RDN newRDN = readRDN(newRDNString, schema);

            message = Requests.newModifyDNRequest(dn, newRDN);

            message.setDeleteOldRDN(reader.readBoolean());

            if (reader.hasNextElement() && (reader.peekType() == TYPE_MODIFY_DN_NEW_SUPERIOR)) {
                final String newSuperiorString =
                        reader.readOctetStringAsString(TYPE_MODIFY_DN_NEW_SUPERIOR);
                final DN newSuperior = readDN(newSuperiorString, schema);
                message.setNewSuperior(newSuperior);
            }
        } finally {
            reader.readEndSequence();
        }

        readControls(message);

        IO_LOG.trace("DECODE LDAP MODIFY DN REQUEST(messageID={}, request={})", messageID, message);

        handler.modifyDNRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a modify DN
     * response protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readModifyDNResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_MODIFY_DN_RESPONSE);
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

        IO_LOG.trace("DECODE MODIFY DN RESULT(messageID={}, result={})", messageID, message);

        handler.modifyDNResult(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP modify
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readModifyRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        ModifyRequest message;

        reader.readStartSequence(OP_TYPE_MODIFY_REQUEST);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = readDN(dnString, schema);
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
                            final AttributeDescription ad = readAttributeDescription(ads, schema);
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

        IO_LOG.trace("DECODE LDAP MODIFY REQUEST(messageID={}, request={})", messageID, message);

        handler.modifyRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a modify response
     * protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readModifyResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        Result message;

        reader.readStartSequence(OP_TYPE_MODIFY_RESPONSE);
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

        IO_LOG.trace("DECODE LDAP MODIFY RESULT(messageID={}, result={})", messageID, message);

        handler.modifyResult(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP protocol
     * op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readProtocolOp(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        final byte type = reader.peekType();

        switch (type) {
        case OP_TYPE_UNBIND_REQUEST: // 0x42
            readUnbindRequest(messageID, handler);
            break;
        case 0x43: // 0x43
        case 0x44: // 0x44
        case 0x45: // 0x45
        case 0x46: // 0x46
        case 0x47: // 0x47
        case 0x48: // 0x48
        case 0x49: // 0x49
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_DELETE_REQUEST: // 0x4A
            readDeleteRequest(messageID, handler);
            break;
        case 0x4B: // 0x4B
        case 0x4C: // 0x4C
        case 0x4D: // 0x4D
        case 0x4E: // 0x4E
        case 0x4F: // 0x4F
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_ABANDON_REQUEST: // 0x50
            readAbandonRequest(messageID, handler);
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
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_BIND_REQUEST: // 0x60
            readBindRequest(messageID, handler);
            break;
        case OP_TYPE_BIND_RESPONSE: // 0x61
            readBindResult(messageID, handler);
            break;
        case 0x62: // 0x62
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_SEARCH_REQUEST: // 0x63
            readSearchRequest(messageID, handler);
            break;
        case OP_TYPE_SEARCH_RESULT_ENTRY: // 0x64
            readSearchResultEntry(messageID, handler);
            break;
        case OP_TYPE_SEARCH_RESULT_DONE: // 0x65
            readSearchResult(messageID, handler);
            break;
        case OP_TYPE_MODIFY_REQUEST: // 0x66
            readModifyRequest(messageID, handler);
            break;
        case OP_TYPE_MODIFY_RESPONSE: // 0x67
            readModifyResult(messageID, handler);
            break;
        case OP_TYPE_ADD_REQUEST: // 0x68
            readAddRequest(messageID, handler);
            break;
        case OP_TYPE_ADD_RESPONSE: // 0x69
            readAddResult(messageID, handler);
            break;
        case 0x6A: // 0x6A
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_DELETE_RESPONSE: // 0x6B
            readDeleteResult(messageID, handler);
            break;
        case OP_TYPE_MODIFY_DN_REQUEST: // 0x6C
            readModifyDNRequest(messageID, handler);
            break;
        case OP_TYPE_MODIFY_DN_RESPONSE: // 0x6D
            readModifyDNResult(messageID, handler);
            break;
        case OP_TYPE_COMPARE_REQUEST: // 0x6E
            readCompareRequest(messageID, handler);
            break;
        case OP_TYPE_COMPARE_RESPONSE: // 0x6F
            readCompareResult(messageID, handler);
            break;
        case 0x70: // 0x70
        case 0x71: // 0x71
        case 0x72: // 0x72
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_SEARCH_RESULT_REFERENCE: // 0x73
            readSearchResultReference(messageID, handler);
            break;
        case 0x74: // 0x74
        case 0x75: // 0x75
        case 0x76: // 0x76
            handler.unrecognizedMessage(messageID, type, reader.readOctetString(type));
            break;
        case OP_TYPE_EXTENDED_REQUEST: // 0x77
            readExtendedRequest(messageID, handler);
            break;
        case OP_TYPE_EXTENDED_RESPONSE: // 0x78
            readExtendedResult(messageID, handler);
            break;
        case OP_TYPE_INTERMEDIATE_RESPONSE: // 0x79
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

    private void readResponseReferrals(final Result message)
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
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readSearchRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        SearchRequest message;

        reader.readStartSequence(OP_TYPE_SEARCH_REQUEST);
        try {
            final String baseDNString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(baseDNString);
            final DN baseDN = readDN(baseDNString, schema);

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

        readControls(message);

        IO_LOG.trace("DECODE LDAP SEARCH REQUEST(messageID={}, request={})", messageID, message);

        handler.searchRequest(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a search result
     * done protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readSearchResult(final int messageID,
            final LDAPMessageHandler handler) throws IOException {

        Result message;

        reader.readStartSequence(OP_TYPE_SEARCH_RESULT_DONE);
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

        IO_LOG.trace("DECODE LDAP SEARCH RESULT(messageID={}, result={})", messageID, message);

        handler.searchResult(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as an LDAP search
     * result entry protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.

     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readSearchResultEntry(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        Entry entry;

        reader.readStartSequence(OP_TYPE_SEARCH_RESULT_ENTRY);
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = readDN(dnString, schema);
            entry = options.getEntryFactory().newEntry(dn);

            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final String ads = reader.readOctetStringAsString();
                        final AttributeDescription ad = readAttributeDescription(ads, schema);
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
        readControls(message);

        IO_LOG.trace("DECODE LDAP SEARCH RESULT ENTRY(messageID={}, entry={})", messageID, message);

        handler.searchResultEntry(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 reader as a search result
     * reference protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readSearchResultReference(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
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

        readControls(message);

        IO_LOG.trace("DECODE LDAP SEARCH RESULT REFERENCE(messageID={}, result={})",
                messageID, message);

        handler.searchResultReference(messageID, message);
    }

    /**
     * Decodes the elements from the provided ASN.1 read as an LDAP unbind
     * request protocol op.
     *
     * @param reader
     *            The ASN.1 reader
     * @param messageID
     *            The decoded message ID for this message.
     * @param handler
     *            The <code>LDAPMessageHandler</code> that will handle this
     *            decoded message.
     * @throws IOException
     *             If an error occurred while reading bytes to decode.
     */
    private void readUnbindRequest(final int messageID,
            final LDAPMessageHandler handler) throws IOException {
        UnbindRequest message;
        reader.readNull(OP_TYPE_UNBIND_REQUEST);
        message = Requests.newUnbindRequest();

        readControls(message);

        IO_LOG.trace("DECODE LDAP UNBIND REQUEST(messageID={}, request={})", messageID, message);

        handler.unbindRequest(messageID, message);
    }
}
