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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.asConditionalFilter;
import static org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.newConditionalFilter;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.openig.oauth2.OAuth2Context;
import org.forgerock.openig.oauth2.ResourceAccess;
import org.forgerock.openig.oauth2.ResourceServerFilter;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.Filters;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.Condition;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.TimeService;

/** Factory methods to create {@link Filter} performing authentication and authorizations. */
public final class Authorization {

    private static final String OAUTH2_AUTHORIZATION_HEADER = "Authorization";

    /**
     * Creates a new {@link Filter} in charge of injecting an {@link AuthenticatedConnectionContext}. This
     * {@link Filter} tries each of the provided filters until one can apply. If no filter can be applied, the last
     * filter in the list will be applied allowing it to formulate a valid, implementation specific, error response.
     *
     * @param filters
     *            {@link Iterable} of authorization {@link ConditionalFilters} to try. If empty, the returned filter
     *            will always respond with 403 Forbidden.
     * @return A new authorization {@link Filter}
     */
    public static Filter newAuthorizationFilter(Iterable<? extends ConditionalFilter> filters) {
        return new AuthorizationFilter(filters);
    }

    /**
     * Creates a new {@link ConditionalFilter} performing authentication. If authentication succeed, it injects a
     * {@link SecurityContext} with the authenticationId provided by the user. Otherwise, returns a HTTP 401 -
     * Unauthorized response. The condition of this {@link ConditionalFilter} will return true if the supplied requests
     * contains credentials information, false otherwise.
     *
     * @param authenticationStrategy
     *            {@link AuthenticationStrategy} to validate the user's provided credentials.
     * @param credentialsExtractor
     *            Function to extract the credentials from the received request.
     * @throws NullPointerException
     *             if a parameter is null.
     * @return a new {@link ConditionalFilter}
     */
    public static ConditionalFilter newConditionalHttpBasicAuthenticationFilter(
            final AuthenticationStrategy authenticationStrategy,
            final Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor) {
        return newConditionalFilter(
                new HttpBasicAuthenticationFilter(authenticationStrategy, credentialsExtractor),
                new Condition() {
                    @Override
                    public boolean canApplyFilter(Context context, Request request) {
                        return credentialsExtractor.apply(request.getHeaders()) != null;
                    }
                });
    }

    /**
     * Creates a {@link ConditionalFilter} injecting an {@link AuthenticatedConnectionContext} with a connection issued
     * from the given connectionFactory. The condition is always true.
     *
     * @param connectionFactory
     *            The factory used to get the {@link Connection} to inject.
     * @return A new {@link ConditionalFilter}.
     * @throws NullPointerException
     *             if connectionFactory is null
     */
    public static ConditionalFilter newConditionalDirectConnectionFilter(ConnectionFactory connectionFactory) {
        return asConditionalFilter(new DirectConnectionFilter(connectionFactory));
    }

    /**
     * Creates a filter injecting an {@link AuthenticatedConnectionContext} given the information provided in the
     * {@link SecurityContext}. The connection contained in the created {@link AuthenticatedConnectionContext} will add
     * a {@link ProxiedAuthV2RequestControl} to each LDAP requests.
     *
     * @param connectionFactory
     *            The connection factory used to create the connection which will be injected in the
     *            {@link AuthenticatedConnectionContext}
     * @return A new filter.
     * @throws NullPointerException
     *             if connectionFactory is null
     */
    public static Filter newProxyAuthorizationFilter(ConnectionFactory connectionFactory) {
        return new ProxiedAuthV2Filter(connectionFactory);
    }

    /**
     * Creates a new {@link AccessTokenResolver} as defined in the RFC-7662.
     * <p>
     * @see <a href="https://tools.ietf.org/html/rfc7662">RFC-7662</a>
     *
     * @param httpClient
     *          Http client handler used to perform the request
     * @param introspectionEndPointURL
     *          Introspect endpoint URL to use to resolve the access token.
     * @param clientAppId
     *          Client application id to use in HTTP Basic authentication header.
     * @param clientAppSecret
     *          Client application secret to use in HTTP Basic authentication header.
     * @return A new {@link AccessTokenResolver} instance.
     */
    public static AccessTokenResolver newRfc7662AccessTokenResolver(final Handler httpClient,
                                                                    final URI introspectionEndPointURL,
                                                                    final String clientAppId,
                                                                    final String clientAppSecret) {
        return new Rfc7662AccessTokenResolver(httpClient, introspectionEndPointURL, clientAppId, clientAppSecret);
    }

