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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb.importLDIF;

import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.jeb.JebFormat;
import com.sleepycat.je.dbi.MemoryBudget;

/**
 * An import ID set backed by an array of ints.
 */
public class IntegerImportIDSet implements ImportIDSet {


  //Indicate if a undefined import set has been written to the index DB.
  private boolean dirty = true;

  //Gleamed from JHAT. The same for 32/64 bit.
  private final static int THIS_OVERHEAD = 25;

  /**
   * The internal array where elements are stored.
   */
  private int[] array = null;


  /**
   * The number of valid elements in the array.
   */
  private int count = 0;


  //Boolean to keep track if the instance is defined or not.
  private boolean isDefined=true;


  //Size of the undefines.
  private long undefinedSize = 0;

  /**
   * Create an empty import set.
   */
  public IntegerImportIDSet() {
  }

  /**
   * Create an import set and add the specified entry ID to it.
   *
   * @param id The entry ID.
   */
  public IntegerImportIDSet(EntryID id) {
    this.array = new int[1];
    this.array[0] = (int) id.longValue();
    count=1;
  }

  /**
   * {@inheritDoc}
   */
  public void setEntryID(EntryID id) {
    if(array == null) {
      this.array = new int[1];
    }
    reset();
    this.array[0] = (int) id.longValue();
    count=1;
  }

  /**
   * {@inheritDoc}
   */
  public void reset() {
    count = 0;
    isDefined = true;
    undefinedSize = 0;
    dirty = true;
  }

  /**
   * {@inheritDoc}
   */
  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isDirty() {
    return dirty;
  }
  /**
   * {@inheritDoc}
   */
  public boolean isDefined() {
    return isDefined;
  }

   /**
   * {@inheritDoc}
   */
  public long getUndefinedSize() {
    return undefinedSize;
  }

  /**
   * {@inheritDoc}
   */
  public void setUndefined() {
    array = null;
    isDefined = false;
  }


