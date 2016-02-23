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
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

/**
 * An abstract unmodifiable Extended request which can be used as the basis for
 * implementing new unmodifiable Extended operations.
 *
 * @param <R>
 *            The type of extended request.
 * @param <S>
 *            The type of result.
 */
abstract class AbstractUnmodifiableExtendedRequest<R extends ExtendedRequest<S>, S extends ExtendedResult>
        extends AbstractUnmodifiableRequest<R> implements ExtendedRequest<S> {
    AbstractUnmodifiableExtendedRequest(final R impl) {
        super(impl);
    }

    @Override
    public final String getOID() {
        return impl.getOID();
    }

    @Override
    public final ExtendedResultDecoder<S> getResultDecoder() {
        return impl.getResultDecoder();
    }

    @Override
    public final ByteString getValue() {
        return impl.getValue();
    }

    @Override
    public final boolean hasValue() {
        return impl.hasValue();
    }
}
