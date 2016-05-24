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
package org.opends.server.protocols.http;

import static  org.opends.messages.ProtocolMessages.ERR_HTTP_ERROR_WHILE_PROCESSING_REQUEST;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.opends.server.core.ServerContext;

/**
 * Creates and inject a {@link HttpLogContext} containing information that'll be logged once the request has been
 * processed.
 */
final class HttpLogFilter implements Filter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ServerContext serverContext;

  HttpLogFilter(final ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, final Request request, Handler next)
  {
    final HttpLogContext logContext = new HttpLogContext(context, serverContext, request);
    return next.handle(logContext, request).thenOnResultOrException(new ResultHandler<Response>()
    {
      @Override
      public void handleResult(Response result)
      {
        logContext.log(result.getStatus().getCode());
        if (result.getCause() != null)
        {
          logError(request, result.getCause());
        }
      }
    }, new ExceptionHandler<Exception>()
    {
      @Override
      public void handleException(Exception exception)
      {
        logError(request, exception);
      }
    });
  }

  private static void logError(Request request, Exception exception)
  {
    logger.error(ERR_HTTP_ERROR_WHILE_PROCESSING_REQUEST
        .get(request.getUri(), stackTraceToSingleLineString(exception)));
  }
}
