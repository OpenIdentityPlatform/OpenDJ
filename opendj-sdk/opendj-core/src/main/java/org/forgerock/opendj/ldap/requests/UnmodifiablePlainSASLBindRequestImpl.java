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
 * Portions copyright 2011-2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Unmodifiable plain SASL bind request implementation.
 */
final class UnmodifiablePlainSASLBindRequestImpl extends
        AbstractUnmodifiableSASLBindRequest<PlainSASLBindRequest> implements PlainSASLBindRequest {
    UnmodifiablePlainSASLBindRequestImpl(final PlainSASLBindRequest impl) {
        super(impl);
    }

    @Override
    public String getAuthenticationID() {
        return impl.getAuthenticationID();
    }

    @Override
    public String getAuthorizationID() {
        return impl.getAuthorizationID();
    }

    @Override
    public byte[] getPassword() {
        // Defensive copy.
        return StaticUtils.copyOfBytes(impl.getPassword());
    }

    @Override
    public PlainSASLBindRequest setAuthenticationID(final String authenticationID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlainSASLBindRequest setAuthorizationID(final String authorizationID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlainSASLBindRequest setPassword(final byte[] password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlainSASLBindRequest setPassword(final char[] password) {
        throw new UnsupportedOperationException();
    }
}
