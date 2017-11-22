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

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_READ_ONLY_ENDPOINT;

import org.forgerock.api.models.ApiDescription;
import org.forgerock.http.ApiProducer;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.services.context.Context;
import org.forgerock.services.descriptor.Describable;
import org.forgerock.util.promise.Promise;

/**
 * Provides a read-only view of an underlying request handler.
 */
final class ReadOnlyRequestHandler extends AbstractRequestHandler {
    private final RequestHandler delegate;

    ReadOnlyRequestHandler(final RequestHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<QueryResponse, ResourceException> handleQuery(
            final Context context, final QueryRequest request, final QueryResourceHandler handler) {
        return delegate.handleQuery(context, request, handler);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleRead(
            final Context context, final ReadRequest request) {
        return delegate.handleRead(context, request);
    }

    @Override
    protected <V> Promise<V, ResourceException> handleRequest(final Context context, final Request request) {
        return new BadRequestException(ERR_READ_ONLY_ENDPOINT.get().toString()).asPromise();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiDescription api(ApiProducer<ApiDescription> producer) {
        if (delegate instanceof Describable) {
            return ((Describable<ApiDescription, Request>)delegate).api(producer);
        }
        else {
            return super.api(producer);
        }
    }
}
