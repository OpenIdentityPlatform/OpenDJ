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

package org.forgerock.opendj.ldap.responses;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_WHOAMI_INVALID_AUTHZID_TYPE;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * Who Am I extended result implementation.
 */
final class WhoAmIExtendedResultImpl extends AbstractExtendedResult<WhoAmIExtendedResult> implements
        WhoAmIExtendedResult {

    /** The authorization ID. */
    private String authorizationID;

    /** Instantiation via factory. */
    WhoAmIExtendedResultImpl(final ResultCode resultCode) {
        super(resultCode);
    }

    WhoAmIExtendedResultImpl(final WhoAmIExtendedResult whoAmIExtendedResult) {
        super(whoAmIExtendedResult);
        this.authorizationID = whoAmIExtendedResult.getAuthorizationID();
    }

    @Override
    public String getAuthorizationID() {
        return authorizationID;
    }

    @Override
    public String getOID() {
        // No response name defined.
        return null;
    }

    @Override
    public ByteString getValue() {
        return (authorizationID != null) ? ByteString.valueOfUtf8(authorizationID) : null;
    }

    @Override
    public boolean hasValue() {
        return authorizationID != null;
    }

    @Override
    public WhoAmIExtendedResult setAuthorizationID(final String authorizationID) {
        if (authorizationID != null && authorizationID.length() != 0) {
            final int colonIndex = authorizationID.indexOf(':');
            if (colonIndex < 0) {
                final LocalizableMessage message =
                        ERR_WHOAMI_INVALID_AUTHZID_TYPE.get(authorizationID);
                throw new LocalizedIllegalArgumentException(message);
            }
        }

        this.authorizationID = authorizationID;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("WhoAmIExtendedResponse(resultCode=");
        builder.append(getResultCode());
        builder.append(", matchedDN=");
        builder.append(getMatchedDN());
        builder.append(", diagnosticMessage=");
        builder.append(getDiagnosticMessage());
        builder.append(", referrals=");
        builder.append(getReferralURIs());
        builder.append(", authzId=");
        builder.append(authorizationID);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
