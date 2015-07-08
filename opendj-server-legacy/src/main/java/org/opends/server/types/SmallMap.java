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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.*;

/**
 * A small map of values. This map implementation is optimized to use as little
 * memory as possible in the case where there zero or one elements. In addition,
 * any normalization of entries is delayed until the second entry is added
 * (normalization may be triggered by invoking {@link Object#hashCode()} or
 * {@link Object#equals(Object)}.
 * <p>
 * Null keys are not supported by this map.
 *
 * @param <K>
 *          the type of keys maintained by this map
 * @param <V>
 *          the type of mapped values
 */
public class SmallMap<K, V> extends AbstractMap<K, V>
{

  /** The map of entries if there are more than one. */
  private LinkedHashMap<K, V> entries;

  /** The first key of the Map. */
  private K firstKey;
  /** The first value of the Map. */
  private V firstValue;


  /** Creates a new small map which is initially empty. */
  public SmallMap()
  {
    // No implementation required.
  }

  private void rejectIfNull(Object key)
  {
    if (key == null)
    {
      throw new NullPointerException("null keys are not allowed");
    }
  }

  /** {@inheritDoc} */
  @Override
  public V get(Object key)
  {
    rejectIfNull(key);
    if (entries != null)
    { // >= 2 entries case
      return entries.get(key);
    }
    // 0 and 1 case
    if (firstKey != null && firstKey.equals(key))
    {
      return firstValue;
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public V put(K key, V value)
  {
    rejectIfNull(key);
    if (entries != null)
    { // >= 2 entries case
      return entries.put(key, value);
    }
    if (firstKey == null)
    { // 0 entries case
      firstKey = key;
      firstValue = value;
      return null;
    }
    // 1 entry case
    if (firstKey.equals(key))
    { // replace value
      V oldValue = firstValue;
      firstValue = value;
      return oldValue;
    }
    // overflow to the underlying map
    entries = new LinkedHashMap<>(2);
    entries.put(firstKey, firstValue);
    firstKey = null;
    firstValue = null;
    return entries.put(key, value);
  }

  /** {@inheritDoc} */
  @Override
  public void putAll(Map<? extends K, ? extends V> m)
  {
    for (Entry<? extends K, ? extends V> entry : m.entrySet())
    {
      put(entry.getKey(), entry.getValue());
    }
  }

  /** {@inheritDoc} */
  @Override
  public V remove(Object key)
  {
    if (entries != null)
    {
      // Note: if there is one or zero values left we could stop using the map.
      // However, lets assume that if the map was multi-valued before
      // then it may become multi-valued again.
      return entries.remove(key);
    }

    if (firstKey != null && firstKey.equals(key))
    {
      V oldV = firstValue;
      firstKey = null;
      firstValue = null;
      return oldV;
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean containsKey(Object key)
  {
    rejectIfNull(key);
    if (entries != null)
    {
      return entries.containsKey(key);
    }
    return firstKey != null && firstKey.equals(key);
  }

  /** {@inheritDoc} */
  @Override
  public boolean containsValue(Object value)
  {
    if (entries != null)
    {
      return entries.containsValue(value);
    }
    if (firstKey == null)
    {
      return false;
    }
    if (firstValue == null)
    {
      return value == null;
    }
    return firstValue.equals(value);
  }

  /** {@inheritDoc} */
  @Override
  public void clear()
  {
    firstKey = null;
    firstValue = null;
    entries = null;
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    if (entries != null)
    {
      return entries.size();
    }
    return firstKey != null ? 1 : 0;
  }

  /** {@inheritDoc} */
  @Override
  public Set<Entry<K, V>> entrySet()
  {
    if (entries != null)
    {
      return entries.entrySet();
    }
    if (firstKey == null)
    {
      return Collections.emptySet();
    }

    return new AbstractSet<Entry<K, V>>()
    {

      @Override
      public Iterator<Entry<K, V>> iterator()
      {
        return new Iterator<Entry<K, V>>()
        {

          private boolean isFirst = true;

          @Override
          public boolean hasNext()
          {
            return isFirst;
          }

          @Override
          public Entry<K, V> next()
          {
            if (!isFirst)
            {
              throw new NoSuchElementException();
            }
            isFirst = false;
            return new SimpleEntry<>(firstKey, firstValue);
          }

          @Override
          public void remove()
          {
            firstKey = null;
            firstValue = null;
          }
        };
      }

      @Override
      public int size()
      {
        return 1;
      }
    };
  }

}
