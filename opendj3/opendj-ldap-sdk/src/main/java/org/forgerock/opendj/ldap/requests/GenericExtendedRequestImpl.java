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
 *      Portions copyright 2012 ForgeRock AS.
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

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * Generic extended request implementation.
 */
final class GenericExtendedRequestImpl extends
        AbstractExtendedRequest<GenericExtendedRequest, GenericExtendedResult> implements
        GenericExtendedRequest {

    static final class RequestDecoder implements
            ExtendedRequestDecoder<GenericExtendedRequest, GenericExtendedResult> {
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

        public GenericExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }

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
    }

    private static final GenericExtendedResultDecoder RESULT_DECODER =
            new GenericExtendedResultDecoder();

    private ByteString requestValue = null;
    private String requestName;

    /**
     * Creates a new generic extended request using the provided name.
     *
     * @param requestName
     *            The dotted-decimal representation of the unique OID
     *            corresponding to this extended request.
     * @throws NullPointerException
     *             If {@code requestName} was {@code null}.
     */
    GenericExtendedRequestImpl(final String requestName) {
        this.requestName = requestName;
    }

    /**
     * Creates a new generic extended request that is an exact copy of the
     * provided request.
     *
     * @param genericExtendedRequest
     *            The generic extended request to be copied.
     * @throws NullPointerException
     *             If {@code extendedRequest} was {@code null} .
     */
    protected GenericExtendedRequestImpl(GenericExtendedRequest genericExtendedRequest) {
        super(genericExtendedRequest);
        this.requestName = genericExtendedRequest.getOID();
        this.requestValue = genericExtendedRequest.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOID() {
        return requestName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExtendedResultDecoder<GenericExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString getValue() {
        return requestValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasValue() {
        return requestValue != null;
    }

    /**
     * {@inheritDoc}
     */
    public GenericExtendedRequest setOID(final String oid) {
        Validator.ensureNotNull(oid);
        this.requestName = oid;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public GenericExtendedRequest setValue(final Object value) {
        this.requestValue = value != null ? ByteString.valueOf(value) : null;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GenericExtendedRequest(requestName=");
        builder.append(getOID());
        if (hasValue()) {
            builder.append(", requestValue=");
            StaticUtils.toHexPlusAscii(getValue(), builder, 4);
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
