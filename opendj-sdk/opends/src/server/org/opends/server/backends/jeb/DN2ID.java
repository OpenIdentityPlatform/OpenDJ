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

import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;

import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;

/**
 * This class represents the DN database, or dn2id, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
public class DN2ID
{
  /**
   * The database container.
   */
  private Container container;

  /**
   * The JE database configuration.
   */
  private DatabaseConfig dbConfig;

  /**
   * The name of the database within the container.
   */
  private String name;

  /**
   * A cached per-thread JE database handle.
   */
  private ThreadLocal<Database> threadLocalDatabase =
       new ThreadLocal<Database>();

  /**
   * Create a DN2ID instance for the DN database in a given container.
   *
   * @param container The container of the DN database.
   * @param dbConfig The JE database configuration which will be used to
   * open the database.
   * @param name The name of the DN database ("dn2id").
   */
  public DN2ID(Container container, DatabaseConfig dbConfig, String name)
  {
    this.container = container;
    this.dbConfig = dbConfig;
    this.name = name;
  }

  /**
   * Open the DN database.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void open() throws DatabaseException
  {
    getDatabase();
  }

  /**
   * Get a handle to the database. It returns a per-thread handle to avoid
   * any thread contention on the database handle. The container is
   * responsible for closing all handles.
   *
   * @return A database handle.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private Database getDatabase() throws DatabaseException
  {
    Database database = threadLocalDatabase.get();
    if (database == null)
    {
      database = container.openDatabase(dbConfig, name);
      threadLocalDatabase.set(database);
    }
    return database;
  }

  /**
   * Create a DN database key from an entry DN.
   * @param dn The entry DN.
   * @return A DatabaseEntry containing the key.
   */
  private static DatabaseEntry DNdata(DN dn)
  {
    byte[] normDN = StaticUtils.getBytes(dn.toNormalizedString());
    return new DatabaseEntry(normDN);
  }

  /**
   * Insert a new record into the DN database.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @return true if the record was inserted, false if a record with that key
   * already exists.
   * @throws DatabaseException If an error occurred while attempting to insert
   * the new record.
   */
  public boolean insert(Transaction txn, DN dn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);
    DatabaseEntry data = id.getDatabaseEntry();

    OperationStatus status;

    status = Container.insert(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a record to the DN database.  If a record with the given key already
   * exists, the record will be replaced, otherwise a new record will be
   * inserted.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @return true if the record was written, false if it was not written.
   * @throws DatabaseException If an error occurred while attempting to write
   * the record.
   */
  public boolean put(Transaction txn, DN dn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);
    DatabaseEntry data = id.getDatabaseEntry();

    OperationStatus status;
    status = Container.put(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a record to the DN database, where the key and value are already
   * formatted.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param key A DatabaseEntry containing the record key.
   * @param data A DatabaseEntry containing the record value.
   * @return true if the record was written, false if it was not written.
   * @throws DatabaseException If an error occurred while attempting to write
   * the record.
   */
  public boolean putRaw(Transaction txn, DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status;
    status = Container.put(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Remove a record from the DN database.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @return true if the record was removed, false if it was not removed.
   * @throws DatabaseException If an error occurred while attempting to remove
   * the record.
   */
  public boolean remove(Transaction txn, DN dn)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);

    OperationStatus status = Container.delete(getDatabase(), txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Fetch the entry ID for a given DN.
   * @param txn A JE database transaction to be used for the database read, or
   * null if none is required.
   * @param dn The DN for which the entry ID is desired.
   * @return The entry ID, or null if the given DN is not in the DN database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public EntryID get(Transaction txn, DN dn)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = Container.read(getDatabase(), txn, key, data, LockMode.DEFAULT);
    if (status != OperationStatus.SUCCESS)
    {
      return null;
    }
    return new EntryID(data);
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
    return getDatabase().openCursor(txn, cursorConfig);
  }

  /**
   * Compute the count of the number of records stored. This can be a
   * long running computation as it must walk the entire btree.
   *
   * @return The number of records stored.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public long computeRecordCount() throws DatabaseException
  {
    BtreeStats stats = (BtreeStats)getDatabase().getStats(new StatsConfig());
    return stats.getLeafNodeCount();
  }

}
