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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackupTaskListener;
import org.opends.server.api.ExportTaskListener;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.RestoreTaskListener;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.util.LDIFReader;

import com.sleepycat.je.DatabaseException;

/**
 * ReplicationServer Listener.
 *
 * This singleton is the main object of the replication server
 * It waits for the incoming connections and create listener
 * and publisher objects for
 * connection with LDAP servers and with replication servers
 *
 * It is responsible for creating the replication server cache and managing it
 */
public class ReplicationServer extends MonitorProvider<MonitorProviderCfg>
  implements Runnable, ConfigurationChangeListener<ReplicationServerCfg>,
             BackupTaskListener, RestoreTaskListener, ImportTaskListener,
             ExportTaskListener
{
  private short serverId;
  private String serverURL;

  private ServerSocket listenSocket;
  private Thread listenThread;
  private Thread connectThread;

  /* The list of replication servers configured by the administrator */
  private Collection<String> replicationServers;

  /* This table is used to store the list of dn for which we are currently
   * handling servers.
   */
  private HashMap<DN, ReplicationCache> baseDNs =
          new HashMap<DN, ReplicationCache>();

  private String localURL = "null";
  private boolean shutdown = false;
  private short replicationServerId;
  private ReplicationDbEnv dbEnv;
  private int rcvWindow;
  private int queueSize;
  private String dbDirname = null;
  private long trimAge; // the time (in sec) after which the  changes must
                        // be deleted from the persistent storage.
  private int replicationPort;
  private boolean stopListen = false;
  private ReplSessionSecurity replSessionSecurity;

  // For the backend associated to this replication server,
  // DN of the config entry of the backend
  private DN backendConfigEntryDN;
  // ID of the backend
  private static final String backendId = "replicationChanges";

  // At startup, the listen thread wait on this flag for the connet
  // thread to look for other servers in the topology.
  // TODO when a replication server is out of date (has old changes
  // to receive from other servers, the listen thread should not accept
  // connection from ldap servers. (issue 1302)
  private boolean connectedInTopology = false;
  private final Object connectedInTopologyLock = new Object();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new Replication server using the provided configuration entry.
   *
   * @param configuration The configuration of this replication server.
   * @throws ConfigException When Configuration is invalid.
   */
  public ReplicationServer(ReplicationServerCfg configuration)
    throws ConfigException
  {
    super("Replication Server" + configuration.getReplicationPort());

    replicationPort = configuration.getReplicationPort();
    replicationServerId = (short) configuration.getReplicationServerId();
    replicationServers = configuration.getReplicationServer();
    if (replicationServers == null)
      replicationServers = new ArrayList<String>();
    queueSize = configuration.getQueueSize();
    trimAge = configuration.getReplicationPurgeDelay();
    dbDirname = configuration.getReplicationDbDirectory();
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

    replSessionSecurity = new ReplSessionSecurity(configuration);
    initialize(replicationServerId, replicationPort);
    configuration.addChangeListener(this);
    DirectoryServer.registerMonitorProvider(this);

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
  }


  /**
   * The run method for the Listen thread.
   * This thread accept incoming connections on the replication server
   * ports from other replication servers or from LDAP servers
   * and spawn further thread responsible for handling those connections
   */

  void runListen()
  {
    Socket newSocket;

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

      try
      {
        newSocket =  listenSocket.accept();
        newSocket.setReceiveBufferSize(1000000);
        newSocket.setTcpNoDelay(true);
        newSocket.setKeepAlive(true);
        ProtocolSession session =
             replSessionSecurity.createServerSession(newSocket);
        ServerHandler handler = new ServerHandler(session, queueSize);
        handler.start(null, serverId, serverURL, rcvWindow,
                      false, this);
      }
      catch (Exception e)
      {
        // The socket has probably been closed as part of the
        // shutdown or changing the port number process.
        // just log debug information and loop.
        Message message = ERR_EXCEPTION_LISTENING.get(e.getLocalizedMessage());
        logError(message);
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
      for (ReplicationCache replicationCache: baseDNs.values())
      {
        Set<String> connectedReplServers = replicationCache.getChangelogs();
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

            if ((serverAddress.compareTo("127.0.0.1:" + replicationPort) != 0)
                && (serverAddress.compareTo(this.localURL) != 0)
                && (!connectedReplServers.contains(serverAddress)))
            {
              this.connect(serverURL, replicationCache.getBaseDn());
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
        synchronized (this)
        {
          /* check if we are connected every second */
          int randomizer = (int) Math.random()*100;
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
   * @param serverURL  The address and port for the server, separated by a
   *                    colon.
   * @param baseDn     The baseDn of the connection
   */
  private void connect(String serverURL, DN baseDn)
  {
    int separator = serverURL.lastIndexOf(':');
    String port = serverURL.substring(separator + 1);
    String hostname = serverURL.substring(0, separator);
    boolean sslEncryption = replSessionSecurity.isSslEncryption(serverURL);

    if (debugEnabled())
      TRACER.debugInfo("RS " + this.getMonitorInstanceName() +
               " connects to " + serverURL);

    try
    {
      InetSocketAddress ServerAddr = new InetSocketAddress(
                     InetAddress.getByName(hostname), Integer.parseInt(port));
      Socket socket = new Socket();
      socket.setReceiveBufferSize(1000000);
      socket.setTcpNoDelay(true);
      socket.connect(ServerAddr, 500);

      ServerHandler handler = new ServerHandler(
           replSessionSecurity.createClientSession(serverURL, socket),
           queueSize);
      handler.start(baseDn, serverId, this.serverURL, rcvWindow,
                    sslEncryption, this);
    }
    catch (Exception e)
    {
      // ignore
    }

  }

  /**
   * initialization function for the replicationServer.
   *
   * @param  changelogId       The unique identifier for this replicationServer.
   * @param  changelogPort     The port on which the replicationServer should
   *                           listen.
   *
   */
  private void initialize(short changelogId, int changelogPort)
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
       * create replicationServer cache
       */
      serverId = changelogId;

      /*
       * Open replicationServer socket
       */
      String localhostname = InetAddress.getLocalHost().getHostName();
      String localAdddress = InetAddress.getLocalHost().getHostAddress();
      serverURL = localhostname + ":" + String.valueOf(changelogPort);
      localURL = localAdddress + ":" + String.valueOf(changelogPort);
      listenSocket = new ServerSocket();
      listenSocket.setReceiveBufferSize(1000000);
      listenSocket.bind(new InetSocketAddress(changelogPort));

      /*
       * creates working threads
       * We must first connect, then start to listen.
       */
      if (debugEnabled())
        TRACER.debugInfo("RS " +getMonitorInstanceName()+
            " creates connect threads");
      connectThread =
        new ReplicationServerConnectThread("Replication Server Connect", this);
      connectThread.start();

      // FIXME : Is it better to have the time to receive the ReplServerInfo
      // from all the other replication servers since this info is necessary
      // to route an early received total update request.
      try { Thread.sleep(300);} catch(Exception e) {}
      if (debugEnabled())
        TRACER.debugInfo("RS " +getMonitorInstanceName()+
            " creates listen threads");

      listenThread =
        new ReplicationServerListenThread("Replication Server Listener", this);
      listenThread.start();

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
    }
  }

  /**
   * Get the ReplicationCache associated to the base DN given in parameter.
   *
   * @param baseDn The base Dn for which the ReplicationCache must be returned.
   * @param create Specifies whether to create the ReplicationCache if it does
   *        not already exist.
   * @return The ReplicationCache associated to the base DN given in parameter.
   */
  public ReplicationCache getReplicationCache(DN baseDn, boolean create)
  {
    ReplicationCache replicationCache;

    synchronized (baseDNs)
    {
      replicationCache = baseDNs.get(baseDn);
      if ((replicationCache == null) && (create))
      {
        replicationCache = new ReplicationCache(baseDn, this);
        baseDNs.put(baseDn, replicationCache);
      }
    }

    return replicationCache;
  }

  /**
   * Shutdown the Replication Server service and all its connections.
   */
  public void shutdown()
  {
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
    for (ReplicationCache replicationCache : baseDNs.values())
    {
      replicationCache.shutdown();
    }

    if (dbEnv != null)
    {
      dbEnv.shutdown();
    }
    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
}


  /**
   * Creates a new DB handler for this ReplicationServer and the serverId and
   * DN given in parameter.
   *
   * @param id The serverId for which the dbHandler must be created.
   * @param baseDn The DN for which the dbHandler muste be created.
   * @param generationId The generationId for this server and this domain.
   * @return The new DB handler for this ReplicationServer and the serverId and
   *         DN given in parameter.
   * @throws DatabaseException in case of underlying database problem.
   */
  public DbHandler newDbHandler(short id, DN baseDn, long generationId)
  throws DatabaseException
  {
    return new DbHandler(id, baseDn, this, dbEnv, generationId);
  }

  /**
   * Clears the generationId for the domain related to the provided baseDn.
   * @param  baseDn The baseDn for which to delete the generationId.
   * @throws DatabaseException When it occurs.
   */
  public void clearGenerationId(DN baseDn)
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
    return trimAge * 1000;
  }

  /**
   * Check if the provided configuration is acceptable for add.
   *
   * @param configuration The configuration to check.
   * @param unacceptableReasons When the configuration is not acceptable, this
   *                            table is use to return the reasons why this
   *                            configuration is not acceptbale.
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
    // Changing those properties don't need specific code.
    // They will be applied for next connections.
    replicationServers = configuration.getReplicationServer();
    if (replicationServers == null)
      replicationServers = new ArrayList<String>();
    queueSize = configuration.getQueueSize();
    trimAge = configuration.getReplicationPurgeDelay();
    rcvWindow = configuration.getWindowSize();

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
        listenSocket.setReceiveBufferSize(1000000);
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
        new ConfigChangeResult(ResultCode.OPERATIONS_ERROR, false);
      }
      catch (InterruptedException e)
      {
        Message message = ERR_COULD_NOT_STOP_LISTEN_THREAD.get(e.toString());
        logError(message);
        new ConfigChangeResult(ResultCode.OPERATIONS_ERROR, false);
      }
    }

    if ((configuration.getReplicationDbDirectory() != null) &&
        (!dbDirname.equals(configuration.getReplicationDbDirectory())))
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
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuraiton)
  {
    // Nothing to do for now
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorInstanceName()
  {
    return "Replication Server " + this.replicationPort + " "
           + replicationServerId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getUpdateInterval()
  {
    /* we don't wont to do polling on this monitor */
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateMonitorData()
  {
    // As long as getUpdateInterval() returns 0, this will never get called

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    /*
     * publish the server id and the port number.
     */
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    attributes.add(new Attribute("replication server id",
        String.valueOf(serverId)));
    attributes.add(new Attribute("replication server port",
        String.valueOf(replicationPort)));

    /*
     * Add all the base DNs that are known by this replication server.
     */
    AttributeType baseType=
      DirectoryServer.getAttributeType("base-dn", true);
    LinkedHashSet<AttributeValue> baseValues =
      new LinkedHashSet<AttributeValue>();
    for (DN base : baseDNs.keySet())
    {
      baseValues.add(new AttributeValue(baseType, base. toString()));
    }

    Attribute bases = new Attribute(baseType, "base-dn", baseValues);
    attributes.add(bases);

    // Publish to monitor the generation ID by domain
    AttributeType generationIdType=
      DirectoryServer.getAttributeType("base-dn-generation-id", true);
    LinkedHashSet<AttributeValue> generationIdValues =
      new LinkedHashSet<AttributeValue>();
    for (DN base : baseDNs.keySet())
    {
      long generationId=-1;
      ReplicationCache cache = getReplicationCache(base, false);
      if (cache != null)
        generationId = cache.getGenerationId();
      generationIdValues.add(new AttributeValue(generationIdType,
          base.toString() + " " + generationId));
    }
    Attribute generationIds = new Attribute(generationIdType, "generation-id",
        generationIdValues);
    attributes.add(generationIds);

    return attributes;
  }

  /**
   * Get the value of generationId for the replication domain
   * associated with the provided baseDN.
   *
   * @param baseDN The baseDN of the domain.
   * @return The value of the generationID.
   */
  public long getGenerationId(DN baseDN)
  {
    ReplicationCache rc = this.getReplicationCache(baseDN, false);
    if (rc!=null)
      return rc.getGenerationId();
    return -1;
  }

  /**
   * Get the serverId for this replication server.
   *
   * @return The value of the serverId.
   *
   */
  public short getServerId()
  {
    return serverId;
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
          "objectClass: ds-cfg-je-backend",
          "ds-cfg-backend-base-dn: dc="+backendId,
          "ds-cfg-backend-enabled: true",
          "ds-cfg-backend-writability-mode: enabled",
          "ds-cfg-backend-class: " +
            "org.opends.server.replication.server.ReplicationBackend",
          "ds-cfg-backend-id: " + backendId,
          "ds-cfg-backend-import-temp-directory: importTmp",
          "ds-cfg-backend-directory: " + getFileForPath(dbDirname));

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
      if (!DirectoryServer.getConfigHandler().entryExists(backendConfigEntryDN))
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
      initialize(this.replicationServerId, this.replicationPort);
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
      // Retrieves the backend related to this domain
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
   * Returns an iterator on the list of replicationCache.
   * Returns null if none.
   * @return the iterator.
   */
  public Iterator<ReplicationCache> getCacheIterator()
  {
    if (!baseDNs.isEmpty())
      return baseDNs.values().iterator();
    else
      return null;
  }
}
