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
 *
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;
import static org.forgerock.json.resource.RouteMatchers.requestUriMatcher;
import static org.forgerock.opendj.ldap.Filter.objectClassPresent;
import static org.forgerock.opendj.ldap.SearchScope.BASE_OBJECT;
import static org.forgerock.opendj.ldap.SearchScope.SINGLE_LEVEL;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.util.promise.Promises.newResultPromise;

import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * Defines a one-to-many relationship between a parent resource and its children. Removal of the parent resource
 * implies that the children (the sub-resources) are also removed. Collections support all request types.
 */
public final class SubResourceCollection extends SubResource {
    /** The LDAP object classes associated with the glue entries forming the DN template. */
    private final Attribute glueObjectClasses = new LinkedAttribute("objectClass");

    private NamingStrategy namingStrategy;

    SubResourceCollection(final String resourceId) {
        super(resourceId);
        useClientDnNaming("uid");
    }

    /**
     * Indicates that the JSON resource ID must be provided by the user, and will be used for naming the associated LDAP
     * entry. More specifically, LDAP entry names will be derived by appending a single RDN to the collection's base DN
     * composed of the specified attribute type and LDAP value taken from the LDAP entry once attribute mapping has been
     * performed.
     * <p>
     * Note that this naming policy requires that the user provides the resource name when creating new resources, which
     * means it must be included in the resource content when not specified explicitly in the create request.
     *
     * @param dnAttribute
     *         The LDAP attribute which will be used for naming.
     * @return A reference to this object.
     */
    public SubResourceCollection useClientDnNaming(final String dnAttribute) {
        this.namingStrategy = new DnNamingStrategy(dnAttribute);
        return this;
    }

    /**
     * Indicates that the JSON resource ID must be provided by the user, but will not be used for naming the
     * associated LDAP entry. Instead the JSON resource ID will be taken from the {@code idAttribute} in the LDAP
     * entry, and the LDAP entry name will be derived by appending a single RDN to the collection's base DN composed
     * of the {@code dnAttribute} taken from the LDAP entry once attribute mapping has been performed.
     * <p>
     * Note that this naming policy requires that the user provides the resource name when creating new resources, which
     * means it must be included in the resource content when not specified explicitly in the create request.
     *
     * @param dnAttribute
     *         The attribute which will be used for naming LDAP entries.
     * @param idAttribute
     *         The attribute which will be used for JSON resource IDs.
     * @return A reference to this object.
     */
    public SubResourceCollection useClientNaming(final String dnAttribute, final String idAttribute) {
        this.namingStrategy = new AttributeNamingStrategy(dnAttribute, idAttribute, false);
        return this;
    }

    /**
     * Indicates that the JSON resource ID will be derived from the server provided "entryUUID" LDAP attribute. The
     * LDAP entry name will be derived by appending a single RDN to the collection's base DN composed of the {@code
     * dnAttribute} taken from the LDAP entry once attribute mapping has been performed.
     * <p>
     * Note that this naming policy requires that the server provides the resource name when creating new resources,
     * which means it must not be specified in the create request, nor included in the resource content.
     *
     * @param dnAttribute
     *         The attribute which will be used for naming LDAP entries.
     * @return A reference to this object.
     */
    public SubResourceCollection useServerEntryUuidNaming(final String dnAttribute) {
        return useServerNaming(dnAttribute, "entryUUID");
    }

    /**
     * Indicates that the JSON resource ID must not be provided by the user, and will not be used for naming the
     * associated LDAP entry. Instead the JSON resource ID will be taken from the {@code idAttribute} in the LDAP
     * entry, and the LDAP entry name will be derived by appending a single RDN to the collection's base DN composed
     * of the {@code dnAttribute} taken from the LDAP entry once attribute mapping has been performed.
     * <p>
     * Note that this naming policy requires that the server provides the resource name when creating new resources,
     * which means it must not be specified in the create request, nor included in the resource content.
     *
     * @param dnAttribute
     *         The attribute which will be used for naming LDAP entries.
     * @param idAttribute
     *         The attribute which will be used for JSON resource IDs.
     * @return A reference to this object.
     */
    public SubResourceCollection useServerNaming(final String dnAttribute, final String idAttribute) {
        this.namingStrategy = new AttributeNamingStrategy(dnAttribute, idAttribute, true);
        return this;
    }

