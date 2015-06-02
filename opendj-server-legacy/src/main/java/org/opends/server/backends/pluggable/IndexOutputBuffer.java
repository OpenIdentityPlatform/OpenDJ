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
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.ImportRecord.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Comparator;

import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.backends.pluggable.OnDiskMergeBufferImporter.IndexKey;

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
 * The records are packed as much as possible, to optimize the buffer space.
 * </p>
 * <p>
 * This class is not thread safe.
 * </p>
 */
final class IndexOutputBuffer implements Comparable<IndexOutputBuffer> {

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
  private IndexKey indexKey;

  /**
   * Set to {@code true} if the buffer should not be recycled. Used when the
   * importer/rebuild index process is doing phase one cleanup and flushing
   * buffers not completed.
   */
  private boolean discarded;
  private ImportRecord currentRecord;


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
    currentRecord = null;
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
   * @return {@code true} if buffer should be recycled, or {@code false} if it should not.
   */
  boolean isDiscarded()
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
  public boolean isSpaceAvailable(ByteSequence kBytes, long entryID) {
    return getRequiredSize(kBytes.length(), entryID) < bytesLeft;
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
    this.currentRecord = toRecord(position);
  }

  /**
   * Sort the buffer.
   */
  public void sort()
  {
    Integer[] keyArray = new Integer[keys];

    for (int i = 0; i < keys; i++)
    {
      keyArray[i] = readInt(i * INT_SIZE);
    }
    Arrays.sort(keyArray, new Comparator<Integer>()
      {
        public int compare(Integer i1, Integer i2)
        {
          return offsetToRecord(i1).compareTo(offsetToRecord(i2));
        }
      });
    for (int i = 0; i < keys; i++)
    {
      writeInt(buffer, i * INT_SIZE, keyArray[i]);
    }
  }

  /**
   * Add the specified key byte array and EntryID to the buffer.
   *
   * @param keyBytes The key byte array.
   * @param entryID The EntryID.
   * @param indexID The index ID the record belongs.
   * @param insert <CODE>True</CODE> if key is an insert, false otherwise.
   */
  public void add(ByteSequence keyBytes, EntryID entryID, int indexID, boolean insert) {
    // write the record data, but leave the space to write the record size just
    // before it
    recordOffset = addRecord(keyBytes, entryID.longValue(), indexID, insert);
    // then write the returned record size
    keyOffset = writeInt(buffer, keyOffset, recordOffset);
    bytesLeft = recordOffset - keyOffset;
    keys++;
  }


  /**
   * Writes the full record minus the record size itself.
   */
  private int addRecord(ByteSequence key, long entryID, int indexID, boolean insert)
  {
     int retOffset = recordOffset - getRecordSize(key.length());
     int offSet = retOffset;

     // write the INS/DEL bit
     buffer[offSet++] = insert ? INS : DEL;
     // write the indexID
     offSet = writeInt(buffer, offSet, indexID);
     // write the entryID
     offSet = writeLong(buffer, offSet, entryID);
     // write the key length
     offSet = writeInt(buffer, offSet, key.length());
     // write the key bytes
     key.copyTo(buffer, offSet);
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
    // also add up the space needed to store the record size
    return getRecordSize(keyLen) + INT_SIZE;
  }

  private static int getRecordSize(int keyLen)
  {
    // Adds up (INS/DEL bit + indexID) + entryID + key length + key bytes
    return REC_OVERHEAD + LONG_SIZE + INT_SIZE + keyLen;
  }

  /**
   * Write the entryID at the specified position to the specified output stream.
   * Used when writing the index scratch files.
   *
   * @param stream The stream to write the record at the index to.
   * @param position The position of the record to write.
   */
  public void writeEntryID(ByteArrayOutputStream stream, int position)
  {
    int offSet = getOffset(position);
    stream.write(buffer, offSet + REC_OVERHEAD, LONG_SIZE);
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

  private int getOffset(int position)
  {
    return readInt(position * INT_SIZE);
  }

  private ImportRecord offsetToRecord(int offset)
  {
    return ImportRecord.fromBufferAndOffset(buffer, offset);
  }

  private ImportRecord toRecord(int position)
  {
    return ImportRecord.fromBufferAndPosition(buffer, position);
  }

  ImportRecord currentRecord()
  {
    return currentRecord;
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
    int cmp = currentRecord().compareTo(b.currentRecord());
    if (cmp == 0)
    {
      // This is tested in a tree set remove when a buffer is removed from the tree set.
      return compareLongs(bufferID, b.getBufferID());
    }
    return cmp;
  }

  private static int compareLongs(long l1, long l2)
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
   * Compare the byte array at the current position with the byte array at the
   * provided position.
   *
   * @param position The index pointing to the byte array to compare.
   * @return {@code true} if the byte arrays are equal, or {@code false} otherwise
   */
  public boolean sameKeyAndIndexID(int position)
  {
    return currentRecord().equals(toRecord(position));
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
    setPosition(position + 1);
  }

  private int writeInt(byte[] buffer, int offset, int val)
  {
    final int endOffset = offset + INT_SIZE;
    for (int i = endOffset - 1; i >= offset; i--) {
      buffer[i] = (byte) (val & 0xff);
      val >>>= 8;
    }
    return endOffset;
  }

  private int writeLong(byte[] buffer, int offset, long val)
  {
    final int endOffset = offset + LONG_SIZE;
    for (int i = endOffset - 1; i >= offset; i--) {
      buffer[i] = (byte) (val & 0xff);
      val >>>= 8;
    }
    return endOffset;
  }

  private int readInt(int index)
  {
    int answer = 0;
    for (int i = 0; i < INT_SIZE; i++) {
      byte b = buffer[index + i];
      answer <<= 8;
      answer |= (b & 0xff);
    }
    return answer;
  }

  /**
   * Set the index key associated with an index buffer.
   *
   * @param indexKey The index key.
   */
  public void setIndexKey(IndexKey indexKey)
  {
    this.indexKey = indexKey;
  }

  /**
   * Return the index key of an index buffer.
   * @return The index buffer's index key.
   */
  public IndexKey getIndexKey()
  {
    return indexKey;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "("
        + "bufferID=" + bufferID + ", " + currentRecord + ", indexKey=" + indexKey
        + ")";
  }
}
