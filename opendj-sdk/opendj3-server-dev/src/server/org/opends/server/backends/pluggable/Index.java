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
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.backends.pluggable.IndexBuffer.BufferedIndexValues;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.JebMessages.*;

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
  public Indexer indexer;

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
   * A flag to indicate if a rebuild process is running on this index.
   * During the rebuild process, we assume that no entryIDSets are
   * accurate and return an undefined set on all read operations.
   * However all write operations will succeed. The rebuildRunning
   * flag overrides all behaviors of the trusted flag.
   */
  private boolean rebuildRunning;

  /** Thread local area to store per thread cursors. */
  private final ThreadLocal<Cursor> curLocal = new ThreadLocal<Cursor>();

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
   * @param storage The JE Storage
   * @param entryContainer The database entryContainer holding this index.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public Index(TreeName name, Indexer indexer, State state,
        int indexEntryLimit, int cursorEntryLimit, boolean maintainCount,
        Storage storage, WriteableStorage txn, EntryContainer entryContainer)
      throws StorageRuntimeException
  {
    super(name, storage, entryContainer);
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

  /**
   * Add an add entry ID operation into a index buffer.
   *
   * @param buffer The index buffer to insert the ID into.
   * @param keyBytes         The index key bytes.
   * @param entryID     The entry ID.
   */
  public void insertID(IndexBuffer buffer, ByteString keyBytes, EntryID entryID)
  {
    getBufferedIndexValues(buffer, keyBytes).addEntryID(keyBytes, entryID);
  }

  /**
   * Update the set of entry IDs for a given key.
   *
   * @param txn A database transaction, or null if none is required.
   * @param key The database key.
   * @param deletedIDs The IDs to remove for the key.
   * @param addedIDs the IDs to add for the key.
   * @throws StorageRuntimeException If a database error occurs.
   */
  void updateKey(WriteableStorage txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws StorageRuntimeException
  {
    if(deletedIDs == null && addedIDs == null)
    {
      boolean success = delete(txn, key);
      if (success && logger.isTraceEnabled())
      {
        StringBuilder builder = new StringBuilder();
        StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
        logger.trace("The expected key does not exist in the index %s.\nKey:%s ", treeName, builder);
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
      updateKeyWithRMW(txn, key, deletedIDs, addedIDs);
    }
    else
    {
      ByteString value = read(txn, key, false);
      if(value != null)
      {
        EntryIDSet entryIDList = new EntryIDSet(key, value);
        if (entryIDList.isDefined())
        {
          updateKeyWithRMW(txn, key, deletedIDs, addedIDs);
        }
      }
      else
      {
        if (deletedIDs != null && trusted && !rebuildRunning)
        {
          logIndexCorruptError(txn, key);
        }

        if ((rebuildRunning || trusted) && isNotNullOrEmpty(addedIDs))
        {
          if(!insert(txn, key, addedIDs.toByteString()))
          {
            updateKeyWithRMW(txn, key, deletedIDs, addedIDs);
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

  private void updateKeyWithRMW(WriteableStorage txn,
                                           ByteString key,
                                           EntryIDSet deletedIDs,
                                           EntryIDSet addedIDs)
      throws StorageRuntimeException
  {
    final ByteString value = read(txn, key, true);
    if(value != null)
    {
      EntryIDSet entryIDList = computeEntryIDList(key, value, deletedIDs, addedIDs);
      ByteString after = entryIDList.toByteString();
      if (after != null)
      {
        put(txn, key, after);
      }
      else
      {
        // No more IDs, so remove the key. If index is not
        // trusted then this will cause all subsequent reads
        // for this key to return undefined set.
        delete(txn, key);
      }
    }
    else
    {
      if (deletedIDs != null && trusted && !rebuildRunning)
      {
        logIndexCorruptError(txn, key);
      }

      if ((rebuildRunning || trusted) && isNotNullOrEmpty(addedIDs))
      {
        insert(txn, key, addedIDs.toByteString());
      }
    }
  }

  private EntryIDSet computeEntryIDList(ByteString key, ByteString value, EntryIDSet deletedIDs,
      EntryIDSet addedIDs)
  {
    EntryIDSet entryIDList = new EntryIDSet(key, value);
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
            StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
            logger.trace("Index entry exceeded in index %s. " +
                "Limit: %d. ID list size: %d.\nKey:%s",
                treeName, indexEntryLimit, idCountDelta + addedIDs.size(), builder);

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
  public void removeID(IndexBuffer buffer, ByteString keyBytes, EntryID entryID)
  {
    getBufferedIndexValues(buffer, keyBytes).deleteEntryID(keyBytes, entryID);
  }

  private void logIndexCorruptError(WriteableStorage txn, ByteString key)
  {
    if (logger.isTraceEnabled())
    {
      StringBuilder builder = new StringBuilder();
      StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
      logger.trace("The expected key does not exist in the index %s.\nKey:%s", treeName, builder);
    }

    setTrusted(txn, false);
    logger.error(ERR_JEB_INDEX_CORRUPT_REQUIRES_REBUILD, treeName);
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
    return buffer.getBufferedIndexValues(this, keyBytes);
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
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public ConditionResult containsID(ReadableStorage txn, ByteString key, EntryID entryID)
       throws StorageRuntimeException
  {
    if(rebuildRunning)
    {
      return ConditionResult.UNDEFINED;
    }

    ByteString value = read(txn, key, false);
    if (value != null)
    {
      EntryIDSet entryIDList = new EntryIDSet(key, value);
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
   * @return The entry IDs indexed by this key.
   */
  public EntryIDSet readKey(ByteSequence key, ReadableStorage txn)
  {
    if(rebuildRunning)
    {
      return new EntryIDSet();
    }

    try
    {
      ByteString value = read(txn, key, false);
      if (value == null)
      {
        if(trusted)
        {
          return new EntryIDSet(key, null);
        }
        else
        {
          return new EntryIDSet();
        }
      }
      return new EntryIDSet(key, value);
    }
    catch (StorageRuntimeException e)
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
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public void writeKey(WriteableStorage txn, ByteString key, EntryIDSet entryIDList)
       throws StorageRuntimeException
  {
    ByteString value = entryIDList.toByteString();
    if (value != null)
    {
      if (!entryIDList.isDefined())
      {
        entryLimitExceededCount++;
      }
      put(txn, key, value);
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
  public EntryIDSet readRange(ReadableStorage txn,
      ByteSequence lower, ByteSequence upper, boolean lowerIncluded, boolean upperIncluded)
  {
    // If this index is not trusted, then just return an undefined id set.
    if(rebuildRunning || !trusted)
    {
      return new EntryIDSet();
    }

    try
    {
      // Total number of IDs found so far.
      int totalIDCount = 0;

      ArrayList<EntryIDSet> lists = new ArrayList<EntryIDSet>();

      Cursor cursor = txn.openCursor(treeName);
      try
      {
        boolean success;
        // Set the lower bound if necessary.
        if (lower.length() > 0)
        {
          // Initialize the cursor to the lower bound.
          success = cursor.positionToKeyOrNext(lower);

          // Advance past the lower bound if necessary.
          if (success
              && !lowerIncluded
              && ByteSequence.COMPARATOR.compare(cursor.getKey(), lower) == 0)
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
          return new EntryIDSet(lowerIncluded ? lower : null, null);
        }

        // Step through the keys until we hit the upper bound or the last key.
        while (success)
        {
          // Check against the upper bound if necessary
          if (upper.length() > 0)
          {
            int cmp = ByteSequence.COMPARATOR.compare(cursor.getKey(), upper);
            if (cmp > 0 || (cmp == 0 && !upperIncluded))
            {
              break;
            }
          }
          EntryIDSet list = new EntryIDSet(cursor.getKey(), cursor.getValue());
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
          success = cursor.next();
        }

        return EntryIDSet.unionOfSets(lists, false);
      }
      finally
      {
        cursor.close();
      }
    }
    catch (StorageRuntimeException e)
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
   * Close any cursors open against this index.
   *
   * @throws StorageRuntimeException  If a database error occurs.
   */
  public void closeCursor() throws StorageRuntimeException {
    Cursor cursor = curLocal.get();
    if(cursor != null) {
      cursor.close();
      curLocal.remove();
    }
  }

  /**
   * Update the index buffer for a deleted entry.
   *
   * @param buffer The index buffer to use to store the deleted keys
   * @param entryID     The entry ID.
   * @param entry       The entry to be indexed.
   * @param options     The indexing options to use
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry,
      IndexingOptions options) throws StorageRuntimeException, DirectoryException
  {
    HashSet<ByteString> addKeys = new HashSet<ByteString>();
    indexer.indexEntry(entry, addKeys, options);

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
   * @param options     The indexing options to use
   * @throws StorageRuntimeException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry,
      IndexingOptions options) throws StorageRuntimeException, DirectoryException
  {
    HashSet<ByteString> delKeys = new HashSet<ByteString>();
    indexer.indexEntry(entry, delKeys, options);

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
   * @param options The indexing options to use
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public void modifyEntry(IndexBuffer buffer,
                          EntryID entryID,
                          Entry oldEntry,
                          Entry newEntry,
                          List<Modification> mods, IndexingOptions options)
      throws StorageRuntimeException
  {
    TreeMap<ByteString, Boolean> modifiedKeys =
        new TreeMap<ByteString, Boolean>(ByteSequence.COMPARATOR);
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
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public synchronized void setTrusted(WriteableStorage txn, boolean trusted)
      throws StorageRuntimeException
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
    return rebuildRunning;
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
