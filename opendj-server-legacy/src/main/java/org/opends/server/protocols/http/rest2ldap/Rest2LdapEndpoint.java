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

import static org.opends.messages.ConfigMessages.ERR_CONFIG_REST2LDAP_MALFORMED_URL;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.forgerock.http.Filter;
import org.forgerock.http.HttpApplication;
import org.forgerock.opendj.adapter.server3x.Adapters;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.rest2ldap.Rest2LDAPHttpApplication;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.opendj.server.config.server.Rest2ldapEndpointCfg;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.AuthenticationInfo;
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
    try
    {
      final URI configURI = new URI(configuration.getConfigUrl());
      final URL absoluteConfigUrl =
          configURI.isAbsolute() ? configURI.toURL() : getFileForPath(configuration.getConfigUrl()).toURI().toURL();
      return new InternalRest2LDAPHttpApplication(absoluteConfigUrl, serverContext.getSchemaNG());
    }
    catch (MalformedURLException | URISyntaxException e)
    {
      throw new InitializationException(ERR_CONFIG_REST2LDAP_MALFORMED_URL
          .get(configuration.dn(), stackTraceToSingleLineString(e)));
    }
  }

  /**
   * Specialized {@link Rest2LDAPHttpApplication} using internal connections to
   * this local LDAP server.
   */
  private final class InternalRest2LDAPHttpApplication extends Rest2LDAPHttpApplication
  {
    private final ConnectionFactory rootInternalConnectionFactory = Adapters.newRootConnectionFactory();
    private final ConnectionFactory anonymousInternalConnectionFactory =
        Adapters.newConnectionFactory(new InternalClientConnection((AuthenticationInfo) null));

    InternalRest2LDAPHttpApplication(final URL configURL, final Schema schema)
    {
      super(configURL, schema);
    }

    @Override
    protected ConditionalFilter newAnonymousFilter(final ConnectionFactory connectionFactory)
    {
      return super.newAnonymousFilter(anonymousInternalConnectionFactory);
    }

    @Override
    protected Filter newProxyAuthzFilter(final ConnectionFactory connectionFactory)
    {
      return new InternalProxyAuthzFilter(DirectoryServer.getProxiedAuthorizationIdentityMapper(), schema);
    }

    @Override
    protected ConnectionFactory getConnectionFactory(final String name)
    {
      return rootInternalConnectionFactory;
    }
  }
}
