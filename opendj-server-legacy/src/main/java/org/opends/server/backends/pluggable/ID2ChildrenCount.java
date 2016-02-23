/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.CursorTransformer.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Function;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.Collector;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

import com.forgerock.opendj.util.PackedLong;

/** Maintain counters reflecting the total number of entries and the number of immediate children for each entry. */
final class ID2ChildrenCount extends AbstractTree
{
  private static final EntryID TOTAL_COUNT_ENTRY_ID = new EntryID(PackedLong.COMPACTED_MAX_VALUE);

  private static final Function<ByteString, EntryID, NeverThrowsException> TO_ENTRY_ID =
      new Function<ByteString, EntryID, NeverThrowsException>()
      {
        @Override
        public EntryID apply(ByteString value) throws NeverThrowsException
        {
          return new EntryID(value.asReader().readCompactUnsignedLong());
        }
      };

  private final ShardedCounter counter;

  ID2ChildrenCount(TreeName name)
  {
    super(name);
    this.counter = new ShardedCounter(name);
  }

  SequentialCursor<EntryID, Void> openCursor(ReadableTransaction txn)
  {
    return transformKeysAndValues(counter.openCursor(txn),
        TO_ENTRY_ID, CursorTransformer.<ByteString, Void> keepValuesUnchanged());
  }

  /**
   * Updates the number of children for a given entry without updating the total number of entries.
   * <p>
   * Implementation note: this method accepts a {@code null} entryID in order to eliminate null checks in client code.
   * In particular, client code has to deal with the special case where a target entry does not have a parent because
   * the target entry is a base entry within the backend.
   *
   * @param txn storage transaction
   * @param entryID The entryID identifying to the counter, which may be
   *                {@code null} in which case calling this method has no effect.
   * @param delta The value to add. Can be negative to decrease counter value.
   */
  void updateCount(final WriteableTransaction txn, final EntryID entryID, final long delta) {
    if (entryID != null)
    {
      addToCounter(txn, entryID, delta);
    }
  }

  /**
   * Updates the total number of entries which should be the sum of all counters.
   * @param txn storage transaction
   * @param delta The value to add. Can be negative to decrease counter value.
   */
  void updateTotalCount(final WriteableTransaction txn, final long delta) {
    addToCounter(txn, TOTAL_COUNT_ENTRY_ID, delta);
  }

  private void addToCounter(WriteableTransaction txn, EntryID entryID, final long delta)
  {
    counter.addCount(txn, toKey(entryID), delta);
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
    counter.importPut(importer, toKey(entryID), delta);
  }

  @Override
  public String keyToString(ByteString key)
  {
    ByteSequenceReader keyReader = key.asReader();
    long keyID = keyReader.readCompactUnsignedLong();
    long shardBucket = keyReader.readByte();
    return (keyID == TOTAL_COUNT_ENTRY_ID.longValue() ? "Total Children Count" : keyID) + "#" + shardBucket;
  }

  @Override
  public String valueToString(ByteString value)
  {
    return counter.valueToString(value);
  }

  @Override
  public ByteString generateKey(String data)
  {
    return new EntryID(Long.parseLong(data)).toByteString();
  }

  /**
   * Get the number of children for the given entry.
   * @param txn storage transaction
   * @param entryID The entryID identifying to the counter
   * @return Value of the counter. 0 if no counter is associated yet.
   */
  long getCount(ReadableTransaction txn, EntryID entryID)
  {
    return counter.getCount(txn, toKey(entryID));
  }

  /**
   * Get the total number of entries.
   * @param txn storage transaction
   * @return Sum of all the counter contained in this tree
   */
  long getTotalCount(ReadableTransaction txn)
  {
    return getCount(txn, TOTAL_COUNT_ENTRY_ID);
  }

  /**
   * Removes the counter associated to the given entry, but does not update the total count.
   * @param txn storage transaction
   * @param entryID The entryID identifying the counter
   * @return Value of the counter before it's deletion.
   */
  long removeCount(final WriteableTransaction txn, final EntryID entryID) {
    return counter.removeCount(txn, toKey(entryID));
  }

  private static ByteSequence toKey(EntryID entryID)
  {
    return new ByteStringBuilder(ByteStringBuilder.MAX_COMPACT_SIZE).appendCompactUnsigned(entryID.longValue());
  }

  static Collector<Long, ByteString> getSumLongCollectorInstance()
  {
    return ShardedCounterCollector.INSTANCE;
  }

  /**
   * {@link Collector} that accepts sharded-counter values encoded into {@link ByteString} objects and produces a
   * {@link ByteString} representing the sum of the sharded-counter values.
   */
  private static final class ShardedCounterCollector implements Collector<Long, ByteString>
  {
    private static final Collector<Long, ByteString> INSTANCE = new ShardedCounterCollector();

    @Override
    public Long get()
    {
      return 0L;
    }

    @Override
    public Long accept(Long resultContainer, ByteString value)
    {
      return resultContainer + ShardedCounter.decodeValue(value);
    }

    @Override
    public ByteString merge(Long resultContainer)
    {
      return ShardedCounter.encodeValue(resultContainer);
    }
  }
}
