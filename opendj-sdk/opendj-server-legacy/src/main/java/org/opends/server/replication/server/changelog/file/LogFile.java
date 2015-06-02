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

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Pair;
import org.forgerock.util.Reject;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.Log.RepositionableCursor;
import org.opends.server.util.StaticUtils;

/**
 * A log file, containing part of a {@code Log}. The log file may be:
 * <ul>
 * <li>write-enabled : allowing to append key-value records and read records
 * from cursors,</li>
 * <li>read-only : allowing to read records from cursors.</li>
 * </ul>
 * <p>
 * A log file is NOT intended to be used directly, but only has part of a
 * {@link Log}. In particular, there is no concurrency management and no checks
 * to ensure that log is not closed when performing any operation on it. Those
 * are managed at the {@code Log} level.
 *
 * @param <K>
 *          Type of the key of a record, which must be comparable.
 * @param <V>
 *          Type of the value of a record.
 */
final class LogFile<K extends Comparable<K>, V> implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The file containing the records. */
  private final File logfile;

  /** The pool to obtain a reader on the log. */
  private final LogReaderPool<K, V> readerPool;

  /**
   * The writer on the log file, which may be {@code null} if log file is not
   * write-enabled.
   */
  private final BlockLogWriter<K, V> writer;

  /** Indicates if log is enabled for write. */
  private final boolean isWriteEnabled;

  /**
   * Creates a new log file.
   *
   * @param logFilePath
   *          Path of the log file.
   * @param parser
   *          Parser of records.
   * @param isWriteEnabled
   *          {@code true} if this changelog is write-enabled, {@code false}
   *          otherwise.
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  private LogFile(final File logFilePath, final RecordParser<K, V> parser, boolean isWriteEnabled)
      throws ChangelogException
  {
    Reject.ifNull(logFilePath, parser);
    this.logfile = logFilePath;
    this.isWriteEnabled = isWriteEnabled;

    createLogFileIfNotExists();
    if (isWriteEnabled)
    {
      ensureLogFileIsValid(parser);
      writer = BlockLogWriter.newWriter(new LogWriter(logfile), parser);
    }
    else
    {
      writer = null;
    }
    readerPool = new LogReaderPool<K, V>(logfile, parser);
  }

  /**
   * Creates a read-only log file with the provided root path and record parser.
   *
   * @param <K>
   *            Type of the key of a record, which must be comparable.
   * @param <V>
   *            Type of the value of a record.
   * @param logFilePath
   *          Path of the log file.
   * @param parser
   *          Parser of records.
   * @return a read-only log file
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  static <K extends Comparable<K>, V> LogFile<K, V> newReadOnlyLogFile(final File logFilePath,
      final RecordParser<K, V> parser) throws ChangelogException
  {
    return new LogFile<K, V>(logFilePath, parser, false);
  }

  /**
   * Creates a write-enabled log file that appends records to the end of file,
   * with the provided root path and record parser.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param logFilePath
   *          Path of the log file.
   * @param parser
   *          Parser of records.
   * @return a write-enabled log file
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  static <K extends Comparable<K>, V> LogFile<K, V> newAppendableLogFile(final File logFilePath,
      final RecordParser<K, V> parser) throws ChangelogException
  {
    return new LogFile<K, V>(logFilePath, parser, true);
  }

  /**
   * Returns the file containing the records.
   *
   * @return the file
   */
  File getFile()
  {
    return logfile;
  }

  private void checkLogIsEnabledForWrite() throws ChangelogException
  {
    if (!isWriteEnabled)
    {
      throw new ChangelogException(WARN_CHANGELOG_NOT_ENABLED_FOR_WRITE.get(logfile.getPath()));
    }
  }

  private void createLogFileIfNotExists() throws ChangelogException
  {
    try
    {
      if (!logfile.exists())
      {
        logfile.createNewFile();
      }
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_CREATE_LOG_FILE.get(logfile.getPath()), e);
    }
  }

  /**
   * Ensure that log file is not corrupted, by checking it is valid and cleaning
   * the end of file if necessary, to remove a partially written record.
   * <p>
   * If log file is cleaned to remove a partially written record, then a message
   * is logged for information.
   *
   * @throws ChangelogException
   *           If an error occurs or if log file is corrupted and can't be
   *           cleaned
   */
  private void ensureLogFileIsValid(final RecordParser<K, V> parser) throws ChangelogException
  {
    BlockLogReader<K, V> reader = null;
    try
    {
      final RandomAccessFile readerWriter = new RandomAccessFile(logfile, "rws");
      reader = BlockLogReader.newReader(logfile, readerWriter, parser) ;
      final long lastValidPosition = reader.checkLogIsValid();
      if (lastValidPosition != -1)
      {
          // truncate the file to point where last valid record has been read
          readerWriter.setLength(lastValidPosition);
          logger.error(INFO_CHANGELOG_LOG_FILE_RECOVERED.get(logfile.getPath()));
      }
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_RECOVER_LOG_FILE.get(
          logfile.getPath(),
          StaticUtils.stackTraceToSingleLineString(e)));
    }
    finally
    {
      StaticUtils.close(reader);
    }
  }

  /**
   * Add the provided record at the end of this log.
   * <p>
   * In order to ensure that record is written out of buffers and persisted
   * to file system, it is necessary to explicitely call the
   * {@code syncToFileSystem()} method.
   *
   * @param record
   *          The record to add.
   * @throws ChangelogException
   *           If the record can't be added to the log.
   */
  void append(final Record<K, V> record) throws ChangelogException
  {
    checkLogIsEnabledForWrite();
    writer.write(record);
  }

  /**
   * Dump this log file as a text file, intended for debugging purpose only.
   *
   * @param dumpFile
   *          File that will contains log records using a human-readable format
   * @throws ChangelogException
   *           If an error occurs during dump
   */
  void dumpAsTextFile(File dumpFile) throws ChangelogException
  {
    DBCursor<Record<K, V>> cursor = getCursor();
    BufferedWriter textWriter = null;
    try
    {
      textWriter = new BufferedWriter(new FileWriter(dumpFile));
      while (cursor.getRecord() != null)
      {
        Record<K, V> record = cursor.getRecord();
        textWriter.write("key=" + record.getKey());
        textWriter.write(" | ");
        textWriter.write("value=" + record.getValue());
        textWriter.write('\n');
        cursor.next();
      }
    }
    catch (IOException e)
    {
      // No I18N needed, used for debugging purpose only
      throw new ChangelogException(
          LocalizableMessage.raw("Error when dumping content of log '%s' in target file : '%s'", getPath(), dumpFile),
          e);
    }
    finally
    {
      StaticUtils.close(textWriter);
    }
  }

  /**
   * Synchronize all records added with the file system, ensuring that records
   * are effectively persisted.
   * <p>
   * After a successful call to this method, it is guaranteed that all records
   * added to the log are persisted to the file system.
   *
   * @throws ChangelogException
   *           If the synchronization fails.
   */
  void syncToFileSystem() throws ChangelogException
  {
    checkLogIsEnabledForWrite();
    try
    {
      writer.sync();
    }
    catch (Exception e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_SYNC.get(getPath()), e);
    }
  }

  /**
   * Returns a cursor that allows to retrieve the records from this log,
   * starting at the first position.
   *
   * @return a cursor on the log records, which is never {@code null}
   * @throws ChangelogException
   *           If the cursor can't be created.
   */
  LogFileCursor<K, V> getCursor() throws ChangelogException
  {
    return new LogFileCursor<K, V>(this);
  }

  /**
   * Returns a cursor initialised to the provided record and position in file.
   *
   * @param record
   *            The initial record this cursor points on
   * @param position
   *            The file position this cursor points on
   * @return the cursor
   * @throws ChangelogException
   *            If a problem occurs while creating the cursor.
   */
  LogFileCursor<K, V> getCursorInitialisedTo(Record<K,V> record, long position) throws ChangelogException
  {
    return new LogFileCursor<K, V>(this, record, position);
  }

  /**
   * Returns the oldest (first) record from this log.
   *
   * @return the oldest record, which may be {@code null} if there is no record
   *         in the log.
   * @throws ChangelogException
   *           If an error occurs while retrieving the record.
   */
  Record<K, V> getOldestRecord() throws ChangelogException
  {
    DBCursor<Record<K, V>> cursor = null;
    try
    {
      cursor = getCursor();
      return cursor.next() ? cursor.getRecord() : null;
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  /**
   * Returns the newest (last) record from this log.
   *
   * @return the newest record, which may be {@code null}
   * @throws ChangelogException
   *           If an error occurs while retrieving the record.
   */
  Record<K, V> getNewestRecord() throws ChangelogException
  {
    // TODO : need a more efficient way to retrieve it
    DBCursor<Record<K, V>> cursor = null;
    try
    {
      cursor = getCursor();
      Record<K, V> record = null;
      while (cursor.next())
      {
        record = cursor.getRecord();
      }
      return record;
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  /**
   * Returns the number of records in the log.
   *
   * @return the number of records
   * @throws ChangelogException
   *            If an error occurs.
   */
  long getNumberOfRecords() throws ChangelogException
  {
    // TODO  : need a more efficient way to retrieve it
    DBCursor<Record<K, V>> cursor = null;
    try
    {
      cursor = getCursor();
      long counter = 0L;
      while (cursor.next())
      {
        counter++;
      }
      return counter;
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    if (isWriteEnabled)
    {
      try
      {
        syncToFileSystem();
      }
      catch (ChangelogException e)
      {
        logger.traceException(e);
      }
      writer.close();
    }
    readerPool.shutdown();
  }

  /**
   * Delete this log file (file is physically removed). Should be called only
   * when log file is closed.
   *
   * @throws ChangelogException
   *            If log file can't be deleted.
   */
  void delete() throws ChangelogException
  {
    final boolean isDeleted = logfile.delete();
    if (!isDeleted)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DELETE_LOG_FILE.get(getPath()));
    }
  }

  /**
   * Return the size of this log file in bytes.
   *
   * @return the size of log file
   */
  long getSizeInBytes()
  {
    return writer.getBytesWritten();
  }

  /** The path of this log file as a String. */
  private String getPath()
  {
    return logfile.getPath();
  }

  /**
   * Returns a reader for this log.
   * <p>
   * Assumes that calling methods ensure that log is not closed.
   */
  private BlockLogReader<K, V> getReader() throws ChangelogException
  {
    return readerPool.get();
  }

  /** Release the provided reader. */
  private void releaseReader(BlockLogReader<K, V> reader) {
    readerPool.release(reader);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return logfile.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object that)
  {
    if (this == that)
    {
      return true;
    }
    if (!(that instanceof LogFile))
    {
      return false;
    }
    final LogFile<?, ?> other = (LogFile<?, ?>) that;
    return logfile.equals(other.logfile);
  }

  /** Implements a repositionable cursor on the log file. */
  static final class LogFileCursor<K extends Comparable<K>, V> implements RepositionableCursor<K,V>
  {
    /** The underlying log on which entries are read. */
    private final LogFile<K, V> logFile;

    /** To read the records. */
    private final BlockLogReader<K, V> reader;

    /** The current available record, may be {@code null}. */
    private Record<K,V> currentRecord;

    /**
     * The initial record when starting from a given key. It must be
     * stored because it is read in advance.
     */
    private Record<K,V> initialRecord;

    /**
     * Creates a cursor on the provided log.
     *
     * @param logFile
     *           The log on which the cursor read records.
     * @throws ChangelogException
     *           If an error occurs when creating the cursor.
     */
    private LogFileCursor(final LogFile<K, V> logFile) throws ChangelogException
    {
      this.logFile = logFile;
      this.reader = logFile.getReader();
    }

    /**
     * Creates a cursor on the provided log, initialised to the provided record and
     * pointing to the provided file position.
     * <p>
     * Note: there is no check to ensure that provided record and file position are
     * consistent. It is the responsability of the caller of this method.
     */
    private LogFileCursor(LogFile<K, V> logFile, Record<K, V> record, long filePosition) throws ChangelogException
    {
      this.logFile = logFile;
      this.reader = logFile.getReader();
      this.currentRecord = record;
      reader.seekToPosition(filePosition);
    }

    /** {@inheritDoc} */
    @Override
    public boolean next() throws ChangelogException
    {
      if (initialRecord != null)
      {
        // initial record is used only once
        currentRecord = initialRecord;
        initialRecord = null;
        return true;
      }
      currentRecord = reader.readRecord();
      return currentRecord != null;
    }

    /** {@inheritDoc} */
    @Override
    public Record<K,V> getRecord()
    {
      return currentRecord;
    }

    /** {@inheritDoc} */
    @Override
    public boolean positionTo(final K key, final KeyMatchingStrategy match, final PositionStrategy pos)
        throws ChangelogException {
      final Pair<Boolean, Record<K, V>> result = reader.seekToRecord(key, match, pos);
      final boolean found = result.getFirst();
      initialRecord = found ? result.getSecond() : null;
      return found;
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
      logFile.releaseReader(reader);
    }

    /**
     * Returns the file position this cursor is pointing at.
     *
     * @return the position of reader on the log file
     * @throws ChangelogException
     *          If an error occurs.
     */
    long getFilePosition() throws ChangelogException
    {
      return reader.getFilePosition();
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return String.format("Cursor on log file: %s, current record: %s", logFile.logfile, currentRecord);
    }
  }
}