    /**
     * Sets the relative URL template beneath which the sub-resources will be located. The template may be empty
     * indicating that the sub-resources will be located directly beneath the parent resource. Any URL template
     * variables will be substituted into the {@link #dnTemplate(String) DN template}.
     *
     * @param urlTemplate
     *         The relative URL template.
     * @return A reference to this object.
     */
    public SubResourceCollection urlTemplate(final String urlTemplate) {
        this.urlTemplate = urlTemplate;
        return this;
    }

    /**
     * Sets the relative DN template beneath which the sub-resource LDAP entries will be located. The template may be
     * empty indicating that the LDAP entries will be located directly beneath the parent LDAP entry. Any DN template
     * variables will be substituted using values extracted from the {@link #urlTemplate(String) URL template}.
     *
     * @param dnTemplate
     *         The relative DN template.
     * @return A reference to this object.
     */
    public SubResourceCollection dnTemplate(final String dnTemplate) {
        this.dnTemplate = dnTemplate;
        return this;
    }

    /**
     * Specifies an LDAP object class which is to be associated with any intermediate "glue" entries forming the DN
     * template. Multiple object classes may be specified.
     *
     * @param objectClass
     *         An LDAP object class which is to be associated with any intermediate "glue" entries forming the DN
     *         template.
     * @return A reference to this object.
     */
    public SubResourceCollection glueObjectClass(final String objectClass) {
        this.glueObjectClasses.add(objectClass);
        return this;
    }

    /**
     * Specifies one or more LDAP object classes which is to be associated with any intermediate "glue" entries
     * forming the DN template. Multiple object classes may be specified.
     *
     * @param objectClasses
     *         The LDAP object classes which is to be associated with any intermediate "glue" entries forming the DN
     *         template.
     * @return A reference to this object.
     */
    public SubResourceCollection glueObjectClasses(final String... objectClasses) {
        this.glueObjectClasses.add((Object[]) objectClasses);
        return this;
    }

    /**
     * Indicates whether this sub-resource collection only supports read and query operations.
     *
     * @param readOnly
     *         {@code true} if this sub-resource collection is read-only.
     * @return A reference to this object.
     */
    public SubResourceCollection isReadOnly(final boolean readOnly) {
        isReadOnly = readOnly;
        return this;
    }

    @Override
    Router addRoutes(final Router router) {
        router.addRoute(requestUriMatcher(EQUALS, urlTemplate), readOnly(new CollectionHandler()));
        router.addRoute(requestUriMatcher(EQUALS, urlTemplate + "/{id}"), readOnly(new InstanceHandler()));
        router.addRoute(requestUriMatcher(STARTS_WITH, urlTemplate + "/{id}"), readOnly(new SubResourceHandler()));
        return router;
    }

    private Promise<RoutingContext, ResourceException> route(final Context context) {
        final Connection conn = context.asContext(AuthenticatedConnectionContext.class).getConnection();
        final SearchRequest searchRequest = namingStrategy.createSearchRequest(dnFrom(context), idFrom(context));
        if (searchRequest.getScope().equals(BASE_OBJECT) && !resource.hasSubTypesWithSubResources()) {
            // There's no point in doing a search because we already know the DN and sub-resources.
            return newResultPromise(new RoutingContext(context, searchRequest.getName(), resource));
        }
        searchRequest.addAttribute("objectClass");
        return conn.searchSingleEntryAsync(searchRequest)
                         .thenAsync(new AsyncFunction<SearchResultEntry, RoutingContext, ResourceException>() {
                             @Override
                             public Promise<RoutingContext, ResourceException> apply(SearchResultEntry entry)
                                     throws ResourceException {
                                 final Resource subType = resource.resolveSubTypeFromObjectClasses(entry);
                                 return newResultPromise(new RoutingContext(context, entry.getName(), subType));
                             }
                         }, new AsyncFunction<LdapException, RoutingContext, ResourceException>() {
                             @Override
                             public Promise<RoutingContext, ResourceException> apply(LdapException e)
                                     throws ResourceException {
                                 return asResourceException(e).asPromise();
                             }
                         });
    }

    private SubResourceImpl collection(final Context context) {
        return new SubResourceImpl(rest2Ldap,
                                   dnFrom(context),
                                   dnTemplate.isEmpty() ? null : glueObjectClasses,
                                   namingStrategy,
                                   resource);
    }

