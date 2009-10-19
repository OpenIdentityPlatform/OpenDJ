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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.ServerConstants.EOL;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.Severity;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.*;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackupTaskListener;
import org.opends.server.api.ExportTaskListener;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.RestoreTaskListener;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.WorkflowImpl;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.*;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ReplServerStartMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ServerStartECLMsg;
import org.opends.server.replication.protocol.ServerStartMsg;
import org.opends.server.replication.protocol.StartECLSessionMsg;
import org.opends.server.replication.protocol.StartMsg;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;

import com.sleepycat.je.DatabaseException;

/**
 * ReplicationServer Listener.
 *
 * This singleton is the main object of the replication server
 * It waits for the incoming connections and create listener
 * and publisher objects for
 * connection with LDAP servers and with replication servers
 *
 * It is responsible for creating the replication server replicationServerDomain
 * and managing it
 */
public class ReplicationServer
  implements ConfigurationChangeListener<ReplicationServerCfg>,
             BackupTaskListener, RestoreTaskListener, ImportTaskListener,
             ExportTaskListener
{
  private int serverId;
  private String serverURL;

  private ServerSocket listenSocket;
  private Thread listenThread;
  private Thread connectThread;

  /* The list of replication servers configured by the administrator */
  private Collection<String> replicationServers;

  /* This table is used to store the list of dn for which we are currently
   * handling servers.
   */
  private ConcurrentHashMap<String, ReplicationServerDomain> baseDNs =
          new ConcurrentHashMap<String, ReplicationServerDomain>();

  private String localURL = "null";
  private boolean shutdown = false;
  private ReplicationDbEnv dbEnv;
  private int rcvWindow;
  private int queueSize;
  private String dbDirname = null;

  // The delay (in sec) after which the  changes must
  // be deleted from the persistent storage.
  private long purgeDelay;

  private int replicationPort;
  private boolean stopListen = false;
  private ReplSessionSecurity replSessionSecurity;

  // For the backend associated to this replication server,
  // DN of the config entry of the backend
  private DN backendConfigEntryDN;
  // ID of the backend
  private static final String backendId = "replicationChanges";

  // At startup, the listen thread wait on this flag for the connect
  // thread to look for other servers in the topology.
  private boolean connectedInTopology = false;
  private final Object connectedInTopologyLock = new Object();

  /*
   * Assured mode properties
   */
  // Timeout (in milliseconds) when waiting for acknowledgments
  private long assuredTimeout = 1000;

  // Group id
  private byte groupId = (byte)1;

  // Number of pending changes for a DS, considered as threshold value to put
  // the DS in DEGRADED_STATUS. If value is 0, status analyzer is disabled
  private int degradedStatusThreshold = 5000;

  // The handler of the draft change numbers database, the database used to
  // store the relation between a draft change number ('seqnum') and the
  // associated cookie.
  private DraftCNDbHandler draftCNDbHandler;
  // The last value generated of the draft change number.
  private int lastGeneratedDraftCN = 0;

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private String externalChangeLogWorkflowID = "External Changelog Workflow ID";
  ECLWorkflowElement eclwe;
  private static HashSet<Integer> localPorts = new HashSet<Integer>();

  // used to synchronize the domain creation with the connect thread.
  final private Object domainMonitor = new Object();

  /**
   * The weight affected to the replication server.
   * Each replication server of the topology has a weight. When combined
   * together, the weights of the replication servers of a same group can be
   * translated to a percentage that determines the quantity of directory
   * servers of the topology that should be connected to a replication server.
   * For instance imagine a topology with 3 replication servers (with the same
   * group id) with the following weights: RS1=1, RS2=1, RS3=2. This means that
   * RS1 should have 25% of the directory servers connected in the topology,
   * RS2 25%, and RS3 50%. This may be useful if the replication servers of the
   * topology have a different power and one wants to spread the load between
   * the replication servers according to their power.
   */
  private int weight = 1;

  /**
   * Creates a new Replication server using the provided configuration entry.
   *
   * @param configuration The configuration of this replication server.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(ReplicationServerCfg configuration)
    throws ConfigException
  {
    replicationPort = configuration.getReplicationPort();
    serverId = configuration.getReplicationServerId();
    replicationServers = configuration.getReplicationServer();
    if (replicationServers == null)
      replicationServers = new ArrayList<String>();
    queueSize = configuration.getQueueSize();
    purgeDelay = configuration.getReplicationPurgeDelay();
    dbDirname = configuration.getReplicationDBDirectory();
    rcvWindow = configuration.getWindowSize();
    if (dbDirname == null)
    {
      dbDirname = "changelogDb";
    }
    // Check that this path exists or create it.
    File f = getFileForPath(dbDirname);
    try
    {
      if (!f.exists())
      {
        f.mkdir();
      }
    }
    catch (Exception e)
    {

      MessageBuilder mb = new MessageBuilder();
      mb.append(e.getLocalizedMessage());
      mb.append(" ");
      mb.append(String.valueOf(getFileForPath(dbDirname)));
      Message msg = ERR_FILE_CHECK_CREATE_FAILED.get(mb.toString());
      throw new ConfigException(msg, e);
    }
    groupId = (byte)configuration.getGroupId();
    assuredTimeout = configuration.getAssuredTimeout();
    degradedStatusThreshold = configuration.getDegradedStatusThreshold();

    replSessionSecurity = new ReplSessionSecurity();
    initialize(replicationPort);
    configuration.addChangeListener(this);
    try
    {
      backendConfigEntryDN = DN.decode(
      "ds-cfg-backend-id=" + backendId + ",cn=Backends,cn=config");
    } catch (Exception e) {}

    // Creates the backend associated to this ReplicationServer
    // if it does not exist.
    createBackend();

    DirectoryServer.registerBackupTaskListener(this);
    DirectoryServer.registerRestoreTaskListener(this);
    DirectoryServer.registerExportTaskListener(this);
    DirectoryServer.registerImportTaskListener(this);

    localPorts.add(replicationPort);
  }


  /**
   * The run method for the Listen thread.
   * This thread accept incoming connections on the replication server
   * ports from other replication servers or from LDAP servers
   * and spawn further thread responsible for handling those connections
   */

  void runListen()
  {
    // wait for the connect thread to find other replication
    // servers in the topology before starting to accept connections
    // from the ldap servers.
    synchronized (connectedInTopologyLock)
    {
      if (connectedInTopology == false)
      {
        try
        {
          connectedInTopologyLock.wait(1000);
        } catch (InterruptedException e)
        {
        }
      }
    }

    while ((shutdown == false) && (stopListen  == false))
    {
      // Wait on the replicationServer port.
      // Read incoming messages and create LDAP or ReplicationServer listener
      // and Publisher.

      ProtocolSession session;
      Socket newSocket = null;

      try
      {
        try
        {
          newSocket = listenSocket.accept();
          newSocket.setTcpNoDelay(true);
          newSocket.setKeepAlive(true);
          session =
            replSessionSecurity.createServerSession(newSocket,
                ReplSessionSecurity.HANDSHAKE_TIMEOUT);
          if (session == null) // Error, go back to accept
            continue;
        }
        catch (Exception e)
        {
          // If problems happen during the SSL handshake, it is necessary
          // to close the socket to free the associated resources.
          if (newSocket != null)
            newSocket.close();
          continue;
        }

        ReplicationMsg msg = session.receive();

        if (msg instanceof ServerStartMsg)
        {
          session.setProtocolVersion(((StartMsg)msg).getVersion());
          DataServerHandler handler = new DataServerHandler(session,
              queueSize,serverURL,serverId,this,rcvWindow);
          handler.startFromRemoteDS((ServerStartMsg)msg);
        }
        else if (msg instanceof ReplServerStartMsg)
        {
          session.setProtocolVersion(((StartMsg)msg).getVersion());
          ReplicationServerHandler handler = new ReplicationServerHandler(
              session,queueSize,serverURL,serverId,this,rcvWindow);
          handler.startFromRemoteRS((ReplServerStartMsg)msg);
        }
        else if (msg instanceof ServerStartECLMsg)
        {
          session.setProtocolVersion(((StartMsg)msg).getVersion());
          ECLServerHandler handler = new ECLServerHandler(
              session,queueSize,serverURL,serverId,this,rcvWindow);
          handler.startFromRemoteServer((ServerStartECLMsg)msg);
        }
        else
        {
          // We did not recognize the message, close session as what
          // can happen after is undetermined and we do not want the server to
          // be disturbed
          ServerHandler.closeSession(session, null, null);
          return;
        }
      }
      catch (Exception e)
      {
        // The socket has probably been closed as part of the
        // shutdown or changing the port number process.
        // Just log debug information and loop.
        // Do not log the message during shutdown.
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        if (shutdown == false) {
          Message message =
            ERR_EXCEPTION_LISTENING.get(e.getLocalizedMessage());
          logError(message);
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
    while (shutdown == false)
    {
      /*
       * periodically check that we are connected to all other
       * replication servers and if not establish the connection
       */
      for (ReplicationServerDomain replicationServerDomain: baseDNs.values())
      {
        Set<String> connectedReplServers =
                replicationServerDomain.getChangelogs();
        /*
         * check that all replication server in the config are in the connected
         * Set. If not create the connection
         */
        for (String serverURL : replicationServers)
        {
          int separator = serverURL.lastIndexOf(':');
          String port = serverURL.substring(separator + 1);
          String hostname = serverURL.substring(0, separator);

          try
          {
            InetAddress inetAddress = InetAddress.getByName(hostname);
            String serverAddress = inetAddress.getHostAddress() + ":" + port;
            String alternServerAddress = null;
            if (hostname.equalsIgnoreCase("localhost"))

            {
              // if "localhost" was used as the hostname in the configuration
              // also check is the connection is already opened with the
              // local address.
              alternServerAddress =
                InetAddress.getLocalHost().getHostAddress() + ":" + port;
            }
            if (inetAddress.equals(InetAddress.getLocalHost()))
            {
              // if the host address is the local one, also check
              // if the connection is already opened with the "localhost"
              // address
              alternServerAddress = "127.0.0.1" + ":" + port;
            }

            if ((serverAddress.compareTo("127.0.0.1:" + replicationPort) != 0)
                && (serverAddress.compareTo(this.localURL) != 0)
                && (!connectedReplServers.contains(serverAddress)
                && ((alternServerAddress == null)
                    || !connectedReplServers.contains(alternServerAddress))))
            {
              this.connect(serverURL, replicationServerDomain.getBaseDn());
            }
          }
          catch (IOException e)
          {
            Message message = ERR_COULD_NOT_SOLVE_HOSTNAME.get(hostname);
            logError(message);
          }
        }
      }
      synchronized (connectedInTopologyLock)
      {
        // wake up the listen thread if necessary.
        if (connectedInTopology == false)
        {
          connectedInTopologyLock.notify();
          connectedInTopology = true;
        }
      }
      try
      {
        synchronized(domainMonitor)
        {
          domainMonitor.notifyAll();
        }
        synchronized (this)
        {
          /* check if we are connected every second */
          int randomizer = (int)(Math.random()*100);
          wait(1000 + randomizer);
        }
      } catch (InterruptedException e)
      {
        // ignore error, will try to connect again or shutdown
      }
    }
  }

  /**
   * Establish a connection to the server with the address and port.
   *
   * @param remoteServerURL  The address and port for the server, separated by a
   *                    colon.
   * @param baseDn     The baseDn of the connection
   */
  private void connect(String remoteServerURL, String baseDn)
  {
    int separator = remoteServerURL.lastIndexOf(':');
    String port = remoteServerURL.substring(separator + 1);
    String hostname = remoteServerURL.substring(0, separator);
    boolean sslEncryption =replSessionSecurity.isSslEncryption(remoteServerURL);

    if (debugEnabled())
      TRACER.debugInfo("RS " + this.getMonitorInstanceName() +
               " connects to " + remoteServerURL);

    try
    {
      InetSocketAddress ServerAddr = new InetSocketAddress(
                     InetAddress.getByName(hostname), Integer.parseInt(port));
      Socket socket = new Socket();
      socket.setTcpNoDelay(true);
      socket.connect(ServerAddr, 500);

      /*
      ServerHandler handler = new ServerHandler(
           replSessionSecurity.createClientSession(serverURL, socket,
           ReplSessionSecurity.HANDSHAKE_TIMEOUT),
           queueSize);
      handler.start(baseDn, serverId, this.serverURL, rcvWindow,
                    sslEncryption, this);
      */

      ReplicationServerHandler handler = new ReplicationServerHandler(
          replSessionSecurity.createClientSession(remoteServerURL,
              socket,
              ReplSessionSecurity.HANDSHAKE_TIMEOUT),
              queueSize,
              this.serverURL,
              serverId,
              this,
              rcvWindow);
      handler.connect(baseDn, sslEncryption);
    }
    catch (Exception e)
    {
      // ignore
    }

  }

  /**
   * initialization function for the replicationServer.
   *
   * @param  changelogPort     The port on which the replicationServer should
   *                           listen.
   *
   */
  private void initialize(int changelogPort)
  {
    shutdown = false;

    try
    {
      /*
       * Initialize the replicationServer database.
       */
      dbEnv = new ReplicationDbEnv(getFileForPath(dbDirname).getAbsolutePath(),
          this);

      /*
       * Open replicationServer socket
       */
      String localhostname = InetAddress.getLocalHost().getHostName();
      String localAdddress = InetAddress.getLocalHost().getHostAddress();
      serverURL = localhostname + ":" + String.valueOf(changelogPort);
      localURL = localAdddress + ":" + String.valueOf(changelogPort);
      listenSocket = new ServerSocket();
      listenSocket.bind(new InetSocketAddress(changelogPort));

      /*
       * creates working threads
       * We must first connect, then start to listen.
       */
      if (debugEnabled())
        TRACER.debugInfo("RS " +getMonitorInstanceName()+
            " creates connect thread");
      connectThread =
        new ReplicationServerConnectThread("Replication Server Connect " +
        serverId , this);
      connectThread.start();

      // FIXME : Is it better to have the time to receive the ReplServerInfo
      // from all the other replication servers since this info is necessary
      // to route an early received total update request.
      try { Thread.sleep(300);} catch(Exception e) {}
      if (debugEnabled())
        TRACER.debugInfo("RS " +getMonitorInstanceName()+
            " creates listen thread");

      listenThread =
        new ReplicationServerListenThread("Replication Server Listener " +
        serverId , this);
      listenThread.start();

      // Initialize the External Changelog
      // FIXME: how is WF creation enabed/disabled in the RS ?
      initializeECL();

      if (debugEnabled())
        TRACER.debugInfo("RS " +getMonitorInstanceName()+
            " successfully initialized");

    } catch (DatabaseException e)
    {
      Message message = ERR_COULD_NOT_INITIALIZE_DB.get(
        getFileForPath(dbDirname).getAbsolutePath());
      logError(message);
    } catch (ReplicationDBException e)
    {
      Message message = ERR_COULD_NOT_READ_DB.get(dbDirname,
          e.getLocalizedMessage());
      logError(message);
    } catch (UnknownHostException e)
    {
      Message message = ERR_UNKNOWN_HOSTNAME.get();
      logError(message);
    } catch (IOException e)
    {
      Message message =
          ERR_COULD_NOT_BIND_CHANGELOG.get(changelogPort, e.getMessage());
      logError(message);
    } catch (DirectoryException e)
    {
      //FIXME:DirectoryException is raised by initializeECL => fix err msg
      Message message = Message.raw(Category.SYNC, Severity.SEVERE_ERROR,
        "Directory Exception raised by ECL initialization: " + e.getMessage());
      logError(message);
    }
  }

  /**
   * Initializes the ECL access by creating a dedicated workflow element.
   * @throws DirectoryException
   */
  private void initializeECL()
  throws DirectoryException
  {
    WorkflowImpl externalChangeLogWorkflow;
    if (WorkflowImpl.getWorkflow(externalChangeLogWorkflowID)
        !=null)
      return;

    ECLWorkflowElement eclwe = new ECLWorkflowElement(this);

    // Create the workflow for the base DN and register the workflow with
    // the server.
    externalChangeLogWorkflow = new WorkflowImpl(
        externalChangeLogWorkflowID,
        DN.decode(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT),
        eclwe.getWorkflowElementID(),
        eclwe);
    externalChangeLogWorkflow.register();

    NetworkGroup defaultNetworkGroup = NetworkGroup.getDefaultNetworkGroup();
    defaultNetworkGroup.registerWorkflow(externalChangeLogWorkflow);

    // FIXME:ECL should the ECL Workflow be registered in adminNetworkGroup?
    NetworkGroup adminNetworkGroup = NetworkGroup.getAdminNetworkGroup();
    adminNetworkGroup.registerWorkflow(externalChangeLogWorkflow);

    // FIXME:ECL should the ECL Workflow be registered in internalNetworkGroup?
    NetworkGroup internalNetworkGroup = NetworkGroup.getInternalNetworkGroup();
    internalNetworkGroup.registerWorkflow(externalChangeLogWorkflow);

    try
    {
      enableECLVirtualAttr("lastexternalchangelogcookie",
          new LastCookieVirtualProvider());
      enableECLVirtualAttr("firstchangenumber",
          new FirstChangeNumberVirtualAttributeProvider());
      enableECLVirtualAttr("lastchangenumber",
          new LastChangeNumberVirtualAttributeProvider());
      enableECLVirtualAttr("changelog",
          new ChangelogBaseDNVirtualAttributeProvider());
    }
    catch (Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
  }

  private void enableECLVirtualAttr(String attrName,
      VirtualAttributeProvider<UserDefinedVirtualAttributeCfg> provider)
  {
    Set<DN> baseDNs = new HashSet<DN>(0);
    Set<DN> groupDNs = new HashSet<DN>(0);
    Set<SearchFilter> filters = new HashSet<SearchFilter>(0);
    VirtualAttributeCfgDefn.ConflictBehavior conflictBehavior =
      ConflictBehavior.VIRTUAL_OVERRIDES_REAL;

    try
    {

      // To avoid the configuration in cn=config just
      // create a rule and register it into the DirectoryServer
      provider.initializeVirtualAttributeProvider(null);

      AttributeType attributeType = DirectoryServer.getAttributeType(
          attrName, false);

      SearchFilter filter =
        SearchFilter.createFilterFromString("objectclass=*");
      filters.add(filter);

      baseDNs.add(DN.decode(""));
      VirtualAttributeRule rule =
        new VirtualAttributeRule(attributeType, provider,
            baseDNs, groupDNs, filters, conflictBehavior);

      DirectoryServer.registerVirtualAttribute(rule);

    }
    catch (Exception e)
    {
      Message message =
        NOTE_UNABLE_TO_ENABLE_ECL_VIRTUAL_ATTR.get(attrName, e.toString());
      logError(message);
    }
  }

  private void shutdownECL()
  {
    WorkflowImpl eclwf =
      (WorkflowImpl)WorkflowImpl.getWorkflow(externalChangeLogWorkflowID);

    // do it only if not already done by another RS (unit test case)
    // if (DirectoryServer.getWorkflowElement(externalChangeLogWorkflowID)
    if (eclwf!=null)
    {


    // FIXME:ECL should the ECL Workflow be registered in internalNetworkGroup?
    NetworkGroup internalNetworkGroup = NetworkGroup.getInternalNetworkGroup();
    internalNetworkGroup.deregisterWorkflow(externalChangeLogWorkflowID);

    // FIXME:ECL should the ECL Workflow be registered in adminNetworkGroup?
    NetworkGroup adminNetworkGroup = NetworkGroup.getAdminNetworkGroup();
    adminNetworkGroup.deregisterWorkflow(externalChangeLogWorkflowID);

    NetworkGroup defaultNetworkGroup = NetworkGroup.getDefaultNetworkGroup();
    defaultNetworkGroup.deregisterWorkflow(externalChangeLogWorkflowID);

    eclwf.deregister();
    eclwf.finalizeWorkflow();
    }

    eclwe = (ECLWorkflowElement)
    DirectoryServer.getWorkflowElement("EXTERNAL CHANGE LOG");
    if (eclwe!=null)
    {
      DirectoryServer.deregisterWorkflowElement(eclwe);
      eclwe.finalizeWorkflowElement();
    }

    if (draftCNDbHandler != null)
      draftCNDbHandler.shutdown();
  }

  /**
   * Get the ReplicationServerDomain associated to the base DN given in
   * parameter.
   *
   * @param baseDn The base Dn for which the ReplicationServerDomain must be
   * returned.
   * @param create Specifies whether to create the ReplicationServerDomain if
   *        it does not already exist.
   * @return The ReplicationServerDomain associated to the base DN given in
   *         parameter.
   */
  public ReplicationServerDomain getReplicationServerDomain(String baseDn,
          boolean create)
  {
    return getReplicationServerDomain(baseDn, create, false);
  }

  /**
   * Get the ReplicationServerDomain associated to the base DN given in
   * parameter.
   *
   * @param baseDn The base Dn for which the ReplicationServerDomain must be
   * returned.
   * @param create Specifies whether to create the ReplicationServerDomain if
   *        it does not already exist.
   * @param waitConnections     Waits for the Connections with other RS to
   *                            be established before returning.
   * @return The ReplicationServerDomain associated to the base DN given in
   *         parameter.
   */
  public ReplicationServerDomain getReplicationServerDomain(String baseDn,
          boolean create, boolean waitConnections)
  {
    ReplicationServerDomain replicationServerDomain;

    synchronized (baseDNs)
    {
      replicationServerDomain = baseDNs.get(baseDn);
      if ((replicationServerDomain == null) && (create))
      {
        replicationServerDomain = new ReplicationServerDomain(baseDn, this);
        baseDNs.put(baseDn, replicationServerDomain);
        synchronized (domainMonitor)
        {
          if (waitConnections)
          {
            synchronized (this)
            {
              // kick up the connect thread so that this new domain
              // gets connected to all the Replication Servers.
              this.notify();
            }
            try
            {
              // wait for the connect thread to signal that it finished its job
              domainMonitor.wait(500);
            } catch (InterruptedException e)
            {
            }
          }
        }
      }
    }

    return replicationServerDomain;
  }

  /**
   * Shutdown the Replication Server service and all its connections.
   */
  public void shutdown()
  {
    localPorts.remove(replicationPort);

    if (shutdown)
      return;

    shutdown = true;

    // shutdown the connect thread
    if (connectThread != null)
    {
      connectThread.interrupt();
    }

    // shutdown the listener thread
    try
    {
      if (listenSocket != null)
      {
        listenSocket.close();
      }
    } catch (IOException e)
    {
      // replication Server service is closing anyway.
    }

    // shutdown the listen thread
    if (listenThread != null)
    {
      listenThread.interrupt();
    }

    // shutdown all the ChangelogCaches
    for (ReplicationServerDomain replicationServerDomain : baseDNs.values())
    {
      replicationServerDomain.shutdown();
    }

    shutdownECL();

    if (dbEnv != null)
    {
      dbEnv.shutdown();
    }

}


  /**
   * Creates a new DB handler for this ReplicationServer and the serverId and
   * DN given in parameter.
   *
   * @param id The serverId for which the dbHandler must be created.
   * @param baseDn The DN for which the dbHandler must be created.
   * @return The new DB handler for this ReplicationServer and the serverId and
   *         DN given in parameter.
   * @throws DatabaseException in case of underlying database problem.
   */
  public DbHandler newDbHandler(int id, String baseDn)
  throws DatabaseException
  {
    return new DbHandler(id, baseDn, this, dbEnv, queueSize);
  }

  /**
   * Clears the generationId for the replicationServerDomain related to the
   * provided baseDn.
   * @param  baseDn The baseDn for which to delete the generationId.
   * @throws DatabaseException When it occurs.
   */
  public void clearGenerationId(String baseDn)
  throws DatabaseException
  {
    try
    {
      dbEnv.clearGenerationId(baseDn);
    }
    catch(Exception e)
    {
      TRACER.debugCaught(LogLevel.ALL, e);
    }
  }

  /**
   * Retrieves the time after which changes must be deleted from the
   * persistent storage (in milliseconds).
   *
   * @return  The time after which changes must be deleted from the
   *          persistent storage (in milliseconds).
   */
  long getTrimage()
  {
    return purgeDelay * 1000;
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
      ReplicationServerCfg configuration, List<Message> unacceptableReasons)
  {
    int port = configuration.getReplicationPort();

    try
    {
      ServerSocket tmpSocket = new ServerSocket();
      tmpSocket.bind(new InetSocketAddress(port));
      tmpSocket.close();
    }
    catch (Exception e)
    {
      Message message = ERR_COULD_NOT_BIND_CHANGELOG.get(port, e.getMessage());
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ReplicationServerCfg configuration)
  {
    // Some of those properties change don't need specific code.
    // They will be applied for next connections. Some others have immediate
    // effect

    disconnectRemovedReplicationServers(configuration.getReplicationServer());

    replicationServers = configuration.getReplicationServer();
    if (replicationServers == null)
      replicationServers = new ArrayList<String>();

    queueSize = configuration.getQueueSize();
    long newPurgeDelay = configuration.getReplicationPurgeDelay();
    if (newPurgeDelay != purgeDelay)
    {
      purgeDelay = newPurgeDelay;
      // propagate
      for (ReplicationServerDomain domain : baseDNs.values())
      {
        domain.setPurgeDelay(purgeDelay*1000);
      }
    }

    rcvWindow = configuration.getWindowSize();
    assuredTimeout = configuration.getAssuredTimeout();

    // changing the listen port requires to stop the listen thread
    // and restart it.
    int newPort = configuration.getReplicationPort();
    if (newPort != replicationPort)
    {
      stopListen = true;
      try
      {
        listenSocket.close();
        listenThread.join();
        stopListen = false;

        replicationPort = newPort;
        String localhostname = InetAddress.getLocalHost().getHostName();
        String localAdddress = InetAddress.getLocalHost().getHostAddress();
        serverURL = localhostname + ":" + String.valueOf(replicationPort);
        localURL = localAdddress + ":" + String.valueOf(replicationPort);
        listenSocket = new ServerSocket();
        listenSocket.bind(new InetSocketAddress(replicationPort));

        listenThread =
          new ReplicationServerListenThread(
              "Replication Server Listener", this);
        listenThread.start();
      }
      catch (IOException e)
      {
        Message message = ERR_COULD_NOT_CLOSE_THE_SOCKET.get(e.toString());
        logError(message);
        new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                               false);
      }
      catch (InterruptedException e)
      {
        Message message = ERR_COULD_NOT_STOP_LISTEN_THREAD.get(e.toString());
        logError(message);
        new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                               false);
      }
    }

    // Update threshold value for status analyzers (stop them if requested
    // value is 0)
    if (degradedStatusThreshold != configuration.getDegradedStatusThreshold())
    {
      int oldThresholdValue = degradedStatusThreshold;
      degradedStatusThreshold = configuration.getDegradedStatusThreshold();
      for(ReplicationServerDomain rsd : baseDNs.values())
      {
        if (degradedStatusThreshold == 0)
        {
          // Requested to stop analyzers
          rsd.stopStatusAnalyzer();
        } else if (rsd.isRunningStatusAnalyzer())
        {
          // Update the threshold value for this running analyzer
          rsd.updateStatusAnalyzer(degradedStatusThreshold);
        } else if (oldThresholdValue == 0)
        {
          // Requested to start analyzers with provided threshold value
          if (rsd.getConnectedDSs().size() > 0)
            rsd.startStatusAnalyzer();
        }
      }
    }

    // Changed the group id ?
    byte newGroupId = (byte)configuration.getGroupId();
    if (newGroupId != groupId)
    {
      groupId = newGroupId;
      // Have a new group id: Disconnect every servers.
      for (ReplicationServerDomain replicationServerDomain : baseDNs.values())
      {
        replicationServerDomain.stopAllServers();
      }
    }

    // Set a potential new weight
    if (weight != configuration.getWeight())
    {
      weight = configuration.getWeight();
      // TODO: send new TopologyMsg
    }

    if ((configuration.getReplicationDBDirectory() != null) &&
        (!dbDirname.equals(configuration.getReplicationDBDirectory())))
    {
      return new ConfigChangeResult(ResultCode.SUCCESS, true);
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ReplicationServerCfg configuration, List<Message> unacceptableReasons)
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
  public long getGenerationId(String baseDN)
  {
    ReplicationServerDomain rsd =
            this.getReplicationServerDomain(baseDN, false);
    if (rsd!=null)
      return rsd.getGenerationId();
    return -1;
  }

  /**
   * Get the serverId for this replication server.
   *
   * @return The value of the serverId.
   *
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * Get the queueSize for this replication server.
   *
   * @return The maximum size of the queues for this Replication Server
   *
   */
  public int getQueueSize()
  {
    return queueSize;
  }

  /**
   * Creates the backend associated to this replication server.
   * @throws ConfigException
   */
  private void createBackend()
  throws ConfigException
  {
    try
    {
      String ldif = makeLdif(
          "dn: ds-cfg-backend-id="+backendId+",cn=Backends,cn=config",
          "objectClass: top",
          "objectClass: ds-cfg-backend",
          "ds-cfg-base-dn: dc="+backendId,
          "ds-cfg-enabled: true",
          "ds-cfg-writability-mode: enabled",
          "ds-cfg-java-class: " +
            "org.opends.server.replication.server.ReplicationBackend",
          "ds-cfg-backend-id: " + backendId);

      LDIFImportConfig ldifImportConfig = new LDIFImportConfig(
          new StringReader(ldif));
      LDIFReader reader = new LDIFReader(ldifImportConfig);
      Entry backendConfigEntry = reader.readEntry();
      if (!DirectoryServer.getConfigHandler().entryExists(backendConfigEntryDN))
      {
        // Add the replication backend
        DirectoryServer.getConfigHandler().addEntry(backendConfigEntry, null);
      }
      ldifImportConfig.close();
    }
    catch(Exception e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(e.getLocalizedMessage());
      Message msg = ERR_CHECK_CREATE_REPL_BACKEND_FAILED.get(mb.toString());
      throw new ConfigException(msg, e);

    }
  }

  private static String makeLdif(String... lines)
  {
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      buffer.append(line).append(EOL);
    }
    // Append an extra line so we can append LDIF Strings.
    buffer.append(EOL);
    return buffer.toString();
  }

  /**
   * Do what needed when the config object related to this replication server
   * is deleted from the server configuration.
   */
  public void remove()
  {
    if (debugEnabled())
      TRACER.debugInfo("RS " +getMonitorInstanceName()+
          " starts removing");

    shutdown();
    removeBackend();

    DirectoryServer.deregisterBackupTaskListener(this);
    DirectoryServer.deregisterRestoreTaskListener(this);
    DirectoryServer.deregisterExportTaskListener(this);
    DirectoryServer.deregisterImportTaskListener(this);
  }

  /**
   * Removes the backend associated to this Replication Server that has been
   * created when this replication server was created.
   */
  protected void removeBackend()
  {
    try
    {
      if (DirectoryServer.getConfigHandler().entryExists(backendConfigEntryDN))
      {
        // Delete the replication backend
        DirectoryServer.getConfigHandler().deleteEntry(backendConfigEntryDN,
            null);
      }
    }
    catch(Exception e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(e.getLocalizedMessage());
      Message msg = ERR_DELETE_REPL_BACKEND_FAILED.get(mb.toString());
      logError(msg);
    }
  }
  /**
   * {@inheritDoc}
   */
  public void processBackupBegin(Backend backend, BackupConfig config)
  {
    // Nothing is needed at the moment
  }

  /**
   * {@inheritDoc}
   */
  public void processBackupEnd(Backend backend, BackupConfig config,
                               boolean successful)
  {
    // Nothing is needed at the moment
  }

  /**
   * {@inheritDoc}
   */
  public void processRestoreBegin(Backend backend, RestoreConfig config)
  {
    if (backend.getBackendID().equals(backendId))
      shutdown();
  }

  /**
   * {@inheritDoc}
   */
  public void processRestoreEnd(Backend backend, RestoreConfig config,
                                boolean successful)
  {
    if (backend.getBackendID().equals(backendId))
      initialize(this.replicationPort);
  }

  /**
   * {@inheritDoc}
   */
  public void processImportBegin(Backend backend, LDIFImportConfig config)
  {
    // Nothing is needed at the moment
  }

  /**
   * {@inheritDoc}
   */
  public void processImportEnd(Backend backend, LDIFImportConfig config,
                               boolean successful)
  {
    // Nothing is needed at the moment
  }

  /**
   * {@inheritDoc}
   */
  public void processExportBegin(Backend backend, LDIFExportConfig config)
  {
    if (debugEnabled())
      TRACER.debugInfo("RS " +getMonitorInstanceName()+
          " Export starts");
    if (backend.getBackendID().equals(backendId))
    {
      // Retrieves the backend related to this replicationServerDomain
      // backend =
      ReplicationBackend b =
      (ReplicationBackend)DirectoryServer.getBackend(backendId);
      b.setServer(this);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processExportEnd(Backend backend, LDIFExportConfig config,
                               boolean successful)
  {
    // Nothing is needed at the moment
  }

  /**
   * Returns an iterator on the list of replicationServerDomain.
   * Returns null if none.
   * @return the iterator.
   */
  public Iterator<ReplicationServerDomain> getDomainIterator()
  {
    if (!baseDNs.isEmpty())
      return baseDNs.values().iterator();
    else
      return null;
  }

  /**
   * Clears the Db associated with that server.
   */
  public void clearDb()
  {
    Iterator<ReplicationServerDomain> rcachei = getDomainIterator();
    if (rcachei != null)
    {
      while (rcachei.hasNext())
      {
        ReplicationServerDomain rsd = rcachei.next();
        rsd.clearDbs();
      }
    }
    if (this.draftCNDbHandler != null)
    {
      try
      {
        try { draftCNDbHandler.clear(); } catch(Exception e){}
        draftCNDbHandler.shutdown();
        lastGeneratedDraftCN = 0;
        draftCNDbHandler = null;
      }
      catch(Exception e) {}
    }
  }

  /**
   * Get the assured mode timeout.
   * @return The assured mode timeout.
   */
  public long getAssuredTimeout()
  {
    return assuredTimeout;
  }

  /**
   * Get The replication server group id.
   * @return The replication server group id.
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Get the threshold value for status analyzer.
   * @return The threshold value for status analyzer.
   */
  public int getDegradedStatusThreshold()
  {
    return degradedStatusThreshold;
  }

  /**
   * Compute the list of replication servers that are not any
   * more connected to this Replication Server and stop the
   * corresponding handlers.
   * @param newReplServers the list of the new replication servers configured.
   */
  private void disconnectRemovedReplicationServers(
      Collection<String> newReplServers)
  {
    Collection<String> serversToDisconnect = new ArrayList<String>();

    for (String server: replicationServers)
    {
      if (!newReplServers.contains(server))
      {
        try
        {
          // translate the server name into IP address
          // and keep the port number
          String[] host = server.split(":");
          serversToDisconnect.add(
              (InetAddress.getByName(host[0])).getHostAddress()
              + ":" + host[1]);
        }
        catch (IOException e)
        {
          Message message = ERR_COULD_NOT_SOLVE_HOSTNAME.get(server);
          logError(message);
        }
      }
    }

    if (serversToDisconnect.isEmpty())
      return;

    for (ReplicationServerDomain replicationServerDomain: baseDNs.values())
    {
      replicationServerDomain.stopReplicationServers(serversToDisconnect);
    }
  }

  /**
   * Retrieves a printable name for this Replication Server Instance.
   *
   * @return A printable name for this Replication Server Instance.
   */
  public String getMonitorInstanceName()
  {
    return "Replication Server " + replicationPort + " " + serverId;
  }

  /**
   * Retrieves the port used by this ReplicationServer.
   *
   * @return The port used by this ReplicationServer.
   */
  public int getReplicationPort()
  {
    return replicationPort;
  }

  // TODO: Remote monitor data cache lifetime is 500ms/should be configurable
  private long monitorDataLifeTime = 500;

  /* The date of the last time they have been elaborated */
  private long monitorDataLastBuildDate = 0;

  /* Search op on monitor data is processed by a worker thread.
   * Requests are sent to the other RS,and responses are received by the
   * listener threads.
   * The worker thread is awoke on this semaphore, or on timeout.
   */
  Semaphore remoteMonitorResponsesSemaphore = new Semaphore(0);

  /**
   * Trigger the computation of the Global Monitoring Data.
   * This should be called by all the MonitorProviders that need
   * the global monitoring data to be updated before they can
   * publish their information to cn=monitor.
   *
   * This method will trigger the update of all the global monitoring
   * information of all the base-DNs of this replication Server.
   *
   * @throws DirectoryException If the computation cannot be achieved.
   */
  public void computeMonitorData() throws DirectoryException
  {
    if (monitorDataLastBuildDate + monitorDataLifeTime > TimeThread.getTime())
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In " + getMonitorInstanceName() + " getRemoteMonitorData in cache");
      // The current data are still valid. No need to renew them.
      return;
    }

    remoteMonitorResponsesSemaphore.drainPermits();
    int count = 0;
    for (ReplicationServerDomain domain : baseDNs.values())
    {
      count += domain.initializeMonitorData();
    }

    // Wait for responses
    waitMonitorDataResponses(count);

    for (ReplicationServerDomain domain : baseDNs.values())
    {
      domain.completeMonitorData();
    }
  }

  /**
   * Wait for the expected count of received MonitorMsg.
   * @param expectedResponses The number of expected answers.
   * @throws DirectoryException When an error occurs.
   */
  private void waitMonitorDataResponses(int expectedResponses)
    throws DirectoryException
  {
    try
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In " + getMonitorInstanceName() + " baseDn=" +
          " waiting for " + expectedResponses + " expected monitor messages");

      boolean allPermitsAcquired =
        remoteMonitorResponsesSemaphore.tryAcquire(
        expectedResponses,
        (long) 5000, TimeUnit.MILLISECONDS);

      if (!allPermitsAcquired)
      {
        monitorDataLastBuildDate = TimeThread.getTime();
        logError(ERR_MISSING_REMOTE_MONITOR_DATA.get());
      // let's go on in best effort even with limited data received.
      } else
      {
        monitorDataLastBuildDate = TimeThread.getTime();
        if (debugEnabled())
          TRACER.debugInfo(
            "In " + getMonitorInstanceName() + " baseDn=" +
            " Successfully received all " + expectedResponses +
            " expected monitor messages");
      }
    } catch (Exception e)
    {
      logError(ERR_PROCESSING_REMOTE_MONITOR_DATA.get(e.getMessage()));
    }
  }


  /**
   * This should be called by each ReplicationServerDomain that receives
   * a response to a monitor request message.
   */
  public void responseReceived()
  {
    remoteMonitorResponsesSemaphore.release();
  }


  /**
   * This should be called when the Monitoring has failed and the
   * Worker thread that is waiting for the result should be awaken.
   */
  public void responseReceivedAll()
  {
    remoteMonitorResponsesSemaphore.notifyAll();
  }

  /**
   * Returns the number of domains managed by this replication server.
   * @return the number of domains managed.
   */
  public int getCacheSize()
  {
    return baseDNs.size();
  }

  /**
   * Create a new session to get the ECL.
   * @param msg The message that specifies the ECL request.
   * @return Returns the created session.
   * @throws DirectoryException When an error occurs.
   */
  public ExternalChangeLogSession createECLSession(StartECLSessionMsg msg)
  throws DirectoryException
  {
    ExternalChangeLogSessionImpl session =
      new ExternalChangeLogSessionImpl(this, msg);
    return session;
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
    int separator = server.lastIndexOf(':');
    if (separator == -1)
      return ;
    int port = Integer.parseInt(server.substring(separator + 1));
    localPorts.add(port);
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
   * This method allows to check if the Replication Server given
   * as the parameter is running in the local JVM.
   *
   * @param server   The Replication Server that should be checked.
   *
   * @return         a boolean indicating if the Replication Server given
   *                 as the parameter is running in the local JVM.
   */
  public static boolean isLocalReplicationServer(String server)
  {
    int separator = server.lastIndexOf(':');
    if (separator == -1)
      return false;
    int port = Integer.parseInt(server.substring(separator + 1));
    String hostname = server.substring(0, separator);
    try
    {
      InetAddress localAddr = InetAddress.getLocalHost();

      if (localPorts.contains(port)
          && (InetAddress.getByName(hostname).isLoopbackAddress() ||
              InetAddress.getByName(hostname).equals(localAddr)))
      {
        return true;
      }
      else
      {
        return false;
      }

    } catch (UnknownHostException e)
    {
      return false;
    }
  }

  private  ArrayList<String> excludedServiceIDs;
  /**
   * Excluded a list of domain from eligibility computation.
   * @param excludedServiceIDs the provided list of serviceIDs excluded from
   *                          the computation of eligibleCN.
   */
  public void disableEligibility(ArrayList<String> excludedServiceIDs)
  {
    this.excludedServiceIDs = excludedServiceIDs;
  }

  /**
   * Returns the eligible CN cross domains - relies on the eligible CN from
   * each domain.
   * @return the cross domain eligible CN.
   */
  public ChangeNumber getEligibleCN()
  {
    String debugLog = "";

    // traverse the domains and get the eligible CN from each domain
    // store the oldest one as the cross domain eligible CN
    ChangeNumber eligibleCN = null;
    Iterator<ReplicationServerDomain> rsdi = this.getDomainIterator();
    if (rsdi != null)
    {
      while (rsdi.hasNext())
      {
        ReplicationServerDomain domain = rsdi.next();
        if ((excludedServiceIDs != null) &&
            excludedServiceIDs.contains(domain.getBaseDn()))
          continue;

        ChangeNumber domainEligibleCN = domain.getEligibleCN();
        String dates = "";
        if (domainEligibleCN != null)
        {
          if ((eligibleCN == null) || (domainEligibleCN.older(eligibleCN)))
          {
            eligibleCN = domainEligibleCN;
          }
          dates = new Date(domainEligibleCN.getTime()).toString();
        }
        debugLog += "[dn=" + domain.getBaseDn()
             + "] [eligibleCN=" + domainEligibleCN + ", " + dates + "]";
      }
    }

    if (eligibleCN==null)
    {
      eligibleCN = new ChangeNumber(0, 0, 0);
    }

    if (debugEnabled())
      TRACER.debugInfo("In " + this +
        " getEligibleCN() ends with " +
        " the following domainEligibleCN for each domain :" + debugLog +
        " thus CrossDomainEligibleCN=" + eligibleCN +
        "  ts=" +
        (eligibleCN!=null?
        new Date(eligibleCN.getTime()).toString(): null));

    return eligibleCN;
  }

  /**
   * Get or create a handler on a Db on DraftCN for external changelog.
   * @return the handler.
   * @throws DirectoryException when needed.
   */
  public synchronized DraftCNDbHandler getDraftCNDbHandler()
  throws DirectoryException
  {
    try
    {
      if (draftCNDbHandler == null)
      {
        draftCNDbHandler = new DraftCNDbHandler(this, this.dbEnv);
        if (draftCNDbHandler == null)
          return null;
        this.lastGeneratedDraftCN = getLastDraftChangeNumber();
      }
      return draftCNDbHandler;
    }
    catch (Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_DRAFT_CHANGENUMBER_DATABASE.get(""));
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
          mb.toMessage(), e);
    }
  }

  /**
   * Get the value of the first draft change number, 0 when db is empty.
   * @return the first value.
   */
  public int getFirstDraftChangeNumber()
  {
    int first=0;
    if (draftCNDbHandler != null)
      first = draftCNDbHandler.getFirstKey();
    return first;
  }

  /**
   * Get the value of the last draft change number, 0 when db is empty.
   * @return the last value.
   */
  public int getLastDraftChangeNumber()
  {
    int last=0;
    if (draftCNDbHandler != null)
      last = draftCNDbHandler.getLastKey();
    return last;
  }

  /**
   * Generate a new Draft ChangeNumber.
   * @return The generated Draft ChangeNUmber
   */
  synchronized public int getNewDraftCN()
  {
    return ++lastGeneratedDraftCN;
  }

  /**
   * Get first and last DraftCN.
   * @param crossDomainEligibleCN The provided crossDomainEligibleCN used as
   *        the upper limit for the lastDraftCN
   * @param excludedServiceIDs The serviceIDs that are excluded from the ECL.
   * @return The first and last draftCN.
   * @throws DirectoryException a.
   */
  public int[] getECLDraftCNLimits(
      ChangeNumber crossDomainEligibleCN,
      ArrayList<String> excludedServiceIDs)
  throws DirectoryException
  {
    /* The content of the DraftCNdb depends on the SEARCH operations done before
     * requesting the DraftCN. If no operations, DraftCNdb is empty.
     * The limits we want to get are the "potential" limits if a request was
     * done, the DraftCNdb is probably not complete to do that.
     *
     * The first DraftCN is :
     *  - the first record from the DraftCNdb
     *  - if none because DraftCNdb empty,
     *      then
     *        if no change in replchangelog then return 0
     *        else return 1 (DraftCN that WILL be returned to next search)
     *
     * The last DraftCN is :
     *  - initialized with the last record from the DraftCNdb (0 if none)
     *    and consider the genState associated
     *  - to the last DraftCN, we add the count of updates in the replchangelog
     *     FROM that genState TO the crossDomainEligibleCN
     *     (this diff is done domain by domain)
     */

    int firstDraftCN;
    int lastDraftCN;
    boolean DraftCNdbIsEmpty;
    DraftCNDbHandler draftCNDbH = this.getDraftCNDbHandler();

    // Get the first DraftCN from the DraftCNdb
    firstDraftCN = draftCNDbH.getFirstKey();
    HashMap<String,ServerState> domainsServerStateForLastSeqnum = null;
    if (firstDraftCN < 1)
    {
      DraftCNdbIsEmpty=true;
      firstDraftCN = 0;
      lastDraftCN = 0;
    }
    else
    {
      DraftCNdbIsEmpty=false;

      // Get the last DraftCN from the DraftCNdb
      lastDraftCN = draftCNDbH.getLastKey();

      // Get the generalized state associated with the current last DraftCN
      // and initializes from it the startStates table
      String lastSeqnumGenState = draftCNDbH.getValue(lastDraftCN);
      if ((lastSeqnumGenState != null) && (lastSeqnumGenState.length()>0))
      {
        domainsServerStateForLastSeqnum = MultiDomainServerState.
          splitGenStateToServerStates(lastSeqnumGenState);
      }
    }

    // Domain by domain
    Iterator<ReplicationServerDomain> rsdi = this.getDomainIterator();
    if (rsdi != null)
    {
      while (rsdi.hasNext())
      {
        // process a domain
        ReplicationServerDomain rsd = rsdi.next();

        if (excludedServiceIDs.contains(rsd.getBaseDn()))
          continue;

        // for this domain, have the state in the replchangelog
        // where the last DraftCN update is
        ServerState domainServerStateForLastSeqnum;
        if ((domainsServerStateForLastSeqnum == null) ||
            (domainsServerStateForLastSeqnum.get(rsd.getBaseDn())==null))
        {
          domainServerStateForLastSeqnum = new ServerState();
        }
        else
        {
          domainServerStateForLastSeqnum =
            domainsServerStateForLastSeqnum.get(rsd.getBaseDn());
        }

        // Count the number of (eligible) changes from this place
        // to the eligible CN (cross server)
        long ec = rsd.getEligibleCount(
            domainServerStateForLastSeqnum, crossDomainEligibleCN);

        // the state from which we started is the one BEFORE the lastdraftCN
        // so we must decrement 1 to the EligibleCount
        if ((ec>0) && (DraftCNdbIsEmpty==false))
          ec--;

        // cumulates on domains
        lastDraftCN += ec;

        // DraftCN Db is empty and there are eligible updates in the replication
        // changelog then init first DraftCN
        if ((ec>0) && (firstDraftCN==0))
          firstDraftCN = 1;
      }
    }
    return new int[]{firstDraftCN, lastDraftCN};
  }

  /**
   * Returns the last (newest) cookie value.
   * @param excludedServiceIDs The list of serviceIDs excluded from ECL.
   * @return the last cookie value.
   */
  public MultiDomainServerState getLastECLCookie(
    ArrayList<String> excludedServiceIDs)
  {
    MultiDomainServerState result = new MultiDomainServerState();
    // Initialize start state for  all running domains with empty state
    Iterator<ReplicationServerDomain> rsdk = this.getDomainIterator();
    if (rsdk != null)
    {
      while (rsdk.hasNext())
      {
        // process a domain
        ReplicationServerDomain rsd = rsdk.next();

        if ((excludedServiceIDs!=null)
            && (excludedServiceIDs.contains(rsd.getBaseDn())))
          continue;

        if (rsd.getDbServerState().isEmpty())
          continue;

        result.update(rsd.getBaseDn(), rsd.getEligibleState(
            getEligibleCN()));
      }
    }
    return result;
  }

  /**
   * Gets the weight.
   * @return the weight
   */
  public int getWeight()
  {
    return weight;
  }

}
