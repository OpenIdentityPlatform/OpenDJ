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

import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.protocol.Headers;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Factory method for function extracting credentials from HTTP request {@link Headers}.
 */
public final class CredentialExtractors {

    /** HTTP Header sent by the client with HTTP basic authentication. */
    public static final String HTTP_BASIC_AUTH_HEADER = "Authorization";

    private CredentialExtractors() {
    }

    /**
     * Creates a function which extracts the user's credentials from the standard HTTP Basic header.
     *
     * @return the basic extractor singleton
     */
    public static Function<Headers, Pair<String, String>, NeverThrowsException> httpBasicExtractor() {
        return HttpBasicExtractor.INSTANCE;
    }

    /**
     * Creates a function which extracts the user's credentials from custom HTTP header in addition of the standard HTTP
     * Basic one.
     *
     * @param customHeaderUsername
     *            Name of the additional header to check for the user's name
     * @param customHeaderPassword
     *            Name of the additional header to check for the user's password
     * @return A new credentials extractors looking for custom header.
     */
    public static Function<Headers, Pair<String, String>, NeverThrowsException> newCustomHeaderExtractor(
            String customHeaderUsername, String customHeaderPassword) {
        return new CustomHeaderExtractor(customHeaderUsername, customHeaderPassword);
    }

    /** Extract the user's credentials from custom {@link Headers}. */
    private static final class CustomHeaderExtractor
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
    private static final class HttpBasicExtractor
            implements Function<Headers, Pair<String, String>, NeverThrowsException> {

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
