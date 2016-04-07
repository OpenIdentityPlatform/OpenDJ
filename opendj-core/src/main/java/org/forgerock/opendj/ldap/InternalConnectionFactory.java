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

package org.forgerock.opendj.ldap;

import org.forgerock.util.promise.Promise;

import static org.forgerock.opendj.ldap.spi.LdapPromises.*;
import static org.forgerock.util.promise.Promises.*;


/**
 * A special {@code ConnectionFactory} which waits for internal connection
 * requests and binds them to a {@link ServerConnection} created using the
 * provided {@link ServerConnectionFactory}.
 * <p>
 * When processing requests, {@code ServerConnection} implementations are passed
 * an integer as the first parameter. This integer represents a pseudo
 * {@code requestID} which is incremented for each successive internal request
 * on a per connection basis. The request ID may be useful for logging purposes.
 * <p>
 * An {@code InternalConnectionFactory} does not require
 * {@code ServerConnection} implementations to return a result when processing
 * requests. However, it is recommended that implementations do always return
 * results even for abandoned requests. This is because application client
 * threads may block indefinitely waiting for results.
 *
 * @param <C>
 *            The type of client context.
 */
final class InternalConnectionFactory<C> implements ConnectionFactory {
    private final ServerConnectionFactory<C, Integer> factory;
    private final C clientContext;

    InternalConnectionFactory(final ServerConnectionFactory<C, Integer> factory,
            final C clientContext) {
        this.factory = factory;
        this.clientContext = clientContext;
    }

    @Override
    public void close() {
        // Nothing to do.
    }

    @Override
    public Connection getConnection() throws LdapException {
        final ServerConnection<Integer> serverConnection = factory.handleAccept(clientContext);
        return new InternalConnection(serverConnection);
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        final ServerConnection<Integer> serverConnection;
        try {
            serverConnection = factory.handleAccept(clientContext);
        } catch (final LdapException e) {
            return newFailedLdapPromise(e);
        }

        return newResultPromise((Connection) new InternalConnection(serverConnection));
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("InternalConnectionFactory(");
        builder.append(clientContext);
        builder.append(',');
        builder.append(factory);
        builder.append(')');
        return builder.toString();
    }
}
