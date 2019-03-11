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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.jeb;

import static com.sleepycat.je.EnvironmentConfig.*;
import static com.sleepycat.je.LockMode.READ_COMMITTED;
import static com.sleepycat.je.LockMode.RMW;
import static com.sleepycat.je.OperationStatus.*;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.backends.pluggable.spi.StorageUtils.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.api.Backupable;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.pluggable.spi.EmptyCursor;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.Storage;
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

import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/** Berkeley DB Java Edition (JE for short) database implementation of the {@link Storage} engine. */
public final class JEStorage implements Storage, Backupable, ConfigurationChangeListener<JEBackendCfg>,
    DiskSpaceMonitorHandler
{
  /** JE implementation of the {@link Cursor} interface. */
  private static final class CursorImpl implements Cursor<ByteString, ByteString>
  {
    private ByteString currentKey;
    private ByteString currentValue;
    private boolean isDefined;
    private final com.sleepycat.je.Cursor cursor;
    private final DatabaseEntry dbKey = new DatabaseEntry();
    private final DatabaseEntry dbValue = new DatabaseEntry();

    private CursorImpl(com.sleepycat.je.Cursor cursor)
    {
      this.cursor = cursor;
    }

    @Override
    public void close()
    {
      closeSilently(cursor);
    }

    @Override
    public boolean isDefined()
    {
      return isDefined;
    }

    @Override
    public ByteString getKey()
    {
      if (currentKey == null)
      {
        throwIfNotSuccess();
        currentKey = ByteString.wrap(dbKey.getData());
      }
      return currentKey;
    }

    @Override
    public ByteString getValue()
    {
      if (currentValue == null)
      {
        throwIfNotSuccess();
        currentValue = ByteString.wrap(dbValue.getData());
      }
      return currentValue;
    }

    @Override
    public boolean next()
    {
      clearCurrentKeyAndValue();
      try
      {
        isDefined = cursor.getNext(dbKey, dbValue, null) == SUCCESS;
        return isDefined;
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      throwIfNotSuccess();
      try
      {
        cursor.delete();
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToKey(final ByteSequence key)
    {
      clearCurrentKeyAndValue();
      setData(dbKey, key);
      try
      {
        isDefined = cursor.getSearchKey(dbKey, dbValue, null) == SUCCESS;
        return isDefined;
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToKeyOrNext(final ByteSequence key)
    {
      clearCurrentKeyAndValue();
      setData(dbKey, key);
      try
      {
        isDefined = cursor.getSearchKeyRange(dbKey, dbValue, null) == SUCCESS;
        return isDefined;
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToIndex(int index)
    {
      clearCurrentKeyAndValue();
      try
      {
        isDefined = cursor.getFirst(dbKey, dbValue, null) == SUCCESS;
        if (!isDefined)
        {
          return false;
        }
        else if (index == 0)
        {
          return true;
        }

        // equivalent to READ_UNCOMMITTED
        long skipped = cursor.skipNext(index, dbKey, dbValue, null);
        if (skipped == index)
        {
          isDefined = cursor.getCurrent(dbKey, dbValue, null) == SUCCESS;
        }
        else
        {
          isDefined = false;
        }
        return isDefined;
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToLastKey()
    {
      clearCurrentKeyAndValue();
      try
      {
        isDefined = cursor.getLast(dbKey, dbValue, null) == SUCCESS;
        return isDefined;
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private void clearCurrentKeyAndValue()
    {
      currentKey = null;
      currentValue = null;
    }

    private void throwIfNotSuccess()
    {
      if (!isDefined())
      {
        throw new NoSuchElementException();
      }
    }
  }

  /** JE implementation of the {@link Importer} interface. */
  private final class ImporterImpl implements Importer
  {
    private final Map<TreeName, Database> trees = new HashMap<>();

    private Database getOrOpenTree(TreeName treeName)
    {
      return getOrOpenTree0(trees, treeName);
    }

    @Override
    public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value)
    {
      try
      {
        getOrOpenTree(treeName).put(null, db(key), db(value));
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public ByteString read(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        DatabaseEntry dbValue = new DatabaseEntry();
        boolean isDefined = getOrOpenTree(treeName).get(null, db(key), dbValue, null) == SUCCESS;
        return valueToBytes(dbValue, isDefined);
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      try
      {
        return new CursorImpl(getOrOpenTree(treeName).openCursor(null, new CursorConfig()));
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void clearTree(TreeName treeName)
    {
      env.truncateDatabase(null, toDatabaseName(treeName), false);
    }

    @Override
    public void close()
    {
      closeSilently(trees.values());
      trees.clear();
      JEStorage.this.close();
    }
  }

  /** JE implementation of the {@link WriteableTransaction} interface. */
  private final class WriteableTransactionImpl implements WriteableTransaction
  {
    private final Transaction txn;

    private WriteableTransactionImpl(Transaction txn)
    {
      this.txn = txn;
    }

    /**
     * This is currently needed for import-ldif:
     * <ol>
     * <li>Opening the EntryContainer calls {@link #openTree(TreeName, boolean)} for each index</li>
     * <li>Then the underlying storage is closed</li>
     * <li>Then {@link #startImport()} is called</li>
     * <li>Then ID2Entry#put() is called</li>
     * <li>Which in turn calls ID2Entry#encodeEntry()</li>
     * <li>Which in turn finally calls PersistentCompressedSchema#store()</li>
     * <li>Which uses a reference to the storage (that was closed before calling startImport()) and
     * uses it as if it was open</li>
     * </ol>
     */
    private Database getOrOpenTree(TreeName treeName)
    {
      try
      {
        return getOrOpenTree0(trees, treeName);
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void put(final TreeName treeName, final ByteSequence key, final ByteSequence value)
    {
      try
      {
        final OperationStatus status = getOrOpenTree(treeName).put(txn, db(key), db(value));
        if (status != SUCCESS)
        {
          throw new StorageRuntimeException(putErrorMsg(treeName, key, value, "did not succeed: " + status));
        }
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(putErrorMsg(treeName, key, value, "threw an exception"), e);
      }
    }

    private String putErrorMsg(TreeName treeName, ByteSequence key, ByteSequence value, String msg)
    {
      return "put(treeName=" + treeName + ", key=" + key + ", value=" + value + ") " + msg;
    }

    @Override
    public boolean delete(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        return getOrOpenTree(treeName).delete(txn, db(key)) == SUCCESS;
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(deleteErrorMsg(treeName, key, "threw an exception"), e);
      }
    }

    private String deleteErrorMsg(TreeName treeName, ByteSequence key, String msg)
    {
      return "delete(treeName=" + treeName + ", key=" + key + ") " + msg;
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      try
      {
        return getOrOpenTree(treeName).count();
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(final TreeName treeName)
    {
      try
      {
        return new CursorImpl(getOrOpenTree(treeName).openCursor(txn, CursorConfig.READ_COMMITTED));
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public ByteString read(final TreeName treeName, final ByteSequence key)
    {
      try
      {
        DatabaseEntry dbValue = new DatabaseEntry();
        boolean isDefined = getOrOpenTree(treeName).get(txn, db(key), dbValue, READ_COMMITTED) == SUCCESS;
        return valueToBytes(dbValue, isDefined);
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean update(final TreeName treeName, final ByteSequence key, final UpdateFunction f)
    {
      try
      {
        final Database tree = getOrOpenTree(treeName);
        final DatabaseEntry dbKey = db(key);
        final DatabaseEntry dbValue = new DatabaseEntry();
        for (;;)
        {
          final boolean isDefined = tree.get(txn, dbKey, dbValue, RMW) == SUCCESS;
          final ByteSequence oldValue = valueToBytes(dbValue, isDefined);
          final ByteSequence newValue = f.computeNewValue(oldValue);
          if (Objects.equals(newValue, oldValue))
          {
            return false;
          }
          if (newValue == null)
          {
            return tree.delete(txn, dbKey) == SUCCESS;
          }
          setData(dbValue, newValue);
          if (isDefined)
          {
            return tree.put(txn, dbKey, dbValue) == SUCCESS;
          }
          else if (tree.putNoOverwrite(txn, dbKey, dbValue) == SUCCESS)
          {
            return true;
          }
          // else retry due to phantom read: another thread inserted a record
        }
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void openTree(final TreeName treeName, boolean createOnDemand)
    {
      getOrOpenTree(treeName);
    }

    @Override
    public void deleteTree(final TreeName treeName)
    {
      try
      {
        synchronized (trees)
        {
          closeSilently(trees.remove(treeName));
          env.removeDatabase(txn, toDatabaseName(treeName));
        }
      }
      catch (DatabaseNotFoundException e)
      {
        // This is fine: end result is what we wanted
      }
      catch (DatabaseException e)
      {
        throw new StorageRuntimeException(e);
      }
    }
  }

  /** JE read-only implementation of {@link WriteableTransaction} interface. */
  private final class ReadOnlyTransactionImpl implements WriteableTransaction
  {
    private final WriteableTransactionImpl delegate;

    ReadOnlyTransactionImpl(WriteableTransactionImpl delegate)
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
      delegate.openTree(treeName, false);
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

  /** No operation storage transaction faking database files are present and empty. */
  private final class ReadOnlyEmptyTransactionImpl implements WriteableTransaction
  {
    @Override
    public void openTree(TreeName name, boolean createOnDemand)
    {
      if (createOnDemand)
      {
        throw new ReadOnlyStorageException();
      }
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

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      return null;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      return new EmptyCursor<>();
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      return 0;
    }
  }

  private WriteableTransaction newWriteableTransaction(Transaction txn)
  {
    // If no database files have been created yet and we're opening READ-ONLY
    // there is no db to use, since open was not called. Fake it.
    if (env == null)
    {
      return new ReadOnlyEmptyTransactionImpl();
    }
    final WriteableTransactionImpl writeableStorage = new WriteableTransactionImpl(txn);
    return accessMode.isWriteable() ? writeableStorage : new ReadOnlyTransactionImpl(writeableStorage);
  }

  private static final int IMPORT_DB_CACHE_SIZE = 32 * MB;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Use read committed isolation instead of the default which is repeatable read. */
  private static final TransactionConfig TXN_READ_COMMITTED = new TransactionConfig().setReadCommitted(true);

  private final ServerContext serverContext;
  private final File backendDirectory;
  private JEBackendCfg config;
  private AccessMode accessMode;

  /** It is NULL when opening the storage READ-ONLY and no files have been created yet. */
  private Environment env;
  private EnvironmentConfig envConfig;
  private MemoryQuota memQuota;
  private JEMonitor monitor;
  private DiskSpaceMonitor diskMonitor;
  private StorageStatus storageStatus = StorageStatus.working();
  private final ConcurrentMap<TreeName, Database> trees = new ConcurrentHashMap<>();

  /**
   * Creates a new JE storage with the provided configuration.
   *
   * @param cfg
   *          The configuration.
   * @param serverContext
   *          This server instance context
   * @throws ConfigException
   *           if memory cannot be reserved
   */
  JEStorage(final JEBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    this.serverContext = serverContext;
    backendDirectory = getBackendDirectory(cfg);
    config = cfg;
    cfg.addJEChangeListener(this);
  }

  private Database getOrOpenTree0(Map<TreeName, Database> trees, TreeName treeName)
  {
    Database tree = trees.get(treeName);
    if (tree == null)
    {
      synchronized (trees)
      {
        tree = trees.get(treeName);
        if (tree == null)
        {
          tree = env.openDatabase(null, toDatabaseName(treeName), dbConfig());
          trees.put(treeName, tree);
        }
      }
    }
    return tree;
  }

  private void buildConfiguration(AccessMode accessMode, boolean isImport) throws ConfigException
  {
    this.accessMode = accessMode;

    if (isImport)
    {
      envConfig = new EnvironmentConfig();
      envConfig
        .setTransactional(false)
        .setAllowCreate(true)
        .setLockTimeout(0, TimeUnit.SECONDS)
        .setTxnTimeout(0, TimeUnit.SECONDS)
        .setCacheSize(IMPORT_DB_CACHE_SIZE)
        .setDurability(Durability.COMMIT_NO_SYNC)
        .setConfigParam(CLEANER_MIN_UTILIZATION, String.valueOf(config.getDBCleanerMinUtilization()))
        .setConfigParam(LOG_FILE_MAX, String.valueOf(config.getDBLogFileMax()))
      	.setConfigParam("je.freeDisk",String.valueOf(50*1024*1024));
    }
    else
    {
      envConfig = ConfigurableEnvironment.parseConfigEntry(config);
    }

    diskMonitor = serverContext.getDiskSpaceMonitor();
    memQuota = serverContext.getMemoryQuota();
    if (config.getDBCacheSize() > 0)
    {
      memQuota.acquireMemory(config.getDBCacheSize());
    }
    else
    {
      memQuota.acquireMemory(memQuota.memPercentToBytes(config.getDBCachePercent()));
    }
  }

  private DatabaseConfig dbConfig()
  {
    boolean isImport = !envConfig.getTransactional();
    return new DatabaseConfig()
      .setKeyPrefixing(true)
      .setAllowCreate(true)
      .setTransactional(!isImport)
      .setDeferredWrite(isImport);
  }

  @Override
  public void close()
  {
    synchronized (trees)
    {
      closeSilently(trees.values());
      trees.clear();
    }

    if (env != null)
    {
      DirectoryServer.deregisterMonitorProvider(monitor);
      monitor = null;
      try
      {
        env.close();
        env = null;
      }
      catch (DatabaseException e)
      {
        throw new IllegalStateException(e);
      }
    }

    if (memQuota != null)
    {
      if (config.getDBCacheSize() > 0)
      {
        memQuota.releaseMemory(config.getDBCacheSize());
      }
      else
      {
        memQuota.releaseMemory(memQuota.memPercentToBytes(config.getDBCachePercent()));
      }
    }
    config.removeJEChangeListener(this);
    envConfig = null;
    if (diskMonitor != null)
    {
      diskMonitor.deregisterMonitoredDirectory(getDirectory(), this);
    }
  }

  @Override
  public void open(AccessMode accessMode) throws ConfigException, StorageRuntimeException
  {
    Reject.ifNull(accessMode, "accessMode must not be null");
    if (isBackendIncomplete(accessMode))
    {
      envConfig = new EnvironmentConfig();
      envConfig.setAllowCreate(false).setTransactional(false).setConfigParam("je.freeDisk",String.valueOf(50*1024*1024));
      // Do not open files on disk
      return;
    }
    buildConfiguration(accessMode, false);
    open0();
  }

  private boolean isBackendIncomplete(AccessMode accessMode)
  {
    return !accessMode.isWriteable() && (!backendDirectory.exists() || backendDirectoryIncomplete());
  }

  // TODO: it belongs to disk-based Storage Interface.
  private boolean backendDirectoryIncomplete()
  {
    try
    {
      return !getFilesToBackup().hasNext();
    }
    catch (DirectoryException ignored)
    {
      return true;
    }
  }

  private void open0() throws ConfigException
  {
    setupStorageFiles(backendDirectory, config.getDBDirectoryPermissions(), config.dn());
    try
    {
      if (env != null)
      {
        throw new IllegalStateException(
            "Database is already open, either the backend is enabled or an import is currently running.");
      }
      env = new Environment(backendDirectory, envConfig);
      monitor = new JEMonitor(config.getBackendId() + " JE Database", env);
      DirectoryServer.registerMonitorProvider(monitor);
    }
    catch (DatabaseException e)
    {
      throw new StorageRuntimeException(e);
    }
    registerMonitoredDirectory(config);
  }

  @Override
  public <T> T read(final ReadOperation<T> operation) throws Exception
  {
    try
    {
      return operation.run(newWriteableTransaction(null));
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

  @Override
  public Importer startImport() throws ConfigException, StorageRuntimeException
  {
    buildConfiguration(AccessMode.READ_WRITE, true);
    open0();
    return new ImporterImpl();
  }

  private static String toDatabaseName(final TreeName treeName)
  {
    return treeName.toString();
  }

  @Override
  public void write(final WriteOperation operation) throws Exception
  {
    final Transaction txn = beginTransaction();
    try
    {
      operation.run(newWriteableTransaction(txn));
      commit(txn);
    }
    catch (final StorageRuntimeException e)
    {
      if (e.getCause() != null)
      {
        throw (Exception) e.getCause();
      }
      throw e;
    }
    finally
    {
      abort(txn);
    }
  }

  private Transaction beginTransaction()
  {
    if (envConfig.getTransactional())
    {
      final Transaction txn = env.beginTransaction(null, TXN_READ_COMMITTED);
      logger.trace("beginTransaction txnid=%d", txn.getId());
      return txn;
    }
    return null;
  }

  private void commit(final Transaction txn)
  {
    if (txn != null)
    {
      txn.commit();
      logger.trace("commit txnid=%d", txn.getId());
    }
  }

  private void abort(final Transaction txn)
  {
    if (txn != null)
    {
      txn.abort();
      logger.trace("abort txnid=%d", txn.getId());
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

  private static File getBackendDirectory(JEBackendCfg cfg)
  {
    return getDBDirectory(cfg.getDBDirectory(), cfg.getBackendId());
  }

  @Override
  public ListIterator<Path> getFilesToBackup() throws DirectoryException
  {
    return new JELogFilesIterator(getDirectory(), config.getBackendId());
  }

  /**
   * Iterator on JE log files to backup.
   * <p>
   * The cleaner thread may delete some log files during the backup. The iterator is automatically
   * renewed if at least one file has been deleted.
   */
  static class JELogFilesIterator implements ListIterator<Path>
  {
    /** Root directory where all files are located. */
    private final File rootDirectory;
    private final String backendID;

    /** Underlying iterator on files. */
    private ListIterator<Path> iterator;
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

    private void setFiles(List<Path> files)
    {
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

    @Override
    public boolean hasNext()
    {
      boolean hasNext = iterator.hasNext();
      if (!hasNext && !files.isEmpty())
      {
        try
        {
          List<Path> allFiles = BackupManager.getFiles(rootDirectory, new JELogFileFilter(), backendID);
          List<Path> compare = new ArrayList<>(files);
          compare.removeAll(allFiles);
          if (!compare.isEmpty())
          {
            // at least one file was deleted,
            // the iterator must be renewed based on last file previously available
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

    @Override
    public Path next()
    {
      if (hasNext())
      {
        return iterator.next();
      }
      throw new NoSuchElementException();
    }

    @Override
    public boolean hasPrevious()
    {
      return iterator.hasPrevious();
    }

    @Override
    public Path previous()
    {
      return iterator.previous();
    }

    @Override
    public int nextIndex()
    {
      return iterator.nextIndex();
    }

    @Override
    public int previousIndex()
    {
      return iterator.previousIndex();
    }

    @Override
    public void remove()
    {
      throw new UnsupportedOperationException("remove() is not implemented");
    }

    @Override
    public void set(Path e)
    {
      throw new UnsupportedOperationException("set() is not implemented");
    }

    @Override
    public void add(Path e)
    {
      throw new UnsupportedOperationException("add() is not implemented");
    }
  }

  /**
   * This class implements a FilenameFilter to detect a JE log file, possibly with a constraint on
   * the file name and file size.
   */
  private static class JELogFileFilter implements FileFilter
  {
    private final String latestFilename;
    private final long latestFileSize;

    /**
     * Creates the filter for log files that are newer than provided file name
     * or equal to provided file name and of larger size.
     * @param latestFilename the latest file name
     * @param latestFileSize the latest file size
     */
    JELogFileFilter(String latestFilename, long latestFileSize)
    {
      this.latestFilename = latestFilename;
      this.latestFileSize = latestFileSize;
    }

    /** Creates the filter for any JE log file. */
    JELogFileFilter()
    {
      this("", 0);
    }

    @Override
    public boolean accept(File file)
    {
      String name = file.getName();
      int cmp = name.compareTo(latestFilename);
      return name.endsWith(".jdb")
          && (cmp > 0 || (cmp == 0 && file.length() > latestFileSize));
    }
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
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), msg);
    }
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    new BackupManager(config.getBackendId()).createBackup(this, backupConfig);
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
    if (env == null)
    {
      return Collections.<TreeName>emptySet();
    }
    try
    {
      List<String> treeNames = env.getDatabaseNames();
      final Set<TreeName> results = new HashSet<>(treeNames.size());
      for (String treeName : treeNames)
      {
        results.add(TreeName.valueOf(treeName));
      }
      return results;
    }
    catch (DatabaseException e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(JEBackendCfg newCfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    long newSize = computeSize(newCfg);
    long oldSize = computeSize(config);
    return (newSize <= oldSize || memQuota.isMemoryAvailable(newSize - oldSize))
        && checkConfigurationDirectories(newCfg, unacceptableReasons);
  }

  private long computeSize(JEBackendCfg cfg)
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
  static boolean isConfigurationAcceptable(JEBackendCfg cfg, List<LocalizableMessage> unacceptableReasons,
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

  private static boolean checkConfigurationDirectories(JEBackendCfg cfg,
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
  public ConfigChangeResult applyConfigurationChange(JEBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      File newBackendDirectory = getBackendDirectory(cfg);

      // Create the directory if it doesn't exist.
      if (!cfg.getDBDirectory().equals(config.getDBDirectory()))
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
    }
    catch (Exception e)
    {
      addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  private void registerMonitoredDirectory(JEBackendCfg cfg)
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

  private static void setData(final DatabaseEntry dbEntry, final ByteSequence bs)
  {
    dbEntry.setData(bs != null ? bs.toByteArray() : null);
  }

  private static DatabaseEntry db(final ByteSequence bs)
  {
    return new DatabaseEntry(bs != null ? bs.toByteArray() : null);
  }

  private static ByteString valueToBytes(final DatabaseEntry dbValue, boolean isDefined)
  {
    if (isDefined)
    {
      return ByteString.wrap(dbValue.getData());
    }
    return null;
  }
}
