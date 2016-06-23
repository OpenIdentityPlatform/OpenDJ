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

import static org.forgerock.opendj.rest2ldap.authz.Authorization.newFileAccessTokenResolver;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_OAUTH2_NON_EXISTING_DIRECTORY;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.HTTPOauth2FileAuthorizationMechanismCfg;
import org.opends.server.core.ServerContext;

/** Injects an {@link AuthenticatedConnectionContext} from an OAuth2 access token which will be resolved from a file. */
final class HttpOAuth2FileAuthorizationMechanism extends
    HttpOAuth2AuthorizationMechanism<HTTPOauth2FileAuthorizationMechanismCfg>
{
  HttpOAuth2FileAuthorizationMechanism(HTTPOauth2FileAuthorizationMechanismCfg config, ServerContext serverContext)
      throws ConfigException
  {
    super(config, serverContext);
  }

  @Override
  AccessTokenResolver newAccessTokenResolver() throws ConfigException
  {
    final String absoluteTokenDir = getFileForPath(config.getAccessTokenDirectory()).getAbsolutePath();
    try
    {
      Files.newDirectoryStream(Paths.get(absoluteTokenDir));
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_CONFIG_OAUTH2_NON_EXISTING_DIRECTORY.get(config.dn(), absoluteTokenDir), e);
    }
    return newFileAccessTokenResolver(absoluteTokenDir);
  }
}
