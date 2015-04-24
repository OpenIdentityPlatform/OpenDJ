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
 *      Copyright 2014-2015 ForgeRock AS
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

  /** {@inheritDoc} */
  @Override
  void close();
}