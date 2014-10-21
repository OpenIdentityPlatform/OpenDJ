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
