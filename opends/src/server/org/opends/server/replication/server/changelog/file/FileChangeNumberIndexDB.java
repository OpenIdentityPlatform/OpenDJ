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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * Logfile-based implementation of a ChangeNumberIndexDB.
 * <p>
 * This class publishes some monitoring information below <code>
 * cn=monitor</code>.
 */
class FileChangeNumberIndexDB implements ChangeNumberIndexDB
{

  private static final DebugTracer TRACER = getTracer();

  private static final int NO_KEY = 0;

  /** The parser of records stored in this ChangeNumberIndexDB. */
  static final RecordParser<Long, ChangeNumberIndexRecord> RECORD_PARSER = new ChangeNumberIndexDBParser();

  /** The log in which records are persisted. */
  private final LogFile<Long, ChangeNumberIndexRecord> logFile;

  /**
   * The newest changenumber stored in the DB. It is used to avoid purging the
   * record with the newest changenumber. The newest record in the changenumber
   * index DB is used to persist the {@link #lastGeneratedChangeNumber} which is
   * then retrieved on server startup.
   */
  private volatile long newestChangeNumber = NO_KEY;

  /**
   * The last generated value for the change number. It is kept separate from
   * the {@link #newestChangeNumber} because there is an opportunity for a race
   * condition between:
   * <ol>
   * <li>this atomic long being incremented for a new record ('recordB')</li>
   * <li>the current newest record ('recordA') being purged from the DB</li>
   * <li>'recordB' failing to be inserted in the DB</li>
   * </ol>
   */
  private final AtomicLong lastGeneratedChangeNumber;

