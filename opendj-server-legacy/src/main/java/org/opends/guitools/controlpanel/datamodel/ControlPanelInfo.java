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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.opends.admin.ads.util.PreferredConnection.Type.*;
import static org.opends.server.tools.ConfigureWindowsService.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.browser.LDAPConnectionPool;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor.ServerStatus;
import org.opends.guitools.controlpanel.event.BackendPopulatedEvent;
import org.opends.guitools.controlpanel.event.BackendPopulatedListener;
import org.opends.guitools.controlpanel.event.BackupCreatedEvent;
import org.opends.guitools.controlpanel.event.BackupCreatedListener;
import org.opends.guitools.controlpanel.event.ConfigChangeListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedListener;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.task.Task.State;
import org.opends.guitools.controlpanel.task.Task.Type;
import org.opends.guitools.controlpanel.util.ConfigFromConnection;
import org.opends.guitools.controlpanel.util.ConfigFromFile;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.HostPort;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.CliConstants;

/**
 * This is the classes that is shared among all the different places in the
 * Control Panel.  It contains information about the server status and
 * configuration and some objects that are shared everywhere.
 */
public class ControlPanelInfo
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static boolean mustDeregisterConfig;
  private static ControlPanelInfo instance;

  private final Set<Task> tasks = new HashSet<>();
  private ConnectionWrapper connWrapper;
  private ConnectionWrapper userDataConn;
  private final LDAPConnectionPool connectionPool = new LDAPConnectionPool();
  /** Used by the browsers. */
  private final IconPool iconPool = new IconPool();

  private long poolingPeriod = 20000;
  private Thread poolingThread;
  private boolean stopPooling;
  private boolean pooling;

  private ApplicationTrustManager trustManager;
  private int connectTimeout = CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT;
  private ConnectionProtocolPolicy connectionPolicy =
    ConnectionProtocolPolicy.USE_MOST_SECURE_AVAILABLE;

  private ServerDescriptor serverDesc;
  private HostPort ldapHostPort;
  private HostPort startTlsHostPort;
  private HostPort ldapsHostPort;
  private HostPort adminConnectorHostPort;
  private HostPort localAdminConnectorHostPort;
  private DN lastWorkingBindDN;
  private String lastWorkingBindPwd;
  private HostPort lastRemoteHostPort;

  private boolean isLocal = true;

  private final Set<AbstractIndexDescriptor> modifiedIndexes = new HashSet<>();
  private final Set<ConfigChangeListener> configListeners = new LinkedHashSet<>();
  private final Set<BackupCreatedListener> backupListeners = new LinkedHashSet<>();
  private final Set<BackendPopulatedListener> backendPopulatedListeners = new LinkedHashSet<>();
  private final Set<IndexModifiedListener> indexListeners = new LinkedHashSet<>();

  /** Default constructor. */
  private ControlPanelInfo()
  {
  }

  /**
   * Returns a singleton for this instance.
   * @return the control panel info.
   */
  public static ControlPanelInfo getInstance()
  {
    if (instance == null)
    {
      instance = new ControlPanelInfo();
      try
      {
        instance.setTrustManager(
            new ApplicationTrustManager(UIKeyStore.getInstance()));
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw("Error retrieving UI key store: "+t, t));
        instance.setTrustManager(new ApplicationTrustManager(null));
      }
    }
    return instance;
  }

  /**
   * Returns the last ServerDescriptor that has been retrieved.
   * @return the last ServerDescriptor that has been retrieved.
   */
  public ServerDescriptor getServerDescriptor()
  {
    return serverDesc;
  }

  /**
   * Returns the list of tasks.
   * @return the list of tasks.
   */
  public Set<Task> getTasks()
  {
    return Collections.unmodifiableSet(tasks);
  }

  /**
   * Registers a task.  The Control Panel creates a task every time an operation
   * is made and they are stored here.
   * @param task the task to be registered.
   */
  public void registerTask(Task task)
  {
    tasks.add(task);
  }

  /**
   * Unregisters a task.
   * @param task the task to be unregistered.
   */
  private void unregisterTask(Task task)
  {
    tasks.remove(task);
  }

  /**
   * Tells whether an index must be reindexed or not.
   * @param index the index.
   * @return {@code true} if the index must be reindexed, {@code false} otherwise.
   */
  public boolean mustReindex(AbstractIndexDescriptor index)
  {
    for (AbstractIndexDescriptor i : modifiedIndexes)
    {
      if (i.getName().equals(index.getName()) &&
          i.getBackend().getBackendID().equals(
              index.getBackend().getBackendID()))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Registers an index as modified.  This is used by the panels to be able
   * to inform the user that a rebuild of the index is required.
   * @param index the index.
   */
  public void registerModifiedIndex(AbstractIndexDescriptor index)
  {
    modifiedIndexes.add(index);
    indexModified(index);
  }

  /**
   * Unregisters a modified index.
   * @param index the index.
   * @return {@code true} if the index is found in the list of modified
   * indexes, {@code false} otherwise.
   */
  public boolean unregisterModifiedIndex(AbstractIndexDescriptor index)
  {
    // We might have stored indexes whose configuration has changed, just remove
    // them if they have the same name, are of the same type and are defined in
    // the same backend.
    Set<AbstractIndexDescriptor> toRemove = new HashSet<>();
    for (AbstractIndexDescriptor i : modifiedIndexes)
    {
      if (i.getName().equalsIgnoreCase(index.getName()) &&
          i.getBackend().getBackendID().equalsIgnoreCase(
              index.getBackend().getBackendID()) &&
          i.getClass().equals(index.getClass()))
      {
        toRemove.add(i);
      }
    }

    if (!toRemove.isEmpty())
    {
      boolean returnValue = modifiedIndexes.removeAll(toRemove);
      indexModified(toRemove.iterator().next());
      return returnValue;
    }
    return false;
  }

  /**
   * Unregisters all the modified indexes on a given backend.
   * @param backendName the name of the backend.
   */
  public void unregisterModifiedIndexesInBackend(String backendName)
  {
    HashSet<AbstractIndexDescriptor> toDelete = new HashSet<>();
    for (AbstractIndexDescriptor index : modifiedIndexes)
    {
      // Compare only the Backend ID, since the backend object attached to
      // the registered index might have changed (for instance the number of
      // entries).  Relying on the backend ID to identify the backend is safe.
      if (index.getBackend().getBackendID().equalsIgnoreCase(backendName))
      {
        toDelete.add(index);
      }
    }
    modifiedIndexes.removeAll(toDelete);
    for (BackendDescriptor backend : getServerDescriptor().getBackends())
    {
      if (backend.getBackendID().equals(backendName))
      {
        IndexModifiedEvent ev = new IndexModifiedEvent(backend);
        for (IndexModifiedListener listener : indexListeners)
        {
          listener.backendIndexesModified(ev);
        }
        break;
      }
    }
  }

  /**
   * Returns a collection with all the modified indexes.
   * @return a collection with all the modified indexes.
   */
  public Collection<AbstractIndexDescriptor> getModifiedIndexes()
  {
    return Collections.unmodifiableCollection(modifiedIndexes);
  }

  /**
   * Sets the dir context to be used by the ControlPanelInfo to retrieve
   * monitoring and configuration information.
   * @param connWrapper the connection.
   */
  public void setConnection(ConnectionWrapper connWrapper)
  {
    this.connWrapper = connWrapper;
    if (connWrapper != null)
    {
      lastWorkingBindDN = connWrapper.getBindDn();
      lastWorkingBindPwd = connWrapper.getBindPassword();
      lastRemoteHostPort = connWrapper.getHostPort();
    }
  }

  /**
   * Returns the connection to be used by the ControlPanelInfo to retrieve
   * monitoring and configuration information.
   * @return the connection to be used by the ControlPanelInfo to retrieve
   * monitoring and configuration information.
   */
  public ConnectionWrapper getConnection()
  {
    return connWrapper;
  }

  /**
   * Sets the connection to be used by the ControlPanelInfo to retrieve user data.
   *
   * @param conn
   *          the connection.
   * @throws LdapException
   *           if there is a problem updating the connection pool.
   */
  public void setUserDataConnection(ConnectionWrapper conn) throws LdapException
  {
    if (userDataConn != null)
    {
      unregisterConnection(connectionPool, conn);
    }
    this.userDataConn = conn;
    if (conn != null)
    {
      connectionPool.registerConnection(new ConnectionWrapper(userDataConn));
    }
  }

  /**
   * Returns the connection to be used by the ControlPanelInfo to retrieve user data.
   *
   * @return the connection to be used by the ControlPanelInfo to retrieve user data.
   */
  public ConnectionWrapper getUserDataConnection()
  {
    return userDataConn;
  }

  /**
   * Informs that a backup has been created.  The method will notify to all
   * the backup listeners that a backup has been created.
   * @param newBackup the new created backup.
   */
  public void backupCreated(BackupDescriptor newBackup)
  {
    BackupCreatedEvent ev = new BackupCreatedEvent(newBackup);
    for (BackupCreatedListener listener : backupListeners)
    {
      listener.backupCreated(ev);
    }
  }

  /**
   * Informs that a set of backends have been populated.  The method will notify
   * to all the backend populated listeners.
   * @param backends the populated backends.
   */
  public void backendPopulated(Set<BackendDescriptor> backends)
  {
    BackendPopulatedEvent ev = new BackendPopulatedEvent(backends);
    for (BackendPopulatedListener listener : backendPopulatedListeners)
    {
      listener.backendPopulated(ev);
    }
  }

  /**
   * Informs that an index has been modified.  The method will notify to all
   * the index listeners that an index has been modified.
   * @param modifiedIndex the modified index.
   */
  private void indexModified(AbstractIndexDescriptor modifiedIndex)
  {
    IndexModifiedEvent ev = new IndexModifiedEvent(modifiedIndex);
    for (IndexModifiedListener listener : indexListeners)
    {
      listener.indexModified(ev);
    }
  }

  /**
   * Updates the contents of the server descriptor with the provider reader.
   * @param reader the configuration reader.
   * @param desc the server descriptor.
   */
  private void updateServerDescriptor(ConfigReader reader, ServerDescriptor desc)
  {
    desc.setExceptions(reader.getExceptions());
    desc.setAdministrativeUsers(reader.getAdministrativeUsers());
    desc.setBackends(reader.getBackends());
    desc.setConnectionHandlers(reader.getConnectionHandlers());
    desc.setAdminConnector(reader.getAdminConnector());
    desc.setSchema(reader.getSchema());
    desc.setSchemaEnabled(reader.isSchemaEnabled());
  }

  /** Regenerates the last found ServerDescriptor object. */
  public synchronized void regenerateDescriptor()
  {
    ServerDescriptor desc = new ServerDescriptor();
    desc.setIsLocal(isLocal);
    if (isLocal)
    {
      desc.setOpenDJVersion(DynamicConstants.FULL_VERSION_STRING);
      String installPath = Utilities.getInstallPathFromClasspath();
      desc.setInstallPath(installPath);
      desc.setInstancePath(Utils.getInstancePathFromInstallPath(installPath));
      desc.setWindowsServiceEnabled(isWindows() && serviceState() == SERVICE_STATE_ENABLED);
    }
    else if (lastRemoteHostPort != null)
    {
      desc.setHostname(lastRemoteHostPort.getHost());
    }

    ConfigReader reader;
    ServerStatus status = getStatus(desc);
    if (status != null)
    {
      desc.setStatus(status);
      if (status == ServerStatus.STOPPING)
      {
        StaticUtils.close(connWrapper);
        connWrapper = null;
        if (userDataConn != null)
        {
          unregisterConnection(connectionPool, null);
          StaticUtils.close(userDataConn);
          userDataConn = null;
        }
      }
      if (isLocal)
      {
        reader = newLocalConfigReader();
      }
      else
      {
        reader = null;
      }
      desc.setAuthenticated(false);
    }
    else if (!isLocal ||
        Utilities.isServerRunning(new File(desc.getInstancePath())))
    {
      desc.setStatus(ServerStatus.STARTED);

      if (connWrapper == null && lastWorkingBindDN != null)
      {
        // Try with previous credentials.
        try
        {
          if (isLocal)
          {
            connWrapper = Utilities.getAdminConnection(this, lastWorkingBindDN, lastWorkingBindPwd);
          }
          else if (lastRemoteHostPort != null)
          {
            connWrapper = new ConnectionWrapper(
                lastRemoteHostPort, LDAPS, lastWorkingBindDN, lastWorkingBindPwd,
                getConnectTimeout(), getTrustManager());
          }
        }
        catch (ConfigReadException | IOException ignored)
        {
          // Ignore: we will ask the user for credentials.
        }
      }

      if (connWrapper == null)
      {
        if (isLocal)
        {
          reader = newLocalConfigReader();
        }
        else
        {
          reader = null;
          desc.setStatus(ServerStatus.NOT_CONNECTED_TO_REMOTE);
        }
      }
      else
      {
        Utilities.initializeConfigurationFramework();
        reader = newRemoteConfigReader();

        boolean connectionWorks = checkConnections(connWrapper, userDataConn);
        if (!connectionWorks)
        {
          if (isLocal)
          {
            reader = newLocalConfigReader();
          }
          else
          {
            reader = null;
            desc.setStatus(ServerStatus.NOT_CONNECTED_TO_REMOTE);
          }
          StaticUtils.close(connWrapper);
          this.connWrapper = null;
          unregisterConnection(connectionPool, connWrapper);
          StaticUtils.close(userDataConn);
          userDataConn = null;
        }
      }

      if (reader != null)
      {
        desc.setAuthenticated(reader instanceof ConfigFromConnection);
        desc.setJavaVersion(reader.getJavaVersion());
        desc.setOpenConnections(reader.getOpenConnections());
        desc.setTaskEntries(reader.getTaskEntries());
        if (reader instanceof ConfigFromConnection)
        {
          ConfigFromConnection rCtx = (ConfigFromConnection)reader;
          desc.setRootMonitor(rCtx.getRootMonitor());
          desc.setEntryCachesMonitor(rCtx.getEntryCaches());
          desc.setJvmMemoryUsageMonitor(rCtx.getJvmMemoryUsage());
          desc.setSystemInformationMonitor(rCtx.getSystemInformation());
          desc.setWorkQueueMonitor(rCtx.getWorkQueue());
          desc.setOpenDJVersion(rCtx.getVersionMonitor().parseAttribute("fullVersion").asString());
          String installPath = rCtx.getSystemInformation().parseAttribute("installPath").asString();
          if (installPath != null)
          {
            desc.setInstallPath(installPath);
          }
          String instancePath = rCtx.getSystemInformation().parseAttribute("instancePath").asString();
          if (instancePath != null)
          {
            desc.setInstancePath(instancePath);
          }
        }
      }
    }
    else
    {
      desc.setStatus(ServerStatus.STOPPED);
      desc.setAuthenticated(false);
      reader = newLocalConfigReader();
    }
    if (reader != null)
    {
      updateServerDescriptor(reader, desc);
    }

    if (serverDesc == null || !serverDesc.equals(desc))
    {
      serverDesc = desc;
      ldapHostPort = getHostPort(serverDesc, ConnectionHandlerDescriptor.Protocol.LDAP);
      ldapsHostPort = getHostPort(serverDesc, ConnectionHandlerDescriptor.Protocol.LDAPS);
      adminConnectorHostPort = getAdminConnectorHostPort(serverDesc);
      if (serverDesc.isLocal())
      {
        localAdminConnectorHostPort = adminConnectorHostPort;
      }
      startTlsHostPort = getHostPort(serverDesc, ConnectionHandlerDescriptor.Protocol.LDAP_STARTTLS);
      ConfigurationChangeEvent ev = new ConfigurationChangeEvent(this, desc);
      for (ConfigChangeListener listener : configListeners)
      {
        listener.configurationChanged(ev);
      }
    }
  }

  private ConfigReader newRemoteConfigReader()
  {
    ConfigFromConnection reader = new ConfigFromConnection();
    reader.setIsLocal(isLocal);
    reader.readConfiguration(connWrapper);
    return reader;
  }

  private ConfigReader newLocalConfigReader()
  {
    ConfigFromFile reader = new ConfigFromFile();
    reader.readConfiguration();
    return reader;
  }

  private ServerStatus getStatus(ServerDescriptor desc)
  {
    ServerStatus status = null;
    for (Task task : getTasks())
    {
      if (task.getType() == Type.START_SERVER
          && task.getState() == State.RUNNING
          && isRunningOnServer(desc, task))
      {
        status = ServerStatus.STARTING;
      }
      else if (task.getType() == Type.STOP_SERVER && task.getState() == State.RUNNING
          && isRunningOnServer(desc, task))
      {
        status = ServerStatus.STOPPING;
      }
    }
    return status;
  }

  private void unregisterConnection(LDAPConnectionPool connectionPool, ConnectionWrapper userDataConn)
  {
    if (connectionPool.isConnectionRegistered(userDataConn))
    {
      try
      {
        connectionPool.unregisterConnection(userDataConn);
      }
      catch (Throwable t)
      {
      }
    }
  }

  /**
   * Adds a configuration change listener.
   * @param listener the listener.
   */
  public void addConfigChangeListener(ConfigChangeListener listener)
  {
    configListeners.add(listener);
  }

  /**
   * Removes a configuration change listener.
   * @param listener the listener.
   * @return {@code true} if the listener is found, {@code false} otherwise.
   */
  public boolean removeConfigChangeListener(ConfigChangeListener listener)
  {
    return configListeners.remove(listener);
  }

  /**
   * Adds a backup creation listener.
   * @param listener the listener.
   */
  public void addBackupCreatedListener(BackupCreatedListener listener)
  {
    backupListeners.add(listener);
  }

  /**
   * Removes a backup creation listener.
   * @param listener the listener.
   * @return {@code true} if the listener is found, {@code false} otherwise.
   */
  public boolean removeBackupCreatedListener(BackupCreatedListener listener)
  {
    return backupListeners.remove(listener);
  }

  /**
   * Adds a backend populated listener.
   * @param listener the listener.
   */
  public void addBackendPopulatedListener(BackendPopulatedListener listener)
  {
    backendPopulatedListeners.add(listener);
  }

  /**
   * Removes a backend populated listener.
   * @param listener the listener.
   * @return {@code true} if the listener is found, {@code false} otherwise.
   */
  public boolean removeBackendPopulatedListener(
      BackendPopulatedListener listener)
  {
    return backendPopulatedListeners.remove(listener);
  }

  /**
   * Adds an index modification listener.
   * @param listener the listener.
   */
  public void addIndexModifiedListener(IndexModifiedListener listener)
  {
    indexListeners.add(listener);
  }

  /**
   * Removes an index modification listener.
   * @param listener the listener.
   * @return {@code true} if the listener is found, {@code false} otherwise.
   */
  public boolean removeIndexModifiedListener(IndexModifiedListener listener)
  {
    return indexListeners.remove(listener);
  }

  /**
   * Starts pooling the server configuration.  The period of the pooling is
   * specified as a parameter.  This method is asynchronous and it will start
   * the pooling in another thread.
   */
  public synchronized void startPooling()
  {
    if (poolingThread != null)
    {
      return;
    }
    pooling = true;
    stopPooling = false;

    poolingThread = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          while (!stopPooling)
          {
            cleanupTasks();
            regenerateDescriptor();
            Thread.sleep(poolingPeriod);
          }
        }
        catch (Throwable t)
        {
        }
        pooling = false;
      }
    });
    poolingThread.start();
  }

  /**
   * Stops pooling the server.  This method is synchronous, it does not return
   * until the pooling is actually stopped.
   */
  public synchronized void stopPooling()
  {
    stopPooling = true;
    while (poolingThread != null && pooling)
    {
      try
      {
        poolingThread.interrupt();
        Thread.sleep(100);
      }
      catch (Throwable t)
      {
        // do nothing;
      }
    }
    poolingThread = null;
    pooling = false;
  }

  /**
   * Returns the trust manager to be used by this ControlPanelInfo (and in
   * general by the control panel).
   * @return the trust manager to be used by this ControlPanelInfo.
   */
  public ApplicationTrustManager getTrustManager()
  {
    return trustManager;
  }

  /**
   * Sets the trust manager to be used by this ControlPanelInfo (and in
   * general by the control panel).
   * @param trustManager the trust manager to be used by this ControlPanelInfo.
   */
  public void setTrustManager(ApplicationTrustManager trustManager)
  {
    this.trustManager = trustManager;
    connectionPool.setTrustManager(trustManager);
  }

  /**
   * Returns the timeout to establish the connection in milliseconds.
   * @return the timeout to establish the connection in milliseconds.
   */
  public int getConnectTimeout()
  {
    return connectTimeout;
  }

  /**
   * Sets the timeout to establish the connection in milliseconds.
   * Use {@code 0} to express no timeout.
   * @param connectTimeout the timeout to establish the connection in
   * milliseconds.
   * Use {@code 0} to express no timeout.
   */
  public void setConnectTimeout(int connectTimeout)
  {
    this.connectTimeout = connectTimeout;
    connectionPool.setConnectTimeout(connectTimeout);
  }

  /**
   * Returns the connection policy to be used by this ControlPanelInfo (and in
   * general by the control panel).
   * @return the connection policy to be used by this ControlPanelInfo.
   */
  public ConnectionProtocolPolicy getConnectionPolicy()
  {
    return connectionPolicy;
  }

  /**
   * Sets the connection policy to be used by this ControlPanelInfo (and in
   * general by the control panel).
   * @param connectionPolicy the connection policy to be used by this
   * ControlPanelInfo.
   */
  public void setConnectionPolicy(ConnectionProtocolPolicy connectionPolicy)
  {
    this.connectionPolicy = connectionPolicy;
  }

  /**
   * Gets the LDAPS HostPort based in what is read in the configuration. It
   * returns {@code null} if no LDAPS HostPort was found.
   * @return the LDAPS HostPort to be used to connect to the server.
   */
  public HostPort getLdapsHostPort()
  {
    return ldapsHostPort;
  }

  /**
   * Gets the Administration Connector HostPort based in what is read in the
   * configuration. It returns {@code null} if no Administration
   * Connector HostPort was found.
   * @return the Administration Connector HostPort to be used to connect
   * to the server.
   */
  public HostPort getAdminConnectorHostPort()
  {
    if (isLocal)
    {
      // If the user set isLocal to true, we want to return the localAdminConnectorHostPort
      // (in particular if regenerateDescriptor has not been called).
      return localAdminConnectorHostPort;
    }
    return adminConnectorHostPort;
  }

  /**
   * Gets the Administration Connector HostPort based in what is read in the local
   * configuration. It returns {@code null} if no Administration
   * Connector HostPort was found.
   * @return the Administration Connector HostPort to be used to connect
   * to the local server.
   */
  public HostPort getLocalAdminConnectorHostPort()
  {
    return localAdminConnectorHostPort;
  }

  /**
   * Gets the LDAP HostPort based in what is read in the configuration.
   * It returns {@code null} if no LDAP HostPort was found.
   * @return the LDAP HostPort to be used to connect to the server.
   */
  public HostPort getLdapHostPort()
  {
    return ldapHostPort;
  }

  /**
   * Gets the Start TLS HostPort based in what is read in the configuration. It
   * returns {@code null} if no Start TLS HostPort is found.
   * @return the Start TLS HostPort to be used to connect to the server.
   */
  public HostPort getStartTlsHostPort()
  {
    return startTlsHostPort;
  }

  /**
   * Returns the HostPort to be used to connect to a given ServerDescriptor
   * using a certain protocol. It returns {@code null} if HostPort for the
   * protocol is not found.
   * @param server the server descriptor.
   * @param protocol the protocol to be used.
   * @return the HostPort to be used to connect to a given ServerDescriptor
   * using a certain protocol.
   */
  private static HostPort getHostPort(ServerDescriptor server, ConnectionHandlerDescriptor.Protocol protocol)
  {
    toString(protocol);

    HostPort hp = null;
    for (ConnectionHandlerDescriptor desc : server.getConnectionHandlers())
    {
      if (desc.getState() == ConnectionHandlerDescriptor.State.ENABLED
          && desc.getProtocol() == protocol)
      {
        int port = desc.getPort();
        if (port > 0)
        {
          if (server.isLocal())
          {
            SortedSet<InetAddress> addresses = desc.getAddresses();
            if (addresses.isEmpty())
            {
              hp = new HostPort("localhost", port);
            }
            else
            {
              InetAddress address = addresses.first();
              hp = new HostPort(address.getHostAddress(), port);
            }
          }
          else
          {
            hp = new HostPort(server.getHostname(), port);
          }
        }
      }
    }
    return hp;
  }

  private static String toString(ConnectionHandlerDescriptor.Protocol protocol)
  {
    switch (protocol)
    {
    case LDAP:
    case LDAP_STARTTLS:
      return "ldap";
    case LDAPS:
      return "ldaps";
    case JMX:
      return "jmx";
    case JMXS:
      return "jmxs";
    default:
      return null;
    }
  }

  /**
   * Returns the Administration Connector HostPort.
   * It returns {@code null} if HostPort for the protocol is not found.
   * @param server the server descriptor.
   * @return the Administration Connector HostPort.
   */
  private static HostPort getAdminConnectorHostPort(ServerDescriptor server) {
    ConnectionHandlerDescriptor desc = server.getAdminConnector();
    if (desc != null)
    {
      int port = desc.getPort();
      if (port > 0) {
        SortedSet<InetAddress> addresses = desc.getAddresses();
        if (!addresses.isEmpty())
        {
          return new HostPort(addresses.first().getHostAddress(), port);
        }
        else
        {
          return new HostPort("localhost", port);
        }
      }
    }
    return null;
  }

  /**
   * Tells whether we must connect to the server using Start TLS.
   * @return {@code true} if we must connect to the server using Start TLS
   * and {@code false} otherwise.
   */
  public boolean connectUsingStartTLS()
  {
    return startTlsHostPort != null && startTlsHostPort.equals(getHostPortToConnect());
  }

  /**
   * Tells whether we must connect to the server using LDAPS.
   * @return {@code true} if we must connect to the server using LDAPS, {@code false} otherwise.
   */
  public boolean connectUsingLDAPS()
  {
    return ldapsHostPort != null && ldapsHostPort.equals(getHostPortToConnect());
  }

  /**
   * Returns the HostPort that must be used to connect to the server based on the
   * available enabled connection handlers in the server and the connection
   * policy.
   * @return the HostPort that must be used to connect to the server.
   */
  private HostPort getHostPortToConnect()
  {
    switch (getConnectionPolicy())
    {
    case USE_STARTTLS:
      return startTlsHostPort;
    case USE_LDAP:
      return ldapHostPort;
    case USE_LDAPS:
      return ldapsHostPort;
    case USE_ADMIN:
      return getAdminConnectorHostPort();
    case USE_MOST_SECURE_AVAILABLE:
      HostPort hp1 = ldapsHostPort;
      if (hp1 == null)
      {
        hp1 = startTlsHostPort;
      }
      if (hp1 == null)
      {
        hp1 = ldapHostPort;
      }
      return hp1;
    case USE_LESS_SECURE_AVAILABLE:
      HostPort hp2 = ldapHostPort;
      if (hp2 == null)
      {
        hp2 = startTlsHostPort;
      }
      if (hp2 == null)
      {
        hp2 = ldapsHostPort;
      }
      return hp2;
    default:
      throw new RuntimeException("Unknown policy: "+getConnectionPolicy());
    }
  }

  /**
   * Returns {@code true} if the configuration must be deregistered and {@code false} otherwise.
   * This is required when we update the configuration, in these cases {@code cn=config}
   * must the deregistered and after that register again.
   * @return {@code true} if the configuration must be deregistered and {@code false} otherwise.
   */
  public boolean mustDeregisterConfig()
  {
    return mustDeregisterConfig;
  }

  /**
   * Sets whether the configuration must be deregistered or not.
   * @param mustDeregisterConfig whether the configuration must be deregistered
   * or not.
   */
  public void setMustDeregisterConfig(boolean mustDeregisterConfig)
  {
    ControlPanelInfo.mustDeregisterConfig = mustDeregisterConfig;
  }

  /**
   * Sets whether the server is local or not.
   * @param isLocal whether the server is local or not.
   */
  public void setIsLocal(boolean isLocal)
  {
    this.isLocal = isLocal;
  }

  /**
   * Returns whether we are trying to manage the local host.
   * @return {@code true} if we are trying to manage the local host,
   * {@code false} otherwise.
   */
  public boolean isLocal()
  {
    return isLocal;
  }

  /**
   * Returns the connection pool to be used by the LDAP entry browsers.
   * @return the connection pool to be used by the LDAP entry browsers.
   */
  public LDAPConnectionPool getConnectionPool()
  {
    return connectionPool;
  }

  /**
   * Returns the icon pool to be used by the LDAP entry browsers.
   * @return the icon pool to be used by the LDAP entry browsers.
   */
  public IconPool getIconPool()
  {
    return iconPool;
  }

  /**
   * Returns the pooling period in milliseconds.
   * @return the pooling period in milliseconds.
   */
  public long getPoolingPeriod()
  {
    return poolingPeriod;
  }

  /**
   * Sets the pooling period in miliseconds.
   * @param poolingPeriod the pooling time in miliseconds.
   */
  public void setPoolingPeriod(long poolingPeriod)
  {
    this.poolingPeriod = poolingPeriod;
  }

  /** Cleans the tasks that are over. */
  private void cleanupTasks()
  {
    Set<Task> toClean = new HashSet<>();
    for (Task task : tasks)
    {
      if (task.getState() == Task.State.FINISHED_SUCCESSFULLY ||
          task.getState() == Task.State.FINISHED_WITH_ERROR)
      {
        toClean.add(task);
      }
    }
    for (Task task : toClean)
    {
      unregisterTask(task);
    }
  }

  /**
   * Returns whether the provided task is running on the provided server or not.
   * The code takes into account that the server object might not be fully
   * initialized (but at least it contains the host name and the instance
   * path if it is local).
   * @param server the server.
   * @param task the task to be analyzed.
   * @return {@code true} if the provided task is running on the provided server,
   * {@code false} otherwise.
   */
  private boolean isRunningOnServer(ServerDescriptor server, Task task)
  {
    if (server.isLocal() && task.getServer().isLocal())
    {
      return true;
    }

    String host1 = server.getHostname();
    String host2 = task.getServer().getHostname();
    boolean isRunningOnServer = host1 != null ? host1.equalsIgnoreCase(host2) : host2 == null;
    if (!isRunningOnServer)
    {
      return false;
    }

    if (server.isLocal())
    {
      // Compare paths
      String path1 = server.getInstancePath();
      String path2 = task.getServer().getInstancePath();
      return Objects.equals(path1, path2);
    }

    // At this point we only have connection information about the new server.
    // Use the dir context which corresponds to the server to compare things.

    // Compare administration port;
    int adminPort1 = -1;
    int adminPort2 = -1;
    if (server.getAdminConnector() != null)
    {
      adminPort1 = server.getAdminConnector().getPort();
    }

    if (getConnection() != null)
    {
      adminPort2 = getConnection().getHostPort().getPort();
    }
    return adminPort1 == adminPort2;
  }

  private boolean checkConnections(ConnectionWrapper conn, ConnectionWrapper userConn)
  {
    // Check the connection
    int nMaxErrors = 5;
    for (int i=0; i< nMaxErrors; i++)
    {
      try
      {
        Utilities.ping(conn);
        if (userConn != null)
        {
          Utilities.ping(userConn);
        }
        return true;
      }
      catch (RuntimeException ignored)
      {
        try
        {
          Thread.sleep(400);
        }
        catch (Throwable t)
        {
        }
      }
    }
    return false;
  }

  /**
   * Initialize the new configuration framework if needed.
   *
   * @throws org.opends.server.config.ConfigException
   *           If error occurred during the initialization
   */
  public void initializeConfigurationFramework() throws org.opends.server.config.ConfigException
  {
    if (!ConfigurationFramework.getInstance().isInitialized())
    {
      try
      {
        ConfigurationFramework.getInstance().initialize();
      }
      catch (ConfigException ce)
      {
        throw new org.opends.server.config.ConfigException(ce.getMessageObject(), ce);
      }
    }
  }
}
