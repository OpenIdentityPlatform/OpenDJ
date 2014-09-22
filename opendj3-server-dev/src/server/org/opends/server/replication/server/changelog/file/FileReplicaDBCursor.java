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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.Log.RepositionableCursor;

/**
 * A cursor on ReplicaDB, which can re-initialize itself after exhaustion.
 * <p>
 * The cursor provides a java.sql.ResultSet like API :
 * <pre>
 *  FileReplicaDBCursor cursor = ...;
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
 * <p>
 * The cursor automatically re-initializes itself if it is exhausted: if a
 * record is newly available, a subsequent call to the {@code next()} method will
 * return {@code true} and the record will be available by calling {@code getRecord()}
 * method.
 *
 * \@NotThreadSafe
 */
class FileReplicaDBCursor implements DBCursor<UpdateMsg>
{
  /** The underlying cursor. */
  private final RepositionableCursor<CSN, UpdateMsg> cursor;

  /** The next record to return. */
  private Record<CSN, UpdateMsg> nextRecord;

  /**  The CSN to re-start with in case the cursor is exhausted. */
  private CSN lastNonNullCurrentCSN;

  private PositionStrategy positionStrategy;

  /**
   * Creates the cursor from provided log cursor and start CSN.
   *
   * @param cursor
   *          The underlying log cursor to read log.
   * @param startCSN
   *          The CSN to use as a start point (excluded from cursor, the lowest
   *          CSN higher than this CSN is used as the real start point).
   * @param positionStrategy
   *          Cursor position strategy, which allow to choose if cursor must
   *          start from the provided CSN or just after the provided CSN.
   */
  FileReplicaDBCursor(
      final RepositionableCursor<CSN, UpdateMsg> cursor,
      final CSN startCSN,
      final PositionStrategy positionStrategy) {
    this.cursor = cursor;
    this.lastNonNullCurrentCSN = startCSN;
    this.positionStrategy = positionStrategy;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return nextRecord == null ? null : nextRecord.getValue();
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (cursor.next())
    {
      nextRecord = cursor.getRecord();
      final int nextCSNCompare = nextRecord.getKey().compareTo(lastNonNullCurrentCSN);
      if (nextCSNCompare > 0 || (nextCSNCompare == 0 && positionStrategy == ON_MATCHING_KEY))
      {
        // start CSN is found, switch to position strategy that always find the next
        lastNonNullCurrentCSN = nextRecord.getKey();
        positionStrategy = AFTER_MATCHING_KEY;
        return true;
      }
    }
    // either cursor is exhausted or we still have not reached the start CSN
    return nextWhenCursorIsExhaustedOrNotCorrectlyPositionned();
  }

  /** Re-initialize the cursor after the last non null CSN. */
  private boolean nextWhenCursorIsExhaustedOrNotCorrectlyPositionned() throws ChangelogException
  {
    final boolean found = cursor.positionTo(lastNonNullCurrentCSN, GREATER_THAN_OR_EQUAL_TO_KEY, positionStrategy);
    if (found && cursor.next())
    {
      nextRecord = cursor.getRecord();
      lastNonNullCurrentCSN = nextRecord.getKey();
      return true;
    }
    nextRecord = null;
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    cursor.close();
  }

}
