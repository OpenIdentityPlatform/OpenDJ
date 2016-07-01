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

import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_KEY_MANAGERS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_TRUST_MANAGERS;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.setOf;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.opendj.ldap.KeyManagers.useSingleCertificate;
import static org.forgerock.opendj.rest2ldap.Rest2LdapJsonConfigurator.*;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.Utils.newJsonValueException;
import static org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategies.newSaslPlainStrategy;
import static org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategies.newSearchThenBindStrategy;
import static org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategies.newSimpleBindStrategy;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.*;
import static org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.newConditionalFilter;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.httpBasicExtractor;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.newCustomHeaderExtractor;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.Utils.joinAsString;

import java.io.File;
import java.io.IOException;
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

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.Filters;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Headers;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategy;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.openig.oauth2.AccessTokenException;
import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.openig.oauth2.resolver.CachingAccessTokenResolver;
import org.forgerock.openig.oauth2.resolver.OpenAmAccessTokenResolver;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.Factory;
import org.forgerock.util.Function;
import org.forgerock.util.Options;
import org.forgerock.util.Pair;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/** Rest2ldap HTTP application. */
public class Rest2LdapHttpApplication implements HttpApplication {
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

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The name of the JSON configuration directory in which config.json and rest2ldap/rest2ldap.json are located. */
    protected final File configDirectory;

    /** Schema used to perform DN validations. */
    protected final Schema schema;

    private final Map<String, ConnectionFactory> connectionFactories = new HashMap<>();
    /** Used for token caching. */
    private ScheduledExecutorService executorService;

    private TrustManager trustManager;
    private X509KeyManager keyManager;

    /** Define the method which should be used to resolve an OAuth2 access token. */
    private enum OAuth2ResolverType {
        RFC7662, OPENAM, CTS, FILE;

        private static String listValues() {
            final List<String> values = new ArrayList<>();
            for (final OAuth2ResolverType value : OAuth2ResolverType.values()) {
                values.add(value.name().toLowerCase());
            }
            return joinAsString(",", values);
        }
    }

    @VisibleForTesting
    enum Policy { OAUTH2, BASIC, ANONYMOUS }

    private enum BindStrategy {
        SIMPLE("simple"),
        SEARCH("search"),
        SASL_PLAIN("sasl-plain");

        private final String jsonField;

        BindStrategy(final String jsonField) {
            this.jsonField = jsonField;
        }

        private static String listValues() {
            final List<String> values = new ArrayList<>();
            for (final BindStrategy mapping : BindStrategy.values()) {
                values.add(mapping.jsonField);
            }
            return joinAsString(",", values);
        }
    }

