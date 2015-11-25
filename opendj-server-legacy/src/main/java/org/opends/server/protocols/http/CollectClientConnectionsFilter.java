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
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.protocols.http;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Collection;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.adapter.server3x.Adapters;
import org.forgerock.opendj.ldap.AddressMask;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.DisconnectReason;
import org.opends.server.util.Base64;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.util.StaticUtils.*;

/** Servlet {@link Filter} that collects information about client connections. */
final class CollectClientConnectionsFilter implements org.forgerock.http.Filter, Closeable
{

  /** HTTP Header sent by the client with HTTP basic authentication. */
  static final String HTTP_BASIC_AUTH_HEADER = "Authorization";

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The connection handler that created this servlet filter. */
  private final HTTPConnectionHandler connectionHandler;
  /**
   * Configures how to perform the search for the username prior to
   * authentication.
   */
  private final HTTPAuthenticationConfig authConfig;

  private final ServerContext serverContext;

  /**
   * Constructs a new instance of this class.
   * @param serverContext
   *            The server context.
   * @param connectionHandler
   *          the connection handler that accepted this connection
   * @param authenticationConfig
   *          configures how to perform the search for the username prior to
   *          authentication
   */
  public CollectClientConnectionsFilter(ServerContext serverContext, HTTPConnectionHandler connectionHandler,
      HTTPAuthenticationConfig authenticationConfig)
  {
    this.serverContext = serverContext;
    this.connectionHandler = connectionHandler;
    this.authConfig = authenticationConfig;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next)
  {
    final HTTPClientConnection clientConnection =
        new HTTPClientConnection(serverContext, this.connectionHandler, context, request);
    connectionHandler.addClientConnection(clientConnection);

    if (connectionHandler.keepStats())
    {
      connectionHandler.getStatTracker().addRequest(request.getMethod());
    }

    try
    {
      if (!canProcessRequest(clientConnection))
      {
        return resourceExceptionToPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR));
      }
      // Logs the connect after all the possible disconnect reasons have been checked.
      logConnect(clientConnection);
      final Connection connection = new SdkConnectionAdapter(clientConnection);

