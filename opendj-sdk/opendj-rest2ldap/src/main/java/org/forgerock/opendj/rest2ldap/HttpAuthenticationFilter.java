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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.json.resource.ResourceException.newResourceException;
import static org.forgerock.services.context.SecurityContext.AUTHZID_DN;
import static org.forgerock.services.context.SecurityContext.AUTHZID_ID;
import static org.forgerock.opendj.ldap.Connections.uncloseable;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.requests.Requests.newPlainSASLBindRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.util.Utils.closeSilently;

import java.io.Closeable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/** An LDAP based HTTP authentication filter. */
final class HttpAuthenticationFilter implements org.forgerock.http.Filter, Closeable {

    /** Indicates how authentication should be performed. */
    private enum AuthenticationMethod {
        SASL_PLAIN,
        SEARCH_SIMPLE,
        SIMPLE
    }

    private final Schema schema = Schema.getDefaultSchema();
    private final String altAuthenticationPasswordHeader;
    private final String altAuthenticationUsernameHeader;
    private final AuthenticationMethod authenticationMethod;
    private final ConnectionFactory bindLDAPConnectionFactory;
    private final boolean reuseAuthenticatedConnection;
    private final String saslAuthzIdTemplate;
    private final DN searchBaseDN;
    private final String searchFilterTemplate;
    private final ConnectionFactory searchLDAPConnectionFactory;
    private final SearchScope searchScope;
    private final boolean supportAltAuthentication;

    private final boolean supportHTTPBasicAuthentication;

    HttpAuthenticationFilter(final JsonValue configuration) {
        // Parse the authentication configuration.
        final JsonValue authnConfig = configuration.get("authenticationFilter");
        supportHTTPBasicAuthentication = authnConfig.get("supportHTTPBasicAuthentication").required().asBoolean();

        // Alternative HTTP authentication.
        supportAltAuthentication = authnConfig.get("supportAltAuthentication").required().asBoolean();
        if (supportAltAuthentication) {
            altAuthenticationUsernameHeader = authnConfig.get("altAuthenticationUsernameHeader").required().asString();
            altAuthenticationPasswordHeader = authnConfig.get("altAuthenticationPasswordHeader").required().asString();
        } else {
            altAuthenticationUsernameHeader = null;
            altAuthenticationPasswordHeader = null;
        }

        // Should the authenticated connection should be cached for use by subsequent LDAP operations?
        reuseAuthenticatedConnection = authnConfig.get("reuseAuthenticatedConnection").required().asBoolean();

        // Parse the authentication method and associated parameters.
        authenticationMethod = parseAuthenticationMethod(authnConfig);
        switch (authenticationMethod) {
        case SASL_PLAIN:
            saslAuthzIdTemplate = authnConfig.get("saslAuthzIdTemplate").required().asString();
            searchBaseDN = null;
            searchScope = null;
            searchFilterTemplate = null;
            searchLDAPConnectionFactory = null;
            break;
        case SEARCH_SIMPLE:
            searchBaseDN = DN.valueOf(authnConfig.get("searchBaseDN").required().asString(), schema);
            searchScope = parseSearchScope(authnConfig);
            searchFilterTemplate = authnConfig.get("searchFilterTemplate").required().asString();

            // Parse the LDAP connection factory to be used for searches.
            final String ldapFactoryName = authnConfig.get("searchLDAPConnectionFactory").required().asString();
            searchLDAPConnectionFactory = Rest2LDAP
                .configureConnectionFactory(configuration.get("ldapConnectionFactories").required(), ldapFactoryName);

            saslAuthzIdTemplate = null;
            break;
        case SIMPLE:
        default:
            saslAuthzIdTemplate = null;
            searchBaseDN = null;
            searchScope = null;
            searchFilterTemplate = null;
            searchLDAPConnectionFactory = null;
            break;
        }

        // Parse the LDAP connection factory to be used for binds.
        final String ldapFactoryName = authnConfig.get("bindLDAPConnectionFactory").required().asString();
        bindLDAPConnectionFactory = Rest2LDAP.configureConnectionFactory(
            configuration.get("ldapConnectionFactories").required(), ldapFactoryName);
    }

