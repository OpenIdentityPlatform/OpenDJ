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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */


package org.opends.server.backends.jeb.importLDIF;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.opends.server.backends.jeb.*;
import com.sleepycat.util.PackedInteger;


/**
 * This class represents a index buffer used to store the keys and entry IDs
 * processed from the LDIF file during phase one of an import, or rebuild index
 * process. Each key and ID is stored in a record in the buffer.
 *
 * The records in the buffer are eventually sorted, based on the key, when the
 * maximum size of the buffer is reached and no more records will fit into the
 * buffer. The buffer is the scheduled to be flushed to an indexes scratch file
 * and then re-cycled by the import, or rebuild-index process.
 *
 * The records are packed as much as possible, to optimize the buffer space.
 * This class is not thread safe.
 *
 */
public final class IndexOutputBuffer implements Comparable<IndexOutputBuffer> {

  /**
  * Enumeration used when sorting a buffer.
  */
  private enum CompareOp {
    LT, GT, LE, GE, EQ
  }

  //The record over head.
  private static final int REC_OVERHEAD = 5;

  //The size of int.
  private static final int INT_SIZE = 4;

  //Buffer records are either insert records or delete records.
  private static final byte DEL = 0, INS = 1;

  //The size of a buffer.
  private final int size;

  //Byte array holding the actual buffer data.
  private final byte buffer[];

  //id is used to break a tie (keys equal) when the buffers are being merged
  //for writing to the index scratch file.
  private long id;

  //Temporary buffer used to store integer values.
  private final byte[] intBytes = new byte[INT_SIZE];

  /*
    keyOffset - offSet where next key is written
    recordOffset- offSet where next value record is written
    bytesLeft - amount of bytes left in the buffer
  */
  private int keyOffset =0, recordOffset=0, bytesLeft = 0;

  //keys - number of keys in the buffer
  //position - used to iterate over the buffer when writing to a scratch file.
  private int keys = 0, position = 0;

  //The comparator to use sort the keys.
  private ComparatorBuffer<byte[]> comparator;

  //This is used to make sure that an instance of this class is put on the
  //correct scratch file writer work queue for processing.
  private Importer.IndexKey indexKey;

  //Initial capacity of re-usable buffer used in key compares.
  private static final int CAP = 32;

  //This buffer is reused during key compares. It's main purpose is to keep
  //memory footprint as small as possible.
  private ByteBuffer keyBuffer = ByteBuffer.allocate(CAP);

  //Set to {@code true} if the buffer should not be recycled. Used when the
  //importer/rebuild index process is doing phase one cleanup and flushing
  //buffers not completed.
  private boolean discard = false;


  /**
   * Create an instance of a IndexBuffer using the specified size.
   *
   * @param size The size of the underlying byte array.
   */
  public IndexOutputBuffer(int size) {
    this.size = size;
    this.buffer = new byte[size];
    this.bytesLeft = size;
    this.recordOffset = size - 1;
  }


  /**
   * Reset an IndexBuffer so it can be re-cycled.
   */
  public void reset() {
    bytesLeft = size;
    keyOffset = 0;
    recordOffset = size - 1;
    keys = 0;
    position = 0;
    comparator = null;
    indexKey = null;
  }


  /**
   * Set the ID of a buffer to the specified value.
   *
   * @param id The value to set the ID to.
   */
  public void setID(long id)
  {
    this.id = id;
  }


  /**
   * Return the ID of a buffer.
   *
   * @return The value of a buffer's ID.
   */
  private long getBufferID()
  {
    return this.id;
  }


  /**
   * Determines if a buffer is a poison buffer. A poison buffer is used to
   * shutdown work queues when import/rebuild index phase one is completed.
   * A poison buffer has a 0 size.
   *
   * @return {@code true} if a buffer is a poison buffer, or {@code false}
   *         otherwise.
   */
  public boolean isPoison()
  {
    return (size == 0);
  }


  /**
   * Determines of a buffer should be re-cycled.
   *
   * @return {@code true} if buffer should be recycled, or {@code false} if it
   *          should not.
   */
  public boolean isDiscard()
  {
    return discard;
  }


