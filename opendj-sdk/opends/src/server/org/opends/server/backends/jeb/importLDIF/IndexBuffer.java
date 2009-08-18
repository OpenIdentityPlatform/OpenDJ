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
 *      Copyright 2009 Sun Microsystems, Inc.
 */


package org.opends.server.backends.jeb.importLDIF;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import org.opends.server.backends.jeb.*;


/**
 * This class is used to hold the keys read from the LDIF file during
 * phase 1. The keys are sorted and written to an temporary index file.
 *
 */
public class IndexBuffer implements Comparable<IndexBuffer> {

    /**
  * Enumeration used when sorting a buffer.
  */
  private enum CompareOp {
    LT, GT, LE, GE, EQ
  }

  private static final int REC_OVERHEAD = 20;

  /**
  * Insert constant -- used when the key should be inserted in a DB.
  */
  public static final int INSERT = 0x0000;

  /**
  * Delete constant -- used when the key should be deleted from a DB.
  */
  public static final int DELETE = 0x0001;

  //The size of a buffer.
  private final int size;

  //Byte array holding the actual buffer data.
  private final byte buffer[];

  //id is used to break a tie (keys equal) when the buffers are being merged
  //when writing.
  private long id;

  //Temporary buffers.
  private final byte[] intBytes = new byte[4];
  private final byte[] idBytes = new byte[8];

  /*
    keyOffset - offSet where next key is written
    recordOffset- offSet where next value record is written
    bytesLeft - amount of bytes left in the buffer
  */
  private int keyOffset =0, recordOffset=0, bytesLeft = 0;

  //keys - number of keys in the buffer
  //position - used to iterate over the buffer when writing to a file.
  private int keys = 0, position = 0;

  //Various things needed to process a buffer.
  private ComparatorBuffer<byte[]> comparator;
  private Importer.IndexKey indexKey;


  private IndexBuffer(int size) {
    this.size = size;
    this.buffer = new byte[size];
    this.bytesLeft = size;
    this.recordOffset = size - 1;
  }

  /**
   * Create an instance of a IndexBuffer using the specified size.
   *
   * @param size The size of the underlying byte array.
   * @return A newly created instance of an IndexBuffer.
   */
  public static
  IndexBuffer createIndexBuffer(int size) {
    return new IndexBuffer(size);
  }

  /**
   * Reset an IndexBuffer so it can be re-used.
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
   * Determines if a buffer is a poison buffer. A poison buffer is used to
   * shutdown work queues when the LDIF reader is completed. A poison buffer
   * has a 0 size.
   *
   * @return <CODE>True</CODE> if a buffer is a poison buffer.
   */
  public boolean isPoison()
  {
    return (size == 0);
  }

  /**
   * Return the ID of a buffer.
   *
   * @return The value of a buffer's ID.
   */
  public long getBufferID()
  {
    return this.id;
  }

  /**
   * Determine is there enough space available to write the specified byte array
   * in the buffer.
   *
   * @param keyBytes The byte array to check space against.
   * @return <CODE>True</CODE> if there is space to write the byte array in a
   *         buffer.
   */
  public boolean isSpaceAvailable(byte[] keyBytes) {
    return (keyBytes.length + REC_OVERHEAD + 4) < bytesLeft;
  }

  /**
   * Set the comparator to be used in the buffer processing to the specified
   * value.
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
   * Set a buffer's position value to the specified value.
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
   * @param IDEntry The EntryID.
   * @param indexID The index ID the record belongs.
   * @param insert <CODE>True</CODE> if key is an insert, false otherwise.
   */
  public void add(byte[] keyBytes, EntryID IDEntry, int indexID,
                  boolean insert) {
    byte[] idBytes = JebFormat.entryIDToDatabase(IDEntry.longValue());
    recordOffset -=  keyBytes.length + REC_OVERHEAD;
    System.arraycopy(getIntBytes(recordOffset), 0, buffer, keyOffset, 4);
    keyOffset += 4;
    System.arraycopy(getIntBytes(indexID), 0, buffer, recordOffset, 4);
    System.arraycopy(getIntBytes(keyBytes.length), 0, buffer,
                     (recordOffset + 4), 4);
    System.arraycopy(keyBytes, 0, buffer, (recordOffset + 8), keyBytes.length);
    if(insert)
    {
      System.arraycopy(getIntBytes(INSERT), 0, buffer,
                      (recordOffset + 8 + keyBytes.length), 4);
    }
    else
    {
      System.arraycopy(getIntBytes(DELETE), 0, buffer,
                      (recordOffset + 8 + keyBytes.length), 4);
    }
    System.arraycopy(idBytes, 0, buffer,
                     (recordOffset + 12 + keyBytes.length), 8);
    bytesLeft = recordOffset - keyOffset;
    keys++;
  }


