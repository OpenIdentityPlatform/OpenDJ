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
 *      Copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.promise.Promise;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.util.promise.Promises.*;
import static org.mockito.Mockito.*;

/**
 * Basic LDAP connection factory implementation to use for tests only.
 */
public final class BasicLDAPConnectionFactory implements LDAPConnectionFactoryImpl {

    private final LDAPOptions options;
    private final String host;
    private final int port;

    /**
     * Creates a new LDAP connection factory which does nothing.
     *
     * @param host
     *            The address of the Directory Server to connect to.
     * @param port
     *            The port of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     */
    public BasicLDAPConnectionFactory(final String host, final int port, final LDAPOptions options) {
        this.host = host;
        this.port = port;
        this.options = new LDAPOptions(options);
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public Connection getConnection() throws LdapException {
        try {
            return getConnectionAsync().getOrThrow();
        } catch (final InterruptedException e) {
            throw newLdapException(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
        }
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        return newResultPromise(mock(Connection.class));
    }

    /**
     * Returns the address of the Directory Server.
     *
     * @return The address of the Directory Server.
     */
    @Override
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public String getHostName() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + host + ':' + port + ')';
    }

    LDAPOptions getLDAPOptions() {
        return options;
    }
}
