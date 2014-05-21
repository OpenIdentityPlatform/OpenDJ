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
import static org.opends.server.loggers.debug.DebugLogger.*;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.forgerock.util.Reject;
import org.opends.messages.Message;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.Log.RepositionableCursor;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DebugLogLevel;
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
 * {@code Log}. In particular, there is no concurrency management and no checks
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
  private static final DebugTracer TRACER = getTracer();

  /** The file containing the records. */
  private final File logfile;

  /** The parser of records, to convert bytes to record and record to bytes. */
  private final RecordParser<K, V> parser;

  /** The pool to obtain a reader on the log. */
  private LogReaderPool readerPool;

  /**
   * The writer on the log file, which may be {@code null} if log file is not
   * write-enabled
   */
  private LogWriter writer;

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
    this.parser = parser;
    this.isWriteEnabled = isWriteEnabled;

    initialize();
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
   * Initialize this log.
   * <p>
   * Create directories and file if necessary, and create a writer
   * and pool of readers.
   *
   * @throws ChangelogException
   *            If an errors occurs during initialization.
   */
  private void initialize() throws ChangelogException
  {
    createLogFileIfNotExists();
    if (isWriteEnabled)
    {
      writer = new LogWriter(logfile);
    }
    readerPool = new LogReaderPool(logfile);
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
    try
    {
      writer.write(encodeRecord(record));
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_ADD_RECORD.get(record.toString(), getPath()), e);
    }
  }

  private ByteString encodeRecord(final Record<K, V> record)
  {
    final ByteString data = parser.encodeRecord(record);
    return new ByteStringBuilder()
      .append(data.length())
      .append(data)
      .toByteString();
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
          Message.raw("Error when dumping content of log '%s' in target file : '%s'", getPath(), dumpFile), e);
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
   * <p>
   * The returned cursor initially points to record corresponding to the first
   * key, that is {@code cursor.getRecord()} is equals to the record
   * corresponding to the first key before any call to {@code cursor.next()}
   * method.
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
   * Returns a cursor that allows to retrieve the records from this log,
   * starting at the position defined by the provided key.
   * <p>
   * The returned cursor initially points to record corresponding to the key,
   * that is {@code cursor.getRecord()} is equals to the record corresponding to
   * the key before any call to {@code cursor.next()} method.
   *
   * @param key
   *          Key to use as a start position for the cursor. If key is
   *          {@code null}, cursor will point at the first record of the log.
   * @return a cursor on the log records, which is never {@code null}
   * @throws ChangelogException
   *           If the cursor can't be created.
   */
  LogFileCursor<K, V> getCursor(final K key) throws ChangelogException
  {
    return getCursor(key, false);
  }

  /**
   * Returns a cursor that allows to retrieve the records from this log,
   * starting at the position defined by the smallest key that is higher than
   * the provided key.
   * <p>
   * The returned cursor initially points to record corresponding to the key
   * found, that is {@code cursor.getRecord()} is equals to the record
   * corresponding to the key found before any call to {@code cursor.next()}
   * method.
   *
   * @param key
   *          Key to use as a start position for the cursor. If key is
   *          {@code null}, cursor will point at the first record of the log.
   * @return a cursor on the log records, which is never {@code null}
   * @throws ChangelogException
   *           If the cursor can't be created.
   */
  LogFileCursor<K, V> getNearestCursor(final K key) throws ChangelogException
  {
    return getCursor(key, true);
  }

  /** Returns a cursor starting from a key, using the strategy corresponding to provided indicator. */
  private LogFileCursor<K, V> getCursor(final K key, boolean findNearest)
      throws ChangelogException
  {
    if (key == null)
    {
      return getCursor();
    }
    LogFileCursor<K, V> cursor = null;
    try
    {
      cursor = new LogFileCursor<K, V>(this);
      cursor.positionTo(key, findNearest);
      // if target is not found, cursor is positioned at end of stream
      return cursor;
    }
    catch (ChangelogException e) {
      StaticUtils.close(cursor);
      throw e;
    }
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
      return cursor.getRecord();
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
      Record<K, V> record = cursor.getRecord();
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
      Record<K, V> record = cursor.getRecord();
      if (record == null)
      {
        return 0L;
      }
      long counter = 1L;
      while (cursor.next())
      {
        record = cursor.getRecord();
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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

  /** Read a record from the provided reader. */
  private Record<K,V> readRecord(final RandomAccessFile reader) throws ChangelogException
  {
    try
    {
      final ByteString recordData = readEncodedRecord(reader);
      return recordData != null ? parser.decodeRecord(recordData) : null;
    }
    catch(DecodingException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DECODE_RECORD.get(logfile.getPath()), e);
    }
  }

  private ByteString readEncodedRecord(final RandomAccessFile reader) throws ChangelogException
  {
    try
    {
      final byte[] lengthData = new byte[4];
      reader.readFully(lengthData);
      int recordLength = ByteString.wrap(lengthData).toInt();

      final byte[] recordData = new byte[recordLength];
      reader.readFully(recordData);
      return ByteString.wrap(recordData);
    }
    catch(EOFException e)
    {
      // end of stream, no record or uncomplete record
      return null;
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DECODE_RECORD.get(logfile.getPath()), e);
    }
  }

  /** Seek to given position on the provided reader. */
  private void seek(RandomAccessFile reader, long position) throws ChangelogException
  {
    try
    {
      reader.seek(position);
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_SEEK.get(position, logfile.getPath()), e);
    }
  }

  /**
   * Returns a random access file to read this log.
   * <p>
   * Assumes that calling methods ensure that log is not closed.
   */
  private RandomAccessFile getReader() throws ChangelogException
  {
    return readerPool.get();
  }

  /** Release the provided reader. */
  private void releaseReader(RandomAccessFile reader) {
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

  /**
   * Implements a repositionable cursor on the log file.
   * <p>
   * The cursor initially points to a record, that is {@code cursor.getRecord()}
   * is equals to the first record available from the cursor before any call to
   * {@code cursor.next()} method.
   */
  static final class LogFileCursor<K extends Comparable<K>, V> implements RepositionableCursor<K,V>
  {
    /** The underlying log on which entries are read. */
    private final LogFile<K, V> logFile;

    /** To read the records. */
    private final RandomAccessFile reader;

    /** The current available record, may be {@code null}. */
    private Record<K,V> currentRecord;

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
      try
      {
        // position to the first record.
        next();
      }
      catch (ChangelogException e)
      {
        close();
        throw e;
      }
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
      logFile.seek(reader, filePosition);
    }

    /** {@inheritDoc} */
    @Override
    public boolean next() throws ChangelogException
    {
      currentRecord = logFile.readRecord(reader);
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
    public boolean positionTo(final K key, boolean findNearest) throws ChangelogException {
      do
      {
        if (currentRecord != null)
        {
          final boolean matches = findNearest ?
              currentRecord.getKey().compareTo(key) >= 0 : currentRecord.getKey().equals(key);
          if (matches)
          {
            if (findNearest && currentRecord.getKey().equals(key))
            {
              // skip key in order to position on lowest higher key
              next();
            }
            return true;
          }
        }
        next();
      }
      while (currentRecord != null);
      return false;
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
      try
      {
        return reader.getFilePointer();
      }
      catch (IOException e)
      {
        throw new ChangelogException(
            ERR_CHANGELOG_UNABLE_TO_GET_CURSOR_READER_POSITION_LOG_FILE.get(logFile.getPath()), e);
      }
    }

    /** {@inheritDoc} */
    public String toString()
    {
      return String.format("Cursor on log file: %s, current record: %s", logFile.logfile, currentRecord);
    }
  }
}
