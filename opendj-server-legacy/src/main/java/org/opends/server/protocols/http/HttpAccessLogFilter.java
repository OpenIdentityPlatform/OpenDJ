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

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.opends.server.core.ServerContext;

/**
 * Creates and inject a {@link HttpLogContext} containing information that'll be logged once the request has been
 * processed.
 */
final class HttpAccessLogFilter implements Filter
{
  private final ServerContext serverContext;

  HttpAccessLogFilter(final ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, final Request request, Handler next)
  {
    final HttpLogContext logContext = new HttpLogContext(context, serverContext, request);
    return next.handle(logContext, request).thenOnResult(new ResultHandler<Response>()
    {
      @Override
      public void handleResult(Response result)
      {
        logContext.log(result.getStatus().getCode());
      }
    });
  }
}
