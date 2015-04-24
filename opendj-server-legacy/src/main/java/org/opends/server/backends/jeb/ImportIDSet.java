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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.nio.ByteBuffer;

import org.forgerock.util.Reject;

/**
 * This class manages the set of ID that are to be eventually added to an index
 * database. It is responsible for determining if the number of IDs is above
 * the configured ID limit. If the limit it reached, the class stops tracking
 * individual IDs and marks the set as undefined. This class is not thread safe.
 */
class ImportIDSet {

  /** The internal array where elements are stored. */
  private long[] array;
  /** The number of valid elements in the array. */
  private int count;
  /** Boolean to keep track if the instance is defined or not. */
  private boolean isDefined = true;
  /** Size of the undefined if count is kept. */
  private long undefinedSize;
  /** Key related to an ID set. */
  private ByteBuffer key;
  /** The entry limit size. */
  private final int limit;
  /** Set to true if a count of ids above the entry limit should be kept. */
  private final boolean doCount;

  /**
   * Create an import ID set of the specified size, index limit and index
   * maintain count, plus an extra 128 slots.
   *
   * @param size The size of the the underlying array, plus some extra space.
   * @param limit The index entry limit.
   * @param doCount The index maintain count.
   */
  public ImportIDSet(int size, int limit, boolean doCount)
  {
    this.array = new long[size + 128];
    // A limit of 0 means unlimited.
    this.limit = limit == 0 ? Integer.MAX_VALUE : limit;
    this.doCount = doCount;
  }

  /**
   * Clear the set so it can be reused again. The boolean indexParam specifies
   * if the index parameters should be cleared also.
   */
  public void clear()
  {
    undefinedSize = 0;
    isDefined = true;
    count = 0;
  }

  /**
   * Return if an import ID set is defined or not.
   *
   * @return <CODE>True</CODE> if an import ID set is defined.
   */
  public boolean isDefined()
  {
    return isDefined;
  }

  /** Set an import ID set to undefined. */
  void setUndefined() {
    array = null;
    isDefined = false;
  }

  /**
   * Add the specified entry id to an import ID set.
   *
   * @param entryID  The entry ID to add to an import ID set.
   */
  void addEntryID(EntryID entryID) {
    addEntryID(entryID.longValue());
  }

  /**
   * Add the specified long value to an import ID set.
   *
   * @param entryID The long value to add to an import ID set.
   */
  void addEntryID(long entryID) {
    Reject.ifTrue(entryID < 0, "entryID must always be positive");

    if(!isDefined()) {
      if(doCount)  {
        undefinedSize++;
      }
      return;
    }
    if (isDefined() && count + 1 > limit)
    {
      setUndefined();
      if(doCount)  {
        undefinedSize = count + 1;
      } else {
        undefinedSize = Long.MAX_VALUE;
      }
      count = 0;
    } else {
      add(entryID);
    }
  }

  private boolean mergeCount(byte[] dBbytes, ImportIDSet importIdSet)  {
    boolean incrementLimitCount=false;
    boolean dbUndefined = isDBUndefined(dBbytes);

    if (dbUndefined && !importIdSet.isDefined())  {
      undefinedSize = JebFormat.entryIDUndefinedSizeFromDatabase(dBbytes) +
              importIdSet.undefinedSize;
      isDefined=false;
    } else if (dbUndefined && importIdSet.isDefined())  {
      undefinedSize = JebFormat.entryIDUndefinedSizeFromDatabase(dBbytes) +
              importIdSet.size();
      isDefined=false;
    } else if(!importIdSet.isDefined()) {
      int dbSize = JebFormat.entryIDListFromDatabase(dBbytes).length;
      undefinedSize = dbSize + importIdSet.undefinedSize;
      isDefined = false;
      incrementLimitCount = true;
    } else {
      array = JebFormat.entryIDListFromDatabase(dBbytes);
      if(array.length + importIdSet.size() > limit) {
        undefinedSize = array.length + importIdSet.size();
        isDefined=false;
        incrementLimitCount=true;
      } else {
        count = array.length;
        addAll(importIdSet);
      }
    }
    return incrementLimitCount;
  }

