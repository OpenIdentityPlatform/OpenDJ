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
 *      Portions copyright 2011 ForgeRock AS.
 */

package com.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * Extended result future implementation.
 *
 * @param <R>
 *            The type of result returned by this future.
 */
final class LDAPExtendedFutureResultImpl<R extends ExtendedResult> extends
        AbstractLDAPFutureResultImpl<R> {
    private final ExtendedRequest<R> request;

    LDAPExtendedFutureResultImpl(final int requestID, final ExtendedRequest<R> request,
            final ResultHandler<? super R> resultHandler,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        super(requestID, resultHandler, intermediateResponseHandler, connection);
        this.request = request;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("LDAPExtendedFutureResultImpl(");
        sb.append("request = ");
        sb.append(request);
        super.toString(sb);
        sb.append(")");
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isCancelable() {
        return !request.getOID().equals(StartTLSExtendedRequest.OID);
    }

    R decodeResult(final ExtendedResult result, final DecodeOptions options) throws DecodeException {
        return request.getResultDecoder().decodeExtendedResult(result, options);
    }

    ExtendedRequest<R> getRequest() {
        return request;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    R newErrorResult(final ResultCode resultCode, final String diagnosticMessage,
            final Throwable cause) {
        return request.getResultDecoder().newExtendedErrorResult(resultCode, "", diagnosticMessage);
    }
}
