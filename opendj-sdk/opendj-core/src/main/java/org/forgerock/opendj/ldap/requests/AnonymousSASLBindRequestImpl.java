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

import org.forgerock.util.Reject;

/**
 * Anonymous SASL bind request implementation.
 */
final class AnonymousSASLBindRequestImpl extends AbstractSASLBindRequest<AnonymousSASLBindRequest>
        implements AnonymousSASLBindRequest {
    private static final class Client extends SASLBindClientImpl {
        private Client(final AnonymousSASLBindRequestImpl initialBindRequest,
                final String serverName) {
            super(initialBindRequest);
            setNextSASLCredentials(ByteString.valueOfUtf8(initialBindRequest.getTraceString()));
        }
    }

    private String traceString;

    AnonymousSASLBindRequestImpl(final AnonymousSASLBindRequest anonymousSASLBindRequest) {
        super(anonymousSASLBindRequest);
        this.traceString = anonymousSASLBindRequest.getTraceString();
    }

    AnonymousSASLBindRequestImpl(final String traceString) {
        Reject.ifNull(traceString);
        this.traceString = traceString;
    }

    @Override
    public BindClient createBindClient(final String serverName) {
        return new Client(this, serverName);
    }

    @Override
    public String getSASLMechanism() {
        return SASL_MECHANISM_NAME;
    }

    @Override
    public String getTraceString() {
        return traceString;
    }

    @Override
    public AnonymousSASLBindRequest setTraceString(final String traceString) {
        Reject.ifNull(traceString);
        this.traceString = traceString;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AnonymousSASLBindRequest(bindDN=");
        builder.append(getName());
        builder.append(", authentication=SASL");
        builder.append(", saslMechanism=");
        builder.append(getSASLMechanism());
        builder.append(", traceString=");
        builder.append(traceString);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
