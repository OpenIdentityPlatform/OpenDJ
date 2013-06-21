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
