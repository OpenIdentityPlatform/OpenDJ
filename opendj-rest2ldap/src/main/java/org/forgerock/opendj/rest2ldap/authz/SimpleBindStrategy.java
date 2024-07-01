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

import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.rest2ldap.authz.Utils.close;
import static org.forgerock.services.context.SecurityContext.AUTHZID_DN;
import static org.forgerock.services.context.SecurityContext.AUTHZID_ID;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/** Bind using a computed DN from a template and the current request/context. */
final class SimpleBindStrategy implements AuthenticationStrategy {

    private final ConnectionFactory connectionFactory;
    private final Schema schema;
    private final String bindDNTemplate;

    /**
     * Create a new SimpleBindStrategy.
     *
     * @param connectionFactory
     *            Factory used to get {@link Connection} receiving the bind requests
     * @param schema
     *            Schema used to validate DN
     * @param bindDNTemplate
     *            The template which will be replaced by the authenticating user (i.e:
     *            uid=%s,ou=People,dc=example,dc=com)
     * @throws NullPointerException
     *             If a parameter is null
     */
    public SimpleBindStrategy(ConnectionFactory connectionFactory, String bindDNTemplate, Schema schema) {
        this.connectionFactory = checkNotNull(connectionFactory, "connectionFactory cannot be null");
        this.bindDNTemplate = checkNotNull(bindDNTemplate, "bindDNTemplate cannot be null");
        this.schema = checkNotNull(schema, "schema cannot be null");
    }

    @Override
    public Promise<SecurityContext, LdapException> authenticate(final String username, final String password,
            final Context parentContext) {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        return connectionFactory
                .getConnectionAsync()
                .thenAsync(doSimpleBind(connectionHolder, parentContext, username,
                           DN.format(bindDNTemplate, schema, username), password))
                .thenFinally(close(connectionHolder));
    }

    static AsyncFunction<Connection, SecurityContext, LdapException> doSimpleBind(
            final AtomicReference<Connection> connectionHolder, final Context parentContext, final String username,
            final DN bindDN, final String password) {
        return new AsyncFunction<Connection, SecurityContext, LdapException>() {
            @Override
            public Promise<SecurityContext, LdapException> apply(Connection connection) throws LdapException {
                connectionHolder.set(connection);
                return connection
                        .bindAsync(newSimpleBindRequest(bindDN.toString(), password.toCharArray()))
                        .then(new Function<BindResult, SecurityContext, LdapException>() {
                            @Override
                            public SecurityContext apply(BindResult result) throws LdapException {
                                final Map<String, Object> authzid = new LinkedHashMap<>(2);
                                authzid.put(AUTHZID_DN, bindDN.toString());
                                authzid.put(AUTHZID_ID, username);
                                return new SecurityContext(parentContext, username, authzid);
                            }
                        });
            }
        };
    }
}