  /**
   * Remove the specified import ID set from the byte array read from the DB.
   *
   * @param bytes The byte array read from JEB.
   * @param importIdSet The import ID set to delete.
   */
  public void remove(byte[] bytes, ImportIDSet importIdSet)
  {
    if (isDBUndefined(bytes)) {
      isDefined=false;
      importIdSet.setUndefined();
      undefinedSize = Long.MAX_VALUE;
    } else if(!importIdSet.isDefined()) {
      isDefined=false;
      undefinedSize = Long.MAX_VALUE;
    } else {
      array = JebFormat.entryIDListFromDatabase(bytes);
      if(array.length - importIdSet.size() > limit) {
        isDefined=false;
        count = 0;
        importIdSet.setUndefined();
        undefinedSize = Long.MAX_VALUE;
      } else {
        count = array.length;
        removeAll(importIdSet);
      }
    }
  }

  /**
   * Merge the specified byte array read from a DB, with the specified import
   * ID set. The specified limit and maintain count parameters define
   * if the newly merged set is defined or not.
   *
   * @param bytes The byte array of IDs read from a DB.
   * @param importIdSet The import ID set to merge the byte array with.
   * @return <CODE>True</CODE> if the import ID set started keeping a count as
   *         a result of the merge.
   */
  public boolean merge(byte[] bytes, ImportIDSet importIdSet)
  {
    boolean incrementLimitCount=false;
    if(doCount) {
      incrementLimitCount = mergeCount(bytes,  importIdSet);
    } else if (isDBUndefined(bytes)) {
      isDefined = false;
      importIdSet.setUndefined();
      undefinedSize = Long.MAX_VALUE;
      count = 0;
    } else if(!importIdSet.isDefined()) {
      isDefined = false;
      incrementLimitCount = true;
      undefinedSize = Long.MAX_VALUE;
      count = 0;
    } else {
      array = JebFormat.entryIDListFromDatabase(bytes);
      if (array.length + importIdSet.size() > limit) {
        isDefined = false;
        incrementLimitCount = true;
        count = 0;
        importIdSet.setUndefined();
        undefinedSize = Long.MAX_VALUE;
      } else {
        count = array.length;
        addAll(importIdSet);
      }
    }
    return incrementLimitCount;
  }

  private boolean isDBUndefined(byte[] bytes)
  {
    return (bytes[0] & 0x80) == 0x80;
  }

  private void removeAll(ImportIDSet that) {
    long[] newArray = new long[array.length];
    int c = 0;
    for(int i=0; i < count; i++)
    {
      if(binarySearch(that.array, that.count, array[i]) < 0)
      {
        newArray[c++] = array[i];
      }
    }
    array = newArray;
    count = c;
  }

  private  void addAll(ImportIDSet that) {
    resize(this.count+that.count);

    if (that.count == 0)
    {
      return;
    }

    // Optimize for the case where the two sets are sure to have no overlap.
    if (this.count == 0 || that.array[0] > this.array[this.count-1])
    {
      System.arraycopy(that.array, 0, this.array, this.count, that.count);
      count += that.count;
      return;
    }

    if (this.array[0] > that.array[that.count-1])
    {
      System.arraycopy(this.array, 0, this.array, that.count, this.count);
      System.arraycopy(that.array, 0, this.array, 0, that.count);
      count += that.count;
      return;
    }

    int destPos = binarySearch(this.array, this.count, that.array[0]);
    if (destPos < 0)
    {
      destPos = -(destPos+1);
    }

    // Make space for the copy.
    int aCount = this.count - destPos;
    int aPos = destPos + that.count;
    int aEnd = aPos + aCount;
    System.arraycopy(this.array, destPos, this.array, aPos, aCount);

    // Optimize for the case where there is no overlap.
    if (this.array[aPos] > that.array[that.count-1])
    {
      System.arraycopy(that.array, 0, this.array, destPos, that.count);
      count += that.count;
      return;
    }

    int bPos;
    for ( bPos = 0; aPos < aEnd && bPos < that.count; )
    {
      if ( this.array[aPos] < that.array[bPos] )
      {
        this.array[destPos++] = this.array[aPos++];
      }
      else if ( this.array[aPos] > that.array[bPos] )
      {
        this.array[destPos++] = that.array[bPos++];
      }
      else
      {
        this.array[destPos++] = this.array[aPos++];
        bPos++;
      }
    }

    // Copy any remainder.
    int aRemain = aEnd - aPos;
    if (aRemain > 0)
    {
      System.arraycopy(this.array, aPos, this.array, destPos, aRemain);
      destPos += aRemain;
    }

    int bRemain = that.count - bPos;
    if (bRemain > 0)
    {
      System.arraycopy(that.array, bPos, this.array, destPos, bRemain);
      destPos += bRemain;
    }

    count = destPos;
  }

