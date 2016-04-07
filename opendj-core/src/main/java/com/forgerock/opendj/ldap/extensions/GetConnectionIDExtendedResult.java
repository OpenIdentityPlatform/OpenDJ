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
package com.forgerock.opendj.ldap.extensions;

import java.io.IOException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResult;

import org.forgerock.util.Reject;

/**
 * Get connection ID extended result.
 *
 * @see GetConnectionIDExtendedRequest
 */
public final class GetConnectionIDExtendedResult extends
        AbstractExtendedResult<GetConnectionIDExtendedResult> {
    /**
     * Creates a new get connection ID extended result with a default connection
     * ID of -1.
     *
     * @param resultCode
     *            The result code.
     * @return The new get connection ID extended result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static GetConnectionIDExtendedResult newResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new GetConnectionIDExtendedResult(resultCode);
    }

    private int connectionID = -1;

    private GetConnectionIDExtendedResult(final ResultCode resultCode) {
        super(resultCode);
    }

    /**
     * Returns the client connection ID.
     *
     * @return The client connection ID.
     */
    public int getConnectionID() {
        return connectionID;
    }

    @Override
    public String getOID() {
        return GetConnectionIDExtendedRequest.OID;
    }

    @Override
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder(6);
        final ASN1Writer writer = ASN1.getWriter(buffer);

        try {
            writer.writeInteger(connectionID);
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }

        return buffer.toByteString();
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    /**
     * Sets the client connection ID.
     *
     * @param connectionID
     *            The client connection ID.
     * @return This get connection ID result.
     */
    public GetConnectionIDExtendedResult setConnectionID(final int connectionID) {
        this.connectionID = connectionID;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GetConnectionIDExtendedResponse(resultCode=");
        builder.append(getResultCode());
        builder.append(", matchedDN=");
        builder.append(getMatchedDN());
        builder.append(", diagnosticMessage=");
        builder.append(getDiagnosticMessage());
        builder.append(", referrals=");
        builder.append(getReferralURIs());
        builder.append(", responseName=");
        builder.append(getOID());
        builder.append(", connectionID=");
        builder.append(connectionID);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
