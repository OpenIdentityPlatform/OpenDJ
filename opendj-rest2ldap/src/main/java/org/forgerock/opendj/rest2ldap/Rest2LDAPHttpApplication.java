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
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.configureConnectionFactory;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.Filters;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategy;
import org.forgerock.opendj.rest2ldap.authz.DirectConnectionFilter;
import org.forgerock.opendj.rest2ldap.authz.HttpBasicAuthenticationFilter;
import org.forgerock.opendj.rest2ldap.authz.HttpBasicAuthenticationFilter.CustomHeaderExtractor;
import org.forgerock.opendj.rest2ldap.authz.HttpBasicAuthenticationFilter.HttpBasicExtractor;
import org.forgerock.opendj.rest2ldap.authz.OptionalFilter;
import org.forgerock.opendj.rest2ldap.authz.OptionalFilter.ConditionalFilter;
import org.forgerock.opendj.rest2ldap.authz.ProxiedAuthV2Filter;
import org.forgerock.opendj.rest2ldap.authz.ProxiedAuthV2Filter.IntrospectionAuthzProvider;
import org.forgerock.opendj.rest2ldap.authz.SASLPlainStrategy;
import org.forgerock.opendj.rest2ldap.authz.SearchThenBindStrategy;
import org.forgerock.opendj.rest2ldap.authz.SimpleBindStrategy;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.Factory;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rest2ldap HTTP application. */
public class Rest2LDAPHttpApplication implements HttpApplication {
    private static final String DEFAULT_ROOT_FACTORY = "root";
    private static final String DEFAULT_BIND_FACTORY = "bind";

    private static final Logger LOG = LoggerFactory.getLogger(Rest2LDAPHttpApplication.class);

    /** URL to the JSON configuration file. */
    protected final URL configurationUrl;

    /** Schema used to perform DN validations. */
    protected final Schema schema;

    private final Map<String, ConnectionFactory> connectionFactories = new HashMap<>();

    private enum Policy {
        oauth2    (0),
        basic     (50),
        anonymous (100);

        private final int priority;

        Policy(int priority) {
            this.priority = priority;
        }
    }