    private static AuthenticationMethod parseAuthenticationMethod(final JsonValue configuration) {
        if (configuration.isDefined("method")) {
            final String method = configuration.get("method").asString();
            if ("simple".equalsIgnoreCase(method)) {
                return AuthenticationMethod.SIMPLE;
            } else if ("sasl-plain".equalsIgnoreCase(method)) {
                return AuthenticationMethod.SASL_PLAIN;
            } else if ("search-simple".equalsIgnoreCase(method)) {
                return AuthenticationMethod.SEARCH_SIMPLE;
            } else {
                throw new JsonValueException(configuration,
                    "Illegal authentication method: must be either 'simple', 'sasl-plain', or 'search-simple'");
            }
        } else {
            return AuthenticationMethod.SEARCH_SIMPLE;
        }
    }

    private static SearchScope parseSearchScope(final JsonValue configuration) {
        if (configuration.isDefined("searchScope")) {
            final String scope = configuration.get("searchScope").asString();
            if ("sub".equalsIgnoreCase(scope)) {
                return SearchScope.WHOLE_SUBTREE;
            } else if ("one".equalsIgnoreCase(scope)) {
                return SearchScope.SINGLE_LEVEL;
            } else {
                throw new JsonValueException(configuration, "Illegal search scope: must be either 'sub' or 'one'");
            }
        } else {
            return SearchScope.WHOLE_SUBTREE;
        }
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
                                                          final Handler next) {
        // Store the authenticated connection so that it can be re-used by the handler if needed.
        // However, make sure that it is closed on completion.
        try {
            final String headerUsername =
                supportAltAuthentication ? request.getHeaders().getFirst(altAuthenticationUsernameHeader) : null;
            final String headerPassword =
                supportAltAuthentication ? request.getHeaders().getFirst(altAuthenticationPasswordHeader) : null;
            final String headerAuthorization =
                supportHTTPBasicAuthentication ? request.getHeaders().getFirst("Authorization") : null;

            final String username;
            final char[] password;
            if (headerUsername != null) {
                if (headerPassword == null || headerUsername.isEmpty() || headerPassword.isEmpty()) {
                    throw newResourceException(401);
                }
                username = headerUsername;
                password = headerPassword.toCharArray();
            } else if (headerAuthorization != null) {
                final StringTokenizer st = new StringTokenizer(headerAuthorization);
                final String method = st.nextToken();
                if (method == null || !"BASIC".equalsIgnoreCase(method)) {
                    throw newResourceException(401);
                }
                final String b64Credentials = st.nextToken();
                if (b64Credentials == null) {
                    throw newResourceException(401);
                }
                final String credentials = ByteString.valueOfBase64(b64Credentials).toString();
                final String[] usernameAndPassword = credentials.split(":");
                if (usernameAndPassword.length != 2) {
                    throw newResourceException(401);
                }
                username = usernameAndPassword[0];
                password = usernameAndPassword[1].toCharArray();
            } else {
                throw newResourceException(401);
            }

            // If we've got here then we have a username and password.
            switch (authenticationMethod) {
            case SIMPLE: {
                final Map<String, Object> authzid;
                authzid = new LinkedHashMap<>(2);
                authzid.put(AUTHZID_DN, username);
                authzid.put(AUTHZID_ID, username);
                return doBind(
                    context, request, next, Requests.newSimpleBindRequest(username, password), username, authzid);
            }
            case SASL_PLAIN: {
                final Map<String, Object> authzid;
                final String bindId;
                if (saslAuthzIdTemplate.startsWith("dn:")) {
                    final String bindDN = DN.format(saslAuthzIdTemplate.substring(3), schema, username).toString();
                    bindId = "dn:" + bindDN;
                    authzid = new LinkedHashMap<>(2);
                    authzid.put(AUTHZID_DN, bindDN);
                    authzid.put(AUTHZID_ID, username);
                } else {
                    bindId = String.format(saslAuthzIdTemplate, username);
                    authzid = Collections.singletonMap(AUTHZID_ID, (Object) username);
                }
                return doBind(context, request, next, newPlainSASLBindRequest(bindId, password), username, authzid);
            }
            default: // SEARCH_SIMPLE
                final AtomicReference<Connection> savedConnection = new AtomicReference<>();
                return searchLDAPConnectionFactory.getConnectionAsync()
                    .thenAsync(doSearchForUser(username, savedConnection))
                    .thenAsync(doBindAfterSearch(context, request, next, username, password, savedConnection),
                        returnErrorAfterFailedSearch(savedConnection));
            }
        } catch (final Throwable t) {
            return asErrorResponse(t);
        }
    }

