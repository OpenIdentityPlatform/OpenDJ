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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.opendj.rest2ldap;

import java.util.Collection;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public final class EntryContainer {
    // FIXME: make this configurable, also allow use of DN.
    private static final String UUID_ATTRIBUTE = "entryUUID";

    // FIXME: make this configurable.
    private static final String ETAG_ATTRIBUTE = "etag";

    private final ConnectionFactory factory;
    private final DN baseDN;

    private abstract class AbstractRequestCompletionHandler<R, H extends ResultHandler<? super R>>
            implements ResultHandler<R> {
        final H resultHandler;
        final Connection connection;

        AbstractRequestCompletionHandler(final Connection connection, final H resultHandler) {
            this.connection = connection;
            this.resultHandler = resultHandler;
        }

        @Override
        public final void handleErrorResult(final ErrorResultException error) {
            connection.close();
            resultHandler.handleErrorResult(error);
        }

        @Override
        public final void handleResult(final R result) {
            connection.close();
            resultHandler.handleResult(result);
        }

    }

    private abstract class ConnectionCompletionHandler<R> implements ResultHandler<Connection> {
        private final ResultHandler<? super R> resultHandler;

        ConnectionCompletionHandler(final ResultHandler<? super R> resultHandler) {
            this.resultHandler = resultHandler;
        }

        @Override
        public final void handleErrorResult(final ErrorResultException error) {
            resultHandler.handleErrorResult(error);
        }

        @Override
        public abstract void handleResult(Connection connection);

    }

    private final class RequestCompletionHandler<R> extends
            AbstractRequestCompletionHandler<R, ResultHandler<? super R>> {
        RequestCompletionHandler(final Connection connection,
                final ResultHandler<? super R> resultHandler) {
            super(connection, resultHandler);
        }
    }

    private final class SearchRequestCompletionHandler extends
            AbstractRequestCompletionHandler<Result, SearchResultHandler> implements
            SearchResultHandler {

        SearchRequestCompletionHandler(final Connection connection,
                final SearchResultHandler resultHandler) {
            super(connection, resultHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean handleEntry(final SearchResultEntry entry) {
            return resultHandler.handleEntry(entry);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean handleReference(final SearchResultReference reference) {
            return resultHandler.handleReference(reference);
        }

    }

    public EntryContainer(DN baseDN, ConnectionFactory factory) {
        this.baseDN = baseDN;
        this.factory = factory;
    }

    public void listEntries(final Context context, final SearchResultHandler handler) {
        final ConnectionCompletionHandler<Result> outerHandler =
                new ConnectionCompletionHandler<Result>(handler) {

                    @Override
                    public void handleResult(final Connection connection) {
                        final SearchRequestCompletionHandler innerHandler =
                                new SearchRequestCompletionHandler(connection, handler);
                        SearchRequest request =
                                Requests.newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, Filter
                                        .objectClassPresent(), UUID_ATTRIBUTE, ETAG_ATTRIBUTE);
                        connection.searchAsync(request, null, innerHandler);
                    }

                };

        factory.getConnectionAsync(outerHandler);
    }

    public void readEntry(final Context c, final String id, final Collection<String> attributes,
            final ResultHandler<SearchResultEntry> handler) {
        final ConnectionCompletionHandler<SearchResultEntry> outerHandler =
                new ConnectionCompletionHandler<SearchResultEntry>(handler) {

                    @Override
                    public void handleResult(final Connection connection) {
                        final RequestCompletionHandler<SearchResultEntry> innerHandler =
                                new RequestCompletionHandler<SearchResultEntry>(connection, handler);
                        // FIXME: who is responsible for adding the UUID and
                        // etag attributes to this search?
                        String[] tmp = attributes.toArray(new String[attributes.size() + 2]);
                        tmp[tmp.length - 2] = UUID_ATTRIBUTE;
                        tmp[tmp.length - 1] = ETAG_ATTRIBUTE;

                        SearchRequest request =
                                Requests.newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, Filter
                                        .equality(UUID_ATTRIBUTE, id), tmp);
                        connection.searchSingleEntryAsync(request, innerHandler);
                    }

                };

        factory.getConnectionAsync(outerHandler);
    }

    public String getIDFromEntry(final Entry entry) {
        return entry.parseAttribute(UUID_ATTRIBUTE).asString();
    }

    public String getEtagFromEntry(final Entry entry) {
        return entry.parseAttribute(ETAG_ATTRIBUTE).asString();
    }

}
