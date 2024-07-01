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
 * Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2017 Rosie Applications, Inc.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.Enum.NOT_ALLOWED_ON_NONLEAF;
import static org.forgerock.opendj.ldap.SearchScope.BASE_OBJECT;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.opendj.rest2ldap.FilterType.*;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.*;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.json.resource.ResourceException.FORBIDDEN;
import static org.forgerock.json.resource.ResourceException.newResourceException;
import static org.forgerock.json.resource.Responses.newActionResponse;
import static org.forgerock.json.resource.Responses.newQueryResponse;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.opendj.ldap.ByteString.valueOfBytes;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Filter.alwaysTrue;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.CONTROLS;
import static org.forgerock.opendj.rest2ldap.RoutingContext.newCollectionRoutingContext;
import static org.forgerock.opendj.rest2ldap.RoutingContext.newRoutingContext;
import static org.forgerock.opendj.rest2ldap.Utils.connectionFrom;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.opendj.rest2ldap.Utils.newNotSupportedException;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;
import static org.forgerock.util.Utils.asEnum;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.promise.Promises.when;

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

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
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
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ResultCode;
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
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.query.QueryFilter;
import org.forgerock.util.query.QueryFilterVisitor;

/** Implements the core CREST operations supported by singleton and collection sub-resources. */
final class SubResourceImpl {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** Dummy exception used for signalling search success. */
    private static final ResourceException SUCCESS = new UncategorizedException(0, null, null);

    private static final JsonPointer ROOT = new JsonPointer();

    private final DN baseDn;
    private final AttributeDescription etagAttribute;
    private final NamingStrategy namingStrategy;
    private final DecodeOptions decodeOptions;
    private final ReadOnUpdatePolicy readOnUpdatePolicy;
    private final boolean useSubtreeDelete;
    private final boolean usePermissiveModify;
    private final Resource resource;
    private final Attribute glueObjectClasses;
    private final boolean flattenSubtree;
    private final Filter baseSearchFilter;

    SubResourceImpl(final Rest2Ldap rest2Ldap, final DN baseDn, final Attribute glueObjectClasses,
                    final NamingStrategy namingStrategy, final Resource resource) {
        this(rest2Ldap, baseDn, glueObjectClasses, namingStrategy, resource, false, null);
    }

    SubResourceImpl(final Rest2Ldap rest2Ldap, final DN baseDn, final Attribute glueObjectClasses,
                    final NamingStrategy namingStrategy, final Resource resource,
                    final boolean flattenSubtree, final Filter baseSearchFilter) {
        this.readOnUpdatePolicy = rest2Ldap.getOptions().get(READ_ON_UPDATE_POLICY);
        this.useSubtreeDelete = rest2Ldap.getOptions().get(USE_SUBTREE_DELETE);
        this.usePermissiveModify = rest2Ldap.getOptions().get(USE_PERMISSIVE_MODIFY);
        this.etagAttribute = rest2Ldap.getOptions().get(USE_MVCC)
                ? AttributeDescription.valueOf(rest2Ldap.getOptions().get(MVCC_ATTRIBUTE)) : null;
        this.decodeOptions = rest2Ldap.getOptions().get(DECODE_OPTIONS);
        this.baseDn = baseDn;
        this.glueObjectClasses = glueObjectClasses;
        this.namingStrategy = namingStrategy;
        this.resource = resource;
        this.flattenSubtree = flattenSubtree;
        this.baseSearchFilter = baseSearchFilter;
    }

    Promise<ActionResponse, ResourceException> action(
            final Context context, final String resourceId, final ActionRequest request) {
        try {
            final Action action = asEnum(request.getAction(), Action.class);
            if (resource.hasSupportedAction(action)) {
                switch (action) {
                case RESET_PASSWORD:
                    return resetPassword(context, resourceId);
                case MODIFY_PASSWORD:
                    return modifyPassword(context, resourceId, request);
                }
            }
        } catch (final IllegalArgumentException ignored) {
            // fall-through
        }
        return newNotSupportedException(ERR_ACTION_NOT_SUPPORTED.get(request.getAction())).asPromise();

    }

