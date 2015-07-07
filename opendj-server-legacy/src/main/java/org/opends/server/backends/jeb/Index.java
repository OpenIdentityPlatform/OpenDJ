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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.BackendMessages.*;

import java.util.*;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.opends.server.backends.jeb.IndexBuffer.BufferedIndexValues;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

/**
 * Represents an index implemented by a JE database in which each key maps to
 * a set of entry IDs.  The key is a byte array, and is constructed from some
 * normalized form of an attribute value (or fragment of a value) appearing
 * in the entry.
 */
public class Index extends DatabaseContainer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The indexer object to construct index keys from LDAP attribute values. */
  private Indexer indexer;

  /** The limit on the number of entry IDs that may be indexed by one key. */
  private int indexEntryLimit;
  /**
   * Limit on the number of entry IDs that may be retrieved by cursoring
   * through an index.
   */
  private final int cursorEntryLimit;
  /**
   * Number of keys that have exceeded the entry limit since this
   * object was created.
   */
  private int entryLimitExceededCount;

  /** The max number of tries to rewrite phantom records. */
  private final int phantomWriteRetries = 3;

  /**
   * Whether to maintain a count of IDs for a key once the entry limit
   * has exceeded.
   */
  private final boolean maintainCount;

  private final State state;

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
  private boolean trusted;

  private final ImportIDSet newImportIDSet;

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
   * @param env The JE Environment
   * @param entryContainer The database entryContainer holding this index.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  @SuppressWarnings("unchecked")
  Index(String name, Indexer indexer, State state,
        int indexEntryLimit, int cursorEntryLimit, boolean maintainCount,
        Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);
    this.indexer = indexer;
    this.indexEntryLimit = indexEntryLimit;
    this.cursorEntryLimit = cursorEntryLimit;
    this.maintainCount = maintainCount;
    this.newImportIDSet = new ImportIDSet(indexEntryLimit,
                                          indexEntryLimit, maintainCount);

    this.dbConfig = JEBUtils.toDatabaseConfigNoDuplicates(env);
    this.dbConfig.setOverrideBtreeComparator(true);
    this.dbConfig.setBtreeComparator((Class<? extends Comparator<byte[]>>)
                                     indexer.getComparator().getClass());

    this.state = state;

    this.trusted = state.getIndexTrustState(null, this);
    if (!trusted && entryContainer.getHighestEntryID().longValue() == 0)
    {
      // If there are no entries in the entry container then there
      // is no reason why this index can't be upgraded to trusted.
      setTrusted(null, true);
    }
  }

  void indexEntry(Entry entry, Set<ByteString> keys)
  {
    indexer.indexEntry(entry, keys);
  }

  /**
   * Add an add entry ID operation into a index buffer.
   *
   * @param buffer The index buffer to insert the ID into.
   * @param keyBytes         The index key bytes.
   * @param entryID     The entry ID.
   */
  void insertID(IndexBuffer buffer, ByteString keyBytes, EntryID entryID)
  {
    getBufferedIndexValues(buffer, keyBytes).addEntryID(keyBytes, entryID);
  }

  /**
   * Delete the specified import ID set from the import ID set associated with the key.
   *
   * @param key The key to delete the set from.
   * @param importIdSet The import ID set to delete.
   * @param data A database entry to use for data.
   * @throws DatabaseException If a database error occurs.
   */
  public void delete(DatabaseEntry key, ImportIDSet importIdSet, DatabaseEntry data) throws DatabaseException {
    if (read(null, key, data, LockMode.DEFAULT) == SUCCESS) {
      newImportIDSet.clear();
      newImportIDSet.remove(data.getData(), importIdSet);
      if (newImportIDSet.isDefined() && newImportIDSet.size() == 0)
      {
        delete(null, key);
      }
      else
      {
        data.setData(newImportIDSet.toDatabase());
        put(null, key, data);
      }
    } else {
      // Should never happen -- the keys should always be there.
      throw new RuntimeException();
    }
  }

  /**
   * Insert the specified import ID set into this index. Creates a DB cursor if needed.
   *
   * @param key The key to add the set to.
   * @param importIdSet The set of import IDs.
   * @param data Database entry to reuse for read.
   * @throws DatabaseException If a database error occurs.
   */
  public void insert(DatabaseEntry key, ImportIDSet importIdSet, DatabaseEntry data) throws DatabaseException {
    final OperationStatus status = read(null, key, data, LockMode.DEFAULT);
    if(status == OperationStatus.SUCCESS) {
      newImportIDSet.clear();
      if (newImportIDSet.merge(data.getData(), importIdSet))
      {
        entryLimitExceededCount++;
      }
      data.setData(newImportIDSet.toDatabase());
      put(null, key, data);
    } else if(status == OperationStatus.NOTFOUND) {
      if(!importIdSet.isDefined()) {
        entryLimitExceededCount++;
      }
      data.setData(importIdSet.toDatabase());
      put(null, key, data);
    } else {
      // Should never happen during import.
      throw new RuntimeException();
    }
  }

  /**
   * Update the set of entry IDs for a given key.
   *
   * @param txn A database transaction, or null if none is required.
   * @param key The database key.
   * @param deletedIDs The IDs to remove for the key.
   * @param addedIDs the IDs to add for the key.
   * @throws DatabaseException If a database error occurs.
   */
  void updateKey(Transaction txn, DatabaseEntry key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws DatabaseException
  {
    DatabaseEntry data = new DatabaseEntry();

    if(deletedIDs == null && addedIDs == null)
    {
      final OperationStatus status = delete(txn, key);
      if (status != SUCCESS && logger.isTraceEnabled())
      {
        StringBuilder builder = new StringBuilder();
        StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
        logger.trace("The expected key does not exist in the index %s.\nKey:%s ", name, builder);
      }
      return;
    }

    // Handle cases where nothing is changed early to avoid DB access.
    if (isNullOrEmpty(deletedIDs) && isNullOrEmpty(addedIDs))
    {
      return;
    }

    if(maintainCount)
    {
      for (int i = 0; i < phantomWriteRetries; i++)
      {
        if (updateKeyWithRMW(txn, key, data, deletedIDs, addedIDs) == SUCCESS)
        {
          return;
        }
      }
    }
    else
    {
      OperationStatus status = read(txn, key, data, LockMode.READ_COMMITTED);
      if(status == OperationStatus.SUCCESS)
      {
        EntryIDSet entryIDList = new EntryIDSet(key.getData(), data.getData());
        if (entryIDList.isDefined())
        {
          for (int i = 0; i < phantomWriteRetries; i++)
          {
            if (updateKeyWithRMW(txn, key, data, deletedIDs, addedIDs) == SUCCESS)
            {
              return;
            }
          }
        }
      }
      else if (trusted)
      {
        if (deletedIDs != null)
        {
          logIndexCorruptError(txn, key);
        }

        if (isNotNullOrEmpty(addedIDs))
        {
          data.setData(addedIDs.toDatabase());

          status = insert(txn, key, data);
          if(status == OperationStatus.KEYEXIST)
          {
            for (int i = 1; i < phantomWriteRetries; i++)
            {
              if (updateKeyWithRMW(txn, key, data, deletedIDs, addedIDs) == SUCCESS)
              {
                return;
              }
            }
          }
        }
      }
    }
  }

  private boolean isNullOrEmpty(EntryIDSet entryIDSet)
  {
    return entryIDSet == null || entryIDSet.size() == 0;
  }

  private boolean isNotNullOrEmpty(EntryIDSet entryIDSet)
  {
    return entryIDSet != null && entryIDSet.size() > 0;
  }

  private OperationStatus updateKeyWithRMW(Transaction txn,
                                           DatabaseEntry key,
                                           DatabaseEntry data,
                                           EntryIDSet deletedIDs,
                                           EntryIDSet addedIDs)
      throws DatabaseException
  {
    final OperationStatus status = read(txn, key, data, LockMode.RMW);
    if(status == SUCCESS)
    {
      EntryIDSet entryIDList = computeEntryIDList(key, data, deletedIDs, addedIDs);
      byte[] after = entryIDList.toDatabase();
      if (after != null)
      {
        data.setData(after);
        return put(txn, key, data);
      }
      else
      {
        // No more IDs, so remove the key. If index is not
        // trusted then this will cause all subsequent reads
        // for this key to return undefined set.
        return delete(txn, key);
      }
    }
    else if (trusted)
    {
      if (deletedIDs != null)
      {
        logIndexCorruptError(txn, key);
      }

      if (isNotNullOrEmpty(addedIDs))
      {
        data.setData(addedIDs.toDatabase());
        return insert(txn, key, data);
      }
    }
    return OperationStatus.SUCCESS;
  }

  private EntryIDSet computeEntryIDList(DatabaseEntry key, DatabaseEntry data, EntryIDSet deletedIDs,
      EntryIDSet addedIDs)
  {
    EntryIDSet entryIDList = new EntryIDSet(key.getData(), data.getData());
    if(addedIDs != null)
    {
      if(entryIDList.isDefined() && indexEntryLimit > 0)
      {
        long idCountDelta = addedIDs.size();
        if(deletedIDs != null)
        {
          idCountDelta -= deletedIDs.size();
        }
        if(idCountDelta + entryIDList.size() >= indexEntryLimit)
        {
          if(maintainCount)
          {
            entryIDList = new EntryIDSet(entryIDList.size() + idCountDelta);
          }
          else
          {
            entryIDList = new EntryIDSet();
          }
          entryLimitExceededCount++;

          if(logger.isTraceEnabled())
          {
            StringBuilder builder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
            logger.trace("Index entry exceeded in index %s. " +
                "Limit: %d. ID list size: %d.\nKey:%s",
                name, indexEntryLimit, idCountDelta + addedIDs.size(), builder);

          }
        }
        else
        {
          entryIDList.addAll(addedIDs);
          if(deletedIDs != null)
          {
            entryIDList.deleteAll(deletedIDs);
          }
        }
      }
      else
      {
        entryIDList.addAll(addedIDs);
        if(deletedIDs != null)
        {
          entryIDList.deleteAll(deletedIDs);
        }
      }
    }
    else if(deletedIDs != null)
    {
      entryIDList.deleteAll(deletedIDs);
    }
    return entryIDList;
  }

  /**
   * Add an remove entry ID operation into a index buffer.
   *
   * @param buffer The index buffer to insert the ID into.
   * @param keyBytes    The index key bytes.
   * @param entryID     The entry ID.
   */
  void removeID(IndexBuffer buffer, ByteString keyBytes, EntryID entryID)
  {
    getBufferedIndexValues(buffer, keyBytes).deleteEntryID(keyBytes, entryID);
  }

  private void logIndexCorruptError(Transaction txn, DatabaseEntry key)
  {
    if (logger.isTraceEnabled())
    {
      StringBuilder builder = new StringBuilder();
      StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
      logger.trace("The expected key does not exist in the index %s.\nKey:%s", name, builder);
    }

    setTrusted(txn, false);
    logger.error(ERR_INDEX_CORRUPT_REQUIRES_REBUILD, name);
  }

  /**
   * Buffered delete of a key from the JE database.
   * @param buffer The index buffer to use to store the deleted keys
   * @param keyBytes The index key bytes.
   */
  public void delete(IndexBuffer buffer, ByteString keyBytes)
  {
    getBufferedIndexValues(buffer, keyBytes);
  }

  private BufferedIndexValues getBufferedIndexValues(IndexBuffer buffer, ByteString keyBytes)
  {
    return buffer.getBufferedIndexValues(this, keyBytes, indexer.getBSComparator());
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
  public ConditionResult containsID(Transaction txn, DatabaseEntry key, EntryID entryID)
       throws DatabaseException
  {
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status = read(txn, key, data, LockMode.DEFAULT);
    if (status == SUCCESS)
    {
      EntryIDSet entryIDList = new EntryIDSet(key.getData(), data.getData());
      if (!entryIDList.isDefined())
      {
        return ConditionResult.UNDEFINED;
      }
      return ConditionResult.valueOf(entryIDList.contains(entryID));
    }
    else if (trusted)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.UNDEFINED;
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
  public EntryIDSet readKey(DatabaseEntry key, Transaction txn, LockMode lockMode)
  {
    try
    {
      DatabaseEntry data = new DatabaseEntry();
      OperationStatus status = read( txn, key, data, lockMode);
      if (status != SUCCESS)
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
      logger.traceException(e);
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
  public void writeKey(Transaction txn, DatabaseEntry key, EntryIDSet entryIDList)
       throws DatabaseException
  {
    DatabaseEntry data = new DatabaseEntry();
    byte[] after = entryIDList.toDatabase();
    if (after != null)
    {
      if (!entryIDList.isDefined())
      {
        entryLimitExceededCount++;
      }
      data.setData(after);
      put(txn, key, data);
    }
    else
    {
      // No more IDs, so remove the key.
      delete(txn, key);
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
    // If this index is not trusted, then just return an undefined id set.
    if (!trusted)
    {
      return new EntryIDSet();
    }

    try
    {
      // Total number of IDs found so far.
      int totalIDCount = 0;
      LockMode lockMode = LockMode.DEFAULT;

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key;

      ArrayList<EntryIDSet> lists = new ArrayList<>();

      Cursor cursor = openCursor(null, CursorConfig.READ_COMMITTED);
      try
      {
        final Comparator<byte[]> comparator = indexer.getComparator();
        OperationStatus status;
        // Set the lower bound if necessary.
        if(lower.length > 0)
        {
          key = new DatabaseEntry(lower);

          // Initialize the cursor to the lower bound.
          status = cursor.getSearchKeyRange(key, data, lockMode);

          // Advance past the lower bound if necessary.
          if (status == SUCCESS && !lowerIncluded &&
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
            if (cmp > 0 || (cmp == 0 && !upperIncluded))
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
      logger.traceException(e);
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
   * Update the index buffer for a deleted entry.
   *
   * @param buffer The index buffer to use to store the deleted keys
   * @param entryID     The entry ID.
   * @param entry       The entry to be indexed.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry) throws DatabaseException, DirectoryException
  {
    final Set<ByteString> addKeys = new HashSet<>();
    indexer.indexEntry(entry, addKeys);

    for (ByteString keyBytes : addKeys)
    {
      insertID(buffer, keyBytes, entryID);
    }
  }

  /**
   * Update the index buffer for a deleted entry.
   *
   * @param buffer The index buffer to use to store the deleted keys
   * @param entryID     The entry ID
   * @param entry       The contents of the deleted entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
      throws DatabaseException, DirectoryException
  {
    final Set<ByteString> delKeys = new HashSet<>();
    indexer.indexEntry(entry, delKeys);

    for (ByteString keyBytes : delKeys)
    {
      removeID(buffer, keyBytes, entryID);
    }
  }

  /**
   * Update the index to reflect a sequence of modifications in a Modify
   * operation.
   *
   * @param buffer The index buffer to use to store the deleted keys
   * @param entryID The ID of the entry that was modified.
   * @param oldEntry The entry before the modifications were applied.
   * @param newEntry The entry after the modifications were applied.
   * @param mods The sequence of modifications in the Modify operation.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void modifyEntry(IndexBuffer buffer,
                          EntryID entryID,
                          Entry oldEntry,
                          Entry newEntry,
                          List<Modification> mods)
      throws DatabaseException
  {
    final Map<ByteString, Boolean> modifiedKeys = new TreeMap<>(indexer.getBSComparator());
    indexer.modifyEntry(oldEntry, newEntry, mods, modifiedKeys);

    for (Map.Entry<ByteString, Boolean> modifiedKey : modifiedKeys.entrySet())
    {
      if(modifiedKey.getValue())
      {
        insertID(buffer, modifiedKey.getKey(), entryID);
      }
      else
      {
        removeID(buffer, modifiedKey.getKey(), entryID);
      }
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
    final boolean rebuildRequired =
        this.indexEntryLimit < indexEntryLimit && entryLimitExceededCount > 0;
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
   * Return <code>true</code> iff this index is being rebuilt.
   * @return The rebuild state of this index
   */
  public synchronized boolean isRebuildRunning()
  {
    return false; // FIXME inline?
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

  /**
   * Return an indexes comparator.
   *
   * @return The comparator related to an index.
   */
  public Comparator<byte[]> getComparator()
  {
    return indexer.getComparator();
  }
}
