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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import java.io.Closeable;

/**
 * Generic cursor interface into the changelog database. Once it is not used
 * anymore, a cursor must be closed to release all the resources into the
 * database.
 * <p>
 * The cursor provides a java.sql.ResultSet like API : it is positioned before
 * the first requested record and needs to be moved forward by calling
 * {@link DBCursor#next()}.
 * <p>
 * Usage:
 * <pre>
 * {@code
 *  DBCursor cursor = ...;
 *  try {
 *    while (cursor.next()) {
 *      Record record = cursor.getRecord();
 *      // ... can call cursor.getRecord() again: it will return the same result
 *    }
 *  }
 *  finally {
 *    close(cursor);
 *  }
 * }
 * </pre>
 *
 * @param <T>
 *          type of the record being returned
 * \@NotThreadSafe
 */
public interface DBCursor<T> extends Closeable
{

  /**
   * Represents a cursor key matching strategy, which allow to choose if only
   * the exact key must be found or if any key equals or higher should match.
   */
  public enum KeyMatchingStrategy {
    /** matches only if the exact key is found. */
    EQUAL_TO_KEY,
    /** matches if the key or a greater key is found. */
    GREATER_THAN_OR_EQUAL_TO_KEY
  }

  /**
   * Represents a cursor positioning strategy, which allow to choose if the start point
   * corresponds to the record at the provided key or the record just after the provided
   * key.
   */
  public enum PositionStrategy {
    /** start point is on the matching key. */
    ON_MATCHING_KEY,
    /** start point is after the matching key. */
    AFTER_MATCHING_KEY
  }

  /**
   * Getter for the current record.
   *
   * @return The current record.
   */
  T getRecord();

  /**
   * Skip to the next record of the database.
   *
   * @return true if has next, false otherwise
   * @throws ChangelogException
   *           When database exception raised.
   */
  boolean next() throws ChangelogException;

  /**
   * Release the resources and locks used by this Iterator. This method must be
   * called when the iterator is no longer used. Failure to do it could cause DB
   * deadlock.
   */
  @Override
  void close();
}