    private Promise<ActionResponse, ResourceException> resetPassword(final Context context, final String resourceId) {
        if (!context.containsContext(ClientContext.class)
                || !context.asContext(ClientContext.class).isSecure()) {
            return newResourceException(FORBIDDEN, ERR_PASSWORD_RESET_SECURE_CONNECTION.get().toString()).asPromise();
        }
        if (!context.containsContext(SecurityContext.class)
                || context.asContext(SecurityContext.class).getAuthenticationId() == null) {
            return newResourceException(FORBIDDEN, ERR_PASSWORD_RESET_USER_AUTHENTICATED.get().toString()).asPromise();
        }

        final Connection connection = connectionFrom(context);
        return resolveResourceDnAndType(context, connection, resourceId, null)
                .thenAsync(new AsyncFunction<RoutingContext, PasswordModifyExtendedResult, ResourceException>() {
                    @Override
                    public Promise<PasswordModifyExtendedResult, ResourceException> apply(RoutingContext dnAndType) {
                        final PasswordModifyExtendedRequest pwdModifyRequest =
                                newPasswordModifyExtendedRequest().setUserIdentity("dn: " + dnAndType.getDn());
                        return connection.extendedRequestAsync(pwdModifyRequest)
                                         .thenCatchAsync(adaptLdapException(PasswordModifyExtendedResult.class));
                    }
                }).thenAsync(new AsyncFunction<PasswordModifyExtendedResult, ActionResponse, ResourceException>() {
                    @Override
                    public Promise<ActionResponse, ResourceException> apply(PasswordModifyExtendedResult r) {
                        final JsonValue result = new JsonValue(new LinkedHashMap<>());
                        final byte[] generatedPwd = r.getGeneratedPassword();
                        if (generatedPwd != null) {
                            result.put("generatedPassword", valueOfBytes(generatedPwd).toString());
                        }
                        return newActionResponse(result).asPromise();
                    }
                });
    }

    private Promise<ActionResponse, ResourceException> modifyPassword(
            final Context context, final String resourceId, final ActionRequest request) {
        if (!context.containsContext(ClientContext.class)
                || !context.asContext(ClientContext.class).isSecure()) {
            return newResourceException(FORBIDDEN, ERR_PASSWORD_MODIFY_SECURE_CONNECTION.get().toString()).asPromise();
        }
        if (!context.containsContext(SecurityContext.class)
                || context.asContext(SecurityContext.class).getAuthenticationId() == null) {
            return newResourceException(FORBIDDEN, ERR_PASSWORD_MODIFY_USER_AUTHENTICATED.get().toString()).asPromise();
        }

        final JsonValue jsonContent = request.getContent();
        final String oldPassword;
        final String newPassword;
        try {
            oldPassword = jsonContent.get("oldPassword").required().asString();
            newPassword = jsonContent.get("newPassword").required().asString();
        } catch (JsonValueException e) {
            return newBadRequestException(ERR_PASSWORD_MODIFY_REQUEST_IS_INVALID.get(), e).asPromise();
        }

        final Connection connection = connectionFrom(context);
        return resolveResourceDnAndType(context, connection, resourceId, null)
                .thenAsync(new AsyncFunction<RoutingContext, PasswordModifyExtendedResult, ResourceException>() {
                    @Override
                    public Promise<PasswordModifyExtendedResult, ResourceException> apply(RoutingContext dnAndType) {
                        final PasswordModifyExtendedRequest pwdModifyRequest = newPasswordModifyExtendedRequest()
                                .setUserIdentity("dn: " + dnAndType.getDn())
                                .setOldPassword(asBytes(oldPassword))
                                .setNewPassword(asBytes(newPassword));
                        return connection.extendedRequestAsync(pwdModifyRequest)
                                         .thenCatchAsync(adaptLdapException(PasswordModifyExtendedResult.class));
                    }
                }).thenAsync(new AsyncFunction<PasswordModifyExtendedResult, ActionResponse, ResourceException>() {
                    @Override
                    public Promise<ActionResponse, ResourceException> apply(PasswordModifyExtendedResult r) {
                        // Empty response.
                        return newActionResponse(new JsonValue(new LinkedHashMap<>(0))).asPromise();
                    }
                });
    }

    private byte[] asBytes(final String s) {
        return s != null ? s.getBytes(StandardCharsets.UTF_8) : null;
    }

    Promise<ResourceResponse, ResourceException> create(final Context context, final CreateRequest  request) {
        // First determine the type of resource being created.
        final Resource subType;
        try {
            subType = resource.resolveSubTypeFromJson(request.getContent());
        } catch (final ResourceException e) {
            return e.asPromise();
        }

        // Temporary routing context which will be used for encoding LDAP attributes. Note that the DN represents the
        // DN of the collection, not the resource being created. The DN of the resource can only be determined once
        // the LDAP attributes have been encoded.
        final RoutingContext parentDnAndType = newCollectionRoutingContext(context, baseDn, subType);

        // Now build the LDAP representation and add it.
        final Connection connection = connectionFrom(context);
        return subType.getPropertyMapper()
                      .create(parentDnAndType, subType, ROOT, request.getContent())
                      .thenAsync(new AsyncFunction<List<Attribute>, ResourceResponse, ResourceException>() {
                          @Override
                          public Promise<ResourceResponse, ResourceException> apply(final List<Attribute> attributes) {
                              // Perform add operation.
                              final AddRequest addRequest = newAddRequest(DN.rootDN());
                              addRequest.addAttribute(subType.getObjectClassAttribute());
                              for (final Attribute attribute : attributes) {
                                  addRequest.addAttribute(attribute);
                              }
                              try {
                                  namingStrategy.encodeResourceId(baseDn, request.getNewResourceId(), addRequest);
                              } catch (final ResourceException e) {
                                  logger.error(raw(e.getLocalizedMessage()), e);
                                  return e.asPromise();
                              }
                              if (readOnUpdatePolicy == CONTROLS) {
                                  final Set<String> ldapAttributes =
                                          getLdapAttributesForKnownType(request.getFields(), subType);
                                  addRequest.addControl(PostReadRequestControl.newControl(false, ldapAttributes));
                              }
                              // Use a routing context which refers to the created entry when computing the response.
                              final RoutingContext dnAndType =
                                      newRoutingContext(context, addRequest.getName(), subType);
                              return connection.addAsync(addRequest)
                                               .thenCatchAsync(lazilyAddGlueEntry(connection, addRequest))
                                               .thenAsync(encodeUpdateResourceResponse(dnAndType, subType),
                                                          adaptLdapException(ResourceResponse.class));
                          }
                      });
    }

