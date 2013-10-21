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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Collection;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.adapter.server2x.Adapters;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.opendj.rest2ldap.servlet.Rest2LDAPContextFactory;
import org.opends.messages.Message;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AddressMask;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DisconnectReason;
import org.opends.server.util.Base64;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Servlet {@link Filter} that collects information about client connections.
 */
final class CollectClientConnectionsFilter implements javax.servlet.Filter
{

  /** This class holds all the necessary data to complete an HTTP request. */
  private static final class HTTPRequestContext
  {
    private AsyncContext asyncContext;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    private HTTPClientConnection clientConnection;
    private Connection connection;

    /** Whether to pretty print the resulting JSON. */
    private boolean prettyPrint;
    /** Used for the bind request when credentials are specified. */
    private String userName;
    /**
     * Used for the bind request when credentials are specified. For security
     * reasons, the password must be discarded as soon as possible after it's
     * been used.
     */
    private String password;
  }

  /**
   * This result handler invokes a bind after a successful search on the user
   * name used for authentication.
   */
  private final class DoBindResultHandler implements
      ResultHandler<SearchResultEntry>
  {
    private HTTPRequestContext ctx;

    private DoBindResultHandler(HTTPRequestContext ctx)
    {
      this.ctx = ctx;
    }

    @Override
    public void handleErrorResult(ErrorResultException error)
    {
      final ResultCode rc = error.getResult().getResultCode();
      if (ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED.equals(rc)
          || ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED.equals(rc))
      {
        // Avoid information leak:
        // do not hint to the user that it is the username that is invalid
        sendAuthenticationFailure(ctx);
      }
      else
      {
        onFailure(error, ctx);
      }
    }

    @Override
    public void handleResult(SearchResultEntry resultEntry)
    {
      final DN bindDN = resultEntry.getName();
      if (bindDN == null)
      {
        sendAuthenticationFailure(ctx);
      }
      else
      {
        final BindRequest bindRequest =
            Requests.newSimpleBindRequest(bindDN.toString(), ctx.password
                .getBytes(Charset.forName("UTF-8")));
        // We are done with the password at this stage,
        // wipe it from memory for security reasons
        ctx.password = null;
        ctx.connection.bindAsync(bindRequest, null,
            new CallDoFilterResultHandler(ctx));
      }
    }

  }

  /**
   * This result handler calls {@link javax.servlet.Filter#doFilter()} after a
   * successful bind.
   */
  private final class CallDoFilterResultHandler implements
      ResultHandler<BindResult>
  {

    private final HTTPRequestContext ctx;

    private CallDoFilterResultHandler(HTTPRequestContext ctx)
    {
      this.ctx = ctx;
    }

    @Override
    public void handleErrorResult(ErrorResultException error)
    {
      onFailure(error, ctx);
    }

    @Override
    public void handleResult(BindResult result)
    {
      ctx.clientConnection.setAuthUser(ctx.userName);

      try
      {
        doFilter(ctx);
      }
      catch (Exception e)
      {
        onFailure(e, ctx);
      }
    }

  }

  /** HTTP Header sent by the client with HTTP basic authentication. */
  static final String HTTP_BASIC_AUTH_HEADER = "Authorization";

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /** The connection handler that created this servlet filter. */
  private final HTTPConnectionHandler connectionHandler;
  /**
   * Configures how to perform the search for the username prior to
   * authentication.
   */
  private final HTTPAuthenticationConfig authConfig;

  /**
   * Constructs a new instance of this class.
   *
   * @param connectionHandler
   *          the connection handler that accepted this connection
   * @param authenticationConfig
   *          configures how to perform the search for the username prior to
   *          authentication
   */
  public CollectClientConnectionsFilter(
      HTTPConnectionHandler connectionHandler,
      HTTPAuthenticationConfig authenticationConfig)
  {
    this.connectionHandler = connectionHandler;
    this.authConfig = authenticationConfig;
  }

  /** {@inheritDoc} */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException
  {
    // nothing to do
  }

