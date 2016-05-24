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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.types.HostPort.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.JMXConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a connection handler that will be used for
 * communicating with administrative clients over JMX. The connection
 * handler is responsible for accepting new connections, reading
 * requests from the clients and parsing them as operations. A single
 * request handler should be used.
 */
public final class JmxConnectionHandler extends
    ConnectionHandler<JMXConnectionHandlerCfg> implements
    ServerShutdownListener,
    ConfigurationChangeListener<JMXConnectionHandlerCfg> {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * Key that may be placed into a JMX connection environment map to
   * provide a custom {@code javax.net.ssl.TrustManager} array
   * for a connection.
   */
  public static final String TRUST_MANAGER_ARRAY_KEY =
    "org.opends.server.protocol.jmx.ssl.trust.manager.array";

  /** The list of active client connection. */
  private final List<ClientConnection> connectionList;

  /** The current configuration state. */
  private JMXConnectionHandlerCfg currentConfig;

  /** The JMX RMI Connector associated with the Connection handler. */
  private RmiConnector rmiConnector;

  /** The unique name for this connection handler. */
  private String connectionHandlerName;

  /** The protocol used to communicate with clients. */
  private String protocol;

  /** The set of listeners for this connection handler. */
  private final List<HostPort> listeners = new LinkedList<>();

  /**
   * Creates a new instance of this JMX connection handler. It must be
   * initialized before it may be used.
   */
  public JmxConnectionHandler() {
    super("JMX Connection Handler Thread");

    this.connectionList = new CopyOnWriteArrayList<>();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      JMXConnectionHandlerCfg config) {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Determine whether the RMI connection needs restarting.
    boolean rmiConnectorRestart = false;
    boolean portChanged = false;

    if (currentConfig.getListenPort() != config.getListenPort()) {
      rmiConnectorRestart = true;
      portChanged = true;
    }

    if (currentConfig.getRmiPort() != config.getRmiPort())
    {
      rmiConnectorRestart = true;
    }
    if (currentConfig.isUseSSL() != config.isUseSSL()) {
      rmiConnectorRestart = true;
    }

    if (notEqualsNotNull(config.getSSLCertNickname(), currentConfig.getSSLCertNickname())
        || notEqualsNotNull(config.getSSLCertNickname(), currentConfig.getSSLCertNickname())) {
      rmiConnectorRestart = true;
    }

    // Save the configuration.
    currentConfig = config;

    // Restart the connector if required.
    if (rmiConnectorRestart) {
      if (config.isUseSSL()) {
        protocol = "JMX+SSL";
      } else {
        protocol = "JMX";
      }

      listeners.clear();
      listeners.add(HostPort.allAddresses(config.getListenPort()));

      rmiConnector.finalizeConnectionHandler(portChanged);
      try
      {
        rmiConnector.initialize();
      }
      catch (RuntimeException e)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(LocalizableMessage.raw(e.getMessage()));
      }
    }

    // If the port number has changed then update the JMX port information
    // stored in the system properties.
    if (portChanged)
    {
      String key = protocol + "_port";
      String value = String.valueOf(config.getListenPort());
      System.clearProperty(key);
      System.setProperty(key, value);
    }

    return ccr;
  }


  private <T> boolean notEqualsNotNull(T o1, T o2)
  {
    return o1 != null && !o1.equals(o2);
  }

  @Override
  public void finalizeConnectionHandler(LocalizableMessage finalizeReason) {
    // Make sure that we don't get notified of any more changes.
    currentConfig.removeJMXChangeListener(this);

    // We should also close the RMI registry.
    rmiConnector.finalizeConnectionHandler(true);
  }


  /**
   * Retrieves the set of active client connections that have been
   * established through this connection handler.
   *
   * @return The set of active client connections that have been
   *         established through this connection handler.
   */
  @Override
  public Collection<ClientConnection> getClientConnections() {
    return connectionList;
  }



  /**
   * Retrieves the DN of the configuration entry with which this alert
   * generator is associated.
   *
   * @return The DN of the configuration entry with which this alert
   *         generator is associated.
   */
  @Override
  public DN getComponentEntryDN() {
    return currentConfig.dn();
  }



  /**
   * Retrieves the DN of the key manager provider that should be used
   * for operations associated with this connection handler which need
   * access to a key manager.
   *
   * @return The DN of the key manager provider that should be used
   *         for operations associated with this connection handler
   *         which need access to a key manager, or {@code null} if no
   *         key manager provider has been configured for this
   *         connection handler.
   */
  public DN getKeyManagerProviderDN() {
    return currentConfig.getKeyManagerProviderDN();
  }


  /**
   * Get the JMX connection handler's listen address.
   *
   * @return Returns the JMX connection handler's listen address.
   */
  public InetAddress getListenAddress()
  {
    return currentConfig.getListenAddress();
  }

  /**
   * Get the JMX connection handler's listen port.
   *
   * @return Returns the JMX connection handler's listen port.
   */
  public int getListenPort() {
    return currentConfig.getListenPort();
  }

  /**
   * Get the JMX connection handler's rmi port.
   *
   * @return Returns the JMX connection handler's rmi port.
   */
  public int getRmiPort() {
    return currentConfig.getRmiPort();
  }


  /**
   * Get the JMX connection handler's RMI connector.
   *
   * @return Returns the JMX connection handler's RMI connector.
   */
  public RmiConnector getRMIConnector() {
    return rmiConnector;
  }

  @Override
  public String getShutdownListenerName() {
    return connectionHandlerName;
  }



  /**
   * Retrieves the nicknames of the server certificates that should be
   * used in conjunction with this JMX connection handler.
   *
   * @return The nicknames of the server certificates that should be
   *         used in conjunction with this JMX connection handler.
   */
  public SortedSet<String> getSSLServerCertNicknames() {
    return currentConfig.getSSLCertNickname();
  }

  @Override
  public void initializeConnectionHandler(ServerContext serverContext, JMXConnectionHandlerCfg config)
         throws ConfigException, InitializationException
  {
    // Configuration is ok.
    currentConfig = config;

    final List<LocalizableMessage> reasons = new LinkedList<>();
    if (!isPortConfigurationAcceptable(String.valueOf(config.dn()),
        config.getListenPort(), reasons))
    {
      LocalizableMessage message = reasons.get(0);
      logger.error(message);
      throw new InitializationException(message);
    }

    if (config.isUseSSL()) {
      protocol = "JMX+SSL";
    } else {
      protocol = "JMX";
    }

    listeners.clear();
    listeners.add(HostPort.allAddresses(config.getListenPort()));
    connectionHandlerName = "JMX Connection Handler " + config.getListenPort();

    // Create a system property to store the JMX port the server is
    // listening to. This information can be displayed with jinfo.
    System.setProperty(
      protocol + "_port", String.valueOf(config.getListenPort()));

    // Create the associated RMI Connector.
    rmiConnector = new RmiConnector(DirectoryServer.getJMXMBeanServer(), this);

    // Register this as a change listener.
    config.addJMXChangeListener(this);
  }

  @Override
  public String getConnectionHandlerName() {
    return connectionHandlerName;
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  @Override
  public Collection<HostPort> getListeners() {
    return listeners;
  }

  @Override
  public boolean isConfigurationAcceptable(ConnectionHandlerCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    JMXConnectionHandlerCfg config = (JMXConnectionHandlerCfg) configuration;

    if ((currentConfig == null ||
        (!currentConfig.isEnabled() && config.isEnabled()) ||
        currentConfig.getListenPort() != config.getListenPort()) &&
        !isPortConfigurationAcceptable(String.valueOf(config.dn()),
          config.getListenPort(), unacceptableReasons))
    {
      return false;
    }

    if (config.getRmiPort() != 0 &&
        (currentConfig == null ||
        (!currentConfig.isEnabled() && config.isEnabled()) ||
        currentConfig.getRmiPort() != config.getRmiPort()) &&
        !isPortConfigurationAcceptable(String.valueOf(config.dn()),
          config.getRmiPort(), unacceptableReasons))
    {
      return false;
    }

    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  /**
   * Attempt to bind to the port to verify whether the connection
   * handler will be able to start.
   * @return true is the port is free to use, false otherwise.
   */
  private boolean isPortConfigurationAcceptable(String configDN,
                      int newPort, List<LocalizableMessage> unacceptableReasons) {
    try {
      if (StaticUtils.isAddressInUse(
          new InetSocketAddress(newPort).getAddress(), newPort, true)) {
        throw new IOException(ERR_CONNHANDLER_ADDRESS_INUSE.get().toString());
      }
      return true;
    } catch (Exception e) {
      LocalizableMessage message = ERR_CONNHANDLER_CANNOT_BIND.get("JMX", configDN,
              WILDCARD_ADDRESS, newPort, getExceptionMessage(e));
      unacceptableReasons.add(message);
      return false;
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      JMXConnectionHandlerCfg config,
      List<LocalizableMessage> unacceptableReasons) {
    // All validation is performed by the admin framework.
    return true;
  }



  /**
   * Determines whether clients are allowed to connect over JMX using SSL.
   *
   * @return Returns {@code true} if clients are allowed to
   *         connect over JMX using SSL.
   */
  public boolean isUseSSL() {
    return currentConfig.isUseSSL();
  }

  @Override
  public void processServerShutdown(LocalizableMessage reason) {
    // We should also close the RMI registry.
    rmiConnector.finalizeConnectionHandler(true);
  }



  /**
   * Registers a client connection with this JMX connection handler.
   *
   * @param connection
   *          The client connection.
   */
  public void registerClientConnection(ClientConnection connection) {
    connectionList.add(connection);
  }


  /**
   * Unregisters a client connection from this JMX connection handler.
   *
   * @param connection
   *          The client connection.
   */
  public void unregisterClientConnection(ClientConnection connection) {
    connectionList.remove(connection);
  }

  @Override
  public void run() {
    try
    {
      rmiConnector.initialize();
    }
    catch (RuntimeException ignore)
    {
      // Already caught and logged
    }
  }

  @Override
  public void toString(StringBuilder buffer) {
    buffer.append(connectionHandlerName);
  }
}