    /**
     * Creates a new CTS access token resolver.
     *
     * @param connectionFactory
     *          The {@link ConnectionFactory} to use to perform search against the CTS.
     * @param ctsBaseDNTemplate
     *          The base DN template to use to resolve the access token DN.
     * @return A new CTS access token resolver.
     */
    public static AccessTokenResolver newCtsAccessTokenResolver(final ConnectionFactory connectionFactory,
                                                                final String ctsBaseDNTemplate) {
        return new CtsAccessTokenResolver(connectionFactory, ctsBaseDNTemplate);
    }

    /**
     * Creates a new file access token resolver which should only be used for test purpose.
     *
     * @param tokenFolder
     *          The folder where the access token to resolve must be stored.
     * @return A new file access token resolver which should only be used for test purpose.
     */
    public static AccessTokenResolver newFileAccessTokenResolver(final String tokenFolder) {
        return new FileAccessTokenResolver(tokenFolder);
    }

    /**
     * Creates a new OAuth2 authorization filter configured with provided parameters.
     *
     * @param realm
     *          The realm to displays in error responses.
     * @param scopes
     *          Scopes that an access token must have to be access a resource.
     * @param resolver
     *          The {@link AccessTokenResolver} to use to resolve an access token.
     * @param authzIdTemplate
     *          Authorization ID template.
     * @return A new OAuth2 authorization filter configured with provided parameters.
     */
    public static Filter newOAuth2ResourceServerFilter(final String realm,
                                                                  final Set<String> scopes,
                                                                  final AccessTokenResolver resolver,
                                                                  final String authzIdTemplate) {
        return createResourceServerFilter(realm, scopes, resolver, authzIdTemplate);
    }

    /**
     * Creates a new optional OAuth2 authorization filter configured with provided parameters.
     * <p>
     * This filter will be used only if an OAuth2 Authorization header is present in the incoming request.
     *
     * @param realm
     *          The realm to displays in error responses.
     * @param scopes
     *          Scopes that an access token must have to be access a resource.
     * @param resolver
     *          The {@link AccessTokenResolver} to use to resolve an access token.
     * @param authzIdTemplate
     *          Authorization ID template.
     * @return A new OAuth2 authorization filter configured with provided parameters.
     */
    public static ConditionalFilter newConditionalOAuth2ResourceServerFilter(final String realm,
                                                                             final Set<String> scopes,
                                                                             final AccessTokenResolver resolver,
                                                                             final String authzIdTemplate) {
        return new ConditionalFilter() {
            @Override
            public Filter getFilter() {
                return createResourceServerFilter(realm, scopes, resolver, authzIdTemplate);
            }

            @Override
            public Condition getCondition() {
                return new Condition() {
                    @Override
                    public boolean canApplyFilter(final Context context, final Request request) {
                        return request.getHeaders().containsKey(OAUTH2_AUTHORIZATION_HEADER);
                    }
                };
            }
        };
    }

    private static Filter createResourceServerFilter(final String realm,
                                                     final Set<String> scopes,
                                                     final AccessTokenResolver resolver,
                                                     final String authzIdTemplate) {
        Reject.ifTrue(realm == null || realm.isEmpty(), "realm must not be empty");
        Reject.ifNull(resolver, "Access token resolver must not be null");
        Reject.ifTrue(scopes == null || scopes.isEmpty(), "scopes set can not be empty");
        Reject.ifTrue(authzIdTemplate == null || authzIdTemplate.isEmpty(), "Authz id template must not be empty");

        final ResourceAccess scopesProvider = new ResourceAccess() {
            @Override
            public Set<String> getRequiredScopes(final Context context, final Request request)
                    throws ResponseException {
                return scopes;
            }
        };

        return Filters.chainOf(new ResourceServerFilter(resolver, TimeService.SYSTEM, scopesProvider, realm),
                               createSecurityContextInjectionFilter(authzIdTemplate));
    }

    private static Filter createSecurityContextInjectionFilter(final String authzIdTemplate) {
        final AuthzIdTemplate template = new AuthzIdTemplate(authzIdTemplate);

        return new Filter() {
            @Override
            public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                  final Request request,
                                                                  final Handler next) {
                final AccessTokenInfo token = context.asContext(OAuth2Context.class).getAccessToken();
                final Map<String, Object> authz = new HashMap<>(1);
                try {
                    authz.put(template.getSecurityContextID(), template.formatAsAuthzId(token.asJsonValue()));
                } catch (final IllegalArgumentException e) {
                    return newResultPromise(new Response().setStatus(Status.INTERNAL_SERVER_ERROR)
                                                          .setCause(e));
                }
                final Context securityContext = new SecurityContext(context, token.getToken(), authz);
                return next.handle(securityContext, request);
            }
        };
    }

    private Authorization() {
        // Prevent instantiation.
    }
}