  /** {@inheritDoc} */
  @Override
  public void doFilter(ServletRequest req, ServletResponse resp,
      FilterChain chain)
  {
    final HttpServletRequest request = (HttpServletRequest) req;
    final HttpServletResponse response = (HttpServletResponse) resp;

    final HTTPRequestContext ctx = new HTTPRequestContext();

    ctx.request = request;
    ctx.response = new HttpServletResponseWrapper(response)
    {

      /** {@inheritDoc} */
      @Override
      public void setStatus(int sc)
      {
        ctx.clientConnection.log(sc);
        super.setStatus(sc);
      }

      /** {@inheritDoc} */
      @SuppressWarnings("deprecation")
      @Override
      public void setStatus(int sc, String sm)
      {
        ctx.clientConnection.log(sc);
        super.setStatus(sc, sm);
      }
    };
    ctx.chain = chain;
    ctx.prettyPrint =
        Boolean.parseBoolean(request.getParameter("_prettyPrint"));

    final HTTPClientConnection clientConnection =
        new HTTPClientConnection(this.connectionHandler, request);
    this.connectionHandler.addClientConnection(clientConnection);

    ctx.clientConnection = clientConnection;

    if (this.connectionHandler.keepStats()) {
      this.connectionHandler.getStatTracker().addRequest(
          ctx.clientConnection.getMethod());
    }

    try
    {
      if (!canProcessRequest(request, clientConnection))
      {
        return;
      }
      // logs the connect after all the possible disconnect reasons have been
      // checked.
      logConnect(clientConnection);

      ctx.connection = new SdkConnectionAdapter(clientConnection);

      final String[] userPassword = extractUsernamePassword(request);
      if (userPassword != null && userPassword.length == 2)
      {
        ctx.userName = userPassword[0];
        ctx.password = userPassword[1];

        ctx.asyncContext = getAsyncContext(request);

        Adapters.newRootConnection().searchSingleEntryAsync(
            buildSearchRequest(ctx.userName), new DoBindResultHandler(ctx));
      }
      else if (this.connectionHandler.acceptUnauthenticatedRequests())
      {
        // use unauthenticated user
        doFilter(ctx);
      }
      else
      {
        sendAuthenticationFailure(ctx);
      }
    }
    catch (Exception e)
    {
      onFailure(e, ctx);
    }
  }

  private void doFilter(HTTPRequestContext ctx)
      throws Exception
  {
    /*
     * WARNING: This action triggers 3-4 others: Set the connection for use with
     * this request on the HttpServletRequest. It will make
     * Rest2LDAPContextFactory create an AuthenticatedConnectionContext which
     * will in turn ensure Rest2LDAP uses the supplied Connection object.
     */
    ctx.request.setAttribute(
        Rest2LDAPContextFactory.ATTRIBUTE_AUTHN_CONNECTION, ctx.connection);

    // send the request further down the filter chain or pass to servlet
    ctx.chain.doFilter(ctx.request, ctx.response);
  }

  private void sendAuthenticationFailure(HTTPRequestContext ctx)
  {
    final int statusCode = HttpServletResponse.SC_UNAUTHORIZED;
    try
    {
      // The user could not be authenticated. Send an HTTP Basic authentication
      // challenge if HTTP Basic authentication is enabled.
      ResourceException unauthorizedException =
          ResourceException.getException(statusCode, "Invalid Credentials");
      sendErrorReponse(ctx.response, ctx.prettyPrint, unauthorizedException);

      ctx.clientConnection.disconnect(DisconnectReason.INVALID_CREDENTIALS,
          false, null);
    }
    finally
    {
      ctx.clientConnection.log(statusCode);

      if (ctx.asyncContext != null)
      {
        ctx.asyncContext.complete();
      }
    }
  }

