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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import static org.forgerock.opendj.adapter.server3x.Converters.from;
import static org.forgerock.opendj.adapter.server3x.Converters.getResponseResult;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.opends.messages.ProtocolMessages.WARN_CLIENT_DISCONNECT_IN_PROGRESS;
import static org.opends.server.loggers.AccessLogger.logDisconnect;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.http.MutableUri;
import org.forgerock.http.protocol.Request;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.LdapPromiseImpl;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.SearchResultReferenceProtocolOp;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

/**
 * This class defines an HTTP client connection, which is a type of client
 * connection that will be accepted by an instance of the HTTP connection
 * handler.
 */
final class HTTPClientConnection extends ClientConnection
{

  // TODO JNR Confirm with Matt that persistent searches are inapplicable to Rest2LDAP.
  // TODO JNR Should I override getIdleTime()?

  /**
   * Class grouping together an {@link Operation} and its associated
   * {@link LdapPromiseImpl} to ensure they are both atomically added
   * and removed from the {@link HTTPClientConnection#operationsInProgress} Map.
   */
  private static class OperationWithPromise
  {
    final Operation operation;
    final LdapPromiseImpl<Result> promise;

    private OperationWithPromise(Operation operation, LdapPromiseImpl<Result> promise)
    {
      this.operation = operation;
      this.promise = promise;
    }

    @Override
    public String toString()
    {
      return operation.toString();
    }
  }

  /** Search Operation with a promise. */
  private static final class SearchOperationWithPromise extends OperationWithPromise
  {

    final SearchResultHandler entryHandler;

    private SearchOperationWithPromise(
        Operation operation, LdapPromiseImpl<Result> promise, SearchResultHandler entryHandler)
    {
      super(operation, promise);
      this.entryHandler = entryHandler;
    }
  }

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Official servlet property giving access to the SSF (Security Strength
   * Factor) used to encrypt the current connection.
   */
  private static final String SERVLET_SSF_CONSTANT = "javax.servlet.request.key_size";

  /**
   * Indicates whether the Directory Server believes this connection to be valid
   * and available for communication.
   */
  private volatile boolean connectionValid = true;

  /**
   * Indicates whether this connection is about to be closed. This will be used
   * to prevent accepting new requests while a disconnect is in progress.
   */
  private boolean disconnectRequested;

  /**
   * Indicates whether the connection should keep statistics regarding the
   * operations that it is performing.
   */
  private final boolean keepStats;

  /**
   * The Map (messageID => {@link OperationWithPromise}) of all operations
   * currently in progress on this connection.
   */
  private final Map<Integer, OperationWithPromise> operationsInProgress = new ConcurrentHashMap<>();

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

  /** The statistics tracker associated with this client connection. */
  private final HTTPStatistics statTracker;
  private boolean useNanoTime;

  /** Total execution time for this request. */
  private final AtomicLong totalProcessingTime = new AtomicLong();

  /** The protocol in use for this client connection. */
  private final String protocol;

  /** The HTTP method/verb used for this request. */
  private final String method;
  /** The URI issued by the client. */
  private final MutableUri uri;

  /** The client (remote) address. */
  private final String clientAddress;

  /** The client (remote) port. */
  private final int clientPort;

  /** The remote (client) address. */
  private final InetAddress remoteAddress;

  /** The server (local) address. */
  private final String serverAddress;

  /** The server (local) port. */
  private final int serverPort;

  /** The local (server) address. */
  private final InetAddress localAddress;

  /** Whether this connection is secure. */
  private boolean isSecure;

  /** Security-Strength Factor extracted from the request attribute. */
  private final int securityStrengthFactor;

