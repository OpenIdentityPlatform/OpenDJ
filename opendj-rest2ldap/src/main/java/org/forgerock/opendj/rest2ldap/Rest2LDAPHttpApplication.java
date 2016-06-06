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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.http.util.Json.readJsonLenient;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.setOf;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.configureConnectionFactory;
import static org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategies.*;
import static org.forgerock.opendj.rest2ldap.authz.Authorizations.*;
import static org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.*;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.*;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.authz.modules.oauth2.AccessTokenInfo;
import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.authz.modules.oauth2.AccessTokenResolver;
import org.forgerock.authz.modules.oauth2.cache.CachingAccessTokenResolver;
import org.forgerock.authz.modules.oauth2.resolver.OpenAmAccessTokenResolver;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.Filters;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Headers;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategy;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.Factory;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rest2ldap HTTP application. */
public class Rest2LDAPHttpApplication implements HttpApplication {
    private static final String DEFAULT_ROOT_FACTORY = "root";
    private static final String DEFAULT_BIND_FACTORY = "bind";

    /** Keys for json oauth2 configuration. */
    private static final String RESOLVER_CONFIG_OBJECT = "resolver";
    private static final String REALM = "realm";
    private static final String SCOPES = "requiredScopes";
    private static final String AUTHZID_TEMPLATE = "authzIdTemplate";
    private static final String CACHE_EXPIRATION_DEFAULT = "5 minutes";

    /** Keys for json oauth2 access token cache configuration. */
    private static final String CACHE_CONFIG_OBJECT = "accessTokenCache";
    private static final String CACHE_ENABLED = "enabled";
    private static final String CACHE_EXPIRATION = "cacheExpiration";

    private static final Logger LOG = LoggerFactory.getLogger(Rest2LDAPHttpApplication.class);

    /** URL to the JSON configuration file. */
    protected final URL configurationUrl;

    /** Schema used to perform DN validations. */
    protected final Schema schema;

    private final Map<String, ConnectionFactory> connectionFactories = new HashMap<>();
    /** Used for token caching. */
    private ScheduledExecutorService executorService;

    /** Define the method which should be used to resolve an OAuth2 access token. */
    private enum OAuth2ResolverType {
        RFC7662,
        OPENAM,
        CTS,
        FILE
    }

    @VisibleForTesting
    enum Policy { OAUTH2, BASIC, ANONYMOUS }

    private enum BindStrategy { SIMPLE, SEARCH, SASL_PLAIN }

    /**
     * Default constructor called by the HTTP Framework which will use the default configuration file location.
     */
    public Rest2LDAPHttpApplication() {
        this.configurationUrl = getClass().getResource("/opendj-rest2ldap-config.json");
        this.schema = Schema.getDefaultSchema();
    }

    /**
     * Creates a new Rest2LDAP HTTP application using the provided configuration URL.
     *
     * @param configurationURL
     *            The URL to the JSON configuration file
     * @param schema
     *            The {@link Schema} used to perform DN validations
     */
    public Rest2LDAPHttpApplication(final URL configurationURL, final Schema schema) {
        this.configurationUrl = checkNotNull(configurationURL, "configurationURL cannot be null");
        this.schema = checkNotNull(schema, "schema cannot be null");
    }

    @Override
    public final Handler start() throws HttpApplicationException {
        try {
            final JsonValue configuration = readJson(configurationUrl);
            executorService = Executors.newSingleThreadScheduledExecutor();
            configureConnectionFactories(configuration.get("ldapConnectionFactories"));
            return Handlers.chainOf(
                    CrestHttp.newHttpHandler(configureRest2Ldap(configuration)),
                    buildAuthorizationFilter(configuration.get("authorization").required()));
        } catch (final Exception e) {
            // TODO i18n, once supported in opendj-rest2ldap
            final String errorMsg = "Unable to start Rest2Ldap Http Application";
            LOG.error(errorMsg, e);
            stop();
            throw new HttpApplicationException(errorMsg, e);
        }
    }

    private static JsonValue readJson(final URL resource) throws IOException {
        try (InputStream in = resource.openStream()) {
            return new JsonValue(readJsonLenient(in));
        }
    }

    private static RequestHandler configureRest2Ldap(final JsonValue configuration) {
        final JsonValue mappings = configuration.get("mappings").required();
        final Router router = new Router();
        for (final String mappingUrl : mappings.keys()) {
            final JsonValue mapping = mappings.get(mappingUrl);
            router.addRoute(Router.uriTemplate(mappingUrl), Rest2LDAP.builder().configureMapping(mapping).build());
        }
        return router;
    }

    private void configureConnectionFactories(final JsonValue config) {
        connectionFactories.clear();
        for (String name : config.keys()) {
            connectionFactories.put(name, configureConnectionFactory(config, name));
        }
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        // Use container default buffer factory.
        return null;
    }