  /**
   * Set the discard flag to {@code true}.
   */
  public void setDiscard()
  {
    discard = true;
  }


  /**
   * Returns {@code true} if there is enough space available to write the
   * specified byte array in the buffer. It returns {@code false} otherwise.
   *
   * @param kBytes The byte array to check space against.
   * @param id The id value to check space against.
   * @return {@code true} if there is space to write the byte array in a
   *         buffer, or {@code false} otherwise.
   */
  public boolean isSpaceAvailable(byte[] kBytes, long id) {
    return (getRecordSize(kBytes.length, id) + INT_SIZE) < bytesLeft;
  }


  /**
   * Set the comparator to be used in the buffer processing to the specified
   * comparator.
   *
   * @param comparator The comparator to set the buffer's comparator to.
   */
  public void setComparator(ComparatorBuffer<byte[]> comparator)
  {
    this.comparator = comparator;
  }


  /**
   * Return a buffer's current position value.
   *
   * @return The buffer's current position value.
   */
  public int getPosition()
  {
    return position;
  }


  /**
   * Set a buffer's position value to the specified position.
   *
   * @param position The value to set the position to.
   */
  public void setPosition(int position)
  {
    this.position = position;
  }


  /**
   * Sort the buffer.
   */
  public void sort() {
    sort(0, keys);
  }


  /**
   * Add the specified key byte array and EntryID to the buffer.
   *
   * @param keyBytes The key byte array.
   * @param entryID The EntryID.
   * @param indexID The index ID the record belongs.
   * @param insert <CODE>True</CODE> if key is an insert, false otherwise.
   */

  public void add(byte[] keyBytes, EntryID entryID, int indexID,
                  boolean insert) {
    recordOffset = addRecord(keyBytes, entryID.longValue(), indexID, insert);
    System.arraycopy(getIntBytes(recordOffset), 0, buffer, keyOffset, INT_SIZE);
    keyOffset += INT_SIZE;
    bytesLeft = recordOffset - keyOffset;
    keys++;
  }


  private int addRecord(byte[]key, long id, int indexID, boolean insert)
  {
     int retOffset = recordOffset - getRecordSize(key.length, id);
     int offSet = retOffset;

     buffer[offSet++] = insert ? INS : DEL;
     System.arraycopy(getIntBytes(indexID), 0, buffer, offSet, INT_SIZE);
     offSet += INT_SIZE;
     offSet = PackedInteger.writeLong(buffer, offSet, id);
     offSet = PackedInteger.writeInt(buffer, offSet, key.length);
     System.arraycopy(key, 0, buffer, offSet, key.length);
     return retOffset;
  }


  /**
   * Computes the full size of the record.
   *
   * @param keyLen The length of the key of index
   * @param id The entry id
   * @return   The size that such record would take in the IndexOutputBuffer
   */
  public static int getRequiredSize(int keyLen, long id)
  {
    return PackedInteger.getWriteIntLength(keyLen) +  keyLen +
        PackedInteger.getWriteLongLength(id) + REC_OVERHEAD + INT_SIZE;
  }

  private int getRecordSize(int keyLen, long id)
  {
     return PackedInteger.getWriteIntLength(keyLen) +  keyLen +
            PackedInteger.getWriteLongLength(id) + REC_OVERHEAD;
  }


  /**
   * Write record at specified index to the specified output stream. Used when
   * when writing the index scratch files.

   * @param stream The stream to write the record at the index to.
   * @param index The index of the record to write.
   */
  public void writeID(ByteArrayOutputStream stream, int index)
  {
    int offSet = getIntegerValue(index * INT_SIZE);
    int len = PackedInteger.getReadLongLength(buffer, offSet + REC_OVERHEAD);
    stream.write(buffer, offSet + REC_OVERHEAD, len);
  }


