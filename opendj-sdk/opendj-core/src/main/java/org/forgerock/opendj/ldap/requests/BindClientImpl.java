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
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.BindResult;

/**
 * Bind client implementation.
 */
class BindClientImpl implements BindClient, ConnectionSecurityLayer {
    private final GenericBindRequest nextBindRequest;

    BindClientImpl(final BindRequest initialBindRequest) {
        this.nextBindRequest =
                new GenericBindRequestImpl(initialBindRequest.getName(), initialBindRequest
                        .getAuthenticationType(), new byte[0], this);
        for (final Control control : initialBindRequest.getControls()) {
            this.nextBindRequest.addControl(control);
        }
    }

    /**
     * Default implementation does nothing.
     */
    @Override
    public void dispose() {
        // Do nothing.
    }

    /**
     * Default implementation does nothing and always returns {@code true}.
     */
    @Override
    public boolean evaluateResult(final BindResult result) throws LdapException {
        return true;
    }

    /**
     * Default implementation always returns {@code null}.
     */
    @Override
    public ConnectionSecurityLayer getConnectionSecurityLayer() {
        return null;
    }

    /**
     * Returns the next bind request.
     */
    @Override
    public final GenericBindRequest nextBindRequest() {
        return nextBindRequest;
    }

    /**
     * Default implementation just returns the copy of the bytes.
     */
    @Override
    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws LdapException {
        final byte[] copy = new byte[len];
        System.arraycopy(incoming, offset, copy, 0, len);
        return copy;
    }

    /**
     * Default implementation just returns the copy of the bytes.
     */
    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws LdapException {
        final byte[] copy = new byte[len];
        System.arraycopy(outgoing, offset, copy, 0, len);
        return copy;
    }

    /**
     * Sets the authentication value to be used in the next bind request.
     *
     * @param authenticationValue
     *            The authentication value to be used in the next bind request.
     * @return A reference to this bind client.
     */
    final BindClient setNextAuthenticationValue(final byte[] authenticationValue) {
        nextBindRequest.setAuthenticationValue(authenticationValue);
        return this;
    }

}
