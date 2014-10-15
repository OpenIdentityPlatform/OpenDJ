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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.getProvider;

import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

/**
 * A factory class which can be used to obtain connections to an LDAP Directory
 * Server.
 */
public final class LDAPConnectionFactory implements ConnectionFactory {
    /**
     * We implement the factory using the pimpl idiom in order to avoid making
     * too many implementation classes public.
     */
    private final LDAPConnectionFactoryImpl impl;

    /**
     * Transport provider that provides the implementation of this factory.
     */
    private final TransportProvider provider;

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided host and port
     * number.
     *
     * @param host
     *            The host name.
     * @param port
     *            The port number.
     * @throws NullPointerException
     *             If {@code host} was {@code null}.
     * @throws ProviderNotFoundException if no provider is available or if the
     *             provider requested using options is not found.
     */
    public LDAPConnectionFactory(final String host, final int port) {
        this(host, port, new LDAPOptions());
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided host and port
     * number.
     *
     * @param host
     *            The host name.
     * @param port
     *            The port number.
     * @param options
     *            The LDAP options to use when creating connections.
     * @throws NullPointerException
     *             If {@code host} or {@code options} was {@code null}.
     * @throws ProviderNotFoundException if no provider is available or if the
     *             provider requested using options is not found.
     */
    public LDAPConnectionFactory(final String host, final int port, final LDAPOptions options) {
        Reject.ifNull(host, options);
        this.provider = getProvider(TransportProvider.class, options.getTransportProvider(),
                options.getProviderClassLoader());
        this.impl = provider.getLDAPConnectionFactory(host, port, options);
    }

    @Override
    public void close() {
        impl.close();
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        return impl.getConnectionAsync();
    }

    @Override
    public Connection getConnection() throws LdapException {
        return impl.getConnection();
    }

    /**
     * Returns the host name of the Directory Server. The returned host name is
     * the same host name that was provided during construction and may be an IP
     * address. More specifically, this method will not perform a reverse DNS
     * lookup.
     *
     * @return The host name of the Directory Server.
     */
    public String getHostName() {
        return impl.getHostName();
    }

    /**
     * Returns the port of the Directory Server.
     *
     * @return The port of the Directory Server.
     */
    public int getPort() {
        return impl.getPort();
    }

    /**
     * Returns the name of the transport provider, which provides the implementation
     * of this factory.
     *
     * @return The name of actual transport provider.
     */
    public String getProviderName() {
        return provider.getName();
    }

    @Override
    public String toString() {
        return impl.toString();
    }
}
