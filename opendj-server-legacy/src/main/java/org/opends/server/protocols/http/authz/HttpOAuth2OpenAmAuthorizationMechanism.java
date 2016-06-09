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

import static org.opends.messages.ConfigMessages.ERR_CONFIG_OAUTH2_CONFIG_ERROR;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_OAUTH2_INVALID_URL;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.openig.oauth2.resolver.OpenAmAccessTokenResolver;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.HTTPOauth2OpenamAuthorizationMechanismCfg;
import org.forgerock.util.time.TimeService;
import org.opends.server.core.ServerContext;

/**
 * Injects an {@link AuthenticatedConnectionContext} from an OAuth2 access token which will be resolved by an external
 * OpenAM instance.
 */
final class HttpOAuth2OpenAmAuthorizationMechanism extends
    HttpOAuth2AuthorizationMechanism<HTTPOauth2OpenamAuthorizationMechanismCfg>
{
  HttpOAuth2OpenAmAuthorizationMechanism(HTTPOauth2OpenamAuthorizationMechanismCfg config, ServerContext serverContext)
      throws ConfigException
  {
    super(config, serverContext);
  }

  @Override
  AccessTokenResolver newAccessTokenResolver() throws ConfigException
  {
    try
    {
      new URI(config.getTokenInfoUrl());
    }
    catch (URISyntaxException e)
    {
      throw new ConfigException(ERR_CONFIG_OAUTH2_INVALID_URL.get(
          config.dn(), config.getTokenInfoUrl(), e.getMessage()), e);
    }
    try
    {
      return
          new OpenAmAccessTokenResolver(
              new HttpClientHandler(toHttpOptions(config.getTrustManagerProviderDN(),
                                                  config.getKeyManagerProviderDN())),
              TimeService.SYSTEM, config.getTokenInfoUrl());
    }
    catch (HttpApplicationException e)
    {
      throw new ConfigException(ERR_CONFIG_OAUTH2_CONFIG_ERROR.get(config.dn(), e.getMessage()), e);
    }
  }
}
