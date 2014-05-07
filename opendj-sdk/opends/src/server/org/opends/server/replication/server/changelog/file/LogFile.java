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

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.messages.Message;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ReplicationMessages.*;

/**
 * A file-based log that allow to append key-value records and
 * read them using a {@code DBCursor}.
 *
 * @param <K>
 *          Type of the key of a record, which must be comparable.
 * @param <V>
 *          Type of the value of a record.
 */
final class LogFile<K extends Comparable<K>, V> implements Closeable
{

  private static final DebugTracer TRACER = getTracer();

  // Non private for use in tests
  static final String LOG_FILE_NAME = "current.log";

  /** The path of directory that contains the log file. */
  private final File rootPath;

  /** The log file containing the records. */
  private final File logfile;

  /** The parser of records, to convert bytes to record and record to bytes. */
  private final RecordParser<K, V> parser;

  /** The pool to obtain a reader on the log. */
  private LogReaderPool readerPool;

  /** The writer on the log, which may be {@code null} if log is not write-enabled */
  private LogWriter writer;

  /** Indicates if log is enabled for write. */
  private final boolean isWriteEnabled;

  /** Indicates if the log is closed. */
  private volatile boolean isClosed;

  /** The exclusive lock used for wide changes on this log file : init, clear, sync and close. */
  private final Lock exclusiveLock;

  /**
   * The shared lock used for read, write and flush operations on this log file.
   * Write and flush operations can be shared because they're synchronized in the underlying writer.
   * Reads can be done safely when writing because partially written records are handled.
   */
  private final Lock sharedLock;

  /**
   * Creates a new log file.
   *
   * @param rootPath
   *          Path of root directory that contains the log file.
   * @param parser
   *          Parser of records.
   * @param isWriteEnabled
   *          {@code true} if this changelog is write-enabled, {@code false}
   *          otherwise.
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  private LogFile(final File rootPath, final RecordParser<K, V> parser, boolean isWriteEnabled)
      throws ChangelogException
  {
    this.rootPath = rootPath;
    this.parser = parser;
    this.isWriteEnabled = isWriteEnabled;
    this.logfile = new File(rootPath, LOG_FILE_NAME);

    final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    this.exclusiveLock = lock.writeLock();
    this.sharedLock = lock.readLock();

    initialize();
  }

  /**
   * Creates a read-only log file with the provided root path and record parser.
   *
   * @param <K>
   *            Type of the key of a record, which must be comparable.
   * @param <V>
   *            Type of the value of a record.
   * @param rootPath
   *          Path of root directory that contains the log file.
   * @param parser
   *          Parser of records.
   * @return a read-only log file
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  public static <K extends Comparable<K>, V> LogFile<K, V> newReadOnlyLogFile(final File rootPath,
      final RecordParser<K, V> parser) throws ChangelogException
  {
    return new LogFile<K, V>(rootPath, parser, false);
  }

  /**
   * Creates a write-enabled log file that appends records to the end of file,
   * with the provided root path and record parser.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param rootPath
   *          Path of root directory that contains the log file.
   * @param parser
   *          Parser of records.
   * @return a write-enabled log file
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  public static <K extends Comparable<K>, V> LogFile<K, V> newAppendableLogFile(final File rootPath,
      final RecordParser<K, V> parser) throws ChangelogException
  {
    return new LogFile<K, V>(rootPath, parser, true);
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
    exclusiveLock.lock();
    try
    {
      createRootDirIfNotExists();
      createLogFileIfNotExists();
      isClosed = false;
      if (isWriteEnabled)
      {
        writer = LogWriter.acquireWriter(logfile);
      }
      readerPool = new LogReaderPool(logfile);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Returns the name of this log.
   *
   * @return the name, which corresponds to the directory containing the log
   */
  public String getName()
  {
    return logfile.getParent().toString();
  }

