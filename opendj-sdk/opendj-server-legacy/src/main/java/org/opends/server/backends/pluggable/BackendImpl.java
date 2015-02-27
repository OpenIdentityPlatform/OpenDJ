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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Reject;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.core.*;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.*;

/**
 * This is an implementation of a Directory Server Backend which stores entries
 * locally in a Berkeley DB JE database.
 */
public abstract class BackendImpl extends Backend<PluggableBackendCfg> implements
    ConfigurationChangeListener<PluggableBackendCfg>, AlertGenerator,
    DiskSpaceMonitorHandler
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration of this backend. */
  private PluggableBackendCfg cfg;
  /** The root JE container to use for this backend. */
  private RootContainer rootContainer;

  // FIXME: this is broken. Replace with read-write lock.
  /** A count of the total operation threads currently in the backend. */
  private final AtomicInteger threadTotalCount = new AtomicInteger(0);
  /** The base DNs defined for this backend instance. */
  private DN[] baseDNs;

  private MonitorProvider<?> rootContainerMonitor;
  /** Disk space monitoring if the storage supports it. */
  private DiskSpaceMonitor diskMonitor;

  /** The controls supported by this backend. */
  private static final Set<String> supportedControls = new HashSet<String>(Arrays.asList(
      OID_SUBTREE_DELETE_CONTROL,
      OID_PAGED_RESULTS_CONTROL,
      OID_MANAGE_DSAIT_CONTROL,
      OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
      OID_VLV_REQUEST_CONTROL));

  /** Begin a Backend API method that accesses the database. */
  private void accessBegin()
  {
    threadTotalCount.getAndIncrement();
  }

  /** End a Backend API method that accesses the database. */
  private void accessEnd()
  {
    threadTotalCount.getAndDecrement();
  }

  /**
   * Wait until there are no more threads accessing the database. It is assumed
   * that new threads have been prevented from entering the database at the time
   * this method is called.
   */
  private void waitUntilQuiescent()
  {
    while (threadTotalCount.get() > 0)
    {
      // Still have threads in the database so sleep a little
      try
      {
        Thread.sleep(500);
      }
      catch (InterruptedException e)
      {
        logger.traceException(e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void configureBackend(PluggableBackendCfg cfg) throws ConfigException
  {
    Reject.ifNull(cfg);

    this.cfg = cfg;
    baseDNs = this.cfg.getBaseDN().toArray(new DN[0]);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeBackend()
      throws ConfigException, InitializationException
  {
    if (mustOpenRootContainer())
    {
      rootContainer = initializeRootContainer();
    }

    // Preload the database cache.
    rootContainer.preload(cfg.getPreloadTimeLimit());

    try
    {
      // Log an informational message about the number of entries.
      logger.info(NOTE_JEB_BACKEND_STARTED, cfg.getBackendId(), getEntryCount());
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      LocalizableMessage message = WARN_JEB_GET_ENTRY_COUNT_FAILED.get(e.getMessage());
      throw new InitializationException(message, e);
    }

    for (DN dn : cfg.getBaseDN())
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        logger.traceException(e);
        throw new InitializationException(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(dn, e), e);
      }
    }

    // Register a monitor provider for the environment.
    rootContainerMonitor = rootContainer.getMonitorProvider();
    DirectoryServer.registerMonitorProvider(rootContainerMonitor);

    // Register as disk space monitor handler
    diskMonitor = newDiskMonitor(cfg);
    if (diskMonitor != null)
    {
      DirectoryServer.registerMonitorProvider(diskMonitor);
    }
    //Register as an AlertGenerator.
    DirectoryServer.registerAlertGenerator(this);
    // Register this backend as a change listener.
    cfg.addPluggableChangeListener(this);
  }

  /**
   * Let the storage create a new disk monitor if supported.
   *
   * @param cfg this storage current configuration
   * @return a new disk monitor if supported or null
   *
   * @throws ConfigException if configuration is incorrect
   * @throws InitializationException when disk monitor cannot be initialized
   */
  protected abstract DiskSpaceMonitor newDiskMonitor(PluggableBackendCfg cfg) throws
    ConfigException, InitializationException;

  /**
   * Updates the disk monitor when configuration changes.
   *
   * @param dm the disk monitor to update
   * @param newCfg the new configuration
   */
  protected abstract void updateDiskMonitor(DiskSpaceMonitor dm, PluggableBackendCfg newCfg);


  /** {@inheritDoc} */
  @Override
  public void finalizeBackend()
  {
    super.finalizeBackend();
    cfg.removePluggableChangeListener(this);

    // Deregister our base DNs.
    for (DN dn : rootContainer.getBaseDNs())
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    DirectoryServer.deregisterMonitorProvider(rootContainerMonitor);
    DirectoryServer.deregisterMonitorProvider(diskMonitor);

    // We presume the server will prevent more operations coming into this
    // backend, but there may be existing operations already in the
    // backend. We need to wait for them to finish.
    waitUntilQuiescent();

    // Close the database.
    try
    {
      rootContainer.close();
      rootContainer = null;
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      logger.error(ERR_JEB_DATABASE_EXCEPTION, e.getMessage());
    }

    DirectoryServer.deregisterAlertGenerator(this);

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.set(0);

    // Log an informational message.
    logger.info(NOTE_BACKEND_OFFLINE, cfg.getBackendId());
  }



  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    try
    {
      EntryContainer ec = rootContainer.getEntryContainer(baseDNs[0]);
      AttributeIndex ai = ec.getAttributeIndex(attributeType);
      if (ai == null)
      {
        return false;
      }

      Set<BackendIndexCfgDefn.IndexType> indexTypes =
           ai.getConfiguration().getIndexType();
      switch (indexType)
      {
        case PRESENCE:
          return indexTypes.contains(BackendIndexCfgDefn.IndexType.PRESENCE);

        case EQUALITY:
          return indexTypes.contains(BackendIndexCfgDefn.IndexType.EQUALITY);

        case SUBSTRING:
        case SUBINITIAL:
        case SUBANY:
        case SUBFINAL:
          return indexTypes.contains(BackendIndexCfgDefn.IndexType.SUBSTRING);

        case GREATER_OR_EQUAL:
        case LESS_OR_EQUAL:
          return indexTypes.contains(BackendIndexCfgDefn.IndexType.ORDERING);

        case APPROXIMATE:
          return indexTypes.contains(BackendIndexCfgDefn.IndexType.APPROXIMATE);

        default:
          return false;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    // it supports all the operations so far
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }

  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    if (rootContainer != null)
    {
      try
      {
        return rootContainer.getEntryCount();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
    return -1;
  }



  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    long ret = numSubordinates(entryDN, false);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    return ConditionResult.valueOf(ret != 0);
  }



  /** {@inheritDoc} */
  @Override
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    if(ec == null)
    {
      return -1;
    }

    accessBegin();
    ec.sharedLock.lock();
    try
    {
      long count = ec.getNumSubordinates(entryDN, subtree);
      if(count == Long.MAX_VALUE)
      {
        // The index entry limit has exceeded and there is no count maintained.
        return -1;
      }
      return count;
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    accessBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    ec.sharedLock.lock();
    Entry entry;
    try
    {
      entry = ec.getEntry(entryDN);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }

    return entry;
  }



  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(addOperation);
    accessBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entry.getName());
    ec.sharedLock.lock();
    try
    {
      ec.addEntry(entry, addOperation);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(deleteOperation);
    accessBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    ec.sharedLock.lock();
    try
    {
      ec.deleteEntry(entryDN, deleteOperation);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException,
      CanceledOperationException
  {
    checkDiskSpace(modifyOperation);
    accessBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(newEntry.getName());
    ec.sharedLock.lock();

    try
    {
      ec.replaceEntry(oldEntry, newEntry, modifyOperation);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(modifyDNOperation);
    accessBegin();

    checkRootContainerInitialized();
    EntryContainer currentContainer = rootContainer.getEntryContainer(currentDN);
    EntryContainer container = rootContainer.getEntryContainer(entry.getName());

    if (currentContainer != container)
    {
      // FIXME: No reason why we cannot implement a move between containers
      // since the containers share the same database environment.
      LocalizableMessage msg = WARN_JEB_FUNCTION_NOT_SUPPORTED.get();
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    currentContainer.sharedLock.lock();
    try
    {
      currentContainer.renameEntry(currentDN, entry, modifyDNOperation);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      currentContainer.sharedLock.unlock();
      accessEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void search(SearchOperation searchOperation)
      throws DirectoryException, CanceledOperationException
  {
    accessBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(searchOperation.getBaseDN());
    ec.sharedLock.lock();

    try
    {
      ec.search(searchOperation);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  private void checkRootContainerInitialized() throws DirectoryException
  {
    if (rootContainer == null)
    {
      LocalizableMessage msg = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), msg);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();
    final ResultCode errorRC = DirectoryServer.getServerErrorResultCode();
    try
    {
      if (openRootContainer)
      {
        rootContainer = getReadOnlyRootContainer();
      }

      ExportJob exportJob = new ExportJob(exportConfig);
      exportJob.exportLDIF(rootContainer);
    }
    catch (IOException ioe)
    {
      logger.traceException(ioe);
      throw new DirectoryException(errorRC, ERR_JEB_EXPORT_IO_ERROR.get(ioe.getMessage()), ioe);
    }
    catch (StorageRuntimeException de)
    {
      logger.traceException(de);
      throw createDirectoryException(de);
    }
    catch (ConfigException ce)
    {
      throw new DirectoryException(errorRC, ce.getMessageObject(), ce);
    }
    catch (IdentifiedException e)
    {
      if (e instanceof DirectoryException)
      {
        throw (DirectoryException) e;
      }
      logger.traceException(e);
      throw new DirectoryException(errorRC, e.getMessageObject(), e);
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  private boolean mustOpenRootContainer()
  {
    return rootContainer == null;
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    /*
     * If the rootContainer is open, the backend is initialized by something
     * else. We can't do import while the backend is online.
     */
    if (rootContainer != null)
    {
      throw new DirectoryException(getServerErrorResultCode(),
          ERR_JEB_IMPORT_BACKEND_ONLINE.get());
    }
    return new RootContainer(this, cfg).importLDIF(importConfig);
  }

  /** {@inheritDoc} */
  @Override
  public long verifyBackend(VerifyConfig verifyConfig)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    final boolean openRootContainer = mustOpenRootContainer();
    try
    {
      if (openRootContainer)
      {
        rootContainer = getReadOnlyRootContainer();
      }

      VerifyJob verifyJob = new VerifyJob(verifyConfig);
      return verifyJob.verifyBackend(rootContainer);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  /**
   * If a root container was opened in the calling method method as read only,
   * close it to leave the backend in the same state.
   */
  private void closeTemporaryRootContainer(boolean openRootContainer)
  {
    if (openRootContainer && rootContainer != null)
    {
      try
      {
        rootContainer.close();
        rootContainer = null;
      }
      catch (StorageRuntimeException e)
      {
        logger.traceException(e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void rebuildBackend(RebuildConfig rebuildConfig)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();

    /*
     * If the rootContainer is open, the backend is initialized by something else.
     * We can't do any rebuild of system indexes while others are using this backend.
     */
    final ResultCode errorRC = DirectoryServer.getServerErrorResultCode();
    if (!openRootContainer && rebuildConfig.includesSystemIndex())
    {
      throw new DirectoryException(errorRC, ERR_JEB_REBUILD_BACKEND_ONLINE.get());
    }

    try
    {
      if (openRootContainer)
      {
        rootContainer = initializeRootContainer();
      }
      final Importer importer = new Importer(rebuildConfig, (PersistitBackendCfg) cfg); // FIXME JNR remove cast
      importer.rebuildIndexes(rootContainer);
    }
    catch (ExecutionException execEx)
    {
      logger.traceException(execEx);
      throw new DirectoryException(errorRC, ERR_EXECUTION_ERROR.get(execEx.getMessage()));
    }
    catch (InterruptedException intEx)
    {
      logger.traceException(intEx);
      throw new DirectoryException(errorRC, ERR_INTERRUPTED_ERROR.get(intEx.getMessage()));
    }
    catch (ConfigException ce)
    {
      logger.traceException(ce);
      throw new DirectoryException(errorRC, ce.getMessageObject());
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      throw new DirectoryException(errorRC, LocalizableMessage.raw(e.getMessage()));
    }
    catch (InitializationException e)
    {
      logger.traceException(e);
      throw new InitializationException(e.getMessageObject());
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    BackupManager backupManager = new BackupManager(getBackendID());
    File backendDir = getBackupDirectory(cfg);
    Storage storage = newStorageInstance();
    backupConfig.setFilesToBackupFilter(storage.getFilesToBackupFilter());
    backupManager.createBackup(backendDir, backupConfig);
  }

  /**
   * Returns the storage corresponding to the backend class defined in the configuration.
   * @return the storage corresponding to the backend class defined in the configuration
   */
  protected abstract Storage newStorageInstance();

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    BackupManager backupManager = new BackupManager(getBackendID());
    backupManager.removeBackup(backupDirectory, backupID);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig)
      throws DirectoryException
  {
    BackupManager backupManager = new BackupManager(getBackendID());
    File backendDir = getBackupDirectory(cfg);
    backupManager.restoreBackup(backendDir, restoreConfig);
  }

  /**
   * Returns the backup directory.
   *
   * @param cfg the configuration for this backend
   * @return the backup directory
   */
  protected abstract File getBackupDirectory(PluggableBackendCfg cfg);

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PluggableBackendCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(PluggableBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(final PluggableBackendCfg newCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      if(rootContainer != null)
      {
        rootContainer.getStorage().write(new WriteOperation()
        {
          @Override
          public void run(WriteableStorage txn) throws Exception
          {
            SortedSet<DN> newBaseDNs = newCfg.getBaseDN();
            DN[] newBaseDNsArray = newBaseDNs.toArray(new DN[newBaseDNs.size()]);

            // Check for changes to the base DNs.
            removeDeletedBaseDNs(newBaseDNs, txn);
            if (!createNewBaseDNs(newBaseDNsArray, ccr, txn))
            {
              return;
            }

            baseDNs = newBaseDNsArray;

            if (diskMonitor != null)
            {
              updateDiskMonitor(diskMonitor, newCfg);
            }

            // Put the new configuration in place.
            cfg = newCfg;
          }
        });
      }
    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  private void removeDeletedBaseDNs(SortedSet<DN> newBaseDNs, WriteableStorage txn) throws DirectoryException
  {
    for (DN baseDN : cfg.getBaseDN())
    {
      if (!newBaseDNs.contains(baseDN))
      {
        // The base DN was deleted.
        DirectoryServer.deregisterBaseDN(baseDN);
        EntryContainer ec = rootContainer.unregisterEntryContainer(baseDN);
        ec.close();
        ec.delete(txn);
      }
    }
  }

  private boolean createNewBaseDNs(DN[] newBaseDNsArray, ConfigChangeResult ccr, WriteableStorage txn)
  {
    for (DN baseDN : newBaseDNsArray)
    {
      if (!rootContainer.getBaseDNs().contains(baseDN))
      {
        try
        {
          // The base DN was added.
          EntryContainer ec = rootContainer.openEntryContainer(baseDN, txn);
          rootContainer.registerEntryContainer(baseDN, ec);
          DirectoryServer.registerBaseDN(baseDN, this, false);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(baseDN, e));
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns a handle to the JE root container currently used by this backend.
   * The rootContainer could be NULL if the backend is not initialized.
   *
   * @return The RootContainer object currently used by this backend.
   */
  public final RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Returns a new read-only handle to the JE root container for this backend.
   * The caller is responsible for closing the root container after use.
   *
   * @return The read-only RootContainer object for this backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises during
   *                           initialization.
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  private final RootContainer getReadOnlyRootContainer()
      throws ConfigException, InitializationException
  {
    return initializeRootContainer();
  }

  /**
   * Creates a customized DirectoryException from the StorageRuntimeException
   * thrown by JE backend.
   *
   * @param e
   *          The StorageRuntimeException to be converted.
   * @return DirectoryException created from exception.
   */
  private DirectoryException createDirectoryException(StorageRuntimeException e)
  {
    if (true) // FIXME JNR
    {
      Throwable cause = e.getCause();
      if (cause instanceof OpenDsException)
      {
        return new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), (OpenDsException) cause);
      }
      else
      {
        return new DirectoryException(
            DirectoryServer.getServerErrorResultCode(),
            LocalizableMessage.raw(e.getMessage()), e);
      }
    }
    if (/*e instanceof EnvironmentFailureException && */ !rootContainer.isValid()) {
      LocalizableMessage message = NOTE_BACKEND_ENVIRONMENT_UNUSABLE.get(getBackendID());
      logger.info(message);
      DirectoryServer.sendAlertNotification(DirectoryServer.getInstance(),
              ALERT_TYPE_BACKEND_ENVIRONMENT_UNUSABLE, message);
    }

    String jeMessage = e.getMessage();
    if (jeMessage == null) {
      jeMessage = stackTraceToSingleLineString(e);
    }
    LocalizableMessage message = ERR_JEB_DATABASE_EXCEPTION.get(jeMessage);
    return new DirectoryException(
        DirectoryServer.getServerErrorResultCode(), message, e);
  }

  /** {@inheritDoc} */
  @Override
  public String getClassName() {
    return BackendImpl.class.getName();
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<String, String>();

    alerts.put(ALERT_TYPE_BACKEND_ENVIRONMENT_UNUSABLE,
            ALERT_DESCRIPTION_BACKEND_ENVIRONMENT_UNUSABLE);
    alerts.put(ALERT_TYPE_DISK_SPACE_LOW,
            ALERT_DESCRIPTION_DISK_SPACE_LOW);
    alerts.put(ALERT_TYPE_DISK_FULL,
            ALERT_DESCRIPTION_DISK_FULL);
    return alerts;
  }

  /** {@inheritDoc} */
  @Override
  public DN getComponentEntryDN() {
    return cfg.dn();
  }

  private RootContainer initializeRootContainer()
          throws ConfigException, InitializationException {
    // Open the database environment
    try {
      RootContainer rc = new RootContainer(this, cfg);
      rc.open();
      return rc;
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_JEB_OPEN_ENV_FAIL.get(e.getMessage());
      throw new InitializationException(message, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void preloadEntryCache() throws
          UnsupportedOperationException {
    EntryCachePreloader preloader = new EntryCachePreloader(this);
    preloader.preload();
  }

  /** {@inheritDoc} */
  @Override
  public void diskLowThresholdReached(DiskSpaceMonitor monitor) {
    LocalizableMessage msg = ERR_JEB_DISK_LOW_THRESHOLD_REACHED.get(
        monitor.getDirectory().getPath(), cfg.getBackendId(), monitor.getFreeSpace(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
    DirectoryServer.sendAlertNotification(this, ALERT_TYPE_DISK_SPACE_LOW, msg);
  }

  /** {@inheritDoc} */
  @Override
  public void diskFullThresholdReached(DiskSpaceMonitor monitor) {
    LocalizableMessage msg = ERR_JEB_DISK_FULL_THRESHOLD_REACHED.get(
        monitor.getDirectory().getPath(), cfg.getBackendId(), monitor.getFreeSpace(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
    DirectoryServer.sendAlertNotification(this, ALERT_TYPE_DISK_FULL, msg);
  }

  /** {@inheritDoc} */
  @Override
  public void diskSpaceRestored(DiskSpaceMonitor monitor) {
    logger.error(NOTE_JEB_DISK_SPACE_RESTORED, monitor.getFreeSpace(),
        monitor.getDirectory().getPath(), cfg.getBackendId(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
  }

  private void checkDiskSpace(Operation operation) throws DirectoryException
  {
    if(diskMonitor.isFullThresholdReached() ||
        (diskMonitor.isLowThresholdReached()
            && operation != null
            && !operation.getClientConnection().hasPrivilege(
                Privilege.BYPASS_LOCKDOWN, operation)))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          WARN_JEB_OUT_OF_DISK_SPACE.get());
    }
  }
}
