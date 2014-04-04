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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.je.ReplicationDB.ReplServerDBCursor;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.util.TimeThread;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class is used for managing the replicationServer database for each
 * server in the topology.
 * <p>
 * It is responsible for efficiently saving the updates that is received from
 * each master server into stable storage.
 * <p>
 * This class is also able to generate a {@link DBCursor} that can be used to
 * read all changes from a given {@link CSN}.
 * <p>
 * This class publish some monitoring information below cn=monitor.
 */
public class JEReplicaDB implements Runnable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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

  /**
   * The msgQueue holds all the updates not yet saved to stable storage.
   * <p>
   * This blocking queue is only used as a temporary placeholder so that the
   * write in the stable storage can be grouped for efficiency reason. Adding an
   * update synchronously add the update to this list. A dedicated thread loops
   * on {@link #flush()} and {@link #trim()}.
   * <dl>
   * <dt>flush()</dt>
   * <dd>get a number of changes from the in memory list by block and write them
   * to the db.</dd>
   * <dt>trim()</dt>
   * <dd>deletes from the DB a number of changes that are older than a certain
   * date.</dd>
   * </dl>
   * <p>
   * Changes are not read back by replicationServer threads that are responsible
   * for pushing the changes to other replication server or to LDAP server
   */
  private final LinkedBlockingQueue<UpdateMsg> msgQueue =
    new LinkedBlockingQueue<UpdateMsg>();

  /**
   * Semaphore used to limit the number of bytes used in memory by the queue.
   * The threads calling {@link #add(UpdateMsg)} method will be blocked if the
   * size of msgQueue becomes larger than the available permits and will resume
   * only when the number of available permits allow it.
   */
  private final Semaphore queueSizeBytes;
  private final int queueMaxBytes;

  private ReplicationDB db;
  /**
   * Holds the oldest and newest CSNs for this replicaDB for fast retrieval.
   *
   * @NonNull
   */
  private volatile CSNLimits csnLimits;
  private int serverId;
  private DN baseDN;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private DirectoryThread thread;
  /**
   * Used to prevent race conditions between threads calling {@link #clear()}
   * {@link #flush()} or {@link #trim()}. This can happen with the thread
   * flushing the queue, on shutdown or on cursor opening, a thread calling
   * clear(), etc.
   */
  private final Object flushLock = new Object();
  private ReplicationServer replicationServer;

  private long latestTrimDate = 0;

  /**
   * The trim age in milliseconds. Changes record in the change DB that
   * are older than this age are removed.
   */
  private long trimAge;

  /**
   * Creates a new ReplicaDB associated to a given LDAP server.
   *
   * @param serverId The serverId for which changes will be stored in the DB.
   * @param baseDN the baseDN for which this DB was created.
   * @param replicationServer The ReplicationServer that creates this ReplicaDB.
   * @param dbenv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  public JEReplicaDB(int serverId, DN baseDN,
      ReplicationServer replicationServer, ReplicationDbEnv dbenv)
      throws ChangelogException
  {
    this.replicationServer = replicationServer;
    this.serverId = serverId;
    this.baseDN = baseDN;
    trimAge = replicationServer.getTrimAge();
    queueMaxBytes = replicationServer.getQueueSize() * 200;
    queueSizeBytes = new Semaphore(queueMaxBytes);
    db = new ReplicationDB(serverId, baseDN, replicationServer, dbenv);
    csnLimits = new CSNLimits(db.readOldestCSN(), db.readNewestCSN());
    thread = new DirectoryThread(this, "Replication server RS("
        + replicationServer.getServerId()
        + ") changelog checkpointer for Replica DS(" + serverId
        + ") for domain \"" + baseDN + "\"");
    thread.start();

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
    if (thread.isShutdownInitiated())
    {
      throw new ChangelogException(
          ERR_COULD_NOT_ADD_CHANGE_TO_SHUTTING_DOWN_REPLICA_DB.get(updateMsg, baseDN, serverId));
    }

    final int msgSize = updateMsg.size();
    if (msgSize < queueMaxBytes)
    {
      try
      {
        queueSizeBytes.acquire(msgSize);
      }
      catch (InterruptedException e)
      {
        throw new ChangelogException(
            ERR_EXCEPTION_COULD_NOT_ADD_CHANGE_TO_REPLICA_DB.get(updateMsg, baseDN, serverId,
                stackTraceToSingleLineString(e)));
      }
    }
    else
    {
      // edge case with a very large message
      collectAllPermits();
    }
    msgQueue.add(updateMsg);

    final CSNLimits limits = csnLimits;
    final boolean updateNew = limits.newestCSN == null
        || limits.newestCSN.isOlderThan(updateMsg.getCSN());
    final boolean updateOld = limits.oldestCSN == null;
    if (updateOld || updateNew)
    {
      csnLimits = new CSNLimits(
          updateOld ? updateMsg.getCSN() : limits.oldestCSN,
          updateNew ? updateMsg.getCSN() : limits.newestCSN);
    }
  }

  /** Collects all the permits from the {@link #queueSizeBytes} semaphore. */
  private void collectAllPermits()
  {
    int collectedPermits = queueSizeBytes.drainPermits();
    while (collectedPermits != queueMaxBytes)
    {
      // Do not use Thread.sleep() because:
      // 1) it is expected the permits will be released very soon
      // 2) we want to collect all the permits, so do not leave a chance to
      // other threads to steal them from us.
      // 3) we want to keep low latency
      Thread.yield();
      collectedPermits += queueSizeBytes.drainPermits();
    }
  }

  /**
   * Get the oldest CSN that has not been purged yet.
   *
   * @return the oldest CSN that has not been purged yet.
   */
  public CSN getOldestCSN()
  {
    return csnLimits.oldestCSN;
  }

  /**
   * Get the newest CSN that has not been purged yet.
   *
   * @return the newest CSN that has not been purged yet.
   */
  public CSN getNewestCSN()
  {
    return csnLimits.newestCSN;
  }

  /**
   * Get the number of changes.
   *
   * @return Returns the number of changes.
   */
  public long getChangesCount()
  {
    final CSNLimits limits = csnLimits;
    if (limits.newestCSN != null && limits.oldestCSN != null)
    {
      return limits.newestCSN.getSeqnum() - limits.oldestCSN.getSeqnum() + 1;
    }
    return 0;
  }

  /**
   * Generate a new {@link DBCursor} that allows to browse the db managed by
   * this ReplicaDB and starting at the position defined by a given CSN.
   *
   * @param startAfterCSN
   *          The position where the cursor must start. If null, start from the
   *          oldest CSN
   * @return a new {@link DBCursor} that allows to browse the db managed by this
   *         ReplicaDB and starting at the position defined by a given CSN.
   * @throws ChangelogException
   *           if a database problem happened
   */
  public DBCursor<UpdateMsg> generateCursorFrom(CSN startAfterCSN)
      throws ChangelogException
  {
    return new JEReplicaDBCursor(db, startAfterCSN, this);
  }

  /**
   * Shutdown this ReplicaDB.
   */
  public void shutdown()
  {
    if (thread.isShutdownInitiated())
    {
      return;
    }

    thread.initiateShutdown();

    while (msgQueue.size() != 0)
    {
      try
      {
        flush();
      }
      catch (ChangelogException e)
      {
        // We are already shutting down
        logger.error(e.getMessageObject());
      }
    }

    db.shutdown();
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
  }

  /**
   * Run method for this class.
   * Periodically Flushes the ReplicationServerDomain cache from memory to the
   * stable storage and trims the old updates.
   */
  @Override
  public void run()
  {
    thread.startWork();

    try
    {
      while (!thread.isShutdownInitiated())
      {
        try
        {
          flush();
          trim();
        }
        catch (ChangelogException end)
        {
          stop(end);
          break;
        }
      }

      try
      {
        // call flush a last time before exiting to make sure that
        // no change was forgotten in the msgQueue
        flush();
      }
      catch (ChangelogException e)
      {
        stop(e);
      }
    }
    finally
    {
      thread.stopWork();
    }
  }

  private void stop(Exception e)
  {
    logger.error(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH, stackTraceToSingleLineString(e));

    thread.initiateShutdown();

    if (replicationServer != null)
    {
      replicationServer.shutdown();
    }
  }

  /**
   * Retrieves the latest trim date.
   * @return the latest trim date.
   */
  public long getLatestTrimDate()
  {
    return latestTrimDate;
  }


  /**
   * Trim old changes from this replicationServer database.
   * @throws ChangelogException In case of database problem.
   */
  private void trim() throws ChangelogException
  {
    if (trimAge == 0)
    {
      return;
    }

    latestTrimDate = TimeThread.getTime() - trimAge;

    CSN trimDate = new CSN(latestTrimDate, 0, 0);

    // Find the last CSN before the trimDate, in the Database.
    CSN lastBeforeTrimDate = db.getPreviousCSN(trimDate);
    if (lastBeforeTrimDate != null)
    {
      // If we found it, we want to stop trimming when reaching it.
      trimDate = lastBeforeTrimDate;
    }

    for (int i = 0; i < 100; i++)
    {
      /*
       * Perform at least some trimming regardless of the flush backlog. Then
       * continue trim iterations while the flush backlog is low (below the
       * lowmark). Once the flush backlog increases, stop trimming and start
       * flushing more eagerly.
       */
      if (i > 20 && isQueueAboveLowMark())
      {
        break;
      }

      /*
       * the trim is done by group in order to save some CPU, IO bandwidth and
       * DB caches: start the transaction then do a bunch of remove then
       * commit.
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
          if (thread.isShutdownInitiated())
          {
            return;
          }

          CSN csn = cursor.nextCSN();
          if (csn == null)
          {
            return;
          }

          if (!csn.equals(csnLimits.newestCSN) && csn.isOlderThan(trimDate))
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
        thread.initiateShutdown();
        throw e;
      }
      finally
      {
        cursor.close();
      }
    }
  }

  private boolean isQueueAboveLowMark()
  {
    final int lowMarkBytes = queueMaxBytes / 5;
    final int bytesUsed = queueMaxBytes - queueSizeBytes.availablePermits();
    return bytesUsed > lowMarkBytes;
  }

  /**
   * Flush a number of updates from the memory list to the stable storage.
   * <p>
   * Flush is done by chunk sized to 500 messages, starting from the beginning
   * of the list.
   *
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void flush() throws ChangelogException
  {
    try
    {
      synchronized (flushLock)
      {
        final List<UpdateMsg> changes = new LinkedList<UpdateMsg>();
        final UpdateMsg change = msgQueue.poll(500, TimeUnit.MILLISECONDS);
        if (change == null)
        {
          // nothing to persist, move on to the trim phase
          return;
        }

        // Try to see if there are more changes and persist them all.
        changes.add(change);
        msgQueue.drainTo(changes);

        int totalSize = db.addEntries(changes);
        // do not release more than queue max size permits
        // (be careful of the edge case with the very large message)
        queueSizeBytes.release(Math.min(totalSize, queueMaxBytes));
      }
    }
    catch (InterruptedException e)
    {
      throw new ChangelogException(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH
          .get(stackTraceToSingleLineString(e)));
    }
  }

  /**
   * This internal class is used to implement the Monitoring capabilities of the
   * ReplicaDB.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    /** {@inheritDoc} */
    @Override
    public List<Attribute> getMonitorData()
    {
      List<Attribute> attributes = new ArrayList<Attribute>();
      create(attributes, "replicationServer-database",String.valueOf(serverId));
      create(attributes, "domain-name", baseDN.toNormalizedString());
      final CSNLimits limits = csnLimits;
      if (limits.oldestCSN != null)
      {
        create(attributes, "first-change", encode(limits.oldestCSN));
      }
      if (limits.newestCSN != null)
      {
        create(attributes, "last-change", encode(limits.newestCSN));
      }
      create(attributes, "queue-size", String.valueOf(msgQueue.size()));
      create(attributes, "queue-size-bytes",
          String.valueOf(queueMaxBytes - queueSizeBytes.availablePermits()));
      return attributes;
    }

    private void create(List<Attribute> attributes, String name, String value)
    {
      attributes.add(Attributes.create(name, value));
    }

    private String encode(CSN csn)
    {
      return csn + " " + new Date(csn.getTime());
    }

    /** {@inheritDoc} */
    @Override
    public String getMonitorInstanceName()
    {
      ReplicationServerDomain domain = replicationServer
          .getReplicationServerDomain(baseDN);
      return "Changelog for DS(" + serverId + "),cn="
          + domain.getMonitorInstanceName();
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
   * Set the Purge delay for this db Handler.
   * @param delay The purge delay in Milliseconds.
   */
  public void setPurgeDelay(long delay)
  {
    trimAge = delay;
  }

  /**
   * Clear the changes from this DB (from both memory cache and DB storage).
   * @throws ChangelogException When an exception occurs while removing the
   * changes from the DB.
   */
  public void clear() throws ChangelogException
  {
    collectAllPermits();
    msgQueue.clear(); // this call should not do anything at all
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
   * Return the size of the msgQueue (the memory cache of the ReplicaDB).
   * For test purpose.
   * @return The memory queue size.
   */
  int getQueueSize()
  {
    return this.msgQueue.size();
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
