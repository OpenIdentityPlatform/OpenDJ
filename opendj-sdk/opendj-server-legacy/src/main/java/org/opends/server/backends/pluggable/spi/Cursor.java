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

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * Cursor that iterates through records in a tree.
 */
public interface Cursor extends Closeable
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
   * @return {@code true} if the cursor could be positioned to the key,
   *         {@code false} otherwise
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

  /**
   * Moves this cursor to the next record in the tree.
   *
   * @return {@code true} if the cursor could move to the next record,
   *         {@code false} if no next record exists
   */
  boolean next();

  /**
   * Moves this cursor to the previous record in the tree.
   *
   * @return {@code true} if the cursor could move to the previous record,
   *         {@code false} if no previous record exists
   */
  boolean previous();

  /**
   * Returns the key of the record on which this cursor is currently positioned.
   *
   * @return the current record's key,
   *         or {@code null} if this cursor is not positioned on any record.
   */
  ByteString getKey();

  /**
   * Returns the value of the record on which this cursor is currently positioned.
   *
   * @return the current record's value,
   *         or {@code null} if this cursor is not positioned on any record.
   */
  ByteString getValue();

  /** {@inheritDoc} */
  @Override
  void close();
}