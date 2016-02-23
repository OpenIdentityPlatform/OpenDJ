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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.util.Options;

/**
 * Provides an basic implementation of a transport provider doing nothing.
 * This should be used for tests only.
 * <p>
 * To be used, this implementation must be declared in the
 * provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.TransportProvider}
 * with this single line:
 *
 * <pre>
 * com.forgerock.opendj.ldap.BasicTransportProvider
 * </pre>.
 */
public class BasicTransportProvider implements TransportProvider {

    @Override
    public LDAPConnectionFactoryImpl getLDAPConnectionFactory(String host, int port, Options options) {
        return new BasicLDAPConnectionFactory(host, port, options);
    }

    @Override
    public LDAPListenerImpl getLDAPListener(
            InetSocketAddress address, ServerConnectionFactory<LDAPClientContext, Integer> factory, Options options)
            throws IOException {
        return new BasicLDAPListener(address, factory, options);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Basic";
    }

}
