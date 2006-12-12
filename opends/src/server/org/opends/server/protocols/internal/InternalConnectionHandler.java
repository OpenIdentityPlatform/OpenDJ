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
package org.opends.server.protocols.internal;



import java.util.Collection;
import java.util.LinkedList;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.HostPort;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a Directory Server connection handler that will
 * handle internal "connections".
 */
public class InternalConnectionHandler
       extends ConnectionHandler
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.internal." +
       "InternalConnectionHandler";



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

    assert debugConstructor(CLASS_NAME);

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
    assert debugEnter(CLASS_NAME, "getInstance");

    return handlerInstance;
  }



  /**
   * Initializes this connection handler based on the information in
   * the provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      connection handler.
   *
   * @throws  ConfigException  If there is a problem with the
   *                           configuration for this connection
   *                           handler.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   attempting to initialize this
   *                                   connection handler.
   */
  public void initializeConnectionHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeConnectionHandler",
                      String.valueOf(configEntry));
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
  public void finalizeConnectionHandler(String finalizeReason,
                                        boolean closeConnections)
  {
    assert debugEnter(CLASS_NAME, "initializeConnectionHandler",
                      String.valueOf(closeConnections));

    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  public String getConnectionHandlerName()
  {
    assert debugEnter(CLASS_NAME, "getConnectionHandlerName");

    return "Internal Connection Handler";
  }



  /**
   * {@inheritDoc}
   */
  public String getProtocol()
  {
    assert debugEnter(CLASS_NAME, "getProtocol");

    return protocol;
  }



  /**
   * {@inheritDoc}
   */
  public Collection<HostPort> getListeners()
  {
    assert debugEnter(CLASS_NAME, "getProtocol");

    return listeners;
  }



  /**
   * Retrieves the set of active client connections that have been
   * established through this connection handler.
   *
   * @return  The set of active client connections that have been
   *          established through this connection handler.
   */
  public Collection<ClientConnection> getClientConnections()
  {
    assert debugEnter(CLASS_NAME, "getClientConnections");

    return connectionList;
  }



  /**
   * Operates in a loop, accepting new connections and ensuring that
   * requests on those connections are handled properly.
   */
  public void run()
  {
    assert debugEnter(CLASS_NAME, "run");

    // No implementation is required since this connection handler
    // won't actually accept connections.
    return;
  }



  /**
   * Retrieves a string representation of this connection handler.
   *
   * @return  A string representation of this connection handler.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    return "Internal Connection Handler";
  }



  /**
   * Appends a string representation of this connection handler to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

    buffer.append("Internal Connection Handler");
  }
}

