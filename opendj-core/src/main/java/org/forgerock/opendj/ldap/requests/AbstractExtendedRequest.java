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

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

/**
 * An abstract Extended request which can be used as the basis for implementing
 * new Extended operations.
 *
 * @param <R>
 *            The type of extended request.
 * @param <S>
 *            The type of result.
 */
public abstract class AbstractExtendedRequest<R extends ExtendedRequest<S>, S extends ExtendedResult>
        extends AbstractRequestImpl<R> implements ExtendedRequest<S> {

    /**
     * Creates a new abstract extended request.
     */
    protected AbstractExtendedRequest() {
        // Nothing to do.
    }

    /**
     * Creates a new extended request that is an exact copy of the provided
     * request.
     *
     * @param extendedRequest
     *            The extended request to be copied.
     * @throws NullPointerException
     *             If {@code extendedRequest} was {@code null} .
     */
    protected AbstractExtendedRequest(final ExtendedRequest<S> extendedRequest) {
        super(extendedRequest);
    }

    @Override
    public abstract String getOID();

    @Override
    public abstract ExtendedResultDecoder<S> getResultDecoder();

    @Override
    public abstract ByteString getValue();

    @Override
    public abstract boolean hasValue();

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ExtendedRequest(requestName=");
        builder.append(getOID());
        if (hasValue()) {
            builder.append(", requestValue=");
            builder.append(getValue().toHexPlusAsciiString(4));
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    final R getThis() {
        return (R) this;
    }

}