    /**
     * A resource and sub-resource may be separated by a "glue" entry in LDAP. This method detects when a glue entry
     * is missing, creates it, and then retries the original add operation. As a concrete example, consider the
     * backend configuration entry "ds-cfg-backend-id=userRoot,cn=backends,cn=config". Since its indexes are located
     * beneath "cn=Indexes,ds-cfg-backend-id=userRoot,cn=backends,cn=config" we need to add "cn=Indexes" before
     * adding an index entry.
     */
    private AsyncFunction<LdapException, Result, LdapException> lazilyAddGlueEntry(final Connection connection,
                                                                                   final AddRequest addRequest) {
        return new AsyncFunction<LdapException, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(final LdapException e) throws LdapException {
                if (glueObjectClasses != null && e instanceof EntryNotFoundException) {
                    // The parent glue entry may be missing - lazily create it.
                    final AddRequest glueAddRequest = newAddRequest(baseDn);
                    glueAddRequest.addAttribute(glueObjectClasses);
                    glueAddRequest.addAttribute(baseDn.rdn().getFirstAVA().toAttribute());
                    return connection.addAsync(glueAddRequest)
                                     .thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                                         @Override
                                         public Promise<Result, LdapException> apply(final Result value) {
                                             return connection.addAsync(addRequest);
                                         }
                                     });
                }
                // Something else happened, so rethrow.
                throw e;
            }
        };
    }

    Promise<ResourceResponse, ResourceException> delete(
            final Context context, final String resourceId, final DeleteRequest request) {
        final Connection connection = connectionFrom(context);
        return resolveResourceDnAndType(context, connection, resourceId, request.getRevision())
                .thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(final RoutingContext dnAndType)
                            throws ResourceException {
                        final ChangeRecord deleteRequest = newDeleteRequest(dnAndType.getDn());
                        if (readOnUpdatePolicy == CONTROLS) {
                            final Set<String> attributes =
                                    getLdapAttributesForKnownType(request.getFields(), dnAndType.getType());
                            deleteRequest.addControl(PreReadRequestControl.newControl(false, attributes));
                        }
                        if (resource.mayHaveSubResources() && useSubtreeDelete) {
                            // Non-critical so that we can detect failure and retry without the control. Some backends,
                            // such as cn=config, do not support the subtree delete control.
                            deleteRequest.addControl(SubtreeDeleteRequestControl.newControl(false));
                        }
                        addAssertionControl(deleteRequest, request.getRevision());
                        return connection.applyChangeAsync(deleteRequest)
                                         .thenCatchAsync(deleteSubtreeWithoutUsingSubtreeDeleteControl(connection,
                                                                                                       deleteRequest))
                                         .thenAsync(encodeUpdateResourceResponse(dnAndType, dnAndType.getType()),
                                                    adaptLdapException(ResourceResponse.class));
                    }
                });
    }

    /**
     * Detects whether a delete request failed because the targeted entry has children and the subtree delete control
     * could not be applied (e.g. due to ACIs or lack of support in the backend). On failure, fall-back to a search
     * and then a recursive bottom up delete of all subordinate entries, before finally retrying the original delete
     * request.
     */
    private AsyncFunction<LdapException, Result, LdapException> deleteSubtreeWithoutUsingSubtreeDeleteControl(
            final Connection connection, final ChangeRecord deleteRequest) {
        return new AsyncFunction<LdapException, Result, LdapException>() {
            @Override
            public Promise<Result, LdapException> apply(final LdapException e) throws LdapException {
                if (e.getResult().getResultCode().asEnum() != NOT_ALLOWED_ON_NONLEAF
                        || !resource.mayHaveSubResources()) {
                    throw e;
                }

                // Perform a subtree search and then delete entries one by one.
                final SearchRequest subordinates = newSearchRequest(deleteRequest.getName(),
                                                                    SearchScope.SUBORDINATES,
                                                                    Filter.objectClassPresent(),
                                                                    "1.1");

                // This list does not need synchronization because search result notification is synchronized.
                final List<DN> subordinateEntries = new ArrayList<>();
                return connection.searchAsync(subordinates, new SearchResultHandler() {
                    @Override
                    public boolean handleEntry(final SearchResultEntry entry) {
                        subordinateEntries.add(entry.getName());
                        return true;
                    }

                    @Override
                    public boolean handleReference(final SearchResultReference reference) {
                        return false;
                    }
                }).thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                    @Override
                    public Promise<Result, LdapException> apply(final Result result) {
                        // Sort the entries in hierarchical order and then delete them in reverse, thus
                        // always deleting children before parents.
                        Collections.sort(subordinateEntries);
                        LdapPromise<Result> promise = newSuccessfulLdapPromise(newResult(ResultCode.SUCCESS));
                        for (int i = subordinateEntries.size() - 1; i >= 0; i--) {
                            final ChangeRecord subordinateDelete = newDeleteRequest(subordinateEntries.get(i));
                            promise = promise.thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                                @Override
                                public Promise<Result, LdapException> apply(final Result result) {
                                    return connection.applyChangeAsync(subordinateDelete);
                                }
                            });
                        }
                        // And finally retry the original delete request.
                        return promise.thenAsync(new AsyncFunction<Result, Result, LdapException>() {
                            @Override
                            public Promise<Result, LdapException> apply(final Result result) {
                                return connection.applyChangeAsync(deleteRequest);
                            }
                        });
                    }
                });
            }
        };
    }

    Promise<ResourceResponse, ResourceException> patch(
            final Context context, final String resourceId, final PatchRequest request) {
        final Connection connection = connectionFrom(context);
        final AtomicReference<RoutingContext> dnAndTypeHolder = new AtomicReference<>();
        return resolveResourceDnAndType(context, connection, resourceId, request.getRevision())
                .thenAsync(new AsyncFunction<RoutingContext, List<List<Modification>>, ResourceException>() {
                    @Override
                    public Promise<List<List<Modification>>, ResourceException> apply(final RoutingContext dnAndType)
                            throws ResourceException {
                        dnAndTypeHolder.set(dnAndType);

                        // Convert the patch operations to LDAP modifications.
                        final List<Promise<List<Modification>, ResourceException>> promises =
                                new ArrayList<>(request.getPatchOperations().size());
                        final Resource subType = dnAndType.getType();
                        final PropertyMapper propertyMapper = subType.getPropertyMapper();
                        for (final PatchOperation operation : request.getPatchOperations()) {
                            promises.add(propertyMapper.patch(dnAndType, subType, ROOT, operation));
                        }
                        return when(promises);
                    }
                }).thenAsync(new AsyncFunction<List<List<Modification>>, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(final List<List<Modification>> result)
                            throws ResourceException {
                        // The patch operations have been converted successfully.
                        final RoutingContext dnAndType = dnAndTypeHolder.get();
                        final ModifyRequest modifyRequest = newModifyRequest(dnAndType.getDn());

                        // Add the modifications.
                        for (final List<Modification> modifications : result) {
                            if (modifications != null) {
                                modifyRequest.getModifications().addAll(modifications);
                            }
                        }

                        final Resource subType = dnAndType.getType();
                        final Set<String> attributes = getLdapAttributesForKnownType(request.getFields(), subType);
                        if (modifyRequest.getModifications().isEmpty()) {
                            // This patch is a no-op so just read the entry and check its version.
                            return connection.readEntryAsync(dnAndType.getDn(), attributes)
                                             .thenAsync(encodeEmptyPatchResourceResponse(dnAndType, subType, request),
                                                        adaptLdapException(ResourceResponse.class));
                        } else {
                            // Add controls and perform the modify request.
                            if (readOnUpdatePolicy == CONTROLS) {
                                modifyRequest.addControl(PostReadRequestControl.newControl(false, attributes));
                            }
                            if (usePermissiveModify) {
                                modifyRequest.addControl(PermissiveModifyRequestControl.newControl(true));
                            }
                            addAssertionControl(modifyRequest, request.getRevision());
                            return connection.applyChangeAsync(modifyRequest)
                                             .thenAsync(encodeUpdateResourceResponse(dnAndType, subType),
                                                        adaptLdapException(ResourceResponse.class));
                        }
                    }
                });
    }

    private AsyncFunction<Entry, ResourceResponse, ResourceException> encodeEmptyPatchResourceResponse(
            final Context context, final Resource resource, final PatchRequest request) {
        return new AsyncFunction<Entry, ResourceResponse, ResourceException>() {
            @Override
            public Promise<ResourceResponse, ResourceException> apply(Entry entry) throws ResourceException {
                try {
                    ensureMvccVersionMatches(entry, request.getRevision());
                    return encodeResourceResponse(context, resource, entry);
                } catch (final Exception e) {
                    return asResourceException(e).asPromise();
                }
            }
        };
    }

    Promise<QueryResponse, ResourceException> query(
            final Context context, final QueryRequest request, final QueryResourceHandler resourceHandler) {
        return getLdapFilter(context, request.getQueryFilter())
                .then(applyBaseSearchFilter())
                .thenAsync(runQuery(context, request, resourceHandler));
    }

    /**
     * Generates a function that applies any base filter that this sub-resource may have been
     * initialized with.
     *
     * @return  The function to invoke to apply a base filter, if one has been specified.
     */
    private Function<Filter, Filter, ResourceException> applyBaseSearchFilter() {
        return new Function<Filter, Filter, ResourceException>() {
            @Override
            public Filter apply(final Filter requestFilter) throws ResourceException {
                final Filter baseSearchFilter = SubResourceImpl.this.baseSearchFilter,
                             searchFilter;

                if (baseSearchFilter != null) {
                    searchFilter = Filter.and(baseSearchFilter, requestFilter);
                } else {
                    searchFilter = requestFilter;
                }

                return searchFilter;
            }
        };
    }

    // FIXME: supporting assertions against sub-type properties.
    private Promise<Filter, ResourceException> getLdapFilter(
            final Context context, final QueryFilter<JsonPointer> queryFilter) {
        if (queryFilter == null) {
            return new BadRequestException(ERR_QUERY_BY_ID_OR_EXPRESSION_NOT_SUPPORTED.get().toString()).asPromise();
        }

        // Temporary routing context which will be used for encoding the LDAP filter.
        final RoutingContext parentDnAndType = newCollectionRoutingContext(context, baseDn, resource);
        final PropertyMapper propertyMapper = resource.getPropertyMapper();
        final QueryFilterVisitor<Promise<Filter, ResourceException>, Void, JsonPointer> visitor =
                new QueryFilterVisitor<Promise<Filter, ResourceException>, Void, JsonPointer>() {
                    @Override
                    public Promise<Filter, ResourceException> visitAndFilter(
                            final Void unused, final List<QueryFilter<JsonPointer>> subFilters) {
                        final List<Promise<Filter, ResourceException>> promises = new ArrayList<>(subFilters.size());

                        for (final QueryFilter<JsonPointer> subFilter : subFilters) {
                            promises.add(subFilter.accept(this, unused));
                        }

                        return when(promises).then(new Function<List<Filter>, Filter, ResourceException>() {
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
                        return newResultPromise(toFilter(value));
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitContainsFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, CONTAINS, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitEqualsFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, EQUAL_TO, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitExtendedMatchFilter(final Void unused,
                                                                                       final JsonPointer field,
                                                                                       final String operator,
                                                                                       final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, EXTENDED, operator, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitGreaterThanFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, GREATER_THAN, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitGreaterThanOrEqualToFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, GREATER_THAN_OR_EQUAL_TO, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitLessThanFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, LESS_THAN, null, valueAssertion);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitLessThanOrEqualToFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, LESS_THAN_OR_EQUAL_TO, null, valueAssertion);
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
                    public Promise<Filter, ResourceException> visitOrFilter(
                            final Void unused, final List<QueryFilter<JsonPointer>> subFilters) {
                        final List<Promise<Filter, ResourceException>> promises = new ArrayList<>(subFilters.size());
                        for (final QueryFilter<JsonPointer> subFilter : subFilters) {
                            promises.add(subFilter.accept(this, unused));
                        }

                        return when(promises).then(new Function<List<Filter>, Filter, ResourceException>() {
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
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, PRESENT, null, null);
                    }

                    @Override
                    public Promise<Filter, ResourceException> visitStartsWithFilter(
                            final Void unused, final JsonPointer field, final Object valueAssertion) {
                        return propertyMapper.getLdapFilter(
                                parentDnAndType, resource, ROOT, field, STARTS_WITH, null, valueAssertion);
                    }
                };
        
        // Note that the returned LDAP filter may be null if it could not be mapped by any property mappers.
        return queryFilter.accept(visitor, null);
    }

    private AsyncFunction<Filter, QueryResponse, ResourceException> runQuery(
            final Context context, final QueryRequest request, final QueryResourceHandler resourceHandler) {
        return new AsyncFunction<Filter, QueryResponse, ResourceException>() {
            // The following fields are guarded by sequenceLock. In addition, the sequenceLock ensures that
            // we send one JSON resource at a time back to the client.
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
                    return newQueryResponse().asPromise();
                }
                final PromiseImpl<QueryResponse, ResourceException> promise = PromiseImpl.create();
                // Perform the search.
                final String[] attributes = getLdapAttributesForUnknownType(request.getFields()).toArray(new String[0]);
                final Filter searchFilter = ldapFilter == Filter.alwaysTrue() ? Filter.objectClassPresent()
                        : ldapFilter;
                final SearchRequest searchRequest = createSearchRequest(searchFilter, attributes);

                // Add the page results control. We can support the page offset by reading the next offset pages, or
                // offset x page size resources.
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

                connectionFrom(context).searchAsync(searchRequest, new SearchResultHandler() {
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
                        final String id = namingStrategy.decodeResourceId(entry);
                        final String revision = getRevisionFromEntry(entry);
                        final Resource subType = resource.resolveSubTypeFromObjectClasses(entry);
                        final RoutingContext dnAndType = newRoutingContext(context, entry.getName(), subType);
                        final PropertyMapper propertyMapper = subType.getPropertyMapper();
                        propertyMapper.read(dnAndType, subType, ROOT, entry)
                                      .thenOnResult(new ResultHandler<JsonValue>() {
                                          @Override
                                          public void handleResult(final JsonValue result) {
                                              synchronized (sequenceLock) {
                                                  pendingResourceCount--;
                                                  if (!resultSent) {
                                                      resourceHandler.handleResource(
                                                              newResourceResponse(id, revision, result));
                                                  }
                                                  completeIfNecessary(promise);
                                              }
                                          }
                                      })
                                      .thenOnException(new ExceptionHandler<ResourceException>() {
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
                        // TODO: should this be classed as an error since rest2ldap assumes entries are all colocated?
                        return true;
                    }

                }).thenOnResult(new ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        synchronized (sequenceLock) {
                            if (request.getPageSize() > 0) {
                                try {
                                    final SimplePagedResultsControl control =
                                            result.getControl(SimplePagedResultsControl.DECODER, decodeOptions);
                                    if (control != null && !control.getCookie().isEmpty()) {
                                        cookie = control.getCookie().toBase64String();
                                    }
                                } catch (final DecodeException e) {
                                    logger.error(ERR_DECODING_CONTROL.get(e.getLocalizedMessage()), e);
                                }
                            }
                            completeIfNecessary(SUCCESS, promise);
                        }
                    }
                }).thenOnException(new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(final LdapException e) {
                        synchronized (sequenceLock) {
                            if (glueObjectClasses != null && e instanceof EntryNotFoundException) {
                                // Glue entry does not exist, so treat this as an empty result set.
                                completeIfNecessary(SUCCESS, promise);
                            } else {
                                completeIfNecessary(asResourceException(e), promise);
                            }
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
                        handler.handleResult(newQueryResponse(cookie));
                    } else {
                        handler.handleException(pendingResult);
                    }
                    resultSent = true;
                }
            }
        };
    }

    Promise<ResourceResponse, ResourceException> read(
            final Context context, final String resourceId, final ReadRequest request) {
        final Connection connection = connectionFrom(context);
        return connection.searchSingleEntryAsync(searchRequestForUnknownType(resourceId, request.getFields()))
                         .thenCatchAsync(adaptLdapException(SearchResultEntry.class))
                         .thenAsync(new AsyncFunction<SearchResultEntry, ResourceResponse, ResourceException>() {
                             @Override
                             public Promise<ResourceResponse, ResourceException> apply(SearchResultEntry entry) {
                                 final Resource subType = resource.resolveSubTypeFromObjectClasses(entry);
                                 final RoutingContext dnAndType = newRoutingContext(context, entry.getName(), subType);
                                 return encodeResourceResponse(dnAndType, subType, entry);
                             }
                         });
    }

    Promise<ResourceResponse, ResourceException> update(
            final Context context, final String resourceId, final UpdateRequest request) {
        final Connection connection = connectionFrom(context);
        final AtomicReference<Entry> entryHolder = new AtomicReference<>();
        final AtomicReference<RoutingContext> dnAndTypeHolder = new AtomicReference<>();
        return connection
                .searchSingleEntryAsync(searchRequestForUnknownType(resourceId, Collections.<JsonPointer>emptyList()))
                .thenCatchAsync(adaptLdapException(SearchResultEntry.class))
                .thenAsync(new AsyncFunction<SearchResultEntry, List<Modification>, ResourceException>() {
                    @Override
                    public Promise<List<Modification>, ResourceException> apply(final SearchResultEntry entry)
                            throws ResourceException {
                        entryHolder.set(entry);

                        // Fail-fast if there is a version mismatch.
                        ensureMvccVersionMatches(entry, request.getRevision());

                        // Determine the type of resource and set of changes that need to be performed.
                        final Resource subType = resource.resolveSubTypeFromObjectClasses(entry);
                        final RoutingContext dnAndType = newRoutingContext(context, entry.getName(), subType);
                        dnAndTypeHolder.set(dnAndType);
                        final PropertyMapper propertyMapper = subType.getPropertyMapper();
                        return propertyMapper.update(dnAndType, subType , ROOT, entry, request.getContent());
                    }
                }).thenAsync(new AsyncFunction<List<Modification>, ResourceResponse, ResourceException>() {
                    @Override
                    public Promise<ResourceResponse, ResourceException> apply(List<Modification> modifications)
                            throws ResourceException {
                        final RoutingContext dnAndType = dnAndTypeHolder.get();
                        final Resource subType = dnAndType.getType();
                        if (modifications.isEmpty()) {
                            // No changes to be performed so just return the entry that we read.
                            return encodeResourceResponse(dnAndType, subType, entryHolder.get());
                        }
                        // Perform the modify operation.
                        final ModifyRequest modifyRequest = newModifyRequest(entryHolder.get().getName());
                        if (readOnUpdatePolicy == CONTROLS) {
                            final Set<String> attributes = getLdapAttributesForKnownType(request.getFields(), subType);
                            modifyRequest.addControl(PostReadRequestControl.newControl(false, attributes));
                        }
                        if (usePermissiveModify) {
                            modifyRequest.addControl(PermissiveModifyRequestControl.newControl(true));
                        }
                        addAssertionControl(modifyRequest, request.getRevision());
                        modifyRequest.getModifications().addAll(modifications);
                        return connection.applyChangeAsync(modifyRequest)
                                         .thenAsync(encodeUpdateResourceResponse(dnAndType, subType),
                                                    adaptLdapException(ResourceResponse.class));
                    }
                });
    }

    private Promise<ResourceResponse, ResourceException> encodeResourceResponse(
            final Context context, final Resource resource, final Entry entry) {
        final PropertyMapper propertyMapper = resource.getPropertyMapper();
        return propertyMapper.read(context, resource, ROOT, entry)
                             .then(new Function<JsonValue, ResourceResponse, ResourceException>() {
                                 @Override
                                 public ResourceResponse apply(final JsonValue value) {
                                     final String revision = getRevisionFromEntry(entry);
                                     final String actualResourceId = namingStrategy.decodeResourceId(entry);
                                     return newResourceResponse(actualResourceId, revision, new JsonValue(value));
                                 }
                             });
    }

    private void addAssertionControl(final ChangeRecord request, final String expectedRevision)
            throws ResourceException {
        if (expectedRevision != null) {
            ensureMvccSupported();
            final Filter filter = Filter.equality(etagAttribute.toString(), expectedRevision);
            request.addControl(AssertionRequestControl.newControl(true, filter));
        }
    }

    private Promise<RoutingContext, ResourceException> resolveResourceDnAndType(
            final Context context, final Connection connection, final String resourceId, final String revision) {
        final SearchRequest searchRequest = namingStrategy.createSearchRequest(baseDn, resourceId);
        if (searchRequest.getScope().equals(BASE_OBJECT) && !resource.hasSubTypes()) {
            // There's no point in doing a search because we already know the DN and sub-resources.
            return newResultPromise(newRoutingContext(context, searchRequest.getName(), resource));
        }
        if (etagAttribute != null && revision != null) {
            searchRequest.addAttribute(etagAttribute.toString());
        }
        // The resource type will be resolved from the LDAP entry's objectClass.
        searchRequest.addAttribute("objectClass");
        return connection.searchSingleEntryAsync(searchRequest)
                         .thenAsync(new AsyncFunction<SearchResultEntry, RoutingContext, ResourceException>() {
                             @Override
                             public Promise<RoutingContext, ResourceException> apply(final SearchResultEntry entry)
                                     throws ResourceException {
                                 // Fail-fast if there is a version mismatch.
                                 ensureMvccVersionMatches(entry, revision);
                                 final Resource subType = resource.resolveSubTypeFromObjectClasses(entry);
                                 return newResultPromise(newRoutingContext(context, entry.getName(), subType));
                             }
                         }, adaptLdapException(RoutingContext.class));
    }

    private void ensureMvccSupported() throws NotSupportedException {
        if (etagAttribute == null) {
            throw newNotSupportedException(ERR_MVCC_NOT_SUPPORTED.get());
        }
    }

    private void ensureMvccVersionMatches(final Entry entry, final String expectedRevision) throws ResourceException {
        if (expectedRevision != null) {
            ensureMvccSupported();
            final String actualRevision = entry.parseAttribute(etagAttribute).asString();
            if (actualRevision == null) {
                throw new PreconditionFailedException(ERR_MVCC_NO_VERSION_INFORMATION.get(expectedRevision).toString());
            } else if (!expectedRevision.equals(actualRevision)) {
                throw new PreconditionFailedException(
                        ERR_MVCC_VERSIONS_MISMATCH.get(expectedRevision, actualRevision).toString());
            }
        }
    }

    private Set<String> getLdapAttributesForUnknownType(final Collection<JsonPointer> fields) {
        final Set<String> ldapAttributes = getLdapAttributesForKnownType(fields, resource);
        getLdapAttributesForUnknownType(fields, resource, ldapAttributes);
        return ldapAttributes;
    }

    private void getLdapAttributesForUnknownType(final Collection<JsonPointer> fields, final Resource resource,
                                                 final Set<String> ldapAttributes) {
        for (final Resource subType : resource.getSubTypes()) {
            addLdapAttributesForFields(fields, subType, ldapAttributes);
            getLdapAttributesForUnknownType(fields, subType, ldapAttributes);
        }
    }

    private Set<String> getLdapAttributesForKnownType(final Collection<JsonPointer> fields, final Resource resource) {
        // Includes the LDAP attributes required by the type, etag, and name strategies.
        final Set<String> ldapAttributes = new LinkedHashSet<>();
        ldapAttributes.add("objectClass");
        final String resourceIdLdapAttribute = namingStrategy.getResourceIdLdapAttribute();
        if (resourceIdLdapAttribute != null) {
            ldapAttributes.add(resourceIdLdapAttribute);
        }
        if (etagAttribute != null) {
            ldapAttributes.add(etagAttribute.toString());
        }
        addLdapAttributesForFields(fields, resource, ldapAttributes);
        return ldapAttributes;
    }

    /** Includes the LDAP attributes required for the specified JSON fields for all sub-types. */
    private void addLdapAttributesForFields(final Collection<JsonPointer> fields, final Resource resource,
                                            final Set<String> ldapAttributes) {
        final PropertyMapper propertyMapper = resource.getPropertyMapper();
        if (fields.isEmpty()) {
            // Full read.
            propertyMapper.getLdapAttributes(ROOT, ROOT, ldapAttributes);
        } else {
            // Partial read.
            for (final JsonPointer field : fields) {
                propertyMapper.getLdapAttributes(ROOT, field, ldapAttributes);
            }
        }
    }

    private String getRevisionFromEntry(final Entry entry) {
        return etagAttribute != null ? entry.parseAttribute(etagAttribute).asString() : null;
    }

    private AsyncFunction<Result, ResourceResponse, ResourceException> encodeUpdateResourceResponse(
            final Context context, final Resource resource) {
        return new AsyncFunction<Result, ResourceResponse, ResourceException>() {
            @Override
            public Promise<ResourceResponse, ResourceException> apply(Result result) {
                // FIXME: handle USE_SEARCH policy.
                try {
                    final PostReadResponseControl postReadControl =
                            result.getControl(PostReadResponseControl.DECODER, decodeOptions);
                    if (postReadControl != null) {
                        return encodeResourceResponse(context, resource, postReadControl.getEntry());
                    }
                    final PreReadResponseControl preReadControl =
                            result.getControl(PreReadResponseControl.DECODER, decodeOptions);
                    if (preReadControl != null) {
                        return encodeResourceResponse(context, resource, preReadControl.getEntry());
                    }
                } catch (final DecodeException e) {
                    logger.error(ERR_DECODING_CONTROL.get(e.getLocalizedMessage()), e);
                }
                // Return an empty resource response.
                return newResourceResponse(null, null, new JsonValue(Collections.emptyMap())).asPromise();
            }
        };
    }

    private SearchRequest searchRequestForUnknownType(final String resourceId, final List<JsonPointer> fields) {
        final String[] attributes = getLdapAttributesForUnknownType(fields).toArray(new String[0]);
        return namingStrategy.createSearchRequest(baseDn, resourceId).addAttribute(attributes);
    }

    /**
     * Creates a request to search LDAP for entries that match the provided search filter, and
     * the specified attributes.
     *
     * If the subtree flattening is enabled, the search request will encompass the whole subtree.
     *
     * @param   searchFilter
     *          The filter that entries must match to be returned.
     * @param   desiredAttributes
     *          The names of the attributes to be included with each entry.
     *
     * @return  The resulting search request.
     */
    private SearchRequest createSearchRequest(Filter searchFilter, String[] desiredAttributes) {
        final SearchScope searchScope;
        final SearchRequest searchRequest;

        if (SubResourceImpl.this.flattenSubtree) {
            searchScope = SearchScope.SUBORDINATES;
        } else {
            searchScope = SearchScope.SINGLE_LEVEL;
        }

        searchRequest = newSearchRequest(baseDn, searchScope, searchFilter, desiredAttributes);

        return searchRequest;
    }

    @SuppressWarnings("unused")
    private static <R> AsyncFunction<LdapException, R, ResourceException> adaptLdapException(final Class<R> clazz) {
        return new AsyncFunction<LdapException, R, ResourceException>() {
            @Override
            public Promise<R, ResourceException> apply(final LdapException ldapException) {
                return asResourceException(ldapException).asPromise();
            }
        };
    }
}
