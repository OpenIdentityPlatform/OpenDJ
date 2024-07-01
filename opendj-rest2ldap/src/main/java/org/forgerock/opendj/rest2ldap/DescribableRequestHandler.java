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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.api.models.ApiDescription;
import org.forgerock.http.ApiProducer;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
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
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.services.descriptor.Describable;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

/** Decorator for a request handler that can return an api descriptor of the underlying handler. */
public class DescribableRequestHandler implements RequestHandler, Describable<ApiDescription, Request> {
    private final RequestHandler delegate;
    private final Describable<ApiDescription, Request> describableDelegate;
    private ApiDescription api;

    /**
     * Builds an object decorating the provided handler.
     *
     * @param handler
     *          the handler to decorate.
     */
    @SuppressWarnings("unchecked")
    public DescribableRequestHandler(final RequestHandler handler) {
        this.delegate = Reject.checkNotNull(handler);
        this.describableDelegate = delegate instanceof Describable
            ? (Describable<ApiDescription, Request>) delegate
            : null;
    }

    @Override
    public Promise<ActionResponse, ResourceException> handleAction(Context context, ActionRequest request) {
        return delegate.handleAction(wrap(context), request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleCreate(Context context, CreateRequest request) {
        return delegate.handleCreate(wrap(context), request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleDelete(Context context, DeleteRequest request) {
        return delegate.handleDelete(wrap(context), request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handlePatch(Context context, PatchRequest request) {
        return delegate.handlePatch(wrap(context), request);
    }

    @Override
    public Promise<QueryResponse, ResourceException> handleQuery(
            Context context, QueryRequest request, QueryResourceHandler handler) {
        return delegate.handleQuery(wrap(context), request, handler);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleRead(Context context, ReadRequest request) {
        return delegate.handleRead(wrap(context), request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleUpdate(Context context, UpdateRequest request) {
        return delegate.handleUpdate(wrap(context), request);
    }

    /**
     * Allows sub classes to wrap the provided context and return the wrapping context.
     *
     * @param context
     *          the context to wrap
     * @return the wrapping context that should be used
     */
    protected Context wrap(final Context context) {
        return context;
    }

    @Override
    public ApiDescription api(ApiProducer<ApiDescription> producer) {
        if (describableDelegate != null) {
            api = describableDelegate.api(producer);
        }
        return api;
    }

    @Override
    public ApiDescription handleApiRequest(Context context, Request request) {
        return api;
    }

    @Override
    public void addDescriptorListener(Describable.Listener listener) {
        if (describableDelegate != null) {
            describableDelegate.addDescriptorListener(listener);
        }
    }

    @Override
    public void removeDescriptorListener(Describable.Listener listener) {
        if (describableDelegate != null) {
            describableDelegate.removeDescriptorListener(listener);
        }
    }
}
