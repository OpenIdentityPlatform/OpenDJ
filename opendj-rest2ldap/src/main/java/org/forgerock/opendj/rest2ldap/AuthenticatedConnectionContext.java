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
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Utils.ensureNotNull;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.Context;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PersistenceConfig;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Connection;

/**
 * A {@link Context} containing a cached pre-authenticated LDAP connection which
 * should be re-used for performing subsequent LDAP operations. The LDAP
 * connection is typically acquired while perform authentication in an HTTP
 * servlet filter. It is the responsibility of the component which acquired the
 * connection to release once processing has completed.
 */
public final class AuthenticatedConnectionContext extends Context {
    /*
     * TODO: this context does not support persistence because there is no
     * obvious way to restore the connection. We could just persist the context
     * and restore it as null, and let rest2ldap switch to using the factory +
     * proxied authz.
     */
    private final Connection connection;

    /**
     * Creates a new pre-authenticated cached LDAP connection context having the
     * provided parent and an ID automatically generated using
     * {@code UUID.randomUUID()}.
     *
     * @param parent
     *            The parent context.
     * @param connection
     *            The cached pre-authenticated LDAP connection which should be
     *            re-used for subsequent LDAP operations.
     */
    public AuthenticatedConnectionContext(final Context parent, final Connection connection) {
        super(ensureNotNull(parent));
        this.connection = connection;
    }

    /**
     * Creates a new pre-authenticated cached LDAP connection context having the
     * provided ID and parent.
     *
     * @param id
     *            The context ID.
     * @param parent
     *            The parent context.
     * @param connection
     *            The cached pre-authenticated LDAP connection which should be
     *            re-used for subsequent LDAP operations.
     */
    public AuthenticatedConnectionContext(final String id, final Context parent,
            final Connection connection) {
        super(id, ensureNotNull(parent));
        this.connection = connection;
    }

    /**
     * Restore from JSON representation.
     *
     * @param savedContext
     *            The JSON representation from which this context's attributes
     *            should be parsed.
     * @param config
     *            The persistence configuration.
     * @throws ResourceException
     *             If the JSON representation could not be parsed.
     */
    AuthenticatedConnectionContext(final JsonValue savedContext, final PersistenceConfig config)
            throws ResourceException {
        super(savedContext, config);
        throw new InternalServerErrorException(i18n("Cached LDAP connections cannot be restored"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveToJson(final JsonValue savedContext, final PersistenceConfig config)
            throws ResourceException {
        super.saveToJson(savedContext, config);
        throw new InternalServerErrorException(i18n("Cached LDAP connections cannot be persisted"));
    }

    /**
     * Returns the cached pre-authenticated LDAP connection which should be
     * re-used for subsequent LDAP operations.
     *
     * @return The cached pre-authenticated LDAP connection which should be
     *         re-used for subsequent LDAP operations.
     */
    Connection getConnection() {
        return connection;
    }
}
