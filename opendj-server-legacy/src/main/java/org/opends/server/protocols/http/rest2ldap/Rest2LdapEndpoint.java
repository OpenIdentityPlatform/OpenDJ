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

import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.opendj.rest2ldap.Rest2LdapJsonConfigurator.configureEndpoint;
import static org.forgerock.util.Options.defaultOptions;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_REST2LDAP_INVALID;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_REST2LDAP_UNABLE_READ;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_REST2LDAP_UNEXPECTED_JSON;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.File;
import java.io.IOException;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.swagger.OpenApiRequestFilter;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.CrestApplication;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.opendj.rest2ldap.DescribableRequestHandler;
import org.forgerock.opendj.server.config.server.Rest2ldapEndpointCfg;
import org.forgerock.util.Factory;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.http.LocalizedHttpApplicationException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.BuildVersion;

/**
 * Encapsulates configuration required to start a REST2LDAP application embedded
 * in this LDAP server. Acts as a factory for {@link HttpApplication}.
 */
public final class Rest2LdapEndpoint extends HttpEndpoint<Rest2ldapEndpointCfg>
{

  /**
   * Create a new Rest2LdapEndpoint with the supplied configuration.
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
   * Specialized {@link HttpApplication} using internal connections to this local LDAP server.
   */
  private final class InternalRest2LDAPHttpApplication implements HttpApplication
  {
    @Override
    public Handler start() throws HttpApplicationException
    {
      final File endpointConfig = getFileForPath(configuration.getConfigDirectory(), serverContext);
      try
      {
        return chainOf(
            newHttpHandler(configureEndpoint(endpointConfig, defaultOptions())),
            new OpenApiRequestFilter());
      }
      catch (IOException e)
      {
        throw new LocalizedHttpApplicationException(ERR_CONFIG_REST2LDAP_UNABLE_READ.get(
                endpointConfig, configuration.dn(), stackTraceToSingleLineString(e)), e);
      }
      catch (JsonValueException e)
      {
        throw new LocalizedHttpApplicationException(ERR_CONFIG_REST2LDAP_UNEXPECTED_JSON.get(
                e.getJsonValue().getPointer(), endpointConfig, configuration.dn(), stackTraceToSingleLineString(e)), e);
      }
      catch (IllegalArgumentException e)
      {
        throw new LocalizedHttpApplicationException(ERR_CONFIG_REST2LDAP_INVALID.get(
                endpointConfig, configuration.dn(), stackTraceToSingleLineString(e)), e);
      }
    }

    private Handler newHttpHandler(final RequestHandler requestHandler) {
        final DescribableRequestHandler handler = new DescribableRequestHandler(requestHandler);
        final org.forgerock.json.resource.ConnectionFactory factory = Resources.newInternalConnectionFactory(handler);
        return CrestHttp.newHttpHandler(new CrestApplication() {
            @Override
            public org.forgerock.json.resource.ConnectionFactory getConnectionFactory() {
                return factory;
            }

            @Override
            public String getApiId() {
                return "frapi:opendj:rest2ldap";
            }

            @Override
            public String getApiVersion() {
                return BuildVersion.binaryVersion().toStringNoRevision();
            }
        });
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
