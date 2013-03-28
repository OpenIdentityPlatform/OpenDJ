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

import static org.forgerock.opendj.adapter.server2x.Converters.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletRequest;

import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.responses.Result;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import com.forgerock.opendj.util.AsynchronousFutureResult;

/**
 * This class defines an HTTP client connection, which is a type of client
 * connection that will be accepted by an instance of the HTTP connection
 * handler.
 */
final class HTTPClientConnection extends ClientConnection
{

  // TODO JNR Confirm with Matt that persistent searches are inapplicable to
  // Rest2LDAP.
  // TODO JNR Should I override getIdleTime()?
  // TODO JNR Implement stats

  /**
   * Class grouping together an {@link Operation} and its associated
   * {@link AsynchronousFutureResult} to ensure they are both atomically added
   * and removed from the {@link HTTPClientConnection#operationsInProgress} Map.
   */
  private static final class OperationWithFutureResult
  {

    final Operation operation;
    final AsynchronousFutureResult<Result, SearchResultHandler> futureResult;

    public OperationWithFutureResult(Operation operation,
        AsynchronousFutureResult<Result, SearchResultHandler> futureResult)
    {
      this.operation = operation;
      this.futureResult = futureResult;
    }

  }

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Official servlet property giving access to the SSF (Security Strength
   * Factor) used to encrypt the current connection.
   */
  private static final String SERVLET_SSF_CONSTANT =
      "javax.servlet.request.key_size";

  /**
   * Indicates whether the Directory Server believes this connection to be valid
   * and available for communication.
   */
  private volatile boolean connectionValid;

  /**
   * Indicates whether this connection is about to be closed. This will be used
   * to prevent accepting new requests while a disconnect is in progress.
   */
  private boolean disconnectRequested;

  /**
   * The Map (messageID => {@link OperationWithFutureResult}) of all operations
   * currently in progress on this connection.
   */
  private final Map<Integer, OperationWithFutureResult> operationsInProgress =
      new ConcurrentHashMap<Integer, OperationWithFutureResult>();

  /**
   * The number of operations performed on this connection. Used to compare with
   * the resource limits of the network group.
   */
  private final AtomicLong operationsPerformed = new AtomicLong(0);

  /**
   * The lock used to provide threadsafe access to the map of operations in
   * progress. This is used when we want to prevent puts on this map while we
   * are removing all operations in progress.
   */
  private final Object opsInProgressLock = new Object();

  /** The connection ID assigned to this connection. */
  private final long connectionID;

  /** The reference to the connection handler that accepted this connection. */
  private final HTTPConnectionHandler connectionHandler;

