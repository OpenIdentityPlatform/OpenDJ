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
abstract class CompositeDBCursor<Data> implements DBCursor<UpdateMsg>
{

  private static final byte UNINITIALIZED = 0;
  private static final byte READY = 1;
  private static final byte CLOSED = 2;

  /**
   * The state of this cursor. One of {@link #UNINITIALIZED}, {@link #READY} or
   * {@link #CLOSED}
   */
  private byte state = UNINITIALIZED;

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
   * <p>
   * New cursors for this Map must be created from the same thread that will
   * make use of them. When this rule is not obeyed, a JE exception will be
   * thrown about
   * "Non-transactional Cursors may not be used in multiple threads;".
   */
  private final TreeMap<DBCursor<UpdateMsg>, Data> cursors =
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

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (state == CLOSED)
    {
      return false;
    }

    if (state == UNINITIALIZED)
    {
      state = READY;
    }
    else
    {
      // Previous state was READY => we must advance the first cursor
      // because the UpdateMsg it is pointing has already been consumed.
      // To keep consistent the cursors' order in the SortedSet, it is necessary
      // to remove the first cursor, then add it again after moving it forward.
      final Entry<DBCursor<UpdateMsg>, Data> cursorToAdvance = cursors.pollFirstEntry();
      if (cursorToAdvance != null)
      {
        addCursor(cursorToAdvance.getKey(), cursorToAdvance.getValue());
      }
    }

    recycleExhaustedCursors();
    removeNoLongerNeededCursors();
    incorporateNewCursors();
    return !cursors.isEmpty();
  }

  private void recycleExhaustedCursors() throws ChangelogException
  {
    if (!exhaustedCursors.isEmpty())
    {
      // try to recycle exhausted cursors in case the underlying replica DBs received new changes.
      final Map<DBCursor<UpdateMsg>, Data> copy =
          new HashMap<DBCursor<UpdateMsg>, Data>(exhaustedCursors);
      exhaustedCursors.clear();
      for (Entry<DBCursor<UpdateMsg>, Data> entry : copy.entrySet())
      {
        addCursor(entry.getKey(), entry.getValue());
      }
    }
  }

  private void removeNoLongerNeededCursors()
  {
    for (Iterator<Entry<DBCursor<UpdateMsg>, Data>> iterator =
        cursors.entrySet().iterator(); iterator.hasNext();)
    {
      final Entry<DBCursor<UpdateMsg>, Data> entry = iterator.next();
      final Data data = entry.getValue();
      if (isCursorNoLongerNeededFor(data))
      {
        entry.getKey().close();
        iterator.remove();
        cursorRemoved(data);
      }
    }
  }

  /**
   * Adds a cursor to this composite cursor. It first calls
   * {@link DBCursor#next()} to verify whether it is exhausted or not.
   *
   * @param cursor
   *          the cursor to add to this composite
   * @param data
   *          the data associated to the provided cursor
   * @throws ChangelogException
   *           if a database problem occurred
   */
  protected void addCursor(final DBCursor<UpdateMsg> cursor, final Data data) throws ChangelogException
  {
    if (cursor.next())
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
    // Cannot call incorporateNewCursors() here because
    // somebody might have already called DBCursor.getRecord() and read the record
    final Entry<DBCursor<UpdateMsg>, Data> entry = cursors.firstEntry();
    if (entry != null)
    {
      return entry.getKey().getRecord();
    }
    return null;
  }

  /**
   * Called when implementors should incorporate new cursors into the current
   * composite DBCursor. Implementors should call
   * {@link #addCursor(DBCursor, Object)} to do so.
   *
   * @throws ChangelogException
   *           if a database problem occurred
   */
  protected abstract void incorporateNewCursors() throws ChangelogException;

  /**
   * Returns whether the cursor associated to the provided data should be removed.
   *
   * @param data the data associated to the cursor to be tested
   * @return true if the cursor associated to the provided data should be removed,
   *         false otherwise
   */
  protected abstract boolean isCursorNoLongerNeededFor(Data data);

  /**
   * Notifies that the cursor associated to the provided data has been removed.
   *
   * @param data
   *          the data associated to the removed cursor
   */
  protected abstract void cursorRemoved(Data data);

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
    state = CLOSED;
    StaticUtils.close(cursors.keySet());
    StaticUtils.close(exhaustedCursors.keySet());
    cursors.clear();
    exhaustedCursors.clear();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " openCursors=" + cursors
        + " exhaustedCursors=" + exhaustedCursors;
  }

}
