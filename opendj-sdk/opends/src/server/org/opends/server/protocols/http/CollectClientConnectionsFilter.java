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

import static org.forgerock.opendj.adapter.server2x.Converters.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Collection;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.ResultCode;
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
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DisconnectReason;
import org.opends.server.util.Base64;

/**
 * Servlet {@link Filter} that collects information about client connections.
 */
final class CollectClientConnectionsFilter implements javax.servlet.Filter
{

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
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain)
  {
    final boolean prettyPrint =
        Boolean.parseBoolean(request.getParameter("_prettyPrint"));

    final HTTPClientConnection clientConnection =
        new HTTPClientConnection(this.connectionHandler, request);
    this.connectionHandler.addClientConnection(clientConnection);
    try
    {
      if (!canProcessRequest(request, clientConnection))
      {
        return;
      }
      // logs the connect after all the possible disconnect reasons have been
      // checked.
      logConnect(clientConnection);

      Connection connection = new SdkConnectionAdapter(clientConnection);

      AuthenticationInfo authInfo = getAuthenticationInfo(request, connection);
      if (authInfo != null)
      {
        clientConnection.setAuthenticationInfo(authInfo);

        /*
         * WARNING: This action triggers 3-4 others: Set the connection for use
         * with this request on the HttpServletRequest. It will make
         * Rest2LDAPContextFactory create an AuthenticatedConnectionContext
         * which will in turn ensure Rest2LDAP uses the supplied Connection
         * object.
         */
        request.setAttribute(
            Rest2LDAPContextFactory.ATTRIBUTE_AUTHN_CONNECTION, connection);

        // send the request further down the filter chain or pass to servlet
        chain.doFilter(request, response);
        return;
      }

      // The user could not be authenticated. Send an HTTP Basic authentication
      // challenge if HTTP Basic authentication is enabled.
      sendErrorReponse(response, prettyPrint, ResourceException.getException(
          HttpServletResponse.SC_UNAUTHORIZED, "Invalid Credentials"));

      clientConnection.disconnect(DisconnectReason.INVALID_CREDENTIALS, false,
          null);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      sendErrorReponse(response, prettyPrint, Rest2LDAP.asResourceException(e));

      Message message =
          INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(clientConnection
              .getClientHostPort(), clientConnection.getServerHostPort(),
              getExceptionMessage(e));
      logError(message);

      clientConnection
          .disconnect(DisconnectReason.SERVER_ERROR, false, message);
    }
  }

  private boolean canProcessRequest(ServletRequest request,
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
   * Returns an {@link AuthenticationInfo} object if the request is accepted. An
   * {@link AuthenticationInfo} object will be returned if authentication
   * credentials were valid or if unauthenticated requests are allowed on this
   * server.
   *
   * @param request
   *          the request used to extract the {@link AuthenticationInfo}
   * @param connection
   *          the connection used to retrieve the {@link AuthenticationInfo}
   * @return an {@link AuthenticationInfo} if the request is accepted, null if
   *         the request was rejected
   * @throws Exception
   *           if any problem occur
   */
  AuthenticationInfo getAuthenticationInfo(ServletRequest request,
      Connection connection) throws Exception
  {
    String[] userPassword = extractUsernamePassword(request);
    if (userPassword != null && userPassword.length == 2)
    {
      return authenticate(userPassword[0], userPassword[1], connection);
    }
    else if (this.connectionHandler.acceptUnauthenticatedRequests())
    {
      // return unauthenticated
      return new AuthenticationInfo();
    }
    return null;
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
  String[] extractUsernamePassword(ServletRequest request)
      throws ResourceException
  {
    HttpServletRequest req = (HttpServletRequest) request;

    // TODO Use session to reduce hits with search + bind?
    // Use proxied authorization control for session.

    if (authConfig.isCustomHeadersAuthenticationSupported())
    {
      final String userName =
          req.getHeader(authConfig.getCustomHeaderUsername());
      final String password =
          req.getHeader(authConfig.getCustomHeaderPassword());
      if (userName != null && password != null)
      {
        return new String[] { userName, password };
      }
    }

    if (authConfig.isBasicAuthenticationSupported())
    {
      String httpBasicAuthHeader = req.getHeader(HTTP_BASIC_AUTH_HEADER);
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
  void sendErrorReponse(ServletResponse response, boolean prettyPrint,
      ResourceException re)
  {
    HttpServletResponse resp = (HttpServletResponse) response;
    resp.setStatus(re.getCode());

    if (re.getCode() == HttpServletResponse.SC_UNAUTHORIZED
        && authConfig.isBasicAuthenticationSupported())
    {
      resp.setHeader("WWW-Authenticate",
          "Basic realm=\"org.forgerock.opendj\"");
    }

    try
    {
      // Send error JSON document out
      resp.setHeader("Content-Type", "application/json");

      ServletOutputStream out = resp.getOutputStream();
      out.println(toJSON(prettyPrint, re));
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

  /**
   * Authenticates the user by doing a search on the user name + bind the
   * returned user entry DN, then return the authentication info if search and
   * bind were successful.
   *
   * @param userName
   *          the user name to authenticate
   * @param password
   *          the password to use with the user
   * @param connection
   *          the connection to use for search and bind
   * @return the {@link AuthenticationInfo} for the supplied credentials, null
   *         if authentication was unsuccessful.
   * @throws ErrorResultException
   *           if a Rest2LDAP result comes back with an error or cancel state
   */
  private AuthenticationInfo authenticate(String userName, String password,
      Connection connection) throws ErrorResultException
  {
    // TODO JNR do the next steps in an async way
    SearchResultEntry resultEntry = searchUniqueEntryDN(userName, connection);
    if (resultEntry != null)
    {
      DN bindDN = resultEntry.getName();
      if (bindDN != null && bind(bindDN.toString(), password, connection))
      {
        return new AuthenticationInfo(to(resultEntry), to(bindDN), ByteString
            .valueOf(password), false);
      }
    }
    return null;
  }

  private SearchResultEntry searchUniqueEntryDN(String userName,
      Connection connection) throws ErrorResultException
  {
    // use configured rights to find the user DN
    final Filter filter =
        Filter.format(authConfig.getSearchFilterTemplate(), userName);
    final SearchRequest searchRequest =
        Requests.newSearchRequest(authConfig.getSearchBaseDN(), authConfig
            .getSearchScope(), filter, SchemaConstants.NO_ATTRIBUTES);
    return connection.searchSingleEntry(searchRequest);
  }

  private boolean bind(String bindDN, String password, Connection connection)
      throws ErrorResultException
  {
    final BindRequest bindRequest =
        Requests.newSimpleBindRequest(bindDN, password.getBytes());
    final BindResult bindResult = connection.bind(bindRequest);
    return ResultCode.SUCCESS.equals(bindResult.getResultCode());
  }

  /** {@inheritDoc} */
  @Override
  public void destroy()
  {
    // nothing to do
  }
}
