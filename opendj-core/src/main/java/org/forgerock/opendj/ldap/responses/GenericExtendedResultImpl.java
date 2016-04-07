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
 * Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * Generic extended result implementation.
 */
final class GenericExtendedResultImpl extends AbstractExtendedResult<GenericExtendedResult>
        implements ExtendedResult, GenericExtendedResult {

    private String responseName;
    private ByteString responseValue;

    GenericExtendedResultImpl(final GenericExtendedResult genericExtendedResult) {
        super(genericExtendedResult);
        this.responseName = genericExtendedResult.getOID();
        this.responseValue = genericExtendedResult.getValue();
    }

    GenericExtendedResultImpl(final ResultCode resultCode) {
        super(resultCode);
    }

    @Override
    public String getOID() {
        return responseName;
    }

    @Override
    public ByteString getValue() {
        return responseValue;
    }

    @Override
    public boolean hasValue() {
        return responseValue != null;
    }

    @Override
    public GenericExtendedResult setOID(final String oid) {
        this.responseName = oid;
        return this;
    }

    @Override
    public GenericExtendedResult setValue(final Object value) {
        this.responseValue = value != null ? ByteString.valueOfObject(value) : null;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GenericExtendedResult(resultCode=");
        builder.append(getResultCode());
        builder.append(", matchedDN=");
        builder.append(getMatchedDN());
        builder.append(", diagnosticMessage=");
        builder.append(getDiagnosticMessage());
        builder.append(", referrals=");
        builder.append(getReferralURIs());
        builder.append(", responseName=");
        builder.append(getOID() == null ? "" : getOID());
        if (hasValue()) {
            builder.append(", responseValue=");
            builder.append(getValue().toHexPlusAsciiString(4));
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
