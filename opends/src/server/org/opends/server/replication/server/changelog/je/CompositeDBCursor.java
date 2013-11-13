/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.*;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.util.StaticUtils;

/**
 * {@link DBCursor} implementation that iterates across a Collection of
 * {@link DBCursor}s, advancing from the oldest to the newest change cross all
 * cursors.
 */
final class CompositeDBCursor implements DBCursor<UpdateMsg>
{

  private UpdateMsg currentChange;
  private final List<DBCursor<UpdateMsg>> exhaustedCursors =
      new ArrayList<DBCursor<UpdateMsg>>();
  /**
   * The cursors are sorted based on the current change of each cursor to
   * consider the next change across all available cursors.
   */
  private final NavigableSet<DBCursor<UpdateMsg>> cursors =
      new TreeSet<DBCursor<UpdateMsg>>(new Comparator<DBCursor<UpdateMsg>>()
      {
        @Override
        public int compare(DBCursor<UpdateMsg> o1, DBCursor<UpdateMsg> o2)
        {
          final CSN csn1 = o1.getRecord().getCSN();
          final CSN csn2 = o2.getRecord().getCSN();
          return CSN.compare(csn1, csn2);
        }
      });

  /**
   * Builds a CompositeDBCursor using the provided collection of cursors.
   *
   * @param cursors
   *          the cursors that will be iterated upon.
   */
  public CompositeDBCursor(Collection<DBCursor<UpdateMsg>> cursors)
  {
    for (DBCursor<UpdateMsg> cursor : cursors)
    {
      add(cursor);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    // try to recycle empty cursors in case the underlying ReplicaDBs received
    // new changes. Copy the List to avoid ConcurrentModificationExceptions.
    DBCursor<UpdateMsg>[] copy =
        exhaustedCursors.toArray(new DBCursor[exhaustedCursors.size()]);
    exhaustedCursors.clear();
    for (DBCursor<UpdateMsg> cursor : copy)
    {
      cursor.next();
      add(cursor);
    }

    if (cursors.isEmpty())
    {
      // no cursors are left with changes.
      currentChange = null;
      return false;
    }

    // To keep consistent the cursors' order in the SortedSet, it is necessary
    // to remove and eventually add again a cursor (after moving it forward).
    final DBCursor<UpdateMsg> cursor = cursors.pollFirst();
    currentChange = cursor.getRecord();
    cursor.next();
    add(cursor);
    return true;
  }

  private void add(DBCursor<UpdateMsg> cursor)
  {
    if (cursor.getRecord() != null)
    {
      this.cursors.add(cursor);
    }
    else
    {
      this.exhaustedCursors.add(cursor);
    }
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return currentChange;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    StaticUtils.close(cursors);
    StaticUtils.close(exhaustedCursors);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " currentChange=" + currentChange
        + " open cursors=" + cursors + " exhausted cursors=" + exhaustedCursors;
  }

}
