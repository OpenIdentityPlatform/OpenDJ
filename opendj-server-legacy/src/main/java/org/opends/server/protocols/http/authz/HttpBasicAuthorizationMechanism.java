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
package org.opends.server.protocols.http.authz;

import static org.forgerock.http.filter.Filters.chainOf;
import static org.forgerock.opendj.adapter.server3x.Adapters.newRootConnectionFactory;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.ResultCode.INVALID_CREDENTIALS;
import static org.forgerock.opendj.ldap.ResultCode.OPERATIONS_ERROR;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newConditionalHttpBasicAuthenticationFilter;
import static org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.newConditionalFilter;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.httpBasicExtractor;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.newCustomHeaderExtractor;
import static org.forgerock.services.context.SecurityContext.AUTHZID_DN;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.opends.server.core.DirectoryServer.getIdentityMapper;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategy;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.opendj.server.config.server.HTTPBasicAuthorizationMechanismCfg;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.api.IdentityMapper;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.http.HttpLogContext;
import org.opends.server.protocols.http.LDAPContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

/**
 * Implements the HTTP Basic authorization by first resolving the user's identity with an {@link IdentityMapper} and
 * then by performing a simple {@link BindRequest}. On success, an {@link AuthenticatedConnectionContext} is created.
 */
final class HttpBasicAuthorizationMechanism extends HttpAuthorizationMechanism<HTTPBasicAuthorizationMechanismCfg>
{
  private static final CloseConnectionFilter CLOSE_CONNECTION = new CloseConnectionFilter();
  private static final int HTTP_BASIC_PRIORITY = 500;

  private final ConditionalFilter delegate;

  HttpBasicAuthorizationMechanism(HTTPBasicAuthorizationMechanismCfg config, ServerContext serverContext)
  {
    super(config.dn(), HTTP_BASIC_PRIORITY);
    final ConditionalFilter httpBasicFilter = newConditionalHttpBasicAuthenticationFilter(
        new IdentityMapperAuthenticationStrategy(newRootConnectionFactory(),
                                                 getIdentityMapper(config.getIdentityMapperDN())),
        config.isAltAuthenticationEnabled()
                  ? newCustomHeaderExtractor(config.getAltUsernameHeader(), config.getAltPasswordHeader())
                  : httpBasicExtractor());
    this.delegate =
        newConditionalFilter(chainOf(httpBasicFilter.getFilter(), CLOSE_CONNECTION), httpBasicFilter.getCondition());
  }

  @Override
  ConditionalFilter getDelegate()
  {
    return delegate;
  }

  /** Close the {@link Connection} present in the {@link AuthenticatedConnectionContext}. */
  private static final class CloseConnectionFilter implements Filter
  {
    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context, Request request, Handler next)
    {
      return next.handle(context, request).thenAlways(new Runnable()
      {
        @Override
        public void run()
        {
          closeSilently(context.asContext(AuthenticatedConnectionContext.class).getConnection());
        }
      });
    }
  }

  /**
   * Performs a simple {@link BindRequest} using the user's DN resolved by an {@link IdentityMapper}. For optimization
   * purpose, returns an {@link AuthenticatedConnectionContext} encapsulated in the {@link SecurityContext} By doing so
   * we're removing the redundant lookup which should otherwise have happen in the {@link InternalProxyAuthzFilter}.
   */
  private static final class IdentityMapperAuthenticationStrategy implements AuthenticationStrategy
  {
    private final ConnectionFactory rootConnectionFactory;
    private final IdentityMapper<?> identityMapper;

    IdentityMapperAuthenticationStrategy(ConnectionFactory rootConnectionFactory, IdentityMapper<?> identityMapper)
    {
      this.rootConnectionFactory = checkNotNull(rootConnectionFactory, "rootConnectionFactory cannot be null");
      this.identityMapper = checkNotNull(identityMapper, "identityMapper cannot be null");
    }

    @Override
    public Promise<SecurityContext, LdapException> authenticate(String username, String password, Context parentContext)
    {
      parentContext.asContext(HttpLogContext.class).setAuthUser(username);
      try
      {
        final Entry userEntry = getMappedIdentity(username);
        doBind(userEntry.getName().toString(), password);
        final Connection connection = parentContext.asContext(LDAPContext.class)
                                                   .getInternalConnectionFactory()
                                                   .getAuthenticatedConnection(userEntry);
        final Context authcContext = new AuthenticatedConnectionContext(parentContext, connection);
        final Map<String, Object> authz = new HashMap<>();
        authz.put(AUTHZID_DN, userEntry.getName().toString());

        return newResultPromise(new SecurityContext(authcContext, username, authz));
      }
      catch (LdapException e)
      {
        return newExceptionPromise(e);
      }
    }

    private Entry getMappedIdentity(String authzId) throws LdapException
    {
      final Entry userEntry;
      try
      {
        userEntry = identityMapper.getEntryForID(authzId);
        if (userEntry != null)
        {
          return userEntry;
        }
      }
      catch (DirectoryException e)
      {
        throw newLdapException(OPERATIONS_ERROR, e);
      }
      throw newLdapException(INVALID_CREDENTIALS);
    }

    private void doBind(String name, String password) throws LdapException
    {
      try (final Connection connection = rootConnectionFactory.getConnection())
      {
        final BindResult result = connection.bind(newSimpleBindRequest(name, password.toCharArray()));
        if (!result.isSuccess())
        {
          throw newLdapException(INVALID_CREDENTIALS);
        }
      }
    }
  }
}