  /**
   * Return the byte array representing the entry ID
   * at the specified index value.
   *
   * @param index The index value to retrieve.
   * @return The byte array at the index value.
   */
  public byte[] getIDBytes(int index)
  {
    int recOffset = getIntValue(index * 4);
    int keyLen = getIntValue(recOffset + 4);
    System.arraycopy(buffer, recOffset + 12 + keyLen, idBytes, 0, 8);
    return idBytes;
  }


  /**
   * Return if the record specified by the index is an insert or not.
   * @param index The index of the record.
   *
   * @return <CODE>True</CODE> if the record is an insert, false otherwise.
   */
  public boolean isInsert(int index)
  {
    boolean returnCode = true;
    int recOffset = getIntValue(index * 4);
    int keyLen = getIntValue(recOffset + 4);
    if(getIntValue(recOffset + 8 + keyLen) == DELETE)
    {
      returnCode = false;
    }

    return returnCode;
  }


  /**
   * Return the size of the key part of the record.
   *
   * @return The size of the key part of the record.
   */
  public int getKeySize()
  {
    int recOffset = getIntValue(position * 4);
    return getIntValue(recOffset + 4);
  }


  private int getIndexID(int x)
  {
    return getIntValue(getIntValue(x * 4));
  }


  /**
   * Return index id associated with the current position's record.
   *
   * @return The index id.
   */
  public int getIndexID()
   {
     return getIntValue(getIntValue(position * 4));
   }

    /**
     * Write a record to the specified data output stream using the specified
     * parameters.
     *
     * @param key  The key byte array.
     * @param indexID The index ID.
     * @param insertByteStream The byte stream containing insert  ids.
     * @param deleteByteStream The byte stream containing delete ids.
     * @param dataStream The data output stream to write to.
     * @return The record size written.
     * @throws IOException If an I/O error occurs writing the record.
     */
    public static int writeRecord(byte[] key, int indexID,
                                  ByteArrayOutputStream insertByteStream,
                                  ByteArrayOutputStream deleteByteStream,
                                  DataOutputStream dataStream)
            throws IOException
    {
        dataStream.writeInt(indexID);
        dataStream.writeInt(key.length);
        dataStream.write(key);
        dataStream.writeInt(insertByteStream.size());
        if(insertByteStream.size() > 0)
        {
            insertByteStream.writeTo(dataStream);
        }
        dataStream.writeInt(deleteByteStream.size());
        if(deleteByteStream.size() > 0)
        {
            deleteByteStream.writeTo(dataStream);
        }
        return (key.length + insertByteStream.size() +
                deleteByteStream.size() + (REC_OVERHEAD - 4));
    }

  /**
   * Write a record to specified output stream using the record pointed to by
   * the current position and the specified byte stream of ids.
   *
   * @param insertByteStream The byte stream containing the ids.
   * @param deleteByteStream The byte stream containing the ids.
   * @param dataStream The data output stream to write to.
   * @return The record size written.
   *
   * @throws IOException If an I/O error occurs writing the record.
   */
  public int writeRecord(ByteArrayOutputStream insertByteStream,
                         ByteArrayOutputStream deleteByteStream,
                         DataOutputStream dataStream) throws IOException
  {
    int recOffset = getIntValue(position * 4);
    int indexID = getIntValue(recOffset);
    int keyLen = getIntValue(recOffset + 4);
    dataStream.writeInt(indexID);
    dataStream.writeInt(keyLen);
    dataStream.write(buffer, recOffset + 8, keyLen);
    dataStream.writeInt(insertByteStream.size());
    if(insertByteStream.size() > 0)
    {
      insertByteStream.writeTo(dataStream);
    }
    dataStream.writeInt(deleteByteStream.size());
    if(deleteByteStream.size() > 0)
    {
      deleteByteStream.writeTo(dataStream);
    }
    return (getKeySize() + insertByteStream.size() +
            deleteByteStream.size() + (REC_OVERHEAD - 4));
  }

