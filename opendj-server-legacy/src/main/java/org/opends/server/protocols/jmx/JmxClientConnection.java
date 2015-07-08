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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.protocols.jmx;

import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.*;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.*;

import static org.opends.messages.ProtocolMessages.*;

/**
 * This class defines the set of methods and structures that must be implemented
 * by a Directory Server client connection.
 */
public class JmxClientConnection
       extends ClientConnection implements NotificationListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The message ID counter to use for jmx connections. */
  private final AtomicInteger nextMessageID;
  /** The operation ID counter to use for operations on this connection. */
  private final AtomicLong nextOperationID;
  /** The empty operation list for this connection. */
  private final LinkedList<Operation> operationList;
  /** The connection ID for this client connection. */
  private final long connectionID;
  /** The JMX connection ID for this client connection. */
  protected String jmxConnectionID;
  /** The reference to the connection handler that accepted this connection. */
  private final JmxConnectionHandler jmxConnectionHandler;
  /** Indicate that the disconnect process is started. */
  private boolean disconnectStarted;

  /**
   * Creates a new Jmx client connection that will be authenticated as
   * as the specified user.
   *
   * @param jmxConnectionHandler
   *        The connection handler on which we should be registered
   * @param authInfo
   *        the User authentication info
   */
  public JmxClientConnection(JmxConnectionHandler jmxConnectionHandler,
      AuthenticationInfo authInfo)
  {
    super();

    nextMessageID    = new AtomicInteger(1);
    nextOperationID  = new AtomicLong(0);

    this.jmxConnectionHandler = jmxConnectionHandler;
    jmxConnectionHandler.registerClientConnection(this);

    setAuthenticationInfo(authInfo);

    connectionID = DirectoryServer.newConnectionAccepted(this);
    if (connectionID < 0)
    {
      disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
          ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
    }
    operationList = new LinkedList<>();

    // Register the Jmx Notification listener (this)
    jmxConnectionHandler.getRMIConnector().jmxRmiConnectorNoClientCertificate
        .addNotificationListener(this, null, null);
  }

  /** {@inheritDoc} */
  @Override
  public void handleNotification(Notification notif, Object handback)
  {
    // We don't have the expected notification
    if ( ! (notif instanceof JMXConnectionNotification))
    {
      return ;
    }
    JMXConnectionNotification jcn = (JMXConnectionNotification) notif;

    // The only handled notifications are CLOSED and FAILED
    if (!JMXConnectionNotification.CLOSED.equals(jcn.getType())
        && !JMXConnectionNotification.FAILED.equals(jcn.getType()))
    {
      return;
    }

    // Check if the closed connection corresponds to the current connection
    if (!jcn.getConnectionId().equals(jmxConnectionID))
    {
      return;
    }

    // Ok, we can perform the unbind: call finalize
    disconnect(DisconnectReason.CLIENT_DISCONNECT, false, null);
  }


  /**
   * Retrieves the operation ID that should be used for the next Jmx
   * operation.
   *
   * @return  The operation ID that should be used for the next Jmx
   *          operation.
   */
  public long nextOperationID()
  {
    long opID = nextOperationID.getAndIncrement();
    if (opID < 0)
    {
      synchronized (nextOperationID)
      {
        if (nextOperationID.get() < 0)
        {
          nextOperationID.set(1);
          return 0;
        }
        else
        {
          return nextOperationID.getAndIncrement();
        }
      }
    }

    return opID;
  }



  /**
   * Retrieves the message ID that should be used for the next Jmx
   * operation.
   *
   * @return  The message ID that should be used for the next Jmx
   *          operation.
   */
  public int nextMessageID()
  {
    int msgID = nextMessageID.getAndIncrement();
    if (msgID < 0)
    {
      synchronized (nextMessageID)
      {
        if (nextMessageID.get() < 0)
        {
          nextMessageID.set(2);
          return 1;
        }
        else
        {
          return nextMessageID.getAndIncrement();
        }
      }
    }

    return msgID;
  }



  /**
   * Retrieves the unique identifier that has been assigned to this connection.
   *
   * @return  The unique identifier that has been assigned to this connection.
   */
  @Override
  public long getConnectionID()
  {
    return connectionID;
  }

  /**
   * Retrieves the connection handler that accepted this client connection.
   *
   * @return  The connection handler that accepted this client connection.
   */
  @Override
  public ConnectionHandler<?> getConnectionHandler()
  {
    return jmxConnectionHandler;
  }

  /**
   * Retrieves the protocol that the client is using to communicate with the
   * Directory Server.
   *
   * @return  The protocol that the client is using to communicate with the
   *          Directory Server.
   */
  @Override
  public String getProtocol()
  {
    return "jmx";
  }



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return  A string representation of the address of the client.
   */
  @Override
  public String getClientAddress()
  {
    return "jmx";
  }



  /**
   * Retrieves the port number for this connection on the client system.
   *
   * @return  The port number for this connection on the client system.
   */
  @Override
  public int getClientPort()
  {
    return -1;
  }



  /**
   * Retrieves a string representation of the address on the server to which the
   * client connected.
   *
   * @return  A string representation of the address on the server to which the
   *          client connected.
   */
  @Override
  public String getServerAddress()
  {
    return "jmx";
  }



  /**
   * Retrieves the port number for this connection on the server
   * system if available.
   *
   * @return The port number for this connection on the server system
   *         or -1 if there is no server port associated with this
   *         connection (e.g. internal client).
   */
  @Override
  public int getServerPort()
  {
    return -1;
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> associated with the remote
   * client system.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> associated with the remote
   *          client system.  It may be <CODE>null</CODE> if the client is not
   *          connected over an IP-based connection.
   */
  @Override
  public InetAddress getRemoteAddress()
  {
    return null;
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> for the Directory Server
   * system to which the client has established the connection.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> for the Directory Server
   *          system to which the client has established the connection.  It may
   *          be <CODE>null</CODE> if the client is not connected over an
   *          IP-based connection.
   */
  @Override
  public InetAddress getLocalAddress()
  {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConnectionValid()
  {
    return !disconnectStarted;
  }

  /**
   * Indicates whether this client connection is currently using a secure
   * mechanism to communicate with the server.  Note that this may change over
   * time based on operations performed by the client or server (e.g., it may go
   * from <CODE>false</CODE> to <CODE>true</CODE> if the client uses the
   * StartTLS extended operation).
   *
   * @return  <CODE>true</CODE> if the client connection is currently using a
   *          secure mechanism to communicate with the server, or
   *          <CODE>false</CODE> if not.
   */
  @Override
  public boolean isSecure()
  {
      return false;
  }


  /**
   * Retrieves the human-readable name of the security mechanism that is used to
   * protect communication with this client.
   *
   * @return  The human-readable name of the security mechanism that is used to
   *          protect communication with this client, or <CODE>null</CODE> if no
   *          security is in place.
   */
  public String getSecurityMechanism()
  {
    return "NULL";
  }



  /**
   * Sends a response to the client based on the information in the provided
   * operation.
   *
   * @param  operation  The operation for which to send the response.
   */
  @Override
  public void sendResponse(Operation operation)
  {
    // There will not be any response sent by this method, since there is not an
    // actual connection.
  }


  /**
   * Processes an Jmx search operation with the provided information.
   *
   * @param  request      The search request.
   * @return  A reference to the internal search operation that was processed
   *          and contains information about the result of the processing as
   *          well as lists of the matching entries and search references.
   */
  public InternalSearchOperation processSearch(SearchRequest request)
  {
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(this, nextOperationID(), nextMessageID(), request);

    if (! hasPrivilege(Privilege.JMX_READ, null))
    {
      LocalizableMessage message = ERR_JMX_SEARCH_INSUFFICIENT_PRIVILEGES.get();
      searchOperation.setErrorMessage(new LocalizableMessageBuilder(message));
      searchOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      searchOperation.run();
    }
    return searchOperation;
  }

  /**
   * Sends the provided search result entry to the client.
   *
   * @param  searchOperation  The search operation with which the entry is
   *                          associated.
   * @param  searchEntry      The search result entry to be sent to the client.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to send
   *                              the entry to the client and the search should
   *                              be terminated.
   */
  @Override
  public void sendSearchEntry(SearchOperation searchOperation,
                              SearchResultEntry searchEntry)
         throws DirectoryException
  {
    ((InternalSearchOperation) searchOperation).addSearchEntry(searchEntry);
  }



  /**
   * Sends the provided search result reference to the client.
   *
   * @param  searchOperation  The search operation with which the reference is
   *                          associated.
   * @param  searchReference  The search result reference to be sent to the
   *                          client.
   *
   * @return  <CODE>true</CODE> if the client is able to accept referrals, or
   *          <CODE>false</CODE> if the client cannot handle referrals and no
   *          more attempts should be made to send them for the associated
   *          search operation.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to send
   *                              the reference to the client and the search
   *                              should be terminated.
   */
  @Override
  public boolean sendSearchReference(SearchOperation searchOperation,
                                     SearchResultReference searchReference)
         throws DirectoryException
  {
    ((InternalSearchOperation)
     searchOperation).addSearchReference(searchReference);
    return true;
  }




  /**
   * Sends the provided intermediate response message to the client.
   *
   * @param  intermediateResponse  The intermediate response message to be sent.
   *
   * @return  <CODE>true</CODE> if processing on the associated operation should
   *          continue, or <CODE>false</CODE> if not.
   */
  @Override
  protected boolean sendIntermediateResponseMessage(
                         IntermediateResponse intermediateResponse)
  {
    // FIXME -- Do we need to support Jmx intermediate responses?  If so,
    // then implement this.
    return false;
  }




  /**
   * Closes the connection to the client, optionally sending it a message
   * indicating the reason for the closure.  Note that the ability to send a
   * notice of disconnection may not be available for all protocols or under all
   * circumstances.
   *
   * @param  disconnectReason  The disconnect reason that provides the generic
   *                           cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide notification
   *                           to the client that the connection will be closed.
   * @param  message           The message to send to the client.  It may be
   *                           <CODE>null</CODE> if no notification is to be
   *                           sent.
   */
  @Override
  public void disconnect(DisconnectReason disconnectReason,
                         boolean sendNotification,
                         LocalizableMessage message)
  {
    // we are already performing a disconnect
    if (disconnectStarted)
    {
      return;
    }
    disconnectStarted = true ;
    jmxConnectionHandler.unregisterClientConnection(this);
    DirectoryServer.connectionClosed(this);
    finalizeConnectionInternal();

    // unbind the underlying connection
    try
    {
      UnbindOperationBasis unbindOp = new UnbindOperationBasis(
          this, nextOperationID(), nextMessageID(), null);
      unbindOp.run();
    }
   catch (Exception e)
    {
      // TODO print a message ?
      logger.traceException(e);
    }

    // Call postDisconnectPlugins
    try
    {
      PluginConfigManager pluginManager =
           DirectoryServer.getPluginConfigManager();
      pluginManager.invokePostDisconnectPlugins(this, disconnectReason,
                                                message);
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }



  /**
   * Retrieves the set of operations in progress for this client connection.
   * This list must not be altered by any caller.
   *
   * @return  The set of operations in progress for this client connection.
   */
  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    return operationList;
  }



  /**
   * Retrieves the operation in progress with the specified message ID.
   *
   * @param  messageID  The message ID of the operation to retrieve.
   *
   * @return  The operation in progress with the specified message ID, or
   *          <CODE>null</CODE> if no such operation could be found.
   */
  @Override
  public Operation getOperationInProgress(int messageID)
  {
    // Jmx operations will not be tracked.
    return null;
  }



  /**
   * Removes the provided operation from the set of operations in progress for
   * this client connection.  Note that this does not make any attempt to
   * cancel any processing that may already be in progress for the operation.
   *
   * @param  messageID  The message ID of the operation to remove from the set
   *                    of operations in progress.
   *
   * @return  <CODE>true</CODE> if the operation was found and removed from the
   *          set of operations in progress, or <CODE>false</CODE> if not.
   */
  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    // No implementation is required, since Jmx operations will not be
    // tracked.
    return false;
  }



  /**
   * Attempts to cancel the specified operation.
   *
   * @param  messageID      The message ID of the operation to cancel.
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   *
   * @return  A cancel result that either indicates that the cancel was
   *          successful or provides a reason that it was not.
   */
  @Override
  public CancelResult cancelOperation(int messageID,
                                      CancelRequest cancelRequest)
  {
    // Jmx operations cannot be cancelled.
    // TODO: i18n
    return new CancelResult(ResultCode.CANNOT_CANCEL,
        LocalizableMessage.raw("Jmx operations cannot be cancelled"));
  }



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   */
  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    // No implementation is required since Jmx operations cannot be
    // cancelled.
  }



  /**
   * Attempts to cancel all operations in progress on this connection except the
   * operation with the specified message ID.
   *
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   * @param  messageID      The message ID of the operation that should not be
   *                        canceled.
   */
  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
                                        int messageID)
  {
    // No implementation is required since Jmx operations cannot be
    // cancelled.
  }

  /** {@inheritDoc} */
  @Override
  public String getMonitorSummary()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("connID=\"");
    buffer.append(connectionID);
    buffer.append("\" connectTime=\"");
    buffer.append(getConnectTimeString());
    buffer.append("\" jmxConnID=\"");
    buffer.append(jmxConnectionID);
    buffer.append("\" authDN=\"");

    DN authDN = getAuthenticationInfo().getAuthenticationDN();
    if (authDN != null)
    {
      authDN.toString(buffer);
    }
    buffer.append("\"");

    return buffer.toString();
  }



  /**
   * Appends a string representation of this client connection to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("JmxClientConnection(connID=");
    buffer.append(connectionID);
    buffer.append(", authDN=\"");
    buffer.append(getAuthenticationInfo().getAuthenticationDN());
    buffer.append("\")");
  }

  /**
   * Called by the Gc when the object is garbage collected
   * Release the cursor in case the iterator was badly used and releaseCursor
   * was never called.
   */
  @Override
  protected void finalize()
  {
    disconnect(DisconnectReason.OTHER, false, null);
  }

  /**
   * To be implemented.
   *
   * @return number of operations performed on this connection
   */
  @Override
  public long getNumberOfOperations() {
    // JMX connections will not be limited.
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public int getSSF() {
      return 0;
  }
}

