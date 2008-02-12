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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.io.File;
import java.io.FilenameFilter;

import org.opends.server.monitors.DatabaseEnvironmentMonitor;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.FilePermission;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.api.Backend;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.JebMessages.*;
import static org.opends.messages.ConfigMessages.
    ERR_CONFIG_BACKEND_MODE_INVALID;
import static org.opends.messages.ConfigMessages.
    ERR_CONFIG_BACKEND_INSANE_MODE;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.messages.ConfigMessages.*;

/**
 * Wrapper class for the JE environment. Root container holds all the entry
 * containers for each base DN. It also maintains all the openings and closings
 * of the entry containers.
 */
public class RootContainer
     implements ConfigurationChangeListener<LocalDBBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * The JE database environment.
   */
  private Environment env;

  /**
   * The backend configuration.
   */
  private LocalDBBackendCfg config;

  /**
   * The backend to which this entry root container belongs.
   */
  private Backend backend;

  /**
   * The database environment monitor for this JE environment.
   */
  private DatabaseEnvironmentMonitor monitor;

  /**
   * The base DNs contained in this entryContainer.
   */
  private ConcurrentHashMap<DN, EntryContainer> entryContainers;

  /**
   * The cached value of the next entry identifier to be assigned.
   */
  private AtomicLong nextid = new AtomicLong(1);

  /**
   * The compressed schema manager for this backend.
   */
  private JECompressedSchema compressedSchema;



  /**
   * Creates a new RootContainer object. Each root container represents a JE
   * environment.
   *
   * @param config The configuration of the JE backend.
   * @param backend A reference to the JE back end that is creating this
   *                root container.
   */
  public RootContainer(Backend backend, LocalDBBackendCfg config)
  {
    this.env = null;
    this.monitor = null;
    this.entryContainers = new ConcurrentHashMap<DN, EntryContainer>();
    this.backend = backend;
    this.config = config;
    this.compressedSchema = null;

    config.addLocalDBChangeListener(this);
  }

  /**
   * Opens the root container using the JE configuration object provided.
   *
   * @param envConfig The JE environment configuration.
   * @throws DatabaseException If an error occurs when creating the environment.
   * @throws ConfigException If an configuration error occurs while creating
   * the enviornment.
   */
  public void open(EnvironmentConfig envConfig)
      throws DatabaseException, ConfigException
  {
    // Determine the backend database directory.
    File parentDirectory = getFileForPath(config.getDBDirectory());
    File backendDirectory = new File(parentDirectory, config.getBackendId());

    // Create the directory if it doesn't exist.
    if (!backendDirectory.exists())
    {
      if(!backendDirectory.mkdirs())
      {
        Message message =
          ERR_JEB_CREATE_FAIL.get(backendDirectory.getPath());
        throw new ConfigException(message);
      }
    }
    //Make sure the directory is valid.
    else if (!backendDirectory.isDirectory())
    {
      Message message =
          ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath());
      throw new ConfigException(message);
    }

    FilePermission backendPermission;
    try
    {
      backendPermission =
          FilePermission.decodeUNIXMode(config.getDBDirectoryPermissions());
    }
    catch(Exception e)
    {
      Message message =
          ERR_CONFIG_BACKEND_MODE_INVALID.get(config.dn().toString());
      throw new ConfigException(message);
    }

    //Make sure the mode will allow the server itself access to
    //the database
    if(!backendPermission.isOwnerWritable() ||
        !backendPermission.isOwnerReadable() ||
        !backendPermission.isOwnerExecutable())
    {
      Message message = ERR_CONFIG_BACKEND_INSANE_MODE.get(
          config.getDBDirectoryPermissions());
      throw new ConfigException(message);
    }

    // Get the backend database backendDirectory permissions and apply
    if(FilePermission.canSetPermissions())
    {
      try
      {
        if(!FilePermission.setPermissions(backendDirectory, backendPermission))
        {
          Message message = WARN_JEB_UNABLE_SET_PERMISSIONS.get(
              backendPermission.toString(), backendDirectory.toString());
          logError(message);
        }
      }
      catch(Exception e)
      {
        // Log an warning that the permissions were not set.
        Message message = WARN_JEB_SET_PERMISSIONS_FAILED.get(
            backendDirectory.toString(), e.toString());
        logError(message);
      }
    }

    // Open the database environment
    env = new Environment(backendDirectory,
                          envConfig);

    if (debugEnabled())
    {
      TRACER.debugInfo("JE (%s) environment opened with the following " +
          "config: %n%s", JEVersion.CURRENT_VERSION.toString(),
                          env.getConfig().toString());

      // Get current size of heap in bytes
      long heapSize = Runtime.getRuntime().totalMemory();

      // Get maximum size of heap in bytes. The heap cannot grow beyond this
      // size.
      // Any attempt will result in an OutOfMemoryException.
      long heapMaxSize = Runtime.getRuntime().maxMemory();

      // Get amount of free memory within the heap in bytes. This size will
      // increase
      // after garbage collection and decrease as new objects are created.
      long heapFreeSize = Runtime.getRuntime().freeMemory();

      TRACER.debugInfo("Current size of heap: %d bytes", heapSize);
      TRACER.debugInfo("Max size of heap: %d bytes", heapMaxSize);
      TRACER.debugInfo("Free memory in heap: %d bytes", heapFreeSize);
    }

    compressedSchema = new JECompressedSchema(env);
    openAndRegisterEntryContainers(config.getBaseDN());
  }

  /**
   * Opens the entry container for a base DN. If the entry container does not
   * exist for the base DN, it will be created. The entry container will be
   * opened with the same mode as the root container. Any entry containers
   * opened in a read only root container will also be read only. Any entry
   * containers opened in a non transactional root container will also be non
   * transactional.
   *
   * @param baseDN The base DN of the entry container to open.
   * @param name The name of the entry container or <CODE>NULL</CODE> to open
   * the default entry container for the given base DN.
   * @return The opened entry container.
   * @throws DatabaseException If an error occurs while opening the entry
   *                           container.
   * @throws ConfigException If an configuration error occurs while opening
   *                         the entry container.
   */
  public EntryContainer openEntryContainer(DN baseDN, String name)
      throws DatabaseException, ConfigException
  {
    String databasePrefix;
    if(name == null || name.equals(""))
    {
      databasePrefix = baseDN.toNormalizedString();
    }
    else
    {
      databasePrefix = name;
    }

    EntryContainer ec = new EntryContainer(baseDN, databasePrefix,
                                           backend, config, env, this);
    ec.open();
    return ec;
  }

  /**
   * Registeres the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @param entryContainer The entry container to register for the baseDN.
   * @throws DatabaseException If an error occurs while opening the entry
   *                           container.
   */
  public void registerEntryContainer(DN baseDN,
                                     EntryContainer entryContainer)
      throws DatabaseException
  {
    EntryContainer ec1=this.entryContainers.get(baseDN);

    //If an entry container for this baseDN is already open we don't allow
    //another to be opened.
    if (ec1 != null)
      throw new DatabaseException("An entry container named " +
          ec1.getDatabasePrefix() + " is alreadly registered for base DN " +
          baseDN.toString());

    this.entryContainers.put(baseDN, entryContainer);
  }

  /**
   * Opens the entry containers for multiple base DNs.
   *
   * @param baseDNs The base DNs of the entry containers to open.
   * @throws DatabaseException If an error occurs while opening the entry
   *                           container.
   * @throws ConfigException if a configuration error occurs while opening the
   *                         container.
   */
  private void openAndRegisterEntryContainers(Set<DN> baseDNs)
      throws DatabaseException, ConfigException
  {
    EntryID id;
    EntryID highestID = null;
    for(DN baseDN : baseDNs)
    {
      EntryContainer ec = openEntryContainer(baseDN, null);
      id = ec.getHighestEntryID();
      registerEntryContainer(baseDN, ec);
      if(highestID == null || id.compareTo(highestID) > 0)
      {
        highestID = id;
      }
    }

    nextid = new AtomicLong(highestID.longValue() + 1);
  }

  /**
   * Unregisteres the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @return The entry container that was unregistered or NULL if a entry
   * container for the base DN was not registered.
   */
  public EntryContainer unregisterEntryContainer(DN baseDN)
  {
    return entryContainers.remove(baseDN);

  }

  /**
   * Retrieves the compressed schema manager for this backend.
   *
   * @return  The compressed schema manager for this backend.
   */
  public JECompressedSchema getCompressedSchema()
  {
    return compressedSchema;
  }

  /**
   * Get the DatabaseEnvironmentMonitor object for JE environment used by this
   * root container.
   *
   * @return The DatabaseEnvironmentMonito object.
   */
  public DatabaseEnvironmentMonitor getMonitorProvider()
  {
    if(monitor == null)
    {
      String monitorName = backend.getBackendID() + " Database Environment";
      monitor = new DatabaseEnvironmentMonitor(monitorName, this);
    }

    return monitor;
  }

  /**
   * Preload the database cache. There is no preload if the configured preload
   * time limit is zero.
   *
   * @param timeLimit The time limit for the preload process.
   */
  public void preload(long timeLimit)
  {
    if (timeLimit > 0)
    {
      // Get a list of all the databases used by the backend.
      ArrayList<DatabaseContainer> dbList =
          new ArrayList<DatabaseContainer>();
      for (EntryContainer ec : entryContainers.values())
      {
        ec.sharedLock.lock();
        try
        {
          ec.listDatabases(dbList);
        }
        finally
        {
          ec.sharedLock.unlock();
        }
      }

      // Sort the list in order of priority.
      Collections.sort(dbList, new DbPreloadComparator());

      // Preload each database until we reach the time limit or the cache
      // is filled.
      try
      {
        // Configure preload of Leaf Nodes (LNs) containing the data values.
        PreloadConfig preloadConfig = new PreloadConfig();
        preloadConfig.setLoadLNs(true);

        Message message =
            NOTE_JEB_CACHE_PRELOAD_STARTED.get(backend.getBackendID());
        logError(message);

        boolean isInterrupted = false;

        long timeEnd = System.currentTimeMillis() + timeLimit;

        for (DatabaseContainer db : dbList)
        {
          // Calculate the remaining time.
          long timeRemaining = timeEnd - System.currentTimeMillis();
          if (timeRemaining <= 0)
          {
            break;
          }

          preloadConfig.setMaxMillisecs(timeRemaining);
          PreloadStats preloadStats = db.preload(preloadConfig);

          if(debugEnabled())
          {
            TRACER.debugInfo("file=" + db.getName() +
                      " LNs=" + preloadStats.getNLNsLoaded());
          }

          // Stop if the cache is full or the time limit has been exceeded.
          PreloadStatus preloadStatus = preloadStats.getStatus();
          if (preloadStatus != PreloadStatus.SUCCESS)
          {
            if (preloadStatus == PreloadStatus.EXCEEDED_TIME) {
              message =
                NOTE_JEB_CACHE_PRELOAD_INTERRUPTED_BY_TIME.get(
                backend.getBackendID(), db.getName());
              logError(message);
            } else if (preloadStatus == PreloadStatus.FILLED_CACHE) {
              message =
                NOTE_JEB_CACHE_PRELOAD_INTERRUPTED_BY_SIZE.get(
                backend.getBackendID(), db.getName());
              logError(message);
            } else {
              message =
                NOTE_JEB_CACHE_PRELOAD_INTERRUPTED_UNKNOWN.get(
                backend.getBackendID(), db.getName());
              logError(message);
            }

            isInterrupted = true;
            break;
          }

          message = NOTE_JEB_CACHE_DB_PRELOADED.get(db.getName());
          logError(message);
        }

        if (!isInterrupted) {
          message = NOTE_JEB_CACHE_PRELOAD_DONE.get(backend.getBackendID());
          logError(message);
        }

        // Log an informational message about the size of the cache.
        EnvironmentStats stats = env.getStats(new StatsConfig());
        long total = stats.getCacheTotalBytes();

        message =
            NOTE_JEB_CACHE_SIZE_AFTER_PRELOAD.get(total / (1024 * 1024));
        logError(message);
      }
      catch (DatabaseException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
          ERR_JEB_CACHE_PRELOAD.get(backend.getBackendID(),
          (e.getCause() != null ? e.getCause().getMessage() :
            stackTraceToSingleLineString(e)));
        logError(message);
      }
    }
  }

  /**
   * Synchronously invokes the cleaner on the database environment then forces a
   * checkpoint to delete the log files that are no longer in use.
   *
   * @throws DatabaseException If an error occurs while cleaning the database
   * environment.
   */
  private void cleanDatabase()
       throws DatabaseException
  {
    Message message;

    FilenameFilter filenameFilter = new FilenameFilter()
    {
      public boolean accept(File d, String name)
      {
        return name.endsWith(".jdb");
      }
    };

    File backendDirectory = env.getHome();
    int beforeLogfileCount = backendDirectory.list(filenameFilter).length;

    message = NOTE_JEB_CLEAN_DATABASE_START.get(
        beforeLogfileCount, backendDirectory.getPath());
    logError(message);

    int currentCleaned = 0;
    int totalCleaned = 0;
    while ((currentCleaned = env.cleanLog()) > 0)
    {
      totalCleaned += currentCleaned;
    }

    message = NOTE_JEB_CLEAN_DATABASE_MARKED.get(totalCleaned);
    logError(message);

    if (totalCleaned > 0)
    {
      CheckpointConfig force = new CheckpointConfig();
      force.setForce(true);
      env.checkpoint(force);
    }

    int afterLogfileCount = backendDirectory.list(filenameFilter).length;

    message = NOTE_JEB_CLEAN_DATABASE_FINISH.get(afterLogfileCount);
    logError(message);

  }

  /**
   * Close the root entryContainer.
   *
   * @throws DatabaseException If an error occurs while attempting to close
   * the entryContainer.
   */
  public void close() throws DatabaseException
  {
    for(DN baseDN : entryContainers.keySet())
    {
      EntryContainer ec = unregisterEntryContainer(baseDN);
      ec.exclusiveLock.lock();
      try
      {
        ec.close();
      }
      finally
      {
        ec.exclusiveLock.unlock();
      }
    }

    compressedSchema.close();

    if (env != null)
    {
      env.close();
      env = null;
    }

    config.removeLocalDBChangeListener(this);
  }

  /**
   * Return all the entry containers in this root container.
   *
   * @return The entry containers in this root container.
   */
  public Collection<EntryContainer> getEntryContainers()
  {
    return entryContainers.values();
  }

  /**
   * Returns all the baseDNs this root container stores.
   *
   * @return The set of DNs this root container stores.
   */
  public Set<DN> getBaseDNs()
  {
    return entryContainers.keySet();
  }

  /**
   * Return the entry container for a specific base DN.
   *
   * @param baseDN The base DN of the entry container to retrive.
   * @return The entry container for the base DN.
   */
  public EntryContainer getEntryContainer(DN baseDN)
  {
    EntryContainer ec = null;
    DN nodeDN = baseDN;

    while (ec == null && nodeDN != null)
    {
      ec = entryContainers.get(nodeDN);
      if (ec == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }

    return ec;
  }

  /**
   * Get the environment stats of the JE environment used in this root
   * container.
   *
   * @param statsConfig The configuration to use for the EnvironmentStats
   *                    object.
   * @return The environment status of the JE environment.
   * @throws DatabaseException If an error occurs while retriving the stats
   *                           object.
   */
  public EnvironmentStats getEnvironmentStats(StatsConfig statsConfig)
      throws DatabaseException
  {
    return env.getStats(statsConfig);
  }

  /**
   * Get the environment lock stats of the JE environment used in this
   * root container.
   *
   * @param statsConfig The configuration to use for the EnvironmentStats
   *                    object.
   * @return The environment status of the JE environment.
   * @throws DatabaseException If an error occurs while retriving the stats
   *                           object.
   */
  public LockStats getEnvironmentLockStats(StatsConfig statsConfig)
      throws DatabaseException
  {
    return env.getLockStats(statsConfig);
  }

  /**
   * Get the environment transaction stats of the JE environment used
   * in this root container.
   *
   * @param statsConfig The configuration to use for the EnvironmentStats
   *                    object.
   * @return The environment status of the JE environment.
   * @throws DatabaseException If an error occurs while retriving the stats
   *                           object.
   */
  public TransactionStats getEnvironmentTransactionStats(
      StatsConfig statsConfig) throws DatabaseException
  {
    return env.getTransactionStats(statsConfig);
  }

  /**
   * Get the environment config of the JE environment used in this root
   * container.
   *
   * @return The environment config of the JE environment.
   * @throws DatabaseException If an error occurs while retriving the
   *                           configuration object.
   */
  public EnvironmentConfig getEnvironmentConfig() throws DatabaseException
  {
    return env.getConfig();
  }

  /**
   * Get the backend configuration used by this root container.
   *
   * @return The JE backend configuration used by this root container.
   */
  public LocalDBBackendCfg getConfiguration()
  {
    return config;
  }

  /**
   * Get the total number of entries in this root container.
   *
   * @return The number of entries in this root container
   * @throws DatabaseException If an error occurs while retriving the entry
   *                           count.
   */
  public long getEntryCount() throws DatabaseException
  {
    long entryCount = 0;
    for(EntryContainer ec : this.entryContainers.values())
    {
      ec.sharedLock.lock();
      try
      {
        entryCount += ec.getEntryCount();
      }
      finally
      {
        ec.sharedLock.unlock();
      }
    }

    return entryCount;
  }

  /**
   * Assign the next entry ID.
   *
   * @return The assigned entry ID.
   */
  public EntryID getNextEntryID()
  {
    return new EntryID(nextid.getAndIncrement());
  }

  /**
   * Return the lowest entry ID assigned.
   *
   * @return The lowest entry ID assigned.
   */
  public Long getLowestEntryID()
  {
    return 1L;
  }

  /**
   * Return the highest entry ID assigned.
   *
   * @return The highest entry ID assigned.
   */
  public Long getHighestEntryID()
  {
    return (nextid.get() - 1);
  }

  /**
   * Resets the next entry ID counter to zero.  This should only be used after
   * clearing all databases.
   */
  public void resetNextEntryID()
  {
    nextid.set(1);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      LocalDBBackendCfg cfg,
      List<Message> unacceptableReasons)
  {
    boolean acceptable = true;

    File parentDirectory = getFileForPath(config.getDBDirectory());
    File backendDirectory = new File(parentDirectory, config.getBackendId());

    //Make sure the directory either already exists or is able to create.
    if (!backendDirectory.exists())
    {
      if(!backendDirectory.mkdirs())
      {
        Message message =
          ERR_JEB_CREATE_FAIL.get(backendDirectory.getPath());
        unacceptableReasons.add(message);
        acceptable = false;
      }
      else
      {
        backendDirectory.delete();
      }
    }
    //Make sure the directory is valid.
    else if (!backendDirectory.isDirectory())
    {
      Message message =
          ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath());
      unacceptableReasons.add(message);
      acceptable = false;
    }

    try
    {
      FilePermission newBackendPermission =
          FilePermission.decodeUNIXMode(cfg.getDBDirectoryPermissions());

      //Make sure the mode will allow the server itself access to
      //the database
      if(!newBackendPermission.isOwnerWritable() ||
          !newBackendPermission.isOwnerReadable() ||
          !newBackendPermission.isOwnerExecutable())
      {
        Message message = ERR_CONFIG_BACKEND_INSANE_MODE.get(
            cfg.getDBDirectoryPermissions());
        unacceptableReasons.add(message);
        acceptable = false;
      }
    }
    catch(Exception e)
    {
      Message message =
              ERR_CONFIG_BACKEND_MODE_INVALID.get(cfg.dn().toString());
      unacceptableReasons.add(message);
      acceptable = false;
    }

    try
    {
      ConfigurableEnvironment.parseConfigEntry(cfg);
    }
    catch (Exception e)
    {
      unacceptableReasons.add(Message.raw(e.getLocalizedMessage()));
      acceptable = false;
    }

    return acceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(LocalDBBackendCfg cfg)
  {
    ConfigChangeResult ccr;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    try
    {
      if(env != null)
      {
        // Check if any JE non-mutable properties were changed.
        EnvironmentConfig oldEnvConfig = env.getConfig();
        EnvironmentConfig newEnvConfig =
            ConfigurableEnvironment.parseConfigEntry(cfg);
        Map<?,?> paramsMap = EnvironmentParams.SUPPORTED_PARAMS;

        // Iterate through native JE properties.
        SortedSet<String> jeProperties = cfg.getJEProperty();
        for (String jeEntry : jeProperties) {
          // There is no need to validate properties yet again.
          StringTokenizer st = new StringTokenizer(jeEntry, "=");
          if (st.countTokens() == 2) {
            String jePropertyName = st.nextToken();
            String jePropertyValue = st.nextToken();
            ConfigParam param = (ConfigParam) paramsMap.get(jePropertyName);
            if (!param.isMutable()) {
              String oldValue = oldEnvConfig.getConfigParam(param.getName());
              String newValue = jePropertyValue;
              if (!oldValue.equalsIgnoreCase(newValue)) {
                adminActionRequired = true;
                messages.add(INFO_CONFIG_JE_PROPERTY_REQUIRES_RESTART.get(
                        jePropertyName));
                if(debugEnabled()) {
                  TRACER.debugInfo("The change to the following property " +
                    "will take effect when the component is restarted: " +
                    jePropertyName);
                }
              }
            }
          }
        }

        // Iterate through JE configuration attributes.
        for (Object o : paramsMap.values())
        {
          ConfigParam param = (ConfigParam) o;
          if (!param.isMutable())
          {
            String oldValue = oldEnvConfig.getConfigParam(param.getName());
            String newValue = newEnvConfig.getConfigParam(param.getName());
            if (!oldValue.equalsIgnoreCase(newValue))
            {
              adminActionRequired = true;
              String configAttr = ConfigurableEnvironment.
                  getAttributeForProperty(param.getName());
              if (configAttr != null)
              {
                messages.add(INFO_JEB_CONFIG_ATTR_REQUIRES_RESTART.get(
                        configAttr));
              }
              if(debugEnabled())
              {
                TRACER.debugInfo("The change to the following property will " +
                    "take effect when the backend is restarted: " +
                    param.getName());
              }
            }
          }
        }

        // This takes care of changes to the JE environment for those
        // properties that are mutable at runtime.
        env.setMutableConfig(newEnvConfig);

        if (debugEnabled())
        {
          TRACER.debugInfo(env.getConfig().toString());
        }
      }

      // Create the directory if it doesn't exist.
      if(!cfg.getDBDirectory().equals(this.config.getDBDirectory()))
      {
        File parentDirectory = getFileForPath(config.getDBDirectory());
        File backendDirectory =
          new File(parentDirectory, config.getBackendId());

        if (!backendDirectory.exists())
        {
          if(!backendDirectory.mkdirs())
          {
            messages.add(ERR_JEB_CREATE_FAIL.get(
                backendDirectory.getPath()));
            ccr = new ConfigChangeResult(
                DirectoryServer.getServerErrorResultCode(),
                adminActionRequired,
                messages);
            return ccr;
          }
        }
        //Make sure the directory is valid.
        else if (!backendDirectory.isDirectory())
        {
          messages.add(ERR_JEB_DIRECTORY_INVALID.get(
              backendDirectory.getPath()));
          ccr = new ConfigChangeResult(
              DirectoryServer.getServerErrorResultCode(),
              adminActionRequired,
              messages);
          return ccr;
        }

        adminActionRequired = true;
        messages.add(INFO_JEB_CONFIG_DB_DIR_REQUIRES_RESTART.get(
                        this.config.getDBDirectory(), cfg.getDBDirectory()));
      }

      if(!cfg.getDBDirectoryPermissions().equalsIgnoreCase(
          config.getDBDirectoryPermissions()) ||
          !cfg.getDBDirectory().equals(this.config.getDBDirectory()))
      {
        FilePermission backendPermission;
        try
        {
          backendPermission =
              FilePermission.decodeUNIXMode(cfg.getDBDirectoryPermissions());
        }
        catch(Exception e)
        {
          messages.add(ERR_CONFIG_BACKEND_MODE_INVALID.get(
              config.dn().toString()));
          ccr = new ConfigChangeResult(
              DirectoryServer.getServerErrorResultCode(),
              adminActionRequired,
              messages);
          return ccr;
        }

        //Make sure the mode will allow the server itself access to
        //the database
        if(!backendPermission.isOwnerWritable() ||
            !backendPermission.isOwnerReadable() ||
            !backendPermission.isOwnerExecutable())
        {
          messages.add(ERR_CONFIG_BACKEND_INSANE_MODE.get(
              cfg.getDBDirectoryPermissions()));
          ccr = new ConfigChangeResult(
              DirectoryServer.getServerErrorResultCode(),
              adminActionRequired,
              messages);
          return ccr;
        }

        // Get the backend database backendDirectory permissions and apply
        if(FilePermission.canSetPermissions())
        {
          File parentDirectory = getFileForPath(config.getDBDirectory());
          File backendDirectory = new File(parentDirectory,
              config.getBackendId());
          try
          {
            if(!FilePermission.setPermissions(backendDirectory,
                backendPermission))
            {
              Message message = WARN_JEB_UNABLE_SET_PERMISSIONS.get(
                  backendPermission.toString(), backendDirectory.toString());
              logError(message);
            }
          }
          catch(Exception e)
          {
            // Log an warning that the permissions were not set.
            Message message = WARN_JEB_SET_PERMISSIONS_FAILED.get(
                backendDirectory.toString(), e.toString());
            logError(message);
          }
        }
      }

      this.config = cfg;
    }
    catch (Exception e)
    {
      messages.add(Message.raw(stackTraceToSingleLineString(e)));
      ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                   adminActionRequired,
                                   messages);
      return ccr;
    }


    ccr = new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
                                 messages);
    return ccr;
  }
}
