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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.io.File;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.browser.LDAPConnectionPool;
import org.opends.guitools.controlpanel.event.BackendPopulatedEvent;
import org.opends.guitools.controlpanel.event.BackendPopulatedListener;
import org.opends.guitools.controlpanel.event.BackupCreatedEvent;
import org.opends.guitools.controlpanel.event.BackupCreatedListener;
import org.opends.guitools.controlpanel.event.ConfigChangeListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedListener;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.ConfigFromDirContext;
import org.opends.guitools.controlpanel.util.ConfigFromFile;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.tools.ConfigureWindowsService;

/**
 * This is the classes that is shared among all the different places in the
 * Control Panel.  It contains information about the server status and
 * configuration and some objects that are shared everywhere.
 *
 */
public class ControlPanelInfo
{
  private long poolingPeriod = 20000;

  private ServerDescriptor serverDesc;
  private Set<Task> tasks = new HashSet<Task>();
  private InitialLdapContext ctx;
  private InitialLdapContext userDataCtx;
  private final LDAPConnectionPool connectionPool = new LDAPConnectionPool();
  // Used by the browsers
  private final IconPool iconPool = new IconPool(); // Used by the browsers
  private Thread poolingThread;
  private boolean stopPooling;
  private boolean pooling;
  private ApplicationTrustManager trustManager;
  private int connectTimeout = ConnectionUtils.getDefaultLDAPTimeout();
  private ConnectionProtocolPolicy connectionPolicy =
    ConnectionProtocolPolicy.USE_MOST_SECURE_AVAILABLE;
  private String ldapURL;
  private String startTLSURL;
  private String ldapsURL;
  private String adminConnectorURL;
  private String localAdminConnectorURL;
  private String lastWorkingBindDN;
  private String lastWorkingBindPwd;
  private String lastRemoteHostName;
  private String lastRemoteAdministrationURL;

  private static boolean mustDeregisterConfig;

  private boolean isLocal = true;

  private Set<AbstractIndexDescriptor> modifiedIndexes =
    new HashSet<AbstractIndexDescriptor>();

  private LinkedHashSet<ConfigChangeListener> configListeners =
    new LinkedHashSet<ConfigChangeListener>();

  private LinkedHashSet<BackupCreatedListener> backupListeners =
    new LinkedHashSet<BackupCreatedListener>();

  private LinkedHashSet<BackendPopulatedListener> backendPopulatedListeners =
    new LinkedHashSet<BackendPopulatedListener>();

  private LinkedHashSet<IndexModifiedListener> indexListeners =
    new LinkedHashSet<IndexModifiedListener>();

  private static final Logger LOG =
    Logger.getLogger(ControlPanelInfo.class.getName());

  private static ControlPanelInfo instance;

  /**
   * Default constructor.
   *
   */
  protected ControlPanelInfo()
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
        LOG.log(Level.WARNING, "Error retrieving UI key store: "+t, t);
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
  public void unregisterTask(Task task)
  {
    tasks.remove(task);
  }

