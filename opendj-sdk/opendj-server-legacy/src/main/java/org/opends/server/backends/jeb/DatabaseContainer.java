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
package org.opends.server.backends.jeb;

import java.io.Closeable;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

/**
 * This class is a wrapper around the JE database object and provides basic
 * read and write methods for entries.
 */
public abstract class DatabaseContainer implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The database entryContainer. */
  protected final EntryContainer entryContainer;
  /** The name of the database within the entryContainer. */
  protected String name;

  /** The JE database configuration. */
  protected DatabaseConfig dbConfig;
  /** The reference to the JE Environment. */
  private final Environment env;
  /** A JE database handle opened through this database container. */
  private Database database;

  /**
   * Create a new DatabaseContainer object.
   *
   * @param name The name of the entry database.
   * @param env The JE Environment.
   * @param entryContainer The entryContainer of the entry database.
   */
  protected DatabaseContainer(String name, Environment env, EntryContainer entryContainer)
  {
    this.env = env;
    this.entryContainer = entryContainer;
    this.name = name;
  }

  /**
   * Opens a JE database in this database container. If the provided
   * database configuration is transactional, a transaction will be
   * created and used to perform the open.
   *
   * @throws DatabaseException if a JE database error occurs while
   * opening the index.
   */
  public void open() throws DatabaseException
  {
    if (dbConfig.getTransactional())
    {
      // Open the database under a transaction.
      Transaction txn = entryContainer.beginTransaction();
      try
      {
        database = env.openDatabase(txn, name, dbConfig);
        if (logger.isTraceEnabled())
        {
          logger.trace("JE database %s opened. txnid=%d", database.getDatabaseName(), txn.getId());
        }
        EntryContainer.transactionCommit(txn);
      }
      catch (DatabaseException e)
      {
        EntryContainer.transactionAbort(txn);
        throw e;
      }
    }
    else
    {
      database = env.openDatabase(null, name, dbConfig);
      if (logger.isTraceEnabled())
      {
        logger.trace("JE database %s opened. txnid=none", database.getDatabaseName());
      }
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
   * @throws DatabaseException if an error occurs.
   */
  @Override
  public synchronized void close() throws DatabaseException
  {
    if(dbConfig.getDeferredWrite())
    {
      database.sync();
    }
    database.close();
    database = null;

    if(logger.isTraceEnabled())
    {
      logger.trace("Closed database %s", name);
    }
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
  OperationStatus put(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException
  {
    OperationStatus status = database.put(txn, key, data);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(status, database, txn, key, data));
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
  OperationStatus read(Transaction txn, DatabaseEntry key, DatabaseEntry data, LockMode lockMode)
      throws DatabaseException
  {
    OperationStatus status = database.get(txn, key, data, lockMode);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(status, database, txn, key, data));
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
  OperationStatus insert(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException
  {
    OperationStatus status = database.putNoOverwrite(txn, key, data);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(status, database, txn, key, data));
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
  OperationStatus delete(Transaction txn, DatabaseEntry key) throws DatabaseException
  {
    OperationStatus status = database.delete(txn, key);
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(status, database, txn, key, null));
    }
    return status;
  }

  /**
   * Open a JE cursor on the JE database.  This is a simple wrapper around
   * the JE Database.openCursor method.
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
    return database.openCursor(txn, cursorConfig);
  }

  /**
   * Open a JE disk ordered cursor on the JE database.  This is a
   * simple wrapper around the JE Database.openCursor method.
   * @param cursorConfig The JE disk ordered cursor configuration.
   * @return A JE disk ordered cursor.
   * @throws DatabaseException If an error occurs while attempting to open
   * the cursor.
   */
  public DiskOrderedCursor openCursor(DiskOrderedCursorConfig cursorConfig)
      throws DatabaseException
  {
    return database.openCursor(cursorConfig);
  }

  /**
   * Get the count of key/data pairs in the database in a JE database.
   * This is a simple wrapper around the JE Database.count method.
   * @return The count of key/data pairs in the database.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public long getRecordCount() throws DatabaseException
  {
    long count = database.count();
    if (logger.isTraceEnabled())
    {
      logger.trace(messageToLog(OperationStatus.SUCCESS, database, null, null, null));
    }
    return count;
  }

  /**
   * Get a string representation of this object.
   * @return return A string representation of this object.
   */
  @Override
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
    return database.preload(config);
  }

  /**
   * Set the JE database name to use for this container.
   *
   * @param name The database name to use for this container.
   */
  void setName(String name)
  {
    this.name = name;
  }

  /** Returns the message to log given the provided information. */
  private String messageToLog(OperationStatus status, Database database,
      Transaction txn, DatabaseEntry key, DatabaseEntry data)
  {
    StringBuilder builder = new StringBuilder();
    builder.append(" (");
    builder.append(status);
    builder.append(")");
    builder.append(" db=");
    try
    {
      builder.append(database.getDatabaseName());
    }
    catch (DatabaseException de)
    {
      builder.append(de);
    }
    if (txn != null)
    {
      builder.append(" txnid=");
      try
      {
        builder.append(txn.getId());
      }
      catch (DatabaseException de)
      {
        builder.append(de);
      }
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
      StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
    }

    // If the operation was successful we log the same common information
    // plus the data
    if (status == OperationStatus.SUCCESS && data != null)
    {
      builder.append("data(len=");
      builder.append(data.getSize());
      builder.append("):");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, data.getData(), 4);
    }
    return builder.toString();
  }

}
