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
package org.opends.server.backends.jeb;

import static com.sleepycat.je.EnvironmentConfig.*;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.backends.jeb.ConfigurableEnvironment.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Reject;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.Backupable;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IdentifiedException;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;
import org.opends.server.util.RuntimeInformation;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;

/**
 * This is an implementation of a Directory Server Backend which stores entries
 * locally in a Berkeley DB JE database.
 */
public class BackendImpl extends Backend<LocalDBBackendCfg>
    implements ConfigurationChangeListener<LocalDBBackendCfg>, AlertGenerator,
    DiskSpaceMonitorHandler, Backupable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration of this JE backend. */
  private LocalDBBackendCfg cfg;
  /** The root JE container to use for this backend. */
  private RootContainer rootContainer;
  /** A count of the total operation threads currently in the backend. */
  private final AtomicInteger threadTotalCount = new AtomicInteger(0);
  /** A count of the write operation threads currently in the backend. */
  private final AtomicInteger threadWriteCount = new AtomicInteger(0);
  /** The base DNs defined for this backend instance. */
  private DN[] baseDNs;

  private MonitorProvider<?> rootContainerMonitor;
  private DiskSpaceMonitor diskMonitor;
  private StorageStatus storageStatus = StorageStatus.working();

  /**
   * The controls supported by this backend.
   */
  private static final Set<String> supportedControls = new HashSet<String>(Arrays.asList(
      OID_SUBTREE_DELETE_CONTROL,
      OID_PAGED_RESULTS_CONTROL,
      OID_MANAGE_DSAIT_CONTROL,
      OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
      OID_VLV_REQUEST_CONTROL));

  /** Begin a Backend API method that reads the database. */
  private void readerBegin()
  {
    threadTotalCount.getAndIncrement();
  }

  /** End a Backend API method that reads the database. */
  private void readerEnd()
  {
    threadTotalCount.getAndDecrement();
  }

  /** Begin a Backend API method that writes the database. */
  private void writerBegin()
  {
    threadTotalCount.getAndIncrement();
    threadWriteCount.getAndIncrement();
  }

  /** End a Backend API method that writes the database. */
  private void writerEnd()
  {
    threadWriteCount.getAndDecrement();
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
  public void configureBackend(LocalDBBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    Reject.ifNull(cfg);

    this.cfg = cfg;
    baseDNs = this.cfg.getBaseDN().toArray(new DN[0]);
    diskMonitor = serverContext.getDiskSpaceMonitor();
  }

  /** {@inheritDoc} */
  @Override
  public void openBackend()
      throws ConfigException, InitializationException
  {
    if (mustOpenRootContainer())
    {
      rootContainer = initializeRootContainer(parseConfigEntry(cfg));
    }

    // Preload the database cache.
    rootContainer.preload(cfg.getPreloadTimeLimit());

    try
    {
      // Log an informational message about the number of entries.
      logger.info(NOTE_BACKEND_STARTED, cfg.getBackendId(), rootContainer.getEntryCount());
    }
    catch(DatabaseException databaseException)
    {
      logger.traceException(databaseException);
      throw new InitializationException(
          WARN_GET_ENTRY_COUNT_FAILED.get(databaseException.getMessage()), databaseException);
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
    diskMonitor.registerMonitoredDirectory(getBackendID(), getDirectory(), cfg.getDiskLowThreshold(),
        cfg.getDiskFullThreshold(), this);

    //Register as an AlertGenerator.
    DirectoryServer.registerAlertGenerator(this);
    // Register this backend as a change listener.
    cfg.addLocalDBChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public File getDirectory()
  {
    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    return new File(parentDirectory, cfg.getBackendId());
  }

  /** {@inheritDoc} */
  @Override
  public void closeBackend()
  {
    cfg.removeLocalDBChangeListener(this);

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
    diskMonitor.deregisterMonitoredDirectory(getDirectory(), this);
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
    catch (DatabaseException e)
    {
      logger.traceException(e);
      logger.error(ERR_DATABASE_EXCEPTION, e.getMessage());
    }

    DirectoryServer.deregisterAlertGenerator(this);

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.set(0);
    threadWriteCount.set(0);

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

      Set<LocalDBIndexCfgDefn.IndexType> indexTypes =
           ai.getConfiguration().getIndexType();
      switch (indexType)
      {
        case PRESENCE:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.PRESENCE);

        case EQUALITY:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.EQUALITY);

        case SUBSTRING:
        case SUBINITIAL:
        case SUBANY:
        case SUBFINAL:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING);

        case GREATER_OR_EQUAL:
        case LESS_OR_EQUAL:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.ORDERING);

        case APPROXIMATE:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.APPROXIMATE);

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
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException {
    checkNotNull(baseDN, "baseDN must not be null");
    EntryContainer ec = rootContainer.getEntryContainer(baseDN);
    if (ec == null || !ec.getBaseDN().equals(baseDN))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_SEARCH_NO_SUCH_OBJECT.get(baseDN));
    }
    return numSubordinates(baseDN, true);
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException {
    checkNotNull(parentDN, "parentDN must not be null");
    return numSubordinates(parentDN, false);
  }

  private long numSubordinates(DN entryDN, boolean subtree) throws DirectoryException
  {
    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    if(ec == null)
    {
      return -1;
    }

    readerBegin();
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
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    readerBegin();
    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    ec.sharedLock.lock();
    try
    {
      return ec.getEntry(entryDN);
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(addOperation);
    writerBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entry.getName());
    ec.sharedLock.lock();
    try
    {
      ec.addEntry(entry, addOperation);
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(deleteOperation);
    writerBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    ec.sharedLock.lock();
    try
    {
      ec.deleteEntry(entryDN, deleteOperation);
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException,
      CanceledOperationException
  {
    checkDiskSpace(modifyOperation);
    writerBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(newEntry.getName());
    ec.sharedLock.lock();

    try
    {
      ec.replaceEntry(oldEntry, newEntry, modifyOperation);
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(modifyDNOperation);
    writerBegin();

    checkRootContainerInitialized();
    EntryContainer currentContainer = rootContainer.getEntryContainer(currentDN);
    EntryContainer container = rootContainer.getEntryContainer(entry.getName());

    if (currentContainer != container)
    {
      // FIXME: No reason why we cannot implement a move between containers
      // since the containers share the same database environment.
      LocalizableMessage msg = WARN_FUNCTION_NOT_SUPPORTED.get();
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    currentContainer.sharedLock.lock();
    try
    {
      currentContainer.renameEntry(currentDN, entry, modifyDNOperation);
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      currentContainer.sharedLock.unlock();
      writerEnd();
    }
  }



  /** {@inheritDoc} */
  @Override
  public void search(SearchOperation searchOperation)
      throws DirectoryException, CanceledOperationException
  {
    readerBegin();

    checkRootContainerInitialized();
    EntryContainer ec = rootContainer.getEntryContainer(searchOperation.getBaseDN());
    ec.sharedLock.lock();

    try
    {
      ec.search(searchOperation);
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
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
      throw new DirectoryException(errorRC, ERR_EXPORT_IO_ERROR.get(ioe.getMessage()), ioe);
    }
    catch (DatabaseException de)
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
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    RuntimeInformation.logInfo();

    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = rootContainer == null;

    // If the rootContainer is open, the backend is initialized by something else.
    // We can't do import while the backend is online.
    final ResultCode errorRC = DirectoryServer.getServerErrorResultCode();
    if(!openRootContainer)
    {
      throw new DirectoryException(errorRC, ERR_IMPORT_BACKEND_ONLINE.get());
    }

    try
    {
      if (Importer.mustClearBackend(importConfig, cfg))
      {
        // We have the writer lock on the environment, now delete the
        // environment and re-open it. Only do this when we are
        // importing to all the base DNs in the backend or if the backend only
        // have one base DN.
        File parentDirectory = getFileForPath(cfg.getDBDirectory());
        File backendDirectory = new File(parentDirectory, cfg.getBackendId());
        // If the backend does not exist the import will create it.
        if (backendDirectory.exists())
        {
          EnvManager.removeFiles(backendDirectory.getPath());
        }
      }

      final EnvironmentConfig envConfig = getEnvConfigForImport();
      final Importer importer = new Importer(importConfig, cfg, envConfig, serverContext);
      rootContainer = initializeRootContainer(envConfig);
      return importer.processImport(rootContainer);
    }
    catch (ExecutionException execEx)
    {
      logger.traceException(execEx);
      if (execEx.getCause() instanceof DirectoryException)
      {
        throw ((DirectoryException) execEx.getCause());
      }
      throw new DirectoryException(errorRC, ERR_EXECUTION_ERROR.get(execEx.getMessage()));
    }
    catch (InterruptedException intEx)
    {
      logger.traceException(intEx);
      throw new DirectoryException(errorRC, ERR_INTERRUPTED_ERROR.get(intEx.getMessage()));
    }
    catch (JebException je)
    {
      logger.traceException(je);
      throw new DirectoryException(errorRC, je.getMessageObject());
    }
    catch (InitializationException ie)
    {
      logger.traceException(ie);
      throw new DirectoryException(errorRC, ie.getMessageObject());
    }
    catch (ConfigException ce)
    {
      logger.traceException(ce);
      throw new DirectoryException(errorRC, ce.getMessageObject());
    }
    finally
    {
      // leave the backend in the same state.
      try
      {
        if (rootContainer != null)
        {
          long startTime = System.currentTimeMillis();
          rootContainer.close();
          long finishTime = System.currentTimeMillis();
          long closeTime = (finishTime - startTime) / 1000;
          logger.info(NOTE_IMPORT_LDIF_ROOTCONTAINER_CLOSE, closeTime);
          rootContainer = null;
        }

        // Sync the environment to disk.
        logger.info(NOTE_IMPORT_CLOSING_DATABASE);
      }
      catch (DatabaseException de)
      {
        logger.traceException(de);
      }
    }
  }

  private EnvironmentConfig getEnvConfigForImport()
  {
    final EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    envConfig.setTransactional(false);
    envConfig.setDurability(Durability.COMMIT_NO_SYNC);
    envConfig.setLockTimeout(0, TimeUnit.SECONDS);
    envConfig.setTxnTimeout(0, TimeUnit.SECONDS);
    envConfig.setConfigParam(CLEANER_MIN_FILE_UTILIZATION,
        String.valueOf(cfg.getDBCleanerMinUtilization()));
    envConfig.setConfigParam(LOG_FILE_MAX,
        String.valueOf(cfg.getDBLogFileMax()));
    return envConfig;
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
    catch (DatabaseException e)
    {
      logger.traceException(e);
      throw createDirectoryException(e);
    }
    catch (JebException e)
    {
      logger.traceException(e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }


  /** {@inheritDoc} */
  @Override
  public void rebuildBackend(RebuildConfig rebuildConfig, ServerContext serverContext)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();

    /*
     * If the rootContainer is open, the backend is initialized by something
     * else. We can't do any rebuild of system indexes while others are using
     * this backend.
     */
    final ResultCode errorRC = DirectoryServer.getServerErrorResultCode();
    if(!openRootContainer && rebuildConfig.includesSystemIndex())
    {
      throw new DirectoryException(errorRC, ERR_REBUILD_BACKEND_ONLINE.get());
    }

    try
    {
      final EnvironmentConfig envConfig;
      if (openRootContainer)
      {
        envConfig = getEnvConfigForImport();
        rootContainer = initializeRootContainer(envConfig);
      }
      else
      {
        envConfig = parseConfigEntry(cfg);

      }
      final Importer importer = new Importer(rebuildConfig, cfg, envConfig, serverContext);
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
    catch (JebException e)
    {
      logger.traceException(e);
      throw new DirectoryException(errorRC, e.getMessageObject());
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
      catch (DatabaseException e)
      {
        logger.traceException(e);
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).createBackup(this, backupConfig);
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    new BackupManager(getBackendID()).removeBackup(backupDirectory, backupID);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).restoreBackup(this, restoreConfig);
  }

  /** {@inheritDoc} */
  @Override
  public ListIterator<Path> getFilesToBackup() throws DirectoryException
  {
    return new JELogFilesIterator(getDirectory(), cfg.getBackendId());
  }

  /**
   * Iterator on JE log files to backup.
   * <p>
   * The cleaner thread may delete some log files during the backup. The
   * iterator is automatically renewed if at least one file has been deleted.
   */
  static class JELogFilesIterator implements ListIterator<Path>
  {
    /** Underlying iterator on files. */
    private ListIterator<Path> iterator;

    /** Root directory where all files are located. */
    private final File rootDirectory;

    private final String backendID;

    /** Files to backup. Used to renew the iterator if necessary. */
    private List<Path> files;

    private String lastFileName = "";
    private long lastFileSize;

    JELogFilesIterator(File rootDirectory, String backendID) throws DirectoryException
    {
      this.rootDirectory = rootDirectory;
      this.backendID = backendID;
      setFiles(BackupManager.getFiles(rootDirectory, new JELogFileFilter(), backendID));
    }

    private void setFiles(List<Path> files) {
      this.files = files;
      Collections.sort(files);
      if (!files.isEmpty())
      {
        Path lastFile = files.get(files.size() - 1);
        lastFileName = lastFile.getFileName().toString();
        lastFileSize = lastFile.toFile().length();
      }
      iterator = files.listIterator();
  }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext()
    {
      boolean hasNext = iterator.hasNext();
      if (!hasNext && !files.isEmpty())
      {
        try
        {
          List<Path> allFiles = BackupManager.getFiles(rootDirectory, new JELogFileFilter(), backendID);
          List<Path> compare = new ArrayList<Path>(files);
          compare.removeAll(allFiles);
          if (!compare.isEmpty())
          {
            // at least one file was deleted, the iterator must be renewed based on last file previously available
            List<Path> newFiles =
                BackupManager.getFiles(rootDirectory, new JELogFileFilter(lastFileName, lastFileSize), backendID);
            logger.info(NOTE_JEB_BACKUP_CLEANER_ACTIVITY.get(newFiles.size()));
            if (!newFiles.isEmpty())
            {
              setFiles(newFiles);
              hasNext = iterator.hasNext();
            }
          }
        }
        catch (DirectoryException e)
        {
          logger.error(ERR_BACKEND_LIST_FILES_TO_BACKUP.get(backendID, stackTraceToSingleLineString(e)));
        }
      }
      return hasNext;
    }

    /** {@inheritDoc} */
    @Override
    public Path next()
    {
      if (hasNext()) {
        return iterator.next();
      }
      throw new NoSuchElementException();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasPrevious()
    {
      return iterator.hasPrevious();
    }

    /** {@inheritDoc} */
    @Override
    public Path previous()
    {
      return iterator.previous();
    }

    /** {@inheritDoc} */
    @Override
    public int nextIndex()
    {
      return iterator.nextIndex();
    }

    /** {@inheritDoc} */
    @Override
    public int previousIndex()
    {
      return iterator.previousIndex();
    }

    /** {@inheritDoc} */
    @Override
    public void remove()
    {
      throw new UnsupportedOperationException("remove() is not implemented");
    }

    /** {@inheritDoc} */
    @Override
    public void set(Path e)
    {
      throw new UnsupportedOperationException("set() is not implemented");
    }

    /** {@inheritDoc} */
    @Override
    public void add(Path e)
    {
      throw new UnsupportedOperationException("add() is not implemented");
    }

  }

  /**
   * This class implements a FilenameFilter to detect a JE log file, possibly with a constraint
   * on the file name and file size.
   */
  private static class JELogFileFilter implements FileFilter {

    private final String latestFilename;
    private final long latestFileSize;

    /**
     * Creates the filter for log files that are newer than provided file name
     * or equal to provided file name and of larger size.
     */
    JELogFileFilter(String latestFilename, long latestFileSize) {
      this.latestFilename = latestFilename;
      this.latestFileSize = latestFileSize;
    }

    /** Creates the filter for any JE log file. */
    JELogFileFilter() {
      this("", 0);
    }

    /** {@inheritDoc} */
    @Override
    public boolean accept(File file)
    {
      String name = file.getName();
      int cmp = name.compareTo(latestFilename);
      return name.endsWith(".jdb") && (cmp > 0 || (cmp == 0 && file.length() > latestFileSize));
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isDirectRestore()
  {
    // restore is done in an intermediate directory
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Path beforeRestore() throws DirectoryException
  {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void afterRestore(Path restoreDirectory, Path saveDirectory) throws DirectoryException
  {
    // intermediate directory content is moved to database directory
    File targetDirectory = getDirectory();
    recursiveDelete(targetDirectory);
    try
    {
      Files.move(restoreDirectory, targetDirectory.toPath());
    }
    catch(IOException e)
    {
      LocalizableMessage msg = ERR_CANNOT_RENAME_RESTORE_DIRECTORY.get(restoreDirectory, targetDirectory.getPath());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), msg);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(LocalDBBackendCfg config,
                                           List<LocalizableMessage> unacceptableReasons,
                                           ServerContext serverContext)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      LocalDBBackendCfg cfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure that the logging level value is acceptable.
    try {
      Level.parse(cfg.getDBLoggingLevel());
      return true;
    } catch (Exception e) {
      unacceptableReasons.add(ERR_JEB_INVALID_LOGGING_LEVEL.get(cfg.getDBLoggingLevel(), cfg.dn()));
      return false;
    }
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(LocalDBBackendCfg newCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      if(rootContainer != null)
      {
        SortedSet<DN> newBaseDNs = newCfg.getBaseDN();
        DN[] newBaseDNsArray = newBaseDNs.toArray(new DN[newBaseDNs.size()]);

        // Check for changes to the base DNs.
        removeDeletedBaseDNs(newBaseDNs);
        ConfigChangeResult failure = createNewBaseDNs(newBaseDNsArray, ccr);
        if (failure != null)
        {
          return failure;
        }

        baseDNs = newBaseDNsArray;
      }

      updateDiskMonitor(diskMonitor, newCfg);

      // Put the new configuration in place.
      this.cfg = newCfg;
    }
    catch (Exception e)
    {
      ccr.addMessage(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }
    return ccr;
  }

  private void updateDiskMonitor(DiskSpaceMonitor dm, LocalDBBackendCfg newCfg)
  {
    diskMonitor.registerMonitoredDirectory(getBackendID(), getDirectory(), newCfg.getDiskLowThreshold(),
        newCfg.getDiskFullThreshold(), this);
  }

  private void removeDeletedBaseDNs(SortedSet<DN> newBaseDNs) throws DirectoryException
  {
    for (DN baseDN : cfg.getBaseDN())
    {
      if (!newBaseDNs.contains(baseDN))
      {
        // The base DN was deleted.
        DirectoryServer.deregisterBaseDN(baseDN);
        EntryContainer ec = rootContainer.unregisterEntryContainer(baseDN);
        ec.close();
        ec.delete();
      }
    }
  }

  private ConfigChangeResult createNewBaseDNs(DN[] newBaseDNsArray, final ConfigChangeResult ccr)
  {
    for (DN baseDN : newBaseDNsArray)
    {
      if (!rootContainer.getBaseDNs().contains(baseDN))
      {
        try
        {
          // The base DN was added.
          EntryContainer ec = rootContainer.openEntryContainer(baseDN, null);
          rootContainer.registerEntryContainer(baseDN, ec);
          DirectoryServer.registerBaseDN(baseDN, this, false);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(baseDN, e));
          return ccr;
        }
      }
    }
    return null;
  }

  /**
   * Returns a handle to the JE root container currently used by this backend.
   * The rootContainer could be NULL if the backend is not initialized.
   *
   * @return The RootContainer object currently used by this backend.
   */
  public RootContainer getRootContainer()
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
  public RootContainer getReadOnlyRootContainer()
      throws ConfigException, InitializationException
  {
    EnvironmentConfig envConfig = parseConfigEntry(cfg);

    envConfig.setReadOnly(true);
    envConfig.setAllowCreate(false);
    envConfig.setTransactional(false);
    envConfig.setConfigParam(ENV_IS_LOCKING, "true");
    envConfig.setConfigParam(ENV_RUN_CHECKPOINTER, "true");

    return initializeRootContainer(envConfig);
  }

  /**
   * Clears all the entries from the backend.  This method is for test cases
   * that use the JE backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  JebException     If an error occurs while removing the data.
   */
  public void clearBackend()
      throws ConfigException, JebException
  {
    // Determine the backend database directory.
    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    File backendDirectory = new File(parentDirectory, cfg.getBackendId());
    EnvManager.removeFiles(backendDirectory.getPath());
  }

  /**
   * Creates a customized DirectoryException from the DatabaseException thrown
   * by JE backend.
   *
   * @param  e The DatabaseException to be converted.
   * @return  DirectoryException created from exception.
   */
  private DirectoryException createDirectoryException(DatabaseException e) {
    if (e instanceof EnvironmentFailureException && !rootContainer.isValid()) {
      LocalizableMessage message = NOTE_BACKEND_ENVIRONMENT_UNUSABLE.get(getBackendID());
      logger.info(message);
      DirectoryServer.sendAlertNotification(DirectoryServer.getInstance(),
              ALERT_TYPE_BACKEND_ENVIRONMENT_UNUSABLE, message);
    }

    String jeMessage = e.getMessage();
    if (jeMessage == null) {
      jeMessage = stackTraceToSingleLineString(e);
    }
    LocalizableMessage message = ERR_DATABASE_EXCEPTION.get(jeMessage);
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

  private RootContainer initializeRootContainer(EnvironmentConfig envConfig)
          throws ConfigException, InitializationException {
    // Open the database environment
    try {
      RootContainer rc = new RootContainer(this, cfg);
      rc.open(envConfig);
      return rc;
    }
    catch (DatabaseException e) {
      logger.traceException(e);
      LocalizableMessage message = ERR_OPEN_ENV_FAIL.get(e.getMessage());
      throw new InitializationException(message, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void diskLowThresholdReached(File directory, long thresholdInBytes) {
    storageStatus = StorageStatus.lockedDown(
        WARN_DISK_SPACE_LOW_THRESHOLD_CROSSED.get(directory.getFreeSpace(), directory.getAbsolutePath(),
        thresholdInBytes, getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void diskFullThresholdReached(File directory, long thresholdInBytes) {
    storageStatus = StorageStatus.unusable(
        WARN_DISK_SPACE_FULL_THRESHOLD_CROSSED.get(directory.getFreeSpace(), directory.getAbsolutePath(),
        thresholdInBytes, getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void diskSpaceRestored(File directory, long lowThresholdInBytes, long fullThresholdInBytes) {
    storageStatus = StorageStatus.working();
  }

  private void checkDiskSpace(Operation operation) throws DirectoryException
  {
    if(storageStatus.isUnusable() ||
        (storageStatus.isLockedDown()
            && operation != null
            && !operation.getClientConnection().hasPrivilege(
                Privilege.BYPASS_LOCKDOWN, operation)))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          WARN_OUT_OF_DISK_SPACE.get());
    }
  }
}
