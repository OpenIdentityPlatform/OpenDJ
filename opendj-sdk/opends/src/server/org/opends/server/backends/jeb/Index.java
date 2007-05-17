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

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;
import static org.opends.server.loggers.debug.DebugLogger.debugVerbose;
import org.opends.server.types.DebugLogLevel;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import java.util.*;

/**
 * Represents an index implemented by a JE database in which each key maps to
 * a set of entry IDs.  The key is a byte array, and is constructed from some
 * normalized form of an attribute value (or fragment of a value) appearing
 * in the entry.
 */
public class Index
{


  /**
   * The database entryContainer holding this index database.
   */
  private EntryContainer entryContainer;

  /**
   * The JE database configuration.
   */
  private DatabaseConfig dbConfig;

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
   * The indexer object to construct index keys from LDAP attribute values.
   */
  public Indexer indexer;

  /**
   * The comparator for index keys.
   */
  private Comparator<byte[]> comparator;

  /**
   * The limit on the number of entry IDs that may be indexed by one key.
   */
  private int indexEntryLimit;

  /**
   * Limit on the number of entry IDs that may be retrieved by cursoring
   * through an index.
   */
  private int cursorEntryLimit;

  /**
   * Number of keys that have exceeded the entry limit since this
   * object was created.
   */
  private int entryLimitExceededCount;

  /**
   * Create a new index object.
   * @param entryContainer The database entryContainer holding this index.
   * @param name The name of the index database within the entryContainer.
   * @param indexer The indexer object to construct index keys from LDAP
   * attribute values.
   * @param indexEntryLimit The configured limit on the number of entry IDs
   * that may be indexed by one key.
   * @param cursorEntryLimit The configured limit on the number of entry IDs
   * that may be retrieved by cursoring through an index.
   */
  public Index(EntryContainer entryContainer, String name, Indexer indexer,
               int indexEntryLimit, int cursorEntryLimit)
  {
    this.entryContainer = entryContainer;
    this.name = name;
    this.indexer = indexer;
    this.comparator = indexer.getComparator();
    this.indexEntryLimit = indexEntryLimit;
    this.cursorEntryLimit = cursorEntryLimit;
  }

