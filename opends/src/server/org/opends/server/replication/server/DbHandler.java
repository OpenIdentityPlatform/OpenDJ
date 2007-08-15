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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
import java.util.NoSuchElementException;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.util.TimeThread;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMessage;
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
  // This queue hold all the updates not yet saved to stable storage
  // it is only used as a temporary placeholder so that the write
  // in the stable storage can be grouped for efficiency reason.
  // it is never read back by replicationServer threads that are responsible
  // for pushing the changes to other replication server or to LDAP server
  private LinkedList<UpdateMessage> msgQueue = new LinkedList<UpdateMessage>();
  private ReplicationDB db;
  private ChangeNumber firstChange = null;
  private ChangeNumber lastChange = null;
  private short serverId;
  private DN baseDn;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private boolean shutdown = false;
  private boolean done = false;
  private DirectoryThread thread = null;
  private Object flushLock = new Object();

  // The High and low water mark for the max size of the msgQueue.
  // the threads calling add() method will be blocked if the size of
  // msgQueue becomes larger than the  MSG_QUEUE_HIMARK and will resume
  // only when the size of the msgQueue goes below MSG_QUEUE_LOWMARK.
  final static int MSG_QUEUE_HIMARK = 5000;
  final static int MSG_QUEUE_LOWMARK = 4000;

  /**
   * the trim age in milliseconds.
   */
  private long trimage;

  /**
   * Creates a New dbHandler associated to a given LDAP server.
   *
   * @param id Identifier of the DB.
   * @param baseDn the baseDn for which this DB was created.
   * @param replicationServer The ReplicationServer that creates this dbHandler.
   * @param dbenv the Database Env to use to create the ReplicationServer DB.
   * @throws DatabaseException If a database problem happened
   */
  public DbHandler(short id, DN baseDn, ReplicationServer replicationServer,
      ReplicationDbEnv dbenv)
         throws DatabaseException
  {
    this.serverId = id;
    this.baseDn = baseDn;
    this.trimage = replicationServer.getTrimage();
    db = new ReplicationDB(id, baseDn, replicationServer, dbenv);
    firstChange = db.readFirstChange();
    lastChange = db.readLastChange();
    thread = new DirectoryThread(this,
                                 "Replication Server db " + id + " " +  baseDn);
    thread.start();

    DirectoryServer.deregisterMonitorProvider(
                      dbMonitor.getMonitorInstanceName());
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
  public void add(UpdateMessage update)
  {
    synchronized (msgQueue)
    {
      int size = msgQueue.size();
      while (size > MSG_QUEUE_HIMARK)
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
   *
   * @param number the number of messages to extract.
   * @return a List containing number changes extracted from the queue.
   */
  private List<UpdateMessage> getChanges(int number)
  {
    int current = 0;
    LinkedList<UpdateMessage> changes = new LinkedList<UpdateMessage>();

    synchronized (msgQueue)
    {
      int size = msgQueue.size();
      while ((current < number) && (current < size))
      {
        UpdateMessage msg = msgQueue.get(current);
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
    /*
     * When we create an iterator we need to make sure that we
     * don't miss some changes because the iterator is created
     * close to the limit of the changed that have not yet been
     * flushed to the database.
     * We detect this by comparing the date of the changeNumber where
     * we want to start with the date of the first ChangeNumber
     * of the msgQueue.
     * If this is the case we flush the queue to the database.
     */
    ChangeNumber recentChangeNumber = null;

    if (changeNumber == null)
      flush();

    synchronized (msgQueue)
    {
      try
      {
        UpdateMessage msg = msgQueue.getFirst();
        recentChangeNumber = msg.getChangeNumber();
      }
      catch (NoSuchElementException e)
      {}
    }

    if ( (recentChangeNumber != null) && (changeNumber != null))
    {
      if (((recentChangeNumber.getTimeSec() - changeNumber.getTimeSec()) < 2) ||
         ((recentChangeNumber.getSeqnum() - changeNumber.getSeqnum()) < 20))
      {
        flush();
      }
    }

    return new ReplicationIterator(serverId, db, changeNumber);
  }

  /**
   * Removes message in a subList of the msgQueue from the msgQueue.
   *
   * @param number the number of changes to be removed.
   */
  private void clear(int number)
  {
    synchronized (msgQueue)
    {
      int current = 0;
      while ((current < number) && (!msgQueue.isEmpty()))
      {
        msgQueue.remove();
        current++;
      }
      if (msgQueue.size() < MSG_QUEUE_LOWMARK)
        msgQueue.notify();
    }
  }

  /**
   * Shutdown this dbHandler.
   */
  public void shutdown()
  {
    shutdown  = true;
    synchronized (this)
    {
      this.notifyAll();
    }

    synchronized (this)
    {
      while (done  == false)
      {
        try
        {
          this.wait();
        } catch (Exception e)
        {}
      }
    }

    while (msgQueue.size() != 0)
      flush();

    db.shutdown();
  }

  /**
   * Run method for this class.
   * Periodically Flushes the ReplicationCache from memory to the stable storage
   * and trims the old updates.
   */
  public void run()
  {
    while (shutdown == false)
    {
      try {
        flush();
        trim();

        synchronized (this)
        {
          try
          {
            this.wait(1000);
          } catch (InterruptedException e)
          { }
        }
      } catch (Exception end)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH.get());
        mb.append(stackTraceToSingleLineString(end));
        logError(mb.toMessage());
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
   * Flush old change information from this replicationServer database.
   * @throws DatabaseException In case of database problem.
   */
  private void trim() throws DatabaseException, Exception
  {
    if (trimage == 0)
      return;
    int size = 0;
    boolean finished = false;
    ChangeNumber trimDate = new ChangeNumber(TimeThread.getTime() - trimage,
        (short) 0, (short)0);

    /* the trim is done by group in order to save some CPU and IO bandwidth
     * start the transaction then do a bunch of remove then commit
     */
    ReplServerDBCursor cursor;

    cursor = db.openDeleteCursor();

    try {
      while ((size < 5000 ) &&  (!finished))
      {
        ChangeNumber changeNumber = cursor.nextChangeNumber();
        if (changeNumber != null)
        {
          if ((!changeNumber.equals(lastChange))
              && (changeNumber.older(trimDate)))
          {
            size++;
            cursor.delete();
          }
          else
          {
            firstChange = changeNumber;
            finished = true;
          }
        }
        else
          finished = true;
      }

      cursor.close();
    } catch (DatabaseException e)
    {
      cursor.close();
      throw (e);
    }
  }

  /**
   * Flush a number of updates from the memory list to the stable storage.
   */
  private void flush()
  {
    int size;

    do
    {
      synchronized(flushLock)
      {
        // get N messages to save in the DB
        List<UpdateMessage> changes = getChanges(500);

        // if no more changes to save exit immediately.
        if ((changes == null) || ((size = changes.size()) == 0))
          return;

        // save the change to the stable storage.
        db.addEntries(changes);

        // remove the changes from the list of changes to be saved.
        clear(changes.size());
      }
    } while (size >=500);
  }

  /**
   * This internal class is used to implement the Monitoring capabilities
   * of the dbHandler.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    private DbMonitorProvider()
    {
      super("ReplicationServer Database");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Attribute> getMonitorData()
    {
      ArrayList<Attribute> attributes = new ArrayList<Attribute>();
      attributes.add(new Attribute("replicationServer-database",
                                   String.valueOf(serverId)));
      attributes.add(new Attribute("base-dn", baseDn.toString()));
      if (firstChange != null)
      {
        Date firstTime = new Date(firstChange.getTime());
        attributes.add(new Attribute("first-change",
            firstChange.toString() + " " + firstTime.toString()));
      }
      if (lastChange != null)
      {
        Date lastTime = new Date(lastChange.getTime());
        attributes.add(new Attribute("last-change",
            lastChange.toString() + " " + lastTime.toString()));
      }

      return attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMonitorInstanceName()
    {
      return "ReplicationServer database " + baseDn.toString() +
             " " + String.valueOf(serverId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUpdateInterval()
    {
      /* we don't wont to do polling on this monitor */
      return 0;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateMonitorData()
    {
      // As long as getUpdateInterval() returns 0, this will never get called
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
    trimage = delay;
  }

}
