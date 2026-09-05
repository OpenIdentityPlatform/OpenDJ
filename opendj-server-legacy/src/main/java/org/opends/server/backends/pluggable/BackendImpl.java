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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2025-2026 3A Systems, LLC
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Reject.*;
import static org.forgerock.util.Utils.closeSilently;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageInUseException;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.Operation;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.LDIFException;
import org.opends.server.util.RuntimeInformation;

/**
 * This is an implementation of a Directory Server Backend which stores entries locally
 * in a pluggable storage.
 *
 * @param <C>
 *          the type of the BackendCfg for the current backend
 */
public abstract class BackendImpl<C extends PluggableBackendCfg> extends LocalBackend<C> implements
    ConfigurationChangeListener<PluggableBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration of this backend. */
  private PluggableBackendCfg cfg;
  /** The root container to use for this backend. */
  private RootContainer rootContainer;

  /**
   * A count of the total operation threads currently in the backend. Bumped
   * twice per operation by all worker threads, so it uses a striped counter
   * to avoid contending on a single cache line; it is only read when waiting
   * for the backend to become quiescent, which is why it is not a LongAdder —
   * see {@link StripedCounter}.
   */
  private final StripedCounter threadTotalCount = new StripedCounter();
  /** The base DNs defined for this backend instance. */
  private Set<DN> baseDNs;

  private MonitorProvider<?> rootContainerMonitor;

  /** The underlying storage engine. */
  private Storage storage;

  /** The controls supported by this backend. */
  private static final Set<String> supportedControls = CollectionUtils.newHashSet(
      OID_SUBTREE_DELETE_CONTROL,
      OID_PAGED_RESULTS_CONTROL,
      OID_MANAGE_DSAIT_CONTROL,
      OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
      OID_VLV_REQUEST_CONTROL);

  private ServerContext serverContext;

  /**
   * Begin a Backend API method that accesses the {@link EntryContainer} for <code>entryDN</code>
   * and returns it.
   * @param operation requesting the storage
   * @param entryDN the target DN for the operation
   * @return <code>EntryContainer</code> where <code>entryDN</code> resides
   */
  private EntryContainer accessBegin(Operation operation, DN entryDN) throws DirectoryException
  {
    return accessBegin(operation, entryDN, ResultCode.UNDEFINED);
  }

  /**
   * Begin a Backend API method that accesses the {@link EntryContainer} for <code>entryDN</code>
   * and returns it.
   * @param operation requesting the storage
   * @param entryDN the target DN for the operation
   * @param noEntryContainerResultCode the result code to report when this backend holds no
   *                                   entry container for <code>entryDN</code>
   * @return <code>EntryContainer</code> where <code>entryDN</code> resides
   */
  private EntryContainer accessBegin(Operation operation, DN entryDN, ResultCode noEntryContainerResultCode)
      throws DirectoryException
  {
    checkRootContainerInitialized();
    rootContainer.checkForEnoughResources(operation);
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    if (ec == null)
    {
      throw new DirectoryException(
          noEntryContainerResultCode, ERR_BACKEND_ENTRY_DOESNT_EXIST.get(entryDN, getBackendID()));
    }
    threadTotalCount.increment();
    return ec;
  }

  /** End a Backend API method that accesses the EntryContainer. */
  private void accessEnd()
  {
    threadTotalCount.decrement();
  }

  /**
   * Wait until there are no more threads accessing the storage. It is assumed
   * that new threads have been prevented from entering the storage at the time
   * this method is called.
   */
  private void waitUntilQuiescent()
  {
    while (threadTotalCount.sum() > 0)
    {
      // Still have threads accessing the storage so sleep a little
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

  @Override
  public void configureBackend(C cfg, ServerContext serverContext) throws ConfigException
  {
    Reject.ifNull(cfg, "cfg must not be null");

    this.cfg = cfg;
    this.serverContext = serverContext;
    baseDNs = new HashSet<>(cfg.getBaseDN());
    storage = new TracedStorage(configureStorage(cfg, serverContext), cfg.getBackendId());
  }

  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    if (mustOpenRootContainer())
    {
      rootContainer = newRootContainer(AccessMode.READ_WRITE);
    }

    // Preload the tree cache.
    rootContainer.preload(cfg.getPreloadTimeLimit());

    try
    {
      // Log an informational message about the number of entries.
      logger.info(NOTE_BACKEND_STARTED, cfg.getBackendId(), getEntryCount());
    }
    catch (StorageRuntimeException e)
    {
      LocalizableMessage message = WARN_GET_ENTRY_COUNT_FAILED.get(e.getMessage());
      throw new InitializationException(message, e);
    }

    for (DN dn : cfg.getBaseDN())
    {
      try
      {
        serverContext.getBackendConfigManager().registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(dn, e), e);
      }
    }

    // Register a monitor provider for the environment.
    rootContainerMonitor = rootContainer.getMonitorProvider();
    DirectoryServer.registerMonitorProvider(rootContainerMonitor);

    // Register this backend as a change listener.
    cfg.addPluggableChangeListener(this);
  }

  @Override
  public void closeBackend()
  {
    cfg.removePluggableChangeListener(this);

    // Deregister our base DNs.
    for (DN dn : rootContainer.getBaseDNs())
    {
      try
      {
        serverContext.getBackendConfigManager().deregisterBaseDN(dn);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    DirectoryServer.deregisterMonitorProvider(rootContainerMonitor);

    // We presume the server will prevent more operations coming into this
    // backend, but there may be existing operations already in the
    // backend. We need to wait for them to finish.
    waitUntilQuiescent();

    // Close RootContainer and Storage.
    try
    {
      rootContainer.close();
      rootContainer = null;
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      logger.error(ERR_DATABASE_EXCEPTION, e.getMessage());
    }

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.reset();

    // Log an informational message.
    logger.info(NOTE_BACKEND_OFFLINE, cfg.getBackendId());
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    try
    {
      EntryContainer ec = rootContainer.getEntryContainer(baseDNs.iterator().next());
      AttributeIndex ai = ec.getAttributeIndex(attributeType);
      return ai != null ? ai.isIndexed(indexType) : false;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return false;
    }
  }

  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    switch (backendOperation)
    {
    case BACKUP:
    case RESTORE:
      // Responsibility of the underlying storage.
      return storage.supportsBackupAndRestore();
    default: // INDEXING, LDIF_EXPORT, LDIF_IMPORT
      // Responsibility of this pluggable backend.
      return true;
    }
  }

  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

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

  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    EntryContainer container;
    try {
      container = accessBegin(null, entryDN);
    }
    catch (DirectoryException de)
    {
      if (de.getResultCode() == ResultCode.UNDEFINED)
      {
        return ConditionResult.UNDEFINED;
      }
      throw de;
    }

    container.beginSharedAccess();
    try
    {
      return ConditionResult.valueOf(container.hasSubordinates(entryDN));
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      container.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");

    final EntryContainer ec = accessBegin(null, baseDN);
    ec.beginSharedAccess();
    try
    {
      return ec.getNumberOfEntriesInBaseDN();
    }
    catch (Exception e)
    {
      throw new DirectoryException(
          serverContext.getCoreConfigManager().getServerErrorResultCode(), LocalizableMessage.raw(e.getMessage()), e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    EntryContainer ec;

    /*
     * Only place where we need special handling. Should return -1 instead of an
     * error if the EntryContainer is null...
     */
    try {
      ec = accessBegin(null, parentDN);
    }
    catch (DirectoryException de)
    {
      if (de.getResultCode() == ResultCode.UNDEFINED)
      {
        return -1;
      }
      throw de;
    }

    ec.beginSharedAccess();
    try
    {
      return ec.getNumberOfChildren(parentDN);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public boolean entryExists(final DN entryDN) throws DirectoryException
  {
    EntryContainer ec = accessBegin(null, entryDN);
    ec.beginSharedAccess();
    try
    {
      return ec.entryExists(entryDN);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    EntryContainer ec = accessBegin(null, entryDN);
    ec.beginSharedAccess();
    try
    {
      return ec.getEntry(entryDN);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation) throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(addOperation, entry.getName());

    ec.beginSharedAccess();
    try
    {
      ec.addEntry(entry, addOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(deleteOperation, entryDN);

    ec.beginSharedAccess();
    try
    {
      ec.deleteEntry(entryDN, deleteOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry, ModifyOperation modifyOperation)
      throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(modifyOperation, newEntry.getName());

    ec.beginSharedAccess();

    try
    {
      ec.replaceEntry(oldEntry, newEntry, modifyOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry, ModifyDNOperation modifyDNOperation)
      throws DirectoryException, CanceledOperationException
  {
    EntryContainer currentContainer = accessBegin(modifyDNOperation, currentDN);
    EntryContainer container = rootContainer.getEntryContainer(entry.getName());

    if (currentContainer != container)
    {
      accessEnd();
      // FIXME: No reason why we cannot implement a move between containers
      // since the containers share the same "container"
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, WARN_FUNCTION_NOT_SUPPORTED.get());
    }

    currentContainer.beginSharedAccess();
    try
    {
      currentContainer.renameEntry(currentDN, entry, modifyDNOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      currentContainer.endSharedAccess();
      accessEnd();
    }
  }

  @Override
  public void search(SearchOperation searchOperation) throws DirectoryException, CanceledOperationException
  {
    // a base DN held by no entry container of this backend does not exist as far as a client
    // is concerned: report it as such instead of the UNDEFINED result code used internally.
    EntryContainer ec = accessBegin(searchOperation, searchOperation.getBaseDN(), ResultCode.NO_SUCH_OBJECT);

    ec.beginSharedAccess();

    try
    {
      ec.search(searchOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.endSharedAccess();
      accessEnd();
    }
  }

  private void checkRootContainerInitialized() throws DirectoryException
  {
    if (rootContainer == null)
    {
      LocalizableMessage msg = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(serverContext.getCoreConfigManager().getServerErrorResultCode(), msg);
    }
  }

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();
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
      throw new DirectoryException(serverContext.getCoreConfigManager()
          .getServerErrorResultCode(), ERR_EXPORT_IO_ERROR.get(ioe.getMessage()), ioe);
    }
    catch (StorageRuntimeException de)
    {
      throw createDirectoryException(de);
    }
    catch (ConfigException | InitializationException | LDIFException e)
    {
      throw new DirectoryException(
          serverContext.getCoreConfigManager().getServerErrorResultCode(), e.getMessageObject(), e);
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

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    RuntimeInformation.logInfo();

    // If the rootContainer is open, the backend is initialized by something else.
    // We can't do import while the backend is online.
    if (rootContainer != null)
    {
      throw new DirectoryException(
          serverContext.getCoreConfigManager().getServerErrorResultCode(), ERR_IMPORT_BACKEND_ONLINE.get());
    }

    try
    {
      try
      {
        if (importConfig.clearBackend())
        {
          // clear all files before opening the root container
          storage.removeStorageFiles();
        }
      }
      catch (Exception e)
      {
        throw new DirectoryException(
            serverContext.getCoreConfigManager().getServerErrorResultCode(), ERR_REMOVE_FAIL.get(e.getMessage()), e);
      }
      rootContainer = newRootContainer(AccessMode.READ_WRITE);
      rootContainer.getStorage().close();
      return getImportStrategy(rootContainer).importLDIF(importConfig);
    }
    catch (Exception e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
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

        logger.info(NOTE_IMPORT_CLOSING_DATABASE);
      }
      catch (StorageRuntimeException de)
      {
        logger.traceException(de);
      }
    }
  }

  private ImportStrategy getImportStrategy(final RootContainer rootContainer)
  {
    return new OnDiskMergeImporter.StrategyImpl(serverContext, rootContainer, cfg);
  }

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
      return new VerifyJob(rootContainer, verifyConfig).verifyBackend();
    }
    catch (StorageRuntimeException e)
    {
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

  @Override
  public void rebuildBackend(RebuildConfig rebuildConfig, ServerContext serverContext)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();

    /*
     * If the rootContainer is open, the backend is initialized by something else.
     * We can't do any rebuild of system indexes while others are using this backend.
     */
    if (!openRootContainer && rebuildConfig.includesSystemIndex())
    {
      throw new DirectoryException(
          serverContext.getCoreConfigManager().getServerErrorResultCode(), ERR_REBUILD_BACKEND_ONLINE.get());
    }

    try
    {
      if (openRootContainer)
      {
        rootContainer = newRootContainer(AccessMode.READ_WRITE);
      }
      getImportStrategy(rootContainer).rebuildIndex(rebuildConfig);
    }
    catch (InitializationException | ConfigException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    storage.createBackup(backupConfig);
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    storage.removeBackup(backupDirectory, backupID);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    storage.restoreBackup(restoreConfig);
  }

  /**
   * Creates the storage engine which will be used by this pluggable backend. Implementations should
   * create and configure a new storage engine but not open it.
   *
   * @param cfg
   *          the configuration object
   * @param serverContext
   *          this Directory Server intsance's server context
   * @return The storage engine to be used by this pluggable backend.
   * @throws ConfigException
   *           If there is an error in the configuration.
   */
  protected abstract Storage configureStorage(C cfg, ServerContext serverContext) throws ConfigException;

  @Override
  public boolean isConfigurationAcceptable(C config, List<LocalizableMessage> unacceptableReasons,
      ServerContext serverContext)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(PluggableBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * {@link Storage#write(WriteOperation)} replays its operation after a transaction conflict, so
   * the operation below is confined to work a rollback undoes: the trees are deleted and opened
   * there, while the registries, which no rollback reaches, are updated once the write has
   * committed. Getting this the wrong way round leaves the change half applied, and its replay
   * reports the missing half rather than the conflict that caused it.
   * <p>
   * What makes the operation replayable is that the base DNs to remove and to add are worked out
   * once, ahead of the write, so that no attempt can see different work to do than the attempt it
   * is replacing.
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(final PluggableBackendCfg newCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    // Read once: importLDIF, rebuildBackend, exportLDIF and verifyBackend all assign this field
    // and null it out again, and this method now goes on using it past the commit.
    final RootContainer rc = rootContainer;
    if (rc == null)
    {
      return ccr;
    }

    final SortedSet<DN> newBaseDNs = newCfg.getBaseDN();
    // Ask the root container what this backend holds rather than the configuration it was last
    // given: a base DN which an earlier, failed change left behind is work to do, and a
    // configuration which was never applied is not. RootContainer.getBaseDNs() is a live view of
    // the registered containers, so take a copy of it before anything registers one.
    final Set<DN> currentBaseDNs = new HashSet<>(rc.getBaseDNs());
    final List<EntryContainer> deleted = new ArrayList<>();
    for (DN baseDN : currentBaseDNs)
    {
      if (!newBaseDNs.contains(baseDN))
      {
        deleted.add(rc.getEntryContainer(baseDN));
      }
    }
    final List<DN> added = new ArrayList<>();
    for (DN baseDN : newBaseDNs)
    {
      if (!currentBaseDNs.contains(baseDN))
      {
        added.add(baseDN);
      }
    }
    if (deleted.isEmpty() && added.isEmpty())
    {
      // The common case - index-entry-limit, db-cache-percent, preload-time-limit and the rest,
      // which the entry containers apply through their own listeners. There is no storage work to
      // do, so no transaction is opened to commit nothing.
      baseDNs = new HashSet<>(newBaseDNs);
      cfg = newCfg;
      return ccr;
    }
    // Opened by the write operation, registered only once it has committed.
    final List<EntryContainer> created = new ArrayList<>();

    // The trees of a removed base DN are now deleted while it is still registered, so hold its
    // entry container exclusively for as long as the write runs, retries included, as
    // RootContainer.close(), EntryContainer's index delete listener and AttributeIndex all do.
    // That keeps out the operations which arrive during that window; an operation which had taken
    // hold of the container before the lock still ends up in a closed one once it is released, as
    // it did before this ordering.
    final List<EntryContainer> locked = new ArrayList<>(deleted.size());
    try
    {
      for (EntryContainer ec : deleted)
      {
        ec.lock();
        locked.add(ec);
      }

      try
      {
        rc.getStorage().write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            // Give up what a previous, rolled back attempt had opened: its trees are gone, and its
            // entry containers still hold the configuration listeners they registered.
            closeSilently(created);
            created.clear();

            // Opening the added base DNs comes first, so that the failure this operation is most
            // likely to meet is met while everything is still there to roll back to. Once a tree
            // has been deleted, a storage engine which does not undo that has nothing to give
            // back.
            for (DN baseDN : added)
            {
              created.add(rc.openEntryContainer(baseDN, txn, AccessMode.READ_WRITE));
            }
            for (EntryContainer ec : deleted)
            {
              ec.delete(txn);
            }
          }
        });
      }
      catch (Exception e)
      {
        logger.traceException(e);

        closeSilently(created);
        ccr.setResultCode(serverContext.getCoreConfigManager().getServerErrorResultCode());
        // On a storage engine whose deleteTree the rollback undoes with the rest - persistit, je,
        // and the jdbc backend on postgresql and sql server - nothing at all has been applied and
        // neither registry is touched below. The failure alone never says which base DNs the
        // change was about, so name them.
        ccr.addMessage(ERR_BACKEND_CANNOT_CHANGE_BASEDNS.get(
            getBackendID(), baseDNsOf(deleted), added, stackTraceToSingleLineString(e)));
        deregisterBaseDNsWhoseTreesAreGone(rc, deleted, ccr);
        return ccr;
      }

      // The change is durable from here on, so every base DN is seen through even if one fails.
      deregisterDeletedBaseDNs(rc, deleted, ccr);
      registerNewBaseDNs(rc, created, ccr);

      // Put the new configuration in place.
      cfg = newCfg;
    }
    finally
    {
      // What the root container ended up holding, not what was asked for: a base DN whose
      // registration failed is not one this backend serves, and getBaseDNs() is what the monitors,
      // isIndexed() and closeBackend() are answered from. Taken on the way out of every path, the
      // failed ones included, so that the two never disagree.
      baseDNs = new HashSet<>(rc.getBaseDNs());
      for (EntryContainer ec : locked)
      {
        ec.unlock();
      }
    }
    return ccr;
  }

  /**
   * Gives up the base DNs whose trees the failed write took with it, which is what a storage engine
   * that commits its DDL of its own accord (mysql, oracle) or has no transaction to roll back
   * (cassandra) leaves behind. A base DN kept registered without its trees answers every operation
   * with a storage error, where its removal was meant to leave a plain "no such entry"; one whose
   * trees the rollback put back is left exactly as it was.
   */
  private void deregisterBaseDNsWhoseTreesAreGone(RootContainer rc, List<EntryContainer> deleted,
      ConfigChangeResult ccr)
  {
    if (deleted.isEmpty())
    {
      return;
    }
    final Set<TreeName> storedTrees;
    try
    {
      storedTrees = rc.getStorage().listTrees();
    }
    catch (Exception e)
    {
      // Nothing can be said about what survived, so nothing is given up on the strength of it.
      logger.traceException(e);
      ccr.setAdminActionRequired(true);
      return;
    }
    for (EntryContainer ec : deleted)
    {
      if (!allTreesStored(ec, storedTrees))
      {
        ccr.setAdminActionRequired(true);
        deregisterDeletedBaseDN(rc, ec, ccr);
      }
    }
  }

  private static boolean allTreesStored(EntryContainer ec, Set<TreeName> storedTrees)
  {
    for (Tree tree : ec.listTrees())
    {
      if (!storedTrees.contains(tree.getName()))
      {
        return false;
      }
    }
    return true;
  }

  private void deregisterDeletedBaseDNs(RootContainer rc, List<EntryContainer> deleted, ConfigChangeResult ccr)
  {
    for (EntryContainer ec : deleted)
    {
      deregisterDeletedBaseDN(rc, ec, ccr);
    }
  }

  private void deregisterDeletedBaseDN(RootContainer rc, EntryContainer ec, ConfigChangeResult ccr)
  {
    final DN baseDN = ec.getBaseDN();
    final BackendConfigManager backendConfigManager = serverContext.getBackendConfigManager();
    try
    {
      backendConfigManager.deregisterBaseDN(baseDN);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      if (backendConfigManager.getLocalBackendWithBaseDN(baseDN) == this)
      {
        // deregisterBaseDN puts its new registry in place only once it has succeeded, so this base
        // DN is still routed here. Leave the entry container registered: closeBackend() reclaims a
        // base DN through rootContainer.getBaseDNs(), and one taken out of there would stay claimed
        // by a backend which no longer holds it until the server is restarted.
        ccr.setResultCode(serverContext.getCoreConfigManager().getServerErrorResultCode());
        ccr.setAdminActionRequired(true);
        ccr.addMessage(ERR_BACKEND_CANNOT_DEREGISTER_BASEDN.get(baseDN, stackTraceToSingleLineString(e)));
        return;
      }
      // It is not registered here, which is what an earlier change whose registerBaseDN failed
      // leaves behind. Nothing routes to it, so there is nothing to hold on to.
    }
    rc.unregisterEntryContainer(baseDN);
    closeSilently(ec);
  }

  private void registerNewBaseDNs(RootContainer rc, List<EntryContainer> created, ConfigChangeResult ccr)
  {
    for (EntryContainer ec : created)
    {
      final DN baseDN = ec.getBaseDN();
      boolean registered = false;
      try
      {
        rc.registerEntryContainer(baseDN, ec);
        registered = true;
        serverContext.getBackendConfigManager().registerBaseDN(baseDN, this, false);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        ccr.setResultCode(serverContext.getCoreConfigManager().getServerErrorResultCode());
        ccr.setAdminActionRequired(true);
        ccr.addMessage(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(baseDN, e));
        if (!registered)
        {
          // Nothing else can reclaim it: closeBackend() and RootContainer.close() both work from
          // the registered containers, and this one keeps the configuration listeners its
          // constructor registered for as long as it is alive.
          closeSilently(ec);
        }
      }
    }
  }

  private static List<DN> baseDNsOf(List<EntryContainer> entryContainers)
  {
    final List<DN> baseDNs = new ArrayList<>(entryContainers.size());
    for (EntryContainer ec : entryContainers)
    {
      baseDNs.add(ec.getBaseDN());
    }
    return baseDNs;
  }

  /**
   * Returns a handle to the root container currently used by this backend.
   * The rootContainer could be NULL if the backend is not initialized.
   *
   * @return The RootContainer object currently used by this backend.
   */
  public final RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Returns a new read-only handle to the root container for this backend.
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
  RootContainer getReadOnlyRootContainer() throws ConfigException, InitializationException
  {
    return newRootContainer(AccessMode.READ_ONLY);
  }

  /**
   * Creates a customized DirectoryException from the StorageRuntimeException
   * thrown by the backend.
   *
   * @param e
   *          The StorageRuntimeException to be converted.
   * @return DirectoryException created from exception.
   */
  private DirectoryException createDirectoryException(Throwable e)
  {
    if (e instanceof DirectoryException)
    {
      return (DirectoryException) e;
    }
    if (e instanceof ExecutionException)
    {
      return createDirectoryException(e.getCause());
    }
    if (e instanceof LocalizableException)
    {
      return new DirectoryException(serverContext
          .getCoreConfigManager().getServerErrorResultCode(), ((LocalizableException) e).getMessageObject());
    }
    return new DirectoryException(serverContext
        .getCoreConfigManager().getServerErrorResultCode(), LocalizableMessage.raw(e.getMessage()==null?e.toString():e.getMessage()), e);
  }

  private RootContainer newRootContainer(AccessMode accessMode)
          throws ConfigException, InitializationException {
    // Open the storage
    try {
      final RootContainer rc = new RootContainer(getBackendID(), serverContext, storage, cfg);
      rc.open(accessMode);
      return rc;
    }
    catch (StorageInUseException e) {
      throw new InitializationException(ERR_VERIFY_BACKEND_ONLINE.get(), e);
    }
    catch (StorageRuntimeException e)
    {
      throw new InitializationException(ERR_OPEN_ENV_FAIL.get(e.getMessage()), e);
    }
  }
}
