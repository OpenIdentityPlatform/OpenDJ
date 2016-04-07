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
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.LdapException;

/**
 * An abstract unmodifiable Bind request which can be used as the basis for
 * implementing new unmodifiable authentication methods.
 *
 * @param <R>
 *            The type of Bind request.
 */
abstract class AbstractUnmodifiableBindRequest<R extends BindRequest> extends
        AbstractUnmodifiableRequest<R> implements BindRequest {

    AbstractUnmodifiableBindRequest(final R impl) {
        super(impl);
    }

    @Override
    public BindClient createBindClient(final String serverName) throws LdapException {
        return impl.createBindClient(serverName);
    }

    @Override
    public byte getAuthenticationType() {
        return impl.getAuthenticationType();
    }

    @Override
    public String getName() {
        return impl.getName();
    }
}
