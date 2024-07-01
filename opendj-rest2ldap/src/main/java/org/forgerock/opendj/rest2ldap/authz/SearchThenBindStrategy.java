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

import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.rest2ldap.authz.SimpleBindStrategy.doSimpleBind;
import static org.forgerock.opendj.rest2ldap.authz.Utils.close;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;

/** Bind using the result of a search request computed from the current request/context. */
final class SearchThenBindStrategy implements AuthenticationStrategy {
    private final ConnectionFactory searchConnectionFactory;
    private final ConnectionFactory bindConnectionFactory;

    private final DN baseDN;
    private final SearchScope searchScope;
    private final String filterTemplate;

    /**
     * Create a new SearchThenBindStrategy.
     *
     * @param searchConnectionFactory
     *            Factory to use to perform the search operation
     * @param bindConnectionFactory
     *            Factory to use to perform the bind operation
     * @param baseDN
     *            BaseDN of the search request
     * @param searchScope
     *            Scope of the search request
     * @param filterTemplate
     *            Filter of the search request (i.e: (&(uid=%s)(objectClass=inetOrgPerson)
     * @throws NullPointerException
     *             If a parameter is null
     */
    public SearchThenBindStrategy(ConnectionFactory searchConnectionFactory, ConnectionFactory bindConnectionFactory,
            DN baseDN, SearchScope searchScope, String filterTemplate) {
        this.searchConnectionFactory = checkNotNull(searchConnectionFactory, "searchConnectionFactory cannot be null");
        this.bindConnectionFactory = checkNotNull(bindConnectionFactory, "bindConnectionFactory cannot be null");
        this.baseDN = checkNotNull(baseDN, "baseDN cannot be null");
        this.searchScope = checkNotNull(searchScope, "searchScope cannot be null");
        this.filterTemplate = checkNotNull(filterTemplate, "filterTemplate cannot be null");
    }

    @Override
    public Promise<SecurityContext, LdapException> authenticate(final String username, final String password,
            final Context parentContext) {
        final AtomicReference<Connection> searchConnectionHolder = new AtomicReference<>();
        return searchConnectionFactory
                .getConnectionAsync()
                // Search
                .thenAsync(new AsyncFunction<Connection, SearchResultEntry, LdapException>() {
                    @Override
                    public Promise<SearchResultEntry, LdapException> apply(final Connection connection)
                            throws LdapException {
                        searchConnectionHolder.set(connection);
                        return connection.searchSingleEntryAsync(
                                newSearchRequest(baseDN, searchScope, Filter.format(filterTemplate, username), "1.1"));
                    }
                })
                .thenFinally(close(searchConnectionHolder))
                // Bind
                .thenAsync(new AsyncFunction<SearchResultEntry, SecurityContext, LdapException>() {
                    @Override
                    public Promise<SecurityContext, LdapException> apply(final SearchResultEntry searchResult)
                            throws LdapException {
                        final AtomicReference<Connection> bindConnectionHolder = new AtomicReference<>();
                        return bindConnectionFactory
                                .getConnectionAsync()
                                .thenAsync(doSimpleBind(bindConnectionHolder, parentContext, username,
                                                        searchResult.getName(), password))
                                .thenFinally(close(bindConnectionHolder));
                    }
                });
    }
}
