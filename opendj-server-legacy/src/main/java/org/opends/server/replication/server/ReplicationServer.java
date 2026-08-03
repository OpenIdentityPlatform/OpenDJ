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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyrighted 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.forgerock.opendj.server.config.server.ReplicationServerCfg;
import org.forgerock.opendj.server.config.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.backends.ChangelogBackend;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.file.ECLEnabledDomainPredicate;
import org.opends.server.replication.server.changelog.file.FileChangelogDB;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;

/**
 * ReplicationServer Listener. This singleton is the main object of the
 * replication server. It waits for the incoming connections and create listener
 * and publisher objects for connection with LDAP servers and with replication
 * servers It is responsible for creating the replication server
 * replicationServerDomain and managing it
 */
public class ReplicationServer
  implements ConfigurationChangeListener<ReplicationServerCfg>
{
  private String serverURL;

  /**
   * Number of attempts to bind the listen port before giving up. The port may be held for a
   * short while by a socket which is being closed, so a few retries make the start-up
   * resilient to such transient conditions.
   */
  private static final int LISTEN_BIND_ATTEMPTS = 5;
  /** Delay between two attempts to bind the listen port. */
  private static final long LISTEN_BIND_RETRY_DELAY_MS = 200;
  /** Timeout of the diagnostic probe performed when the listen port cannot be bound. */
  private static final int LISTEN_PORT_PROBE_TIMEOUT_MS = 200;

  private volatile ServerSocket listenSocket;
  private Thread listenThread;
  private Thread connectThread;

  /** The current configuration of this replication server. */
  private ReplicationServerCfg config;
  private final DSRSShutdownSync dsrsShutdownSync;

  /** This table is used to store the list of dn for which we are currently handling servers. */
  private final Map<DN, ReplicationServerDomain> baseDNs = new HashMap<>();

  /** The database storing the changes. */
  private final ChangelogDB changelogDB;

  /** The backend that allow to search the changes (external changelog). */
  private ChangelogBackend changelogBackend;

  /**
   * Whether this instance registered the virtual attribute rules of the external changelog.
   * They are registered globally, by attribute name, so an instance which did not register
   * them must not deregister them: it would strip them from the instance which did.
   */
  private boolean externalChangelogRegistered;

  private final AtomicBoolean shutdown = new AtomicBoolean();
  private final ReplSessionSecurity replSessionSecurity;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** To know whether a domain is enabled for the external changelog. */
  private final ECLEnabledDomainPredicate domainPredicate;

  /**
   * This is required for unit testing, so that we can keep track of all the
   * replication servers which are running in the VM.
   */
  private static final Set<Integer> localPorts = new CopyOnWriteArraySet<>();

  /**
   * Number of attempts to bind a listen port which failed in this VM.
   * <p>
   * This is required for unit testing: it lets a test which holds a listen port release it
   * as soon as a replication server has actually failed to bind it, instead of after a
   * delay which would make the test vacuous when it is too long, and flaky when it is too
   * short.
   */
  static final AtomicInteger listenPortBindFailures = new AtomicInteger();

  /**
   * Number of listen port changes whose wait for the previous listen thread was interrupted.
   * <p>
   * This is required for unit testing: a wait which is interrupted and a wait which is over
   * before it starts leave this replication server in the same state, so nothing else tells
   * a test that it exercised the interruption instead of passing over it.
   */
  static final AtomicInteger interruptedListenThreadStops = new AtomicInteger();

  /** Monitors for synchronizing domain creation with the connect thread. */
  private final Object domainTicketLock = new Object();
  private final Object connectThreadLock = new Object();
  private long domainTicket;

  /**
   * Holds the list of all replication servers instantiated in this VM.
   * This allows to perform clean up of the RS databases in unit tests.
   */
  private static final List<ReplicationServer> allInstances = new ArrayList<>();

  private final CryptoSuite cryptoSuite;

  /**
   * Creates a new Replication server using the provided configuration entry.
   *
   * @param cfg The configuration of this replication server.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(ReplicationServerCfg cfg) throws ConfigException
  {
    this(cfg, new DSRSShutdownSync(), new ECLEnabledDomainPredicate());
  }

  /**
   * Creates a new Replication server using the provided configuration entry and shutdown
   * synchronization object.
   *
   * @param cfg The configuration of this replication server.
   * @param dsrsShutdownSync Synchronization object for shutdown of combined DS/RS instances.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(ReplicationServerCfg cfg, DSRSShutdownSync dsrsShutdownSync) throws ConfigException
  {
    this(cfg, dsrsShutdownSync, new ECLEnabledDomainPredicate());
  }

  /**
   * Creates a new Replication server using the provided configuration entry, shutdown
   * synchronization object and domain predicate.
   *
   * @param cfg The configuration of this replication server.
   * @param dsrsShutdownSync Synchronization object for shutdown of combined DS/RS instances.
   * @param predicate Indicates whether a domain is enabled for the external changelog.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(final ReplicationServerCfg cfg, final DSRSShutdownSync dsrsShutdownSync,
      final ECLEnabledDomainPredicate predicate) throws ConfigException
  {
    this.config = cfg;
    this.dsrsShutdownSync = dsrsShutdownSync;
    this.domainPredicate = predicate;

    try
    {
      enableExternalChangeLog();
      ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
      cryptoSuite = serverContext.getCryptoManager().
          newCryptoSuite(cfg.getCipherTransformation(), cfg.getCipherKeyLength(), cfg.isConfidentialityEnabled());

      this.changelogDB = new FileChangelogDB(this, config.getReplicationDBDirectory(), cryptoSuite);

      replSessionSecurity = new ReplSessionSecurity();
      initialize();
    }
    catch (ConfigException e)
    {
      // This instance is never returned to the caller, so nothing will ever shut it down:
      // release what the initialization managed to acquire before failing.
      abortInitialization();
      throw e;
    }
    cfg.addChangeListener(this);

    localPorts.add(getReplicationPort());

    // Keep track of this new instance
    allInstances.add(this);
  }

  private Set<HostPort> getConfiguredRSAddresses()
  {
    final Set<HostPort> results = new HashSet<>();
    for (String serverAddress : this.config.getReplicationServer())
    {
      results.add(HostPort.valueOf(serverAddress));
    }
    return results;
  }

  /**
   * Get the list of every replication servers instantiated in the current VM.
   * @return The list of every replication servers instantiated in the current
   * VM.
   */
  public static List<ReplicationServer> getAllInstances()
  {
    return allInstances;
  }

  /**
   * The run method for the Listen thread.
   * This thread accept incoming connections on the replication server
   * ports from other replication servers or from LDAP servers
   * and spawn further thread responsible for handling those connections
   * <p>
   * The socket is the one this thread was created for, not the one this replication server
   * currently listens on: closing it is what stops this thread, and it is then the only
   * thread it stops, even when another one is already listening on another port.
   *
   * @param socket
   *          the bound socket this thread accepts connections on
   */
  void runListen(ServerSocket socket)
  {
    logger.info(NOTE_REPLICATION_SERVER_LISTENING,
        getServerId(),
        socket.getInetAddress().getHostAddress(),
        socket.getLocalPort());

    while (!shutdown.get() && !socket.isClosed())
    {
      // Wait on the replicationServer port.
      // Read incoming messages and create LDAP or ReplicationServer listener
      // and Publisher.
      try
      {
        Session session;
        Socket newSocket = null;
        try
        {
          newSocket = socket.accept();
          newSocket.setTcpNoDelay(true);
          newSocket.setKeepAlive(true);
          int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
          session = replSessionSecurity.createServerSession(newSocket, timeoutMS);
          if (session == null) // Error, go back to accept
          {
            continue;
          }
        }
        catch (Exception e)
        {
          // If problems happen during the SSL handshake, it is necessary
          // to close the socket to free the associated resources.
          if (newSocket != null)
          {
            newSocket.close();
          }
          continue;
        }

        ReplicationMsg msg = session.receive();

        final int queueSize = this.config.getQueueSize();
        final int rcvWindow = this.config.getWindowSize();
        if (msg instanceof ServerStartMsg)
        {
          DataServerHandler dsHandler = new DataServerHandler(
              session, queueSize, this, rcvWindow);
          dsHandler.startFromRemoteDS((ServerStartMsg) msg);
        }
        else if (msg instanceof ReplServerStartMsg)
        {
          ReplicationServerHandler rsHandler = new ReplicationServerHandler(
              session, queueSize, this, rcvWindow);
          rsHandler.startFromRemoteRS((ReplServerStartMsg) msg);
        }
        else
        {
          // We did not recognize the message, close session as what
          // can happen after is undetermined and we do not want the server to
          // be disturbed
          logger.error(ERR_REPLICATION_UNEXPECTED_MESSAGE,
                  session.getRemoteAddress().toString(),
                  (msg == null) ? "(null)" : msg.getClass().getSimpleName());
          session.close();
        }
      }
      catch (Exception e)
      {
        // The socket has probably been closed as part of the
        // shutdown or changing the port number process.
        // Just log debug information and loop.
        // Do not log the message during shutdown.
        logger.traceException(e);
        if (!shutdown.get())
        {
          logger.error(ERR_EXCEPTION_LISTENING, e.getLocalizedMessage());
        }
      }
    }
  }

  /**
   * This method manages the connection with the other replication servers.
   * It periodically checks that this replication server is indeed connected
   * to all the other replication servers and if not attempts to
   * make the connection.
   */
  void runConnect()
  {
    synchronized (connectThreadLock)
    {
      final Map<HostPort, Long> blacklistedHosts = new HashMap<>();
      while (!shutdown.get())
      {
        HostPort localAddress = HostPort.localAddress(getReplicationPort());
        for (ReplicationServerDomain domain : getReplicationServerDomains())
        {
          /*
           * If there are N RSs configured then we will usually be connected to
           * N-1 of them, since one of them is usually this RS. However, we
           * cannot guarantee this since the configuration may not contain this
           * RS.
           */
          final Set<HostPort> connectedRSAddresses =
              getConnectedRSAddresses(domain);
          for (HostPort rsAddress : getConfiguredRSAddresses())
          {
            if (connectedRSAddresses.contains(rsAddress))
            {
              continue; // Skip: already connected.
            }

            // FIXME: this will need changing if we ever support listening on
            // specific addresses.
            if (rsAddress.equals(localAddress))
            {
              continue; // Skip: avoid connecting to self.
            }

            if (blacklistedHosts.getOrDefault(rsAddress, 0L) > domainTicket)
            {
              continue; // Skip: avoid connecting to blacklisted hosts.
            }

            if (!connect(rsAddress, domain.getBaseDN()))
            {
                // Blacklist for a few iterations
                blacklistedHosts.put(rsAddress, domainTicket + 6);
            }
          }
        }

        // Notify any threads waiting with domain tickets after each iteration.
        synchronized (domainTicketLock)
        {
          domainTicket++;
          domainTicketLock.notifyAll();
        }

        // Retry each second.
        final int randomizer = (int) (Math.random() * 100);
        try
        {
          // Releases lock, allows threads to get domain ticket.
          connectThreadLock.wait(1000 + randomizer);
        }
        catch (InterruptedException e)
        {
          // Signaled to shutdown.
          return;
        }
      }
    }
  }

  private Set<HostPort> getConnectedRSAddresses(ReplicationServerDomain domain)
  {
    Set<HostPort> results = new HashSet<>();
    for (ReplicationServerHandler rsHandler : domain.getConnectedRSs().values())
    {
      results.add(HostPort.valueOf(rsHandler.getServerAddressURL()));
    }
    return results;
  }

  /**
   * Establish a connection to the server with the address and port.
   *
   * @param remoteServerAddress
   *          The address and port for the server
   * @param baseDN
   *          The baseDN of the connection
   */
  private boolean connect(HostPort remoteServerAddress, DN baseDN)
  {
    boolean sslEncryption = replSessionSecurity.isSslEncryption();

    if (logger.isTraceEnabled())
    {
      logger.trace("RS " + getMonitorInstanceName() + " connects to "
          + remoteServerAddress);
    }

    Socket socket = new Socket();
    Session session = null;
    try
    {
      socket.setReuseAddress(true);
      socket.setTcpNoDelay(true);
      if (config.getSourceAddress() != null)
      {
        InetSocketAddress local = new InetSocketAddress(config.getSourceAddress(), 0);
        socket.bind(local);
      }
      int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
      socket.connect(remoteServerAddress.toInetSocketAddress(), timeoutMS);
      if (isSelfConnection(socket))
      {
        // While the remote RS is down, the kernel may pick its port as the
        // local port of this connecting socket (TCP simultaneous open),
        // "connecting" it to itself. Keeping such a socket open would hold
        // the port and prevent the RS from binding it on restart.
        throw new ConnectException("Connection to " + remoteServerAddress
            + " is a TCP self-connect, no replication server is listening");
      }
      session = replSessionSecurity.createClientSession(socket, timeoutMS);

      ReplicationServerHandler rsHandler = new ReplicationServerHandler(
          session, config.getQueueSize(), this, config.getWindowSize());
      rsHandler.connect(baseDN, sslEncryption);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      close(session);
      close(socket);
      return false;
    }
    return true;
  }

  /**
   * Initialization function for the replicationServer.
   *
   * @throws ConfigException
   *           when the replication server cannot be started, in particular when its changelog
   *           cannot be read or when its listen port cannot be bound.
   */
  private void initialize() throws ConfigException
  {
    shutdown.set(false);

    try
    {
      // Assigned before the changelog is opened: the monitor instance name of the domains it
      // restores, and of their changelogs, embeds it, and a provider registered under a name
      // which later changes can never be deregistered again.
      setServerURL();

      this.changelogDB.initializeDB();

      // Assigned before the threads are created, so that a failure below still releases it.
      listenSocket = bindListenPort(getReplicationPort());

      // creates working threads: we must first connect, then start to listen.
      if (logger.isTraceEnabled())
      {
        logger.trace("RS " + getMonitorInstanceName() + " creates connect thread");
      }
      connectThread = new ReplicationServerConnectThread(this);
      connectThread.start();

      if (logger.isTraceEnabled())
      {
        logger.trace("RS " + getMonitorInstanceName() + " creates listen thread");
      }

      startListenThread(listenSocket);

      if (logger.isTraceEnabled())
      {
        logger.trace("RS " + getMonitorInstanceName() + " successfully initialized");
      }
    } catch (ChangelogException e)
    {
      // A replication server which cannot read its changelog is as dead as one which cannot
      // bind its listen port (issue #802). The message already names the changelog directory.
      logger.traceException(e);
      throw new ConfigException(e.getMessageObject(), e);
    } catch (UnknownHostException e)
    {
      // Not logged here: the caller reports the ConfigException, logging it once.
      logger.traceException(e);
      throw new ConfigException(ERR_UNKNOWN_HOSTNAME.get(), e);
    } catch (IOException e)
    {
      // Every consumer would otherwise only learn about it as a "connection refused"
      // somewhere else (issue #792).
      logger.traceException(e);
      throw new ConfigException(bindFailureMessage(getReplicationPort(), e), e);
    }
  }

  /**
   * Binds the given port, retrying a few times when it is momentarily unavailable.
   *
   * @param port
   *          the port to bind
   * @return the bound listen socket
   * @throws IOException
   *           if the port could not be bound within {@link #LISTEN_BIND_ATTEMPTS} attempts
   */
  private ServerSocket bindListenPort(int port) throws IOException
  {
    for (int attempt = 1; ; attempt++)
    {
      final ServerSocket socket = new ServerSocket();
      try
      {
        socket.bind(new InetSocketAddress(port));
        if (attempt > 1)
        {
          logger.info(NOTE_BOUND_CHANGELOG_AFTER_RETRY, port, attempt);
        }
        return socket;
      }
      catch (IOException e)
      {
        close(socket);
        listenPortBindFailures.incrementAndGet();
        if (attempt >= LISTEN_BIND_ATTEMPTS)
        {
          throw e;
        }
        // The port is probed only when giving up: probing it on every attempt would cost a
        // connection to whoever holds it, and delay the next attempt for nothing.
        logger.warn(WARN_RETRYING_BIND_CHANGELOG, port, getExceptionMessage(e), LISTEN_BIND_RETRY_DELAY_MS);
        try
        {
          Thread.sleep(LISTEN_BIND_RETRY_DELAY_MS);
        }
        catch (InterruptedException e2)
        {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  /**
   * Stops the listen thread of the provided listen socket and releases that port.
   * <p>
   * Both are the ones the thread was started on rather than the current ones, so this stops
   * that thread only, even when another one is already listening on another port.
   *
   * @param socket
   *          the listen socket to close, which is what stops its thread
   * @param thread
   *          the listen thread of that socket, {@code null} when it was never started
   * @throws InterruptedException
   *           if this thread is interrupted while waiting for the listen thread to stop
   */
  private void stopListenThread(ServerSocket socket, Thread thread) throws InterruptedException
  {
    close(socket);
    if (thread != null)
    {
      thread.join();
    }
  }

  /**
   * Starts a listen thread on the provided listen socket.
   *
   * @param boundListenSocket
   *          the bound socket the listen thread will accept connections on
   */
  private void startListenThread(ServerSocket boundListenSocket)
  {
    listenSocket = boundListenSocket;
    listenThread = new ReplicationServerListenThread(this, boundListenSocket);
    listenThread.start();
  }

  /**
   * Switches the listen port to the one of the provided configuration.
   * <p>
   * The new port is bound, and its listen thread started, while the current one is still
   * open and serving. A failure therefore leaves this replication server listening on its
   * current port, with its current configuration: there is nothing to roll back, and there
   * is no window during which this replication server listens on no port at all.
   * <p>
   * The trade is a window during which both ports accept, so a peer which connects to the
   * previous port just before it is released gets a session which outlives the change. That
   * is the deliberate inverse of a window during which nothing listens at all.
   *
   * @param newConfig
   *          the configuration being applied, whose listen port differs from the current one
   * @param ccr
   *          the result of the configuration change, to which a failure is added
   * @return {@code true} when this replication server listens on the new port, in which
   *         case {@code newConfig} has become its configuration
   */
  private boolean switchListenPort(ReplicationServerCfg newConfig, ConfigChangeResult ccr)
  {
    final ReplicationServerCfg previousConfig = this.config;
    final String previousServerURL = serverURL;
    final ServerSocket previousListenSocket = listenSocket;
    final Thread previousListenThread = listenThread;
    final int newPort = newConfig.getReplicationPort();
    ServerSocket newListenSocket = null;
    try
    {
      // The current listen socket is still open and serving while the new port is bound.
      newListenSocket = bindListenPort(newPort);

      this.config = newConfig;
      setServerURL();

      // The new port is served before the current one is released, so that this replication
      // server is never left with no listener at all, whatever happens next.
      startListenThread(newListenSocket);
      newListenSocket = null;
      try
      {
        stopListenThread(previousListenSocket, previousListenThread);
      }
      catch (InterruptedException e)
      {
        // The previous port is already released and its thread stops on its own as soon as
        // it wakes up on its closed socket: only the wait for it was cut short.
        interruptedListenThreadStops.incrementAndGet();
        Thread.currentThread().interrupt();
        logger.traceException(e);
      }

      localPorts.remove(previousConfig.getReplicationPort());
      localPorts.add(newPort);
      return true;
    }
    catch (UnknownHostException e)
    {
      logger.traceException(e);
      ccr.setResultCode(ResultCode.OPERATIONS_ERROR);
      ccr.addMessage(ERR_UNKNOWN_HOSTNAME.get());
    }
    catch (IOException e)
    {
      // The new port could not be bound, the current listen socket was left untouched.
      logger.traceException(e);
      ccr.setResultCode(ResultCode.OPERATIONS_ERROR);
      ccr.addMessage(bindFailureMessage(newPort, e));
    }
    // The failure is reported through the ConfigChangeResult, which the configuration
    // handler logs: nothing of the new configuration was applied.
    this.config = previousConfig;
    serverURL = previousServerURL;
    close(newListenSocket);
    return false;
  }

  /**
   * Returns the message of a failed bind of the listen port, i.e. the error itself plus a
   * best effort diagnostic of what holds the port.
   *
   * @param port
   *          the port which could not be bound
   * @param cause
   *          the error returned by the failed bind
   * @return the message describing the failure
   */
  private static LocalizableMessage bindFailureMessage(int port, IOException cause)
  {
    return new LocalizableMessageBuilder(ERR_COULD_NOT_BIND_CHANGELOG.get(port, getExceptionMessage(cause)))
        .append(" ").append(describeListenPortHolder(port)).toMessage();
  }

  /**
   * Returns a best effort diagnostic of what holds the given port.
   * <p>
   * The port is probed over the loopback interface only, so the diagnostic reports what was
   * observed there and nothing more: a socket bound to another address holds the port
   * without ever accepting a loopback connection, and cannot be told apart from a socket
   * which does not accept connections at all, e.g. a client socket which was given that
   * port as its local port.
   *
   * @param port
   *          the port which could not be bound
   * @return the message describing what was observed on the loopback interface
   */
  private static LocalizableMessage describeListenPortHolder(int port)
  {
    final String probedAddress = InetAddress.getLoopbackAddress().getHostAddress() + ":" + port;
    try (Socket probe = new Socket())
    {
      // Without SO_REUSEADDR a probe which self-connects would, once closed, hold the port
      // in TIME_WAIT and defeat the very bind it is diagnosing.
      probe.setReuseAddress(true);
      probe.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), LISTEN_PORT_PROBE_TIMEOUT_MS);
      if (isSelfConnection(probe))
      {
        // The kernel can only give the probed port to the probe as its local port while
        // that port is free: whoever held it released it in the meantime.
        return ERR_COULD_NOT_BIND_CHANGELOG_PORT_FREE.get(probedAddress);
      }
      return ERR_COULD_NOT_BIND_CHANGELOG_LISTENING.get(probedAddress);
    }
    catch (IOException e)
    {
      logger.traceException(e);
      return ERR_COULD_NOT_BIND_CHANGELOG_NOT_ACCEPTING.get(probedAddress);
    }
  }

  /**
   * Releases what the initialization acquired before it failed.
   * <p>
   * It runs on a partially constructed instance, so every field it uses may still be
   * unassigned.
   */
  private void abortInitialization()
  {
    shutdown.set(true);
    if (connectThread != null)
    {
      connectThread.interrupt();
    }
    close(listenSocket);
    if (listenThread != null)
    {
      listenThread.interrupt();
    }
    // Opening the changelog restores one domain per domain it holds, and each of them starts
    // its threads and registers its monitor provider: a failure after that point, such as a
    // listen port which cannot be bound, would otherwise leave them behind. Shut them down
    // before the changelog they write to, and one failure at a time: the changelog this one
    // is built on is known to be broken, and what follows still has to run.
    for (ReplicationServerDomain domain : getReplicationServerDomains())
    {
      try
      {
        domain.shutdown();
      }
      catch (RuntimeException ignored)
      {
        logger.traceException(ignored);
      }
    }
    shutdownExternalChangelog();
    if (this.changelogDB != null)
    {
      try
      {
        this.changelogDB.shutdownDB();
      }
      catch (ChangelogException ignored)
      {
        logger.traceException(ignored);
      }
    }
  }

  /**
   * Indicates whether this replication server has bound its listen port, i.e. whether it
   * can accept connections from directory servers and from other replication servers.
   *
   * @return {@code true} if this replication server is listening
   */
  public boolean isListening()
  {
    final ServerSocket socket = listenSocket;
    return socket != null && socket.isBound() && !socket.isClosed();
  }

  /**
   * Enable the external changelog if it is not already enabled.
   * <p>
   * The external changelog is provided by the changelog backend.
   *
   * @throws ConfigException
   *            If an error occurs.
   */
  private void enableExternalChangeLog() throws ConfigException
  {
    BackendConfigManager backendConfigManager =
        DirectoryServer.getInstance().getServerContext().getBackendConfigManager();
    if (backendConfigManager.hasLocalBackend(ChangelogBackend.BACKEND_ID))
    {
      // Backend has already been created and initialized
      // This can occurs in tests
      return;
    }
    try
    {
      changelogBackend = new ChangelogBackend(this, domainPredicate);
      changelogBackend.openBackend();
      try
      {
        backendConfigManager.registerLocalBackend(changelogBackend);
      }
      catch (Exception e)
      {
        logger.error(WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND.get(changelogBackend.getBackendID(),
            getExceptionMessage(e)));
      }

      // Set before the rules are registered, so that a partial registration is released
      // too: this instance is then the one which registered whatever is registered.
      externalChangelogRegistered = true;
      registerVirtualAttributeRules();
    }
    catch (Exception e)
    {
      // TODO : I18N with correct message + what kind of exception should we really throw ?
      // (Directory/Initialization/Config Exception)
      throw new ConfigException(LocalizableMessage.raw("Error when enabling external changelog"), e);
    }
  }

  private void shutdownExternalChangelog()
  {
    if (changelogBackend != null)
    {
      BackendConfigManager backendConfigManager =
          DirectoryServer.getInstance().getServerContext().getBackendConfigManager();
      backendConfigManager.deregisterLocalBackend(changelogBackend);
      changelogBackend.finalizeBackend();
      changelogBackend = null;
    }
    if (externalChangelogRegistered)
    {
      // Virtual attribute rules are registered globally, by attribute name: deregistering
      // rules which this instance did not register would strip them from the instance
      // which did, e.g. when this one took the early return of enableExternalChangeLog().
      externalChangelogRegistered = false;
      deregisterVirtualAttributeRules();
    }
  }

  private List<VirtualAttributeRule> getVirtualAttributesRules() throws DirectoryException
  {
    final List<VirtualAttributeRule> rules = new ArrayList<>();
    rules.add(buildVirtualAttributeRule("lastexternalchangelogcookie", new LastCookieVirtualProvider(this)));
    rules.add(buildVirtualAttributeRule("firstchangenumber", new FirstChangeNumberVirtualAttributeProvider(this)));
    rules.add(buildVirtualAttributeRule("lastchangenumber", new LastChangeNumberVirtualAttributeProvider(this)));
    rules.add(buildVirtualAttributeRule("changelog", new ChangelogBaseDNVirtualAttributeProvider()));
    return rules;
  }

  private void registerVirtualAttributeRules() throws DirectoryException {
    for (VirtualAttributeRule rule : getVirtualAttributesRules())
    {
      DirectoryServer.registerVirtualAttribute(rule);
    }
  }

  private void deregisterVirtualAttributeRules()
  {
    try
    {
      for (VirtualAttributeRule rule : getVirtualAttributesRules())
      {
        DirectoryServer.deregisterVirtualAttribute(rule);
      }
    }
    catch (DirectoryException e)
    {
      // Should never happen
      throw new RuntimeException(e);
    }
  }

  private static VirtualAttributeRule buildVirtualAttributeRule(String attrName,
      VirtualAttributeProvider<UserDefinedVirtualAttributeCfg> provider)
      throws DirectoryException
  {
    ConflictBehavior conflictBehavior = ConflictBehavior.VIRTUAL_OVERRIDES_REAL;

    try
    {
      Set<DN> baseDNs = Collections.singleton(DN.valueOf(""));
      Set<DN> groupDNs = Collections.emptySet();
      Set<SearchFilter> filters = Collections.singleton(SearchFilter.objectClassPresent());

      // To avoid the configuration in cn=config just
      // create a rule and register it into the DirectoryServer
      provider.initializeVirtualAttributeProvider(null);

      Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
      AttributeType attributeType = schema.getAttributeType(attrName);
      return new VirtualAttributeRule(attributeType, provider,
            baseDNs, SearchScope.BASE_OBJECT,
            groupDNs, filters, conflictBehavior);
    }
    catch (Exception e)
    {
      LocalizableMessage message =
        NOTE_ERR_UNABLE_TO_ENABLE_ECL_VIRTUAL_ATTR.get(attrName, e);
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR, message, e);
    }
  }

  /**
   * Get the ReplicationServerDomain associated to the base DN given in
   * parameter.
   *
   * @param baseDN
   *          The base DN for which the ReplicationServerDomain must be
   *          returned.
   * @return The ReplicationServerDomain associated to the base DN given in
   *         parameter.
   */
  public ReplicationServerDomain getReplicationServerDomain(DN baseDN)
  {
    return getReplicationServerDomain(baseDN, false);
  }

  /** Returns the replicated domain DNs minus the provided set of excluded DNs. */
  private Set<DN> getDomainDNs(Set<DN> excludedBaseDNs) throws DirectoryException
  {
    Set<DN> domains = null;
    synchronized (baseDNs)
    {
      domains = new HashSet<>(baseDNs.keySet());
    }
    domains.removeAll(excludedBaseDNs);
    return domains;
  }

  /**
   * Validate that provided cookie is coherent with this replication server,
   * when ignoring the provided set of DNs.
   * <p>
   * The cookie is coherent if and only if it exactly has the set of DNs corresponding to
   * the replication domains, and the states in the cookie are not older than oldest states
   * in the server.
   *
   * @param cookie
   *            The multi domain state (cookie) to validate.
   * @param ignoredBaseDNs
   *            The set of DNs to ignore when validating
   * @throws DirectoryException
   *            If the cookie is not valid
   */
  public void validateCookie(MultiDomainServerState cookie, Set<DN> ignoredBaseDNs) throws DirectoryException
  {
    final Set<DN> activeDomains = getDNsOfActiveDomainsInServer(ignoredBaseDNs);
    final Set<DN> cookieDomains = getDNsOfCookie(cookie);

    checkNoUnknownDomainIsProvidedInCookie(cookie, activeDomains, cookieDomains);
    checkCookieIsNotOutdated(cookie, activeDomains);
  }

  private Set<DN> getDNsOfCookie(MultiDomainServerState cookie)
  {
    final Set<DN> cookieDomains = new HashSet<>();
    for (final DN dn : cookie)
    {
      cookieDomains.add(dn);
    }
    return cookieDomains;
  }

  private Set<DN> getDNsOfActiveDomainsInServer(final Set<DN> ignoredBaseDNs) throws DirectoryException
  {
    final Set<DN> activeDomains = new HashSet<>();
    for (final DN dn : getDomainDNs(ignoredBaseDNs))
    {
      final ServerState lastServerState = getReplicationServerDomain(dn).getLatestServerState();
      if (!lastServerState.isEmpty())
      {
         activeDomains.add(dn);
      }
    }
    return activeDomains;
  }

  private void checkNoUnknownDomainIsProvidedInCookie(final MultiDomainServerState cookie, final Set<DN> activeDomains,
      final Set<DN> cookieDomains) throws DirectoryException
  {
    if (!activeDomains.containsAll(cookieDomains))
    {
      final Set<DN> unknownCookieDomains = new HashSet<>(cookieDomains);
      unknownCookieDomains.removeAll(activeDomains);
      final StringBuilder currentStartingCookie = new StringBuilder();
      for (DN domainDN : activeDomains) {
        currentStartingCookie.append(domainDN).append(":").append(cookie.getServerState(domainDN)).append(";");
      }
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_RESYNC_REQUIRED_UNKNOWN_DOMAIN_IN_PROVIDED_COOKIE.get(
              unknownCookieDomains.toString(), currentStartingCookie));
    }
  }

  private void checkCookieIsNotOutdated(final MultiDomainServerState cookie, final Set<DN> activeDomains)
      throws DirectoryException
  {
    for (DN dn : activeDomains)
    {
      if (isCookieOutdatedForDomain(cookie, dn))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_RESYNC_REQUIRED_TOO_OLD_DOMAIN_IN_PROVIDED_COOKIE.get(dn.toString()));
      }
    }
  }

  /** Check that provided cookie is not outdated compared to the oldest state of a domain. */
  private boolean isCookieOutdatedForDomain(MultiDomainServerState cookie, DN domainDN)
  {
    final ServerState providedState = cookie.getServerState(domainDN);
    if (providedState == null)
    {
      // missing domains do not invalidate a cookie.
      // results will include all the changes of the missing domains
      return false;
    }
    final ServerState domainOldestState = getReplicationServerDomain(domainDN).getOldestState();
    for (final CSN oldestCsn : domainOldestState)
    {
      final CSN providedCsn = providedState.getCSN(oldestCsn.getServerId());
      if (providedCsn != null && providedCsn.isOlderThan(oldestCsn))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the ReplicationServerDomain associated to the base DN given in
   * parameter.
   *
   * @param baseDN The base DN for which the ReplicationServerDomain must be
   * returned.
   * @param create Specifies whether to create the ReplicationServerDomain if
   *        it does not already exist.
   * @return The ReplicationServerDomain associated to the base DN given in
   *         parameter.
   */
  public ReplicationServerDomain getReplicationServerDomain(DN baseDN,
      boolean create)
  {
    synchronized (baseDNs)
    {
      ReplicationServerDomain domain = baseDNs.get(baseDN);
      if (domain == null && create) {
        domain = new ReplicationServerDomain(baseDN, this).start();
        baseDNs.put(baseDN, domain);
      }
      return domain;
    }
  }

  /** Waits for connections to this ReplicationServer. */
  void waitConnections()
  {
    // Acquire a domain ticket and wait for a complete cycle of the connect
    // thread.
    final long myDomainTicket;
    synchronized (connectThreadLock)
    {
      // Connect thread must be waiting.
      synchronized (domainTicketLock)
      {
        // Determine the ticket which will be used in the next connect thread
        // iteration.
        myDomainTicket = domainTicket + 1;
      }

      // Wake up connect thread.
      connectThreadLock.notifyAll();
    }

    // Wait until the connect thread has processed next connect phase.
    synchronized (domainTicketLock)
    {
      while (myDomainTicket > domainTicket && !shutdown.get())
      {
        try
        {
          // Wait with timeout so that we detect shutdown.
          domainTicketLock.wait(500);
        }
        catch (InterruptedException e)
        {
          // Can't do anything with this.
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  /** Shutdown the Replication Server service and all its connections. */
  public void shutdown()
  {
    localPorts.remove(getReplicationPort());

    if (!shutdown.compareAndSet(false, true))
    {
      return;
    }

    // shutdown the connect thread
    if (connectThread != null)
    {
      connectThread.interrupt();
    }

    // shutdown the listener thread
    close(listenSocket);
    if (listenThread != null)
    {
      listenThread.interrupt();
    }

    // shutdown all the replication domains
    for (ReplicationServerDomain domain : getReplicationServerDomains())
    {
      domain.shutdown();
    }

    shutdownExternalChangelog();

    try
    {
      this.changelogDB.shutdownDB();
    }
    catch (ChangelogException ignored)
    {
      logger.traceException(ignored);
    }

    // Remove this instance from the global instance list
    allInstances.remove(this);
  }

  /**
   * Retrieves the time after which changes must be deleted from the
   * persistent storage (in milliseconds).
   *
   * @return  The time after which changes must be deleted from the
   *          persistent storage (in milliseconds).
   */
  public long getPurgeDelay()
  {
    return this.config.getReplicationPurgeDelay() * 1000;
  }

  /**
   * Check if the provided configuration is acceptable for add.
   *
   * @param configuration The configuration to check.
   * @param unacceptableReasons When the configuration is not acceptable, this
   *                            table is use to return the reasons why this
   *                            configuration is not acceptable.
   *
   * @return true if the configuration is acceptable, false other wise.
   */
  public static boolean isConfigurationAcceptable(
      ReplicationServerCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    int port = configuration.getReplicationPort();

    try (ServerSocket tmpSocket = new ServerSocket())
    {
      tmpSocket.bind(new InetSocketAddress(port));
      return true;
    }
    catch (Exception e)
    {
      unacceptableReasons.add(ERR_COULD_NOT_BIND_CHANGELOG.get(port, getExceptionMessage(e)));
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      ReplicationServerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    final Set<HostPort> oldRSAddresses = getConfiguredRSAddresses();
    final ReplicationServerCfg oldConfig = this.config;

    // Changing the listen port requires to stop the listen thread and restart it. It is
    // done first, and the new port is bound before the current one is released, so that a
    // change which cannot be applied leaves this replication server as it was, instead of
    // half configured and, worse, without any listener.
    if (configuration.getReplicationPort() != oldConfig.getReplicationPort()
        && !switchListenPort(configuration, ccr))
    {
      return ccr;
    }

    // Some of those properties change don't need specific code.
    // They will be applied for next connections. Some others have immediate effect
    this.config = configuration;

    disconnectRemovedReplicationServers(oldRSAddresses);

    final long newPurgeDelay = config.getReplicationPurgeDelay();
    if (newPurgeDelay != oldConfig.getReplicationPurgeDelay())
    {
      this.changelogDB.setPurgeDelay(getPurgeDelay());
    }
    final boolean computeCN = config.isComputeChangeNumber();
    if (computeCN != oldConfig.isComputeChangeNumber())
    {
      try
      {
        this.changelogDB.setComputeChangeNumber(computeCN);
      }
      catch (ChangelogException e)
      {
        logger.traceException(e);
        ccr.setResultCode(ResultCode.OPERATIONS_ERROR);
      }
    }

    cryptoSuite.newParameters(config.getCipherTransformation(), config.getCipherKeyLength(),
        config.isConfidentialityEnabled());

    // Update period value for monitoring publishers
    if (oldConfig.getMonitoringPeriod() != config.getMonitoringPeriod())
    {
      for (ReplicationServerDomain domain : getReplicationServerDomains())
      {
        domain.updateMonitoringPeriod(config.getMonitoringPeriod());
      }
    }

    // Changed the group id ?
    if (config.getGroupId() != oldConfig.getGroupId())
    {
      // Have a new group id: Disconnect every servers.
      for (ReplicationServerDomain domain : getReplicationServerDomains())
      {
        domain.stopAllServers(true);
      }
    }

    // Set a potential new weight
    if (oldConfig.getWeight() != config.getWeight())
    {
      // Broadcast the new weight the the whole topology. This will make some
      // DSs reconnect (if needed) to other RSs according to the new weight of
      // this RS.
      broadcastConfigChange();
    }

    final String newDir = config.getReplicationDBDirectory();
    if (newDir != null && !newDir.equals(oldConfig.getReplicationDBDirectory()))
    {
      ccr.setAdminActionRequired(true);
    }
    return ccr;
  }

  /**
   * Try and set a sensible URL for this replication server. Since we are
   * listening on all addresses there are a couple of potential candidates:
   * <ol>
   * <li>a matching server URL in the replication server's configuration,</li>
   * <li>hostname local address.</li>
   * </ol>
   */
  private void setServerURL() throws UnknownHostException
  {
    /*
     * First try the set of configured replication servers to see if one of them
     * is this replication server (this should always be the case).
     */
    for (HostPort rsAddress : getConfiguredRSAddresses())
    {
      /* No need validate the string format because the admin framework has already done it. */
      if (rsAddress.getPort() == getReplicationPort()
          && rsAddress.isLocalAddress())
      {
        serverURL = rsAddress.toString();
        return;
      }
    }

    // Fall-back to the machine hostname.
    final String host = InetAddress.getLocalHost().getHostName();
    // Ensure correct formatting of IPv6 addresses by using a HostPort instance.
    serverURL = new HostPort(host, getReplicationPort()).toString();
  }

  /**
   * Broadcast a configuration change that just happened to the whole topology
   * by sending a TopologyMsg to every entity in the topology.
   */
  private void broadcastConfigChange()
  {
    for (ReplicationServerDomain domain : getReplicationServerDomains())
    {
      domain.sendTopoInfoToAll();
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      ReplicationServerCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.getReplicationPort() == getReplicationPort())
    {
      return true;
    }
    // The change is persisted before it is applied, so rejecting a port which cannot be
    // bound is the only way to keep the configuration and the listen port in sync.
    return isConfigurationAcceptable(configuration, unacceptableReasons);
  }

  /**
   * Get the value of generationId for the replication replicationServerDomain
   * associated with the provided baseDN.
   *
   * @param baseDN The baseDN of the replicationServerDomain.
   * @return The value of the generationID.
   */
  public long getGenerationId(DN baseDN)
  {
    final ReplicationServerDomain rsd = getReplicationServerDomain(baseDN);
    return rsd != null ? rsd.getGenerationId() : -1;
  }

  /**
   * Get the serverId for this replication server.
   *
   * @return The value of the serverId.
   */
  public int getServerId()
  {
    return this.config.getReplicationServerId();
  }

  /**
   * Do what needed when the config object related to this replication server
   * is deleted from the server configuration.
   */
  public void remove()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("RS " + getMonitorInstanceName() + " starts removing");
    }
    shutdown();
  }

  /**
   * Returns an iterator on the list of replicationServerDomain.
   * Returns null if none.
   * @return the iterator.
   */
  public Iterator<ReplicationServerDomain> getDomainIterator()
  {
    return getReplicationServerDomains().iterator();
  }

  /**
   * Get the assured mode timeout.
   * <p>
   * It is the Timeout (in milliseconds) when waiting for acknowledgments.
   *
   * @return The assured mode timeout.
   */
  public long getAssuredTimeout()
  {
    return this.config.getAssuredTimeout();
  }

  /**
   * Get The replication server group id.
   * @return The replication server group id.
   */
  public byte getGroupId()
  {
    return (byte) this.config.getGroupId();
  }

  /**
   * Get the degraded status threshold value for status analyzer.
   * <p>
   * The degraded status threshold is the number of pending changes for a DS,
   * considered as threshold value to put the DS in DEGRADED_STATUS. If value is
   * 0, status analyzer is disabled.
   *
   * @return The degraded status threshold value for status analyzer.
   */
  public int getDegradedStatusThreshold()
  {
    return this.config.getDegradedStatusThreshold();
  }

  /**
   * Get the monitoring publisher period value.
   * <p>
   * It is the number of milliseconds to wait before sending new monitoring
   * messages. If value is 0, monitoring publisher is disabled.
   *
   * @return the monitoring publisher period value.
   */
  public long getMonitoringPublisherPeriod()
  {
    return this.config.getMonitoringPeriod();
  }

  /**
   * Compute the list of replication servers that are not any more connected to
   * this Replication Server and stop the corresponding handlers.
   *
   * @param oldRSAddresses
   *          the old list of configured replication servers addresses.
   */
  private void disconnectRemovedReplicationServers(Set<HostPort> oldRSAddresses)
  {
    final Collection<HostPort> serversToDisconnect = new ArrayList<>();

    final Set<HostPort> newRSAddresses = getConfiguredRSAddresses();
    for (HostPort oldRSAddress : oldRSAddresses)
    {
      if (!newRSAddresses.contains(oldRSAddress))
      {
        serversToDisconnect.add(oldRSAddress);
      }
    }

    if (serversToDisconnect.isEmpty())
    {
      return;
    }

    for (ReplicationServerDomain domain: getReplicationServerDomains())
    {
      domain.stopReplicationServers(serversToDisconnect);
    }
  }

  /**
   * Retrieves a printable name for this Replication Server Instance.
   *
   * @return A printable name for this Replication Server Instance.
   */
  public String getMonitorInstanceName()
  {
    return "Replication Server " + getReplicationPort() + " " + getServerId();
  }

  /**
   * Retrieves the port used by this ReplicationServer.
   *
   * @return The port used by this ReplicationServer.
   */
  public int getReplicationPort()
  {
    return config.getReplicationPort();
  }

  /**
   * Getter on the server URL.
   * @return the server URL.
   */
  public String getServerURL()
  {
    return this.serverURL;
  }

  /**
   * WARNING : only use this methods for tests purpose.
   *
   * Add the Replication Server given as a parameter in the list
   * of local replication servers.
   *
   * @param server The server to be added.
   */
  public static void onlyForTestsAddlocalReplicationServer(String server)
  {
    localPorts.add(HostPort.valueOf(server).getPort());
  }

  /**
   * WARNING : only use this methods for tests purpose.
   *
   * Clear the list of local Replication Servers
   */
  public static void onlyForTestsClearLocalReplicationServerList()
  {
    localPorts.clear();
  }

  /**
   * Returns {@code true} if the provided port is one of the ports that this
   * replication server is listening on.
   *
   * @param port
   *          The port to be checked.
   * @return {@code true} if the provided port is one of the ports that this
   *         replication server is listening on.
   */
  public static boolean isLocalReplicationServerPort(int port)
  {
    return localPorts.contains(port);
  }

  /**
   * Get (or create) a handler on the {@link ChangeNumberIndexDB} for external
   * changelog.
   *
   * @return the handler.
   */
  ChangeNumberIndexDB getChangeNumberIndexDB()
  {
    return this.changelogDB.getChangeNumberIndexDB();
  }

  /**
   * Returns the oldest change number in the change number index DB.
   *
   * @return the oldest change number in the change number index DB
   * @throws DirectoryException
   *           When a problem happens
   */
  public long getOldestChangeNumber() throws DirectoryException
  {
    try
    {
      final ChangeNumberIndexDB cnIndexDB = getChangeNumberIndexDB();
      final ChangeNumberIndexRecord oldestRecord = cnIndexDB.getOldestRecord();
      if (oldestRecord != null)
      {
        return oldestRecord.getChangeNumber();
      }
      // database is empty
      return cnIndexDB.getLastGeneratedChangeNumber();
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR, e);
    }
  }

  /**
   * Returns the newest change number in the change number index DB.
   *
   * @return the newest change number in the change number index DB
   * @throws DirectoryException
   *           When a problem happens
   */
  public long getNewestChangeNumber() throws DirectoryException
  {
    try
    {
      final ChangeNumberIndexDB cnIndexDB = getChangeNumberIndexDB();
      final ChangeNumberIndexRecord newestRecord = cnIndexDB.getNewestRecord();
      if (newestRecord != null)
      {
        return newestRecord.getChangeNumber();
      }
      // database is empty
      return cnIndexDB.getLastGeneratedChangeNumber();
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR, e);
    }
  }

  /**
   * Returns the newest cookie value.
   *
   * @param excludedBaseDNs
   *          The set of baseDNs excluded from ECL.
   * @return the newest cookie value.
   */
  public MultiDomainServerState getNewestECLCookie(Set<DN> excludedBaseDNs)
  {
    // Initialize start state for all running domains with empty state
    final MultiDomainServerState result = new MultiDomainServerState();
    for (ReplicationServerDomain rsDomain : getReplicationServerDomains())
    {
      if (!excludedBaseDNs.contains(rsDomain.getBaseDN()))
      {
        final ServerState latestDBServerState = rsDomain.getLatestServerState();
        if (!latestDBServerState.isEmpty())
        {
          result.replace(rsDomain.getBaseDN(), latestDBServerState);
        }
      }
    }
    return result;
  }

  /**
   * Gets the weight affected to the replication server.
   * <p>
   * Each replication server of the topology has a weight. When combined
   * together, the weights of the replication servers of a same group can be
   * translated to a percentage that determines the quantity of directory
   * servers of the topology that should be connected to a replication server.
   * <p>
   * For instance imagine a topology with 3 replication servers (with the same
   * group id) with the following weights: RS1=1, RS2=1, RS3=2. This means that
   * RS1 should have 25% of the directory servers connected in the topology, RS2
   * 25%, and RS3 50%. This may be useful if the replication servers of the
   * topology have a different power and one wants to spread the load between
   * the replication servers according to their power.
   *
   * @return the weight
   */
  public int getWeight()
  {
    return this.config.getWeight();
  }

  private Collection<ReplicationServerDomain> getReplicationServerDomains()
  {
    synchronized (baseDNs)
    {
      return new ArrayList<>(baseDNs.values());
    }
  }

  /**
   * Returns the changelogDB.
   *
   * @return the changelogDB.
   */
  public ChangelogDB getChangelogDB()
  {
    return this.changelogDB;
  }

  /**
   * Returns the synchronization object for shutdown of combined DS/RS instances.
   *
   * @return the synchronization object for shutdown of combined DS/RS instances.
   */
  DSRSShutdownSync getDSRSShutdownSync()
  {
    return dsrsShutdownSync;
  }

  /**
   * Returns whether change-log indexing is enabled for this RS.
   * @return true if change-log indexing is enabled for this RS.
   */
  public boolean isChangeNumberEnabled()
  {
    return config.isComputeChangeNumber();
  }

  /**
   * Returns whether the external change-log contains data from at least a domain.
   * @return whether the external change-log contains data from at least a domain
   */
  public boolean isECLEnabled()
  {
    return MultimasterReplication.isECLEnabled();
  }

  /**
   * Return whether change-log records should be encrypted.
   * @return trus if change-log records should be encrypted
   */
  public boolean isEncrypted()
  {
    return config.isConfidentialityEnabled();
  }

  @Override
  public String toString()
  {
    return "RS(" + getServerId() + ") on " + serverURL + ", domains=" + baseDNs.keySet();
  }
}
