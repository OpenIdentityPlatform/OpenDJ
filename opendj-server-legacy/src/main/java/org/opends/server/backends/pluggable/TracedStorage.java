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
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import java.util.NoSuchElementException;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
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
final class TracedStorage implements Storage
{
  private void appendKeyValue(final StringBuilder builder, final Object key, final Object value)
  {
    builder.append("\"").append(key).append("\":\"").append(value).append("\"");
  }

  /**
   * Decorates {@link Cursor} with trace logging. Navigational methods will perform enter/leave logging in order to
   * help detect deadlocks.
   */
  private class TracedCursor implements Cursor<ByteString, ByteString>
  {
    private final Cursor<ByteString, ByteString> cursor;

    private TracedCursor(final Cursor<ByteString, ByteString> cursor)
    {
      this.cursor = cursor;
    }

    private void traceEnter(String method, Object... args)
    {
      trace("Cursor", id(), method + "#enter", args);
    }

    private void traceLeave(String method, Object... args)
    {
      trace("Cursor", id(), method + "#leave", args);
    }

    @Override
    public boolean positionToKey(final ByteSequence key)
    {
      traceEnter("positionToKey", "key", hex(key));
      boolean found = cursor.positionToKey(key);
      traceLeave("positionToKey", "key", hex(key), "found", found);
      return found;
    }

    @Override
    public boolean positionToKeyOrNext(final ByteSequence key)
    {
      traceEnter("positionToKeyOrNext", "key", hex(key));
      boolean found = cursor.positionToKeyOrNext(key);
      traceLeave("positionToKeyOrNext", "key", hex(key), "found", found);
      return found;
    }

    @Override
    public boolean positionToLastKey()
    {
      traceEnter("positionToLastKey");
      boolean found = cursor.positionToLastKey();
      traceLeave("positionToLastKey", "found", found);
      return found;
    }

    @Override
    public boolean positionToIndex(final int index)
    {
      traceEnter("positionToIndex", "index", index);
      boolean found = cursor.positionToIndex(index);
      traceLeave("positionToIndex", "index", index, "found", found);
      return found;
    }

    @Override
    public boolean next()
    {
      traceEnter("next");
      boolean found = cursor.next();
      traceLeave("next", "found", found);
      return found;
    }

    @Override
    public void delete()
    {
      traceEnter("delete");
      cursor.delete();
      traceLeave("delete");
    }

    @Override
    public boolean isDefined()
    {
      traceEnter("isDefined");
      boolean isDefined = cursor.isDefined();
      traceLeave("isDefined", "isDefined", isDefined);
      return isDefined;
    }

    @Override
    public ByteString getKey() throws NoSuchElementException
    {
      traceEnter("getKey");
      try
      {
        ByteString key = cursor.getKey();
        traceLeave("getKey", "key", hex(key));
        return key;
      }
      catch (NoSuchElementException e)
      {
        traceLeave("getKey", "NoSuchElementException", e.getMessage());
        throw e;
      }
    }

    @Override
    public ByteString getValue() throws NoSuchElementException
    {
      traceEnter("getValue");
      try
      {
        ByteString value = cursor.getValue();
        traceLeave("getValue", "value", hex(value));
        return value;
      }
      catch (NoSuchElementException e)
      {
        traceLeave("getValue", "NoSuchElementException", e.getMessage());
        throw e;
      }
    }

    @Override
    public void close()
    {
      traceEnter("close");
      cursor.close();
      traceLeave("close");
    }

    private int id()
    {
      return System.identityHashCode(this);
    }
  }

  /** Decorates an {@link Importer} with additional trace logging. */
  private final class TracedImporter implements Importer
  {
    private final Importer importer;

    private TracedImporter(final Importer importer)
    {
      this.importer = importer;
    }

    private void traceEnter(String method, Object... args)
    {
      trace("Importer", id(), method + "#enter", args);
    }

    private void traceLeave(String method, Object... args)
    {
      trace("Importer", id(), method + "#leave", args);
    }

