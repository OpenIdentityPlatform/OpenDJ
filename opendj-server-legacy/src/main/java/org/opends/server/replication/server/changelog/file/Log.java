/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.jcip.annotations.GuardedBy;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Pair;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.forgerock.util.time.TimeService;
import org.opends.server.replication.server.changelog.api.AbortedChangelogCursorException;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.file.LogFile.LogFileCursor;
import org.opends.server.util.StaticUtils;

/**
 * A multi-file log that features appending key-value records and reading them
 * using a {@code DBCursor}.
 * The records must be appended to the log in ascending order of the keys.
 * <p>
 * A log is defined for a path - the log path - and contains one to many log files:
 * <ul>
 * <li>it has always at least one log file, the head log file, named "head.log",
 * which is used to append the records.</li>
 * <li>it may have from zero to many read-only log files, which are named after
 * the pattern '[lowkey]_[highkey}.log' where [lowkey] and [highkey] are respectively
 * the string representation of lowest and highest key present in the log file.</li>
 * </ul>
 * A read-only log file is created each time the head log file has reached the
 * maximum size limit or the time limit. The head log file is then rotated to the
 * read-only file and a new empty head log file is opened. There is no limit on the
 * number of read-only files, but they can be purged.
 * <p>
 * A log is obtained using the {@code Log.openLog()} method and must always be
 * released using the {@code close()} method.
 * <p>
 * Usage example:
 * <pre>
 *   Log<K, V> log = null;
 *   try
 *   {
 *     log = Log.openLog(logPath, parser, maxFileSize);
 *     log.append(key, value);
 *     DBCursor<K, V> cursor = log.getCursor(someKey);
 *     // use cursor, then close cursor
 *   }
 *   finally
 *   {
 *     log.close();
 *   }
 * }
 * </pre>
 *
 * @param <K>
 *          Type of the key of a record, which must be comparable.
 * @param <V>
 *          Type of the value of a record.
 */
