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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * Store a counter associated to a key. Counter value is sharded amongst multiple keys to allow concurrent
 * update without contention (at the price of a slower read).
 */
@SuppressWarnings("javadoc")
final class ID2Count extends AbstractTree
{
  /**
   * Must be a power of 2 @see <a href="http://en.wikipedia.org/wiki/Modulo_operation#Performance_issues">Performance
   * issues</a>
   */
  private static final long SHARD_COUNT = 4096;
  private static final int LONG_SIZE = Long.SIZE / Byte.SIZE;
  private static final EntryID TOTAL_COUNT_ENTRY_ID = new EntryID(ByteStringBuilder.COMPACTED_MAX_VALUE);

  ID2Count(TreeName name)
  {
    super(name);
  }

  Cursor<EntryID, Long> openCursor(ReadableTransaction txn) {
    return CursorTransformer.transformKeysAndValues(txn.openCursor(getName()),
        new Function<ByteString, EntryID, Exception>()
        {
          @Override
          public EntryID apply(ByteString value) throws Exception
          {
            return new EntryID(value.asReader().getCompactUnsigned());
          }
        }, new CursorTransformer.ValueTransformer<ByteString, ByteString, Long, NeverThrowsException>()
        {
          @Override
          public Long transform(ByteString key, ByteString value) throws NeverThrowsException
          {
            return fromValue(value);
          }
        });
  }

  /**
   * Add a value to the counter associated to the given key
   * @param txn Database transaction
   * @param entryID The entryID identifying to the counter
   * @param delta The value to add. Can be negative to decrease counter value.
   */
  void addDelta(WriteableTransaction txn, EntryID entryID, final long delta)
  {
    Reject.ifTrue(entryID.longValue() >= TOTAL_COUNT_ENTRY_ID.longValue(), "EntryID overflow.");

    addToCounter(txn, entryID, delta);
    addToCounter(txn, TOTAL_COUNT_ENTRY_ID, delta);
  }

  private void addToCounter(WriteableTransaction txn, EntryID entryID, final long delta)
  {
    final ByteSequence shardedKey = getShardedKey(entryID);
    txn.update(getName(), shardedKey, new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldValue)
      {
        final long currentValue = oldValue != null ? oldValue.asReader().getLong() : 0;
        return toValue(currentValue + delta);
      }
    });
  }

  void importPut(Importer importer, EntryID entryID, long total)
  {
    Reject.ifTrue(entryID.longValue() >= TOTAL_COUNT_ENTRY_ID.longValue(), "EntryID overflow.");
    importPut0(importer, entryID, total);
  }

  void importPutTotalCount(Importer importer, long total)
  {
    importPut0(importer, TOTAL_COUNT_ENTRY_ID, total);
  }

  private void importPut0(Importer importer, EntryID entryID, final long delta)
  {
    Reject.ifNull(importer, "importer must not be null");
    final ByteSequence shardedKey = getShardedKey(entryID);
    importer.put(getName(), shardedKey, toValue(delta));
  }

  private ByteSequence getShardedKey(EntryID entryID)
  {
    final long bucket = Thread.currentThread().getId() & (SHARD_COUNT - 1);
    return getKeyFromEntryIDAndBucket(entryID, bucket);
  }

  ByteSequence toKey(EntryID entryID)
  {
    if (entryID == null)
    {
      entryID = TOTAL_COUNT_ENTRY_ID;
    }
    return getShardedKey(entryID);
  }

  ByteString toValue(final long count)
  {
    return ByteString.valueOf(count);
  }

  long fromValue(ByteString value)
  {
    return value.toLong();
  }

  /**
   * Get the counter value for the specified key
   * @param txn The transaction
   * @param entryID The entryID identifying to the counter
   * @return Value of the counter. 0 if no counter is associated yet.
   */
  long getCount(ReadableTransaction txn, EntryID entryID)
  {
    long counterValue = 0;
    try(final Cursor<EntryID, Long> cursor = openCursor(txn)) {
      cursor.positionToKeyOrNext(getKeyFromEntryID(entryID));
      while (cursor.isDefined() && cursor.getKey().equals(entryID))
      {
        counterValue += cursor.getValue().longValue();
        cursor.next();
      }
    }
    return counterValue;
  }

  private static final ByteSequence getKeyFromEntryID(EntryID entryID) {
    return new ByteStringBuilder(LONG_SIZE).appendCompactUnsigned(entryID.longValue());
  }

  private static final ByteSequence getKeyFromEntryIDAndBucket(EntryID entryID, long bucket) {
    return new ByteStringBuilder(LONG_SIZE + LONG_SIZE).appendCompactUnsigned(entryID.longValue())
        .appendCompactUnsigned(bucket);
  }

  /**
   * Get the total counter value. The total counter maintain the sum of all
   * the counter contained in this tree.
   * @param txn The transaction
   * @return Sum of all the counter contained in this tree
   */
  long getTotalCount(ReadableTransaction txn)
  {
    return getCount(txn, TOTAL_COUNT_ENTRY_ID);
  }

  /**
   * Delete the counter associated to the given key
   * @param txn The transaction
   * @param entryID The entryID identifying the counter
   * @return Value of the counter before it's deletion.
   */
  long deleteCount(WriteableTransaction txn, EntryID entryID)
  {
    long counterValue = 0;
    try(final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName())) {
      final ByteSequence encodedEntryID = getKeyFromEntryID(entryID);
      if (cursor.positionToKeyOrNext(encodedEntryID)) {
        while (cursor.getKey().startsWith(encodedEntryID))
        {
          counterValue += cursor.getValue().asReader().getLong();
          txn.delete(getName(), cursor.getKey());
          cursor.next();
        }
      }
    }
    addToCounter(txn, TOTAL_COUNT_ENTRY_ID, -counterValue);

    return counterValue;
  }
}