    private enum BindStrategy {
        simple, search, sasl_plain
    }

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
            configureConnectionFactories(configuration.get("ldapConnectionFactories"));
            return Handlers.chainOf(
                    CrestHttp.newHttpHandler(configureRest2Ldap(configuration)),
                    newAuthorizationFilter(configuration.get("authorization").required()));
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
    }

    private Filter newAuthorizationFilter(final JsonValue config) {
        final List<Policy> configuredPolicies = new ArrayList<>();
        for (String policy : config.get("policies").required().asList(String.class)) {
            configuredPolicies.add(Policy.valueOf(policy.toLowerCase()));
        }
        final TreeMap<Integer, Filter> policyFilters = new TreeMap<>();
        final int lastIndex = configuredPolicies.size() - 1;
        for (int i = 0; i < configuredPolicies.size(); i++) {
            final Policy policy = configuredPolicies.get(i);
            policyFilters.put(policy.priority,
                    buildAuthzPolicyFilter(policy, config.get(policy.toString()), i != lastIndex));
        }
        return Filters.chainOf(new ArrayList<>(policyFilters.values()));
    }

    private Filter buildAuthzPolicyFilter(final Policy policy, final JsonValue config, boolean optional) {
        switch (policy) {
        case anonymous:
            return buildAnonymousFilter(config);
        case basic:
            final ConditionalFilter basicFilter = buildBasicFilter(config.required());
            final Filter basicFilterChain =
                    config.get("reuseAuthenticatedConnection").defaultTo(Boolean.FALSE).asBoolean()
                        ? basicFilter
                        : Filters.chainOf(basicFilter, newProxyAuthzFilter(getConnectionFactory(DEFAULT_ROOT_FACTORY),
                                                                           IntrospectionAuthzProvider.INSTANCE));
            return optional ? new OptionalFilter(basicFilterChain, basicFilter) : basicFilterChain;
        default:
            throw new IllegalArgumentException("Unsupported policy '" + policy + "'");
        }
    }

    /**
     * Create a new {@link Filter} in charge of injecting {@link AuthenticatedConnectionContext}.
     *
     * @param connectionFactory
     *            The {@link ConnectionFactory} providing the {@link Connection} injected as
     *            {@link AuthenticatedConnectionContext}
     * @param authzIdProvider
     *            Function computing the authzId to use for the LDAP's ProxiedAuth control.
     * @return a newly created {@link Filter}
     */
    protected Filter newProxyAuthzFilter(final ConnectionFactory connectionFactory,
            final Function<SecurityContext, String, LdapException> authzIdProvider) {
        return new ProxiedAuthV2Filter(connectionFactory, authzIdProvider);
    }

    private Filter buildAnonymousFilter(final JsonValue config) {
        if (config.contains("userDN")) {
            final DN userDN = DN.valueOf(config.get("userDN").asString(), schema);
            final Map<String, Object> authz = new HashMap<>(1);
            authz.put(SecurityContext.AUTHZID_DN, userDN.toString());
            return Filters.chainOf(
                    newStaticSecurityContextFilter(null, authz),
                    newProxyAuthzFilter(
                            getConnectionFactory(config.get("ldapConnectionFactory")
                                    .defaultTo(DEFAULT_ROOT_FACTORY)
                                    .asString()),
                            IntrospectionAuthzProvider.INSTANCE));
        }
        return newDirectConnectionFilter(getConnectionFactory(config.get("ldapConnectionFactory")
                                                             .defaultTo(DEFAULT_ROOT_FACTORY).asString()));
    }

    /**
     * Create a new {@link Filter} injecting a predefined {@link SecurityContext}.
     *
     * @param authenticationId
     *            AuthenticationID of the {@link SecurityContext}.
     * @param authorization
     *            Authorization of the {@link SecurityContext}
     * @return a newly created {@link Filter}
     */
    protected Filter newStaticSecurityContextFilter(final String authenticationId,
            final Map<String, Object> authorization) {
        return new Filter() {
            @Override
            public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
                return next.handle(new SecurityContext(context, authenticationId, authorization), request);
            }
        };
    }

    /**
     * Create a new {@link Filter} in charge of injecting {@link AuthenticatedConnectionContext} directly from a
     * {@link ConnectionFactory}.
     *
     * @param connectionFactory
     *            The {@link ConnectionFactory} used to get the {@link Connection}
     * @return a newly created {@link Filter}
     */
    protected Filter newDirectConnectionFilter(ConnectionFactory connectionFactory) {
        return new DirectConnectionFilter(connectionFactory);
    }

    /**
     * Get a {@link ConnectionFactory} from its name.
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
        final BindStrategy strategy = BindStrategy.valueOf(bind.toLowerCase().replace('-', '_'));
        return newBasicAuthenticationFilter(buildBindStrategy(strategy, config.get(bind).required()),
                config.get("supportAltAuthentication").defaultTo(Boolean.FALSE).asBoolean()
                        ? new CustomHeaderExtractor(
                                config.get("altAuthenticationUsernameHeader").required().asString(),
                                config.get("altAuthenticationPasswordHeader").required().asString())
                        : HttpBasicExtractor.INSTANCE,
                config.get("reuseAuthenticatedConnection").defaultTo(Boolean.FALSE).asBoolean());
    }

    /**
     * Get a {@link Filter} in charge of performing the HTTP-Basic Authentication. This filter create a
     * {@link SecurityContext} reflecting the authenticated users.
     *
     * @param authenticationStrategy
     *            The {@link AuthenticationStrategy} to use to authenticate the user.
     * @param credentialsExtractor
     *            Extract the user's credentials from the {@link Headers}.
     * @param reuseAuthenticatedConnection
     *            Let the bound connection open so that it can be reused to perform the LDAP operations.
     * @return A new {@link Filter}
     */
    protected ConditionalFilter newBasicAuthenticationFilter(AuthenticationStrategy authenticationStrategy,
            Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor,
            boolean reuseAuthenticatedConnection) {
        return new HttpBasicAuthenticationFilter(authenticationStrategy, credentialsExtractor,
                reuseAuthenticatedConnection);
    }

    private AuthenticationStrategy buildBindStrategy(final BindStrategy strategy, final JsonValue config) {
        switch (strategy) {
        case simple:
            return buildSimpleBindStrategy(config);
        case search:
            return buildSearchThenBindStrategy(config);
        case sasl_plain:
            return buildSASLBindStrategy(config);
        default:
            throw new IllegalArgumentException("Unsupported strategy '" + strategy + "'");
        }
    }

    private AuthenticationStrategy buildSimpleBindStrategy(final JsonValue config) {
        return newSimpleBindStrategy(getConnectionFactory(config.get("ldapConnectionFactory")
                                                                .defaultTo(DEFAULT_BIND_FACTORY).asString()),
                                     config.get("bindDNTemplate").defaultTo("%s").asString(),
                                     schema);
    }

    /**
     * {@link AuthenticationStrategy} performing an LDAP Bind request with a computed DN.
     *
     * @param connectionFactory
     *            The {@link ConnectionFactory} to use to perform the bind operation
     * @param schema
     *            {@link Schema} used to perform the DN validation.
     * @param bindDNTemplate
     *            DN template containing a single %s which will be replaced by the authenticating user's name. (i.e:
     *            uid=%s,ou=people,dc=example,dc=com)
     * @return A new {@link AuthenticationStrategy}
     */
    protected AuthenticationStrategy newSimpleBindStrategy(ConnectionFactory connectionFactory, String bindDNTemplate,
            Schema schema) {
        return new SimpleBindStrategy(connectionFactory, bindDNTemplate, schema);
    }

    private AuthenticationStrategy buildSASLBindStrategy(JsonValue config) {
        return newSASLBindStrategy(getConnectionFactory(config.get("ldapConnectionFactory")
                                                              .defaultTo(DEFAULT_BIND_FACTORY).asString()),
                                   config.get("authcIdTemplate").defaultTo("u:%s").asString());
    }

    /**
     * {@link AuthenticationStrategy} performing an LDAP SASL-Plain Bind.
     *
     * @param connectionFactory
     *            The {@link ConnectionFactory} to use to perform the bind operation
     * @param authcIdTemplate
     *            Authentication identity template containing a single %s which will be replaced by the authenticating
     *            user's name. (i.e: (u:%s)
     * @return A new {@link AuthenticationStrategy}
     */
    protected AuthenticationStrategy newSASLBindStrategy(ConnectionFactory connectionFactory, String authcIdTemplate) {
        return new SASLPlainStrategy(connectionFactory, schema, authcIdTemplate);
    }

    private AuthenticationStrategy buildSearchThenBindStrategy(JsonValue config) {
        return newSearchThenBindStrategy(
                getConnectionFactory(
                        config.get("searchLDAPConnectionFactory").defaultTo(DEFAULT_ROOT_FACTORY).asString()),
                getConnectionFactory(
                        config.get("bindLDAPConnectionFactory").defaultTo(DEFAULT_BIND_FACTORY).asString()),
                DN.valueOf(config.get("baseDN").required().asString(), schema),
                SearchScope.valueOf(config.get("scope").required().asString().toLowerCase()),
                config.get("filterTemplate").required().asString());
    }

    /**
     * {@link AuthenticationStrategy} performing an LDAP Search to get a DN to bind with.
     *
     * @param searchConnectionFactory
     *            The {@link ConnectionFactory} to sue to perform the search operation.
     * @param bindConnectionFactory
     *            The {@link ConnectionFactory} to use to perform the bind operation
     * @param baseDN
     *            The base DN of the search request
     * @param scope
     *            {@link SearchScope} of the search request
     * @param filterTemplate
     *            filter template containing a single %s which will be replaced by the authenticating user's name. (i.e:
     *            (&(uid=%s)(objectClass=inetOrgPerson))
     * @return A new {@link AuthenticationStrategy}
     */
    protected AuthenticationStrategy newSearchThenBindStrategy(ConnectionFactory searchConnectionFactory,
            ConnectionFactory bindConnectionFactory, DN baseDN, SearchScope scope, String filterTemplate) {
        return new SearchThenBindStrategy(
                searchConnectionFactory, bindConnectionFactory, baseDN, scope, filterTemplate);
    }
}
