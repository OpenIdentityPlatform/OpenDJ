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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.opends.messages.ReplicationMessages.*;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;

import com.forgerock.opendj.util.Pair;

/**
 * A log reader with binary search support.
 * <p>
 * The log file contains record offsets at fixed block size : given a block size N,
 * an offset is written at every N bytes. The offset contains the number of bytes to
 * reach the beginning of previous record (or next record if offset equals 0).
 * <p>
 * The reader provides both sequential access, using the {@code readRecord()} method,
 * and reasonably fast random access, using the {@code seek(K, boolean)} method.
 *
 * @param <K>
 *          Type of the key of a record, which must be comparable.
 * @param <V>
 *          Type of the value of a record.
 */
class BlockLogReader<K extends Comparable<K>, V> implements Closeable
{
  static final int SIZE_OF_BLOCK_OFFSET = 4;

  static final int SIZE_OF_RECORD_SIZE = 4;

  /**
   * Size of a block, after which an offset to the nearest record is written.
   * <p>
   * This value has been fixed based on performance tests. See
   * <a href="https://bugster.forgerock.org/jira/browse/OPENDJ-1472">
   * OPENDJ-1472</a> for details.
   */
  static final int BLOCK_SIZE = 256;

  private final int blockSize;

  private final RecordParser<K, V> parser;

  private final RandomAccessFile reader;

  private final File file;

  /**
   * Creates a reader for the provided file, file reader and parser.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param file
   *          The log file to read.
   * @param reader
   *          The random access reader on the log file.
   * @param parser
   *          The parser to decode the records read.
   * @return a new log reader
   */
  static <K extends Comparable<K>, V> BlockLogReader<K, V> newReader(
      final File file, final RandomAccessFile reader, final RecordParser<K, V> parser)
  {
    return new BlockLogReader<K, V>(file, reader, parser, BLOCK_SIZE);
  }

  /**
   * Creates a reader for the provided file, file reader, parser and block size.
   * <p>
   * This method is intended for tests only, to allow tuning of the block size.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param file
   *          The log file to read.
   * @param reader
   *          The random access reader on the log file.
   * @param parser
   *          The parser to decode the records read.
   * @param blockSize
   *          The size of each block, or frequency at which the record offset is
   *          present in the log file.
   * @return a new log reader
   */
  static <K extends Comparable<K>, V> BlockLogReader<K, V> newReaderForTests(
      final File file, final RandomAccessFile reader, final RecordParser<K, V> parser, int blockSize)
  {
    return new BlockLogReader<K, V>(file, reader, parser, blockSize);
  }

  private BlockLogReader(
      final File file, final RandomAccessFile reader, final RecordParser<K, V> parser, final int blockSize)
  {
    this.file = file;
    this.reader = reader;
    this.parser = parser;
    this.blockSize = blockSize;
  }

  /**
   * Position the reader to the record corresponding to the provided key or to
   * the nearest key (the lowest key higher than the provided key), and returns
   * the last record read.
   *
   * @param key
   *          Key to use as a start position. Key must not be {@code null}.
   * @param findNextRecord
   *          If {@code true}, start position is the lowest key that is higher
   *          than the provided key, otherwise start position is the provided
   *          key.
   * @return The pair (key_found, last_record_read). key_found is a boolean
   *         indicating if reader is successfully positioned to the key or the
   *         nearest key. last_record_read is the last record that was read.
   *         When key_found is equals to {@code false}, then last_record_read is
   *         always {@code null}. When key_found is equals to {@code true},
   *         last_record_read can be valued or be {@code null}
   * @throws ChangelogException
   *           If an error occurs when seeking the key.
   */
  public Pair<Boolean, Record<K,V>> seekToRecord(final K key, final boolean findNextRecord) throws ChangelogException
  {
    final long markerPosition = searchClosestBlockStartToKey(key);
    if (markerPosition >= 0)
    {
      return positionToKeySequentially(markerPosition, key, findNextRecord);
    }
    return Pair.of(false, null);
  }

