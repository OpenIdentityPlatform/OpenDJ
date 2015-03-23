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

import java.io.File;
import java.io.FilenameFilter;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;

/**
 * Decorates a {@link Storage} with additional trace logging.
 */
final class TracedStorage implements Storage
{
  /**
   * Decorates an {@link Importer} with additional trace logging.
   */
  private final class TracedImporter implements Importer
  {
    private final Importer importer;

    private TracedImporter(final Importer importer)
    {
      this.importer = importer;
    }

    @Override
    public void close()
    {
      importer.close();
      logger.trace("Storage@%s.Importer@%s.close(%s)",
          storageId(), id(), backendId);
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

    private int id()
    {
      return System.identityHashCode(this);
    }
  }

  /**
   * Decorates an {@link ReadableStorage} with additional trace logging.
   */
  private final class TracedReadableStorage implements ReadableStorage
  {
    private final ReadableStorage txn;

    private TracedReadableStorage(final ReadableStorage txn)
    {
      this.txn = txn;
    }

    @Override
    public long getRecordCount(TreeName name)
    {
      final long count = txn.getRecordCount(name);
      logger.trace("Storage@%s.ReadableStorage@%s.getRecordCount(%s, %s) = %d",
          storageId(), id(), backendId, name, count);
      return count;
    }

    @Override
    public Cursor openCursor(final TreeName name)
    {
      final Cursor cursor = txn.openCursor(name);
      logger.trace("Storage@%s.ReadableStorage@%s.openCursor(%s, %s)",
          storageId(), id(), backendId, name);
      return cursor;
    }

    @Override
    public ByteString read(final TreeName name, final ByteSequence key)
    {
      final ByteString value = txn.read(name, key);
      logger.trace("Storage@%s.ReadableStorage@%s.read(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), hex(value));
      return value;
    }

    @Override
    public void close()
    {
      logger.trace("Storage@%s.ReadableStorage@%s.close()", storageId(), id());
    }

    private int id()
    {
      return System.identityHashCode(this);
    }
  }

  /**
   * Decorates an {@link WriteableStorage} with additional trace logging.
   */
  private final class TracedWriteableStorage implements WriteableStorage
  {
    private final WriteableStorage txn;

    private TracedWriteableStorage(final WriteableStorage txn)
    {
      this.txn = txn;
    }

    @Override
    public void put(final TreeName name, final ByteSequence key, final ByteSequence value)
    {
      txn.put(name, key, value);
      logger.trace("Storage@%s.WriteableStorage@%s.create(%s, %s, %s, %s)",
          storageId(), id(), backendId, name, hex(key), hex(value));
    }

    @Override
    public boolean delete(final TreeName name, final ByteSequence key)
    {
      final boolean isDeleted = txn.delete(name, key);
      logger.trace("Storage@%s.WriteableStorage@%s.delete(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), isDeleted);
      return isDeleted;
    }

    @Override
    public void deleteTree(final TreeName name)
    {
      txn.deleteTree(name);
      logger.trace("Storage@%s.WriteableStorage@%s.deleteTree(%s, %s)",
          storageId(), id(), backendId, name);
    }

    @Override
    public long getRecordCount(TreeName name)
    {
      final long count = txn.getRecordCount(name);
      logger.trace("Storage@%s.WriteableStorage@%s.getRecordCount(%s, %s) = %d",
          storageId(), id(), backendId, name, count);
      return count;
    }

    @Override
    public Cursor openCursor(final TreeName name)
    {
      final Cursor cursor = txn.openCursor(name);
      logger.trace("Storage@%s.WriteableStorage@%s.openCursor(%s, %s)",
          storageId(), id(), backendId, name);
      return cursor;
    }

    @Override
    public void openTree(final TreeName name)
    {
      txn.openTree(name);
      logger.trace("Storage@%s.WriteableStorage@%s.openTree(%s, %s)",
          storageId(), id(), backendId, name);
    }

    @Override
    public ByteString read(final TreeName name, final ByteSequence key)
    {
      final ByteString value = txn.read(name, key);
      logger.trace("Storage@%s.WriteableStorage@%s.read(%s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), hex(value));
      return value;
    }

    @Override
    public void renameTree(final TreeName oldName, final TreeName newName)
    {
      txn.renameTree(oldName, newName);
      logger.trace("Storage@%s.WriteableStorage@%s.renameTree(%s, %s, %s)",
          storageId(), id(), backendId, oldName, newName);
    }

    @Override
    public boolean update(final TreeName name, final ByteSequence key, final UpdateFunction f)
    {
      final boolean isUpdated = txn.update(name, key, f);
      logger.trace("Storage@%s.WriteableStorage@%s.update(%s, %s, %s, %s) = %s",
          storageId(), id(), backendId, name, hex(key), f, isUpdated);
      return isUpdated;
    }

    @Override
    public void close()
    {
      logger.trace("Storage@%s.WriteableStorage@%s.close()", storageId(), id());
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
  public File getDirectory()
  {
    return storage.getDirectory();
  }

  @Override
  public FilenameFilter getFilesToBackupFilter()
  {
    return storage.getFilesToBackupFilter();
  }

  @Override
  public StorageStatus getStorageStatus()
  {
    return storage.getStorageStatus();
  }

  @Override
  public void open() throws Exception
  {
    storage.open();
    if (logger.isTraceEnabled())
    {
      logger.trace("Storage@%s.open() - Opened storage for backend %s", storageId(), backendId);
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
        public T run(final ReadableStorage txn) throws Exception
        {
          return readOperation.run(new TracedReadableStorage(txn));
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
  public Importer startImport() throws Exception
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
        public void run(final WriteableStorage txn) throws Exception
        {
          writeOperation.run(new TracedWriteableStorage(txn));
        }
      };
    }
    storage.write(op);
  }

  @Override
  public WriteableStorage getWriteableStorage()
  {
    final WriteableStorage writeableStorage = storage.getWriteableStorage();
    if (logger.isTraceEnabled())
    {
      return new TracedWriteableStorage(writeableStorage);
    }
    return writeableStorage;
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
