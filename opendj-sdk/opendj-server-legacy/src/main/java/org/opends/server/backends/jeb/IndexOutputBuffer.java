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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.backends.jeb;

import static org.opends.server.backends.jeb.Importer.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.sleepycat.util.PackedInteger;

/**
 * This class represents a index buffer used to store the keys and entry IDs
 * processed from the LDIF file during phase one of an import, or rebuild index
 * process. Each key and ID is stored in a record in the buffer.
 * <p>
 * The records in the buffer are eventually sorted, based on the key, when the
 * maximum size of the buffer is reached and no more records will fit into the
 * buffer. The buffer is scheduled to be flushed to an index scratch file and
 * then re-cycled by the import, or rebuild-index process.
 * </p>
 * <p>
 * The structure of a record in the buffer is the following:
 *
 * <pre>
 * +-------------+-------------+---------+---------+------------+-----------+
 * | record size | INS/DEL bit | indexID | entryID | key length | key bytes |
 * +-------------+-------------+---------+---------+------------+-----------+
 * </pre>
 *
 * The record size is used for fast seeks to quickly "jump" over records.
 * </p>
 * <p>
 * The records are packed as much as possible, to optimize the buffer space.<br>
 * This class is not thread safe.
 * </p>
 */
final class IndexOutputBuffer implements Comparable<IndexOutputBuffer> {

  /** Enumeration used when sorting a buffer. */
  private enum CompareOp {
    LT, GT, LE, GE, EQ
  }

  /**
   * The record overhead. In addition to entryID, key length and key bytes, the
   * record overhead includes the indexID + INS/DEL bit
   */
  private static final int REC_OVERHEAD = INT_SIZE + 1;

  /** Buffer records are either insert records or delete records. */
  private static final byte DEL = 0, INS = 1;

  /** The size of a buffer. */
  private final int size;
  /** Byte array holding the actual buffer data. */
  private final byte buffer[];

  /**
   * Used to break a tie (keys equal) when the buffers are being merged
   * for writing to the index scratch file.
   */
  private long bufferID;

  /** OffSet where next key is written. */
  private int keyOffset;
  /** OffSet where next value record is written. */
  private int recordOffset;
  /** Amount of bytes left in the buffer. */
  private int bytesLeft;
  /** Number of keys in the buffer. */
  private int keys;
  /** Used to iterate over the buffer when writing to a scratch file. */
  private int position;

  /**
   * Used to make sure that an instance of this class is put on the
   * correct scratch file writer work queue for processing.
   */
  private Importer.IndexKey indexKey;

  /** Initial capacity of re-usable buffer used in key compares. */
  private static final int CAP = 32;

  /**
   * This buffer is reused during key compares. It's main purpose is to keep
   * memory footprint as small as possible.
   */
  private ByteBuffer keyBuffer = ByteBuffer.allocate(CAP);

