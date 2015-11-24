/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013-2015 ForgeRock AS
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
