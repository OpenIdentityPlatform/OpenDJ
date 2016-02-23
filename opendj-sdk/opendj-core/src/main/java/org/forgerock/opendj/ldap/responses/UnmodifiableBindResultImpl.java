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
 * Portions copyright 2012 ForgeRock AS.
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
