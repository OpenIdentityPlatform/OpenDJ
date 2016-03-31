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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.adapter.server3x.Adapters;
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
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.util.Base64;

/** Servlet {@link Filter} that collects information about client connections. */
public final class AuthenticationFilter implements org.forgerock.http.Filter, Closeable
{

  /** HTTP Header sent by the client with HTTP basic authentication. */
  static final String HTTP_BASIC_AUTH_HEADER = "Authorization";

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Configures how to perform the search for the username prior to
   * authentication.
   */
  private final HTTPAuthenticationConfig authConfig;

  private final boolean authenticationRequired;

  /**
   * Constructs a new instance of this class.
   *
   * @param authenticationConfig
   *          configures how to perform the search for the username prior to
   *          authentication
   * @param authenticationRequired
   *          If true, only authenticated requests will be accepted.
   */
  public AuthenticationFilter(HTTPAuthenticationConfig authenticationConfig, boolean authenticationRequired)
  {
    this.authConfig = authenticationConfig;
    this.authenticationRequired = authenticationRequired;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next)
  {
    try
    {
      final Connection ldapConnection = context.asContext(LDAPContext.class).getLdapConnectionFactory().getConnection();
      final String[] userCredentials = extractUsernamePassword(request);
      if (userCredentials != null && userCredentials.length == 2)
      {
        final String userName = userCredentials[0];
        final String password = userCredentials[1];
        return Adapters.newRootConnection()
                       .searchSingleEntryAsync(buildSearchRequest(userName))
                       .thenAsync(doBindAfterSearch(context, request, next, userName, password, ldapConnection),
                                  returnErrorAfterFailedSearch(context.asContext(ClientContext.class)));
      }
      else if (authenticationRequired)
      {
        return authenticationFailure(context.asContext(ClientContext.class));
      }
      else
      {
        // Use unauthenticated user
        return doFilter(context, request, next, ldapConnection);
      }
    }
    catch (Exception e)
    {
      return asErrorResponse(e, context.asContext(ClientContext.class));
    }
  }

  private SearchRequest buildSearchRequest(String userName)
  {
    // Use configured rights to find the user DN
    final Filter filter = Filter.format(authConfig.getSearchFilterTemplate(), userName);
    return Requests.newSearchRequest(
        authConfig.getSearchBaseDN(), authConfig.getSearchScope(), filter, SchemaConstants.NO_ATTRIBUTES);
  }

  private AsyncFunction<SearchResultEntry, Response, NeverThrowsException> doBindAfterSearch(final Context context,
      final Request request, final Handler next, final String userName, final String password,
      final Connection connection)
  {
    final ClientContext clientContext = context.asContext(ClientContext.class);
    return new AsyncFunction<SearchResultEntry, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(final SearchResultEntry resultEntry)
      {
        final DN bindDN = resultEntry.getName();

        if (bindDN == null)
        {
          return authenticationFailure(clientContext);
        }

        final BindRequest bindRequest =
            Requests.newSimpleBindRequest(bindDN.toString(), password.getBytes(Charset.forName("UTF-8")));
        return connection.bindAsync(bindRequest)
                         .thenAsync(doChain(context, request, next, userName, connection),
                                    returnErrorAfterFailedBind(clientContext));
      }
    };
  }

  private AsyncFunction<BindResult, Response, NeverThrowsException> doChain(final Context context,
      final Request request, final Handler next, final String userName, final Connection connection)
  {
    return new AsyncFunction<BindResult, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(BindResult value) throws NeverThrowsException
      {
        try
        {
          SecurityContext securityContext = new SecurityContext(context, userName, null);
          return doFilter(securityContext, request, next, connection);
        }
        catch (Exception e)
        {
          return asErrorResponse(e, context.asContext(ClientContext.class));
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
      final ClientContext clientContext)
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
          return authenticationFailure(clientContext);
        }
        else
        {
          return asErrorResponse(exception, clientContext);
        }
      }
    };
  }

  private AsyncFunction<LdapException, Response, NeverThrowsException> returnErrorAfterFailedBind(
      final ClientContext clientContext)
  {
    return new AsyncFunction<LdapException, Response, NeverThrowsException>()
    {
      @Override
      public Promise<Response, NeverThrowsException> apply(final LdapException e)
      {
        return asErrorResponse(e, clientContext);
      }
    };
  }

  private Promise<Response, NeverThrowsException> authenticationFailure(final ClientContext clientContext)
  {
    return asErrorResponse(ResourceException.getException(401, "Invalid Credentials"), clientContext, false);
  }

  private Promise<Response, NeverThrowsException> asErrorResponse(final Throwable t, final ClientContext clientContext)
  {
    return asErrorResponse(t, clientContext, true);
  }

  private Promise<Response, NeverThrowsException> asErrorResponse(final Throwable t, final ClientContext clientContext,
      final boolean logError)
  {
    final ResourceException ex = Rest2LDAP.asResourceException(t);
    LocalizableMessage message = null;
    if (logError)
    {
      logger.traceException(ex);
      message =
          INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(clientContext.getRemotePort(), clientContext.getLocalPort(),
              getExceptionMessage(ex));
      logger.debug(message);
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
