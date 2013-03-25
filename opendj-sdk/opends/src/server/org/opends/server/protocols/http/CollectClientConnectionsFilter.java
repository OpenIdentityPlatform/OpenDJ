/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.forgerock.opendj.adapter.server2x.Adapters;
import org.forgerock.opendj.rest2ldap.servlet.Rest2LDAPContextFactory;
import org.opends.messages.Message;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AddressMask;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DisconnectReason;

/**
 * Servlet {@link Filter} that collects information about client connections.
 */
final class CollectClientConnectionsFilter implements Filter
{

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /** The connection handler that created this servlet filter. */
  private final HTTPConnectionHandler connectionHandler;

  /**
   * Constructs a new instance of this class.
   *
   * @param connectionHandler
   *          the connection handler that accepted this connection
   */
  public CollectClientConnectionsFilter(HTTPConnectionHandler connectionHandler)
  {
    this.connectionHandler = connectionHandler;
  }

  /** {@inheritDoc} */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException
  {
    // nothing to do
  }

  /** {@inheritDoc} */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain)
  {
    final Map<ClientConnection, ClientConnection> clientConnections =
        this.connectionHandler.getClientConnectionsMap();
    final ClientConnection clientConnection =
        new HTTPClientConnection(this.connectionHandler, request);
    clientConnections.put(clientConnection, clientConnection);
    try
    {
      String ipAddress = request.getRemoteAddr();
      InetAddress clientAddr = InetAddress.getByName(ipAddress);

      // Check to see if the core server rejected the
      // connection (e.g., already too many connections
      // established).
      if (clientConnection.getConnectionID() < 0)
      {
        // The connection will have already been closed.
        return;
      }

      // Check to see if the client is on the denied list.
      // If so, then reject it immediately.
      ConnectionHandlerCfg config = this.connectionHandler.getCurrentConfig();
      Collection<AddressMask> allowedClients = config.getAllowedClient();
      Collection<AddressMask> deniedClients = config.getDeniedClient();
      if (!deniedClients.isEmpty()
          && AddressMask.maskListContains(clientAddr, deniedClients))
      {
        clientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED,
            false, ERR_CONNHANDLER_DENIED_CLIENT.get(clientConnection
                .getClientHostPort(), clientConnection.getServerHostPort()));
        return;
      }
      // Check to see if there is an allowed list and if
      // there is whether the client is on that list. If
      // not, then reject the connection.
      if (!allowedClients.isEmpty()
          && !AddressMask.maskListContains(clientAddr, allowedClients))
      {
        clientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED,
            false, ERR_CONNHANDLER_DISALLOWED_CLIENT.get(clientConnection
                .getClientHostPort(), clientConnection.getServerHostPort()));
        return;
      }

      // TODO JNR handle authentication + send the HTTPClientConnection
      // to Rest2LDAP
      Object result = Adapters.newRootConnection();

      // WARNING: This action triggers 3-4 others:
      // Set the connection for use with this request on the HttpServletRequest.
      // It will make Rest2LDAPContextFactory create an
      // AuthenticatedConnectionContext which will in turn ensure Rest2LDAP uses
      // the supplied Connection object
      request.setAttribute(Rest2LDAPContextFactory.ATTRIBUTE_AUTHN_CONNECTION,
          result);

      // send the request further down the filter chain or pass to servlet
      chain.doFilter(request, response);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(clientConnection
              .getClientHostPort(), clientConnection.getServerHostPort(),
              getExceptionMessage(e));
      logError(message);

      clientConnection
          .disconnect(DisconnectReason.SERVER_ERROR, false, message);
    }
    finally
    {
      clientConnections.remove(clientConnection);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void destroy()
  {
    // nothing to do
  }
}
