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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Implements an iterator over a range of entry IDs.
 */
public class IndexIteratorRange implements Iterator<EntryID>
{
  /**
   * The upper bound of the range.
   */
  private long highID;

  /**
   * The current position of the iterator in the range.
   */
  private long currentID;



  /**
   * Constructs a range iterator from lower and upper bounds.
   *
   * @param lowID  The lower bound.
   * @param highID The upper bound.
   */
  public IndexIteratorRange(long lowID, long highID)
  {
    this.currentID = lowID;
    this.highID = highID;
  }



  /**
   * Returns <tt>true</tt> if the iteration has more elements. (In other words,
   * returns <tt>true</tt> if <tt>next</tt> would return an element rather than
   * throwing an exception.)
   *
   * @return <tt>true</tt> if the iterator has more elements.
   */
  public boolean hasNext()
  {
    return currentID <= highID;
  }



  /**
   * Returns the next element in the iteration.  Calling this method repeatedly
   * until the {@link #hasNext()} method returns false will return each element
   * in the underlying collection exactly once.
   *
   * @return the next element in the iteration.
   * @throws java.util.NoSuchElementException
   *          iteration has no more elements.
   */
  public EntryID next() throws NoSuchElementException
  {
    if (currentID <= highID)
    {
      return new EntryID(currentID++);
    }
    else
    {
      throw new NoSuchElementException();
    }
  }



  /**
   * Removes from the underlying collection the last element returned by the
   * iterator (optional operation).  This method can be called only once per
   * call to <tt>next</tt>.  The behavior of an iterator is unspecified if the
   * underlying collection is modified while the iteration is in progress in any
   * way other than by calling this method.
   *
   * @throws UnsupportedOperationException if the <tt>remove</tt> operation is
   *                                       not supported by this Iterator.
   */
  public void remove() throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }
}
