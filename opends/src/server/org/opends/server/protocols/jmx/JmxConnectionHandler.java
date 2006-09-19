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
package org.opends.server.protocols.jmx;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.debugConstructor;
import static org.opends.server.loggers.Debug.debugEnter;
import static org.opends.server.loggers.Debug.debugException;
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
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

/**
 * This class defines a connection handler that will be used for
 * communicating with administrative clients over JMX. The connection
 * handler is responsible for accepting new connections, reading requests
 * from the clients and parsing them as operations. A single request
 * handler should be used.
 */
public class JmxConnectionHandler
    extends ConnectionHandler implements ConfigurableComponent,
    ConfigChangeListener, ConfigDeleteListener, ConfigAddListener,
    AlertGenerator
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
    "org.opends.server.protocols.jmx.JMXConnectionHandler";

  /**
   * The DN of the configuration entry for this connection handler.
   */
  private DN configEntryDN;

  /**
   * The RDN of the key Manager, if exists.
   * TODO Should we move this 'static' definition into another file?
   */
  private final static String KeyManagerRDN = "cn=Key Manager Provider";

  /**
   * Indicates whether this connection handler is enabled.
   */
  protected boolean enabled;

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
   * The key manager to used for encryption.
   */
  protected KeyManagerProvider jmxKeyManager;

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

    assert debugConstructor(CLASS_NAME);

    // No real implementation is required. Do all the work in the
    // initializeConnectionHandler method.
  }

  /**
   * Indicates whether the configuration entry that will result from a
   * proposed add is acceptable to this add listener.
   * <br>
   * Up to now, only a keyManager could be added under the JMX
   * Connector.
   *
   * @param configEntry
   *            The configuration entry that will result from the
   *            requested add.
   * @param unacceptableReason
   *            A buffer to which this method can append a human-readable
   *            message explaining why the proposed entry is not
   *            acceptable.
   * @return <CODE>true</CODE> if the proposed entry contains an
   *         acceptable configuration, or <CODE>false</CODE> if it does
   *         not.
   */
  public boolean configAddIsAcceptable(
      ConfigEntry configEntry, StringBuilder unacceptableReason)
  {
    assert debugEnter(CLASS_NAME, "configAddIsAcceptable");

    //
    // First check if we already have a key manager. If yes, this means
    // that the enter is already here and cannot be added ...
    if (jmxKeyManager != null)
    {
      return false;
    }

    // Check if it's the correct DN:
    // - Only child "key manager" is registered
    // - We should have no more than one child under the JMX connection
    // handler ...
    DN JmxKeymanagerDN = null;
    try
    {
      JmxKeymanagerDN = DN.decode(KeyManagerRDN + ", " + this.configEntryDN);
    }
    catch (Exception e)
    {
      return false;
    }

    if (!(JmxKeymanagerDN.equals(configEntry.getDN())))
    {
      return false;
    }

    //
    // return part: all other cases are valid
    return true;
  }

  /**
   * Attempts to apply a new configuration based on the provided added
   * entry.
   *
   * @param configEntry
   *            The new configuration entry that contains the
   *            configuration to apply.
   * @return Information about the result of processing the configuration
   *         change.
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {
    assert debugEnter(CLASS_NAME, "applyConfigurationAdd");
    jmxKeyManager = getJmxKeyManager(configEntry);

    //
    // Ok, we have a key manager and if we have to use SSL, just do it.
    if (useSSL)
    {
      applyNewConfiguration(listenPort, useSSL, sslServerCertNickname);
    }
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * Indicates whether it is acceptable to remove the provided
   * configuration entry.
   *
   * @param configEntry
   *            The configuration entry that will be removed from the
   *            configuration.
   * @param unacceptableReason
   *            A buffer to which this method can append a human-readable
   *            message explaining why the proposed delete is not
   *            acceptable.
   * @return <CODE>true</CODE> if the proposed entry may be removed
   *         from the configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(
      ConfigEntry configEntry, StringBuilder unacceptableReason)
  {
    //
    // We can allow to remove the key manager only if we don't use it.
    if (useSSL)
    {
      return false;
    }
    else
    {
      return true;
    }
  }

  /**
   * Attempts to apply a new configuration based on the provided deleted
   * entry.
   *
   * @param configEntry
   *            The new configuration entry that has been deleted.
   * @return Information about the result of processing the configuration
   *         change.
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {
    //
    // Just set the key manager to null
    jmxKeyManager = null;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * Indicates whether the configuration entry that will result from a
   * proposed modification is acceptable to this change listener.
   *
   * @param configEntry
   *            The configuration entry that will result from the
   *            requested update.
   * @param unacceptableReason
   *            A buffer to which this method can append a human-readable
   *            message explaining why the proposed change is not
   *            acceptable.
   * @return <CODE>true</CODE> if the proposed entry contains an
   *         acceptable configuration, or <CODE>false</CODE> if it does
   *         not.
   */
  public boolean configChangeIsAcceptable(
      ConfigEntry configEntry, StringBuilder unacceptableReason)
  {
    //
    // We are checking first if we are dealing with a change
    // in the current entry.
    // Always return true as the check will be performed by the
    // hasAcceptableConfiguration call
    if (configEntry.getDN().compareTo(configEntryDN) == 0)
    {
      return true;
    }

    //
    // Then, we are checking that a change in the key manager
    // is acceptable.
    if (useSSL)
    {
      return false;
    }
    else
    {
      return true;
    }
  }

  /**
   * Attempts to apply a new configuration to this Directory Server
   * component based on the provided changed entry.
   *
   * @param configEntry
   *            The configuration entry that containing the updated
   *            configuration for this component.
   * @return Information about the result of processing the configuration
   *         change.
   */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry)
  {
    //
    // We are checking first if we are dealing with a change
    // in the current entry.
    if (configEntry.getDN().compareTo(configEntryDN) == 0)
    {
      ArrayList<String> messages = new ArrayList<String>();
      return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
    }

    //
    // Only child "key manager" are registered
    jmxKeyManager = getJmxKeyManager(configEntry);
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
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
    assert debugEnter(CLASS_NAME, "initializeConnectionHandler", String
        .valueOf(configEntry));

    //
    // This ConnectionHandler is always available.
    // TODO: Do we really want to always enable the JMX connector?
    enabled = true;

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
    // At this point, we have a configuration entry. Register a change
    // listener with it so we can be notified of changes to it over
    // time.
    // We will also want to register a delete and add listeners with
    // its parent.
    configEntry.registerDeleteListener(this);
    configEntry.registerChangeListener(this);
    configEntry.registerAddListener(this);

    //
    // Get the KeyManager, if specified.
    if (useSSL)
    {
      ConfigEntry keyManagerConfigEntry;
      try
      {
        DN KeyManagerDN = DN.decode(KeyManagerRDN + ", " + configEntryDN);
        keyManagerConfigEntry = DirectoryServer.getConfigEntry(KeyManagerDN);
        jmxKeyManager = getJmxKeyManager(keyManagerConfigEntry);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeKeyManagerProvider", e);

        logError(
            ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.SEVERE_ERROR,
            MSGID_CONFIG_KEYMANAGER_CANNOT_GET_CONFIG_ENTRY,
            stackTraceToSingleLineString(e));
        configEntry.registerAddListener(this);
        jmxKeyManager = null;
      }
    }
    else
    {
      jmxKeyManager = null;
    }

    // Create the associated RMI Connector
    rmiConnector = new RmiConnector(DirectoryServer.getJMXMBeanServer(), this);

    //
    // Register this JMX ConnectionHandler as an MBean
    DirectoryServer.registerConfigurableComponent(this);

    //
    // Check if we have a correct SSL configuration
    if ((useSSL && jmxKeyManager == null))
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
    assert debugEnter(CLASS_NAME, "finalizeConnectionHandler");
    rmiConnector.finalizeConnectionHandler(closeConnections);
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
    assert debugEnter(CLASS_NAME, "getClientConnections");
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
    assert debugEnter(CLASS_NAME, "getShutdownListenerName");

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
    assert debugEnter(CLASS_NAME, "processServerShutdown");
    rmiConnector.finalizeConnectionHandler(true);

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
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

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
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

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
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration", String
        .valueOf(configEntry), "java.util.List<String>");

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
    // Determine whether to use SSL.
    try
    {
      boolean newUseSSL = getUseSSL(configEntry).activeValue();
      if (newUseSSL && (jmxKeyManager == null))
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
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);
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
    assert debugEnter(CLASS_NAME, "applyNewConfiguration", String
        .valueOf(configEntry), String.valueOf(detailedResults));

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
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);
      int msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
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
          newSslServerCertNickname);
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
   */
  private void applyNewConfiguration(
      int newListenPort, boolean newUseSSL, String newSslServerCertNickname)
  {
    //
    // Stop the current connector
    // TODO Set Msg
    this.finalizeConnectionHandler("new config", true);

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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

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
    assert debugEnter(CLASS_NAME, "getComponentEntryDN");

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
    assert debugEnter(CLASS_NAME, "getClassName");

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
    assert debugEnter(CLASS_NAME, "getAlerts");

    LinkedHashMap<String, String> alerts = new LinkedHashMap<String, String>();

    return alerts;
  }

  /**
   * Retrieves the list port of the configuration entry with which this
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
      assert debugException(CLASS_NAME, "initializeConnectionHandler", ce);

      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

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
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

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
        // This is fine -- we'll just use the default.
        certNameAttr = new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME,
            getMessage(msgID), false, false, false, DEFAULT_SSL_CERT_NICKNAME);
      }
      return certNameAttr;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
      String message = getMessage(
          msgID,
          String.valueOf(configEntryDN),
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }

  /**
   * Retrieve the KeyManager configured for the JMX Connection handler.
   * With look for the child config entry (We should have no more than
   * one child entry)
   *
   * @param jmxConnectorDN the DN of the associated JMX connector
   * entry
   *
   * @return the configured key manager if set or the server
   * key manager
   */
  private KeyManagerProvider getJmxKeyManager(
      ConfigEntry keyManagerConfigEntry)
  {
    //
    // Get the key manager provider configuration entry. If it is not
    // present, then register an add listener.
    boolean shouldReturnNull = false;

    if (keyManagerConfigEntry == null)
    {
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_WARNING,
          MSGID_CONFIG_KEYMANAGER_NO_CONFIG_ENTRY);
      return null;
    }

    //
    // See if the entry indicates whether the key manager provider
    // should be enabled.
    int msgID = MSGID_CONFIG_KEYMANAGER_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub = new BooleanConfigAttribute(
        ATTR_KEYMANAGER_ENABLED, getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute enabledAttr = (BooleanConfigAttribute)
         keyManagerConfigEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        //
        // The attribute is not present, so the key manager
        // provider will be disabled.
        // Log a warning message and return.
        // FIXME -- Message shouldn't be the same than the server one
        logError(
            ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.SEVERE_WARNING,
            MSGID_CONFIG_KEYMANAGER_NO_ENABLED_ATTR);
        shouldReturnNull = true;
      }
      else if (!enabledAttr.activeValue())
      {
        //
        // The key manager provider is explicitly disabled. Log a
        // mild warning and return.
        // FIXME -- Message shouldn't be the same than the server one
        logError(
            ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.MILD_WARNING,
            MSGID_CONFIG_KEYMANAGER_DISABLED);
        shouldReturnNull = true;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeKeyManagerProvider", e);

      // FIXME -- Message shouldn't be the same than the server one
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_ERROR,
          MSGID_CONFIG_KEYMANAGER_UNABLE_TO_DETERMINE_ENABLED_STATE,
          stackTraceToSingleLineString(e));
      return null;
    }

    //
    // See if it specifies the class name for the key manager provider
    // implementation.
    String className;
    msgID = MSGID_CONFIG_KEYMANAGER_DESCRIPTION_CLASS;
    StringConfigAttribute classStub = new StringConfigAttribute(
        ATTR_KEYMANAGER_CLASS, getMessage(msgID), true, false, false);
    try
    {
      StringConfigAttribute classAttr = (StringConfigAttribute)
            keyManagerConfigEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        // FIXME -- Message shouldn't be the same than the server one
        logError(
            ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.SEVERE_ERROR,
            MSGID_CONFIG_KEYMANAGER_NO_CLASS_ATTR);
        return null;
      }
      else
      {
        className = classAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeKeyManagerProvider", e);

      // FIXME Message shouldn't be the same than the server one
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_ERROR,
          MSGID_CONFIG_KEYMANAGER_CANNOT_DETERMINE_CLASS,
          stackTraceToSingleLineString(e));
      return null;
    }

    //
    // Try to load the class and instantiate it as a key manager
    // provider.
    Class keyManagerProviderClass;
    try
    {
      // FIXME -- Should we use a custom class loader for this?
      keyManagerProviderClass = Class.forName(className);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeKeyManagerProvider", e);

      // FIXME -- Message shouldn't be the same than the server one
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_ERROR,
          MSGID_CONFIG_KEYMANAGER_CANNOT_LOAD_CLASS,
          String.valueOf(className),
          stackTraceToSingleLineString(e));
      return null;
    }

    KeyManagerProvider keyManagerProvider;
    try
    {
      keyManagerProvider = (KeyManagerProvider) keyManagerProviderClass
          .newInstance();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeKeyManagerProvider", e);

      // FIXME -- Message shouldn't be the same than the server one
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_ERROR,
          MSGID_CONFIG_KEYMANAGER_CANNOT_INSTANTIATE_CLASS,
          String.valueOf(className),
          stackTraceToSingleLineString(e));
      return null;
    }

    //
    // Try to initialize the key manager provider with the contents of
    // the configuration entry.
    try
    {
      keyManagerProvider.initializeKeyManagerProvider(keyManagerConfigEntry);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeKeyManagerProvider", e);

      // FIXME -- Message shouldn't be the same than the server one
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.SEVERE_WARNING,
          MSGID_CONFIG_KEYMANAGER_CANNOT_INITIALIZE,
          String.valueOf(className),
          e.getMessage());
      return null;
    }

    if (shouldReturnNull)
    {
      return null;
    }
    else
    {
      return keyManagerProvider;
    }
  }
}
