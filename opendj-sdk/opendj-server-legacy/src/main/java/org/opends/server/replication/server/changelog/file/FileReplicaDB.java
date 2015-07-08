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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.file.Log.RepositionableCursor;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ReplicationMessages.*;

/**
 * Represents a replication server database for one server in the topology.
 * <p>
 * It is responsible for efficiently saving the updates that is received from
 * each master server into stable storage.
 * <p>
 * It is also able to generate a {@link DBCursor} that can be used to
 * read all changes from a given {@link CSN}.
 * <p>
 * It publishes some monitoring information below cn=monitor.
 */
class FileReplicaDB
{

  /** The parser of records stored in Replica DB. */
  static final RecordParser<CSN, UpdateMsg> RECORD_PARSER = new ReplicaDBParser();

  /**
   * Class that allows atomically setting oldest and newest CSNs without
   * synchronization.
   *
   * @Immutable
   */
  private static final class CSNLimits
  {
    private final CSN oldestCSN;
    private final CSN newestCSN;

    public CSNLimits(CSN oldestCSN, CSN newestCSN)
    {
      this.oldestCSN = oldestCSN;
      this.newestCSN = newestCSN;
    }
  }

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  /** The log in which records are persisted. */
  private final Log<CSN, UpdateMsg> log;

  /**
   * Holds the oldest and newest CSNs for this replicaDB for fast retrieval.
   *
   * @NonNull
   */
  private volatile CSNLimits csnLimits;
  private final int serverId;
  private final DN baseDN;
  private final DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private final ReplicationServer replicationServer;
  private final ReplicationEnvironment replicationEnv;

  /**
   * Creates a new ReplicaDB associated to a given LDAP server.
   *
   * @param serverId
   *          Id of this server.
   * @param baseDN
   *          the replication domain baseDN.
   * @param replicationServer
   *          The ReplicationServer that creates this ReplicaDB.
   * @param replicationEnv
   *          the Database Env to use to create the ReplicationServer DB. server
   *          for this domain.
   * @throws ChangelogException
   *           If a database problem happened
   */
  FileReplicaDB(final int serverId, final DN baseDN, final ReplicationServer replicationServer,
      final ReplicationEnvironment replicationEnv) throws ChangelogException
  {
    this.serverId = serverId;
    this.baseDN = baseDN;
    this.replicationServer = replicationServer;
    this.replicationEnv = replicationEnv;
    this.log = createLog(replicationEnv);
    this.csnLimits = new CSNLimits(readOldestCSN(), readNewestCSN());

    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  private CSN readOldestCSN() throws ChangelogException
  {
    final Record<CSN, UpdateMsg> record = log.getOldestRecord();
    return record == null ? null : record.getKey();
  }

  private CSN readNewestCSN() throws ChangelogException
  {
    final Record<CSN, UpdateMsg> record = log.getNewestRecord();
    return record == null ? null : record.getKey();
  }

  private Log<CSN, UpdateMsg> createLog(final ReplicationEnvironment replicationEnv) throws ChangelogException
  {
    final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);
    return replicationEnv.getOrCreateReplicaDB(baseDN, serverId, domain.getGenerationId());
  }

  /**
   * Adds a new message.
   *
   * @param updateMsg
   *          The update message to add.
   * @throws ChangelogException
   *           If an error occurs when trying to add the message.
   */
  void add(final UpdateMsg updateMsg) throws ChangelogException
  {
    if (shutdown.get())
    {
      throw new ChangelogException(
          ERR_COULD_NOT_ADD_CHANGE_TO_SHUTTING_DOWN_REPLICA_DB.get(updateMsg
              .toString(), String.valueOf(baseDN), String.valueOf(serverId)));
    }

    log.append(Record.from(updateMsg.getCSN(), updateMsg));

    final CSNLimits limits = csnLimits;
    final boolean updateNew = limits.newestCSN == null || limits.newestCSN.isOlderThan(updateMsg.getCSN());
    final boolean updateOld = limits.oldestCSN == null;
    if (updateOld || updateNew)
    {
      csnLimits = new CSNLimits(
          updateOld ? updateMsg.getCSN() : limits.oldestCSN,
          updateNew ? updateMsg.getCSN() : limits.newestCSN);
    }
  }

  /**
   * Get the oldest CSN that has not been purged yet.
   *
   * @return the oldest CSN that has not been purged yet.
   */
  CSN getOldestCSN()
  {
    return csnLimits.oldestCSN;
  }

  /**
   * Get the newest CSN that has not been purged yet.
   *
   * @return the newest CSN that has not been purged yet.
   */
  CSN getNewestCSN()
  {
    return csnLimits.newestCSN;
  }

