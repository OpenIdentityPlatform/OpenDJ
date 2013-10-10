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

import static org.forgerock.opendj.ldap.ErrorResultException.*;

import java.net.SocketAddress;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;

import com.forgerock.opendj.util.AsynchronousFutureResult;

/**
 * Basic LDAP connection factory implementation to use for tests only.
 */
public final class BasicLDAPConnectionFactory implements LDAPConnectionFactoryImpl {

    private final LDAPOptions options;
    private final SocketAddress socketAddress;

    /**
     * Creates a new LDAP connection factory which does nothing.
     *
     * @param address
     *            The address of the Directory Server to connect to.
     * @param options
     *            The LDAP connection options to use when creating connections.
     */
    public BasicLDAPConnectionFactory(final SocketAddress address, final LDAPOptions options) {
        this.socketAddress = address;
        this.options = new LDAPOptions(options);
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public Connection getConnection() throws ErrorResultException {
        try {
            return getConnectionAsync(null).get();
        } catch (final InterruptedException e) {
            throw newErrorResult(ResultCode.CLIENT_SIDE_USER_CANCELLED, e);
        }
    }

    @Override
    public FutureResult<Connection> getConnectionAsync(
            final ResultHandler<? super Connection> handler) {
        final AsynchronousFutureResult<Connection, ResultHandler<? super Connection>> future =
                new AsynchronousFutureResult<Connection, ResultHandler<? super Connection>>(handler);
        future.handleResult(org.mockito.Mockito.mock(Connection.class));
        return future;
    }

    /**
     * Returns the address of the Directory Server.
     *
     * @return The address of the Directory Server.
     */
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("LDAPConnectionFactory(");
        builder.append(getSocketAddress().toString());
        builder.append(')');
        return builder.toString();
    }

    LDAPOptions getLDAPOptions() {
        return options;
    }
}
