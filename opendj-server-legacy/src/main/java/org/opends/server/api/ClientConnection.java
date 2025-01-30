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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2025 3A Systems, LLC.
 */
package org.opends.server.api;

import java.net.InetAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.AuthenticatedUsers;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.operation.RollbackOperation;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of authentication information for this client connection.
   */
  protected AuthenticationInfo authenticationInfo;

  /**
   * Indicates whether a multistage SASL bind is currently in progress
   * on this client connection.  If so, then no other operations
   * should be allowed until the bind completes.
   */
  protected AtomicBoolean saslBindInProgress;

  /**
   * Indicates if a bind request is currently in progress on this client
   * connection. If so, then no further socket reads will occur until the
   * request completes.
   */
  protected AtomicBoolean bindInProgress;

  /**
   * Indicates if a Start TLS request is currently in progress on this client
   * connection. If so, then no further socket reads will occur until the
   * request completes.
   */
  protected AtomicBoolean startTLSInProgress;

  /**
   *  Indicates whether any necessary finalization work has been done for this
   *  client connection.
   */
  private boolean finalized;

  /** The set of privileges assigned to this client connection. */
  private HashSet<Privilege> privileges = new HashSet<>();

  /** The size limit for use with this client connection. */
  private int sizeLimit;
  /** The time limit for use with this client connection. */
  private int timeLimit;
  /** The lookthrough limit for use with this client connection. */
  private int lookthroughLimit;
  /** The time that this client connection was established. */
  private final long connectTime;
  /** The idle time limit for this client connection. */
  private long idleTimeLimit;

  /**
   * The opaque information used for storing intermediate state information
   * needed across multi-stage SASL binds.
   */
  private Object saslAuthState;

  /** A string representation of the time that this client connection was established. */
  private final String connectTimeString;

  /** A set of persistent searches registered for this client. */
  private final CopyOnWriteArrayList<PersistentSearch> persistentSearches = new CopyOnWriteArrayList<>();

  /** Performs the appropriate initialization generic to all client connections. */
  protected ClientConnection()
  {
    connectTime        = TimeThread.getTime();
    connectTimeString  = TimeThread.getGMTTime();
    authenticationInfo = new AuthenticationInfo();
    saslAuthState      = null;
    saslBindInProgress = new AtomicBoolean(false);
    bindInProgress     = new AtomicBoolean(false);
    startTLSInProgress = new AtomicBoolean(false);
    CoreConfigManager coreConfigManager = DirectoryServer.getCoreConfigManager();
    sizeLimit          = coreConfigManager.getSizeLimit();
    timeLimit          = coreConfigManager.getTimeLimit();
    idleTimeLimit      = DirectoryServer.getIdleTimeLimit();
    lookthroughLimit   = coreConfigManager.getLookthroughLimit();
    finalized          = false;
  }



  /**
   * Performs any internal cleanup that may be necessary when this
   * client connection is disconnected.  In
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

    AuthenticatedUsers authenticatedUsers = DirectoryServer.getAuthenticatedUsers();
    if (authNEntry != null)
    {
      if (authZEntry == null || authZEntry.getName().equals(authNEntry.getName()))
      {
        authenticatedUsers.remove(authNEntry.getName(), this);
      }
      else
      {
        authenticatedUsers.remove(authNEntry.getName(), this);
        authenticatedUsers.remove(authZEntry.getName(), this);
      }
    }
    else if (authZEntry != null)
    {
      authenticatedUsers.remove(authZEntry.getName(), this);
    }
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
  public abstract ConnectionHandler<?> getConnectionHandler();



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
   * Returns whether the Directory Server believes this connection to be valid
   * and available for communication.
   *
   * @return true if the connection is valid, false otherwise
   */
  public abstract boolean isConnectionValid();

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
    // There will not be a write selector in the default implementation.
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
    // Invoke the intermediate response plugins for the response message.
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

    return continueProcessing && pluginResult.continueProcessing();
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
                                  LocalizableMessage message);



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
    return authenticationInfo != null
        && authenticationInfo.mustChangePassword();
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
  public abstract Collection<Operation> getOperationsInProgress();



  /**
   * Retrieves the operation in progress with the specified message ID.
   *
   * @param  messageID  The message ID of the operation to retrieve.
   * @return  The operation in progress with the specified message ID,
   *          or {@code null} if no such operation could be found.
   */
  public abstract Operation getOperationInProgress(int messageID);



  /**
   * Removes the provided operation from the set of operations in
   * progress for this client connection.  Note that this does not
   * make any attempt to cancel any processing that may already be in
   * progress for the operation.
   *
   * @param  messageID  The message ID of the operation to remove from
   *                    the set of operations in progress.
   * @return  {@code true} if the operation was found and removed from
   *          the set of operations in progress, or {@code false} if not.
   */
  public abstract boolean removeOperationInProgress(int messageID);



  /**
   * Retrieves the set of persistent searches registered for this client.
   *
   * @return  The set of persistent searches registered for this client.
   */
  public final List<PersistentSearch> getPersistentSearches()
  {
    return persistentSearches;
  }



  /**
   * Registers the provided persistent search for this client.
   * Note that this should only be called by
   * {@code DirectoryServer.registerPersistentSearch} and not through any other means.
   *
   * @param  persistentSearch  The persistent search to register for this client.
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
    AuthenticatedUsers authenticatedUsers = DirectoryServer.getAuthenticatedUsers();
    if (this.authenticationInfo != null)
    {
      Entry authNEntry = this.authenticationInfo.getAuthenticationEntry();
      Entry authZEntry = this.authenticationInfo.getAuthorizationEntry();

      if (authNEntry != null)
      {
        if (authZEntry == null ||
            authZEntry.getName().equals(authNEntry.getName()))
        {
          authenticatedUsers.remove(authNEntry.getName(), this);
        }
        else
        {
          authenticatedUsers.remove(authNEntry.getName(), this);
          authenticatedUsers.remove(authZEntry.getName(), this);
        }
      }
      else if (authZEntry != null)
      {
        authenticatedUsers.remove(authZEntry.getName(), this);
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
        if (authZEntry == null || authZEntry.getName().equals(authNEntry.getName()))
        {
          authenticatedUsers.put(authNEntry.getName(), this);
        }
        else
        {
          authenticatedUsers.put(authNEntry.getName(), this);
          authenticatedUsers.put(authZEntry.getName(), this);
        }
      }
      else
      {
        if (authZEntry != null)
        {
          authenticatedUsers.put(authZEntry.getName(), this);
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

    if (authNEntry != null && authNEntry.getName().equals(oldEntry.getName()))
    {
      if (authZEntry == null || !authZEntry.getName().equals(authNEntry.getName()))
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
    else if (authZEntry != null && authZEntry.getName().equals(oldEntry.getName()))
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
  }


  /**
   * Indicate whether the specified authorization entry parameter
   * has the specified privilege. The method can be used to perform
   * a "what-if" scenario.
   *
 * @param authorizationEntry The authentication entry to use.
 * @param privilege The privilege to check for.
   *
   * @return  {@code true} if the authentication entry has the
   *          specified privilege, or {@code false} if not.
   */
  public static boolean hasPrivilege(Entry authorizationEntry,
                                   Privilege privilege) {
      boolean isRoot =
          DirectoryServer.isRootDN(authorizationEntry.getName());
      return getPrivileges(authorizationEntry,
              isRoot).contains(privilege) ||
              DirectoryServer.isDisabled(privilege);
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
      return getPrivileges(authEntry, isRoot).contains(Privilege.PROXIED_AUTH) ||
             DirectoryServer.isDisabled(Privilege.PROXIED_AUTH);
    }

    boolean result;
    if (operation == null)
    {
      result = privileges.contains(privilege);
      logger.trace(INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGE,
          getConnectionID(), -1L, authenticationInfo.getAuthenticationDN(),
          privilege.getName(), result);
    }
    else
    {
      if (operation.getAuthorizationDN().equals(
               authenticationInfo.getAuthorizationDN()) ||
          (operation.getAuthorizationDN().equals(DN.rootDN()) &&
           !authenticationInfo.isAuthenticated())) {
        result = privileges.contains(privilege) ||
                 DirectoryServer.isDisabled(privilege);
        logger.trace(INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGE,
            getConnectionID(), operation.getOperationID(),
            authenticationInfo.getAuthenticationDN(),
            privilege.getName(), result);
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
               DirectoryServer.isRootDN(authorizationEntry.getName());
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
  public boolean hasAllPrivileges(Privilege[] privileges, Operation operation)
  {
    final boolean result = hasAllPrivileges0(this.privileges, privileges);
    if (logger.isTraceEnabled())
    {
      long operationID = operation != null ? operation.getOperationID() : -1;
      final DN authDN = authenticationInfo.getAuthenticationDN();
      StringBuilder buffer = toStringBuilder(privileges);
      logger.trace(INFO_CLIENTCONNECTION_AUDIT_HASPRIVILEGES, getConnectionID(), operationID, authDN, buffer, result);
    }
    return result;
  }

  private boolean hasAllPrivileges0(Set<Privilege> privSet, Privilege[] privileges)
  {
    for (Privilege p : privileges)
    {
      if (!privSet.contains(p))
      {
        return false;
      }
    }
    return true;
  }

  private StringBuilder toStringBuilder(Privilege[] privileges)
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("{");
    for (int i = 0; i < privileges.length; i++)
    {
      Privilege privilege = privileges[i];
      if (i > 0)
      {
        buffer.append(",");
      }
      buffer.append(privilege.getName());
    }
    buffer.append(" }");
    return buffer;
  }

  /**
   * Retrieves the set of privileges encoded in the provided entry.
   *
   * @param entry
   *          The entry to use to obtain the privilege information.
   * @param isRoot
   *          Indicates whether the set of root privileges should be automatically included in the
   *          privilege set.
   * @return A set of the privileges that should be assigned.
   */
  private static HashSet<Privilege> getPrivileges(Entry entry,
                                           boolean isRoot)
  {
    if (entry == null)
    {
      return new HashSet<>(0);
    }

    HashSet<Privilege> newPrivileges = new HashSet<>();
    HashSet<Privilege> removePrivileges = new HashSet<>();

    if (isRoot)
    {
      newPrivileges.addAll(DirectoryServer.getRootPrivileges());
    }

    Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
    AttributeType privType = schema.getAttributeType(OP_ATTR_PRIVILEGE_NAME);
    for (Attribute a : entry.getAllAttributes(privType))
    {
      for (ByteString v : a)
      {
        String privName = toLowerCase(v.toString());

        // If the name of the privilege is prefixed with a minus sign,
        // then we will take away that privilege from the user.
        // We'll handle that at the end so that we can make sure it's not added back later.
        if (privName.startsWith("-"))
        {
          privName = privName.substring(1);
          Privilege p = Privilege.privilegeForName(privName);
          if (p == null)
          {
            // FIXME -- Generate an administrative alert.

            // We don't know what privilege to remove, so we'll remove all of them.
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

    newPrivileges.removeAll(removePrivileges);

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
  protected void updatePrivileges(Entry entry, boolean isRoot)
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
   * Return the lowest level channel associated with a connection.
   * This is normally the channel associated with the socket
   * channel.
   *
   * @return The lowest level channel associated with a connection.
   */
  public ByteChannel getChannel() {
    // By default, return null, which indicates that there should
    // be no channel.  Subclasses should override this if
    // they want to support a channel.
    return null;
  }



  /**
   * Return the Socket channel associated with a connection.
   *
   * @return The Socket channel associated with a connection.
   */
  public SocketChannel getSocketChannel() {
    // By default, return null, which indicates that there should
    // be no socket channel.  Subclasses should override this if
    // they want to support a socket channel.
    return null;
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
  public boolean isMemberOf(Group<?> group, Operation operation)
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
  public Set<Group<?>> getGroups(Operation operation)
         throws DirectoryException
  {
    // FIXME -- This probably isn't the most efficient implementation.
    DN authzDN;
    if (operation == null)
    {
      if (authenticationInfo == null || !authenticationInfo.isAuthenticated())
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

    if (authzDN == null || authzDN.isRootDN())
    {
      return Collections.<Group<?>>emptySet();
    }

    Entry userEntry = DirectoryServer.getEntry(authzDN);
    if (userEntry == null)
    {
      return Collections.<Group<?>>emptySet();
    }

    HashSet<Group<?>> groupSet = new HashSet<>();
    for (Group<?> g : DirectoryServer.getGroupManager().getGroupInstances())
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
   * manager provider is available, but subclasses should override
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
   * trust manager provider is available, but subclasses should
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
   *          for operations requiring a server certificate, or
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
  @Override
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

  /**
   * Return the Security Strength Factor of a client connection.
   *
   * @return An integer representing the SSF value of a connection.
   */
  public abstract int getSSF();

  /**
   * Indicates a bind or start TLS request processing is finished
   * and the client connection may start processing data read from
   * the socket again. This must be called after processing each
   * bind request in a multistage SASL bind.
   */
  public void finishBind()
  {
    bindInProgress.set(false);
  }

  /**
   * Indicates a bind or start TLS request processing is finished
   * and the client connection may start processing data read from
   * the socket again. This must be called after processing each
   * bind request in a multistage SASL bind.
   */
  public void finishStartTLS()
  {
    startTLSInProgress.set(false);
  }

  /**
   * Indicates a multistage SASL bind operation is finished and the
   * client connection may accept additional LDAP messages.
   */
  public void finishSaslBind()
  {
    saslBindInProgress.set(false);
  }

  /**
   * Returns whether this connection is used for inner work not directly
   * requested by an external client.
   *
   * @return {@code true} if this is an inner connection, {@code false}
   *         otherwise
   */
  public boolean isInnerConnection()
  {
    return getConnectionID() < 0;
  }

  public class Transaction {
      final String transactionId=UUID.randomUUID().toString().toLowerCase();

      public Transaction() {
          transactions.put(getTransactionId(),this);
      }

      public String getTransactionId() {
          return transactionId;
      }

      final Queue<Operation> waiting=new LinkedList<>();
      public void add(Operation operation) {
          waiting.add(operation);
      }

      public Queue<Operation> getWaiting() {
          return waiting;
      }

      public void clear() {
          transactions.remove(getTransactionId());
      }
      final Deque<RollbackOperation> completed =new ArrayDeque<>();
      public void success(RollbackOperation operation) {
          completed.add(operation);
      }

      public Deque<RollbackOperation> getCompleted() {
          return completed;
      }
  }

  Map<String,Transaction> transactions=new ConcurrentHashMap<>();

  public Transaction startTransaction() {
      return new Transaction();
  }

  public Transaction getTransaction(String id) {
      return transactions.get(id);
  }
}