    private AsyncFunction<Connection, SearchResultEntry, LdapException> doSearchForUser(
            final String username, final AtomicReference<Connection> savedConnection) {
        return new AsyncFunction<Connection, SearchResultEntry, LdapException>() {
            @Override
            public Promise<SearchResultEntry, LdapException> apply(final Connection connection) throws LdapException {
                savedConnection.set(connection);
                final Filter filter = Filter.format(searchFilterTemplate, username);
                return connection.searchSingleEntryAsync(newSearchRequest(searchBaseDN, searchScope, filter, "1.1"));
            }
        };
    }

    private AsyncFunction<SearchResultEntry, Response, NeverThrowsException> doBindAfterSearch(
            final Context context, final Request request, final Handler next, final String username,
            final char[] password, final AtomicReference<Connection> savedConnection) {
        return new AsyncFunction<SearchResultEntry, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final SearchResultEntry entry) {
                closeConnection(savedConnection);
                final String bindDN = entry.getName().toString();
                final Map<String, Object> authzid = new LinkedHashMap<>(2);
                authzid.put(AUTHZID_DN, bindDN);
                authzid.put(AUTHZID_ID, username);
                return doBind(context, request, next, newSimpleBindRequest(bindDN, password), username, authzid);
            }
        };
    }

    /**
     * Get a bind connection and then perform the bind operation, setting the cached connection and authorization
     * credentials on completion.
     */
    private Promise<Response, NeverThrowsException> doBind(
            final Context context, final Request request, final Handler next, final BindRequest bindRequest,
            final String authcid, final Map<String, Object> authzid) {
        final AtomicReference<Connection> savedConnection = new AtomicReference<>();
        return bindLDAPConnectionFactory.getConnectionAsync()
            .thenAsync(new AsyncFunction<Connection, BindResult, LdapException>() {
                @Override
                public Promise<BindResult, LdapException> apply(final Connection connection) throws LdapException {
                    savedConnection.set(connection);
                    return connection.bindAsync(bindRequest);
                }
            })
            .thenAsync(doChain(context, request, next, authcid, authzid, savedConnection),
                       returnErrorAfterFailedBind())
            .thenFinally(new Runnable() {
                @Override
                public void run() {
                    closeConnection(savedConnection);
                }
            });
    }

    private AsyncFunction<BindResult, Response, NeverThrowsException> doChain(
            final Context context, final Request request, final Handler next, final String authcid,
            final Map<String, Object> authzid, final AtomicReference<Connection> savedConnection) {
        return new AsyncFunction<BindResult, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final BindResult result) {
                // Pass through the authentication ID and authorization principals.
                Context forwardedContext = new SecurityContext(context, authcid, authzid);

                // Cache the pre-authenticated connection and prevent downstream
                // components from closing it since this filter will close it.
                if (reuseAuthenticatedConnection) {
                    forwardedContext = new AuthenticatedConnectionContext(
                            forwardedContext, uncloseable(savedConnection.get()));
                }

                return next.handle(forwardedContext, request);
            }
        };
    }

    private AsyncFunction<LdapException, Response, NeverThrowsException> returnErrorAfterFailedSearch(
            final AtomicReference<Connection> savedConnection) {
        return new AsyncFunction<LdapException, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final LdapException e) {
                if (closeConnection(savedConnection)) {
                    // The search error should not be passed as-is back to the user.
                    if (e instanceof EntryNotFoundException || e instanceof MultipleEntriesFoundException) {
                        return asErrorResponse(newLdapException(ResultCode.INVALID_CREDENTIALS, e));
                    } else if (e instanceof AuthenticationException || e instanceof AuthorizationException) {
                        return asErrorResponse(newLdapException(ResultCode.CLIENT_SIDE_LOCAL_ERROR, e));
                    } else {
                        return asErrorResponse(e);
                    }
                } else {
                    return asErrorResponse(e);
                }
            }
        };
    }

    private AsyncFunction<LdapException, Response, NeverThrowsException> returnErrorAfterFailedBind() {
        return new AsyncFunction<LdapException, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final LdapException e) {
                return asErrorResponse(e);
            }
        };
    }

    private Promise<Response, NeverThrowsException> asErrorResponse(final Throwable t) {
        final ResourceException e = asResourceException(t);
        final Response response =
            new Response().setStatus(Status.valueOf(e.getCode())).setEntity(e.toJsonValue().getObject());
        return Promises.newResultPromise(response);
    }

    private boolean closeConnection(final AtomicReference<Connection> savedConnection) {
        final Connection connection = savedConnection.get();
        if (connection != null) {
            connection.close();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        closeSilently(searchLDAPConnectionFactory, bindLDAPConnectionFactory);
    }
}
