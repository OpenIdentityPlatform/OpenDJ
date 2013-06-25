/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;
import org.opends.messages.MessageBuilder;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;
import org.opends.server.util.TimeThread;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationDB.ReplServerDBCursor;

import com.sleepycat.je.DatabaseException;

/**
 * This class is used for managing the replicationServer database for each
 * server in the topology.
 * It is responsible for efficiently saving the updates that is received from
 * each master server into stable storage.
 * This class is also able to generate a ReplicationIterator that can be
 * used to read all changes from a given ChangeNUmber.
 *
 * This class publish some monitoring information below cn=monitor.
 *
 */
public class DbHandler implements Runnable
{
  // The msgQueue holds all the updates not yet saved to stable storage.
  // This list is only used as a temporary placeholder so that the write
  // in the stable storage can be grouped for efficiency reason.
  // Adding an update synchronously add the update to this list.
  // A dedicated thread loops on flush() and trim().
  // flush() : get a number of changes from the in memory list by block
  //           and write them to the db.
  // trim()  : deletes from the DB a number of changes that are older than a
  //           certain date.
  //
  // Changes are not read back by replicationServer threads that are responsible
  // for pushing the changes to other replication server or to LDAP server
  //
  private final LinkedList<UpdateMsg> msgQueue =
    new LinkedList<UpdateMsg>();

  // The High and low water mark for the max size of the msgQueue.
  // the threads calling add() method will be blocked if the size of
  // msgQueue becomes larger than the  queueHimark and will resume
  // only when the size of the msgQueue goes below queueLowmark.
  int queueMaxSize = 5000;
  int queueLowmark = 1000;
  int queueHimark = 4000;

  // The queue himark and lowmark in bytes, this is set to 100 times the
  // himark and lowmark in number of updates.
  int queueMaxBytes = 100 * queueMaxSize;
  int queueLowmarkBytes = 100 * queueLowmark;
  int queueHimarkBytes = 100 * queueHimark;

  // The number of bytes currently in the queue
  int queueByteSize = 0;

  private ReplicationDB db;
  private ChangeNumber firstChange = null;
  private ChangeNumber lastChange = null;
  private int serverId;
  private String baseDn;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private boolean shutdown = false;
  private boolean done = false;
  private DirectoryThread thread;
  private final Object flushLock = new Object();
  private ReplicationServer replicationServer;

  private long latestTrimDate = 0;

  /**
   *
   * The trim age in milliseconds. Changes record in the change DB that
   * are older than this age are removed.
   *
   */
  private long trimAge;

  /**
   * Creates a new dbHandler associated to a given LDAP server.
   *
   * @param id Identifier of the DB.
   * @param baseDn the baseDn for which this DB was created.
   * @param replicationServer The ReplicationServer that creates this dbHandler.
   * @param dbenv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @param queueSize The queueSize to use when creating the dbHandler.
   * @throws DatabaseException If a database problem happened
   */
  public DbHandler(
      int id, String baseDn, ReplicationServer replicationServer,
      ReplicationDbEnv dbenv, int queueSize)
         throws DatabaseException
  {
    this.replicationServer = replicationServer;
    serverId = id;
    this.baseDn = baseDn;
    trimAge = replicationServer.getTrimAge();
    queueMaxSize = queueSize;
    queueLowmark = queueSize / 5;
    queueHimark = queueSize * 4 / 5;
    queueMaxBytes = 200 * queueMaxSize;
    queueLowmarkBytes = 200 * queueLowmark;
    queueHimarkBytes = 200 * queueLowmark;
    db = new ReplicationDB(id, baseDn, replicationServer, dbenv);
    firstChange = db.readFirstChange();
    lastChange = db.readLastChange();
    thread = new DirectoryThread(this, "Replication server RS("
        + replicationServer.getServerId()
        + ") changelog checkpointer for Replica DS(" + id
        + ") for domain \"" + baseDn + "\"");
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
      if ((size > queueHimark) || (queueByteSize > queueHimarkBytes))
        msgQueue.notify();

      while ((size > queueMaxSize) || (queueByteSize > queueMaxBytes))
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
      if (lastChange == null || lastChange.older(update.getChangeNumber()))
      {
        lastChange = update.getChangeNumber();
      }
      if (firstChange == null)
        firstChange = update.getChangeNumber();
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
    int current = 0;
    LinkedList<UpdateMsg> changes = new LinkedList<UpdateMsg>();

    synchronized (msgQueue)
    {
      int size = msgQueue.size();
      while ((current < number) && (current < size))
      {
        UpdateMsg msg = msgQueue.get(current);
        current++;
        changes.add(msg);
      }
    }
    return changes;
  }

