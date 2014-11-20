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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.forgerock.opendj.ldap.ByteString;

/**
 * Represents a set of Entry IDs.  It can represent a set where the IDs are
 * not defined, for example when the index entry limit has been exceeded.
 */
public class EntryIDSet implements Iterable<EntryID>
{

  /**
   * The IDs are stored here in an array in ascending order.
   * A null array implies not defined, rather than zero IDs.
   */
  private long[] values;

  /**
   * The size of the set when it is not defined. This value is only maintained
   * when the set is undefined.
   */
  private long undefinedSize = Long.MAX_VALUE;

  /**
   * The database key containing this set, if the set was constructed
   * directly from the database.
   */
  private final ByteString keyBytes;

  /** Create a new undefined set. */
  public EntryIDSet()
  {
    this.keyBytes = null;
    this.undefinedSize = Long.MAX_VALUE;
  }

  /**
   * Create a new undefined set with a initial size.
   *
   * @param size The undefined size for this set.
   */
  public EntryIDSet(long size)
  {
    this.keyBytes = null;
    this.undefinedSize = size;
  }

  /**
   * Create a new entry ID set from the raw database value.
   *
   * @param keyBytes The database key that contains this value.
   * @param bytes The database value, or null if there are no entry IDs.
   */
  public EntryIDSet(byte[] keyBytes, byte[] bytes)
  {
    this(keyBytes != null ? ByteString.wrap(keyBytes) : null,
        bytes != null ? ByteString.wrap(bytes) : null);
  }

  /**
   * Create a new entry ID set from the raw database value.
   *
   * @param keyBytes
   *          The database key that contains this value.
   * @param bytes
   *          The database value, or null if there are no entry IDs.
   */
  public EntryIDSet(ByteString keyBytes, ByteString bytes)
  {
    this.keyBytes = keyBytes;

    if (bytes == null)
    {
      values = new long[0];
      return;
    }

    if (bytes.length() == 0)
    {
      // Entry limit has exceeded and there is no encoded undefined set size.
      undefinedSize = Long.MAX_VALUE;
    }
    else if ((bytes.byteAt(0) & 0x80) == 0x80)
    {
      // Entry limit has exceeded and there is an encoded undefined set size.
      undefinedSize =
          JebFormat.entryIDUndefinedSizeFromDatabase(bytes.toByteArray());
    }
    else
    {
      // Seems like entry limit has not been exceeded and the bytes is a
      // list of entry IDs.
      values = JebFormat.entryIDListFromDatabase(bytes.toByteArray());
    }
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
    this.keyBytes = null;
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
    int count = 0;

    boolean undefined = false;
    for (EntryIDSet l : sets)
    {
      if (!l.isDefined())
      {
        if(l.undefinedSize == Long.MAX_VALUE)
        {
          return new EntryIDSet();
        }
        undefined = true;
      }
      count += l.size();
    }

    if(undefined)
    {
      return new EntryIDSet(count);
    }

    boolean needSort = false;
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
    for (long l : n)
    {
      if (l != last)
      {
        last = n1[j++] = l;
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
  public long size()
  {
    if (values != null)
    {
      return values.length;
    }
    return undefinedSize;
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  @Override
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
        if(undefinedSize == Long.MAX_VALUE)
        {
          buffer.append("[LIMIT-EXCEEDED]");
        }
        else
        {
          buffer.append("[LIMIT-EXCEEDED:");
          buffer.append(undefinedSize);
          buffer.append("]");
        }
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
    if(isDefined())
    {
      return JebFormat.entryIDListToDatabase(values);
    }
    else
    {
      return JebFormat.entryIDUndefinedSizeToDatabase(undefinedSize);
    }
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
      if(undefinedSize != Long.MAX_VALUE)
      {
        undefinedSize++;
      }
      return true;
    }

    long id = entryID.longValue();
    if (values.length == 0)
    {
      values = new long[] { id };
      return true;
    }

    if (id > values[values.length-1])
    {
      long[] updatedValues = Arrays.copyOf(values, values.length + 1);
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
      if(undefinedSize != Long.MAX_VALUE)
      {
        undefinedSize--;
      }
      return true;
    }

    if (values.length == 0)
    {
      return false;
    }

    // Binary search to locate the ID.
    long id = entryID.longValue();
    int pos = Arrays.binarySearch(values, id);
    if (pos >= 0)
    {
      // Found it.
      long[] updatedValues = new long[values.length-1];
      System.arraycopy(values, 0, updatedValues, 0, pos);
      System.arraycopy(values, pos+1, updatedValues, pos, values.length-pos-1);
      values = updatedValues;
      return true;
    }
    // Not found.
    return false;
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

    final long id = entryID.longValue();
    return values.length != 0
        && id <= values[values.length - 1]
        && Arrays.binarySearch(values, id) >= 0;
  }

  /**
   * Takes the intersection of this set with another.
   * Retain those IDs that appear in the given set.
   *
   * @param that The set of IDs that are to be retained from this object.
   */
  public void retainAll(EntryIDSet that)
  {
    if (!isDefined())
    {
      this.values = that.values;
      this.undefinedSize = that.undefinedSize;
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
      values = Arrays.copyOf(c, ci);
    }
    else
    {
      values = c;
    }
  }

  /**
   * Add all the IDs from a given set that are not already present.
   *
   * @param that The set of IDs to be added. It MUST be defined
   */
  public void addAll(EntryIDSet that)
  {
    if(!that.isDefined())
    {
      return;
    }

    if (!isDefined())
    {
      // Assume there are no overlap between IDs in that set with this set
      if(undefinedSize != Long.MAX_VALUE)
      {
        undefinedSize += that.size();
      }
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
      values = Arrays.copyOf(n, ni);
    }
    else
    {
      values = n;
    }
  }

  /**
   * Delete all IDs in this set that are in a given set.
   *
   * @param that The set of IDs to be deleted. It MUST be defined.
   */
  public void deleteAll(EntryIDSet that)
  {
    if(!that.isDefined())
    {
      return;
    }

    if (!isDefined())
    {
      // Assume all IDs in the given set exists in this set.
      if(undefinedSize != Long.MAX_VALUE)
      {
        undefinedSize -= that.size();
      }
      return;
    }

    long[] a = this.values;
    long[] b = that.values;

    if (a.length == 0 || b.length == 0
        // Optimize for cases where the two sets are sure to have no overlap.
        || b[0] > a[a.length-1]
        || a[0] > b[b.length-1])
    {
      return;
    }

    long[] n = new long[a.length];

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
      values = Arrays.copyOf(n, ni);
    }
    else
    {
      values = n;
    }
  }

  /**
   * Create an iterator over the set or an empty iterator
   * if the set is not defined.
   *
   * @return An EntryID iterator.
   */
  @Override
  public Iterator<EntryID> iterator()
  {
    return iterator(null);
  }

  /**
   * Create an iterator over the set or an empty iterator
   * if the set is not defined.
   *
   * @param  begin  The entry ID of the first entry to return in the list.
   *
   * @return An EntryID iterator.
   */
  public Iterator<EntryID> iterator(EntryID begin)
  {
    if (values != null)
    {
      // The set is defined.
      return new IDSetIterator(values, begin);
    }
    // The set is not defined.
    return new IDSetIterator(new long[0]);
  }

}
