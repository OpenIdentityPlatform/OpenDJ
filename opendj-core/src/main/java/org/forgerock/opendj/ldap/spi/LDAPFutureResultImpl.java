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
 *      Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;

/**
 * Result future implementation.
 */
public final class LDAPFutureResultImpl extends AbstractLDAPFutureResultImpl<Result> {
    private final Request request;

    /**
     * Creates a future result.
     *
     * @param requestID
     *            identifier of the request
     * @param request
     *            the request sent to server
     * @param intermediateResponseHandler
     *            handler that consumes intermediate responses from extended
     *            operations
     * @param connection
     *            the connection to directory server
     */
    public LDAPFutureResultImpl(final int requestID, final Request request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        super(requestID, intermediateResponseHandler, connection);
        this.request = request;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("LDAPFutureResultImpl(");
        sb.append("request = ");
        sb.append(request);
        super.toString(sb);
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns the initial request.
     *
     * @return the request
     */
    public Request getRequest() {
        return request;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Result newErrorResult(final ResultCode resultCode, final String diagnosticMessage,
            final Throwable cause) {
        return Responses.newResult(resultCode).setDiagnosticMessage(diagnosticMessage).setCause(
                cause);
    }
}
