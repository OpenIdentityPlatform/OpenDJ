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

import static java.util.Arrays.asList;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Filter.alwaysTrue;
import static org.forgerock.opendj.ldap.requests.Requests.newAddRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newDeleteRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newModifyRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.CONTROLS;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;
import static org.forgerock.opendj.rest2ldap.Utils.transform;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.PreconditionFailedException;
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
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
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
    // Dummy exception used for signalling search success.
    private static final ResourceException SUCCESS = new UncategorizedException(0, null, null);

    private final List<Attribute> additionalLDAPAttributes;
    private final AttributeMapper attributeMapper;
    private final DN baseDN; // TODO: support template variables.
    private final Config config;
    private final AttributeDescription etagAttribute;
    private final NameStrategy nameStrategy;

    LDAPCollectionResourceProvider(final DN baseDN, final AttributeMapper mapper,
            final NameStrategy nameStrategy, final AttributeDescription etagAttribute,
            final Config config, final List<Attribute> additionalLDAPAttributes) {
        this.baseDN = baseDN;
        this.attributeMapper = mapper;
        this.config = config;
        this.nameStrategy = nameStrategy;
        this.etagAttribute = etagAttribute;
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
        final ResultHandler<Resource> h = wrap(c, handler);

        // Get the connection, then determine entry content, then perform add.
        c.run(h, new Runnable() {
            @Override
            public void run() {
                // Calculate entry content.
                attributeMapper.create(c, new JsonPointer(), request.getContent(),
                        new ResultHandler<List<Attribute>>() {
                            @Override
                            public void handleError(final ResourceException error) {
                                h.handleError(error);
                            }

                            @Override
                            public void handleResult(final List<Attribute> result) {
                                // Perform add operation.
                                final AddRequest addRequest = newAddRequest(DN.rootDN());
                                for (final Attribute attribute : additionalLDAPAttributes) {
                                    addRequest.addAttribute(attribute);
                                }
                                for (final Attribute attribute : result) {
                                    addRequest.addAttribute(attribute);
                                }
                                try {
                                    nameStrategy.setResourceId(c, getBaseDN(c), request
                                            .getNewResourceId(), addRequest);
                                } catch (final ResourceException e) {
                                    h.handleError(e);
                                    return;
                                }
                                if (config.readOnUpdatePolicy() == CONTROLS) {
                                    final String[] attributes =
                                            getLDAPAttributes(c, request.getFields());
                                    addRequest.addControl(PostReadRequestControl.newControl(false,
                                            attributes));
                                }
                                c.getConnection().applyChangeAsync(addRequest, null,
                                        postUpdateHandler(c, h));
                            }
                        });
            }
        });
    }

    @Override
    public void deleteInstance(final ServerContext context, final String resourceId,
            final DeleteRequest request, final ResultHandler<Resource> handler) {
        final Context c = wrap(context);
        final ResultHandler<Resource> h = wrap(c, handler);

        // Get connection, search if needed, then delete.
        c.run(h, doUpdate(c, resourceId, request.getRevision(), new ResultHandler<DN>() {
            @Override
            public void handleError(final ResourceException error) {
                h.handleError(error);
            }

            @Override
            public void handleResult(final DN dn) {
                try {
                    final ChangeRecord deleteRequest = newDeleteRequest(dn);
                    if (config.readOnUpdatePolicy() == CONTROLS) {
                        final String[] attributes = getLDAPAttributes(c, request.getFields());
                        deleteRequest.addControl(PreReadRequestControl
                                .newControl(false, attributes));
                    }
                    if (config.useSubtreeDelete()) {
                        deleteRequest.addControl(SubtreeDeleteRequestControl.newControl(true));
                    }
                    addAssertionControl(deleteRequest, request.getRevision());
                    c.getConnection()
                            .applyChangeAsync(deleteRequest, null, postUpdateHandler(c, h));
                } catch (final Exception e) {
                    h.handleError(asResourceException(e));
                }
            }
        }));
    }

    @Override
    public void patchInstance(final ServerContext context, final String resourceId,
            final PatchRequest request, final ResultHandler<Resource> handler) {
        final Context c = wrap(context);
        final ResultHandler<Resource> h = wrap(c, handler);

        if (request.getPatchOperations().isEmpty()) {
            /*
             * This patch is a no-op so just read the entry and check its
             * version.
             */
            c.run(h, new Runnable() {
                @Override
                public void run() {
                    final String[] attributes = getLDAPAttributes(c, request.getFields());
                    final SearchRequest searchRequest =
                            nameStrategy.createSearchRequest(c, getBaseDN(c), resourceId)
                                    .addAttribute(attributes);
                    c.getConnection().searchSingleEntryAsync(searchRequest,
                            postEmptyPatchHandler(c, request, h));
                }
            });
        } else {
            /*
             * Get the connection, search if needed, then determine
             * modifications, then perform modify.
             */
            c.run(h, doUpdate(c, resourceId, request.getRevision(), new ResultHandler<DN>() {
                @Override
                public void handleError(final ResourceException error) {
                    h.handleError(error);
                }

                @Override
                public void handleResult(final DN dn) {
                    //  Convert the patch operations to LDAP modifications.
                    final ResultHandler<List<Modification>> handler =
                            accumulate(request.getPatchOperations().size(),
                                    new ResultHandler<List<List<Modification>>>() {
                                        @Override
                                        public void handleError(final ResourceException error) {
                                            h.handleError(error);
                                        }

                                        @Override
                                        public void handleResult(
                                                final List<List<Modification>> result) {
                                            //  The patch operations have been converted successfully.
                                            try {
                                                final ModifyRequest modifyRequest =
                                                        newModifyRequest(dn);

                                                // Add the modifications.
                                                for (final List<Modification> modifications : result) {
                                                    if (modifications != null) {
                                                        modifyRequest.getModifications().addAll(
                                                                modifications);
                                                    }
                                                }

                                                final List<String> attributes =
                                                        asList(getLDAPAttributes(c, request
                                                                .getFields()));
                                                if (modifyRequest.getModifications().isEmpty()) {
                                                    /*
                                                     * This patch is a no-op so
                                                     * just read the entry and
                                                     * check its version.
                                                     */
                                                    c.getConnection().readEntryAsync(dn,
                                                            attributes,
                                                            postEmptyPatchHandler(c, request, h));
                                                } else {
                                                    // Add controls and perform the modify request.
                                                    if (config.readOnUpdatePolicy() == CONTROLS) {
                                                        modifyRequest
                                                                .addControl(PostReadRequestControl
                                                                        .newControl(false,
                                                                                attributes));
                                                    }
                                                    if (config.usePermissiveModify()) {
                                                        modifyRequest
                                                                .addControl(PermissiveModifyRequestControl
                                                                        .newControl(true));
                                                    }
                                                    addAssertionControl(modifyRequest, request
                                                            .getRevision());
                                                    c.getConnection().applyChangeAsync(
                                                            modifyRequest, null,
                                                            postUpdateHandler(c, h));
                                                }
                                            } catch (final Exception e) {
                                                h.handleError(asResourceException(e));
                                            }
                                        }
                                    });

                    for (final PatchOperation operation : request.getPatchOperations()) {
                        attributeMapper.patch(c, new JsonPointer(), operation, handler);
                    }
                }
            }));
        }
    }

    @Override
    public void queryCollection(final ServerContext context, final QueryRequest request,
            final QueryResultHandler handler) {
        final Context c = wrap(context);
        final QueryResultHandler h = wrap(c, handler);

        /*
         * Get the connection, then calculate the search filter, then perform
         * the search.
         */
        c.run(h, new Runnable() {
            @Override
            public void run() {
                // Calculate the filter (this may require the connection).
                getLDAPFilter(c, request.getQueryFilter(), new ResultHandler<Filter>() {
                    @Override
                    public void handleError(final ResourceException error) {
                        h.handleError(error);
                    }

                    @Override
                    public void handleResult(final Filter ldapFilter) {
                        /*
                         * Avoid performing a search if the filter could not be
                         * mapped or if it will never match.
                         */
                        if (ldapFilter == null || ldapFilter == alwaysFalse()) {
                            h.handleResult(new QueryResult());
                        } else {
                            // Perform the search.
                            final String[] attributes = getLDAPAttributes(c, request.getFields());
                            final SearchRequest request =
                                    newSearchRequest(getBaseDN(c), SearchScope.SINGLE_LEVEL,
                                            ldapFilter == Filter.alwaysTrue() ? Filter
                                                    .objectClassPresent() : ldapFilter, attributes);
                            c.getConnection().searchAsync(request, null, new SearchResultHandler() {
                                /*
                                 * The following fields are guarded by
                                 * sequenceLock. In addition, the sequenceLock
                                 * ensures that we send one JSON resource at a
                                 * time back to the client.
                                 */
                                private final Object sequenceLock = new Object();
                                private int pendingResourceCount = 0;
                                private ResourceException pendingResult = null;
                                private boolean resultSent = false;

                                @Override
                                public boolean handleEntry(final SearchResultEntry entry) {
                                    /*
                                     * Search result entries will be returned
                                     * before the search result/error so the
                                     * only reason pendingResult will be
                                     * non-null is if a mapping error has
                                     * occurred.
                                     */
                                    synchronized (sequenceLock) {
                                        if (pendingResult != null) {
                                            return false;
                                        }
                                        pendingResourceCount++;
                                    }

                                    /*
                                     * FIXME: secondary asynchronous searches
                                     * will complete in a non-deterministic
                                     * order and may cause the JSON resources to
                                     * be returned in a different order to the
                                     * order in which the primary LDAP search
                                     * results were received. This is benign at
                                     * the moment, but will need resolving when
                                     * we implement server side sorting. A
                                     * possible fix will be to use a queue of
                                     * pending resources (futures?). However,
                                     * the queue cannot be unbounded in case it
                                     * grows very large, but it cannot be
                                     * bounded either since that could cause a
                                     * deadlock between rest2ldap and the LDAP
                                     * server (imagine the case where the server
                                     * has a single worker thread which is
                                     * occupied processing the primary search).
                                     * The best solution is probably to process
                                     * the primary search results in batches
                                     * using the paged results control.
                                     */
                                    final String id = nameStrategy.getResourceId(c, entry);
                                    final String revision = getRevisionFromEntry(entry);
                                    attributeMapper.read(c, new JsonPointer(), entry,
                                            new ResultHandler<JsonValue>() {
                                                @Override
                                                public void handleError(final ResourceException e) {
                                                    synchronized (sequenceLock) {
                                                        pendingResourceCount--;
                                                        completeIfNecessary(e);
                                                    }
                                                }

                                                @Override
                                                public void handleResult(final JsonValue result) {
                                                    synchronized (sequenceLock) {
                                                        pendingResourceCount--;
                                                        if (!resultSent) {
                                                            h.handleResource(new Resource(id,
                                                                    revision, result));
                                                        }
                                                        completeIfNecessary();
                                                    }
                                                }
                                            });
                                    return true;
                                }

                                @Override
                                public void handleErrorResult(final ErrorResultException error) {
                                    synchronized (sequenceLock) {
                                        completeIfNecessary(asResourceException(error));
                                    }
                                }

                                @Override
                                public boolean handleReference(final SearchResultReference reference) {
                                    // TODO: should this be classed as an error since rest2ldap
                                    // assumes entries are all colocated?
                                    return true;
                                }

                                @Override
                                public void handleResult(final Result result) {
                                    synchronized (sequenceLock) {
                                        completeIfNecessary(SUCCESS);
                                    }
                                }

                                /*
                                 * This method must be invoked with the
                                 * sequenceLock held.
                                 */
                                private void completeIfNecessary(final ResourceException e) {
                                    if (pendingResult == null) {
                                        pendingResult = e;
                                    }
                                    completeIfNecessary();
                                }

                                /*
                                 * Close out the query result set if there are
                                 * no more pending resources and the LDAP result
                                 * has been received. This method must be
                                 * invoked with the sequenceLock held.
                                 */
                                private void completeIfNecessary() {
                                    if (pendingResourceCount == 0 && pendingResult != null
                                            && !resultSent) {
                                        if (pendingResult == SUCCESS) {
                                            h.handleResult(new QueryResult());
                                        } else {
                                            h.handleError(pendingResult);
                                        }
                                        resultSent = true;
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    public void readInstance(final ServerContext context, final String resourceId,
            final ReadRequest request, final ResultHandler<Resource> handler) {
        final Context c = wrap(context);
        final ResultHandler<Resource> h = wrap(c, handler);

        // Get connection then perform the search.
        c.run(h, new Runnable() {
            @Override
            public void run() {
                // Do the search.
                final String[] attributes = getLDAPAttributes(c, request.getFields());
                final SearchRequest request =
                        nameStrategy.createSearchRequest(c, getBaseDN(c), resourceId).addAttribute(
                                attributes);
                c.getConnection().searchSingleEntryAsync(request,
                        new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
                            @Override
                            public void handleErrorResult(final ErrorResultException error) {
                                h.handleError(asResourceException(error));
                            }

                            @Override
                            public void handleResult(final SearchResultEntry entry) {
                                adaptEntry(c, entry, h);
                            }
                        });
            }
        });
    }

    @Override
    public void updateInstance(final ServerContext context, final String resourceId,
            final UpdateRequest request, final ResultHandler<Resource> handler) {
        /*
         * Update operations are a bit awkward because there is no direct
         * mapping to LDAP. We need to convert the update request into an LDAP
         * modify operation which means reading the current LDAP entry,
         * generating the new entry content, then comparing the two in order to
         * obtain a set of changes. We also need to handle read-only fields
         * correctly: if a read-only field is included with the new resource
         * then it must match exactly the value of the existing field.
         */
        final Context c = wrap(context);
        final ResultHandler<Resource> h = wrap(c, handler);

        // Get connection then, search for the existing entry, then modify.
        c.run(h, new Runnable() {
            @Override
            public void run() {
                final String[] attributes =
                        getLDAPAttributes(c, Collections.<JsonPointer> emptyList());
                final SearchRequest searchRequest =
                        nameStrategy.createSearchRequest(c, getBaseDN(c), resourceId).addAttribute(
                                attributes);
                c.getConnection().searchSingleEntryAsync(searchRequest,
                        new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
                            @Override
                            public void handleErrorResult(final ErrorResultException error) {
                                h.handleError(asResourceException(error));
                            }

                            @Override
                            public void handleResult(final SearchResultEntry entry) {
                                try {
                                    // Fail-fast if there is a version mismatch.
                                    ensureMVCCVersionMatches(entry, request.getRevision());

                                    //  Create the modify request.
                                    final ModifyRequest modifyRequest =
                                            newModifyRequest(entry.getName());
                                    if (config.readOnUpdatePolicy() == CONTROLS) {
                                        final String[] attributes =
                                                getLDAPAttributes(c, request.getFields());
                                        modifyRequest.addControl(PostReadRequestControl.newControl(
                                                false, attributes));
                                    }
                                    if (config.usePermissiveModify()) {
                                        modifyRequest.addControl(PermissiveModifyRequestControl
                                                .newControl(true));
                                    }
                                    addAssertionControl(modifyRequest, request.getRevision());

                                    /*
                                     * Determine the set of changes that need to
                                     * be performed.
                                     */
                                    attributeMapper.update(c, new JsonPointer(), entry, request
                                            .getNewContent(),
                                            new ResultHandler<List<Modification>>() {
                                                @Override
                                                public void handleError(
                                                        final ResourceException error) {
                                                    h.handleError(error);
                                                }

                                                @Override
                                                public void handleResult(
                                                        final List<Modification> result) {
                                                    // Perform the modify operation.
                                                    if (result.isEmpty()) {
                                                        /*
                                                         * No changes to be
                                                         * performed, so just
                                                         * return the entry that
                                                         * we read.
                                                         */
                                                        adaptEntry(c, entry, h);
                                                    } else {
                                                        modifyRequest.getModifications().addAll(
                                                                result);
                                                        c.getConnection().applyChangeAsync(
                                                                modifyRequest, null,
                                                                postUpdateHandler(c, h));
                                                    }
                                                }
                                            });
                                } catch (final Exception e) {
                                    h.handleError(asResourceException(e));
                                }
                            }
                        });
            }
        });
    }

    private void adaptEntry(final Context c, final Entry entry,
            final ResultHandler<Resource> handler) {
        final String actualResourceId = nameStrategy.getResourceId(c, entry);
        final String revision = getRevisionFromEntry(entry);
        attributeMapper.read(c, new JsonPointer(), entry, transform(
                new Function<JsonValue, Resource, Void>() {
                    @Override
                    public Resource apply(final JsonValue value, final Void p) {
                        return new Resource(actualResourceId, revision, new JsonValue(value));
                    }
                }, handler));
    }

    private void addAssertionControl(final ChangeRecord request, final String expectedRevision)
            throws ResourceException {
        if (expectedRevision != null) {
            ensureMVCCSupported();
            request.addControl(AssertionRequestControl.newControl(true, Filter.equality(
                    etagAttribute.toString(), expectedRevision)));
        }
    }

    private Runnable doUpdate(final Context c, final String resourceId, final String revision,
            final ResultHandler<DN> updateHandler) {
        return new Runnable() {
            @Override
            public void run() {
                final String ldapAttribute =
                        (etagAttribute != null && revision != null) ? etagAttribute.toString()
                                : "1.1";
                final SearchRequest searchRequest =
                        nameStrategy.createSearchRequest(c, getBaseDN(c), resourceId).addAttribute(
                                ldapAttribute);
                if (searchRequest.getScope().equals(SearchScope.BASE_OBJECT)) {
                    // There's no point in doing a search because we already know the DN.
                    updateHandler.handleResult(searchRequest.getName());
                } else {
                    c.getConnection().searchSingleEntryAsync(searchRequest,
                            new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
                                @Override
                                public void handleErrorResult(final ErrorResultException error) {
                                    updateHandler.handleError(asResourceException(error));
                                }

                                @Override
                                public void handleResult(final SearchResultEntry entry) {
                                    try {
                                        // Fail-fast if there is a version mismatch.
                                        ensureMVCCVersionMatches(entry, revision);

                                        // Perform update operation.
                                        updateHandler.handleResult(entry.getName());
                                    } catch (final Exception e) {
                                        updateHandler.handleError(asResourceException(e));
                                    }
                                }
                            });
                }
            }
        };
    }

    private void ensureMVCCSupported() throws NotSupportedException {
        if (etagAttribute == null) {
            throw new NotSupportedException(
                    i18n("Multi-version concurrency control is not supported by this resource"));
        }
    }

    private void ensureMVCCVersionMatches(final Entry entry, final String expectedRevision)
            throws ResourceException {
        if (expectedRevision != null) {
            ensureMVCCSupported();
            final String actualRevision = entry.parseAttribute(etagAttribute).asString();
            if (actualRevision == null) {
                throw new PreconditionFailedException(i18n(
                        "The resource could not be accessed because it did not contain any "
                                + "version information, when the version '%s' was expected",
                        expectedRevision));
            } else if (!expectedRevision.equals(actualRevision)) {
                throw new PreconditionFailedException(i18n(
                        "The resource could not be accessed because the expected version '%s' "
                                + "does not match the current version '%s'", expectedRevision,
                        actualRevision));
            }
        }
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
            attributeMapper.getLDAPAttributes(c, new JsonPointer(), new JsonPointer(),
                    requestedLDAPAttributes);
        } else {
            // Partial read.
            requestedLDAPAttributes = new LinkedHashSet<String>(requestedAttributes.size());
            for (final JsonPointer requestedAttribute : requestedAttributes) {
                attributeMapper.getLDAPAttributes(c, new JsonPointer(), requestedAttribute,
                        requestedLDAPAttributes);
            }
        }

        // Get the LDAP attributes required by the Etag and name stategies.
        nameStrategy.getLDAPAttributes(c, requestedLDAPAttributes);
        if (etagAttribute != null) {
            requestedLDAPAttributes.add(etagAttribute.toString());
        }
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
                                                    if (f == alwaysFalse()) {
                                                        return alwaysFalse();
                                                    } else if (f == alwaysTrue()) {
                                                        i.remove();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return alwaysTrue();
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
                        p.handleResult(toFilter(value));
                        return null;
                    }

                    @Override
                    public Void visitContainsFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.CONTAINS, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitEqualsFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.EQUAL_TO, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitExtendedMatchFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final String operator,
                            final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.EXTENDED, operator, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitGreaterThanFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.GREATER_THAN, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitGreaterThanOrEqualToFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.GREATER_THAN_OR_EQUAL_TO, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitLessThanFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.LESS_THAN, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitLessThanOrEqualToFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.LESS_THAN_OR_EQUAL_TO, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitNotFilter(final ResultHandler<Filter> p,
                            final QueryFilter subFilter) {
                        subFilter.accept(this, transform(new Function<Filter, Filter, Void>() {
                            @Override
                            public Filter apply(final Filter value, final Void p) {
                                if (value == null || value == alwaysFalse()) {
                                    return alwaysTrue();
                                } else if (value == alwaysTrue()) {
                                    return alwaysFalse();
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
                                                    if (f == alwaysFalse()) {
                                                        i.remove();
                                                    } else if (f == alwaysTrue()) {
                                                        return alwaysTrue();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return alwaysFalse();
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
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.PRESENT, null, null, p);
                        return null;
                    }

                    @Override
                    public Void visitStartsWithFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, new JsonPointer(), field,
                                FilterType.STARTS_WITH, null, valueAssertion, p);
                        return null;
                    }

                };
        /*
         * Note that the returned LDAP filter may be null if it could not be
         * mapped by any attribute mappers.
         */
        queryFilter.accept(visitor, h);
    }

    private String getRevisionFromEntry(final Entry entry) {
        return etagAttribute != null ? entry.parseAttribute(etagAttribute).asString() : null;
    }

    private org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry> postEmptyPatchHandler(
            final Context c, final PatchRequest request, final ResultHandler<Resource> h) {
        return new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
            @Override
            public void handleErrorResult(final ErrorResultException error) {
                h.handleError(asResourceException(error));
            }

            @Override
            public void handleResult(final SearchResultEntry entry) {
                try {
                    // Fail if there is a version mismatch.
                    ensureMVCCVersionMatches(entry, request.getRevision());
                    adaptEntry(c, entry, h);
                } catch (final Exception e) {
                    h.handleError(asResourceException(e));
                }
            }
        };
    }

    private org.forgerock.opendj.ldap.ResultHandler<Result> postUpdateHandler(final Context c,
            final ResultHandler<Resource> handler) {
        // The handler which will be invoked for the LDAP add result.
        return new org.forgerock.opendj.ldap.ResultHandler<Result>() {
            @Override
            public void handleErrorResult(final ErrorResultException error) {
                handler.handleError(asResourceException(error));
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
    }

    private QueryResultHandler wrap(final Context c, final QueryResultHandler handler) {
        return new QueryResultHandler() {
            @Override
            public void handleError(final ResourceException error) {
                try {
                    handler.handleError(error);
                } finally {
                    c.close();
                }
            }

            @Override
            public boolean handleResource(final Resource resource) {
                return handler.handleResource(resource);
            }

            @Override
            public void handleResult(final QueryResult result) {
                try {
                    handler.handleResult(result);
                } finally {
                    c.close();
                }
            }
        };
    }

    private <V> ResultHandler<V> wrap(final Context c, final ResultHandler<V> handler) {
        return new ResultHandler<V>() {
            @Override
            public void handleError(final ResourceException error) {
                try {
                    handler.handleError(error);
                } finally {
                    c.close();
                }
            }

            @Override
            public void handleResult(final V result) {
                try {
                    handler.handleResult(result);
                } finally {
                    c.close();
                }
            }
        };
    }

    private Context wrap(final ServerContext context) {
        return new Context(config, context);
    }
}
