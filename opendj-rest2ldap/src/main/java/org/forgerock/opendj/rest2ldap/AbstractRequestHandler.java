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
import org.forgerock.util.promise.Promise;

/**
 * An abstract base class from which request handlers may be easily implemented. The default implementation of each
 * method is to invoke the {@link #handleRequest(Context, Request)} method.
 */
public abstract class AbstractRequestHandler implements RequestHandler {
    /** Creates a new {@code AbstractRequestHandler}. */
    protected AbstractRequestHandler() {
        // Nothing to do.
    }

    @Override
    public Promise<ActionResponse, ResourceException> handleAction(final Context context, final ActionRequest request) {
        return handleRequest(context, request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleCreate(final Context context,
                                                                     final CreateRequest request) {
        return handleRequest(context, request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleDelete(final Context context,
                                                                     final DeleteRequest request) {
        return handleRequest(context, request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handlePatch(final Context context, final PatchRequest request) {
        return handleRequest(context, request);
    }

    @Override
    public Promise<QueryResponse, ResourceException> handleQuery(final Context context, final QueryRequest request,
                                                                 final QueryResourceHandler handler) {
        return handleRequest(context, request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleRead(final Context context, final ReadRequest request) {
        return handleRequest(context, request);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleUpdate(final Context context,
                                                                     final UpdateRequest request) {
        return handleRequest(context, request);
    }

    /**
     * Implement this method in order to provide a default behavior when processing requests.
     *
     * @param <V>
     *         The type of response.
     * @param context
     *         The request context.
     * @param request
     *         The request.
     * @return A {@code Promise} containing the result of the operation.
     */
    protected abstract <V> Promise<V, ResourceException> handleRequest(final Context context, final Request request);
}
