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
 * Unmodifiable CRAM-MD5 SASL bind request implementation.
 */
final class UnmodifiableCRAMMD5SASLBindRequestImpl extends
        AbstractUnmodifiableSASLBindRequest<CRAMMD5SASLBindRequest> implements
        CRAMMD5SASLBindRequest {
    UnmodifiableCRAMMD5SASLBindRequestImpl(final CRAMMD5SASLBindRequest impl) {
        super(impl);
    }

    @Override
    public String getAuthenticationID() {
        return impl.getAuthenticationID();
    }

    @Override
    public byte[] getPassword() {
        // Defensive copy.
        return StaticUtils.copyOfBytes(impl.getPassword());
    }

    @Override
    public CRAMMD5SASLBindRequest setAuthenticationID(final String authenticationID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CRAMMD5SASLBindRequest setPassword(final byte[] password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CRAMMD5SASLBindRequest setPassword(final char[] password) {
        throw new UnsupportedOperationException();
    }
}