  /**
   * Open this index database.
   * @param dbConfig The requested JE database configuration.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void open(DatabaseConfig dbConfig) throws DatabaseException
  {
    this.dbConfig = dbConfig;
    this.dbConfig.setOverrideBtreeComparator(true);
    this.dbConfig.setBtreeComparator(comparator.getClass());
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
  public Database getDatabase() throws DatabaseException
  {
    Database database = threadLocalDatabase.get();
    if (database == null)
    {
      database = entryContainer.openDatabase(dbConfig, name);
      threadLocalDatabase.set(database);

      if(debugEnabled())
      {
        debugInfo("JE Index database %s opened with %d records.",
                  database.getDatabaseName(), database.count());
      }
    }
    return database;
  }

  /**
   * Insert an entry ID into the set of IDs indexed by a given key.
   *
   * @param txn A database transaction, or null if none is required.
   * @param key         The index key.
   * @param entryID     The entry ID.
   * @return True if the entry ID is inserted or ignored because the entry limit
   *         count is exceeded. False if it alreadly exists in the entry ID set
   *         for the given key.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean insertID(Transaction txn, DatabaseEntry key, EntryID entryID)
       throws DatabaseException
  {
    OperationStatus status;
    LockMode lockMode = LockMode.RMW;
    DatabaseEntry entryIDData = entryID.getDatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    boolean success = true;

    status = EntryContainer.read(getDatabase(), txn, key, data, lockMode);

    if (status == OperationStatus.SUCCESS)
    {
      EntryIDSet entryIDList =
           new EntryIDSet(key.getData(), data.getData());
      if (entryIDList.isDefined())
      {
        if (indexEntryLimit > 0 && entryIDList.size() >= indexEntryLimit)
        {
          entryIDList = new EntryIDSet();
          entryLimitExceededCount++;
        }
        else
        {
          if(!entryIDList.add(entryID))
          {
            success = false;
          }
        }

        byte[] after = entryIDList.toDatabase();
        data.setData(after);
        EntryContainer.put(getDatabase(), txn, key, data);
      }
    }
    else
    {
      EntryContainer.put(getDatabase(), txn, key, entryIDData);
    }

    return success;
  }

  /**
   * Remove an entry ID from the set of IDs indexed by a given key.
   *
   * @param txn A database transaction, or null if none is required.
   * @param key         The index key.
   * @param entryID     The entry ID.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void removeID(Transaction txn, DatabaseEntry key, EntryID entryID)
       throws DatabaseException
  {
    OperationStatus status;
    LockMode lockMode = LockMode.RMW;
    DatabaseEntry data = new DatabaseEntry();

    status = EntryContainer.read(getDatabase(), txn, key, data, lockMode);

    if (status == OperationStatus.SUCCESS)
    {
      EntryIDSet entryIDList = new EntryIDSet(key.getData(), data.getData());
      if (entryIDList.isDefined())
      {
        if (!entryIDList.remove(entryID))
        {
          // This shouldn't happen!
          // TODO notify the database needs to be checked
        }
        else
        {
          byte[] after = entryIDList.toDatabase();
          if (after == null)
          {
            // No more IDs, so remove the key
            EntryContainer.delete(getDatabase(), txn, key);
          }
          else
          {
            data.setData(after);
            EntryContainer.put(getDatabase(), txn, key, data);
          }
        }
      }
    }
    else
    {
      // This shouldn't happen!
      // TODO notify the database needs to be checked
    }
  }

  /**
   * Check if an entry ID is in the set of IDs indexed by a given key.
   *
   * @param txn A database transaction, or null if none is required.
   * @param key         The index key.
   * @param entryID     The entry ID.
   * @return true if the entry ID is indexed by the given key,
   *         false if it is not indexed by the given key,
   *         undefined if the key has exceeded the entry limit.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public ConditionResult containsID(Transaction txn, DatabaseEntry key,
                                    EntryID entryID)
       throws DatabaseException
  {
    OperationStatus status;
    LockMode lockMode = LockMode.DEFAULT;
    DatabaseEntry data = new DatabaseEntry();

    status = EntryContainer.read(getDatabase(), txn, key, data, lockMode);
    if (status == OperationStatus.SUCCESS)
    {
      EntryIDSet entryIDList =
           new EntryIDSet(key.getData(), data.getData());

      if (!entryIDList.isDefined())
      {
        return ConditionResult.UNDEFINED;
      }
      else if (entryIDList.contains(entryID))
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }
    else
    {
      return ConditionResult.FALSE;
    }
  }

  /**
   * Reads the set of entry IDs for a given key.
   *
   * @param key The database key.
   * @param txn A database transaction, or null if none is required.
   * @param lockMode The JE locking mode to be used for the database read.
   * @return The entry IDs indexed by this key.
   */
  public EntryIDSet readKey(DatabaseEntry key, Transaction txn,
                            LockMode lockMode)
  {
    try
    {
      OperationStatus status;
      DatabaseEntry data = new DatabaseEntry();
      status = EntryContainer.read(getDatabase(), txn, key, data, lockMode);
      if (status != OperationStatus.SUCCESS)
      {
        return new EntryIDSet(key.getData(), null);
      }
      return new EntryIDSet(key.getData(), data.getData());
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      return new EntryIDSet();
    }
  }