  /**
   * Return {@code true} if the record specified by the index is an insert
   * record, or {@code false} if it a delete record.
   *
   * @param index The index of the record.
   *
   * @return {@code true} if the record is an insert record, or {@code false}
   *          if it is a delete record.
   */
  public boolean isInsert(int index)
  {
    int recOffset = getIntegerValue(index * INT_SIZE);
    return buffer[recOffset] != DEL;
  }


  /**
   * Return the size of the key part of the record.
   *
   * @return The size of the key part of the record.
   */
  public int getKeySize()
  {
    int offSet = getIntegerValue(position * INT_SIZE) + REC_OVERHEAD;
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    return PackedInteger.readInt(buffer, offSet);
  }


  /**
   * Return the key value part of a record indicated by the current buffer
   * position.
   *
   * @return byte array containing the key value.
   */
  public byte[] getKey()
  {
    return getKey(position);
  }

  //Used to minimized memory usage when comparing keys.
  private ByteBuffer getKeyBuf(int x)
  {
    keyBuffer.clear();
    int offSet = getIntegerValue(x * INT_SIZE) + REC_OVERHEAD;
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    int keyLen = PackedInteger.readInt(buffer, offSet);
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    //Re-allocate if the key is bigger than the capacity.
    if(keyLen > keyBuffer.capacity())
    {
      keyBuffer = ByteBuffer.allocate(keyLen);
    }
    keyBuffer.put(buffer, offSet, keyLen);
    keyBuffer.flip();
    return keyBuffer;
  }


  /**
   * Return the key value part of a record specified by the index.
   *
   * @param x index to return.
   * @return byte array containing the key value.
   */
  private byte[] getKey(int x)
  {
    int offSet = getIntegerValue(x * INT_SIZE) + REC_OVERHEAD;
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    int keyLen = PackedInteger.readInt(buffer, offSet);
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    byte[] key = new byte[keyLen];
    System.arraycopy(buffer, offSet, key, 0, keyLen);
    return key;
  }


  private int getIndexID(int x)
  {
    return getIntegerValue(getIntegerValue(x * INT_SIZE) + 1);
  }


  /**
   * Return index id associated with the current position's record.
   *
   * @return The index id.
   */
  public int getIndexID()
  {
     return getIntegerValue(getIntegerValue(position * INT_SIZE) + 1);
  }


  private boolean is(int x, int y, CompareOp op)
  {
    int xoffSet = getIntegerValue(x * INT_SIZE);
    int xIndexID = getIntegerValue(xoffSet + 1);
    xoffSet += REC_OVERHEAD;
    xoffSet += PackedInteger.getReadIntLength(buffer, xoffSet);
    int xKeyLen = PackedInteger.readInt(buffer, xoffSet);
    int xKey = PackedInteger.getReadIntLength(buffer, xoffSet) + xoffSet;
    int yoffSet = getIntegerValue(y * INT_SIZE);
    int yIndexID = getIntegerValue(yoffSet + 1);
    yoffSet += REC_OVERHEAD;
    yoffSet += PackedInteger.getReadIntLength(buffer, yoffSet);
    int yKeyLen = PackedInteger.readInt(buffer, yoffSet);
    int yKey = PackedInteger.getReadIntLength(buffer, yoffSet) + yoffSet;
    return evaluateReturnCode(comparator.compare(buffer, xKey, xKeyLen,
                              xIndexID, yKey, yKeyLen, yIndexID), op);
  }


  private boolean is(int x, byte[] yKey, CompareOp op, int yIndexID)
  {
    int xoffSet = getIntegerValue(x * INT_SIZE);
    int xIndexID = getIntegerValue(xoffSet + 1);
    xoffSet += REC_OVERHEAD;
    xoffSet += PackedInteger.getReadIntLength(buffer, xoffSet);
    int xKeyLen = PackedInteger.readInt(buffer, xoffSet);
    int xKey = PackedInteger.getReadIntLength(buffer, xoffSet) + xoffSet;
    return evaluateReturnCode(comparator.compare(buffer, xKey, xKeyLen,
                        xIndexID, yKey, yKey.length, yIndexID), op);
  }


