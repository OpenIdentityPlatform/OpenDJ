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

import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.CursorTransformer.ValueTransformer;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.SequentialCursorDecorator;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * Store counters associated to a key. Counter value is sharded amongst multiple keys to allow concurrent update without
 * contention (at the price of a slower read).
 */
final class ShardedCounter extends AbstractTree
{
  /**
   * Must be a power of 2
   * @see <a href="http://en.wikipedia.org/wiki/Modulo_operation#Performance_issues">Performance issues</a>
   */
  private static final long SHARD_COUNT = 256;

  private static final ValueTransformer<ByteString, ByteString, Long, NeverThrowsException> TO_LONG =
      new ValueTransformer<ByteString, ByteString, Long, NeverThrowsException>()
      {
        @Override
        public Long transform(ByteString key, ByteString value)
        {
          return decodeValue(value);
        }
      };

  private static final Function<ByteString, ByteString, NeverThrowsException> TO_KEY =
      new Function<ByteString, ByteString, NeverThrowsException>()
      {
        @Override
        public ByteString apply(ByteString shardedKey)
        {
          // -1 to remove the shard id.
          return shardedKey.subSequence(0, shardedKey.length() - 1);
        }
      };

  ShardedCounter(TreeName name)
  {
    super(name);
  }

  SequentialCursor<ByteString, Void> openCursor(ReadableTransaction txn)
  {
    return new UniqueKeysCursor<>(transformKeysAndValues(
        txn.openCursor(getName()), TO_KEY,
        CursorTransformer.<ByteString, ByteString, Void> constant(null)));
  }

  private Cursor<ByteString, Long> openCursor0(ReadableTransaction txn)
  {
    return transformKeysAndValues(txn.openCursor(getName()), TO_KEY, TO_LONG);
  }

  void addCount(final WriteableTransaction txn, ByteSequence key, final long delta)
  {
    txn.update(getName(), getShardedKey(key), new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldValue)
      {
        final long currentValue = oldValue == null ? 0 : decodeValue(oldValue.toByteString());
        return encodeValue(currentValue + delta);
      }
    });
  }

  void importPut(Importer importer, ByteSequence key, long delta)
  {
    if (delta != 0)
    {
      importer.put(getName(), getShardedKey(key), encodeValue(delta));
    }
  }

  long getCount(final ReadableTransaction txn, ByteSequence key)
  {
    long counterValue = 0;
    try (final SequentialCursor<ByteString, Long> cursor = new ShardCursor(openCursor0(txn), key))
    {
      while (cursor.next())
      {
        counterValue += cursor.getValue();
      }
    }
    return counterValue;
  }

  long removeCount(final WriteableTransaction txn, ByteSequence key)
  {
    long counterValue = 0;
    try (final SequentialCursor<ByteString, Long> cursor = new ShardCursor(openCursor0(txn), key))
    {
      // Iterate over and remove all the thread local shards
      while (cursor.next())
      {
        counterValue += cursor.getValue();
        cursor.delete();
      }
    }
    return counterValue;
  }

  static long decodeValue(ByteString value)
  {
    switch (value.length())
    {
    case 1:
      return value.byteAt(0);
    case (Integer.SIZE / Byte.SIZE):
      return value.toInt();
    case (Long.SIZE / Byte.SIZE):
      return value.toLong();
    default:
      throw new IllegalArgumentException("Unsupported sharded-counter value format.");
    }
  }

  static ByteString encodeValue(long value)
  {
    final byte valueAsByte = (byte) value;
    if (valueAsByte == value)
    {
      return ByteString.wrap(new byte[] { valueAsByte });
    }
    final int valueAsInt = (int) value;
    if (valueAsInt == value)
    {
      return ByteString.valueOfInt(valueAsInt);
    }
    return ByteString.valueOfLong(value);
  }

  @Override
  public String valueToString(ByteString value)
  {
    return String.valueOf(decodeValue(value));
  }

  private static ByteSequence getShardedKey(ByteSequence key)
  {
    final byte bucket = (byte) (Thread.currentThread().getId() & (SHARD_COUNT - 1));
    return new ByteStringBuilder(key.length() + ByteStringBuilder.MAX_COMPACT_SIZE).appendBytes(key).appendByte(bucket);
  }

  /** Restricts a cursor to the shards of a specific key. */
  private final class ShardCursor extends SequentialCursorDecorator<Cursor<ByteString, Long>, ByteString, Long>
  {
    private final ByteSequence targetKey;
    private boolean initialized;

    ShardCursor(Cursor<ByteString, Long> delegate, ByteSequence targetKey)
    {
      super(delegate);
      this.targetKey = targetKey;
    }

    @Override
    public boolean next()
    {
      if (!initialized)
      {
        initialized = true;
        return delegate.positionToKeyOrNext(targetKey) && isOnTargetKey();
      }
      return delegate.next() && isOnTargetKey();
    }

    private boolean isOnTargetKey()
    {
      return targetKey.equals(delegate.getKey());
    }
  }

  /**
   * Cursor that returns unique keys and null values. Ensure that {@link #getKey()} will return a different key after
   * each {@link #next()}.
   */
  private static final class UniqueKeysCursor<K> implements SequentialCursor<K, Void>
  {
    private final Cursor<K, ?> delegate;
    private boolean isDefined;
    private K key;

    private UniqueKeysCursor(Cursor<K, ?> cursor)
    {
      this.delegate = cursor;
      if (!delegate.isDefined())
      {
        delegate.next();
      }
    }

    @Override
    public boolean next()
    {
      isDefined = delegate.isDefined();
      if (isDefined)
      {
        key = delegate.getKey();
        skipEntriesWithSameKey();
      }
      return isDefined;
    }

    private void skipEntriesWithSameKey()
    {
      throwIfUndefined(this);
      while (delegate.next() && key.equals(delegate.getKey()))
      {
        // Skip all entries having the same key.
      }
      // Delegate is one step beyond. When delegate.isDefined() return false, we have to return true once more.
      isDefined = true;
    }

    @Override
    public boolean isDefined()
    {
      return isDefined;
    }

    @Override
    public K getKey() throws NoSuchElementException
    {
      throwIfUndefined(this);
      return key;
    }

    @Override
    public Void getValue() throws NoSuchElementException
    {
      throwIfUndefined(this);
      return null;
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
      key = null;
      delegate.close();
    }

    private static void throwIfUndefined(SequentialCursor<?, ?> cursor)
    {
      if (!cursor.isDefined())
      {
        throw new NoSuchElementException();
      }
    }
  }
}
