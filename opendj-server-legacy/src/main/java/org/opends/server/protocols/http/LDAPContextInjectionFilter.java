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

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.http.LDAPContext.InternalConnectionFactory;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Entry;

/**
 * Filter injecting the {@link LDAPContext} giving access to
 * {@link LDAPConnectionFactory} to the underlying {@link HttpApplication}.
 */
final class LDAPContextInjectionFilter implements Filter
{
  private final ServerContext serverContext;
  private final HTTPConnectionHandler httpConnectionHandler;

  LDAPContextInjectionFilter(ServerContext serverContext, HTTPConnectionHandler httpConnectionHandler)
  {
    this.serverContext = serverContext;
    this.httpConnectionHandler = httpConnectionHandler;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
      final Handler next)
  {
    final LDAPContext djContext = new LDAPContext(context, new InternalConnectionFactory()
    {
      @Override
      public Connection getAuthenticatedConnection(Entry userEntry) throws LdapException
      {
        final HTTPClientConnection clientConnection =
            new HTTPClientConnection(serverContext, httpConnectionHandler, context, request);
        clientConnection.setAuthenticationInfo(getAuthInfoForUserEntry(userEntry));
        if (clientConnection.getConnectionID() < 0)
        {
          throw LdapException.newLdapException(ResultCode.ADMIN_LIMIT_EXCEEDED);
        }
        httpConnectionHandler.addClientConnection(clientConnection);
        return new SdkConnectionAdapter(clientConnection);
      }

      private AuthenticationInfo getAuthInfoForUserEntry(Entry userEntry)
      {
        return new AuthenticationInfo(userEntry, DirectoryServer.isRootDN(userEntry.getName()));
      }
    });
    return next.handle(djContext, request);
  }
}