  /**
   * Compare the byte array at the current position with the specified one and
   * using the specified index id. It will return {@code true} if the byte
   * array at the current position is equal to the specified byte array as
   * determined by the comparator and the index ID is is equal. It will
   * return {@code false} otherwise.
   *
   * @param b The byte array to compare.
   * @param bIndexID The index key.
   * @return <CODE>True</CODE> if the byte arrays are equal.
   */
  public boolean compare(byte[]b, int bIndexID)
  {
    int offset = getIntegerValue(position * INT_SIZE);
    int indexID = getIntegerValue(offset + 1);
    offset += REC_OVERHEAD;
    offset += PackedInteger.getReadIntLength(buffer, offset);
    int keyLen = PackedInteger.readInt(buffer, offset);
    int key = PackedInteger.getReadIntLength(buffer, offset) + offset;
    if( comparator.compare(buffer, key, keyLen, b, b.length) == 0)
    {
      if(indexID == bIndexID)
      {
        return true;
      }
    }
    return false;
  }


  /**
   * Compare current IndexBuffer to the specified index buffer using both the
   * comparator and index ID of both buffers.
   *
   * The key at the value of position in both buffers are used in the compare.
   *
   * @param b The IndexBuffer to compare to.
   * @return  0 if the buffers are equal, -1 if the current buffer is less
   *          than the specified buffer, or 1 if it is greater.
   */
  public int compareTo(IndexOutputBuffer b)
  {
    ByteBuffer keyBuf = b.getKeyBuf(b.position);
    int offset = getIntegerValue(position * INT_SIZE);
    int indexID = getIntegerValue(offset + 1);
    offset += REC_OVERHEAD;
    offset += PackedInteger.getReadIntLength(buffer, offset);
    int keyLen = PackedInteger.readInt(buffer, offset);
    int key = PackedInteger.getReadIntLength(buffer, offset) + offset;
    int returnCode = comparator.compare(buffer, key, keyLen, keyBuf.array(),
                                        keyBuf.limit());
    if(returnCode == 0)
    {
      int bIndexID = b.getIndexID();
      if(indexID == bIndexID)
      {
        long otherBufferID = b.getBufferID();
        //This is tested in a tree set remove when a buffer is removed from
        //the tree set.
        if(this.id == otherBufferID)
        {
          returnCode = 0;
        }
        else if(this.id < otherBufferID)
        {
          returnCode = -1;
        }
        else
        {
          returnCode = 1;
        }
      }
      else if(indexID < bIndexID)
      {
        returnCode = -1;
      }
      else
      {
        returnCode = 1;
      }
    }
    return returnCode;
  }


  /**
   * Write a record to specified output stream using the record pointed to by
   * the current position and the specified byte stream of ids.
   *
   * @param dataStream The data output stream to write to.
   *
   * @throws IOException If an I/O error occurs writing the record.
   */
  public void writeKey(DataOutputStream dataStream) throws IOException
  {
    int offSet = getIntegerValue(position * INT_SIZE) + REC_OVERHEAD;
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    int keyLen = PackedInteger.readInt(buffer, offSet);
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    dataStream.write(buffer, offSet, keyLen);
  }


   /**
   * Compare the byte array at the current position with the byte array at the
   * specified index.
   *
   * @param i The index pointing to the byte array to compare.
   * @return {@code true} if the byte arrays are equal, or {@code false}
    *                     otherwise.
   */
  public boolean compare(int i)
  {
      return is(i, position, CompareOp.EQ);
  }


  /**
   * Return the current number of keys.
   *
   * @return The number of keys currently in an index buffer.
   */
  public int getNumberKeys()
  {
    return keys;
  }


  /**
   * Return {@code true} if the buffer has more data to process, or
   * {@code false} otherwise. Used when iterating over the buffer writing the
   * scratch index file.
   *
   * @return {@code true} if a buffer has more data to process, or
   *         {@code false} otherwise.
   */
  public boolean hasMoreData()
   {
     return (position + 1) < keys;
   }


