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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.io.Closeable;

import org.opends.server.types.DirectoryException;

/**
 * Interface for a key-value store, also known as an index.
 *
 * @param <K>
 *          type of key objects
 * @param <V>
 *          type of value objects
 * @param <T>
 *          type of transaction objects. Underlying databases that do not
 *          support transactions should use a {@link Void} type parameter and
 *          pass in a null value.
 * @param <M>
 *          type of lock mode objects. Underlying databases might not need this
 *          parameter. They should use a {@link Void} type parameter and pass in
 *          a null value.
 */
public interface KeyValueStore<K, V, T, M> extends Closeable
{

  /**
   * Opens a key-value store.
   *
   * @throws DirectoryException
   *           If an error occurs while opening the key-value store.
   */
  void open() throws DirectoryException;

  /**
   * Inserts a new record for the provided key-value mapping in the key-value
   * store.
   *
   * @param txn
   *          the current transaction
   * @param key
   *          the key to use when inserting the provided value
   * @param value
   *          the value to insert
   * @return true if the record was inserted, false if a record with that key
   *         already exists.
   * @throws DirectoryException
   *           If an error occurs while opening the key-value store.
   */
  boolean insert(T txn, K key, V value) throws DirectoryException;

  /**
   * Puts the provided key-value mapping in the key-value store, overwriting any
   * previous mapping for the key.
   *
   * @param txn
   *          the current transaction
   * @param key
   *          the key to use when putting the provided value
   * @param value
   *          the value to put
   * @return true if the key-value mapping could be put in the key-value store,
   *         false otherwise
   * @throws DirectoryException
   *           If an error occurs while opening the key-value store.
   */
  boolean put(T txn, K key, V value) throws DirectoryException;

  /**
   * Returns the value associated to the provided key.
   *
   * @param txn
   *          the current transaction
   * @param key
   *          the key for which to retrieve the value
   * @param mode
   *          the mode to use when retrieving the value
   * @return The value associated with the provided key, or null if there is no
   *         such key-value mapping
   * @throws DirectoryException
   *           If an error occurs while opening the key-value store.
   */
  V get(T txn, K key, M mode) throws DirectoryException;

  /**
   * Removes the mapping for the provided key in the key-value store.
   *
   * @param txn
   *          the current transaction
   * @param key
   *          the key to remove from the key-value store
   * @return true if the key could be removed, false otherwise
   * @throws DirectoryException
   *           If an error occurs while opening the key-value store.
   */
  boolean remove(T txn, K key) throws DirectoryException;
}
