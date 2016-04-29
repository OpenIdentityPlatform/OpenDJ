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
package org.opends.server.protocols.http.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.Utils.closeSilently;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.opends.server.api.IdentityMapper;
import org.opends.server.protocols.http.LDAPContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

/**
 * Authorization proxy using internal connection's capabilities as an optimized
 * alternative to {@link ProxiedAuthV2Control}. This proxy creates an
 * {@link AuthenticatedConnectionContext} for the current request using its
 * {@link SecurityContext}.
 */
final class InternalProxyAuthzFilter implements Filter
{
  private final IdentityMapper<?> identityMapper;
  private final Schema schema;
  private final Function<SecurityContext, String, LdapException> authzIdProvider;

  InternalProxyAuthzFilter(IdentityMapper<?> identityMapper, Schema schema,
      Function<SecurityContext, String, LdapException> authzIdProvider)
  {
    this.identityMapper = checkNotNull(identityMapper, "identityMapper cannot be null");
    this.schema = checkNotNull(schema, "schema cannot be null");
    this.authzIdProvider = checkNotNull(authzIdProvider, "authzIdProvider cannot be null");
  }

  @Override
  public final Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next)
  {
    final SecurityContext securityContext = context.asContext(SecurityContext.class);
    final LDAPContext ldapContext = context.asContext(LDAPContext.class);
    Connection tmp = null;
    try
    {
      tmp = ldapContext.getInternalConnectionFactory().getConnection(getUserDN(securityContext));
    }
    catch (LdapException | DirectoryException e)
    {
      closeSilently(tmp);
      return asErrorResponse(e);
    }
    final Connection authConnection = tmp;
    return next.handle(new AuthenticatedConnectionContext(context, authConnection), request)
               .thenFinally(new Runnable()
               {
                 @Override
                 public void run()
                 {
                   closeSilently(authConnection);
                 }
               });
  }

  private DN getUserDN(final SecurityContext securityContext) throws LdapException, DirectoryException
  {
    final String authzId = authzIdProvider.apply(securityContext);
    if (authzId.startsWith("u:"))
    {
      final Entry entry = identityMapper.getEntryForID(authzId);
      if (entry == null)
      {
        throw LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS);
      }
      return entry.getName();
    }
    else if (authzId.startsWith("dn:"))
    {
      try
      {
        return DN.valueOf(authzId.substring(3), schema);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw LdapException.newLdapException(ResultCode.INVALID_DN_SYNTAX, e);
      }
    }
    throw LdapException.newLdapException(ResultCode.AUTHORIZATION_DENIED);
  }

  static Promise<Response, NeverThrowsException> asErrorResponse(final Throwable t)
  {
    final ResourceException e = asResourceException(t);
    final Response response =
        new Response().setStatus(Status.valueOf(e.getCode())).setEntity(e.toJsonValue().getObject());
    return Promises.newResultPromise(response);
  }
}
