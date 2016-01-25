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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.internal;
import org.forgerock.i18n.LocalizableMessage;




import java.util.Collection;
import java.util.LinkedList;

import org.forgerock.opendj.server.config.server.*;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
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
  /** The singleton instance of this internal connection handler. */
  private static InternalConnectionHandler handlerInstance =
       new InternalConnectionHandler();

  /** The list of "connections" associated with this connection handler. */
  private LinkedList<ClientConnection> connectionList;

  /** The list of listeners associated with this connection handler. */
  private LinkedList<HostPort> listeners;

  /** The name of the protocol for this connection handler. */
  private String protocol;

  /** Configuration object of the connection handler. */
  private ConnectionHandlerCfg configuration;


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
    connectionList = new LinkedList<>();
    listeners      = new LinkedList<>();
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
   * @param  serverContext  The server context.
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
  @Override
  public void initializeConnectionHandler(ServerContext serverContext, ConnectionHandlerCfg configuration)
      throws ConfigException, InitializationException
  {
    this.configuration = configuration;
  }



  /** {@inheritDoc} */
  @Override
  public void finalizeConnectionHandler(LocalizableMessage finalizeReason)
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
  @Override
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
  @Override
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
  @Override
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
  @Override
  public Collection<ClientConnection> getClientConnections()
  {
    return connectionList;
  }



  /**
   * Operates in a loop, accepting new connections and ensuring that
   * requests on those connections are handled properly.
   */
  @Override
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
  @Override
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
  @Override
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

  /**
   * Return the configuration dn of the object.
   * @return DN of the entry.
   */
  @Override
  public DN getComponentEntryDN() {
      return this.configuration.dn();
  }

}

