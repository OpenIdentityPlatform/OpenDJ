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
package org.opends.server.backends.pluggable.spi;

import org.forgerock.opendj.ldap.ByteSequence;

/**
 * Sequential cursor extended with navigation methods.
 * @param <K> Type of the record's key
 * @param <V> Type of the record's value
 */
public interface Cursor<K,V> extends SequentialCursor<K, V>
{
  /**
   * Positions the cursor to the provided key if it exists in the tree.
   *
   * @param key
   *          the key where to position the cursor
   * @return {@code true} if the cursor could be positioned to the key,
   *         {@code false} otherwise
   */
  boolean positionToKey(ByteSequence key);

  /**
   * Positions the cursor to the provided key if it exists in the tree,
   * or else the lesser key greater than the provided key in the tree.
   *
   * @param key
   *          the key where to position the cursor
   * @return {@code true} if the cursor could be positioned to the key
   *         or the next one, {@code false} otherwise
   */
  boolean positionToKeyOrNext(ByteSequence key);

  /**
   * Positions the cursor to the last key in the tree.
   *
   * @return {@code true} if the cursor could be positioned to the last key,
   *         {@code false} otherwise
   */
  boolean positionToLastKey();

  /**
   * Positions the cursor to the specified index within the tree. Implementations may take advantage
   * of optimizations provided by the underlying storage, such as counted B-Trees.
   *
   * @param index
   *          the index where the cursor should be positioned, (0 is the first record).
   * @return {@code true} if the cursor could be positioned to the index, {@code false} otherwise
   */
  boolean positionToIndex(int index);
}
