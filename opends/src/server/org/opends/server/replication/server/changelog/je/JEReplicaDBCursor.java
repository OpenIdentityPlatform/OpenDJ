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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.je.ReplicationDB.ReplServerDBCursor;

import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;

/**
 * Berkeley DB JE implementation of {@link DBCursor}.
 *
 * \@NotThreadSafe
 */
class JEReplicaDBCursor implements DBCursor<UpdateMsg>
{
  private final ReplicationDB db;
  private final PositionStrategy positionStrategy;
  private JEReplicaDB replicaDB;
  private CSN lastNonNullCurrentCSN;
  private ReplServerDBCursor cursor;
  private UpdateMsg currentChange;

  /**
   * Creates a new {@link JEReplicaDBCursor}. All created cursor must be
   * released by the caller using the {@link #close()} method.
   *
   * @param db
   *          The db where the cursor must be created.
   * @param startCSN
   *          The CSN after which the cursor must start.If null, start from the
   *          oldest CSN
   * @param positionStrategy
   *          indicates at which exact position the cursor must start
   * @param replicaDB
   *          The associated JEReplicaDB.
   * @throws ChangelogException
   *          if a database problem happened.
   */
  public JEReplicaDBCursor(ReplicationDB db, CSN startCSN, PositionStrategy positionStrategy,
      JEReplicaDB replicaDB) throws ChangelogException
  {
    this.db = db;
    this.positionStrategy = positionStrategy;
    this.replicaDB = replicaDB;
    this.lastNonNullCurrentCSN = startCSN;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return currentChange;
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (currentChange == null)
    {
      synchronized (this)
      {
        closeCursor();
        // Previously exhausted cursor must be able to reinitialize themselves.
        // There is a risk of readLock never being unlocked
        // if following code is called while the cursor is closed.
        // It is better to let the deadlock happen to help quickly identifying
        // and fixing such issue with unit tests.
        cursor = db.openReadCursor(lastNonNullCurrentCSN, positionStrategy);
      }
    }

    // For ON_MATCHING_KEY, do not call next() if the cursor has just been initialized.
    if (positionStrategy == ON_MATCHING_KEY && currentChange != null
        || positionStrategy == AFTER_MATCHING_KEY)
    {
      cursor.next();
    }
    currentChange = cursor.getRecord();

    if (currentChange != null)
    {
      lastNonNullCurrentCSN = currentChange.getCSN();
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

  private void closeCursor()
  {
    if (cursor != null)
    {
      cursor.close();
      cursor = null;
      currentChange = null;
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
        + " positionStrategy=" + positionStrategy
        + " currentChange=" + currentChange
        + " replicaDB=" + replicaDB;
  }
}
