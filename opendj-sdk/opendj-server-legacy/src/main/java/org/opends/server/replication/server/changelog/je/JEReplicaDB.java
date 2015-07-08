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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.je.ReplicationDB.ReplServerDBCursor;
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
class JEReplicaDB
{

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
  private final ReplicationDB db;

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
  JEReplicaDB(final int serverId, final DN baseDN, final ReplicationServer replicationServer,
      final ReplicationDbEnv replicationEnv) throws ChangelogException
  {
    this.serverId = serverId;
    this.baseDN = baseDN;
    this.replicationServer = replicationServer;
    this.db = new ReplicationDB(serverId, baseDN, replicationServer, replicationEnv);
    this.csnLimits = new CSNLimits(db.readOldestCSN(), db.readNewestCSN());

    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  /**
   * Add an update to the list of messages that must be saved to the db managed
   * by this db handler. This method is blocking if the size of the list of
   * message is larger than its maximum.
   *
   * @param updateMsg
   *          The update message that must be saved to the db managed by this db
   *          handler.
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void add(UpdateMsg updateMsg) throws ChangelogException
  {
    if (shutdown.get())
    {
      throw new ChangelogException(
          ERR_COULD_NOT_ADD_CHANGE_TO_SHUTTING_DOWN_REPLICA_DB.get(updateMsg, baseDN, serverId));
    }

    db.addEntry(updateMsg);

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
   * Generate a new {@link DBCursor} that allows to browse the db managed by
   * this ReplicaDB and starting at the position defined by a given CSN.
   *
   * @param startCSN
   *          The position where the cursor must start. If null, start from the
   *          oldest CSN
   * @param matchingStrategy
   *          Cursor key matching strategy
   * @param positionStrategy
   *          Cursor position strategy
   * @return a new {@link DBCursor} that allows to browse the db managed by this
   *         ReplicaDB and starting at the position defined by a given CSN.
   * @throws ChangelogException
   *           if a database problem happened
   */
  DBCursor<UpdateMsg> generateCursorFrom(final CSN startCSN, final KeyMatchingStrategy matchingStrategy,
      final PositionStrategy positionStrategy) throws ChangelogException
  {
    CSN actualStartCSN = (startCSN != null && startCSN.getServerId() == serverId) ? startCSN : null;
    return new JEReplicaDBCursor(db, actualStartCSN, matchingStrategy, positionStrategy, this);
  }

  /** Shutdown this ReplicaDB. */
  void shutdown()
  {
    if (shutdown.compareAndSet(false, true))
    {
      db.shutdown();
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

    for (int i = 0; i < 100; i++)
    {
      /*
       * the purge is done by group in order to save some CPU, IO bandwidth and
       * DB caches: start the transaction then do a bunch of remove then commit.
       */
      /*
       * Matt wrote: The record removal is done as a DB transaction and the
       * deleted records are only "deleted" on commit. While the txn/cursor is
       * open the records to be deleted will, I think, be pinned in the DB
       * cache. In other words, the larger the transaction (the more records
       * deleted during a single batch) the more DB cache will be used to
       * process the transaction.
       */
      final ReplServerDBCursor cursor = db.openDeleteCursor();
      try
      {
        for (int j = 0; j < 50; j++)
        {
          if (shutdown.get())
          {
            return;
          }

          CSN csn = cursor.nextCSN();
          if (csn == null)
          {
            return;
          }

          if (!csn.equals(csnLimits.newestCSN) && csn.isOlderThan(purgeCSN))
          {
            cursor.delete();
          }
          else
          {
            csnLimits = new CSNLimits(csn, csnLimits.newestCSN);
            return;
          }
        }
      }
      catch (ChangelogException e)
      {
        // mark shutdown for this db so that we don't try again to
        // stop it from cursor.close() or methods called by cursor.close()
        cursor.abort();
        shutdown.set(true);
        throw e;
      }
      finally
      {
        cursor.close();
      }
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
        throws ConfigException,InitializationException
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
   * Clear the changes from this DB (from both memory cache and DB storage).
   * @throws ChangelogException When an exception occurs while removing the
   * changes from the DB.
   */
  void clear() throws ChangelogException
  {
    db.clear();
    csnLimits = new CSNLimits(null, null);
  }

  /**
   * Getter for the serverID of the server for which this database is managed.
   *
   * @return the serverId.
   */
  public int getServerId()
  {
    return this.serverId;
  }

  /**
   * Return the number of records of this replicaDB.
   * <p>
   * For test purpose.
   *
   * @return The number of records of this replicaDB.
   */
  long getNumberRecords()
  {
    return db.getNumberRecords();
  }

  /**
   * Set the window size for writing counter records in the DB.
   * <p>
   * for unit tests only!!
   *
   * @param size
   *          window size in number of records.
   */
  void setCounterRecordWindowSize(int size)
  {
    db.setCounterRecordWindowSize(size);
  }

}
