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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.ErrorLogger.*;

import com.sleepycat.je.*;

import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.opends.server.backends.jeb.importLDIF.IntegerImportIDSet;
import org.opends.server.backends.jeb.importLDIF.ImportIDSet;
import static org.opends.messages.JebMessages.*;

import java.util.*;

/**
 * Represents an index implemented by a JE database in which each key maps to
 * a set of entry IDs.  The key is a byte array, and is constructed from some
 * normalized form of an attribute value (or fragment of a value) appearing
 * in the entry.
 */
public class Index extends DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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
   * The max number of tries to rewrite phantom records.
   */
  final int phantomWriteRetires = 3;

  /**
   * Whether to maintain a count of IDs for a key once the entry limit
   * has exceeded.
   */
  boolean maintainCount;

  private State state;

  /**
   * A flag to indicate if this index should be trusted to be consistent
   * with the entries database. If not trusted, we assume that existing
   * entryIDSets for a key is still accurate. However, keys that do not
   * exist are undefined instead of an empty entryIDSet. The following
   * rules will be observed when the index is not trusted:
   *
   * - no entryIDs will be added to a non-existing key.
   * - undefined entryIdSet will be returned whenever a key is not found.
   */
  private boolean trusted = false;

  /**
   * A flag to indicate if a rebuild process is running on this index.
   * During the rebuild process, we assume that no entryIDSets are
   * accurate and return an undefined set on all read operations.
   * However all write opeations will succeed. The rebuildRunning
   * flag overrides all behaviours of the trusted flag.
   */
  private boolean rebuildRunning = false;


  /**
   * Create a new index object.
   * @param name The name of the index database within the entryContainer.
   * @param indexer The indexer object to construct index keys from LDAP
   * attribute values.
   * @param state The state database to persist index state info.
   * @param indexEntryLimit The configured limit on the number of entry IDs
   * that may be indexed by one key.
   * @param cursorEntryLimit The configured limit on the number of entry IDs
   * @param maintainCount Whether to maintain a count of IDs for a key once
   * the entry limit has exceeded.
   * @param env The JE Environemnt
   * @param entryContainer The database entryContainer holding this index.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public Index(String name, Indexer indexer, State state,
        int indexEntryLimit, int cursorEntryLimit, boolean maintainCount,
        Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);
    this.indexer = indexer;
    this.comparator = indexer.getComparator();
    this.indexEntryLimit = indexEntryLimit;
    this.cursorEntryLimit = cursorEntryLimit;
    this.maintainCount = maintainCount;

    DatabaseConfig dbNodupsConfig = new DatabaseConfig();

    if(env.getConfig().getReadOnly())
    {
      dbNodupsConfig.setReadOnly(true);
      dbNodupsConfig.setAllowCreate(false);
      dbNodupsConfig.setTransactional(false);
    }
    else if(!env.getConfig().getTransactional())
    {
      dbNodupsConfig.setAllowCreate(true);
      dbNodupsConfig.setTransactional(false);
      dbNodupsConfig.setDeferredWrite(true);
    }
    else
    {
      dbNodupsConfig.setAllowCreate(true);
      dbNodupsConfig.setTransactional(true);
    }

    this.dbConfig = dbNodupsConfig;
    this.dbConfig.setOverrideBtreeComparator(true);
    this.dbConfig.setBtreeComparator(comparator.getClass());

    this.state = state;

    this.trusted = state.getIndexTrustState(null, this);
    if(!trusted && entryContainer.getHighestEntryID().equals(new EntryID(0)))
    {
      // If there are no entries in the entry container then there
      // is no reason why this index can't be upgraded to trusted.
      setTrusted(null, true);
    }

    // Issue warning if this index is not trusted
    if(!trusted)
    {
      logError(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(name));
    }

  }

  /**
   * Insert an entry ID into the set of IDs indexed by a given key.
   *
   * @param txn A database transaction, or null if none is required.
   * @param key         The index key.
   * @param entryID     The entry ID.
   * @return True if the entry ID is inserted or ignored because the entry limit
   *         count is exceeded. False if it already exists in the entry ID set
   *         for the given key.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean insertID(Transaction txn, DatabaseEntry key, EntryID entryID)
       throws DatabaseException
  {
    OperationStatus status;
    DatabaseEntry entryIDData = entryID.getDatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    boolean success = false;

    if(maintainCount)
    {
      for(int i = 0; i < phantomWriteRetires; i++)
      {
        if(insertIDWithRMW(txn, key, data, entryIDData, entryID) ==
            OperationStatus.SUCCESS)
        {
          return true;
        }
      }
    }
    else
    {
      status = read(txn, key, data, LockMode.READ_COMMITTED);
      if(status == OperationStatus.SUCCESS)
      {
        EntryIDSet entryIDList =
            new EntryIDSet(key.getData(), data.getData());

        if (entryIDList.isDefined())
        {
          for(int i = 0; i < phantomWriteRetires; i++)
          {
            if(insertIDWithRMW(txn, key, data, entryIDData, entryID) ==
                OperationStatus.SUCCESS)
            {
              return true;
            }
          }
        }
      }
      else
      {
        if(rebuildRunning || trusted)
        {
          status = insert(txn, key, entryIDData);
          if(status == OperationStatus.KEYEXIST)
          {
            for(int i = 1; i < phantomWriteRetires; i++)
            {
              if(insertIDWithRMW(txn, key, data, entryIDData, entryID) ==
                  OperationStatus.SUCCESS)
              {
                return true;
              }
            }
          }
        }
        else
        {
          return true;
        }
      }
    }

    return success;
  }


  /**
   * Add the specified import ID set to the provided key. Used during
   * substring buffer flushing.
   *
   * @param txn A transaction.
   * @param key The key to add the set to.
   * @param importIdSet The set of import IDs.
   * @throws DatabaseException If an database error occurs.
   */
  public void insert(Transaction txn, DatabaseEntry key,
                     ImportIDSet importIdSet)
  throws DatabaseException {

    OperationStatus status;
    DatabaseEntry data = new DatabaseEntry();
      status = read(txn, key, data, LockMode.RMW);
      if(status == OperationStatus.SUCCESS) {
        ImportIDSet newImportIDSet = new IntegerImportIDSet();
        if (newImportIDSet.merge(data.getData(), importIdSet,
                                 indexEntryLimit, maintainCount)) {
          entryLimitExceededCount++;
        }
        data.setData(newImportIDSet.toDatabase());
      } else if(status == OperationStatus.NOTFOUND) {
        if(!importIdSet.isDefined()) {
          entryLimitExceededCount++;
        }
        data.setData(importIdSet.toDatabase());
      } else {
        //Should never happen during import.
        throw new DatabaseException();
      }
      put(txn,key, data);
  }


  /**
   * Add the specified entry ID to the provided keys in the keyset.
   *
   * @param txn  A transaction.
   * @param keySet  The set containing the keys.
   * @param entryID The entry ID.
   * @return <CODE>True</CODE> if the insert was successful.
   * @throws DatabaseException If a database error occurs.
   */
  public synchronized
  boolean insert(Transaction txn, Set<byte[]> keySet, EntryID entryID)
  throws DatabaseException {
    for(byte[] key : keySet) {
      if(insertIDWithRMW(txn, new DatabaseEntry(key), new DatabaseEntry(),
              entryID.getDatabaseEntry(), entryID) !=
              OperationStatus.SUCCESS)  {
        return false;
      }
    }
    return true;
  }

  private OperationStatus insertIDWithRMW(Transaction txn, DatabaseEntry key,
                                          DatabaseEntry data,
                                          DatabaseEntry entryIDData,
                                          EntryID entryID)
      throws DatabaseException
  {
    OperationStatus status;

    status = read(txn, key, data, LockMode.RMW);
    if(status == OperationStatus.SUCCESS)
    {
      EntryIDSet entryIDList =
          new EntryIDSet(key.getData(), data.getData());
      if (entryIDList.isDefined() && indexEntryLimit > 0 &&
          entryIDList.size() >= indexEntryLimit)
      {
        if(maintainCount)
        {
          entryIDList = new EntryIDSet(entryIDList.size());
        }
        else
        {
          entryIDList = new EntryIDSet();
        }
        entryLimitExceededCount++;

        if(debugEnabled())
        {
          StringBuilder builder = new StringBuilder();
          StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
          TRACER.debugInfo("Index entry exceeded in index %s. " +
              "Limit: %d. ID list size: %d.\nKey:",
              name, indexEntryLimit, entryIDList.size(),
              builder);

        }
      }

      entryIDList.add(entryID);

      byte[] after = entryIDList.toDatabase();
      data.setData(after);
      return put(txn, key, data);
    }
    else
    {
      if(rebuildRunning || trusted)
      {
        return insert(txn, key, entryIDData);
      }
      else
      {
        return OperationStatus.SUCCESS;
      }
    }
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
    DatabaseEntry data = new DatabaseEntry();

    if(maintainCount)
    {
      removeIDWithRMW(txn, key, data, entryID);
    }
    else
    {
      status = read(txn, key, data, LockMode.READ_COMMITTED);
      if(status == OperationStatus.SUCCESS)
      {
        EntryIDSet entryIDList = new EntryIDSet(key.getData(), data.getData());
        if(entryIDList.isDefined())
        {
          removeIDWithRMW(txn, key, data, entryID);
        }
      }
      else
      {
        // Ignore failures if rebuild is running since a empty entryIDset
        // will probably not be rebuilt.
        if(trusted && !rebuildRunning)
        {
          setTrusted(txn, false);

          if(debugEnabled())
          {
            StringBuilder builder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
            TRACER.debugError("The expected key does not exist in the " +
                "index %s.\nKey:%s", name, builder.toString());
          }

          logError(ERR_JEB_INDEX_CORRUPT_REQUIRES_REBUILD.get(name));
        }
      }
    }
  }

  /**
   * Delete specified entry ID from all keys in the provided key set.
   *
   * @param txn  A Transaction.
   * @param keySet A set of keys.
   * @param entryID The entry ID to delete.
   * @throws DatabaseException If a database error occurs.
   */
  public synchronized
  void delete(Transaction txn, Set<byte[]> keySet, EntryID entryID)
  throws DatabaseException {
    setTrusted(txn, false);
    for(byte[] key : keySet) {
       removeIDWithRMW(txn, new DatabaseEntry(key),
                       new DatabaseEntry(), entryID);
    }
    setTrusted(txn, true);
  }

  private void removeIDWithRMW(Transaction txn, DatabaseEntry key,
                               DatabaseEntry data, EntryID entryID)
      throws DatabaseException
  {
    OperationStatus status;
    status = read(txn, key, data, LockMode.RMW);

    if (status == OperationStatus.SUCCESS)
    {
      EntryIDSet entryIDList = new EntryIDSet(key.getData(), data.getData());
      // Ignore failures if rebuild is running since the entry ID is
      // probably already removed.
      if (!entryIDList.remove(entryID) && !rebuildRunning)
      {
        if(trusted)
        {
          setTrusted(txn, false);

          if(debugEnabled())
          {
            StringBuilder builder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
            TRACER.debugError("The expected entry ID does not exist in " +
                "the entry ID list for index %s.\nKey:%s",
                name, builder.toString());
          }

          logError(ERR_JEB_INDEX_CORRUPT_REQUIRES_REBUILD.get(name));
        }
      }
      else
      {
        byte[] after = entryIDList.toDatabase();
        if (after == null)
        {
          // No more IDs, so remove the key. If index is not
          // trusted then this will cause all subsequent reads
          // for this key to return undefined set.
          delete(txn, key);
        }
        else
        {
          data.setData(after);
          put(txn, key, data);
        }
      }
    }
    else
    {
      // Ignore failures if rebuild is running since a empty entryIDset
      // will probably not be rebuilt.
      if(trusted && !rebuildRunning)
      {
        setTrusted(txn, false);

        if(debugEnabled())
        {
          StringBuilder builder = new StringBuilder();
          StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
          TRACER.debugError("The expected key does not exist in the " +
              "index %s.\nKey:%s", name, builder.toString());
        }

        logError(ERR_JEB_INDEX_CORRUPT_REQUIRES_REBUILD.get(name));
      }
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
    if(rebuildRunning)
    {
      return ConditionResult.UNDEFINED;
    }

    OperationStatus status;
    LockMode lockMode = LockMode.DEFAULT;
    DatabaseEntry data = new DatabaseEntry();

    status = read(txn, key, data, lockMode);
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
      if(trusted)
      {
        return ConditionResult.FALSE;
      }
      else
      {
        return ConditionResult.UNDEFINED;
      }
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
    if(rebuildRunning)
    {
      return new EntryIDSet();
    }

    try
    {
      OperationStatus status;
      DatabaseEntry data = new DatabaseEntry();
      status = read( txn, key, data, lockMode);
      if (status != OperationStatus.SUCCESS)
      {
        if(trusted)
        {
          return new EntryIDSet(key.getData(), null);
        }
        else
        {
          return new EntryIDSet();
        }
      }
      return new EntryIDSet(key.getData(), data.getData());
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
      delete(txn, key);
    }
    else
    {
      if (!entryIDList.isDefined())
      {
        entryLimitExceededCount++;
      }
      data.setData(after);
      put(txn, key, data);
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

    // If this index is not trusted, then just return an undefined
    // id set.
    if(rebuildRunning || !trusted)
    {
      return new EntryIDSet();
    }

    try
    {
      // Total number of IDs found so far.
      int totalIDCount = 0;

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key;

      ArrayList<EntryIDSet> lists = new ArrayList<EntryIDSet>();

      OperationStatus status;
      Cursor cursor;

      cursor = openCursor(null, CursorConfig.READ_COMMITTED);

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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
   * Increment the count of the number of keys that have exceeded the entry
   * limit since this object was created.
   */
  public void incEntryLimitExceededCount()
  {
    entryLimitExceededCount++;
  }

  /**
   * Update the index for a new entry.
   *
   * @param txn A database transaction, or null if none is required.
   * @param entryID     The entry ID.
   * @param entry       The entry to be indexed.
   * @return True if all the indexType keys for the entry are added. False if
   *         the entry ID already exists for some keys.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public boolean addEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    TreeSet<byte[]> addKeys = new TreeSet<byte[]>(indexer.getComparator());
    boolean success = true;

    indexer.indexEntry(txn, entry, addKeys);

    DatabaseEntry key = new DatabaseEntry();
    for (byte[] keyBytes : addKeys)
    {
      key.setData(keyBytes);
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
    TreeSet<byte[]> delKeys = new TreeSet<byte[]>(indexer.getComparator());

    indexer.indexEntry(txn, entry, delKeys);

    DatabaseEntry key = new DatabaseEntry();
    for (byte[] keyBytes : delKeys)
    {
      key.setData(keyBytes);
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
    TreeSet<byte[]> addKeys = new TreeSet<byte[]>(indexer.getComparator());
    TreeSet<byte[]> delKeys = new TreeSet<byte[]>(indexer.getComparator());

    indexer.modifyEntry(txn, oldEntry, newEntry, mods, addKeys, delKeys);

    DatabaseEntry key = new DatabaseEntry();
    for (byte[] keyBytes : delKeys)
    {
      key.setData(keyBytes);
      removeID(txn, key, entryID);
    }

    for (byte[] keyBytes : addKeys)
    {
      key.setData(keyBytes);
      insertID(txn, key, entryID);
    }
  }

  /**
   * Set the index entry limit.
   *
   * @param indexEntryLimit The index entry limit to set.
   * @return True if a rebuild is required or false otherwise.
   */
  public boolean setIndexEntryLimit(int indexEntryLimit)
  {
    boolean rebuildRequired = false;
    if(this.indexEntryLimit < indexEntryLimit &&
        entryLimitExceededCount > 0 )
    {
      rebuildRequired = true;
    }
    this.indexEntryLimit = indexEntryLimit;

    return rebuildRequired;
  }

  /**
   * Set the indexer.
   *
   * @param indexer The indexer to set
   */
  public void setIndexer(Indexer indexer)
  {
    this.indexer = indexer;
  }

  /**
   * Return entry limit.
   *
   * @return The entry limit.
   */
  public int getIndexEntryLimit() {
    return this.indexEntryLimit;
  }

  /**
   * Set the index trust state.
   * @param txn A database transaction, or null if none is required.
   * @param trusted True if this index should be trusted or false
   *                otherwise.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public synchronized void setTrusted(Transaction txn, boolean trusted)
      throws DatabaseException
  {
    this.trusted = trusted;
    state.putIndexTrustState(txn, this, trusted);
  }

  /**
   * Return true iff this index is trusted.
   * @return the trusted state of this index
   */
  public synchronized boolean isTrusted()
  {
    return trusted;
  }

  /**
   * Set the rebuild status of this index.
   * @param rebuildRunning True if a rebuild process on this index
   *                       is running or False otherwise.
   */
  public synchronized void setRebuildStatus(boolean rebuildRunning)
  {
    this.rebuildRunning = rebuildRunning;
  }

  /**
   * Whether this index maintains a count of IDs for keys once the
   * entry limit has exceeded.
   * @return <code>true</code> if this index maintains court of IDs
   * or <code>false</code> otherwise
   */
  public boolean getMaintainCount()
  {
    return maintainCount;
  }
}
