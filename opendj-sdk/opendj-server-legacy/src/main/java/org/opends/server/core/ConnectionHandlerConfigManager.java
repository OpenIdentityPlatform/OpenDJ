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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.ConnectionHandlerCfgDefn;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * The mapping between configuration entry DNs and their corresponding
   * connection handler implementations.
   */
  private final Map<DN, ConnectionHandler<?>> connectionHandlers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this connection handler config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public ConnectionHandlerConfigManager(ServerContext serverContext) {
    this.serverContext = serverContext;
    connectionHandlers = new ConcurrentHashMap<>();
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(
      ConnectionHandlerCfg configuration) {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Register as a change listener for this connection handler entry
    // so that we will be notified of any changes that may be made to it.
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
        logger.traceException(e);

        ccr.addMessage(e.getMessageObject());
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      } catch (Exception e) {
        logger.traceException(e);
        ccr.addMessage(ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
            configuration.getJavaClass(), dn, stackTraceToSingleLineString(e)));
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
    }

    return ccr;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      ConnectionHandlerCfg configuration) {
    // Attempt to get the existing connection handler. This will only
    // succeed if it was enabled.
    DN dn = configuration.dn();
    ConnectionHandler<?> connectionHandler = connectionHandlers.get(dn);

    final ConfigChangeResult ccr = new ConfigChangeResult();

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
          DirectoryServer.registerConnectionHandler(connectionHandler);
        } catch (ConfigException e) {
          logger.traceException(e);

          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(e.getMessageObject());
        } catch (Exception e) {
          logger.traceException(e);

          ccr.addMessage(ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
              configuration.getJavaClass(), dn, stackTraceToSingleLineString(e)));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
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
          ccr.setAdminActionRequired(true);
        }
      } else {
        // We need to disable the connection handler.
        DirectoryServer
            .deregisterConnectionHandler(connectionHandler);
        connectionHandlers.remove(dn);


        connectionHandler.finalizeConnectionHandler(
                INFO_CONNHANDLER_CLOSED_BY_DISABLE.get());
      }
    }

    return ccr;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(
      ConnectionHandlerCfg configuration) {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // See if the entry is registered as a connection handler. If so,
    // deregister and stop it. We'll try to leave any established
    // connections alone if possible.
    DN dn = configuration.dn();
    ConnectionHandler<?> connectionHandler = connectionHandlers.get(dn);
    if (connectionHandler != null) {
      DirectoryServer.deregisterConnectionHandler(connectionHandler);
      connectionHandlers.remove(dn);

      connectionHandler.finalizeConnectionHandler(
              INFO_CONNHANDLER_CLOSED_BY_DELETE.get());
    }

    return ccr;
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
    // Clear the set of connection handlers in case of in-core restart.
    connectionHandlers.clear();

    // Initialize the admin connector.
    initializeAdministrationConnectorConfig();

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



  private void initializeAdministrationConnectorConfig()
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


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(
      ConnectionHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons) {
    return !configuration.isEnabled()
        || isJavaClassAcceptable(configuration, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      ConnectionHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons) {
    return !configuration.isEnabled()
        || isJavaClassAcceptable(configuration, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
      ConnectionHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons) {
    // A delete should always be acceptable, so just return true.
    return true;
  }



  /** Load and initialize the connection handler named in the config. */
  private <T extends ConnectionHandlerCfg> ConnectionHandler<T> getConnectionHandler(
      T config) throws ConfigException
  {
    String className = config.getJavaClass();
    ConnectionHandlerCfgDefn d = ConnectionHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    try {
      @SuppressWarnings("rawtypes")
      Class<? extends ConnectionHandler> theClass =
          pd.loadClass(className, ConnectionHandler.class);
      ConnectionHandler<T> connectionHandler = theClass.newInstance();

      connectionHandler.initializeConnectionHandler(config);

      return connectionHandler;
    } catch (Exception e) {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
          className, config.dn(), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }
  }



  /**
   * Determines whether or not the new configuration's implementation
   * class is acceptable.
   */
  private boolean isJavaClassAcceptable(
      ConnectionHandlerCfg config,
      List<LocalizableMessage> unacceptableReasons) {
    String className = config.getJavaClass();
    ConnectionHandlerCfgDefn d = ConnectionHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    try {
      ConnectionHandler<?> connectionHandler = connectionHandlers.get(config.dn());
      if (connectionHandler == null) {
        @SuppressWarnings("rawtypes")
        Class<? extends ConnectionHandler> theClass =
            pd.loadClass(className, ConnectionHandler.class);
        connectionHandler = theClass.newInstance();
      }

      return connectionHandler.isConfigurationAcceptable(config, unacceptableReasons);
    } catch (Exception e) {
      logger.traceException(e);

      unacceptableReasons.add(ERR_CONFIG_CONNHANDLER_CANNOT_INITIALIZE.get(
          className, config.dn(), stackTraceToSingleLineString(e)));
      return false;
    }
  }
}
