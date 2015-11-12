/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.protocols.http;

import static org.forgerock.util.Utils.closeSilently;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.io.File;
import java.io.FileReader;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.util.Json;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.json.resource.http.HttpContextFactory;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.rest2ldap.AuthorizationPolicy;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.messages.ProtocolMessages;

/** Main class of the HTTP Connection Handler web application */
class LdapHttpApplication implements HttpApplication
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Http Handler which sets a connection to an OpenDJ server. */
  private static class LdapHttpHandler implements Handler
  {
    private final Handler delegate;

    /**
     * Build a new {@code LdapHttpHandler}.
     *
     * @param configuration
     *            The configuration which will be used to set
     *            the connection and the mappings to the OpenDJ server.
     */
    public LdapHttpHandler(final JsonValue configuration)
    {
      ConnectionFactory connectionFactory = Resources.newInternalConnectionFactory(createRouter(configuration));
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

  private HTTPConnectionHandler connectionHandler;
  private LdapHttpHandler handler;
  private CollectClientConnectionsFilter filter;

  LdapHttpApplication(HTTPConnectionHandler connectionHandler)
  {
    this.connectionHandler = connectionHandler;
  }

  @Override
  public Handler start() throws HttpApplicationException
  {
    try
    {
      final File configFile = getFileForPath(connectionHandler.getCurrentConfig().getConfigFile());
      final Object jsonElems = Json.readJsonLenient(new FileReader(configFile));
      final JsonValue configuration = new JsonValue(jsonElems).recordKeyAccesses();
      handler = new LdapHttpHandler(configuration);
      filter = new CollectClientConnectionsFilter(connectionHandler, getAuthenticationConfig(configuration));
      configuration.verifyAllKeysAccessed();
      return Handlers.chainOf(handler, filter);
    }
    catch (final Exception e)
    {
      final LocalizableMessage errorMsg = ProtocolMessages.ERR_INITIALIZE_HTTP_CONNECTION_HANDLER.get();
      logger.error(errorMsg, e);
      stop();
      throw new HttpApplicationException(errorMsg.toString(), e);
    }
  }

  private HTTPAuthenticationConfig getAuthenticationConfig(final JsonValue configuration)
  {
    final HTTPAuthenticationConfig result = new HTTPAuthenticationConfig();

    final JsonValue val = configuration.get("authenticationFilter");
    result.setBasicAuthenticationSupported(asBool(val, "supportHTTPBasicAuthentication"));
    result.setCustomHeadersAuthenticationSupported(asBool(val, "supportAltAuthentication"));
    result.setCustomHeaderUsername(val.get("altAuthenticationUsernameHeader").asString());
    result.setCustomHeaderPassword(val.get("altAuthenticationPasswordHeader").asString());

    final String searchBaseDN = asString(val, "searchBaseDN");
    result.setSearchBaseDN(org.forgerock.opendj.ldap.DN.valueOf(searchBaseDN));
    result.setSearchScope(SearchScope.valueOf(asString(val, "searchScope")));
    result.setSearchFilterTemplate(asString(val, "searchFilterTemplate"));

    return result;
  }

  private String asString(JsonValue value, String key)
  {
    return value.get(key).required().asString();
  }

  private boolean asBool(JsonValue value, String key)
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
    closeSilently(filter);
    handler = null;
    filter = null;
  }
}
