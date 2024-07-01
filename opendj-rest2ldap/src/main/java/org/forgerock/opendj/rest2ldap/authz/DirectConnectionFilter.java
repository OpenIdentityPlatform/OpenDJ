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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.opendj.rest2ldap.authz.Utils.*;
import static org.forgerock.util.Reject.*;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Inject {@link Connection} into a {@link AuthenticatedConnectionContext}.
 */
final class DirectConnectionFilter implements Filter {

    private final ConnectionFactory connectionFactory;

    /**
     * Create a new {@link DirectConnectionFilter}.
     *
     * @param connectionFactory
     *            The factory used to get the {@link Connection}
     * @throws NullPointerException
     *             if connectionFactory is null.
     */
    public DirectConnectionFilter(ConnectionFactory connectionFactory) {
        this.connectionFactory = checkNotNull(connectionFactory, "connectionFactory cannot be null");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
            final Handler next) {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<Connection>();
        return connectionFactory
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(Connection connection) {
                        connectionHolder.set(connection);
                        return next.handle(new AuthenticatedConnectionContext(context, connection), request);
                    }
                }, handleConnectionFailure())
                .thenFinally(close(connectionHolder));
    }
}
