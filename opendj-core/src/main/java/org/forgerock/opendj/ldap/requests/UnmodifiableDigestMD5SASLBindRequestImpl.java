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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Unmodifiable digest-MD5 SASL bind request implementation.
 */
final class UnmodifiableDigestMD5SASLBindRequestImpl extends
        AbstractUnmodifiableSASLBindRequest<DigestMD5SASLBindRequest> implements
        DigestMD5SASLBindRequest {
    UnmodifiableDigestMD5SASLBindRequestImpl(final DigestMD5SASLBindRequest impl) {
        super(impl);
    }

    @Override
    public DigestMD5SASLBindRequest addAdditionalAuthParam(final String name, final String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest addQOP(final String... qopValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getAdditionalAuthParams() {
        return Collections.unmodifiableMap(impl.getAdditionalAuthParams());
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
    public String getCipher() {
        return impl.getCipher();
    }

    @Override
    public int getMaxReceiveBufferSize() {
        return impl.getMaxReceiveBufferSize();
    }

    @Override
    public int getMaxSendBufferSize() {
        return impl.getMaxSendBufferSize();
    }

    @Override
    public byte[] getPassword() {
        // Defensive copy.
        return StaticUtils.copyOfBytes(impl.getPassword());
    }

    @Override
    public List<String> getQOPs() {
        return Collections.unmodifiableList(impl.getQOPs());
    }

    @Override
    public String getRealm() {
        return impl.getRealm();
    }

    @Override
    public boolean isServerAuth() {
        return impl.isServerAuth();
    }

    @Override
    public DigestMD5SASLBindRequest setAuthenticationID(final String authenticationID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setAuthorizationID(final String authorizationID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setCipher(final String cipher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setMaxReceiveBufferSize(final int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setMaxSendBufferSize(final int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setPassword(final byte[] password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setPassword(final char[] password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setRealm(final String realm) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DigestMD5SASLBindRequest setServerAuth(final boolean serverAuth) {
        throw new UnsupportedOperationException();
    }
}
