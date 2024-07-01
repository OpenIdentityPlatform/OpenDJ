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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import static org.forgerock.audit.events.AccessAuditEventBuilder.accessEvent;
import static org.forgerock.json.resource.Requests.newCreateRequest;
import static org.forgerock.json.resource.ResourcePath.resourcePath;

import java.util.concurrent.TimeUnit;

import org.forgerock.audit.events.AccessAuditEventBuilder;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.MutableUri;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RequestAuditContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;
import org.forgerock.util.time.TimeService;

/**
 * This filter aims to send some access audit events to the AuditService managed as a CREST handler.
 */
public class CommonAuditHttpAccessAuditFilter implements Filter {

    private static Response newInternalServerError() {
        return new Response(Status.INTERNAL_SERVER_ERROR);

    }

    private final RequestHandler auditServiceHandler;
    private final TimeService time;

    /**
     * Constructs a new HttpAccessAuditFilter.
     *
     * @param productName The name of product generating the event.
     * @param auditServiceHandler The {@link RequestHandler} to publish the events.
     * @param time The {@link TimeService} to use.
     */
    public CommonAuditHttpAccessAuditFilter(String productName, RequestHandler auditServiceHandler, TimeService time) {
        this.auditServiceHandler = auditServiceHandler;
        this.time = time;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        ClientContext clientContext = context.asContext(ClientContext.class);

        AccessAuditEventBuilder<?> accessAuditEventBuilder = accessEvent();

        accessAuditEventBuilder
                .eventName("DJ-HTTP")
                .timestamp(time.now())
                .transactionIdFromContext(context)
                .serverFromContext(clientContext)
                .clientFromContext(clientContext)
                .httpRequest(clientContext.isSecure(),
                             request.getMethod(),
                             getRequestPath(request.getUri()),
                             new Form().fromRequestQuery(request),
                             request.getHeaders().copyAsMultiMapOfStrings());

        final PromiseImpl<Response, NeverThrowsException> promiseImpl = PromiseImpl.create();
        try {
            next.handle(context, request)
                    .thenOnResult(onResult(context, accessAuditEventBuilder, promiseImpl))
                    .thenOnRuntimeException(
                            onRuntimeException(context, accessAuditEventBuilder, promiseImpl));
        } catch (RuntimeException e) {
            onRuntimeException(context, accessAuditEventBuilder, promiseImpl).handleRuntimeException(e);
        }

        return promiseImpl;
    }

    // See HttpContext.getRequestPath
    private String getRequestPath(MutableUri uri) {
        return new StringBuilder()
            .append(uri.getScheme())
            .append("://")
            .append(uri.getRawAuthority())
            .append(uri.getRawPath()).toString();
    }

    private ResultHandler<? super Response> onResult(final Context context,
                                                     final AccessAuditEventBuilder<?> accessAuditEventBuilder,
                                                     final PromiseImpl<Response, NeverThrowsException> promiseImpl) {
        return new ResultHandler<Response>() {
            @Override
            public void handleResult(Response response) {
                sendAuditEvent(response, context, accessAuditEventBuilder);
                promiseImpl.handleResult(response);
            }

        };
    }

    private RuntimeExceptionHandler onRuntimeException(final Context context,
            final AccessAuditEventBuilder<?> accessAuditEventBuilder,
            final PromiseImpl<Response, NeverThrowsException> promiseImpl) {
        return new RuntimeExceptionHandler() {
            @Override
            public void handleRuntimeException(RuntimeException exception) {
                // TODO How to be sure that the final status code sent back with the response will be a 500 ?
                final Response errorResponse = newInternalServerError();
                sendAuditEvent(errorResponse, context, accessAuditEventBuilder);
                promiseImpl.handleResult(errorResponse.setCause(exception));
            }
        };
    }

    private void sendAuditEvent(final Response response,
                                final Context context,
                                final AccessAuditEventBuilder<?> accessAuditEventBuilder) {
        RequestAuditContext requestAuditContext = context.asContext(RequestAuditContext.class);
        long elapsedTime = time.now() - requestAuditContext.getRequestReceivedTime();
        accessAuditEventBuilder.httpResponse(response.getHeaders().copyAsMultiMapOfStrings());
        accessAuditEventBuilder.response(mapResponseStatus(response.getStatus()),
                                         String.valueOf(response.getStatus().getCode()),
                                         elapsedTime,
                                         TimeUnit.MILLISECONDS);

        CreateRequest request =
            newCreateRequest(resourcePath("/http-access"), accessAuditEventBuilder.toEvent().getValue());
        auditServiceHandler.handleCreate(context, request);
    }

    private static AccessAuditEventBuilder.ResponseStatus mapResponseStatus(Status status) {
        switch(status.getFamily()) {
        case CLIENT_ERROR:
        case SERVER_ERROR:
            return AccessAuditEventBuilder.ResponseStatus.FAILED;
        default:
            return AccessAuditEventBuilder.ResponseStatus.SUCCESSFUL;
        }
    }
}
