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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pdb;

import static com.persistit.Transaction.CommitPolicy.*;
import static java.util.Arrays.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.UtilityMessages.*;
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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PDBBackendCfg;
import org.opends.server.api.Backupable;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageInUseException;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
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
import org.opends.server.types.FilePermission;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;

import com.persistit.Configuration;
import com.persistit.Configuration.BufferPoolConfiguration;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Tree;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.VolumeSpecification;
import com.persistit.exception.InUseException;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;
import com.persistit.exception.TreeNotFoundException;

/** PersistIt database implementation of the {@link Storage} engine. */
@SuppressWarnings("javadoc")
public final class PDBStorage implements Storage, Backupable, ConfigurationChangeListener<PDBBackendCfg>,
  DiskSpaceMonitorHandler
{
  private static final String VOLUME_NAME = "dj";

  private static final String JOURNAL_NAME = VOLUME_NAME + "_journal";

  /** The buffer / page size used by the PersistIt storage. */
  private static final int BUFFER_SIZE = 16 * 1024;

  /** PersistIt implementation of the {@link Cursor} interface. */
  private static final class CursorImpl implements Cursor<ByteString, ByteString>
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
      exchange.getPersistitInstance().releaseExchange(exchange);
    }

    @Override
    public boolean isDefined() {
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

    private void throwIfUndefined() {
      if (!isDefined()) {
        throw new NoSuchElementException();
      }
    }
  }

  /** PersistIt implementation of the {@link Importer} interface. */
  private final class ImporterImpl implements Importer
  {
    private final Map<TreeName, Tree> trees = new HashMap<>();
    private final Queue<Map<TreeName, Exchange>> allExchanges = new ConcurrentLinkedDeque<>();
    private final ThreadLocal<Map<TreeName, Exchange>> exchanges = new ThreadLocal<Map<TreeName, Exchange>>()
    {
      @Override
      protected Map<TreeName, Exchange> initialValue()
      {
        final Map<TreeName, Exchange> value = new HashMap<>();
        allExchanges.add(value);
        return value;
      }
    };

    @Override
    public void close()
    {
      for (Map<TreeName, Exchange> map : allExchanges)
      {
        for (Exchange exchange : map.values())
        {
          db.releaseExchange(exchange);
        }
        map.clear();
      }
      PDBStorage.this.close();
    }

    @Override
    public void createTree(final TreeName treeName)
    {
      try
      {
        final Tree tree = volume.getTree(mangleTreeName(treeName), true);
        trees.put(treeName, tree);
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
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
    public boolean delete(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        return ex.remove();
      }
      catch (final PersistitException e)
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
  }

  /** Common interface for internal WriteableTransaction implementations. */
  private interface StorageImpl extends WriteableTransaction, Closeable {
  }

  /** PersistIt implementation of the {@link WriteableTransaction} interface. */
  private final class WriteableStorageImpl implements StorageImpl
  {
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
      catch (final Exception e)
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
      catch (final PersistitException e)
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
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        exchanges.values().remove(ex);
        db.releaseExchange(ex);
      }
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      // FIXME: is there a better/quicker way to do this?
      final Cursor<?, ?> cursor = openCursor(treeName);
      try
      {
        long count = 0;
        while (cursor.next())
        {
          count++;
        }
        return count;
      }
      finally
      {
        cursor.close();
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
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void openTree(final TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        ex = getNewExchange(treeName, true);
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        db.releaseExchange(ex);
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

    @Override
    public void renameTree(final TreeName oldTreeName, final TreeName newTreeName)
    {
      throw new UnsupportedOperationException();
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
        if (!equals(newValue, oldValue))
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
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private boolean equals(ByteSequence b1, ByteSequence b2)
    {
      if (b1 == null)
      {
        return b2 == null;
      }
      return b1.equals(b2);
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
        db.releaseExchange(ex);
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
    public void openTree(TreeName treeName)
    {
      Exchange ex = null;
      try
      {
        ex = getNewExchange(treeName, false);
      }
      catch (final TreeNotFoundException e)
      {
        throw new ReadOnlyStorageException();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        db.releaseExchange(ex);
      }
    }

    @Override
    public void close()
    {
      delegate.close();
    }

    @Override
    public void renameTree(TreeName oldName, TreeName newName)
    {
      throw new ReadOnlyStorageException();
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

  private Exchange getNewExchange(final TreeName treeName, final boolean create) throws PersistitException
  {
    return db.getExchange(volume, mangleTreeName(treeName), create);
  }

  private StorageImpl newStorageImpl() {
    final WriteableStorageImpl writeableStorage = new WriteableStorageImpl();
    return accessMode.equals(AccessMode.READ_ONLY) ? new ReadOnlyStorageImpl(writeableStorage) : writeableStorage;
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private final ServerContext serverContext;
  private final File backendDirectory;
  private AccessMode accessMode;
  private Persistit db;
  private Volume volume;
  private PDBBackendCfg config;
  private DiskSpaceMonitor diskMonitor;
  private PDBMonitor pdbMonitor;
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
    backendDirectory = new File(getFileForPath(cfg.getDBDirectory()), cfg.getBackendId());
    config = cfg;
    cfg.addPDBChangeListener(this);
  }

  private Configuration buildConfiguration(AccessMode accessMode)
  {
    this.accessMode = accessMode;

    final Configuration dbCfg = new Configuration();
    dbCfg.setLogFile(new File(backendDirectory, VOLUME_NAME + ".log").getPath());
    dbCfg.setJournalPath(new File(backendDirectory, JOURNAL_NAME).getPath());
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
    dbCfg.setCommitPolicy(config.isDBTxnNoSync() ? SOFT : GROUP);
    dbCfg.setJmxEnabled(false);
    return dbCfg;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    if (db != null)
    {
      DirectoryServer.deregisterMonitorProvider(pdbMonitor);
      pdbMonitor = null;
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

  /** {@inheritDoc} */
  @Override
  public void open(AccessMode accessMode) throws ConfigException, StorageRuntimeException
  {
    Reject.ifNull(accessMode, "accessMode must not be null");
    open0(buildConfiguration(accessMode));
  }

  private void open0(final Configuration dbCfg) throws ConfigException
  {
    setupStorageFiles();
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
      pdbMonitor = new PDBMonitor(config.getBackendId() + " PDB Database", db);
      DirectoryServer.registerMonitorProvider(pdbMonitor);
    }
    catch(final InUseException e) {
      throw new StorageInUseException(e);
    }
    catch (final PersistitException e)
    {
      throw new StorageRuntimeException(e);
    }
    diskMonitor.registerMonitoredDirectory(
        config.getBackendId() + " backend",
        getDirectory(),
        config.getDiskLowThreshold(),
        config.getDiskFullThreshold(),
        this);
  }

  /** {@inheritDoc} */
  @Override
  public <T> T read(final ReadOperation<T> operation) throws Exception
  {
    final Transaction txn = db.getTransaction();
    for (;;)
    {
      txn.begin();
      try
      {
        try (final StorageImpl storageImpl = newStorageImpl())
        {
          final T result = operation.run(storageImpl);
          txn.commit();
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

  /** {@inheritDoc} */
  @Override
  public Importer startImport() throws ConfigException, StorageRuntimeException
  {
    open0(buildConfiguration(AccessMode.READ_WRITE));
    return new ImporterImpl();
  }

  private static String mangleTreeName(final TreeName treeName)
  {
    StringBuilder mangled = new StringBuilder();
    String name = treeName.toString();

    for (int idx = 0; idx < name.length(); idx++)
    {
      char ch = name.charAt(idx);
      if (ch == '=' || ch == ',')
      {
        ch = '_';
      }
      mangled.append(ch);
    }
    return mangled.toString();
  }

  /** {@inheritDoc} */
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
          txn.commit();
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
  public boolean supportsBackupAndRestore()
  {
    return true;
  }

  @Override
  public File getDirectory()
  {
    File parentDir = getFileForPath(config.getDBDirectory());
    return new File(parentDir, config.getBackendId());
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
      return name.equals(VOLUME_NAME) || name.matches(JOURNAL_NAME + "\\.\\d+$");
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
        results.add(TreeName.valueOf(treeName));
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

  /** {@inheritDoc} */
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
   * @param context TODO
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
    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    File newBackendDirectory = new File(parentDirectory, cfg.getBackendId());

    checkDBDirExistsOrCanCreate(newBackendDirectory, ccr, true);
    checkDBDirPermissions(cfg, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      unacceptableReasons.addAll(ccr.getMessages());
      return false;
    }
    return true;
  }

  /**
   * Checks a directory exists or can actually be created.
   *
   * @param backendDir the directory to check for
   * @param ccr the list of reasons to return upstream or null if called from setupStorage()
   * @param cleanup true if the directory should be deleted after creation
   */
  private static void checkDBDirExistsOrCanCreate(File backendDir, ConfigChangeResult ccr, boolean cleanup)
  {
    if (!backendDir.exists())
    {
      if(!backendDir.mkdirs())
      {
        addErrorMessage(ccr, ERR_CREATE_FAIL.get(backendDir.getPath()));
      }
      if (cleanup)
      {
        backendDir.delete();
      }
    }
    else if (!backendDir.isDirectory())
    {
      addErrorMessage(ccr, ERR_DIRECTORY_INVALID.get(backendDir.getPath()));
    }
  }

  /**
   * Returns false if directory permissions in the configuration are invalid. Otherwise returns the
   * same value as it was passed in.
   *
   * @param cfg a (possibly new) backend configuration
   * @param ccr the current list of change results
   * @throws forwards a file exception
   */
  private static void checkDBDirPermissions(PDBBackendCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      FilePermission backendPermission = decodeDBDirPermissions(cfg);
      // Make sure the mode will allow the server itself access to the database
      if(!backendPermission.isOwnerWritable() ||
          !backendPermission.isOwnerReadable() ||
          !backendPermission.isOwnerExecutable())
      {
        addErrorMessage(ccr, ERR_CONFIG_BACKEND_INSANE_MODE.get(cfg.getDBDirectoryPermissions()));
      }
    }
    catch(ConfigException ce)
    {
      addErrorMessage(ccr, ce.getMessageObject());
    }
  }

  /**
   * Sets files permissions on the backend directory.
   *
   * @param backendDir the directory to setup
   * @param curCfg a backend configuration
   */
  private void setDBDirPermissions(PDBBackendCfg curCfg, File backendDir) throws ConfigException
  {
    FilePermission backendPermission = decodeDBDirPermissions(curCfg);

    // Get the backend database backendDirectory permissions and apply
    try
    {
      if(!FilePermission.setPermissions(backendDir, backendPermission))
      {
        logger.warn(WARN_UNABLE_SET_PERMISSIONS, backendPermission, backendDir);
      }
    }
    catch(Exception e)
    {
      // Log an warning that the permissions were not set.
      logger.warn(WARN_SET_PERMISSIONS_FAILED, backendDir, e);
    }
  }

  private static FilePermission decodeDBDirPermissions(PDBBackendCfg curCfg) throws ConfigException
  {
    try
    {
      return FilePermission.decodeUNIXMode(curCfg.getDBDirectoryPermissions());
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_CONFIG_BACKEND_MODE_INVALID.get(curCfg.dn()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(PDBBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      File parentDirectory = getFileForPath(cfg.getDBDirectory());
      File newBackendDirectory = new File(parentDirectory, cfg.getBackendId());

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
        checkDBDirPermissions(cfg, ccr);
        if (!ccr.getMessages().isEmpty())
        {
          return ccr;
        }

        setDBDirPermissions(cfg, newBackendDirectory);
      }
      diskMonitor.registerMonitoredDirectory(
        config.getBackendId() + " backend",
        getDirectory(),
        cfg.getDiskLowThreshold(),
        cfg.getDiskFullThreshold(),
        this);
      config = cfg;
    }
    catch (Exception e)
    {
      addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  private static void addErrorMessage(final ConfigChangeResult ccr, LocalizableMessage message)
  {
    ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    ccr.addMessage(message);
  }

  private void setupStorageFiles() throws ConfigException
  {
    ConfigChangeResult ccr = new ConfigChangeResult();

    checkDBDirExistsOrCanCreate(backendDirectory, ccr, false);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().get(0));
    }
    checkDBDirPermissions(config, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().get(0));
    }
    setDBDirPermissions(config, backendDirectory);
  }

  @Override
  public void removeStorageFiles() throws StorageRuntimeException
  {
    if (!backendDirectory.exists())
    {
      return;
    }

    if (!backendDirectory.isDirectory())
    {
      throw new StorageRuntimeException(ERR_DIRECTORY_INVALID.get(backendDirectory.getPath()).toString());
    }

    try
    {
      File[] files = backendDirectory.listFiles();
      for (File f : files)
      {
        f.delete();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new StorageRuntimeException(ERR_REMOVE_FAIL.get(e.getMessage()).toString(), e);
    }

  }

  @Override
  public StorageStatus getStorageStatus()
  {
    return storageStatus;
  }

  /** {@inheritDoc} */
  @Override
  public void diskFullThresholdReached(File directory, long thresholdInBytes) {
    storageStatus = StorageStatus.unusable(
        WARN_DISK_SPACE_FULL_THRESHOLD_CROSSED.get(directory.getFreeSpace(), directory.getAbsolutePath(),
        thresholdInBytes, config.getBackendId()));
  }

  /** {@inheritDoc} */
  @Override
  public void diskLowThresholdReached(File directory, long thresholdInBytes) {
    storageStatus = StorageStatus.lockedDown(
        WARN_DISK_SPACE_LOW_THRESHOLD_CROSSED.get(directory.getFreeSpace(), directory.getAbsolutePath(),
        thresholdInBytes, config.getBackendId()));
  }

  /** {@inheritDoc} */
  @Override
  public void diskSpaceRestored(File directory, long lowThresholdInBytes, long fullThresholdInBytes) {
    storageStatus = StorageStatus.working();
  }
}

