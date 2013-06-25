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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ServletException;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.servlet.HttpServlet;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.rest2ldap.AuthorizationPolicy;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.opendj.rest2ldap.servlet.Rest2LDAPContextFactory;
import org.glassfish.grizzly.http.HttpProbe;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.HttpServerMonitoringConfig;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.HTTPConnectionHandlerCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.NullKeyManagerProvider;
import org.opends.server.extensions.NullTrustManagerProvider;
import org.opends.server.loggers.HTTPAccessLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.monitors.ClientConnectionMonitorProvider;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a connection handler that will be used for communicating
 * with clients over HTTP. The connection handler is responsible for
 * starting/stopping the embedded web server.
 */
public class HTTPConnectionHandler extends
    ConnectionHandler<HTTPConnectionHandlerCfg> implements
    ConfigurationChangeListener<HTTPConnectionHandlerCfg>,
    ServerShutdownListener, AlertGenerator
{

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /** Default friendly name for this connection handler. */
  private static final String DEFAULT_FRIENDLY_NAME = "HTTP Connection Handler";

  /** SSL instance name used in context creation. */
  private static final String SSL_CONTEXT_INSTANCE_NAME = "TLS";

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper().configure(
      JsonParser.Feature.ALLOW_COMMENTS, true);

  /** The initialization configuration. */
  private HTTPConnectionHandlerCfg initConfig;

  /** The current configuration. */
  private HTTPConnectionHandlerCfg currentConfig;

  /**
   * Indicates whether the Directory Server is in the process of shutting down.
   */
  private volatile boolean shutdownRequested;

  /** Indicates whether this connection handler is enabled. */
  private boolean enabled;

  /** The set of listeners for this connection handler. */
  private List<HostPort> listeners = new LinkedList<HostPort>();

  /** The HTTP server embedded in OpenDJ. */
  private HttpServer httpServer;

  /** The HTTP probe that collects stats. */
  private HTTPStatsProbe httpProbe;

  /**
   * Holds the current client connections. Using {@link ConcurrentHashMap} to
   * ensure no concurrent reads/writes can happen and adds/removes are fast. We
   * only use the keys, so it does not matter what value is put there.
   */
  private Map<ClientConnection, ClientConnection> clientConnections =
      new ConcurrentHashMap<ClientConnection, ClientConnection>();

  /** The set of statistics collected for this connection handler. */
  private HTTPStatistics statTracker;

  /**
   * The client connection monitor provider associated with this connection
   * handler.
   */
  private ClientConnectionMonitorProvider connMonitor;

  /** The unique name assigned to this connection handler. */
  private String handlerName;

  /** The protocol used by this connection handler. */
  private String protocol;

  /**
   * The condition variable that will be used by the start method to wait for
   * the socket port to be opened and ready to process requests before
   * returning.
   */
  private final Object waitListen = new Object();

  /** The friendly name of this connection handler. */
  private String friendlyName;

  /**
   * The SSL engine configurator is used for obtaining default SSL parameters.
   */
  private SSLEngineConfigurator sslEngineConfigurator;

  /**
   * Default constructor. It is invoked by reflection to create this
   * {@link ConnectionHandler}.
   */
  public HTTPConnectionHandler()
  {
    super(DEFAULT_FRIENDLY_NAME);
  }

  /**
   * Returns whether unauthenticated HTTP requests are allowed. The server
   * checks whether unauthenticated requests are allowed server-wide first then
   * for the HTTP Connection Handler second.
   *
   * @return true if unauthenticated requests are allowed, false otherwise.
   */
  public boolean acceptUnauthenticatedRequests()
  {
    // the global setting overrides the more specific setting here.
    return !DirectoryServer.rejectUnauthenticatedRequests()
        && !this.currentConfig.isAuthenticationRequired();
  }

  /**
   * Registers a client connection to track it.
   *
   * @param clientConnection
   *          the client connection to register
   */
  void addClientConnection(ClientConnection clientConnection)
  {
    clientConnections.put(clientConnection, clientConnection);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      HTTPConnectionHandlerCfg config)
  {
    // Create variables to include in the response.
    boolean adminActionRequired = false;
    List<Message> messages = new ArrayList<Message>();

    if (anyChangeRequiresRestart(config))
    {
      adminActionRequired = true;
      messages.add(ERR_CONNHANDLER_CONFIG_CHANGES_REQUIRE_RESTART.get("HTTP"));
    }

    // Reconfigure SSL if needed.
    try
    {
      configureSSL(config);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      messages.add(e.getMessageObject());
      return new ConfigChangeResult(e.getResultCode(), adminActionRequired,
          messages);
    }

    if (config.isEnabled() && this.currentConfig.isEnabled() && isListening())
    { // server was running and will still be running
      // if the "enabled" was flipped, leave it to the stop / start server to
      // handle it
      if (!this.currentConfig.isKeepStats() && config.isKeepStats())
      { // it must now keep stats while it was not previously
        setHttpStatsProbe(this.httpServer);
      }
      else if (this.currentConfig.isKeepStats() && !config.isKeepStats()
          && this.httpProbe != null)
      { // it must NOT keep stats anymore
        getHttpConfig(this.httpServer).removeProbes(this.httpProbe);
        this.httpProbe = null;
      }
    }

    this.initConfig = config;
    this.currentConfig = config;
    this.enabled = this.currentConfig.isEnabled();

    return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
        messages);
  }

  private boolean anyChangeRequiresRestart(HTTPConnectionHandlerCfg newCfg)
  {
    return !equals(newCfg.getListenPort(), initConfig.getListenPort())
        || !equals(newCfg.getListenAddress(), initConfig.getListenAddress())
        || !equals(newCfg.getMaxRequestSize(), currentConfig
            .getMaxRequestSize())
        || !equals(newCfg.isAllowTCPReuseAddress(), currentConfig
            .isAllowTCPReuseAddress())
        || !equals(newCfg.isUseTCPKeepAlive(), currentConfig
            .isUseTCPKeepAlive())
        || !equals(newCfg.isUseTCPNoDelay(), currentConfig.isUseTCPNoDelay())
        || !equals(newCfg.getMaxBlockedWriteTimeLimit(), currentConfig
            .getMaxBlockedWriteTimeLimit())
        || !equals(newCfg.getBufferSize(), currentConfig.getBufferSize())
        || !equals(newCfg.getAcceptBacklog(), currentConfig.getAcceptBacklog())
        || !equals(newCfg.isUseSSL(), currentConfig.isUseSSL())
        || !equals(newCfg.getKeyManagerProviderDN(), currentConfig
            .getKeyManagerProviderDN())
        || !equals(newCfg.getSSLCertNickname(), currentConfig
            .getSSLCertNickname())
        || !equals(newCfg.getTrustManagerProviderDN(), currentConfig
            .getTrustManagerProviderDN())
        || !equals(newCfg.getSSLProtocol(), currentConfig.getSSLProtocol())
        || !equals(newCfg.getSSLCipherSuite(), currentConfig
            .getSSLCipherSuite())
        || !equals(newCfg.getSSLClientAuthPolicy(), currentConfig
            .getSSLClientAuthPolicy());
  }

  private boolean equals(Object o1, Object o2)
  {
    if (o1 == null)
    {
      return o2 == null;
    }
    return o1.equals(o2);
  }

  private boolean equals(long l1, long l2)
  {
    return l1 == l2;
  }

  private boolean equals(boolean b1, boolean b2)
  {
    return b1 == b2;
  }

  private void configureSSL(HTTPConnectionHandlerCfg config)
      throws DirectoryException
  {
    protocol = config.isUseSSL() ? "HTTPS" : "HTTP";
    if (config.isUseSSL())
    {
      sslEngineConfigurator = createSSLEngineConfigurator(config);
    }
    else
    {
      sslEngineConfigurator = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeConnectionHandler(Message finalizeReason)
  {
    shutdownRequested = true;
    // Unregister this as a change listener.
    currentConfig.removeHTTPChangeListener(this);

    if (connMonitor != null)
    {
      DirectoryServer.deregisterMonitorProvider(connMonitor);
    }

    if (statTracker != null)
    {
      DirectoryServer.deregisterMonitorProvider(statTracker);
    }
  }

  /** {@inheritDoc} */
  @Override
  public LinkedHashMap<String, String> getAlerts()
  {
    LinkedHashMap<String, String> alerts = new LinkedHashMap<String, String>();

    alerts.put(ALERT_TYPE_HTTP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES,
        ALERT_DESCRIPTION_HTTP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES);

    return alerts;
  }

  /** {@inheritDoc} */
  @Override
  public String getClassName()
  {
    return HTTPConnectionHandler.class.getName();
  }

  /** {@inheritDoc} */
  @Override
  public Collection<ClientConnection> getClientConnections()
  {
    return clientConnections.keySet();
  }

  /** {@inheritDoc} */
  @Override
  public DN getComponentEntryDN()
  {
    return currentConfig.dn();
  }

  /** {@inheritDoc} */
  @Override
  public String getConnectionHandlerName()
  {
    return handlerName;
  }

  /**
   * Returns the current config of this connection handler.
   *
   * @return the current config of this connection handler
   */
  HTTPConnectionHandlerCfg getCurrentConfig()
  {
    return this.currentConfig;
  }

  /** {@inheritDoc} */
  @Override
  public Collection<String> getEnabledSSLCipherSuites()
  {
    final SSLEngineConfigurator configurator = sslEngineConfigurator;
    if (configurator != null)
    {
      return Arrays.asList(configurator.getEnabledCipherSuites());
    }
    return super.getEnabledSSLCipherSuites();
  }

  /** {@inheritDoc} */
  @Override
  public Collection<String> getEnabledSSLProtocols()
  {
    final SSLEngineConfigurator configurator = sslEngineConfigurator;
    if (configurator != null)
    {
      return Arrays.asList(configurator.getEnabledProtocols());
    }
    return super.getEnabledSSLProtocols();
  }

  /** {@inheritDoc} */
  @Override
  public Collection<HostPort> getListeners()
  {
    return listeners;
  }

  /**
   * Returns the listen port for this connection handler.
   *
   * @return the listen port for this connection handler.
   */
  int getListenPort()
  {
    return this.initConfig.getListenPort();
  }

  /** {@inheritDoc} */
  @Override
  public String getProtocol()
  {
    return protocol;
  }

  /**
   * Returns the SSL engine configured for this connection handler if SSL is
   * enabled, null otherwise.
   *
   * @return the SSL engine if SSL is enabled, null otherwise
   */
  SSLEngine getSSLEngine()
  {
    return sslEngineConfigurator.createSSLEngine();
  }

  /** {@inheritDoc} */
  @Override
  public String getShutdownListenerName()
  {
    return handlerName;
  }

  /**
   * Retrieves the set of statistics maintained by this connection handler.
   *
   * @return The set of statistics maintained by this connection handler.
   */
  public HTTPStatistics getStatTracker()
  {
    return statTracker;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeConnectionHandler(HTTPConnectionHandlerCfg config)
      throws ConfigException, InitializationException
  {
    if (friendlyName == null)
    {
      friendlyName = config.dn().getRDN().getAttributeValue(0).toString();
    }

    int listenPort = config.getListenPort();
    for (InetAddress a : config.getListenAddress())
    {
      listeners.add(new HostPort(a.getHostAddress(), listenPort));
    }

    handlerName = getHandlerName(config);

    // Configure SSL if needed.
    try
    {
      configureSSL(config);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new InitializationException(e.getMessageObject());
    }

    // Create and register monitors.
    statTracker = new HTTPStatistics(handlerName + " Statistics");
    DirectoryServer.registerMonitorProvider(statTracker);

    connMonitor = new ClientConnectionMonitorProvider(this);
    DirectoryServer.registerMonitorProvider(connMonitor);

    // Register this as a change listener.
    config.addHTTPChangeListener(this);

    this.initConfig = config;
    this.currentConfig = config;
    this.enabled = this.currentConfig.isEnabled();
  }

  private String getHandlerName(HTTPConnectionHandlerCfg config)
  {
    StringBuilder nameBuffer = new StringBuilder();
    nameBuffer.append(friendlyName);
    for (InetAddress a : config.getListenAddress())
    {
      nameBuffer.append(" ");
      nameBuffer.append(a.getHostAddress());
    }
    nameBuffer.append(" port ");
    nameBuffer.append(config.getListenPort());
    return nameBuffer.toString();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(ConnectionHandlerCfg configuration,
      List<Message> unacceptableReasons)
  {
    HTTPConnectionHandlerCfg config = (HTTPConnectionHandlerCfg) configuration;

    if ((currentConfig == null) || (!this.enabled && config.isEnabled()))
    {
      // Attempt to bind to the listen port on all configured addresses to
      // verify whether the connection handler will be able to start.
      Message errorMessage =
          checkAnyListenAddressInUse(config.getListenAddress(), config
              .getListenPort(), config.isAllowTCPReuseAddress(), config.dn());
      if (errorMessage != null)
      {
        unacceptableReasons.add(errorMessage);
        return false;
      }
    }

    if (config.isEnabled())
    {
      // Check that the SSL configuration is valid.
      if (config.isUseSSL())
      {
        try
        {
          createSSLEngineConfigurator(config);
        }
        catch (DirectoryException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          unacceptableReasons.add(e.getMessageObject());
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Checks whether any listen address is in use for the given port. The check
   * is performed by binding to each address and port.
   *
   * @param listenAddresses
   *          the listen {@link InetAddress} to test
   * @param listenPort
   *          the listen port to test
   * @param allowReuseAddress
   *          whether addresses can be reused
   * @param configEntryDN
   *          the configuration entry DN
   * @return an error message if at least one of the address is already in use,
   *         null otherwise.
   */
  private Message checkAnyListenAddressInUse(
      Collection<InetAddress> listenAddresses, int listenPort,
      boolean allowReuseAddress, DN configEntryDN)
  {
    for (InetAddress a : listenAddresses)
    {
      try
      {
        if (isAddressInUse(a, listenPort, allowReuseAddress))
        {
          throw new IOException(ERR_CONNHANDLER_ADDRESS_INUSE.get().toString());
        }
      }
      catch (IOException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        return ERR_CONNHANDLER_CANNOT_BIND.get("HTTP", String
            .valueOf(configEntryDN), a.getHostAddress(), listenPort,
            getExceptionMessage(e));
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      HTTPConnectionHandlerCfg configuration, List<Message> unacceptableReasons)
  {
    return isConfigurationAcceptable(configuration, unacceptableReasons);
  }

  /**
   * Indicates whether this connection handler should maintain usage statistics.
   *
   * @return <CODE>true</CODE> if this connection handler should maintain usage
   *         statistics, or <CODE>false</CODE> if not.
   */
  public boolean keepStats()
  {
    return currentConfig.isKeepStats();
  }

  /** {@inheritDoc} */
  @Override
  public void processServerShutdown(Message reason)
  {
    shutdownRequested = true;
  }

  private boolean isListening()
  {
    return httpServer != null;
  }

  /** {@inheritDoc} */
  @Override
  public void start()
  {
    // The Directory Server start process should only return
    // when the connection handlers port are fully opened
    // and working. The start method therefore needs to wait for
    // the created thread to
    synchronized (waitListen)
    {
      super.start();

      try
      {
        waitListen.wait();
      }
      catch (InterruptedException e)
      {
        // If something interrupted the start its probably better
        // to return ASAP.
      }
    }
  }

  /**
   * Unregisters a client connection to stop tracking it.
   *
   * @param clientConnection
   *          the client connection to unregister
   */
  void removeClientConnection(ClientConnection clientConnection)
  {
    clientConnections.remove(clientConnection);
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    setName(handlerName);

    boolean lastIterationFailed = false;
    while (!shutdownRequested)
    {
      // If this connection handler is not enabled, then just sleep
      // for a bit and check again.
      if (!this.enabled)
      {
        if (isListening())
        {
          stopHttpServer();
        }

        StaticUtils.sleep(1000);
        continue;
      }

      if (isListening())
      {
        // If already listening, then sleep for a bit and check again.
        StaticUtils.sleep(1000);
        continue;
      }

      try
      {
        // At this point, the connection Handler either started
        // correctly or failed to start but the start process
        // should be notified and resume its work in any cases.
        synchronized (waitListen)
        {
          waitListen.notify();
        }

        // If we have gotten here, then we are about to start listening
        // for the first time since startup or since we were previously
        // disabled. Start the embedded HTTP server
        startHttpServer();
        lastIterationFailed = false;
      }
      catch (Exception e)
      {
        // clean up the messed up HTTP server
        cleanUpHttpServer();

        // error + alert about the horked config
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(ERR_CONNHANDLER_CANNOT_ACCEPT_CONNECTION.get(friendlyName,
            String.valueOf(currentConfig.dn()), getExceptionMessage(e)));

        if (lastIterationFailed)
        {
          // The last time through the accept loop we also
          // encountered a failure. Rather than enter a potential
          // infinite loop of failures, disable this acceptor and
          // log an error.
          Message message =
              ERR_CONNHANDLER_CONSECUTIVE_ACCEPT_FAILURES.get(friendlyName,
                  String.valueOf(currentConfig.dn()),
                  stackTraceToSingleLineString(e));
          logError(message);

          DirectoryServer.sendAlertNotification(this,
              ALERT_TYPE_HTTP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES, message);

          this.enabled = false;
        }
        else
        {
          lastIterationFailed = true;
        }
      }
    }

    // Initiate shutdown
    stopHttpServer();
  }

  private void startHttpServer() throws Exception
  {
    // silence Grizzly's own logging
    Logger.getLogger("org.glassfish.grizzly").setLevel(Level.OFF);

    if (HTTPAccessLogger.getHTTPAccessLogPublishers().isEmpty())
    {
      logError(WARN_CONFIG_LOGGER_NO_ACTIVE_HTTP_ACCESS_LOGGERS.get());
    }

    this.httpServer = createHttpServer();

    // register servlet as default servlet and also able to serve REST requests
    createAndRegisterServlet("OpenDJ Rest2LDAP servlet", "", "/*");

    TRACER.debugInfo("Starting HTTP server...");
    this.httpServer.start();
    TRACER.debugInfo("HTTP server started");
    logError(NOTE_CONNHANDLER_STARTED_LISTENING.get(handlerName));
  }

  private HttpServer createHttpServer()
  {
    final HttpServer server = new HttpServer();

    final int requestSize = (int) currentConfig.getMaxRequestSize();
    final ServerConfiguration serverConfig = server.getServerConfiguration();
    serverConfig.setMaxBufferedPostSize(requestSize);
    serverConfig.setMaxFormPostSize(requestSize);
    if (keepStats())
    {
      setHttpStatsProbe(server);
    }

    // configure the network listener
    final NetworkListener listener =
        new NetworkListener("Rest2LDAP", NetworkListener.DEFAULT_NETWORK_HOST,
            initConfig.getListenPort());
    server.addListener(listener);

    // configure the network transport
    final TCPNIOTransport transport = listener.getTransport();
    transport.setReuseAddress(currentConfig.isAllowTCPReuseAddress());
    transport.setKeepAlive(currentConfig.isUseTCPKeepAlive());
    transport.setTcpNoDelay(currentConfig.isUseTCPNoDelay());
    transport.setWriteTimeout(currentConfig.getMaxBlockedWriteTimeLimit(),
        TimeUnit.MILLISECONDS);

    final int bufferSize = (int) currentConfig.getBufferSize();
    transport.setReadBufferSize(bufferSize);
    transport.setWriteBufferSize(bufferSize);
    transport.setIOStrategy(SameThreadIOStrategy.getInstance());
    final int numRequestHandlers =
        getNumRequestHandlers(currentConfig.getNumRequestHandlers(),
            friendlyName);
    transport.setSelectorRunnersCount(numRequestHandlers);
    transport.setServerConnectionBackLog(currentConfig.getAcceptBacklog());

    // configure SSL
    if (sslEngineConfigurator != null)
    {
      listener.setSecure(true);
      listener.setSSLEngineConfig(sslEngineConfigurator);
    }

    return server;
  }

  private void setHttpStatsProbe(HttpServer server)
  {
    this.httpProbe = new HTTPStatsProbe(this.statTracker);
    getHttpConfig(server).addProbes(this.httpProbe);
  }

  private MonitoringConfig<HttpProbe> getHttpConfig(HttpServer server)
  {
    final HttpServerMonitoringConfig monitoringCfg =
        server.getServerConfiguration().getMonitoringConfig();
    return monitoringCfg.getHttpConfig();
  }

  private void createAndRegisterServlet(final String servletName,
      final String... urlPatterns) throws Exception
  {
    // parse and use JSON config
    File jsonConfigFile = getFileForPath(this.currentConfig.getConfigFile());
    final JsonValue configuration =
        parseJsonConfiguration(jsonConfigFile).recordKeyAccesses();
    final HTTPAuthenticationConfig authenticationConfig =
        getAuthenticationConfig(configuration);
    final ConnectionFactory connFactory = getConnectionFactory(configuration);
    configuration.verifyAllKeysAccessed();

    Filter filter =
        new CollectClientConnectionsFilter(this, authenticationConfig);
    final HttpServlet servlet = new HttpServlet(connFactory,
    // Used for hooking our HTTPClientConnection in Rest2LDAP
        Rest2LDAPContextFactory.getHttpServletContextFactory());

    // Create and deploy the Web app context
    final WebappContext ctx = new WebappContext(servletName);
    ctx.addFilter("collectClientConnections", filter).addMappingForUrlPatterns(
        EnumSet.of(DispatcherType.REQUEST), true, urlPatterns);
    ctx.addServlet(servletName, servlet).addMapping(urlPatterns);
    ctx.deploy(this.httpServer);
  }

  private HTTPAuthenticationConfig getAuthenticationConfig(
      final JsonValue configuration)
  {
    final HTTPAuthenticationConfig result = new HTTPAuthenticationConfig();
    final JsonValue val = configuration.get("authenticationFilter");
    result.setBasicAuthenticationSupported(asBool(val,
        "supportHTTPBasicAuthentication"));
    result.setCustomHeadersAuthenticationSupported(asBool(val,
        "supportAltAuthentication"));
    result.setCustomHeaderUsername(val.get("altAuthenticationUsernameHeader")
        .asString());
    result.setCustomHeaderPassword(val.get("altAuthenticationPasswordHeader")
        .asString());
    final String searchBaseDN = asString(val, "searchBaseDN");
    result.setSearchBaseDN(org.forgerock.opendj.ldap.DN.valueOf(searchBaseDN));
    result.setSearchScope(SearchScope.valueOf(asString(val, "searchScope")));
    result.setSearchFilterTemplate(asString(val, "searchFilterTemplate"));
    return result;
  }

  private String asString(JsonValue value, String key)
  {
    return value.get(key).required().asString();
  }

  private boolean asBool(JsonValue value, String key)
  {
    return value.get(key).defaultTo(false).asBoolean();
  }

  private ConnectionFactory getConnectionFactory(final JsonValue configuration)
  {
    final Router router = new Router();
    final JsonValue mappings =
        configuration.get("servlet").get("mappings").required();
    for (final String mappingUrl : mappings.keys())
    {
      final JsonValue mapping = mappings.get(mappingUrl);
      final CollectionResourceProvider provider =
          Rest2LDAP.builder().authorizationPolicy(AuthorizationPolicy.REUSE)
              .configureMapping(mapping).build();
      router.addRoute(mappingUrl, provider);
    }
    return Resources.newInternalConnectionFactory(router);
  }

  private JsonValue parseJsonConfiguration(File configFile) throws IOException,
      JsonParseException, JsonMappingException, ServletException
  {
    // Parse the config file.
    final Object content = JSON_MAPPER.readValue(configFile, Object.class);
    if (!(content instanceof Map))
    {
      throw new ServletException("Servlet configuration file '" + configFile
          + "' does not contain a valid JSON configuration");
    }

    // TODO JNR should we restrict the possible configurations in this file?
    // Should we remove any config that does not make any sense to the
    // HTTP Connection Handler?
    return new JsonValue(content);
  }

  private void stopHttpServer()
  {
    if (this.httpServer != null)
    {
      TRACER.debugInfo("Stopping HTTP server...");
      this.httpServer.stop();
      cleanUpHttpServer();
      TRACER.debugInfo("HTTP server stopped");
      logError(NOTE_CONNHANDLER_STOPPED_LISTENING.get(handlerName));
    }
  }

  private void cleanUpHttpServer()
  {
    this.httpServer = null;
    this.httpProbe = null;
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append(handlerName);
  }

  private SSLEngineConfigurator createSSLEngineConfigurator(
      HTTPConnectionHandlerCfg config) throws DirectoryException
  {
    if (!config.isUseSSL())
    {
      return null;
    }

    try
    {
      SSLContext sslContext = createSSLContext(config);
      SSLEngineConfigurator configurator =
          new SSLEngineConfigurator(sslContext);
      configurator.setClientMode(false);

      // configure with defaults from the JVM
      final SSLEngine defaults = sslContext.createSSLEngine();
      configurator.setEnabledProtocols(defaults.getEnabledProtocols());
      configurator.setEnabledCipherSuites(defaults.getEnabledCipherSuites());

      final Set<String> protocols = config.getSSLProtocol();
      if (!protocols.isEmpty())
      {
        String[] array = protocols.toArray(new String[protocols.size()]);
        configurator.setEnabledProtocols(array);
      }

      final Set<String> ciphers = config.getSSLCipherSuite();
      if (!ciphers.isEmpty())
      {
        String[] array = ciphers.toArray(new String[ciphers.size()]);
        configurator.setEnabledCipherSuites(array);
      }

      switch (config.getSSLClientAuthPolicy())
      {
      case DISABLED:
        configurator.setNeedClientAuth(false);
        configurator.setWantClientAuth(false);
        break;
      case REQUIRED:
        configurator.setNeedClientAuth(true);
        configurator.setWantClientAuth(true);
        break;
      case OPTIONAL:
      default:
        configurator.setNeedClientAuth(false);
        configurator.setWantClientAuth(true);
        break;
      }

      return configurator;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      ResultCode resCode = DirectoryServer.getServerErrorResultCode();
      Message message =
          ERR_CONNHANDLER_SSL_CANNOT_INITIALIZE.get(getExceptionMessage(e));
      throw new DirectoryException(resCode, message, e);
    }
  }

  private SSLContext createSSLContext(HTTPConnectionHandlerCfg config)
      throws Exception
  {
    if (!config.isUseSSL())
    {
      return null;
    }

    DN keyMgrDN = config.getKeyManagerProviderDN();
    KeyManagerProvider<?> keyManagerProvider =
        DirectoryServer.getKeyManagerProvider(keyMgrDN);
    if (keyManagerProvider == null)
    {
      keyManagerProvider = new NullKeyManagerProvider();
    }

    String alias = config.getSSLCertNickname();
    KeyManager[] keyManagers;
    if (alias == null)
    {
      keyManagers = keyManagerProvider.getKeyManagers();
    }
    else
    {
      keyManagers =
          SelectableCertificateKeyManager.wrap(keyManagerProvider
              .getKeyManagers(), alias);
    }

    DN trustMgrDN = config.getTrustManagerProviderDN();
    TrustManagerProvider<?> trustManagerProvider =
        DirectoryServer.getTrustManagerProvider(trustMgrDN);
    if (trustManagerProvider == null)
    {
      trustManagerProvider = new NullTrustManagerProvider();
    }

    SSLContext sslContext = SSLContext.getInstance(SSL_CONTEXT_INSTANCE_NAME);
    sslContext.init(keyManagers, trustManagerProvider.getTrustManagers(), null);
    return sslContext;
  }

}
