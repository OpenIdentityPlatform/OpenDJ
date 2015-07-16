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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.je.ReplicationDB.ReplServerDBCursor;

import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;

/**
 * Berkeley DB JE implementation of {@link DBCursor}.
 *
 * \@NotThreadSafe
 */
class JEReplicaDBCursor implements DBCursor<UpdateMsg>
{
  private final ReplicationDB db;
  private PositionStrategy positionStrategy;
  private KeyMatchingStrategy matchingStrategy;
  private JEReplicaDB replicaDB;
  private final CSN startCSN;
  private CSN lastNonNullCurrentCSN;
  /**
   * The underlying replica DB cursor.
   * <p>
   * Initially <code>null</code>, the first call to {@link #next()} will
   * populate it. A call to {@link #close()} will set it to null again.
   */
  private ReplServerDBCursor cursor;

  /**
   * Creates a new {@link JEReplicaDBCursor}. All created cursor must be
   * released by the caller using the {@link #close()} method.
   *
   * @param db
   *          The db where the cursor must be created.
   * @param startCSN
   *          The CSN after which the cursor must start.If null, start from the
   *          oldest CSN
   * @param matchingStrategy
   *          Cursor key matching strategy
   * @param positionStrategy
   *          Cursor position strategy
   * @param replicaDB
   *          The associated JEReplicaDB.
   * @throws ChangelogException
   *          if a database problem happened.
   */
  public JEReplicaDBCursor(ReplicationDB db, CSN startCSN, KeyMatchingStrategy matchingStrategy,
      PositionStrategy positionStrategy, JEReplicaDB replicaDB) throws ChangelogException
  {
    this.db = db;
    this.matchingStrategy = matchingStrategy;
    this.positionStrategy = positionStrategy;
    this.replicaDB = replicaDB;
    this.startCSN = startCSN;
    this.lastNonNullCurrentCSN = startCSN;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    if (!isClosed() && cursor != null)
    {
      return cursor.getRecord();
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (isClosed())
    {
      return false;
    }

    final ReplServerDBCursor previousCursor = cursor;
    if (getRecord() == null)
    {
      synchronized (this)
      {
        closeCursor();
        // Previously exhausted cursor must be able to reinitialize themselves.
        // There is a risk of readLock never being unlocked
        // if following code is called while the cursor is closed.
        // It is better to let the deadlock happen to help quickly identifying
        // and fixing such issue with unit tests.
        if (lastNonNullCurrentCSN != startCSN)
        {
          // re-initialize to further CSN, take care to use appropriate strategies
          matchingStrategy = GREATER_THAN_OR_EQUAL_TO_KEY;
          positionStrategy = AFTER_MATCHING_KEY;
        }
        cursor = db.openReadCursor(lastNonNullCurrentCSN, matchingStrategy, positionStrategy);
      }
    }

    // For ON_MATCHING_KEY, do not call next() if the cursor has just been initialized.
    if ((positionStrategy == ON_MATCHING_KEY && previousCursor != null)
        || positionStrategy == AFTER_MATCHING_KEY)
    {
      cursor.next();
    }

    final UpdateMsg currentRecord = cursor.getRecord();
    if (currentRecord != null)
    {
      lastNonNullCurrentCSN = currentRecord.getCSN();
      return true;
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    synchronized (this)
    {
      closeCursor();
      replicaDB = null;
    }
  }

  private boolean isClosed()
  {
    return replicaDB == null;
  }

  private void closeCursor()
  {
    if (cursor != null)
    {
      cursor.close();
      cursor = null;
    }
  }

  /**
   * Called by the GC when the object is garbage collected. Release the internal
   * cursor in case the cursor was badly used and {@link #close()} was never
   * called.
   */
  @Override
  protected void finalize()
  {
    close();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + " currentChange=" + cursor.getRecord()
        + " positionStrategy=" + positionStrategy
        + " matchingStrategy=" + matchingStrategy
        + " replicaDB=" + replicaDB;
  }
}
