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

import static org.forgerock.util.Reject.checkNotNull;
import static org.opends.messages.JebMessages.ERR_JEB_INDEX_CORRUPT_REQUIRES_REBUILD;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.State.IndexFlag.COMPACTED;
import static org.opends.server.backends.pluggable.State.IndexFlag.TRUSTED;

import java.util.EnumSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.CursorTransformer.ValueTransformer;
import org.opends.server.backends.pluggable.EntryIDSet.EntryIDSetCodec;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.util.StaticUtils;

/**
 * Represents an index implemented by a tree in which each key maps to a set of entry IDs. The key
 * is a byte array, and is constructed from some normalized form of an attribute value (or fragment
 * of a value) appearing in the entry.
 */
class DefaultIndex extends AbstractDatabaseContainer implements Index
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The limit on the number of entry IDs that may be indexed by one key. */
  private int indexEntryLimit;
  /**
   * Whether to maintain a count of IDs for a key once the entry limit has exceeded.
   */
  private final boolean maintainCount;

  private final State state;

  private final EntryIDSetCodec codec;

  /**
   * A flag to indicate if this index should be trusted to be consistent with the entries database.
   * If not trusted, we assume that existing entryIDSets for a key is still accurate. However, keys
   * that do not exist are undefined instead of an empty entryIDSet. The following rules will be
   * observed when the index is not trusted: - no entryIDs will be added to a non-existing key. -
   * undefined entryIdSet will be returned whenever a key is not found.
   */
  private volatile boolean trusted;

  /**
   * Create a new index object.
   *
   * @param name
   *          The name of the index database within the entryContainer.
   * @param state
   *          The state database to persist index state info.
   * @param indexEntryLimit
   *          The configured limit on the number of entry IDs that may be indexed by one key.
   * @param maintainCount
   *          Whether to maintain a count of IDs for a key once the entry limit has exceeded.
   * @param txn
   *          a non null database transaction
   * @param entryContainer
   *          The database entryContainer holding this index.
   * @throws StorageRuntimeException
   *           If an error occurs in the database.
   */
  DefaultIndex(TreeName name, State state, int indexEntryLimit, boolean maintainCount, WriteableTransaction txn,
      EntryContainer entryContainer) throws StorageRuntimeException
  {
    super(name);
    this.indexEntryLimit = indexEntryLimit;
    this.maintainCount = maintainCount;
    this.state = state;

    final EnumSet<IndexFlag> flags = state.getIndexFlags(txn, getName());
    this.codec = flags.contains(COMPACTED) ? CODEC_V2 : CODEC_V1;
    this.trusted = flags.contains(TRUSTED);
    if (!trusted && entryContainer.getHighestEntryID(txn).longValue() == 0)
    {
      // If there are no entries in the entry container then there
      // is no reason why this index can't be upgraded to trusted.
      setTrusted(txn, true);
    }
  }

  public final Cursor<ByteString, EntryIDSet> openCursor(ReadableTransaction txn)
  {
    checkNotNull(txn, "txn must not be null");
    return CursorTransformer.transformValues(txn.openCursor(getName()),
        new ValueTransformer<ByteString, ByteString, EntryIDSet, NeverThrowsException>()
        {
          @Override
          public EntryIDSet transform(ByteString key, ByteString value) throws NeverThrowsException
          {
            return codec.decode(key, value);
          }
        });
  }

  public final void importPut(WriteableTransaction txn, ImportIDSet idsToBeAdded) throws StorageRuntimeException
  {
    ByteSequence key = idsToBeAdded.getKey();
    ByteString value = txn.read(getName(), key);
    if (value != null)
    {
      final ImportIDSet importIDSet = new ImportIDSet(key, codec.decode(key, value), indexEntryLimit, maintainCount);
      importIDSet.merge(idsToBeAdded);
      txn.put(getName(), key, importIDSet.valueToByteString(codec));
    }
    else
    {
      txn.put(getName(), key, idsToBeAdded.valueToByteString(codec));
    }
  }

  public final void importRemove(WriteableTransaction txn, ImportIDSet idsToBeRemoved) throws StorageRuntimeException
  {
    ByteSequence key = idsToBeRemoved.getKey();
    ByteString value = txn.read(getName(), key);
    if (value != null)
    {
      final ImportIDSet importIDSet = new ImportIDSet(key, codec.decode(key, value), indexEntryLimit, maintainCount);
      importIDSet.remove(idsToBeRemoved);
      if (importIDSet.isDefined() && importIDSet.size() == 0)
      {
        txn.delete(getName(), key);
      }
      else
      {
        txn.put(getName(), key, importIDSet.valueToByteString(codec));
      }
    }
    else
    {
      // Should never happen -- the keys should always be there.
      throw new RuntimeException();
    }
  }

  public final void update(WriteableTransaction txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
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
      update0(txn, key, deletedIDs, addedIDs);
    }
    else if (get(txn, key).isDefined())
    {
      /*
       * Avoid taking a write lock on a record which has hit all IDs because it is likely to be a
       * point of contention.
       */
      update0(txn, key, deletedIDs, addedIDs);
    } // else the record exists but we've hit all IDs.
  }

  private boolean isNullOrEmpty(EntryIDSet entryIDSet)
  {
    return entryIDSet == null || entryIDSet.size() == 0;
  }

  private boolean isNotEmpty(EntryIDSet entryIDSet)
  {
    return entryIDSet != null && entryIDSet.size() > 0;
  }

  private void update0(final WriteableTransaction txn, final ByteString key, final EntryIDSet deletedIDs,
      final EntryIDSet addedIDs) throws StorageRuntimeException
  {
    txn.update(getName(), key, new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(final ByteSequence oldValue)
      {
        if (oldValue != null)
        {
          EntryIDSet entryIDSet = computeEntryIDSet(key, oldValue.toByteString(), deletedIDs, addedIDs);
          ByteString after = codec.encode(entryIDSet);
          /*
           * If there are no more IDs then return null indicating that the record should be removed.
           * If index is not trusted then this will cause all subsequent reads for this key to
           * return undefined set.
           */
          return after.isEmpty() ? null : after;
        }
        else if (trusted)
        {
          if (deletedIDs != null)
          {
            logIndexCorruptError(txn, key);
          }
          if (isNotEmpty(addedIDs))
          {
            return codec.encode(addedIDs);
          }
        }
        return null; // no change.
      }
    });
  }

  private EntryIDSet computeEntryIDSet(ByteString key, ByteString value, EntryIDSet deletedIDs, EntryIDSet addedIDs)
  {
    EntryIDSet entryIDSet = codec.decode(key, value);
    if (addedIDs != null)
    {
      if (entryIDSet.isDefined() && indexEntryLimit > 0)
      {
        long idCountDelta = addedIDs.size();
        if (deletedIDs != null)
        {
          idCountDelta -= deletedIDs.size();
        }
        if (idCountDelta + entryIDSet.size() >= indexEntryLimit)
        {
          if (maintainCount)
          {
            entryIDSet = newUndefinedSetWithSize(key, entryIDSet.size() + idCountDelta);
          }
          else
          {
            entryIDSet = newUndefinedSet();
          }

          if (logger.isTraceEnabled())
          {
            StringBuilder builder = new StringBuilder();
            StaticUtils.byteArrayToHexPlusAscii(builder, key.toByteArray(), 4);
            logger.trace("Index entry exceeded in index %s. " + "Limit: %d. ID list size: %d.\nKey:%s", getName(),
                indexEntryLimit, idCountDelta + addedIDs.size(), builder);

          }
        }
        else
        {
          entryIDSet.addAll(addedIDs);
          if (deletedIDs != null)
          {
            entryIDSet.removeAll(deletedIDs);
          }
        }
      }
      else
      {
        entryIDSet.addAll(addedIDs);
        if (deletedIDs != null)
        {
          entryIDSet.removeAll(deletedIDs);
        }
      }
    }
    else if (deletedIDs != null)
    {
      entryIDSet.removeAll(deletedIDs);
    }
    return entryIDSet;
  }

  private void logIndexCorruptError(WriteableTransaction txn, ByteString key)
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

  public final EntryIDSet get(ReadableTransaction txn, ByteSequence key)
  {
    try
    {
      ByteString value = txn.read(getName(), key);
      if (value != null)
      {
        return codec.decode(key, value);
      }
      return trusted ? newDefinedSet() : newUndefinedSet();
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      return newUndefinedSet();
    }
  }

  public final boolean setIndexEntryLimit(int indexEntryLimit)
  {
    final boolean rebuildRequired = this.indexEntryLimit < indexEntryLimit;
    this.indexEntryLimit = indexEntryLimit;
    return rebuildRequired;
  }

  public final int getIndexEntryLimit()
  {
    return indexEntryLimit;
  }

  public final synchronized void setTrusted(WriteableTransaction txn, boolean trusted) throws StorageRuntimeException
  {
    this.trusted = trusted;
    if (trusted)
    {
      state.addFlagsToIndex(txn, getName(), TRUSTED);
    }
    else
    {
      state.removeFlagsFromIndex(txn, getName(), TRUSTED);
    }
  }

  public final boolean isTrusted()
  {
    return trusted;
  }

  public final boolean getMaintainCount()
  {
    return maintainCount;
  }
}
