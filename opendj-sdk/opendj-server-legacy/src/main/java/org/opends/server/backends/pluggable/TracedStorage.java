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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;

/** Decorates a {@link Storage} with additional trace logging. */
@SuppressWarnings("javadoc")
final class TracedStorage implements Storage
{
  /** Decorates an {@link Importer} with additional trace logging. */
  private final class TracedImporter implements Importer
  {
    private final Importer importer;

    private TracedImporter(final Importer importer)
    {
      this.importer = importer;
    }

    @Override
    public void createTree(final TreeName name)
    {
      importer.createTree(name);
      logger.trace("Storage@%s.Importer@%s.createTree(%s, %s)",
          storageId(), id(), backendId, name);
    }

    @Override
    public void put(final TreeName name, final ByteSequence key, final ByteSequence value)
    {
      importer.put(name, key, value);
      logger.trace("Storage@%s.Importer@%s.put(%s, %s, %s, %s)",
          storageId(), id(), backendId, name, hex(key), hex(value));
    }

    @Override
    public ByteString read(TreeName name, ByteSequence key)
    {
      final ByteString value = importer.read(name, key);
      logger.trace("Storage@%s.Importer@%s.read(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), hex(value));
      return value;
    }

    @Override
    public boolean delete(TreeName name, ByteSequence key)
    {
      final boolean delete = importer.delete(name, key);
      logger.trace("Storage@%s.Importer@%s.delete(%s, %s, %s) = %b",
          storageId(), id(), backendId, name, hex(key), delete);
      return delete;
    }

    @Override
    public void close()
    {
      importer.close();
      logger.trace("Storage@%s.Importer@%s.close(%s)",
          storageId(), id(), backendId);
    }

    private int id()
    {
      return System.identityHashCode(this);
    }
  }

  /** Decorates an {@link ReadableTransaction} with additional trace logging. */
  private final class TracedReadableTransaction implements ReadableTransaction
  {
    private final ReadableTransaction txn;

    private TracedReadableTransaction(final ReadableTransaction txn)
    {
      this.txn = txn;
    }

