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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.io.Closeable;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * This class is a wrapper around the JE database object and provides basic
 * read and write methods for entries.
 */
abstract class DatabaseContainer implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The database entryContainer. */
  final EntryContainer entryContainer;
  /** The name of the database within the entryContainer. */
  TreeName treeName;

  /** The reference to the JE Storage. */
  final Storage storage;

  /**
   * Create a new DatabaseContainer object.
   *
   * @param treeName The name of the entry database.
   * @param storage The JE Storage.
   * @param entryContainer The entryContainer of the entry database.
   */
  DatabaseContainer(TreeName treeName, Storage storage, EntryContainer entryContainer)
  {
    this.storage = storage;
    this.entryContainer = entryContainer;
    this.treeName = treeName;
  }

  /**
   * Opens a JE database in this database container. If the provided
   * database configuration is transactional, a transaction will be
   * created and used to perform the open.
   *
   * @param txn The JE transaction handle, or null if none.
   * @throws StorageRuntimeException if a JE database error occurs while
   * opening the index.
   */
  void open(WriteableStorage txn) throws StorageRuntimeException
  {
    txn.openTree(treeName);
    if (logger.isTraceEnabled())
    {
      logger.trace("JE database %s opened. txn=%s", treeName, txn);
    }
  }

  /**
   * Flush any cached database information to disk and close the
   * database container.
   *
   * The database container should not be closed while other processes
   * acquired the container. The container should not be closed
   * while cursors handles into the database remain open, or
   * transactions that include operations on the database have not yet
   * been committed or aborted.
   *
   * The container may not be accessed again after this method is
   * called, regardless of the method's success or failure.
   *
   * @throws StorageRuntimeException if an error occurs.
   */
  @Override
  public synchronized void close() throws StorageRuntimeException
  {
    storage.closeTree(treeName);

    if(logger.isTraceEnabled())
    {
      logger.trace("Closed tree %s", treeName);
    }
  }

  /**
   * Replace or insert a record into a JE database, with optional debug logging.
   * This is a simple wrapper around the JE Database.put method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param value The record value.
   * @throws StorageRuntimeException If an error occurs in the JE operation.
   */
  void put(WriteableStorage txn, ByteSequence key, ByteSequence value) throws StorageRuntimeException
  {
    txn.create(treeName, key, value);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(true, treeName, txn, key, value));
    }
  }

  /**
   * Read a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.get method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @param isRMW whether the read operation is part of a larger read-modify-write operation
   * @return The operation status.
   * @throws StorageRuntimeException If an error occurs in the JE operation.
   */
  ByteString read(ReadableStorage txn, ByteSequence key, boolean isRMW) throws StorageRuntimeException
  {
    ByteString value = isRMW ? txn.getRMW(treeName, key) : txn.read(treeName, key);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(value != null, treeName, txn, key, value));
    }
    return value;
  }

  /**
   * Insert a record into a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.putNoOverwrite method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param value The record value.
   * @return {@code true} if the key-value mapping could be inserted,
   *         {@code false} if the key was already mapped to another value
   * @throws StorageRuntimeException If an error occurs in the JE operation.
   */
  boolean insert(WriteableStorage txn, ByteString key, ByteString value) throws StorageRuntimeException
  {
    boolean result = txn.putIfAbsent(treeName, key, value);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(result, treeName, txn, key, value));
    }
    return result;
  }

  /**
   * Delete a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.delete method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @return {@code true} if the key mapping was removed, {@code false} otherwise
   * @throws StorageRuntimeException If an error occurs in the JE operation.
   */
  boolean delete(WriteableStorage txn, ByteSequence key) throws StorageRuntimeException
  {
    final boolean result = txn.delete(treeName, key);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(result, treeName, txn, key, null));
    }
    return result;
  }

  /**
   * Open a JE cursor on the JE database.  This is a simple wrapper around
   * the JE Database.openCursor method.
   * @param txn A JE database transaction to be used by the cursor,
   * or null if none.
   * @return A JE cursor.
   * @throws StorageRuntimeException If an error occurs while attempting to open
   * the cursor.
   */
  final Cursor openCursor(ReadableStorage txn) throws StorageRuntimeException
  {
    return txn.openCursor(treeName);
  }

  /**
   * Get the count of key/data pairs in the database in a JE database.
   * This is a simple wrapper around the JE Database.count method.
   * @param txn The JE transaction handle, or null if none.
   * @return The count of key/data pairs in the database.
   * @throws StorageRuntimeException If an error occurs in the JE operation.
   */
  long getRecordCount(ReadableStorage txn) throws StorageRuntimeException
  {
    long count = count(txn);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(true, treeName, null, null, null));
    }
    return count;
  }

  private long count(ReadableStorage txn)
  {
    final Cursor cursor = txn.openCursor(treeName);
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

  /**
   * Get a string representation of this object.
   * @return return A string representation of this object.
   */
  @Override
  public String toString()
  {
    return treeName.toString();
  }

  /**
   * Get the JE database name for this database container.
   *
   * @return JE database name for this database container.
   */
  final TreeName getName()
  {
    return treeName;
  }

  /**
   * Set the JE database name to use for this container.
   *
   * @param name The database name to use for this container.
   */
  final void setName(TreeName name)
  {
    this.treeName = name;
  }

  /** Returns the message to log given the provided information. */
  private String messageToLog(boolean success, TreeName treeName, ReadableStorage txn, ByteSequence key,
      ByteSequence value)
  {
    StringBuilder builder = new StringBuilder();
    builder.append(" (");
    builder.append(success ? "SUCCESS" : "ERROR");
    builder.append(")");
    builder.append(" db=");
    builder.append(treeName);
    if (txn != null)
    {
      builder.append(" txn=");
      builder.append(txn);
    }
    else
    {
      builder.append(" txnid=none");
    }

    builder.append(ServerConstants.EOL);
    if (key != null)
    {
      builder.append("key:");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
    }

    // If the operation was successful we log the same common information
    // plus the data
    if (value != null)
    {
      builder.append("value(len=");
      builder.append(value.length());
      builder.append("):");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, value.toByteArray(), 4);
    }
    return builder.toString();
  }

}