  /**
   * Return the key value part of a record specified by the buffer position.
   *
   * @return byte array containing the key value.
   */
  public byte[] getKeyBytes()
  {
    return getKeyBytes(position);
  }


  /**
   * Return the key value part of a record specified by the index.
   *
   * @param x index to return.
   * @return byte array containing the key value.
   */
  private byte[] getKeyBytes(int x)
  {
    int recOffset = getIntValue(x * 4);
    int keyLen = getIntValue(recOffset + 4);
    byte[] keyBytes = indexKey.getKeyBytes(keyLen);
    System.arraycopy(buffer, recOffset + 8, keyBytes, 0, keyLen);
    return keyBytes;
  }

  private boolean is(int x, int y, CompareOp op)
  {
    int xRecOffset = getIntValue(x * 4);
    int xIndexID = getIntValue(xRecOffset);
    int xKeyLen = getIntValue(xRecOffset + 4);
    int xKey =  xRecOffset + 8;
    int yRecOffset = getIntValue(y * 4);
    int yIndexID = getIntValue(yRecOffset);
    int yKeyLen = getIntValue(yRecOffset + 4);
    int yKey = yRecOffset + 8;
    return evaluateReturnCode(comparator.compare(buffer, xKey, xKeyLen,
                              xIndexID, yKey, yKeyLen, yIndexID), op);
  }


  private boolean is(int x, byte[] yBytes, CompareOp op, int yIndexID)
  {
    int xRecOffset = getIntValue(x * 4);
    int xIndexID = getIntValue(xRecOffset);
    int xKeyLen = getIntValue(xRecOffset + 4);
    int xKey = xRecOffset + 8;
    return evaluateReturnCode(comparator.compare(buffer, xKey, xKeyLen,
                              xIndexID, yBytes, yIndexID), op);
  }


  /**
   * Compare the byte array at the current position with the specified one and
   * using the specified index id.
   *
   * @param b The byte array to compare.
   * @param bIndexID The index key.
   * @return <CODE>True</CODE> if the byte arrays are equal.
   */
  public boolean compare(byte[] b, int bIndexID)
  {
    boolean returnCode = false;
    int xRecOffset = getIntValue(position * 4);
    int xIndexID = getIntValue(xRecOffset);
    int xKeyLen = getIntValue(xRecOffset + 4);
    if( comparator.compare(buffer, xRecOffset + 8, xKeyLen, b) == 0)
    {
      if(xIndexID == bIndexID)
      {
        returnCode = true;
      }
    }
    return returnCode;
  }

   /**
   * Compare the byte array at the current position with the byte array at the
   * specified index.
   *
   * @param i The index pointing to the byte array to compare.
   * @return <CODE>True</CODE> if the byte arrays are equal.
   */
  public boolean compare(int i)
  {
      return is(i, position, CompareOp.EQ);
  }

