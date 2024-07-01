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

/**
 * An abstract unmodifiable SASL Bind request which can be used as the basis for
 * implementing new unmodifiable SASL authentication methods.
 *
 * @param <R>
 *            The type of SASL Bind request.
 */
abstract class AbstractUnmodifiableSASLBindRequest<R extends SASLBindRequest> extends
        AbstractUnmodifiableBindRequest<R> implements SASLBindRequest {

    AbstractUnmodifiableSASLBindRequest(final R impl) {
        super(impl);
    }

    @Override
    public String getSASLMechanism() {
        return impl.getSASLMechanism();
    }
}
