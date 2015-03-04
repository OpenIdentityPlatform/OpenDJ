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

import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;

/**
 * This class is a wrapper around the JE database object and provides basic
 * read and write methods for entries.
 */
abstract class DatabaseContainer implements Closeable
{
  /** The database entryContainer. */
  final EntryContainer entryContainer;

  /** The name of the database within the entryContainer. */
  private TreeName name;

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
    this.name = treeName;
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
    // FIXME: remove?
    txn.openTree(name);
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
    // FIXME: is this method needed?
    storage.closeTree(name);
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
    /*
     * FIXME: push down to storage. Some DBs have native support for this, e.g. using counted
     * B-Trees.
     */
    final Cursor cursor = txn.openCursor(name);
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
    return name.toString();
  }

  /**
   * Get the JE database name for this database container.
   *
   * @return JE database name for this database container.
   */
  final TreeName getName()
  {
    return name;
  }

  /**
   * Set the JE database name to use for this container.
   *
   * @param name The database name to use for this container.
   */
  final void setName(TreeName name)
  {
    this.name = name;
  }
}
