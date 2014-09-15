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
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Responses;

/**
 * Bind result future implementation.
 */
public final class LDAPBindFutureResultImpl extends AbstractLDAPFutureResultImpl<BindResult> {
    private final BindClient bindClient;

    /**
     * Creates an bind future result.
     *
     * @param requestID
     *            identifier of the request
     * @param bindClient
     *            client that binds to the server
     * @param intermediateResponseHandler
     *            handler that consumes intermediate responses from extended
     *            operations
     * @param connection
     *            the connection to directory server
     */
    public LDAPBindFutureResultImpl(final int requestID, final BindClient bindClient,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        super(requestID, intermediateResponseHandler, connection);
        this.bindClient = bindClient;
    }

    @Override
    public boolean isBindOrStartTLS() {
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("LDAPBindFutureResultImpl(");
        sb.append("bindClient = ");
        sb.append(bindClient);
        super.toString(sb);
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns the client.
     *
     * @return the bind client
     */
    public BindClient getBindClient() {
        return bindClient;
    }

    @Override
    protected BindResult newErrorResult(final ResultCode resultCode, final String diagnosticMessage,
            final Throwable cause) {
        return Responses.newBindResult(resultCode).setDiagnosticMessage(diagnosticMessage)
                .setCause(cause);
    }
}
