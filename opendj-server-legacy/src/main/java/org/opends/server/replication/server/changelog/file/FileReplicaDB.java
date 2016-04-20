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
import static org.opends.server.replication.protocol.ProtocolVersion.REPLICATION_PROTOCOL_V7;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jcip.annotations.Immutable;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.file.Log.RepositionableCursor;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.CryptoManagerException;
import org.opends.server.types.InitializationException;

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
  /** Class that allows atomically setting oldest and newest CSNs without synchronization. */
  @Immutable
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
      final CryptoSuite cryptoSuite, final ReplicationEnvironment replicationEnv) throws ChangelogException
  {
    this.serverId = serverId;
    this.baseDN = baseDN;
    this.replicationServer = replicationServer;
    this.replicationEnv = replicationEnv;
    this.log = createLog(replicationEnv, cryptoSuite);
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

  private Log<CSN, UpdateMsg> createLog(final ReplicationEnvironment replicationEnv, final CryptoSuite cryptoSuite)
      throws ChangelogException
  {
    final ReplicationServerDomain domain = replicationServer.getReplicationServerDomain(baseDN, true);
    return replicationEnv.getOrCreateReplicaDB(baseDN, serverId, domain.getGenerationId(), cryptoSuite);
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
    @Override
    public MonitorData getMonitorData()
    {
      final MonitorData attributes = new MonitorData(4);
      attributes.add("replicationServer-database", serverId);
      attributes.add("domain-name", baseDN);
      final CSNLimits limits = csnLimits;
      if (limits.oldestCSN != null)
      {
        attributes.add("first-change", encode(limits.oldestCSN));
      }
      if (limits.newestCSN != null)
      {
        attributes.add("last-change", encode(limits.newestCSN));
      }
      return attributes;
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

  static ReplicaDBParser newReplicaDBParser(final CryptoSuite cryptoSuite)
  {
    return new ReplicaDBParser(cryptoSuite);
  }

  /** Parser of records persisted in the ReplicaDB log. */
  private static class ReplicaDBParser implements RecordParser<CSN, UpdateMsg>
  {
    private static final byte RECORD_VERSION = 0x01;
    private final CryptoSuite cryptoSuite;
    /** Adjusts the ByteStringBuilder capacity to avoid capacity increases (and copies) when encoding records. */
    private int encryptionOverhead;

    ReplicaDBParser(CryptoSuite cryptoSuite)
    {
      this.cryptoSuite = cryptoSuite;
    }

    @Override
    public ByteString encodeRecord(final Record<CSN, UpdateMsg> record) throws IOException
    {
      final UpdateMsg message = record.getValue();
      if (cryptoSuite.isEncrypted())
      {
        try
        {
          byte[] messageBytes = message.getBytes();
          ByteStringBuilder builder = new ByteStringBuilder(messageBytes.length + encryptionOverhead);
          builder.appendByte(UpdateMsg.MSG_TYPE_DISK_ENCODING);
          builder.appendByte(RECORD_VERSION);
          builder.appendBytes(cryptoSuite.encrypt(messageBytes));
          final int overhead = builder.length() - messageBytes.length;
          if (encryptionOverhead < overhead)
          {
            encryptionOverhead = overhead;
          }
          return builder.toByteString();
        }
        catch (GeneralSecurityException | CryptoManagerException e)
        {
          throw new IOException(e);
        }
      }
      return ByteString.wrap(message.getBytes());
    }

    @Override
    public Record<CSN, UpdateMsg> decodeRecord(final ByteString data) throws DecodingException
    {
      try
      {
        byte[] recordBytes;
        if (data.byteAt(0) == UpdateMsg.MSG_TYPE_DISK_ENCODING)
        {
          final int version = data.byteAt(1);
          if (version != RECORD_VERSION)
          {
            throw new DecodingException(ERR_UNRECOGNIZED_RECORD_VERSION.get(version));
          }
          recordBytes = cryptoSuite.decrypt(data.subSequence(2, data.length()).toByteArray());
        }
        else
        {
          recordBytes = data.toByteArray();
        }
        final UpdateMsg msg = (UpdateMsg) UpdateMsg.generateMsg(recordBytes, REPLICATION_PROTOCOL_V7);
        return Record.from(msg.getCSN(), msg);
      }
      catch (Exception e)
      {
        throw new DecodingException(e);
      }
    }

    @Override
    public CSN decodeKeyFromString(String key) throws ChangelogException
    {
      return new CSN(key);
    }

    @Override
    public String encodeKeyToString(CSN key)
    {
      return key.toString();
    }

    @Override
    public CSN getMaxKey()
    {
      return CSN.MAX_CSN_VALUE;
    }
  }
}