  /**
   * Position the reader to the provided file position.
   *
   * @param filePosition
   *            offset from the beginning of the file, in bytes.
   * @throws ChangelogException
   *            If an error occurs.
   */
  public void seekToPosition(final long filePosition) throws ChangelogException
  {
    try
    {
      reader.seek(filePosition);
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_SEEK.get(filePosition, file.getPath()), e);
    }
  }

  /**
   * Read a record from current position.
   *
   * @return the record read
   * @throws ChangelogException
   *            If an error occurs during read.
   */
  public Record<K,V> readRecord() throws ChangelogException
  {
    return readRecord(-1);
  }

  /**
   * Returns the file position for this reader.
   *
   * @return the position of reader on the log file
   * @throws ChangelogException
   *          If an error occurs.
   */
  public long getFilePosition() throws ChangelogException
  {
    try
    {
      return reader.getFilePointer();
    }
    catch (IOException e)
    {
      throw new ChangelogException(
          ERR_CHANGELOG_UNABLE_TO_GET_CURSOR_READER_POSITION_LOG_FILE.get(file.getPath()), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws IOException
  {
    reader.close();
  }

  /**
   * Read a record, either from the provided start of block position or from
   * the current position.
   *
   * @param blockStartPosition
   *          The position of start of block, where a record offset is written.
   *          If provided value is -1, then record is read from current position instead.
   * @return the record read
   * @throws ChangelogException
   *           If an error occurs during read.
   */
  private Record<K,V> readRecord(final long blockStartPosition) throws ChangelogException
  {
    try
    {
      final ByteString recordData = blockStartPosition == -1 ?
          readNextRecord() : readRecordFromBlockStartPosition(blockStartPosition);
      return recordData != null ? parser.decodeRecord(recordData) : null;
    }
    catch (IOException io)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DECODE_RECORD.get(reader.toString()), io);
    }
    catch (DecodingException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DECODE_RECORD.get(reader.toString()), e);
    }
  }

  /**
   * Reads the first record found after the provided file position of block
   * start.
   *
   * @param blockStartPosition
   *          Position of read pointer in the file, expected to be the start of
   *          a block where a record offset is written.
   * @return the record read
   * @throws IOException
   *           If an error occurs during read.
   */
  private ByteString readRecordFromBlockStartPosition(final long blockStartPosition) throws IOException
  {
    reader.seek(blockStartPosition);
    if (blockStartPosition > 0)
    {
      final byte[] offsetData = new byte[SIZE_OF_BLOCK_OFFSET];
      reader.readFully(offsetData);
      final int offsetToRecord = ByteString.wrap(offsetData).toInt();
      if (offsetToRecord > 0)
      {
        reader.seek(blockStartPosition - offsetToRecord);
      } // if offset is zero, reader is already well positioned
    }
    return readNextRecord();
  }

  /**
   * Reads the next record.
   *
   * @return the bytes of the next record, or {@code null} if no record is available
   * @throws IOException
   *            If an error occurs while reading.
   */
  private ByteString readNextRecord() throws IOException
  {
    try
    {
      // read length of record
      final long filePosition = reader.getFilePointer();
      int distanceToBlockStart = getDistanceToNextBlockStart(filePosition, blockSize);
      final int recordLength = readRecordLength(distanceToBlockStart);

      // read the record
      long currentPosition = reader.getFilePointer();
      distanceToBlockStart = getDistanceToNextBlockStart(currentPosition, blockSize);
      final ByteStringBuilder recordBytes =
          new ByteStringBuilder(getLengthOfStoredRecord(recordLength, distanceToBlockStart));
      int remainingBytesToRead = recordLength;
      while (distanceToBlockStart < remainingBytesToRead)
      {
        if (distanceToBlockStart != 0)
        {
          recordBytes.append(reader, distanceToBlockStart);
        }
        // skip the offset
        reader.skipBytes(SIZE_OF_BLOCK_OFFSET);

        // next step
        currentPosition += distanceToBlockStart + SIZE_OF_BLOCK_OFFSET;
        remainingBytesToRead -= distanceToBlockStart;
        distanceToBlockStart = blockSize - SIZE_OF_BLOCK_OFFSET;
      }
      if (remainingBytesToRead > 0)
      {
        // last bytes of the record
        recordBytes.append(reader, remainingBytesToRead);
      }
      return recordBytes.toByteString();
    }
    catch(EOFException e)
    {
      // end of stream, no record or uncomplete record
      return null;
    }
  }

  /**
   * Returns the total length in bytes taken by a record when stored in log file,
   * including size taken by block offsets.
   *
   * @param recordLength
   *            The length of record to write.
   * @param distanceToBlockStart
   *            Distance before the next block start.
   * @return the length in bytes necessary to store the record in the log
   */
  int getLengthOfStoredRecord(int recordLength, int distanceToBlockStart)
  {
    int totalLength = recordLength;
    if (recordLength > distanceToBlockStart)
    {
      totalLength += SIZE_OF_BLOCK_OFFSET;
      final int remainingBlocks = (recordLength - distanceToBlockStart -1) / (blockSize - SIZE_OF_BLOCK_OFFSET);
      totalLength += remainingBlocks * SIZE_OF_BLOCK_OFFSET;
    }
    return totalLength;
  }

  /** Read the length of a record. */
  private int readRecordLength(final int distanceToBlockStart) throws IOException
  {
    final ByteStringBuilder lengthBytes = new ByteStringBuilder();
    if (distanceToBlockStart > 0 && distanceToBlockStart < SIZE_OF_RECORD_SIZE)
    {
      lengthBytes.append(reader, distanceToBlockStart);
      // skip the offset
      reader.skipBytes(SIZE_OF_BLOCK_OFFSET);
      lengthBytes.append(reader, SIZE_OF_RECORD_SIZE - distanceToBlockStart);
      return lengthBytes.toByteString().toInt();
    }
    else
    {
      if (distanceToBlockStart == 0)
      {
        // skip the offset
        reader.skipBytes(SIZE_OF_BLOCK_OFFSET);
      }
      lengthBytes.append(reader, SIZE_OF_RECORD_SIZE);
      return lengthBytes.toByteString().toInt();
    }
  }

  /**
   * Search the closest block start to the provided key, using binary search.
   * <p>
   * Note that position of reader is modified by this method.
   *
   * @param key
   *          The key to search
   * @return the file position of block start that must be used to find the given key,
   *      or a negative number if no position could be found.
   * @throws ChangelogException
   *          if a problem occurs
   */
  long searchClosestBlockStartToKey(K key) throws ChangelogException
  {
    final long maxPos = getLastPositionInFile();
    long lowPos = 0L;
    long highPos = getClosestBlockStartStrictlyAfterPosition(maxPos);

    while (lowPos <= highPos)
    {
      final long middlePos = Math.min((lowPos + highPos) / 2, maxPos);
      final long middleBlockStartPos = getClosestBlockStartBeforeOrAtPosition(middlePos);
      final Record<K, V> middleRecord = readRecord(middleBlockStartPos);
      if (middleRecord == null)
      {
        return -1;
      }

      final int keyComparison = middleRecord.getKey().compareTo(key);
      if (keyComparison < 0)
      {
        if (middleBlockStartPos <= lowPos)
        {
          return lowPos;
        }
        lowPos = middleBlockStartPos;
      }
      else if (keyComparison > 0)
      {
        if (middleBlockStartPos >= highPos)
        {
          return highPos;
        }
        highPos = middleBlockStartPos;
      }
      else
      {
        return middleBlockStartPos;
      }
    }
    // Unable to find a position where key can be found
    return -1;
  }

  private long getLastPositionInFile() throws ChangelogException
  {
    try
    {
      return reader.length() - 1;
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_RETRIEVE_FILE_LENGTH.get(file.getPath()), e);
    }
  }

  /**
   * Position to provided key, starting from provided block start position and
   * reading sequentially until key is found.
   *
   * @param blockStartPosition
   *          Position of read pointer in the file, expected to be the start of
   *          a block where a record offset is written.
   * @param key
   *          The key to find.
   * @param findNextRecord
   *          If {@code true}, position at the end of this method is the lowest
   *          key that is higher than the provided key, otherwise position is
   *          the provided key.
   * @return The pair ({@code true}, last record read) if reader is successfully
   *         positioned to the key or the nearest key (last record may be null
   *         if end of file is reached), ({@code false}, null) otherwise.
   * @throws ChangelogException
   *            If an error occurs.
   */
   Pair<Boolean, Record<K,V>> positionToKeySequentially(
       final long blockStartPosition,
       final K key,
       final boolean findNextRecord)
       throws ChangelogException {
    Record<K,V> record = readRecord(blockStartPosition);
    do {
      if (record != null)
      {
        final int keysComparison = record.getKey().compareTo(key);
        final boolean matches = findNextRecord ? keysComparison >= 0 : record.getKey().equals(key);
        if (matches)
        {
          if (findNextRecord && keysComparison == 0)
          {
            // skip key in order to position on lowest higher key
            record = readRecord();
          }
          return Pair.of(true, record);
        }
      }
      record = readRecord();
    }
    while (record != null);
    return Pair.of(false, null);
  }

  /**
   * Returns the closest start of block which has a position lower than or equal
   * to the provided file position.
   *
   * @param filePosition
   *          The position of reader on file.
   * @return the file position of the block start.
   */
  long getClosestBlockStartBeforeOrAtPosition(final long filePosition)
  {
    final int dist = getDistanceToNextBlockStart(filePosition, blockSize);
    return dist == 0 ? filePosition : filePosition + dist - blockSize;
  }

  /**
   * Returns the closest start of block which has a position strictly
   * higher than the provided file position.
   *
   * @param filePosition
   *           The position of reader on file.
   * @return the file position of the block start.
   */
  long getClosestBlockStartStrictlyAfterPosition(final long filePosition)
  {
    final int dist = getDistanceToNextBlockStart(filePosition, blockSize);
    return dist == 0 ? filePosition + blockSize : filePosition + dist;
  }

  /**
   * Returns the distance to next block for the provided file position.
   *
   * @param filePosition
   *            offset from the beginning of the file, in bytes.
   * @param blockSize
   *            Size of each block in bytes.
   * @return the distance to next block in bytes
   */
  static int getDistanceToNextBlockStart(final long filePosition, final int blockSize)
  {
    if (filePosition == 0)
    {
      return blockSize;
    }
    final int distance = (int) (filePosition % blockSize);
    return distance == 0 ? 0 : blockSize - distance;
  }

}
