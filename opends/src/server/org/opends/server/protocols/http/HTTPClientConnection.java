/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import static org.opends.messages.ProtocolMessages.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import javax.servlet.ServletRequest;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.extensions.TLSCapableConnection;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

/**
 * This class defines an HTTP client connection, which is a type of client
 * connection that will be accepted by an instance of the HTTP connection
 * handler.
 */
final class HTTPClientConnection extends ClientConnection implements
    TLSCapableConnection
{

  /** The reference to the connection handler that accepted this connection. */
  private final HTTPConnectionHandler connectionHandler;

  /** The servlet request representing this client connection. */
  private final ServletRequest request;

  /** The connection ID assigned to this connection. */
  private final long connectionID;

  /**
   * Constructs an instance of this class.
   *
   * @param connectionHandler
   *          the connection handler that accepted this connection
   * @param request
   *          represents this client connection.
   */
  public HTTPClientConnection(HTTPConnectionHandler connectionHandler,
      ServletRequest request)
  {
    this.connectionHandler = connectionHandler;
    this.request = request;

    this.connectionID = DirectoryServer.newConnectionAccepted(this);
    if (this.connectionID < 0)
    {
      disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
          ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getConnectionID()
  {
    return connectionID;
  }

  /** {@inheritDoc} */
  @Override
  public ConnectionHandler<?> getConnectionHandler()
  {
    return connectionHandler;
  }

  /** {@inheritDoc} */
  @Override
  public String getProtocol()
  {
    return request.getProtocol();
  }

  /** {@inheritDoc} */
  @Override
  public String getClientAddress()
  {
    return request.getRemoteAddr();
  }

  /** {@inheritDoc} */
  @Override
  public int getClientPort()
  {
    return request.getRemotePort();
  }

  /** {@inheritDoc} */
  @Override
  public String getServerAddress()
  {
    return request.getLocalAddr();
  }

  /** {@inheritDoc} */
  @Override
  public int getServerPort()
  {
    return request.getLocalPort();
  }

  /** {@inheritDoc} */
  @Override
  public InetAddress getRemoteAddress()
  {
    try
    {
      return InetAddress.getByName(request.getRemoteAddr());
    }
    catch (UnknownHostException e)
    {
      throw new RuntimeException("Should never happen", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public InetAddress getLocalAddress()
  {
    try
    {
      return InetAddress.getByName(request.getLocalAddr());
    }
    catch (UnknownHostException e)
    {
      throw new RuntimeException("Should never happen", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSecure()
  {
    return request.isSecure();
  }

  /** {@inheritDoc} */
  @Override
  public void sendResponse(Operation operation)
  {
    // TODO Auto-generated method stub
  }

  /** {@inheritDoc} */
  @Override
  public void sendSearchEntry(SearchOperation searchOperation,
      SearchResultEntry searchEntry) throws DirectoryException
  {
    // TODO Auto-generated method stub
  }

  /** {@inheritDoc} */
  @Override
  public boolean sendSearchReference(SearchOperation searchOperation,
      SearchResultReference searchReference) throws DirectoryException
  {
    // TODO Auto-generated method stub
    return false;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean sendIntermediateResponseMessage(
      IntermediateResponse intermediateResponse)
  {
    // TODO Auto-generated method stub
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void disconnect(DisconnectReason disconnectReason,
      boolean sendNotification, Message message)
  {
    // TODO Auto-generated method stub
  }

  /** {@inheritDoc} */
  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    // TODO Auto-generated method stub
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Operation getOperationInProgress(int messageID)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    // TODO Auto-generated method stub
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public CancelResult cancelOperation(int messageID,
      CancelRequest cancelRequest)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    // TODO Auto-generated method stub
  }

  /** {@inheritDoc} */
  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
      int messageID)
  {
    // TODO Auto-generated method stub
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfOperations()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public String getMonitorSummary()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("connID=\"");
    buffer.append(getConnectionID());
    buffer.append("\" connectTime=\"");
    buffer.append(getConnectTimeString());
    buffer.append("\" source=\"");
    buffer.append(getClientAddress());
    buffer.append(":");
    buffer.append(getClientPort());
    buffer.append("\" destination=\"");
    buffer.append(getServerAddress());
    buffer.append(":");
    buffer.append(connectionHandler.getListenPort());
    buffer.append("\" authDN=\"");
    DN authDN = getAuthenticationInfo().getAuthenticationDN();
    if (authDN != null)
    {
      authDN.toString(buffer);
    }
    return buffer.toString();
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("HTTP client connection from ");
    buffer.append(getClientAddress()).append(":").append(getClientPort());
    buffer.append(" to ");
    buffer.append(getServerAddress()).append(":").append(getServerPort());
  }

  /** {@inheritDoc} */
  @Override
  public boolean prepareTLS(MessageBuilder unavailableReason)
  {
    // TODO JNR add message to mention that this client connection cannot start
    // TLS
    unavailableReason.append(INFO_HTTP_CONNHANDLER_STARTTLS_NOT_SUPPORTED);
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public int getSSF()
  {
    Object attribute = request.getAttribute("javax.servlet.request.key_size");
    if (attribute instanceof Number)
    {
      return ((Number) attribute).intValue();
    }
    else if (attribute instanceof String)
    {
      try
      {
        return Integer.parseInt((String) attribute);
      }
      catch (IllegalArgumentException e)
      {
        // TODO tracer debug
      }
    }
    return 0;
  }

}
