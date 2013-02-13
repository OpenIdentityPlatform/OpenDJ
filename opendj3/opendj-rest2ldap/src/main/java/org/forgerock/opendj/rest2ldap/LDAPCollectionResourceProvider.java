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
 * Copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.USE_READ_ENTRY_CONTROLS;
import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.transform;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryFilter;
import org.forgerock.json.resource.QueryFilterVisitor;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UncategorizedException;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.AssertionFailureException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;

/**
 * A {@code CollectionResourceProvider} implementation which maps a JSON
 * resource collection to LDAP entries beneath a base DN.
 */
final class LDAPCollectionResourceProvider implements CollectionResourceProvider {

    private abstract class AbstractRequestCompletionHandler
            <R, H extends org.forgerock.opendj.ldap.ResultHandler<? super R>>
            implements org.forgerock.opendj.ldap.ResultHandler<R> {
        final Connection connection;
        final H resultHandler;

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

    private abstract class ConnectionCompletionHandler<R> implements
            org.forgerock.opendj.ldap.ResultHandler<Connection> {
        private final org.forgerock.opendj.ldap.ResultHandler<? super R> resultHandler;

        ConnectionCompletionHandler(
                final org.forgerock.opendj.ldap.ResultHandler<? super R> resultHandler) {
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
            AbstractRequestCompletionHandler<R, org.forgerock.opendj.ldap.ResultHandler<? super R>> {
        RequestCompletionHandler(final Connection connection,
                final org.forgerock.opendj.ldap.ResultHandler<? super R> resultHandler) {
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

        @Override
        public final boolean handleEntry(final SearchResultEntry entry) {
            return resultHandler.handleEntry(entry);
        }

        @Override
        public final boolean handleReference(final SearchResultReference reference) {
            return resultHandler.handleReference(reference);
        }

    }

    // Dummy exception used for signalling search success.
    private static final ResourceException SUCCESS = new UncategorizedException(0, null, null);

    private final List<Attribute> additionalLDAPAttributes;
    private final AttributeMapper attributeMapper;
    private final DN baseDN; // TODO: support template variables.
    private final Config config;
    private final ConnectionFactory factory;
    private final MVCCStrategy mvccStrategy;
    private final NameStrategy nameStrategy;

    LDAPCollectionResourceProvider(final DN baseDN, final AttributeMapper mapper,
            final ConnectionFactory factory, final NameStrategy nameStrategy,
            final MVCCStrategy mvccStrategy, final Config config,
            final List<Attribute> additionalLDAPAttributes) {
        this.baseDN = baseDN;
        this.attributeMapper = mapper;
        this.factory = factory;
        this.config = config;
        this.nameStrategy = nameStrategy;
        this.mvccStrategy = mvccStrategy;
        this.additionalLDAPAttributes = additionalLDAPAttributes;
    }

    @Override
    public void actionCollection(final ServerContext context, final ActionRequest request,
            final ResultHandler<JsonValue> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    @Override
    public void actionInstance(final ServerContext context, final String resourceId,
            final ActionRequest request, final ResultHandler<JsonValue> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    @Override
    public void createInstance(final ServerContext context, final CreateRequest request,
            final ResultHandler<Resource> handler) {
        final Context c = wrap(context);
        attributeMapper.toLDAP(c, request.getContent(), new ResultHandler<List<Attribute>>() {
            @Override
            public void handleError(final ResourceException error) {
                handler.handleError(error);
            }

            @Override
            public void handleResult(final List<Attribute> result) {
                final AddRequest addRequest = Requests.newAddRequest(DN.rootDN());
                for (final Attribute attribute : additionalLDAPAttributes) {
                    addRequest.addAttribute(attribute);
                }
                for (final Attribute attribute : result) {
                    addRequest.addAttribute(attribute);
                }
                try {
                    nameStrategy.setResourceId(c, getBaseDN(c), request.getNewResourceId(),
                            addRequest);
                } catch (ResourceException e) {
                    handler.handleError(e);
                    return;
                }
                if (config.readOnUpdatePolicy() == USE_READ_ENTRY_CONTROLS) {
                    final String[] attributes = getLDAPAttributes(c, request.getFieldFilters());
                    addRequest.addControl(PostReadRequestControl.newControl(false, attributes));
                }
                applyUpdate(c, addRequest, handler);
            }
        });
    }

    @Override
    public void deleteInstance(final ServerContext context, final String resourceId,
            final DeleteRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    @Override
    public void patchInstance(final ServerContext context, final String resourceId,
            final PatchRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    @Override
    public void queryCollection(final ServerContext context, final QueryRequest request,
            final QueryResultHandler handler) {
        final Context c = wrap(context);

        // The handler which will be invoked for each LDAP search result.
        final SearchResultHandler searchHandler = new SearchResultHandler() {
            private final AtomicInteger pendingResourceCount = new AtomicInteger();
            private final AtomicReference<ResourceException> pendingResult =
                    new AtomicReference<ResourceException>();
            private final AtomicBoolean resultSent = new AtomicBoolean();

            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                /*
                 * Search result entries will be returned before the search
                 * result/error so the only reason pendingResult will be
                 * non-null is if a mapping error has occurred.
                 */
                if (pendingResult.get() != null) {
                    return false;
                }

                final String id = nameStrategy.getResourceId(c, entry);
                final String revision = mvccStrategy.getRevisionFromEntry(c, entry);
                final ResultHandler<JsonValue> mapHandler = new ResultHandler<JsonValue>() {
                    @Override
                    public void handleError(final ResourceException e) {
                        pendingResult.compareAndSet(null, e);
                        pendingResourceCount.decrementAndGet();
                        completeIfNecessary();
                    }

                    @Override
                    public void handleResult(final JsonValue result) {
                        final Resource resource = new Resource(id, revision, result);
                        handler.handleResource(resource);
                        pendingResourceCount.decrementAndGet();
                        completeIfNecessary();
                    }
                };

                pendingResourceCount.incrementAndGet();
                attributeMapper.toJSON(c, entry, mapHandler);
                return true;
            }

            @Override
            public void handleErrorResult(final ErrorResultException error) {
                pendingResult.compareAndSet(null, adaptErrorResult(error));
                completeIfNecessary();
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                // TODO: should this be classed as an error since rest2ldap
                // assumes entries are all colocated?
                return true;
            }

            @Override
            public void handleResult(final Result result) {
                pendingResult.compareAndSet(null, SUCCESS);
                completeIfNecessary();
            }

            /*
             * Close out the query result set if there are no more pending
             * resources and the LDAP result has been received.
             */
            private void completeIfNecessary() {
                if (pendingResourceCount.get() == 0) {
                    final ResourceException result = pendingResult.get();
                    if (result != null && resultSent.compareAndSet(false, true)) {
                        if (result == SUCCESS) {
                            handler.handleResult(new QueryResult());
                        } else {
                            handler.handleError(result);
                        }
                    }
                }
            }
        };

        // The handler which will be invoked once the LDAP filter has been transformed.
        final ResultHandler<Filter> filterHandler = new ResultHandler<Filter>() {
            @Override
            public void handleError(final ResourceException error) {
                handler.handleError(error);
            }

            @Override
            public void handleResult(final Filter ldapFilter) {
                // Avoid performing a search if the filter could not be mapped or if it will never match.
                if (ldapFilter == null || ldapFilter == c.getConfig().falseFilter()) {
                    handler.handleResult(new QueryResult());
                } else {
                    final ConnectionCompletionHandler<Result> outerHandler =
                            new ConnectionCompletionHandler<Result>(searchHandler) {

                                @Override
                                public void handleResult(final Connection connection) {
                                    final SearchRequestCompletionHandler innerHandler =
                                            new SearchRequestCompletionHandler(connection,
                                                    searchHandler);
                                    final String[] attributes =
                                            getLDAPAttributes(c, request.getFieldFilters());
                                    final SearchRequest request =
                                            Requests.newSearchRequest(getBaseDN(c),
                                                    SearchScope.SINGLE_LEVEL, ldapFilter,
                                                    attributes);
                                    connection.searchAsync(request, null, innerHandler);
                                }

                            };

                    factory.getConnectionAsync(outerHandler);
                }
            }
        };

        getLDAPFilter(c, request.getQueryFilter(), filterHandler);
    }

    @Override
    public void readInstance(final ServerContext context, final String resourceId,
            final ReadRequest request, final ResultHandler<Resource> handler) {
        final Context c = wrap(context);

        // The handler which will be invoked for the LDAP search result.
        final org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry> searchHandler =
                new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        handler.handleError(adaptErrorResult(error));
                    }

                    @Override
                    public void handleResult(final SearchResultEntry entry) {
                        adaptEntry(c, entry, handler);
                    }

                };

        // The handler which will be invoked
        final ConnectionCompletionHandler<SearchResultEntry> outerHandler =
                new ConnectionCompletionHandler<SearchResultEntry>(searchHandler) {

                    @Override
                    public void handleResult(final Connection connection) {
                        final RequestCompletionHandler<SearchResultEntry> innerHandler =
                                new RequestCompletionHandler<SearchResultEntry>(connection,
                                        searchHandler);
                        final String[] attributes = getLDAPAttributes(c, request.getFieldFilters());
                        final SearchRequest request =
                                nameStrategy.createSearchRequest(c, getBaseDN(c), resourceId)
                                        .addAttribute(attributes);
                        connection.searchSingleEntryAsync(request, innerHandler);
                    }

                };

        factory.getConnectionAsync(outerHandler);
    }

    @Override
    public void updateInstance(final ServerContext context, final String resourceId,
            final UpdateRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    private void adaptEntry(final Context c, final Entry entry,
            final ResultHandler<Resource> handler) {
        final String actualResourceId = nameStrategy.getResourceId(c, entry);
        final String revision = mvccStrategy.getRevisionFromEntry(c, entry);
        attributeMapper.toJSON(c, entry, transform(new Function<JsonValue, Resource, Void>() {
            @Override
            public Resource apply(final JsonValue value, final Void p) {
                return new Resource(actualResourceId, revision, new JsonValue(value));
            }
        }, handler));
    }

    /**
     * Adapts an LDAP result code to a resource exception.
     *
     * @param error
     *            The LDAP error that should be adapted.
     * @return The equivalent resource exception.
     */
    private ResourceException adaptErrorResult(final ErrorResultException error) {
        int resourceResultCode;
        try {
            throw error;
        } catch (final AssertionFailureException e) {
            resourceResultCode = ResourceException.VERSION_MISMATCH;
        } catch (final AuthenticationException e) {
            resourceResultCode = 401;
        } catch (final AuthorizationException e) {
            resourceResultCode = ResourceException.FORBIDDEN;
        } catch (final ConnectionException e) {
            resourceResultCode = ResourceException.UNAVAILABLE;
        } catch (final EntryNotFoundException e) {
            resourceResultCode = ResourceException.NOT_FOUND;
        } catch (final MultipleEntriesFoundException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        } catch (final TimeoutResultException e) {
            resourceResultCode = 408;
        } catch (final ErrorResultException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        }
        return ResourceException.getException(resourceResultCode, null, error.getMessage(), error);
    }

    private void applyUpdate(final Context c, final ChangeRecord request,
            final ResultHandler<Resource> handler) {
        final org.forgerock.opendj.ldap.ResultHandler<Result> resultHandler =
                postUpdateHandler(c, handler);
        final ConnectionCompletionHandler<Result> outerHandler =
                new ConnectionCompletionHandler<Result>(resultHandler) {

                    @Override
                    public void handleResult(final Connection connection) {
                        final RequestCompletionHandler<Result> innerHandler =
                                new RequestCompletionHandler<Result>(connection, resultHandler);
                        connection.applyChangeAsync(request, null, innerHandler);
                    }
                };
        factory.getConnectionAsync(outerHandler);
    }

    private DN getBaseDN(final Context context) {
        return baseDN;
    }

    /**
     * Determines the set of LDAP attributes to request in an LDAP read (search,
     * post-read), based on the provided list of JSON pointers.
     *
     * @param requestedAttributes
     *            The list of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private String[] getLDAPAttributes(final Context c,
            final Collection<JsonPointer> requestedAttributes) {
        // Get all the LDAP attributes required by the attribute mappers.
        final Set<String> requestedLDAPAttributes;
        if (requestedAttributes.isEmpty()) {
            // Full read.
            requestedLDAPAttributes = new LinkedHashSet<String>();
            attributeMapper.getLDAPAttributes(c, new JsonPointer(), requestedLDAPAttributes);
        } else {
            // Partial read.
            requestedLDAPAttributes = new LinkedHashSet<String>(requestedAttributes.size());
            for (final JsonPointer requestedAttribute : requestedAttributes) {
                attributeMapper.getLDAPAttributes(c, requestedAttribute, requestedLDAPAttributes);
            }
        }

        // Get the LDAP attributes required by the Etag and name stategies.
        nameStrategy.getLDAPAttributes(c, requestedLDAPAttributes);
        mvccStrategy.getLDAPAttributes(c, requestedLDAPAttributes);
        return requestedLDAPAttributes.toArray(new String[requestedLDAPAttributes.size()]);
    }

    private void getLDAPFilter(final Context c, final QueryFilter queryFilter,
            final ResultHandler<Filter> h) {
        final QueryFilterVisitor<Void, ResultHandler<Filter>> visitor =
                new QueryFilterVisitor<Void, ResultHandler<Filter>>() {
                    @Override
                    public Void visitAndFilter(final ResultHandler<Filter> p,
                            final List<QueryFilter> subFilters) {
                        final ResultHandler<Filter> handler =
                                accumulate(subFilters.size(), transform(
                                        new Function<List<Filter>, Filter, Void>() {
                                            @Override
                                            public Filter apply(final List<Filter> value,
                                                    final Void p) {
                                                // Check for unmapped filter components and optimize.
                                                final Iterator<Filter> i = value.iterator();
                                                while (i.hasNext()) {
                                                    final Filter f = i.next();
                                                    if (f == null) {
                                                        // Filter component did not match any attribute mappers.
                                                        return c.getConfig().falseFilter();
                                                    } else if (f == c.getConfig().falseFilter()) {
                                                        return c.getConfig().falseFilter();
                                                    } else if (f == c.getConfig().trueFilter()) {
                                                        i.remove();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return c.getConfig().trueFilter();
                                                case 1:
                                                    return value.get(0);
                                                default:
                                                    return Filter.and(value);
                                                }
                                            }
                                        }, p));
                        for (final QueryFilter subFilter : subFilters) {
                            subFilter.accept(this, handler);
                        }
                        return null;
                    }

                    @Override
                    public Void visitBooleanLiteralFilter(final ResultHandler<Filter> p,
                            final boolean value) {
                        p.handleResult(value ? c.getConfig().trueFilter() : c.getConfig()
                                .falseFilter());
                        return null;
                    }

                    @Override
                    public Void visitContainsFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.CONTAINS, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitEqualsFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.EQUAL_TO, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitExtendedMatchFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final String operator,
                            final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.EXTENDED, field, operator,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitGreaterThanFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.GREATER_THAN, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitGreaterThanOrEqualToFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.GREATER_THAN_OR_EQUAL_TO,
                                field, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitLessThanFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.LESS_THAN, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitLessThanOrEqualToFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.LESS_THAN_OR_EQUAL_TO, field,
                                null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitNotFilter(final ResultHandler<Filter> p,
                            final QueryFilter subFilter) {
                        subFilter.accept(this, transform(new Function<Filter, Filter, Void>() {
                            @Override
                            public Filter apply(final Filter value, final Void p) {
                                if (value == null) {
                                    // Filter component did not match any attribute mappers.
                                    return c.getConfig().trueFilter();
                                } else if (value == c.getConfig().falseFilter()) {
                                    return c.getConfig().trueFilter();
                                } else if (value == c.getConfig().trueFilter()) {
                                    return c.getConfig().falseFilter();
                                } else {
                                    return Filter.not(value);
                                }
                            }
                        }, p));
                        return null;
                    }

                    @Override
                    public Void visitOrFilter(final ResultHandler<Filter> p,
                            final List<QueryFilter> subFilters) {
                        final ResultHandler<Filter> handler =
                                accumulate(subFilters.size(), transform(
                                        new Function<List<Filter>, Filter, Void>() {
                                            @Override
                                            public Filter apply(final List<Filter> value,
                                                    final Void p) {
                                                // Check for unmapped filter components and optimize.
                                                final Iterator<Filter> i = value.iterator();
                                                while (i.hasNext()) {
                                                    final Filter f = i.next();
                                                    if (f == null) {
                                                        // Filter component did not match any attribute mappers.
                                                        i.remove();
                                                    } else if (f == c.getConfig().falseFilter()) {
                                                        i.remove();
                                                    } else if (f == c.getConfig().trueFilter()) {
                                                        return c.getConfig().trueFilter();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return c.getConfig().falseFilter();
                                                case 1:
                                                    return value.get(0);
                                                default:
                                                    return Filter.or(value);
                                                }
                                            }
                                        }, p));
                        for (final QueryFilter subFilter : subFilters) {
                            subFilter.accept(this, handler);
                        }
                        return null;
                    }

                    @Override
                    public Void visitPresentFilter(final ResultHandler<Filter> p,
                            final JsonPointer field) {
                        attributeMapper.getLDAPFilter(c, FilterType.PRESENT, field, null, null, p);
                        return null;
                    }

                    @Override
                    public Void visitStartsWithFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.STARTS_WITH, field, null,
                                valueAssertion, p);
                        return null;
                    }

                };
        // Note that the returned LDAP filter may be null if it could not be mapped by any attribute mappers.
        queryFilter.accept(visitor, h);
    }

    private org.forgerock.opendj.ldap.ResultHandler<Result> postUpdateHandler(final Context c,
            final ResultHandler<Resource> handler) {
        // The handler which will be invoked for the LDAP add result.
        final org.forgerock.opendj.ldap.ResultHandler<Result> resultHandler =
                new org.forgerock.opendj.ldap.ResultHandler<Result>() {
                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        handler.handleError(adaptErrorResult(error));
                    }

                    @Override
                    public void handleResult(final Result result) {
                        // FIXME: handle USE_SEARCH policy.
                        Entry entry;
                        try {
                            final PostReadResponseControl postReadControl =
                                    result.getControl(PostReadResponseControl.DECODER, config
                                            .decodeOptions());
                            if (postReadControl != null) {
                                entry = postReadControl.getEntry();
                            } else {
                                final PreReadResponseControl preReadControl =
                                        result.getControl(PreReadResponseControl.DECODER, config
                                                .decodeOptions());
                                if (preReadControl != null) {
                                    entry = preReadControl.getEntry();
                                } else {
                                    entry = null;
                                }
                            }
                        } catch (final DecodeException e) {
                            // FIXME: log something?
                            entry = null;
                        }
                        if (entry != null) {
                            adaptEntry(c, entry, handler);
                        } else {
                            final Resource resource =
                                    new Resource(null, null, new JsonValue(Collections.emptyMap()));
                            handler.handleResult(resource);
                        }
                    }

                };
        return resultHandler;
    }

    private Context wrap(final ServerContext context) {
        return new Context(config, context);
    }
}
