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
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNSUPPORTED_REQUEST_AGAINST_SINGLETON;
import static org.forgerock.util.promise.Promises.newResultPromise;

import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
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
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * Represents a one to one relationship between a parent resource and a child sub-resource. Removal of the parent
 * resource implies that the child (the sub-resource) is also removed. Singletons only support read, update, patch, and
 * action requests.
 */
public final class SubResourceSingleton extends SubResource {
    /**
     * A simple naming strategy that allows singletons to use the same processing logic as collections. The passed in
     * resource ID will always be {@code null}.
     */
    private static final NamingStrategy SINGLETON_NAMING_STRATEGY = new NamingStrategy() {
        @Override
        public SearchRequest createSearchRequest(final DN baseDn, final String resourceId) {
            return newSearchRequest(baseDn, BASE_OBJECT, objectClassPresent());
        }

        @Override
        public String getResourceIdLdapAttribute() {
            // Nothing to do.
            return null;
        }

        @Override
        public String decodeResourceId(final Entry entry) {
            // It's safe to return null. The resource response will default to the _id field if present.
            return null;
        }

        @Override
        public void encodeResourceId(final DN baseDn, final String resourceId, final Entry entry)
                throws ResourceException {
            // Nothing to do because singletons cannot be created.
        }
    };

    SubResourceSingleton(final String resourceId) {
        super(resourceId);
    }

    /**
     * Sets the relative URL template of the single sub-resource. The template must comprise of at least one path
     * element. Any URL template variables will be substituted into the {@link #dnTemplate(String) DN template}.
     *
     * @param urlTemplate
     *         The relative URL template.
     * @return A reference to this object.
     */
    public SubResourceSingleton urlTemplate(final String urlTemplate) {
        this.urlTemplate = urlTemplate;
        return this;
    }

    /**
     * Sets the relative DN template of the single sub-resource LDAP entry. The template must comprise of at least one
     * RDN. Any DN template variables will be substituted using values extracted from the {@link #urlTemplate(String)
     * URL template}.
     *
     * @param dnTemplate
     *         The relative DN template.
     * @return A reference to this object.
     */
    public SubResourceSingleton dnTemplate(final String dnTemplate) {
        this.dnTemplate = dnTemplate;
        return this;
    }

    /**
     * Indicates whether this sub-resource singleton only supports read operations.
     *
     * @param readOnly
     *         {@code true} if this sub-resource singleton is read-only.
     * @return A reference to this object.
     */
    public SubResourceSingleton isReadOnly(final boolean readOnly) {
        isReadOnly = readOnly;
        return this;
    }

    @Override
    Router addRoutes(final Router router) {
        router.addRoute(requestUriMatcher(EQUALS, urlTemplate), readOnly(new InstanceHandler()));
        router.addRoute(requestUriMatcher(STARTS_WITH, urlTemplate), readOnly(new SubResourceHandler()));
        return router;
    }

    private Promise<RoutingContext, ResourceException> route(final Context context) {
        return newResultPromise(new RoutingContext(context, dnFrom(context), resource));
    }

    private SubResourceImpl singleton(final Context context) {
        return new SubResourceImpl(rest2Ldap, dnFrom(context), null, SINGLETON_NAMING_STRATEGY, resource);
    }

    /**
     * Responsible for processing instance requests (RUPA) against this singleton and collection requests (CQ) to
     * any collections sharing the same base URL as this singleton. More specifically, given the
     * URL template /singleton/{child} then this handler processes requests against /singleton since it is
     * both a singleton and also a collection of {child}.
     */
    private final class InstanceHandler extends AbstractRequestHandler {
        @Override
        public Promise<ActionResponse, ResourceException> handleAction(final Context context,
                                                                       final ActionRequest request) {
            return singleton(context).action(context, null, request);
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
        public Promise<ResourceResponse, ResourceException> handlePatch(final Context context,
                                                                        final PatchRequest request) {
            return singleton(context).patch(context, null, request);
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
            return singleton(context).read(context, null, request);
        }

        @Override
        public Promise<ResourceResponse, ResourceException> handleUpdate(final Context context,
                                                                         final UpdateRequest request) {
            return singleton(context).update(context, null, request);
        }

        @Override
        protected <V> Promise<V, ResourceException> handleRequest(final Context context, final Request request) {
            return new BadRequestException(ERR_UNSUPPORTED_REQUEST_AGAINST_SINGLETON.get().toString()).asPromise();
        }

        private <T> Function<ResourceException, T, ResourceException> convert404To400() {
            return SubResource.convert404To400(ERR_UNSUPPORTED_REQUEST_AGAINST_SINGLETON.get());
        }
    }



    /**
     * Responsible for routing requests to sub-resources of this singleton. More specifically, given
     * the URL template /singleton then this handler processes all requests beneath /singleton.
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