  private final DbMonitorProvider dbMonitor = new DbMonitorProvider();

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  /**
   * Creates a new JEChangeNumberIndexDB associated to a given LDAP server.
   *
   * @param replicationEnv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  FileChangeNumberIndexDB(ReplicationEnvironment replicationEnv) throws ChangelogException
  {
    logFile = replicationEnv.getOrCreateCNIndexDB();
    final ChangeNumberIndexRecord newestRecord = readLastRecord();
    newestChangeNumber = getChangeNumber(newestRecord);
    // initialization of the lastGeneratedChangeNumber from the DB content
    // if DB is empty => last record does not exist => default to 0
    lastGeneratedChangeNumber = new AtomicLong(newestChangeNumber);

    // Monitoring registration
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  private ChangeNumberIndexRecord readLastRecord() throws ChangelogException
  {
    final Record<Long, ChangeNumberIndexRecord> record = logFile.getNewestRecord();
    return record == null ? null : record.getValue();
  }

  private ChangeNumberIndexRecord readFirstRecord() throws ChangelogException
  {
    final Record<Long, ChangeNumberIndexRecord> record = logFile.getOldestRecord();
    return record == null ? null : record.getValue();
  }

  private long getChangeNumber(final ChangeNumberIndexRecord record) throws ChangelogException
  {
    if (record != null)
    {
      return record.getChangeNumber();
    }
    return NO_KEY;
  }

  /** {@inheritDoc} */
  @Override
  public long addRecord(final ChangeNumberIndexRecord record) throws ChangelogException
  {
    final long changeNumber = nextChangeNumber();
    final ChangeNumberIndexRecord newRecord =
        new ChangeNumberIndexRecord(changeNumber, record.getPreviousCookie(), record.getBaseDN(), record.getCSN());
    logFile.addRecord(newRecord.getChangeNumber(), newRecord);
    newestChangeNumber = changeNumber;

    if (debugEnabled())
    {
      TRACER.debugInfo("In FileChangeNumberIndexDB.addRecord, added: " + newRecord);
    }
    return changeNumber;
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexRecord getOldestRecord() throws ChangelogException
  {
    return readFirstRecord();
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexRecord getNewestRecord() throws ChangelogException
  {
    return readLastRecord();
  }

  private long nextChangeNumber()
  {
    return lastGeneratedChangeNumber.incrementAndGet();
  }

  /** {@inheritDoc} */
  @Override
  public long getLastGeneratedChangeNumber()
  {
    return lastGeneratedChangeNumber.get();
  }

  /**
   * Get the number of changes.
   *
   * @return Returns the number of changes.
   * @throws ChangelogException
   *            If a problem occurs.
   */
  long count() throws ChangelogException
  {
    return logFile.getNumberOfRecords();
  }

  /**
   * Returns whether this database is empty.
   *
   * @return <code>true</code> if this database is empty, <code>false</code>
   *         otherwise
   * @throws ChangelogException
   *           if a problem occurs.
   */
  boolean isEmpty() throws ChangelogException
  {
    return getNewestRecord() == null;
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<ChangeNumberIndexRecord> getCursorFrom(final long startChangeNumber) throws ChangelogException
  {
    return new FileChangeNumberIndexDBCursor(logFile.getCursor(startChangeNumber));
  }

  /**
   * Shutdown this DB.
   */
  void shutdown()
  {
    if (shutdown.compareAndSet(false, true))
    {
      logFile.close();
      DirectoryServer.deregisterMonitorProvider(dbMonitor);
    }
  }

  /**
   * Synchronously purges the change number index DB up to and excluding the
   * provided timestamp.
   *
   * @param purgeCSN
   *          the timestamp up to which purging must happen
   * @return the oldest non purged CSN.
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  CSN purgeUpTo(final CSN purgeCSN) throws ChangelogException
  {
    if (isEmpty() || purgeCSN == null)
    {
      return null;
    }

    // TODO : no purge implemented yet as implementation is based on a single-file log.
    // The purge must be implemented once we handle a log with multiple files.
    // The purge will only delete whole files.
    return null;
  }

  /**
   * Implements the Monitoring capabilities of the FileChangeNumberIndexDB.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    /** {@inheritDoc} */
    @Override
    public List<Attribute> getMonitorData()
    {
      final List<Attribute> attributes = new ArrayList<Attribute>();
      attributes.add(createChangeNumberAttribute(true));
      attributes.add(createChangeNumberAttribute(false));
      long numberOfChanges = 0;
      try
      {
         numberOfChanges = count();
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.WARNING, e);
        }
      }
      attributes.add(Attributes.create("count", Long.toString(numberOfChanges)));
      return attributes;
    }

    private Attribute createChangeNumberAttribute(final boolean isFirst)
    {
      final String attributeName = isFirst ? "first-draft-changenumber" : "last-draft-changenumber";
      final String changeNumber = String.valueOf(getChangeNumber(isFirst));
      return Attributes.create(attributeName, changeNumber);
    }

    private long getChangeNumber(final boolean isFirst)
    {
      try
      {
        final ChangeNumberIndexRecord record = isFirst ? readFirstRecord() : readLastRecord();
        if (record != null)
        {
          return record.getChangeNumber();
        }
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.WARNING, e);
        }
      }
      return 0;
    }

    /** {@inheritDoc} */
    @Override
    public String getMonitorInstanceName()
    {
      return "ChangeNumber Index Database";
    }

    /** {@inheritDoc} */
    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
                            throws ConfigException, InitializationException
    {
      // Nothing to do for now
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + ", newestChangeNumber=" + newestChangeNumber;
  }

  /**
   * Clear the changes from this DB (from both memory cache and DB storage).
   *
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  public void clear() throws ChangelogException
  {
    logFile.clear();
    newestChangeNumber = NO_KEY;
  }

  /** Parser of records persisted in the FileChangeNumberIndex log. */
  private static class ChangeNumberIndexDBParser implements RecordParser<Long, ChangeNumberIndexRecord>
  {
    private static final byte STRING_SEPARATOR = 0;

    @Override
    public ByteString encodeRecord(final Long changeNumber, final ChangeNumberIndexRecord record)
    {
      return new ByteStringBuilder()
        .append(changeNumber)
        .append(record.getPreviousCookie())
        .append(STRING_SEPARATOR)
        .append(record.getBaseDN().toString())
        .append(STRING_SEPARATOR)
        .append(record.getCSN().toByteString()).toByteString();
    }

    @Override
    public Record<Long, ChangeNumberIndexRecord> decodeRecord(final ByteString data) throws DecodingException
    {
      try
      {
        ByteSequenceReader reader = data.asReader();
        Long changeNumber = reader.getLong();

        String previousCookie = reader.getString(getNextStringLength(reader));
        reader.skip(1);

        String baseDN = reader.getString(getNextStringLength(reader));
        reader.skip(1);
        DN dn = DN.decode(baseDN);

        ByteString csnBytes = reader.getByteString(reader.remaining());
        CSN csn = CSN.valueOf(csnBytes);

        return Record.from(changeNumber, new ChangeNumberIndexRecord(changeNumber, previousCookie, dn, csn));
      }
      catch (Exception e)
      {
        throw new DecodingException(e);
      }
    }

    /** Returns the length of next string by looking for the zero byte used as separator. */
    private int getNextStringLength(ByteSequenceReader reader)
    {
      int length = 0;
      while (reader.peek(length) != STRING_SEPARATOR)
      {
        length++;
      }
      return length;
    }
  }

}
