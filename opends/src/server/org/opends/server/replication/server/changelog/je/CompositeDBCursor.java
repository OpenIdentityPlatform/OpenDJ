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
package org.opends.server.replication.server.changelog.je;

import java.util.*;
import java.util.Map.Entry;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.util.StaticUtils;

/**
 * {@link DBCursor} implementation that iterates across a Collection of
 * {@link DBCursor}s, advancing from the oldest to the newest change cross all
 * cursors.
 *
 * @param <Data>
 *          The type of data associated with each cursor
 */
final class CompositeDBCursor<Data> implements DBCursor<UpdateMsg>
{

  private UpdateMsg currentRecord;
  private Data currentData;
  private final Map<DBCursor<UpdateMsg>, Data> exhaustedCursors =
      new HashMap<DBCursor<UpdateMsg>, Data>();
  /**
   * The cursors are sorted based on the current change of each cursor to
   * consider the next change across all available cursors.
   */
  private final NavigableMap<DBCursor<UpdateMsg>, Data> cursors =
      new TreeMap<DBCursor<UpdateMsg>, Data>(
          new Comparator<DBCursor<UpdateMsg>>()
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
  public CompositeDBCursor(Map<DBCursor<UpdateMsg>, Data> cursors)
  {
    for (Entry<DBCursor<UpdateMsg>, Data> entry : cursors.entrySet())
    {
      put(entry);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (!exhaustedCursors.isEmpty())
    {
      // try to recycle empty cursors in case the underlying ReplicaDBs received
      // new changes. Copy the List to avoid ConcurrentModificationExceptions.
      final Map<DBCursor<UpdateMsg>, Data> copy =
          new HashMap<DBCursor<UpdateMsg>, Data>(exhaustedCursors);
      exhaustedCursors.clear();
      for (Entry<DBCursor<UpdateMsg>, Data> entry : copy.entrySet())
      {
        entry.getKey().next();
        put(entry);
      }
    }

    if (cursors.isEmpty())
    {
      // no cursors are left with changes.
      currentRecord = null;
      currentData = null;
      return false;
    }

    // To keep consistent the cursors' order in the SortedSet, it is necessary
    // to remove and eventually add again a cursor (after moving it forward).
    final Entry<DBCursor<UpdateMsg>, Data> entry = cursors.pollFirstEntry();
    final DBCursor<UpdateMsg> cursor = entry.getKey();
    currentRecord = cursor.getRecord();
    currentData = entry.getValue();
    cursor.next();
    put(entry);
    return true;
  }

  private void put(Entry<DBCursor<UpdateMsg>, Data> entry)
  {
    final DBCursor<UpdateMsg> cursor = entry.getKey();
    final Data data = entry.getValue();
    if (cursor.getRecord() != null)
    {
      this.cursors.put(cursor, data);
    }
    else
    {
      this.exhaustedCursors.put(cursor, data);
    }
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return currentRecord;
  }

  /**
   * Returns the data associated to the cursor that returned the current record.
   *
   * @return the data associated to the cursor that returned the current record.
   */
  public Data getData()
  {
    return currentData;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    StaticUtils.close(cursors.keySet());
    StaticUtils.close(exhaustedCursors.keySet());
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " currentRecord=" + currentRecord
        + " currentData=" + currentData + " openCursors=" + cursors
        + " exhaustedCursors=" + exhaustedCursors;
  }

}
