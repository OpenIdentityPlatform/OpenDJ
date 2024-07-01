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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

/**
 * Implementation of a ChangeNumberIndexDB with a log.
 * <p>
 * This class publishes some monitoring information below <code>
 * cn=monitor</code>.
 */
class FileChangeNumberIndexDB implements ChangeNumberIndexDB
{
  /** The type of the required change number. */
  private static enum ChangeNumberType
  {
    FIRST("first-draft-changenumber"), LAST("last-draft-changenumber");

    private final String attrName;

    private ChangeNumberType(String attrName)
    {
      this.attrName = attrName;
    }

    private String getAttributeName()
    {
      return this.attrName;
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final int NO_KEY = 0;
  private static final Record.Mapper<ChangeNumberIndexRecord, CSN> MAPPER_TO_CSN =
      new Record.Mapper<ChangeNumberIndexRecord, CSN>()
      {
        @Override
        public CSN map(ChangeNumberIndexRecord value)
        {
          return value.getCSN();
        }
      };
  /** The parser of records stored in this ChangeNumberIndexDB. */
  static final RecordParser<Long, ChangeNumberIndexRecord> RECORD_PARSER = new ChangeNumberIndexDBParser();

  /** The log in which records are persisted. */
  private final Log<Long, ChangeNumberIndexRecord> log;

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

  private final ReentrantReadWriteLock resetCNisRunningLock = new ReentrantReadWriteLock(false);

  private final FileChangelogDB changelogDB;

  /**
   * Creates a new JEChangeNumberIndexDB associated to a given LDAP server.
   *
   * @param replicationEnv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  FileChangeNumberIndexDB(FileChangelogDB changelogDB, ReplicationEnvironment replicationEnv) throws ChangelogException
  {
    this.changelogDB = changelogDB;
    log = replicationEnv.getOrCreateCNIndexDB();
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
    final Record<Long, ChangeNumberIndexRecord> record = log.getNewestRecord();
    return record == null ? null : record.getValue();
  }

  private ChangeNumberIndexRecord readFirstRecord() throws ChangelogException
  {
    final Record<Long, ChangeNumberIndexRecord> record = log.getOldestRecord();
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
        new ChangeNumberIndexRecord(changeNumber, record.getBaseDN(), record.getCSN());
    log.append(Record.from(newRecord.getChangeNumber(), newRecord));
    newestChangeNumber = changeNumber;

    if (logger.isTraceEnabled())
    {
      logger.trace("In FileChangeNumberIndexDB.addRecord, added: " + newRecord);
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
    resetCNisRunningLock.readLock().lock();
    try {
      long lgcn = lastGeneratedChangeNumber.incrementAndGet();
      return lgcn;
    }
    finally
    {
      resetCNisRunningLock.readLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getLastGeneratedChangeNumber()
  {
    resetCNisRunningLock.readLock().lock();
    try {
      long lgcn = lastGeneratedChangeNumber.get();
      return lgcn;
    }
    finally
    {
      resetCNisRunningLock.readLock().unlock();
    }
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
    return log.getNumberOfRecords();
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
    return new FileChangeNumberIndexDBCursor(log.getCursor(startChangeNumber));
  }

  /**
   * Shutdown this DB.
   */
  void shutdown()
  {
    if (shutdown.compareAndSet(false, true))
    {
      log.close();
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
    // Retrieve the oldest change number that must not be purged
    final Long purgeChangeNumber = log.findBoundaryKeyFromRecord(MAPPER_TO_CSN, purgeCSN);
    if (purgeChangeNumber != null)
    {
      final Record<Long, ChangeNumberIndexRecord> record = log.purgeUpTo(purgeChangeNumber);
      return record != null ? record.getValue().getCSN() : null;
    }
    return null;
  }

  /** Implements the Monitoring capabilities of the FileChangeNumberIndexDB. */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    @Override
    public MonitorData getMonitorData()
    {
      long firstCN = readChangeNumber(ChangeNumberType.FIRST);
      long lastCN = readChangeNumber(ChangeNumberType.LAST);
      long numberOfChanges = lastCN == NO_KEY ? 0 : lastCN - firstCN + 1;

      final MonitorData attributes = new MonitorData(3);
      attributes.add(ChangeNumberType.FIRST.getAttributeName(), firstCN);
      attributes.add(ChangeNumberType.LAST.getAttributeName(), lastCN);
      attributes.add("count", numberOfChanges);
      return attributes;
    }

    private long readChangeNumber(final ChangeNumberType type)
    {
      try
      {
        return getChangeNumber(readChangeNumber0(type));
      }
      catch (ChangelogException e)
      {
        logger.traceException(e);
        return NO_KEY;
      }
    }

    private ChangeNumberIndexRecord readChangeNumber0(final ChangeNumberType type) throws ChangelogException
    {
      if (ChangeNumberType.FIRST.equals(type))
      {
        return readFirstRecord();
      }
      else if (ChangeNumberType.LAST.equals(type))
      {
        return readLastRecord();
      }
      throw new IllegalArgumentException("Not implemented for ChangeNumber: " + type);
    }

    @Override
    public String getMonitorInstanceName()
    {
      return "ChangeNumber Index Database";
    }

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
    log.clear();
    newestChangeNumber = NO_KEY;
  }

  /**
   * Same as {@code clear()}, with the addition of also resetting last GeneratedChangeNumber counter to the provided
   * value.
   * @param newStart
   *            the new changeNumber for for the first change in the changelog
   */
  public void clearAndSetChangeNumber(long newStart) throws ChangelogException
  {
    resetCNisRunningLock.writeLock().lock();
    try{
      clear();
      lastGeneratedChangeNumber.set(newStart - 1);
    }
    finally
    {
      resetCNisRunningLock.writeLock().unlock();
    }
  }

  @Override
  public void resetChangeNumberTo(long newFirstCN, DN baseDN, CSN newFirstCSN) throws ChangelogException
  {
    changelogDB.resetChangeNumberIndex(newFirstCN, baseDN, newFirstCSN);
  }

  /** Parser of records persisted in the FileChangeNumberIndex log. */
  private static class ChangeNumberIndexDBParser implements RecordParser<Long, ChangeNumberIndexRecord>
  {
    private static final byte STRING_SEPARATOR = 0;

    @Override
    public ByteString encodeRecord(final Record<Long, ChangeNumberIndexRecord> record) throws IOException
    {
      final ChangeNumberIndexRecord cnIndexRecord = record.getValue();
      return new ByteStringBuilder()
        .appendLong(record.getKey())
        .appendUtf8(cnIndexRecord.getBaseDN().toString())
        .appendByte(STRING_SEPARATOR)
        .appendBytes(cnIndexRecord.getCSN().toByteString())
        .toByteString();
    }

    @Override
    public Record<Long, ChangeNumberIndexRecord> decodeRecord(final ByteString data) throws DecodingException
    {
      try
      {
        ByteSequenceReader reader = data.asReader();
        final long changeNumber = reader.readLong();
        final DN baseDN = DN.valueOf(reader.readStringUtf8(getNextStringLength(reader)));
        reader.skip(1);
        final CSN csn = CSN.valueOf(reader.readByteString(reader.remaining()));

        return Record.from(changeNumber, new ChangeNumberIndexRecord(changeNumber, baseDN, csn));
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

    /** {@inheritDoc} */
    @Override
    public Long decodeKeyFromString(String key) throws ChangelogException
    {
      try
      {
        return Long.valueOf(key);
      }
      catch (NumberFormatException e)
      {
        throw new ChangelogException(
            ERR_CHANGELOG_UNABLE_TO_DECODE_KEY_FROM_STRING.get(key), e);
      }
    }

    /** {@inheritDoc} */
    @Override
    public String encodeKeyToString(Long key)
    {
      return key.toString();
    }

    /** {@inheritDoc} */
    @Override
    public Long getMaxKey()
    {
      return Long.MAX_VALUE;
    }
  }

}