    @Override
    public void clearTree(final TreeName name)
    {
      traceEnter("clearTree", "name", name);
      importer.clearTree(name);
      traceLeave("clearTree", "name", name);
    }

    @Override
    public void put(final TreeName name, final ByteSequence key, final ByteSequence value)
    {
      traceEnter("put", "name", name, "key", hex(key), "value", hex(value));
      importer.put(name, key, value);
      traceLeave("put", "name", name, "key", hex(key), "value", hex(value));
    }

    @Override
    public ByteString read(TreeName name, ByteSequence key)
    {
      traceEnter("read", "name", name, "key", hex(key));
      final ByteString value = importer.read(name, key);
      traceLeave("read", "name", name, "key", hex(key), "value", hex(value));
      return value;
    }

    @Override
    public void close()
    {
      traceEnter("close");
      importer.close();
      traceLeave("close");
    }

    private int id()
    {
      return System.identityHashCode(this);
    }

    @Override
    public SequentialCursor<ByteString, ByteString> openCursor(TreeName name)
    {
      traceEnter("openCursor", "name", name);
      SequentialCursor<ByteString, ByteString> cursor = importer.openCursor(name);
      traceLeave("openCursor", "name", name);
      return cursor;
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

    private void traceEnter(String method, Object... args)
    {
      trace("ReadableTransaction", id(), method + "#enter", args);
    }

    private void traceLeave(String method, Object... args)
    {
      trace("ReadableTransaction", id(), method + "#leave", args);
    }

    @Override
    public long getRecordCount(TreeName name)
    {
      traceEnter("getRecordCount", "name", name);
      final long count = txn.getRecordCount(name);
      traceLeave("getRecordCount", "name", name, "count", count);
      return count;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(final TreeName name)
    {
      traceEnter("openCursor", "name", name);
      final Cursor<ByteString, ByteString> cursor = txn.openCursor(name);
      traceLeave("openCursor", "name", name);
      return new TracedCursor(cursor);
    }

    @Override
    public ByteString read(final TreeName name, final ByteSequence key)
    {
      traceEnter("read", "name", name, "key", hex(key));
      final ByteString value = txn.read(name, key);
      traceLeave("read", "name", name, "key", hex(key), "value", hex(value));
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

    private void traceEnter(String method, Object... args)
    {
      trace("WriteableTransaction", id(), method + "#enter", args);
    }

    private void traceLeave(String method, Object... args)
    {
      trace("WriteableTransaction", id(), method + "#leave", args);
    }

    @Override
    public void put(final TreeName name, final ByteSequence key, final ByteSequence value)
    {
      traceEnter("put", "name", name, "key", hex(key), "value", hex(value));
      txn.put(name, key, value);
      traceLeave("put", "name", name, "key", hex(key), "value", hex(value));
    }

    @Override
    public boolean delete(final TreeName name, final ByteSequence key)
    {
      traceEnter("delete", "name", name, "key", hex(key));
      final boolean isDeleted = txn.delete(name, key);
      traceLeave("delete", "name", name, "key", hex(key), "isDeleted", isDeleted);
      return isDeleted;
    }

    @Override
    public void deleteTree(final TreeName name)
    {
      traceEnter("deleteTree", "name", name);
      txn.deleteTree(name);
      traceLeave("deleteTree", "name", name);
    }

    @Override
    public long getRecordCount(TreeName name)
    {
      traceEnter("getRecordCount", "name", name);
      final long count = txn.getRecordCount(name);
      traceLeave("getRecordCount", "name", name, "count", count);
      return count;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(final TreeName name)
    {
      traceEnter("openCursor", "name", name);
      final Cursor<ByteString, ByteString> cursor = txn.openCursor(name);
      traceLeave("openCursor", "name", name);
      return new TracedCursor(cursor);
    }

    @Override
    public void openTree(final TreeName name, boolean createOnDemand)
    {
      traceEnter("openTree", "name", name, "createOnDemand", createOnDemand);
      txn.openTree(name, createOnDemand);
      traceLeave("openTree", "name", name, "createOnDemand", createOnDemand);
    }

    @Override
    public ByteString read(final TreeName name, final ByteSequence key)
    {
      traceEnter("read", "name", name, "key", hex(key));
      final ByteString value = txn.read(name, key);
      traceLeave("read", "name", name, "key", hex(key), "value", hex(value));
      return value;
    }

    @Override
    public boolean update(final TreeName name, final ByteSequence key, final UpdateFunction f)
    {
      traceEnter("update", "name", name, "key", hex(key));
      final boolean isUpdated = txn.update(name, key, new UpdateFunction() {
        @Override
        public ByteSequence computeNewValue(final ByteSequence oldValue) {
          traceEnter("updateFunction", "oldValue", hex(oldValue));
          ByteSequence newValue = f.computeNewValue(oldValue);
          traceLeave("updateFunction", "oldValue", hex(oldValue), "newValue", hex(newValue));
          return newValue;
        }
      });
      traceLeave("update", "name", name, "key", hex(key), "isUpdated", isUpdated);
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

  private void trace(String type, int id, String method, Object... args)
  {
    StringBuilder builder = new StringBuilder();
    // Include a "!" in order to facilitate filtering using commands like "cut".
    builder.append("!{");
    appendKeyValue(builder, "backend", backendId);
    builder.append(",");
    appendKeyValue(builder, "storage", storageId());
    builder.append(",");
    appendKeyValue(builder, "type", type);
    builder.append(",");
    appendKeyValue(builder, "id", id);
    builder.append(",");
    appendKeyValue(builder, "method", method);
    for (int i = 0; i < args.length; i += 2)
    {
      builder.append(",");
      appendKeyValue(builder, args[i], args[i + 1]);
    }
    builder.append("}");
    logger.trace(builder.toString());
  }

  private void traceEnter(String method, Object... args)
  {
    if (logger.isTraceEnabled())
    {
      trace("Storage", storageId(), method + "#enter", args);
    }
  }

  private void traceLeave(String method, Object... args)
  {
    if (logger.isTraceEnabled())
    {
      trace("Storage", storageId(), method + "#leave", args);
    }
  }


  @Override
  public void close()
  {
    traceEnter("close");
    storage.close();
    traceLeave("close");
  }

  @Override
  public StorageStatus getStorageStatus()
  {
    return storage.getStorageStatus();
  }

  @Override
  public void open(AccessMode accessMode) throws Exception
  {
    traceEnter("open", "accessMode", accessMode);
    storage.open(accessMode);
    traceLeave("open", "accessMode", accessMode);
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
    traceEnter("removeStorageFiles");
    storage.removeStorageFiles();
    traceLeave("removeStorageFiles");
  }

  @Override
  public Importer startImport() throws ConfigException, StorageRuntimeException
  {
    traceEnter("startImport");
    final Importer importer = storage.startImport();
    traceLeave("startImport");
    return logger.isTraceEnabled() ? new TracedImporter(importer) : importer;
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
    traceEnter("createBackup");
    storage.createBackup(backupConfig);
    traceLeave("createBackup");
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    traceEnter("removeBackup", "backupID", backupID);
    storage.removeBackup(backupDirectory, backupID);
    traceLeave("removeBackup", "backupID", backupID);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    traceEnter("restoreBackup");
    storage.restoreBackup(restoreConfig);
    traceLeave("restoreBackup");
  }

  @Override
  public Set<TreeName> listTrees()
  {
    traceEnter("listTrees");
    final Set<TreeName> results = storage.listTrees();
    traceLeave("listTrees", "trees", results);
    return results;
  }

  private static String hex(final ByteSequence bytes)
  {
    return bytes != null ? bytes.toByteString().toASCIIString() : null;
  }

  private int storageId()
  {
    return System.identityHashCode(this);
  }
}
