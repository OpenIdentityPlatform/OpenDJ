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
import static org.forgerock.opendj.ldap.requests.Requests.newSingleEntrySearchRequest;
import static org.forgerock.opendj.rest2ldap.authz.Utils.close;
import static org.forgerock.opendj.rest2ldap.authz.Utils.newAccessTokenException;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenException;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * This class resolves an access token in order to get the {@link AccessTokenInfo}
 * by performing a request to an OpenDJ server.
 */
final class CtsAccessTokenResolver implements AccessTokenResolver {

    private static final Filter FR_CORE_TOKEN_OC_FILTER = Filter.equality("objectclass", "frCoreToken");

    private final ConnectionFactory connectionFactory;
    private final DN ctsBaseDN;

    CtsAccessTokenResolver(final ConnectionFactory connectionFactory, final String ctsBaseDN) {
        this.connectionFactory = checkNotNull(connectionFactory, "connectionFactory cannot be null");
        this.ctsBaseDN = DN.valueOf(checkNotNull(ctsBaseDN, "ctsBaseDN cannot be null"));
    }

    @Override
    public Promise<AccessTokenInfo, AccessTokenException> resolve(final Context context, final String token) {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();

        return connectionFactory
            .getConnectionAsync()
            .thenAsync(new AsyncFunction<Connection, SearchResultEntry, LdapException>() {
                @Override
                public Promise<SearchResultEntry, LdapException> apply(final Connection connection)
                        throws LdapException {
                    connectionHolder.set(connection);
                    return connection.searchSingleEntryAsync(newSingleEntrySearchRequest(
                            ctsBaseDN.child("coreTokenId", token),
                            SearchScope.BASE_OBJECT, FR_CORE_TOKEN_OC_FILTER, "coreTokenObject"));
                }
            }).then(new Function<SearchResultEntry, AccessTokenInfo, AccessTokenException>() {
                @Override
                public AccessTokenInfo apply(final SearchResultEntry entry) throws AccessTokenException {
                    final JsonValue accessToken = parseJson(
                            entry.getAttribute("coreTokenObject").firstValueAsString(), token);

                    final String tokenName = getRequiredFirstValue(accessToken.get("tokenName"));
                    if (!tokenName.equals("access_token")) {
                        throw newAccessTokenException(ERR_OAUTH2_CTS_INVALID_TOKEN_TYPE.get(token, tokenName));
                    }

                    return new AccessTokenInfo(accessToken, token,
                            accessToken.get("scope").required().asSet(String.class),
                            Long.parseLong(getRequiredFirstValue(accessToken.get("expireTime"))));
                }
            }, new Function<LdapException, AccessTokenInfo, AccessTokenException>() {
                @Override
                public AccessTokenInfo apply(final LdapException e) throws AccessTokenException {
                    throw newAccessTokenException(ERR_OAUTH2_CTS_TOKEN_NOT_FOUND.get(token, e.getMessage()), e);
                }
            }).thenCatchRuntimeException(new Function<RuntimeException, AccessTokenInfo, AccessTokenException>() {
                @Override
                public AccessTokenInfo apply(final RuntimeException e) throws AccessTokenException {
                    throw newAccessTokenException(ERR_OAUTH2_CTS_TOKEN_RESOLUTION.get(token, e.getMessage()), e);
                }
            }).thenFinally(close(connectionHolder));
    }

    private String getRequiredFirstValue(final JsonValue list) {
        return list.required().asList(String.class).get(0);
    }

    private JsonValue parseJson(final String accessTokenJson, final String token) throws AccessTokenException {
        try {
            return new JsonValue(Json.readJson(accessTokenJson));
        } catch (final IOException e) {
            throw newAccessTokenException(ERR_OAUTH2_CTS_INVALID_JSON_TOKEN.get(token));
        }
    }
}
