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

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.je.ReplicationDB.*;
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
   * The msgQueue holds all the updates not yet saved to stable storage.
   * <p>
   * This list is only used as a temporary placeholder so that the write in the
   * stable storage can be grouped for efficiency reason. Adding an update
   * synchronously add the update to this list. A dedicated thread loops on
   * flush() and trim().
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
  private final LinkedList<UpdateMsg> msgQueue =
    new LinkedList<UpdateMsg>();

  /**
   * The High and low water mark for the max size of the msgQueue. The threads
   * calling add() method will be blocked if the size of msgQueue becomes larger
   * than the queueHimark and will resume only when the size of the msgQueue
   * goes below queueLowmark.
   */
  private int queueMaxSize = 5000;
  private int queueLowmark = 1000;
  private int queueHimark = 4000;

  /**
   * The queue himark and lowmark in bytes, this is set to 100 times the himark
   * and lowmark in number of updates.
   */
  private int queueMaxBytes = 100 * queueMaxSize;
  private int queueLowmarkBytes = 100 * queueLowmark;
  private int queueHimarkBytes = 100 * queueHimark;

  /** The number of bytes currently in the queue. */
  private int queueByteSize = 0;

  private ReplicationDB db;
  private CSN oldestCSN;
  private CSN newestCSN;
  private int serverId;
  private DN baseDN;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private DirectoryThread thread;
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
    final int queueSize = replicationServer.getQueueSize();
    queueMaxSize = queueSize;
    queueLowmark = queueSize / 5;
    queueHimark = queueSize * 4 / 5;
    queueMaxBytes = 200 * queueMaxSize;
    queueLowmarkBytes = 200 * queueLowmark;
    queueHimarkBytes = 200 * queueLowmark;
    db = new ReplicationDB(serverId, baseDN, replicationServer, dbenv);
    oldestCSN = db.readOldestCSN();
    newestCSN = db.readNewestCSN();
    thread = new DirectoryThread(this, "Replication server RS("
        + replicationServer.getServerId()
        + ") changelog checkpointer for Replica DS(" + serverId
        + ") for domain \"" + baseDN + "\"");
    thread.start();

    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  /**
   * Add an update to the list of messages that must be saved to the db
   * managed by this db handler.
   * This method is blocking if the size of the list of message is larger
   * than its maximum.
   *
   * @param update The update that must be saved to the db managed by this db
   *               handler.
   */
  public void add(UpdateMsg update)
  {
    synchronized (msgQueue)
    {
      int size = msgQueue.size();
      if (size > queueHimark || queueByteSize > queueHimarkBytes)
      {
        msgQueue.notify();
      }

      while (size > queueMaxSize || queueByteSize > queueMaxBytes)
      {
        try
        {
          msgQueue.wait(500);
        } catch (InterruptedException e)
        {
          // simply loop to try again.
        }
        size = msgQueue.size();
      }

      queueByteSize += update.size();
      msgQueue.add(update);
      if (newestCSN == null || newestCSN.isOlderThan(update.getCSN()))
      {
        newestCSN = update.getCSN();
      }
      if (oldestCSN == null)
      {
        oldestCSN = update.getCSN();
      }
    }
  }

  /**
   * Get some changes out of the message queue of the LDAP server.
   * (from the beginning of the queue)
   * @param number the maximum number of messages to extract.
   * @return a List containing number changes extracted from the queue.
   */
  private List<UpdateMsg> getChanges(int number)
  {
    synchronized (msgQueue)
    {
      final int minAvailableNb = Math.min(number, msgQueue.size());
      return new LinkedList<UpdateMsg>(msgQueue.subList(0, minAvailableNb));
    }
  }

  /**
   * Get the oldest CSN that has not been purged yet.
   *
   * @return the oldest CSN that has not been purged yet.
   */
  public CSN getOldestCSN()
  {
    return oldestCSN;
  }

  /**
   * Get the newest CSN that has not been purged yet.
   *
   * @return the newest CSN that has not been purged yet.
   */
  public CSN getNewestCSN()
  {
    return newestCSN;
  }

  /**
   * Get the number of changes.
   *
   * @return Returns the number of changes.
   */
  public long getChangesCount()
  {
    if (newestCSN != null && oldestCSN != null)
    {
      return newestCSN.getSeqnum() - oldestCSN.getSeqnum() + 1;
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
    if (startAfterCSN == null)
    {
      flush();
    }
    return new JEReplicaDBCursor(db, startAfterCSN, this);
  }

  /**
   * Removes the provided number of messages from the beginning of the msgQueue.
   *
   * @param number the number of changes to be removed.
   */
  private void clearQueue(int number)
  {
    synchronized (msgQueue)
    {
      int current = 0;
      while (current < number && !msgQueue.isEmpty())
      {
        UpdateMsg msg = msgQueue.remove(); // remove first
        queueByteSize -= msg.size();
        current++;
      }
      if (msgQueue.size() < queueLowmark
          && queueByteSize < queueLowmarkBytes)
      {
        msgQueue.notifyAll();
      }
    }
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

    synchronized (msgQueue)
    {
      msgQueue.notifyAll();
    }

    while (msgQueue.size() != 0)
    {
      try
      {
        flush();
      }
      catch (ChangelogException e)
      {
        // We are already shutting down
        logger.error(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH, stackTraceToSingleLineString(e));
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

          synchronized (msgQueue)
          {
            if (msgQueue.size() < queueLowmark
                && queueByteSize < queueLowmarkBytes)
            {
              try
              {
                msgQueue.wait(1000);
              }
              catch (InterruptedException e)
              {
                // Do not reset the interrupt flag here,
                // because otherwise JE will barf next time flush() is called:
                // JE 5.0.97 refuses to persist changes to the DB when invoked
                // from a Thread with the interrupt flag set to true.
              }
            }
          }
        }
        catch (Exception end)
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

    synchronized (this)
    {
      notifyAll();
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
      synchronized (flushLock)
      {
        /*
         * the trim is done by group in order to save some CPU and IO bandwidth
         * start the transaction then do a bunch of remove then commit
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

            if (!csn.equals(newestCSN) && csn.isOlderThan(trimDate))
            {
              cursor.delete();
            }
            else
            {
              oldestCSN = csn;
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
    int size;
    int chunksize = Math.min(queueMaxSize, 500);

    do
    {
      synchronized(flushLock)
      {
        // get N (or less) messages from the queue to save to the DB
        // (from the beginning of the queue)
        List<UpdateMsg> changes = getChanges(chunksize);

        // if no more changes to save exit immediately.
        if (changes == null || (size = changes.size()) == 0)
        {
          return;
        }

        // save the change to the stable storage.
        db.addEntries(changes);

        // remove the changes from the list of changes to be saved
        // (remove from the beginning of the queue)
        clearQueue(changes.size());
      }
      // loop while there are more changes in the queue
    } while (size == chunksize);
  }

  /**
   * This internal class is used to implement the Monitoring capabilities of the
   * ReplicaDB.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public List<Attribute> getMonitorData()
    {
      List<Attribute> attributes = new ArrayList<Attribute>();
      attributes.add(Attributes.create("replicationServer-database",
          String.valueOf(serverId)));
      attributes.add(Attributes.create("domain-name",
          baseDN.toNormalizedString()));
      if (oldestCSN != null)
      {
        attributes.add(Attributes.create("first-change", encode(oldestCSN)));
      }
      if (newestCSN != null)
      {
        attributes.add(Attributes.create("last-change", encode(newestCSN)));
      }
      attributes.add(
          Attributes.create("queue-size", String.valueOf(msgQueue.size())));
      attributes.add(
          Attributes.create("queue-size-bytes", String.valueOf(queueByteSize)));
      return attributes;
    }

    private String encode(CSN csn)
    {
      return csn + " " + new Date(csn.getTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMonitorInstanceName()
    {
      ReplicationServerDomain domain = replicationServer
          .getReplicationServerDomain(baseDN);
      return "Changelog for DS(" + serverId + "),cn="
          + domain.getMonitorInstanceName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
                            throws ConfigException,InitializationException
    {
      // Nothing to do for now
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " " + baseDN + " " + serverId + " "
        + oldestCSN + " " + newestCSN;
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
    synchronized(flushLock)
    {
      msgQueue.clear();
      queueByteSize = 0;

      db.clear();
      oldestCSN = db.readOldestCSN();
      newestCSN = db.readNewestCSN();
    }
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
  public int getQueueSize()
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
