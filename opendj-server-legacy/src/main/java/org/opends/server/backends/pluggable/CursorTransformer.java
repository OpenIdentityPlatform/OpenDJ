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

import static org.forgerock.util.Reject.*;

import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.SequentialCursor;

/**
 * Transforms the keys and values of a cursor from their original types to others. Typically used for
 * data serialization,deserialization.
 *
 * @param <KI>
 *          Original cursor's key type
 * @param <VI>
 *          Original cursor's value type
 * @param <KO>
 *          Transformed cursor's key type
 * @param <VO>
 *          Transformed cursor's value type
 */
final class CursorTransformer<KI, VI, KO, VO> implements Cursor<KO, VO>
{
  /**
   * Allow to transform a cursor value given the key and the original value.
   * @param <KI> Original type of the cursor's key
   * @param <VI> Original type of the cursor's value
   * @param <VO> New transformed type of the value
   * @param <E> Possible exception type
   */
  interface ValueTransformer<KI, VI, VO, E extends Exception>
  {
    VO transform(KI key, VI value) throws E;
  }

  private final static ValueTransformer<?, ?, ?, NeverThrowsException> KEEP_VALUES_UNCHANGED =
      new ValueTransformer<Object, Object, Object, NeverThrowsException>()
      {
        @Override
        public Object transform(Object key, Object value) throws NeverThrowsException
        {
          return value;
        }
      };

  private final Cursor<KI, VI> input;
  private final Function<KI, KO, ? extends Exception> keyTransformer;
  private final ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer;
  private KO cachedTransformedKey;
  private VO cachedTransformedValue;

  static <KI, VI, KO, VO> Cursor<KO, VO> transformKeysAndValues(Cursor<KI, VI> input,
      Function<KI, KO, ? extends Exception> keyTransformer,
      ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer)
  {
    return new CursorTransformer<>(input, keyTransformer, valueTransformer);
  }

  static <KI, VI, KO, VO> SequentialCursor<KO, VO> transformKeysAndValues(SequentialCursor<KI, VI> input,
      Function<KI, KO, ? extends Exception> keyTransformer,
      ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer)
  {
    // SequentialCursorAdapter constructor never throws
    return new CursorTransformer<>(new SequentialCursorAdapter<>(input), keyTransformer, valueTransformer);
  }

  static <KI, VI, VO> Cursor<KI, VO> transformValues(Cursor<KI, VI> input,
      ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer)
  {
    return transformKeysAndValues(input, Functions.<KI>identityFunction(), valueTransformer);
  }

  static <KI, VI, VO> Cursor<KI, VO> transformValues(SequentialCursor<KI, VI> input,
      ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer)
  {
    // SequentialCursorAdapter constructor never throws
    return transformKeysAndValues(new SequentialCursorAdapter<>(input), Functions.<KI> identityFunction(),
        valueTransformer);
  }

  @SuppressWarnings("unchecked")
  static <K, V> ValueTransformer<K, V, V, NeverThrowsException> keepValuesUnchanged()
  {
    return (ValueTransformer<K, V, V, NeverThrowsException>) KEEP_VALUES_UNCHANGED;
  }

  static <K, VI, VO> ValueTransformer<K, VI, VO, NeverThrowsException> constant(final VO constant)
  {
    return new ValueTransformer<K, VI, VO, NeverThrowsException>()
    {
      @Override
      public VO transform(K key, VI value) throws NeverThrowsException
      {
        return constant;
      }
    };
  }

  private CursorTransformer(Cursor<KI, VI> input, Function<KI, KO, ? extends Exception> keyTransformer,
      ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer)
  {
    this.input = checkNotNull(input, "input must not be null");
    this.keyTransformer = checkNotNull(keyTransformer, "keyTransformer must not be null");
    this.valueTransformer = checkNotNull(valueTransformer, "valueTransformer must not be null");
  }

  @Override
  public void close()
  {
    input.close();
  }

  @Override
  public KO getKey()
  {
    if (cachedTransformedKey == null)
    {
      try
      {
        cachedTransformedKey = keyTransformer.apply(input.getKey());
      }
      catch (Exception e)
      {
        throw new TransformationException(e, input.getKey(), input.getValue());
      }
    }
    return cachedTransformedKey;
  }

  @Override
  public VO getValue()
  {
    if (cachedTransformedValue == null)
    {
      try
      {
        cachedTransformedValue = valueTransformer.transform(input.getKey(), input.getValue());
      }
      catch (Exception e)
      {
        throw new TransformationException(e, input.getKey(), input.getValue());
      }
    }
    return cachedTransformedValue;
  }

  @Override
  public void delete() throws NoSuchElementException, UnsupportedOperationException
  {
    // No need to clear the cached key/value. They will be updated when the cursor moves.
    input.delete();
  }

  @Override
  public boolean next()
  {
    clearCache();
    return input.next();
  }

  @Override
  public boolean positionToKey(final ByteSequence key)
  {
    clearCache();
    return input.positionToKey(key);
  }

  @Override
  public boolean positionToKeyOrNext(final ByteSequence key)
  {
    clearCache();
    return input.positionToKeyOrNext(key);
  }

  @Override
  public boolean positionToLastKey()
  {
    clearCache();
    return input.positionToLastKey();
  }

  @Override
  public boolean positionToIndex(int index)
  {
    return input.positionToIndex(index);
  }

  @Override
  public boolean isDefined()
  {
    return input.isDefined();
  }

  private void clearCache()
  {
    cachedTransformedKey = null;
    cachedTransformedValue = null;
  }

  /** Make a {@link SequentialCursor} looks like a {@link Cursor}. */
  static final class SequentialCursorAdapter<K, V> implements Cursor<K, V>
  {
    private final SequentialCursor<K, V> delegate;

    SequentialCursorAdapter(SequentialCursor<K, V> delegate)
    {
      this.delegate = delegate;
    }

    @Override
    public boolean next()
    {
      return delegate.next();
    }

    @Override
    public boolean isDefined()
    {
      return delegate.isDefined();
    }

    @Override
    public K getKey() throws NoSuchElementException
    {
      return delegate.getKey();
    }

    @Override
    public V getValue() throws NoSuchElementException
    {
      return delegate.getValue();
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      delegate.delete();
    }

    @Override
    public void close()
    {
      delegate.close();
    }

    @Override
    public boolean positionToKey(ByteSequence key)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean positionToKeyOrNext(ByteSequence key)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean positionToLastKey()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean positionToIndex(int index)
    {
      throw new UnsupportedOperationException();
    }
  }

  /** Runtime exception for problems happening during the transformation. */
  @SuppressWarnings("serial")
  private static class TransformationException extends RuntimeException
  {
    private final Object originalKey;
    private final Object originalValue;

    private TransformationException(Exception e, Object originalKey, Object originalValue)
    {
      super(e);
      this.originalKey = originalKey;
      this.originalValue = originalValue;
    }

    /**
     * Get the key of the record which caused the transformation error.
     *
     * @return The not transformed key of the record.
     */
    public Object getOriginalKey()
    {
      return originalKey;
    }

    /**
     * Get the value of the record which caused the transformation error.
     *
     * @return The not transformed value of the record.
     */
    public Object getOriginalValue()
    {
      return originalValue;
    }
  }
}
