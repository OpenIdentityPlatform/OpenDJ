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

/**
 * Unmodifiable Who Am I extended result implementation.
 */
class UnmodifiableWhoAmIExtendedResultImpl extends
        AbstractUnmodifiableExtendedResultImpl<WhoAmIExtendedResult> implements
        WhoAmIExtendedResult {
    UnmodifiableWhoAmIExtendedResultImpl(final WhoAmIExtendedResult impl) {
        super(impl);
    }

    @Override
    public String getAuthorizationID() {
        return impl.getAuthorizationID();
    }

    @Override
    public WhoAmIExtendedResult setAuthorizationID(final String authorizationID) {
        throw new UnsupportedOperationException();
    }
}
