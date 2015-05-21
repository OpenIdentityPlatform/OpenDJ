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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;

import com.sleepycat.je.*;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Wrapper class for the JE environment. Root container holds all the entry
 * containers for each base DN. It also maintains all the openings and closings
 * of the entry containers.
 */
public class RootContainer
     implements ConfigurationChangeListener<LocalDBBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The JE database environment. */
  private Environment env;

  /** Used to force a checkpoint during import. */
  private final CheckpointConfig importForceCheckPoint = new CheckpointConfig();

  /** The backend configuration. */
  private LocalDBBackendCfg config;

  /** The backend to which this entry root container belongs. */
  private final Backend<?> backend;

  /** The database environment monitor for this JE environment. */
  private DatabaseEnvironmentMonitor monitor;

  /** The base DNs contained in this root container. */
  private final ConcurrentHashMap<DN, EntryContainer> entryContainers = new ConcurrentHashMap<DN, EntryContainer>();

  /** The cached value of the next entry identifier to be assigned. */
  private AtomicLong nextid = new AtomicLong(1);

  /** The compressed schema manager for this backend. */
  private JECompressedSchema compressedSchema;



  /**
   * Creates a new RootContainer object. Each root container represents a JE
   * environment.
   *
   * @param config The configuration of the JE backend.
   * @param backend A reference to the JE back end that is creating this
   *                root container.
   */
  RootContainer(Backend<?> backend, LocalDBBackendCfg config)
  {
    this.backend = backend;
    this.config = config;

    getMonitorProvider().enableFilterUseStats(config.isIndexFilterAnalyzerEnabled());
    getMonitorProvider().setMaxEntries(config.getIndexFilterAnalyzerMaxFilters());

    config.addLocalDBChangeListener(this);
    importForceCheckPoint.setForce(true);
  }

  /**
   * Opens the root container using the JE configuration object provided.
   *
   * @param  envConfig               The JE environment configuration.
   * @throws DatabaseException       If a database error occurs when creating
   *                                 the environment.
   * @throws InitializationException If an initialization error occurs while
   *                                 creating the environment.
   * @throws ConfigException         If an configuration error occurs while
   *                                 creating the environment.
   */
  public void open(EnvironmentConfig envConfig)
      throws DatabaseException, InitializationException, ConfigException
  {
    // Determine the backend database directory.
    File parentDirectory = getFileForPath(config.getDBDirectory());
    File backendDirectory = new File(parentDirectory, config.getBackendId());

    // Create the directory if it doesn't exist.
    if (!backendDirectory.exists())
    {
      if(!backendDirectory.mkdirs())
      {
        throw new ConfigException(ERR_CREATE_FAIL.get(backendDirectory.getPath()));
      }
    }
    //Make sure the directory is valid.
    else if (!backendDirectory.isDirectory())
    {
      throw new ConfigException(ERR_DIRECTORY_INVALID.get(backendDirectory.getPath()));
    }

    FilePermission backendPermission;
    try
    {
      backendPermission =
          FilePermission.decodeUNIXMode(config.getDBDirectoryPermissions());
    }
    catch(Exception e)
    {
      throw new ConfigException(ERR_CONFIG_BACKEND_MODE_INVALID.get(config.dn()));
    }

    //Make sure the mode will allow the server itself access to
    //the database
    if(!backendPermission.isOwnerWritable() ||
        !backendPermission.isOwnerReadable() ||
        !backendPermission.isOwnerExecutable())
    {
      LocalizableMessage message = ERR_CONFIG_BACKEND_INSANE_MODE.get(
          config.getDBDirectoryPermissions());
      throw new ConfigException(message);
    }

    // Get the backend database backendDirectory permissions and apply
    try
    {
      if(!FilePermission.setPermissions(backendDirectory, backendPermission))
      {
        logger.warn(WARN_UNABLE_SET_PERMISSIONS, backendPermission, backendDirectory);
      }
    }
    catch(Exception e)
    {
      // Log an warning that the permissions were not set.
      logger.warn(WARN_SET_PERMISSIONS_FAILED, backendDirectory, e);
    }

    // Open the database environment
    env = new Environment(backendDirectory,
                          envConfig);

    if (logger.isTraceEnabled())
    {
      logger.trace("JE (%s) environment opened with the following config: %n%s",
          JEVersion.CURRENT_VERSION, env.getConfig());

      // Get current size of heap in bytes
      long heapSize = Runtime.getRuntime().totalMemory();

      // Get maximum size of heap in bytes. The heap cannot grow beyond this size.
      // Any attempt will result in an OutOfMemoryException.
      long heapMaxSize = Runtime.getRuntime().maxMemory();

      // Get amount of free memory within the heap in bytes. This size will increase
      // after garbage collection and decrease as new objects are created.
      long heapFreeSize = Runtime.getRuntime().freeMemory();

      logger.trace("Current size of heap: %d bytes", heapSize);
      logger.trace("Max size of heap: %d bytes", heapMaxSize);
      logger.trace("Free memory in heap: %d bytes", heapFreeSize);
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
  EntryContainer openEntryContainer(DN baseDN, String name)
      throws DatabaseException, ConfigException
  {
    String databasePrefix;
    if(name == null || name.equals(""))
    {
      databasePrefix = baseDN.toNormalizedUrlSafeString();
    }
    else
    {
      databasePrefix = name;
    }

    EntryContainer ec = new EntryContainer(baseDN, databasePrefix, backend.getBackendID(), config, env, this);
    ec.open();
    return ec;
  }

  /**
   * Registers the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @param entryContainer The entry container to register for the baseDN.
   * @throws InitializationException If an error occurs while opening the
   *                                 entry container.
   */
  void registerEntryContainer(DN baseDN, EntryContainer entryContainer)
      throws InitializationException
  {
    EntryContainer ec1 = this.entryContainers.get(baseDN);

    // If an entry container for this baseDN is already open we don't allow
    // another to be opened.
    if (ec1 != null)
    {
      throw new InitializationException(ERR_ENTRY_CONTAINER_ALREADY_REGISTERED.get(ec1.getDatabasePrefix(), baseDN));
    }

    this.entryContainers.put(baseDN, entryContainer);
  }

  /**
   * Opens the entry containers for multiple base DNs.
   *
   * @param baseDNs The base DNs of the entry containers to open.
   * @throws DatabaseException       If a database error occurs while opening
   *                                 the entry container.
   * @throws InitializationException If an initialization error occurs while
   *                                 opening the entry container.
   * @throws ConfigException         If a configuration error occurs while
   *                                 opening the entry container.
   */
  private void openAndRegisterEntryContainers(Set<DN> baseDNs)
      throws DatabaseException, InitializationException, ConfigException
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
   * Unregisters the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @return The entry container that was unregistered or NULL if a entry
   * container for the base DN was not registered.
   */
  EntryContainer unregisterEntryContainer(DN baseDN)
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
      ArrayList<DatabaseContainer> dbList = new ArrayList<DatabaseContainer>();
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

        logger.info(NOTE_CACHE_PRELOAD_STARTED, backend.getBackendID());

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

          if(logger.isTraceEnabled())
          {
            logger.trace("file=" + db.getName() + " LNs=" + preloadStats.getNLNsLoaded());
          }

          // Stop if the cache is full or the time limit has been exceeded.
          PreloadStatus preloadStatus = preloadStats.getStatus();
          if (preloadStatus != PreloadStatus.SUCCESS)
          {
            if (preloadStatus == PreloadStatus.EXCEEDED_TIME) {
              logger.info(NOTE_CACHE_PRELOAD_INTERRUPTED_BY_TIME, backend.getBackendID(), db.getName());
            } else if (preloadStatus == PreloadStatus.FILLED_CACHE) {
              logger.info(NOTE_CACHE_PRELOAD_INTERRUPTED_BY_SIZE, backend.getBackendID(), db.getName());
            } else {
              logger.info(NOTE_CACHE_PRELOAD_INTERRUPTED_UNKNOWN, backend.getBackendID(), db.getName());
            }

            isInterrupted = true;
            break;
          }

          logger.info(NOTE_CACHE_DB_PRELOADED, db.getName());
        }

        if (!isInterrupted) {
          logger.info(NOTE_CACHE_PRELOAD_DONE, backend.getBackendID());
        }

        // Log an informational message about the size of the cache.
        EnvironmentStats stats = env.getStats(new StatsConfig());
        long total = stats.getCacheTotalBytes();

        logger.info(NOTE_CACHE_SIZE_AFTER_PRELOAD, total / (1024 * 1024));
      }
      catch (DatabaseException e)
      {
        logger.traceException(e);

        logger.error(ERR_CACHE_PRELOAD, backend.getBackendID(),
            stackTraceToSingleLineString(e.getCause() != null ? e.getCause() : e));
      }
    }
  }

  /**
   * Closes this root container.
   *
   * @throws DatabaseException If an error occurs while attempting to close
   * the root container.
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
    config.removeLocalDBChangeListener(this);

    if (env != null)
    {
      env.close();
      env = null;
    }
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
   * @param baseDN The base DN of the entry container to retrieve.
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
   * @throws DatabaseException If an error occurs while retrieving the stats
   *                           object.
   */
  public EnvironmentStats getEnvironmentStats(StatsConfig statsConfig)
      throws DatabaseException
  {
    return env.getStats(statsConfig);
  }

  /**
   * Get the environment transaction stats of the JE environment used
   * in this root container.
   *
   * @param statsConfig The configuration to use for the EnvironmentStats
   *                    object.
   * @return The environment status of the JE environment.
   * @throws DatabaseException If an error occurs while retrieving the stats
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
   * @throws DatabaseException If an error occurs while retrieving the
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
   * @throws DatabaseException If an error occurs while retrieving the entry
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
   * Resets the next entry ID counter to zero.  This should only be used after
   * clearing all databases.
   */
  public void resetNextEntryID()
  {
    nextid.set(1);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      LocalDBBackendCfg cfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    boolean acceptable = true;

    File parentDirectory = getFileForPath(config.getDBDirectory());
    File backendDirectory = new File(parentDirectory, config.getBackendId());

    //Make sure the directory either already exists or is able to create.
    if (!backendDirectory.exists())
    {
      if(!backendDirectory.mkdirs())
      {
        unacceptableReasons.add(ERR_CREATE_FAIL.get(backendDirectory.getPath()));
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
      unacceptableReasons.add(ERR_DIRECTORY_INVALID.get(backendDirectory.getPath()));
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
        LocalizableMessage message = ERR_CONFIG_BACKEND_INSANE_MODE.get(
            cfg.getDBDirectoryPermissions());
        unacceptableReasons.add(message);
        acceptable = false;
      }
    }
    catch(Exception e)
    {
      unacceptableReasons.add(ERR_CONFIG_BACKEND_MODE_INVALID.get(cfg.dn()));
      acceptable = false;
    }

    try
    {
      ConfigurableEnvironment.parseConfigEntry(cfg);
    }
    catch (Exception e)
    {
      unacceptableReasons.add(LocalizableMessage.raw(e.getLocalizedMessage()));
      acceptable = false;
    }

    return acceptable;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(LocalDBBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

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
              if (!oldValue.equalsIgnoreCase(jePropertyValue)) {
                ccr.setAdminActionRequired(true);
                ccr.addMessage(INFO_CONFIG_JE_PROPERTY_REQUIRES_RESTART.get(jePropertyName));
                if(logger.isTraceEnabled()) {
                  logger.trace("The change to the following property " +
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
              ccr.setAdminActionRequired(true);
              String configAttr = ConfigurableEnvironment.
                  getAttributeForProperty(param.getName());
              if (configAttr != null)
              {
                ccr.addMessage(NOTE_JEB_CONFIG_ATTR_REQUIRES_RESTART.get(configAttr));
              }
              else
              {
                ccr.addMessage(NOTE_JEB_CONFIG_ATTR_REQUIRES_RESTART.get(param.getName()));
              }
              if(logger.isTraceEnabled())
              {
                logger.trace("The change to the following property will " +
                    "take effect when the backend is restarted: " +
                    param.getName());
              }
            }
          }
        }

        // This takes care of changes to the JE environment for those
        // properties that are mutable at runtime.
        env.setMutableConfig(newEnvConfig);

        logger.trace("JE database configuration: %s", env.getConfig());
      }

      // Create the directory if it doesn't exist.
      if(!cfg.getDBDirectory().equals(this.config.getDBDirectory()))
      {
        File parentDirectory = getFileForPath(cfg.getDBDirectory());
        File backendDirectory =
          new File(parentDirectory, cfg.getBackendId());

        if (!backendDirectory.exists())
        {
          if(!backendDirectory.mkdirs())
          {
            ccr.addMessage(ERR_CREATE_FAIL.get(backendDirectory.getPath()));
            ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
            return ccr;
          }
        }
        //Make sure the directory is valid.
        else if (!backendDirectory.isDirectory())
        {
          ccr.addMessage(ERR_DIRECTORY_INVALID.get(backendDirectory.getPath()));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          return ccr;
        }

        ccr.setAdminActionRequired(true);
        ccr.addMessage(NOTE_CONFIG_DB_DIR_REQUIRES_RESTART.get(this.config.getDBDirectory(), cfg.getDBDirectory()));
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
          ccr.addMessage(ERR_CONFIG_BACKEND_MODE_INVALID.get(config.dn()));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          return ccr;
        }

        //Make sure the mode will allow the server itself access to
        //the database
        if(!backendPermission.isOwnerWritable() ||
            !backendPermission.isOwnerReadable() ||
            !backendPermission.isOwnerExecutable())
        {
          ccr.addMessage(ERR_CONFIG_BACKEND_INSANE_MODE.get(
              cfg.getDBDirectoryPermissions()));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          return ccr;
        }

        // Get the backend database backendDirectory permissions and apply
        File parentDirectory = getFileForPath(config.getDBDirectory());
        File backendDirectory = new File(parentDirectory, config.getBackendId());
        try
        {
          if (!FilePermission.setPermissions(backendDirectory, backendPermission))
          {
            logger.warn(WARN_UNABLE_SET_PERMISSIONS, backendPermission, backendDirectory);
          }
        }
        catch(Exception e)
        {
          // Log an warning that the permissions were not set.
          logger.warn(WARN_SET_PERMISSIONS_FAILED, backendDirectory, e);
        }
      }

      getMonitorProvider().enableFilterUseStats(
          cfg.isIndexFilterAnalyzerEnabled());
      getMonitorProvider()
          .setMaxEntries(cfg.getIndexFilterAnalyzerMaxFilters());

      this.config = cfg;
    }
    catch (Exception e)
    {
      ccr.addMessage(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      return ccr;
    }

    return ccr;
  }

  /**
   * Returns whether this container JE database environment is
   * open, valid and can be used.
   *
   * @return {@code true} if valid, or {@code false} otherwise.
   */
  public boolean isValid() {
    return env.isValid();
  }
}
