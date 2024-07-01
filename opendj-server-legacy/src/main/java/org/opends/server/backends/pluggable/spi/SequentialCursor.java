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

import java.io.Closeable;
import java.util.NoSuchElementException;

/**
 * Cursor extended with navigation methods.
 * @param <K> Type of the record's key
 * @param <V> Type of the record's value
 */
public interface SequentialCursor<K,V> extends Closeable
{
  /**
   * Moves this cursor to the next record in the tree.
   *
   * @return {@code true} if the cursor has moved to the next record,
   *         {@code false} if no next record exists leaving cursor
   *         in undefined state.
   */
  boolean next();

  /**
   * Check whether this cursor is currently pointing to valid record.
   *
   * @return {@code true} if the cursor is pointing to a valid entry,
   *         {@code false} if cursor is not pointing to a valid entry
   */
  boolean isDefined();

  /**
   * Returns the key of the record on which this cursor is currently positioned.
   *
   * @return the current record's key.
   * @throws NoSuchElementException if the cursor is not defined.
   */
  K getKey() throws NoSuchElementException;

  /**
   * Returns the value of the record on which this cursor is currently positioned.
   *
   * @return the current record's value.
   * @throws NoSuchElementException if the cursor is not defined.
   */
  V getValue() throws NoSuchElementException;

  /**
   * Deletes the record on which this cursor is currently positioned. This method does not alter the position of the
   * cursor. In particular, {@link #next()} must be called in order to point to the next record. The behavior of
   * methods {@link #getKey()} and {@link #getValue()} after this method returns is undefined.
   *
   * @throws NoSuchElementException if the cursor is not defined.
   * @throws UnsupportedOperationException if the cursor implementation does not support updates.
   */
  void delete() throws NoSuchElementException, UnsupportedOperationException;

  /** {@inheritDoc} */
  @Override
  void close();
}