    private String idFrom(final Context context) {
        return context.asContext(UriRouterContext.class).getUriTemplateVariables().get("id");
    }

    private static final class AttributeNamingStrategy implements NamingStrategy {
        private final AttributeDescription dnAttribute;
        private final AttributeDescription idAttribute;
        private final boolean isServerProvided;

        private AttributeNamingStrategy(final String dnAttribute, final String idAttribute,
                                        final boolean isServerProvided) {
            this.dnAttribute = AttributeDescription.valueOf(dnAttribute);
            this.idAttribute = AttributeDescription.valueOf(idAttribute);
            if (this.dnAttribute.equals(this.idAttribute)) {
                throw new LocalizedIllegalArgumentException(ERR_CONFIG_NAMING_STRATEGY_DN_AND_ID_NOT_DIFFERENT.get());
            }
            this.isServerProvided = isServerProvided;
        }

        @Override
        public SearchRequest createSearchRequest(final DN baseDn, final String resourceId) {
            return newSearchRequest(baseDn, SINGLE_LEVEL, Filter.equality(idAttribute.toString(), resourceId));
        }

        @Override
        public String getResourceIdLdapAttribute() {
            return idAttribute.toString();
        }

        @Override
        public String decodeResourceId(final Entry entry) {
            return entry.parseAttribute(idAttribute).asString();
        }

        @Override
        public void encodeResourceId(final DN baseDn, final String resourceId, final Entry entry)
                throws ResourceException {
            if (isServerProvided) {
                if (resourceId != null) {
                    throw newBadRequestException(ERR_SERVER_PROVIDED_RESOURCE_ID_UNEXPECTED.get());
                }
            } else {
                entry.addAttribute(new LinkedAttribute(idAttribute, ByteString.valueOfUtf8(resourceId)));
            }
            final String rdnValue = entry.parseAttribute(dnAttribute).asString();
            final RDN rdn = new RDN(dnAttribute.getAttributeType(), rdnValue);
            entry.setName(baseDn.child(rdn));
        }
    }

    private static final class DnNamingStrategy implements NamingStrategy {
        private final AttributeDescription attribute;

        private DnNamingStrategy(final String attribute) {
            this.attribute = AttributeDescription.valueOf(attribute);
        }

        @Override
        public SearchRequest createSearchRequest(final DN baseDn, final String resourceId) {
            return newSearchRequest(baseDn.child(rdn(resourceId)), BASE_OBJECT, objectClassPresent());
        }

        @Override
        public String getResourceIdLdapAttribute() {
            return attribute.toString();
        }

        @Override
        public String decodeResourceId(final Entry entry) {
            return entry.parseAttribute(attribute).asString();
        }

        @Override
        public void encodeResourceId(final DN baseDn, final String resourceId, final Entry entry)
                throws ResourceException {
            if (resourceId != null) {
                entry.setName(baseDn.child(rdn(resourceId)));
                entry.addAttribute(new LinkedAttribute(attribute, ByteString.valueOfUtf8(resourceId)));
            } else if (entry.getAttribute(attribute) != null) {
                entry.setName(baseDn.child(rdn(entry.parseAttribute(attribute).asString())));
            } else {
                throw newBadRequestException(ERR_CLIENT_PROVIDED_RESOURCE_ID_MISSING.get());
            }
        }

        private RDN rdn(final String resourceId) {
            return new RDN(attribute.getAttributeType(), resourceId);
        }
    }

    /**
     * Responsible for routing collection requests (CQ) to this collection. More specifically, given the
     * URL template /collection/{id} then this handler processes requests against /collection.
     */
    private final class CollectionHandler extends AbstractRequestHandler {
        @Override
        public Promise<ActionResponse, ResourceException> handleAction(final Context context,
                                                                       final ActionRequest request) {
            return new NotSupportedException(ERR_COLLECTION_ACTIONS_NOT_SUPPORTED.get().toString()).asPromise();
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleCreate(final Context context,
                                                                         final CreateRequest request) {
            return collection(context).create(context, request);
        }

        @Override
        public Promise<QueryResponse, ResourceException> handleQuery(final Context context, final QueryRequest request,
                                                                     final QueryResourceHandler handler) {
            return collection(context).query(context, request, handler);
        }

        @Override
        protected <V> Promise<V, ResourceException> handleRequest(final Context context, final Request request) {
            return new BadRequestException(ERR_UNSUPPORTED_REQUEST_AGAINST_COLLECTION.get().toString()).asPromise();
        }
    }

