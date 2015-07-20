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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
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
