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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.server.changelog.file.BlockLogReader.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.SyncFailedException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Reject;
import org.opends.server.replication.server.changelog.api.ChangelogException;

/**
 * A log writer, using fixed-size blocks to allow fast retrieval when reading.
 * <p>
 * The log file contains record offsets at fixed block size : given block size N,
 * an offset is written at every N bytes. The offset contains the number of bytes to
 * reach the beginning of previous record (or next record if offset equals 0).
 *
 * @param <K>
 *          Type of the key of a record, which must be comparable.
 * @param <V>
 *          Type of the value of a record.
 */
class BlockLogWriter<K extends Comparable<K>, V> implements Closeable
{
  private final int blockSize;

  private final RecordParser<K, V> parser;

  private final LogWriter writer;

  /**
   * Creates a writer for the provided log writer and parser.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param writer
   *          The writer on the log file.
   * @param parser
   *          The parser to encode the records.
   * @return a new log reader
   */
  static <K extends Comparable<K>, V> BlockLogWriter<K,V> newWriter(
      final LogWriter writer, final RecordParser<K, V> parser)
  {
    return new BlockLogWriter<>(writer, parser, BLOCK_SIZE);
  }

  /**
   * Creates a writer for the provided log writer, parser and size for blocks.
   * <p>
   * This method is intended for tests only, to allow tuning of the block size.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param writer
   *          The writer on the log file.
   * @param parser
   *          The parser to encode the records.
   * @param blockSize
   *          The size of each block, or frequency at which the record offset is
   *          present in the log file.
   * @return a new log reader
   */
  static <K extends Comparable<K>, V> BlockLogWriter<K,V> newWriterForTests(
      final LogWriter writer, final RecordParser<K, V> parser, final int blockSize)
  {
    return new BlockLogWriter<>(writer, parser, blockSize);
  }

  /**
   * Creates the writer with an underlying writer, a parser and a size for blocks.
   *
   * @param writer
   *            The writer to the log file.
   * @param parser
   *            The parser to encode the records.
   * @param blockSize
   *            The size of each block.
   */
  private BlockLogWriter(LogWriter writer, RecordParser<K, V> parser, int blockSize)
  {
    Reject.ifNull(writer, parser);
    this.writer = writer;
    this.parser = parser;
    this.blockSize = blockSize;
  }

  /**
   * Writes the provided record to the log file.
   *
   * @param record
   *            The record to write.
   * @throws ChangelogException
   *            If a problem occurs during write.
   */
  public void write(final Record<K, V> record) throws ChangelogException
  {
    try
    {
      write(parser.encodeRecord(record));
      writer.flush();
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_ADD_RECORD.get(record.toString(),
          writer.getFile().getPath()), e);
    }
  }

  /**
   * Returns the number of bytes written in the log file.
   *
   * @return the number of bytes
   */
  public long getBytesWritten()
  {
    return writer.getBytesWritten();
  }

  /**
   * Synchronize all modifications to the log file to the underlying device.
   *
   * @throws SyncFailedException
   *           If synchronization fails.
   */
  public void sync() throws SyncFailedException
  {
    writer.sync();
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    writer.close();
  }

  /**
   * Writes the provided byte string to the log file.
   *
   * @param record
   *            The value to write.
   * @throws IOException
   *            If an error occurs while writing
   */
  private void write(final ByteString record) throws IOException
  {
    // Add length of record before writing
    ByteString data = new ByteStringBuilder(SIZE_OF_RECORD_SIZE + record.length()).
        append(record.length()).
        append(record).
        toByteString();

    int distanceToBlockStart = BlockLogReader.getDistanceToNextBlockStart(writer.getBytesWritten(), blockSize);
    int cumulatedDistanceToBeginning = distanceToBlockStart;
    int dataPosition = 0;
    int dataRemaining = data.length();
    final int dataSizeForOneBlock = blockSize - SIZE_OF_BLOCK_OFFSET;

    while (distanceToBlockStart < dataRemaining)
    {
      if (distanceToBlockStart > 0)
      {
        // append part of record
        final int dataEndPosition = dataPosition + distanceToBlockStart;
        writer.write(data.subSequence(dataPosition, dataEndPosition));
        dataPosition = dataEndPosition;
        dataRemaining -= distanceToBlockStart;
      }
      // append the offset to the record
      writer.write(ByteString.valueOf(cumulatedDistanceToBeginning));

      // next step
      distanceToBlockStart = dataSizeForOneBlock;
      cumulatedDistanceToBeginning += blockSize;
    }
    // append the remaining bytes to finish the record
    writer.write(data.subSequence(dataPosition, data.length()));
  }

}
