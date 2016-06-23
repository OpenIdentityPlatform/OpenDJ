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

import static org.forgerock.opendj.adapter.server3x.Adapters.newRootConnectionFactory;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newCtsAccessTokenResolver;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_AUTHZ_REFERENCED_DN_DOESNT_EXISTS;

import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.HTTPOauth2CtsAuthorizationMechanismCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;

/**
 * Injects an {@link AuthenticatedConnectionContext} from an OAuth2 access token which will be resolved from core token
 * service.
 */
final class HttpOAuth2CtsAuthorizationMechanism extends
    HttpOAuth2AuthorizationMechanism<HTTPOauth2CtsAuthorizationMechanismCfg>
{
  HttpOAuth2CtsAuthorizationMechanism(HTTPOauth2CtsAuthorizationMechanismCfg config, ServerContext serverContext)
      throws ConfigException
  {
    super(config, serverContext);
  }

  @Override
  AccessTokenResolver newAccessTokenResolver() throws ConfigException
  {
    try
    {
      if (DirectoryServer.getEntry(DN.valueOf(config.getBaseDN())) == null)
      {
        throw new ConfigException(ERR_CONFIG_AUTHZ_REFERENCED_DN_DOESNT_EXISTS.get(config.dn(), config.getBaseDN()));
      }
    }
    catch (DirectoryException e)
    {
      throw new ConfigException(e.getMessageObject());
    }
    return newCtsAccessTokenResolver(newRootConnectionFactory(), config.getBaseDN());
  }
}
