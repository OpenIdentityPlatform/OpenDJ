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
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Represents a set of Entry IDs.  It can represent a set where the IDs are
 * not defined, for example when the index entry limit has been exceeded.
 */
public class EntryIDSet implements Iterable<EntryID>
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.EntryIDSet";


  /**
   * The IDs are stored here in an array in ascending order.
   * A null array implies not defined, rather than zero IDs.
   */
  private long[] values = null;

  /**
   * The database key containing this set, if the set was constructed
   * directly from the database.
   */
  private byte[] keyBytes = null;

  /**
   * Create a new undefined set.
   */
  public EntryIDSet()
  {
    values = null;
  }

  /**
   * Create a new entry ID set from the raw database value.
   *
   * @param keyBytes The database key that contains this value.
   * @param bytes The database value, or null if there are no entry IDs.
   */
  public EntryIDSet(byte[] keyBytes, byte[] bytes)
  {
    this.keyBytes = keyBytes;

    if (bytes == null)
    {
      values = new long[0];
      return;
    }

    values = JebFormat.entryIDListFromDatabase(bytes);
  }

  /**
   * Construct an EntryIDSet from an array of longs.
   *
   * @param values The array of IDs represented as longs.
   * @param pos The position of the first ID to take from the array.
   * @param len the number of IDs to take from the array.
   */
  EntryIDSet(long[] values, int pos, int len)
  {
    this.values = new long[len];
    System.arraycopy(values, pos, this.values, 0, len);
  }

  /**
   * Create a new set of entry IDs that is the union of several entry ID sets.
   *
   * @param sets A list of entry ID sets.
   * @param allowDuplicates true if duplicate IDs are allowed in the resulting
   * set, or if the provided sets are sure not to overlap; false if
   * duplicates should be eliminated.
   * @return The union of the provided entry ID sets.
   */
  public static EntryIDSet unionOfSets(ArrayList<EntryIDSet> sets,
                                         boolean allowDuplicates)
  {
    boolean needSort = false;
    int count = 0;

    for (EntryIDSet l : sets)
    {
      if (!l.isDefined())
      {
        return new EntryIDSet();
      }
      count += l.size();
    }

    long[] n = new long[count];
    int pos = 0;
    for (EntryIDSet l : sets)
    {
      if (l.values.length != 0)
      {
        if (!needSort && pos > 0 && l.values[0] < n[pos-1])
        {
          needSort = true;
        }
        System.arraycopy(l.values, 0, n, pos, l.values.length);
        pos += l.values.length;
      }
    }
    if (needSort)
    {
      Arrays.sort(n);
    }
    if (allowDuplicates)
    {
      EntryIDSet ret = new EntryIDSet();
      ret.values = n;
      return ret;
    }
    long[] n1 = new long[n.length];
    long last = -1;
    int j = 0;
    for (int i = 0; i < n.length; i++)
    {
      if (n[i] != last)
      {
        last = n1[j++] = n[i];
      }
    }
    if (j == n1.length)
    {
      EntryIDSet ret = new EntryIDSet();
      ret.values = n1;
      return ret;
    }
    else
    {
      return new EntryIDSet(n1, 0, j);
    }
  }

  /**
   * Get the size of this entry ID set.
   *
   * @return The number of IDs in the set.
   */
  public int size()
  {
    if (values == null)
    {
      return Integer.MAX_VALUE;
    }
    else
    {
      return values.length;
    }
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder(16);
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Convert to a short string to aid with debugging.
   *
   * @param buffer The string is appended to this string builder.
   */
  public void toString(StringBuilder buffer)
  {
    if (!isDefined())
    {
      if (keyBytes != null)
      {
        // The index entry limit was exceeded
        buffer.append("[LIMIT-EXCEEDED]");
      }
      else
      {
        // Not indexed
        buffer.append("[NOT-INDEXED]");
      }
    }
    else
    {
      buffer.append("[COUNT:");
      buffer.append(size());
      buffer.append("]");
    }
  }

  /**
   * Determine whether this set of IDs is defined.
   *
   * @return true if the set of IDs is defined.
   */
  public boolean isDefined()
  {
    return values != null;
  }

  /**
   * Get a database representation of this object.
   * @return A database representation of this object as a byte array.
   */
  public byte[] toDatabase()
  {
    return JebFormat.entryIDListToDatabase(values);
  }

  /**
   * Insert an ID into this set.
   *
   * @param entryID The ID to be inserted.
   * @return true if the set was changed, false if it was not changed,
   *         for example if the set is undefined or the ID was already present.
   */
  public boolean add(EntryID entryID)
  {
    if (values == null)
    {
      return false;
    }

    long id = entryID.longValue();
    if (values.length == 0)
    {
      values = new long[1];
      values[0] = id;
      return true;
    }

    if (id > values[values.length-1])
    {
      long[] updatedValues = new long[values.length+1];
      System.arraycopy(values, 0, updatedValues, 0, values.length);
      updatedValues[values.length] = id;
      values = updatedValues;
    }
    else
    {
      int pos = Arrays.binarySearch(values, id);
      if (pos >= 0)
      {
        // The ID is already present.
        return false;
      }

      // For a negative return value r, the index -(r+1) gives the array
      // index at which the specified value can be inserted to maintain
      // the sorted order of the array.
      pos = -(pos+1);

      long[] updatedValues = new long[values.length+1];
      System.arraycopy(values, 0, updatedValues, 0, pos);
      System.arraycopy(values, pos, updatedValues, pos+1, values.length-pos);
      updatedValues[pos] = id;
      values = updatedValues;
    }

    return true;
  }

  /**
   * Remove an ID from this set.
   *
   * @param entryID The ID to be removed
   * @return true if the set was changed, false if it was not changed,
   *         for example if the set was undefined or the ID was not present.
   */
  public boolean remove(EntryID entryID)
  {
    if (values == null)
    {
      return false;
    }

    if (values.length == 0)
    {
      return false;
    }

    long id = entryID.longValue();

    // Binary search to locate the ID.
    int pos = Arrays.binarySearch(values, id);
    if (pos < 0)
    {
      // Not found.
      return false;
    }
    else
    {
      // Found it.
      long[] updatedValues = new long[values.length-1];
      System.arraycopy(values, 0, updatedValues, 0, pos);
      System.arraycopy(values, pos+1, updatedValues, pos, values.length-pos-1);
      values = updatedValues;
      return true;
    }
  }

  /**
   * Check whether this set of entry IDs contains a given ID.
   *
   * @param entryID The ID to be checked.
   * @return true if this set contains the given ID,
   *         or if the set is undefined.
   */
  public boolean contains(EntryID entryID)
  {
    if (values == null)
    {
      return true;
    }

    long id = entryID.longValue();
    if (values.length == 0)
    {
      return false;
    }

    if (id > values[values.length-1])
    {
      return false;
    }
    else
    {
      int pos = Arrays.binarySearch(values, id);
      if (pos >= 0)
      {
        return true;
      }
      else
      {
        return false;
      }
    }
  }

  /**
   * Takes the intersection of this set with another.
   * Retain those IDs that appear in the given set.
   *
   * @param that The set of IDs that are to be retained from this object.
   */
  public void retainAll(EntryIDSet that)
  {
    if (!this.isDefined())
    {
      this.values = that.values;
      return;
    }

    if (!that.isDefined())
    {
      return;
    }

    // TODO Perhaps Arrays.asList and retainAll list method are more efficient?

    long[] a = this.values;
    long[] b = that.values;

    int ai = 0, bi = 0, ci = 0;
    long[] c = new long[Math.min(a.length,b.length)];
    while (ai < a.length && bi < b.length)
    {
      if (a[ai] == b[bi])
      {
        c[ci] = a[ai];
        ai++;
        bi++;
        ci++;
      }
      else if (a[ai] > b[bi])
      {
        bi++;
      }
      else
      {
        ai++;
      }
    }
    if (ci < c.length)
    {
      long[] results = new long[ci];
      System.arraycopy(c, 0, results, 0, ci);
      values = results;
    }
    else
    {
      values = c;
    }
  }

  /**
   * Add all the IDs from a given set that are not already present.
   *
   * @param that The set of IDs to be added.
   */
  public void addAll(EntryIDSet that)
  {
    if (!this.isDefined())
    {
      return;
    }

    if (!that.isDefined())
    {
      values = null;
      return;
    }

    long[] a = this.values;
    long[] b = that.values;

    if (a.length == 0)
    {
      values = b;
      return;
    }

    if (b.length == 0)
    {
      return;
    }

    // Optimize for case where the two sets are sure to have no overlap.
    if (b[0] > a[a.length-1])
    {
      // All IDs in 'b' are greater than those in 'a'.
      long[] n = new long[a.length + b.length];
      System.arraycopy(a, 0, n, 0, a.length);
      System.arraycopy(b, 0, n, a.length, b.length);
      values = n;
      return;
    }

    if (a[0] > b[b.length-1])
    {
      // All IDs in 'a' are greater than those in 'b'.
      long[] n = new long[a.length + b.length];
      System.arraycopy(b, 0, n, 0, b.length);
      System.arraycopy(a, 0, n, b.length, a.length);
      values = n;
      return;
    }

    long[] n;
    if ( b.length < a.length ) {
      n = a;
      a = b;
      b = n;
    }

    n = new long[a.length + b.length];

    int ai, bi, ni;
    for ( ni = 0, ai = 0, bi = 0; ai < a.length && bi < b.length; ) {
      if ( a[ai] < b[bi] ) {
        n[ni++] = a[ai++];
      } else if ( b[bi] < a[ai] ) {
        n[ni++] = b[bi++];
      } else {
        n[ni++] = a[ai];
        ai++;
        bi++;
      }
    }

    // Copy any remainder from the first array.
    int aRemain = a.length - ai;
    if (aRemain > 0)
    {
      System.arraycopy(a, ai, n, ni, aRemain);
      ni += aRemain;
    }

    // Copy any remainder from the second array.
    int bRemain = b.length - bi;
    if (bRemain > 0)
    {
      System.arraycopy(b, bi, n, ni, bRemain);
      ni += bRemain;
    }

    if (ni < n.length)
    {
      long[] results = new long[ni];
      System.arraycopy(n, 0, results, 0, ni);
      values = results;
    }
    else
    {
      values = n;
    }
  }

  /**
   * Delete all IDs in this set that are in a given set.
   *
   * @param that The set of IDs to be deleted.
   */
  public void deleteAll(EntryIDSet that)
  {
    if (!this.isDefined())
    {
      return;
    }

    if (!that.isDefined())
    {
      values = null;
      return;
    }

    long[] a = this.values;
    long[] b = that.values;

    if (a.length == 0 || b.length == 0)
    {
      return;
    }

    // Optimize for case where the two sets are sure to have no overlap.
    if (b[0] > a[a.length-1])
    {
      return;
    }

    if (a[0] > b[b.length-1])
    {
      return;
    }

    long[] n;

    n = new long[a.length];

    int ai, bi, ni;
    for ( ni = 0, ai = 0, bi = 0; ai < a.length && bi < b.length; ) {
      if ( a[ai] < b[bi] ) {
        n[ni++] = a[ai++];
      } else if ( b[bi] < a[ai] ) {
        bi++;
      } else {
        ai++;
        bi++;
      }
    }

    System.arraycopy(a, ai, n, ni, a.length - ai);
    ni += a.length - ai;

    if (ni < a.length)
    {
      long[] results = new long[ni];
      System.arraycopy(n, 0, results, 0, ni);
      values = results;
    }
    else
    {
      values = n;
    }
  }

  /**
   * Create an iterator over the set, or over the entire database
   * if the set is not defined.
   *
   * @return An EntryID iterator.
   */
  public Iterator<EntryID> iterator()
  {
    if (values == null)
    {
      // The set is not defined.
      return new IndexIteratorAllIds();
    }
    else
    {
      // The set is defined.
      return new IDSetIterator(values);
    }
  }

}
