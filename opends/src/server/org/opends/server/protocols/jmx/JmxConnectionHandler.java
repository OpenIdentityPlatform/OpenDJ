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
package org.opends.server.protocols.jmx;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

/**
 * This class defines a connection handler that will be used for
 * communicating with administrative clients over JMX. The connection
 * handler is responsible for accepting new connections, reading requests
 * from the clients and parsing them as operations. A single request
 * handler should be used.
 */
public class JmxConnectionHandler
    extends ConnectionHandler implements ConfigurableComponent, AlertGenerator
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
    "org.opends.server.protocols.jmx.JMXConnectionHandler";

  /**
   * The DN of the configuration entry for this connection handler.
   */
  private DN configEntryDN;

  /**
   * Indicates whether this connection handler is enabled.
   */
  protected boolean enabled;

  /**
   * The attribute which  whether this connection handler is enabled.
   */
  BooleanConfigAttribute enabledAtt;

  /**
   * Indicates whether to use SSL to communicate with the clients.
   */
  protected boolean useSSL;

  /**
   * The attribute which indicates whether to use SSL to communicate with
   * the clients.
   */
  BooleanConfigAttribute useSslAtt;

  /**
   * The nickname of the SSL certificate that should be used if SSL is
   * enabled.
   */
  protected String sslServerCertNickname;

  /**
   * The attribute which represents the nickname of the SSL certificate
   * that should be used if SSL is enabled.
   */
  StringConfigAttribute sslServerCertNickNameAtt;

  /**
   * The unique name assigned to this connection handler.
   */
  private String handlerName;

  /**
   * The JMX RMI Connector associated with the Connection handler.
   */
  protected RmiConnector rmiConnector;

  /**
   * The port on which this connection handler should listen for
   * requests.
   */
  protected int listenPort;

  /**
   * The attibute which represents the port on which this connection
   * handler should listen for requests.
   */
  private IntegerConfigAttribute listenPortAtt;

  /**
   * The DN of the key manager provider to use with this connection handler.
   */
  protected DN keyManagerProviderDN;

  /**
   * The key manager provider for this connection handler.
   */
  protected KeyManagerProvider keyManagerProvider;

  /**
   * The attribute which represents the DN of the key manager provider for this
   * connection handler.
   */
  private DNConfigAttribute keyManagerDNAtt;

  /**
   * Key that may be placed into a JMX connection environment map to
   * provide a custom <code>javax.net.ssl.TrustManager</code> array for
   * a connection.
   */
  public static final String TRUST_MANAGER_ARRAY_KEY =
    "org.opends.server.protocol.jmx.ssl.trust.manager.array";

  /**
   * Configuration attributes that are associated
   * with this configurable component.
   *
   */
  private LinkedList<ConfigAttribute> configAttrs =
    new LinkedList<ConfigAttribute>();

  /**
   * The unique name for this connection handler.
   */
  protected String connectionHandlerName;

  /**
   * The protocol used to communicate with clients.
   */
  protected String protocol;

  /**
   * The set of listeners for this connection handler.
   */
  protected LinkedList<HostPort> listeners = new LinkedList<HostPort>();

  /**
   * The list of active client connection.
   */
  protected LinkedList<ClientConnection> connectionList =
    new LinkedList<ClientConnection>();

  /**
   * Creates a new instance of this JMX connection handler. It must be
   * initialized before it may be used.
   */
  public JmxConnectionHandler()
  {
    super("JMX Connection Handler Thread");


    // No real implementation is required. Do all the work in the
    // initializeConnectionHandler method.
  }

  /**
   * Initializes this connection handler based on the information in the
   * provided configuration entry.
   *
   * @param configEntry
   *            The configuration entry that contains the information to
   *            use to initialize this connection handler.
   * @throws ConfigException
   *             If there is a problem with the configuration for this
   *             connection handler.
   * @throws InitializationException
   *             If a problem occurs while attempting to initialize this
   *             connection handler.
   */
  public void initializeConnectionHandler(ConfigEntry configEntry)
      throws ConfigException, InitializationException
  {
    //
    // If the initializeConnectionHandler method is called,
    // it means that the "enabled" attribure was true.
    enabledAtt = getEnabledAtt(configEntry);
    configAttrs.add(enabledAtt);
    enabled = enabledAtt.activeValue();

    //
    // Set the entry DN
    configEntryDN = configEntry.getDN();

    //
    // Determine the port on which to listen. There should be a single
    // port specified.
    listenPortAtt = getListenPort(configEntry);
    configAttrs.add(listenPortAtt);
    listenPort = listenPortAtt.activeIntValue();

    //
    // Determine whether to use SSL.
    useSslAtt = getUseSSL(configEntry);
    configAttrs.add(useSslAtt);
    useSSL = useSslAtt.activeValue();

    //
    // Determine which SSL certificate to use.
    sslServerCertNickNameAtt = getServerCertNickname(configEntry);
    configAttrs.add(sslServerCertNickNameAtt);
    sslServerCertNickname = sslServerCertNickNameAtt.activeValue();

    //
    // Determine which key manager provider to use.
    keyManagerDNAtt = getKeyManagerDN(configEntry);
    configAttrs.add(keyManagerDNAtt);
    if (keyManagerDNAtt == null)
    {
      keyManagerProviderDN = null;
    }
    else
    {
      keyManagerProviderDN = keyManagerDNAtt.activeValue();
      keyManagerProvider =
           DirectoryServer.getKeyManagerProvider(keyManagerProviderDN);
    }

    // Create the associated RMI Connector
    rmiConnector = new RmiConnector(DirectoryServer.getJMXMBeanServer(), this);

    //
    // Register this JMX ConnectionHandler as an MBean
    DirectoryServer.registerConfigurableComponent(this);

    //
    // Check if we have a correct SSL configuration
    if ((useSSL && keyManagerProvider == null))
    {

      //
      // TODO : give a more useful feedback message
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_WARNING,
          MSGID_CONFIG_KEYMANAGER_NO_ENABLED_ATTR);
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_USE_SSL;
      String message = getMessage(msgID, String.valueOf(configEntryDN), "");
      throw new InitializationException(msgID, message);
    }

    if (useSSL)
    {
      protocol = "JMX+SSL";
    }
    else
    {
      protocol = "JMX";
    }

    listeners.clear();
    listeners.add(new HostPort("0.0.0.0", listenPort));
    connectionHandlerName = "JMX Connection Handler "+ listenPort;
  }

  /**
   * Closes this connection handler so that it will no longer accept new
   * client connections. It may or may not disconnect existing client
   * connections based on the provided flag.
   *
   * @param finalizeReason
   *            The reason that this connection handler should be
   *            finalized.
   * @param closeConnections
   *            Indicates whether any established client connections
   *            associated with the connection handler should also be
   *            closed.
   */
  public void finalizeConnectionHandler(
      String finalizeReason, boolean closeConnections)
  {
    // We should also close the RMI registry.
    rmiConnector.finalizeConnectionHandler(closeConnections, true);
  }

  /**
   * {@inheritDoc}
   */
  public String getConnectionHandlerName()
  {
    return connectionHandlerName;
  }

  /**
   * {@inheritDoc}
   */
  public String getProtocol()
  {
    return protocol;
  }

  /**
   * {@inheritDoc}
   */
  public Collection<HostPort> getListeners()
  {
    return listeners;
  }

  /**
   * Retrieves the set of active client connections that have been
   * established through this connection handler.
   *
   * @return The set of active client connections that have been
   *         established through this connection handler.
   */
  public Collection<ClientConnection> getClientConnections()
  {
    return connectionList;
  }

  /**
   * Start the JMX RMI Connector.
   */
  public void run()
  {
    rmiConnector.initialize();
  }

  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return The human-readable name for this shutdown listener.
   */
  public String getShutdownListenerName()
  {
    return handlerName;
  }

  /**
   * Indicates that the Directory Server has received a request to stop
   * running and that this shutdown listener should take any action
   * necessary to prepare for it.
   *
   * @param reason
   *            The human-readable reason for the shutdown.
   */
  public void processServerShutdown(String reason)
  {
    //  We should also close the RMI registry.
    rmiConnector.finalizeConnectionHandler(true, true);

  }

  /**
   * Retrieves the DN of the configuration entry with which this
   * component is associated.
   *
   * @return The DN of the configuration entry with which this component
   *         is associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configEntryDN;
  }

  /**
   * Retrieves the set of configuration attributes that are associated
   * with this configurable component.
   *
   * @return The set of configuration attributes that are associated with
   *         this configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    return configAttrs;

  }

  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component. If it does not, then detailed
   * information about the problem(s) should be added to the provided
   * list.
   *
   * @param configEntry
   *            The configuration entry for which to make the
   *            determination.
   * @param unacceptableReasons
   *            A list that can be used to hold messages about why the
   *            provided entry does not have an acceptable configuration.
   * @return <CODE>true</CODE> if the provided entry has an acceptable
   *         configuration for this component, or <CODE>false</CODE> if
   *         not.
   */
  public boolean hasAcceptableConfiguration(
      ConfigEntry configEntry, List<String> unacceptableReasons)
  {
    boolean configValid = true;

    //
    // Determine the port on which to listen.
    try
    {
      getListenPort(configEntry);
    }
    catch (Exception e)
    {
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT;
      unacceptableReasons.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      configValid = false;
    }

    //
    // Determine the DN of the key manager provider.
    DN newKeyManagerProviderDN = null;
    KeyManagerProvider newKeyManagerProvider = null;
    try
    {
      DNConfigAttribute attr = getKeyManagerDN(configEntry);
      if (attr == null)
      {
        newKeyManagerProviderDN = null;
      }
      else
      {
        newKeyManagerProviderDN = attr.pendingValue();
        newKeyManagerProvider   =
             DirectoryServer.getKeyManagerProvider(newKeyManagerProviderDN);
      }
    }
    catch (Exception e)
    {
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_KEYMANAGER_DN;
      unacceptableReasons.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      configValid = false;
    }

    //
    // Determine whether to use SSL.
    try
    {
      boolean newUseSSL = getUseSSL(configEntry).activeValue();
      if (newUseSSL && (newKeyManagerProvider == null))
      {
        //
        // TODO Set an appropriate message (instead of null)
        int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_USE_SSL;
        unacceptableReasons.add(getMessage(msgID, String
            .valueOf(configEntryDN), null));
        configValid = false;
      }
    }
    catch (Exception e)
    {
      int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_USE_SSL;
      unacceptableReasons.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      configValid = false;
    }

    //
    // Determine which SSL certificate to use.
    try
    {
      getServerCertNickname(configEntry);
    }
    catch (Exception e)
    {
      configValid = false;
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
      unacceptableReasons.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
    }

    //
    // return part
    return configValid;
  }

  /**
   * Makes a best-effort attempt to apply the configuration contained in
   * the provided entry. Information about the result of this processing
   * should be added to the provided message list. Information should
   * always be added to this list if a configuration change could not be
   * applied. If detailed results are requested, then information about
   * the changes applied successfully (and optionally about parameters
   * that were not changed) should also be included.
   *
   * @param configEntry
   *            The entry containing the new configuration to apply for
   *            this component.
   * @param detailedResults
   *            Indicates whether detailed information about the
   *            processing should be added to the list.
   * @return Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(
      ConfigEntry configEntry, boolean detailedResults)
  {
    //
    // Create variables to include in the response.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean rmiConnectorRestart = false;
    ArrayList<String> messages = new ArrayList<String>();

    //
    // Determine the port on which to listen.
    int newListenPort = listenPort;
    try
    {
      if ((newListenPort = getListenPort(configEntry).activeIntValue())
          != listenPort)
      {
        rmiConnectorRestart = true;
      }
    }
    catch (Exception e)
    {
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT;
      messages.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    //
    // Determine whether to use SSL.
    boolean newUseSSL = useSSL;
    try
    {
      if ((newUseSSL = getUseSSL(configEntry).activeValue()) != useSSL)
      {
        rmiConnectorRestart = true;
      }
    }
    catch (Exception e)
    {
      int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_USE_SSL;
      messages.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    //
    // Determine which SSL certificate to use.
    String newSslServerCertNickname = sslServerCertNickname;
    try
    {
      if ((newSslServerCertNickname = getServerCertNickname(configEntry)
          .activeValue()) != sslServerCertNickname)
      {
        rmiConnectorRestart = true;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
      messages.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    //
    // Determine which key manager provider to use.
    DN newKeyManagerProviderDN = keyManagerProviderDN;
    KeyManagerProvider newKeyManagerProvider = keyManagerProvider;
    try
    {
      DNConfigAttribute attr = getKeyManagerDN(configEntry);
      if (attr == null)
      {
        newKeyManagerProviderDN = null;
        newKeyManagerProvider   = null;
        if (keyManagerProviderDN != null)
        {
          rmiConnectorRestart = true;
        }
      }
      else
      {
        newKeyManagerProviderDN = attr.pendingValue();
        newKeyManagerProvider =
             DirectoryServer.getKeyManagerProvider(newKeyManagerProviderDN);
        if (newUseSSL && (newKeyManagerProvider == null))
        {
          int msgID = MSGID_JMX_CONNHANDLER_INVALID_KEY_MANAGER_DN;
          messages.add(getMessage(
              msgID,
              String.valueOf(configEntryDN),
              String.valueOf(newKeyManagerProviderDN)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
        else
        {
          if (! newKeyManagerProviderDN.equals(keyManagerProviderDN))
          {
            rmiConnectorRestart = true;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_KEYMANAGER_DN;
      messages.add(getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    //
    // Apply new config, best effort mode
    if (rmiConnectorRestart)
    {
      applyNewConfiguration(
          newListenPort,
          newUseSSL,
          newSslServerCertNickname,
          newKeyManagerProviderDN,
          newKeyManagerProvider);
    }

    //
    // return part
    return new ConfigChangeResult(resultCode, false, messages);
  }

  /**
   * Apply the configuration.
   *
   * @param newListenPort
   *            the new listen port
   * @param newUseSSL
   *            Indicates if we should use ssl
   * @param newSslServerCertNickname
   *            Indicates the new server certificate nickname
   * @param newKeyManagerProviderDN
   *            The new key manager provider DN.
   * @param newKeyManagerProvider
   *            The new key manager provider instance.
   */
  private void applyNewConfiguration(
      int newListenPort, boolean newUseSSL, String newSslServerCertNickname,
      DN newKeyManagerProviderDN, KeyManagerProvider newKeyManagerProvider)
  {
    //
    // Stop the current connector
    // TODO Set Msg
    this.rmiConnector.finalizeConnectionHandler(true,
        (listenPort != newListenPort));

    //
    // set new params and update JMX attributes
    if (listenPort != newListenPort)
    {
      try
      {
        listenPortAtt.setValue(newListenPort);
        listenPort = newListenPort;
      }
      catch (Exception e)
      {
        // TODO
        // Print error message
      }
    }
    if (useSSL != newUseSSL)
    {
      useSSL = newUseSSL;
      useSslAtt.setValue(newUseSSL);
    }
    if (sslServerCertNickname != newSslServerCertNickname)
    {
      try
      {
        sslServerCertNickNameAtt.setValue(newSslServerCertNickname);
        sslServerCertNickname = newSslServerCertNickname;
      }
      catch (Exception e)
      {
        // TODO
        // Print error message
      }
    }

    if (keyManagerProviderDN == null)
    {
      if (newKeyManagerProviderDN != null)
      {
        try
        {
          keyManagerProviderDN = newKeyManagerProviderDN;
          keyManagerProvider   = newKeyManagerProvider;
          keyManagerDNAtt.setValue(newKeyManagerProviderDN);
        }
        catch (Exception e)
        {
          // TODO
          // Print error message
        }
      }
    }
    else if ((newKeyManagerProviderDN == null) ||
             (! newKeyManagerProviderDN.equals(keyManagerProviderDN)))
    {
      try
      {
        keyManagerProviderDN = newKeyManagerProviderDN;
        keyManagerProvider   = newKeyManagerProvider;
        keyManagerDNAtt.setValue(newKeyManagerProviderDN);
      }
      catch (Exception e)
      {
        // TODO
        // Print error message
      }
    }

    if (useSSL)
    {
      protocol = "JMX+SSL";
    }
    else
    {
      protocol = "JMX";
    }

    listeners.clear();
    listeners.add(new HostPort(listenPort));

    //
    // Start the new RMI Connector
    rmiConnector.initialize();
  }

  /**
   * Appends a string representation of this connection handler to the
   * provided buffer.
   *
   * @param buffer
   *            The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(handlerName);
  }

  /**
   * Retrieves the DN of the configuration entry with which this alert
   * generator is associated.
   *
   * @return The DN of the configuration entry with which this alert
   *         generator is associated.
   */
  public DN getComponentEntryDN()
  {
    return configEntryDN;
  }

  /**
   * Retrieves the fully-qualified name of the Java class for this alert
   * generator implementation.
   *
   * @return The fully-qualified name of the Java class for this alert
   *         generator implementation.
   */
  public String getClassName()
  {
    return CLASS_NAME;
  }

  /**
   * Retrieves information about the set of alerts that this generator
   * may produce. The map returned should be between the notification
   * type for a particular notification and the human-readable
   * description for that notification. This alert generator must not
   * generate any alerts with types that are not contained in this list.
   *
   * @return Information about the set of alerts that this generator may
   *         produce.
   */
  public LinkedHashMap<String, String> getAlerts()
  {
    LinkedHashMap<String, String> alerts = new LinkedHashMap<String, String>();

    return alerts;
  }

  /**
   * Retrieves the enabled attribure from the configuration entry with
   * which this component is associated.
   *
   * @param configEntry
   *        The configuration entry for which to make the determination.
   * @return The enabled attribute
   * @throws ConfigException
   *         If there is a problem with the configuration for this
   *         connection handler.
   * @throws InitializationException
   *         If a problem occurs while attempting to initialize this
   *         connection handler.
   */
  private BooleanConfigAttribute getEnabledAtt(ConfigEntry configEntry)
      throws InitializationException, ConfigException
  {
    int msgID = MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                                    getMessage(msgID), false);
    BooleanConfigAttribute attr = null;
    try
    {
      attr = (BooleanConfigAttribute) configEntry
          .getConfigAttribute(enabledStub);
      if (attr == null)
      {
        msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }

    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ce);
      }

      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR;
      String message = getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    return attr;
  }

  /**
   * Retrieves the listen port of the configuration entry with which this
   * component is associated.
   *
   * @param configEntry
   *            The configuration entry for which to make the
   *            determination.
   * @return The listen port
   *
   * @throws ConfigException
   *             If there is a problem with the configuration for this
   *             connection handler.
   * @throws InitializationException
   *             If a problem occurs while attempting to initialize this
   *             connection handler.
   */
  private IntegerConfigAttribute getListenPort(ConfigEntry configEntry)
      throws InitializationException, ConfigException
  {
    int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_LISTEN_PORT;
    IntegerConfigAttribute portStub = new IntegerConfigAttribute(
        ATTR_LISTEN_PORT, getMessage(msgID), true, false, false, true, 1,
        true, 65535);
    IntegerConfigAttribute portAttr = null;
    try
    {
      portAttr = (IntegerConfigAttribute) configEntry
          .getConfigAttribute(portStub);
      if (portAttr == null)
      {
        msgID = MSGID_JMX_CONNHANDLER_NO_LISTEN_PORT;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }

    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ce);
      }

      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT;
      String message = getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    return portAttr;
  }

  /**
   * Determine if the specified Configuration entry defines the
   * use-ssl attribute.
   * @param configEntry The entry to check
   * @return true if we should use SSL, else false
   * @throws InitializationException
   *      If a problem occurs while attempting to get the entry
   *      useSSL attribute
   */
  private BooleanConfigAttribute getUseSSL(ConfigEntry configEntry)
      throws InitializationException
  {
    //
    // Determine whether to use SSL.
    int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_USE_SSL;
    BooleanConfigAttribute useSSLStub = new BooleanConfigAttribute(
        ATTR_USE_SSL, getMessage(msgID), false);
    BooleanConfigAttribute useSSLAttr = null;
    try
    {
      useSSLAttr = (BooleanConfigAttribute) configEntry
          .getConfigAttribute(useSSLStub);
      if (useSSLAttr == null)
      {
        //
        // This is fine -- we'll just use the default value.
        useSSLAttr = new BooleanConfigAttribute(ATTR_USE_SSL,
            getMessage(msgID), false, DEFAULT_USE_SSL, DEFAULT_USE_SSL);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_USE_SSL;
      String message = getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
    return useSSLAttr;
  }

  /**
   * Determine if the specified Configuration entry defines the
   * server certificate nickname.
   * @param configEntry The entry to check
   * @return The server certificate nickname
   * @throws InitializationException
   *      If a problem occurs while attempting to get the entry
   *      certificate nickname
   */
  private StringConfigAttribute getServerCertNickname(ConfigEntry configEntry)
      throws InitializationException
  {
    int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME;
    StringConfigAttribute certNameStub = new StringConfigAttribute(
        ATTR_SSL_CERT_NICKNAME, getMessage(msgID), false, false, false);
    StringConfigAttribute certNameAttr = null;
    try
    {
      certNameAttr = (StringConfigAttribute) configEntry
          .getConfigAttribute(certNameStub);
      if (certNameAttr == null)
      {
        //
        // This is fine -- we'll just let the server pick one.
        certNameAttr = new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME,
            getMessage(msgID), false, false, false, (String) null);
      }
      return certNameAttr;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
      String message = getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }

  /**
   * Determine if the specified Configuration entry defines the
   * key manager provider DN.
   * @param configEntry The entry to check.
   * @return The key manager provider DN.
   * @throws InitializationException
   *      If a problem occurs while attempting to get the key manager
   *      provider DN.
   */
  private DNConfigAttribute getKeyManagerDN(ConfigEntry configEntry)
      throws InitializationException
  {
    int msgID = MSGID_JMX_CONNHANDLER_DESCRIPTION_KEYMANAGER_DN;
    DNConfigAttribute keyManagerStub = new DNConfigAttribute(
        ATTR_KEYMANAGER_DN, getMessage(msgID), false, false, false);
    DNConfigAttribute keyManagerAttr = null;
    try
    {
      keyManagerAttr = (DNConfigAttribute) configEntry
          .getConfigAttribute(keyManagerStub);
      return keyManagerAttr;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_KEYMANAGER_DN;
      String message = getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }
}
