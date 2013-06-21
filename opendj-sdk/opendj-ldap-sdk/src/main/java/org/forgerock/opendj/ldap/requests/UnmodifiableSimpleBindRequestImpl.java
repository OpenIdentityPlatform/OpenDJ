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
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Unmodifiable simple bind request implementation.
 */
final class UnmodifiableSimpleBindRequestImpl extends
        AbstractUnmodifiableBindRequest<SimpleBindRequest> implements SimpleBindRequest {
    UnmodifiableSimpleBindRequestImpl(final SimpleBindRequest impl) {
        super(impl);
    }

    @Override
    public byte[] getPassword() {
        // Defensive copy.
        return StaticUtils.copyOfBytes(impl.getPassword());
    }

    @Override
    public SimpleBindRequest setName(final String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SimpleBindRequest setPassword(final byte[] password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SimpleBindRequest setPassword(final char[] password) {
        throw new UnsupportedOperationException();
    }
}