  /**
   * {@inheritDoc}
   */
  public int getMemorySize() {
    if(array != null) {
      return THIS_OVERHEAD + MemoryBudget.byteArraySize(array.length * 4);
    } else {
      return THIS_OVERHEAD;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void
  merge(ImportIDSet importIDSet, int limit, boolean maintainCount) {
    if(!isDefined()) {
      if(maintainCount)  {
        if(importIDSet.isDefined()) {
          undefinedSize += importIDSet.size();
        } else {
          undefinedSize += importIDSet.getUndefinedSize();
        }
      }
      return;
    }
    if(isDefined() && ((count + importIDSet.size()) > limit)) {
      isDefined = false;
      if(maintainCount)  {
        undefinedSize = size() + importIDSet.size();
      } else {
        undefinedSize = Long.MAX_VALUE;
      }
      array = null;
      count = 0;
    } else {
      addAll((IntegerImportIDSet) importIDSet);
    }
  }


  /**
   * {@inheritDoc}
   */
  public void addEntryID(EntryID entryID, int limit, boolean maintainCount) {
    if(!isDefined()) {
      if(maintainCount)  {
        undefinedSize++;
      }
      return;
    }
    if(isDefined() && ((count + 1) > limit)) {
      isDefined = false;
      array = null;
      if(maintainCount)  {
        undefinedSize = count + 1;
      } else {
        undefinedSize = Long.MAX_VALUE;
      }
      count = 0;
    } else {
      add((int)entryID.longValue());
    }
  }

  /**
   * More complicated version of merge below that keeps track of the undefined
   * sizes when in undefined mode or moving to undefined mode.
   *
   * @param dBbytes The bytes read from jeb.
   * @param importIdSet
   * @param limit
   * @return
   */
  private boolean
  mergeCount(byte[] dBbytes, ImportIDSet importIdSet, int limit)  {
    boolean incrLimitCount=false;
    boolean dbUndefined = ((dBbytes[0] & 0x80) == 0x80);

    if(dbUndefined && (!importIdSet.isDefined()))  {
       undefinedSize = JebFormat.entryIDUndefinedSizeFromDatabase(dBbytes) +
                                                 importIdSet.getUndefinedSize();
       isDefined=false;
    } else if(dbUndefined && (importIdSet.isDefined()))  {
       undefinedSize = JebFormat.entryIDUndefinedSizeFromDatabase(dBbytes) +
                                                 importIdSet.size();
       importIdSet.setUndefined();
       isDefined=false;
    } else if(!importIdSet.isDefined()) {
       int dbSize = JebFormat.entryIDListFromDatabase(dBbytes).length;
       undefinedSize= dbSize + importIdSet.getUndefinedSize();
       isDefined=false;
       incrLimitCount = true;
    } else {
      array = JebFormat.intArrayFromDatabaseBytes(dBbytes);
      if(array.length + importIdSet.size() > limit) {
          undefinedSize = array.length + importIdSet.size();
          importIdSet.setUndefined();
          isDefined=false;
          incrLimitCount=true;
      } else {
        count = array.length;
        addAll((IntegerImportIDSet) importIdSet);
      }
    }
    return incrLimitCount;
  }

  /**
   * {@inheritDoc}
   */
  public boolean merge(byte[] dBbytes, ImportIDSet importIdSet,
                       int limit, boolean maintainCount) {
    boolean incrLimitCount=false;
    if(maintainCount) {
      incrLimitCount = mergeCount(dBbytes,  importIdSet, limit);
    } else {
      boolean dbUndefined = ((dBbytes[0] & 0x80) == 0x80);
      if(dbUndefined) {
        isDefined=false;
        importIdSet.setUndefined();
        undefinedSize = Long.MAX_VALUE;
      } else if(!importIdSet.isDefined()) {
        isDefined=false;
        incrLimitCount=true;
        undefinedSize = Long.MAX_VALUE;
      } else {
        array = JebFormat.intArrayFromDatabaseBytes(dBbytes);
        if(array.length + importIdSet.size() > limit) {
          isDefined=false;
          incrLimitCount=true;
          count = 0;
          importIdSet.setUndefined();
          undefinedSize = Long.MAX_VALUE;
        } else {
          count = array.length;
          addAll((IntegerImportIDSet) importIdSet);
        }
      }
    }
    return incrLimitCount;
  }

  /**
   * Add all of the specified import ID set to the import set.
   *
   * @param that The import ID set to add.
   */
  private  void addAll(IntegerImportIDSet that) {
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
   * {@inheritDoc}
   */
  public int size() {
    return count;
  }


  /**
   * Add the specified integer to the import set.
   *
   * @param v The integer value to add.
   * @return <CODE>True</CODE> if the value was added.
   */
  private boolean add(int v) {
    resize(count+1);

    if (count == 0 || v > array[count-1])
    {
      array[count++] = v;
      return true;
    }

    int pos = binarySearch(array, count, v);
    if (pos >=0)
    {
      return false;
    }

    // For a negative return value r, the index -(r+1) gives the array
    // index at which the specified value can be inserted to maintain
    // the sorted order of the array.
    pos = -(pos+1);

    System.arraycopy(array, pos, array, pos+1, count-pos);
    array[pos] = v;
    count++;
    return true;
  }

  /**
   * Perform binary search for the specified key in the specified array.
   *
   * @param a The array to search in.
   * @param count The max value in the array.
   * @param key The key value.
   * @return Position in array key is found or a negative if it wasn't found.
   */
  private static int binarySearch(int[] a, int count, int key) {
    int low = 0;
    int high = count-1;

    while (low <= high)
    {
      int mid = (low + high) >> 1;
      int midVal = a[mid];

      if (midVal < key)
        low = mid + 1;
      else if (midVal > key)
        high = mid - 1;
      else
        return mid; // key found
    }
    return -(low + 1);  // key not found.
  }

  /**
   * Resize the array to the specified size if needed.
   *
   * @param size The required size.
   */
  private void resize(int size) {
    if (array == null)
    {
      array = new int[size];
    }
    else if (array.length < size)
    {
      // Expand the size of the array in powers of two.
      int newSize = array.length == 0 ? 1 : array.length;
      do
      {
        newSize *= 2;
      } while (newSize < size);

      int[] newBytes = new int[newSize];
      System.arraycopy(array, 0, newBytes, 0, count);
      array = newBytes;
    }

  }


  /**
   * {@inheritDoc}
   */
  public byte[] toDatabase() {
    if(isDefined) {
       return encode(null);
     } else {
       return JebFormat.entryIDUndefinedSizeToDatabase(undefinedSize);
     }
   }

  /**
   * Encode the integer array to a byte array suitable for writing to DB.
   *
   * @param bytes The byte array to use in the encoding.
   * @return A byte array suitable to write to DB.
   */
  private byte[] encode(byte[] bytes) {
    int encodedSize = count * 8;
    if (bytes == null || bytes.length < encodedSize) {
      bytes = new byte[encodedSize];
    }

    for (int pos = 0, i = 0; i < count; i++) {
      long v = (long)array[i] & 0x00ffffffffL;
      bytes[pos++] = (byte) ((v >>> 56) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 48) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 40) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 32) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 24) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 16) & 0xFF);
      bytes[pos++] = (byte) ((v >>> 8) & 0xFF);
      bytes[pos++] = (byte) (v & 0xFF);
    }

    return bytes;
  }
}
