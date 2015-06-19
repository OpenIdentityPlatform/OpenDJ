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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import static org.opends.messages.ReplicationMessages.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
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

  /**
   * Creates a new JEChangeNumberIndexDB associated to a given LDAP server.
   *
   * @param replicationEnv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  FileChangeNumberIndexDB(ReplicationEnvironment replicationEnv) throws ChangelogException
  {
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
    public List<Attribute> getMonitorData()
    {
      long firstCN = readChangeNumber(ChangeNumberType.FIRST);
      long lastCN = readChangeNumber(ChangeNumberType.LAST);
      long numberOfChanges = lastCN == NO_KEY ? 0 : lastCN - firstCN + 1;

      final List<Attribute> attributes = new ArrayList<Attribute>();
      attributes.add(toAttribute(ChangeNumberType.FIRST, firstCN));
      attributes.add(toAttribute(ChangeNumberType.LAST, lastCN));
      attributes.add(Attributes.create("count", Long.toString(numberOfChanges)));
      return attributes;
    }

    private Attribute toAttribute(final ChangeNumberType cnType, long changeNumber)
    {
      return Attributes.create(cnType.getAttributeName(), String.valueOf(changeNumber));
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

  /** Parser of records persisted in the FileChangeNumberIndex log. */
  private static class ChangeNumberIndexDBParser implements RecordParser<Long, ChangeNumberIndexRecord>
  {
    private static final byte STRING_SEPARATOR = 0;

    @Override
    public ByteString encodeRecord(final Record<Long, ChangeNumberIndexRecord> record)
    {
      final ChangeNumberIndexRecord cnIndexRecord = record.getValue();
      return new ByteStringBuilder()
        .append((long) record.getKey())
        .append(cnIndexRecord.getBaseDN().toString())
        .append(STRING_SEPARATOR)
        .append(cnIndexRecord.getCSN().toByteString()).toByteString();
    }

    @Override
    public Record<Long, ChangeNumberIndexRecord> decodeRecord(final ByteString data) throws DecodingException
    {
      try
      {
        ByteSequenceReader reader = data.asReader();
        final long changeNumber = reader.getLong();
        final DN baseDN = DN.valueOf(reader.getString(getNextStringLength(reader)));
        reader.skip(1);
        final CSN csn = CSN.valueOf(reader.getByteString(reader.remaining()));

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