  /**
   * Constructs an instance of this class.
   * @param serverContext
   *            The server context.
   * @param connectionHandler
   *          the connection handler that accepted this connection
   * @param context
   *          represents the context of this client connection.
   */
  public HTTPClientConnection(ServerContext serverContext, HTTPConnectionHandler connectionHandler, Context context,
      Request request)
  {
    this.connectionHandler = connectionHandler;
    final ClientContext clientCtx = context.asContext(ClientContext.class);
    // Memorize all the fields we need from the request before Grizzly decides to recycle it
    this.clientAddress = clientCtx.getRemoteAddress();
    this.remoteAddress = toInetAddress(clientAddress);
    this.clientPort = clientCtx.getRemotePort();
    this.isSecure = clientCtx.isSecure();

    this.uri = request.getUri();
    this.serverAddress = uri.getHost();
    this.localAddress = toInetAddress(serverAddress);
    this.serverPort = uri.getPort();
    this.securityStrengthFactor = calcSSF(
            context.asContext(AttributesContext.class).getAttributes().get(SERVLET_SSF_CONSTANT));
    this.method = request.getMethod();
    this.protocol = request.getVersion();

    this.statTracker = this.connectionHandler.getStatTracker();

    this.keepStats = connectionHandler.keepStats();
    if (this.keepStats)
    {
      this.statTracker.updateConnect();
      this.useNanoTime = DirectoryServer.getUseNanoTime();
    }
    this.connectionID = DirectoryServer.newConnectionAccepted(this);
    context.asContext(HttpLogContext.class).setConnectionID(connectionID);
  }

  @Override
  public long getConnectionID()
  {
    return connectionID;
  }

  @Override
  public HTTPConnectionHandler getConnectionHandler()
  {
    return connectionHandler;
  }

  @Override
  public String getProtocol()
  {
    return protocol;
  }

  @Override
  public String getClientAddress()
  {
    return clientAddress;
  }

  @Override
  public int getClientPort()
  {
    return clientPort;
  }

  @Override
  public String getServerAddress()
  {
    return serverAddress;
  }

  @Override
  public int getServerPort()
  {
    return serverPort;
  }

  @Override
  public InetAddress getRemoteAddress()
  {
    return remoteAddress;
  }

  @Override
  public InetAddress getLocalAddress()
  {
    return localAddress;
  }

  @Override
  public boolean isSecure()
  {
    return isSecure;
  }

