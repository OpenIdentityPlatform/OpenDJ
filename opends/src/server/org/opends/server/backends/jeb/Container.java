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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import static org.opends.server.types.DebugLogCategory.*;
import static org.opends.server.types.DebugLogSeverity.VERBOSE;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

import org.opends.server.loggers.Debug;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;

import java.util.List;
import java.util.ArrayList;

/**
 * A container is a logical portion of a JE environment.  Each base DN of a
 * JE backend is given its own container.  Multiple containers are implemented
 * by prefixing each JE database name with the container name, which is derived
 * from the base DN.  Static methods of this class are wrappers around the
 * JE database methods to add Directory Server debug logging for database
 * access.
 */
public class Container
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.Container";

  /**
   * The JE database environment.
   */
  private Environment env;

  /**
   * The container name, which may be null.
   */
  private String containerName;

  /**
   * A list of JE database handles opened through this container.
   * They will be closed by the container.
   */
  private ArrayList<Database> databases;

  /**
   * A list of JE cursor handles registered with this container.
   * They will be closed by the container.
   */
  private ArrayList<Cursor> cursors;

  /**
   * Creates a new container object.  It does not actually create anything
   * in the JE database environment.
   *
   * @param env           The JE database environment in which the container
   * resides.
   * @param containerName The container name, which may be null.
   */
  public Container(Environment env, String containerName)
  {
    this.env = env;
    this.containerName = containerName;
  }

  /**
   * Open the container.
   */
  public void open()
  {
    databases = new ArrayList<Database>();
    cursors = new ArrayList<Cursor>();
  }

  /**
   * Close the container.
   *
   * @throws DatabaseException If an error occurs while attempting to close
   * the container.
   */
  public void close() throws DatabaseException
  {
    // Close each cursor that has been registered.
    for (Cursor cursor : cursors)
    {
      cursor.close();
    }

    // Close each database handle that has been opened.
    for (Database database : databases)
    {
      if (database.getConfig().getDeferredWrite())
      {
        database.sync();
      }

      database.close();
    }
  }

  /**
   * Constructs a full JE database name incorporating a container name.
   *
   * @param builder A string builder to which the full name will be appended.
   * @param name    The short database name.
   */
  private void buildDatabaseName(StringBuilder builder, String name)
  {
    if (containerName != null)
    {
      builder.append(containerName);
      builder.append('_');
    }
    builder.append(name);
  }

  /**
   * Opens a JE database in this container. The resulting database handle
   * must not be closed by the caller, as it will be closed by the container.
   * If the provided database configuration is transactional, a transaction will
   * be created and used to perform the open.
   * <p>
   * Note that a database can be opened multiple times and will result in
   * multiple unique handles to the database.  This is used for example to
   * give each server thread its own database handle to eliminate contention
   * that could occur on a single handle.
   *
   * @param dbConfig The JE database configuration to be used to open the
   * database.
   * @param name     The short database name, to which the container name will
   * be added.
   * @return A new JE database handle.
   * @throws DatabaseException If an error occurs while attempting to open the
   * database.
   */
  public synchronized Database openDatabase(DatabaseConfig dbConfig,
                                            String name)
       throws DatabaseException
  {
    Database database;

    StringBuilder builder = new StringBuilder();
    buildDatabaseName(builder, name);
    String fullName = builder.toString();

    if (dbConfig.getTransactional())
    {
      // Open the database under a transaction.
      Transaction txn = beginTransaction();
      try
      {
        database = env.openDatabase(txn, fullName, dbConfig);
        assert Debug.debugMessage(DATABASE_ACCESS, VERBOSE, CLASS_NAME,
                                  "openDatabase",
                                  "open db=" + database.getDatabaseName() +
                                  " txnid=" + txn.getId());
        transactionCommit(txn);
      }
      catch (DatabaseException e)
      {
        transactionAbort(txn);
        throw e;
      }
    }
    else
    {
      database = env.openDatabase(null, fullName, dbConfig);
      assert Debug.debugMessage(DATABASE_ACCESS, VERBOSE, CLASS_NAME,
                                "openDatabase",
                                "open db=" + database.getDatabaseName() +
                                " txnid=none");
    }

    // Insert into the list of database handles.
    databases.add(database);

    return database;
  }

  /**
   * Register a cursor with the container. The container will then take care of
   * closing the cursor when the container is closed.
   * @param cursor A cursor to one of the databases in the container.
   */
  public synchronized void addCursor(Cursor cursor)
  {
    cursors.add(cursor);
  }

  /**
   * Begin a leaf transaction using the default configuration.
   * Provides assertion debug logging.
   * @return A JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to begin
   * a new transaction.
   */
  public Transaction beginTransaction()
       throws DatabaseException
  {
    Transaction parentTxn = null;
    TransactionConfig txnConfig = null;
    Transaction txn = env.beginTransaction(parentTxn, txnConfig);
    assert Debug.debugMessage(DATABASE_ACCESS, VERBOSE, CLASS_NAME,
                       "beginTransaction", "begin txnid=" + txn.getId());
    return txn;
  }

  /**
   * Commit a transaction.
   * Provides assertion debug logging.
   * @param txn The JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to commit
   * the transaction.
   */
  public static void transactionCommit(Transaction txn)
       throws DatabaseException
  {
    if (txn != null)
    {
      txn.commit();
      assert Debug.debugMessage(DATABASE_ACCESS, VERBOSE, CLASS_NAME,
                                "transactionCommit", "commit txnid=" +
                                                     txn.getId());
    }
  }

  /**
   * Abort a transaction.
   * Provides assertion debug logging.
   * @param txn The JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to abort the
   * transaction.
   */
  public static void transactionAbort(Transaction txn)
       throws DatabaseException
  {
    if (txn != null)
    {
      txn.abort();
      assert Debug.debugMessage(DATABASE_ACCESS, VERBOSE, CLASS_NAME,
                                "transactionAbort", "abort txnid=" +
                                                    txn.getId());
    }
  }

  /**
   * Debug log a read or write access to the database.
   * @param operation The operation label: "read", "put", "insert".
   * @param category The log category for raw data value logging
   * @param status The JE return status code of the operation.
   * @param database The JE database handle operated on.
   * @param txn The JE transaction handle used in the operation.
   * @param key The database key operated on.
   * @param data The database value read or written.
   * @return true so that the method can be used in an assertion
   * @throws DatabaseException If an error occurs while retrieving information
   * about the JE objects provided to the method.
   */
  private static boolean debugAccess(String operation,
                             DebugLogCategory category,
                             OperationStatus status,
                             Database database,
                             Transaction txn,
                             DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    // Build the string that is common to category DATABASE_ACCESS and
    // DATABASE_READ/DATABASE_WRITE
    StringBuilder builder = new StringBuilder();
    builder.append(operation);
    if (status == OperationStatus.SUCCESS)
    {
      builder.append(" (ok)");
    }
    else
    {
      builder.append(" (");
      builder.append(status.toString());
      builder.append(")");
    }
    builder.append(" db=");
    builder.append(database.getDatabaseName());
    if (txn != null)
    {
      builder.append(" txnid=");
      builder.append(txn.getId());
    }
    else
    {
      builder.append(" txnid=none");
    }
    Debug.debugMessage(DATABASE_ACCESS, VERBOSE, CLASS_NAME,
                       "debugAccess", builder.toString());

    // If the operation was successful we log the same common information
    // plus the key and data under category DATABASE_READ or DATABASE_WRITE
    if (status == OperationStatus.SUCCESS)
    {
      builder.append(" key:");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 0);
      if (data != null)
      {
        builder.append("data(len=");
        builder.append(data.getSize());
        builder.append("):");
        builder.append(ServerConstants.EOL);
        StaticUtils.byteArrayToHexPlusAscii(builder, data.getData(), 0);
      }
      Debug.debugMessage(category, VERBOSE, CLASS_NAME,
                         "debugAccess", builder.toString());
/*
      if (category == DATABASE_WRITE)
      {
        System.out.println(builder.toString());
      }
*/
    }
    return true;
  }

  /**
   * Insert a record into a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.putNoOverwrite method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus insert(Database database, Transaction txn,
                                DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = database.putNoOverwrite(txn, key, data);
    assert debugAccess("insert", DATABASE_WRITE,
                       status, database, txn, key, data);
    return status;
  }

  /**
   * Insert a record into a JE database through a cursor, with optional debug
   * logging. This is a simple wrapper around the JE Cursor.putNoOverwrite
   * method.
   * @param cursor The JE cursor handle.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus cursorInsert(Cursor cursor,
                                             DatabaseEntry key,
                                             DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = cursor.putNoOverwrite(key, data);
    assert debugAccess("cursorInsert", DATABASE_WRITE,
                       status, cursor.getDatabase(), null, key, data);
    return status;
  }

  /**
   * Replace or insert a record into a JE database, with optional debug logging.
   * This is a simple wrapper around the JE Database.put method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus put(Database database, Transaction txn,
                                    DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = database.put(txn, key, data);
    assert debugAccess("put", DATABASE_WRITE,
                       status, database, txn, key, data);
    return status;
  }

  /**
   * Replace or insert a record into a JE database through a cursor, with
   * optional debug logging. This is a simple wrapper around the JE Cursor.put
   * method.
   * @param cursor The JE cursor handle.
   * @param key The record key.
   * @param data The record value.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus cursorPut(Cursor cursor,
                                          DatabaseEntry key,
                                          DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status = cursor.put(key, data);
    assert debugAccess("cursorPut", DATABASE_WRITE,
                       status, cursor.getDatabase(), null, key, data);
    return status;
  }

  /**
   * Read a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.get method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @param data The record value returned as output. Its byte array does not
   * need to be initialized by the caller.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus read(Database database, Transaction txn,
                              DatabaseEntry key, DatabaseEntry data,
                              LockMode lockMode)
       throws DatabaseException
  {
    OperationStatus status = database.get(txn, key, data, lockMode);
    assert debugAccess("read", DATABASE_READ,
                       status, database, txn, key, data);
    return status;
  }

  /**
   * Read a record from a JE database through a cursor, with optional debug
   * logging. This is a simple wrapper around the JE Cursor.getSearchKey method.
   * @param cursor The JE cursor handle.
   * @param key The key of the record to be read.
   * @param data The record value returned as output. Its byte array does not
   * need to be initialized by the caller.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus cursorRead(Cursor cursor,
                                           DatabaseEntry key,
                                           DatabaseEntry data,
                                           LockMode lockMode)
       throws DatabaseException
  {
    OperationStatus status = cursor.getSearchKey(key, data, lockMode);
    assert debugAccess("cursorRead", DATABASE_READ,
                       status, cursor.getDatabase(), null, key, data);
    return status;
  }

  /**
   * Delete a record from a JE database, with optional debug logging. This is a
   * simple wrapper around the JE Database.delete method.
   * @param database The JE database handle.
   * @param txn The JE transaction handle, or null if none.
   * @param key The key of the record to be read.
   * @return The operation status.
   * @throws DatabaseException If an error occurs in the JE operation.
   */
  public static OperationStatus delete(Database database, Transaction txn,
                                       DatabaseEntry key)
       throws DatabaseException
  {
    OperationStatus status = database.delete(txn, key);
    assert debugAccess("delete", DATABASE_WRITE,
                       status, database, txn, key, null);
    return status;
  }

  /**
   * Remove a database from disk.
   *
   * @param name The short database name, to which the container name will be
   * added.
   * @throws DatabaseException If an error occurs while attempting to delete the
   * database.
   */
  public void removeDatabase(String name) throws DatabaseException
  {
    StringBuilder builder = new StringBuilder();
    buildDatabaseName(builder, name);
    String fullName = builder.toString();
    env.removeDatabase(null, fullName);
  }

  /**
   * Remove from disk all the databases in this container.
   *
   * @throws DatabaseException If an error occurs while attempting to delete any
   * database.
   */
  public void removeAllDatabases() throws DatabaseException
  {
    StringBuilder builder = new StringBuilder();
    buildDatabaseName(builder, "");
    String prefix = builder.toString();
    List nameList = env.getDatabaseNames();
    if (nameList != null)
    {
      for (Object o : nameList)
      {
        String name = (String)o;
        if (name.startsWith(prefix))
        {
          env.removeDatabase(null, name);
        }
      }
    }
  }

  /**
   * Get the list of database handles opened by this container.  Note that
   * there can be multiple handles referring to the same database.
   * @return The list of open database handles.
   */
  public synchronized ArrayList<Database> getDatabaseList()
  {
    return new ArrayList<Database>(databases);
  }
}