  /**
   * Get the firstChange.
   * @return Returns the firstChange.
   */
  public ChangeNumber getFirstChange()
  {
    return firstChange;
  }

  /**
   * Get the lastChange.
   * @return Returns the lastChange.
   */
  public ChangeNumber getLastChange()
  {
    return lastChange;
  }

  /**
   * Get the number of changes.
   *
   * @return Returns the number of changes.
   */
  public long getChangesCount()
  {
    try
    {
      return lastChange.getSeqnum() - firstChange.getSeqnum() + 1;
    }
    catch (Exception e)
    {
      return 0;
    }
  }

  /**
   * Generate a new ReplicationIterator that allows to browse the db
   * managed by this dbHandler and starting at the position defined
   * by a given changeNumber.
   *
   * @param changeNumber The position where the iterator must start.
   *
   * @return a new ReplicationIterator that allows to browse the db
   *         managed by this dbHandler and starting at the position defined
   *         by a given changeNumber.
   *
   * @throws DatabaseException if a database problem happened.
   * @throws Exception  If there is no other change to push after change
   *         with changeNumber number.
   */
  public ReplicationIterator generateIterator(ChangeNumber changeNumber)
                           throws DatabaseException, Exception
  {
    if (changeNumber == null)
    {
      flush();
    }
    return new ReplicationIterator(serverId, db, changeNumber, this);
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
      while ((current < number) && (!msgQueue.isEmpty()))
      {
        UpdateMsg msg = msgQueue.remove(); // remove first
        queueByteSize -= msg.size();
        current++;
      }
      if ((msgQueue.size() < queueLowmark) &&
          (queueByteSize < queueLowmarkBytes))
        msgQueue.notifyAll();
    }
  }

  /**
   * Shutdown this dbHandler.
   */
  public void shutdown()
  {
    if (shutdown)
    {
      return;
    }

    shutdown  = true;
    synchronized (msgQueue)
    {
      msgQueue.notifyAll();
    }

    synchronized (this)
    { /* Can this be replaced with thread.join() ? */
      while (!done)
      {
        try
        {
          this.wait();
        } catch (Exception e)
        { /* do nothing */}
      }
    }

    while (msgQueue.size() != 0)
      flush();

    db.shutdown();
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
  }

  /**
   * Run method for this class.
   * Periodically Flushes the ReplicationServerDomain cache from memory to the
   * stable storage and trims the old updates.
   */
  public void run()
  {
    while (!shutdown)
    {
      try
      {
        flush();
        trim();

        synchronized (msgQueue)
        {
          if ((msgQueue.size() < queueLowmark) &&
              (queueByteSize < queueLowmarkBytes))
          {
            try
            {
              msgQueue.wait(1000);
            } catch (InterruptedException e)
            {
              Thread.currentThread().interrupt();
            }
          }
        }
      } catch (Exception end)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH.get());
        mb.append(stackTraceToSingleLineString(end));
        logError(mb.toMessage());
        synchronized (this)
        {
          // set the done variable to true so that this thread don't
          // get stuck in this dbHandler.shutdown() when it get called
          // by replicationServer.shutdown();
          done = true;
        }
        if (replicationServer != null)
          replicationServer.shutdown();
        break;
      }
    }
    // call flush a last time before exiting to make sure that
    // no change was forgotten in the msgQueue
    flush();

    synchronized (this)
    {
      done = true;
      this.notifyAll();
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
   * @throws DatabaseException In case of database problem.
   */
  private void trim() throws DatabaseException, Exception
  {
    if (trimAge == 0)
    {
      return;
    }

    latestTrimDate = TimeThread.getTime() - trimAge;

    ChangeNumber trimDate = new ChangeNumber(latestTrimDate, 0, 0);

    // Find the last changeNumber before the trimDate, in the Database.
    ChangeNumber lastBeforeTrimDate = db
        .getPreviousChangeNumber(trimDate);
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
            ChangeNumber changeNumber = cursor.nextChangeNumber();
            if (changeNumber == null)
            {
              cursor.close();
              done = true;
              return;
            }

            if ((!changeNumber.equals(lastChange))
                && (changeNumber.older(trimDate)))
            {
              cursor.delete();
            }
            else
            {
              firstChange = changeNumber;
              cursor.close();
              done = true;
              return;
            }
          }
          cursor.close();
        }
        catch (Exception e)
        {
          // mark shutdown for this db so that we don't try again to
          // stop it from cursor.close() or methods called by cursor.close()
          cursor.abort();
          shutdown = true;
          throw e;
        }
      }
    }
  }

  /**
   * Flush a number of updates from the memory list to the stable storage.
   * Flush is done by chunk sized to 500 messages, starting from the
   * beginning of the list.
   */
  public void flush()
  {
    int size;
    int chunksize = (500 < queueMaxSize ? 500 : queueMaxSize);

    do
    {
      synchronized(flushLock)
      {
        // get N (or less) messages from the queue to save to the DB
        // (from the beginning of the queue)
        List<UpdateMsg> changes = getChanges(chunksize);

        // if no more changes to save exit immediately.
        if ((changes == null) || ((size = changes.size()) == 0))
          return;

        // save the change to the stable storage.
        db.addEntries(changes);

        // remove the changes from the list of changes to be saved
        // (remove from the beginning of the queue)
        clearQueue(changes.size());
      }
    } while (size >= chunksize);
  }

  /**
   * This internal class is used to implement the Monitoring capabilities
   * of the dbHandler.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Attribute> getMonitorData()
    {
      ArrayList<Attribute> attributes = new ArrayList<Attribute>();
      attributes.add(Attributes.create("replicationServer-database",
          String.valueOf(serverId)));
      attributes.add(Attributes.create("domain-name", baseDn));
      if (firstChange != null)
      {
        Date firstTime = new Date(firstChange.getTime());
        attributes.add(Attributes.create("first-change", firstChange
            .toString()
            + " " + firstTime.toString()));
      }
      if (lastChange != null)
      {
        Date lastTime = new Date(lastChange.getTime());
        attributes.add(Attributes.create("last-change", lastChange
            .toString()
            + " " + lastTime.toString()));
      }
      attributes.add(
          Attributes.create("queue-size", String.valueOf(msgQueue.size())));
      attributes.add(
          Attributes.create("queue-size-bytes", String.valueOf(queueByteSize)));

      return attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMonitorInstanceName()
    {
      ReplicationServerDomain domain = replicationServer
          .getReplicationServerDomain(baseDn, false);
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
    return(baseDn + " " + serverId + " " + firstChange + " " + lastChange);
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
   * @throws DatabaseException When an exception occurs while removing the
   * changes from the DB.
   * @throws Exception When an exception occurs while accessing a resource
   * from the DB.
   *
   */
  public void clear() throws DatabaseException, Exception
  {
    synchronized(flushLock)
    {
      msgQueue.clear();
      queueByteSize = 0;

      db.clear();
      firstChange = db.readFirstChange();
      lastChange = db.readLastChange();
    }
  }

  /**
   * Getter fot the serverID of the server for which this database is managed.
   *
   * @return the serverId.
   */
  public int getServerId()
  {
    return this.serverId;
  }

  /**
   * Return the size of the msgQueue (the memory cache of the DbHandler).
   * For test purpose.
   * @return The memory queue size.
   */
  public int getQueueSize()
  {
    return this.msgQueue.size();
  }

  /**
   * Set the counter writing window size (public for unit tests only).
   * @param size Size in number of record.
   */
  public void setCounterWindowSize(int size)
  {
    db.setCounterWindowSize(size);
  }

  /**
   * Return the number of changes between 2 provided change numbers.
   * This a alternative to traverseAndCount, expected to be much more efficient
   * when there is a huge number of changes in the Db.
   * @param from The lower (older) change number.
   * @param to   The upper (newer) change number.
   * @return The computed number of changes.
   */
  public int getCount(ChangeNumber from, ChangeNumber to)
  {
    int c=0;
    // Now that we always keep the last ChangeNumber in the DB to avoid
    // expiring cookies to quickly, we need to check if the "to"
    // is older than the trim date.
    if ((to == null) || !to.older(new ChangeNumber(latestTrimDate, 0, 0)))
    {
      flush();
      c = db.count(from, to);
    }
    return c;
  }

}