    @Override
    public long getRecordCount(TreeName name)
    {
      final long count = txn.getRecordCount(name);
      logger.trace("Storage@%s.ReadableTransaction@%s.getRecordCount(%s, %s) = %d",
          storageId(), id(), backendId, name, count);
      return count;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(final TreeName name)
    {
      final Cursor<ByteString, ByteString> cursor = txn.openCursor(name);
      logger.trace("Storage@%s.ReadableTransaction@%s.openCursor(%s, %s)",
          storageId(), id(), backendId, name);
      return cursor;
    }

    @Override
    public ByteString read(final TreeName name, final ByteSequence key)
    {
      final ByteString value = txn.read(name, key);
      logger.trace("Storage@%s.ReadableTransaction@%s.read(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), hex(value));
      return value;
    }

    private int id()
    {
      return System.identityHashCode(this);
    }
  }

  /** Decorates an {@link WriteableTransaction} with additional trace logging. */
  private final class TracedWriteableTransaction implements WriteableTransaction
  {
    private final WriteableTransaction txn;

    private TracedWriteableTransaction(final WriteableTransaction txn)
    {
      this.txn = txn;
    }

    @Override
    public void put(final TreeName name, final ByteSequence key, final ByteSequence value)
    {
      txn.put(name, key, value);
      logger.trace("Storage@%s.WriteableTransaction@%s.create(%s, %s, %s, %s)",
          storageId(), id(), backendId, name, hex(key), hex(value));
    }

    @Override
    public boolean delete(final TreeName name, final ByteSequence key)
    {
      final boolean isDeleted = txn.delete(name, key);
      logger.trace("Storage@%s.WriteableTransaction@%s.delete(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), isDeleted);
      return isDeleted;
    }

    @Override
    public void deleteTree(final TreeName name)
    {
      txn.deleteTree(name);
      logger.trace("Storage@%s.WriteableTransaction@%s.deleteTree(%s, %s)",
          storageId(), id(), backendId, name);
    }

    @Override
    public long getRecordCount(TreeName name)
    {
      final long count = txn.getRecordCount(name);
      logger.trace("Storage@%s.WriteableTransaction@%s.getRecordCount(%s, %s) = %d",
          storageId(), id(), backendId, name, count);
      return count;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(final TreeName name)
    {
      final Cursor<ByteString, ByteString> cursor = txn.openCursor(name);
      logger.trace("Storage@%s.WriteableTransaction@%s.openCursor(%s, %s)",
          storageId(), id(), backendId, name);
      return cursor;
    }

    @Override
    public void openTree(final TreeName name)
    {
      txn.openTree(name);
      logger.trace("Storage@%s.WriteableTransaction@%s.openTree(%s, %s)",
          storageId(), id(), backendId, name);
    }

    @Override
    public ByteString read(final TreeName name, final ByteSequence key)
    {
      final ByteString value = txn.read(name, key);
      logger.trace("Storage@%s.WriteableTransaction@%s.read(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), hex(value));
      return value;
    }

    @Override
    public void renameTree(final TreeName oldName, final TreeName newName)
    {
      txn.renameTree(oldName, newName);
      logger.trace("Storage@%s.WriteableTransaction@%s.renameTree(%s, %s, %s)",
          storageId(), id(), backendId, oldName, newName);
    }

    @Override
    public boolean update(final TreeName name, final ByteSequence key, final UpdateFunction f)
    {
      final boolean isUpdated = txn.update(name, key, f);
      logger.trace("Storage@%s.WriteableTransaction@%s.update(%s, %s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), f, isUpdated);
      return isUpdated;
    }

    private int id()
    {
      return System.identityHashCode(this);
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final String backendId;
  private final Storage storage;

  TracedStorage(final Storage storage, final String backendId)
  {
    this.storage = storage;
    this.backendId = backendId;
  }

  @Override
  public void close()
  {
    storage.close();
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.close(%s)", storageId(), backendId);
    }
  }

  @Override
  public StorageStatus getStorageStatus()
  {
    return storage.getStorageStatus();
  }

  @Override
  public void open(AccessMode accessMode) throws Exception
  {
    storage.open(accessMode);
    if (logger.isTraceEnabled())
    {
      logger
          .trace("Storage@%s.open(accessMode=%s) - Opened storage for backend %s", storageId(), accessMode, backendId);
    }
  }

  @Override
  public <T> T read(final ReadOperation<T> readOperation) throws Exception
  {
    ReadOperation<T> op = readOperation;
    if (logger.isTraceEnabled())
    {
      op = new ReadOperation<T>()
      {
        @Override
        public T run(final ReadableTransaction txn) throws Exception
        {
          return readOperation.run(new TracedReadableTransaction(txn));
        }
      };
    }
    return storage.read(op);
  }

  @Override
  public void removeStorageFiles() throws StorageRuntimeException
  {
    storage.removeStorageFiles();
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.removeStorageFiles(%s)", storageId(), backendId);
    }
  }

  @Override
  public Importer startImport() throws ConfigException, StorageRuntimeException
  {
    final Importer importer = storage.startImport();
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.startImport(%s)", storageId(), backendId);
      return new TracedImporter(importer);
    }
    return importer;
  }

  @Override
  public boolean supportsBackupAndRestore()
  {
    return storage.supportsBackupAndRestore();
  }

  @Override
  public void write(final WriteOperation writeOperation) throws Exception
  {
    WriteOperation op = writeOperation;
    if (logger.isTraceEnabled())
    {
      op = new WriteOperation()
      {
        @Override
        public void run(final WriteableTransaction txn) throws Exception
        {
          writeOperation.run(new TracedWriteableTransaction(txn));
        }
      };
    }
    storage.write(op);
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    storage.createBackup(backupConfig);
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.createBackup(%s)", storageId(), backendId);
    }
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    storage.removeBackup(backupDirectory, backupID);
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.removeBackup(%s, %s)", storageId(), backupID, backendId);
    }
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    storage.restoreBackup(restoreConfig);
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.restoreBackup(%s)", storageId(), backendId);
    }
  }

  @Override
  public Set<TreeName> listTrees()
  {
    final Set<TreeName> results = storage.listTrees();
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.listTrees() = ", storageId(), results);
    }
    return results;
  }

  private String hex(final ByteSequence bytes)
  {
    return bytes != null ? bytes.toByteString().toHexString() : null;
  }

  private int storageId()
  {
    return System.identityHashCode(this);
  }
}
