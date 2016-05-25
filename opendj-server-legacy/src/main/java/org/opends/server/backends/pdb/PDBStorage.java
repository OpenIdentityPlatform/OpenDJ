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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pdb;

import static com.persistit.Transaction.CommitPolicy.*;
import static java.util.Arrays.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.backends.pluggable.spi.StorageUtils.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.Backupable;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageInUseException;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.StorageUtils;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;

import com.persistit.Configuration;
import com.persistit.Configuration.BufferPoolConfiguration;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Transaction.CommitPolicy;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.VolumeSpecification;
import com.persistit.exception.InUseException;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;
import com.persistit.exception.TreeNotFoundException;

/** PersistIt database implementation of the {@link Storage} engine. */
public final class PDBStorage implements Storage, Backupable, ConfigurationChangeListener<PDBBackendCfg>,
  DiskSpaceMonitorHandler
{
  private static final int IMPORT_DB_CACHE_SIZE = 32 * MB;

  private static final double MAX_SLEEP_ON_RETRY_MS = 50.0;
  private static final String VOLUME_NAME = "dj";
  private static final String JOURNAL_NAME = VOLUME_NAME + "_journal";
  /** The buffer / page size used by the PersistIt storage. */
  private static final int BUFFER_SIZE = 16 * 1024;

  /** PersistIt implementation of the {@link Cursor} interface. */
  private final class CursorImpl implements Cursor<ByteString, ByteString>
  {
    private ByteString currentKey;
    private ByteString currentValue;
    private final Exchange exchange;

    private CursorImpl(final Exchange exchange)
    {
      this.exchange = exchange;
    }

    @Override
    public void close()
    {
      // Release immediately because this exchange did not come from the txn cache
      releaseExchange(exchange);
    }

    @Override
    public boolean isDefined()
    {
      return exchange.getValue().isDefined();
    }

    @Override
    public ByteString getKey()
    {
      if (currentKey == null)
      {
        throwIfUndefined();
        currentKey = ByteString.wrap(exchange.getKey().reset().decodeByteArray());
      }
      return currentKey;
    }

    @Override
    public ByteString getValue()
    {
      if (currentValue == null)
      {
        throwIfUndefined();
        currentValue = ByteString.wrap(exchange.getValue().getByteArray());
      }
      return currentValue;
    }

    @Override
    public boolean next()
    {
      clearCurrentKeyAndValue();
      try
      {
        return exchange.next();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void delete()
    {
      throwIfUndefined();
      try
      {
        exchange.remove();
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToKey(final ByteSequence key)
    {
      clearCurrentKeyAndValue();
      bytesToKey(exchange.getKey(), key);
      try
      {
        exchange.fetch();
        return exchange.getValue().isDefined();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToKeyOrNext(final ByteSequence key)
    {
      clearCurrentKeyAndValue();
      bytesToKey(exchange.getKey(), key);
      try
      {
        exchange.fetch();
        return exchange.getValue().isDefined() || exchange.next();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToIndex(int index)
    {
      // There doesn't seem to be a way to optimize this using Persistit.
      clearCurrentKeyAndValue();
      exchange.getKey().to(Key.BEFORE);
      try
      {
        for (int i = 0; i <= index; i++)
        {
          if (!exchange.next())
          {
            return false;
          }
        }
        return true;
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToLastKey()
    {
      clearCurrentKeyAndValue();
      exchange.getKey().to(Key.AFTER);
      try
      {
        return exchange.previous();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private void clearCurrentKeyAndValue()
    {
      currentKey = null;
      currentValue = null;
    }

    private void throwIfUndefined()
    {
      if (!isDefined())
      {
        throw new NoSuchElementException();
      }
    }
  }

  /** PersistIt implementation of the {@link Importer} interface. */
  private final class ImporterImpl implements Importer
  {
    private final ThreadLocal<Map<TreeName, Exchange>> exchanges = new ThreadLocal<Map<TreeName, Exchange>>()
    {
      @Override
      protected Map<TreeName, Exchange> initialValue()
      {
        return new HashMap<>();
      }
    };

    @Override
    public void close()
    {
      PDBStorage.this.close();
    }

    @Override
    public void clearTree(final TreeName treeName)
    {
      final Transaction txn = db.getTransaction();
      deleteTree(txn, treeName);
      createTree(txn, treeName);
    }

    private void createTree(final Transaction txn, final TreeName treeName)
    {
      try
      {
        txn.begin();
        getNewExchange(treeName, true);
        txn.commit(commitPolicy);
      }
      catch (PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        txn.end();
      }
    }

    private void deleteTree(Transaction txn, final TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        txn.begin();
        ex = getNewExchange(treeName, true);
        ex.removeTree();
        txn.commit(commitPolicy);
      }
      catch (PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        txn.end();
      }
    }

    @Override
    public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        bytesToValue(ex.getValue(), value);
        ex.store();
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public ByteString read(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        return valueToBytes(ex.getValue());
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private Exchange getExchangeFromCache(final TreeName treeName) throws PersistitException
    {
      Map<TreeName, Exchange> threadExchanges = exchanges.get();
      Exchange exchange = threadExchanges.get(treeName);
      if (exchange == null)
      {
        exchange = getNewExchange(treeName, false);
        threadExchanges.put(treeName, exchange);
      }
      return exchange;
    }

    @Override
    public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      try
      {
        return new CursorImpl(getNewExchange(treeName, false));
      }
      catch (PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }
  }

  /** Common interface for internal WriteableTransaction implementations. */
  private interface StorageImpl extends WriteableTransaction, Closeable {
  }

  /** PersistIt implementation of the {@link WriteableTransaction} interface. */
  private final class WriteableStorageImpl implements StorageImpl
  {
    private static final String DUMMY_RECORD = "_DUMMY_RECORD_";
    private final Map<TreeName, Exchange> exchanges = new HashMap<>();

    @Override
    public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        bytesToValue(ex.getValue(), value);
        ex.store();
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean delete(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        return ex.remove();
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void deleteTree(final TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        ex = getExchangeFromCache(treeName);
        ex.removeTree();
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        exchanges.values().remove(ex);
        releaseExchange(ex);
      }
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      // FIXME: is there a better/quicker way to do this?
      try(final Cursor<?, ?> cursor = openCursor(treeName))
      {
        long count = 0;
        while (cursor.next())
        {
          count++;
        }
        return count;
      }
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(final TreeName treeName)
    {
      try
      {
        /*
         * Acquire a new exchange for the cursor rather than using a cached
         * exchange in order to avoid reentrant accesses to the same tree
         * interfering with the cursor position.
         */
        return new CursorImpl(getNewExchange(treeName, false));
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void openTree(final TreeName treeName, boolean createOnDemand)
    {
      if (createOnDemand)
      {
        openCreateTree(treeName);
      }
      else
      {
        try
        {
          getExchangeFromCache(treeName);
        }
        catch (final PersistitException | RollbackException e)
        {
          throw new StorageRuntimeException(e);
        }
      }
    }

    @Override
    public ByteString read(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        return valueToBytes(ex.getValue());
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean update(final TreeName treeName, final ByteSequence key, final UpdateFunction f)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        final ByteSequence oldValue = valueToBytes(ex.getValue());
        final ByteSequence newValue = f.computeNewValue(oldValue);
        if (!Objects.equals(newValue, oldValue))
        {
          if (newValue == null)
          {
            ex.remove();
          }
          else
          {
            ex.getValue().clear().putByteArray(newValue.toByteArray());
            ex.store();
          }
          return true;
        }
        return false;
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private void openCreateTree(final TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        ex = getNewExchange(treeName, true);
        // Work around a problem with forced shutdown right after tree creation.
        // Tree operations are not part of the journal, so force a couple operations to be able to recover.
        ByteString dummyKey = ByteString.valueOfUtf8(DUMMY_RECORD);
        put(treeName, dummyKey, ByteString.empty());
        delete(treeName, dummyKey);
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        releaseExchange(ex);
      }
    }

    private Exchange getExchangeFromCache(final TreeName treeName) throws PersistitException
    {
      Exchange exchange = exchanges.get(treeName);
      if (exchange == null)
      {
        exchange = getNewExchange(treeName, false);
        exchanges.put(treeName, exchange);
      }
      return exchange;
    }

    @Override
    public void close()
    {
      for (final Exchange ex : exchanges.values())
      {
        releaseExchange(ex);
      }
      exchanges.clear();
    }
  }

  /** PersistIt read-only implementation of {@link StorageImpl} interface. */
  private final class ReadOnlyStorageImpl implements StorageImpl {
    private final WriteableStorageImpl delegate;

    ReadOnlyStorageImpl(WriteableStorageImpl delegate)
    {
      this.delegate = delegate;
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      return delegate.read(treeName, key);
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      return delegate.openCursor(treeName);
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      return delegate.getRecordCount(treeName);
    }

    @Override
    public void openTree(TreeName treeName, boolean createOnDemand)
    {
      if (createOnDemand)
      {
        throw new ReadOnlyStorageException();
      }
      Exchange ex = null;
      try
      {
        ex = getNewExchange(treeName, false);
      }
      catch (final TreeNotFoundException e)
      {
        // ignore missing trees.
      }
      catch (final PersistitException | RollbackException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        releaseExchange(ex);
      }
    }

    @Override
    public void close()
    {
      delegate.close();
    }

    @Override
    public void deleteTree(TreeName name)
    {
      throw new ReadOnlyStorageException();
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      throw new ReadOnlyStorageException();
    }

    @Override
    public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f)
    {
      throw new ReadOnlyStorageException();
    }

    @Override
    public boolean delete(TreeName treeName, ByteSequence key)
    {
      throw new ReadOnlyStorageException();
    }
  }

  Exchange getNewExchange(final TreeName treeName, final boolean create) throws PersistitException
  {
    final Exchange ex = db.getExchange(volume, treeName.toString(), create);
    ex.setMaximumValueSize(Value.MAXIMUM_SIZE);
    return ex;
  }

  void releaseExchange(Exchange ex)
  {
    // Don't keep exchanges with enlarged value - let them be GC'd.
    // This is also done internally by Persistit in TransactionPlayer line 197.
    if (ex.getValue().getEncodedBytes().length < Value.DEFAULT_MAXIMUM_SIZE)
    {
      db.releaseExchange(ex);
    }
  }

  private StorageImpl newStorageImpl() {
    final WriteableStorageImpl writeableStorage = new WriteableStorageImpl();
    return accessMode.isWriteable() ? writeableStorage : new ReadOnlyStorageImpl(writeableStorage);
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ServerContext serverContext;
  private final File backendDirectory;
  private CommitPolicy commitPolicy;
  private AccessMode accessMode;
  private Persistit db;
  private Volume volume;
  private PDBBackendCfg config;
  private DiskSpaceMonitor diskMonitor;
  private PDBMonitor monitor;
  private MemoryQuota memQuota;
  private StorageStatus storageStatus = StorageStatus.working();

  /**
   * Creates a new persistit storage with the provided configuration.
   *
   * @param cfg
   *          The configuration.
   * @param serverContext
   *          This server instance context
   * @throws ConfigException if memory cannot be reserved
   */
  // FIXME: should be package private once importer is decoupled.
  public PDBStorage(final PDBBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    this.serverContext = serverContext;
    backendDirectory = getBackendDirectory(cfg);
    config = cfg;
    cfg.addPDBChangeListener(this);
  }

  private Configuration buildImportConfiguration()
  {
    final Configuration dbCfg = buildConfiguration(AccessMode.READ_WRITE);
    getBufferPoolCfg(dbCfg).setMaximumMemory(IMPORT_DB_CACHE_SIZE);
    commitPolicy = SOFT;
    return dbCfg;
  }

  private Configuration buildConfiguration(AccessMode accessMode)
  {
    this.accessMode = accessMode;

    final Configuration dbCfg = new Configuration();
    dbCfg.setLogFile(new File(backendDirectory, VOLUME_NAME + ".log").getPath());
    dbCfg.setJournalPath(new File(backendDirectory, JOURNAL_NAME).getPath());
    dbCfg.setCheckpointInterval(config.getDBCheckpointerWakeupInterval());
    // Volume is opened read write because recovery will fail if opened read-only
    dbCfg.setVolumeList(asList(new VolumeSpecification(new File(backendDirectory, VOLUME_NAME).getPath(), null,
        BUFFER_SIZE, 4096, Long.MAX_VALUE / BUFFER_SIZE, 2048, true, false, false)));
    final BufferPoolConfiguration bufferPoolCfg = getBufferPoolCfg(dbCfg);
    bufferPoolCfg.setMaximumCount(Integer.MAX_VALUE);

    diskMonitor = serverContext.getDiskSpaceMonitor();
    memQuota = serverContext.getMemoryQuota();
    if (config.getDBCacheSize() > 0)
    {
      bufferPoolCfg.setMaximumMemory(config.getDBCacheSize());
      memQuota.acquireMemory(config.getDBCacheSize());
    }
    else
    {
      bufferPoolCfg.setMaximumMemory(memQuota.memPercentToBytes(config.getDBCachePercent()));
      memQuota.acquireMemory(memQuota.memPercentToBytes(config.getDBCachePercent()));
    }
    commitPolicy = config.isDBTxnNoSync() ? SOFT : GROUP;
    dbCfg.setJmxEnabled(false);
    return dbCfg;
  }

  @Override
  public void close()
  {
    if (db != null)
    {
      DirectoryServer.deregisterMonitorProvider(monitor);
      monitor = null;
      try
      {
        db.close();
        db = null;
      }
      catch (final PersistitException e)
      {
        throw new IllegalStateException(e);
      }
    }
    if (config.getDBCacheSize() > 0)
    {
      memQuota.releaseMemory(config.getDBCacheSize());
    }
    else
    {
      memQuota.releaseMemory(memQuota.memPercentToBytes(config.getDBCachePercent()));
    }
    config.removePDBChangeListener(this);
    diskMonitor.deregisterMonitoredDirectory(getDirectory(), this);
  }

  private static BufferPoolConfiguration getBufferPoolCfg(Configuration dbCfg)
  {
    return dbCfg.getBufferPoolMap().get(BUFFER_SIZE);
  }

  @Override
  public void open(AccessMode accessMode) throws ConfigException, StorageRuntimeException
  {
    Reject.ifNull(accessMode, "accessMode must not be null");
    open0(buildConfiguration(accessMode));
  }

  private void open0(final Configuration dbCfg) throws ConfigException
  {
    setupStorageFiles(backendDirectory, config.getDBDirectoryPermissions(), config.dn());
    try
    {
      if (db != null)
      {
        throw new IllegalStateException(
            "Database is already open, either the backend is enabled or an import is currently running.");
      }
      db = new Persistit(dbCfg);

      final long bufferCount = getBufferPoolCfg(dbCfg).computeBufferCount(db.getAvailableHeap());
      final long totalSize = bufferCount * BUFFER_SIZE / 1024;
      logger.info(NOTE_PDB_MEMORY_CFG, config.getBackendId(), bufferCount, BUFFER_SIZE, totalSize);

      db.initialize();
      volume = db.loadVolume(VOLUME_NAME);
      monitor = new PDBMonitor(config.getBackendId() + " PDB Database", db);
      DirectoryServer.registerMonitorProvider(monitor);
    }
    catch(final InUseException e) {
      throw new StorageInUseException(e);
    }
    catch (final PersistitException | RollbackException e)
    {
      throw new StorageRuntimeException(e);
    }
    registerMonitoredDirectory(config);
  }

  @Override
  public <T> T read(final ReadOperation<T> operation) throws Exception
  {
    // This check may be unnecessary for PDB, but it will help us detect bad business logic
    // in the pluggable backend that would cause problems for JE.
    final Transaction txn = db.getTransaction();
    for (;;)
    {
      txn.begin();
      try
      {
        try (final StorageImpl storageImpl = newStorageImpl())
        {
          final T result = operation.run(storageImpl);
          txn.commit(commitPolicy);
          return result;
        }
        catch (final StorageRuntimeException e)
        {
          if (e.getCause() != null)
          {
              throw (Exception) e.getCause();
          }
          throw e;
        }
      }
      catch (final RollbackException e)
      {
        // retry
      }
      catch (final Exception e)
      {
        txn.rollback();
        throw e;
      }
      finally
      {
        txn.end();
      }
    }
  }

  @Override
  public Importer startImport() throws ConfigException, StorageRuntimeException
  {
    open0(buildImportConfiguration());
    return new ImporterImpl();
  }

  @Override
  public void write(final WriteOperation operation) throws Exception
  {
    final Transaction txn = db.getTransaction();
    for (;;)
    {
      txn.begin();
      try
      {
        try (final StorageImpl storageImpl = newStorageImpl())
        {
          operation.run(storageImpl);
          txn.commit(commitPolicy);
          return;
        }
        catch (final StorageRuntimeException e)
        {
          if (e.getCause() != null)
          {
            throw (Exception) e.getCause();
          }
          throw e;
        }
      }
      catch (final RollbackException e)
      {
        // retry after random sleep (reduces transactions collision. Drawback: increased latency)
        Thread.sleep((long) (Math.random() * MAX_SLEEP_ON_RETRY_MS));
      }
      catch (final Exception e)
      {
        txn.rollback();
        throw e;
      }
      finally
      {
        txn.end();
      }
    }
  }

  @Override
  public boolean supportsBackupAndRestore()
  {
    return true;
  }

  @Override
  public File getDirectory()
  {
    return getBackendDirectory(config);
  }

  private static File getBackendDirectory(PDBBackendCfg cfg)
  {
    return getDBDirectory(cfg.getDBDirectory(), cfg.getBackendId());
  }

  @Override
  public ListIterator<Path> getFilesToBackup() throws DirectoryException
  {
    try
    {
      if (db == null)
      {
        return getFilesToBackupWhenOffline();
      }

      // FIXME: use full programmatic way of retrieving backup file once available in persistIt
      // When requesting files to backup, append only mode must also be set (-a) otherwise it will be ended
      // by PersistIt and performing backup may corrupt the DB.
      String filesAsString = db.getManagement().execute("backup -a -f");
      String[] allFiles = filesAsString.split("[\r\n]+");
      final List<Path> files = new ArrayList<>();
      for (String file : allFiles)
      {
        files.add(Paths.get(file));
      }
      return files.listIterator();
    }
    catch (Exception e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_LIST_FILES_TO_BACKUP.get(config.getBackendId(), stackTraceToSingleLineString(e)));
    }
  }

  /** Filter to retrieve the database files to backup. */
  private static final FileFilter BACKUP_FILES_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      String name = file.getName();
      return VOLUME_NAME.equals(name) || name.matches(JOURNAL_NAME + "\\.\\d+$");
    }
  };

  /**
   * Returns the list of files to backup when there is no open database.
   * <p>
   * It is not possible to rely on the database returning the files, so the files must be retrieved
   * from a file filter.
   */
  private ListIterator<Path> getFilesToBackupWhenOffline() throws DirectoryException
  {
    return BackupManager.getFiles(getDirectory(), BACKUP_FILES_FILTER, config.getBackendId()).listIterator();
  }

  @Override
  public Path beforeRestore() throws DirectoryException
  {
    return null;
  }

  @Override
  public boolean isDirectRestore()
  {
    // restore is done in an intermediate directory
    return false;
  }

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

  /**
   * Switch the database in append only mode.
   * <p>
   * This is a mandatory operation before performing a backup.
   */
  private void switchToAppendOnlyMode() throws DirectoryException
  {
    try
    {
      // FIXME: use full programmatic way of switching to this mode once available in persistIt
      db.getManagement().execute("backup -a -c");
    }
    catch (RemoteException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_SWITCH_TO_APPEND_MODE.get(config.getBackendId(), stackTraceToSingleLineString(e)));
    }
  }

  /**
   * Terminate the append only mode of the database.
   * <p>
   * This should be called only when database was previously switched to append only mode.
   */
  private void endAppendOnlyMode() throws DirectoryException
  {
    try
    {
      // FIXME: use full programmatic way of ending append mode once available in persistIt
      db.getManagement().execute("backup -e");
    }
    catch (RemoteException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_END_APPEND_MODE.get(config.getBackendId(), stackTraceToSingleLineString(e)));
    }
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    if (db != null)
    {
      switchToAppendOnlyMode();
    }
    try
    {
      new BackupManager(config.getBackendId()).createBackup(this, backupConfig);
    }
    finally
    {
      if (db != null)
      {
        endAppendOnlyMode();
      }
    }
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    new BackupManager(config.getBackendId()).removeBackup(backupDirectory, backupID);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    new BackupManager(config.getBackendId()).restoreBackup(this, restoreConfig);
  }

  @Override
  public Set<TreeName> listTrees()
  {
    try
    {
      String[] treeNames = volume.getTreeNames();
      final Set<TreeName> results = new HashSet<>(treeNames.length);
      for (String treeName : treeNames)
      {
        if (!treeName.equals("_classIndex"))
        {
          results.add(TreeName.valueOf(treeName));
        }
      }
      return results;
    }
    catch (PersistitException e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  /**
   * TODO: it would be nice to use the low-level key/value APIs. They seem quite
   * inefficient at the moment for simple byte arrays.
   */
  private static Key bytesToKey(final Key key, final ByteSequence bytes)
  {
    final byte[] tmp = bytes.toByteArray();
    return key.clear().appendByteArray(tmp, 0, tmp.length);
  }

  private static Value bytesToValue(final Value value, final ByteSequence bytes)
  {
    value.clear().putByteArray(bytes.toByteArray());
    return value;
  }

  private static ByteString valueToBytes(final Value value)
  {
    if (value.isDefined())
    {
      return ByteString.wrap(value.getByteArray());
    }
    return null;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(PDBBackendCfg newCfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    long newSize = computeSize(newCfg);
    long oldSize = computeSize(config);
    return (newSize <= oldSize || memQuota.isMemoryAvailable(newSize - oldSize))
        && checkConfigurationDirectories(newCfg, unacceptableReasons);
  }

  private long computeSize(PDBBackendCfg cfg)
  {
    return cfg.getDBCacheSize() > 0 ? cfg.getDBCacheSize() : memQuota.memPercentToBytes(cfg.getDBCachePercent());
  }

  /**
   * Checks newly created backend has a valid configuration.
   * @param cfg the new configuration
   * @param unacceptableReasons the list of accumulated errors and their messages
   * @param context the server context
   * @return true if newly created backend has a valid configuration
   */
  static boolean isConfigurationAcceptable(PDBBackendCfg cfg, List<LocalizableMessage> unacceptableReasons,
      ServerContext context)
  {
    if (context != null)
    {
      MemoryQuota memQuota = context.getMemoryQuota();
      if (cfg.getDBCacheSize() > 0 && !memQuota.isMemoryAvailable(cfg.getDBCacheSize()))
      {
        unacceptableReasons.add(ERR_BACKEND_CONFIG_CACHE_SIZE_GREATER_THAN_JVM_HEAP.get(
            cfg.getDBCacheSize(), memQuota.getAvailableMemory()));
        return false;
      }
      else if (!memQuota.isMemoryAvailable(memQuota.memPercentToBytes(cfg.getDBCachePercent())))
      {
        unacceptableReasons.add(ERR_BACKEND_CONFIG_CACHE_PERCENT_GREATER_THAN_JVM_HEAP.get(
            cfg.getDBCachePercent(), memQuota.memBytesToPercent(memQuota.getAvailableMemory())));
        return false;
      }
    }
    return checkConfigurationDirectories(cfg, unacceptableReasons);
  }

  private static boolean checkConfigurationDirectories(PDBBackendCfg cfg,
    List<LocalizableMessage> unacceptableReasons)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    File newBackendDirectory = getBackendDirectory(cfg);

    checkDBDirExistsOrCanCreate(newBackendDirectory, ccr, true);
    checkDBDirPermissions(cfg.getDBDirectoryPermissions(), cfg.dn(), ccr);
    if (!ccr.getMessages().isEmpty())
    {
      unacceptableReasons.addAll(ccr.getMessages());
      return false;
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(PDBBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      File newBackendDirectory = getBackendDirectory(cfg);

      // Create the directory if it doesn't exist.
      if(!cfg.getDBDirectory().equals(config.getDBDirectory()))
      {
        checkDBDirExistsOrCanCreate(newBackendDirectory, ccr, false);
        if (!ccr.getMessages().isEmpty())
        {
          return ccr;
        }

        ccr.setAdminActionRequired(true);
        ccr.addMessage(NOTE_CONFIG_DB_DIR_REQUIRES_RESTART.get(config.getDBDirectory(), cfg.getDBDirectory()));
      }

      if (!cfg.getDBDirectoryPermissions().equalsIgnoreCase(config.getDBDirectoryPermissions())
          || !cfg.getDBDirectory().equals(config.getDBDirectory()))
      {
        checkDBDirPermissions(cfg.getDBDirectoryPermissions(), cfg.dn(), ccr);
        if (!ccr.getMessages().isEmpty())
        {
          return ccr;
        }

        setDBDirPermissions(newBackendDirectory, cfg.getDBDirectoryPermissions(), cfg.dn(), ccr);
        if (!ccr.getMessages().isEmpty())
        {
          return ccr;
        }
      }
      registerMonitoredDirectory(cfg);
      config = cfg;
      commitPolicy = config.isDBTxnNoSync() ? SOFT : GROUP;
    }
    catch (Exception e)
    {
      addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  private void registerMonitoredDirectory(PDBBackendCfg cfg)
  {
    diskMonitor.registerMonitoredDirectory(
      cfg.getBackendId() + " backend",
      getDirectory(),
      cfg.getDiskLowThreshold(),
      cfg.getDiskFullThreshold(),
      this);
  }

  @Override
  public void removeStorageFiles() throws StorageRuntimeException
  {
    StorageUtils.removeStorageFiles(backendDirectory);
  }

  @Override
  public StorageStatus getStorageStatus()
  {
    return storageStatus;
  }

  @Override
  public void diskFullThresholdReached(File directory, long thresholdInBytes) {
    storageStatus = statusWhenDiskSpaceFull(directory, thresholdInBytes, config.getBackendId());
  }

  @Override
  public void diskLowThresholdReached(File directory, long thresholdInBytes) {
    storageStatus = statusWhenDiskSpaceLow(directory, thresholdInBytes, config.getBackendId());
  }

  @Override
  public void diskSpaceRestored(File directory, long lowThresholdInBytes, long fullThresholdInBytes) {
    storageStatus = StorageStatus.working();
  }
}
