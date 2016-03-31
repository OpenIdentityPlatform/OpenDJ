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
package org.opends.server.protocols.http.rest2ldap;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.forgerock.http.HttpApplication;
import org.forgerock.opendj.server.config.server.Rest2ldapEndpointCfg;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.types.InitializationException;

/**
 * Encapsulates configuration required to start a rest2ldap application in an
 * OpenDJ context. Acts as a factory for {@link Rest2LdapEmbeddedHttpApplication}.
 */
public final class Rest2LdapEndpoint extends HttpEndpoint<Rest2ldapEndpointCfg>
{
  /**
   * Create a new Rest2LdapEnpoint with the supplied configuration.
   *
   * @param configuration
   *          Configuration to use for the {@link HttpApplication}
   */
  public Rest2LdapEndpoint(Rest2ldapEndpointCfg configuration)
  {
    super(configuration);
  }

  @Override
  public HttpApplication newHttpApplication() throws InitializationException
  {
    try
    {
      final URI configURI = new URI(configuration.getConfigUrl());
      final URL absoluteConfigUrl =
          configURI.isAbsolute() ? configURI.toURL() : getFileForPath(configuration.getConfigUrl()).toURI().toURL();
      return new Rest2LdapEmbeddedHttpApplication(absoluteConfigUrl, configuration.isAuthenticationRequired());
    }
    catch (MalformedURLException | URISyntaxException e)
    {
      throw new InitializationException(ERR_CONFIG_REST2LDAP_MALFORMED_URL
          .get(configuration.dn(), stackTraceToSingleLineString(e)));
    }
  }

}
