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
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap.servlet;

import static org.forgerock.json.resource.SecurityContext.AUTHZID_DN;
import static org.forgerock.json.resource.SecurityContext.AUTHZID_ID;
import static org.forgerock.json.resource.servlet.SecurityContextFactory.ATTRIBUTE_AUTHCID;
import static org.forgerock.json.resource.servlet.SecurityContextFactory.ATTRIBUTE_AUTHZID;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.forgerock.opendj.ldap.requests.Requests.newPlainSASLBindRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.opendj.rest2ldap.servlet.Rest2LDAPContextFactory.ATTRIBUTE_AUTHN_CONNECTION;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.servlet.ServletSynchronizer;
import org.forgerock.json.resource.servlet.ServletApiVersionAdapter;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;

/**
 * An LDAP based authentication Servlet filter.
 * <p>
 * TODO: this is a work in progress. In particular, in order to embed this into
 * the OpenDJ HTTP listener it will need to provide a configuration API.
 */
public final class Rest2LDAPAuthnFilter implements Filter {
    /** Indicates how authentication should be performed. */
    private static enum AuthenticationMethod {
        SASL_PLAIN, SEARCH_SIMPLE, SIMPLE;
    }

    private static final String INIT_PARAM_CONFIG_FILE = "config-file";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper().configure(
            JsonParser.Feature.ALLOW_COMMENTS, true);

    private String altAuthenticationPasswordHeader;
    private String altAuthenticationUsernameHeader;
    private AuthenticationMethod authenticationMethod = AuthenticationMethod.SEARCH_SIMPLE;
    private ConnectionFactory bindLDAPConnectionFactory;
    /** Indicates whether or not authentication should be performed. */
    private boolean isEnabled = false;
    private boolean reuseAuthenticatedConnection = true;
    private String saslAuthzIdTemplate;
    private final Schema schema = Schema.getDefaultSchema();
    private DN searchBaseDN;
    private String searchFilterTemplate;
    private ConnectionFactory searchLDAPConnectionFactory;
    private SearchScope searchScope = SearchScope.WHOLE_SUBTREE;
    private boolean supportAltAuthentication;
    private boolean supportHTTPBasicAuthentication = true;
    private ServletApiVersionAdapter syncFactory;

    @Override
    public void destroy() {
        if (searchLDAPConnectionFactory != null) {
            searchLDAPConnectionFactory.close();
        }
        if (bindLDAPConnectionFactory != null) {
            bindLDAPConnectionFactory.close();
        }
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response,
            final FilterChain chain) throws IOException, ServletException {
        // Skip this filter if authentication has not been configured.
        if (!isEnabled) {
            chain.doFilter(request, response);
            return;
        }

        // First of all parse the HTTP headers for authentication credentials.
        if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
            // This should never happen.
            throw new ServletException("non-HTTP request or response");
        }

        // TODO: support logout, sessions, reauth?
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse res = (HttpServletResponse) response;

        /*
         * Store the authenticated connection so that it can be re-used by the
         * servlet if needed. However, make sure that it is closed on
         * completion.
         */
        final AtomicReference<Connection> savedConnection = new AtomicReference<Connection>();
        final ServletSynchronizer sync = syncFactory.createServletSynchronizer(req, res);

        sync.addAsyncListener(new Runnable() {
            @Override
            public void run() {
                closeConnection(savedConnection);
            }
        });

