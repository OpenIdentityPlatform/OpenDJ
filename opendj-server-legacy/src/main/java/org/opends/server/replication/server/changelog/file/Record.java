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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

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
    return equals(key, other.key)
        && equals(value, other.value);
  }

  private boolean equals(Object o1, Object o2)
  {
    return o1 == null ? o2 == null : o1.equals(o2);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
      return "Record [" + key + ":" + value + "]";
  }

}
