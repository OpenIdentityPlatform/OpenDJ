/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.*;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

/**
 * This class is a wrapper around the JE database object and provides basic
 * read and write methods for entries.
 */
public abstract class DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The database entryContainer.
   */
  protected EntryContainer entryContainer;

  /**
   * The JE database configuration.
   */
  protected DatabaseConfig dbConfig;

  /**
   * The name of the database within the entryContainer.
   */
  protected String name;

  /**
   * The reference to the JE Environment.
   */
  private Environment env;

  /**
   * A list of JE database handles opened through this database
   * container.
   */
  private CopyOnWriteArrayList<Database> databases;

  /**
   * A cached per-thread JE database handle.
   */
  private ThreadLocal<Database> threadLocalDatabase =
      new ThreadLocal<Database>();

  /**
   * Create a new DatabaseContainer object.
   *
   * @param name The name of the entry database.
   * @param env The JE Environment.
   * @param entryContainer The entryContainer of the entry database.
   * @throws DatabaseException if a JE database error occurs.
   */
  protected DatabaseContainer(String name, Environment env,
                              EntryContainer entryContainer)
      throws DatabaseException
  {
    this.env = env;
    this.entryContainer = entryContainer;
    this.databases = new CopyOnWriteArrayList<Database>();

    StringBuilder builder = new StringBuilder();
    buildDatabaseName(builder, name);
    this.name = builder.toString();
  }

  /**
   * Opens a JE database in this database container. If the provided
   * database configuration is transactional, a transaction will be
   * created and used to perform the open.
   * <p>
   * Note that a database can be opened multiple times and will result in
   * multiple unique handles to the database.  This is used for example to
   * give each server thread its own database handle to eliminate contention
   * that could occur on a single handle.
   *
   * @return A new JE database handle.
   * @throws DatabaseException If an error occurs while attempting to open the
   * database.
   */
  private Database openDatabase() throws DatabaseException
  {
    Database database;

    if (dbConfig.getTransactional())
    {
      // Open the database under a transaction.
      Transaction txn =
          entryContainer.beginTransaction();
      try
      {
        database = env.openDatabase(txn, name, dbConfig);
        if (debugEnabled())
        {
          TRACER.debugVerbose("JE database %s opened with %d records. txnid=%d",
                              database.getDatabaseName(), database.count(),
                              txn.getId());
        }
        entryContainer.transactionCommit(txn);
      }
      catch (DatabaseException e)
      {
        entryContainer.transactionAbort(txn);
        throw e;
      }
    }
    else
    {
      database = env.openDatabase(null, name, dbConfig);
      if (debugEnabled())
      {
        TRACER.debugVerbose("JE database %s opened with %d records. txnid=none",
                            database.getDatabaseName(), database.count());
      }
    }

    return database;
  }

  private Database getDatabase() throws DatabaseException
  {
    Database database = threadLocalDatabase.get();
    if (database == null)
    {
      database = openDatabase();
      databases.add(database);
      threadLocalDatabase.set(database);
    }
    return database;
  }

  /**
   * Open the database container.
   *
   * @throws DatabaseException if a JE database error occurs while
   * openning the index.
   */
  public void open() throws DatabaseException
  {
    getDatabase();
  }

  /**
   * Constructs a full JE database name incorporating a entryContainer name.
   *
   * @param builder A string builder to which the full name will be appended.
   * @param name    The short database name.
   */
  private void buildDatabaseName(StringBuilder builder, String name)
  {
    builder.append(entryContainer.getContainerName());
    builder.append('_');
    builder.append(name);
  }

  /**
   * Flush any cached database information to disk and close the
   * database container.
   *
   * The database container should not be closed while other processes
   * aquired the container. The container should not be closed
   * while cursors handles into the database remain open, or
   * transactions that include operations on the database have not yet
   * been commited or aborted.
   *
   * The container may not be accessed again after this method is
   * called, regardless of the method's success or failure.
   *
   * @throws DatabaseException if an error occurs.
   */
  synchronized void close() throws DatabaseException
  {
    // Close each database handle that has been opened.
    for (Database database : databases)
    {
      if (database.getConfig().getDeferredWrite())
      {
        database.sync();
      }

      database.close();
    }

    if(debugEnabled())
    {
      TRACER.debugInfo("Closed database %s (%d handles)", name,
                       databases.size());
    }

    databases.clear();
    threadLocalDatabase = new ThreadLocal<Database>();
  }

  /**
   * Replace or insert a record into a JE database, with optional debug logging.
   * This is a simple wrapper around the JE Database.put method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  protected OperationStatus put(Transaction txn, DatabaseEntry key,
                                DatabaseEntry data)
      throws DatabaseException
  {
    Database database = getDatabase();
    OperationStatus status = database.put(txn, key, data);
    if (debugEnabled())
    {
      TRACER.debugJEAccess(DebugLogLevel.VERBOSE, status, database,
                           txn, key, data);
    }
    return status;
  }

  /**
   * Read a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.get method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @param data The record value returned as output. Its byte array does not
   * need to be initialized by the caller.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  protected OperationStatus read(Transaction txn,
                                 DatabaseEntry key, DatabaseEntry data,
                                 LockMode lockMode)
      throws DatabaseException
  {
    Database database = getDatabase();
    OperationStatus status = database.get(txn, key, data, lockMode);
    if (debugEnabled())
    {
      TRACER.debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn, key,
                           data);
    }
    return status;
  }

  /**
   * Insert a record into a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.putNoOverwrite method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  protected OperationStatus insert(Transaction txn,
                                   DatabaseEntry key, DatabaseEntry data)
      throws DatabaseException
  {
    Database database = getDatabase();
    OperationStatus status = database.putNoOverwrite(txn, key, data);
    if (debugEnabled())
    {
      TRACER.debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn, key,
                           data);
    }
    return status;
  }

  /**
   * Delete a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.delete method.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  protected OperationStatus delete(Transaction txn,
                                   DatabaseEntry key)
      throws DatabaseException
  {
    Database database = getDatabase();
    OperationStatus status = database.delete(txn, key);
    if (debugEnabled())
    {
      TRACER.debugJEAccess(DebugLogLevel.VERBOSE, status, database, txn,
                           key, null);
    }
    return status;
  }

  /**
   * Open a JE cursor on the DN database.
   * @param txn A JE database transaction to be used by the cursor,
   * or null if none.
   * @param cursorConfig The JE cursor configuration.
   * @return A JE cursor.
   * @throws DatabaseException If an error occurs while attempting to open
   * the cursor.
   */
  public Cursor openCursor(Transaction txn, CursorConfig cursorConfig)
       throws DatabaseException
  {
    Database database = getDatabase();
    return database.openCursor(txn, cursorConfig);
  }

  /**
   * Get the count of key/data pairs in the database in a JE database.
   * This is a simple wrapper around the JE Database.count method.
   * @return The count of key/data pairs in the database.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public long getRecordCount() throws DatabaseException
  {
    Database database = getDatabase();
    long count = database.count();
    if (debugEnabled())
    {
      TRACER.debugJEAccess(DebugLogLevel.VERBOSE, OperationStatus.SUCCESS,
                    database, null, null, null);
    }
    return count;
  }

  /**
   * Get a string representation of this object.
   * @return return A string representation of this object.
   */
  public String toString()
  {
    return name;
  }

  /**
   * Get the JE database name for this database container.
   *
   * @return JE database name for this database container.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Preload the database into cache.
   *
   * @param config The preload configuration.
   * @return Statistics about the preload process.
   * @throws DatabaseException If an JE database error occurs
   * during the preload.
   */
  public PreloadStats preload(PreloadConfig config)
      throws DatabaseException
  {
    return getDatabase().preload(config);
  }
}
