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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_CANCEL_NO_REQUEST_VALUE;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.Responses;

/**
 * Cancel extended request implementation.
 */
final class CancelExtendedRequestImpl extends
        AbstractExtendedRequest<CancelExtendedRequest, ExtendedResult> implements
        CancelExtendedRequest {
    static final class RequestDecoder implements
            ExtendedRequestDecoder<CancelExtendedRequest, ExtendedResult> {
        @Override
        public CancelExtendedRequest decodeExtendedRequest(final ExtendedRequest<?> request,
                final DecodeOptions options) throws DecodeException {
            final ByteString requestValue = request.getValue();
            if (requestValue == null || requestValue.length() <= 0) {
                throw DecodeException.error(ERR_EXTOP_CANCEL_NO_REQUEST_VALUE.get());
            }

            try {
                final ASN1Reader reader = ASN1.getReader(requestValue);
                reader.readStartSequence();
                final int idToCancel = (int) reader.readInteger();
                reader.readEndSequence();

                final CancelExtendedRequest newRequest = new CancelExtendedRequestImpl(idToCancel);

                for (final Control control : request.getControls()) {
                    newRequest.addControl(control);
                }

                return newRequest;
            } catch (final IOException e) {
                final LocalizableMessage message =
                        ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE.get(getExceptionMessage(e));
                throw DecodeException.error(message, e);
            }
        }
    }

    private static final class ResultDecoder extends AbstractExtendedResultDecoder<ExtendedResult> {
        @Override
        public ExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            // TODO: Should we check to make sure OID and value is null?
            return result;
        }

        @Override
        public ExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }
    }

    /** No need to expose this. */
    private static final ExtendedResultDecoder<ExtendedResult> RESULT_DECODER = new ResultDecoder();

    private int requestID;

    CancelExtendedRequestImpl(final CancelExtendedRequest cancelExtendedRequest) {
        super(cancelExtendedRequest);
        this.requestID = cancelExtendedRequest.getRequestID();
    }

    /** Instantiation via factory. */
    CancelExtendedRequestImpl(final int requestID) {
        this.requestID = requestID;
    }

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public int getRequestID() {
        return requestID;
    }

    @Override
    public ExtendedResultDecoder<ExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    @Override
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder(6);
        final ASN1Writer writer = ASN1.getWriter(buffer);

        try {
            writer.writeStartSequence();
            writer.writeInteger(requestID);
            writer.writeEndSequence();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }

        return buffer.toByteString();
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    @Override
    public CancelExtendedRequest setRequestID(final int id) {
        this.requestID = id;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("CancelExtendedRequest(requestName=");
        builder.append(getOID());
        builder.append(", requestID=");
        builder.append(requestID);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