  /**
   * Advance the position pointer to the next record in the buffer. Used when
   * iterating over the buffer examining keys.
   */
   public void getNextRecord()
   {
     position++;
   }


  private byte[] getIntBytes(int val)
  {
    for (int i = 3; i >= 0; i--) {
      intBytes[i] = (byte) (val & 0xff);
      val >>>= 8;
    }
    return intBytes;
  }


  private int getIntegerValue(int index)
  {
    int answer = 0;
    for (int i = 0; i < INT_SIZE; i++) {
      byte b = buffer[index + i];
      answer <<= 8;
      answer |= (b & 0xff);
    }
    return answer;
  }


  private int med3(int a, int b, int c)
  {
    return (is(a,b, CompareOp.LT) ?
           (is(b,c,CompareOp.LT) ? b : is(a,c,CompareOp.LT) ? c : a) :
           (is(b,c,CompareOp.GT) ? b :is(a,c,CompareOp.GT) ? c : a));
  }


  private void sort(int off, int len)
  {
    if (len < 7) {
      for (int i=off; i<len+off; i++)
        for (int j=i; j>off && is(j-1, j, CompareOp.GT); j--)
          swap(j, j-1);
      return;
    }

    int m = off + (len >> 1);
    if (len > 7) {
      int l = off;
      int n = off + len - 1;
      if (len > 40) {
        int s = len/8;
        l = med3(l, l+s, l+2*s);
        m = med3(m-s, m,  m+s);
        n = med3(n-2*s, n-s, n);
      }
      m = med3(l, m, n);
    }

    byte[] mKey = getKey(m);
    int mIndexID = getIndexID(m);

    int a = off, b = a, c = off + len - 1, d = c;
    while(true)
    {
      while ((b <= c) && is(b, mKey, CompareOp.LE, mIndexID))
      {
        if (is(b, mKey, CompareOp.EQ, mIndexID))
          swap(a++, b);
        b++;
      }
      while (c >= b && is(c, mKey, CompareOp.GE, mIndexID))
      {
        if (is(c, mKey, CompareOp.EQ, mIndexID))
          swap(c, d--);
        c--;
      }
      if (b > c)
        break;
      swap(b++, c--);
    }

    // Swap partition elements back to middle
    int s, n = off + len;
    s = Math.min(a-off, b-a );
    vectorSwap(off, b-s, s);
    s = Math.min(d-c,   n-d-1);
    vectorSwap(b, n-s, s);

    // Recursively sort non-partition-elements
    if ((s = b-a) > 1)
      sort(off, s);
    if ((s = d-c) > 1)
      sort(n-s, s);
  }


  private void swap(int a, int b)
  {
    int aOffset = a * INT_SIZE;
     int bOffset = b * INT_SIZE;
     int bVal = getIntegerValue(bOffset);
     System.arraycopy(buffer, aOffset, buffer, bOffset, INT_SIZE);
     System.arraycopy(getIntBytes(bVal), 0, buffer, aOffset, INT_SIZE);
  }


  private void vectorSwap(int a, int b, int n)
  {
    for (int i=0; i<n; i++, a++, b++)
      swap(a, b);
  }


  private boolean evaluateReturnCode(int rc, CompareOp op)
  {
    boolean returnCode = false;
    switch(op) {
    case LT:
      returnCode = rc < 0;
      break;
    case GT:
      returnCode = rc > 0;
      break;
    case LE:
      returnCode = rc <= 0;
      break;
    case GE:
      returnCode = rc >= 0;
      break;
    case EQ:
      returnCode = rc == 0;
      break;
    }
    return returnCode;
  }


  /**
   * Interface that defines two methods used to compare keys used in this
   * class. The Comparator interface cannot be used in this class, so this
   * special one is used that knows about the special properties of this class.
   *
   * @param <T> object to use in the compare
   */
  public static interface ComparatorBuffer<T> {


     /**
     * Compare two offsets in an object, usually a byte array.
     *
     * @param o The object.
     * @param offset The first offset.
     * @param length The first length.
     * @param indexID The first index id.
     * @param otherOffset The second offset.
     * @param otherLength The second length.
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second.
     */
    int compare(T o, int offset, int length, int indexID, int otherOffset,
                int otherLength, int otherIndexID);


