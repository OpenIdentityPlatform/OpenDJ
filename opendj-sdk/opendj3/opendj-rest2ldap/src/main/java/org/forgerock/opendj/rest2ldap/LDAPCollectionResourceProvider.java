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
 * Copyright 2012 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.Collection;
import java.util.LinkedHashSet;
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
        final Set<JsonPointer> requestedAttributes = new LinkedHashSet<JsonPointer>();
        final Collection<String> requestedLDAPAttributes = getRequestedLDAPAttributes(requestedAttributes);

        // List the entries.
        final SearchResultHandler searchHandler = new SearchResultHandler() {
            private final AtomicInteger pendingResourceCount = new AtomicInteger();
            private final AtomicReference<ResourceException> pendingResult = new AtomicReference<ResourceException>();
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
                final ResultHandler<Map<String, Object>> mapHandler = new ResultHandler<Map<String, Object>>() {
                    @Override
                    public void handleError(final ResourceException e) {
                        pendingResult.compareAndSet(null, e);
                        pendingResourceCount.decrementAndGet();
                        completeIfNecessary();
                    }

                    @Override
                    public void handleResult(final Map<String, Object> result) {
                        final Resource resource = new Resource(id, revision, new JsonValue(result));
                        handler.handleResource(resource);
                        pendingResourceCount.decrementAndGet();
                        completeIfNecessary();
                    }
                };

                pendingResourceCount.incrementAndGet();
                attributeMapper.toJSON(context, entry, mapHandler);
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
        entryContainer.listEntries(context, requestedLDAPAttributes, searchHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readInstance(final ServerContext context, final String resourceId,
            final ReadRequest request, final ResultHandler<Resource> handler) {
        // TODO: Determine the set of LDAP attributes that need to be read.
        final Set<JsonPointer> requestedAttributes = new LinkedHashSet<JsonPointer>();
        final Collection<String> requestedLDAPAttributes = getRequestedLDAPAttributes(requestedAttributes);

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
                final ResultHandler<Map<String, Object>> mapHandler = new ResultHandler<Map<String, Object>>() {
                    @Override
                    public void handleError(final ResourceException e) {
                        handler.handleError(e);
                    }

                    @Override
                    public void handleResult(final Map<String, Object> result) {
                        final Resource resource = new Resource(resourceId, revision, new JsonValue(
                                result));
                        handler.handleResult(resource);
                    }
                };
                attributeMapper.toJSON(context, entry, mapHandler);
            }
        };
        // @Checkstyle:on
        entryContainer.readEntry(context, resourceId, requestedLDAPAttributes, searchHandler);
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
     * post-read), based on the provided set of JSON pointers.
     *
     * @param requestedAttributes
     *            The set of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private Collection<String> getRequestedLDAPAttributes(final Set<JsonPointer> requestedAttributes) {
        final Set<String> requestedLDAPAttributes;
        if (requestedAttributes.isEmpty()) {
            // Full read.
            requestedLDAPAttributes = new LinkedHashSet<String>();
            attributeMapper.getLDAPAttributes(new JsonPointer(), requestedLDAPAttributes);
        } else {
            // Partial read.
            requestedLDAPAttributes = new LinkedHashSet<String>(requestedAttributes.size());
            for (final JsonPointer requestedAttribute : requestedAttributes) {
                attributeMapper.getLDAPAttributes(requestedAttribute, requestedLDAPAttributes);
            }
        }
        return requestedLDAPAttributes;
    }

}
