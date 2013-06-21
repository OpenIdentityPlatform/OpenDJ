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

package org.forgerock.opendj.ldap.responses;

import org.forgerock.opendj.ldap.ByteString;

/**
 * Unmodifiable Bind result implementation.
 */
class UnmodifiableBindResultImpl extends AbstractUnmodifiableResultImpl<BindResult> implements
        BindResult {
    UnmodifiableBindResultImpl(final BindResult impl) {
        super(impl);
    }

    @Override
    public ByteString getServerSASLCredentials() {
        return impl.getServerSASLCredentials();
    }

    @Override
    public boolean isSASLBindInProgress() {
        return impl.isSASLBindInProgress();
    }

    @Override
    public BindResult setServerSASLCredentials(final ByteString credentials) {
        throw new UnsupportedOperationException();
    }
}
