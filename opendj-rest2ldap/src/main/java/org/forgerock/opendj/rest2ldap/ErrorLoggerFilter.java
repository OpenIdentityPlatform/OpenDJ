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

import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_ERROR_RESPONSE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_RUNTIME_EXCEPTION;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/** Logs internal server errors and runtime exceptions once the request has been processed. */
public final class ErrorLoggerFilter implements Filter {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        return next.handle(context, request)
                .thenOnResult(new ResultHandler<Response>() {
                    @Override
                    public void handleResult(final Response response) {
                        final Exception cause = response.getCause();
                        final Status status = response.getStatus();
                        if (status.isServerError() && cause != null) {
                            logger.error(ERR_ERROR_RESPONSE,
                                         toLog(request),
                                         status.toString(),
                                         cause.getLocalizedMessage(),
                                         cause);
                        }
                    }
                }).thenCatchRuntimeException(new Function<RuntimeException, Response, NeverThrowsException>() {
                    @Override
                    public Response apply(final RuntimeException e) {
                        logger.error(ERR_RUNTIME_EXCEPTION, toLog(request), e.getLocalizedMessage(), e);
                        return newInternalServerError(e);
                    }
                });
    }

    private String toLog(final Request request) {
        return request.getMethod() + request.getUri();
    }
}
