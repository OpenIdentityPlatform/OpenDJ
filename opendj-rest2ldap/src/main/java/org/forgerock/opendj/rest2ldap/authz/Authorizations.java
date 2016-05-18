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

import org.forgerock.http.Filter;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
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
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Factory methods to create {@link Filter} performing authentication and authorizations.
 */
public final class Authorizations {

    private Authorizations() {
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
}
