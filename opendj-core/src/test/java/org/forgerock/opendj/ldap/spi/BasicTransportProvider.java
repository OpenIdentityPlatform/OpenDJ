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
 *      Copyright 2013-2015 ForgeRock AS.
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