  private void onFailure(Exception e, HTTPRequestContext ctx)
  {
    ResourceException ex = Rest2LDAP.asResourceException(e);
    try
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      sendErrorReponse(ctx.response, ctx.prettyPrint, ex);

      Message message =
          INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(ctx.clientConnection
              .getClientHostPort(), ctx.clientConnection.getServerHostPort(),
              getExceptionMessage(e));
      logError(message);

      ctx.clientConnection.disconnect(DisconnectReason.SERVER_ERROR, false,
          message);
    }
    finally
    {
      ctx.clientConnection.log(ex.getCode());

      if (ctx.asyncContext != null)
      {
        ctx.asyncContext.complete();
      }
    }
  }

  private boolean canProcessRequest(HttpServletRequest request,
      final HTTPClientConnection clientConnection) throws UnknownHostException
  {
    InetAddress clientAddr = InetAddress.getByName(request.getRemoteAddr());

    // Check to see if the core server rejected the
    // connection (e.g., already too many connections
    // established).
    if (clientConnection.getConnectionID() < 0)
    {
      clientConnection.disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
          ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
      return false;
    }

    // Check to see if the client is on the denied list.
    // If so, then reject it immediately.
    ConnectionHandlerCfg config = this.connectionHandler.getCurrentConfig();
    Collection<AddressMask> allowedClients = config.getAllowedClient();
    Collection<AddressMask> deniedClients = config.getDeniedClient();
    if (!deniedClients.isEmpty()
        && AddressMask.maskListContains(clientAddr, deniedClients))
    {
      clientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED, false,
          ERR_CONNHANDLER_DENIED_CLIENT.get(clientConnection
              .getClientHostPort(), clientConnection.getServerHostPort()));
      return false;
    }
    // Check to see if there is an allowed list and if
    // there is whether the client is on that list. If
    // not, then reject the connection.
    if (!allowedClients.isEmpty()
        && !AddressMask.maskListContains(clientAddr, allowedClients))
    {
      clientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED, false,
          ERR_CONNHANDLER_DISALLOWED_CLIENT.get(clientConnection
              .getClientHostPort(), clientConnection.getServerHostPort()));
      return false;
    }
    return true;
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
  String[] extractUsernamePassword(HttpServletRequest request)
      throws ResourceException
  {
    // TODO Use session to reduce hits with search + bind?
    // Use proxied authorization control for session.

    // Security: How can we remove the password held in the request headers?
    if (authConfig.isCustomHeadersAuthenticationSupported())
    {
      final String userName =
          request.getHeader(authConfig.getCustomHeaderUsername());
      final String password =
          request.getHeader(authConfig.getCustomHeaderPassword());
      if (userName != null && password != null)
      {
        return new String[] { userName, password };
      }
    }

    if (authConfig.isBasicAuthenticationSupported())
    {
      String httpBasicAuthHeader = request.getHeader(HTTP_BASIC_AUTH_HEADER);
      if (httpBasicAuthHeader != null)
      {
        String[] userPassword = parseUsernamePassword(httpBasicAuthHeader);
        if (userPassword != null)
        {
          return userPassword;
        }
      }
    }

    return null;
  }

  /**
   * Sends an error response back to the client. If the error response is
   * "Unauthorized", then it will send a challenge for HTTP Basic authentication
   * if HTTP Basic authentication is enabled.
   *
   * @param response
   *          where to send the Unauthorized status code.
   * @param prettyPrint
   *          whether to format the JSON document output
   * @param re
   *          the resource exception with the error response content
   */
  void sendErrorReponse(HttpServletResponse response, boolean prettyPrint,
      ResourceException re)
  {
    response.setStatus(re.getCode());

    if (re.getCode() == HttpServletResponse.SC_UNAUTHORIZED
        && authConfig.isBasicAuthenticationSupported())
    {
      response.setHeader("WWW-Authenticate",
          "Basic realm=\"org.forgerock.opendj\"");
    }

    try
    {
      // Send error JSON document out
      response.setHeader("Content-Type", "application/json");
      response.getOutputStream().println(toJSON(prettyPrint, re));
    }
    catch (IOException ignore)
    {
      // nothing else we can do in this case
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ignore);
      }
    }
  }

  /**
   * Returns a JSON representation of the {@link ResourceException}.
   *
   * @param prettyPrint
   *          whether to format the resulting JSON document
   * @param re
   *          the resource exception to convert to a JSON document
   * @return a String containing the JSON representation of the
   *         {@link ResourceException}.
   */
  private String toJSON(boolean prettyPrint, ResourceException re)
  {
    final String indent = "\n    ";
    final StringBuilder sb = new StringBuilder();
    sb.append("{");
    if (prettyPrint) sb.append(indent);
    sb.append("\"code\": ").append(re.getCode()).append(",");
    if (prettyPrint) sb.append(indent);
    sb.append("\"message\": \"").append(re.getMessage()).append("\",");
    if (prettyPrint) sb.append(indent);
    sb.append("\"reason\": \"").append(re.getReason()).append("\"");
    if (prettyPrint) sb.append("\n");
    sb.append("}");
    return sb.toString();
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
      String base64UserPassword = authHeader.substring("basic".length() + 1);
      try
      {
        // Example usage of base64:
        // Base64("Aladdin:open sesame") = "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        String userPassword = new String(Base64.decode(base64UserPassword));
        String[] split = userPassword.split(":");
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

  private AsyncContext getAsyncContext(ServletRequest request)
  {
    return request.isAsyncStarted() ? request.getAsyncContext() : request
        .startAsync();
  }

  private SearchRequest buildSearchRequest(String userName)
  {
    // use configured rights to find the user DN
    final Filter filter =
        Filter.format(authConfig.getSearchFilterTemplate(), userName);
    return Requests.newSearchRequest(authConfig.getSearchBaseDN(), authConfig
        .getSearchScope(), filter, SchemaConstants.NO_ATTRIBUTES);
  }

  /** {@inheritDoc} */
  @Override
  public void destroy()
  {
    // nothing to do
  }
}
