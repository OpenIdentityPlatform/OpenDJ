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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 */
package org.opends.server.api;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.monitors.ConnectionHandlerMonitor;
import org.opends.server.types.DN;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;

/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server connection handler.
 *
 * @param <T>
 *          The type of connection handler configuration handled by
 *          this connection handler implementation.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class ConnectionHandler
       <T extends ConnectionHandlerCfg>
       extends DirectoryThread
{
  /** The monitor associated with this connection handler. */
  private ConnectionHandlerMonitor monitor;

  /** Is this handler the admin connection handler. */
  private boolean isAdminConnectionHandler = false;



  /**
   * Creates a new instance of this connection handler. This must be
   * called by all connection handlers, and all connection handlers
   * must provide default constructors (i.e., those that do not take
   * any arguments) that invoke this constructor.
   *
   * @param threadName
   *          The name to use for this thread.
   */
  protected ConnectionHandler(String threadName) {
    super(threadName);

    monitor = null;
  }



  /**
   * Closes this connection handler so that it will no longer accept
   * new client connections. Implementations should disconnect any
   * existing connections.
   *
   * @param finalizeReason
   *          The reason that this connection handler should be
   *          finalized.
   */
  public abstract void finalizeConnectionHandler(
      Message finalizeReason);



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
   * Retrieves an unmodifiable set of enabled SSL cipher suites configured for
   * this connection handler, if applicable. Implementations must return an
   * empty set if use of SSL/TLS is not possible.
   *
   * @return The set of enabled SSL cipher suites configured for this connection
   *         handler.
   */
  public Collection<String> getEnabledSSLCipherSuites()
  {
    return Collections.emptyList();
  }



  /**
   * Retrieves the set of enabled SSL protocols configured for this connection
   * handler. Implementations must return an empty set if use of SSL/TLS is not
   * possible.
   *
   * @return The set of enabled SSL protocols configured for this connection
   *         handler.
   */
  public Collection<String> getEnabledSSLProtocols()
  {
    return Collections.emptyList();
  }



   /**
   * Retrieves the DN of the configuration entry with which this alert
   * generator is associated.
   *
   * @return The DN of the configuration entry with which this alert
   *         generator is associated.
   */
  public abstract DN getComponentEntryDN();

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
   * @return The set of active client connections that have been
   *         established through this connection handler.
   */
  public abstract Collection<ClientConnection> getClientConnections();



  /**
   * Initializes this connection handler provider based on the
   * information in the provided connection handler configuration.
   *
   * @param configuration
   *          The connection handler configuration that contains the
   *          information to use to initialize this connection
   *          handler.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public abstract void initializeConnectionHandler(T configuration)
      throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this connection handler.  It should be possible to call this
   * method on an uninitialized connection handler instance in order
   * to determine whether the connection handler would be able to use
   * the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The connection handler configuration
   *                              for which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this connection handler, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      ConnectionHandlerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by connection handler
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Operates in a loop, accepting new connections and ensuring that
   * requests on those connections are handled properly.
   */
  @Override
  public abstract void run();



  /**
   * Retrieves the monitor instance for this connection handler.
   *
   * @return  The monitor instance for this connection handler, or
   *          {@code null} if none has been provided.
   */
  public final ConnectionHandlerMonitor getConnectionHandlerMonitor()
  {
    return monitor;
  }



  /**
   * Sets the monitor instance for this connection handler.
   *
   * @param  monitor  The monitor instance for this connection
   *                  handler.
   */
  public final void setConnectionHandlerMonitor(
                         ConnectionHandlerMonitor monitor)
  {
    this.monitor = monitor;
  }



  /**
   * Sets this connection handler as the admin connection handler.
   */
  public void setAdminConnectionHandler() {
    isAdminConnectionHandler = true;
  }


  /**
   * Returns whether this connection handler is the admin
   * connection handler.
   * @return boolean True if this connection handler is the admin
   *                 connection handler, false otherwise
   */
  public boolean isAdminConnectionHandler() {
    return isAdminConnectionHandler;
  }


  /**
   * Determine the number of request handlers.
   *
   * @param numRequestHandlers
   *          the number of request handlers from the configuration.
   * @param friendlyName
   *          the friendly name of this connection handler
   * @return the number of request handlers from the configuration determined
   *         from the configuration or from the number of available processors
   *         on the current machine
   */
  public int getNumRequestHandlers(Integer numRequestHandlers,
      String friendlyName)
  {
    if (numRequestHandlers == null)
    {
      // Automatically choose based on the number of processors.
      int cpus = Runtime.getRuntime().availableProcessors();
      int value = Math.max(2, cpus / 2);

      Message message =
          INFO_ERGONOMIC_SIZING_OF_REQUEST_HANDLER_THREADS.get(friendlyName,
              value);
      logError(message);

      return value;
    }
    else
    {
      return numRequestHandlers;
    }
  }

  /**
   * Retrieves a string representation of this connection handler.
   *
   * @return A string representation of this connection handler.
   */
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this connection handler to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  public abstract void toString(StringBuilder buffer);
}
