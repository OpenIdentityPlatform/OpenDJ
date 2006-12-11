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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.PreloadStatus;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.CheckpointConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.io.File;
import java.io.FilenameFilter;

import org.opends.server.monitors.DatabaseEnvironmentMonitor;
import org.opends.server.types.*;
import org.opends.server.loggers.Debug;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.loggers.Debug.debugException;
import static org.opends.server.loggers.Debug.debugEnter;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.
    MSGID_JEB_CACHE_SIZE_AFTER_PRELOAD;
import static org.opends.server.messages.JebMessages.
    MSGID_JEB_CLEAN_DATABASE_START;
import static org.opends.server.messages.JebMessages.
    MSGID_JEB_CLEAN_DATABASE_MARKED;
import static org.opends.server.messages.JebMessages.
    MSGID_JEB_CLEAN_DATABASE_FINISH;
import static org.opends.server.messages.JebMessages.
    MSGID_JEB_SET_PERMISSIONS_FAILED;
import org.opends.server.api.Backend;

/**
 * Wrapper class for the JE environment. Root container holds all the entry
 * containers for each base DN. It also maintains all the openings and closings
 * of the entry containers.
 */
public class RootContainer
{
    /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.RootContainer";

  /**
   * The JE database environment.
   */
  private Environment env;

  /**
   * The backend configuration.
   */
  private Config config;

  /**
   * The backend to which this entry root container belongs.
   */
  private Backend backend;

  /**
   * The database environment monitor for this JE environment.
   */
  private DatabaseEnvironmentMonitor monitor;

  /**
   * A configurable component to handle changes to the configuration of
   * the database environment.
   */
  private ConfigurableEnvironment configurableEnv;

  /**
   * The base DNs contained in this entryContainer.
   */
  private ConcurrentHashMap<DN, EntryContainer> entryContainers;

  /**
   * The cached value of the next entry identifier to be assigned.
   */
  private AtomicLong nextid = new AtomicLong(1);

  /**
   * Creates a new RootContainer object. Each root container represents a JE
   * environment.
   *
   * @param config The configuration of the JE backend.
   * @param backend A reference to the JE back end that is creating this
   *                root container.
   */
  public RootContainer(Config config, Backend backend)
  {
    this.env = null;
    this.configurableEnv = null;
    this.monitor = null;
    this.entryContainers = new ConcurrentHashMap<DN, EntryContainer>();
    this.backend = backend;
    this.config = config;
  }

