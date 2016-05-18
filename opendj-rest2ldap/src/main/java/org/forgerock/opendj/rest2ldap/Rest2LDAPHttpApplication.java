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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
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

    private enum Policy { BASIC, ANONYMOUS }

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
        final Set<Policy> policies = config.get("policies").as(setOf(enumConstant(Policy.class)));
        final ConditionalFilter anonymous =
                policies.contains(Policy.ANONYMOUS) ? buildAnonymousFilter(config.get("anonymous")) : NEVER_APPLICABLE;
        final ConditionalFilter basic =
                policies.contains(Policy.BASIC) ? buildBasicFilter(config.get("basic")) : NEVER_APPLICABLE;
        return new Filter() {
            @Override
            public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
                if (basic.getCondition().canApplyFilter(context, request)) {
                    return basic.getFilter().filter(context, request, next);
                }
                if (anonymous.getCondition().canApplyFilter(context, request)) {
                    return anonymous.getFilter().filter(context, request, next);
                }
                return Response.newResponsePromise(new Response(Status.FORBIDDEN));
            }
        };
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
                                     config.get("bindDNTemplate").defaultTo("%s").asString(),
                                     schema);
    }

    private AuthenticationStrategy buildSASLBindStrategy(JsonValue config) {
        return newSASLPlainStrategy(
                getConnectionFactory(config.get("ldapConnectionFactory").defaultTo(DEFAULT_BIND_FACTORY).asString()),
                schema, config.get("authcIdTemplate").defaultTo("u:%s").asString());
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
}