      final String[] userCredentials = extractUsernamePassword(request);
      if (userCredentials != null && userCredentials.length == 2)
      {
        final String userName = userCredentials[0];
        final String password = userCredentials[1];

        return Adapters.newRootConnection()
            .searchSingleEntryAsync(buildSearchRequest(userName))
            .thenAsync(doBindAfterSearch(context, request, next, userName, password, clientConnection, connection),
                       returnErrorAfterFailedSearch(clientConnection));
      }
      else if (this.connectionHandler.acceptUnauthenticatedRequests())
      {
        // Use unauthenticated user
        return doFilter(context, request, next, connection);
      }
      else
      {
        return authenticationFailure(clientConnection);
      }
    }
    catch (Exception e)
    {
      return asErrorResponse(e, clientConnection);
    }
  }

  private boolean canProcessRequest(final HTTPClientConnection connection) throws UnknownHostException
  {
    final InetAddress clientAddr = connection.getRemoteAddress();

    // Check to see if the core server rejected the connection (e.g. already too many connections established).
    if (connection.getConnectionID() < 0)
    {
      connection.disconnect(
          DisconnectReason.ADMIN_LIMIT_EXCEEDED, true, ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
      return false;
    }

    // Check to see if the client is on the denied list. If so, then reject it immediately.
    final ConnectionHandlerCfg config = this.connectionHandler.getCurrentConfig();
    final Collection<AddressMask> deniedClients = config.getDeniedClient();
    if (!deniedClients.isEmpty()
        && AddressMask.matchesAny(deniedClients, clientAddr))
    {
      connection.disconnect(DisconnectReason.CONNECTION_REJECTED, false,
          ERR_CONNHANDLER_DENIED_CLIENT.get(connection.getClientHostPort(), connection.getServerHostPort()));
      return false;
    }

    // Check to see if there is an allowed list and if there is whether the client is on that list.
    // If not, then reject the connection.
    final Collection<AddressMask> allowedClients = config.getAllowedClient();
    if (!allowedClients.isEmpty()
        && !AddressMask.matchesAny(allowedClients, clientAddr))
    {
      connection.disconnect(DisconnectReason.CONNECTION_REJECTED, false,
          ERR_CONNHANDLER_DISALLOWED_CLIENT.get(connection.getClientHostPort(), connection.getServerHostPort()));
      return false;
    }
    return true;
  }

  private SearchRequest buildSearchRequest(String userName)
  {
    // Use configured rights to find the user DN
    final Filter filter = Filter.format(authConfig.getSearchFilterTemplate(), userName);
    return Requests.newSearchRequest(
        authConfig.getSearchBaseDN(), authConfig.getSearchScope(), filter, SchemaConstants.NO_ATTRIBUTES);
  }

  private AsyncFunction<SearchResultEntry, Response, NeverThrowsException> doBindAfterSearch(
      final Context context, final Request request, final Handler next, final String userName, final String password,
      final HTTPClientConnection clientConnection, final Connection connection)
  {
    return new AsyncFunction<SearchResultEntry, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(final SearchResultEntry resultEntry)
      {
        final DN bindDN = resultEntry.getName();
        if (bindDN == null)
        {
          return authenticationFailure(clientConnection);
        }

        final BindRequest bindRequest =
            Requests.newSimpleBindRequest(bindDN.toString(), password.getBytes(Charset.forName("UTF-8")));
        return connection.bindAsync(bindRequest)
                         .thenAsync(doChain(context, request, next, userName, clientConnection, connection),
                                    returnErrorAfterFailedBind(clientConnection));
      }
    };
  }

  private AsyncFunction<BindResult, Response, NeverThrowsException> doChain(
      final Context context, final Request request, final Handler next, final String userName,
      final HTTPClientConnection clientConnection, final Connection connection)
  {
    return new AsyncFunction<BindResult, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(BindResult value) throws NeverThrowsException
      {
        clientConnection.setAuthUser(userName);
        try
        {
          SecurityContext securityContext = new SecurityContext(context, userName, null);
          return doFilter(securityContext, request, next, connection);
        }
        catch (Exception e)
        {
          return asErrorResponse(e, clientConnection);
        }
      }
    };
  }

  private Promise<Response, NeverThrowsException> doFilter(
      final Context context, final Request request, final Handler next, final Connection connection) throws Exception
  {
    final Context forwardedContext = new AuthenticatedConnectionContext(context, connection);
    // Send the request further down the filter chain or pass to servlet
    return next.handle(forwardedContext, request);
  }

  private AsyncFunction<? super LdapException, Response, NeverThrowsException> returnErrorAfterFailedSearch(
      final HTTPClientConnection clientConnection)
  {
    return new AsyncFunction<LdapException, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(final LdapException exception)
      {
        final ResultCode rc = exception.getResult().getResultCode();
        if (ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED.equals(rc)
         || ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED.equals(rc))
        {
          // Avoid information leak:
          // do not hint to the user that it is the username that is invalid
          return authenticationFailure(clientConnection);
        }
        else
        {
          return asErrorResponse(exception, clientConnection);
        }
      }
    };
  }

  private AsyncFunction<LdapException, Response, NeverThrowsException> returnErrorAfterFailedBind(
      final HTTPClientConnection clientConnection)
  {
    return new AsyncFunction<LdapException, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(final LdapException e)
      {
        return asErrorResponse(e, clientConnection);
      }
    };
  }

  private Promise<Response, NeverThrowsException> authenticationFailure(final HTTPClientConnection clientConnection)
  {
    return asErrorResponse(ResourceException.getException(401, "Invalid Credentials"), clientConnection,
        DisconnectReason.INVALID_CREDENTIALS, false);
  }

  private Promise<Response, NeverThrowsException> asErrorResponse(
      final Throwable t, final HTTPClientConnection clientConnection)
  {
    return asErrorResponse(t, clientConnection, DisconnectReason.SERVER_ERROR, true);
  }

  private Promise<Response, NeverThrowsException> asErrorResponse(final Throwable t,
      final HTTPClientConnection clientConnection, final DisconnectReason reason, final boolean logError)
  {
    final ResourceException ex = Rest2LDAP.asResourceException(t);
    try
    {
      LocalizableMessage message = null;
      if (logError)
      {
        logger.traceException(ex);
        message = INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(
            clientConnection.getClientHostPort(), clientConnection.getServerHostPort(), getExceptionMessage(ex));
        logger.debug(message);
      }
      clientConnection.disconnect(reason, false, message);
    }
    finally
    {
      clientConnection.log(ex.getCode());
    }

    return resourceExceptionToPromise(ex);
  }

  Promise<Response, NeverThrowsException> resourceExceptionToPromise(final ResourceException e)
  {
    final Response response = new Response().setStatus(Status.valueOf(e.getCode()))
                                            .setEntity(e.toJsonValue().getObject());
    if (e.getCode() == 401 && authConfig.isBasicAuthenticationSupported())
    {
      response.getHeaders().add("WWW-Authenticate", "Basic realm=\"org.forgerock.opendj\"");
    }
    return Promises.newResultPromise(response);
  }

  /**
   * Extracts the username and password from the request using one of the
   * enabled authentication mechanism: HTTP Basic authentication or HTTP Custom
   * headers. If no username and password can be obtained, then send back an
   * HTTP basic authentication challenge if HTTP basic authentication is
   * enabled.
   *
   * @param request
   *          the request where to extract the username and password from
   * @return the array containing the username/password couple if both exist,
   *         null otherwise
   * @throws ResourceException
   *           if any error occur
   */
  String[] extractUsernamePassword(Request request) throws ResourceException
  {
    // TODO Use session to reduce hits with search + bind?
    // Use proxied authorization control for session.

    // Security: How can we remove the password held in the request headers?
    if (authConfig.isCustomHeadersAuthenticationSupported())
    {
      final String userName = request.getHeaders().getFirst(authConfig.getCustomHeaderUsername());
      final String password = request.getHeaders().getFirst(authConfig.getCustomHeaderPassword());
      if (userName != null && password != null)
      {
        return new String[] { userName, password };
      }
    }

    if (authConfig.isBasicAuthenticationSupported())
    {
      String httpBasicAuthHeader = request.getHeaders().getFirst(HTTP_BASIC_AUTH_HEADER);
      if (httpBasicAuthHeader != null)
      {
        String[] userCredentials = parseUsernamePassword(httpBasicAuthHeader);
        if (userCredentials != null)
        {
          return userCredentials;
        }
      }
    }

    return null;
  }

  /**
   * Parses username and password from the authentication header used in HTTP
   * basic authentication.
   *
   * @param authHeader
   *          the authentication header obtained from the request
   * @return an array containing the username at index 0 and the password at
   *         index 1, or null if the header cannot be parsed successfully
   * @throws ResourceException
   *           if the base64 password cannot be decoded
   */
  String[] parseUsernamePassword(String authHeader) throws ResourceException
  {
    if (authHeader != null
        && (authHeader.startsWith("Basic") || authHeader.startsWith("basic")))
    {
      // We received authentication info
      // Example received header:
      // "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
      String base64UserCredentials = authHeader.substring("basic".length() + 1);
      try
      {
        // Example usage of base64:
        // Base64("Aladdin:open sesame") = "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        String userCredentials = new String(Base64.decode(base64UserCredentials));
        String[] split = userCredentials.split(":");
        if (split.length == 2)
        {
          return split;
        }
      }
      catch (ParseException e)
      {
        throw Rest2LDAP.asResourceException(e);
      }
    }
    return null;
  }

  @Override
  public void close() throws IOException {}
}