  /**
   * Tells whether an index must be reindexed or not.
   * @param index the index.
   * @return <CODE>true</CODE> if the index must be reindexed and
   * <CODE>false</CODE> otherwise.
   */
  public boolean mustReindex(AbstractIndexDescriptor index)
  {
    boolean mustReindex = false;
    for (AbstractIndexDescriptor i : modifiedIndexes)
    {
      if (i.getName().equals(index.getName()) &&
          i.getBackend().getBackendID().equals(
              index.getBackend().getBackendID()))
      {
        mustReindex = true;
        break;
      }
    }
    return mustReindex;
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
   * @return <CODE>true</CODE> if the index is found in the list of modified
   * indexes and <CODE>false</CODE> otherwise.
   */
  public boolean unregisterModifiedIndex(AbstractIndexDescriptor index)
  {
    // We might have stored indexes whose configuration has changed, just remove
    // them if they have the same name, are of the same type and are defined in
    // the same backend.
    Set<AbstractIndexDescriptor> toRemove =
      new HashSet<AbstractIndexDescriptor>();
    for (AbstractIndexDescriptor i : modifiedIndexes)
    {
      if (i.getName().equalsIgnoreCase(index.getName()) &&
          i.getBackend().getBackendID().equalsIgnoreCase(
              index.getBackend().getBackendID()) &&
          i.getClass().equals((index.getClass())))
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
    else
    {
      return false;
    }
  }

  /**
   * Unregisters all the modified indexes on a given backend.
   * @param backendName the name of the backend.
   */
  public void unregisterModifiedIndexesInBackend(String backendName)
  {
    HashSet<AbstractIndexDescriptor> toDelete =
      new HashSet<AbstractIndexDescriptor>();
    for (AbstractIndexDescriptor index : modifiedIndexes)
    {
      // Compare only the Backend ID, since the backend object attached to
      // the registered index might have changed (for instance the number of
      // entries).  Relying on the backend ID to identify the backend is
      // safe.
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
   * @param ctx the connection.
   */
  public void setDirContext(InitialLdapContext ctx)
  {
    this.ctx = ctx;
    if (ctx != null)
    {
      lastWorkingBindDN = ConnectionUtils.getBindDN(ctx);
      lastWorkingBindPwd = ConnectionUtils.getBindPassword(ctx);
      lastRemoteHostName = ConnectionUtils.getHostName(ctx);
      lastRemoteAdministrationURL = ConnectionUtils.getLdapUrl(ctx);
    }
  }

  /**
   * Returns the dir context to be used by the ControlPanelInfo to retrieve
   * monitoring and configuration information.
   * @return the dir context to be used by the ControlPanelInfo to retrieve
   * monitoring and configuration information.
   */
  public InitialLdapContext getDirContext()
  {
    return ctx;
  }

  /**
   * Sets the dir context to be used by the ControlPanelInfo to retrieve
   * user data.
   * @param ctx the connection.
   * @throws NamingException if there is a problem updating the connection pool.
   */
  public void setUserDataDirContext(InitialLdapContext ctx)
  throws NamingException
  {
    if (userDataCtx != null)
    {
      if (connectionPool.isConnectionRegistered(userDataCtx))
      {
        try
        {
          connectionPool.unregisterConnection(userDataCtx);
        }
        catch (Throwable t)
        {
        }
      }
    }
    this.userDataCtx = ctx;
    if (ctx != null)
    {
      InitialLdapContext cloneLdc =
        ConnectionUtils.cloneInitialLdapContext(userDataCtx,
            getConnectTimeout(),
            getTrustManager(), null);
      connectionPool.registerConnection(cloneLdc);
    }
  }

  /**
   * Returns the dir context to be used by the ControlPanelInfo to retrieve
   * user data.
   * @return the dir context to be used by the ControlPanelInfo to retrieve
   * user data.
   */
  public InitialLdapContext getUserDataDirContext()
  {
    return userDataCtx;
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
  public void indexModified(AbstractIndexDescriptor modifiedIndex)
  {
    IndexModifiedEvent ev = new IndexModifiedEvent(modifiedIndex);
    for (IndexModifiedListener listener : indexListeners)
    {
      listener.indexModified(ev);
    }
  }

  /**
   * Returns an empty new server descriptor instance.
   * @return an empty new server descriptor instance.
   */
  protected ServerDescriptor createNewServerDescriptorInstance()
  {
    return new ServerDescriptor();
  }

  /**
   * Returns a reader that will read the configuration from a file.
   * @return a reader that will read the configuration from a file.
   */
  protected ConfigFromFile createNewConfigFromFileReader()
  {
    return new ConfigFromFile();
  }

  /**
   * Returns a reader that will read the configuration from a dir context.
   * @return a reader that will read the configuration from a dir context.
   */
  protected ConfigFromDirContext createNewConfigFromDirContextReader()
  {
    ConfigFromDirContext configFromDirContext = new ConfigFromDirContext();
    configFromDirContext.setIsLocal(isLocal());
    return configFromDirContext;
  }

  /**
   * Updates the contents of the server descriptor with the provider reader.
   * @param reader the configuration reader.
   * @param desc the server descriptor.
   */
  protected void updateServerDescriptor(ConfigReader reader,
      ServerDescriptor desc)
  {
    desc.setExceptions(reader.getExceptions());
    desc.setAdministrativeUsers(reader.getAdministrativeUsers());
    desc.setBackends(reader.getBackends());
    desc.setConnectionHandlers(reader.getConnectionHandlers());
    desc.setAdminConnector(reader.getAdminConnector());
    desc.setSchema(reader.getSchema());
    desc.setSchemaEnabled(reader.isSchemaEnabled());
  }

  /**
   * Regenerates the last found ServerDescriptor object.
   *
   */
  public synchronized void regenerateDescriptor()
  {
    boolean isLocal = isLocal();

    ServerDescriptor desc = createNewServerDescriptorInstance();
    desc.setIsLocal(isLocal);
    InitialLdapContext ctx = getDirContext();
    if (isLocal)
    {
      desc.setOpenDSVersion(
        org.opends.server.util.DynamicConstants.FULL_VERSION_STRING);
      String installPath = Utilities.getInstallPathFromClasspath();
      desc.setInstallPath(installPath);
      desc.setInstancePath(Utils.getInstancePathFromInstallPath(installPath));
      boolean windowsServiceEnabled = false;
      if (Utilities.isWindows())
      {
        int result = ConfigureWindowsService.serviceState(null, null);
        windowsServiceEnabled =
          result == ConfigureWindowsService.SERVICE_STATE_ENABLED;
      }
      desc.setWindowsServiceEnabled(windowsServiceEnabled);
    }
    else
    {
      if (lastRemoteHostName != null)
      {
        desc.setHostname(lastRemoteHostName);
      }
    }
    ConfigReader reader;

    ServerDescriptor.ServerStatus status = null;
    for (Task task : getTasks())
    {
      if ((task.getType() == Task.Type.START_SERVER) &&
          (task.getState() == Task.State.RUNNING) &&
          isRunningOnServer(desc, task))
      {
        status = ServerDescriptor.ServerStatus.STARTING;
      }
      else if ((task.getType() == Task.Type.STOP_SERVER) &&
          (task.getState() == Task.State.RUNNING) &&
          isRunningOnServer(desc, task))
      {
        status = ServerDescriptor.ServerStatus.STOPPING;
      }
    }
    if (status != null)
    {
      desc.setStatus(status);
      if (status == ServerDescriptor.ServerStatus.STOPPING)
      {
        if (ctx != null)
        {
          try
          {
            ctx.close();
          }
          catch (Throwable t)
          {
          }
          this.ctx = null;
        }
        if (userDataCtx != null)
        {
          if (connectionPool.isConnectionRegistered(userDataCtx))
          {
            try
            {
              connectionPool.unregisterConnection(userDataCtx);
            }
            catch (Throwable t)
            {
            }
          }
          try
          {
            userDataCtx.close();
          }
          catch (Throwable t)
          {
          }
          userDataCtx = null;
        }
      }
      if (isLocal)
      {
        reader = createNewConfigFromFileReader();
        ((ConfigFromFile)reader).readConfiguration();
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
      desc.setStatus(ServerDescriptor.ServerStatus.STARTED);

      if ((ctx == null) && (lastWorkingBindDN != null))
      {
        // Try with previous credentials.
        try
        {
          if (isLocal)
          {
            ctx = Utilities.getAdminDirContext(this, lastWorkingBindDN,
              lastWorkingBindPwd);
          }
          else if (lastRemoteAdministrationURL != null)
          {
            ctx = Utils.createLdapsContext(lastRemoteAdministrationURL,
                lastWorkingBindDN,
                lastWorkingBindPwd,
                getConnectTimeout(), null,
                getTrustManager());
          }
        }
        catch (ConfigReadException cre)
        {
//        Ignore: we will ask the user for credentials.
        }
        catch (NamingException ne)
        {
          // Ignore: we will ask the user for credentials.
        }
        if (ctx != null)
        {
          this.ctx = ctx;
        }
      }

      if (isLocal && (ctx == null))
      {
        reader = createNewConfigFromFileReader();
        ((ConfigFromFile)reader).readConfiguration();
      }
      else
      {
        if (!isLocal && (ctx == null))
        {
          desc.setStatus(ServerDescriptor.ServerStatus.NOT_CONNECTED_TO_REMOTE);
          reader = null;
        }
        else
        {
          reader = createNewConfigFromDirContextReader();
          ((ConfigFromDirContext)reader).readConfiguration(ctx);

          boolean connectionWorks = checkConnections(ctx, userDataCtx);
          if (!connectionWorks)
          {
            if (isLocal)
            {
              // Try with off-line info
              reader = createNewConfigFromFileReader();
              ((ConfigFromFile)reader).readConfiguration();
            }
            else
            {
              desc.setStatus(
                  ServerDescriptor.ServerStatus.NOT_CONNECTED_TO_REMOTE);
              reader = null;
            }
            try
            {
              ctx.close();
            }
            catch (Throwable t)
            {
            }
            this.ctx = null;
            if (connectionPool.isConnectionRegistered(userDataCtx))
            {
              try
              {
                connectionPool.unregisterConnection(userDataCtx);
              }
              catch (Throwable t)
              {
              }
            }
            try
            {
              userDataCtx.close();
            }
            catch (Throwable t)
            {
            }
            userDataCtx = null;
          }
        }
      }
      if (reader != null)
      {
        desc.setAuthenticated(reader instanceof ConfigFromDirContext);
        desc.setJavaVersion(reader.getJavaVersion());
        desc.setOpenConnections(reader.getOpenConnections());
        desc.setTaskEntries(reader.getTaskEntries());
        if (reader instanceof ConfigFromDirContext)
        {
          ConfigFromDirContext rCtx = (ConfigFromDirContext)reader;
          desc.setRootMonitor(rCtx.getRootMonitor());
          desc.setEntryCachesMonitor(rCtx.getEntryCaches());
          desc.setJvmMemoryUsageMonitor(rCtx.getJvmMemoryUsage());
          desc.setSystemInformationMonitor(rCtx.getSystemInformation());
          desc.setWorkQueueMonitor(rCtx.getWorkQueue());
          desc.setOpenDSVersion((String)Utilities.getFirstMonitoringValue(
              rCtx.getVersionMonitor(), "fullVersion"));
          String installPath = (String)Utilities.getFirstMonitoringValue(
              rCtx.getSystemInformation(), "installPath");
          if (installPath != null)
          {
            desc.setInstallPath(installPath);
          }
          String instancePath = (String)Utilities.getFirstMonitoringValue(
              rCtx.getSystemInformation(), "instancePath");
          if (instancePath != null)
          {
            desc.setInstancePath(instancePath);
          }
        }
      }
    }
    else
    {
      desc.setStatus(ServerDescriptor.ServerStatus.STOPPED);
      desc.setAuthenticated(false);
      reader = createNewConfigFromFileReader();
      ((ConfigFromFile)reader).readConfiguration();
    }
    if (reader != null)
    {
      updateServerDescriptor(reader, desc);
    }

    if ((serverDesc == null) || !serverDesc.equals(desc))
    {
      serverDesc = desc;
      ldapURL = getURL(serverDesc, ConnectionHandlerDescriptor.Protocol.LDAP);
      ldapsURL = getURL(serverDesc, ConnectionHandlerDescriptor.Protocol.LDAPS);
      adminConnectorURL = getAdminConnectorURL(serverDesc);
      if (serverDesc.isLocal())
      {
        localAdminConnectorURL = adminConnectorURL;
      }
      startTLSURL = getURL(serverDesc,
          ConnectionHandlerDescriptor.Protocol.LDAP_STARTTLS);
      ConfigurationChangeEvent ev = new ConfigurationChangeEvent(this, desc);
      for (ConfigChangeListener listener : configListeners)
      {
        listener.configurationChanged(ev);
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
   * @return <CODE>true</CODE> if the listener is found and <CODE>false</CODE>
   * otherwise.
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
   * @return <CODE>true</CODE> if the listener is found and <CODE>false</CODE>
   * otherwise.
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
   * @return <CODE>true</CODE> if the listener is found and <CODE>false</CODE>
   * otherwise.
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
   * @return <CODE>true</CODE> if the listener is found and <CODE>false</CODE>
   * otherwise.
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
   *
   */
  public synchronized void stopPooling()
  {
    stopPooling = true;
    while ((poolingThread != null) && pooling)
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
   * Gets the LDAPS URL based in what is read in the configuration. It
   * returns <CODE>null</CODE> if no LDAPS URL was found.
   * @return the LDAPS URL to be used to connect to the server.
   */
  public String getLDAPSURL()
  {
    return ldapsURL;
  }

  /**
   * Gets the Administration Connector URL based in what is read in the
   * configuration. It returns <CODE>null</CODE> if no Administration
   * Connector URL was found.
   * @return the Administration Connector URL to be used to connect
   * to the server.
   */
  public String getAdminConnectorURL()
  {
    if (isLocal)
    {
      // If the user set isLocal to true, we want to return the
      // localAdminConnectorURL (in particular if regenerateDescriptor has not
      // been called).
      return localAdminConnectorURL;
    }
    else
    {
      return adminConnectorURL;
    }
  }

  /**
   * Gets the Administration Connector URL based in what is read in the local
   * configuration. It returns <CODE>null</CODE> if no Administration
   * Connector URL was found.
   * @return the Administration Connector URL to be used to connect
   * to the local server.
   */
  public String getLocalAdminConnectorURL()
  {
    return localAdminConnectorURL;
  }

  /**
   * Gets the LDAP URL based in what is read in the configuration. It
   * returns <CODE>null</CODE> if no LDAP URL was found.
   * @return the LDAP URL to be used to connect to the server.
   */
  public String getLDAPURL()
  {
    return ldapURL;
  }

  /**
   * Gets the Start TLS URL based in what is read in the configuration. It
   * returns <CODE>null</CODE> if no Start TLS URL is found.
   * @return the Start TLS URL to be used to connect to the server.
   */
  public String getStartTLSURL()
  {
    return startTLSURL;
  }

  /**
   * Returns the LDAP URL to be used to connect to a given ServerDescriptor
   * using a certain protocol. It returns <CODE>null</CODE> if URL for the
   * protocol is not found.
   * @param server the server descriptor.
   * @param protocol the protocol to be used.
   * @return the LDAP URL to be used to connect to a given ServerDescriptor
   * using a certain protocol.
   */
  private static String getURL(ServerDescriptor server,
      ConnectionHandlerDescriptor.Protocol protocol)
  {
    String url = null;

    String sProtocol = null;
    switch (protocol)
    {
    case LDAP:
      sProtocol = "ldap";
      break;
    case LDAPS:
      sProtocol = "ldaps";
      break;
    case LDAP_STARTTLS:
      sProtocol = "ldap";
      break;
    case JMX:
      sProtocol = "jmx";
      break;
    case JMXS:
      sProtocol = "jmxs";
      break;
    }

    for (ConnectionHandlerDescriptor desc : server.getConnectionHandlers())
    {
      if ((desc.getState() == ConnectionHandlerDescriptor.State.ENABLED) &&
          (desc.getProtocol() == protocol))
      {
        int port = desc.getPort();
        SortedSet<InetAddress> addresses = desc.getAddresses();
        if (addresses.size() == 0)
        {
          if (port > 0)
          {
            if (server.isLocal())
            {
              url = sProtocol +"://localhost:"+port;
            }
            else
            {
              url = sProtocol +"://"+
              ConnectionUtils.getHostNameForLdapUrl(server.getHostname())+
              ":"+port;
            }
          }
        }
        else
        {
          if (port > 0)
          {
            if (server.isLocal())
            {
              InetAddress address = addresses.first();
              url = sProtocol +"://"+
              ConnectionUtils.getHostNameForLdapUrl(address.getHostAddress())+
              ":"+
              port;
            }
            else
            {
              url = sProtocol +"://"+
              ConnectionUtils.getHostNameForLdapUrl(server.getHostname())+
              ":"+port;
            }
          }
        }
      }
    }
    return url;
  }

  /**
   * Returns the Administration Connector URL.
   * It returns <CODE>null</CODE> if URL for the
   * protocol is not found.
   * @param server the server descriptor.
   * @return the Administration Connector URL.
   */
  private static String getAdminConnectorURL(ServerDescriptor server) {
    String url = null;

    ConnectionHandlerDescriptor desc = server.getAdminConnector();
    if (desc != null)
    {
      int port = desc.getPort();
      SortedSet<InetAddress> addresses = desc.getAddresses();
      if (addresses.size() == 0) {
        if (port > 0) {
          url = ConnectionUtils.getLDAPUrl("localhost", port, true);
        }
      } else {
        if (port > 0) {
          InetAddress address = addresses.first();
          String a = address.getHostAddress();
          url = ConnectionUtils.getLDAPUrl(a, port, true);
        }
      }
    }
    else
    {
      url = null;
    }
    return url;
  }

  /**
   * Tells whether we must connect to the server using Start TLS.
   * @return <CODE>true</CODE> if we must connect to the server using Start TLS
   * and <CODE>false</CODE> otherwise.
   */
  public boolean connectUsingStartTLS()
  {
    boolean connectUsingStartTLS = false;
    if (getStartTLSURL() != null)
    {
      connectUsingStartTLS = getStartTLSURL().equals(getURLToConnect());
    }
    return connectUsingStartTLS;
  }

  /**
   * Tells whether we must connect to the server using LDAPS.
   * @return <CODE>true</CODE> if we must connect to the server using LDAPS
   * and <CODE>false</CODE> otherwise.
   */
  public boolean connectUsingLDAPS()
  {
    boolean connectUsingLDAPS = false;
    if (getLDAPSURL() != null)
    {
      connectUsingLDAPS = getLDAPSURL().equals(getURLToConnect());
    }
    return connectUsingLDAPS;
  }

  /**
   * Returns the URL that must be used to connect to the server based on the
   * available enabled connection handlers in the server and the connection
   * policy.
   * @return the URL that must be used to connect to the server.
   */
  public String getURLToConnect()
  {
    String url;
    switch (getConnectionPolicy())
    {
    case USE_STARTTLS:
      url = getStartTLSURL();
      break;
    case USE_LDAP:
      url = getLDAPURL();
      break;
    case USE_LDAPS:
      url = getLDAPSURL();
      break;
    case USE_ADMIN:
      url = getAdminConnectorURL();
      break;
    case USE_MOST_SECURE_AVAILABLE:
      url = getLDAPSURL();
      if (url == null)
      {
        url = getStartTLSURL();
      }
      if (url == null)
      {
        url = getLDAPURL();
      }
      break;
    case USE_LESS_SECURE_AVAILABLE:
      url = getLDAPURL();
      if (url == null)
      {
        url = getStartTLSURL();
      }
      if (url == null)
      {
        url = getLDAPSURL();
      }
      break;
    default:
      throw new RuntimeException("Unknown policy: "+getConnectionPolicy());
    }
    return url;
  }

  /**
   * Returns <CODE>true</CODE> if the configuration must be deregistered and
   * <CODE>false</CODE> otherwise.
   * This is required when we use the ConfigFileHandler to update the
   * configuration, in these cases cn=config must the deregistered from the
   * ConfigFileHandler and after that register again.
   * @return <CODE>true</CODE> if the configuration must be deregistered and
   * <CODE>false</CODE> otherwise.
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
   * Returns <CODE>true</CODE> if we are trying to manage the local host and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are trying to manage the local host and
   * <CODE>false</CODE> otherwise.
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
   * Returns the pooling period in miliseconds.
   * @return the pooling period in miliseconds.
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

  /**
   * Cleans the tasks that are over.
   */
  private void cleanupTasks()
  {
    Set<Task> toClean = new HashSet<Task>();
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
   * @return <CODE>true</CODE> if the provided task is running on the provided
   * server and <CODE>false</CODE> otherwise.
   */
  private boolean isRunningOnServer(ServerDescriptor server, Task task)
  {
    boolean isRunningOnServer;
    if (!server.isLocal() || !task.getServer().isLocal())
    {
      if (!server.isLocal())
      {
        // At this point we only have connection information about the new
        // server.  Use the dir context which corresponds to the server to
        // compare things.
        String host1 = server.getHostname();
        String host2 = task.getServer().getHostname();
        if (host1 == null)
        {
          isRunningOnServer = host2 == null;
        }
        else
        {
          isRunningOnServer = host1.equalsIgnoreCase(host2);
        }
        if (isRunningOnServer)
        {
          // Compare administration port;
          int adminPort1 = -1;
          int adminPort2 = -1;
          if (server.getAdminConnector() != null)
          {
            adminPort1 = server.getAdminConnector().getPort();
          }

          if (getDirContext() != null)
          {
            adminPort2 = ConnectionUtils.getPort(getDirContext());
          }
          isRunningOnServer = adminPort1 == adminPort2;
        }
      }
      else
      {
        // Compare host names and paths
        String f1 = server.getInstancePath();
        String f2 = task.getServer().getInstancePath();

        String host1 = server.getHostname();
        String host2 = task.getServer().getHostname();
        if (host1 == null)
        {
          isRunningOnServer = host2 == null;
        }
        else
        {
          isRunningOnServer = host1.equalsIgnoreCase(host2);
        }
        if (isRunningOnServer)
        {
          if (f1 == null)
          {
            isRunningOnServer = f2 == null;
          }
          else
          {
            isRunningOnServer = f1.equals(f2);
          }
        }
      }
    }
    else
    {
      isRunningOnServer = true;
    }
    return isRunningOnServer;
  }

  private boolean checkConnections(InitialLdapContext ctx,
      InitialLdapContext userCtx)
  {
    // Check the connection
    boolean connectionWorks = false;
    int nMaxErrors = 5;
    for (int i=0; i< nMaxErrors && !connectionWorks; i++)
    {
      try
      {
        Utilities.pingDirContext(ctx);
        if (userCtx != null)
        {
          Utilities.pingDirContext(userCtx);
        }
        connectionWorks = true;
      }
      catch (NamingException ne)
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
    return connectionWorks;
  }
}
