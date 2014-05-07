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

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.LogFile.LogCursor;

/**
 * A cursor on ReplicaDB.
 * <p>
 * This cursor behaves specially in two ways :
 * <ul>
 *  <li>The cursor initially points to a {@code null} value: the
 *      {@code getRecord()} method return {@code null} if called before any call to
 *      {@code next()} method.</li>
 *  <li>The cursor automatically re-initializes itself if it is exhausted: when
 *      exhausted, the cursor re-position itself to the last non null CSN previously
 *      read.
 *  <li>
 * </ul>
 */
class FileReplicaDBCursor implements DBCursor<UpdateMsg>
{

  /** The underlying cursor. */
  private final LogCursor<CSN, UpdateMsg> cursor;

  /** The next record to return. */
  private Record<CSN, UpdateMsg> nextRecord;

  /** The CSN to re-start with in case the cursor is exhausted. */
  private CSN lastNonNullCurrentCSN;

  /**
   * Creates the cursor from provided log cursor and start CSN.
   *
   * @param cursor
   *          The underlying log cursor to read log.
   * @param startAfterCSN
   *          The CSN to use as a start point (excluded from cursor, the lowest
   *          CSN higher than this CSN is used as the real start point).
   */
  FileReplicaDBCursor(LogCursor<CSN, UpdateMsg> cursor, CSN startAfterCSN) {
    this.cursor = cursor;
    this.lastNonNullCurrentCSN = startAfterCSN;
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
    nextRecord = cursor.getRecord();
    if (nextRecord != null)
    {
      lastNonNullCurrentCSN = nextRecord.getKey();
    }
    else
    {
      // Exhausted cursor must be able to reinitialize itself
      cursor.rewind();
      cursor.positionTo(lastNonNullCurrentCSN, true);

      nextRecord = cursor.getRecord();
      if (nextRecord != null)
      {
        lastNonNullCurrentCSN = nextRecord.getKey();
      }
    }
    // the underlying cursor is one record in advance
    cursor.next();
    return nextRecord != null;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    cursor.close();
  }

}
