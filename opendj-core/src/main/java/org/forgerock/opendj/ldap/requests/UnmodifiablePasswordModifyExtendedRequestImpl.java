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
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Unmodifiable password modify extended request implementation.
 */
final class UnmodifiablePasswordModifyExtendedRequestImpl
        extends
        AbstractUnmodifiableExtendedRequest<PasswordModifyExtendedRequest, PasswordModifyExtendedResult>
        implements PasswordModifyExtendedRequest {
    UnmodifiablePasswordModifyExtendedRequestImpl(final PasswordModifyExtendedRequest impl) {
        super(impl);
    }

    @Override
    public byte[] getNewPassword() {
        // Defensive copy.
        return StaticUtils.copyOfBytes(impl.getNewPassword());
    }

    @Override
    public byte[] getOldPassword() {
        // Defensive copy.
        return StaticUtils.copyOfBytes(impl.getOldPassword());
    }

    @Override
    public ByteString getUserIdentity() {
        return impl.getUserIdentity();
    }

    @Override
    public String getUserIdentityAsString() {
        return impl.getUserIdentityAsString();
    }

    @Override
    public PasswordModifyExtendedRequest setNewPassword(final byte[] newPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PasswordModifyExtendedRequest setNewPassword(final char[] newPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PasswordModifyExtendedRequest setOldPassword(final byte[] oldPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PasswordModifyExtendedRequest setOldPassword(final char[] oldPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PasswordModifyExtendedRequest setUserIdentity(final Object userIdentity) {
        throw new UnsupportedOperationException();
    }
}
