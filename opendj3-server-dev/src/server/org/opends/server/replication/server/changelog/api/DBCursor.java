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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import java.io.Closeable;

/**
 * Generic cursor interface into the changelog database. Once it is not used
 * anymore, a cursor must be closed to release all the resources into the
 * database.
 * <p>
 * Here is a typical usage pattern:
 *
 * <pre>
 * DBCursor&lt;T&gt; cursor = ...;         // obtain a new cursor,
 *                                   // already initialized
 * T record1 = cursor.getRecord();   // get the first record
 * while (cursor.next()) {           // advance to the next record
 *   T record = cursor.getRecord();  // get the next record
 *   ...                             // etc.
 * }
 * </pre>
 *
 * @param <T>
 *          type of the record being returned
 */
public interface DBCursor<T> extends Closeable
{

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
