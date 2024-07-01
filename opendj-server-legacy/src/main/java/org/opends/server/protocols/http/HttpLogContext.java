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

import static org.opends.server.loggers.CommonAudit.DEFAULT_TRANSACTION_ID;

import java.net.URI;

import org.forgerock.http.header.MalformedHeaderException;
import org.forgerock.http.header.TransactionIdHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.opends.server.core.ServerContext;
import org.opends.server.loggers.HTTPAccessLogger;
import org.opends.server.loggers.HTTPRequestInfo;

/** This context contains the logging informations related to the request processing. */
public final class HttpLogContext extends AbstractContext implements HTTPRequestInfo
{
  /** The client (remote) address. */
  private final String clientAddress;
  /** The client (remote) port. */
  private final int clientPort;
  /** The server (local) address. */
  private final String serverAddress;
  /** The server (local) port. */
  private final int serverPort;
  /** The protocol in use for this client connection. */
  private final String protocol;
  /** The HTTP method/verb used for this request. */
  private final String method;
  /** The user agent used by the client. */
  private final String userAgent;
  /** TransactionId for tracking of ForgeRock stack transactions. */
  private final String transactionId;
  /** The URI issued by the client. */
  private final URI uri;

  private final long startTime;
  private long totalProcessingTime;
  private long connectionId;
  private int statusCode;
  private String authUser;

  HttpLogContext(Context parent, ServerContext serverContext, Request request)
  {
    super(parent, "Http Log Context");

    final ClientContext clientContext = parent.asContext(ClientContext.class);
    this.uri = request.getUri().asURI();
    this.serverAddress = uri.getHost();
    this.serverPort = uri.getPort();
    this.method = request.getMethod();
    this.protocol = request.getVersion();
    this.clientAddress = clientContext.getRemoteAddress();
    this.clientPort = clientContext.getRemotePort();
    this.userAgent = clientContext.getUserAgent();
    this.startTime = System.currentTimeMillis();
    this.transactionId = getTransactionId(serverContext, request);
  }

  private String getTransactionId(ServerContext serverContext, Request request)
  {
    if (serverContext.getCommonAudit().shouldTrustTransactionIds())
    {
      try
      {
        TransactionIdHeader txHeader = request.getHeaders().get(TransactionIdHeader.class);
        return txHeader == null ? DEFAULT_TRANSACTION_ID :  txHeader.getTransactionId().getValue();
      }
      catch (MalformedHeaderException e)
      {
        // ignore it
      }
    }
    return DEFAULT_TRANSACTION_ID;
  }

  void setConnectionID(long connectionID)
  {
    this.connectionId = connectionID;
  }

  @Override
  public void log(int statusCode)
  {
    this.statusCode = statusCode;
    totalProcessingTime = System.currentTimeMillis() - startTime;
    HTTPAccessLogger.logRequestInfo(this);
  }

  @Override
  public String getAuthUser()
  {
    return authUser;
  }

  @Override
  public void setAuthUser(String authUser)
  {
    this.authUser = authUser;
  }

  @Override
  public int getStatusCode()
  {
    return statusCode;
  }

  @Override
  public String getServerAddress()
  {
    return serverAddress;
  }

  @Override
  public String getServerHost()
  {
    return serverAddress;
  }

  @Override
  public int getServerPort()
  {
    return serverPort;
  }

  @Override
  public String getClientAddress()
  {
    return clientAddress;
  }

  @Override
  public String getClientHost()
  {
    return clientAddress;
  }

  @Override
  public int getClientPort()
  {
    return clientPort;
  }

  @Override
  public String getProtocol()
  {
    return protocol;
  }

  @Override
  public String getMethod()
  {
    return method;
  }

  @Override
  public URI getUri()
  {
    return uri;
  }

  @Override
  public String getUserAgent()
  {
    return userAgent;
  }

  @Override
  public long getConnectionID()
  {
    return connectionId;
  }

  @Override
  public long getTotalProcessingTime()
  {
    return totalProcessingTime;
  }

  @Override
  public String getTransactionId()
  {
    return transactionId;
  }
}