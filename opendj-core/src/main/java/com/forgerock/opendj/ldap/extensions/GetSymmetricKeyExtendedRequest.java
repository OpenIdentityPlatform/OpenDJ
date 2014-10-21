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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2014 ForgeRock AS
 */
package com.forgerock.opendj.ldap.extensions;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AbstractExtendedRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequestDecoder;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.Responses;

/**
 * Get symmetric key extended request.
 */
public final class GetSymmetricKeyExtendedRequest extends
        AbstractExtendedRequest<GetSymmetricKeyExtendedRequest, ExtendedResult> {
    private static final class RequestDecoder implements
            ExtendedRequestDecoder<GetSymmetricKeyExtendedRequest, ExtendedResult> {

        public GetSymmetricKeyExtendedRequest decodeExtendedRequest(
                final ExtendedRequest<?> request, final DecodeOptions options)
                throws DecodeException {
            final ByteString requestValue = request.getValue();
            if (requestValue == null) {
                // The request must always have a value.
                final LocalizableMessage message = ERR_GET_SYMMETRIC_KEY_NO_VALUE.get();
                throw DecodeException.error(message);
            }

            String requestSymmetricKey = null;
            String instanceKeyID = null;

            try {
                final ASN1Reader reader = ASN1.getReader(requestValue);
                reader.readStartSequence();
                if (reader.hasNextElement() && (reader.peekType() == TYPE_SYMMETRIC_KEY_ELEMENT)) {
                    requestSymmetricKey = reader.readOctetStringAsString();
                }
                if (reader.hasNextElement() && (reader.peekType() == TYPE_INSTANCE_KEY_ID_ELEMENT)) {
                    instanceKeyID = reader.readOctetStringAsString();
                }
                reader.readEndSequence();

                final GetSymmetricKeyExtendedRequest newRequest =
                        new GetSymmetricKeyExtendedRequest().setRequestSymmetricKey(
                                requestSymmetricKey).setInstanceKeyID(instanceKeyID);

                for (final Control control : request.getControls()) {
                    newRequest.addControl(control);
                }

                return newRequest;
            } catch (final IOException ioe) {
                logger.debug(LocalizableMessage.raw("%s", ioe));

                final LocalizableMessage message =
                        ERR_GET_SYMMETRIC_KEY_ASN1_DECODE_EXCEPTION.get(ioe.getMessage());
                throw DecodeException.error(message, ioe);
            }
        }
    }

    private static final class ResultDecoder extends AbstractExtendedResultDecoder<ExtendedResult> {

        public ExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }

        public ExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            return result;
        }
    }

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * The request OID for the get symmetric key extended operation.
     */
    public static final String OID = "1.3.6.1.4.1.26027.1.6.3";

    /**
     * The BER type value for the symmetric key element of the operation value.
     */
    private static final byte TYPE_SYMMETRIC_KEY_ELEMENT = (byte) 0x80;

    /**
     * The BER type value for the instance key ID element of the operation
     * value.
     */
    private static final byte TYPE_INSTANCE_KEY_ID_ELEMENT = (byte) 0x81;

    /**
     * A decoder which can be used to decode get symmetric key extended
     * operation requests.
     */
    public static final RequestDecoder REQUEST_DECODER = new RequestDecoder();

    /** No need to expose this. */
    private static final ResultDecoder RESULT_DECODER = new ResultDecoder();

    /**
     * Creates a new get symmetric key extended request.
     *
     * @return The new get symmetric key extended request.
     */
    public static GetSymmetricKeyExtendedRequest newRequest() {
        return new GetSymmetricKeyExtendedRequest();
    }

    private String requestSymmetricKey;
    private String instanceKeyID;

    /** Instantiation via factory. */
    private GetSymmetricKeyExtendedRequest() {
    }

    /**
     * Returns the instance key ID.
     *
     * @return The instance key ID.
     */
    public String getInstanceKeyID() {
        return instanceKeyID;
    }

    /** {@inheritDoc} */
    @Override
    public String getOID() {
        return OID;
    }

    /**
     * Returns the request symmetric key.
     *
     * @return The request symmetric key.
     */
    public String getRequestSymmetricKey() {
        return requestSymmetricKey;
    }

    /** {@inheritDoc} */
    @Override
    public ExtendedResultDecoder<ExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);

        try {
            writer.writeStartSequence();
            if (requestSymmetricKey != null) {
                writer.writeOctetString(TYPE_SYMMETRIC_KEY_ELEMENT, requestSymmetricKey);
            }
            if (instanceKeyID != null) {
                writer.writeOctetString(TYPE_INSTANCE_KEY_ID_ELEMENT, instanceKeyID);
            }
            writer.writeEndSequence();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }

        return buffer.toByteString();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasValue() {
        return true;
    }

    /**
     * Sets the instance key ID.
     *
     * @param instanceKeyID
     *            The instance key ID.
     * @return This get symmetric key request.
     */
    public GetSymmetricKeyExtendedRequest setInstanceKeyID(final String instanceKeyID) {
        this.instanceKeyID = instanceKeyID;
        return this;
    }

    /**
     * Sets the request symmetric key.
     *
     * @param requestSymmetricKey
     *            The request symmetric key.
     * @return This get symmetric key request.
     */
    public GetSymmetricKeyExtendedRequest setRequestSymmetricKey(final String requestSymmetricKey) {
        this.requestSymmetricKey = requestSymmetricKey;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GetSymmetricKeyExtendedRequest(requestName=");
        builder.append(getOID());
        builder.append(", requestSymmetricKey=");
        builder.append(requestSymmetricKey);
        builder.append(", instanceKeyID=");
        builder.append(instanceKeyID);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
