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
 * Portions Copyright 2017 Rosie Applications, Inc.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNRECOGNIZED_SUB_RESOURCE_TYPE;

import org.forgerock.api.models.ApiDescription;
import org.forgerock.http.ApiProducer;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * Defines a parent-child relationship between a parent resource and one or more child resource(s). Removal of the
 * parent resource implies that the children (the sub-resources) are also removed. There are two types of
 * sub-resource:
 * <ul>
 * <li>{@link SubResourceSingleton} represents a one-to-one relationship supporting read, update, patch, and action
 *     requests</li>
 * <li>{@link SubResourceCollection} represents a one-to-many relationship supporting all requests.</li>
 * </ul>
 */
public abstract class SubResource {
    private final String resourceId;
    private DnTemplate dnTemplate;

    String urlTemplate = "";
    String dnTemplateString = "";

    protected boolean isReadOnly = false;
    protected Rest2Ldap rest2Ldap;
    protected Resource resource;

    SubResource(final String resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public final boolean equals(final Object o) {
        return this == o || (o instanceof SubResource && urlTemplate.equals(((SubResource) o).urlTemplate));
    }

    @Override
    public final int hashCode() {
        return urlTemplate.hashCode();
    }

    @Override
    public final String toString() {
        return getUrlTemplate();
    }

    /**
     * Gets the URL template that must match for this sub-resource to apply to a given request.
     *
     * @return  The URL template for this sub-resource.
     */
    public String getUrlTemplate() {
        return urlTemplate;
    }

    /**
     * Gets whether or not this sub-resource has been configured for read-only access.
     *
     * @return  {@code true} if the sub-resource is read-only; {@code false} otherwise.
     */
    public boolean isReadOnly() {
        return isReadOnly;
    }

    final Resource getResource() {
        return resource;
    }

    final void build(final Rest2Ldap rest2Ldap, final String parent) {
        this.rest2Ldap = rest2Ldap;
        this.resource = rest2Ldap.getResource(resourceId);
        if (resource == null) {
            throw new LocalizedIllegalArgumentException(ERR_UNRECOGNIZED_SUB_RESOURCE_TYPE.get(parent, resourceId));
        }
        this.dnTemplate = DnTemplate.compileRelative(dnTemplateString);
    }

    abstract Router addRoutes(Router router);

    /** A 404 indicates that this instance is not also a collection, so return a more helpful message. */
    static <T> Function<ResourceException, T, ResourceException> convert404To400(final LocalizableMessage msg) {
        return new Function<ResourceException, T, ResourceException>() {
            @Override
            public T apply(final ResourceException e) throws ResourceException {
                if (e instanceof NotFoundException) {
                    throw new BadRequestException(msg.toString());
                }
                throw e;
            }
        };
    }

    final RequestHandler readOnly(final RequestHandler handler) {
        return isReadOnly ? new ReadOnlyRequestHandler(handler) : handler;
    }

    final DN dnFrom(final Context context) {
        return dnTemplate.format(context);
    }

    final RequestHandler subResourceRouterFrom(final RoutingContext context) {
        return context.getType().getSubResourceRouter();
    }

    abstract Promise<RoutingContext, ResourceException> route(final Context context);

    /**
     * Responsible for routing requests to sub-resources:
     * <ul>
     * <li>of this singleton,</li>
     * <li>or of instances within a collection<./li>
     * </ul>
     * <p>
     * More specifically, given
     * <ul>
     * <li>the URL template /singleton then this handler processes all requests beneath /singleton.</li>
     * <li>the URL template /collection/{id} then this handler processes all requests beneath /collection/{id},</li>
     * </ul>
     */
    final class SubResourceHandler extends AbstractRequestHandler {
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

        @Override
        public ApiDescription api(ApiProducer<ApiDescription> producer) {
            return resource.subResourcesApi(producer);
        }
    }
}
