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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.admin.std.meta.ReplicationServerCfgDefn.ReplicationDBImplementation;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.WorkflowImpl;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.replication.common.*;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.file.FileChangelogDB;
import org.opends.server.replication.server.changelog.je.JEChangelogDB;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * ReplicationServer Listener. This singleton is the main object of the
 * replication server. It waits for the incoming connections and create listener
 * and publisher objects for connection with LDAP servers and with replication
 * servers It is responsible for creating the replication server
 * replicationServerDomain and managing it
 */
public final class ReplicationServer
  implements ConfigurationChangeListener<ReplicationServerCfg>
{
  private String serverURL;

  private ServerSocket listenSocket;
  private Thread listenThread;
  private Thread connectThread;

  /** The current configuration of this replication server. */
  private ReplicationServerCfg config;
  private final DSRSShutdownSync dsrsShutdownSync;

  /**
   * This table is used to store the list of dn for which we are currently
   * handling servers.
   */
  private final Map<DN, ReplicationServerDomain> baseDNs =
      new HashMap<DN, ReplicationServerDomain>();

  private ChangelogDB changelogDB;
  private final AtomicBoolean shutdown = new AtomicBoolean();
  private boolean stopListen = false;
  private final ReplSessionSecurity replSessionSecurity;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String eclWorkflowID =
    "External Changelog Workflow ID";
  private ECLWorkflowElement eclwe;
  private final AtomicReference<WorkflowImpl> eclWorkflowImpl =
      new AtomicReference<WorkflowImpl>();

  /**
   * This is required for unit testing, so that we can keep track of all the
   * replication servers which are running in the VM.
   */
  private static final Set<Integer> localPorts =
      new CopyOnWriteArraySet<Integer>();

  // Monitors for synchronizing domain creation with the connect thread.
  private final Object domainTicketLock = new Object();
  private final Object connectThreadLock = new Object();
  private long domainTicket = 0L;

  /**
   * Holds the list of all replication servers instantiated in this VM.
   * This allows to perform clean up of the RS databases in unit tests.
   */
  private static final List<ReplicationServer> allInstances =
    new ArrayList<ReplicationServer>();

  /**
   * Creates a new Replication server using the provided configuration entry.
   *
   * @param cfg The configuration of this replication server.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(ReplicationServerCfg cfg) throws ConfigException
  {
    this(cfg, new DSRSShutdownSync());
  }

  /**
   * Creates a new Replication server using the provided configuration entry.
   *
   * @param cfg The configuration of this replication server.
   * @param dsrsShutdownSync Synchronization object for shutdown of combined DS/RS instances.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(ReplicationServerCfg cfg,
      DSRSShutdownSync dsrsShutdownSync) throws ConfigException
  {
    this.config = cfg;
    this.changelogDB = new JEChangelogDB(this, cfg);
    this.dsrsShutdownSync = dsrsShutdownSync;
    this.config = cfg;
    ReplicationDBImplementation dbImpl = cfg.getReplicationDBImplementation();
    if (dbImpl == ReplicationDBImplementation.JE)
    {
      logger.trace("Using JE as DB implementation for changelog DB");
      this.changelogDB = new JEChangelogDB(this, cfg);
    }
    else
    {
      logger.trace("Using LOG FILE as DB implementation for changelog DB");
      this.changelogDB = new FileChangelogDB(this, cfg);
    }

    replSessionSecurity = new ReplSessionSecurity();
    initialize();
    cfg.addChangeListener(this);

    localPorts.add(getReplicationPort());

    // Keep track of this new instance
    allInstances.add(this);
  }

  private Set<HostPort> getConfiguredRSAddresses()
  {
    final Set<HostPort> results = new HashSet<HostPort>();
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
   */
  void runListen()
  {
    logger.info(NOTE_REPLICATION_SERVER_LISTENING,
        getServerId(),
        listenSocket.getInetAddress().getHostAddress(),
        listenSocket.getLocalPort());

    while (!shutdown.get() && !stopListen)
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
          newSocket = listenSocket.accept();
          newSocket.setTcpNoDelay(true);
          newSocket.setKeepAlive(true);
          int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
          session = replSessionSecurity.createServerSession(newSocket,
              timeoutMS);
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
        else if (msg instanceof ServerStartECLMsg)
        {
          ECLServerHandler eclHandler = new ECLServerHandler(
              session, queueSize, this, rcvWindow);
          eclHandler.startFromRemoteServer((ServerStartECLMsg) msg);
        }
        else
        {
          // We did not recognize the message, close session as what
          // can happen after is undetermined and we do not want the server to
          // be disturbed
          session.close();
          return;
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

            connect(rsAddress, domain.getBaseDN());
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
    Set<HostPort> results = new HashSet<HostPort>();
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
  private void connect(HostPort remoteServerAddress, DN baseDN)
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
      socket.setTcpNoDelay(true);
      if (config.getSourceAddress() != null)
      {
        InetSocketAddress local = new InetSocketAddress(config.getSourceAddress(), 0);
        socket.bind(local);
      }
      int timeoutMS = MultimasterReplication.getConnectionTimeoutMS();
      socket.connect(remoteServerAddress.toInetSocketAddress(), timeoutMS);
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
    }
  }

  /**
   * initialization function for the replicationServer.
   */
  private void initialize()
  {
    shutdown.set(false);

    try
    {
      this.changelogDB.initializeDB();

      setServerURL();
      listenSocket = new ServerSocket();
      listenSocket.bind(new InetSocketAddress(getReplicationPort()));

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

      listenThread = new ReplicationServerListenThread(this);
      listenThread.start();

      // Creates the ECL workflow elem so that DS (LDAPReplicationDomain)
      // can know me and really enableECL.
      if (WorkflowImpl.getWorkflow(eclWorkflowID) != null)
      {
        // Already done. Nothing to do
        return;
      }
      eclwe = new ECLWorkflowElement(this);

      if (logger.isTraceEnabled())
      {
        logger.trace("RS " + getMonitorInstanceName() + " successfully initialized");
      }
    } catch (UnknownHostException e)
    {
      logger.error(ERR_UNKNOWN_HOSTNAME);
    } catch (IOException e)
    {
      logger.error(ERR_COULD_NOT_BIND_CHANGELOG, getReplicationPort(), e.getMessage());
    } catch (DirectoryException e)
    {
      //FIXME:DirectoryException is raised by initializeECL => fix err msg
      logger.error(LocalizableMessage.raw(
          "Directory Exception raised by ECL initialization: %s", e.getMessage()));
    }
  }

  /**
   * Enable the ECL access by creating a dedicated workflow element.
   * @throws DirectoryException when an error occurs.
   */
  public void enableECL() throws DirectoryException
  {
    if (eclWorkflowImpl.get() != null)
    {
      // ECL is already enabled, do nothing
      return;
    }

    // Create the workflow for the base DN
    // and register the workflow with the server.
    final DN dn = DN.valueOf(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
    final WorkflowImpl workflowImpl = new WorkflowImpl(eclWorkflowID, dn,
        eclwe.getWorkflowElementID(), eclwe);
    if (!eclWorkflowImpl.compareAndSet(null, workflowImpl))
    {
      // ECL is being enabled, do nothing
      return;
    }

    workflowImpl.register();

    NetworkGroup.getDefaultNetworkGroup().registerWorkflow(workflowImpl);

    // FIXME:ECL should the ECL Workflow be registered in admin and internal
    // network groups?
    NetworkGroup.getAdminNetworkGroup().registerWorkflow(workflowImpl);
    NetworkGroup.getInternalNetworkGroup().registerWorkflow(workflowImpl);

    DirectoryServer.registerVirtualAttribute(buildVirtualAttributeRule(
        "lastexternalchangelogcookie", new LastCookieVirtualProvider()));
    DirectoryServer.registerVirtualAttribute(buildVirtualAttributeRule(
        "firstchangenumber", new FirstChangeNumberVirtualAttributeProvider()));
    DirectoryServer.registerVirtualAttribute(buildVirtualAttributeRule(
        "lastchangenumber", new LastChangeNumberVirtualAttributeProvider()));
    DirectoryServer.registerVirtualAttribute(buildVirtualAttributeRule(
        "changelog", new ChangelogBaseDNVirtualAttributeProvider()));
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
      Set<SearchFilter> filters = Collections.singleton(
          SearchFilter.createFilterFromString("(objectclass=*)"));

      // To avoid the configuration in cn=config just
      // create a rule and register it into the DirectoryServer
      provider.initializeVirtualAttributeProvider(null);

      AttributeType attributeType = DirectoryServer.getAttributeType(
          attrName, false);

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

  private void shutdownECL()
  {
    WorkflowImpl eclwf = (WorkflowImpl) WorkflowImpl.getWorkflow(eclWorkflowID);
    // do it only if not already done by another RS (unit test case)
    if (eclwf != null)
    {
      // FIXME:ECL should the ECL Workflow be registered in admin and internal
      // network groups?
      NetworkGroup.getInternalNetworkGroup().deregisterWorkflow(eclWorkflowID);
      NetworkGroup.getAdminNetworkGroup().deregisterWorkflow(eclWorkflowID);

      NetworkGroup.getDefaultNetworkGroup().deregisterWorkflow(eclWorkflowID);

      try
      {
        DirectoryServer.deregisterVirtualAttribute(buildVirtualAttributeRule(
            "lastexternalchangelogcookie", new LastCookieVirtualProvider()));
        DirectoryServer.deregisterVirtualAttribute(buildVirtualAttributeRule(
            "firstchangenumber",
            new FirstChangeNumberVirtualAttributeProvider()));
        DirectoryServer.deregisterVirtualAttribute(buildVirtualAttributeRule(
            "lastchangenumber",
            new LastChangeNumberVirtualAttributeProvider()));
        DirectoryServer.deregisterVirtualAttribute(buildVirtualAttributeRule(
            "changelog", new ChangelogBaseDNVirtualAttributeProvider()));
      }
      catch (DirectoryException e)
      {
        // Should never happen
        throw new RuntimeException(e);
      }

      eclwf.deregister();
      eclwf.finalizeWorkflow();
    }

    eclwe = (ECLWorkflowElement) DirectoryServer
        .getWorkflowElement("EXTERNAL CHANGE LOG");
    if (eclwe != null)
    {
      DirectoryServer.deregisterWorkflowElement(eclwe);
      eclwe.finalizeWorkflowElement();
    }
  }

  /**
   * Get the ReplicationServerDomain associated to the base DN given in
   * parameter.
   *
   * @param baseDN
   *          The base Dn for which the ReplicationServerDomain must be
   *          returned.
   * @return The ReplicationServerDomain associated to the base DN given in
   *         parameter.
   */
  public ReplicationServerDomain getReplicationServerDomain(DN baseDN)
  {
    return getReplicationServerDomain(baseDN, false);
  }

  /**
   * Get the ReplicationServerDomain associated to the base DN given in
   * parameter.
   *
   * @param baseDN The base Dn for which the ReplicationServerDomain must be
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
        domain = new ReplicationServerDomain(baseDN, this);
        baseDNs.put(baseDN, domain);
      }
      return domain;
    }
  }

  /**
   * Waits for connections to this ReplicationServer.
   */
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
      connectThreadLock.notify();
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

  /**
   * Shutdown the Replication Server service and all its connections.
   */
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

    shutdownECL();

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

    try
    {
      ServerSocket tmpSocket = new ServerSocket();
      tmpSocket.bind(new InetSocketAddress(port));
      tmpSocket.close();
      return true;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_COULD_NOT_BIND_CHANGELOG.get(port, e.getMessage());
      unacceptableReasons.add(message);
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      ReplicationServerCfg configuration)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    // Some of those properties change don't need specific code.
    // They will be applied for next connections. Some others have immediate
    // effect
    final Set<HostPort> oldRSAddresses = getConfiguredRSAddresses();

    final ReplicationServerCfg oldConfig = this.config;
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
        resultCode = ResultCode.OPERATIONS_ERROR;
      }
    }

    // changing the listen port requires to stop the listen thread
    // and restart it.
    if (getReplicationPort() != oldConfig.getReplicationPort())
    {
      stopListen = true;
      try
      {
        listenSocket.close();
        listenThread.join();
        stopListen = false;

        setServerURL();
        listenSocket = new ServerSocket();
        listenSocket.bind(new InetSocketAddress(getReplicationPort()));

        listenThread = new ReplicationServerListenThread(this);
        listenThread.start();
      }
      catch (IOException e)
      {
        logger.traceException(e);
        logger.error(ERR_COULD_NOT_CLOSE_THE_SOCKET, e);
      }
      catch (InterruptedException e)
      {
        logger.traceException(e);
        logger.error(ERR_COULD_NOT_STOP_LISTEN_THREAD, e);
      }
    }

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
      adminActionRequired = true;
    }
    return new ConfigChangeResult(resultCode, adminActionRequired);
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
      /*
       * No need validate the string format because the admin framework has
       * already done it.
       */
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

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      ReplicationServerCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
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
   *
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
    final Collection<HostPort> serversToDisconnect = new ArrayList<HostPort>();

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
   *
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
   * Get the oldest and newest change numbers.
   *
   * @return an array of size 2 holding the oldest and newest change numbers at
   *         indexes 0 and 1.
   * @throws DirectoryException
   *           When it happens.
   */
  public long[] getECLChangeNumberLimits() throws DirectoryException
  {
    try
    {
      final ChangeNumberIndexDB cnIndexDB = getChangeNumberIndexDB();
      final ChangeNumberIndexRecord oldestRecord = cnIndexDB.getOldestRecord();
      if (oldestRecord == null)
      {
        // The database is empty, just keep increasing numbers since last time
        // we generated one change number.
        final long lastGeneratedCN = cnIndexDB.getLastGeneratedChangeNumber();
        return new long[] { lastGeneratedCN, lastGeneratedCN };
      }

      final ChangeNumberIndexRecord newestRecord = cnIndexDB.getNewestRecord();
      if (newestRecord == null)
      {
        // Edge case: DB was cleaned (or purged) in between calls to
        // getOldest*() and getNewest*().
        // The only remaining solution is to fail fast.
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
            ERR_READING_OLDEST_THEN_NEWEST_IN_CHANGENUMBER_DATABASE.get());
      }
      return new long[] { oldestRecord.getChangeNumber(),
        newestRecord.getChangeNumber() };
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
   *          The list of baseDNs excluded from ECL.
   * @return the newest cookie value.
   */
  public MultiDomainServerState getNewestECLCookie(Set<String> excludedBaseDNs)
  {
    // Initialize start state for all running domains with empty state
    final MultiDomainServerState result = new MultiDomainServerState();
    for (ReplicationServerDomain rsDomain : getReplicationServerDomains())
    {
      if (!contains(excludedBaseDNs, rsDomain.getBaseDN().toNormalizedString()))
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

  private boolean contains(Set<String> col, String elem)
  {
    return col != null && col.contains(elem);
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
      return new ArrayList<ReplicationServerDomain>(baseDNs.values());
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

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "RS(" + getServerId() + ") on " + serverURL + ", domains="
        + baseDNs.keySet();
  }

}
