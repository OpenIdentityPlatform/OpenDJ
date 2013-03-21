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

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

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

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
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
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.SelectableCertificateKeyManager;

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

  /**
   * Fake Servlet.
   * <p>
   * TODO JNR remove when using REST2LDAP servlet
   */
  private static final class FakeServlet extends HttpServlet
  {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
      // TODO Auto-generated method stub
      super.doGet(req, resp);
    }

  }

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /** Default friendly name for this connection handler. */
  private static final String DEFAULT_FRIENDLY_NAME = "HTTP Connection Handler";

  /** SSL instance name used in context creation. */
  private static final String SSL_CONTEXT_INSTANCE_NAME = "TLS";

  /** The initialization configuration. */
  private HTTPConnectionHandlerCfg initConfig;

  /** The current configuration. */
  private HTTPConnectionHandlerCfg currentConfig;

  /**
   * Indicates whether the Directory Server is in the process of shutting down.
   */
  private volatile boolean shutdownRequested;

  /** The set of listeners for this connection handler. */
  private List<HostPort> listeners = new LinkedList<HostPort>();

  /** The HTTP server embedded in OpenDJ. */
  private HttpServer httpServer;

  /**
   * Holds the current client connections. Using {@link ConcurrentHashMap} to
   * ensure no concurrent reads/writes can happen and adds/removes are fast. We
   * only use the keys, so it does not matter what value is put there.
   */
  private Map<ClientConnection, ClientConnection> clientConnections =
      new ConcurrentHashMap<ClientConnection, ClientConnection>();

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

  /**
   * The friendly name of this connection handler.
   */
  private String friendlyName;

  /**
   * SSL context.
   *
   * @see HTTPConnectionHandler#sslEngine
   */
  private SSLContext sslContext;

  /** The SSL engine is used for obtaining default SSL parameters. */
  private SSLEngine sslEngine;

  /**
   * Default constructor. It is invoked by reflection to create this
   * {@link ConnectionHandler}.
   */
  public HTTPConnectionHandler()
  {
    super(DEFAULT_FRIENDLY_NAME);
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

    this.initConfig = config;
    this.currentConfig = config;

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
      sslContext = createSSLContext(config);
      sslEngine = createSSLEngine(config, sslContext);
    }
    else
    {
      sslContext = null;
      sslEngine = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeConnectionHandler(Message finalizeReason)
  {
    shutdownRequested = true;
    // Unregister this as a change listener.
    currentConfig.removeHTTPChangeListener(this);

    // TODO JNR
    // if (connMonitor != null)
    // {
    // String lowerName = toLowerCase(connMonitor.getMonitorInstanceName());
    // DirectoryServer.deregisterMonitorProvider(lowerName);
    // }
    //
    // if (statTracker != null)
    // {
    // String lowerName = toLowerCase(statTracker.getMonitorInstanceName());
    // DirectoryServer.deregisterMonitorProvider(lowerName);
    // }

  }

  /** {@inheritDoc} */
  @Override
  public LinkedHashMap<String, String> getAlerts()
  {
    // TODO Auto-generated method stub
    return null;
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
    final SSLEngine engine = sslEngine;
    if (engine != null)
    {
      return Arrays.asList(engine.getEnabledCipherSuites());
    }
    return super.getEnabledSSLCipherSuites();
  }

  /** {@inheritDoc} */
  @Override
  public Collection<String> getEnabledSSLProtocols()
  {
    final SSLEngine engine = sslEngine;
    if (engine != null)
    {
      return Arrays.asList(engine.getEnabledProtocols());
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
    return sslEngine;
  }

  /** {@inheritDoc} */
  @Override
  public String getShutdownListenerName()
  {
    return handlerName;
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

    // TODO JNR
    // handle ds-cfg-keep-stats
    // handle ds-cfg-num-request-handlers??
    // // Create and register monitors.
    // statTracker = new LDAPStatistics(handlerName + " Statistics");
    // DirectoryServer.registerMonitorProvider(statTracker);
    //
    // connMonitor = new ClientConnectionMonitorProvider(this);
    // DirectoryServer.registerMonitorProvider(connMonitor);

    // Register this as a change listener.
    config.addHTTPChangeListener(this);

    this.initConfig = config;
    this.currentConfig = config;
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

    if ((currentConfig == null)
        || (!currentConfig.isEnabled() && config.isEnabled()))
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
          SSLContext sslContext = createSSLContext(config);
          createSSLEngine(config, sslContext);
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

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    setName(handlerName);

    while (!shutdownRequested)
    {
      // If this connection handler is not enabled, then just sleep
      // for a bit and check again.
      if (!currentConfig.isEnabled())
      {
        if (isListening())
        {
          stopHttpServer();
        }

        sleep(1000);
        continue;
      }

      if (isListening())
      {
        // If already listening, then sleep for a bit and check again.
        sleep(1000);
        continue;
      }

      // If we have gotten here, then we are about to start listening
      // for the first time since startup or since we were previously
      // disabled. Start the embedded HTTP server
      startHttpServer();
    }

    // Initiate shutdown
    stopHttpServer();
  }

  private void startHttpServer()
  {
    // TODO JNR stop Grizzly own logging.
    // [testng] Mar 14, 2013 11:22:13 AM org.glassfish.grizzly.http.server.
    // NetworkListener stop
    // [testng] INFO: Stopped listener bound to [0.0.0.0:8080]
    // [testng] Mar 14, 2013 11:22:19 AM org.glassfish.grizzly.servlet.
    // WebappContext deploy
    // [testng] INFO: Starting application [example] ...
    // [testng] Mar 14, 2013 11:22:19 AM org.glassfish.grizzly.servlet.
    // WebappContext initServlets
    // [testng] INFO: [example] Servlet
    // [org.opends.server.protocols.http.HTTPConnec
    // tionHandler$FakeServlet] registered for url pattern(s) [[/managed/*]].
    // [testng] Mar 14, 2013 11:22:19 AM
    // org.glassfish.grizzly.servlet.WebappContext
    // initFilters
    // [testng] INFO: [example] Filter [org.opends.server.protocols.http.
    // AllowAdressesFilter] registered for
    // url pattern(s) [[/managed/*]] and servlet name(s) [[]]
    // [testng] Mar 14, 2013 11:22:19 AM
    // org.glassfish.grizzly.servlet.WebappContext
    // deploy
    // [testng] INFO: Application [example] is ready to service requests. Root:
    // [/example].
    // [testng] Mar 14, 2013 11:22:19 AM org.glassfish.grizzly.http.server.
    // NetworkListener start
    // [testng] INFO: Started listener bound to [0.0.0.0:8080]
    // [testng] Mar 14, 2013 11:22:19 AM org.glassfish.grizzly.http.server.
    // HttpServer start
    // [testng] INFO: [HttpServer-1] Started.
    this.httpServer =
        HttpServer.createSimpleServer("./", initConfig.getListenPort());

    int requestSize = (int) currentConfig.getMaxRequestSize();
    final ServerConfiguration serverConfig =
        this.httpServer.getServerConfiguration();
    serverConfig.setMaxBufferedPostSize(requestSize);
    serverConfig.setMaxFormPostSize(requestSize);

    try
    {
      for (NetworkListener listener : this.httpServer.getListeners())
      {
        TCPNIOTransport transport = listener.getTransport();
        transport.setReuseAddress(currentConfig.isAllowTCPReuseAddress());
        transport.setKeepAlive(currentConfig.isUseTCPKeepAlive());
        transport.setTcpNoDelay(currentConfig.isUseTCPNoDelay());
        transport.setWriteTimeout(currentConfig.getMaxBlockedWriteTimeLimit(),
            TimeUnit.MILLISECONDS);

        int bufferSize = (int) currentConfig.getBufferSize();
        transport.setReadBufferSize(bufferSize);
        transport.setWriteBufferSize(bufferSize);
        // TODO JNR
        // transport.setIOStrategy(SameThreadIOStrategy.getInstance());
        // transport.setWorkerThreadPool(threadPool);
        // transport.setWorkerThreadPoolConfig(workerPoolConfig);
        transport.setServerConnectionBackLog(currentConfig.getAcceptBacklog());

        if (sslContext != null)
        {
          listener.setSecure(true);
          listener.setSSLEngineConfig(new SSLEngineConfigurator(sslContext));
        }
      }

      // TODO JNR what to use here?
      final String displayName = "example";
      final String contextPath = "/example";
      final String servletName = "managed";
      final String urlPattern = "/managed/*";

      // TODO JNR what to use here?
      final WebappContext ctx = new WebappContext(displayName, contextPath);

      Filter filter =
          new CollectClientConnectionsFilter(this, clientConnections);
      FilterRegistration filterReg =
          ctx.addFilter("collectClientConnections", filter);
      // TODO JNR this is not working
      // filterReg.addMappingForServletNames(EnumSet.allOf(
      // DispatcherType.class), servletName);
      filterReg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),
          urlPattern);

      final ServletRegistration reg =
          ctx.addServlet(servletName, new FakeServlet());
      reg.addMapping(urlPattern);

      ctx.deploy(this.httpServer);

      TRACER.debugInfo("Starting HTTP server...");
      this.httpServer.start();
      TRACER.debugInfo("HTTP server started");
      logError(NOTE_CONNHANDLER_STARTED_LISTENING.get(handlerName));

      // At this point, the connection Handler either started
      // correctly or failed to start but the start process
      // should be notified and resume its work in any cases.
      synchronized (waitListen)
      {
        waitListen.notify();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }

  private void stopHttpServer()
  {
    TRACER.debugInfo("Stopping HTTP server...");
    this.httpServer.stop();
    this.httpServer = null;
    TRACER.debugInfo("HTTP server stopped");
    logError(NOTE_CONNHANDLER_STOPPED_LISTENING.get(handlerName));
  }

  private void sleep(int millis)
  {
    try
    {
      Thread.sleep(millis);
    }
    catch (InterruptedException wokenUp)
    {
    }
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append(handlerName);
  }

  private SSLEngine createSSLEngine(HTTPConnectionHandlerCfg config,
      SSLContext sslContext) throws DirectoryException
  {
    if (sslContext == null)
    {
      return null;
    }

    try
    {
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setUseClientMode(false);

      final Set<String> protocols = config.getSSLProtocol();
      if (!protocols.isEmpty())
      {
        String[] array = protocols.toArray(new String[protocols.size()]);
        sslEngine.setEnabledProtocols(array);
      }

      final Set<String> ciphers = config.getSSLCipherSuite();
      if (!ciphers.isEmpty())
      {
        String[] array = ciphers.toArray(new String[ciphers.size()]);
        sslEngine.setEnabledCipherSuites(array);
      }

      switch (config.getSSLClientAuthPolicy())
      {
      case DISABLED:
        sslEngine.setNeedClientAuth(false);
        sslEngine.setWantClientAuth(false);
        break;
      case REQUIRED:
        sslEngine.setWantClientAuth(true);
        sslEngine.setNeedClientAuth(true);
        break;
      case OPTIONAL:
      default:
        sslEngine.setNeedClientAuth(false);
        sslEngine.setWantClientAuth(true);
        break;
      }

      return sslEngine;
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
      throws DirectoryException
  {
    if (!config.isUseSSL())
    {
      return null;
    }

    try
    {
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
      sslContext.init(keyManagers, trustManagerProvider.getTrustManagers(),
          null);
      return sslContext;
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

}
