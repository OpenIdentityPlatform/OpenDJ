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
 *      Portions Copyright 2006 - 2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.JebMessages.*;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import org.opends.server.types.Entry;

/**
 * Represents the database containing the LDAP entries. The database key is
 * the entry ID and the value is the entry contents.
 *
 */
public class ID2Entry
{
  /**
   * The database entryContainer.
   */
  private EntryContainer entryContainer;

  /**
   * The JE database configuration.
   */
  private DatabaseConfig dbConfig;

  /**
   * Parameters for compression and encryption.
   */
  private DataConfig dataConfig;

  /**
   * The name of the database within the entryContainer.
   */
  private String name;

  /**
   * A cached per-thread JE database handle.
   */
  private ThreadLocal<Database> threadLocalDatabase =
       new ThreadLocal<Database>();

  /**
   * Create a new ID2Entry object.
   * @param entryContainer The entryContainer of the entry database.
   * @param dbConfig The JE database configuration to be used to open the
   * underlying JE database.
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry database.
   * @param name The name of the entry database.
   */
  public ID2Entry(EntryContainer entryContainer, DatabaseConfig dbConfig,
                  DataConfig dataConfig, String name)
  {
    this.entryContainer = entryContainer;
    this.dbConfig = dbConfig;
    this.name = name;
    this.dataConfig = dataConfig;
  }

  /**
   * Open the entry database.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void open() throws DatabaseException
  {
    getDatabase();
  }

  /**
   * Get a handle to the database. It returns a per-thread handle to avoid
   * any thread contention on the database handle. The entryContainer is
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
      database = entryContainer.openDatabase(dbConfig, name);
      threadLocalDatabase.set(database);
    }
    return database;
  }

  /**
   * Convert an entry to its database format.
   *
   * @param entry The LDAP entry to be converted.
   * @return The database entry.
   */
  private DatabaseEntry entryData(Entry entry)
  {
    byte[] entryBytes;
    entryBytes = JebFormat.entryToDatabase(entry, dataConfig);
    return new DatabaseEntry(entryBytes);
  }

  /**
   * Insert a record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was inserted, false if a record with that
   *         ID already existed.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean insert(Transaction txn, EntryID id, Entry entry)
       throws DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = entryData(entry);

    OperationStatus status;
    status = EntryContainer.insert(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a record in the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean put(Transaction txn, EntryID id, Entry entry)
       throws DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = entryData(entry);

    OperationStatus status;
    status = EntryContainer.put(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a pre-formatted record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param key The key containing a pre-formatted entry ID.
   * @param data The data value containing a pre-formatted LDAP entry.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean putRaw(Transaction txn, DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status;
    status = EntryContainer.put(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @return true if the entry was removed, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean remove(Transaction txn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();

    OperationStatus status = EntryContainer.delete(getDatabase(), txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Fetch a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The desired entry ID which forms the key.
   * @return The requested entry, or null if there is no such record.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public Entry get(Transaction txn, EntryID id)
       throws JebException, DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = EntryContainer.read(getDatabase(), txn, key, data,
                                 LockMode.DEFAULT);

    if (status != OperationStatus.SUCCESS)
    {
      return null;
    }

    byte[] entryBytes = data.getData();
    byte entryVersion = JebFormat.getEntryVersion(entryBytes);

    //Try to decode the entry based on the version number. On later versions,
    //a case could be written to upgrade entries if it is not the current
    //version
    switch(entryVersion)
    {
      case JebFormat.FORMAT_VERSION :
        try
        {
          return JebFormat.entryFromDatabase(entryBytes);
        }
        catch (Exception e)
        {
          int msgID = MSGID_JEB_ENTRY_DATABASE_CORRUPT;
          String message = getMessage(msgID, id.toString());
          throw new JebException(msgID, message);
        }
      //case 0x00                     :
      //  Call upgrade method? Call 0x00 decode method?
      default   :
        int msgID = MSGID_JEB_INCOMPATIBLE_ENTRY_VERSION;
        String message = getMessage(msgID, id.toString(), entryVersion);
        throw new JebException(msgID, message);
    }
  }

  /**
   * Open a database cursor on the entry database.
   *
   * @param txn The database transaction, or null if none.
   * @param cursorConfig The JE cursor configuration.
   * @return A new cursor.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public Cursor openCursor(Transaction txn, CursorConfig cursorConfig)
       throws DatabaseException
  {
    Database database = getDatabase();
    return database.openCursor(txn, cursorConfig);
  }

  /**
   * Get the count of the number of entries stored.
   *
   * @return The number of entries stored.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public long getRecordCount() throws DatabaseException
  {
    // Read the current count, if any.
    return EntryContainer.count(getDatabase());
  }

}
