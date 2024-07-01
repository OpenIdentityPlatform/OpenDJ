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
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.opendj.rest2ldap.authz.Utils.newAccessTokenException;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.http.Handler;
import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.AccessTokenResolver;
import org.forgerock.http.protocol.Responses;
import org.forgerock.http.protocol.Entity;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.Promise;

/**
 * An {@link AccessTokenResolver} which is RFC 7662 compliant.
 * <p>
 * @see <a href="https://tools.ietf.org/html/rfc7662">RFC-7662</a>
 */
final class Rfc7662AccessTokenResolver implements AccessTokenResolver {
    /** RFC 7662 defined fields for token introspection request. */
    private static final String RFC_7662_FORM_TOKEN_FIELD = "token";
    private static final String RFC_7662_FORM_TOKEN_TYPE_HINT_FIELD = "token_type_hint";
    private static final String RFC_7662_FORM_TOKEN_TYPE_HINT_ACCESS_TOKEN = "access_token";

    /** RFC 7662 defined fields for token introspection response. */
    private static final String RFC_7662_RESPONSE_SCOPE_FIELD = "scope";
    private static final String RFC_7662_RESPONSE_EXPIRE_TIME_FIELD = "exp";

    /** Rest2Ldap fields name for the RFC 7662 access token resolver. */
    private static final String RFC_7662_RESPONSE_ACTIVE_FIELD = "active";

    private final Handler httpClient;
    private final URI introspectionEndPointURI;
    private final String clientAppId;
    private final String clientAppSecret;

    Rfc7662AccessTokenResolver(final Handler httpClient,
                               final URI introspectionEndPointURI,
                               final String clientAppId,
                               final String clientAppSecret) {
        this.httpClient = checkNotNull(httpClient);
        this.introspectionEndPointURI = checkNotNull(introspectionEndPointURI);
        this.clientAppId = checkNotNull(clientAppId);
        this.clientAppSecret = checkNotNull(clientAppSecret);
    }

    @Override
    public Promise<AccessTokenInfo, AccessTokenException> resolve(final Context context, final String token) {
        final Request request = new Request().setUri(introspectionEndPointURI);

        final Headers headers = request.getHeaders();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + Base64.encode((clientAppId + ":" + clientAppSecret).getBytes()));

        final Form form = new Form();
        form.add(RFC_7662_FORM_TOKEN_FIELD, token);
        form.add(RFC_7662_FORM_TOKEN_TYPE_HINT_FIELD, RFC_7662_FORM_TOKEN_TYPE_HINT_ACCESS_TOKEN);
        form.toRequestEntity(request);

        return httpClient.handle(context, request)
                         .then(buildAccessToken(token),
                               Responses.<AccessTokenInfo, AccessTokenException> noopExceptionFunction());
    }

    private Function<Response, AccessTokenInfo, AccessTokenException> buildAccessToken(final String tokenSent) {
        return new Function<Response, AccessTokenInfo, AccessTokenException>() {
            @Override
            public AccessTokenInfo apply(final Response response) throws AccessTokenException {
                final Status status = response.getStatus();
                if (!Status.OK.equals(status)) {
                    throw newAccessTokenException(
                            ERR_OAUTH2_RFC7662_RETURNED_ERROR.get(status), response.getCause());
                }

                try (final Entity entity = response.getEntity()) {
                    final JsonValue jsonResponse = asJson(entity);
                    if (!jsonResponse.get(RFC_7662_RESPONSE_ACTIVE_FIELD).defaultTo(Boolean.FALSE).asBoolean()) {
                        throw newAccessTokenException(ERR_OAUTH2_RFC7662_TOKEN_NOT_ACTIVE.get());
                    }
                    return buildAccessTokenFromJson(jsonResponse, tokenSent);
                } catch (final JsonValueException e) {
                    throw newAccessTokenException(ERR_OAUTH2_RFC7662_INVALID_JSON_TOKEN.get(e.getMessage()), e);
                }
            }
        };
    }

    private AccessTokenInfo buildAccessTokenFromJson(final JsonValue jsonToken, final String token) {
        final Set<String> tokenScopes = new HashSet<>(Arrays.asList(
                jsonToken.get(RFC_7662_RESPONSE_SCOPE_FIELD).required().asString().trim().split(" +")));
        final Long expiresAt = jsonToken.get(RFC_7662_RESPONSE_EXPIRE_TIME_FIELD).required().asLong();
        return new AccessTokenInfo(jsonToken, token, tokenScopes, SECONDS.toMillis(expiresAt));
    }

    private JsonValue asJson(final Entity entity) throws AccessTokenException {
        try {
            return new JsonValue(entity.getJson());
        } catch (final IOException e) {
            // Do not use Entity.toString(), we probably don't want to fully output the content here
            throw newAccessTokenException(ERR_OAUTH2_RFC7662_CANNOT_READ_RESPONSE.get(), e);
        }
    }
}
