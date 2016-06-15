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
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * An abstract base class from which request handlers may be easily implemented. The default implementation of each
 * method is to return the {@link ResourceException} passed in during construction.
 */
abstract class AbstractRequestHandler implements RequestHandler {
    private final ResourceException defaultErrorResponse;

    AbstractRequestHandler(final ResourceException defaultErrorResponse) {
        this.defaultErrorResponse = defaultErrorResponse;
    }

    @Override
    public Promise<ActionResponse, ResourceException> handleAction(final Context context, final ActionRequest request) {
        return defaultErrorResponse.asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleCreate(final Context context,
                                                                     final CreateRequest request) {
        return defaultErrorResponse.asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleDelete(final Context context,
                                                                     final DeleteRequest request) {
        return defaultErrorResponse.asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handlePatch(final Context context, final PatchRequest request) {
        return defaultErrorResponse.asPromise();
    }

    @Override
    public Promise<QueryResponse, ResourceException> handleQuery(final Context context, final QueryRequest request,
                                                                 final QueryResourceHandler handler) {
        return defaultErrorResponse.asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleRead(final Context context, final ReadRequest request) {
        return defaultErrorResponse.asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleUpdate(final Context context,
                                                                     final UpdateRequest request) {
        return defaultErrorResponse.asPromise();
    }
}
