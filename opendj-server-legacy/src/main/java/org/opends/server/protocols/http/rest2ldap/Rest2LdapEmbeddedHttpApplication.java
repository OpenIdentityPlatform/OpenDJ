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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.protocols.http.rest2ldap;

import static org.forgerock.http.util.Json.*;
import static org.opends.messages.ProtocolMessages.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.json.resource.http.HttpContextFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.rest2ldap.AuthorizationPolicy;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.protocols.http.AuthenticationFilter;
import org.opends.server.protocols.http.HTTPAuthenticationConfig;

/** Entry point of the Rest2Ldap application when used in embedded mode. */
final class Rest2LdapEmbeddedHttpApplication implements HttpApplication
{
  /**
   * Http Handler re-using the pre-established internal LDAP connection.
   *
   * @see AuthenticationFilter
   */
  private static final class Rest2LdapHandler implements Handler
  {
    private final Handler delegate;

    /**
     * Build a new {@code LdapHttpHandler}.
     *
     * @param configuration
     *            The configuration which will be used to set
     *            the connection and the mappings to the OpenDJ server.
     */
    public Rest2LdapHandler(final JsonValue configuration)
    {
      final ConnectionFactory connectionFactory = Resources.newInternalConnectionFactory(createRouter(configuration));
      delegate = CrestHttp.newHttpHandler(connectionFactory, new HttpContextFactory()
      {
        @Override
        public Context createContext(Context parentContext, Request request) throws ResourceException
        {
          return parentContext;
        }
      });
    }

    private RequestHandler createRouter(final JsonValue configuration)
    {
      final JsonValue mappings = configuration.get("servlet").get("mappings").required();
      final Router router = new Router();

      for (final String mappingUrl : mappings.keys()) {
        final JsonValue mapping = mappings.get(mappingUrl);
        final CollectionResourceProvider provider = Rest2LDAP.builder()
                .authorizationPolicy(AuthorizationPolicy.REUSE)
                .configureMapping(mapping)
                .build();
        router.addRoute(Router.uriTemplate(mappingUrl), provider);
      }
      return router;
    }

    @Override
    public final Promise<Response, NeverThrowsException> handle(final Context context, final Request request)
    {
      return delegate.handle(context, request);
    }
  }

  private final URL configFileUrl;
  private final boolean authenticationRequired;

  Rest2LdapEmbeddedHttpApplication(URL configFileUrl, boolean authenticationRequired)
  {
    this.configFileUrl = configFileUrl;
    this.authenticationRequired = authenticationRequired;
  }

  @Override
  public Handler start() throws HttpApplicationException
  {
    try
    {
      final Object jsonElems = readJson(configFileUrl);
      final JsonValue configuration = new JsonValue(jsonElems).recordKeyAccesses();
      final Handler handler = Handlers.chainOf(
          new Rest2LdapHandler(configuration),
          new AuthenticationFilter(getAuthenticationConfig(configuration), authenticationRequired));
      configuration.verifyAllKeysAccessed();
      return handler;
    }
    catch (final Exception e)
    {
      stop();
      throw new HttpApplicationException(ERR_INITIALIZE_HTTP_CONNECTION_HANDLER.get().toString(), e);
    }
  }

  private static JsonValue readJson(final URL resource) throws IOException
  {
    try (final InputStream in = resource.openStream())
    {
      return new JsonValue(readJsonLenient(in));
    }
  }

  private static HTTPAuthenticationConfig getAuthenticationConfig(final JsonValue configuration)
  {
    final HTTPAuthenticationConfig result = new HTTPAuthenticationConfig();

    final JsonValue val = configuration.get("authenticationFilter");
    result.setBasicAuthenticationSupported(asBool(val, "supportHTTPBasicAuthentication"));
    result.setCustomHeadersAuthenticationSupported(asBool(val, "supportAltAuthentication"));
    result.setCustomHeaderUsername(val.get("altAuthenticationUsernameHeader").asString());
    result.setCustomHeaderPassword(val.get("altAuthenticationPasswordHeader").asString());

    result.setSearchBaseDN(DN.valueOf(asString(val, "searchBaseDN")));
    result.setSearchScope(SearchScope.valueOf(asString(val, "searchScope")));
    result.setSearchFilterTemplate(asString(val, "searchFilterTemplate"));

    return result;
  }

  private static String asString(JsonValue value, String key)
  {
    return value.get(key).required().asString();
  }

  private static boolean asBool(JsonValue value, String key)
  {
    return value.get(key).defaultTo(false).asBoolean();
  }

  @Override
  public Factory<Buffer> getBufferFactory()
  {
    return null;
  }

  @Override
  public void stop()
  {
    // Nothing to do
  }
}
