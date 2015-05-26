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

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.State.IndexFlag.*;

import java.util.EnumSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.CursorTransformer.ValueTransformer;
import org.opends.server.backends.pluggable.EntryIDSet.EntryIDSetCodec;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * Represents an index implemented by a tree in which each key maps to a set of entry IDs. The key
 * is a byte array, and is constructed from some normalized form of an attribute value (or fragment
 * of a value) appearing in the entry.
 */
@SuppressWarnings("javadoc")
class DefaultIndex extends AbstractTree implements Index
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The limit on the number of entry IDs that may be indexed by one key. */
  private final State state;
  private final EntryContainer entryContainer;
  private int indexEntryLimit;
  private EntryIDSetCodec codec;

  /**
   * A flag to indicate if this index should be trusted to be consistent with the entries tree.
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
   *          The name of the index tree within the entryContainer.
   * @param state
   *          The state tree to persist index state info.
   * @param indexEntryLimit
   *          The configured limit on the number of entry IDs that may be indexed by one key.
   * @param entryContainer
   *          The entryContainer holding this index.
   * @throws StorageRuntimeException
   *           If an error occurs in the storage.
   */
  DefaultIndex(TreeName name, State state, int indexEntryLimit, EntryContainer entryContainer)
      throws StorageRuntimeException
  {
    super(name);
    this.indexEntryLimit = indexEntryLimit;
    this.state = state;
    this.entryContainer = entryContainer;
  }

  @Override
  final void open0(WriteableTransaction txn)
  {
    final EnumSet<IndexFlag> flags = state.getIndexFlags(txn, getName());
    codec = flags.contains(COMPACTED) ? CODEC_V2 : CODEC_V1;
    trusted = flags.contains(TRUSTED);
    if (!trusted && entryContainer.getHighestEntryID(txn).longValue() == 0)
    {
      // If there are no entries in the entry container then there
      // is no reason why this index can't be upgraded to trusted.
      setTrusted(txn, true);
    }
  }

  @Override
  public final Cursor<ByteString, EntryIDSet> openCursor(ReadableTransaction txn)
  {
    checkNotNull(txn, "txn must not be null");
    return CursorTransformer.transformValues(txn.openCursor(getName()),
        new ValueTransformer<ByteString, ByteString, EntryIDSet, NeverThrowsException>()
        {
          @Override
          public EntryIDSet transform(ByteString key, ByteString value) throws NeverThrowsException
          {
            return decodeValue(key, value);
          }
        });
  }

  EntryIDSet decodeValue(ByteSequence key, ByteString value)
  {
    return codec.decode(key, value);
  }

  ByteString toValue(EntryID entryID)
  {
    return codec.encode(newDefinedSet(entryID.longValue()));
  }

  ByteString toValue(EntryIDSet entryIDSet)
  {
    return codec.encode(entryIDSet);
  }

  ByteString toValue(ImportIDSet importIDSet)
  {
    return importIDSet.valueToByteString(codec);
  }

  // TODO JNR rename to importUpsert() ?
  @Override
  public final void importPut(Importer importer, ImportIDSet idsToBeAdded) throws StorageRuntimeException
  {
    Reject.ifNull(importer, "importer must not be null");
    Reject.ifNull(idsToBeAdded, "idsToBeAdded must not be null");
    ByteSequence key = idsToBeAdded.getKey();
    ByteString value = importer.read(getName(), key);
    if (value != null)
    {
      final EntryIDSet entryIDSet = decodeValue(key, value);
      final ImportIDSet importIDSet = new ImportIDSet(key, entryIDSet, indexEntryLimit);
      importIDSet.merge(idsToBeAdded);
      importer.put(getName(), key, toValue(importIDSet));
    }
    else
    {
      importer.put(getName(), key, toValue(idsToBeAdded));
    }
  }

  @Override
  public final void importRemove(Importer importer, ImportIDSet idsToBeRemoved) throws StorageRuntimeException
  {
    Reject.ifNull(importer, "importer must not be null");
    Reject.ifNull(idsToBeRemoved, "idsToBeRemoved must not be null");
    ByteSequence key = idsToBeRemoved.getKey();
    ByteString value = importer.read(getName(), key);
    if (value == null)
    {
      // Should never happen -- the keys should always be there.
      throw new IllegalStateException("Expected to have a value associated to key " + key + " for index " + getName());
    }

    final EntryIDSet entryIDSet = decodeValue(key, value);
    final ImportIDSet importIDSet = new ImportIDSet(key, entryIDSet, indexEntryLimit);
    importIDSet.remove(idsToBeRemoved);
    if (importIDSet.isDefined() && importIDSet.size() == 0)
    {
      importer.delete(getName(), key);
    }
    else
    {
      importer.put(getName(), key, toValue(importIDSet));
    }
  }

  @Override
  public final void update(final WriteableTransaction txn, final ByteString key, final EntryIDSet deletedIDs,
      final EntryIDSet addedIDs) throws StorageRuntimeException
  {
    /*
     * Check the special condition where both deletedIDs and addedIDs are null. This is used when
     * deleting entries must be completely removed.
     */
    if (deletedIDs == null && addedIDs == null)
    {
      boolean success = txn.delete(getName(), key);
      if (!success && logger.isTraceEnabled())
      {
        logger.trace("The expected key does not exist in the index %s.\nKey:%s ",
            getName(), key.toHexPlusAsciiString(4));
      }
      return;
    }

    // Handle cases where nothing is changed early to avoid DB access.
    if (isNullOrEmpty(deletedIDs) && isNullOrEmpty(addedIDs))
    {
      return;
    }

    /*
     * Avoid taking a write lock on a record which has hit all IDs because it is likely to be a
     * point of contention.
     */
    if (!get(txn, key).isDefined())
    {
      return;
    }

    // The record is going to be changed in some way.
    txn.update(getName(), key, new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(final ByteSequence oldValue)
      {
        if (oldValue != null)
        {
          EntryIDSet entryIDSet = computeEntryIDSet(key, oldValue.toByteString(), deletedIDs, addedIDs);
          ByteString after = toValue(entryIDSet);
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
            return toValue(addedIDs);
          }
        }
        return null; // no change.
      }
    });
  }

  private static boolean isNullOrEmpty(EntryIDSet entryIDSet)
  {
    return entryIDSet == null || entryIDSet.size() == 0;
  }

  private static boolean isNotEmpty(EntryIDSet entryIDSet)
  {
    return entryIDSet != null && entryIDSet.size() > 0;
  }

  private EntryIDSet computeEntryIDSet(ByteString key, ByteString value, EntryIDSet deletedIDs, EntryIDSet addedIDs)
  {
    EntryIDSet entryIDSet = decodeValue(key, value);
    if (addedIDs != null)
    {
      if (entryIDSet.isDefined() && indexEntryLimit > 0)
      {
        final long nbDeleted = deletedIDs != null ? deletedIDs.size() : 0;
        final long idCountDelta = addedIDs.size() - nbDeleted;
        if (idCountDelta + entryIDSet.size() >= indexEntryLimit)
        {
          entryIDSet = newUndefinedSetWithKey(key);
          if (logger.isTraceEnabled())
          {
            logger.trace("Index entry exceeded in index %s. " + "Limit: %d. ID list size: %d.\nKey:%s", getName(),
                indexEntryLimit, idCountDelta + addedIDs.size(), key.toHexPlusAsciiString(4));
          }
          return entryIDSet;
        }
      }
      entryIDSet.addAll(addedIDs);
    }
    if (deletedIDs != null)
    {
      entryIDSet.removeAll(deletedIDs);
    }
    return entryIDSet;
  }

  private void logIndexCorruptError(WriteableTransaction txn, ByteString key)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("The expected key does not exist in the index %s.\nKey:%s", getName(), key.toHexPlusAsciiString(4));
    }

    setTrusted(txn, false);
    logger.error(ERR_INDEX_CORRUPT_REQUIRES_REBUILD, getName());
  }

  @Override
  public final EntryIDSet get(ReadableTransaction txn, ByteSequence key)
  {
    try
    {
      ByteString value = txn.read(getName(), key);
      if (value != null)
      {
        return decodeValue(key, value);
      }
      return trusted ? newDefinedSet() : newUndefinedSet();
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      return newUndefinedSet();
    }
  }

  @Override
  public final boolean setIndexEntryLimit(int indexEntryLimit)
  {
    final boolean rebuildRequired = this.indexEntryLimit < indexEntryLimit;
    this.indexEntryLimit = indexEntryLimit;
    return rebuildRequired;
  }

  @Override
  public final int getIndexEntryLimit()
  {
    return indexEntryLimit;
  }

  @Override
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

  @Override
  public final boolean isTrusted()
  {
    return trusted;
  }
}
