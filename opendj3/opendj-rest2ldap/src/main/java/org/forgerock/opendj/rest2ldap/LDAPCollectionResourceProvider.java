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

import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.transform;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * A {@code CollectionResourceProvider} implementation which maps a JSON
 * resource collection to LDAP entries beneath a base DN.
 */
public class LDAPCollectionResourceProvider implements CollectionResourceProvider {
    // Dummy exception used for signalling search success.
    private static final ResourceException SUCCESS = new UncategorizedException(0, null, null);
    private final AttributeMapper attributeMapper;
    private final EntryContainer entryContainer;
    private final Config config = new Config();

    /**
     * Creates a new LDAP resource.
     *
     * @param container
     *            The LDAP entry container.
     * @param mapper
     *            The attribute mapper which will be used for mapping LDAP
     *            attributes to JSON attributes.
     */
    public LDAPCollectionResourceProvider(final EntryContainer container,
            final AttributeMapper mapper) {
        this.entryContainer = container;
        this.attributeMapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionCollection(final ServerContext context, final ActionRequest request,
            final ResultHandler<JsonValue> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionInstance(final ServerContext context, final String resourceId,
            final ActionRequest request, final ResultHandler<JsonValue> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createInstance(final ServerContext context, final CreateRequest request,
            final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteInstance(final ServerContext context, final String resourceId,
            final DeleteRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patchInstance(final ServerContext context, final String resourceId,
            final PatchRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void queryCollection(final ServerContext context, final QueryRequest request,
            final QueryResultHandler handler) {
        // List the entries.
        final Context c = wrap(context);
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

                // TODO: should the resource or the container define the ID
                // mapping?
                final String id = entryContainer.getIDFromEntry(entry);
                final String revision = entryContainer.getEtagFromEntry(entry);
                final ResultHandler<Map<String, Object>> mapHandler =
                        new ResultHandler<Map<String, Object>>() {
                            @Override
                            public void handleError(final ResourceException e) {
                                pendingResult.compareAndSet(null, e);
                                pendingResourceCount.decrementAndGet();
                                completeIfNecessary();
                            }

                            @Override
                            public void handleResult(final Map<String, Object> result) {
                                final Resource resource =
                                        new Resource(id, revision, new JsonValue(result));
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

        final Collection<String> ldapAttributes = getLDAPAttributes(c, request.getFieldFilters());
        getLDAPFilter(c, request.getQueryFilter(), new ResultHandler<Filter>() {
            @Override
            public void handleError(final ResourceException error) {
                handler.handleError(error);
            }

            @Override
            public void handleResult(final Filter ldapFilter) {
                // Avoid performing a search if the filter could not be mapped or if it will never match.
                if (ldapFilter == null || ldapFilter == c.getConfig().getFalseFilter()) {
                    handler.handleResult(new QueryResult());
                } else {
                    entryContainer.listEntries(c, ldapFilter, ldapAttributes, searchHandler);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readInstance(final ServerContext context, final String resourceId,
            final ReadRequest request, final ResultHandler<Resource> handler) {
        final Context c = wrap(context);
        // @Checkstyle:off
        final org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry> searchHandler =
                new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        handler.handleError(adaptErrorResult(error));
                    }

                    @Override
                    public void handleResult(final SearchResultEntry entry) {
                        final String revision = entryContainer.getEtagFromEntry(entry);
                        final ResultHandler<Map<String, Object>> mapHandler =
                                new ResultHandler<Map<String, Object>>() {
                                    @Override
                                    public void handleError(final ResourceException e) {
                                        handler.handleError(e);
                                    }

                                    @Override
                                    public void handleResult(final Map<String, Object> result) {
                                        final Resource resource =
                                                new Resource(resourceId, revision, new JsonValue(
                                                        result));
                                        handler.handleResult(resource);
                                    }
                                };
                        attributeMapper.toJSON(c, entry, mapHandler);
                    }
                };
        // @Checkstyle:on
        final Collection<String> ldapAttributes = getLDAPAttributes(c, request.getFieldFilters());
        entryContainer.readEntry(c, resourceId, ldapAttributes, searchHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateInstance(final ServerContext context, final String resourceId,
            final UpdateRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
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

    /**
     * Determines the set of LDAP attributes to request in an LDAP read (search,
     * post-read), based on the provided list of JSON pointers.
     *
     * @param requestedAttributes
     *            The list of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private Collection<String> getLDAPAttributes(final Context c,
            final Collection<JsonPointer> requestedAttributes) {
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
        return requestedLDAPAttributes;
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
                                                        return c.getConfig().getFalseFilter();
                                                    } else if (f == c.getConfig().getFalseFilter()) {
                                                        return c.getConfig().getFalseFilter();
                                                    } else if (f == c.getConfig().getTrueFilter()) {
                                                        i.remove();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return c.getConfig().getTrueFilter();
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
                        p.handleResult(value ? c.getConfig().getTrueFilter() : c.getConfig()
                                .getFalseFilter());
                        return null;
                    }

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
                                    return c.getConfig().getTrueFilter();
                                } else if (value == c.getConfig().getFalseFilter()) {
                                    return c.getConfig().getTrueFilter();
                                } else if (value == c.getConfig().getTrueFilter()) {
                                    return c.getConfig().getFalseFilter();
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
                                                    } else if (f == c.getConfig().getFalseFilter()) {
                                                        i.remove();
                                                    } else if (f == c.getConfig().getTrueFilter()) {
                                                        return c.getConfig().getTrueFilter();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return c.getConfig().getFalseFilter();
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

    private Context wrap(ServerContext context) {
        return new Context(config, context);
    }
}
