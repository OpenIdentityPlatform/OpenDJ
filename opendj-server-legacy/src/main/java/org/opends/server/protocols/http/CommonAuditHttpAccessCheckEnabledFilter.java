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

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.core.ServerContext;

/**
 * Filter that checks if at least one HTTP access logger is enabled for common audit, and short-circuit
 * the corresponding filter if this is not the case.
 */
class CommonAuditHttpAccessCheckEnabledFilter implements Filter
{

  private final ServerContext serverContext;

  /** The HTTP access audit filter to go through if HTTP access logger is enabled. */
  private final Filter httpAccessAuditFilter;

  CommonAuditHttpAccessCheckEnabledFilter(ServerContext serverContext, Filter httpAccessAuditFilter)
  {
    this.serverContext = serverContext;
    this.httpAccessAuditFilter = httpAccessAuditFilter;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(
      final Context context, final Request request, final Handler next)
  {
    if (serverContext.getCommonAudit().isHttpAccessLogEnabled())
    {
      // introduce HttpAccessAuditFilter into the filter chain
      return httpAccessAuditFilter.filter(context, request, next);
    }
    // avoid HttpAccessAuditFilter, follow the filter chain
    return next.handle(context, request);
  }

}