  /**
   * Writes the set of entry IDs for a given key.
   *
   * @param key The database key.
   * @param entryIDList The entry IDs indexed by this key.
   * @param txn A database transaction, or null if none is required.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void writeKey(Transaction txn, DatabaseEntry key,
                       EntryIDSet entryIDList)
       throws DatabaseException
  {
    DatabaseEntry data = new DatabaseEntry();
    byte[] after = entryIDList.toDatabase();
    if (after == null)
    {
      // No more IDs, so remove the key.
      EntryContainer.delete(getDatabase(), txn, key);
    }
    else
    {
      if (!entryIDList.isDefined())
      {
        entryLimitExceededCount++;
      }
      data.setData(after);
      EntryContainer.put(getDatabase(), txn, key, data);
    }
  }

  /**
   * Reads a range of keys and collects all their entry IDs into a
   * single set.
   *
   * @param lower The lower bound of the range. A 0 length byte array indicates
   *                      no lower bound and the range will start from the
   *                      smallest key.
   * @param upper The upper bound of the range. A 0 length byte array indicates
   *                      no upper bound and the range will end at the largest
   *                      key.
   * @param lowerIncluded true if a key exactly matching the lower bound
   *                      is included in the range, false if only keys
   *                      strictly greater than the lower bound are included.
   *                      This value is ignored if the lower bound is not
   *                      specified.
   * @param upperIncluded true if a key exactly matching the upper bound
   *                      is included in the range, false if only keys
   *                      strictly less than the upper bound are included.
   *                      This value is ignored if the upper bound is not
   *                      specified.
   * @return The set of entry IDs.
   */
  public EntryIDSet readRange(byte[] lower, byte[] upper,
                               boolean lowerIncluded, boolean upperIncluded)
  {
    LockMode lockMode = LockMode.DEFAULT;

    try
    {
      // Total number of IDs found so far.
      int totalIDCount = 0;

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key;

      ArrayList<EntryIDSet> lists = new ArrayList<EntryIDSet>();

      OperationStatus status;
      Cursor cursor;

      cursor = getDatabase().openCursor(null, CursorConfig.READ_COMMITTED);

      try
      {
        // Set the lower bound if necessary.
        if(lower.length > 0)
        {
          key = new DatabaseEntry(lower);

          // Initialize the cursor to the lower bound.
          status = cursor.getSearchKeyRange(key, data, lockMode);

          // Advance past the lower bound if necessary.
          if (status == OperationStatus.SUCCESS && !lowerIncluded &&
               comparator.compare(key.getData(), lower) == 0)
          {
            // Do not include the lower value.
            status = cursor.getNext(key, data, lockMode);
          }
        }
        else
        {
          key = new DatabaseEntry();
          status = cursor.getNext(key, data, lockMode);
        }

        if (status != OperationStatus.SUCCESS)
        {
          // There are no values.
          return new EntryIDSet(key.getData(), null);
        }

        // Step through the keys until we hit the upper bound or the last key.
        while (status == OperationStatus.SUCCESS)
        {
          // Check against the upper bound if necessary
          if(upper.length > 0)
          {
            int cmp = comparator.compare(key.getData(), upper);
            if ((cmp > 0) || (cmp == 0 && !upperIncluded))
            {
              break;
            }
          }
          EntryIDSet list = new EntryIDSet(key.getData(), data.getData());
          if (!list.isDefined())
          {
            // There is no point continuing.
            return list;
          }
          totalIDCount += list.size();
          if (cursorEntryLimit > 0 && totalIDCount > cursorEntryLimit)
          {
            // There are too many. Give up and return an undefined list.
            return new EntryIDSet();
          }
          lists.add(list);
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }

        return EntryIDSet.unionOfSets(lists, false);
      }
      finally
      {
        cursor.close();
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      return new EntryIDSet();
    }
  }

  /**
   * Get the number of keys that have exceeded the entry limit since this
   * object was created.
   * @return The number of keys that have exceeded the entry limit since this
   * object was created.
   */
  public int getEntryLimitExceededCount()
  {
    return entryLimitExceededCount;
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
   * Open a cursor to the JE database holding this index.
   * @param txn A JE database transaction to be associated with the cursor,
   * or null if none is required.
   * @param cursorConfig The requested JE cursor configuration.
   * @return A new JE cursor.
   * @throws DatabaseException If an error occurs while attempting to open
   * the cursor.
   */
  public Cursor openCursor(Transaction txn, CursorConfig cursorConfig)
       throws DatabaseException
  {
    return getDatabase().openCursor(txn, cursorConfig);
  }

  /**
   * Removes all records from the database.
   * @param txn A JE database transaction to be used during the clear operation
   *            or null if not required. Using transactions increases the chance
   *            of lock contention.
   * @return The number of records removed.
   * @throws DatabaseException If an error occurs while cleaning the database.
   */
  public long clear(Transaction txn) throws DatabaseException
  {
    long deletedCount = 0;
    Cursor cursor = openCursor(txn, null);
    try
    {
      if(debugEnabled())
      {
        debugVerbose("%d existing records will be deleted from the " +
            "database", getRecordCount());
      }
      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key = new DatabaseEntry();

      OperationStatus status;

      // Step forward until we deleted all records.
      for (status = cursor.getFirst(key, data, LockMode.DEFAULT);
           status == OperationStatus.SUCCESS;
           status = cursor.getNext(key, data, LockMode.DEFAULT))
      {
        cursor.delete();
        deletedCount++;
      }
      if(debugEnabled())
      {
        debugVerbose("%d records deleted", deletedCount);
      }
    }
    catch(DatabaseException de)
    {
      if(debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, de);
      }

      throw de;
    }
    finally
    {
      cursor.close();
    }

    return deletedCount;
  }

  /**
   * Update the index for a new entry.
   *
   * @param txn A database transaction, or null if none is required.
   * @param entryID     The entry ID.
   * @param entry       The entry to be indexed.
   * @return True if all the indexType keys for the entry are added. False if
   *         the entry ID alreadly exists for some keys.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public boolean addEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    HashSet<ASN1OctetString> addKeys = new HashSet<ASN1OctetString>();
    boolean success = true;

    indexer.indexEntry(txn, entry, addKeys);

    DatabaseEntry key = new DatabaseEntry();
    for (ASN1OctetString keyBytes : addKeys)
    {
      key.setData(keyBytes.value());
      if(!insertID(txn, key, entryID))
      {
        success = false;
      }
    }

    return success;
  }


  /**
   * Update the index for a deleted entry.
   *
   * @param txn A database transaction, or null if none is required.
   * @param entryID     The entry ID
   * @param entry       The contents of the deleted entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void removeEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    HashSet<ASN1OctetString> delKeys = new HashSet<ASN1OctetString>();

    indexer.indexEntry(txn, entry, delKeys);

    DatabaseEntry key = new DatabaseEntry();
    for (ASN1OctetString keyBytes : delKeys)
    {
      key.setData(keyBytes.value());
      removeID(txn, key, entryID);
    }
  }


  /**
   * Update the index to reflect a sequence of modifications in a Modify
   * operation.
   *
   * @param txn A database transaction, or null if none is required.
   * @param entryID The ID of the entry that was modified.
   * @param oldEntry The entry before the modifications were applied.
   * @param newEntry The entry after the modifications were applied.
   * @param mods The sequence of modifications in the Modify operation.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void modifyEntry(Transaction txn,
                          EntryID entryID,
                          Entry oldEntry,
                          Entry newEntry,
                          List<Modification> mods)
       throws DatabaseException
  {
    HashSet<ASN1OctetString> addKeys = new HashSet<ASN1OctetString>();
    HashSet<ASN1OctetString> delKeys = new HashSet<ASN1OctetString>();

    indexer.modifyEntry(txn, oldEntry, newEntry, mods, addKeys, delKeys);

    DatabaseEntry key = new DatabaseEntry();
    for (ASN1OctetString keyBytes : delKeys)
    {
      key.setData(keyBytes.value());
      removeID(txn, key, entryID);
    }

    for (ASN1OctetString keyBytes : addKeys)
    {
      key.setData(keyBytes.value());
      insertID(txn, key, entryID);
    }
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
    return EntryContainer.count(getDatabase());
  }

}
