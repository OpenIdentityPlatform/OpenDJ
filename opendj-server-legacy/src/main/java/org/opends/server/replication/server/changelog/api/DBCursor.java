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
 *      Copyright 2013-2015 ForgeRock AS
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
 * A cursor can be initialised from a key, using a {@code KeyMatchingStrategy} and
 * a {@code PositionStrategy}, to determine the exact starting position.
 * <p>
 * Let's call Kp the highest key lower than K and Kn the lowest key higher
 * than K : Kp &lt; K &lt; Kn
 * <ul>
 *  <li>When using EQUAL_TO_KEY on key K :
 *   <ul>
 *    <li>with ON_MATCHING_KEY, cursor is positioned on key K (if K exists in log),
 *        otherwise it is empty</li>
 *    <li>with AFTER_MATCHING_KEY, cursor is positioned on key Kn (if K exists in log),
 *        otherwise it is empty</li>
 *   </ul>
 *  </li>
 *  <li>When using LESS_THAN_OR_EQUAL_TO_KEY on key K :
 *   <ul>
 *    <li>with ON_MATCHING_KEY, cursor is positioned on key K (if K exists in log)
 *        or else Kp (if Kp exists in log), otherwise it is empty</li>
 *    <li>with AFTER_MATCHING_KEY, cursor is positioned on key Kn (if Kp or K exist in log),
 *        otherwise it is empty</li>
 *   </ul>
 *  </li>
 *  <li>When using GREATER_THAN_OR_EQUAL_TO_KEY on key K :
 *   <ul>
 *    <li>with ON_MATCHING_KEY, cursor is positioned on key K (if K exists in log)
 *        or else Kn (if Kn exists in log), otherwise it is empty</li>
 *    <li>with AFTER_MATCHING_KEY, cursor is positioned on key Kn (if K or Kn exist in log),
 *        otherwise it is empty</li>
 *   </ul>
 *  </li>
 * </ul>
 *
 * @param <T>
 *          type of the record being returned
 * \@NotThreadSafe
 */
public interface DBCursor<T> extends Closeable
{

  /**
   * Represents a cursor key matching strategy, which allow to choose if only
   * the exact key must be found or if any key equal or lower/higher should match.
   */
  public enum KeyMatchingStrategy {
    /** Matches if the key or a lower key is found. */
    LESS_THAN_OR_EQUAL_TO_KEY,
    /** Matches only if the exact key is found. */
    EQUAL_TO_KEY,
    /** Matches if the key or a greater key is found. */
    GREATER_THAN_OR_EQUAL_TO_KEY
  }

  /**
   * Represents a cursor positioning strategy, which allow to choose if the start point
   * corresponds to the record at the provided key or the record just after the provided
   * key.
   */
  public enum PositionStrategy {
    /** Start point is on the matching key. */
    ON_MATCHING_KEY,
    /** Start point is after the matching key. */
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