  /**
   * Returns a cursor that allows to retrieve the update messages from this DB.
   * The actual starting position is defined by the provided CSN, the key
   * matching strategy and the positioning strategy.
   *
   * @param startCSN
   *          The position where the cursor must start. If null, start from the
   *          oldest CSN
   * @param matchingStrategy
   *          Cursor key matching strategy
   * @param positionStrategy
   *          Cursor position strategy
   * @return a new {@link DBCursor} to retrieve update messages.
   * @throws ChangelogException
   *           if a database problem happened
   */
  DBCursor<UpdateMsg> generateCursorFrom(final CSN startCSN, final KeyMatchingStrategy matchingStrategy,
      final PositionStrategy positionStrategy) throws ChangelogException
  {
    RepositionableCursor<CSN, UpdateMsg> cursor = log.getCursor(startCSN, matchingStrategy, positionStrategy);
    CSN actualStartCSN = (startCSN != null && startCSN.getServerId() == serverId) ? startCSN : null;
    return new FileReplicaDBCursor(cursor, actualStartCSN, positionStrategy);
  }

  /** Shutdown this ReplicaDB. */
  void shutdown()
  {
    if (shutdown.compareAndSet(false, true))
    {
      log.close();
      DirectoryServer.deregisterMonitorProvider(dbMonitor);
    }
  }

  /**
   * Synchronously purge changes older than purgeCSN from this replicaDB.
   *
   * @param purgeCSN
   *          The CSN up to which changes can be purged. No purging happens when
   *          it is {@code null}.
   * @throws ChangelogException
   *           In case of database problem.
   */
  void purgeUpTo(final CSN purgeCSN) throws ChangelogException
  {
    if (purgeCSN == null)
    {
      return;
    }
    final Record<CSN, UpdateMsg> oldestRecord = log.purgeUpTo(purgeCSN);
    if (oldestRecord != null)
    {
      csnLimits = new CSNLimits(oldestRecord.getKey(), csnLimits.newestCSN);
    }
  }

  /**
   * Implements monitoring capabilities of the ReplicaDB.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    /** {@inheritDoc} */
    @Override
    public List<Attribute> getMonitorData()
    {
      final List<Attribute> attributes = new ArrayList<>();
      create(attributes, "replicationServer-database",String.valueOf(serverId));
      create(attributes, "domain-name", baseDN.toString());
      final CSNLimits limits = csnLimits;
      if (limits.oldestCSN != null)
      {
        create(attributes, "first-change", encode(limits.oldestCSN));
      }
      if (limits.newestCSN != null)
      {
        create(attributes, "last-change", encode(limits.newestCSN));
      }
      return attributes;
    }

    private void create(final List<Attribute> attributes, final String name, final String value)
    {
      attributes.add(Attributes.create(name, value));
    }

    private String encode(final CSN csn)
    {
      return csn + " " + new Date(csn.getTime());
    }

    /** {@inheritDoc} */
    @Override
    public String getMonitorInstanceName()
    {
      ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN);
      return "Changelog for DS(" + serverId + "),cn=" + domain.getMonitorInstanceName();
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
    final CSNLimits limits = csnLimits;
    return getClass().getSimpleName() + " " + baseDN + " " + serverId + " "
        + limits.oldestCSN + " " + limits.newestCSN;
  }

  /**
   * Clear the changes from this DB, from both memory cache and persistent
   * storage.
   *
   * @throws ChangelogException
   *           If an error occurs while removing the changes from the DB.
   */
  void clear() throws ChangelogException
  {
    // Remove all persisted data and reset generationId to default value
    log.clear();
    replicationEnv.resetGenerationId(baseDN);

    csnLimits = new CSNLimits(null, null);
  }

  /**
   * Return the number of records of this replicaDB.
   * <p>
   * For test purpose.
   *
   * @return The number of records of this replicaDB.
   * @throws ChangelogException
   *            If an error occurs
   */
  long getNumberRecords() throws ChangelogException
  {
    return log.getNumberOfRecords();
  }

  /**
   * Dump this DB as text files, intended for debugging purpose only.
   *
   * @throws ChangelogException
   *           If an error occurs during dump
   */
  void dumpAsTextFiles() throws ChangelogException {
    log.dumpAsTextFile(log.getPath());
  }

  /** Parser of records persisted in the ReplicaDB log. */
  private static class ReplicaDBParser implements RecordParser<CSN, UpdateMsg>
  {

    @Override
    public ByteString encodeRecord(final Record<CSN, UpdateMsg> record)
    {
      final UpdateMsg message = record.getValue();
      return ByteString.wrap(message.getBytes());
    }

    @Override
    public Record<CSN, UpdateMsg> decodeRecord(final ByteString data) throws DecodingException
    {
      try
      {
        final UpdateMsg msg =
            (UpdateMsg) UpdateMsg.generateMsg(data.toByteArray(), ProtocolVersion.REPLICATION_PROTOCOL_V7);
        return Record.from(msg.getCSN(), msg);
      }
      catch (Exception e)
      {
        throw new DecodingException(e);
      }
    }

    /** {@inheritDoc} */
    @Override
    public CSN decodeKeyFromString(String key) throws ChangelogException
    {
      return new CSN(key);
    }

    /** {@inheritDoc} */
    @Override
    public String encodeKeyToString(CSN key)
    {
      return key.toString();
    }

    /** {@inheritDoc} */
    @Override
    public CSN getMaxKey()
    {
      return CSN.MAX_CSN_VALUE;
    }
  }

}
