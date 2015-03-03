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
package org.opends.server.backends.persistit;

import static com.persistit.Transaction.CommitPolicy.*;
import static java.util.Arrays.*;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_BACKEND_INSANE_MODE;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_BACKEND_MODE_INVALID;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.util.ServerConstants.ALERT_DESCRIPTION_DISK_FULL;
import static org.opends.server.util.ServerConstants.ALERT_DESCRIPTION_DISK_SPACE_LOW;
import static org.opends.server.util.ServerConstants.ALERT_TYPE_DISK_FULL;
import static org.opends.server.util.ServerConstants.ALERT_TYPE_DISK_SPACE_LOW;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.DN;
import org.opends.server.types.FilePermission;

import com.persistit.Configuration;
import com.persistit.Configuration.BufferPoolConfiguration;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Tree;
import com.persistit.TreeBuilder;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.VolumeSpecification;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;

/** PersistIt database implementation of the {@link Storage} engine. */
public final class PersistItStorage implements Storage, ConfigurationChangeListener<PersistitBackendCfg>,
  DiskSpaceMonitorHandler, AlertGenerator
{
  private static final String VOLUME_NAME = "dj";
  /** The buffer / page size used by the PersistIt storage. */
  private static final int BUFFER_SIZE = 16 * 1024;

  /** PersistIt implementation of the {@link Cursor} interface. */
  private final class CursorImpl implements Cursor
  {
    private ByteString currentKey;
    private ByteString currentValue;
    private final Exchange ex;

    private CursorImpl(final Exchange exchange)
    {
      this.ex = exchange;
    }

    @Override
    public void close()
    {
      // Release immediately because this exchange did not come from the txn cache
      db.releaseExchange(ex);
    }

    @Override
    public ByteString getKey()
    {
      if (currentKey == null)
      {
        currentKey = keyToBytes(ex.getKey());
      }
      return currentKey;
    }

    @Override
    public ByteString getValue()
    {
      if (currentValue == null)
      {
        currentValue = valueToBytes(ex.getValue());
      }
      return currentValue;
    }

    @Override
    public boolean next()
    {
      clearCurrentKeyAndValue();
      try
      {
        return ex.next();
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
      bytesToKey(ex.getKey(), key);
      try
      {
        ex.fetch();
        return ex.getValue().isDefined();
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
      bytesToKey(ex.getKey(), key);
      try
      {
        ex.fetch();
        return ex.getValue().isDefined() || ex.next();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean positionToLastKey()
    {
      try
      {
        clearCurrentKeyAndValue();
        ex.getKey().to(Key.AFTER);
        return ex.previous();
      }
      catch (final PersistitException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public boolean previous()
    {
      clearCurrentKeyAndValue();
      try
      {
        return ex.previous();
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
  }

  /** PersistIt implementation of the {@link Importer} interface. */
  private final class ImporterImpl implements Importer
  {
    private final TreeBuilder importer = new TreeBuilder(db);
    private final Key importKey = new Key(db);
    private final Value importValue = new Value(db);
    private final Map<TreeName, Tree> trees = new HashMap<TreeName, Tree>();

    @Override
    public void close()
    {
      try
      {
        importer.merge();
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
      finally
      {
        PersistItStorage.this.close();
      }
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
    public void put(final TreeName treeName, final ByteSequence key,
        final ByteSequence value)
    {
      try
      {
        final Tree tree = trees.get(treeName);
        importer.store(tree, bytesToKey(importKey, key),
            bytesToValue(importValue, value));
      }
      catch (final Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }
  }

  /** PersistIt implementation of the {@link WriteableStorage} interface. */
  private final class StorageImpl implements WriteableStorage
  {
    private final Map<TreeName, Exchange> exchanges = new HashMap<TreeName, Exchange>();

    @Override
    public void create(final TreeName treeName, final ByteSequence key,
        final ByteSequence value)
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
    public ByteString getRMW(final TreeName treeName, final ByteSequence key)
    {
      return read(treeName, key);
    }

    @Override
    public Cursor openCursor(final TreeName treeName)
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
    public boolean putIfAbsent(final TreeName treeName, final ByteSequence key,
        final ByteSequence value)
    {
      try
      {
        // There is no CAS (Compare And Swap) operation to do here :)
        // Following code is fine because Persistit provides snapshot isolation.
        // If another thread tries to update the same key, we'll get a RollbackException
        // And the write operation will be retried (see write() method in this class)
        final Exchange ex = getExchangeFromCache(treeName);
        bytesToKey(ex.getKey(), key);
        ex.fetch();
        final Value exValue = ex.getValue();
        if (exValue.isDefined())
        {
          return false;
        }
        bytesToValue(exValue, value);
        ex.store();
        return true;
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

    @Override
    public void renameTree(final TreeName oldTreeName, final TreeName newTreeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void truncateTree(final TreeName treeName)
    {
      try
      {
        getExchangeFromCache(treeName).removeAll();
      }
      catch (final PersistitException e)
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
        if (!equals(newValue, oldValue))
        {
          ex.getValue().clear().putByteArray(newValue.toByteArray());
          ex.store();
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

    private Exchange getExchangeFromCache(final TreeName treeName)
        throws PersistitException
    {
      Exchange exchange = exchanges.get(treeName);
      if (exchange == null)
      {
        exchange = getNewExchange(treeName, false);
        exchanges.put(treeName, exchange);
      }
      return exchange;
    }

    private void release()
    {
      for (final Exchange ex : exchanges.values())
      {
        db.releaseExchange(ex);
      }
      exchanges.clear();
    }

    private Exchange getNewExchange(final TreeName treeName, final boolean create)
        throws PersistitException
    {
      return db.getExchange(volume, mangleTreeName(treeName), create);
    }
  }

  private static void clearAndCreateDbDir(final File dbDir)
  {
    if (dbDir.exists())
    {
      for (final File child : dbDir.listFiles())
      {
        child.delete();
      }
    }
    else
    {
      dbDir.mkdirs();
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private File backendDirectory;
  private Persistit db;
  private Volume volume;
  private Configuration dbCfg;
  private PersistitBackendCfg config;
  private DiskSpaceMonitor diskMonitor;

  /**
   * Creates a new persistit storage with the provided configuration.
   *
   * @param cfg
   *          The configuration.
   */
  // FIXME: should be package private once importer is decoupled.
  public PersistItStorage(final PersistitBackendCfg cfg)
  {
    backendDirectory = new File(getFileForPath(cfg.getDBDirectory()), cfg.getBackendId());
    config = cfg;
    dbCfg = new Configuration();
    dbCfg.setLogFile(new File(backendDirectory, VOLUME_NAME + ".log").getPath());
    dbCfg.setJournalPath(new File(backendDirectory, VOLUME_NAME + "_journal").getPath());
    dbCfg.setVolumeList(asList(new VolumeSpecification(new File(backendDirectory, VOLUME_NAME).getPath(), null,
        BUFFER_SIZE, 4096, Long.MAX_VALUE / BUFFER_SIZE, 2048, true, false, false)));
    final BufferPoolConfiguration bufferPoolCfg = getBufferPoolCfg();
    bufferPoolCfg.setMaximumCount(Integer.MAX_VALUE);
    if (cfg.getDBCacheSize() > 0)
    {
      bufferPoolCfg.setMaximumMemory(cfg.getDBCacheSize());
    }
    else
    {
      bufferPoolCfg.setFraction(cfg.getDBCachePercent() / 100.0f);
    }
    dbCfg.setCommitPolicy(cfg.isDBTxnNoSync() ? SOFT : GROUP);
    cfg.addPersistitChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    if (db != null)
    {
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
    config.removePersistitChangeListener(this);
    DirectoryServer.deregisterMonitorProvider(diskMonitor);
    DirectoryServer.deregisterAlertGenerator(this);
  }

  /** {@inheritDoc} */
  @Override
  public void closeTree(final TreeName treeName)
  {
    // nothing to do, in persistit you close the volume itself
  }

  private BufferPoolConfiguration getBufferPoolCfg()
  {
    return dbCfg.getBufferPoolMap().get(BUFFER_SIZE);
  }

  /** {@inheritDoc} */
  @Override
  public void open() throws Exception
  {
    setupStorageFiles();
    try
    {
      db = new Persistit(dbCfg);

      final long bufferCount = getBufferPoolCfg().computeBufferCount(db.getAvailableHeap());
      final long totalSize = bufferCount * BUFFER_SIZE / 1024;
      logger.info(NOTE_PERSISTIT_MEMORY_CFG, config.getBackendId(),
          bufferCount, BUFFER_SIZE, totalSize);

      db.initialize();
      volume = db.loadVolume(VOLUME_NAME);
    }
    catch (final PersistitException e)
    {
      throw new StorageRuntimeException(e);
    }
    // Register as disk space monitor handler
    diskMonitor = newDiskMonitor(config);
    DirectoryServer.registerMonitorProvider(diskMonitor);
    //Register as an AlertGenerator.
    DirectoryServer.registerAlertGenerator(this);
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
        final StorageImpl storageImpl = new StorageImpl();
        try
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
        finally
        {
          storageImpl.release();
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
  public Importer startImport() throws Exception
  {
    clearAndCreateDbDir(backendDirectory);
    open();
    return new ImporterImpl();
  }

  private String mangleTreeName(final TreeName treeName)
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
        final StorageImpl storageImpl = new StorageImpl();
        try
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
        finally
        {
          storageImpl.release();
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

  /** {@inheritDoc} */
  @Override
  public FilenameFilter getFilesToBackupFilter()
  {
    return new FilenameFilter()
    {
      @Override
      public boolean accept(File d, String name)
      {
        return name.startsWith(VOLUME_NAME) && !name.endsWith(".lck");
      }
    };
  }

  /**
   * TODO: it would be nice to use the low-level key/value APIs. They seem quite
   * inefficient at the moment for simple byte arrays.
   */
  private Key bytesToKey(final Key key, final ByteSequence bytes)
  {
    final byte[] tmp = bytes.toByteArray();
    return key.clear().appendByteArray(tmp, 0, tmp.length);
  }

  private Value bytesToValue(final Value value, final ByteSequence bytes)
  {
    value.clear().putByteArray(bytes.toByteArray());
    return value;
  }

  private ByteString keyToBytes(final Key key)
  {
    return ByteString.wrap(key.reset().decodeByteArray());
  }

  private ByteString valueToBytes(final Value value)
  {
    if (value.isDefined())
    {
      return ByteString.wrap(value.getByteArray());
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(PersistitBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
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
  private void checkDBDirExistsOrCanCreate(File backendDir, ConfigChangeResult ccr, boolean cleanup)
  {
    if (!backendDir.exists())
    {
      if(!backendDir.mkdirs())
      {
        addErrorMessage(ccr, ERR_JEB_CREATE_FAIL.get(backendDir.getPath()));
      }
      if (cleanup)
      {
        backendDir.delete();
      }
    }
    else if (!backendDir.isDirectory())
    {
      addErrorMessage(ccr, ERR_JEB_DIRECTORY_INVALID.get(backendDir.getPath()));
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
  private void checkDBDirPermissions(PersistitBackendCfg cfg, ConfigChangeResult ccr)
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
   * Sets files permissions on the backend directory
   *
   * @param backendDir the directory to setup
   * @param curCfg a backend configuration
   */
  private void setDBDirPermissions(PersistitBackendCfg curCfg, File backendDir) throws ConfigException
  {
    FilePermission backendPermission = decodeDBDirPermissions(curCfg);

    // Get the backend database backendDirectory permissions and apply
    if(FilePermission.canSetPermissions())
    {
      try
      {
        if(!FilePermission.setPermissions(backendDir, backendPermission))
        {
          logger.warn(WARN_JEB_UNABLE_SET_PERMISSIONS, backendPermission, backendDir);
        }
      }
      catch(Exception e)
      {
        // Log an warning that the permissions were not set.
        logger.warn(WARN_JEB_SET_PERMISSIONS_FAILED, backendDir, e);
      }
    }
  }

  private FilePermission decodeDBDirPermissions(PersistitBackendCfg curCfg) throws ConfigException
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
  public ConfigChangeResult applyConfigurationChange(PersistitBackendCfg cfg)
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
        ccr.addMessage(NOTE_JEB_CONFIG_DB_DIR_REQUIRES_RESTART.get(config.getDBDirectory(),
            cfg.getDBDirectory()));
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
      diskMonitor.setFullThreshold(cfg.getDiskFullThreshold());
      diskMonitor.setLowThreshold(cfg.getDiskLowThreshold());

      config = cfg;
    }
    catch (Exception e)
    {
      addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  private void addErrorMessage(final ConfigChangeResult ccr, LocalizableMessage message)
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

  /** {@inheritDoc} */
  public void removeStorageFiles() throws StorageRuntimeException
  {
    if (!backendDirectory.isDirectory())
    {
      LocalizableMessage msg = ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath());
      throw new StorageRuntimeException(msg.toString());
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
      LocalizableMessage message = ERR_JEB_REMOVE_FAIL.get(e.getMessage());
      throw new StorageRuntimeException(message.toString(), e);
    }
  }

  @Override
  public StorageStatus getStorageStatus()
  {
    if (diskMonitor.isFullThresholdReached())
    {
      return StorageStatus.unusable(WARN_JEB_OUT_OF_DISK_SPACE.get());
    }
    if (diskMonitor.isLowThresholdReached())
    {
      return StorageStatus.lockedDown(WARN_JEB_OUT_OF_DISK_SPACE.get());
    }
    return StorageStatus.working();
  }

  /** {@inheritDoc} */
  @Override
  public void diskFullThresholdReached(DiskSpaceMonitor monitor) {
    LocalizableMessage msg = ERR_JEB_DISK_FULL_THRESHOLD_REACHED.get(
        monitor.getDirectory().getPath(), config.getBackendId(), monitor.getFreeSpace(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
    DirectoryServer.sendAlertNotification(this, ALERT_TYPE_DISK_FULL, msg);
  }

  /** {@inheritDoc} */
  @Override
  public void diskLowThresholdReached(DiskSpaceMonitor monitor) {
    LocalizableMessage msg = ERR_JEB_DISK_LOW_THRESHOLD_REACHED.get(
        monitor.getDirectory().getPath(), config.getBackendId(), monitor.getFreeSpace(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
    DirectoryServer.sendAlertNotification(this, ALERT_TYPE_DISK_SPACE_LOW, msg);
  }

  /** {@inheritDoc} */
  @Override
  public void diskSpaceRestored(DiskSpaceMonitor monitor) {
    logger.error(NOTE_JEB_DISK_SPACE_RESTORED, monitor.getFreeSpace(),
        monitor.getDirectory().getPath(), config.getBackendId(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
  }

  private DiskSpaceMonitor newDiskMonitor(PersistitBackendCfg config) throws Exception
  {
    File parentDirectory = getFileForPath(config.getDBDirectory());
    File backendDirectory = new File(parentDirectory, config.getBackendId());
    DiskSpaceMonitor dm = new DiskSpaceMonitor(config.getBackendId() + " backend",
        backendDirectory, config.getDiskLowThreshold(), config.getDiskFullThreshold(),
        5, TimeUnit.SECONDS, this);
    dm.initializeMonitorProvider(null);
    return dm;
  }

  /** {@inheritDoc} */
  @Override
  public DN getComponentEntryDN() {
    return config.dn();
  }

  /** {@inheritDoc} */
  @Override
  public String getClassName() {
    return PersistItStorage.class.getName();
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<String, String>();
    alerts.put(ALERT_TYPE_DISK_SPACE_LOW, ALERT_DESCRIPTION_DISK_SPACE_LOW);
    alerts.put(ALERT_TYPE_DISK_FULL, ALERT_DESCRIPTION_DISK_FULL);
    return alerts;
  }
}

