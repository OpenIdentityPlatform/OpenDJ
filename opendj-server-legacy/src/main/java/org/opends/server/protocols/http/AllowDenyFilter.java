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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.opendj.ldap.AddressMask;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Implements a blacklist/whitelist to prevent denied clients to perform
 * requests.
 */
final class AllowDenyFilter implements Filter
{
  private final Collection<AddressMask> deniedClients;
  private final Collection<AddressMask> allowedClients;

  AllowDenyFilter(Collection<AddressMask> deniedClients, Collection<AddressMask> allowedClients)
  {
    this.deniedClients = deniedClients;
    this.allowedClients = allowedClients;
  }

  @Override
  public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next)
  {
    final InetAddress clientAddress;
    try
    {
      // The remote address will always be an IP address, so no reverse lookup will be performed.
      clientAddress = InetAddress.getByName(context.asContext(ClientContext.class).getRemoteAddress());
    }
    catch (UnknownHostException e)
    {
      return internalError();
    }

    // Check to see if the client is on the denied list. If so, then reject it immediately.
    if (!deniedClients.isEmpty() && AddressMask.matchesAny(deniedClients, clientAddress))
    {
      return forbidden();
    }

    // Check to see if there is an allowed list and if there is whether the client is on that list.
    // If not, then reject the connection.
    if (!allowedClients.isEmpty() && !AddressMask.matchesAny(allowedClients, clientAddress))
    {
      return forbidden();
    }

    return next.handle(context, request);
  }

  private static final Promise<Response, NeverThrowsException> forbidden()
  {
    return Promises.newResultPromise(new Response(Status.FORBIDDEN));
  }

  private static final Promise<Response, NeverThrowsException> internalError()
  {
    return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
  }
}