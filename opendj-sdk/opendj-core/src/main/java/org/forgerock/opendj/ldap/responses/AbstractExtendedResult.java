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
 * An abstract Extended result which can be used as the basis for implementing
 * new Extended operations.
 *
 * @param <S>
 *            The type of Extended result.
 */
public abstract class AbstractExtendedResult<S extends ExtendedResult> extends
        AbstractResultImpl<S> implements ExtendedResult {

    /**
     * Creates a new extended result that is an exact copy of the provided
     * result.
     *
     * @param extendedResult
     *            The extended result to be copied.
     * @throws NullPointerException
     *             If {@code extendedResult} was {@code null} .
     */
    protected AbstractExtendedResult(final ExtendedResult extendedResult) {
        super(extendedResult);
    }

    /**
     * Creates a new extended result using the provided result code.
     *
     * @param resultCode
     *            The result code.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    protected AbstractExtendedResult(final ResultCode resultCode) {
        super(resultCode);
    }

    @Override
    public abstract String getOID();

    @Override
    public abstract ByteString getValue();

    @Override
    public abstract boolean hasValue();

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ExtendedResult(resultCode=");
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

    @Override
    @SuppressWarnings("unchecked")
    final S getThis() {
        return (S) this;
    }
}
