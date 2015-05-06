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
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;

/**
 * Wrapper class for a backend "container". Root container holds all the entry
 * containers for each base DN. It also maintains all the openings and closings
 * of the entry containers.
 */
public class RootContainer implements ConfigurationChangeListener<PluggableBackendCfg>
{
  /** Logs the progress of the import. */
  private static final class ImportProgress implements Runnable
  {
    private final LDIFReader reader;
    private long previousCount;
    private long previousTime;

    public ImportProgress(LDIFReader reader)
    {
      this.reader = reader;
    }

    @Override
    public void run()
    {
      long latestCount = reader.getEntriesRead() + 0;
      long deltaCount = latestCount - previousCount;
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      if (deltaTime == 0)
      {
        return;
      }
      long entriesRead = reader.getEntriesRead();
      long entriesIgnored = reader.getEntriesIgnored();
      long entriesRejected = reader.getEntriesRejected();
      float rate = 1000f * deltaCount / deltaTime;
      logger.info(NOTE_IMPORT_PROGRESS_REPORT, entriesRead, entriesIgnored, entriesRejected, rate);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int IMPORT_PROGRESS_INTERVAL = 10000;

  /** The tree storage. */
  private Storage storage;

  /** The backend to which this entry root container belongs. */
  private final BackendImpl<?> backend;
  /** The backend configuration. */
  private final PluggableBackendCfg config;
  /** The monitor for this backend. */
  private BackendMonitor monitor;

  /** The base DNs contained in this root container. */
  private final ConcurrentHashMap<DN, EntryContainer> entryContainers = new ConcurrentHashMap<DN, EntryContainer>();

  /** The cached value of the next entry identifier to be assigned. */
  private AtomicLong nextid = new AtomicLong(1);

  /** The compressed schema manager for this backend. */
  private PersistentCompressedSchema compressedSchema;

  /**
   * Creates a new RootContainer object representing a storage.
   *
   * @param config
   *          The configuration of the backend.
   * @param backend
   *          A reference to the backend that is creating this root
   *          container.
   */
  RootContainer(BackendImpl<?> backend, PluggableBackendCfg config)
  {
    this.backend = backend;
    this.config = config;

    getMonitorProvider().enableFilterUseStats(config.isIndexFilterAnalyzerEnabled());
    getMonitorProvider().setMaxEntries(config.getIndexFilterAnalyzerMaxFilters());

    config.addPluggableChangeListener(this);
  }

  /**
   * Returns the underlying storage engine.
   *
   * @return the underlying storage engine
   */
  Storage getStorage()
  {
    return storage;
  }

  /**
   * Imports information from an LDIF file into this backend. This method should
   * only be called if {@code supportsLDIFImport} returns {@code true}. <p>Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param importConfig
   *          The configuration to use when performing the import.
   * @param serverContext The server context
   * @return information about the result of the import processing.
   * @throws DirectoryException
   *           If a problem occurs while performing the LDIF import.
   */
  LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext) throws DirectoryException
  {//TODO JNR may call importLDIFWithSuccessiveAdds(importConfig) depending on configured import strategy
    return importLDIFWithOnDiskMerge(importConfig, serverContext);
  }

