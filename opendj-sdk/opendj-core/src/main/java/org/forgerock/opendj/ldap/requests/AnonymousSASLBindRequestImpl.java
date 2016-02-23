/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
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
        private Client(final AnonymousSASLBindRequestImpl initialBindRequest) {
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
        return new Client(this);
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
