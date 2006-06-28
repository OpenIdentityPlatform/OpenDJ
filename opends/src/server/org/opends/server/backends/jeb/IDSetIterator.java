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
 * Iterator for a set of Entry IDs.  It must return values in order of ID.
 */
public class IDSetIterator implements Iterator<EntryID>
{
  /**
   * An array of ID values in order of ID.
   */
  private long[] entryIDList;

  /**
   * Current position of the iterator as an index into the array of IDs.
   */
  private int i;

  /**
   * Create a new iterator for a given array of entry IDs.
   * @param entryIDList An array of IDs in order or ID.
   */
  public IDSetIterator(long[] entryIDList)
  {
    this.entryIDList = entryIDList;
  }

  /**
   * Returns <tt>true</tt> if the iteration has more elements. (In other
   * words, returns <tt>true</tt> if <tt>next</tt> would return an element
   * rather than throwing an exception.)
   *
   * @return <tt>true</tt> if the iterator has more elements.
   */
  public boolean hasNext()
  {
    return i < entryIDList.length;
  }

  /**
   * Returns the next element in the iteration.  Calling this method
   * repeatedly until the {@link #hasNext()} method returns false will
   * return each element in the underlying collection exactly once.
   *
   * @return the next element in the iteration.
   * @throws java.util.NoSuchElementException
   *          iteration has no more elements.
   */
  public EntryID next()
       throws NoSuchElementException
  {
    if (i < entryIDList.length)
    {
      return new EntryID(entryIDList[i++]);
    }
    throw new NoSuchElementException();
  }

  /**
   *
   * Removes from the underlying collection the last element returned by the
   * iterator (optional operation).  This method can be called only once per
   * call to <tt>next</tt>.  The behavior of an iterator is unspecified if
   * the underlying collection is modified while the iteration is in
   * progress in any way other than by calling this method.
   *
   * @exception UnsupportedOperationException if the <tt>remove</tt>
   *            operation is not supported by this Iterator.
   */
  public void remove() throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }
}
