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
 *      Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.forgerock.opendj.ldap.LDAPConnectionFactoryImpl;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * A factory class which can be used to obtain connections to an LDAP Directory
 * Server.
 */
public final class LDAPConnectionFactory implements ConnectionFactory {
    /*
     * We implement the factory using the pimpl idiom in order to avoid making
     * too many implementation classes public.
     */
    private final LDAPConnectionFactoryImpl impl;

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided address.
     *
     * @param address
     *            The address of the Directory Server.
     * @throws NullPointerException
     *             If {@code address} was {@code null}.
     * @deprecated use {@link #LDAPConnectionFactory(InetSocketAddress)} instead
     */
    @Deprecated
    public LDAPConnectionFactory(final SocketAddress address) {
        this((InetSocketAddress) address, new LDAPOptions());
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided address.
     *
     * @param address
     *            The address of the Directory Server.
     * @param options
     *            The LDAP options to use when creating connections.
     * @throws NullPointerException
     *             If {@code address} or {@code options} was {@code null}.
     * @deprecated use {@link #LDAPConnectionFactory(InetSocketAddress, LDAPOptions)} instead
     */
    @Deprecated
    public LDAPConnectionFactory(final SocketAddress address, final LDAPOptions options) {
        this((InetSocketAddress) address, options);
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided address.
     *
     * @param address
     *            The address of the Directory Server.
     * @throws NullPointerException
     *             If {@code address} was {@code null}.
     * @deprecated use {@link #LDAPConnectionFactory(String, int)} instead.
     */
    @Deprecated
    public LDAPConnectionFactory(final InetSocketAddress address) {
        this(address, new LDAPOptions());
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided address.
     *
     * @param address
     *            The address of the Directory Server.
     * @param options
     *            The LDAP options to use when creating connections.
     * @throws NullPointerException
     *             If {@code address} or {@code options} was {@code null}.
     * @deprecated use {@link #LDAPConnectionFactory(String, int,
     * org.forgerock.opendj.ldap.LDAPOptions)} instead.
     */
    @Deprecated
    public LDAPConnectionFactory(final InetSocketAddress address, final LDAPOptions options) {
        Validator.ensureNotNull(address, options);
        this.impl = new LDAPConnectionFactoryImpl(address, options);
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided host and port
     * address.
     *
     * @param host
     *            The host name.
     * @param port
     *            The port number.
     * @throws NullPointerException
     *             If {@code host} was {@code null}.
     */
    public LDAPConnectionFactory(final String host, final int port) {
        this(host, port, new LDAPOptions());
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided host and port
     * address.
     *
     * @param host
     *            The host name.
     * @param port
     *            The port number.
     * @param options
     *            The LDAP options to use when creating connections.
     * @throws NullPointerException
     *             If {@code host} or {@code options} was {@code null}.
     */
    public LDAPConnectionFactory(final String host, final int port, final LDAPOptions options) {
        Validator.ensureNotNull(host, options);
        this.impl = new LDAPConnectionFactoryImpl(host, port, options);
    }

    /**
     * Returns the {@code InetAddress} that this LDAP listener is listening on.
     *
     * @return The {@code InetAddress} that this LDAP listener is listening on,
     *         or {@code null} if it is unknown.
     */
    public InetAddress getAddress() {
        return getInetSocketAddress().getAddress();
    }

    @Override
    public void close() {
        impl.close();
    }

    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        return impl.getConnectionAsync(handler);
    }

    @Override
    public Connection getConnection() throws ErrorResultException {
        return impl.getConnection();
    }

    /**
     * Returns the host name that this LDAP listener is listening on. The
     * returned host name is the same host name that was provided during
     * construction and may be an IP address. More specifically, this method
     * will not perform a reverse DNS lookup.
     *
     * @return The host name that this LDAP listener is listening on.
     * @deprecated use {@link #getHostName()} instead
     */
    @Deprecated
    public String getHostname() {
        return getHostName();
    }

    /**
     * Returns the host name that this LDAP listener is listening on. The
     * returned host name is the same host name that was provided during
     * construction and may be an IP address. More specifically, this method
     * will not perform a reverse DNS lookup.
     *
     * @return The host name that this LDAP listener is listening on.
     */
    public String getHostName() {
        return StaticUtils.getHostName(getInetSocketAddress());
    }

    /**
     * Returns the port that this LDAP listener is listening on.
     *
     * @return The port that this LDAP listener is listening on.
     */
    public int getPort() {
        return getInetSocketAddress().getPort();
    }

    /**
     * Returns the address that this LDAP listener is listening on.
     *
     * @return The address that this LDAP listener is listening on.
     */
    public SocketAddress getSocketAddress() {
        return getInetSocketAddress();
    }

    private InetSocketAddress getInetSocketAddress() {
        return impl.getSocketAddress();
    }

    @Override
    public String toString() {
        return impl.toString();
    }
}