  /**
   * Set to {@code true} if the buffer should not be recycled. Used when the
   * importer/rebuild index process is doing phase one cleanup and flushing
   * buffers not completed.
   */
  private boolean discarded;


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
    indexKey = null;
  }

  /**
   * Creates a new poison buffer. Poison buffers are used to stop the processing of import tasks.
   *
   * @return a new poison buffer
   */
  public static IndexOutputBuffer poison()
  {
    return new IndexOutputBuffer(0);
  }

  /**
   * Set the ID of a buffer to the specified value.
   *
   * @param bufferID The value to set the buffer ID to.
   */
  public void setBufferID(long bufferID)
  {
    this.bufferID = bufferID;
  }

  /**
   * Return the ID of a buffer.
   *
   * @return The value of a buffer's ID.
   */
  private long getBufferID()
  {
    return this.bufferID;
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
    return size == 0;
  }

  /**
   * Determines if buffer should be re-cycled by calling {@link #reset()}.
   *
   * @return {@code true} if buffer should be recycled, or {@code false} if it
   *          should not.
   */
  public boolean isDiscarded()
  {
    return discarded;
  }

  /**
   * Sets the discarded flag to {@code true}.
   */
  public void discard()
  {
    discarded = true;
  }

  /**
   * Returns {@code true} if there is enough space available to write the
   * specified byte array in the buffer. It returns {@code false} otherwise.
   *
   * @param kBytes The byte array to check space against.
   * @param entryID The entryID value to check space against.
   * @return {@code true} if there is space to write the byte array in a
   *         buffer, or {@code false} otherwise.
   */
  public boolean isSpaceAvailable(byte[] kBytes, long entryID) {
    return getRequiredSize(kBytes.length, entryID) < bytesLeft;
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
  public void add(byte[] keyBytes, EntryID entryID, int indexID, boolean insert) {
    // write the record data, but leave the space to write the record size just
    // before it
    recordOffset = addRecord(keyBytes, entryID.longValue(), indexID, insert);
    // then write the returned record size
    keyOffset += writeIntBytes(buffer, keyOffset, recordOffset);
    bytesLeft = recordOffset - keyOffset;
    keys++;
  }


  /**
   * Writes the full record minus the record size itself.
   */
  private int addRecord(byte[] key, long entryID, int indexID, boolean insert)
  {
     int retOffset = recordOffset - getRecordSize(key.length, entryID);
     int offSet = retOffset;

     // write the INS/DEL bit
     buffer[offSet++] = insert ? INS : DEL;
     // write the indexID
     offSet += writeIntBytes(buffer, offSet, indexID);
     // write the entryID
     offSet = PackedInteger.writeLong(buffer, offSet, entryID);
     // write the key length
     offSet = PackedInteger.writeInt(buffer, offSet, key.length);
     // write the key bytes
     System.arraycopy(key, 0, buffer, offSet, key.length);
     return retOffset;
  }


  /**
   * Computes the full size of the record.
   *
   * @param keyLen The length of the key of index
   * @param entryID The entry id
   * @return   The size that such record would take in the IndexOutputBuffer
   */
  public static int getRequiredSize(int keyLen, long entryID)
  {
    // Adds up the key length + key bytes + entryID + indexID + the INS/DEL bit
    // and finally the space needed to store the record size
    return getRecordSize(keyLen, entryID) + INT_SIZE;
  }

  private static int getRecordSize(int keyLen, long entryID)
  {
     // Adds up the key length + key bytes + ...
     return PackedInteger.getWriteIntLength(keyLen) + keyLen +
            // ... entryID + (indexID + INS/DEL bit).
            PackedInteger.getWriteLongLength(entryID) + REC_OVERHEAD;
  }

  /**
   * Write record at specified position to the specified output stream.
   * Used when when writing the index scratch files.
   *
   * @param stream The stream to write the record at the index to.
   * @param position The position of the record to write.
   */
  public void writeEntryID(ByteArrayOutputStream stream, int position)
  {
    int offSet = getOffset(position);
    int len = PackedInteger.getReadLongLength(buffer, offSet + REC_OVERHEAD);
    stream.write(buffer, offSet + REC_OVERHEAD, len);
  }


  /**
   * Return {@code true} if the record specified by the position is an insert
   * record, or {@code false} if it a delete record.
   *
   * @param position The position of the record.
   * @return {@code true} if the record is an insert record, or {@code false}
   *          if it is a delete record.
   */
  public boolean isInsertRecord(int position)
  {
    int recOffset = getOffset(position);
    return buffer[recOffset] == INS;
  }

  /**
   * Return the size of the key part of the record.
   *
   * @return The size of the key part of the record.
   */
  public int getKeySize()
  {
    int offSet = getOffset(position) + REC_OVERHEAD;
    offSet += PackedInteger.getReadLongLength(buffer, offSet);
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

  /** Used to minimized memory usage when comparing keys. */
  private ByteBuffer getKeyBuf(int position)
  {
    keyBuffer.clear();
    int offSet = getOffset(position) + REC_OVERHEAD;
    offSet += PackedInteger.getReadLongLength(buffer, offSet);
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
   * @param position position to return.
   * @return byte array containing the key value.
   */
  private byte[] getKey(int position)
  {
    int offSet = getOffset(position) + REC_OVERHEAD;
    offSet += PackedInteger.getReadLongLength(buffer, offSet);
    int keyLen = PackedInteger.readInt(buffer, offSet);
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    byte[] key = new byte[keyLen];
    System.arraycopy(buffer, offSet, key, 0, keyLen);
    return key;
  }

  private int getOffset(int position)
  {
    return getIntegerValue(position * INT_SIZE);
  }

  /**
   * Return index id associated with the current position's record.
   *
   * @return The index id.
   */
  public int getIndexID()
  {
    return getIndexID(position);
  }

  private int getIndexID(int position)
  {
    return getIndexIDFromOffset(getOffset(position));
  }

  private int getIndexIDFromOffset(int offset)
  {
    return getIntegerValue(offset + 1);
  }

  private boolean is(CompareOp op, int xPosition, int yPosition)
  {
    int xoffSet = getOffset(xPosition);
    int xIndexID = getIndexIDFromOffset(xoffSet);
    xoffSet += REC_OVERHEAD;
    xoffSet += PackedInteger.getReadLongLength(buffer, xoffSet);
    int xKeyLen = PackedInteger.readInt(buffer, xoffSet);
    int xKey = PackedInteger.getReadIntLength(buffer, xoffSet) + xoffSet;

    int yoffSet = getOffset(yPosition);
    int yIndexID = getIndexIDFromOffset(yoffSet);
    yoffSet += REC_OVERHEAD;
    yoffSet += PackedInteger.getReadLongLength(buffer, yoffSet);
    int yKeyLen = PackedInteger.readInt(buffer, yoffSet);
    int yKey = PackedInteger.getReadIntLength(buffer, yoffSet) + yoffSet;

    int cmp = indexComparator.compare(buffer, xKey, xKeyLen, xIndexID, yKey, yKeyLen, yIndexID);
    return evaluateReturnCode(cmp, op);
  }

  private boolean is(CompareOp op, int xPosition, byte[] yKey, int yIndexID)
  {
    int xoffSet = getOffset(xPosition);
    int xIndexID = getIndexIDFromOffset(xoffSet);
    xoffSet += REC_OVERHEAD;
    xoffSet += PackedInteger.getReadLongLength(buffer, xoffSet);
    int xKeyLen = PackedInteger.readInt(buffer, xoffSet);
    int xKey = PackedInteger.getReadIntLength(buffer, xoffSet) + xoffSet;

    int cmp = indexComparator.compare(buffer, xKey, xKeyLen, xIndexID, yKey, yKey.length, yIndexID);
    return evaluateReturnCode(cmp, op);
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
    int offset = getOffset(position);
    int indexID = getIndexIDFromOffset(offset);
    offset += REC_OVERHEAD;
    offset += PackedInteger.getReadLongLength(buffer, offset);
    int keyLen = PackedInteger.readInt(buffer, offset);
    int key = PackedInteger.getReadIntLength(buffer, offset) + offset;
    return indexComparator.compare(buffer, key, keyLen, b, b.length) == 0
        && indexID == bIndexID;
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
  @Override
  public int compareTo(IndexOutputBuffer b)
  {
    final ByteBuffer keyBuf = b.getKeyBuf(b.position);
    int offset = getOffset(position);
    int indexID = getIndexIDFromOffset(offset);
    offset += REC_OVERHEAD;
    offset += PackedInteger.getReadLongLength(buffer, offset);
    int keyLen = PackedInteger.readInt(buffer, offset);
    int key = PackedInteger.getReadIntLength(buffer, offset) + offset;

    final int cmp = indexComparator.compare(buffer, key, keyLen, keyBuf.array(), keyBuf.limit());
    if (cmp != 0)
    {
      return cmp;
    }

    final int bIndexID = b.getIndexID();
    if (indexID == bIndexID)
    {
      // This is tested in a tree set remove when a buffer is removed from the tree set.
      return compare(this.bufferID, b.getBufferID());
    }
    else if (indexID < bIndexID)
    {
      return -1;
    }
    else
    {
      return 1;
    }
  }

  private int compare(long l1, long l2)
  {
    if (l1 == l2)
    {
      return 0;
    }
    else if (l1 < l2)
    {
      return -1;
    }
    else
    {
      return 1;
    }
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
    int offSet = getOffset(position) + REC_OVERHEAD;
    offSet += PackedInteger.getReadLongLength(buffer, offSet);
    int keyLen = PackedInteger.readInt(buffer, offSet);
    offSet += PackedInteger.getReadIntLength(buffer, offSet);
    dataStream.write(buffer, offSet, keyLen);
  }

  /**
   * Compare the byte array at the current position with the byte array at the
   * specified index.
   *
   * @param i The index pointing to the byte array to compare.
   * @return {@code true} if the byte arrays are equal, or {@code false} otherwise
   */
  public boolean compare(int i)
  {
    return is(CompareOp.EQ, i, position);
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
    return position + 1 < keys;
  }

  /**
   * Advance the position pointer to the next record in the buffer. Used when
   * iterating over the buffer examining keys.
   */
  public void nextRecord()
  {
    position++;
  }

  private int writeIntBytes(byte[] buffer, int offset, int val)
  {
    for (int i = offset + INT_SIZE - 1; i >= offset; i--) {
      buffer[i] = (byte) (val & 0xff);
      val >>>= 8;
    }
    return INT_SIZE;
  }

  private int getIntegerValue(int index)
  {
    int answer = 0;
    for (int i = 0; i < INT_SIZE; i++) {
      byte b = buffer[index + i];
      answer <<= 8;
      answer |= b & 0xff;
    }
    return answer;
  }

  private int med3(int a, int b, int c)
  {
    return is(CompareOp.LT, a, b) ?
           (is(CompareOp.LT,b,c) ? b : is(CompareOp.LT,a,c) ? c : a) :
           (is(CompareOp.GT,b,c) ? b : is(CompareOp.GT,a,c) ? c : a);
  }

  private void sort(int off, int len)
  {
    if (len < 7) {
      for (int i=off; i<len+off; i++)
      {
        for (int j=i; j>off && is(CompareOp.GT, j-1, j); j--)
        {
          swap(j, j-1);
        }
      }
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
      while (b <= c && is(CompareOp.LE, b, mKey, mIndexID))
      {
        if (is(CompareOp.EQ, b, mKey, mIndexID))
        {
          swap(a++, b);
        }
        b++;
      }
      while (c >= b && is(CompareOp.GE, c, mKey, mIndexID))
      {
        if (is(CompareOp.EQ, c, mKey, mIndexID))
        {
          swap(c, d--);
        }
        c--;
      }
      if (b > c)
      {
        break;
      }
      swap(b++, c--);
    }

    // Swap partition elements back to middle
    int s, n = off + len;
    s = Math.min(a-off, b-a );
    vectorSwap(off, b-s, s);
    s = Math.min(d-c,   n-d-1);
    vectorSwap(b, n-s, s);

    s = b - a;
    // Recursively sort non-partition-elements
    if (s > 1)
    {
      sort(off, s);
    }
    s = d - c;
    if (s > 1)
    {
      sort(n-s, s);
    }
  }

  private void swap(int a, int b)
  {
     int aOffset = a * INT_SIZE;
     int bOffset = b * INT_SIZE;
     int bVal = getIntegerValue(bOffset);
     System.arraycopy(buffer, aOffset, buffer, bOffset, INT_SIZE);
     writeIntBytes(buffer, aOffset, bVal);
  }

  private void vectorSwap(int a, int b, int n)
  {
    for (int i=0; i<n; i++, a++, b++)
    {
      swap(a, b);
    }
  }

  private boolean evaluateReturnCode(int rc, CompareOp op)
  {
    switch(op) {
    case LT:
      return rc < 0;
    case GT:
      return rc > 0;
    case LE:
      return rc <= 0;
    case GE:
      return rc >= 0;
    case EQ:
      return rc == 0;
    default:
      return false;
    }
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
  public static class IndexComparator implements IndexOutputBuffer.ComparatorBuffer<byte[]>
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
    @Override
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
      return compareLengthThenIndexID(length, indexID, otherLength, otherIndexID);
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
    @Override
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
      return compareLengthThenIndexID(length, indexID, otherLength, otherIndexID);
    }

    /**
     * The arrays are equal, make sure they are in the same index
     * since multiple suffixes might have the same key.
     */
    private int compareLengthThenIndexID(int length, int indexID, int otherLength, int otherIndexID)
    {
      if (length == otherLength)
      {
        return compare(indexID, otherIndexID);
      }
      else if (length > otherLength)
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
    @Override
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
      return compare(length, otherLength);
    }

    private int compare(int i1, int i2)
    {
      if (i1 == i2)
      {
        return 0;
      }
      else if (i1 > i2)
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
