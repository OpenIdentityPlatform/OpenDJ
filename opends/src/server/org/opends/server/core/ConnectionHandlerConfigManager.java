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
package org.opends.server.core;
import org.opends.messages.Message;



import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;

import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.AdministrationConnector;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.*;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;


/**
 * This class defines a utility that will be used to manage the
 * configuration for the set of connection handlers defined in the
 * Directory Server. It will perform the necessary initialization of
 * those connection handlers when the server is first started, and
 * then will manage any changes to them while the server is running.
 */
public class ConnectionHandlerConfigManager implements
    ConfigurationAddListener<ConnectionHandlerCfg>,
    ConfigurationDeleteListener<ConnectionHandlerCfg>,
    ConfigurationChangeListener<ConnectionHandlerCfg> {
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  // The mapping between configuration entry DNs and their
  // corresponding connection handler implementations.
  private ConcurrentHashMap<DN, ConnectionHandler> connectionHandlers =
        new ConcurrentHashMap<DN, ConnectionHandler>();



  /**
   * Creates a new instance of this connection handler config manager.
   */
  public ConnectionHandlerConfigManager() {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      ConnectionHandlerCfg configuration) {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    // Register as a change listener for this connection handler entry
    // so that we will be notified of any changes that may be made to
    // it.
    configuration.addChangeListener(this);

    // Ignore this connection handler if it is disabled.
    if (configuration.isEnabled()) {
      // The connection handler needs to be enabled.
      DN dn = configuration.dn();
      try {
        // Attempt to start the connection handler.
        ConnectionHandler<? extends ConnectionHandlerCfg> connectionHandler =
          getConnectionHandler(configuration);
        connectionHandler.start();

        // Put this connection handler in the hash so that we will be
        // able to find it if it is altered.
        connectionHandlers.put(dn, connectionHandler);

        // Register the connection handler with the Directory Server.
        DirectoryServer.registerConnectionHandler(connectionHandler);
      } catch (ConfigException e) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      } catch (Exception e) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }


        messages.add(ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
                String.valueOf(configuration.getJavaClass()),
                String.valueOf(dn),
            stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }

    // Return the configuration result.
    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ConnectionHandlerCfg configuration) {
    // Attempt to get the existing connection handler. This will only
    // succeed if it was enabled.
    DN dn = configuration.dn();
    ConnectionHandler connectionHandler = connectionHandlers.get(dn);

    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    // See whether the connection handler should be enabled.
    if (connectionHandler == null) {
      if (configuration.isEnabled()) {
        // The connection handler needs to be enabled.
        try {
          // Attempt to start the connection handler.
          connectionHandler = getConnectionHandler(configuration);
          connectionHandler.start();

          // Put this connection handler in the hash so that we will
          // be able to find it if it is altered.
          connectionHandlers.put(dn, connectionHandler);

          // Register the connection handler with the Directory
          // Server.
          DirectoryServer.registerConnectionHandler(
               (ConnectionHandler<? extends ConnectionHandlerCfg>)
               connectionHandler);
        } catch (ConfigException e) {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          messages.add(e.getMessageObject());
          resultCode = DirectoryServer.getServerErrorResultCode();
        } catch (Exception e) {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          messages.add(ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
                  String.valueOf(configuration
              .getJavaClass()), String.valueOf(dn),
              stackTraceToSingleLineString(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    } else {
      if (configuration.isEnabled()) {
        // The connection handler is currently active, so we don't
        // need to do anything. Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = configuration.getJavaClass();
        if (!className.equals(connectionHandler.getClass().getName())) {
          adminActionRequired = true;
        }
      } else {
        // We need to disable the connection handler.
        DirectoryServer
            .deregisterConnectionHandler(connectionHandler);
        connectionHandlers.remove(dn);


        connectionHandler.finalizeConnectionHandler(
                INFO_CONNHANDLER_CLOSED_BY_DISABLE.get(), false);
      }
    }

    // Return the configuration result.
    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      ConnectionHandlerCfg configuration) {

    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    // See if the entry is registered as a connection handler. If so,
    // deregister and stop it. We'll try to leave any established
    // connections alone if possible.
    DN dn = configuration.dn();
    ConnectionHandler connectionHandler = connectionHandlers.get(dn);
    if (connectionHandler != null) {
      DirectoryServer.deregisterConnectionHandler(connectionHandler);
      connectionHandlers.remove(dn);

      connectionHandler.finalizeConnectionHandler(
              INFO_CONNHANDLER_CLOSED_BY_DELETE.get(),
              false);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }



  /**
   * Initializes the configuration associated with the Directory
   * Server connection handlers. This should only be called at
   * Directory Server startup.
   *
   * @throws ConfigException
   *           If a critical configuration problem prevents the
   *           connection handler initialization from succeeding.
   * @throws InitializationException
   *           If a problem occurs while initializing the connection
   *           handlers that is not related to the server
   *           configuration.
   */
  public void initializeConnectionHandlerConfig()
      throws ConfigException, InitializationException {
    connectionHandlers = new ConcurrentHashMap<DN, ConnectionHandler>();

    // Get the root configuration which acts as the parent of all
    // connection handlers.
    ServerManagementContext context = ServerManagementContext
        .getInstance();
    RootCfg root = context.getRootConfiguration();

    // Register as an add and delete listener so that we can
    // be notified if new connection handlers are added or existing
    // connection handlers are removed.
    root.addConnectionHandlerAddListener(this);
    root.addConnectionHandlerDeleteListener(this);

    // Initialize existing connection handles.
    for (String name : root.listConnectionHandlers()) {
      ConnectionHandlerCfg config = root
          .getConnectionHandler(name);

      // Register as a change listener for this connection handler
      // entry so that we will be notified of any changes that may be
      // made to it.
      config.addChangeListener(this);

      // Ignore this connection handler if it is disabled.
      if (config.isEnabled()) {
        // Note that we don't want to start the connection handler
        // because we're still in the startup process. Therefore, we
        // will not do so and allow the server to start it at the very
        // end of the initialization process.
        ConnectionHandler<? extends ConnectionHandlerCfg> connectionHandler =
             getConnectionHandler(config);

        // Put this connection handler in the hash so that we will be
        // able to find it if it is altered.
        connectionHandlers.put(config.dn(), connectionHandler);

        // Register the connection handler with the Directory Server.
        DirectoryServer.registerConnectionHandler(connectionHandler);
      }
    }
  }



  /**
   * Initializes the configuration associated with the Directory
   * Server administration connector. This should only be called at
   * Directory Server startup.
   *
   * @throws ConfigException
   *           If a critical configuration problem prevents the
   *           administration connector initialization from succeeding.
   * @throws InitializationException
   *           If a problem occurs while initializing the administration
   *           connector that is not related to the server
   *           configuration.
   */
  public void initializeAdministrationConnectorConfig()
    throws ConfigException, InitializationException {

    RootCfg root =
      ServerManagementContext.getInstance().getRootConfiguration();
    AdministrationConnectorCfg administrationConnectorCfg =
      root.getAdministrationConnector();

    AdministrationConnector ac = new AdministrationConnector();
    ac.initializeAdministrationConnector(administrationConnectorCfg);

    // Put this connection handler in the hash so that we will be
    // able to find it if it is altered.
    LDAPConnectionHandler connectionHandler = ac.getConnectionHandler();
    connectionHandlers.put(administrationConnectorCfg.dn(), connectionHandler);

    // Register the connection handler with the Directory Server.
    DirectoryServer.registerConnectionHandler(connectionHandler);
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      ConnectionHandlerCfg configuration,
      List<Message> unacceptableReasons) {
    if (configuration.isEnabled()) {
      // It's enabled so always validate the class.
      return isJavaClassAcceptable(configuration, unacceptableReasons);
    } else {
      // It's disabled so ignore it.
      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ConnectionHandlerCfg configuration,
      List<Message> unacceptableReasons) {
    if (configuration.isEnabled()) {
      // It's enabled so always validate the class.
      return isJavaClassAcceptable(configuration, unacceptableReasons);
    } else {
      // It's disabled so ignore it.
      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      ConnectionHandlerCfg configuration,
      List<Message> unacceptableReasons) {
    // A delete should always be acceptable, so just return true.
    return true;
  }



  // Load and initialize the connection handler named in the config.
  private ConnectionHandler<? extends ConnectionHandlerCfg>
               getConnectionHandler(ConnectionHandlerCfg config)
          throws ConfigException
  {
    String className = config.getJavaClass();
    ConnectionHandlerCfgDefn d =
      ConnectionHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d
        .getJavaClassPropertyDefinition();

    // Load the class and cast it to a connection handler.
    Class<? extends ConnectionHandler> theClass;
    ConnectionHandler connectionHandler;

    try {
      theClass = pd.loadClass(className, ConnectionHandler.class);
      connectionHandler = theClass.newInstance();
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.
          get(String.valueOf(className), String.valueOf(config.dn()),
              stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    // Perform the necessary initialization for the connection
    // handler.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("initializeConnectionHandler", config
          .configurationClass());

      method.invoke(connectionHandler, config);
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.
          get(String.valueOf(className), String.valueOf(config.dn()),
              stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    // The connection handler has been successfully initialized.
    return (ConnectionHandler<? extends ConnectionHandlerCfg>)
           connectionHandler;
  }



  // Determines whether or not the new configuration's implementation
  // class is acceptable.
  private boolean isJavaClassAcceptable(
      ConnectionHandlerCfg config,
      List<Message> unacceptableReasons) {
    String className = config.getJavaClass();
    ConnectionHandlerCfgDefn d =
      ConnectionHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d
        .getJavaClassPropertyDefinition();

    // Load the class and cast it to a connection handler.
    ConnectionHandler connectionHandler = null;
    Class<? extends ConnectionHandler> theClass;
    try {
      connectionHandler = connectionHandlers.get(config.dn());
      theClass = pd.loadClass(className, ConnectionHandler.class);
      if (connectionHandler == null) {
        connectionHandler = theClass.newInstance();
      }
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      unacceptableReasons.add(
              ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
                      String.valueOf(className),
                      String.valueOf(config.dn()),
                      stackTraceToSingleLineString(e)));
      return false;
    }

    // Perform the necessary initialization for the connection
    // handler.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("isConfigurationAcceptable",
                                         ConnectionHandlerCfg.class,
                                         List.class);
      Boolean acceptable = (Boolean) method.invoke(connectionHandler, config,
                                                   unacceptableReasons);

      if (! acceptable)
      {
        return false;
      }
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      unacceptableReasons.add(ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
              String.valueOf(className), String.valueOf(config.dn()),
              stackTraceToSingleLineString(e)));
      return false;
    }

    // The class is valid as far as we can tell.
    return true;
  }
}
