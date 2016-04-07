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
 * Copyright 2012-2015 ForgeRock AS.
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
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Responses;
import org.forgerock.json.resource.UncategorizedException;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.query.QueryFilter;
import org.forgerock.util.query.QueryFilterVisitor;

/**
 * A {@code CollectionResourceProvider} implementation which maps a JSON
 * resource collection to LDAP entries beneath a base DN.
 */
final class LDAPCollectionResourceProvider implements CollectionResourceProvider {
    /** Dummy exception used for signalling search success. */
    private static final ResourceException SUCCESS = new UncategorizedException(0, null, null);

    /** Empty decode options required for decoding response controls. */
    private static final DecodeOptions DECODE_OPTIONS = new DecodeOptions();

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
    public Promise<ActionResponse, ResourceException> actionCollection(
            final Context context, final ActionRequest request) {
        return Promises.<ActionResponse, ResourceException> newExceptionPromise(
                                                            new NotSupportedException("Not yet implemented"));
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(
            final Context context, final String resourceId, final ActionRequest request) {
        String actionId = request.getAction();
        if (actionId.equals("passwordModify")) {
            return passwordModify(context, resourceId, request);
        }
        return Promises.<ActionResponse, ResourceException> newExceptionPromise(
                new NotSupportedException("The action '" + actionId + "' is not supported"));
    }

    private Promise<ActionResponse, ResourceException> passwordModify(
            final Context context, final String resourceId, final ActionRequest request) {
        if (!context.containsContext(ClientContext.class)
                || !context.asContext(ClientContext.class).isSecure()) {
            return Promises.newExceptionPromise(ResourceException.newResourceException(
                    ResourceException.FORBIDDEN, "Password modify requires a secure connection."));
        }
        if (!context.containsContext(SecurityContext.class)
                || context.asContext(SecurityContext.class).getAuthenticationId() == null) {
            return Promises.newExceptionPromise(ResourceException.newResourceException(
                    ResourceException.FORBIDDEN, "Password modify requires user to be authenticated."));
        }

        final JsonValue jsonContent = request.getContent();
        final String oldPassword;
        final String newPassword;
        try {
            oldPassword = jsonContent.get("oldPassword").asString();
            newPassword = jsonContent.get("newPassword").asString();
        } catch (JsonValueException e) {
            return Promises.newExceptionPromise(
                    ResourceException.newResourceException(ResourceException.BAD_REQUEST, e.getLocalizedMessage(), e));
        }

        final RequestState requestState = wrap(context);
        return requestState.getConnection()
                .thenAsync(new AsyncFunction<Connection, ActionResponse, ResourceException>() {
                    @Override
                    public Promise<ActionResponse, ResourceException> apply(final Connection connection)
                            throws ResourceException {
                        List<JsonPointer> attrs = Collections.emptyList();
                        return connection.searchSingleEntryAsync(searchRequest(requestState, resourceId, attrs))
                                .thenAsync(new AsyncFunction<SearchResultEntry, ActionResponse, ResourceException>() {
                                    @Override
                                    public Promise<ActionResponse, ResourceException> apply(
                                              final SearchResultEntry entry) {
                                        PasswordModifyExtendedRequest pwdModifyRequest =
                                                Requests.newPasswordModifyExtendedRequest();
                                        pwdModifyRequest.setUserIdentity("dn: " + entry.getName());
                                        pwdModifyRequest.setOldPassword(asBytes(oldPassword));
                                        pwdModifyRequest.setNewPassword(asBytes(newPassword));
                                        return connection.extendedRequestAsync(pwdModifyRequest)
                                            .thenAsync(new AsyncFunction<PasswordModifyExtendedResult,
                                                    ActionResponse, ResourceException>() {
                                                @Override
                                                public Promise<ActionResponse, ResourceException> apply(
                                                        PasswordModifyExtendedResult value) throws ResourceException {
                                                    JsonValue result = new JsonValue(new LinkedHashMap<>());
                                                    byte[] generatedPwd = value.getGeneratedPassword();
                                                    if (generatedPwd != null) {
                                                        result = result.put("generatedPassword",
                                                                ByteString.valueOfBytes(generatedPwd).toString());
                                                    }
                                                    return Responses.newActionResponse(result).asPromise();
                                                }
                                            }, ldapExceptionToResourceException());
                                    }
                                }, ldapExceptionToResourceException());
                    }

                    private AsyncFunction<LdapException, ActionResponse, ResourceException>
                    ldapExceptionToResourceException() {
                        return ldapToResourceException();
                    }
                }).thenFinally(close(requestState));
    }

    private byte[] asBytes(final String s) {
        return s != null ? s.getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    public Promise<ResourceResponse, ResourceException> createInstance(
            final Context context, final CreateRequest request) {
        final RequestState requestState = wrap(context);

        return requestState.getConnection().thenAsync(
            new AsyncFunction<Connection, ResourceResponse, ResourceException>() {
                @Override
                public Promise<ResourceResponse, ResourceException> apply(final Connection connection)
                        throws ResourceException {
                    // Calculate entry content.
                    return attributeMapper.create(requestState, new JsonPointer(), request.getContent())
                            .thenAsync(new AsyncFunction<List<Attribute>, ResourceResponse, ResourceException>() {
                                @Override
                                public Promise<ResourceResponse, ResourceException> apply(
                                        final List<Attribute> attributes) {
                                    // Perform add operation.
                                    final AddRequest addRequest = newAddRequest(DN.rootDN());
                                    for (final Attribute attribute : additionalLDAPAttributes) {
                                        addRequest.addAttribute(attribute);
                                    }
                                    for (final Attribute attribute : attributes) {
                                        addRequest.addAttribute(attribute);
                                    }
                                    try {
                                        nameStrategy.setResourceId(requestState, getBaseDN(),
                                                request.getNewResourceId(), addRequest);
                                    } catch (final ResourceException e) {
                                        return Promises.newExceptionPromise(e);
                                    }
                                    if (config.readOnUpdatePolicy() == CONTROLS) {
                                        addRequest.addControl(PostReadRequestControl.newControl(
                                                false, getLDAPAttributes(requestState, request.getFields())));
                                    }
                                    return connection.applyChangeAsync(addRequest)
                                                     .thenAsync(postUpdateResultAsyncFunction(requestState),
                                                                ldapExceptionToResourceException());
                                }
                            });
                }
            }).thenFinally(close(requestState));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> deleteInstance(
            final Context context, final String resourceId, final DeleteRequest request) {
        final RequestState requestState = wrap(context);
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        return requestState.getConnection()
                .thenOnResult(saveConnection(connectionHolder))
                .thenAsync(doUpdateFunction(requestState, resourceId, request.getRevision()))
                .thenAsync(new AsyncFunction<DN, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(DN dn) throws ResourceException {
                        try {
                            final ChangeRecord deleteRequest = newDeleteRequest(dn);
                            if (config.readOnUpdatePolicy() == CONTROLS) {
                                final String[] attributes = getLDAPAttributes(requestState, request.getFields());
                                deleteRequest.addControl(PreReadRequestControl.newControl(false, attributes));
                            }
                            if (config.useSubtreeDelete()) {
                                deleteRequest.addControl(SubtreeDeleteRequestControl.newControl(true));
                            }
                            addAssertionControl(deleteRequest, request.getRevision());
                            return connectionHolder.get().applyChangeAsync(deleteRequest)
                                                         .thenAsync(postUpdateResultAsyncFunction(requestState),
                                                                    ldapExceptionToResourceException());

                        } catch (final Exception e) {
                            return Promises.newExceptionPromise(asResourceException(e));
                        }
                    }
                }).thenFinally(close(requestState));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(
            final Context context, final String resourceId, final PatchRequest request) {
        final RequestState requestState = wrap(context);

        if (request.getPatchOperations().isEmpty()) {
            return emptyPatchInstance(requestState, resourceId, request);
        }

        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
        return requestState.getConnection()
                .thenOnResult(saveConnection(connectionHolder))
                .thenAsync(doUpdateFunction(requestState, resourceId, request.getRevision()))
                .thenAsync(new AsyncFunction<DN, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(final DN dn) throws ResourceException {
                        // Convert the patch operations to LDAP modifications.
                        List<Promise<List<Modification>, ResourceException>> promises =
                                new ArrayList<>(request.getPatchOperations().size());
                        for (final PatchOperation operation : request.getPatchOperations()) {
                            promises.add(attributeMapper.patch(requestState, new JsonPointer(), operation));
                        }

                        return Promises.when(promises).thenAsync(
                                new AsyncFunction<List<List<Modification>>, ResourceResponse, ResourceException>() {
                                    @Override
                                    public Promise<ResourceResponse, ResourceException> apply(
                                            final List<List<Modification>> result) {
                                        // The patch operations have been converted successfully.
                                        try {
                                            final ModifyRequest modifyRequest = newModifyRequest(dn);

                                            // Add the modifications.
                                            for (final List<Modification> modifications : result) {
                                                if (modifications != null) {
                                                    modifyRequest.getModifications().addAll(modifications);
                                                }
                                            }

                                            final List<String> attributes =
                                                    asList(getLDAPAttributes(requestState, request.getFields()));
                                            if (modifyRequest.getModifications().isEmpty()) {
                                                // This patch is a no-op so just read the entry and check its version.
                                                return connectionHolder.get()
                                                        .readEntryAsync(dn, attributes)
                                                        .thenAsync(postEmptyPatchAsyncFunction(requestState, request),
                                                                   ldapExceptionToResourceException());
                                            } else {
                                                // Add controls and perform the modify request.
                                                if (config.readOnUpdatePolicy() == CONTROLS) {
                                                    modifyRequest.addControl(
                                                            PostReadRequestControl.newControl(false, attributes));
                                                }
                                                if (config.usePermissiveModify()) {
                                                    modifyRequest.addControl(
                                                            PermissiveModifyRequestControl.newControl(true));
                                                }
                                                addAssertionControl(modifyRequest, request.getRevision());
                                                return connectionHolder.get()
                                                        .applyChangeAsync(modifyRequest)
                                                        .thenAsync(postUpdateResultAsyncFunction(requestState),
                                                                   ldapExceptionToResourceException());
                                            }
                                        } catch (final Exception e) {
                                            return Promises.newExceptionPromise(asResourceException(e));
                                        }
                                    }
                                });
                    }
                }).thenFinally(close(requestState));
    }

    /** Just read the entry and check its version. */
    private Promise<ResourceResponse, ResourceException> emptyPatchInstance(
            final RequestState requestState, final String resourceId, final PatchRequest request) {
        return requestState.getConnection()
                .thenAsync(new AsyncFunction<Connection, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(final Connection connection)
                            throws ResourceException {
                        SearchRequest searchRequest = searchRequest(requestState, resourceId, request.getFields());
                        return connection.searchSingleEntryAsync(searchRequest)
                                         .thenAsync(postEmptyPatchAsyncFunction(requestState, request),
                                                    ldapExceptionToResourceException());
                    }
                });
    }

    private AsyncFunction<SearchResultEntry, ResourceResponse, ResourceException> postEmptyPatchAsyncFunction(
            final RequestState requestState, final PatchRequest request) {
        return new AsyncFunction<SearchResultEntry, ResourceResponse, ResourceException>() {
            @Override
            public Promise<ResourceResponse, ResourceException> apply(SearchResultEntry entry)
                    throws ResourceException {
                try {
                    // Fail if there is a version mismatch.
                    ensureMVCCVersionMatches(entry, request.getRevision());
                    return adaptEntry(requestState, entry);
                } catch (final Exception e) {
                    return Promises.newExceptionPromise(asResourceException(e));
                }
            }
        };
    }

    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(
            final Context context, final QueryRequest request, final QueryResourceHandler resourceHandler) {
        final RequestState requestState = wrap(context);

        return requestState.getConnection()
                .thenAsync(new AsyncFunction<Connection, QueryResponse, ResourceException>() {
                    @Override
                    public Promise<QueryResponse, ResourceException> apply(final Connection connection)
                            throws ResourceException {
                        // Calculate the filter (this may require the connection).
                        return getLDAPFilter(requestState, request.getQueryFilter())
                                            .thenAsync(runQuery(request, resourceHandler, requestState, connection));
                    }
                })
                .thenFinally(close(requestState));
    }

    private Promise<Filter, ResourceException> getLDAPFilter(
            final RequestState requestState, final QueryFilter<JsonPointer> queryFilter) {
        final QueryFilterVisitor<Promise<Filter, ResourceException>, Void, JsonPointer> visitor =
                new QueryFilterVisitor<Promise<Filter, ResourceException>, Void, JsonPointer>() {

                    @Override
                    public Promise<Filter, ResourceException> visitAndFilter(final Void unused,
                            final List<QueryFilter<JsonPointer>> subFilters) {
                        final List<Promise<Filter, ResourceException>> promises = new ArrayList<>(subFilters.size());
                        for (final QueryFilter<JsonPointer> subFilter : subFilters) {
                            promises.add(subFilter.accept(this, unused));
                        }

                        return Promises.when(promises).then(new Function<List<Filter>, Filter, ResourceException>() {
                            @Override
                            public Filter apply(final List<Filter> value) {
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
                        });
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitBooleanLiteralFilter(
                            final Void unused, final boolean value) {
                        return Promises.newResultPromise(toFilter(value));
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitContainsFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.CONTAINS, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitEqualsFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.EQUAL_TO, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitExtendedMatchFilter(final Void unused,
                            final JsonPointer field, final String operator, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.EXTENDED, operator, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitGreaterThanFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.GREATER_THAN, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitGreaterThanOrEqualToFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(requestState, new JsonPointer(), field,
                                FilterType.GREATER_THAN_OR_EQUAL_TO, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitLessThanFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.LESS_THAN, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitLessThanOrEqualToFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(requestState, new JsonPointer(), field,
                                FilterType.LESS_THAN_OR_EQUAL_TO, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitNotFilter(
                            final Void unused, final QueryFilter<JsonPointer> subFilter) {
                        return subFilter.accept(this, unused).then(new Function<Filter, Filter, ResourceException>() {
                            @Override
                            public Filter apply(final Filter value) {
                                if (value == null || value == alwaysFalse()) {
                                    return alwaysTrue();
                                } else if (value == alwaysTrue()) {
                                    return alwaysFalse();
                                } else {
                                    return Filter.not(value);
                                }
                            }
                        });
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitOrFilter(final Void unused,
                            final List<QueryFilter<JsonPointer>> subFilters) {
                        final List<Promise<Filter, ResourceException>> promises = new ArrayList<>(subFilters.size());
                        for (final QueryFilter<JsonPointer> subFilter : subFilters) {
                            promises.add(subFilter.accept(this, unused));
                        }

                        return Promises.when(promises).then(new Function<List<Filter>, Filter, ResourceException>() {
                            @Override
                            public Filter apply(final List<Filter> value) {
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
                        });
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitPresentFilter(
                            final Void unused, final JsonPointer field) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.PRESENT, null, null);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitStartsWithFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return attributeMapper.getLDAPFilter(
                                requestState, new JsonPointer(), field, FilterType.STARTS_WITH, null, valueAssertion);
                    }

                };
        // Note that the returned LDAP filter may be null if it could not be mapped by any attribute mappers.
        return queryFilter.accept(visitor, null);
    }

    private AsyncFunction<Filter, QueryResponse, ResourceException> runQuery(final QueryRequest request,
            final QueryResourceHandler resourceHandler, final RequestState requestState, final Connection connection) {
        return new AsyncFunction<Filter, QueryResponse, ResourceException>() {
            /**
             * The following fields are guarded by sequenceLock. In addition,
             * the sequenceLock ensures that we send one JSON resource at a time
             * back to the client.
             */
            private final Object sequenceLock = new Object();
            private String cookie;
            private ResourceException pendingResult;
            private int pendingResourceCount;
            private boolean resultSent;
            private int totalResourceCount;

            @Override
            public Promise<QueryResponse, ResourceException> apply(final Filter ldapFilter) {
                if (ldapFilter == null || ldapFilter == alwaysFalse()) {
                    // Avoid performing a search if the filter could not be mapped or if it will never match.
                    return Promises.newResultPromise(Responses.newQueryResponse());
                }
                final PromiseImpl<QueryResponse, ResourceException> promise = PromiseImpl.create();
                // Perform the search.
                final String[] attributes = getLDAPAttributes(requestState, request.getFields());
                final Filter searchFilter = ldapFilter == Filter.alwaysTrue() ? Filter.objectClassPresent()
                                                                              : ldapFilter;
                final SearchRequest searchRequest = newSearchRequest(
                        getBaseDN(), SearchScope.SINGLE_LEVEL, searchFilter, attributes);

                // Add the page results control. We can support the page offset by
                // reading the next offset pages, or offset x page size resources.
                final int pageResultStartIndex;
                final int pageSize = request.getPageSize();
                if (request.getPageSize() > 0) {
                    final int pageResultEndIndex;
                    if (request.getPagedResultsOffset() > 0) {
                        pageResultStartIndex = request.getPagedResultsOffset() * pageSize;
                        pageResultEndIndex = pageResultStartIndex + pageSize;
                    } else {
                        pageResultStartIndex = 0;
                        pageResultEndIndex = pageSize;
                    }
                    final ByteString cookie = request.getPagedResultsCookie() != null
                            ? ByteString.valueOfBase64(request.getPagedResultsCookie()) : ByteString.empty();
                    final SimplePagedResultsControl control =
                            SimplePagedResultsControl.newControl(true, pageResultEndIndex, cookie);
                    searchRequest.addControl(control);
                } else {
                    pageResultStartIndex = 0;
                }

                connection.searchAsync(searchRequest, new SearchResultHandler() {
                    @Override
                    public boolean handleEntry(final SearchResultEntry entry) {
                        // Search result entries will be returned before the search result/error so the only reason
                        // pendingResult will be non-null is if a mapping error has occurred.
                        synchronized (sequenceLock) {
                            if (pendingResult != null) {
                                return false;
                            }
                            if (totalResourceCount++ < pageResultStartIndex) {
                                // Haven't reached paged results threshold yet.
                                return true;
                            }
                            pendingResourceCount++;
                        }

                        /*
                         * FIXME: secondary asynchronous searches will complete in a non-deterministic order and
                         * may cause the JSON resources to be returned in a different order to the order in which
                         * the primary LDAP search results were received. This is benign at the moment, but will
                         * need resolving when we implement server side sorting. A possible fix will be to use a
                         * queue of pending resources (promises?). However, the queue cannot be unbounded in case
                         * it grows very large, but it cannot be bounded either since that could cause a deadlock
                         * between rest2ldap and the LDAP server (imagine the case where the server has a single
                         * worker thread which is occupied processing the primary search).
                         * The best solution is probably to process the primary search results in batches using
                         * the paged results control.
                         */
                        final String id = nameStrategy.getResourceId(requestState, entry);
                        final String revision = getRevisionFromEntry(entry);
                        attributeMapper.read(requestState, new JsonPointer(), entry)
                                       .thenOnResult(new ResultHandler<JsonValue>() {
                                           @Override
                                           public void handleResult(final JsonValue result) {
                                               synchronized (sequenceLock) {
                                                   pendingResourceCount--;
                                                   if (!resultSent) {
                                                       resourceHandler.handleResource(
                                                               Responses.newResourceResponse(id, revision, result));
                                                   }
                                                   completeIfNecessary(promise);
                                               }
                                           }
                                       }).thenOnException(new ExceptionHandler<ResourceException>() {
                                           @Override
                                           public void handleException(ResourceException exception) {
                                               synchronized (sequenceLock) {
                                                   pendingResourceCount--;
                                                   completeIfNecessary(exception, promise);
                                               }
                                           }
                                       });
                        return true;
                    }

                    @Override
                    public boolean handleReference(final SearchResultReference reference) {
                        // TODO: should this be classed as an error since
                        // rest2ldap assumes entries are all colocated?
                        return true;
                    }

                }).thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        synchronized (sequenceLock) {
                            if (request.getPageSize() > 0) {
                                try {
                                    final SimplePagedResultsControl control =
                                            result.getControl(SimplePagedResultsControl.DECODER, DECODE_OPTIONS);
                                    if (control != null && !control.getCookie().isEmpty()) {
                                        cookie = control.getCookie().toBase64String();
                                    }
                                } catch (final DecodeException e) {
                                    // FIXME: need some logging.
                                }
                            }
                            completeIfNecessary(SUCCESS, promise);
                        }
                    }
                }).thenOnException(new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(LdapException exception) {
                        synchronized (sequenceLock) {
                            completeIfNecessary(asResourceException(exception), promise);
                        }
                    }
                });

                return promise;
            }

            /** This method must be invoked with the sequenceLock held. */
            private void completeIfNecessary(
                    final ResourceException e, final PromiseImpl<QueryResponse, ResourceException> handler) {
                if (pendingResult == null) {
                    pendingResult = e;
                }
                completeIfNecessary(handler);
            }

            /**
             * Close out the query result set if there are no more pending
             * resources and the LDAP result has been received.
             * This method must be invoked with the sequenceLock held.
             */
            private void completeIfNecessary(final PromiseImpl<QueryResponse, ResourceException> handler) {
                if (pendingResourceCount == 0 && pendingResult != null && !resultSent) {
                    if (pendingResult == SUCCESS) {
                        handler.handleResult(Responses.newQueryResponse(cookie));
                    } else {
                        handler.handleException(pendingResult);
                    }
                    resultSent = true;
                }
            }
        };
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(
            final Context context, final String resourceId, final ReadRequest request) {
        final RequestState requestState = wrap(context);

        return requestState.getConnection()
                .thenAsync(new AsyncFunction<Connection, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(Connection connection)
                            throws ResourceException {
                        // Do the search.
                        SearchRequest searchRequest = searchRequest(requestState, resourceId, request.getFields());
                        return connection.searchSingleEntryAsync(searchRequest)
                                    .thenAsync(
                                        new AsyncFunction<SearchResultEntry, ResourceResponse, ResourceException>() {
                                            @Override
                                            public Promise<ResourceResponse, ResourceException> apply(
                                                    SearchResultEntry entry) throws ResourceException {
                                                return adaptEntry(requestState, entry);
                                            }
                                        },
                                        ldapExceptionToResourceException());
                    }
                })
                .thenFinally(close(requestState));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(
            final Context context, final String resourceId, final UpdateRequest request) {
        final RequestState requestState = wrap(context);
        final AtomicReference<Connection> connectionHolder = new AtomicReference<>();

        return requestState.getConnection().thenOnResult(saveConnection(connectionHolder))
                .thenAsync(new AsyncFunction<Connection, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(final Connection connection)
                            throws ResourceException {
                        List<JsonPointer> attrs = Collections.emptyList();
                        SearchRequest searchRequest = searchRequest(requestState, resourceId, attrs);
                        return connection.searchSingleEntryAsync(searchRequest)
                                .thenAsync(new AsyncFunction<SearchResultEntry, ResourceResponse, ResourceException>() {
                                    @Override
                                    public Promise<ResourceResponse, ResourceException> apply(
                                            final SearchResultEntry entry) {
                                        try {
                                            // Fail-fast if there is a version mismatch.
                                            ensureMVCCVersionMatches(entry, request.getRevision());

                                            // Create the modify request.
                                            final ModifyRequest modifyRequest = newModifyRequest(entry.getName());
                                            if (config.readOnUpdatePolicy() == CONTROLS) {
                                                final String[] attributes =
                                                        getLDAPAttributes(requestState, request.getFields());
                                                modifyRequest.addControl(
                                                        PostReadRequestControl.newControl(false, attributes));
                                            }
                                            if (config.usePermissiveModify()) {
                                                modifyRequest.addControl(
                                                        PermissiveModifyRequestControl.newControl(true));
                                            }
                                            addAssertionControl(modifyRequest, request.getRevision());

                                            // Determine the set of changes that need to be performed.
                                            return attributeMapper.update(
                                                        requestState, new JsonPointer(), entry, request.getContent())
                                                    .thenAsync(new AsyncFunction<
                                                            List<Modification>, ResourceResponse, ResourceException>() {
                                                        @Override
                                                        public Promise<ResourceResponse, ResourceException> apply(
                                                                List<Modification> modifications)
                                                                throws ResourceException {
                                                            if (modifications.isEmpty()) {
                                                                // No changes to be performed so just return
                                                                // the entry that we read.
                                                                return adaptEntry(requestState, entry);
                                                            }
                                                            // Perform the modify operation.
                                                            modifyRequest.getModifications().addAll(modifications);
                                                            return connection.applyChangeAsync(modifyRequest).thenAsync(
                                                                    postUpdateResultAsyncFunction(requestState),
                                                                    ldapExceptionToResourceException());
                                                        }
                                                    });
                                        } catch (final Exception e) {
                                            return Promises.newExceptionPromise(asResourceException(e));
                                        }
                                    }
                                }, ldapExceptionToResourceException());
                    }
                }).thenFinally(close(requestState));
    }

    private Promise<ResourceResponse, ResourceException> adaptEntry(
            final RequestState requestState, final Entry entry) {
        final String actualResourceId = nameStrategy.getResourceId(requestState, entry);
        final String revision = getRevisionFromEntry(entry);
        return attributeMapper.read(requestState, new JsonPointer(), entry)
                              .then(new Function<JsonValue, ResourceResponse, ResourceException>() {
                                  @Override
                                  public ResourceResponse apply(final JsonValue value) {
                                      return Responses.newResourceResponse(
                                              actualResourceId, revision, new JsonValue(value));
                                  }
                              });
    }

    private void addAssertionControl(final ChangeRecord request, final String expectedRevision)
            throws ResourceException {
        if (expectedRevision != null) {
            ensureMVCCSupported();
            request.addControl(AssertionRequestControl.newControl(true, Filter.equality(
                    etagAttribute.toString(), expectedRevision)));
        }
    }

    private AsyncFunction<Connection, DN, ResourceException> doUpdateFunction(
            final RequestState requestState, final String resourceId, final String revision) {
        return new AsyncFunction<Connection, DN, ResourceException>() {
            @Override
            public Promise<DN, ResourceException> apply(Connection connection) {
                final String ldapAttribute =
                        (etagAttribute != null && revision != null) ? etagAttribute.toString() : "1.1";
                final SearchRequest searchRequest =
                        nameStrategy.createSearchRequest(requestState, getBaseDN(), resourceId)
                                    .addAttribute(ldapAttribute);
                if (searchRequest.getScope().equals(SearchScope.BASE_OBJECT)) {
                    // There's no point in doing a search because we already know the DN.
                    return Promises.newResultPromise(searchRequest.getName());
                }
                return connection.searchSingleEntryAsync(searchRequest)
                        .thenAsync(new AsyncFunction<SearchResultEntry, DN, ResourceException>() {
                            @Override
                            public Promise<DN, ResourceException> apply(SearchResultEntry entry)
                                    throws ResourceException {
                                try {
                                    // Fail-fast if there is a version mismatch.
                                    ensureMVCCVersionMatches(entry, revision);
                                    // Perform update operation.
                                    return Promises.newResultPromise(entry.getName());
                                } catch (final Exception e) {
                                    return Promises.newExceptionPromise(asResourceException(e));
                                }
                            }
                        }, new AsyncFunction<LdapException, DN, ResourceException>() {
                            @Override
                            public Promise<DN, ResourceException> apply(LdapException ldapException)
                                    throws ResourceException {
                                return Promises.newExceptionPromise(asResourceException(ldapException));
                            }
                        });
            }
        };
    }

    private void ensureMVCCSupported() throws NotSupportedException {
        if (etagAttribute == null) {
            throw new NotSupportedException(
                    i18n("Multi-version concurrency control is not supported by this resource"));
        }
    }

    private void ensureMVCCVersionMatches(final Entry entry, final String expectedRevision) throws ResourceException {
        if (expectedRevision != null) {
            ensureMVCCSupported();
            final String actualRevision = entry.parseAttribute(etagAttribute).asString();
            if (actualRevision == null) {
                throw new PreconditionFailedException(i18n(
                        "The resource could not be accessed because it did not contain any "
                                + "version information, when the version '%s' was expected", expectedRevision));
            } else if (!expectedRevision.equals(actualRevision)) {
                throw new PreconditionFailedException(i18n(
                        "The resource could not be accessed because the expected version '%s' "
                                + "does not match the current version '%s'", expectedRevision, actualRevision));
            }
        }
    }

    private DN getBaseDN() {
        return baseDN;
    }

    /**
     * Determines the set of LDAP attributes to request in an LDAP read (search,
     * post-read), based on the provided list of JSON pointers.
     *
     * @param requestState
     *          The request state.
     * @param requestedAttributes
     *          The list of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private String[] getLDAPAttributes(
            final RequestState requestState, final Collection<JsonPointer> requestedAttributes) {
        // Get all the LDAP attributes required by the attribute mappers.
        final Set<String> requestedLDAPAttributes;
        if (requestedAttributes.isEmpty()) {
            // Full read.
            requestedLDAPAttributes = new LinkedHashSet<>();
            attributeMapper.getLDAPAttributes(requestState, new JsonPointer(), new JsonPointer(),
                    requestedLDAPAttributes);
        } else {
            // Partial read.
            requestedLDAPAttributes = new LinkedHashSet<>(requestedAttributes.size());
            for (final JsonPointer requestedAttribute : requestedAttributes) {
                attributeMapper.getLDAPAttributes(requestState, new JsonPointer(), requestedAttribute,
                        requestedLDAPAttributes);
            }
        }

        // Get the LDAP attributes required by the Etag and name stategies.
        nameStrategy.getLDAPAttributes(requestState, requestedLDAPAttributes);
        if (etagAttribute != null) {
            requestedLDAPAttributes.add(etagAttribute.toString());
        }
        return requestedLDAPAttributes.toArray(new String[requestedLDAPAttributes.size()]);
    }

    private String getRevisionFromEntry(final Entry entry) {
        return etagAttribute != null ? entry.parseAttribute(etagAttribute).asString() : null;
    }

    private AsyncFunction<Result, ResourceResponse, ResourceException> postUpdateResultAsyncFunction(
            final RequestState requestState) {
        // The handler which will be invoked for the LDAP add result.
        return new AsyncFunction<Result, ResourceResponse, ResourceException>() {
            @Override
            public Promise<ResourceResponse, ResourceException> apply(Result result) throws ResourceException {
                // FIXME: handle USE_SEARCH policy.
                Entry entry;
                try {
                    final PostReadResponseControl postReadControl =
                        result.getControl(PostReadResponseControl.DECODER, config.decodeOptions());
                    if (postReadControl != null) {
                        entry = postReadControl.getEntry();
                    } else {
                        final PreReadResponseControl preReadControl =
                            result.getControl(PreReadResponseControl.DECODER, config.decodeOptions());
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
                    return adaptEntry(requestState, entry);
                } else {
                    return Promises.newResultPromise(
                            Responses.newResourceResponse(null, null, new JsonValue(Collections.emptyMap())));
                }
            }
        };
    }

    private AsyncFunction<LdapException, ResourceResponse, ResourceException> ldapExceptionToResourceException() {
        return ldapToResourceException();
    }

    private <R> AsyncFunction<LdapException, R, ResourceException> ldapToResourceException() {
        // The handler which will be invoked for the LDAP add result.
        return new AsyncFunction<LdapException, R, ResourceException>() {
            @Override
            public Promise<R, ResourceException> apply(final LdapException ldapException) throws ResourceException {
                return Promises.newExceptionPromise(asResourceException(ldapException));
            }
        };
    }

    private SearchRequest searchRequest(
            final RequestState requestState, final String resourceId, final List<JsonPointer> requestedAttributes) {
        final String[] attributes = getLDAPAttributes(requestState, requestedAttributes);
        return nameStrategy.createSearchRequest(requestState, getBaseDN(), resourceId).addAttribute(attributes);
    }

    private RequestState wrap(final Context context) {
        return new RequestState(config, context);
    }

    private Runnable close(final RequestState requestState) {
        return new Runnable() {
            @Override
            public void run() {
                requestState.close();
            }
        };
    }

    private ResultHandler<Connection> saveConnection(final AtomicReference<Connection> connectionHolder) {
        return new ResultHandler<Connection>() {
            @Override
            public void handleResult(Connection connection) {
                connectionHolder.set(connection);
            }
        };
    }
}