  /**
   * Compare current IndexBuffer to the one in the specified argument. The key
   * at the value of position in both buffers are used in the compare.
   *
   * @param b The IndexBuffer to compare to.
   * @return  0 if the buffers are equal, -1 if the current buffer is less
   *          than the specified buffer, or 1 if it is greater.
   */
  public int compareTo(IndexBuffer b) {
    byte[] key2 = b.getKeyBytes(b.getPosition());
    int xRecOffset = getIntValue(position * 4);
    int xIndexID = getIntValue(xRecOffset);
    int xLen = getIntValue(xRecOffset + 4);
    int returnCode = comparator.compare(buffer, xRecOffset + 8, xLen, key2);
    if(returnCode == 0)
    {
      int bIndexID = b.getIndexID();
      if(xIndexID == bIndexID)
      {
        long otherBufferID = b.getBufferID();
        //Used in Remove.
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
      else if(xIndexID < bIndexID)
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
   * Return the number of keys in an index buffer.
   *
   * @return The number of keys currently in an index buffer.
   */
  public int getNumberKeys()
  {
    return keys;
  }


  /**
   * Return if the buffer has more data. Used when iterating over the
   * buffer examining keys.
   *
   * @return <CODE>True</CODE> if the buffer has more data to process.
   */
  public boolean hasMoreData()
   {
     return (position + 1) < keys;
   }

  /**
   * Move to the next record in the buffer. Used when iterating over the
   * buffer examining keys.
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

  private int getIntValue(int index)
  {
    int answer = 0;
    for (int i = 0; i < 4; i++) {
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

    byte[] mKey = getKeyBytes(m);
    int mIndexID = getIndexID(m);

    int a = off, b = a, c = off + len - 1, d = c;
    while(true)
    {
      while (b <= c && is(b, mKey, CompareOp.LE, mIndexID))
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
    int aOffset = a * 4;
    int bOffset = b * 4;
    int bVal = getIntValue(bOffset);
    System.arraycopy(buffer, aOffset, buffer, bOffset, 4);
    System.arraycopy(getIntBytes(bVal), 0, buffer, aOffset, 4);
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
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         object.
     */
    int compare(T o, int offset, int length, int indexID, T other,
                int otherIndexID);

    /**
     * Compare an offset in an object with the specified object.
     *
     * @param o The first object.
     * @param offset The first offset.
     * @param length The first length.
     * @param other The second object.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         object.
     */
    int compare(T o, int offset, int length, T other);

  }

  /**
   * Implementation of ComparatorBuffer interface. Used to compare keys when
   * they are DNs.
   */
  public static
  class DNComparator implements IndexBuffer.ComparatorBuffer<byte[]>
  {
    /**
     * Compare two offsets in an byte array using the DN compare algorithm.
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
      for (int i = length - 1, j = otherLength - 1;
      i >= 0 && j >= 0; i--, j--) {
        if (b[offset + i] > b[otherOffset + j])
        {
          return 1;
        }
        else if (b[offset + i] < b[otherOffset + j])
        {
          return -1;
        }
      }
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
      if(length > otherLength)
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
     * @param indexID The first index id.
     * @param other The second byte array to compare to.
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int length, int indexID,
                       byte[]other, int otherIndexID)
    {
      int otherLength = other.length;
      for (int i = length - 1, j = otherLength - 1;
      i >= 0 && j >= 0; i--, j--) {
        if (b[offset + i] > other[j])
        {
          return 1;
        }
        else if (b[offset + i] < other[j])
        {
          return -1;
        }
      }
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
      if(length > otherLength)
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
       * @return a negative integer, zero, or a positive integer as the first
       *         offset value is less than, equal to, or greater than the
       *         second byte array.
       */
      public int compare(byte[] b, int offset, int length, byte[] other)
      {
          int otherLength = other.length;
          for (int i = length - 1, j = otherLength - 1;
               i >= 0 && j >= 0; i--, j--) {
              if (b[offset + i] > other[j])
              {
                  return 1;
              }
              else if (b[offset + i] < other[j])
              {
                  return -1;
              }
          }
          if(length == otherLength)
          {
              return 0;
          }
          if(length > otherLength)
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
   * Implementation of ComparatorBuffer interface. Used to compare keys when
   * they are regular indexes.
   */
  public static
  class IndexComparator implements IndexBuffer.ComparatorBuffer<byte[]>
  {
   /**
     * Compare two offsets in an byte array using the index compare
     * algorithm.
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
     * @param indexID The first index id.
     * @param other The second byte array to compare to.
     * @param otherIndexID The second index id.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int length, int indexID,
                       byte[] other, int otherIndexID)
    {
      int otherLength = other.length;
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
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int length, byte[] other)
    {
      int otherLength = other.length;
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
