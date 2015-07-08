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
package org.opends.server.replication.server.changelog.file;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

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
 * @param <T>
 *          The type of data associated with each cursor
 * \@NotThreadSafe
 */
abstract class CompositeDBCursor<T> implements DBCursor<UpdateMsg>
{
  private static final byte UNINITIALIZED = 0;
  private static final byte READY = 1;
  private static final byte CLOSED = 2;

  /** The state of this cursor. One of {@link #UNINITIALIZED}, {@link #READY} or {@link #CLOSED} */
  private byte state = UNINITIALIZED;

  /**
   * These cursors are considered exhausted because they had no new changes the
   * last time {@link DBCursor#next()} was called on them. Exhausted cursors
   * might be recycled at some point when they start returning changes again.
   */
  private final Map<DBCursor<UpdateMsg>, T> exhaustedCursors = new HashMap<>();
  /**
   * The cursors are sorted based on the current change of each cursor to
   * consider the next change across all available cursors.
   * <p>
   * New cursors for this Map must be created from the same thread that will
   * make use of them. When this rule is not obeyed, a JE exception will be
   * thrown about
   * "Non-transactional Cursors may not be used in multiple threads;".
   */
  private final TreeMap<DBCursor<UpdateMsg>, T> cursors = new TreeMap<>(
          new Comparator<DBCursor<UpdateMsg>>()
          {
            @Override
            public int compare(DBCursor<UpdateMsg> o1, DBCursor<UpdateMsg> o2)
            {
              final CSN csn1 = o1.getRecord().getCSN();
              final CSN csn2 = o2.getRecord().getCSN();
              int cmpCsn = CSN.compare(csn1, csn2);
              if (cmpCsn == 0
                  && o1 instanceof CompositeDBCursor
                  && o2 instanceof CompositeDBCursor)
              {
                // Ensures a consistent order when the CSNs are equal (rare in practice)
                T data1 = ((CompositeDBCursor<T>) o1).getData();
                T data2 = ((CompositeDBCursor<T>) o1).getData();
                if (data1 instanceof Comparable && data2 instanceof Comparable)
                {
                  return ((Comparable<T>) data1).compareTo(data2);
                }
              }
              return cmpCsn;
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

    // If previous state was ready, then we must advance the first cursor
    // (which UpdateMsg has been consumed).
    // To keep consistent the cursors' order in the SortedSet, it is necessary
    // to remove the first cursor, then add it again after moving it forward.
    final Entry<DBCursor<UpdateMsg>, T> cursorToAdvance =
        state != UNINITIALIZED ? cursors.pollFirstEntry() : null;
    state = READY;
    recycleExhaustedCursors();
    if (cursorToAdvance != null)
    {
      addCursor(cursorToAdvance.getKey(), cursorToAdvance.getValue());
    }

    incorporateNewCursors();
    return !cursors.isEmpty();
  }

  private void recycleExhaustedCursors() throws ChangelogException
  {
    if (!exhaustedCursors.isEmpty())
    {
      // try to recycle exhausted cursors in case the underlying replica DBs received new changes.
      final Map<DBCursor<UpdateMsg>, T> copy = new HashMap<>(exhaustedCursors);
      exhaustedCursors.clear();
      for (Entry<DBCursor<UpdateMsg>, T> entry : copy.entrySet())
      {
        addCursor(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Removes the cursor matching the provided data.
   *
   * @param dataToFind
   *          the data for which the cursor must be found and removed
   */
  protected void removeCursor(final T dataToFind)
  {
    removeCursor(this.cursors, dataToFind);
    removeCursor(this.exhaustedCursors, dataToFind);
  }

  private void removeCursor(Map<DBCursor<UpdateMsg>, T> cursors, T dataToFind)
  {
    for (Iterator<Entry<DBCursor<UpdateMsg>, T>> cursorIter =
        cursors.entrySet().iterator(); cursorIter.hasNext();)
    {
      final Entry<DBCursor<UpdateMsg>, T> entry = cursorIter.next();
      if (dataToFind.equals(entry.getValue()))
      {
        entry.getKey().close();
        cursorIter.remove();
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
  protected void addCursor(final DBCursor<UpdateMsg> cursor, final T data) throws ChangelogException
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
    final Entry<DBCursor<UpdateMsg>, T> entry = cursors.firstEntry();
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
   * Returns the data associated to the cursor that returned the current record.
   *
   * @return the data associated to the cursor that returned the current record.
   */
  public T getData()
  {
    final Entry<DBCursor<UpdateMsg>, T> entry = cursors.firstEntry();
    if (entry != null)
    {
      return entry.getValue();
    }
    return null;
  }

  @Override
  public void close()
  {
    state = CLOSED;
    StaticUtils.close(cursors.keySet());
    StaticUtils.close(exhaustedCursors.keySet());
    cursors.clear();
    exhaustedCursors.clear();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " openCursors=" + cursors
        + " exhaustedCursors=" + exhaustedCursors;
  }
}
