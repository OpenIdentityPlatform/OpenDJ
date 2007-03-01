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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.Collection;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.monitors.ConnectionHandlerMonitor;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;




/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server connection handler.
 */
public abstract class ConnectionHandler
       extends DirectoryThread
{



  // The monitor associated with this connection handler.
  private ConnectionHandlerMonitor monitor;



  /**
   * Creates a new instance of this connection handler.  This must be
   * called by all connection handlers, and all connection handlers
   * must provide default constructors (i.e., those that do not take
   * any arguments) that invoke this constructor.
   *
   * @param  threadName  The name to use for this thread.
   */
  protected ConnectionHandler(String threadName)
  {
    super(threadName);

    monitor = null;
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
  public abstract void initializeConnectionHandler(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



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
  public abstract void finalizeConnectionHandler(
                            String finalizeReason,
                            boolean closeConnections);



  /**
   * Retrieves a name that may be used to refer to this connection
   * handler.  Every connection handler instance (even handlers of the
   * same type) must have a unique name.
   *
   * @return  A unique name that may be used to refer to this
   *          connection handler.
   */
  public abstract String getConnectionHandlerName();



  /**
   * Retrieves the name of the protocol used to communicate with
   * clients.  It should take into account any special naming that may
   * be needed to express any security mechanisms or other constraints
   * in place (e.g., "LDAPS" for LDAP over SSL).
   *
   * @return  The name of the protocol used to communicate with
   *          clients.
   */
  public abstract String getProtocol();



  /**
   * Retrieves information about the listener(s) that will be used to
   * accept client connections.
   *
   * @return  Information about the listener(s) that will be used to
   *          accept client connections, or an empty list if this
   *          connection handler does not accept connections from
   *          network clients.
   */
  public abstract Collection<HostPort> getListeners();



  /**
   * Retrieves the set of active client connections that have been
   * established through this connection handler.
   *
   * @return  The set of active client connections that have been
   *          established through this connection handler.
   */
  public abstract Collection<ClientConnection> getClientConnections();



  /**
   * Operates in a loop, accepting new connections and ensuring that
   * requests on those connections are handled properly.
   */
  public abstract void run();



  /**
   * Retrieves the monitor instance for this connection handler.
   *
   * @return  The monitor instance for this connection handler, or
   *          {@code null} if none has been provided.
   */
  public ConnectionHandlerMonitor getConnectionHandlerMonitor()
  {
    return monitor;
  }



  /**
   * Sets the monitor instance for this connection handler.
   *
   * @param  monitor  The monitor instance for this connection
   *                  handler.
   */
  public void setConnectionHandlerMonitor(
                   ConnectionHandlerMonitor monitor)
  {
    this.monitor = monitor;
  }



  /**
   * Retrieves a string representation of this connection handler.
   *
   * @return  A string representation of this connection handler.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this connection handler to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public abstract void toString(StringBuilder buffer);
}

