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
 * Copyright 2012 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.servlet;

import javax.servlet.http.HttpServletRequest;

import org.forgerock.json.resource.Context;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.RootContext;
import org.forgerock.json.resource.SecurityContext;
import org.forgerock.json.resource.servlet.HttpServletContextFactory;
import org.forgerock.json.resource.servlet.SecurityContextFactory;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext;

/**
 * An HTTP servlet context factory which will create a {@link Context} chain
 * comprising of a {@link SecurityContext} and optionally an
 * {@link AuthenticatedConnectionContext}.
 * <p>
 * This class provides integration between Rest2LDAP HTTP Servlets and the
 * {@link Rest2LDAPAuthnFilter}, by providing a mechanism allowing the filter to
 * pass a pre-authenticated LDAP connection through to the underlying Rest2LDAP
 * implementation for use when performing subsequent LDAP operations. The
 * following code illustrates how an authentication Servlet filter can populate
 * the attributes:
 *
 * <pre>
 * public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
 *     // Authenticate the user.
 *     String username = getUserName(request);
 *     String password = getPassword(request);
 *     final Connection connection = getLDAPConnection();
 *
 *     // Publish the authenticated connection.
 *     try {
 *         connection.bind(username, password.toCharArray());
 *         request.setAttribute(ATTRIBUTE_AUTHN_CONNECTION, connection);
 *     } catch (ErrorResultException e) {
 *         // Fail the HTTP request.
 *         response.setStatus(...);
 *         return;
 *     }
 *
 *     // Invoke the rest of the filter chain and then release the LDAP connection once
 *     // processing has completed. Note that this assumes that the filter chain is
 *     // processes requests synchronous.
 *     try {
 *         chain.doFilter(request, response);
 *     } finally {
 *         connection.close();
 *     }
 * }
 * </pre>
 */
public final class Rest2LDAPContextFactory implements HttpServletContextFactory {

    /**
     * The name of the HTTP Servlet Request attribute where this factory expects
     * to find the authenticated user's authentication ID. The name of this
     * attribute is {@code org.forgerock.security.authcid} and it MUST contain a
     * {@code String} if it is present.
     *
     * @see AuthenticatedConnectionContext
     */
    public static final String ATTRIBUTE_AUTHN_CONNECTION =
            "org.forgerock.opendj.rest2ldap.authn-connection";

    // Singleton instance.
    private static final Rest2LDAPContextFactory INSTANCE = new Rest2LDAPContextFactory();

    /**
     * Returns the singleton context factory which can be used for obtaining
     * context information from a HTTP servlet request.
     * <p>
     * This method is named {@code getHttpServletContextFactory} so that it can
     * easily be used for
     * {@link org.forgerock.json.resource.servlet.HttpServlet#getHttpServletContextFactory
     * configuring} JSON Resource Servlets.
     *
     * @return The singleton context factory.
     */
    public static Rest2LDAPContextFactory getHttpServletContextFactory() {
        return INSTANCE;
    }

    private Rest2LDAPContextFactory() {
        // Prevent instantiation.
    }

    /**
     * Creates a new {@link Context} chain comprising of the provided parent
     * context(s), a {@link SecurityContext} obtained using a
     * {@link SecurityContextFactory} , and optionally a
     * {@code AuthenticatedConnectionContext}. The authenticated connection will
     * be obtained from the {@link #ATTRIBUTE_AUTHN_CONNECTION} attribute
     * contained in the provided HTTP servlet request. If the attribute is not
     * present then the {@code AuthenticatedConnectionContext} will not be
     * created.
     *
     * @param parent
     *            The parent context.
     * @param request
     *            The HTTP servlet request from which the security and
     *            authenticated connection attributes should be obtained.
     * @return A new {@link Context} chain comprising of the provided parent
     *         context(s), a {@link SecurityContext} obtained using a
     *         {@link SecurityContextFactory} , and optionally a
     *         {@code AuthenticatedConnectionContext}.
     * @throws ResourceException
     *             If one of the attributes was present but had the wrong type.
     */
    public Context createContext(final Context parent, final HttpServletRequest request)
            throws ResourceException {
        // First get the security context.
        final Context securityContext =
                SecurityContextFactory.getHttpServletContextFactory()
                        .createContext(parent, request);

        // Now append the pre-authenticated connection context if required.
        final Connection connection;
        try {
            connection = (Connection) request.getAttribute(ATTRIBUTE_AUTHN_CONNECTION);
        } catch (final ClassCastException e) {
            throw new InternalServerErrorException(
                    "The rest2ldap authenticated connection context could not be "
                            + "created because the connection attribute, "
                            + ATTRIBUTE_AUTHN_CONNECTION + ", contained in the HTTP "
                            + "servlet request did not have the correct type", e);
        }
        if (connection != null) {
            return new AuthenticatedConnectionContext(securityContext, connection);
        } else {
            return securityContext;
        }
    }

    /**
     * Creates a new {@link Context} chain comprising of a {@link RootContext},
     * a {@link SecurityContext} obtained using a {@link SecurityContextFactory}
     * , and optionally a {@code AuthenticatedConnectionContext}. The
     * authenticated connection will be obtained from the
     * {@link #ATTRIBUTE_AUTHN_CONNECTION} attribute contained in the provided
     * HTTP servlet request. If the attribute is not present then the
     * {@code AuthenticatedConnectionContext} will not be created.
     *
     * @param request
     *            The HTTP servlet request from which the security and
     *            authenticated connection attributes should be obtained.
     * @return A new {@link Context} chain comprising of a {@link RootContext},
     *         a {@link SecurityContext} obtained using a
     *         {@link SecurityContextFactory} , and optionally a
     *         {@code AuthenticatedConnectionContext}.
     * @throws ResourceException
     *             If one of the attributes was present but had the wrong type.
     */
    @Override
    public Context createContext(final HttpServletRequest request) throws ResourceException {
        return createContext(new RootContext(), request);
    }

}