    /**
     * Default constructor called by the HTTP Framework which will use the default configuration directory.
     */
    public Rest2LdapHttpApplication() {
        try {
            // The null check is required for unit test mocks because the resource does not exist.
            final URL configUrl = getClass().getResource("/config.json");
            this.configDirectory = configUrl != null ? new File(configUrl.toURI()).getParentFile() : null;
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        this.schema = Schema.getDefaultSchema();
    }

    /**
     * Creates a new Rest2LDAP HTTP application using the provided configuration directory.
     *
     * @param configDirectory
     *         The name of the JSON configuration directory in which config.json and rest2ldap/rest2ldap.json are
     *         located.
     * @param schema
     *         The {@link Schema} used to perform DN validations
     */
    public Rest2LdapHttpApplication(final File configDirectory, final Schema schema) {
        this.configDirectory = checkNotNull(configDirectory, "configDirectory cannot be null");
        this.schema = checkNotNull(schema, "schema cannot be null");
    }

    @Override
    public final Handler start() throws HttpApplicationException {
        try {
            logger.info(INFO_REST2LDAP_STARTING.get(configDirectory));

            executorService = Executors.newSingleThreadScheduledExecutor();

            final JsonValue config = readJson(new File(configDirectory, "config.json"));
            configureSecurity(config.get("security"));
            configureConnectionFactories(config.get("ldapConnectionFactories"));
            final Filter authorizationFilter = buildAuthorizationFilter(config.get("authorization").required());
            return chainOf(newHttpHandler(configureRest2Ldap(configDirectory)),
                           new ErrorLoggerFilter(),
                           authorizationFilter);
        } catch (final Exception e) {
            final LocalizableMessage errorMsg = ERR_FAIL_PARSE_CONFIGURATION.get(e.getLocalizedMessage());
            logger.error(errorMsg, e);
            stop();
            throw new HttpApplicationException(errorMsg.toString(), e);
        }
    }

    private static RequestHandler configureRest2Ldap(final File configDirectory) throws IOException {
        final File rest2LdapConfigDirectory = new File(configDirectory, "rest2ldap");
        final Options options = configureOptions(readJson(new File(rest2LdapConfigDirectory, "rest2ldap.json")));
        final File endpointsDirectory = new File(rest2LdapConfigDirectory, "endpoints");
        return configureEndpoints(endpointsDirectory, options);
    }

    private void configureSecurity(final JsonValue configuration) {
        trustManager = configureTrustManager(configuration);
        keyManager = configureKeyManager(configuration);
    }

    private void configureConnectionFactories(final JsonValue config) {
        connectionFactories.clear();
        for (String name : config.keys()) {
            connectionFactories.put(name, configureConnectionFactory(config, name, trustManager, keyManager));
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
        final String resolverName = config.get(RESOLVER_CONFIG_OBJECT).asString();
        final ConditionalFilter oAuth2Filter = newConditionalOAuth2ResourceServerFilter(
                realm, scopes, resolver, config.get(resolverName).get(AUTHZID_TEMPLATE).required().asString());
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
            final JsonValue openAm = configuration.get("openam");
            return new OpenAmAccessTokenResolver(newHttpClientHandler(openAm),
                                                 TimeService.SYSTEM,
                                                 openAm.get("endpointUrl").required().asString());
        case CTS:
            final JsonValue cts = configuration.get("cts").required();
            return newCtsAccessTokenResolver(
                getConnectionFactory(cts.get("ldapConnectionFactory").defaultTo(DEFAULT_ROOT_FACTORY).asString()),
                                     cts.get("baseDn").required().asString());
        case FILE:
            return newFileAccessTokenResolver(configuration.get("file").get("folderPath").required().asString());
        default:
            throw newJsonValueException(resolver,
                                        ERR_CONFIG_OAUTH2_UNSUPPORTED_ACCESS_TOKEN_RESOLVER.get(
                                                resolver.getObject(), OAuth2ResolverType.listValues()));
        }
    }

    private AccessTokenResolver parseRfc7662Resolver(final JsonValue configuration) throws HttpApplicationException {
        final JsonValue rfc7662 = configuration.get("rfc7662").required();
        final String introspectionEndPointURL = rfc7662.get("endpointUrl").required().asString();
        try {
            return newRfc7662AccessTokenResolver(newHttpClientHandler(rfc7662),
                                                 new URI(introspectionEndPointURL),
                                                 rfc7662.get("clientId").required().asString(),
                                                 rfc7662.get("clientSecret").required().asString());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException(ERR_CONIFG_OAUTH2_INVALID_INTROSPECT_URL.get(
                    introspectionEndPointURL, e.getLocalizedMessage()).toString(), e);
        }
    }

    private HttpClientHandler newHttpClientHandler(final JsonValue config) throws HttpApplicationException {
        final Options httpOptions = Options.defaultOptions();
        if (trustManager != null) {
            httpOptions.set(OPTION_TRUST_MANAGERS, new TrustManager[] { trustManager });
        }
        if (keyManager != null) {
            final String keyAlias = config.get("sslCertAlias").asString();
            httpOptions.set(OPTION_KEY_MANAGERS,
                    new KeyManager[] { keyAlias != null ? useSingleCertificate(keyAlias, keyManager) : keyManager });
        }
        return new HttpClientHandler(httpOptions);
    }

    private Duration parseCacheExpiration(final JsonValue expirationJson) {
        try {
            final Duration expiration = expirationJson.as(duration());
            if (expiration.isZero() || expiration.isUnlimited()) {
                throw newJsonValueException(expirationJson,
                                            expiration.isZero() ? ERR_CONIFG_OAUTH2_CACHE_ZERO_DURATION.get()
                                                                : ERR_CONIFG_OAUTH2_CACHE_UNLIMITED_DURATION.get());
            }
            return expiration;
        } catch (final Exception e) {
            throw newJsonValueException(expirationJson,
                                        ERR_CONFIG_OAUTH2_CACHE_INVALID_DURATION.get(expirationJson.toString()));
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
            return buildSaslBindStrategy(config);
        default:
            throw new LocalizedIllegalArgumentException(
                    ERR_CONFIG_UNSUPPORTED_BIND_STRATEGY.get(strategy, BindStrategy.listValues()));
        }
    }

    private AuthenticationStrategy buildSimpleBindStrategy(final JsonValue config) {
        return newSimpleBindStrategy(getConnectionFactory(config.get("ldapConnectionFactory")
                                                                .defaultTo(DEFAULT_BIND_FACTORY).asString()),
                                     parseUserNameTemplate(config.get("bindDnTemplate").defaultTo("%s")),
                                     schema);
    }

    private AuthenticationStrategy buildSaslBindStrategy(JsonValue config) {
        return newSaslPlainStrategy(
                getConnectionFactory(config.get("ldapConnectionFactory").defaultTo(DEFAULT_BIND_FACTORY).asString()),
                schema, parseUserNameTemplate(config.get(AUTHZID_TEMPLATE).defaultTo("u:%s")));
    }

    private AuthenticationStrategy buildSearchThenBindStrategy(JsonValue config) {
        return newSearchThenBindStrategy(
                getConnectionFactory(
                        config.get("searchLdapConnectionFactory").defaultTo(DEFAULT_ROOT_FACTORY).asString()),
                getConnectionFactory(
                        config.get("bindLdapConnectionFactory").defaultTo(DEFAULT_BIND_FACTORY).asString()),
                DN.valueOf(config.get("baseDn").required().asString(), schema),
                SearchScope.valueOf(config.get("scope").required().asString().toLowerCase()),
                parseUserNameTemplate(config.get("filterTemplate").required()));
    }

    private String parseUserNameTemplate(final JsonValue template) {
        return template.asString().replace("{username}", "%s");
    }
}