  @Override
  public void sendResponse(Operation operation)
  {
    final long time = getProcessingTime(operation);
    this.totalProcessingTime.addAndGet(time);

    if (keepStats)
    {
      this.statTracker.updateRequestMonitoringData(method, time);
      this.statTracker.updateOperationMonitoringData(operation.getOperationType(), time);
    }

    OperationWithPromise op = this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      try
      {
        op.promise.handleResult(getResponseResult(operation));

        if (keepStats)
        {
          this.statTracker.updateMessageWritten(
              new LDAPMessage(operation.getMessageID(), toResponseProtocolOp(operation)));
        }
      }
      catch (LdapException e)
      {
        op.promise.handleException(e);
      }
    }
  }

  private long getProcessingTime(Operation operation)
  {
    if (useNanoTime)
    {
      return operation.getProcessingNanoTime();
    }
    return operation.getProcessingTime();
  }

  private ProtocolOp toResponseProtocolOp(Operation operation)
  {
    final int resultCode = operation.getResultCode().intValue();
    if (operation instanceof AddOperation)
    {
      return new AddResponseProtocolOp(resultCode);
    }
    else if (operation instanceof BindOperation)
    {
      return new BindResponseProtocolOp(resultCode);
    }
    else if (operation instanceof CompareOperation)
    {
      return new CompareResponseProtocolOp(resultCode);
    }
    else if (operation instanceof DeleteOperation)
    {
      return new DeleteResponseProtocolOp(resultCode);
    }
    else if (operation instanceof ExtendedOperation)
    {
      return new ExtendedResponseProtocolOp(resultCode);
    }
    else if (operation instanceof ModifyDNOperation)
    {
      return new ModifyDNResponseProtocolOp(resultCode);
    }
    else if (operation instanceof ModifyOperation)
    {
      return new ModifyResponseProtocolOp(resultCode);
    }
    else if (operation instanceof SearchOperation)
    {
      return new SearchResultDoneProtocolOp(resultCode);
    }
    throw new RuntimeException("Not implemented for operation " + operation);
  }

  @Override
  public void sendSearchEntry(SearchOperation operation, SearchResultEntry searchEntry) throws DirectoryException
  {
    SearchOperationWithPromise op =
        (SearchOperationWithPromise) this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      op.entryHandler.handleEntry(from(searchEntry));
      if (keepStats)
      {
        this.statTracker.updateMessageWritten(
            new LDAPMessage(operation.getMessageID(), new SearchResultEntryProtocolOp(searchEntry)));
      }
    }
  }

  @Override
  public boolean sendSearchReference(SearchOperation operation, SearchResultReference searchReference)
      throws DirectoryException
  {
    SearchOperationWithPromise op =
        (SearchOperationWithPromise) this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      op.entryHandler.handleReference(from(searchReference));
      if (keepStats)
      {
        this.statTracker.updateMessageWritten(
            new LDAPMessage(operation.getMessageID(), new SearchResultReferenceProtocolOp(searchReference)));
      }
    }

    return connectionValid;
  }

  @Override
  protected boolean sendIntermediateResponseMessage(IntermediateResponse intermediateResponse)
  {
    // if (keepStats)
    // {
    // this.statTracker.updateMessageWritten(new LDAPMessage(
    // intermediateResponse.getOperation().getMessageID(),
    // new IntermediateResponseProtocolOp(intermediateResponse.getOID())));
    // }
    throw new RuntimeException("Not implemented");
  }

  /**
   * {@inheritDoc}
   *
   * @param sendNotification
   *          not used with HTTP.
   */
  @Override
  public void disconnect(DisconnectReason disconnectReason, boolean sendNotification, LocalizableMessage message)
  {
    // Set a flag indicating that the connection is being terminated so
    // that no new requests will be accepted.
    // Also cancel all operations in progress.
    synchronized (opsInProgressLock)
    {
      // If we are already in the middle of a disconnect, then don't do anything.
      if (disconnectRequested)
      {
        return;
      }

      disconnectRequested = true;
    }

    if (keepStats)
    {
      statTracker.updateDisconnect();
    }

    if (connectionID >= 0)
    {
      DirectoryServer.connectionClosed(this);
    }

    // Indicate that this connection is no longer valid.
    connectionValid = false;

    if (message != null)
    {
      LocalizableMessageBuilder msgBuilder = new LocalizableMessageBuilder();
      msgBuilder.append(disconnectReason.getClosureMessage());
      msgBuilder.append(": ");
      msgBuilder.append(message);
      cancelAllOperations(new CancelRequest(true, msgBuilder.toMessage()));
    }
    else
    {
      cancelAllOperations(new CancelRequest(true, disconnectReason.getClosureMessage()));
    }
    finalizeConnectionInternal();


    this.connectionHandler.removeClientConnection(this);
    logDisconnect(this, disconnectReason, message);
  }

  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    Collection<OperationWithPromise> values = operationsInProgress.values();
    Collection<Operation> results = new ArrayList<>(values.size());
    for (OperationWithPromise op : values)
    {
      results.add(op.operation);
    }
    return results;
  }

  @Override
  public Operation getOperationInProgress(int messageID)
  {
    OperationWithPromise op = operationsInProgress.get(messageID);
    if (op != null)
    {
      return op.operation;
    }
    return null;
  }

  /**
   * Adds the passed in search operation to the in progress list along with the
   * associated promise and the {@code SearchResultHandler}.
   *
   * @param operation
   *          the operation to add to the in progress list
   * @param promise
   *          the promise associated to the operation
   * @param searchResultHandler
   *          the search result handler associated to the promise result
   * @throws DirectoryException
   *           If an error occurs
   */
  void addOperationInProgress(Operation operation, LdapPromiseImpl<Result> promise,
      SearchResultHandler searchResultHandler) throws DirectoryException
  {
    if (searchResultHandler != null)
    {
      addOperationWithPromise(new SearchOperationWithPromise(operation, promise, searchResultHandler));
    }
    else
    {
      addOperationWithPromise(new OperationWithPromise(operation, promise));
    }
  }

  private void addOperationWithPromise(OperationWithPromise opPromise) throws DirectoryException
  {
    synchronized (opsInProgressLock)
    {
      // If we're already in the process of disconnecting the client, then reject the operation.
      if (disconnectRequested)
      {
        LocalizableMessage message = WARN_CLIENT_DISCONNECT_IN_PROGRESS.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      operationsInProgress.put(opPromise.operation.getMessageID(), opPromise);
    }
  }

  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    final OperationWithPromise previousValue = operationsInProgress.remove(messageID);
    if (previousValue != null)
    {
      operationsPerformed.incrementAndGet();

      final Operation operation = previousValue.operation;
      if (operation.getOperationType() == OperationType.ABANDON
          && keepStats
          && operation.getResultCode() == ResultCode.CANCELLED)
      {
        statTracker.updateAbandonedOperation();
      }
    }
    return previousValue != null;
  }

  @Override
  public CancelResult cancelOperation(int messageID, CancelRequest cancelRequest)
  {
    OperationWithPromise op = operationsInProgress.remove(messageID);
    if (op != null)
    {
      op.promise.handleException(newLdapException(ResultCode.CANCELLED));
      return op.operation.cancel(cancelRequest);
    }
    return new CancelResult(ResultCode.NO_SUCH_OPERATION, null);
  }

  private int calcSSF(Object ssf)
  {
    if (ssf instanceof Number)
    {
      return ((Number) ssf).intValue();
    }
    else if (ssf instanceof String)
    {
      try
      {
        return Integer.parseInt((String) ssf);
      }
      catch (IllegalArgumentException ignored)
      {
        // We cannot do much about it. Just log it.
        logger.traceException(ignored);
      }
    }
    return 0;
  }

  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    synchronized (opsInProgressLock)
    {
      try
      {
        for (OperationWithPromise op : operationsInProgress.values())
        {
          try
          {
            op.promise.handleException(newLdapException(ResultCode.CANCELLED));
            op.operation.abort(cancelRequest);

            if (keepStats)
            {
              statTracker.updateAbandonedOperation();
            }
          }
          catch (Exception e)
          { // Make sure all operations are cancelled, no matter what
            logger.traceException(e);
          }
        }

        operationsInProgress.clear();
      }
      catch (Exception e)
      { // TODO JNR should I keep this catch?
        logger.traceException(e);
      }
    }
  }

  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
      int messageID)
  {
    synchronized (opsInProgressLock)
    {
      OperationWithPromise toKeep = operationsInProgress.remove(messageID);
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

  @Override
  public long getNumberOfOperations()
  {
    return this.operationsPerformed.get();
  }

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
      buffer.append(authDN);
    }
    return buffer.toString();
  }

  private InetAddress toInetAddress(String address)
  {
    try
    {
      return InetAddress.getByName(address);
    }
    catch (UnknownHostException e)
    {
      throw new RuntimeException("Should never happen", e);
    }
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("HTTP client connection from ");
    buffer.append(getClientAddress()).append(":").append(getClientPort());
    buffer.append(" to ");
    buffer.append(getServerAddress()).append(":").append(getServerPort());
  }

  @Override
  public int getSSF()
  {
    return securityStrengthFactor;
  }

  @Override
  public boolean isConnectionValid()
  {
    return connectionValid;
  }

  @Override
  public boolean isInnerConnection()
  {
    return true;
  }
}