  /**
   * Helper method to apply database directory permissions and create a new
   * JE environment.
   *
   * @param backendDirectory The environment home directory for JE.
   * @param backendPermission The file permissions for the environment home
   *                          directory.
   * @param envConfig The JE environment configuration.
   * @throws DatabaseException If an error occurs when creating the environment.
   */
  private void open(File backendDirectory,
                    FilePermission backendPermission,
                    EnvironmentConfig envConfig) throws DatabaseException
  {
    // Get the backend database backendDirectory permissions and apply
    if(FilePermission.canSetPermissions())
    {
      try
      {
        if(!FilePermission.setPermissions(backendDirectory, backendPermission))
        {
          throw new Exception();
        }
      }
      catch(Exception e)
      {
        // Log an warning that the permissions were not set.
        int msgID = MSGID_JEB_SET_PERMISSIONS_FAILED;
        String message = getMessage(msgID, backendDirectory.getPath());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }

    // Open the database environment
    env = new Environment(backendDirectory,
                          envConfig);

    Debug.debugMessage(DebugLogCategory.BACKEND, DebugLogSeverity.INFO,
                         CLASS_NAME, "initializeBackend",
                         env.getConfig().toString());
  }

  /**
   * Opens the root container.
   *
   * @throws DatabaseException If an error occurs when opening the container.
   */
  public void open() throws DatabaseException
  {
    open(config.getBackendDirectory(),
         config.getBackendPermission(),
         config.getEnvironmentConfig());
  }

  /**
   * Opens the root container using the configuration parameters provided. Any
   * configuration parameters provided will override the parameters in the
   * JE configuration object.
   *
   * @param backendDirectory The environment home directory for JE.
   * @param backendPermission he file permissions for the environment home
   *                          directory.
   * @param readOnly Open the container in read only mode.
   * @param allowCreate Allow creating new entries in the container.
   * @param transactional Use transactions on operations.
   * @param txnNoSync Use asynchronous transactions.
   * @param isLocking Create the environment with locking.
   * @param runCheckPointer Start the checkpointer.
   * @throws DatabaseException If an error occurs when openinng the container.
   */
  public void open(File backendDirectory,
                   FilePermission backendPermission,
                   boolean readOnly,
                   boolean allowCreate,
                   boolean transactional,
                   boolean txnNoSync,
                   boolean isLocking,
                   boolean runCheckPointer) throws DatabaseException
  {

    EnvironmentConfig envConfig;
    if(config.getEnvironmentConfig() != null)
    {
      envConfig = config.getEnvironmentConfig();
    }
    else
    {
      envConfig = new EnvironmentConfig();
    }
    envConfig.setReadOnly(readOnly);
    envConfig.setAllowCreate(allowCreate);
    envConfig.setTransactional(transactional);
    envConfig.setTxnNoSync(txnNoSync);
    envConfig.setConfigParam("je.env.isLocking", String.valueOf(isLocking));
    envConfig.setConfigParam("je.env.runCheckpointer",
                             String.valueOf(runCheckPointer));

    open(backendDirectory, backendPermission, envConfig);
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
   * @return The opened entry container.
   * @throws DatabaseException If an error occurs while opening the entry
   *                           container.
   */
  public EntryContainer openEntryContainer(DN baseDN) throws DatabaseException
  {
    EntryContainer ec = new EntryContainer(baseDN, backend, config, env, this);
    EntryContainer ec1=this.entryContainers.get(baseDN);
    //If an entry container for this baseDN is already open we don't allow
    //another to be opened.
      if (ec1 != null)
          throw new DatabaseException("Entry container for baseDN " +
                  baseDN.toString() + " already is open.");
    if(env.getConfig().getReadOnly())
    {
      ec.openReadOnly();
    }
    else if(!env.getConfig().getTransactional())
    {
      ec.openNonTransactional(true);
    }
    else
    {
      ec.open();
    }
    this.entryContainers.put(baseDN, ec);

    return ec;
  }

  /**
   * Opens the entry containers for multiple base DNs.
   *
   * @param baseDNs The base DNs of the entry containers to open.
   * @throws DatabaseException If an error occurs while opening the entry
   *                           container.
   */
  public void openEntryContainers(DN[] baseDNs) throws DatabaseException
  {
    EntryID id = null;
    EntryID highestID = null;
    for(DN baseDN : baseDNs)
    {
      EntryContainer ec = openEntryContainer(baseDN);
      id = ec.getHighestEntryID();
      if(highestID == null || id.compareTo(highestID) > 0)
      {
        highestID = id;
      }
    }

    nextid = new AtomicLong(highestID.longValue() + 1);
  }

  /**
   * Close the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @throws DatabaseException If an error occurs while closing the entry
   *                           container.
   */
  public void closeEntryContainer(DN baseDN) throws DatabaseException
  {
    getEntryContainer(baseDN).close();
    entryContainers.remove(baseDN);
  }

  /**
   * Close and remove a entry container for a base DN from disk.
   *
   * @param baseDN The base DN of the entry container to remove.
   * @throws DatabaseException If an error occurs while removing the entry
   *                           container.
   */
  public void removeEntryContainer(DN baseDN) throws DatabaseException
  {
    getEntryContainer(baseDN).close();
    getEntryContainer(baseDN).removeContainer();
    entryContainers.remove(baseDN);
  }

  /**
   * Get the ConfigurableEnvironment object for JE environment used by this
   * root container.
   *
   * @return The ConfigurableEnvironment object.
   */
  public ConfigurableEnvironment getConfigurableEnvironment()
  {
    if(configurableEnv == null)
    {
      DN envConfigDN = config.getEnvConfigDN();
      if (envConfigDN != null)
      {
        configurableEnv = new ConfigurableEnvironment(envConfigDN, env);
      }
    }

    return configurableEnv;
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
      monitor = new DatabaseEnvironmentMonitor(monitorName, env);
    }

    return monitor;
  }

  /**
   * Preload the database cache. There is no preload if the configured preload
   * time limit is zero.
   */
  public void preload()
  {
    assert debugEnter(CLASS_NAME, "preload");

    long timeLimit = config.getPreloadTimeLimit();

    if (timeLimit > 0)
    {
      // Get a list of all the databases used by the backend.
      ArrayList<Database> dbList = new ArrayList<Database>();
      for (EntryContainer ec : entryContainers.values())
      {
        ec.listDatabases(dbList);
      }

      // Sort the list in order of priority.
      Collections.sort(dbList, new DbPreloadComparator());

      // Preload each database until we reach the time limit or the cache
      // is filled.
      try
      {
        long timeEnd = System.currentTimeMillis() + timeLimit;

        // Configure preload of Leaf Nodes (LNs) containing the data values.
        PreloadConfig preloadConfig = new PreloadConfig();
        preloadConfig.setLoadLNs(true);

        for (Database db : dbList)
        {
          // Calculate the remaining time.
          long timeRemaining = timeEnd - System.currentTimeMillis();
          if (timeRemaining <= 0)
          {
            break;
          }

          preloadConfig.setMaxMillisecs(timeRemaining);
          PreloadStats preloadStats = db.preload(preloadConfig);
/*
          System.out.println("file=" + db.getDatabaseName() +
                             " LNs=" + preloadStats.getNLNsLoaded());
*/

          // Stop if the cache is full or the time limit has been exceeded.
          if (preloadStats.getStatus() != PreloadStatus.SUCCESS)
          {
            break;
          }
        }

        // Log an informational message about the size of the cache.
        EnvironmentStats stats = env.getStats(new StatsConfig());
        long total = stats.getCacheTotalBytes();

        int msgID = MSGID_JEB_CACHE_SIZE_AFTER_PRELOAD;
        String message = getMessage(msgID, total / (1024 * 1024));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                 msgID);
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "preload", e);
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
    assert debugEnter(CLASS_NAME, "cleanDatabase");

    int msgID;
    String message;

    FilenameFilter filenameFilter = new FilenameFilter()
    {
      public boolean accept(File d, String name)
      {
        return name.endsWith(".jdb");
      }
    };

    File backendDirectory = env.getHome();
    int beforeLogfileCount = backendDirectory.list(filenameFilter).length;

    msgID = MSGID_JEB_CLEAN_DATABASE_START;
    message = getMessage(msgID, beforeLogfileCount, backendDirectory.getPath());
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

    int currentCleaned = 0;
    int totalCleaned = 0;
    while ((currentCleaned = env.cleanLog()) > 0)
    {
      totalCleaned += currentCleaned;
    }

    msgID = MSGID_JEB_CLEAN_DATABASE_MARKED;
    message = getMessage(msgID, totalCleaned);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

    if (totalCleaned > 0)
    {
      CheckpointConfig force = new CheckpointConfig();
      force.setForce(true);
      env.checkpoint(force);
    }

    int afterLogfileCount = backendDirectory.list(filenameFilter).length;

    msgID = MSGID_JEB_CLEAN_DATABASE_FINISH;
    message = getMessage(msgID, afterLogfileCount);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

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
      entryContainers.get(baseDN).close();
      entryContainers.remove(baseDN);
    }

    env.close();
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
   * Apply new configuration to the JE environment.
   *
   * @param newConfig The new configuration to apply.
   * @throws DatabaseException If an error occurs while applying the new
   *                           configuration.
   */
  public void applyNewConfig(Config newConfig) throws DatabaseException
  {
    // Check for changes to the database directory permissions
    FilePermission oldPermission = config.getBackendPermission();
    FilePermission newPermission = newConfig.getBackendPermission();

    if(FilePermission.canSetPermissions() &&
        !FilePermission.toUNIXMode(oldPermission).equals(
        FilePermission.toUNIXMode(newPermission)))
    {
      try
      {
        if(!FilePermission.setPermissions(newConfig.getBackendDirectory(),
                                          newPermission))
        {
          throw new Exception();
        }
      }
      catch(Exception e)
      {
        // Log an warning that the permissions were not set.
        int msgID = MSGID_JEB_SET_PERMISSIONS_FAILED;
        String message = getMessage(msgID,
                                    config.getBackendDirectory().getPath());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }

    // Check if any JE non-mutable properties were changed.
    EnvironmentConfig oldEnvConfig = this.config.getEnvironmentConfig();
    EnvironmentConfig newEnvConfig = newConfig.getEnvironmentConfig();
    Map paramsMap = EnvironmentParams.SUPPORTED_PARAMS;
    for (Object o : paramsMap.values())
    {
      ConfigParam param = (ConfigParam)o;
      if (!param.isMutable())
      {
        String oldValue = oldEnvConfig.getConfigParam(param.getName());
        String newValue = newEnvConfig.getConfigParam(param.getName());
        if (!oldValue.equalsIgnoreCase(newValue))
        {
          System.out.println("The change to the following property will " +
                             "take effect when the backend is restarted: " +
                             param.getName());
        }
      }
    }

    // This takes care of changes to the JE environment for those
    // properties that are mutable at runtime.
    env.setMutableConfig(newConfig.getEnvironmentConfig());

    config = newConfig;

    Debug.debugMessage(DebugLogCategory.BACKEND, DebugLogSeverity.INFO,
                       CLASS_NAME, "applyNewConfiguration",
                       env.getConfig().toString());
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
      entryCount += ec.getEntryCount();
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
}
