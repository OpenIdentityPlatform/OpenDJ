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

import java.io.File;
import java.io.RandomAccessFile;

import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ReplicationMessages.*;

/**
 * A Pool of readers to a log file.

 *
 * @param <K>
 *          Type of the key of a record, which must be comparable.
 * @param <V>
 *          Type of the value of a record.
 */
// TODO : implement a real pool - reusing readers instead of opening-closing them each time
class LogReaderPool<K extends Comparable<K>, V>
{
  /** The file to read. */
  private final File file;

  private final RecordParser<K, V> parser;

  /**
   * Creates a pool of readers for provided file.
   *
   * @param file
   *          The file to read.
   * @param parser
   *          The parser to decode the records read.
   */
  LogReaderPool(File file, RecordParser<K, V> parser)
  {
    this.file = file;
    this.parser = parser;
  }

  /**
   * Returns a random access reader on the provided file.
   * <p>
   * The acquired reader must be released with the {@code release()}
   * method.
   *
   * @return a random access reader
   * @throws ChangelogException
   *            If the file can't be found or read.
   */
  BlockLogReader<K, V> get() throws ChangelogException
  {
    return getReader(file);
  }

  /**
   * Release the provided reader.
   * <p>
   * Once released, this reader must not be used any more.
   *
   * @param reader
   *          The random access reader to a file previously acquired with this
   *          pool.
   */
  void release(BlockLogReader<K, V> reader)
  {
    StaticUtils.close(reader);
  }

  /** Returns a random access file to read this log. */
  private BlockLogReader<K, V> getReader(File file) throws ChangelogException
  {
    try
    {
      return BlockLogReader.newReader(file, new RandomAccessFile(file, "r"), parser) ;
    }
    catch (Exception e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_OPEN_READER_ON_LOG_FILE.get(file.getPath()), e);
    }
  }

  /**
   * Shutdown this pool, releasing all files handles opened
   * on the file.
   */
  void shutdown()
  {
    // Nothing to do yet as no file handle is kept opened.
  }

}
