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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Reject.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.spi.Cursor;

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
   * Allow to transform a cursor value given the key and the original value
   * @param <KI> Original type of the cursor's key
   * @param <VI> Original type of the cursor's value
   * @param <VO> New transformed type of the value
   * @param <E> Possible exception type
   */
  interface ValueTransformer<KI, VI, VO, E extends Exception>
  {
    VO transform(KI key, VI value) throws E;
  }

  private static final Function<Object, Object, NeverThrowsException> NO_TRANSFORM =
      new Function<Object, Object, NeverThrowsException>()
      {
        @Override
        public Object apply(Object value) throws NeverThrowsException
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

  @SuppressWarnings("unchecked")
  static <KI, VI, VO> Cursor<KI, VO> transformValues(Cursor<KI, VI> input,
      ValueTransformer<KI, VI, VO, ? extends Exception> valueTransformer)
  {
    return transformKeysAndValues(input, (Function<KI, KI, NeverThrowsException>) NO_TRANSFORM, valueTransformer);
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

  /**
   * Runtime exception for problems happening during the transformation
   */
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
