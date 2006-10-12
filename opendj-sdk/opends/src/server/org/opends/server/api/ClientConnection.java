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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.api.plugin.IntermediateResponsePluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.Operation;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server client connection.
 */
public abstract class ClientConnection
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.ClientConnection";



  // The set of authentication information for this client connection.
  private AuthenticationInfo authenticationInfo;

  // Indicates whether a bind is currently in progress on this client
  // connection.  If so, then no other operations should be allowed
  // until the bind completes.
  private boolean bindInProgress;

  // The size limit for use with this client connection.
  private int sizeLimit;

  // The time limit for use with this client connection.
  private int timeLimit;

  // The lookthrough limit for use with this client connection.
  private int lookthroughLimit;

  // The opaque information used for storing intermediate state
  // information needed across multi-stage SASL binds.
  private Object saslAuthState;

  // A set of persistent searches registered for this client.
  private CopyOnWriteArrayList<PersistentSearch> persistentSearches;



  /**
   * Performs the appropriate initialization generic to all client
   * connections.
   */
  protected ClientConnection()
  {
    assert debugConstructor(CLASS_NAME);

    authenticationInfo = new AuthenticationInfo();
    saslAuthState      = null;
    bindInProgress     = false;
    persistentSearches = new CopyOnWriteArrayList<PersistentSearch>();
    sizeLimit          = DirectoryServer.getSizeLimit();
    timeLimit          = DirectoryServer.getTimeLimit();
    lookthroughLimit   = DirectoryServer.getLookthroughLimit();
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
   * Retrieves a string representation of the address on the server to
   * which the client connected.
   *
   * @return  A string representation of the address on the server to
   *          which the client connected.
   */
  public abstract String getServerAddress();



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> associated with
   * the remote client system.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> associated with
   *          the remote client system.  It may be <CODE>null</CODE>
   *          if the client is not connected over an IP-based
   *          connection.
   */
  public abstract InetAddress getRemoteAddress();



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> for the Directory
   * Server system to which the client has established the connection.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> for the Directory
   *          Server system to which the client has established the
   *          connection.  It may be <CODE>null</CODE> if the client
   *          is not connected over an IP-based connection.
   */
  public abstract InetAddress getLocalAddress();



  /**
   * Indicates whether this client connection is currently using a
   * secure mechanism to communicate with the server.  Note that this
   * may change over time based on operations performed by the client
   * or server (e.g., it may go from <CODE>false</CODE> to
   * <CODE>true</CODE> if the client uses the StartTLS extended
   * operation).
   *
   * @return  <CODE>true</CODE> if the client connection is currently
   *          using a secure mechanism to communicate with the server,
   *          or <CODE>false</CODE> if not.
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
   *          <CODE>null</CODE> if no security is in place.
   */
  public abstract String getSecurityMechanism();



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
   * @return  <CODE>true</CODE> if all the data in the provided buffer
   *          was processed and the client connection can remain
   *          established, or <CODE>false</CODE> if a decoding error
   *          occurred and requests from this client should no longer
   *          be processed.  Note that if this method does return
   *          <CODE>false</CODE>, then it must have already
   *          disconnected the client.
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
   */
  public abstract void sendSearchEntry(
                            SearchOperation searchOperation,
                            SearchResultEntry searchEntry);



  /**
   * Sends the provided search result reference to the client.
   *
   * @param  searchOperation  The search operation with which the
   *                          reference is associated.
   * @param  searchReference  The search result reference to be sent
   *                          to the client.
   *
   * @return  <CODE>true</CODE> if the client is able to accept
   *          referrals, or <CODE>false</CODE> if the client cannot
   *          handle referrals and no more attempts should be made to
   *          send them for the associated search operation.
   */
  public abstract boolean sendSearchReference(
                               SearchOperation searchOperation,
                               SearchResultReference searchReference);



  /**
   * Invokes the intermediate response plugins on the provided
   * response message and sends it to the client.
   *
   * @param  intermediateResponse  The intermediate response message
   *                               to be sent.
   *
   * @return  <CODE>true</CODE> if processing on the associated
   *          operation should continue, or <CODE>false</CODE> if not.
   */
  public final boolean sendIntermediateResponse(
                            IntermediateResponse intermediateResponse)
  {
    assert debugEnter(CLASS_NAME, "sendIntermediateResponse",
                      String.valueOf(intermediateResponse));


    // Invoke the intermediate response plugins for the response
    // message.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    IntermediateResponsePluginResult pluginResult =
         pluginConfigManager.invokeIntermediateResponsePlugins(
                                  intermediateResponse);

    boolean continueProcessing = true;
    if (pluginResult.sendIntermediateResponse())
    {
      continueProcessing =
           sendIntermediateResponseMessage(intermediateResponse);
    }

    return (continueProcessing && pluginResult.continueOperation());
  }




  /**
   * Sends the provided intermediate response message to the client.
   *
   * @param  intermediateResponse  The intermediate response message
   *                               to be sent.
   *
   * @return  <CODE>true</CODE> if processing on the associated
   *          operation should continue, or <CODE>false</CODE> if not.
   */
  protected abstract boolean
       sendIntermediateResponseMessage(
            IntermediateResponse intermediateResponse);



  /**
   * Closes the connection to the client, optionally sending it a
   * message indicating the reason for the closure.  Note that the
   * ability to send a notice of disconnection may not be available
   * for all protocols or under all circumstances.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification to the client that the
   *                           connection will be closed.
   * @param  messageID         The unique identifier associated with
   *                           the message to send to the client.  It
   *                           may be -1 if no notification is to be
   *                           sent.
   * @param  arguments         An optional set of arguments that may
   *                           be used to customize the format string
   *                           associated with the provided message
   *                           ID.
   */
  public void disconnect(DisconnectReason disconnectReason,
                         boolean sendNotification, int messageID,
                         Object... arguments)
  {
    assert debugEnter(CLASS_NAME, "disconnect",
                      String.valueOf(disconnectReason),
                      String.valueOf(sendNotification),
                      String.valueOf(messageID),
                      String.valueOf(arguments));

    String message = getMessage(messageID, arguments);
    disconnect(disconnectReason, sendNotification, message,
               messageID);
  }



  /**
   * Closes the connection to the client, optionally sending it a
   * message indicating the reason for the closure.  Note that the
   * ability to send a notice of disconnection may not be available
   * for all protocols or under all circumstances.  Also note that
   * when attempting to disconnect a client connection as a part of
   * operation processing (e.g., within a plugin or other extension),
   * the <CODE>disconnectClient</CODE> method within that operation
   * should be called rather than invoking this method directly.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification to the client that the
   *                           connection will be closed.
   * @param  message           The message to send to the client.  It
   *                           may be <CODE>null</CODE> if no
   *                           notification is to be sent.
   * @param  messageID         The unique identifier associated with
   *                           the message to send to the client.  It
   *                           may be -1 if no notification is to be
   *                           sent.
   */
  public abstract void disconnect(DisconnectReason disconnectReason,
                                  boolean sendNotification,
                                  String message, int messageID);



  /**
   * Indicates whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed
   * until the bind has completed.
   *
   * @return  <CODE>true</CODE> if a bind operation is in progress on
   *          this connection, or <CODE>false</CODE> if not.
   */
  public boolean bindInProgress()
  {
    assert debugEnter(CLASS_NAME, "bindInProgress");

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
    assert debugEnter(CLASS_NAME, "setBindInProgress",
                      String.valueOf(bindInProgress));

    this.bindInProgress = bindInProgress;
  }



  /**
   * Indicates whether the user associated with this client connection
   * must change their password before they will be allowed to do
   * anything else.
   *
   * @return  <CODE>true</CODE> if the user associated with this
   *          client connection must change their password before they
   *          will be allowed to do anything else, or
   *          <CODE>false</CODE> if not.
   */
  public boolean mustChangePassword()
  {
    assert debugEnter(CLASS_NAME, "mustChangePassword");

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
  public void setMustChangePassword(boolean mustChangePassword)
  {
    assert debugEnter(CLASS_NAME, "setMustChangePassword",
                      String.valueOf(mustChangePassword));

    if (authenticationInfo == null)
    {
      authenticationInfo = new AuthenticationInfo();
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
   * Retrieves the operation in progress with the specified message
   * ID.
   *
   * @param  messageID  The message ID of the operation to retrieve.
   *
   * @return  The operation in progress with the specified message ID,
   *          or <CODE>null</CODE> if no such operation could be
   *          found.
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
   *
   * @return  <CODE>true</CODE> if the operation was found and removed
   *          from the set of operations in progress, or
   *          <CODE>false</CODE> if not.
   */
  public abstract boolean removeOperationInProgress(int messageID);



  /**
   * Retrieves the set of persistent searches registered for this
   * client.
   *
   * @return  The set of persistent searches registered for this
   *          client.
   */
  public CopyOnWriteArrayList<PersistentSearch>
              getPersistentSearches()
  {
    assert debugEnter(CLASS_NAME, "getPersistentSearches");

    return persistentSearches;
  }



  /**
   * Registers the provided persistent search for this client.  Note
   * that this should only be called by
   * <CODE>DirectoryServer.registerPersistentSearch</CODE> and not
   * through any other means.
   *
   * @param  persistentSearch  The persistent search to register for
   *                           this client.
   */
  public void registerPersistentSearch(PersistentSearch
                                            persistentSearch)
  {
    assert debugEnter(CLASS_NAME, "registerPersistentSearch",
                      String.valueOf(persistentSearch));

    persistentSearches.add(persistentSearch);
  }



  /**
   * Deregisters the provided persistent search for this client.  Note
   * that this should only be called by
   * <CODE>DirectoryServer.deregisterPersistentSearch</CODE> and not
   * through any other means.
   *
   * @param  persistentSearch  The persistent search to deregister for
   *                           this client.
   */
  public void deregisterPersistentSearch(PersistentSearch
                                              persistentSearch)
  {
    assert debugEnter(CLASS_NAME, "deregisterPersistentSearch",
                      String.valueOf(persistentSearch));

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
    assert debugEnter(CLASS_NAME, "getAuthenticationInfo");

    return authenticationInfo;
  }



  /**
   * Specifies information about the authentication that has been
   * performed for this connection.
   *
   * @param  authenticationInfo  Information about the authentication
   *                             that has been performed for this
   *                             connection.  It should not be
   *                             <CODE>null</CODE>.
   */
  public void setAuthenticationInfo(AuthenticationInfo
                                         authenticationInfo)
  {
    assert debugEnter(CLASS_NAME, "setAuthenticationInfo",
                      String.valueOf(authenticationInfo));

    if (authenticationInfo == null)
    {
      this.authenticationInfo = new AuthenticationInfo();
    }
    else
    {
      this.authenticationInfo = authenticationInfo;
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
    assert debugEnter(CLASS_NAME, "setUnauthenticated");

    this.authenticationInfo = new AuthenticationInfo();
    this.sizeLimit          = DirectoryServer.getSizeLimit();
    this.timeLimit          = DirectoryServer.getTimeLimit();
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
    assert debugEnter(CLASS_NAME, "getSASLAuthStateInfo");

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
    assert debugEnter(CLASS_NAME, "setSASLAuthStateInfo",
                      String.valueOf(saslAuthState));

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
    assert debugEnter(CLASS_NAME, "getSizeLimit");

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
  public final void setSizeLimit(int sizeLimit)
  {
    assert debugEnter(CLASS_NAME, "setSizeLimit",
                      String.valueOf(sizeLimit));

    this.sizeLimit = sizeLimit;
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
    assert debugEnter(CLASS_NAME, "getLookthroughLimit");

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
  public final void setLookthroughLimit(int lookthroughLimit)
  {
    assert debugEnter(CLASS_NAME, "setLookthroughLimit",
      String.valueOf(lookthroughLimit));

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
    assert debugEnter(CLASS_NAME, "getTimeLimit");

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
  public final void setTimeLimit(int timeLimit)
  {
    assert debugEnter(CLASS_NAME, "setTimeLimit",
                      String.valueOf(timeLimit));

    this.timeLimit = timeLimit;
  }



  /**
   * Retrieves a string representation of this client connection.
   *
   * @return  A string representation of this client connection.
   */
  public final String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

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
}

