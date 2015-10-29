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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;

import org.forgerock.util.Reject;

/**
 * Generic extended request implementation.
 */
final class GenericExtendedRequestImpl extends
        AbstractExtendedRequest<GenericExtendedRequest, GenericExtendedResult> implements
        GenericExtendedRequest {

    static final class RequestDecoder implements
            ExtendedRequestDecoder<GenericExtendedRequest, GenericExtendedResult> {
        @Override
        public GenericExtendedRequest decodeExtendedRequest(final ExtendedRequest<?> request,
                final DecodeOptions options) throws DecodeException {
            if (request instanceof GenericExtendedRequest) {
                return (GenericExtendedRequest) request;
            } else {
                final GenericExtendedRequest newRequest =
                        new GenericExtendedRequestImpl(request.getOID()).setValue(request
                                .getValue());

                for (final Control control : request.getControls()) {
                    newRequest.addControl(control);
                }

                return newRequest;
            }
        }
    }

    private static final class GenericExtendedResultDecoder extends
            AbstractExtendedResultDecoder<GenericExtendedResult> {

        @Override
        public GenericExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            if (result instanceof GenericExtendedResult) {
                return (GenericExtendedResult) result;
            } else {
                final GenericExtendedResult newResult =
                        Responses.newGenericExtendedResult(result.getResultCode()).setMatchedDN(
                                result.getMatchedDN()).setDiagnosticMessage(
                                result.getDiagnosticMessage()).setOID(result.getOID()).setValue(
                                result.getValue());
                for (final Control control : result.getControls()) {
                    newResult.addControl(control);
                }
                return newResult;
            }
        }

        @Override
        public GenericExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }
    }

    private static final GenericExtendedResultDecoder RESULT_DECODER =
            new GenericExtendedResultDecoder();

    private String requestName;
    private ByteString requestValue;

    GenericExtendedRequestImpl(final GenericExtendedRequest genericExtendedRequest) {
        super(genericExtendedRequest);
        this.requestName = genericExtendedRequest.getOID();
        this.requestValue = genericExtendedRequest.getValue();
    }

    GenericExtendedRequestImpl(final String requestName) {
        this.requestName = requestName;
    }

    @Override
    public String getOID() {
        return requestName;
    }

    @Override
    public ExtendedResultDecoder<GenericExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    @Override
    public ByteString getValue() {
        return requestValue;
    }

    @Override
    public boolean hasValue() {
        return requestValue != null;
    }

    @Override
    public GenericExtendedRequest setOID(final String oid) {
        Reject.ifNull(oid);
        this.requestName = oid;
        return this;
    }

    @Override
    public GenericExtendedRequest setValue(final Object value) {
        this.requestValue = value != null ? ByteString.valueOfObject(value) : null;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GenericExtendedRequest(requestName=");
        builder.append(getOID());
        if (hasValue()) {
            builder.append(", requestValue=");
            builder.append(getValue().toHexPlusAsciiString(4));
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