    /**
     * Compare an offset in an object with the specified object.
     *
     * @param o The first object.
     * @param offset The first offset.
     * @param length The first length.
     * @param indexID The first index id.
     * @param other The second object.
     * @param otherLength The length of the second object.
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         object.
     */
    int compare(T o, int offset, int length, int indexID, T other,
                int otherLength, int otherIndexID);


    /**
     * Compare an offset in an object with the specified object.
     *
     * @param o The first object.
     * @param offset The first offset.
     * @param length The first length.
     * @param other The second object.
     * @param otherLen The length of the second object.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         object.
     */
    int compare(T o, int offset, int length, T other,
                int otherLen);

  }


  /**
   * Implementation of ComparatorBuffer interface. Used to compare keys when
   * they are non-DN indexes.
   */
  public static
  class IndexComparator implements IndexOutputBuffer.ComparatorBuffer<byte[]>
  {

    /**
     * Compare two offsets in an byte array using the index compare
     * algorithm.  The specified index ID is used in the comparison if the
     * byte arrays are equal.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param length The first length.
     * @param indexID The first index id.
     * @param otherOffset The second offset.
     * @param otherLength The second length.
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second.
     */
    public int compare(byte[] b, int offset, int length, int indexID,
                       int otherOffset, int otherLength, int otherIndexID)
    {
      for(int i = 0; i < length && i < otherLength; i++)
      {
        if(b[offset + i] > b[otherOffset + i])
        {
          return 1;
        }
        else if (b[offset + i] < b[otherOffset + i])
        {
          return -1;
        }
      }
      //The arrays are equal, make sure they are in the same index since
      //multiple suffixes might have the same key.
      if(length == otherLength)
      {
        if(indexID == otherIndexID)
        {
          return 0;
        }
        else if(indexID > otherIndexID)
        {
          return 1;
        }
        else
        {
          return -1;
        }
      }
      if (length > otherLength)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }


    /**
     * Compare an offset in an byte array with the specified byte array,
     * using the DN compare algorithm.   The specified index ID is used in the
     * comparison if the byte arrays are equal.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param length The first length.
     * @param indexID The first index id.
     * @param other The second byte array to compare to.
     * @param otherLength The second byte array's length.
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int length, int indexID,
                       byte[] other, int otherLength, int otherIndexID)
    {
      for(int i = 0; i < length && i < otherLength; i++)
      {
        if(b[offset + i] > other[i])
        {
          return 1;
        }
        else if (b[offset + i] < other[i])
        {
          return -1;
        }
      }
      //The arrays are equal, make sure they are in the same index since
      //multiple suffixes might have the same key.
      if(length == otherLength)
      {
        if(indexID == otherIndexID)
        {
          return 0;
        }
        else if(indexID > otherIndexID)
        {
          return 1;
        }
        else
        {
          return -1;
        }
      }
      if (length > otherLength)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }


    /**
     * Compare an offset in an byte array with the specified byte array,
     * using the DN compare algorithm.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param length The first length.
     * @param other The second byte array to compare to.
     * @param otherLength The second byte array's length.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int length, byte[] other,
                       int otherLength)
    {
      for(int i = 0; i < length && i < otherLength; i++)
      {
        if(b[offset + i] > other[i])
        {
          return 1;
        }
        else if (b[offset + i] < other[i])
        {
          return -1;
        }
      }
      if(length == otherLength)
      {
        return 0;
      }
      if (length > otherLength)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
  }


  /**
   * Set the index key associated with an index buffer.
   *
   * @param indexKey The index key.
   */
  public void setIndexKey(Importer.IndexKey indexKey)
  {
    this.indexKey = indexKey;
  }


  /**
   * Return the index key of an index buffer.
   * @return The index buffer's index key.
   */
  public Importer.IndexKey getIndexKey()
  {
    return indexKey;
  }
}
