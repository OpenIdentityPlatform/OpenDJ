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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import java.util.Objects;

/**
 * Represents a record, which is a pair of key-value.
 *
 * @param <K>
 *         The type of a key.
 * @param <V>
 *         The type of a value.
 */
class Record<K, V>
{
  /** Map the record value to another value. */
  static interface Mapper<V, V2> {
      /**
       * Map a record value to another value.
       *
       * @param value
       *          The value to map
       * @return the new value
       */
      V2 map(V value);
  }

  private final K key;
  private final V value;

  /**
   * Creates a record from provided key and value.
   *
   * @param key
   *          The key.
   * @param value
   *          The value.
   */
  private Record(final K key, final V value)
  {
    this.key = key;
    this.value = value;
  }

  /**
   * Create a record from provided key and value.
   *
   * @param <K>
   *          The type of the key.
   * @param <V>
   *          The type of the value.
   * @param key
   *          The key.
   * @param value
   *          The value.
   * @return a record
   */
  static <K, V> Record<K, V> from(final K key, final V value) {
    return new Record<>(key, value);
  }

  /**
   * Returns the key of this record.
   *
   * @return the key
   */
  K getKey()
  {
    return key;
  }

  /**
   * Returns the value of this record.
   *
   * @return the value
   */
  V getValue()
  {
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object that)
  {
    if (this == that)
    {
      return true;
    }
    if (!(that instanceof Record))
    {
      return false;
    }
    Record<?, ?> other = (Record<?, ?>) that;
    return Objects.equals(key, other.key)
        && Objects.equals(value, other.value);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
      return "Record [" + key + ":" + value + "]";
  }

}
