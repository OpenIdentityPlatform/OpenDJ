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

import static org.forgerock.opendj.rest2ldap.authz.Authorization.newRfc7662AccessTokenResolver;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_OAUTH2_CONFIG_ERROR;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_OAUTH2_INVALID_URL;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.HTTPOauth2TokenIntrospectionAuthorizationMechanismCfg;
import org.opends.server.core.ServerContext;

/**
 * Injects an {@link AuthenticatedConnectionContext} from an OAuth2 access token which will be resolved by an external
 * RFC7662 introspection endpoint.
 */
final class HttpOAuth2TokenIntrospectionAuthorizationMechanism extends
    HttpOAuth2AuthorizationMechanism<HTTPOauth2TokenIntrospectionAuthorizationMechanismCfg>
{
  HttpOAuth2TokenIntrospectionAuthorizationMechanism(HTTPOauth2TokenIntrospectionAuthorizationMechanismCfg config,
      ServerContext serverContext) throws ConfigException
  {
    super(config, serverContext);
  }

  @Override
  AccessTokenResolver newAccessTokenResolver() throws ConfigException
  {
    try
    {
      return newRfc7662AccessTokenResolver(
          new HttpClientHandler(toHttpOptions(config.getTrustManagerProviderDN(), config.getKeyManagerProviderDN())),
          new URI(config.getTokenIntrospectionUrl()),
          config.getClientId(), config.getClientSecret());
    }
    catch (HttpApplicationException e)
    {
      throw new ConfigException(ERR_CONFIG_OAUTH2_CONFIG_ERROR.get(config.dn(), e.getMessage()), e);
    }
    catch (URISyntaxException e)
    {
      throw new ConfigException(ERR_CONFIG_OAUTH2_INVALID_URL.get(
          config.dn(), config.getTokenIntrospectionUrl(), e.getMessage()), e);
    }
  }
}
