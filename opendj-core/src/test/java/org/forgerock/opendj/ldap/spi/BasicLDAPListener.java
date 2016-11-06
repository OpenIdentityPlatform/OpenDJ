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
 * Copyright 2013-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.util.Function;
import org.forgerock.util.Options;

import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Stream;

/**
 * Basic LDAP listener implementation to use for tests only.
 */
public final class BasicLDAPListener implements LDAPListenerImpl {
    private final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> connectionFactory;
    private final Set<InetSocketAddress> socketAddresses;

    /**
     * Creates a new LDAP listener implementation which does nothing.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory can be used to create server connections.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             is never thrown with this do-nothing implementation
     */
    public BasicLDAPListener(final Set<InetSocketAddress> addresses,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory,
            final Options options) throws IOException {
        this.connectionFactory = factory;
        this.socketAddresses = addresses;
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public Set<InetSocketAddress> getSocketAddresses() {
        return socketAddresses;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPListener(");
        builder.append(getSocketAddresses());
        builder.append(')');
        return builder.toString();
    }

    Function<LDAPClientContext,
             ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
             LdapException> getConnectionFactory() {
        return connectionFactory;
    }
}