    /**
     * Responsible for processing instance requests (RUDPA) against this collection and collection requests (CQ) to
     * any collections sharing the same base URL as an instance within this collection. More specifically, given the
     * URL template /collection/{parent}/{child} then this handler processes requests against {parent} since it is
     * both an instance within /collection and also a collection of {child}.
     */
    private final class InstanceHandler implements RequestHandler {
        @Override
        public Promise<ActionResponse, ResourceException> handleAction(final Context context,
                                                                       final ActionRequest request) {
            return collection(context).action(context, idFrom(context), request);
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleCreate(final Context context,
                                                                         final CreateRequest request) {
            return route(context)
                    .thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                        @Override
                        public Promise<ResourceResponse, ResourceException> apply(final RoutingContext context) {
                            return subResourceRouterFrom(context).handleCreate(context, request);
                        }
                    }).thenCatch(this.<ResourceResponse>convert404To400());
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleDelete(final Context context,
                                                                         final DeleteRequest request) {
            return collection(context).delete(context, idFrom(context), request);
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handlePatch(final Context context,
                                                                        final PatchRequest request) {
            return collection(context).patch(context, idFrom(context), request);
        }

        @Override
        public Promise<QueryResponse, ResourceException> handleQuery(final Context context, final QueryRequest request,
                                                                     final QueryResourceHandler handler) {
            return route(context)
                    .thenAsync(new AsyncFunction<RoutingContext, QueryResponse, ResourceException>() {
                        @Override
                        public Promise<QueryResponse, ResourceException> apply(final RoutingContext context) {
                            return subResourceRouterFrom(context).handleQuery(context, request, handler);
                        }
                    }).thenCatch(this.<QueryResponse>convert404To400());
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleRead(final Context context,
                                                                       final ReadRequest request) {
            return collection(context).read(context, idFrom(context), request);
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleUpdate(final Context context,
                                                                         final UpdateRequest request) {
            return collection(context).update(context, idFrom(context), request);
        }

        private <T> Function<ResourceException, T, ResourceException> convert404To400() {
            return SubResource.convert404To400(ERR_UNSUPPORTED_REQUEST_AGAINST_INSTANCE.get());
        }
    }

    /**
     * Responsible for routing requests to sub-resources of instances within this collection. More specifically, given
     * the URL template /collection/{id} then this handler processes all requests beneath /collection/{id}.
     */
    private final class SubResourceHandler implements RequestHandler {
        @Override
        public Promise<ActionResponse, ResourceException> handleAction(final Context context,
                                                                       final ActionRequest request) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, ActionResponse, ResourceException>() {
                @Override
                public Promise<ActionResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handleAction(context, request);
                }
            });
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleCreate(final Context context,
                                                                         final CreateRequest request) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                @Override
                public Promise<ResourceResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handleCreate(context, request);
                }
            });
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleDelete(final Context context,
                                                                         final DeleteRequest request) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                @Override
                public Promise<ResourceResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handleDelete(context, request);
                }
            });
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handlePatch(final Context context,
                                                                        final PatchRequest request) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                @Override
                public Promise<ResourceResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handlePatch(context, request);
                }
            });
        }

        @Override
        public Promise<QueryResponse, ResourceException> handleQuery(final Context context, final QueryRequest request,
                                                                     final QueryResourceHandler handler) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, QueryResponse, ResourceException>() {
                @Override
                public Promise<QueryResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handleQuery(context, request, handler);
                }
            });
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleRead(final Context context,
                                                                       final ReadRequest request) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                @Override
                public Promise<ResourceResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handleRead(context, request);
                }
            });
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleUpdate(final Context context,
                                                                         final UpdateRequest request) {
            return route(context).thenAsync(new AsyncFunction<RoutingContext, ResourceResponse, ResourceException>() {
                @Override
                public Promise<ResourceResponse, ResourceException> apply(final RoutingContext context) {
                    return subResourceRouterFrom(context).handleUpdate(context, request);
                }
            });
        }
    }
}
