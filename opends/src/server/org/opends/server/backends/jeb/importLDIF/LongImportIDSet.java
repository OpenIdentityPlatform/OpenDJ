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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb.importLDIF;

import com.sleepycat.je.dbi.MemoryBudget;
import org.opends.server.util.RuntimeInformation;
import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.jeb.JebFormat;


/**
 * A import ID set backed by an array of longs.
 */
public class LongImportIDSet implements ImportIDSet {


  //Overhead values gleamed from JHAT.
  private final static int LONGS_OVERHEAD;
  private final static int LONGS_OVERHEAD_32 = 25;
  private final static int LONGS_OVERHEAD_64 = 25;

  /**
   * The internal array where elements are stored.
   */
  private long[] array = null;


  /**
   * The number of valid elements in the array.
   */
  private int count = 0;


  //Boolean to keep track if the instance is defined or not.
  boolean isDefined=true;


  //Size of the undefines.
  private long undefinedSize = 0;


  static {
    if(RuntimeInformation.is64Bit()) {
      LONGS_OVERHEAD = LONGS_OVERHEAD_64;
    } else {
      LONGS_OVERHEAD = LONGS_OVERHEAD_32;
    }
  }

  /**
   * Create an empty instance.
   */
  public LongImportIDSet() {
  }

  /**
   * Create instance and add specified entry ID to the set.
   *
   * @param id The entry ID.
   */
  public LongImportIDSet(EntryID id) {
     this.array = new long[1];
     this.array[0] = id.longValue();
     count=1;
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
  public void setUndefined() {
    array = null;
    isDefined = false;
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
  public int getMemorySize() {
    if(array != null) {
      return LONGS_OVERHEAD + MemoryBudget.byteArraySize(array.length * 8);
    } else {
      return LONGS_OVERHEAD;
    }
  }


  /**
   * {@inheritDoc}
   */
  public boolean merge(byte[] DBbytes, ImportIDSet importIdSet,
                       int limit, boolean maintainCount) {
    boolean incrLimitCount=false;
    boolean dbUndefined = ((DBbytes[0] & 0x80) == 0x80);

    if(dbUndefined) {
      isDefined=false;
    } else if(!importIdSet.isDefined()) {
      isDefined=false;
      incrLimitCount=true;
    } else {
      array = JebFormat.entryIDListFromDatabase(DBbytes);
      if(array.length + importIdSet.size() > limit) {
          isDefined=false;
          incrLimitCount=true;
          importIdSet.setUndefined();
      } else {
        count = array.length;
        addAll((LongImportIDSet) importIdSet);
      }
    }
    return incrLimitCount;
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
       add(entryID.longValue());
    }
  }


  /**
   * {@inheritDoc}
   */
  public byte[] toDatabase() {
    if (isDefined()) {
      return encode(null);
    } else {
      return JebFormat.entryIDUndefinedSizeToDatabase(undefinedSize);
    }
  }

  /**
   * Decodes a set from a byte array.
   * @param bytes The encoded value.
   */
  void decode(byte[] bytes)
  {
    if (bytes == null)
    {
      count = 0;
      return;
    }

    int count = bytes.length / 8;
    resize(count);

    for (int pos = 0, i = 0; i < count; i++)
    {
      long v = 0;
      v |= (bytes[pos++] & 0xFFL) << 56;
      v |= (bytes[pos++] & 0xFFL) << 48;
      v |= (bytes[pos++] & 0xFFL) << 40;
      v |= (bytes[pos++] & 0xFFL) << 32;
      v |= (bytes[pos++] & 0xFFL) << 24;
      v |= (bytes[pos++] & 0xFFL) << 16;
      v |= (bytes[pos++] & 0xFFL) << 8;
      v |= (bytes[pos++] & 0xFFL);
      array[i] = v;
    }
    this.count = count;
  }


  /**
   * Encode this value into a byte array.
   * @param bytes The array into which the value will be encoded.  If the
   * provided array is null, or is not big enough, a new array will be
   * allocated.
   * @return The encoded array. If the provided array was bigger than needed
   * to encode the value then the provided array is returned and the number
   * of bytes of useful data is given by the encodedSize method.
   */
  byte[] encode(byte[] bytes) {
    int encodedSize = count * 8;
    if (bytes == null || bytes.length < encodedSize)
    {
      bytes = new byte[encodedSize];
    }

    for (int pos = 0, i = 0; i < count; i++)
    {
      long v = array[i];
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



  /**
   * This is very much like Arrays.binarySearch except that it searches only
   * an initial portion of the provided array.
   * @param a The array to be searched.
   * @param count The number of initial elements in the array to be searched.
   * @param key The element to search for.
   * @return See Arrays.binarySearch.
   */
  private static int binarySearch(long[] a, int count, long key) {
    int low = 0;
    int high = count-1;

    while (low <= high)
    {
      int mid = (low + high) >> 1;
      long midVal = a[mid];

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
   * Add a new value to the set.
   * @param v The value to be added.
   * @return true if the value was added, false if it was already present
   * in the set.
   */
  private boolean add(long v) {
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
   * Adds all the elements of a provided set to this set if they are not
   * already present.
   * @param that The set of elements to be added.
   */
  private void addAll(LongImportIDSet that) {
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
   * Ensures capacity of the internal array for a given number of elements.
   * @param size The internal array will be guaranteed to be at least this
   * size.
   */
  private void resize(int size) {
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

}
