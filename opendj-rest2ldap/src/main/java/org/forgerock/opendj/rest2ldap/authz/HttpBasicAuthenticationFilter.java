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

import static org.forgerock.opendj.rest2ldap.authz.Utils.asErrorResponse;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.opendj.rest2ldap.authz.Utils.close;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;
import org.forgerock.opendj.rest2ldap.authz.OptionalFilter.ConditionalFilter;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Inject a {@link SecurityContext} if the credentials provided in the {@link Request} headers have been successfully
 * verified.
 */
public final class HttpBasicAuthenticationFilter implements ConditionalFilter {

    private final AuthenticationStrategy authenticationStrategy;
    private final Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor;
    private final boolean reuseAuthenticatedConnection;

    /**
     * Create a new HttpBasicAuthenticationFilter.
     *
     * @param authenticationStrategy
     *            The strategy to use to perform the authentication.
     * @param credentialsExtractor
     *            The function to use to extract credentials from the {@link Headers}.
     * @param reuseAuthenticatedConnection
     *            Let the bound connection open so that it can be reused to perform the LDAP operations.
     * @throws NullPointerException
     *             If a parameter is null.
     */
    public HttpBasicAuthenticationFilter(AuthenticationStrategy authenticationStrategy,
            Function<Headers, Pair<String, String>, NeverThrowsException> credentialsExtractor,
            boolean reuseAuthenticatedConnection) {
        this.authenticationStrategy = checkNotNull(authenticationStrategy, "authenticationStrategy cannot be null");
        this.credentialsExtractor = checkNotNull(credentialsExtractor, "credentialsExtractor cannot be null");
        this.reuseAuthenticatedConnection = reuseAuthenticatedConnection;
    }

    @Override
    public boolean canApplyFilter(Context context, Request request) {
        return credentialsExtractor.apply(request.getHeaders()) != null;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
            final Handler next) {
        final Pair<String, String> credentials = credentialsExtractor.apply(request.getHeaders());
        if (credentials == null) {
            return asErrorResponse(LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS));
        }
        final AtomicReference<Connection> authConnHolder = new AtomicReference<Connection>();
        return authenticationStrategy
                .authenticate(credentials.getFirst(), credentials.getSecond(), context, authConnHolder)
                .thenAsync(new AsyncFunction<SecurityContext, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(final SecurityContext securityContext) {
                        if (reuseAuthenticatedConnection) {
                            return next
                                    .handle(new AuthenticatedConnectionContext(securityContext, authConnHolder.get()),
                                            request);
                        }
                        close(authConnHolder);
                        return next.handle(securityContext, request);
                    }
                }, new AsyncFunction<LdapException, Response, NeverThrowsException>() {
                    @Override
                    public Promise<? extends Response, ? extends NeverThrowsException> apply(LdapException exception) {
                        return asErrorResponse(exception instanceof EntryNotFoundException
                                ? LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS, exception.getMessage())
                                : exception);
                    }
                })
                .thenFinally(close(authConnHolder));
    }

    /** Extract the user's credentials from custom {@link Headers}. */
    public static final class CustomHeaderExtractor
            implements Function<Headers, Pair<String, String>, NeverThrowsException> {

        private final String customHeaderUsername;
        private final String customHeaderPassword;

        /**
         * Create a new CustomHeaderExtractor.
         *
         * @param customHeaderUsername
         *            Name of the header containing the username
         * @param customHeaderPassword
         *            Name of the header containing the password
         * @throws NullPointerException
         *             if a parameter is null.
         */
        public CustomHeaderExtractor(String customHeaderUsername, String customHeaderPassword) {
            this.customHeaderUsername = checkNotNull(customHeaderUsername, "customHeaderUsername cannot be null");
            this.customHeaderPassword = checkNotNull(customHeaderPassword, "customHeaderPassword cannot be null");
        }

        @Override
        public Pair<String, String> apply(Headers headers) {
            final String userName = headers.getFirst(customHeaderUsername);
            final String password = headers.getFirst(customHeaderPassword);
            if (userName != null && password != null) {
                return Pair.of(userName, password);
            }
            return HttpBasicExtractor.INSTANCE.apply(headers);
        }
    }

    /** Extract the user's credentials from the standard HTTP Basic {@link Headers}. */
    public static final class HttpBasicExtractor
            implements Function<Headers, Pair<String, String>, NeverThrowsException> {

        /** HTTP Header sent by the client with HTTP basic authentication. */
        public static final String HTTP_BASIC_AUTH_HEADER = "Authorization";

        /** Reference to the HttpBasicExtractor Singleton. */
        public static final HttpBasicExtractor INSTANCE = new HttpBasicExtractor();

        private HttpBasicExtractor() { }

        @Override
        public Pair<String, String> apply(Headers headers) {
            final String httpBasicAuthHeader = headers.getFirst(HTTP_BASIC_AUTH_HEADER);
            if (httpBasicAuthHeader != null) {
                final Pair<String, String> userCredentials = parseUsernamePassword(httpBasicAuthHeader);
                if (userCredentials != null) {
                    return userCredentials;
                }
            }
            return null;
        }

        private Pair<String, String> parseUsernamePassword(String authHeader) {
            if (authHeader != null && (authHeader.toLowerCase().startsWith("basic"))) {
                // We received authentication info
                // Example received header:
                // "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
                final String base64UserCredentials = authHeader.substring("basic".length() + 1);
                // Example usage of base64:
                // Base64("Aladdin:open sesame") = "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
                final String userCredentials = new String(Base64.decode(base64UserCredentials));
                String[] split = userCredentials.split(":");
                if (split.length == 2) {
                    return Pair.of(split[0], split[1]);
                }
            }
            return null;
        }
    }
}