        try {
            final String headerUsername =
                    supportAltAuthentication ? req.getHeader(altAuthenticationUsernameHeader)
                            : null;
            final String headerPassword =
                    supportAltAuthentication ? req.getHeader(altAuthenticationPasswordHeader)
                            : null;
            final String headerAuthorization =
                    supportHTTPBasicAuthentication ? req.getHeader("Authorization") : null;

            final String username;
            final char[] password;
            if (headerUsername != null) {
                if (headerPassword == null || headerUsername.isEmpty() || headerPassword.isEmpty()) {
                    throw ResourceException.getException(401);
                }
                username = headerUsername;
                password = headerPassword.toCharArray();
            } else if (headerAuthorization != null) {
                final StringTokenizer st = new StringTokenizer(headerAuthorization);
                final String method = st.nextToken();
                if (method == null || !method.equalsIgnoreCase(HttpServletRequest.BASIC_AUTH)) {
                    throw ResourceException.getException(401);
                }
                final String b64Credentials = st.nextToken();
                if (b64Credentials == null) {
                    throw ResourceException.getException(401);
                }
                final String credentials = ByteString.valueOfBase64(b64Credentials).toString();
                final String[] usernameAndPassword = credentials.split(":");
                if (usernameAndPassword.length != 2) {
                    throw ResourceException.getException(401);
                }
                username = usernameAndPassword[0];
                password = usernameAndPassword[1].toCharArray();
            } else {
                throw ResourceException.getException(401);
            }

            // If we've got here then we have a username and password.
            switch (authenticationMethod) {
            case SIMPLE: {
                final Map<String, Object> authzid;
                authzid = new LinkedHashMap<String, Object>(2);
                authzid.put(AUTHZID_DN, username);
                authzid.put(AUTHZID_ID, username);
                doBind(req, res, newSimpleBindRequest(username, password), chain, savedConnection,
                        sync, username, authzid);
                break;
            }
            case SASL_PLAIN: {
                final Map<String, Object> authzid;
                final String bindId;
                if (saslAuthzIdTemplate.startsWith("dn:")) {
                    final String bindDN =
                            DN.format(saslAuthzIdTemplate.substring(3), schema, username)
                                    .toString();
                    bindId = "dn:" + bindDN;
                    authzid = new LinkedHashMap<String, Object>(2);
                    authzid.put(AUTHZID_DN, bindDN);
                    authzid.put(AUTHZID_ID, username);
                } else {
                    bindId = String.format(saslAuthzIdTemplate, username);
                    authzid = Collections.singletonMap(AUTHZID_ID, (Object) username);
                }
                doBind(req, res, newPlainSASLBindRequest(bindId, password), chain, savedConnection,
                        sync, username, authzid);
                break;
            }
            default: // SEARCH_SIMPLE
            {
                /*
                 * First do a search to find the user's entry and then perform a
                 * bind request using the user's DN.
                 */
                final org.forgerock.opendj.ldap.Filter filter =
                        org.forgerock.opendj.ldap.Filter.format(searchFilterTemplate, username);
                final SearchRequest searchRequest =
                        newSearchRequest(searchBaseDN, searchScope, filter, "1.1");
                searchLDAPConnectionFactory.getConnectionAsync(new ResultHandler<Connection>() {
                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        sync.signalAndComplete(asResourceException(error));
                    }

                    @Override
                    public void handleResult(final Connection connection) {
                        // Do the search.
                        connection.searchSingleEntryAsync(searchRequest,
                                new ResultHandler<SearchResultEntry>() {

                                    @Override
                                    public void handleErrorResult(final ErrorResultException error) {
                                        connection.close();
                                        /*
                                         * The search error should not be passed
                                         * as-is back to the user.
                                         */
                                        final ErrorResultException normalizedError;
                                        if (error instanceof EntryNotFoundException
                                                || error instanceof MultipleEntriesFoundException) {
                                            normalizedError =
                                                    newErrorResult(ResultCode.INVALID_CREDENTIALS,
                                                            error);
                                        } else if (error instanceof AuthenticationException
                                                || error instanceof AuthorizationException) {
                                            normalizedError =
                                                    newErrorResult(
                                                            ResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                                            error);
                                        } else {
                                            normalizedError = error;
                                        }
                                        sync.signalAndComplete(asResourceException(normalizedError));
                                    }

                                    @Override
                                    public void handleResult(final SearchResultEntry result) {
                                        connection.close();
                                        final String bindDN = result.getName().toString();
                                        final Map<String, Object> authzid =
                                                new LinkedHashMap<String, Object>(2);
                                        authzid.put(AUTHZID_DN, bindDN);
                                        authzid.put(AUTHZID_ID, username);
                                        doBind(req, res, newSimpleBindRequest(bindDN, password),
                                                chain, savedConnection, sync, username, authzid);
                                    }
                                });
                    }
                });
                break;
            }
            }
            sync.awaitIfNeeded();
            if (!sync.isAsync()) {
                chain.doFilter(request, response);
            }
        } catch (final Throwable t) {
            sync.signalAndComplete(t);
        } finally {
            if (!sync.isAsync()) {
                closeConnection(savedConnection);
            }
        }
    }

    @Override
    public void init(final FilterConfig config) throws ServletException {
        // FIXME: make it possible to configure the filter externally, especially
        // connection factories.
        final String configFileName = config.getInitParameter(INIT_PARAM_CONFIG_FILE);
        if (configFileName == null) {
            throw new ServletException("Authentication filter initialization parameter '"
                    + INIT_PARAM_CONFIG_FILE + "' not specified");
        }
        final InputStream configFile =
                config.getServletContext().getResourceAsStream(configFileName);
        if (configFile == null) {
            throw new ServletException("Servlet filter configuration file '" + configFileName
                    + "' not found");
        }
        try {
            // Parse the config file.
            final Object content = JSON_MAPPER.readValue(configFile, Object.class);
            if (!(content instanceof Map)) {
                throw new ServletException("Servlet filter configuration file '" + configFileName
                        + "' does not contain a valid JSON configuration");
            }

            // Parse the authentication configuration.
            final JsonValue configuration = new JsonValue(content);
            final JsonValue authnConfig = configuration.get("authenticationFilter");
            if (!authnConfig.isNull()) {
                supportHTTPBasicAuthentication =
                        authnConfig.get("supportHTTPBasicAuthentication").required().asBoolean();

                // Alternative HTTP authentication.
                supportAltAuthentication =
                        authnConfig.get("supportAltAuthentication").required().asBoolean();
                if (supportAltAuthentication) {
                    altAuthenticationUsernameHeader =
                            authnConfig.get("altAuthenticationUsernameHeader").required()
                                    .asString();
                    altAuthenticationPasswordHeader =
                            authnConfig.get("altAuthenticationPasswordHeader").required()
                                    .asString();
                }

                // Should the authenticated connection should be cached for use by subsequent LDAP operations?
                reuseAuthenticatedConnection =
                        authnConfig.get("reuseAuthenticatedConnection").required().asBoolean();

                // Parse the authentication method and associated parameters.
                authenticationMethod = parseAuthenticationMethod(authnConfig);
                switch (authenticationMethod) {
                case SIMPLE:
                    // Nothing to do.
                    break;
                case SASL_PLAIN:
                    saslAuthzIdTemplate =
                            authnConfig.get("saslAuthzIdTemplate").required().asString();
                    break;
                case SEARCH_SIMPLE:
                    searchBaseDN =
                            DN.valueOf(authnConfig.get("searchBaseDN").required().asString(),
                                    schema);
                    searchScope = parseSearchScope(authnConfig);
                    searchFilterTemplate =
                            authnConfig.get("searchFilterTemplate").required().asString();

                    // Parse the LDAP connection factory to be used for searches.
                    final String ldapFactoryName =
                            authnConfig.get("searchLDAPConnectionFactory").required().asString();
                    searchLDAPConnectionFactory =
                            Rest2LDAP.configureConnectionFactory(configuration.get(
                                    "ldapConnectionFactories").required(), ldapFactoryName);
                    break;
                }

                // Parse the LDAP connection factory to be used for binds.
                final String ldapFactoryName =
                        authnConfig.get("bindLDAPConnectionFactory").required().asString();
                bindLDAPConnectionFactory =
                        Rest2LDAP.configureConnectionFactory(configuration.get(
                                "ldapConnectionFactories").required(), ldapFactoryName);

                // Set the completion handler factory based on the Servlet API version.
                syncFactory = ServletApiVersionAdapter.getInstance(config.getServletContext());

                isEnabled = true;
            }
        } catch (final ServletException e) {
            // Rethrow.
            throw e;
        } catch (final Exception e) {
            throw new ServletException("Servlet filter configuration file '" + configFileName
                    + "' could not be read: " + e.getMessage());
        } finally {
            try {
                configFile.close();
            } catch (final Exception e) {
                // Ignore.
            }
        }
    }

    private void closeConnection(final AtomicReference<Connection> savedConnection) {
        final Connection connection = savedConnection.get();
        if (connection != null) {
            connection.close();
        }
    }

    /*
     * Get a bind connection and then perform the bind operation, setting the
     * cached connection and authorization credentials on completion.
     */
    private void doBind(final HttpServletRequest request, final ServletResponse response,
            final BindRequest bindRequest, final FilterChain chain,
            final AtomicReference<Connection> savedConnection, final ServletSynchronizer sync,
            final String authcid, final Map<String, Object> authzid) {
        bindLDAPConnectionFactory.getConnectionAsync(new ResultHandler<Connection>() {
            @Override
            public void handleErrorResult(final ErrorResultException error) {
                sync.signalAndComplete(asResourceException(error));
            }

            @Override
            public void handleResult(final Connection connection) {
                savedConnection.set(connection);
                connection.bindAsync(bindRequest, null, new ResultHandler<BindResult>() {

                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        sync.signalAndComplete(asResourceException(error));
                    }

                    @Override
                    public void handleResult(final BindResult result) {
                        /*
                         * Cache the pre-authenticated connection and prevent
                         * downstream components from closing it since this
                         * filter will close it.
                         */
                        if (reuseAuthenticatedConnection) {
                            request.setAttribute(ATTRIBUTE_AUTHN_CONNECTION, Connections
                                    .uncloseable(connection));
                        }

                        // Pass through the authentication ID and authorization principals.
                        request.setAttribute(ATTRIBUTE_AUTHCID, authcid);
                        request.setAttribute(ATTRIBUTE_AUTHZID, authzid);

                        // Invoke the remainder of the filter chain.
                        sync.signal();
                        if (sync.isAsync()) {
                            try {
                                chain.doFilter(request, response);
                            } catch (Throwable t) {
                                sync.signalAndComplete(asResourceException(t));
                            }
                        }
                    }
                });
            }
        });
    }

    private AuthenticationMethod parseAuthenticationMethod(final JsonValue configuration) {
        if (configuration.isDefined("method")) {
            final String method = configuration.get("method").asString();
            if (method.equalsIgnoreCase("simple")) {
                return AuthenticationMethod.SIMPLE;
            } else if (method.equalsIgnoreCase("sasl-plain")) {
                return AuthenticationMethod.SASL_PLAIN;
            } else if (method.equalsIgnoreCase("search-simple")) {
                return AuthenticationMethod.SEARCH_SIMPLE;
            } else {
                throw new JsonValueException(configuration,
                        "Illegal authentication method: must be either 'simple', "
                                + "'sasl-plain', or 'search-simple'");
            }
        } else {
            return AuthenticationMethod.SEARCH_SIMPLE;
        }
    }

    private SearchScope parseSearchScope(final JsonValue configuration) {
        if (configuration.isDefined("searchScope")) {
            final String scope = configuration.get("searchScope").asString();
            if (scope.equalsIgnoreCase("sub")) {
                return SearchScope.WHOLE_SUBTREE;
            } else if (scope.equalsIgnoreCase("one")) {
                return SearchScope.SINGLE_LEVEL;
            } else {
                throw new JsonValueException(configuration,
                        "Illegal search scope: must be either 'sub' or 'one'");
            }
        } else {
            return SearchScope.WHOLE_SUBTREE;
        }
    }

}
