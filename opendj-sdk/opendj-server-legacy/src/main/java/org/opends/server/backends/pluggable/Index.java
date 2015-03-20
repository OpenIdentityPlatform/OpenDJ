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
package org.opends.server.backends.pluggable;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.backends.pluggable.IndexBuffer.BufferedIndexValues;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.util.StaticUtils;

/**
 * Represents an index implemented by a JE database in which each key maps to
 * a set of entry IDs.  The key is a byte array, and is constructed from some
 * normalized form of an attribute value (or fragment of a value) appearing
 * in the entry.
 */
class Index extends DatabaseContainer
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
   * @param txn The transaction to use when creating this object
   * @param entryContainer The database entryContainer holding this index.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  Index(TreeName name, Indexer indexer, State state, int indexEntryLimit, int cursorEntryLimit, boolean maintainCount,
      WriteableStorage txn, EntryContainer entryContainer) throws StorageRuntimeException
  {
    super(name);
    this.indexer = indexer;
    this.indexEntryLimit = indexEntryLimit;
    this.cursorEntryLimit = cursorEntryLimit;
    this.maintainCount = maintainCount;

    this.state = state;
    this.trusted = state.getIndexTrustState(txn, this);
    if (!trusted && entryContainer.getHighestEntryID(txn).longValue() == 0)
    {
      // If there are no entries in the entry container then there
      // is no reason why this index can't be upgraded to trusted.
      setTrusted(txn, true);
    }
  }

  void indexEntry(Entry entry, Set<ByteString> keys, IndexingOptions options)
  {
    indexer.indexEntry(entry, keys, options);
  }

  final void insertID(IndexBuffer buffer, ByteString keyBytes, EntryID entryID)
  {
    getBufferedIndexValues(buffer, keyBytes).addEntryID(keyBytes, entryID);
  }

  /**
   * Delete the specified import ID set from the import ID set associated with the key.
   *
   * @param txn The database transaction
   * @param importIdSet The import ID set to delete.
   * @throws StorageRuntimeException If a database error occurs.
   */
  final void delete(WriteableStorage txn, ImportIDSet importIdSet) throws StorageRuntimeException
  {
    ByteSequence key = importIdSet.getKey();
    ByteString value = txn.read(getName(), key);
    if (value != null) {
      final ImportIDSet importIDSet = new ImportIDSet(key, newSetFromBytes(key, value), indexEntryLimit, maintainCount);
      importIDSet.remove(importIdSet);
      if (importIDSet.isDefined() && importIDSet.size() == 0)
      {
        txn.delete(getName(), key);
      }
      else
      {
        value = importIDSet.valueToByteString();
        txn.create(getName(), key, value);
      }
    } else {
      // Should never happen -- the keys should always be there.
      throw new RuntimeException();
    }
  }

  /**
   * Insert the specified import ID set into this index. Creates a DB cursor if needed.
   *
   * @param txn The database transaction
   * @param importIdSet The set of import IDs.
   * @throws StorageRuntimeException If a database error occurs.
   */
  final void insert(WriteableStorage txn, ImportIDSet importIdSet) throws StorageRuntimeException
  {
    ByteSequence key = importIdSet.getKey();
    ByteString value = txn.read(getName(), key);
    if(value != null) {
      final ImportIDSet importIDSet = new ImportIDSet(key, newSetFromBytes(key, value), indexEntryLimit, maintainCount);
      if (importIDSet.merge(importIdSet)) {
        entryLimitExceededCount++;
      }
      value = importIDSet.valueToByteString();
    } else {
      if(!importIdSet.isDefined()) {
        entryLimitExceededCount++;
      }
      value = importIdSet.valueToByteString();
    }
    txn.create(getName(), key, value);
  }

  void updateKey(WriteableStorage txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws StorageRuntimeException
  {
    /*
     * Check the special condition where both deletedIDs and addedIDs are null. This is used when
     * deleting entries and corresponding id2children and id2subtree records must be completely
     * removed.
     */
    if (deletedIDs == null && addedIDs == null)
    {
      boolean success = txn.delete(getName(), key);
      if (success && logger.isTraceEnabled())
      {
        StringBuilder builder = new StringBuilder();
        StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
        logger.trace("The expected key does not exist in the index %s.\nKey:%s ", getName(), builder);
      }
      return;
    }

    // Handle cases where nothing is changed early to avoid DB access.
    if (isNullOrEmpty(deletedIDs) && isNullOrEmpty(addedIDs))
    {
      return;
    }

    if (maintainCount)
    {
      updateKeyWithRMW(txn, key, deletedIDs, addedIDs);
    }
    else
    {
      /*
       * Avoid taking a write lock on a record which has hit all IDs because it is likely to be a
       * point of contention.
       */
      ByteString value = txn.read(getName(), key);
      if (value != null)
      {
        EntryIDSet entryIDSet = newSetFromBytes(key, value);
        if (entryIDSet.isDefined())
        {
          updateKeyWithRMW(txn, key, deletedIDs, addedIDs);
        } // else the record exists but we've hit all IDs.
      }
      else if (trusted)
      {
        if (deletedIDs != null)
        {
          logIndexCorruptError(txn, key);
        }

        if (isNotNullOrEmpty(addedIDs)
            && !txn.putIfAbsent(getName(), key, addedIDs.toByteString()))
        {
          updateKeyWithRMW(txn, key, deletedIDs, addedIDs);
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

  private void updateKeyWithRMW(WriteableStorage txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws StorageRuntimeException
  {
    final ByteString value = txn.getRMW(getName(), key);
    if (value != null)
    {
      EntryIDSet entryIDSet = computeEntryIDSet(key, value, deletedIDs, addedIDs);
      ByteString after = entryIDSet.toByteString();
      if (!after.isEmpty())
      {
        txn.create(getName(), key, after);
      }
      else
      {
        // No more IDs, so remove the key. If index is not
        // trusted then this will cause all subsequent reads
        // for this key to return undefined set.
        txn.delete(getName(), key);
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
        txn.putIfAbsent(getName(), key, addedIDs.toByteString());
      }
    }
  }

  private EntryIDSet computeEntryIDSet(ByteString key, ByteString value, EntryIDSet deletedIDs, EntryIDSet addedIDs)
  {
    EntryIDSet entryIDSet = newSetFromBytes(key, value);
    if(addedIDs != null)
    {
      if(entryIDSet.isDefined() && indexEntryLimit > 0)
      {
        long idCountDelta = addedIDs.size();
        if(deletedIDs != null)
        {
          idCountDelta -= deletedIDs.size();
        }
        if(idCountDelta + entryIDSet.size() >= indexEntryLimit)
        {
          if(maintainCount)
          {
            entryIDSet = newUndefinedSetWithSize(key, entryIDSet.size() + idCountDelta);
          }
          else
          {
            entryIDSet = newUndefinedSet();
          }
          entryLimitExceededCount++;

          if(logger.isTraceEnabled())
          {
            StringBuilder builder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
            logger.trace("Index entry exceeded in index %s. " +
                "Limit: %d. ID list size: %d.\nKey:%s",
                getName(), indexEntryLimit, idCountDelta + addedIDs.size(), builder);

          }
        }
        else
        {
          entryIDSet.addAll(addedIDs);
          if(deletedIDs != null)
          {
            entryIDSet.removeAll(deletedIDs);
          }
        }
      }
      else
      {
        entryIDSet.addAll(addedIDs);
        if(deletedIDs != null)
        {
          entryIDSet.removeAll(deletedIDs);
        }
      }
    }
    else if(deletedIDs != null)
    {
      entryIDSet.removeAll(deletedIDs);
    }
    return entryIDSet;
  }

  final void removeID(IndexBuffer buffer, ByteString keyBytes, EntryID entryID)
  {
    getBufferedIndexValues(buffer, keyBytes).deleteEntryID(keyBytes, entryID);
  }

  private void logIndexCorruptError(WriteableStorage txn, ByteString key)
  {
    if (logger.isTraceEnabled())
    {
      StringBuilder builder = new StringBuilder();
      StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
      logger.trace("The expected key does not exist in the index %s.\nKey:%s", getName(), builder);
    }

    setTrusted(txn, false);
    logger.error(ERR_JEB_INDEX_CORRUPT_REQUIRES_REBUILD, getName());
  }

  void delete(IndexBuffer buffer, ByteString keyBytes)
  {
    getBufferedIndexValues(buffer, keyBytes);
  }

  private BufferedIndexValues getBufferedIndexValues(IndexBuffer buffer, ByteString keyBytes)
  {
    return buffer.getBufferedIndexValues(this, keyBytes);
  }

  /**
   * Check if an entry ID is in the set of IDs indexed by a given key.
   *
   * @param txn
   *          A database transaction.
   * @param key
   *          The index key.
   * @param entryID
   *          The entry ID.
   * @return true if the entry ID is indexed by the given key, false if it is not indexed by the
   *         given key, undefined if the key has exceeded the entry limit.
   * @throws StorageRuntimeException
   *           If an error occurs in the JE database.
   */
  ConditionResult containsID(ReadableStorage txn, ByteString key, EntryID entryID)
       throws StorageRuntimeException
  {
    ByteString value = txn.read(getName(), key);
    if (value != null)
    {
      EntryIDSet entryIDSet = newSetFromBytes(key, value);
      if (!entryIDSet.isDefined())
      {
        return ConditionResult.UNDEFINED;
      }
      return ConditionResult.valueOf(entryIDSet.contains(entryID));
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

  EntryIDSet read(ReadableStorage txn, ByteSequence key)
  {
    try
    {
      ByteString value = txn.read(getName(), key);
      if (value == null)
      {
        if(trusted)
        {
          return newDefinedSet();
        }
        else
        {
          return newUndefinedSet();
        }
      }
      return newSetFromBytes(key, value);
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      return newUndefinedSet();
    }
  }

  /**
   * Reads a range of keys and collects all their entry IDs into a
   * single set.
   *
   * @param txn The transaction to use for the operation
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
  EntryIDSet readRange(ReadableStorage txn,
      ByteSequence lower, ByteSequence upper, boolean lowerIncluded, boolean upperIncluded)
  {
    // If this index is not trusted, then just return an undefined id set.
    if (!trusted)
    {
      return newUndefinedSet();
    }

    try
    {
      // Total number of IDs found so far.
      int totalIDCount = 0;

      ArrayList<EntryIDSet> sets = new ArrayList<EntryIDSet>();

      Cursor cursor = txn.openCursor(getName());
      try
      {
        boolean success;
        // Set the lower bound if necessary.
        if (lower.length() > 0)
        {
          // Initialize the cursor to the lower bound.
          success = cursor.positionToKeyOrNext(lower);

          // Advance past the lower bound if necessary.
          if (success && !lowerIncluded && cursor.getKey().equals(lower))
          {
            // Do not include the lower value.
            success = cursor.next();
          }
        }
        else
        {
          success = cursor.next();
        }

        if (!success)
        {
          // There are no values.
          return newDefinedSet();
        }

        // Step through the keys until we hit the upper bound or the last key.
        while (success)
        {
          // Check against the upper bound if necessary
          if (upper.length() > 0)
          {
            int cmp = cursor.getKey().compareTo(upper);
            if (cmp > 0 || (cmp == 0 && !upperIncluded))
            {
              break;
            }
          }

          EntryIDSet set = newSetFromBytes(cursor.getKey(), cursor.getValue());
          if (!set.isDefined())
          {
            // There is no point continuing.
            return set;
          }
          totalIDCount += set.size();
          if (cursorEntryLimit > 0 && totalIDCount > cursorEntryLimit)
          {
            // There are too many. Give up and return an undefined list.
            return newUndefinedSet();
          }
          sets.add(set);
          success = cursor.next();
        }

        return newSetFromUnion(sets);
      }
      finally
      {
        cursor.close();
      }
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      return newUndefinedSet();
    }
  }

  int getEntryLimitExceededCount()
  {
    return entryLimitExceededCount;
  }

  void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry, IndexingOptions options)
      throws StorageRuntimeException
  {
    HashSet<ByteString> addKeys = new HashSet<ByteString>();
    indexer.indexEntry(entry, addKeys, options);

    for (ByteString keyBytes : addKeys)
    {
      insertID(buffer, keyBytes, entryID);
    }
  }

  void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry, IndexingOptions options)
      throws StorageRuntimeException
  {
    HashSet<ByteString> delKeys = new HashSet<ByteString>();
    indexer.indexEntry(entry, delKeys, options);

    for (ByteString keyBytes : delKeys)
    {
      removeID(buffer, keyBytes, entryID);
    }
  }

  void modifyEntry(IndexBuffer buffer, EntryID entryID, Entry oldEntry, Entry newEntry, List<Modification> mods,
      IndexingOptions options) throws StorageRuntimeException
  {
    TreeMap<ByteString, Boolean> modifiedKeys = new TreeMap<ByteString, Boolean>();
    indexer.modifyEntry(oldEntry, newEntry, mods, modifiedKeys, options);

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

  boolean setIndexEntryLimit(int indexEntryLimit)
  {
    final boolean rebuildRequired = this.indexEntryLimit < indexEntryLimit && entryLimitExceededCount > 0;
    this.indexEntryLimit = indexEntryLimit;
    return rebuildRequired;
  }

  final void setIndexer(Indexer indexer)
  {
    this.indexer = indexer;
  }

  int getIndexEntryLimit()
  {
    return indexEntryLimit;
  }

  synchronized void setTrusted(WriteableStorage txn, boolean trusted) throws StorageRuntimeException
  {
    this.trusted = trusted;
    state.putIndexTrustState(txn, this, trusted);
  }

  synchronized boolean isTrusted()
  {
    return trusted;
  }

  synchronized boolean isRebuildRunning()
  {
    return false; // FIXME inline?
  }

  boolean getMaintainCount()
  {
    return maintainCount;
  }
}
