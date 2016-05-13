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

import static org.forgerock.http.util.Json.readJsonLenient;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.io.Buffer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.opendj.server.config.server.Rest2ldapEndpointCfg;
import org.forgerock.util.Factory;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.http.LocalizedHttpApplicationException;
import org.opends.server.types.InitializationException;

/**
 * Encapsulates configuration required to start a REST2LDAP application embedded
 * in this LDAP server. Acts as a factory for {@link Rest2LDAPHttpApplication}.
 */
public final class Rest2LdapEndpoint extends HttpEndpoint<Rest2ldapEndpointCfg>
{

  /**
   * Create a new Rest2LdapEnpoint with the supplied configuration.
   *
   * @param configuration
   *          Configuration to use for the {@link HttpApplication}
   * @param serverContext
   *          Server of this LDAP server
   */
  public Rest2LdapEndpoint(Rest2ldapEndpointCfg configuration, ServerContext serverContext)
  {
    super(configuration, serverContext);
  }

  @Override
  public HttpApplication newHttpApplication() throws InitializationException
  {
    return new InternalRest2LDAPHttpApplication();
  }

  /**
   * Specialized {@link Rest2LDAPHttpApplication} using internal connections to
   * this local LDAP server.
   */
  private final class InternalRest2LDAPHttpApplication implements HttpApplication
  {
    private final URL configURL;

    InternalRest2LDAPHttpApplication() throws InitializationException
    {
      try
      {
        final URI configURI = new URI(configuration.getConfigUrl());
        configURL = configURI.isAbsolute()
            ? configURI.toURL()
            : getFileForPath(configuration.getConfigUrl()).toURI().toURL();
      }
      catch (MalformedURLException | URISyntaxException e)
      {
        throw new InitializationException(
            ERR_CONFIG_REST2LDAP_MALFORMED_URL.get(configuration.dn(), stackTraceToSingleLineString(e)));
      }
    }

    @Override
    public Handler start() throws HttpApplicationException
    {
      JsonValue mappingConfiguration;
      try
      {
        mappingConfiguration = readJson(configURL);
      }
      catch (IOException e)
      {
        throw new LocalizedHttpApplicationException(
            ERR_CONFIG_REST2LDAP_UNABLE_READ.get(configURL, configuration.dn(), stackTraceToSingleLineString(e)), e);
      }
      final JsonValue mappings = mappingConfiguration.get("mappings").required();
      final Router router = new Router();
      try
      {
        for (final String mappingUrl : mappings.keys())
        {
          final JsonValue mapping = mappings.get(mappingUrl);
          router.addRoute(Router.uriTemplate(mappingUrl), Rest2LDAP.builder().configureMapping(mapping).build());
        }
      }
      catch (JsonValueException e)
      {
        throw new LocalizedHttpApplicationException(
            ERR_CONFIG_REST2LDAP_UNEXPECTED_JSON.get(e.getJsonValue().getPointer(), configURL, configuration.dn(),
                                                     stackTraceToSingleLineString(e)), e);
      }
      catch (IllegalArgumentException e)
      {
        throw new LocalizedHttpApplicationException(
            ERR_CONFIG_REST2LDAP_INVALID.get(configURL, configuration.dn(), stackTraceToSingleLineString(e)), e);
      }
      return CrestHttp.newHttpHandler(router);
    }

    private JsonValue readJson(final URL resource) throws IOException
    {
      try (InputStream in = resource.openStream())
      {
        return new JsonValue(readJsonLenient(in));
      }
    }

    @Override
    public void stop()
    {
      // Nothing to do
    }

    @Override
    public Factory<Buffer> getBufferFactory()
    {
      return null;
    }
  }
}
