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
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * Bind result implementation.
 */
final class BindResultImpl extends AbstractResultImpl<BindResult> implements BindResult {
    private ByteString credentials;

    BindResultImpl(final BindResult bindResult) {
        super(bindResult);
        this.credentials = bindResult.getServerSASLCredentials();
    }

    BindResultImpl(final ResultCode resultCode) {
        super(resultCode);
    }

    @Override
    public ByteString getServerSASLCredentials() {
        return credentials;
    }

    @Override
    public boolean isSASLBindInProgress() {
        final ResultCode code = getResultCode();
        return code.equals(ResultCode.SASL_BIND_IN_PROGRESS);
    }

    @Override
    public BindResult setServerSASLCredentials(final ByteString credentials) {
        this.credentials = credentials;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("BindResult(resultCode=");
        builder.append(getResultCode());
        builder.append(", matchedDN=");
        builder.append(getMatchedDN());
        builder.append(", diagnosticMessage=");
        builder.append(getDiagnosticMessage());
        builder.append(", referrals=");
        builder.append(getReferralURIs());
        builder.append(", serverSASLCreds=");
        builder.append(getServerSASLCredentials() == null ? ByteString.empty()
                : getServerSASLCredentials());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    BindResult getThis() {
        return this;
    }

}
