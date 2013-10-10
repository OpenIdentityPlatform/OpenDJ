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
 *      Copyright 2013 ForgeRock AS.
 */

package com.forgerock.opendj.ldap;

import java.io.IOException;
import java.net.SocketAddress;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;

/**
 * Basic LDAP listener implementation to use for tests only.
 */
public final class BasicLDAPListener implements LDAPListenerImpl {
    private final ServerConnectionFactory<LDAPClientContext, Integer> connectionFactory;
    private final SocketAddress socketAddress;

    /**
     * Creates a new LDAP listener implementation which does nothing.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory can be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             is never thrown with this do-nothing implementation
     */
    public BasicLDAPListener(final SocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final LDAPListenerOptions options) throws IOException {
        this.connectionFactory = factory;
        this.socketAddress = address;
    }

    @Override
    public void close() {
        // nothing to do
    }

    /** {@inheritDoc} */
    @Override
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPListener(");
        builder.append(getSocketAddress().toString());
        builder.append(')');
        return builder.toString();
    }

    ServerConnectionFactory<LDAPClientContext, Integer> getConnectionFactory() {
        return connectionFactory;
    }
}
