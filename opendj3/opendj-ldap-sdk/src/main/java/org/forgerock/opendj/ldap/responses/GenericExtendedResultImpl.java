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

import com.forgerock.opendj.util.StaticUtils;

/**
 * Generic extended result implementation.
 */
final class GenericExtendedResultImpl extends AbstractExtendedResult<GenericExtendedResult>
        implements ExtendedResult, GenericExtendedResult {

    private String responseName = null;
    private ByteString responseValue = null;

    /**
     * Creates a new generic extended result using the provided result code.
     *
     * @param resultCode
     *            The result code.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    GenericExtendedResultImpl(final ResultCode resultCode) {
        super(resultCode);
    }

    /**
     * Creates a new generic extended result that is an exact copy of the
     * provided result.
     *
     * @param genericExtendedResult
     *            The generic extended result to be copied.
     * @throws NullPointerException
     *             If {@code genericExtendedResult} was {@code null} .
     */
    GenericExtendedResultImpl(final GenericExtendedResult genericExtendedResult) {
        super(genericExtendedResult);
        this.responseName = genericExtendedResult.getOID();
        this.responseValue = genericExtendedResult.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public String getOID() {
        return responseName;
    }

    /**
     * {@inheritDoc}
     */
    public ByteString getValue() {
        return responseValue;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasValue() {
        return responseValue != null;
    }

    /**
     * {@inheritDoc}
     */
    public GenericExtendedResult setOID(final String oid) {
        this.responseName = oid;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public GenericExtendedResult setValue(final Object value) {
        this.responseValue = value != null ? ByteString.valueOf(value) : null;
        return this;
    }

    /**
     * {@inheritDoc}
     */
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
            StaticUtils.toHexPlusAscii(getValue(), builder, 4);
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
