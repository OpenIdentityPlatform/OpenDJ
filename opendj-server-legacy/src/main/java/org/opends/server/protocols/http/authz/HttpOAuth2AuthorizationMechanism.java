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

import static org.forgerock.http.filter.Filters.chainOf;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_KEY_MANAGERS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSL_CIPHER_SUITES;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSL_ENABLED_PROTOCOLS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_TRUST_MANAGERS;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newConditionalOAuth2ResourceServerFilter;
import static org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.newConditionalFilter;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_OAUTH2_INVALID_JSON_POINTER;
import static org.opends.server.core.DirectoryServer.getCryptoManager;
import static org.opends.server.core.DirectoryServer.getIdentityMapper;
import static org.opends.server.core.DirectoryServer.getKeyManagerProvider;
import static org.opends.server.core.DirectoryServer.getTrustManagerProvider;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.openig.oauth2.AccessTokenException;
import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.openig.oauth2.resolver.CachingAccessTokenResolver;
import org.forgerock.json.JsonException;
import org.forgerock.json.JsonPointer;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.opendj.server.config.server.HTTPOauth2AuthorizationMechanismCfg;
import org.forgerock.util.Options;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;

/**
 * Abstract Authorization Mechanism injecting an {@link AuthenticatedConnectionContext} from an OAuth2 access token
 * resolved with an {@link AccessTokenResolver}.
 *
 * @param <T>
 *          Type of the configuration required by the OAuth2 authorization mechanism.
 */
abstract class HttpOAuth2AuthorizationMechanism<T extends HTTPOauth2AuthorizationMechanismCfg> extends
    HttpAuthorizationMechanism<T>
{
  private static final int HTTP_OAUTH2_PRIORITY = 100;
  private static final ScheduledExecutorService CACHE_EVICTOR = Executors.newSingleThreadScheduledExecutor();

  protected final T config;
  protected final ServerContext serverContext;
  private final ConditionalFilter delegate;

  HttpOAuth2AuthorizationMechanism(T config, ServerContext serverContext) throws ConfigException
  {
    super(config.dn(), HTTP_OAUTH2_PRIORITY);
    this.config = config;
    this.serverContext = serverContext;

    try
    {
      new JsonPointer(config.getAuthzidJsonPointer());
    }
    catch (JsonException e)
    {
      throw new ConfigException(ERR_CONFIG_OAUTH2_INVALID_JSON_POINTER.get(
          config.dn(), config.getAuthzidJsonPointer(), e.getMessage()), e);
    }

    AccessTokenResolver resolver = newAccessTokenResolver();
    if (config.isAccessTokenCacheEnabled())
    {
      final Duration expiration = Duration.duration(config.getAccessTokenCacheExpiration(), TimeUnit.SECONDS);
      final PerItemEvictionStrategyCache<String, Promise<AccessTokenInfo, AccessTokenException>> cache =
          new PerItemEvictionStrategyCache<String, Promise<AccessTokenInfo, AccessTokenException>>(CACHE_EVICTOR,
              expiration);
      cache.setMaxTimeout(expiration);
      resolver = new CachingAccessTokenResolver(TimeService.SYSTEM, resolver, cache);
    }

    final ConditionalFilter oauth2Filter = newConditionalOAuth2ResourceServerFilter(
        "no_realm", config.getRequiredScope(), resolver, "u:{" + config.getAuthzidJsonPointer()+"}");
    this.delegate = newConditionalFilter(
        chainOf(oauth2Filter.getFilter(), new InternalProxyAuthzFilter(getIdentityMapper(config.getIdentityMapperDN()),
                                                                                         serverContext.getSchemaNG())),
        oauth2Filter.getCondition());
  }

  abstract AccessTokenResolver newAccessTokenResolver() throws ConfigException;

  @Override
  final ConditionalFilter getDelegate()
  {
    return delegate;
  }

  static Options toHttpOptions(DN trustManagerDN, DN keyManagerDN) throws ConfigException
  {
    final Options options = Options.defaultOptions();
    try
    {
      options.set(OPTION_TRUST_MANAGERS, trustManagerDN != null
          ? getTrustManagerProvider(trustManagerDN).getTrustManagers() : null);
      options.set(OPTION_KEY_MANAGERS, keyManagerDN != null
          ? getKeyManagerProvider(keyManagerDN).getKeyManagers() : null);
      options.set(OPTION_SSL_CIPHER_SUITES, new ArrayList<>(getCryptoManager().getSslCipherSuites()));
      options.set(OPTION_SSL_ENABLED_PROTOCOLS, new ArrayList<>(getCryptoManager().getSslProtocols()));
    }
    catch (DirectoryException e)
    {
      throw new ConfigException(e.getMessageObject(), e);
    }
    return options;
  }
}