  /** The servlet request representing this client connection. */
  private final ServletRequest request;

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
    OperationWithFutureResult op =
        this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      try
      {
        op.futureResult.handleResult(getResponseResult(operation));
      }
      catch (ErrorResultException e)
      {
        op.futureResult.handleErrorResult(e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void sendSearchEntry(SearchOperation operation,
      SearchResultEntry searchEntry) throws DirectoryException
  {
    OperationWithFutureResult op =
        this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      op.futureResult.getResultHandler().handleEntry(from(searchEntry));
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean sendSearchReference(SearchOperation operation,
      SearchResultReference searchReference) throws DirectoryException
  {
    OperationWithFutureResult op =
        this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      op.futureResult.getResultHandler().handleReference(from(searchReference));
    }
    return connectionValid;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean sendIntermediateResponseMessage(
      IntermediateResponse intermediateResponse)
  {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @param sendNotification
   *          not used with HTTP.
   */
  @Override
  public void disconnect(DisconnectReason disconnectReason,
      boolean sendNotification, Message message)
  {
    // Set a flag indicating that the connection is being terminated so
    // that no new requests will be accepted. Also cancel all operations
    // in progress.
    synchronized (opsInProgressLock)
    {
      // If we are already in the middle of a disconnect, then don't
      // do anything.
      if (disconnectRequested)
      {
        return;
      }

      disconnectRequested = true;
    }

    // TODO JNR
    // if (keepStats)
    // {
    // statTracker.updateDisconnect();
    // }

    if (connectionID >= 0)
    {
      DirectoryServer.connectionClosed(this);
    }

    // Indicate that this connection is no longer valid.
    connectionValid = false;

    if (message != null)
    {
      MessageBuilder msgBuilder = new MessageBuilder();
      msgBuilder.append(disconnectReason.getClosureMessage());
      msgBuilder.append(": ");
      msgBuilder.append(message);
      cancelAllOperations(new CancelRequest(true, msgBuilder.toMessage()));
    }
    else
    {
      cancelAllOperations(new CancelRequest(true, disconnectReason
          .getClosureMessage()));
    }
    finalizeConnectionInternal();
  }

  /** {@inheritDoc} */
  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    Collection<OperationWithFutureResult> values =
        operationsInProgress.values();
    Collection<Operation> results = new ArrayList<Operation>(values.size());
    for (OperationWithFutureResult op : values)
    {
      results.add(op.operation);
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public Operation getOperationInProgress(int messageID)
  {
    OperationWithFutureResult op = operationsInProgress.get(messageID);
    if (op != null)
    {
      return op.operation;
    }
    return null;
  }

  /**
   * Adds the passed in operation to the in progress list along with the
   * associated future.
   *
   * @param operation
   *          the operation to add to the in progress list
   * @param futureResult
   *          the future associated to the operation.
   * @throws DirectoryException
   *           If an error occurs
   */
  void addOperationInProgress(Operation operation,
      AsynchronousFutureResult<Result, SearchResultHandler> futureResult)
      throws DirectoryException
  {
    synchronized (opsInProgressLock)
    {
      // If we're already in the process of disconnecting the client,
      // then reject the operation.
      if (disconnectRequested)
      {
        Message message = WARN_CLIENT_DISCONNECT_IN_PROGRESS.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      operationsInProgress.put(operation.getMessageID(),
          new OperationWithFutureResult(operation, futureResult));
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    final OperationWithFutureResult previousValue =
        operationsInProgress.remove(messageID);
    if (previousValue != null)
    {
      operationsPerformed.incrementAndGet();
    }
    return previousValue != null;
  }

  /** {@inheritDoc} */
  @Override
  public CancelResult cancelOperation(int messageID,
      CancelRequest cancelRequest)
  {
    OperationWithFutureResult op = operationsInProgress.remove(messageID);
    if (op != null)
    {
      op.futureResult.handleErrorResult(ErrorResultException
          .newErrorResult(org.forgerock.opendj.ldap.ResultCode.CANCELLED));
      return op.operation.cancel(cancelRequest);
    }
    return new CancelResult(ResultCode.NO_SUCH_OPERATION, null);
  }

  /** {@inheritDoc} */
  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    synchronized (opsInProgressLock)
    {
      try
      {
        for (OperationWithFutureResult op : operationsInProgress.values())
        {
          try
          {
            op.futureResult.handleErrorResult(ErrorResultException
               .newErrorResult(org.forgerock.opendj.ldap.ResultCode.CANCELLED));
            op.operation.abort(cancelRequest);
          }
          catch (Exception e)
          { // make sure all operations are cancelled, no mattter what
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }

        operationsInProgress.clear();
      }
      catch (Exception e)
      { // TODO JNR should I keep this catch?
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
      int messageID)
  {
    synchronized (opsInProgressLock)
    {
      OperationWithFutureResult toKeep = operationsInProgress.remove(messageID);
      try
      {
        cancelAllOperations(cancelRequest);
      }
      finally
      { // Ensure we always put back this operation
        operationsInProgress.put(messageID, toKeep);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfOperations()
  {
    return this.operationsPerformed.get();
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
  public int getSSF()
  {
    Object attribute = request.getAttribute(SERVLET_SSF_CONSTANT);
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
      catch (IllegalArgumentException ignored)
      {
        // We cannot do much about it. Just log it.
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ignored);
        }
      }
    }
    return 0;
  }

  /**
   * Returns whether the client connection is valid.
   *
   * @return true if the connection is valid, false otherwise
   */
  boolean isConnectionValid()
  {
    return connectionValid;
  }
}
