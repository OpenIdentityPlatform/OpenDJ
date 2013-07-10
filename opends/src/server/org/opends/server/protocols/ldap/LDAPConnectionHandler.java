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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.protocols.ldap;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.api.*;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.QueueingStrategy;
import org.opends.server.core.WorkQueueStrategy;
import org.opends.server.extensions.NullKeyManagerProvider;
import org.opends.server.extensions.NullTrustManagerProvider;
import org.opends.server.extensions.TLSByteChannel;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.monitors.ClientConnectionMonitorProvider;
import org.opends.server.types.*;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a connection handler that will be used for communicating
 * with clients over LDAP. It is actually implemented in two parts: as a
 * connection handler and one or more request handlers. The connection handler
 * is responsible for accepting new connections and registering each of them
 * with a request handler. The request handlers then are responsible for reading
 * requests from the clients and parsing them as operations. A single request
 * handler may be used, but having multiple handlers might provide better
 * performance in a multi-CPU system.
 */
public final class LDAPConnectionHandler extends
    ConnectionHandler<LDAPConnectionHandlerCfg> implements
    ConfigurationChangeListener<LDAPConnectionHandlerCfg>,
    ServerShutdownListener, AlertGenerator
{

  /**
   * Task run periodically by the connection finalizer.
   */
  private final class ConnectionFinalizerRunnable implements Runnable
  {
    @Override
    public void run()
    {
      if (!connectionFinalizerActiveJobQueue.isEmpty())
      {
        for (Runnable r : connectionFinalizerActiveJobQueue)
        {
          r.run();
        }
        connectionFinalizerActiveJobQueue.clear();
      }

      // Switch the lists.
      synchronized (connectionFinalizerLock)
      {
        List<Runnable> tmp = connectionFinalizerActiveJobQueue;
        connectionFinalizerActiveJobQueue = connectionFinalizerPendingJobQueue;
        connectionFinalizerPendingJobQueue = tmp;
      }

    }
  }



  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Default friendly name for the LDAP connection handler.
   */
  private static final String DEFAULT_FRIENDLY_NAME = "LDAP Connection Handler";

  /** SSL instance name used in context creation. */
  private static final String SSL_CONTEXT_INSTANCE_NAME = "TLS";

  /** The current configuration state. */
  private LDAPConnectionHandlerCfg currentConfig;

  /* Properties that cannot be modified dynamically */

  /** The set of addresses on which to listen for new connections. */
  private Set<InetAddress> listenAddresses;

  /** The port on which this connection handler should listen for requests. */
  private int listenPort;

  /** The SSL client auth policy used by this connection handler. */
  private SSLClientAuthPolicy sslClientAuthPolicy;

  /** The backlog that will be used for the accept queue. */
  private int backlog;

  /** Indicates whether to allow the reuse address socket option. */
  private boolean allowReuseAddress;

  /**
   * The number of request handlers that should be used for this connection
   * handler.
   */
  private int numRequestHandlers;

  /**
   * Indicates whether the Directory Server is in the process of shutting down.
   */
  private volatile boolean shutdownRequested;

  /* Internal LDAP connection handler state */

  /** Indicates whether this connection handler is enabled. */
  private boolean enabled;

  /** The set of clients that are explicitly allowed access to the server. */
  private Collection<AddressMask> allowedClients;

  /**
   * The set of clients that have been explicitly denied access to the server.
   */
  private Collection<AddressMask> deniedClients;

  /**
   * The index to the request handler that will be used for the next connection
   * accepted by the server.
   */
  private int requestHandlerIndex;

  /** The set of listeners for this connection handler. */
  private List<HostPort> listeners;

  /**
   * The set of request handlers that are associated with this connection
   * handler.
   */
  private LDAPRequestHandler[] requestHandlers;

  /** The set of statistics collected for this connection handler. */
  private LDAPStatistics statTracker;

  /**
   * The client connection monitor provider associated with this connection
   * handler.
   */
  private ClientConnectionMonitorProvider connMonitor;

  /**
   * The selector that will be used to multiplex connection acceptance across
   * multiple sockets by a single thread.
   */
  private Selector selector;

  /** The unique name assigned to this connection handler. */
  private String handlerName;

  /** The protocol used by this connection handler. */
  private String protocol;

  /** Queueing strategy. */
  private final QueueingStrategy queueingStrategy;

  /**
   * The condition variable that will be used by the start method to wait for
   * the socket port to be opened and ready to process requests before
   * returning.
   */
  private final Object waitListen = new Object();

  /** The friendly name of this connection handler. */
  private String friendlyName;

  /**
   * SSL context.
   *
   * @see LDAPConnectionHandler#sslEngine
   */
  private SSLContext sslContext;

  /** The SSL engine is used for obtaining default SSL parameters. */
  private SSLEngine sslEngine;

  /**
   * Connection finalizer thread.
   * <p>
   * This thread is defers closing clients for approximately 100ms. This gives
   * the client a chance to close the connection themselves before the server
   * thus avoiding leaving the server side in the TIME WAIT state.
   */
  private final Object connectionFinalizerLock = new Object();
  private ScheduledExecutorService connectionFinalizer;
  private List<Runnable> connectionFinalizerActiveJobQueue;
  private List<Runnable> connectionFinalizerPendingJobQueue;



  /**
   * Creates a new instance of this LDAP connection handler. It must be
   * initialized before it may be used.
   */
  public LDAPConnectionHandler()
  {
    this(new WorkQueueStrategy(), null); // Use name from configuration.
  }



  /**
   * Creates a new instance of this LDAP connection handler, using a queueing
   * strategy. It must be initialized before it may be used.
   *
   * @param strategy
   *          Request handling strategy.
   * @param friendlyName
   *          The name of of this connection handler, or {@code null} if the
   *          name should be taken from the configuration.
   */
  public LDAPConnectionHandler(QueueingStrategy strategy, String friendlyName)
  {
    super(friendlyName != null ? friendlyName : DEFAULT_FRIENDLY_NAME
        + " Thread");

    this.friendlyName = friendlyName;
    this.queueingStrategy = strategy;

    // No real implementation is required. Do all the work in the
    // initializeConnectionHandler method.
  }



  /**
   * Indicates whether this connection handler should allow interaction with
   * LDAPv2 clients.
   *
   * @return <CODE>true</CODE> if LDAPv2 is allowed, or <CODE>false</CODE> if
   *         not.
   */
  public boolean allowLDAPv2()
  {
    return currentConfig.isAllowLDAPV2();
  }



  /**
   * Indicates whether this connection handler should allow the use of the
   * StartTLS extended operation.
   *
   * @return <CODE>true</CODE> if StartTLS is allowed, or <CODE>false</CODE> if
   *         not.
   */
  public boolean allowStartTLS()
  {
    return currentConfig.isAllowStartTLS() && !currentConfig.isUseSSL();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      LDAPConnectionHandlerCfg config)
  {
    // Create variables to include in the response.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    // Note that the following properties cannot be modified:
    //
    // * listen port and addresses
    // * use ssl
    // * ssl policy
    // * ssl cert nickname
    // * accept backlog
    // * tcp reuse address
    // * num request handler

    // Clear the stat tracker if LDAPv2 is being enabled.
    if (currentConfig.isAllowLDAPV2() != config.isAllowLDAPV2())
    {
      if (config.isAllowLDAPV2())
      {
        statTracker.clearStatistics();
      }
    }

    // Apply the changes.
    currentConfig = config;
    enabled = config.isEnabled();
    allowedClients = config.getAllowedClient();
    deniedClients = config.getDeniedClient();

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

    if (config.isAllowLDAPV2())
    {
      DirectoryServer.registerSupportedLDAPVersion(2, this);
    }
    else
    {
      DirectoryServer.deregisterSupportedLDAPVersion(2, this);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  private void configureSSL(LDAPConnectionHandlerCfg config)
      throws DirectoryException
  {
    protocol = config.isUseSSL() ? "LDAPS" : "LDAP";
    if (config.isUseSSL() || config.isAllowStartTLS())
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


  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeConnectionHandler(Message finalizeReason)
  {
    shutdownRequested = true;
    currentConfig.removeLDAPChangeListener(this);

    if (connMonitor != null)
    {
      DirectoryServer.deregisterMonitorProvider(connMonitor);
    }

    if (statTracker != null)
    {
      DirectoryServer.deregisterMonitorProvider(statTracker);
    }

    DirectoryServer.deregisterSupportedLDAPVersion(2, this);
    DirectoryServer.deregisterSupportedLDAPVersion(3, this);

    try
    {
      selector.wakeup();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    for (LDAPRequestHandler requestHandler : requestHandlers)
    {
      requestHandler.processServerShutdown(finalizeReason);
    }

    // Shutdown the connection finalizer and ensure that any pending
    // unclosed connections are closed.
    synchronized (connectionFinalizerLock)
    {
      connectionFinalizer.shutdown();
      connectionFinalizer = null;

      Runnable r = new ConnectionFinalizerRunnable();
      r.run(); // Flush active queue.
      r.run(); // Flush pending queue.
    }
  }



  /**
   * Retrieves information about the set of alerts that this generator may
   * produce. The map returned should be between the notification type for a
   * particular notification and the human-readable description for that
   * notification. This alert generator must not generate any alerts with types
   * that are not contained in this list.
   *
   * @return Information about the set of alerts that this generator may
   *         produce.
   */
  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<String, String>();

    alerts.put(ALERT_TYPE_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES,
        ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES);
    alerts.put(ALERT_TYPE_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR,
        ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR);

    return alerts;
  }



  /**
   * Retrieves the fully-qualified name of the Java class for this alert
   * generator implementation.
   *
   * @return The fully-qualified name of the Java class for this alert generator
   *         implementation.
   */
  @Override
  public String getClassName()
  {
    return LDAPConnectionHandler.class.getName();
  }



  /**
   * Retrieves the set of active client connections that have been established
   * through this connection handler.
   *
   * @return The set of active client connections that have been established
   *         through this connection handler.
   */
  @Override
  public Collection<ClientConnection> getClientConnections()
  {
    List<ClientConnection> connectionList = new LinkedList<ClientConnection>();
    for (LDAPRequestHandler requestHandler : requestHandlers)
    {
      connectionList.addAll(requestHandler.getClientConnections());
    }
    return connectionList;
  }



  /**
   * Retrieves the DN of the configuration entry with which this alert generator
   * is associated.
   *
   * @return The DN of the configuration entry with which this alert generator
   *         is associated.
   */
  @Override
  public DN getComponentEntryDN()
  {
    return currentConfig.dn();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getConnectionHandlerName()
  {
    return handlerName;
  }



  /**
   * {@inheritDoc}
   */
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



  /**
   * {@inheritDoc}
   */
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



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<HostPort> getListeners()
  {
    return listeners;
  }



  /**
   * Retrieves the port on which this connection handler is listening for client
   * connections.
   *
   * @return The port on which this connection handler is listening for client
   *         connections.
   */
  public int getListenPort()
  {
    return listenPort;
  }



  /**
   * Retrieves the maximum length of time in milliseconds that attempts to write
   * to LDAP client connections should be allowed to block.
   *
   * @return The maximum length of time in milliseconds that attempts to write
   *         to LDAP client connections should be allowed to block, or zero if
   *         there should not be any limit imposed.
   */
  public long getMaxBlockedWriteTimeLimit()
  {
    return currentConfig.getMaxBlockedWriteTimeLimit();
  }



  /**
   * Retrieves the maximum ASN.1 element value length that will be allowed by
   * this connection handler.
   *
   * @return The maximum ASN.1 element value length that will be allowed by this
   *         connection handler.
   */
  public int getMaxRequestSize()
  {
    return (int) currentConfig.getMaxRequestSize();
  }



  /**
   * Retrieves the size in bytes of the LDAP response message write buffer
   * defined for this connection handler.
   *
   * @return The size in bytes of the LDAP response message write buffer.
   */
  public int getBufferSize()
  {
    return (int) currentConfig.getBufferSize();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getProtocol()
  {
    return protocol;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getShutdownListenerName()
  {
    return handlerName;
  }



  /**
   * Retrieves the SSL client authentication policy for this connection handler.
   *
   * @return The SSL client authentication policy for this connection handler.
   */
  public SSLClientAuthPolicy getSSLClientAuthPolicy()
  {
    return sslClientAuthPolicy;
  }



  /**
   * Retrieves the set of statistics maintained by this connection handler.
   *
   * @return The set of statistics maintained by this connection handler.
   */
  public LDAPStatistics getStatTracker()
  {
    return statTracker;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeConnectionHandler(LDAPConnectionHandlerCfg config)
      throws ConfigException, InitializationException
  {
    if (friendlyName == null)
    {
      friendlyName = config.dn().getRDN().getAttributeValue(0).toString();
    }

    // Open the selector.
    try
    {
      selector = Selector.open();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_CONNHANDLER_OPEN_SELECTOR_FAILED.get(
          String.valueOf(config.dn()), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }

    // Save this configuration for future reference.
    currentConfig = config;
    enabled = config.isEnabled();
    requestHandlerIndex = 0;
    allowedClients = config.getAllowedClient();
    deniedClients = config.getDeniedClient();

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

    // Save properties that cannot be dynamically modified.
    allowReuseAddress = config.isAllowTCPReuseAddress();
    backlog = config.getAcceptBacklog();
    listenAddresses = config.getListenAddress();
    listenPort = config.getListenPort();
    numRequestHandlers =
        getNumRequestHandlers(config.getNumRequestHandlers(), friendlyName);

    // Construct a unique name for this connection handler, and put
    // together the set of listeners.
    listeners = new LinkedList<HostPort>();
    StringBuilder nameBuffer = new StringBuilder();
    nameBuffer.append(friendlyName);
    for (InetAddress a : listenAddresses)
    {
      listeners.add(new HostPort(a.getHostAddress(), listenPort));
      nameBuffer.append(" ");
      nameBuffer.append(a.getHostAddress());
    }
    nameBuffer.append(" port ");
    nameBuffer.append(listenPort);
    handlerName = nameBuffer.toString();

    // Attempt to bind to the listen port on all configured addresses to
    // verify whether the connection handler will be able to start.
    Message errorMessage =
        checkAnyListenAddressInUse(listenAddresses, listenPort,
            allowReuseAddress, config.dn());
    if (errorMessage != null)
    {
      logError(errorMessage);
      throw new InitializationException(errorMessage);
    }

    // Create a system property to store the LDAP(S) port the server is
    // listening to. This information can be displayed with jinfo.
    System.setProperty(protocol + "_port", String.valueOf(listenPort));

    // Create and start a connection finalizer thread for this
    // connection handler.
    connectionFinalizer = Executors
        .newSingleThreadScheduledExecutor(new DirectoryThread.Factory(
            "LDAP Connection Finalizer for connection handler " + toString()));

    connectionFinalizerActiveJobQueue = new ArrayList<Runnable>();
    connectionFinalizerPendingJobQueue = new ArrayList<Runnable>();

    connectionFinalizer.scheduleWithFixedDelay(
        new ConnectionFinalizerRunnable(), 100, 100, TimeUnit.MILLISECONDS);

    // Create and start the request handlers.
    requestHandlers = new LDAPRequestHandler[numRequestHandlers];
    for (int i = 0; i < numRequestHandlers; i++)
    {
      requestHandlers[i] = new LDAPRequestHandler(this, i);
    }

    for (int i = 0; i < numRequestHandlers; i++)
    {
      requestHandlers[i].start();
    }

    // Register the set of supported LDAP versions.
    DirectoryServer.registerSupportedLDAPVersion(3, this);
    if (config.isAllowLDAPV2())
    {
      DirectoryServer.registerSupportedLDAPVersion(2, this);
    }

    // Create and register monitors.
    statTracker = new LDAPStatistics(handlerName + " Statistics");
    DirectoryServer.registerMonitorProvider(statTracker);

    connMonitor = new ClientConnectionMonitorProvider(this);
    DirectoryServer.registerMonitorProvider(connMonitor);

    // Register this as a change listener.
    config.addLDAPChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(ConnectionHandlerCfg configuration,
      List<Message> unacceptableReasons)
  {
    LDAPConnectionHandlerCfg config = (LDAPConnectionHandlerCfg) configuration;

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
      if (config.isUseSSL() || config.isAllowStartTLS())
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
        if (StaticUtils.isAddressInUse(a, listenPort, allowReuseAddress))
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
        return ERR_CONNHANDLER_CANNOT_BIND.get("LDAP", String
            .valueOf(configEntryDN), a.getHostAddress(), listenPort,
            getExceptionMessage(e));
      }
    }
    return null;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      LDAPConnectionHandlerCfg config, List<Message> unacceptableReasons)
  {
    return isConfigurationAcceptable(config, unacceptableReasons);
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



  /**
   * {@inheritDoc}
   */
  @Override
  public void processServerShutdown(Message reason)
  {
    shutdownRequested = true;

    try
    {
      for (LDAPRequestHandler requestHandler : requestHandlers)
      {
        try
        {
          requestHandler.processServerShutdown(reason);
        }
        catch (Exception ignored)
        {
        }
      }
    }
    catch (Exception ignored)
    {
    }
  }



  /**
   * {@inheritDoc}
   */
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
   * Operates in a loop, accepting new connections and ensuring that requests on
   * those connections are handled properly.
   */
  @Override
  public void run()
  {
    setName(handlerName);
    boolean listening = false;

    while (!shutdownRequested)
    {
      // If this connection handler is not enabled, then just sleep
      // for a bit and check again.
      if (!enabled)
      {
        if (listening)
        {
          cleanUpSelector();
          listening = false;

          logError(NOTE_CONNHANDLER_STOPPED_LISTENING.get(handlerName));
        }

        StaticUtils.sleep(1000);
        continue;
      }

      // If we have gotten here, then we are about to start listening
      // for the first time since startup or since we were previously
      // disabled. Make sure to start with a clean selector and then
      // create all the listeners.
      try
      {
        cleanUpSelector();

        int numRegistered = registerChannels();

        // At this point, the connection Handler either started
        // correctly or failed to start but the start process
        // should be notified and resume its work in any cases.
        synchronized (waitListen)
        {
          waitListen.notify();
        }

        // If none of the listeners were created successfully, then
        // consider the connection handler disabled and require
        // administrative action before trying again.
        if (numRegistered == 0)
        {
          logError(ERR_LDAP_CONNHANDLER_NO_ACCEPTORS.get(String
              .valueOf(currentConfig.dn())));

          enabled = false;
          continue;
        }

        listening = true;

        // Enter a loop, waiting for new connections to arrive and
        // then accepting them as they come in.
        boolean lastIterationFailed = false;
        while (enabled && (!shutdownRequested))
        {
          try
          {
            serveIncomingConnections();

            lastIterationFailed = false;
          }
          catch (Exception e)
          {
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
              Message message = ERR_CONNHANDLER_CONSECUTIVE_ACCEPT_FAILURES
                  .get(friendlyName, String.valueOf(currentConfig.dn()),
                      stackTraceToSingleLineString(e));
              logError(message);

              DirectoryServer.sendAlertNotification(this,
                  ALERT_TYPE_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES,
                  message);

              cleanUpSelector();
              enabled = false;
            }
            else
            {
              lastIterationFailed = true;
            }
          }
        }

        if (shutdownRequested)
        {
          cleanUpSelector();
          selector.close();
          listening = false;
          enabled = false;
        }

      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // This is very bad because we failed outside the loop. The
        // only thing we can do here is log a message, send an alert,
        // and disable the selector until an administrator can figure
        // out what's going on.
        Message message = ERR_LDAP_CONNHANDLER_UNCAUGHT_ERROR
            .get(String.valueOf(currentConfig.dn()),
                stackTraceToSingleLineString(e));
        logError(message);

        DirectoryServer.sendAlertNotification(this,
            ALERT_TYPE_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR, message);

        cleanUpSelector();
        enabled = false;
      }
    }
  }


  /**
   * Serves the incoming connections.
   *
   * @throws IOException
   * @throws DirectoryException
   */
  private void serveIncomingConnections() throws IOException, DirectoryException
  {
    int selectorState = selector.select();

    // We can't rely on return value of select to determine if any keys
    // are ready.
    // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4850373
    for (Iterator<SelectionKey> iterator =
        selector.selectedKeys().iterator(); iterator.hasNext();)
    {
      SelectionKey key = iterator.next();
      iterator.remove();
      if (key.isAcceptable())
      {
        // Accept the new client connection.
        ServerSocketChannel serverChannel = (ServerSocketChannel) key
            .channel();
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null)
        {
          acceptConnection(clientChannel);
        }
      }

      if (selectorState == 0 && enabled && (!shutdownRequested)
          && debugEnabled())
      {
        // Selected keys was non empty but select() returned 0.
        // Log warning and hope it blocks on the next select() call.
        TRACER.debugWarning("Selector.select() returned 0. "
            + "Selected Keys: %d, Interest Ops: %d, Ready Ops: %d ",
            selector.selectedKeys().size(), key.interestOps(),
            key.readyOps());
      }
    }
  }


  /**
   * Open channels for each listen address and register them against this
   * ConnectionHandler's {@link Selector}.
   *
   * @return the number of successfully registered channel
   */
  private int registerChannels()
  {
    int numRegistered = 0;
    for (InetAddress a : listenAddresses)
    {
      try
      {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.socket().setReuseAddress(allowReuseAddress);
        channel.socket()
            .bind(new InetSocketAddress(a, listenPort), backlog);
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_ACCEPT);
        numRegistered++;

        logError(NOTE_CONNHANDLER_STARTED_LISTENING.get(handlerName));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(ERR_LDAP_CONNHANDLER_CREATE_CHANNEL_FAILED.get(
            String.valueOf(currentConfig.dn()), a.getHostAddress(),
            listenPort, stackTraceToSingleLineString(e)));
      }
    }
    return numRegistered;
  }



  private void acceptConnection(SocketChannel clientChannel)
      throws DirectoryException
  {
    try
    {
      clientChannel.socket().setKeepAlive(currentConfig.isUseTCPKeepAlive());
      clientChannel.socket().setTcpNoDelay(currentConfig.isUseTCPNoDelay());
    }
    catch (SocketException se)
    {
      // TCP error occurred because connection reset/closed? In any case,
      // just close it and ignore.
      // See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6378870
      close(clientChannel);
    }

    // Check to see if the core server rejected the
    // connection (e.g., already too many connections
    // established).
    LDAPClientConnection clientConnection = new LDAPClientConnection(this,
        clientChannel, getProtocol());
    if (clientConnection.getConnectionID() < 0)
    {
      clientConnection.disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
          ERR_CONNHANDLER_REJECTED_BY_SERVER.get());
      return;
    }

    InetAddress clientAddr = clientConnection.getRemoteAddress();
    // Check to see if the client is on the denied list.
    // If so, then reject it immediately.
    if ((!deniedClients.isEmpty())
        && AddressMask.maskListContains(clientAddr, deniedClients))
    {
      clientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED,
          currentConfig.isSendRejectionNotice(), ERR_CONNHANDLER_DENIED_CLIENT
              .get(clientConnection.getClientHostPort(), clientConnection
                  .getServerHostPort()));
      return;
    }
    // Check to see if there is an allowed list and if
    // there is whether the client is on that list. If
    // not, then reject the connection.
    if ((!allowedClients.isEmpty())
        && (!AddressMask.maskListContains(clientAddr, allowedClients)))
    {
      clientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED,
          currentConfig.isSendRejectionNotice(),
          ERR_CONNHANDLER_DISALLOWED_CLIENT.get(clientConnection
              .getClientHostPort(), clientConnection.getServerHostPort()));
      return;
    }

    // If we've gotten here, then we'll take the
    // connection so invoke the post-connect plugins and
    // register the client connection with a request
    // handler.
    try
    {
      PluginConfigManager pluginManager = DirectoryServer
          .getPluginConfigManager();
      PluginResult.PostConnect pluginResult = pluginManager
          .invokePostConnectPlugins(clientConnection);
      if (!pluginResult.continueProcessing())
      {
        clientConnection.disconnect(pluginResult.getDisconnectReason(),
            pluginResult.sendDisconnectNotification(),
            pluginResult.getErrorMessage());
        return;
      }

      LDAPRequestHandler requestHandler =
          requestHandlers[requestHandlerIndex++];
      if (requestHandlerIndex >= numRequestHandlers)
      {
        requestHandlerIndex = 0;
      }
      requestHandler.registerClient(clientConnection);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          INFO_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT.get(clientConnection
              .getClientHostPort(), clientConnection.getServerHostPort(),
              getExceptionMessage(e));
      logError(message);

      clientConnection.disconnect(DisconnectReason.SERVER_ERROR,
          currentConfig.isSendRejectionNotice(), message);
    }
  }



  /**
   * Appends a string representation of this connection handler to the provided
   * buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append(handlerName);
  }



  /**
   * Indicates whether this connection handler should use SSL to communicate
   * with clients.
   *
   * @return {@code true} if this connection handler should use SSL to
   *         communicate with clients, or {@code false} if not.
   */
  public boolean useSSL()
  {
    return currentConfig.isUseSSL();
  }



  /**
   * Cleans up the contents of the selector, closing any server socket channels
   * that might be associated with it. Any connections that might have been
   * established through those channels should not be impacted.
   */
  private void cleanUpSelector()
  {
    try
    {
      for (SelectionKey key : selector.keys())
      {
        try
        {
          key.cancel();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        try
        {
          key.channel().close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
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



  /**
   * Get the queueing strategy.
   *
   * @return The queueing strategy.
   */
  public QueueingStrategy getQueueingStrategy()
  {
    return queueingStrategy;
  }



  /**
   * Creates a TLS Byte Channel instance using the specified socket channel.
   *
   * @param channel
   *          The socket channel to use in the creation.
   * @return A TLS Byte Channel instance.
   * @throws DirectoryException
   *           If the channel cannot be created.
   */
  public TLSByteChannel getTLSByteChannel(ByteChannel channel)
      throws DirectoryException
  {
    SSLEngine sslEngine = createSSLEngine(currentConfig, sslContext);
    return new TLSByteChannel(channel, sslEngine);
  }



  private SSLEngine createSSLEngine(LDAPConnectionHandlerCfg config,
      SSLContext sslContext) throws DirectoryException
  {
    try
    {
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setUseClientMode(false);

      final Set<String> protocols = config.getSSLProtocol();
      if (!protocols.isEmpty())
      {
        sslEngine.setEnabledProtocols(protocols.toArray(new String[0]));
      }

      final Set<String> ciphers = config.getSSLCipherSuite();
      if (!ciphers.isEmpty())
      {
        sslEngine.setEnabledCipherSuites(ciphers.toArray(new String[0]));
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
      Message message = ERR_CONNHANDLER_SSL_CANNOT_INITIALIZE
          .get(getExceptionMessage(e));
      throw new DirectoryException(resCode, message, e);
    }
  }



  private SSLContext createSSLContext(LDAPConnectionHandlerCfg config)
      throws DirectoryException
  {
    try
    {
      DN keyMgrDN = config.getKeyManagerProviderDN();
      KeyManagerProvider<?> keyManagerProvider = DirectoryServer
          .getKeyManagerProvider(keyMgrDN);
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
        keyManagers = SelectableCertificateKeyManager.wrap(
            keyManagerProvider.getKeyManagers(), alias);
      }

      DN trustMgrDN = config.getTrustManagerProviderDN();
      TrustManagerProvider<?> trustManagerProvider = DirectoryServer
          .getTrustManagerProvider(trustMgrDN);
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
      Message message = ERR_CONNHANDLER_SSL_CANNOT_INITIALIZE
          .get(getExceptionMessage(e));
      throw new DirectoryException(resCode, message, e);
    }
  }



  /**
   * Enqueue a connection finalizer which will be invoked after a short delay.
   *
   * @param r
   *          The connection finalizer runnable.
   */
  void registerConnectionFinalizer(Runnable r)
  {
    synchronized (connectionFinalizerLock)
    {
      if (connectionFinalizer != null)
      {
        connectionFinalizerPendingJobQueue.add(r);
      }
      else
      {
        // Already finalized - invoked immediately.
        r.run();
      }
    }
  }

}