  private LDIFImportResult importLDIFWithSuccessiveAdds(LDIFImportConfig importConfig) throws DirectoryException
  {
    try
    {
      ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
      try
      {
        final LDIFReader reader;
        try
        {
          reader = new LDIFReader(importConfig);
        }
        catch (Exception e)
        {
          LocalizableMessage m = ERR_LDIF_BACKEND_CANNOT_CREATE_LDIF_READER.get(stackTraceToSingleLineString(e));
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), m, e);
        }

        long importCount = 0;
        final long startTime = System.currentTimeMillis();
        timerService.scheduleAtFixedRate(new ImportProgress(reader),
            IMPORT_PROGRESS_INTERVAL, IMPORT_PROGRESS_INTERVAL, TimeUnit.MILLISECONDS);
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
              LocalizableMessage m = ERR_LDIF_BACKEND_ERROR_READING_LDIF.get(stackTraceToSingleLineString(le));
              throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), m, le);
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
            importCount++;
          }
          catch (DirectoryException e)
          {
            switch (e.getResultCode().asEnum())
            {
            case ENTRY_ALREADY_EXISTS:
              if (importConfig.replaceExistingEntries())
              {
                final Entry oldEntry = ec.getEntry(entry.getName());
                ec.replaceEntry(oldEntry, entry, null);
              }
              else
              {
                reader.rejectLastEntry(WARN_IMPORT_ENTRY_EXISTS.get());
              }
              break;
            case NO_SUCH_OBJECT:
              reader.rejectLastEntry(ERR_IMPORT_PARENT_NOT_FOUND.get(dn.parent()));
              break;
            default:
              // Not sure why it failed.
              reader.rejectLastEntry(e.getMessageObject());
              break;
            }
          }
        }
        final long finishTime = System.currentTimeMillis();

        waitForShutdown(timerService);

        final long importTime = finishTime - startTime;
        float rate = 0;
        if (importTime > 0)
        {
          rate = 1000f * reader.getEntriesRead() / importTime;
        }
        logger.info(NOTE_IMPORT_FINAL_STATUS, reader.getEntriesRead(), importCount, reader.getEntriesIgnored(),
            reader.getEntriesRejected(), 0, importTime / 1000, rate);
        return new LDIFImportResult(reader.getEntriesRead(), reader.getEntriesRejected(), reader.getEntriesIgnored());
      }
      finally
      {
        close();

        // if not already stopped, then stop it
        waitForShutdown(timerService);
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
      throw new DirectoryException(getServerErrorResultCode(), e.getMessageObject());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(), LocalizableMessage.raw(e.getMessage()));
    }
  }

  private void waitForShutdown(ScheduledThreadPoolExecutor timerService) throws InterruptedException
  {
    timerService.shutdown();
    timerService.awaitTermination(20, TimeUnit.SECONDS);
  }

  private LDIFImportResult importLDIFWithOnDiskMerge(final LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    try
    {
      return new Importer(this, importConfig, config, serverContext).processImport();
    }
    catch (DirectoryException e)
    {
      logger.traceException(e);
      throw e;
    }
    catch (OpenDsException e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(), e.getMessageObject(), e);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new DirectoryException(getServerErrorResultCode(),
          LocalizableMessage.raw(stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Opens the root container.
   *
   * @throws StorageRuntimeException
   *           If an error occurs when opening the storage.
   * @throws ConfigException
   *           If an configuration error occurs while opening the storage.
   */
  void open() throws StorageRuntimeException, ConfigException
  {
    try
    {
      storage = backend.getStorage();
      storage.open();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          compressedSchema = new PersistentCompressedSchema(storage, txn);
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
   * @param baseDN
   *          The base DN of the entry container to open.
   * @param txn
   *          The transaction
   * @return The opened entry container.
   * @throws StorageRuntimeException
   *           If an error occurs while opening the entry container.
   * @throws ConfigException
   *           If an configuration error occurs while opening the entry container.
   */
  EntryContainer openEntryContainer(DN baseDN, WriteableTransaction txn)
      throws StorageRuntimeException, ConfigException
  {
    EntryContainer ec = new EntryContainer(baseDN, backend, config, storage, this);
    ec.open(txn);
    return ec;
  }

  /**
   * Registers the entry container for a base DN.
   *
   * @param baseDN
   *          The base DN of the entry container to close.
   * @param entryContainer
   *          The entry container to register for the baseDN.
   * @throws InitializationException
   *           If an error occurs while opening the entry container.
   */
  void registerEntryContainer(DN baseDN, EntryContainer entryContainer) throws InitializationException
  {
    EntryContainer ec1 = this.entryContainers.get(baseDN);

    // If an entry container for this baseDN is already open we don't allow
    // another to be opened.
    if (ec1 != null)
    {
      throw new InitializationException(ERR_ENTRY_CONTAINER_ALREADY_REGISTERED.get(ec1.getTreePrefix(), baseDN));
    }

    this.entryContainers.put(baseDN, entryContainer);
  }

  /**
   * Opens the entry containers for multiple base DNs.
   *
   * @param baseDNs
   *          The base DNs of the entry containers to open.
   * @throws StorageRuntimeException
   *           If an error occurs while opening the entry container.
   * @throws InitializationException
   *           If an initialization error occurs while opening the entry
   *           container.
   * @throws ConfigException
   *           If a configuration error occurs while opening the entry
   *           container.
   */
  private void openAndRegisterEntryContainers(WriteableTransaction txn, Set<DN> baseDNs) throws StorageRuntimeException,
      InitializationException, ConfigException
  {
    EntryID highestID = null;
    for (DN baseDN : baseDNs)
    {
      EntryContainer ec = openEntryContainer(baseDN, txn);
      EntryID id = ec.getHighestEntryID(txn);
      registerEntryContainer(baseDN, ec);
      if (highestID == null || id.compareTo(highestID) > 0)
      {
        highestID = id;
      }
    }

    nextid = new AtomicLong(highestID.longValue() + 1);
  }

  /**
   * Unregisters the entry container for a base DN.
   *
   * @param baseDN
   *          The base DN of the entry container to close.
   * @return The entry container that was unregistered or NULL if a entry
   *         container for the base DN was not registered.
   */
  EntryContainer unregisterEntryContainer(DN baseDN)
  {
    return entryContainers.remove(baseDN);
  }

  /**
   * Retrieves the compressed schema manager for this backend.
   *
   * @return The compressed schema manager for this backend.
   */
  CompressedSchema getCompressedSchema()
  {
    return compressedSchema;
  }

  /**
   * Get the BackendMonitor object used by this root container.
   *
   * @return The BackendMonitor object.
   */
  BackendMonitor getMonitorProvider()
  {
    if (monitor == null)
    {
      String monitorName = backend.getBackendID() + " Storage";
      monitor = new BackendMonitor(monitorName, this);
    }

    return monitor;
  }

  /**
   * Preload the tree cache. There is no preload if the configured preload
   * time limit is zero.
   *
   * @param timeLimit
   *          The time limit for the preload process.
   */
  void preload(long timeLimit)
  {
    if (timeLimit > 0)
    {
      // Get a list of all the tree used by the backend.
      final List<Tree> trees = new ArrayList<>();
      for (EntryContainer ec : entryContainers.values())
      {
        ec.sharedLock.lock();
        try
        {
          trees.addAll(ec.listTrees());
        }
        finally
        {
          ec.sharedLock.unlock();
        }
      }

      // Sort the list in order of priority.
      Collections.sort(trees, new TreePreloadComparator());

      // Preload each tree until we reach the time limit or the cache
      // is filled.
      try
      {
        throw new UnsupportedOperationException("Not implemented exception");
      }
      catch (StorageRuntimeException e)
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
   * @throws StorageRuntimeException
   *           If an error occurs while attempting to close the root container.
   */
  void close() throws StorageRuntimeException
  {
    for (DN baseDN : entryContainers.keySet())
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
    config.removePluggableChangeListener(this);
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
  Set<DN> getBaseDNs()
  {
    return entryContainers.keySet();
  }

  /**
   * Return the entry container for a specific base DN.
   *
   * @param baseDN
   *          The base DN of the entry container to retrieve.
   * @return The entry container for the base DN.
   */
  EntryContainer getEntryContainer(DN baseDN)
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
   * @return The backend configuration used by this root container.
   */
  PluggableBackendCfg getConfiguration()
  {
    return config;
  }

  /**
   * Get the total number of entries in this root container.
   *
   * @return The number of entries in this root container
   * @throws StorageRuntimeException
   *           If an error occurs while retrieving the entry count.
   */
  long getEntryCount() throws StorageRuntimeException
  {
    try
    {
      return storage.read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableTransaction txn) throws Exception
        {
          long entryCount = 0;
          for (EntryContainer ec : entryContainers.values())
          {
            ec.sharedLock.lock();
            try
            {
              entryCount += ec.getNumberOfEntriesInBaseDN();
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
  EntryID getNextEntryID()
  {
    return new EntryID(nextid.getAndIncrement());
  }

  /**
   * Resets the next entry ID counter to zero. This should only be used after
   * clearing all trees.
   */
  public void resetNextEntryID()
  {
    nextid.set(1);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(PluggableBackendCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // Storage has also registered a change listener, delegate to it.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(PluggableBackendCfg configuration)
  {
    getMonitorProvider().enableFilterUseStats(configuration.isIndexFilterAnalyzerEnabled());
    getMonitorProvider().setMaxEntries(configuration.getIndexFilterAnalyzerMaxFilters());

    return new ConfigChangeResult();
  }

  /**
   * Checks the storage has enough resources for an operation.
   *
   * @param operation the current operation
   * @throws DirectoryException if resources are in short supply
   */
  public void checkForEnoughResources(Operation operation) throws DirectoryException
  {
    StorageStatus status = storage.getStorageStatus();
    if (status.isUnusable()
        || (status.isLockedDown() && hasBypassLockdownPrivileges(operation)))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, status.getReason());
    }
  }

  private boolean hasBypassLockdownPrivileges(Operation operation)
  {
    return operation != null
          // Read operations are always allowed in lock down mode
          && !(operation instanceof SearchOperation)
          && !operation.getClientConnection().hasPrivilege(
              Privilege.BYPASS_LOCKDOWN, operation);
  }
}
