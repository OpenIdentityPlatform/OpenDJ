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

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.authz.Utils.asErrorResponse;
import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Inject a {@link SecurityContext} if the credentials provided in the {@link Request} headers have been successfully
 * verified.
 */
final class HttpBasicAuthenticationFilter implements Filter {

    private final AuthenticationStrategy authenticationStrategy;
    private final Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor;

    /**
     * Create a new HttpBasicAuthenticationFilter.
     *
     * @param authenticationStrategy
     *            The strategy to use to perform the authentication.
     * @param credentialsExtractor
     *            The function to use to extract credentials from the {@link Headers}.
     * @throws NullPointerException
     *             If a parameter is null.
     */
    public HttpBasicAuthenticationFilter(AuthenticationStrategy authenticationStrategy,
            Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor) {
        this.authenticationStrategy = checkNotNull(authenticationStrategy, "authenticationStrategy cannot be null");
        this.credentialsExtractor = checkNotNull(credentialsExtractor, "credentialsExtractor cannot be null");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
            final Handler next) {
        final Pair<String, String> credentials = credentialsExtractor.apply(request.getHeaders());
        if (credentials == null) {
            return asErrorResponse(LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS));
        }
        return authenticationStrategy
                .authenticate(credentials.getFirst(), credentials.getSecond(), context)
                .thenAsync(new AsyncFunction<SecurityContext, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(final SecurityContext securityContext) {
                        return next.handle(securityContext, request);
                    }
                }, new AsyncFunction<LdapException, Response, NeverThrowsException>() {
                    @Override
                    public Promise<? extends Response, ? extends NeverThrowsException> apply(LdapException exception) {
                        return asErrorResponse(exception instanceof EntryNotFoundException
                                ? LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS)
                                : exception);
                    }
                });
    }
}
