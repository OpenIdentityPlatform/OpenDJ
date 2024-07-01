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

import static org.forgerock.opendj.rest2ldap.authz.Authorization.newConditionalDirectConnectionFilter;
import static org.forgerock.opendj.adapter.server3x.Adapters.*;

import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.opendj.server.config.server.HTTPAnonymousAuthorizationMechanismCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.DirectoryException;

/**
 * Injects an {@link AuthenticatedConnectionContext} with a {@link Connection} authenticated as the given user's DN.
 *
 * @see {@link Authorizations#newConditionalDirectConnectionFilter(ConnectionFactory)}
 */
final class HttpAnonymousAuthorizationMechanism extends
    HttpAuthorizationMechanism<HTTPAnonymousAuthorizationMechanismCfg>
{
  private static final int STATIC_FILTER_PRIORITY = Integer.MAX_VALUE;

  private final ConditionalFilter delegate;

  HttpAnonymousAuthorizationMechanism(HTTPAnonymousAuthorizationMechanismCfg config, ServerContext serverContext)
      throws ConfigException
  {
    super(config.dn(), STATIC_FILTER_PRIORITY);
    try
    {
      this.delegate =
          newConditionalDirectConnectionFilter(newConnectionFactory(new InternalClientConnection(config.getUserDN())));
    }
    catch (DirectoryException e)
    {
      throw new ConfigException(e.getMessageObject(), e);
    }
  }

  @Override
  ConditionalFilter getDelegate()
  {
    return delegate;
  }
}