    @Override
    public void stop() {
        for (ConnectionFactory factory : connectionFactories.values()) {
            closeSilently(factory);
        }
        connectionFactories.clear();
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    private Filter buildAuthorizationFilter(final JsonValue config) throws HttpApplicationException {
        final Set<Policy> policies = config.get("policies").as(setOf(enumConstant(Policy.class)));
        final List<ConditionalFilter> filters = new ArrayList<>(policies.size());
        if (policies.contains(Policy.OAUTH2)) {
            filters.add(buildOAuth2Filter(config.get("oauth2")));
        }
        if (policies.contains(Policy.BASIC)) {
            filters.add(buildBasicFilter(config.get("basic")));
        }
        if (policies.contains(Policy.ANONYMOUS)) {
            filters.add(buildAnonymousFilter(config.get("anonymous")));
        }
        return newAuthorizationFilter(filters);
    }

    @VisibleForTesting
    ConditionalFilter buildOAuth2Filter(final JsonValue config) throws HttpApplicationException {
        final String realm = config.get(REALM).defaultTo("no_realm").asString();
        final Set<String> scopes = config.get(SCOPES).required().asSet(String.class);
        final AccessTokenResolver resolver =
                createCachedTokenResolverIfNeeded(config, parseUnderlyingResolver(config));
        final ConditionalFilter oAuth2Filter = newConditionalOAuth2ResourceServerFilter(
                realm, scopes, resolver, config.get(AUTHZID_TEMPLATE).required().asString());
        return newConditionalFilter(
                Filters.chainOf(oAuth2Filter.getFilter(),
                                newProxyAuthzFilter(getConnectionFactory(DEFAULT_ROOT_FACTORY))),
                oAuth2Filter.getCondition());
    }

    @VisibleForTesting
    AccessTokenResolver createCachedTokenResolverIfNeeded(
            final JsonValue config, final AccessTokenResolver resolver) {
        final JsonValue cacheConfig = config.get(CACHE_CONFIG_OBJECT);
        if (cacheConfig.isNull() || !cacheConfig.get(CACHE_ENABLED).defaultTo(Boolean.FALSE).asBoolean()) {
            return resolver;
        }
        final Duration expiration = parseCacheExpiration(
                cacheConfig.get(CACHE_EXPIRATION).defaultTo(CACHE_EXPIRATION_DEFAULT));

        final PerItemEvictionStrategyCache<String, Promise<AccessTokenInfo, AccessTokenException>> cache =
                new PerItemEvictionStrategyCache<>(executorService, expiration);
        cache.setMaxTimeout(expiration);
        return new CachingAccessTokenResolver(TimeService.SYSTEM, resolver, cache);
    }

    @VisibleForTesting
    AccessTokenResolver parseUnderlyingResolver(final JsonValue configuration) throws HttpApplicationException {
        final JsonValue resolver = configuration.get(RESOLVER_CONFIG_OBJECT).required();
        switch (resolver.as(enumConstant(OAuth2ResolverType.class))) {
        case RFC7662:
            return parseRfc7662Resolver(configuration);
        case OPENAM:
            return new OpenAmAccessTokenResolver(new HttpClientHandler(),
                                                 TimeService.SYSTEM,
                                                 configuration.get("openam").get("endpointURL").required().asString());
        case CTS:
            final JsonValue cts = configuration.get("cts").required();
            return newCtsAccessTokenResolver(
                getConnectionFactory(cts.get("ldapConnectionFactory").defaultTo(DEFAULT_ROOT_FACTORY).asString()),
                                     cts.get("baseDN").required().asString());
        case FILE:
            return newFileAccessTokenResolver(configuration.get("file").get("folderPath").required().asString());
        default:
            throw new JsonValueException(resolver, "is not a supported access token resolver");
        }
    }

    private AccessTokenResolver parseRfc7662Resolver(final JsonValue configuration) throws HttpApplicationException {
        final JsonValue rfc7662 = configuration.get("rfc7662").required();
        final String introspectionEndPointURL = rfc7662.get("endpointURL").required().asString();
        try {
            return newRfc7662AccessTokenResolver(new HttpClientHandler(),
                                                 new URI(introspectionEndPointURL),
                                                 rfc7662.get("clientId").required().asString(),
                                                 rfc7662.get("clientSecret").required().asString());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("The token introspection endpoint '"
                    + introspectionEndPointURL + "' URL has an invalid syntax: " + e.getLocalizedMessage(), e);
        }
    }

    private Duration parseCacheExpiration(final JsonValue expirationJson) {
        try {
            final Duration expiration = expirationJson.as(duration());
            if (expiration.isZero() || expiration.isUnlimited()) {
                throw new JsonValueException(expirationJson, "The cache expiration duration cannot be "
                        + (expiration.isZero() ? "zero" : "unlimited."));
            }
            return expiration;
        } catch (final Exception e) {
            throw new JsonValueException(expirationJson,
                      "Malformed duration value '" + expirationJson.toString() + "' for cache expiration. "
                    + "The duration syntax supports all human readable notations from day ('days'', 'day'', 'd'') "
                    + "to nanosecond ('nanoseconds', 'nanosecond', 'nanosec', 'nanos', 'nano', 'ns')");
        }
    }

    /**
     * Creates a new {@link Filter} in charge of injecting {@link AuthenticatedConnectionContext}.
     *
     * @param connectionFactory
     *            The {@link ConnectionFactory} providing the {@link Connection} injected as
     *            {@link AuthenticatedConnectionContext}
     * @return a newly created {@link Filter}
     */
    protected Filter newProxyAuthzFilter(final ConnectionFactory connectionFactory) {
        return newProxyAuthorizationFilter(connectionFactory);
    }

    private ConditionalFilter buildAnonymousFilter(final JsonValue config) {
        return newAnonymousFilter(getConnectionFactory(config.get("ldapConnectionFactory")
                                                             .defaultTo(DEFAULT_ROOT_FACTORY)
                                                             .asString()));
    }

    /**
     * Creates a new {@link Filter} in charge of injecting {@link AuthenticatedConnectionContext} directly from a
     * {@link ConnectionFactory}.
     *
     * @param connectionFactory
     *            The {@link ConnectionFactory} used to get the {@link Connection}
     * @return a newly created {@link Filter}
     */
    protected ConditionalFilter newAnonymousFilter(ConnectionFactory connectionFactory) {
        return newConditionalDirectConnectionFilter(connectionFactory);
    }

    /**
     * Gets a {@link ConnectionFactory} from its name.
     *
     * @param name
     *            Name of the {@link ConnectionFactory} as specified in the configuration
     * @return The associated {@link ConnectionFactory} or null if none can be found
     */
    protected ConnectionFactory getConnectionFactory(final String name) {
        return connectionFactories.get(name);
    }

    private ConditionalFilter buildBasicFilter(final JsonValue config) {
        final String bind = config.get("bind").required().asString();
        final BindStrategy strategy = BindStrategy.valueOf(bind.toUpperCase().replace('-', '_'));
        return newBasicAuthenticationFilter(buildBindStrategy(strategy, config.get(bind).required()),
                config.get("supportAltAuthentication").defaultTo(Boolean.FALSE).asBoolean()
                        ? newCustomHeaderExtractor(
                                config.get("altAuthenticationUsernameHeader").required().asString(),
                                config.get("altAuthenticationPasswordHeader").required().asString())
                        : httpBasicExtractor());
    }

    /**
     * Gets a {@link Filter} in charge of performing the HTTP-Basic Authentication. This filter create a
     * {@link SecurityContext} reflecting the authenticated users.
     *
     * @param authenticationStrategy
     *            The {@link AuthenticationStrategy} to use to authenticate the user.
     * @param credentialsExtractor
     *            Extract the user's credentials from the {@link Headers}.
     * @return A new {@link Filter}
     */
    protected ConditionalFilter newBasicAuthenticationFilter(AuthenticationStrategy authenticationStrategy,
            Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor) {
        final ConditionalFilter httpBasicFilter =
                newConditionalHttpBasicAuthenticationFilter(authenticationStrategy, credentialsExtractor);
        return newConditionalFilter(Filters.chainOf(httpBasicFilter.getFilter(),
                                                    newProxyAuthzFilter(getConnectionFactory(DEFAULT_ROOT_FACTORY))),
                                    httpBasicFilter.getCondition());
    }

    private AuthenticationStrategy buildBindStrategy(final BindStrategy strategy, final JsonValue config) {
        switch (strategy) {
        case SIMPLE:
            return buildSimpleBindStrategy(config);
        case SEARCH:
            return buildSearchThenBindStrategy(config);
        case SASL_PLAIN:
            return buildSASLBindStrategy(config);
        default:
            throw new IllegalArgumentException("Unsupported strategy '" + strategy + "'");
        }
    }

    private AuthenticationStrategy buildSimpleBindStrategy(final JsonValue config) {
        return newSimpleBindStrategy(getConnectionFactory(config.get("ldapConnectionFactory")
                                                                .defaultTo(DEFAULT_BIND_FACTORY).asString()),
                                     parseUserNameTemplate(config.get("bindDNTemplate").defaultTo("%s")),
                                     schema);
    }

    private AuthenticationStrategy buildSASLBindStrategy(JsonValue config) {
        return newSASLPlainStrategy(
                getConnectionFactory(config.get("ldapConnectionFactory").defaultTo(DEFAULT_BIND_FACTORY).asString()),
                schema, parseUserNameTemplate(config.get(AUTHZID_TEMPLATE).defaultTo("u:%s")));
    }

    private AuthenticationStrategy buildSearchThenBindStrategy(JsonValue config) {
        return newSearchThenBindStrategy(
                getConnectionFactory(
                        config.get("searchLDAPConnectionFactory").defaultTo(DEFAULT_ROOT_FACTORY).asString()),
                getConnectionFactory(
                        config.get("bindLDAPConnectionFactory").defaultTo(DEFAULT_BIND_FACTORY).asString()),
                DN.valueOf(config.get("baseDN").required().asString(), schema),
                SearchScope.valueOf(config.get("scope").required().asString().toLowerCase()),
                parseUserNameTemplate(config.get("filterTemplate").required()));
    }

    private String parseUserNameTemplate(final JsonValue template) {
        return template.asString().replace("{username}", "%s");
    }
}
