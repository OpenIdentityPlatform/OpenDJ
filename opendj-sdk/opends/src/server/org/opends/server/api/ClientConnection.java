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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.messages.Message;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server client connection.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=true,
     mayInvoke=true)
public abstract class ClientConnection
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of authentication information for this client connection.
  private AuthenticationInfo authenticationInfo;

  // Indicates whether a bind is currently in progress on this client
  // connection.  If so, then no other operations should be allowed
  // until the bind completes.
  private boolean bindInProgress;

  // Indicates whether any necessary finalization work has been done
  // for this client connection.
  private boolean finalized;

  // The set of privileges assigned to this client connection.
  private HashSet<Privilege> privileges;

  // The size limit for use with this client connection.
  private int sizeLimit;

  // The time limit for use with this client connection.
  private int timeLimit;

  // The lookthrough limit for use with this client connection.
  private int lookthroughLimit;

  // The time that this client connection was established.
  private long connectTime;

  // The idle time limit for this client connection.
  private long idleTimeLimit;

  // The opaque information used for storing intermediate state
  // information needed across multi-stage SASL binds.
  private Object saslAuthState;

  // A string representation of the time that this client connection
  // was established.
  private String connectTimeString;

  // A set of persistent searches registered for this client.
  private CopyOnWriteArrayList<PersistentSearch> persistentSearches;

  // The network group to which the connection belongs to.
  private NetworkGroup networkGroup;

  /** Need to evaluate the network group for the first operation. */
  protected boolean mustEvaluateNetworkGroup;


  /**
   * Performs the appropriate initialization generic to all client
   * connections.
   */
  protected ClientConnection()
  {
    connectTime        = TimeThread.getTime();
    connectTimeString  = TimeThread.getGMTTime();
    authenticationInfo = new AuthenticationInfo();
    saslAuthState      = null;
    bindInProgress     = false;
    persistentSearches = new CopyOnWriteArrayList<PersistentSearch>();
    sizeLimit          = DirectoryServer.getSizeLimit();
    timeLimit          = DirectoryServer.getTimeLimit();
    idleTimeLimit      = DirectoryServer.getIdleTimeLimit();
    lookthroughLimit   = DirectoryServer.getLookthroughLimit();
    finalized          = false;
    privileges         = new HashSet<Privilege>();
    networkGroup       = NetworkGroup.getDefaultNetworkGroup();
    networkGroup.addConnection(this);
    mustEvaluateNetworkGroup = true;
    if (debugEnabled())
      {
        Message message =
                INFO_CHANGE_NETWORK_GROUP.get(
                  getConnectionID(),
                  "null",
                  networkGroup.getID());
        TRACER.debugMessage(DebugLogLevel.INFO, message.toString());
      }

  }



  /**
   * Performs any internal cleanup that may be necessary when this
   * client connection is disconnected, or if not on disconnec, then
   * ultimately whenever it is reaped by the garbage collector.  In
   * this case, it will be used to ensure that the connection is
   * deregistered with the {@code AuthenticatedUsers} manager, and
   * will then invoke the {@code finalizeClientConnection} method.
   */
 @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.PRIVATE,
      mayInstantiate=false,
      mayExtend=false,
      mayInvoke=true,
      notes="This method should only be invoked by connection " +
             "handlers.")
  protected final void finalizeConnectionInternal()
  {
    if (finalized)
    {
      return;
    }

    finalized = true;

    // Deregister with the set of authenticated users.
    Entry authNEntry = authenticationInfo.getAuthenticationEntry();
    Entry authZEntry = authenticationInfo.getAuthorizationEntry();

    if (authNEntry != null)
    {
      if ((authZEntry == null) ||
          authZEntry.getDN().equals(authNEntry.getDN()))
      {
        DirectoryServer.getAuthenticatedUsers().remove(
             authNEntry.getDN(), this);
      }
      else
      {
        DirectoryServer.getAuthenticatedUsers().remove(
             authNEntry.getDN(), this);
        DirectoryServer.getAuthenticatedUsers().remove(
             authZEntry.getDN(), this);
      }
    }
    else if (authZEntry != null)
    {
      DirectoryServer.getAuthenticatedUsers().remove(
           authZEntry.getDN(), this);
    }

    networkGroup.removeConnection(this);

    try
    {
      finalizeClientConnection();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Performs any cleanup work that may be necessary when this client
   * connection is terminated.  By default, no action is taken.
   * <BR><BR>
   * If possible, this method will be invoked when the client
   * connection is disconnected.  If it isn't invoked at that time,
   * then it will be called when the client connection object is
   * finalized by the garbage collector.
   */
 @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.VOLATILE,
      mayInstantiate=false,
      mayExtend=true,
      mayInvoke=false)
  protected void finalizeClientConnection()
  {
    // No implementation is required by default.
  }



  /**
   * Retrieves the time that this connection was established, measured
   * in the number of milliseconds since January 1, 1970 UTC.
   *
   * @return  The time that this connection was established, measured
   *          in the number of milliseconds since January 1, 1970 UTC.
   */
  public final long getConnectTime()
  {
    return connectTime;
  }



  /**
   * Retrieves a string representation of the time that this
   * connection was established.
   *
   * @return  A string representation of the time that this connection
   *          was established.
   */
  public final String getConnectTimeString()
  {
    return connectTimeString;
  }



  /**
   * Retrieves the unique identifier that has been assigned to this
   * connection.
   *
   * @return  The unique identifier that has been assigned to this
   *          connection.
   */
  public abstract long getConnectionID();



  /**
   * Retrieves the connection handler that accepted this client
   * connection.
   *
   * @return  The connection handler that accepted this client
   *          connection.
   */
  public abstract ConnectionHandler getConnectionHandler();



  /**
   * Retrieves the protocol that the client is using to communicate
   * with the Directory Server.
   *
   * @return  The protocol that the client is using to communicate
   *          with the Directory Server.
   */
  public abstract String getProtocol();



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return  A string representation of the address of the client.
   */
  public abstract String getClientAddress();



  /**
   * Retrieves the port number for this connection on the client
   * system if available.
   *
   * @return The port number for this connection on the client system
   *         or -1 if there is no client port associated with this
   *         connection (e.g. internal client).
   */
  public abstract int getClientPort();



  /**
   * Retrieves the address and port (if available) of the client
   * system, separated by a colon.
   *
   * @return The address and port of the client system, separated by a
   *         colon.
   */
  public final String getClientHostPort()
  {
    int port = getClientPort();
    if (port >= 0)
    {
      return getClientAddress() + ":" + port;
    }
    else
    {
      return getClientAddress();
    }
  }



  /**
   * Retrieves a string representation of the address on the server to
   * which the client connected.
   *
   * @return  A string representation of the address on the server to
   *          which the client connected.
   */
  public abstract String getServerAddress();




  /**
   * Retrieves the port number for this connection on the server
   * system if available.
   *
   * @return The port number for this connection on the server system
   *         or -1 if there is no server port associated with this
   *         connection (e.g. internal client).
   */
  public abstract int getServerPort();



  /**
   * Retrieves the address and port of the server system, separated by
   * a colon.
   *
   * @return The address and port of the server system, separated by a
   *         colon.
   */
  public final String getServerHostPort()
  {
    int port = getServerPort();
    if (port >= 0)
    {
      return getServerAddress() + ":" + port;
    }
    else
    {
      return getServerAddress();
    }
  }



  /**
   * Retrieves the {@code java.net.InetAddress} associated with the
   * remote client system.
   *
   * @return  The {@code java.net.InetAddress} associated with the
   *          remote client system.  It may be {@code null} if the
   *          client is not connected over an IP-based connection.
   */
  public abstract InetAddress getRemoteAddress();



  /**
   * Retrieves the {@code java.net.InetAddress} for the Directory
   * Server system to which the client has established the connection.
   *
   * @return  The {@code java.net.InetAddress} for the Directory
   *          Server system to which the client has established the
   *          connection.  It may be {@code null} if the client is not
   *          connected over an IP-based connection.
   */
  public abstract InetAddress getLocalAddress();



  /**
   * Indicates whether this client connection is currently using a
   * secure mechanism to communicate with the server.  Note that this
   * may change over time based on operations performed by the client
   * or server (e.g., it may go from {@code false} to {@code true} if
   * if the client uses the StartTLS extended operation).
   *
   * @return  {@code true} if the client connection is currently using
   *          a secure mechanism to communicate with the server, or
   *          {@code false} if not.
   */
  public abstract boolean isSecure();



  /**
   * Retrieves the connection security provider for this client
   * connection.
   *
   * @return  The connection security provider for this client
   *          connection.
   */
  public abstract ConnectionSecurityProvider
                       getConnectionSecurityProvider();



  /**
   * Specifies the connection security provider for this client
   * connection.
   *
   * @param  securityProvider  The connection security provider to use
   *                           for communication on this client
   *                           connection.
   */
  public abstract void setConnectionSecurityProvider(
                            ConnectionSecurityProvider
                                 securityProvider);



  /**
   * Retrieves the human-readable name of the security mechanism that
   * is used to protect communication with this client.
   *
   * @return  The human-readable name of the security mechanism that
   *          is used to protect communication with this client, or
   *          {@code null} if no security is in place.
   */
  public abstract String getSecurityMechanism();



  /**
   * Retrieves a {@code Selector} that may be used to ensure that
   * write  operations complete in a timely manner, or terminate the
   * connection in the event that they fail to do so.  This is an
   * optional method for client connections, and the default
   * implementation returns {@code null} to indicate that the maximum
   * blocked write time limit is not supported for this connection.
   * Subclasses that do wish to support this functionality should
   * return a valid {@code Selector} object.
   *
   * @return  The {@code Selector} that may be used to ensure that
   *          write operations complete in a timely manner, or
   *          {@code null} if this client connection does not support
   *          maximum blocked write time limit functionality.
   */
  public Selector getWriteSelector()
  {
    // There will not be a write selector in the default
    // implementation.
    return null;
  }



  /**
   * Retrieves the maximum length of time in milliseconds that
   * attempts to write data to the client should be allowed to block.
   * A value of zero indicates there should be no limit.
   *
   * @return  The maximum length of time in milliseconds that attempts
   *          to write data to the client should be allowed to block,
   *          or zero if there should be no limit.
   */
  public long getMaxBlockedWriteTimeLimit()
  {
    // By default, we'll return 0, which indicates that there should
    // be no maximum time limit.  Subclasses should override this if
    // they want to support a maximum blocked write time limit.
    return 0L;
  }



  /**
   * Retrieves the total number of operations performed
   * on this connection.
   *
   * @return The total number of operations performed
   * on this connection.
   */
  public abstract long getNumberOfOperations();

  /**
   * Indicates whether the network group must be evaluated for
   * the next connection.
   * @param operation The operation going to be performed. Bind
   *                  operations imply a network group evaluation.
   * @return boolean indicating if the network group must be evaluated
   */
  public boolean mustEvaluateNetworkGroup(
          PreParseOperation operation) {
    //  Connections inside the internal network group MUST NOT
    // change network group
    if (this.networkGroup == NetworkGroup.getInternalNetworkGroup()) {
      return false;
    }
    // Connections inside the admin network group MUST NOT
    // change network group
    if (this.networkGroup == NetworkGroup.getAdminNetworkGroup()) {
      return false;
    }

    // If the operation is a BIND, the network group MUST be evaluated
    if (operation != null
        && operation.getOperationType() == OperationType.BIND) {
      return true;
    }

    return mustEvaluateNetworkGroup;
  }

  /**
   * Indicates that the network group will have to be evaluated
   * for the next connection.
   *
   * @param bool true if the network group must be evaluated
   */
  public void mustEvaluateNetworkGroup(boolean bool) {
      mustEvaluateNetworkGroup = bool;
  }

  /**
   * Indicates that the data in the provided buffer has been read from
   * the client and should be processed.  The contents of the provided
   * buffer will be in clear-text (the data may have been passed
   * through a connection security provider to obtain the clear-text
   * version), and may contain part or all of one or more client
   * requests.
   *
   * @param  buffer  The byte buffer containing the data available for
   *                 reading.
   *
   * @return  {@code true} if all the data in the provided buffer was
   *          processed and the client connection can remain
   *          established, or {@code false} if a decoding error
   *          occurred and requests from this client should no longer
   *          be processed.  Note that if this method does return
   *          {@code false}, then it must have already disconnected
   *          the client.
   */
  public abstract boolean processDataRead(ByteBuffer buffer);



  /**
   * Sends a response to the client based on the information in the
   * provided operation.
   *
   * @param  operation  The operation for which to send the response.
   */
  public abstract void sendResponse(Operation operation);



  /**
   * Sends the provided search result entry to the client.
   *
   * @param  searchOperation  The search operation with which the
   *                          entry is associated.
   * @param  searchEntry      The search result entry to be sent to
   *                          the client.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to send the entry to the client and
   *                              the search should be terminated.
   */
  public abstract void sendSearchEntry(
                            SearchOperation searchOperation,
                            SearchResultEntry searchEntry)
         throws DirectoryException;



  /**
   * Sends the provided search result reference to the client.
   *
   * @param  searchOperation  The search operation with which the
   *                          reference is associated.
   * @param  searchReference  The search result reference to be sent
   *                          to the client.
   *
   * @return  {@code true} if the client is able to accept referrals,
   *          or {@code false} if the client cannot handle referrals
   *          and no more attempts should be made to send them for the
   *          associated search operation.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to send the reference to the client
   *                              and the search should be terminated.
   */
  public abstract boolean sendSearchReference(
                               SearchOperation searchOperation,
                               SearchResultReference searchReference)
         throws DirectoryException;



  /**
   * Invokes the intermediate response plugins on the provided
   * response message and sends it to the client.
   *
   * @param  intermediateResponse  The intermediate response message
   *                               to be sent.
   *
   * @return  {@code true} if processing on the associated operation
   *          should continue, or {@code false} if not.
   */
  public final boolean sendIntermediateResponse(
                            IntermediateResponse intermediateResponse)
  {
    // Invoke the intermediate response plugins for the response
    // message.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    PluginResult.IntermediateResponse pluginResult =
         pluginConfigManager.invokeIntermediateResponsePlugins(
                                  intermediateResponse);

    boolean continueProcessing = true;
    if (pluginResult.sendResponse())
    {
      continueProcessing =
           sendIntermediateResponseMessage(intermediateResponse);
    }

    return (continueProcessing && pluginResult.continueProcessing());
  }




  /**
   * Sends the provided intermediate response message to the client.
   *
   * @param  intermediateResponse  The intermediate response message
   *                               to be sent.
   *
   * @return  {@code true} if processing on the associated operation
   *          should continue, or {@code false} if not.
   */
  protected abstract boolean
       sendIntermediateResponseMessage(
            IntermediateResponse intermediateResponse);



  /**
   * Closes the connection to the client, optionally sending it a
   * message indicating the reason for the closure.  Note that the
   * ability to send a notice of disconnection may not be available
   * for all protocols or under all circumstances.  Also note that
   * when attempting to disconnect a client connection as a part of
   * operation processing (e.g., within a plugin or other extension),
   * the {@code disconnectClient} method within that operation should
   * be called rather than invoking this method directly.
   * <BR><BR>
   * All subclasses must invoke the {@code finalizeConnectionInternal}
   * method during the course of processing this method.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification to the client that the
   *                           connection will be closed.
   * @param  message           The message to send to the client.  It
   *                           may be {@code null} if no notification
   *                           is to be sent.
   */
  public abstract void disconnect(DisconnectReason disconnectReason,
                                  boolean sendNotification,
                                  Message message);



  /**
   * Indicates whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed
   * until the bind has completed.
   *
   * @return  {@code true} if a bind operation is in progress on this
   *          connection, or {@code false} if not.
   */
  public boolean bindInProgress()
  {
    return bindInProgress;
  }



  /**
   * Specifies whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed
   * until the bind has completed.
   *
   * @param  bindInProgress  Specifies whether a bind operation is in
   *                         progress on this client connection.
   */
  public void setBindInProgress(boolean bindInProgress)
  {
    this.bindInProgress = bindInProgress;
  }



  /**
   * Indicates whether the user associated with this client connection
   * must change their password before they will be allowed to do
   * anything else.
   *
   * @return  {@code true} if the user associated with this client
   *          connection must change their password before they will
   *          be allowed to do anything else, or {@code false} if not.
   */
  public final boolean mustChangePassword()
  {
    if (authenticationInfo == null)
    {
      return false;
    }
    else
    {
      return authenticationInfo.mustChangePassword();
    }
  }



  /**
   * Specifies whether the user associated with this client connection
   * must change their password before they will be allowed to do
   * anything else.
   *
   * @param  mustChangePassword  Specifies whether the user associated
   *                             with this client connection must
   *                             change their password before they
   *                             will be allowed to do anything else.
   */
  public final void setMustChangePassword(boolean mustChangePassword)
  {
    if (authenticationInfo == null)
    {
      setAuthenticationInfo(new AuthenticationInfo());
    }

    authenticationInfo.setMustChangePassword(mustChangePassword);
  }



  /**
   * Retrieves the set of operations in progress for this client
   * connection.  This list must not be altered by any caller.
   *
   * @return  The set of operations in progress for this client
   *          connection.
   */
  public abstract Collection<AbstractOperation>
                                      getOperationsInProgress();



  /**
   * Retrieves the operation in progress with the specified message
   * ID.
   *
   * @param  messageID  The message ID of the operation to retrieve.
   *
   * @return  The operation in progress with the specified message ID,
   *          or {@code null} if no such operation could be found.
   */
  public abstract AbstractOperation
                          getOperationInProgress(int messageID);



  /**
   * Removes the provided operation from the set of operations in
   * progress for this client connection.  Note that this does not
   * make any attempt to cancel any processing that may already be in
   * progress for the operation.
   *
   * @param  messageID  The message ID of the operation to remove from
   *                    the set of operations in progress.
   *
   * @return  {@code true} if the operation was found and removed from
   *          the set of operations in progress, or {@code false} if
   *          not.
   */
  public abstract boolean removeOperationInProgress(int messageID);



  /**
   * Retrieves the set of persistent searches registered for this
   * client.
   *
   * @return  The set of persistent searches registered for this
   *          client.
   */
  public final CopyOnWriteArrayList<PersistentSearch>
                    getPersistentSearches()
  {
    return persistentSearches;
  }



  /**
   * Registers the provided persistent search for this client.  Note
   * that this should only be called by
   * {@code DirectoryServer.registerPersistentSearch} and not through
   * any other means.
   *
   * @param  persistentSearch  The persistent search to register for
   *                           this client.
   */
 @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.PRIVATE,
      mayInstantiate=false,
      mayExtend=false,
      mayInvoke=false)
  public final void registerPersistentSearch(PersistentSearch
                                                  persistentSearch)
  {
    persistentSearches.add(persistentSearch);
  }



  /**
   * Deregisters the provided persistent search for this client.  Note
   * that this should only be called by
   * {@code DirectoryServer.deregisterPersistentSearch} and not
   * through any other means.
   *
   * @param  persistentSearch  The persistent search to deregister for
   *                           this client.
   */
 @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.PRIVATE,
      mayInstantiate=false,
      mayExtend=false,
      mayInvoke=false)
  public final void deregisterPersistentSearch(PersistentSearch
                                                    persistentSearch)
  {
    persistentSearches.remove(persistentSearch);
  }



  /**
   * Attempts to cancel the specified operation.
   *
   * @param  messageID      The message ID of the operation to cancel.
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   *
   * @return  A cancel result that either indicates that the cancel
   *          was successful or provides a reason that it was not.
   */
  public abstract CancelResult cancelOperation(int messageID,
                                    CancelRequest cancelRequest);



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   */
  public abstract void cancelAllOperations(
                            CancelRequest cancelRequest);



  /**
   * Attempts to cancel all operations in progress on this connection
   * except the operation with the specified message ID.
   *
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   * @param  messageID      The message ID of the operation that
   *                        should not be canceled.
   */
  public abstract void cancelAllOperationsExcept(
                            CancelRequest cancelRequest,
                            int messageID);



  /**
   * Retrieves information about the authentication that has been
   * performed for this connection.
   *
   * @return  Information about the user that is currently
   *          authenticated on this connection.
   */
  public AuthenticationInfo getAuthenticationInfo()
  {
    return authenticationInfo;
  }



  /**
   * Specifies information about the authentication that has been
   * performed for this connection.
   *
   * @param  authenticationInfo  Information about the authentication
   *                             that has been performed for this
   *                             connection.  It should not be
   *                             {@code null}.
   */
  public void setAuthenticationInfo(AuthenticationInfo
                                         authenticationInfo)
  {
    if (this.authenticationInfo != null)
    {
      Entry authNEntry =
                 this.authenticationInfo.getAuthenticationEntry();
      Entry authZEntry =
                 this.authenticationInfo.getAuthorizationEntry();

      if (authNEntry != null)
      {
        if ((authZEntry == null) ||
            authZEntry.getDN().equals(authNEntry.getDN()))
        {
          DirectoryServer.getAuthenticatedUsers().remove(
               authNEntry.getDN(), this);
        }
        else
        {
          DirectoryServer.getAuthenticatedUsers().remove(
               authNEntry.getDN(), this);
          DirectoryServer.getAuthenticatedUsers().remove(
               authZEntry.getDN(), this);
        }
      }
      else if (authZEntry != null)
      {
        DirectoryServer.getAuthenticatedUsers().remove(
             authZEntry.getDN(), this);
      }
    }

    if (authenticationInfo == null)
    {
      this.authenticationInfo = new AuthenticationInfo();
      updatePrivileges(null, false);
    }
    else
    {
      this.authenticationInfo = authenticationInfo;

      Entry authNEntry = authenticationInfo.getAuthenticationEntry();
      Entry authZEntry = authenticationInfo.getAuthorizationEntry();

      if (authNEntry != null)
      {
        if ((authZEntry == null) ||
            authZEntry.getDN().equals(authNEntry.getDN()))
        {
          DirectoryServer.getAuthenticatedUsers().put(
               authNEntry.getDN(), this);
        }
        else
        {
          DirectoryServer.getAuthenticatedUsers().put(
               authNEntry.getDN(), this);
          DirectoryServer.getAuthenticatedUsers().put(
               authZEntry.getDN(), this);
        }
      }
      else
      {
        if (authZEntry != null)
        {
          DirectoryServer.getAuthenticatedUsers().put(
               authZEntry.getDN(), this);
        }
      }

      updatePrivileges(authZEntry, authenticationInfo.isRoot());
    }
  }



  /**
   * Updates the cached entry associated with either the
   * authentication and/or authorization identity with the provided
   * version.
   *
   * @param  oldEntry  The user entry currently serving as the
   *                   authentication and/or authorization identity.
   * @param  newEntry  The updated entry that should replace the
   *                   existing entry.  It may optionally have a
   *                   different DN than the old entry.
   */
  public final void updateAuthenticationInfo(Entry oldEntry,
                                             Entry newEntry)
  {
    Entry authNEntry = authenticationInfo.getAuthenticationEntry();
    Entry authZEntry = authenticationInfo.getAuthorizationEntry();

    if ((authNEntry != null) &&
        authNEntry.getDN().equals(oldEntry.getDN()))
    {
      if ((authZEntry == null) ||
          (! authZEntry.getDN().equals(authNEntry.getDN())))
      {
        setAuthenticationInfo(
             authenticationInfo.duplicate(newEntry, authZEntry));
        updatePrivileges(newEntry, authenticationInfo.isRoot());
      }
      else
      {
        setAuthenticationInfo(
             authenticationInfo.duplicate(newEntry, newEntry));
        updatePrivileges(newEntry, authenticationInfo.isRoot());
      }
    }
    else if ((authZEntry != null) &&
             (authZEntry.getDN().equals(oldEntry.getDN())))
    {
      setAuthenticationInfo(
           authenticationInfo.duplicate(authNEntry, newEntry));
    }
  }



  /**
   * Sets properties in this client connection to indicate that the
   * client is unauthenticated.  This includes setting the
   * authentication info structure to an empty default, as well as
   * setting the size and time limit values to their defaults.
   */
  public void setUnauthenticated()
  {
    setAuthenticationInfo(new AuthenticationInfo());
    this.sizeLimit = networkGroup.getSearchSizeLimit();
    if (this.sizeLimit == -1) {
        this.sizeLimit = DirectoryServer.getSizeLimit();
    }
    this.timeLimit = networkGroup.getSearchDurationLimit();
    if (this.timeLimit == -1) {
      this.timeLimit = DirectoryServer.getTimeLimit();
    }
  }



  /**
   * Indicates whether the authenticated client has the specified
   * privilege.
   *
   * @param  privilege  The privilege for which to make the
   *                    determination.
   * @param  operation  The operation being processed which needs to
   *                    make the privilege determination, or
   *                    {@code null} if there is no associated
   *                    operation.
   *
   * @return  {@code true} if the authenticated client has the
   *          specified privilege, or {@code false} if not.
   */
  public boolean hasPrivilege(Privilege privilege,
                              Operation operation)
  {
    if (privilege == Privilege.PROXIED_AUTH)
    {
      // This determination should always be made against the
      // authentication identity rather than the authorization
      // identity.
      Entry authEntry = authenticationInfo.getAuthenticationEntry();
      boolean isRoot  = authenticationInfo.isRoot();
      return getPrivileges(authEntry,
                           isRoot).contains(Privilege.PROXIED_AUTH) ||
             DirectoryServer.isDisabled(Privilege.PROXIED_AUTH);
    }

    boolean result;
    if (operation == null)
    {
      result = privileges.contains(privilege);
      if (debugEnabled())
      {
        DN authDN = authenticationInfo.getAuthenticationDN();

        Message message = INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGE
                .get(getConnectionID(), -1L,
                     String.valueOf(authDN),
                     privilege.getName(), result);
        TRACER.debugMessage(DebugLogLevel.INFO, message.toString());
      }
    }
    else
    {
      if (operation.getAuthorizationDN().equals(
               authenticationInfo.getAuthorizationDN()) ||
          (operation.getAuthorizationDN().equals(DN.NULL_DN) &&
           !authenticationInfo.isAuthenticated())) {
        result = privileges.contains(privilege) ||
                 DirectoryServer.isDisabled(privilege);
        if (debugEnabled())
        {
          DN authDN = authenticationInfo.getAuthenticationDN();

          Message message =
                  INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGE.get(
                    getConnectionID(),
                    operation.getOperationID(),
                    String.valueOf(authDN),
                    privilege.getName(), result);
          TRACER.debugMessage(DebugLogLevel.INFO, message.toString());
        }
      }
      else
      {
        Entry authorizationEntry = operation.getAuthorizationEntry();
        if (authorizationEntry == null)
        {
          result = false;
        }
        else
        {
          boolean isRoot =
               DirectoryServer.isRootDN(authorizationEntry.getDN());
          result = getPrivileges(authorizationEntry,
                                 isRoot).contains(privilege) ||
                   DirectoryServer.isDisabled(privilege);
        }
      }
    }

    return result;
  }



  /**
   * Indicates whether the authenticate client has all of the
   * specified privileges.
   *
   * @param  privileges  The array of privileges for which to make the
   *                     determination.
   * @param  operation   The operation being processed which needs to
   *                     make the privilege determination, or
   *                     {@code null} if there is no associated
   *                     operation.
   *
   * @return  {@code true} if the authenticated client has all of the
   *          specified privileges, or {@code false} if not.
   */
  public boolean hasAllPrivileges(Privilege[] privileges,
                                  Operation operation)
  {
    HashSet<Privilege> privSet = this.privileges;

    if (debugEnabled())
    {
      for (Privilege p : privileges)
      {
        if (! privSet.contains(p))
        {
          return false;
        }
      }

      return true;
    }
    else
    {
      boolean result = true;
      StringBuilder buffer = new StringBuilder();
      buffer.append("{");

      for (int i=0; i < privileges.length; i++)
      {
        if (i > 0)
        {
          buffer.append(",");
        }

        buffer.append(privileges[i].getName());

        if (! privSet.contains(privileges[i]))
        {
          result = false;
        }
      }

      buffer.append(" }");

      if (operation == null)
      {
        DN authDN = authenticationInfo.getAuthenticationDN();

        Message message =
                INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGES.get(
                  getConnectionID(), -1L,
                  String.valueOf(authDN),
                  buffer.toString(), result);
        TRACER.debugMessage(DebugLogLevel.INFO,
                message.toString());
      }
      else
      {
        DN authDN = authenticationInfo.getAuthenticationDN();

        Message message = INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGES
                .get(
                  getConnectionID(),
                  operation.getOperationID(),
                  String.valueOf(authDN),
                  buffer.toString(), result);
        TRACER.debugMessage(DebugLogLevel.INFO, message.toString());
      }

      return result;
    }
  }



  /**
   * Retrieves the set of privileges encoded in the provided entry.
   *
   * @param  entry   The entry to use to obtain the privilege
   *                 information.
   * @param  isRoot  Indicates whether the set of root privileges
   *                 should be automatically included in the
   *                 privilege set.
   *
   * @return  A set of the privileges that should be assigned.
   */
  private HashSet<Privilege> getPrivileges(Entry entry,
                                           boolean isRoot)
  {
    if (entry == null)
    {
      return new HashSet<Privilege>(0);
    }

    HashSet<Privilege> newPrivileges = new HashSet<Privilege>();
    HashSet<Privilege> removePrivileges = new HashSet<Privilege>();

    if (isRoot)
    {
      newPrivileges.addAll(DirectoryServer.getRootPrivileges());
    }

    AttributeType privType =
         DirectoryServer.getAttributeType(OP_ATTR_PRIVILEGE_NAME);
    List<Attribute> attrList = entry.getAttribute(privType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          String privName = toLowerCase(v.getStringValue());

          // If the name of the privilege is prefixed with a minus
          // sign, then we will take away that privilege from the
          // user.  We'll handle that at the end so that we can make
          // sure it's not added back later.
          if (privName.startsWith("-"))
          {
            privName = privName.substring(1);
            Privilege p = Privilege.privilegeForName(privName);
            if (p == null)
            {
              // FIXME -- Generate an administrative alert.

              // We don't know what privilege to remove, so we'll
              // remove all of them.
              newPrivileges.clear();
              return newPrivileges;
            }
            else
            {
              removePrivileges.add(p);
            }
          }
          else
          {
            Privilege p = Privilege.privilegeForName(privName);
            if (p == null)
            {
              // FIXME -- Generate an administrative alert.
            }
            else
            {
              newPrivileges.add(p);
            }
          }
        }
      }
    }

    for (Privilege p : removePrivileges)
    {
      newPrivileges.remove(p);
    }

    return newPrivileges;
  }



  /**
   * Updates the privileges associated with this client connection
   * object based on the provided entry for the authentication
   * identity.
   *
   * @param  entry   The entry for the authentication identity
   *                 associated with this client connection.
   * @param  isRoot  Indicates whether the associated user is a root
   *                 user and should automatically inherit the root
   *                 privilege set.
   */
  private void updatePrivileges(Entry entry, boolean isRoot)
  {
    privileges = getPrivileges(entry, isRoot);
  }



  /**
   * Retrieves an opaque set of information that may be used for
   * processing multi-stage SASL binds.
   *
   * @return  An opaque set of information that may be used for
   *          processing multi-stage SASL binds.
   */
  public final Object getSASLAuthStateInfo()
  {
    return saslAuthState;
  }



  /**
   * Specifies an opaque set of information that may be used for
   * processing multi-stage SASL binds.
   *
   * @param  saslAuthState  An opaque set of information that may be
   *                        used for processing multi-stage SASL
   *                        binds.
   */
  public final void setSASLAuthStateInfo(Object saslAuthState)
  {
    this.saslAuthState = saslAuthState;
  }



  /**
   * Retrieves the size limit that will be enforced for searches
   * performed using this client connection.
   *
   * @return  The size limit that will be enforced for searches
   *          performed using this client connection.
   */
  public final int getSizeLimit()
  {
    return sizeLimit;
  }



  /**
   * Specifies the size limit that will be enforced for searches
   * performed using this client connection.
   *
   * @param  sizeLimit  The size limit that will be enforced for
   *                    searches performed using this client
   *                    connection.
   */
  public void setSizeLimit(int sizeLimit)
  {
    this.sizeLimit = sizeLimit;
  }



  /**
   * Retrieves the maximum length of time in milliseconds that this
   * client connection will be allowed to remain idle before it should
   * be disconnected.
   *
   * @return  The maximum length of time in milliseconds that this
   *          client connection will be allowed to remain idle before
   *          it should be disconnected.
   */
  public final long getIdleTimeLimit()
  {
    return idleTimeLimit;
  }



  /**
   * Specifies the maximum length of time in milliseconds that this
   * client connection will be allowed to remain idle before it should
   * be disconnected.
   *
   * @param  idleTimeLimit  The maximum length of time in milliseconds
   *                        that this client connection will be
   *                        allowed to remain idle before it should be
   *                        disconnected.
   */
  public void setIdleTimeLimit(long idleTimeLimit)
  {
    this.idleTimeLimit = idleTimeLimit;
  }



  /**
   * Retrieves the default maximum number of entries that should
   * checked for matches during a search.
   *
   * @return  The default maximum number of entries that should
   *          checked for matches during a search.
   */
  public final int getLookthroughLimit()
  {
    return lookthroughLimit;
  }



  /**
   * Specifies the default maximum number of entries that should
   * be checked for matches during a search.
   *
   * @param  lookthroughLimit  The default maximum number of
   *                           entries that should be check for
   *                           matches during a search.
   */
  public void setLookthroughLimit(int lookthroughLimit)
  {
    this.lookthroughLimit = lookthroughLimit;
  }



  /**
   * Retrieves the time limit that will be enforced for searches
   * performed using this client connection.
   *
   * @return  The time limit that will be enforced for searches
   *          performed using this client connection.
   */
  public final int getTimeLimit()
  {
    return timeLimit;
  }



  /**
   * Specifies the time limit that will be enforced for searches
   * performed using this client connection.
   *
   * @param  timeLimit  The time limit that will be enforced for
   *                    searches performed using this client
   *                    connection.
   */
  public void setTimeLimit(int timeLimit)
  {
    this.timeLimit = timeLimit;
  }



  /**
   * Retrieves a one-line summary of this client connection in a form
   * that is suitable for including in the monitor entry for the
   * associated connection handler.  It should be in a format that is
   * both humand readable and machine parseable (e.g., a
   * space-delimited name-value list, with quotes around the values).
   *
   * @return  A one-line summary of this client connection in a form
   *          that is suitable for including in the monitor entry for
   *          the associated connection handler.
   */
  public abstract String getMonitorSummary();



  /**
   * Indicates whether the user associated with this client connection
   * should be considered a member of the specified group, optionally
   * evaluated within the context of the provided operation.  If an
   * operation is given, then the determination should be made based
   * on the authorization identity for that operation.  If the
   * operation is {@code null}, then the determination should be made
   * based on the authorization identity for this client connection.
   * Note that this is a point-in-time determination and the caller
   * must not cache the result.
   *
   * @param  group      The group for which to make the determination.
   * @param  operation  The operation to use to obtain the
   *                    authorization identity for which to make the
   *                    determination, or {@code null} if the
   *                    authorization identity should be obtained from
   *                    this client connection.
   *
   * @return  {@code true} if the target user is currently a member of
   *          the specified group, or {@code false} if not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                             to make the determination.
   */
  public boolean isMemberOf(Group group, Operation operation)
         throws DirectoryException
  {
    if (operation == null)
    {
      return group.isMember(authenticationInfo.getAuthorizationDN());
    }
    else
    {
      return group.isMember(operation.getAuthorizationDN());
    }
  }



  /**
   * Retrieves the set of groups in which the user associated with
   * this client connection may be considered to be a member.  If an
   * operation is provided, then the determination should be made
   * based on the authorization identity for that operation.  If the
   * operation is {@code null}, then it should be made based on the
   * authorization identity for this client connection.  Note that
   * this is a point-in-time determination and the caller must not
   * cache the result.
   *
   * @param  operation  The operation to use to obtain the
   *                    authorization identity for which to retrieve
   *                    the associated groups, or {@code null} if the
   *                    authorization identity should be obtained from
   *                    this client connection.
   *
   * @return  The set of groups in which the target user is currently
   *          a member.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to make the determination.
   */
  public Set<Group> getGroups(Operation operation)
         throws DirectoryException
  {
    // FIXME -- This probably isn't the most efficient implementation.
    DN authzDN;
    if (operation == null)
    {
      if ((authenticationInfo == null) ||
          (! authenticationInfo.isAuthenticated()))
      {
        authzDN = null;
      }
      else
      {
        authzDN = authenticationInfo.getAuthorizationDN();
      }
    }
    else
    {
      authzDN = operation.getAuthorizationDN();
    }

    if ((authzDN == null) || authzDN.isNullDN())
    {
      return java.util.Collections.<Group>emptySet();
    }

    Entry userEntry = DirectoryServer.getEntry(authzDN);
    if (userEntry == null)
    {
      return java.util.Collections.<Group>emptySet();
    }

    HashSet<Group> groupSet = new HashSet<Group>();
    for (Group g :
         DirectoryServer.getGroupManager().getGroupInstances())
    {
      if (g.isMember(userEntry))
      {
        groupSet.add(g);
      }
    }

    return groupSet;
  }



  /**
   * Retrieves the DN of the key manager provider that should be used
   * for operations requiring access to a key manager.  The default
   * implementation returns {@code null} to indicate that no key
   * manager provider is avaialble, but subclasses should override
   * this method to return a valid DN if they perform operations which
   * may need access to a key manager.
   *
   * @return  The DN of the key manager provider that should be used
   *          for operations requiring access to a key manager, or
   *          {@code null} if there is no key manager provider
   *          configured for this client connection.
   */
  public DN getKeyManagerProviderDN()
  {
    // In the default implementation, we'll return null.
    return null;
  }



  /**
   * Retrieves the DN of the trust manager provider that should be
   * used for operations requiring access to a trust manager.  The
   * default implementation returns {@code null} to indicate that no
   * trust manager provider is avaialble, but subclasses should
   * override this method to return a valid DN if they perform
   * operations which may need access to a trust manager.
   *
   * @return  The DN of the trust manager provider that should be used
   *          for operations requiring access to a trust manager, or
   *          {@code null} if there is no trust manager provider
   *          configured for this client connection.
   */
  public DN getTrustManagerProviderDN()
  {
    // In the default implementation, we'll return null.
    return null;
  }



  /**
   * Retrieves the alias of the server certificate that should be used
   * for operations requiring a server certificate.  The default
   * implementation returns {@code null} to indicate that any alias is
   * acceptable.
   *
   * @return  The alias of the server certificate that should be used
   *          for operations requring a server certificate, or
   *          {@code null} if any alias is acceptable.
   */
  public String getCertificateAlias()
  {
    // In the default implementation, we'll return null.
    return null;
  }



  /**
   * Retrieves a string representation of this client connection.
   *
   * @return  A string representation of this client connection.
   */
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this client connection to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public abstract void toString(StringBuilder buffer);



  /**
   * Performs any work that may be needed before the JVM invokes
   * garbage collection for this object.  In this case, it makes sure
   * to deregister with the Directory Server as a change notification
   * listener.  If a subclass wishes to perform custom finalization
   * processing, then it should override this method and make sure to
   * invoke {@code super.finalize} as its first call.
   */
  protected void finalize()
  {
    finalizeConnectionInternal();
  }


  /**
   * Returns the network group to which the connection belongs.
   *
   * @return the network group attached to the connection
   */
  public final NetworkGroup getNetworkGroup()
  {
    return networkGroup;
  }

  /**
   * Sets the network group to which the connection belongs.
   *
   * @param networkGroup  the network group to which the
   *                      connections belongs to
   */
  public final void setNetworkGroup (NetworkGroup networkGroup)
  {
    if (this.networkGroup != networkGroup) {
      if (debugEnabled())
      {
        Message message =
                INFO_CHANGE_NETWORK_GROUP.get(
                  getConnectionID(),
                  this.networkGroup.getID(),
                  networkGroup.getID());
        TRACER.debugMessage(DebugLogLevel.INFO, message.toString());
      }

      // If there is a change, first remove this connection
      // from the current network group
      this.networkGroup.removeConnection(this);
      // Then set the new network group
      this.networkGroup = networkGroup;
      // And add the connection to the new ng
      this.networkGroup.addConnection(this);

      // The client connection inherits the resource limits
      sizeLimit = networkGroup.getSearchSizeLimit();
      if (sizeLimit == -1) {
        sizeLimit = DirectoryServer.getSizeLimit();
      }
      timeLimit = networkGroup.getSearchDurationLimit();
      if (timeLimit == -1) {
        timeLimit = DirectoryServer.getTimeLimit();
      }
    }
  }



  /**
   * Retrieves the length of time in milliseconds that this client
   * connection has been idle.
   * <BR><BR>
   * Note that the default implementation will always return zero.
   * Subclasses associated with connection handlers should override
   * this method if they wish to provided idle time limit
   * functionality.
   *
   * @return  The length of time in milliseconds that this client
   *          connection has been idle.
   */
  public long getIdleTime()
  {
    return 0L;
  }
}

