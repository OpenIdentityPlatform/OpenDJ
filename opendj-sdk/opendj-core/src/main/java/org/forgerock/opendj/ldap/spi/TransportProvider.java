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
 * Interface for transport providers, which provide implementation
 * for {@code LDAPConnectionFactory} and {@code LDAPListener} classes,
 * using a specific transport.
 * <p>
 * A transport provider must be declared in the provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.TransportProvider}
 * in order to allow automatic loading of the implementation classes using the
 * {@code java.util.ServiceLoader} facility.
 */
public interface TransportProvider extends Provider {

    /**
     * Returns an implementation of {@code LDAPConnectionFactory}. The address
     * will be resolved each time a new connection is returned.
     *
     * @param host
     *            The hostname of the Directory Server to connect to.
     * @param port
     *            The port number of the Directory Server to connect to.
     * @param options
     *            The LDAP options to use when creating connections.
     * @return an implementation of {@code LDAPConnectionFactory}
     */
    LDAPConnectionFactoryImpl getLDAPConnectionFactory(String host, int port, Options options);

  /**
     * Returns an implementation of {@code LDAPListener}.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @return an implementation of {@code LDAPListener}
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     */
    LDAPListenerImpl getLDAPListener(InetSocketAddress address,
            ServerConnectionFactory<LDAPClientContext, Integer> factory, Options options)
            throws IOException;

}