  /**
   * Return the number of IDs in an import ID set.
   *
   * @return The current size of an import ID set.
   */
  public int size()
  {
    return count;
  }

  private boolean add(long entryID)
  {
    resize(count+1);

    if (count == 0 || entryID > array[count-1])
    {
      array[count++] = entryID;
      return true;
    }

    int pos = binarySearch(array, count, entryID);
    if (pos >=0)
    {
      return false;
    }

    // For a negative return value r, the index -(r+1) gives the array
    // index at which the specified value can be inserted to maintain
    // the sorted order of the array.
    pos = -(pos+1);

    System.arraycopy(array, pos, array, pos+1, count-pos);
    array[pos] = entryID;
    count++;
    return true;
  }

  private static int binarySearch(long[] a, int count, long key)
  {
    int low = 0;
    int high = count-1;

    while (low <= high)
    {
      int mid = low + high >> 1;
      long midVal = a[mid];

      if (midVal < key)
      {
        low = mid + 1;
      }
      else if (midVal > key)
      {
        high = mid - 1;
      }
      else
      {
        return mid; // key found
      }
    }
    return -(low + 1);  // key not found.
  }

  private void resize(int size)
  {
    if (array == null)
    {
      array = new long[size];
    }
    else if (array.length < size)
    {
      // Expand the size of the array in powers of two.
      int newSize = array.length == 0 ? 1 : array.length;
      do
      {
        newSize *= 2;
      } while (newSize < size);

      long[] newBytes = new long[newSize];
      System.arraycopy(array, 0, newBytes, 0, count);
      array = newBytes;
    }
  }

  /**
   * Create a byte array suitable to write to a JEB DB from an import ID set.
   *
   * @return A byte array suitable for writing to a JEB DB.
   */
  public byte[] toDatabase()
  {
    if(isDefined) {
      return encodeDefined();
    } else {
      return JebFormat.entryIDUndefinedSizeToDatabase(undefinedSize);
    }
  }

  private byte[] encodeDefined()
  {
    final int encodedSize = count * 8;
    final byte[] bytes = new byte[encodedSize];
    int pos = 0;
    for (int i = 0; i < count; i++) {
      final long id = array[i] & 0x00ffffffffL; // JNR: why is this necessary?

      // encode the entryID
      bytes[pos++] = (byte) ((id >>> 56) & 0xFF);
      bytes[pos++] = (byte) ((id >>> 48) & 0xFF);
      bytes[pos++] = (byte) ((id >>> 40) & 0xFF);
      bytes[pos++] = (byte) ((id >>> 32) & 0xFF);
      bytes[pos++] = (byte) ((id >>> 24) & 0xFF);
      bytes[pos++] = (byte) ((id >>> 16) & 0xFF);
      bytes[pos++] = (byte) ((id >>> 8) & 0xFF);
      bytes[pos++] = (byte) (id & 0xFF);
    }
    return bytes;
  }

  /**
   * Set the DB key related to an import ID set.
   *
   * @param key Byte array containing the key.
   */
  public void setKey(ByteBuffer key)
  {
    this.key = key;
  }

  /**
   * Return the DB key related to an import ID set.
   *
   * @return  The byte array containing the key.
   */
  public ByteBuffer getKey()
  {
    return key;
  }
}
