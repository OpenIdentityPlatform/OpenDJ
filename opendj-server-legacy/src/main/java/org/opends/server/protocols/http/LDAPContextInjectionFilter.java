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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import static org.opends.messages.ProtocolMessages.*;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DisconnectReason;

/**
 * Filter injecting the {@link LDAPContext} giving access to
 * {@link LDAPConnectionFactory} to the underlying {@link HttpApplication}.
 */
final class LDAPContextInjectionFilter implements Filter
{
  private final ServerContext serverContext;
  private final HTTPConnectionHandler httpConnectionHandler;

  LDAPContextInjectionFilter(ServerContext serverContext, HTTPConnectionHandler httpConnectionHandler) {
    this.serverContext = serverContext;
    this.httpConnectionHandler= httpConnectionHandler;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next)
  {
    final HTTPClientConnection clientConnection =
        new HTTPClientConnection(serverContext, httpConnectionHandler, context, request);
    if (clientConnection.getConnectionID() < 0)
    {
      clientConnection.disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
          ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
      return Promises.newResultPromise(new Response(Status.SERVICE_UNAVAILABLE));
    }

    final LDAPContext djContext  = new LDAPContext(context, new ConnectionFactory()
    {
      private final Connection connection = new SdkConnectionAdapter(clientConnection);

      @Override
      public Promise<Connection, LdapException> getConnectionAsync()
      {
        return Promises.newResultPromise(connection);
      }

      @Override
      public Connection getConnection() throws LdapException
      {
        return connection;
      }

      @Override
      public void close()
      {
      }
    });
    return next.handle(djContext, request);
  }

}