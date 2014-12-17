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
package org.opends.server.backends.pluggable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.messages.UtilityMessages;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.persistit.PersistItStorage;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.core.DefaultCompressedSchema;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.RuntimeInformation;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.messages.UtilityMessages.ERR_LDIF_SKIP;
import static org.opends.server.core.DirectoryServer.getServerErrorResultCode;
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
  private PersistItStorage storage; // FIXME JNR do not hardcode here

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

  // FIXME JNR Switch back to a database persisted implementation of CompressedSchema
  /** The compressed schema manager for this backend. */
  private CompressedSchema compressedSchema;

  private File backendDirectory;



  /**
   * Creates a new RootContainer object. Each root container represents a JE
   * environment.
   *
   * @param config The configuration of the JE backend.
   * @param backend A reference to the JE back end that is creating this
   *                root container.
   */
  public RootContainer(Backend<?> backend, LocalDBBackendCfg config)
  {
    this.backend = backend;
    this.config = config;
    this.backendDirectory = new File(getFileForPath(config.getDBDirectory()),
        config.getBackendId());

    getMonitorProvider().enableFilterUseStats(config.isIndexFilterAnalyzerEnabled());
    getMonitorProvider().setMaxEntries(config.getIndexFilterAnalyzerMaxFilters());

    config.addLocalDBChangeListener(this);
  }

  PersistItStorage getStorage()
  {
    return storage;
  }

  LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    RuntimeInformation.logInfo();
    if (!importConfig.appendToExistingData()
        && (importConfig.clearBackend() || config.getBaseDN().size() <= 1))
    {
      removeFiles();
    }
    try
    {
      open();
      try
      {
        final LDIFReader reader;
        try
        {
          reader = new LDIFReader(importConfig);
        }
        catch (Exception e)
        {
          LocalizableMessage m = ERR_LDIF_BACKEND_CANNOT_CREATE_LDIF_READER.get(
                           stackTraceToSingleLineString(e));
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                       m, e);
        }

        while (true)
        {
          final Entry entry;
          try
          {
            entry = reader.readEntry();
            if (entry == null)
            {
              break;
            }
          }
          catch (LDIFException le)
          {
            if (!le.canContinueReading())
            {
              LocalizableMessage m = ERR_LDIF_BACKEND_ERROR_READING_LDIF
                  .get(stackTraceToSingleLineString(le));
              throw new DirectoryException(
                  DirectoryServer.getServerErrorResultCode(), m, le);
            }
            continue;
          }

          final DN dn = entry.getName();
          final EntryContainer ec = getEntryContainer(dn);
          if (ec == null)
          {
            final LocalizableMessage m = ERR_LDIF_SKIP.get(dn);
            logger.error(m);
            reader.rejectLastEntry(m);
            continue;
          }

          try
          {
            ec.addEntry(entry, null);
          }
          catch (DirectoryException e)
          {
            switch (e.getResultCode().asEnum())
            {
            case ENTRY_ALREADY_EXISTS:
              // TODO: support replace of existing entries.
              reader.rejectLastEntry(WARN_JEB_IMPORT_ENTRY_EXISTS.get());
              break;
            case NO_SUCH_OBJECT:
              reader.rejectLastEntry(ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(dn
                  .parent()));
              break;
            default:
              // Not sure why it failed.
              reader.rejectLastEntry(e.getMessageObject());
              break;
            }
          }
        }
        return new LDIFImportResult(reader.getEntriesRead(),
            reader.getEntriesRejected(), reader.getEntriesIgnored());
      }
      finally
      {
        close();
      }
    }
    catch (DirectoryException e)
    {
      logger.traceException(e);
      throw e;
    }
    catch (OpenDsException e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(),
          e.getMessageObject());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(),
          LocalizableMessage.raw(e.getMessage()));
    }
  }

  private void removeFiles() throws StorageRuntimeException
  {
    if (!backendDirectory.isDirectory())
    {
      LocalizableMessage message = ERR_JEB_DIRECTORY_INVALID
          .get(backendDirectory.getPath());
      throw new StorageRuntimeException(message.toString());
    }

    try
    {
      File[] jdbFiles = backendDirectory.listFiles();
      for (File f : jdbFiles)
      {
        f.delete();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_JEB_REMOVE_FAIL.get(e.getMessage());
      throw new StorageRuntimeException(message.toString(), e);
    }
  }

  void open() throws StorageRuntimeException, ConfigException
  {
    // Create the directory if it doesn't exist.
    if (!backendDirectory.exists())
    {
      if(!backendDirectory.mkdirs())
      {
        LocalizableMessage message =
          ERR_JEB_CREATE_FAIL.get(backendDirectory.getPath());
        throw new ConfigException(message);
      }
    }
    //Make sure the directory is valid.
    else if (!backendDirectory.isDirectory())
    {
      throw new ConfigException(ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath()));
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
    if(FilePermission.canSetPermissions())
    {
      try
      {
        if(!FilePermission.setPermissions(backendDirectory, backendPermission))
        {
          logger.warn(WARN_JEB_UNABLE_SET_PERMISSIONS, backendPermission, backendDirectory);
        }
      }
      catch(Exception e)
      {
        // Log an warning that the permissions were not set.
        logger.warn(WARN_JEB_SET_PERMISSIONS_FAILED, backendDirectory, e);
      }
    }

    storage = new PersistItStorage(backendDirectory, config);
    compressedSchema = new DefaultCompressedSchema();
    try
    {
      storage.initialize(null);
      storage.open();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          openAndRegisterEntryContainers(txn, config.getBaseDN());
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
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
   * @throws StorageRuntimeException If an error occurs while opening the entry
   *                           container.
   * @throws ConfigException If an configuration error occurs while opening
   *                         the entry container.
   */
  public EntryContainer openEntryContainer(DN baseDN, String name, WriteableStorage txn)
      throws StorageRuntimeException, ConfigException
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

    EntryContainer ec = new EntryContainer(baseDN, toSuffixName(databasePrefix),
                                           backend, config, storage, this);
    ec.open(txn);
    return ec;
  }

  /**
   * Transform a database prefix string to one usable by the DB.
   *
   * @param databasePrefix
   *          the database prefix
   * @return a new string when non letter or digit characters have been replaced
   *         with underscore
   */
  private TreeName toSuffixName(String databasePrefix)
  {
    return TreeName.of(storage.toSuffixName(databasePrefix));
  }

  /**
   * Registers the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @param entryContainer The entry container to register for the baseDN.
   * @throws InitializationException If an error occurs while opening the
   *                                 entry container.
   */
  public void registerEntryContainer(DN baseDN, EntryContainer entryContainer)
      throws InitializationException
  {
    EntryContainer ec1 = this.entryContainers.get(baseDN);

    // If an entry container for this baseDN is already open we don't allow
    // another to be opened.
    if (ec1 != null)
    {
      throw new InitializationException(ERR_JEB_ENTRY_CONTAINER_ALREADY_REGISTERED.get(
          ec1.getDatabasePrefix(), baseDN));
    }

    this.entryContainers.put(baseDN, entryContainer);
  }

  /**
   * Opens the entry containers for multiple base DNs.
   *
   * @param baseDNs The base DNs of the entry containers to open.
   * @throws StorageRuntimeException       If a database error occurs while opening
   *                                 the entry container.
   * @throws InitializationException If an initialization error occurs while
   *                                 opening the entry container.
   * @throws ConfigException         If a configuration error occurs while
   *                                 opening the entry container.
   */
  private void openAndRegisterEntryContainers(WriteableStorage txn, Set<DN> baseDNs)
      throws StorageRuntimeException, InitializationException, ConfigException
  {
    EntryID highestID = null;
    for(DN baseDN : baseDNs)
    {
      EntryContainer ec = openEntryContainer(baseDN, null, txn);
      EntryID id = ec.getHighestEntryID(txn);
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
  public EntryContainer unregisterEntryContainer(DN baseDN)
  {
    return entryContainers.remove(baseDN);
  }

  /**
   * Retrieves the compressed schema manager for this backend.
   *
   * @return  The compressed schema manager for this backend.
   */
  public CompressedSchema getCompressedSchema()
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
      String monitorName = backend.getBackendID() + " Database Storage";
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
        throw new NotImplementedException();
      }
      catch (StorageRuntimeException e)
      {
        logger.traceException(e);

        logger.error(ERR_JEB_CACHE_PRELOAD, backend.getBackendID(),
            stackTraceToSingleLineString(e.getCause() != null ? e.getCause() : e));
      }
    }
  }

  /**
   * Closes this root container.
   *
   * @throws StorageRuntimeException If an error occurs while attempting to close
   * the root container.
   */
  public void close() throws StorageRuntimeException
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

    // FIXME JNR call close() for a DB stored compressed schema
    // compressedSchema.close();
    config.removeLocalDBChangeListener(this);

    if (storage != null)
    {
      storage.close();
      storage = null;
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
   * @throws StorageRuntimeException If an error occurs while retrieving the entry
   *                           count.
   */
  public long getEntryCount() throws StorageRuntimeException
  {
    try
    {
      return storage.read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableStorage txn) throws Exception
        {
          long entryCount = 0;
          for (EntryContainer ec : entryContainers.values())
          {
            ec.sharedLock.lock();
            try
            {
              entryCount += ec.getEntryCount(txn);
            }
            finally
            {
              ec.sharedLock.unlock();
            }
          }
          return entryCount;
        }
      });
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(e);
    }
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
        unacceptableReasons.add(ERR_JEB_CREATE_FAIL.get(backendDirectory.getPath()));
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
      unacceptableReasons.add(ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath()));
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
      // FIXME JNR validate database specific configuration
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
            ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
            ccr.addMessage(ERR_JEB_CREATE_FAIL.get(backendDirectory.getPath()));
            return ccr;
          }
        }
        //Make sure the directory is valid.
        else if (!backendDirectory.isDirectory())
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath()));
          return ccr;
        }

        ccr.setAdminActionRequired(true);
        ccr.addMessage(NOTE_JEB_CONFIG_DB_DIR_REQUIRES_RESTART.get(this.config.getDBDirectory(), cfg.getDBDirectory()));
      }

      if (!cfg.getDBDirectoryPermissions().equalsIgnoreCase(config.getDBDirectoryPermissions())
          || !cfg.getDBDirectory().equals(this.config.getDBDirectory()))
      {
        FilePermission backendPermission;
        try
        {
          backendPermission =
              FilePermission.decodeUNIXMode(cfg.getDBDirectoryPermissions());
        }
        catch(Exception e)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_CONFIG_BACKEND_MODE_INVALID.get(config.dn()));
          return ccr;
        }

        // Make sure the mode will allow the server itself access to the database
        if(!backendPermission.isOwnerWritable() ||
            !backendPermission.isOwnerReadable() ||
            !backendPermission.isOwnerExecutable())
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_CONFIG_BACKEND_INSANE_MODE.get(cfg.getDBDirectoryPermissions()));
          return ccr;
        }

        // Get the backend database backendDirectory permissions and apply
        if(FilePermission.canSetPermissions())
        {
          File parentDirectory = getFileForPath(config.getDBDirectory());
          File backendDirectory = new File(parentDirectory, config.getBackendId());
          try
          {
            if (!FilePermission.setPermissions(backendDirectory, backendPermission))
            {
              logger.warn(WARN_JEB_UNABLE_SET_PERMISSIONS, backendPermission, backendDirectory);
            }
          }
          catch(Exception e)
          {
            // Log an warning that the permissions were not set.
            logger.warn(WARN_JEB_SET_PERMISSIONS_FAILED, backendDirectory, e);
          }
        }
      }

      getMonitorProvider().enableFilterUseStats(cfg.isIndexFilterAnalyzerEnabled());
      getMonitorProvider().setMaxEntries(cfg.getIndexFilterAnalyzerMaxFilters());

      this.config = cfg;
    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
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
    return storage.isValid();
  }
}
