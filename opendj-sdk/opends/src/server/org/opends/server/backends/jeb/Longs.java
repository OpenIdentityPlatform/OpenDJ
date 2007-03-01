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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import java.io.DataInputStream;
import java.io.IOException;


/**
 * This class represents a sorted set of longs.  Internally it uses an array
 * that can grow when necessary. A goal of this class is to avoid memory
 * allocations where possible.
 */
public class Longs
{
  /**
   * The internal array where elements are stored.
   */
  private long[] array = null;


  /**
   * The number of valid elements in the array.
   */
  private int count = 0;



  /**
   * Construct a new empty set.
   */
  public Longs()
  {
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
   * Get the number of bytes needed to encode this value into a byte array.
   * @return The number of bytes needed to encode this value into a byte array.
   */
  public int encodedSize()
  {
    return count*8;
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
  byte[] encode(byte[] bytes)
  {
    int encodedSize = encodedSize();
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
  private static int binarySearch(long[] a, int count, long key)
  {
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
  public boolean add(long v)
  {
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
  public void addAll(Longs that)
  {
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
   * Deletes all the elements of a provided set from this set if they are
   * present.
   * @param that The set of elements to be deleted.
   */
  public void deleteAll(Longs that)
  {
    int thisPos, thatPos, destPos;
    for ( destPos = 0, thisPos = 0, thatPos = 0;
          thisPos < count && thatPos < that.count; )
    {
      if ( array[thisPos] < that.array[thatPos] )
      {
        array[destPos++] = array[thisPos++];
      }
      else if ( array[thisPos] > that.array[thatPos] )
      {
        thatPos++;
      }
      else
      {
        thisPos++;
        thatPos++;
      }
    }

    System.arraycopy(array, thisPos, array, destPos, count - thisPos);
    destPos += count - thisPos;

    count = destPos;
  }


  /**
   * Return the number of elements in the set.
   * @return The number of elements in the set.
   */
  public int size()
  {
    return count;
  }


  /**
   * Decode a value from a data input stream.
   * @param dataInputStream The data input stream to read the value from.
   * @throws IOException If an I/O error occurs while reading the value.
   */
  public void decode(DataInputStream dataInputStream)
       throws IOException
  {
    int len = dataInputStream.readInt();
    int count = len/8;
    resize(count);
    for (int i = 0; i < count; i++)
    {
      array[i] = dataInputStream.readLong();
    }
    this.count = count;
  }


  /**
   * Ensures capacity of the internal array for a given number of elements.
   * @param size The internal array will be guaranteed to be at least this
   * size.
   */
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
   * Clears the set leaving it empty.
   */
  public void clear()
  {
    count = 0;
  }



  /**
   * Convert the set to a new array of longs.
   * @return An array of longs.
   */
  public long[] toArray()
  {
    long[] dst = new long[count];

    System.arraycopy(array, 0, dst, 0, count);
    return dst;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    StringBuilder b = new StringBuilder();
    b.append(count);
    if (count > 0)
    {
      b.append('[');
      b.append(array[0]);
      if (count > 1)
      {
        b.append(':');
        b.append(array[count-1]);
      }
      b.append(']');
    }
    return b.toString();
  }

}
