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

  private static final byte UNINITIALIZED = 0;
  private static final byte READY = 1;
  private static final byte CLOSED = 2;

  /**
   * The state of this cursor. One of {@link #UNINITIALIZED}, {@link #READY} or
   * {@link #CLOSED}
   */
  private byte state = UNINITIALIZED;

  /** Whether this composite should try to recycle exhausted cursors. */
  private final boolean recycleExhaustedCursors;
  /**
   * These cursors are considered exhausted because they had no new changes the
   * last time {@link DBCursor#next()} was called on them. Exhausted cursors
   * might be recycled at some point when they start returning changes again.
   */
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
   * @param recycleExhaustedCursors
   *          whether a call to {@link #next()} tries to recycle exhausted
   *          cursors
   */
  public CompositeDBCursor(Map<DBCursor<UpdateMsg>, Data> cursors,
      boolean recycleExhaustedCursors)
  {
    this.recycleExhaustedCursors = recycleExhaustedCursors;
    for (Entry<DBCursor<UpdateMsg>, Data> entry : cursors.entrySet())
    {
      put(entry);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (state == CLOSED)
    {
      return false;
    }
    final boolean advanceNonExhaustedCursors = state != UNINITIALIZED;
    state = READY;
    if (recycleExhaustedCursors && !exhaustedCursors.isEmpty())
    {
      // try to recycle empty cursors in case the underlying ReplicaDBs received
      // new changes.
      final Map<DBCursor<UpdateMsg>, Data> copy =
          new HashMap<DBCursor<UpdateMsg>, Data>(exhaustedCursors);
      exhaustedCursors.clear();
      for (Entry<DBCursor<UpdateMsg>, Data> entry : copy.entrySet())
      {
        entry.getKey().next();
        put(entry);
      }
      final Entry<DBCursor<UpdateMsg>, Data> firstEntry = cursors.firstEntry();
      if (firstEntry != null && copy.containsKey(firstEntry.getKey()))
      {
        // if the first cursor was previously an exhausted cursor,
        // then we have already called next() on it.
        // Avoid calling it again because we know new changes have been found.
        return true;
      }
    }

    // To keep consistent the cursors' order in the SortedSet, it is necessary
    // to remove and add again the cursor after moving it forward.
    if (advanceNonExhaustedCursors)
    {
      Entry<DBCursor<UpdateMsg>, Data> firstEntry = cursors.pollFirstEntry();
      if (firstEntry != null)
      {
        final DBCursor<UpdateMsg> cursor = firstEntry.getKey();
        cursor.next();
        put(firstEntry);
      }
    }
    // no cursors are left with changes.
    return !cursors.isEmpty();
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
    final Entry<DBCursor<UpdateMsg>, Data> entry = cursors.firstEntry();
    if (entry != null)
    {
      return entry.getKey().getRecord();
    }
    return null;
  }

  /**
   * Returns the data associated to the cursor that returned the current record.
   *
   * @return the data associated to the cursor that returned the current record.
   */
  public Data getData()
  {
    final Entry<DBCursor<UpdateMsg>, Data> entry = cursors.firstEntry();
    if (entry != null)
    {
      return entry.getValue();
    }
    return null;
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
    return getClass().getSimpleName() + " openCursors=" + cursors
        + " exhaustedCursors=" + exhaustedCursors;
  }

}
