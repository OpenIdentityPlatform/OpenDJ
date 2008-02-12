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
package org.opends.server.protocols.internal;
import org.opends.messages.Message;



import java.util.Collection;
import java.util.LinkedList;

import org.opends.server.admin.std.server.*;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.HostPort;



/**
 * This class defines a Directory Server connection handler that will
 * handle internal "connections".
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.PRIVATE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=false)
public final class InternalConnectionHandler
       extends ConnectionHandler
{
  // The singleton instance of this internal connection handler.
  private static InternalConnectionHandler handlerInstance =
       new InternalConnectionHandler();

  // The list of "connections" associated with this connection
  // handler.
  private LinkedList<ClientConnection> connectionList;

  // The list of listeners associated with this connection handler.
  private LinkedList<HostPort> listeners;

  // The name of the protocol for this connection handler.
  private String protocol;



  /**
   * Creates a new instance of this connection handler.  All
   * initialization should be done in the
   * <CODE>initializeConnectionHandler</CODE> method.
   */
  private InternalConnectionHandler()
  {
    super("Internal Connection Handler Thread");


    // Since we can't guarantee that the initializeConnectionHandler
    // method will always be called for this method, we'll do the
    // necessary "initialization" here.
    protocol       = "internal";
    connectionList = new LinkedList<ClientConnection>();
    listeners      = new LinkedList<HostPort>();
  }



  /**
   * Retrieves the static instance of this internal connection
   * handler.
   *
   * @return  The static instance of this internal connection handler.
   */
  public static InternalConnectionHandler getInstance()
  {
    return handlerInstance;
  }



  /**
   * Initializes this connection handler provider based on the
   * information in the provided connection handler configuration.
   *
   * @param  configuration  The connection handler configuration that
   *                        contains the information to use to
   *                        initialize this connection handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization as a result of the
   *                           server configuration.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public void initializeConnectionHandler(
                   ConnectionHandlerCfg configuration)
      throws ConfigException, InitializationException
  {
    // No implementation required.
  }



  /**
   * Closes this connection handler so that it will no longer accept
   * new client connections.  It may or may not disconnect existing
   * client connections based on the provided flag.  Note, however,
   * that some connection handler implementations may not have any way
   * to continue processing requests from existing connections, in
   * which case they should always be closed regardless of the value
   * of the <CODE>closeConnections</CODE> flag.
   *
   * @param  finalizeReason    The reason that this connection handler
   *                           should be finalized.
   * @param  closeConnections  Indicates whether any established
   *                           client connections associated with the
   *                           connection handler should also be
   *                           closed.
   */
  @Override()
  public void finalizeConnectionHandler(Message finalizeReason,
                                        boolean closeConnections)
  {
    // No implementation is required.
  }



  /**
   * Retrieves a name that may be used to refer to this connection
   * handler.  Every connection handler instance (even handlers of the
   * same type) must have a unique name.
   *
   * @return  A unique name that may be used to refer to this
   *          connection handler.
   */
  @Override()
  public String getConnectionHandlerName()
  {
    return "Internal Connection Handler";
  }



  /**
   * Retrieves the name of the protocol used to communicate with
   * clients.  It should take into account any special naming that may
   * be needed to express any security mechanisms or other constraints
   * in place (e.g., "LDAPS" for LDAP over SSL).
   *
   * @return  The name of the protocol used to communicate with
   *          clients.
   */
  @Override()
  public String getProtocol()
  {
    return protocol;
  }



  /**
   * Retrieves information about the listener(s) that will be used to
   * accept client connections.
   *
   * @return  Information about the listener(s) that will be used to
   *          accept client connections, or an empty list if this
   *          connection handler does not accept connections from
   *          network clients.
   */
  @Override()
  public Collection<HostPort> getListeners()
  {
    return listeners;
  }



  /**
   * Retrieves the set of active client connections that have been
   * established through this connection handler.
   *
   * @return  The set of active client connections that have been
   *          established through this connection handler.
   */
  @Override()
  public Collection<ClientConnection> getClientConnections()
  {
    return connectionList;
  }



  /**
   * Operates in a loop, accepting new connections and ensuring that
   * requests on those connections are handled properly.
   */
  @Override()
  public void run()
  {
    // No implementation is required since this connection handler
    // won't actually accept connections.
    return;
  }



  /**
   * Retrieves a string representation of this connection handler.
   *
   * @return  A string representation of this connection handler.
   */
  @Override()
  public String toString()
  {
    return "Internal Connection Handler";
  }



  /**
   * Appends a string representation of this connection handler to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  @Override()
  public void toString(StringBuilder buffer)
  {
    buffer.append("Internal Connection Handler");
  }

  /**
   * Called near the end of server shutdown.  This ensures that a new
   * InternalClientConnection is created if the server is immediately
   * restarted as part of an in-core restart.
   */
  public static void clearRootClientConnectionAtShutdown()
  {
    InternalClientConnection.clearRootClientConnectionAtShutdown();
  }

}