  /**
   * Empties the log, discarding all records it contains.
   * <p>
   * This method should not be called with open cursors or
   * when multiple instances of the log are opened.
   *
   * @throws ChangelogException
   *            If a problem occurs.
   */
  public void clear() throws ChangelogException
  {
    checkLogIsEnabledForWrite();

    exclusiveLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      close();
      final boolean isDeleted = logfile.delete();
      if (!isDeleted)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DELETE_LOG_FILE.get(logfile.getPath()));
      }
      initialize();
    }
    catch (Exception e)
    {
      throw new ChangelogException(ERR_ERROR_CLEARING_DB.get(getName(), stackTraceToSingleLineString(e)));
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private void checkLogIsEnabledForWrite() throws ChangelogException
  {
    if (!isWriteEnabled)
    {
      throw new ChangelogException(WARN_CHANGELOG_NOT_ENABLED_FOR_WRITE.get(rootPath.getPath()));
    }
  }

  private void createRootDirIfNotExists() throws ChangelogException
  {
    if (!rootPath.exists())
    {
      final boolean created = rootPath.mkdirs();
      if (!created)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_CREATE_LOG_DIRECTORY.get(rootPath.getPath()));
      }
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
   * Add a record at the end of this log from the provided key and value.
   * <p>
   * In order to ensure that record is written out of buffers and persisted
   * to file system, it is necessary to explicitely call the
   * {@code syncToFileSystem()} method.
   *
   * @param key
   *          The key of the record.
   * @param value
   *          The value of the record.
   * @throws ChangelogException
   *           If the record can't be added to the log.
   */
  public void addRecord(final K key, final V value) throws ChangelogException
  {
    addRecord(Record.from(key, value));
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
  public void addRecord(final Record<K, V> record) throws ChangelogException
  {
    checkLogIsEnabledForWrite();

    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      writer.write(encodeRecord(record));
      writer.flush();
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_ADD_RECORD.get(record.toString(), getName()), e);
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  private ByteString encodeRecord(final Record<K, V> record)
  {
    final ByteString data = parser.encodeRecord(record.getKey(), record.getValue());
    return new ByteStringBuilder()
      .append(data.length())
      .append(data)
      .toByteString();
  }

  /**
   * Dump this log as text file, intended for debugging purpose only.
   *
   * @param dumpFile
   *          File that will contains log records using a human-readable format
   * @throws ChangelogException
   *           If an error occurs during dump
   */
  public void dumpAsTextFile(File dumpFile) throws ChangelogException
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
        textWriter.write(" -- ");
        textWriter.write("value=" + record.getValue());
        textWriter.write('\n');
        cursor.next();
      }
    }
    catch (IOException e)
    {
      // No I18N needed, used for debugging purpose only
      throw new ChangelogException(
          Message.raw("Error when dumping content of log '%s' in target file : '%s'", getName(), dumpFile), e);
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
  public void syncToFileSystem() throws ChangelogException
  {
    exclusiveLock.lock();
    try
    {
      writer.sync();
    }
    catch (Exception e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_SYNC.get(getName()), e);
    }
    finally
    {
      exclusiveLock.unlock();
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
  public LogCursor<K, V> getCursor() throws ChangelogException
  {
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return new EmptyLogCursor<K, V>();
      }
      return new LogFileCursor<K, V>(this);
    }
    finally
    {
      sharedLock.unlock();
    }
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
  public LogCursor<K, V> getCursor(final K key) throws ChangelogException
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
  public LogCursor<K, V> getNearestCursor(final K key) throws ChangelogException
  {
    return getCursor(key, true);
  }

  /** Returns a cursor starting from a key, using the strategy corresponding to provided indicator. */
  private LogCursor<K, V> getCursor(final K key, boolean findNearest)
      throws ChangelogException
  {
    if (key == null)
    {
      return getCursor();
    }
    LogFileCursor<K, V> cursor = null;
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return new EmptyLogCursor<K, V>();
      }
      cursor = new LogFileCursor<K, V>(this);
      cursor.positionTo(key, findNearest);
      // if target is not found, cursor is positioned at end of stream
      return cursor;
    }
    catch (ChangelogException e) {
      StaticUtils.close(cursor);
      throw e;
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  /**
   * Returns the oldest (first) record from this log.
   *
   * @return the oldest record, which may be {@code null} if there is no record
   *         in the log.
   * @throws ChangelogException
   *           If an error occurs while retrieving the record.
   */
  public Record<K, V> getOldestRecord() throws ChangelogException
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
  public Record<K, V> getNewestRecord() throws ChangelogException
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
    exclusiveLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }

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
      isClosed = true;
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /** Read a record from the provided reader. */
  private Record<K,V> readRecord(final RandomAccessFile reader) throws ChangelogException
  {
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return null;
      }
      final ByteString recordData = readEncodedRecord(reader);
      return recordData != null ? parser.decodeRecord(recordData) : null;
    }
    catch(DecodingException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DECODE_RECORD.get(logfile.getPath()), e);
    }
    finally
    {
      sharedLock.unlock();
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

  /** Seek to provided position on the provided reader. */
  private void seek(RandomAccessFile reader, long position) throws ChangelogException
  {
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      reader.seek(position);
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_SEEK.get(position, logfile.getPath()), e);
    }
    finally
    {
      sharedLock.unlock();
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
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      readerPool.release(reader);
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  /**
   * A cursor on the log.
   */
  static interface LogCursor<K extends Comparable<K>,V> extends DBCursor<Record<K, V>>
  {
    /**
     * Position the cursor to the record corresponding to the provided key or to
     * the nearest key (the lowest key higher than the provided key).
     * <p>
     * The record is only searched forward. To search backward, it is first
     * necessary to call the {@code rewind()} method to start from beginning of
     * log file.
     *
     * @param key
     *          Key to use as a start position for the cursor. If key is
     *          {@code null}, use the key of the first record instead.
     * @param findNearest
     *          If {@code true}, start position is the lowest key that is higher
     *          than the provided key, otherwise start position is the provided
     *          key.
     * @return {@code true} if cursor is successfully positionned to the key or
     *         the the nearest key, {@code false} otherwise.
     * @throws ChangelogException
     *           If an error occurs when positioning cursor.
     */
    boolean positionTo(K key, boolean findNearest) throws ChangelogException;

    /**
     * Rewind the cursor, positioning it to the beginning of the log file,
     * pointing to no record initially.
     *
     * @throws ChangelogException
     *          If an error occurs when rewinding to zero.
     */
    void rewind() throws ChangelogException;
  }

  /**
   * Implements a cursor on the log.
   * <p>
   * The cursor initially points to a record, that is {@code cursor.getRecord()}
   * is equals to the first record available from the cursor before any call to
   * {@code cursor.next()} method.
   */
  private static final class LogFileCursor<K extends Comparable<K>, V> implements LogCursor<K,V>
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
    LogFileCursor(final LogFile<K, V> logFile) throws ChangelogException
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

    /** {@inheritDoc} */
    public String toString()
    {
      return String.format("Cursor on log file: %s, current record: %s", logFile.logfile, currentRecord);
    }

    /** {@inheritDoc} */
    @Override
    public Record<K,V> getRecord()
    {
      return currentRecord;
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
    public void rewind() throws ChangelogException
    {
      logFile.seek(reader, 0);
      currentRecord = null;
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
      logFile.releaseReader(reader);
    }
  }

  /** An empty cursor, that always return null records and false to {@code next()} method. */
  static final class EmptyLogCursor<K extends Comparable<K>, V> implements LogCursor<K,V>
  {
    /** {@inheritDoc} */
    @Override
    public Record<K,V> getRecord()
    {
      return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean next()
    {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean positionTo(K key, boolean returnLowestHigher) throws ChangelogException
    {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public void rewind() throws ChangelogException
    {
      // nothing to do
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
      // nothing to do
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return "EmptyLogCursor";
    }

  }
}
