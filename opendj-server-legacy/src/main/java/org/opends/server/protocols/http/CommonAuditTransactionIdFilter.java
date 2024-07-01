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
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.MalformedHeaderException;
import org.forgerock.http.header.TransactionIdHeader;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.services.TransactionId;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.TransactionIdContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.core.ServerContext;

/**
 * This filter is responsible for creating a {@link TransactionIdContext} in the
 * context's chain.
 * <p>
 * This class is a copy of org.forgerock.http.filter.TransactionIdInboundFilter
 * in CHF, modified to not use a system property to indicate if transaction id
 * is trusted. Instead, it relies on DJ configuration to allow for runtime
 * modification It would be better if TransactionIdInboundFilter class could be
 * modified to be more generic and then usable by DJ, but it remains to be done.
 */
class CommonAuditTransactionIdFilter implements Filter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ServerContext serverContext;

  CommonAuditTransactionIdFilter(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next)
  {
    final TransactionId transactionId = serverContext.getCommonAudit().shouldTrustTransactionIds() ?
        createTransactionId(request.getHeaders()) : new TransactionId();
    final Context newContext = new TransactionIdContext(context, transactionId);
    return next.handle(newContext, request);
  }

  private TransactionId createTransactionId(Headers headers)
  {
    try
    {
      TransactionIdHeader txHeader = headers.get(TransactionIdHeader.class);
      return txHeader == null ? new TransactionId() : txHeader.getTransactionId();
    }
    catch (MalformedHeaderException ex)
    {
      logger.trace("The TransactionId header is malformed.", ex);
      return new TransactionId();
    }
  }
}
