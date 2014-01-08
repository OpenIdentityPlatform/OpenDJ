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

/**
 * An abstract Intermediate response which can be used as the basis for
 * implementing new Intermediate responses.
 *
 * @param <S>
 *            The type of Intermediate response.
 */
public abstract class AbstractIntermediateResponse<S extends IntermediateResponse> extends
        AbstractResponseImpl<S> implements IntermediateResponse {

    /**
     * Creates a new intermediate response.
     */
    protected AbstractIntermediateResponse() {
        // Nothing to do.
    }

    /**
     * Creates a new intermediate response that is an exact copy of the provided
     * response.
     *
     * @param intermediateResponse
     *            The intermediate response to be copied.
     * @throws NullPointerException
     *             If {@code intermediateResponse} was {@code null} .
     */
    protected AbstractIntermediateResponse(final IntermediateResponse intermediateResponse) {
        super(intermediateResponse);
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
        builder.append("IntermediateResponse(responseName=");
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