final class Log<K extends Comparable<K>, V> implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String LOG_FILE_SUFFIX = ".log";

  static final String HEAD_LOG_FILE_NAME = "head" + LOG_FILE_SUFFIX;

  private static final String LOG_FILE_NAME_SEPARATOR = "_";

  private static final FileFilter READ_ONLY_LOG_FILES_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      return file.isFile() && file.getName().endsWith(LOG_FILE_SUFFIX) &&
          !file.getName().equals(HEAD_LOG_FILE_NAME);
    }
  };

  /** Map that holds the unique log instance for each log path. */
  private static final Map<File, Log<?, ?>> logsCache = new HashMap<>();

  /**
   * The number of references on this log instance. It is incremented each time
   * a log is opened on the same log path. The log is effectively closed only
   * when the {@code close()} method is called and this value is at most 1.
   */
  private int referenceCount;

  /** The path of directory for this log, where log files are stored. */
  private final File logPath;

  /** The parser used for encoding/decoding of records. */
  private final RecordParser<K, V> recordParser;

  /**
   * Indicates if this log is closed. When the log is closed, all methods return
   * immediately with no effect.
   */
  private boolean isClosed;

  /**
   * The log files contained in this log, ordered by key.
   * <p>
   * The head log file is always present and is associated with the maximum
   * possible key, given by the record parser.
   * <p>
   * The read-only log files are associated with the highest key they contain.
   */
  private final TreeMap<K, LogFile<K, V>> logFiles = new TreeMap<>();

  /**
   * The list of non-empty cursors opened on this log. Opened cursors may have
   * to be updated when rotating the head log file.
   */
  private final List<AbortableLogCursor<K, V>> openCursors = new CopyOnWriteArrayList<>();

  /**
   * A log file can be rotated once it has exceeded this size limit. The log file can have
   * a size much larger than this limit if the last record written has a huge size.
   *
   * TODO : to be replaced later by a list of configurable Rotation policy
   * eg, List<RotationPolicy> rotationPolicies = new ArrayList<RotationPolicy>();
   */
  private final long sizeLimitPerLogFileInBytes;

  /** The time service used for timing. It is package private so it can be modified by test case. */
  TimeService timeService = TimeService.SYSTEM;

  /** A log file can be rotated once it has exceeded a given time interval. No rotation happens if equals to zero. */
  private long rotationIntervalInMillis;

  /** The last time a log file was rotated. */
  private long lastRotationTime;

  /**
   * The exclusive lock used for log rotation and lifecycle operations on this log:
   * initialize, clear, sync and close.
   */
  private final Lock exclusiveLock;

  /** The shared lock used for write operations and accessing {@link #logFiles} map. */
  private final Lock sharedLock;

  /**
   * The replication environment used to create this log. The log is notifying it for any change
   * that must be persisted.
   */
  private final ReplicationEnvironment replicationEnv;

  /**
   * Open a log with the provided log path, record parser and maximum size per
   * log file.
   * <p>
   * If no log exists for the provided path, a new one is created.
   *
   * @param <K>
   *          Type of the key of a record, which must be comparable.
   * @param <V>
   *          Type of the value of a record.
   * @param replicationEnv
   *          The replication environment used to create this log.
   * @param logPath
   *          Path of the log.
   * @param parser
   *          Parser for encoding/decoding of records.
   * @param rotationParameters
   *          Parameters for the log files rotation.
   * @return a log
   * @throws ChangelogException
   *           If a problem occurs during initialization.
   */
  static synchronized <K extends Comparable<K>, V> Log<K, V> openLog(final ReplicationEnvironment replicationEnv,
      final File logPath, final RecordParser<K, V> parser, final LogRotationParameters rotationParameters)
      throws ChangelogException
  {
    Reject.ifNull(logPath, parser);
    @SuppressWarnings("unchecked")
    Log<K, V> log = (Log<K, V>) logsCache.get(logPath);
    if (log == null)
    {
      log = new Log<>(replicationEnv, logPath, parser, rotationParameters);
      logsCache.put(logPath, log);
    }
    else
    {
      // TODO : check that parser and size limit are compatible with the existing one,
      // and issue a warning if it is not the case
      log.referenceCount++;
    }
    return log;
  }

  /**
   * Returns an empty cursor.
   *
   * @param <K> the type of keys.
   * @param <V> the type of values.
   * @return an empty cursor
   */
  static <K extends Comparable<K>, V> RepositionableCursor<K, V> getEmptyCursor() {
    return new Log.EmptyCursor<>();
  }

  /** Holds the parameters for log files rotation. */
  static class LogRotationParameters {
    private final long sizeLimitPerFileInBytes;
    private final long rotationInterval;
    private final long lastRotationTime;

    /**
     * Creates rotation parameters.
     *
     * @param sizeLimitPerFileInBytes
     *           Size limit before rotating a log file.
     * @param rotationInterval
     *           Time interval before rotating a log file.
     * @param lastRotationTime
     *           Last time a log file was rotated.
     */
    LogRotationParameters(long sizeLimitPerFileInBytes, long rotationInterval, long lastRotationTime)
    {
      this.sizeLimitPerFileInBytes = sizeLimitPerFileInBytes;
      this.rotationInterval = rotationInterval;
      this.lastRotationTime = lastRotationTime;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "("
          + "sizeLimitPerFileInBytes=" + sizeLimitPerFileInBytes
          + ", rotationInterval=" + rotationInterval
          + ", lastRotationTime=" + lastRotationTime
          + ")";
    }
  }

  /**
   * Set the time interval for rotation of log file.
   *
   * @param rotationIntervalInMillis
   *           time interval before rotation of log file
   */
  void setRotationInterval(long rotationIntervalInMillis)
  {
    this.rotationIntervalInMillis = rotationIntervalInMillis;
  }

  /**
   * Release a reference to the log corresponding to provided path. The log is
   * closed if this is the last reference.
   */
  private static synchronized void releaseLog(final File logPath)
  {
    Log<?, ?> log = logsCache.get(logPath);
    if (log == null)
    {
      // this should never happen
      logger.error(ERR_CHANGELOG_UNREFERENCED_LOG_WHILE_RELEASING.get(logPath.getPath()));
      return;
    }
    if (log.referenceCount > 1)
    {
      log.referenceCount--;
    }
    else
    {
      log.doClose();
      logsCache.remove(logPath);
    }
  }

  /**
   * Creates a new log.
   *
   * @param replicationEnv
   *            The replication environment used to create this log.
   * @param logPath
   *            The directory path of the log.
   * @param parser
   *          Parser of records.
   * @param rotationParams
   *          Parameters for log-file rotation.
   *
   * @throws ChangelogException
   *            If a problem occurs during initialization.
   */
  private Log(final ReplicationEnvironment replicationEnv, final File logPath, final RecordParser<K, V> parser,
      final LogRotationParameters rotationParams) throws ChangelogException
  {
    this.replicationEnv = replicationEnv;
    this.logPath = logPath;
    this.recordParser = parser;
    this.sizeLimitPerLogFileInBytes = rotationParams.sizeLimitPerFileInBytes;
    this.rotationIntervalInMillis = rotationParams.rotationInterval;
    this.lastRotationTime = rotationParams.lastRotationTime;

    this.referenceCount = 1;

    final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    this.exclusiveLock = lock.writeLock();
    this.sharedLock = lock.readLock();

    createOrOpenLogFiles();
  }

  /** Create or open log files used by this log. */
  private void createOrOpenLogFiles() throws ChangelogException
  {
    exclusiveLock.lock();
    try
    {
      createRootDirIfNotExists();
      openHeadLogFile();
      for (final File file : getReadOnlyLogFiles())
      {
        openReadOnlyLogFile(file);
      }
      isClosed = false;
    }
    catch (ChangelogException e)
    {
      // ensure all log files opened at this point are closed
      close();
      throw new ChangelogException(
          ERR_CHANGELOG_UNABLE_TO_INITIALIZE_LOG.get(logPath.getPath()), e);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private File[] getReadOnlyLogFiles() throws ChangelogException
  {
    File[] files = logPath.listFiles(READ_ONLY_LOG_FILES_FILTER);
    if (files == null)
    {
      throw new ChangelogException(
          ERR_CHANGELOG_UNABLE_TO_RETRIEVE_READ_ONLY_LOG_FILES_LIST.get(logPath.getPath()));
    }
    return files;
  }

  private void createRootDirIfNotExists() throws ChangelogException
  {
    if (!logPath.exists() && !logPath.mkdirs())
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_CREATE_LOG_DIRECTORY.get(logPath.getPath()));
    }
  }

  /**
   * Returns the path of this log.
   *
   * @return the path of this log directory
   */
  public File getPath()
  {
    return logPath;
  }

  /**
   * Add the provided record at the end of this log.
   * <p>
   * The record must have a key strictly higher than the key
   * of the last record added. If it is not the case, the record is not
   * appended.
   * <p>
   * In order to ensure that record is written out of buffers and persisted
   * to file system, it is necessary to explicitly call the
   * {@code syncToFileSystem()} method.
   *
   * @param record
   *          The record to add.
   * @throws ChangelogException
   *           If an error occurs while adding the record to the log.
   */
  public void append(final Record<K, V> record) throws ChangelogException
  {
    // Fast-path - assume that no rotation is needed and use shared lock.
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      LogFile<K, V> headLogFile = getHeadLogFile();
      if (!mustRotate(headLogFile))
      {
        headLogFile.append(record);
        return;
      }
    }
    finally
    {
      sharedLock.unlock();
    }

    // Slow-path - rotation is needed so use exclusive lock.
    exclusiveLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      LogFile<K, V> headLogFile = getHeadLogFile();
      if (headLogFile.appendWouldBreakKeyOrdering(record))
      {
        // abort rotation
        return;
      }
      if (mustRotate(headLogFile))
      {
        logger.trace(INFO_CHANGELOG_LOG_FILE_ROTATION.get(logPath.getPath(), headLogFile.getSizeInBytes()));

        rotateHeadLogFile();
        headLogFile = getHeadLogFile();
      }
      headLogFile.append(record);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private boolean mustRotate(LogFile<K, V> headLogFile)
  {
    if (headLogFile.getNewestRecord() == null)
    {
      // never rotate an empty file
      return false;
    }
    if (headLogFile.getSizeInBytes() > sizeLimitPerLogFileInBytes)
    {
      // rotate because file size exceeded threshold
      logger.trace("Rotate log %s due to size: %s", logPath.getPath(), headLogFile.getSizeInBytes());
      return true;
    }
    if (rotationIntervalInMillis > 0)
    {
      // rotate if time limit is reached
      final long timeElapsed = timeService.since(lastRotationTime);
      boolean shouldRotate = timeElapsed > rotationIntervalInMillis;
      if (shouldRotate)
      {
        logger.trace("Rotate log %s due to time: time elapsed %s, rotation interval: %s",
            logPath.getPath(), timeElapsed, rotationIntervalInMillis);
      }
      return shouldRotate;
    }
    return false;
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
      getHeadLogFile().syncToFileSystem();
    }
    finally
    {
      exclusiveLock.unlock();
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
  public RepositionableCursor<K, V> getCursor() throws ChangelogException
  {
    AbortableLogCursor<K, V> cursor = null;
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return new EmptyCursor<>();
      }
      cursor = new AbortableLogCursor<>(this, new InternalLogCursor<K, V>(this));
      cursor.positionTo(null, null, null);
      registerCursor(cursor);
      return cursor;
    }
    catch (ChangelogException e)
    {
      StaticUtils.close(cursor);
      throw e;
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  /**
   * Returns a cursor that allows to retrieve the records from this log,
   * starting at the position defined by the provided key.
   *
   * @param key
   *          Key to use as a start position for the cursor. If key is
   *          {@code null}, cursor will point at the first record of the log.
   * @return a cursor on the log records, which is never {@code null}
   * @throws ChangelogException
   *           If the cursor can't be created.
   */
  public RepositionableCursor<K, V> getCursor(final K key) throws ChangelogException
  {
    return getCursor(key, EQUAL_TO_KEY, ON_MATCHING_KEY);
  }

  /**
   * Returns a cursor that allows to retrieve the records from this log. The
   * starting position is defined by the provided key, cursor matching strategy
   * and cursor positioning strategy.
   *
   * @param key
   *          Key to use as a start position for the cursor. If key is
   *          {@code null}, cursor will point at the first record of the log.
   * @param matchingStrategy
   *          Cursor key matching strategy.
   * @param positionStrategy
   *          The cursor positioning strategy.
   * @return a cursor on the log records, which is never {@code null}
   * @throws ChangelogException
   *           If the cursor can't be created.
   */
  public RepositionableCursor<K, V> getCursor(final K key, final KeyMatchingStrategy matchingStrategy,
      final PositionStrategy positionStrategy) throws ChangelogException
  {
    if (key == null)
    {
      return getCursor();
    }
    AbortableLogCursor<K, V> cursor = null;
    sharedLock.lock();
    try
    {
      if (isClosed)
      {
        return new EmptyCursor<>();
      }
      cursor = new AbortableLogCursor<>(this, new InternalLogCursor<K, V>(this));
      final boolean isSuccessfullyPositioned = cursor.positionTo(key, matchingStrategy, positionStrategy);
      // Allow for cursor re-initialization after exhaustion in case of
      // LESS_THAN_OR_EQUAL_TO_KEY ands GREATER_THAN_OR_EQUAL_TO_KEY strategies
      if (isSuccessfullyPositioned || matchingStrategy != EQUAL_TO_KEY)
      {
        registerCursor(cursor);
        return cursor;
      }
      else
      {
        StaticUtils.close(cursor);
        return new EmptyCursor<>();
      }
    }
    catch (ChangelogException e)
    {
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
    sharedLock.lock();
    try
    {
      return getOldestLogFile().getOldestRecord();
    }
    finally
    {
      sharedLock.unlock();
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
    sharedLock.lock();
    try
    {
      return getHeadLogFile().getNewestRecord();
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  /**
   * Returns the number of records in the log.
   *
   * @return the number of records
   * @throws ChangelogException
   *            If a problem occurs.
   */
  public long getNumberOfRecords() throws ChangelogException
  {
    long count = 0;
    sharedLock.lock();
    try
    {
      for (final LogFile<K, V> logFile : logFiles.values())
      {
        count += logFile.getNumberOfRecords();
      }
      return count;
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  /**
   * Purge the log up to and excluding the provided key.
   *
   * @param purgeKey
   *            the key up to which purging must happen
   * @return the oldest non purged record, or {@code null}
   *         if no record was purged
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  public Record<K,V> purgeUpTo(final K purgeKey) throws ChangelogException
  {
    exclusiveLock.lock();
    try
    {
      if (isClosed)
      {
        return null;
      }
      final SortedMap<K, LogFile<K, V>> logFilesToPurge = logFiles.headMap(purgeKey);
      if (logFilesToPurge.isEmpty())
      {
        return null;
      }

      logger.trace("About to purge log files older than purgeKey %s: %s", purgeKey, logFilesToPurge);
      final List<String> undeletableFiles = new ArrayList<>();
      final Iterator<LogFile<K, V>> entriesToPurge = logFilesToPurge.values().iterator();
      while (entriesToPurge.hasNext())
      {
        final LogFile<K, V> logFile = entriesToPurge.next();
        try
        {
          abortCursorsOpenOnLogFile(logFile);
          logFile.close();
          logFile.delete();
          entriesToPurge.remove();
        }
        catch (ChangelogException e)
        {
          // The deletion of log file on file system has failed
          undeletableFiles.add(logFile.getFile().getPath());
        }
      }
      if (!undeletableFiles.isEmpty())
      {
        throw new ChangelogException(
            ERR_CHANGELOG_UNABLE_TO_DELETE_LOG_FILE_WHILE_PURGING.get(
                Utils.joinAsString(", ", undeletableFiles)));
      }
      return getOldestRecord();
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /** Abort all cursors opened on the provided log file. */
  @GuardedBy("exclusiveLock")
  private void abortCursorsOpenOnLogFile(LogFile<K, V> logFile)
  {
    for (AbortableLogCursor<K, V> cursor : openCursors)
    {
      if (cursor.isAccessingLogFile(logFile))
      {
        cursor.abort();
      }
    }
  }

  /**
   * Empties the log, discarding all records it contains.
   * <p>
   * All cursors open on the log are aborted.
   *
   * @throws ChangelogException
   *           If cursors are opened on this log, or if a problem occurs during
   *           clearing operation.
   */
  public void clear() throws ChangelogException
  {
    exclusiveLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      if (!openCursors.isEmpty())
      {
        // All open cursors are aborted, which means the change number indexer thread
        // should manage AbortedChangelogCursorException specifically to avoid being
        // stopped
        abortAllOpenCursors();
      }

      // delete all log files
      final List<String> undeletableFiles = new ArrayList<>();
      for (LogFile<K, V> logFile : logFiles.values())
      {
        try
        {
          logFile.close();
          logFile.delete();
        }
        catch (ChangelogException e)
        {
          undeletableFiles.add(logFile.getFile().getPath());
        }
      }
      if (!undeletableFiles.isEmpty())
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DELETE_LOG_FILE.get(
            Utils.joinAsString(", ", undeletableFiles)));
      }
      logFiles.clear();

      // recreate an empty head log file
      openHeadLogFile();
    }
    catch (Exception e)
    {
      throw new ChangelogException(ERR_ERROR_CLEARING_DB.get(logPath.getPath(), stackTraceToSingleLineString(e)));
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  /**
   * Dump this log as a text files, intended for debugging purpose only.
   *
   * @param dumpDirectory
   *          Directory that will contains log files with text format
   *          and ".txt" extensions
   * @throws ChangelogException
   *           If an error occurs during dump
   */
  void dumpAsTextFile(File dumpDirectory) throws ChangelogException
  {
    sharedLock.lock();
    try
    {
      for (LogFile<K, V> logFile : logFiles.values())
      {
        logFile.dumpAsTextFile(new File(dumpDirectory, logFile.getFile().getName() + ".txt"));
      }
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  @Override
  public void close()
  {
    releaseLog(logPath);
  }

  /**
   * Find the highest key that corresponds to a record that is the oldest (or
   * first) of a read-only log file and where value mapped from the record is
   * lower or equals to provided limit value.
   * <p>
   * Example<br>
   * Given a log with 3 log files, with Record<Int, String> and Mapper<String,
   * Long> mapping a string to its long value
   * <ul>
   * <li>1_10.log where oldest record is (key=1, value="50")</li>
   * <li>11_20.log where oldest record is (key=11, value="150")</li>
   * <li>head.log where oldest record is (key=25, value="250")</li>
   * </ul>
   * Then
   * <ul>
   * <li>findBoundaryKeyFromRecord(mapper, 20) => null</li>
   * <li>findBoundaryKeyFromRecord(mapper, 50) => 1</li>
   * <li>findBoundaryKeyFromRecord(mapper, 100) => 1</li>
   * <li>findBoundaryKeyFromRecord(mapper, 150) => 11</li>
   * <li>findBoundaryKeyFromRecord(mapper, 200) => 11</li>
   * <li>findBoundaryKeyFromRecord(mapper, 250) => 25</li>
   * <li>findBoundaryKeyFromRecord(mapper, 300) => 25</li>
   * </ul>
   *
   * @param <V2>
   *          Type of the value extracted from the record
   * @param mapper
   *          The mapper to extract a value from a record. It is expected that
   *          extracted values are ordered according to an order consistent with
   *          this log ordering, i.e. for two records, if key(R1) > key(R2) then
   *          extractedValue(R1) > extractedValue(R2).
   * @param limitValue
   *          The limit value to search for
   * @return the key or {@code null} if no key corresponds
   * @throws ChangelogException
   *           If a problem occurs
   */
  <V2 extends Comparable<V2>> K findBoundaryKeyFromRecord(Record.Mapper<V, V2> mapper, V2 limitValue)
      throws ChangelogException
  {
    sharedLock.lock();
    try
    {
      K key = null;
      for (LogFile<K, V> logFile : logFiles.values())
      {
        final Record<K, V> record = logFile.getOldestRecord();
        final V2 oldestValue = mapper.map(record.getValue());
        if (oldestValue.compareTo(limitValue) > 0)
        {
          return key;
        }
        key = record.getKey();
      }
      return key;
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  /** Effectively close this log. */
  private void doClose()
  {
    exclusiveLock.lock();
    try
    {
      if (isClosed)
      {
        return;
      }
      if (!openCursors.isEmpty())
      {
        logger.error(ERR_CHANGELOG_CURSOR_OPENED_WHILE_CLOSING_LOG.get(logPath.getPath(), openCursors.size()));
      }
      StaticUtils.close(logFiles.values());
      isClosed = true;
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }

  private LogFile<K, V> getHeadLogFile()
  {
    return logFiles.lastEntry().getValue();
  }

  private LogFile<K, V> getOldestLogFile()
  {
    return logFiles.firstEntry().getValue();
  }

  /**
   * Rotate the head log file to a read-only log file, and open a new empty head
   * log file to write in.
   * <p>
   * All cursors opened on this log are temporarily disabled (closing underlying resources)
   * and then re-open with their previous state.
   */
  @GuardedBy("exclusiveLock")
  private void rotateHeadLogFile() throws ChangelogException
  {
    // Temporarily disable cursors opened on head, saving their state
    final List<Pair<AbortableLogCursor<K, V>, CursorState<K, V>>> cursorsOnHead = disableOpenedCursorsOnHead();

    final LogFile<K, V> headLogFile = getHeadLogFile();
    final File readOnlyLogFile = new File(logPath, generateReadOnlyFileName(headLogFile));
    headLogFile.close();
    renameHeadLogFileTo(readOnlyLogFile);

    openHeadLogFile();
    openReadOnlyLogFile(readOnlyLogFile);

    // Re-enable cursors previously opened on head, with the saved state
    updateOpenedCursorsOnHeadAfterRotation(cursorsOnHead);

    // Notify even if time-based rotation is not enabled, as it could be enabled at any time
    replicationEnv.notifyLogFileRotation(this);
    lastRotationTime = timeService.now();
  }

  private void renameHeadLogFileTo(final File rotatedLogFile) throws ChangelogException
  {
    final File headLogFile = new File(logPath, HEAD_LOG_FILE_NAME);
    try
    {
      StaticUtils.renameFile(headLogFile, rotatedLogFile);
    }
    catch (IOException e)
    {
      throw new ChangelogException(
          ERR_CHANGELOG_UNABLE_TO_RENAME_HEAD_LOG_FILE.get(headLogFile.getPath(), rotatedLogFile.getPath()), e);
    }
  }

  /**
   * Returns the key bounds for the provided log file.
   *
   * @return the pair of (lowest key, highest key) that correspond to records
   *         stored in the corresponding log file.
   * @throws ChangelogException
   *            if an error occurs while retrieving the keys
   */
   private Pair<K, K> getKeyBounds(final LogFile<K, V> logFile) throws ChangelogException
   {
     try
     {
       final String name = logFile.getFile().getName();
       final String[] keys = name.substring(0, name.length() - Log.LOG_FILE_SUFFIX.length())
           .split(LOG_FILE_NAME_SEPARATOR);
       return Pair.of(recordParser.decodeKeyFromString(keys[0]), recordParser.decodeKeyFromString(keys[1]));
     }
     catch (Exception e)
     {
       throw new ChangelogException(
           ERR_CHANGELOG_UNABLE_TO_RETRIEVE_KEY_BOUNDS_FROM_FILE.get(logFile.getFile().getPath()), e);
     }
   }

   /**
    * Returns the file name to use for the read-only version of the provided
    * log file.
    * <p>
    * The file name is based on the lowest and highest key in the log file.
    *
    * @return the name to use for the read-only version of the log file
    * @throws ChangelogException
    *            If an error occurs.
    */
  private String generateReadOnlyFileName(final LogFile<K,V> logFile) throws ChangelogException
  {
    final K lowestKey = logFile.getOldestRecord().getKey();
    final K highestKey = logFile.getNewestRecord().getKey();
    return recordParser.encodeKeyToString(lowestKey) + LOG_FILE_NAME_SEPARATOR
       + recordParser.encodeKeyToString(highestKey) + LOG_FILE_SUFFIX;
  }

  /** Update the cursors that were pointing to head after a rotation of the head log file. */
  @GuardedBy("exclusiveLock")
  private void updateOpenedCursorsOnHeadAfterRotation(List<Pair<AbortableLogCursor<K, V>, CursorState<K, V>>> cursors)
      throws ChangelogException
  {
    for (Pair<AbortableLogCursor<K, V>, CursorState<K, V>> pair : cursors)
    {
      final CursorState<K, V> cursorState = pair.getSecond();

      // Need to update the cursor only if it is pointing to the head log file
      if (cursorState.isValid() && isHeadLogFile(cursorState.logFile))
      {
        final K previousKey = logFiles.lowerKey(recordParser.getMaxKey());
        final LogFile<K, V> logFile = findLogFileFor(previousKey, KeyMatchingStrategy.EQUAL_TO_KEY);
        final AbortableLogCursor<K, V> cursor = pair.getFirst();
        cursor.reinitializeTo(new CursorState<K, V>(logFile, cursorState.filePosition, cursorState.record));
      }
    }
  }

  @GuardedBy("exclusiveLock")
  private void abortAllOpenCursors() throws ChangelogException
  {
    for (AbortableLogCursor<K, V> cursor : openCursors)
    {
      cursor.abort();
    }
  }

  /**
   * Disable the cursors opened on the head log file log, by closing their underlying cursor.
   * Returns the state of each cursor just before the close operation.
   *
   * @return the pairs (cursor, cursor state) for each cursor pointing to head log file.
   * @throws ChangelogException
   *           If an error occurs.
   */
  @GuardedBy("exclusiveLock")
  private List<Pair<AbortableLogCursor<K, V>, CursorState<K, V>>> disableOpenedCursorsOnHead()
      throws ChangelogException
  {
    final List<Pair<AbortableLogCursor<K, V>, CursorState<K, V>>> openCursorsStates = new ArrayList<>();
    final LogFile<K, V> headLogFile = getHeadLogFile();
    for (AbortableLogCursor<K, V> cursor : openCursors)
    {
      if (cursor.isAccessingLogFile(headLogFile))
      {
        openCursorsStates.add(Pair.of(cursor, cursor.getState()));
        cursor.closeUnderlyingCursor();
      }
    }
    return openCursorsStates;
  }

  private void openHeadLogFile() throws ChangelogException
  {
    final LogFile<K, V> head = LogFile.newAppendableLogFile(new File(logPath,  HEAD_LOG_FILE_NAME), recordParser);
    logFiles.put(recordParser.getMaxKey(), head);
  }

  private void openReadOnlyLogFile(final File logFilePath) throws ChangelogException
  {
    final LogFile<K, V> logFile = LogFile.newReadOnlyLogFile(logFilePath, recordParser);
    final Pair<K, K> bounds = getKeyBounds(logFile);
    logFiles.put(bounds.getSecond(), logFile);
  }

  private void registerCursor(final AbortableLogCursor<K, V> cursor)
  {
    openCursors.add(cursor);
  }

  private void unregisterCursor(final LogCursor<K, V> cursor)
  {
    openCursors.remove(cursor);
  }

  /**
   * Returns the log file that is just after the provided log file wrt the order
   * defined on keys, or {@code null} if the provided log file is the last one
   * (the head log file).
   */
  private LogFile<K, V> getNextLogFile(final LogFile<K, V> currentLogFile) throws ChangelogException
  {
    sharedLock.lock();
    try
    {
      if (isHeadLogFile(currentLogFile))
      {
        return null;
      }
      final Pair<K, K> bounds = getKeyBounds(currentLogFile);
      return logFiles.higherEntry(bounds.getSecond()).getValue();
    }
    finally
    {
      sharedLock.unlock();
    }
  }

  private boolean isHeadLogFile(final LogFile<K, V> logFile)
  {
    return Log.HEAD_LOG_FILE_NAME.equals(logFile.getFile().getName());
  }

  @GuardedBy("sharedLock")
  private LogFile<K, V> findLogFileFor(final K key, KeyMatchingStrategy keyMatchingStrategy) throws ChangelogException
  {
    if (key == null || logFiles.lowerKey(key) == null)
    {
      return getOldestLogFile();
    }

    final LogFile<K, V> candidate = logFiles.ceilingEntry(key).getValue();
    if (KeyMatchingStrategy.LESS_THAN_OR_EQUAL_TO_KEY.equals(keyMatchingStrategy)
        && candidate.getOldestRecord().getKey().compareTo(key) > 0)
    {
      // This handle the special case where the first key of the candidate is actually greater than the expected one.
      // We have to return the previous logfile in order to match the LESS_THAN_OR_EQUAL_TO_KEY matching strategy.
      return logFiles.floorEntry(key).getValue();
    }
    return candidate;
  }

  /**
   * Represents a DB Cursor than can be repositioned on a given key.
   * <p>
   * Note that as a DBCursor, it provides a java.sql.ResultSet like API.
   */
  static interface RepositionableCursor<K extends Comparable<K>, V> extends DBCursor<Record<K, V>>
  {
    /**
     * Position the cursor to the record corresponding to the provided key and
     * provided matching and positioning strategies.
     *
     * @param key
     *          Key to use as a start position for the cursor. If key is
     *          {@code null}, use the key of the first record instead.
     * @param matchStrategy
     *          The cursor key matching strategy.
     * @param positionStrategy
     *          The cursor positioning strategy.
     * @return {@code true} if cursor is successfully positioned, or
     *         {@code false} otherwise.
     * @throws ChangelogException
     *           If an error occurs when positioning cursor.
     */
    boolean positionTo(K key, KeyMatchingStrategy matchStrategy, PositionStrategy positionStrategy)
        throws ChangelogException;
  }

  /**
   * Represents an internal view of a cursor on the log, with extended operations.
   * <p>
   * This is an abstract class rather than an interface to allow reduced visibility of the methods.
   */
  private static abstract class LogCursor<K extends Comparable<K>, V> implements RepositionableCursor<K, V>
  {
    /** Closes the underlying cursor. */
    abstract void closeUnderlyingCursor();

    /** Returns the state of this cursor. */
    abstract CursorState<K, V> getState() throws ChangelogException;

    /** Reinitialize this cursor to the provided state. */
    abstract void reinitializeTo(final CursorState<K, V> cursorState) throws ChangelogException;

    /** Returns true if cursor is pointing on provided log file. */
    abstract boolean isAccessingLogFile(LogFile<K, V> logFile);
  }

  /**
   * Implements an internal cursor on the log.
   * <p>
   * This cursor is intended to be used <b>only<b> inside an {@link AbortableLogCursor},
   * because it is relying on AbortableLogCursor for locking.
   */
  private static class InternalLogCursor<K extends Comparable<K>, V> extends LogCursor<K, V>
  {
    private final Log<K, V> log;
    private LogFile<K, V> currentLogFile;
    private LogFileCursor<K, V> currentCursor;

    /**
     * Creates a cursor on the provided log.
     *
     * @param log
     *           The log on which the cursor read records.
     * @throws ChangelogException
     *           If an error occurs when creating the cursor.
     */
    private InternalLogCursor(final Log<K, V> log) throws ChangelogException
    {
      this.log = log;
    }

    @Override
    public Record<K, V> getRecord()
    {
      return currentCursor != null ? currentCursor.getRecord() : null;
    }

    @Override
    public boolean next() throws ChangelogException
    {
      // Lock is needed here to ensure that log rotation is performed atomically.
      // This ensures that currentCursor will not be aborted concurrently.
      log.sharedLock.lock();
      try
      {
        final boolean hasNext = currentCursor.next();
        if (hasNext)
        {
          return true;
        }
        final LogFile<K, V> logFile = log.getNextLogFile(currentLogFile);
        if (logFile != null)
        {
          switchToLogFile(logFile);
          return currentCursor.next();
        }
        return false;
      }
      finally
      {
        log.sharedLock.unlock();
      }
    }

    @Override
    public void close()
    {
      StaticUtils.close(currentCursor);
    }

    @Override
    public boolean positionTo(
        final K key,
        final KeyMatchingStrategy matchStrategy,
        final PositionStrategy positionStrategy)
            throws ChangelogException
    {
      // Lock is needed here to ensure that log rotation is performed atomically.
      // This ensures that currentLogFile will not be closed concurrently.
      log.sharedLock.lock();
      try
      {
        final LogFile<K, V> logFile = log.findLogFileFor(key, matchStrategy);
        if (logFile != currentLogFile)
        {
          switchToLogFile(logFile);
        }
        return (key == null) ? true : currentCursor.positionTo(key, matchStrategy, positionStrategy);
      }
      finally
      {
        log.sharedLock.unlock();
      }
    }

    @Override
    CursorState<K, V> getState() throws ChangelogException
    {
      // Lock is needed here to ensure that log rotation is performed atomically.
      // This ensures that currentCursor will not be aborted concurrently.
      log.sharedLock.lock();
      try
      {
        return new CursorState<>(currentLogFile, currentCursor.getFilePosition(), currentCursor.getRecord());
      }
      finally
      {
        log.sharedLock.unlock();
      }
    }

    @Override
    void closeUnderlyingCursor()
    {
      StaticUtils.close(currentCursor);
    }

    /** Reinitialize this cursor to the provided state. */
    @Override
    void reinitializeTo(final CursorState<K, V> cursorState) throws ChangelogException
    {
      currentLogFile = cursorState.logFile;
      currentCursor = currentLogFile.getCursorInitialisedTo(cursorState.record, cursorState.filePosition);
    }

    @Override
    boolean isAccessingLogFile(LogFile<K, V> logFile)
    {
      return currentLogFile != null && currentLogFile.equals(logFile);
    }

    /** Switch the cursor to the provided log file. */
    private void switchToLogFile(final LogFile<K, V> logFile) throws ChangelogException
    {
      StaticUtils.close(currentCursor);
      currentLogFile = logFile;
      currentCursor = currentLogFile.getCursor();
    }

    @Override
    public String toString()
    {
      return  String.format("Cursor on log : %s, current log file: %s, current cursor: %s",
              log.logPath, currentLogFile.getFile().getName(), currentCursor);
    }
  }

  /**
   * An empty cursor, that always return null records and false to {@link #next()} method.
   * <p>
   * This class is thread-safe.
   */
  private static final class EmptyCursor<K extends Comparable<K>, V> implements RepositionableCursor<K, V>
  {
    @Override
    public Record<K,V> getRecord()
    {
      return null;
    }

    @Override
    public boolean next()
    {
      return false;
    }

    @Override
    public boolean positionTo(K key, KeyMatchingStrategy match, PositionStrategy pos) throws ChangelogException
    {
      return false;
    }

    @Override
    public void close()
    {
      // nothing to do
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName();
    }
  }

  /**
   * An aborted cursor, that throws AbortedChangelogCursorException on methods that can
   * throw a ChangelogException and returns a default value on other methods.
   * <p>
   * Although this cursor is thread-safe, it is intended to be used inside an
   * AbortableLogCursor which manages locking.
   */
  private static final class AbortedLogCursor<K extends Comparable<K>, V> extends LogCursor<K, V>
  {
    /** Records the path of the log the aborted cursor was positioned on. */
    private final File logPath;

    AbortedLogCursor(File logPath)
    {
      this.logPath = logPath;
    }

    @Override
    public Record<K,V> getRecord()
    {
      throw new IllegalStateException("this cursor is aborted");
    }

    @Override
    public boolean next() throws ChangelogException
    {
      throw abortedCursorException();
    }

    private AbortedChangelogCursorException abortedCursorException()
    {
      return new AbortedChangelogCursorException(ERR_CHANGELOG_CURSOR_ABORTED.get(logPath));
    }

    @Override
    public boolean positionTo(K key, KeyMatchingStrategy match, PositionStrategy pos) throws ChangelogException
    {
      throw abortedCursorException();
    }

    @Override
    public void close()
    {
      // nothing to do
    }

    @Override
    CursorState<K, V> getState() throws ChangelogException
    {
      throw abortedCursorException();
    }

    @Override
    void closeUnderlyingCursor()
    {
      // nothing to do
    }

    @Override
    void reinitializeTo(CursorState<K, V> cursorState) throws ChangelogException
    {
      throw abortedCursorException();
    }

    @Override
    boolean isAccessingLogFile(LogFile<K, V> logFile)
    {
      return false;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName();
    }
  }

  /**
   * A cursor on the log that can be aborted.
   * <p>
   * The cursor uses the log sharedLock to ensure no read can occur during a
   * rotation, a clear or a purge.
   * <p>
   * Note that only public methods use the sharedLock. Protected methods are intended to be used only
   * internally in the Log class when the log exclusiveLock is on.
   * <p>
   * The cursor can be be aborted by calling the {@link #abort()} method.
   */
  private static class AbortableLogCursor<K extends Comparable<K>, V> extends LogCursor<K, V>
  {
    /** The log on which this cursor is created. */
    private final Log<K, V> log;

    /** The actual cursor on which methods are delegated. */
    private LogCursor<K, V> delegate;

    /** Indicates if the cursor must be aborted. */
    private boolean mustAbort;

    private AbortableLogCursor(Log<K,V> log, LogCursor<K, V> delegate)
    {
      this.log = log;
      this.delegate = delegate;
    }

    @Override
    public Record<K, V> getRecord()
    {
      return delegate.getRecord();
    }

    @Override
    public boolean next() throws ChangelogException
    {
      // This lock is needed to ensure that abort() is atomic.
      log.sharedLock.lock();
      try
      {
        if (mustAbort)
        {
          delegate.close();
          delegate = new AbortedLogCursor<>(log.getPath());
          mustAbort = false;
        }
        return delegate.next();
      }
      finally
      {
        log.sharedLock.unlock();
      }
    }

    @Override
    public void close()
    {
      // Lock is needed here to ensure that log cursor cannot be closed while still referenced in the cursor list.
      // Removing the cursor before the close is not enough due to the CopyOnWrite nature of the cursor list.
      log.sharedLock.lock();
      try
      {
        delegate.close();
        log.unregisterCursor(this);
      }
      finally
      {
        log.sharedLock.unlock();
      }
    }

    @Override
    public boolean positionTo(K key, KeyMatchingStrategy matchStrategy, PositionStrategy positionStrategy)
        throws ChangelogException
    {
      return delegate.positionTo(key, matchStrategy, positionStrategy);
    }

    /**
     * Aborts this cursor. Once aborted, a cursor throws an
     * AbortedChangelogCursorException if it is used.
     */
    @GuardedBy("exclusiveLock")
    void abort()
    {
      mustAbort = true;
    }

    @GuardedBy("exclusiveLock")
    @Override
    CursorState<K, V> getState() throws ChangelogException
    {
      return delegate.getState();
    }

    @GuardedBy("exclusiveLock")
    @Override
    void closeUnderlyingCursor()
    {
      delegate.closeUnderlyingCursor();
    }

    @GuardedBy("exclusiveLock")
    @Override
    void reinitializeTo(final CursorState<K, V> cursorState) throws ChangelogException
    {
      delegate.reinitializeTo(cursorState);
    }

    @GuardedBy("exclusiveLock")
    @Override
    boolean isAccessingLogFile(LogFile<K, V> logFile)
    {
      return delegate.isAccessingLogFile(logFile);
    }

    @Override
    public String toString()
    {
      return delegate.toString();
    }
  }

  /**
   * Represents the state of a cursor.
   * <p>
   * The state is used to update a cursor when rotating the head log file : the
   * state of cursor on head log file must be reported to the new read-only log
   * file that is created when rotating.
   */
  private static class CursorState<K extends Comparable<K>, V>
  {
    /** The log file. */
    private final LogFile<K, V> logFile;

    /**
     * The position of the reader on the log file. It is the offset from the
     * beginning of the file, in bytes, at which the next read occurs.
     */
    private final long filePosition;

    /** The record the cursor is pointing to. */
    private final Record<K,V> record;

    private final boolean isValid;

    /** Creates a non-valid state. */
    private CursorState() {
      logFile = null;
      filePosition = 0;
      record = null;
      isValid = false;
    }

    /** Creates a valid state. */
    private CursorState(final LogFile<K, V> logFile, final long filePosition, final Record<K, V> record)
    {
      this.logFile = logFile;
      this.filePosition = filePosition;
      this.record = record;
      isValid = true;
    }

    /**
     * Indicates if this state is valid, i.e if it has non-null values.
     *
     * @return {@code true iff state is valid}
     */
    public boolean isValid()
    {
      return isValid;
    }
  }
}
