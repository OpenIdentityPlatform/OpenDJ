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

  //The size of a buffer.
  private final int size;

  //Byte array holding the actual buffer data.
  private final byte buffer[];

  //id is used to break a tie (keys equal) when the buffers are being merged
  //when writing.
  private long id;

  //Temporaty buffers.
  private final byte[] intBytes = new byte[4];
  private final byte[] idBytes = new byte[8];

  //keyPtr - offSet where next key is written
  //recPtr - offSet where next value record is written
  //bytesLeft - amount of bytes left in the buffer
  private int keyPtr=0, recPtr=0, bytesLeft = 0;

  //keys - number of keys in the buffer
  //pos - used to iterate over the buffer when writing to a file.
  private int keys = 0, pos = 0;

  //Various things needed to process a buffer.
  private ComparatorBuffer<byte[]> comparator;
  private DatabaseContainer container;
  private EntryContainer entryContainer;


  private IndexBuffer(int size) {
    this.size = size;
    this.buffer = new byte[size];
    this.bytesLeft = size;
    this.recPtr = size - 1;
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
    keyPtr = 0;
    recPtr = size - 1;
    keys = 0;
    pos = 0;
    container = null;
    entryContainer = null;
    comparator = null;
  }

  /**
   * Compare current IndexBuffer to the one in the specified argument. The key
   * at the value of pos in both buffers are used in the comparision.
   *
   * @param b The IndexBuffer to compare to.
   * @return  0 if the buffers are equal, -1 if the current buffer is less
   *          than the specified buffer, or 1 if it is greater.
   */
  public int compareTo(IndexBuffer b) {
    byte[] key2 = b.getKeyBytes(b.getPos());
    int xKeyOffset = pos * 4;
    int xOffset = getValue(xKeyOffset);
    int xLen = getValue(xOffset);
    xOffset += 4;
    int rc = comparator.compare(buffer, xOffset, xLen, key2);
    if(rc == 0)
    {
      if(this.id == b.getBufID())
      {
        rc = 0;
      }
      else if(this.id < b.getBufID()) {
        rc = -1;
      }
      else
      {
        rc = 1;
      }
    }
    return rc;
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
   * Determines if a buffer is a posion buffer. A posion buffer is used to
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
  public long getBufID()
  {
    return this.id;
  }

  /**
   * Set the DB container to be used in the buffer processing to the specified
   * value.
   *
   * @param container The DB container to set a buffer's container to.
   */
  public void setContainer(DatabaseContainer container) {
    this.container = container;
  }

  /**
   * Return the DB container value of a buffer.
   *
   * @return The DB container value of a buffer.
   */
  public DatabaseContainer getContainer() {
    return this.container;
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
    int recLen =  4 + keyBytes.length + 8;
    return (recLen + 4) < bytesLeft;
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
   * Set an buffer's entry container to the specified paramter.
   *
   * @param entryContainer The entry container to set the buffer' container to.
   */
  public void setEntryContainer(EntryContainer entryContainer)
  {
    this.entryContainer = entryContainer;
  }

  /**
   * Return a buffer's entry container value.
   *
   * @return The buffer's entry container value.
   */
  public EntryContainer getEntryContainer()
  {
    return entryContainer;
  }

  /**
   * Return a buffer's current pos value.
   *
   * @return The buffer's current pos value.
   */
  public int getPos()
  {
    return pos;
  }

  /**
   * Set a buffer's pos value to the specified value.
   *
   * @param mark The value to set the pos to.
   */
  public void setPos(int mark)
  {
    this.pos = mark;
  }

  /**
   * Sort the buffer.
   */
  public void sort() {
    sort(0, keys);
  }

  /**
   * Add the specifed key byte array and EntryID to the buffer.
   *
   * @param keyBytes The key byte array.
   * @param IDEntry The EntryID.
   */
  public void add(byte[] keyBytes, EntryID IDEntry) {
    byte[] idBytes = JebFormat.entryIDToDatabase(IDEntry.longValue());
    int recLen =  4 + keyBytes.length + 8;
    recPtr -= recLen;
    System.arraycopy(getBytes(recPtr), 0, buffer, keyPtr, 4);
    keyPtr += 4;
    System.arraycopy(getBytes(keyBytes.length), 0, buffer, recPtr, 4);
    System.arraycopy(keyBytes, 0, buffer, (recPtr+4), keyBytes.length);
    System.arraycopy(idBytes, 0, buffer, (recPtr + 4 + keyBytes.length), 8);
    bytesLeft = recPtr - keyPtr;
    keys++;
  }


  /**
   * Return the byte array representing the entry ID
   * at the specified index value.
   *
   * @param index The index value to retrieve.
   * @return The byte array at the index value.
   */
  public byte[] getID(int index)
  {
    int offset = index * 4;
    int recOffset = getValue(offset);
    int dnLen = getValue(recOffset);
    System.arraycopy(buffer, recOffset + 4 + dnLen, idBytes, 0, 8);
    return idBytes;
  }

  /**
   * Compare the byte array at the current pos with the specified one.
   *
   * @param b The byte array to compare.
   * @return <CODE>True</CODE> if the byte arrays are equal.
   */
  public boolean compare(byte[] b)
  {
    return is(pos, b, CompareOp.EQ);
  }

   /**
   * Compare the byte array at the current pos with the byte array at the
   * specified index.
   *
   * @param i The index pointing to the byte array to compare.
   * @return <CODE>True</CODE> if the byte arrays are equal.
   */
  public boolean compare(int i)
  {
      return is(i, pos, CompareOp.EQ);
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
   * Write a key to an output stream.
   *
   * @param out The stream to write the key to.
   *
   * @throws IOException If there was an error writing the key.
   */
  public void writeKey(DataOutputStream out) throws IOException
  {
    int offSet = pos * 4;
    int recOffset = getValue(offSet);
    int len = getValue(recOffset);
    out.writeInt(len);
    out.write(buffer, recOffset + 4, len);
  }

  /**
   * Return the size of the key part of the record.
   *
   * @return The size of the key part of the record.
   */
  public int getKeySize()
  {
    int offSet = pos * 4;
    int recOffset = getValue(offSet);
    return getValue(recOffset);
  }

  /**
   * Return the key value part of a record specifed by the index.
   *
   * @param index The index to return the key value of.
   * @return byte array containing the key value.
   */
  public byte[] getKeyBytes(int index)
  {
    int offSet = index * 4;
    int recOffset = getValue(offSet);
    int dnLen = getValue(recOffset);
    byte[] b = new byte[dnLen];
    System.arraycopy(buffer, recOffset + 4, b, 0, dnLen);
    return b;
  }

  /**
   * Return if the buffer has more data. Used when iterating over the
   * buffer examining keys.
   *
   * @return <CODE>True</CODE> if the buffer has more data to process.
   */
  public boolean hasMoreData()
   {
     return (pos + 1) < keys ? true : false;
   }

  /**
   * Move to the next record in the buffer. Used when iterating over the
   * buffer examining keys.
   */
   public void getNextRecord()
   {
     pos++;
   }

  private byte[] getBytes(int val)
  {
    for (int i = 3; i >= 0; i--) {
      intBytes[i] = (byte) (val & 0xff);
      val >>>= 8;
    }
    return intBytes;
  }

  private int getValue(int pos)
  {
    int answer = 0;
    for (int i = 0; i < 4; i++) {
      byte b = buffer[pos + i];
      answer <<= 8;
      answer |= (b & 0xff);
    }
    return answer;
  }


  private boolean is(int x, int y, CompareOp op)
  {
    int xKeyOffset = x * 4;
    int xOffset = getValue(xKeyOffset);
    int xLen = getValue(xOffset);
    xOffset += 4;
    int yKeyOffset = y * 4;
    int yOffset = getValue(yKeyOffset);
    int yLen = getValue(yOffset);
    yOffset += 4;
    return eval(comparator.compare(buffer, xOffset, xLen, yOffset, yLen), op);
  }


  private boolean is(int x, byte[] m, CompareOp op)
  {
    int xKeyOffset = x * 4;
    int xOffset = getValue(xKeyOffset);
    int xLen = getValue(xOffset);
    xOffset += 4;
    return eval(comparator.compare(buffer, xOffset, xLen, m), op);
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

    byte[] mKey = this.getKeyBytes(m);
    int a = off, b = a, c = off + len - 1, d = c;
    while(true)
    {
      while (b <= c && is(b, mKey, CompareOp.LE))
      {
        if (is(b, mKey, CompareOp.EQ))
          swap(a++, b);
        b++;
      }
      while (c >= b && is(c, mKey, CompareOp.GE))
      {
        if (is(c, mKey, CompareOp.EQ))
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
    vecswap(off, b-s, s);
    s = Math.min(d-c,   n-d-1);
    vecswap(b, n-s, s);

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
    int bVal = getValue(bOffset);
    System.arraycopy(buffer, aOffset, buffer, bOffset, 4);
    System.arraycopy(getBytes(bVal), 0, buffer, aOffset, 4);
  }

  private void vecswap(int a, int b, int n)
  {
    for (int i=0; i<n; i++, a++, b++)
      swap(a, b);
  }

  private boolean eval(int rc, CompareOp op)
  {
    boolean retVal = false;
    switch(op) {
    case LT:
      retVal = rc < 0;
      break;
    case GT:
      retVal = rc > 0;
      break;
    case LE:
      retVal = rc <= 0;
      break;
    case GE:
      retVal = rc >= 0;
      break;
    case EQ:
      retVal = rc == 0;
      break;
    }
    return retVal;
  }

  /**
   * Inteface that defines two methods used to compare keys used in this
   * class. The Comparator interface cannot be used in this class, so this
   * special one is used that knows about the special properties of this class.
   *
   * @param <T> object to use in the comparisions
   */
  public static interface ComparatorBuffer<T> {
     /**
     * Compare two offsets in an object, usually a byte array.
     *
     * @param o The object.
     * @param offset The first offset.
     * @param len The first length.
     * @param offset1 The second offset.
     * @param len1 The second length.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second.
     */
    int compare(T o, int offset, int len, int offset1, int len1);
       /**
     * Compare an offset in an object with the specified object.
     *
     * @param o The first object.
     * @param offset The first offset.
     * @param len The first length.
     * @param o2 The second object.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         object.
     */
    int compare(T o, int offset, int len, T o2);
  }

  /**
   * Implementation of ComparatorBuffer interface. Used to compare keys when
   * they are DNs.
   */
  public static
  class DNComparator implements IndexBuffer.ComparatorBuffer<byte[]>
  {
    /**
     * Compare two offsets in an byte array using the DN comparision algorithm.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param len The first length.
     * @param offset1 The second offset.
     * @param len1 The second length.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second.
     */
    public int compare(byte[] b, int offset, int len, int offset1, int len1)
    {
      for (int ai = len - 1, bi = len1 - 1;
      ai >= 0 && bi >= 0; ai--, bi--) {
        if (b[offset + ai] > b[offset1 + bi])
        {
          return 1;
        }
        else if (b[offset + ai] < b[offset1 + bi])
        {
          return -1;
        }
      }
      if(len == len1)
      {
        return 0;
      }
      if(len > len1)
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
     * using the DN comparision algorithm.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param len The first length.
     * @param m The second byte array to compare to.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int len, byte[]m)
    {
      int len1 = m.length;
      for (int ai = len - 1, bi = len1 - 1;
      ai >= 0 && bi >= 0; ai--, bi--) {
        if (b[offset + ai] > m[bi])
        {
          return 1;
        }
        else if (b[offset + ai] < m[bi])
        {
          return -1;
        }
      }
      if(len == len1)
      {
        return 0;
      }
      if(len > len1)
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
     * Compare two offsets in an byte array using the index comparision
     * algorithm.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param len The first length.
     * @param offset1 The second offset.
     * @param len1 The second length.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second.
     */
    public int compare(byte[] b, int offset, int len, int offset1, int len1)
    {
      for(int i = 0; i < len && i < len1; i++)
      {
        if(b[offset + i] > b[offset1 + i])
        {
          return 1;
        }
        else if (b[offset + i] < b[offset1 + i])
        {
          return -1;
        }
      }
      if(len == len1)
      {
        return 0;
      }
      if (len > len1)
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
     * using the DN comparision algorithm.
     *
     * @param b The byte array.
     * @param offset The first offset.
     * @param len The first length.
     * @param m The second byte array to compare to.
     * @return a negative integer, zero, or a positive integer as the first
     *         offset value is less than, equal to, or greater than the second
     *         byte array.
     */
    public int compare(byte[] b, int offset, int len, byte[] m)
    {
      int len1 = m.length;
      for(int i = 0; i < len && i < len1; i++)
      {
        if(b[offset + i] > m[i])
        {
          return 1;
        }
        else if (b[offset + i] < m[i])
        {
          return -1;
        }
      }
      if(len == len1)
      {
        return 0;
      }
      if (len > len1)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
  }
}
