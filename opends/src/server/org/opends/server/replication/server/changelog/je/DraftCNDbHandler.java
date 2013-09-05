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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.replication.server.changelog.je.DraftCNDB.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class is used for managing the replicationServer database for each
 * server in the topology. It is responsible for efficiently saving the updates
 * that is received from each master server into stable storage. This class is
 * also able to generate a {@link ChangeNumberIndexDBCursor} that can be used to
 * read all changes from a given change number.
 * <p>
 * This class publishes some monitoring information below <code>
 * cn=monitor</code>.
 */
public class DraftCNDbHandler implements ChangeNumberIndexDB, Runnable
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();
  private static int NO_KEY = 0;

  private DraftCNDB db;
  /**
   * FIXME Is this field that useful? {@link #getFirstChangeNumber()} does not
   * even use it!
   */
  private long firstChangeNumber = NO_KEY;
  /**
   * FIXME Is this field that useful? {@link #getLastChangeNumber()} does not
   * even use it!
   */
  private long lastChangeNumber = NO_KEY;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private boolean shutdown = false;
  private boolean trimDone = false;
  /**
   * A dedicated thread loops trim().
   * <p>
   * trim() : deletes from the DB a number of changes that are older than a
   * certain date.
   */
  private DirectoryThread thread;
  /**
   * The trim age in milliseconds. Changes record in the change DB that are
   * older than this age are removed.
   * <p>
   * FIXME it never gets updated even when the replication server purge delay is
   * updated
   */
  private long trimAge;

  private ReplicationServer replicationServer;


  /**
   * Creates a new dbHandler associated to a given LDAP server.
   *
   * @param replicationServer The ReplicationServer that creates this dbHandler.
   * @param dbenv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  public DraftCNDbHandler(ReplicationServer replicationServer,
      ReplicationDbEnv dbenv) throws ChangelogException
  {
    this.replicationServer = replicationServer;
    this.trimAge = replicationServer.getTrimAge();

    // DB initialization
    db = new DraftCNDB(dbenv);
    firstChangeNumber = getChangeNumber(db.readFirstCNIndexData());
    lastChangeNumber = getChangeNumber(db.readLastCNIndexData());

    // Trimming thread
    thread = new DirectoryThread(this, "Replication DraftCN db");
    thread.start();

    // Monitoring registration
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  private long getChangeNumber(CNIndexData cnIndexData)
      throws ChangelogException
  {
    if (cnIndexData != null)
    {
      return cnIndexData.getChangeNumber();
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void add(CNIndexData cnIndexData) throws ChangelogException
  {
    db.addEntry(cnIndexData);

    if (debugEnabled())
      TRACER.debugInfo("In DraftCNDbhandler.add, added: " + cnIndexData);
  }

  /** {@inheritDoc} */
  @Override
  public CNIndexData getFirstCNIndexData() throws ChangelogException
  {
    return db.readFirstCNIndexData();
  }

  /** {@inheritDoc} */
  @Override
  public CNIndexData getLastCNIndexData() throws ChangelogException
  {
    return db.readLastCNIndexData();
  }

  /**
   * Get the number of changes.
   * @return Returns the number of changes.
   */
  public long count()
  {
    return db.count();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEmpty() throws ChangelogException
  {
    return getLastCNIndexData() == null;
  }

  /**
   * Get a read cursor on the database from a provided key. The cursor MUST be
   * closed after use.
   * <p>
   * This method is only used by unit tests.
   *
   * @param startChangeNumber
   *          The change number from where to start.
   * @return the new cursor.
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  DraftCNDBCursor getReadCursor(long startChangeNumber)
      throws ChangelogException
  {
    return db.openReadCursor(startChangeNumber);
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexDBCursor getCursorFrom(long startChangeNumber)
      throws ChangelogException
  {
    return new DraftCNDbIterator(db, startChangeNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void shutdown()
  {
    if (shutdown)
    {
      return;
    }

    shutdown = true;
    synchronized (this)
    {
      notifyAll();
    }

    synchronized (this)
    { /* Can we just do a thread.join() ? */
      while (!trimDone)
      {
        try
        {
          wait();
        } catch (InterruptedException e)
        { /* do nothing */ }
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
    while (!shutdown)
    {
      try {
        trim();

        synchronized (this)
        {
          try
          {
            wait(1000);
          } catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
          }
        }
      } catch (Exception end)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH.get());
        mb.append(" ");
        mb.append(stackTraceToSingleLineString(end));
        logError(mb.toMessage());
        if (replicationServer != null)
          replicationServer.shutdown();
        break;
      }
    }

    synchronized (this)
    {
      trimDone = true;
      notifyAll();
    }
  }

  /**
   * Trim old changes from this database.
   * @throws ChangelogException In case of database problem.
   */
  public void trim() throws ChangelogException
  {
    if (trimAge == 0)
      return;

    clear(null);
  }

  /** {@inheritDoc} */
  @Override
  public void clear(String baseDNToClear) throws ChangelogException
  {
    if (isEmpty())
    {
      return;
    }

    for (int i = 0; i < 100; i++)
    {
      final DraftCNDBCursor cursor = db.openDeleteCursor();
      try
      {
        for (int j = 0; j < 50; j++)
        {
          // let's traverse the DraftCNDb
          if (!cursor.next())
          {
            cursor.close();
            return;
          }

          // From the draftCNDb change record, get the domain and CSN
          final CNIndexData data = cursor.currentData();
          final String baseDN = data.getBaseDN();
          if (baseDNToClear != null && baseDNToClear.equalsIgnoreCase(baseDN))
          {
            cursor.delete();
            continue;
          }

          final ReplicationServerDomain domain =
              replicationServer.getReplicationServerDomain(baseDN);
          if (domain == null)
          {
            // the domain has been removed since the record was written in the
            // draftCNDb, thus it makes no sense to keep the record in the
            // draftCNDb.
            cursor.delete();
            continue;
          }

          final CSN csn = data.getCSN();
          final ServerState startState = domain.getStartState();
          final CSN fcsn = startState.getCSN(csn.getServerId());

          final long currentChangeNumber = data.getChangeNumber();

          if (csn.older(fcsn))
          {
            cursor.delete();
            continue;
          }

          ServerState csnVector;
          try
          {
            Map<String, ServerState> csnStartStates =
                MultiDomainServerState.splitGenStateToServerStates(
                        data.getPreviousCookie());
            csnVector = csnStartStates.get(baseDN);

            if (debugEnabled())
              TRACER.debugInfo("DraftCNDBHandler:clear() - ChangeVector:"
                  + csnVector + " -- StartState:" + startState);
          }
          catch(Exception e)
          {
            // We couldn't parse the mdss from the DraftCNData Value
            cursor.delete();
            continue;
          }

          if (csnVector == null
              || (csnVector.getCSN(csn.getServerId()) != null
                    && !csnVector.cover(startState)))
          {
            cursor.delete();
            if (debugEnabled())
              TRACER.debugInfo("DraftCNDBHandler:clear() - deleted " + csn
                  + "Not covering startState");
            continue;
          }

          firstChangeNumber = currentChangeNumber;
          cursor.close();
          return;
        }

        cursor.close();
      }
      catch (ChangelogException e)
      {
        // mark shutdown for this db so that we don't try again to
        // stop it from cursor.close() or methods called by cursor.close()
        cursor.abort();
        shutdown = true;
        throw e;
      }
      catch (Exception e)
      {
        // mark shutdown for this db so that we don't try again to
        // stop it from cursor.close() or methods called by cursor.close()
        cursor.abort();
        shutdown = true;
        throw new ChangelogException(e);
      }
    }
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
    public List<Attribute> getMonitorData()
    {
      List<Attribute> attributes = new ArrayList<Attribute>();

      try
      {
        CNIndexData firstCNData = db.readFirstCNIndexData();
        String firstCN = String.valueOf(firstCNData.getChangeNumber());
        attributes.add(Attributes.create("first-draft-changenumber", firstCN));
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.WARNING, e);
        attributes.add(Attributes.create("first-draft-changenumber", "0"));
      }

      try
      {
        CNIndexData lastCNData = db.readLastCNIndexData();
        if (lastCNData != null)
        {
          String lastCN = String.valueOf(lastCNData.getChangeNumber());
          attributes.add(Attributes.create("last-draft-changenumber", lastCN));
        }
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.WARNING, e);
        attributes.add(Attributes.create("last-draft-changenumber", "0"));
      }

      attributes.add(Attributes.create("count", Long.toString(count())));
      return attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMonitorInstanceName()
    {
      return "ChangeNumber Index Database";
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
    return "draftCNdb:" + " " + firstChangeNumber + " " + lastChangeNumber;
  }

  /**
   * Set the Purge delay for this db Handler.
   * @param delay The purge delay in Milliseconds.
   */
  public void setPurgeDelay(long delay)
  {
    trimAge = delay;
  }

  /** {@inheritDoc} */
  @Override
  public void clear() throws ChangelogException
  {
    db.clear();
    firstChangeNumber = getChangeNumber(db.readFirstCNIndexData());
    lastChangeNumber = getChangeNumber(db.readLastCNIndexData());
  }

  private ReentrantLock lock = new ReentrantLock();

  /**
   * Tests if the current thread has the lock on this object.
   * @return True if the current thread has the lock.
   */
  public boolean hasLock()
  {
    return lock.getHoldCount() > 0;
  }

  /**
   * Takes the lock on this object (blocking until lock can be acquired).
   * @throws InterruptedException If interrupted.
   */
   public void lock() throws InterruptedException
  {
    lock.lockInterruptibly();
  }

  /**
   * Releases the lock on this object.
   */
  public void release()
  {
    lock.unlock();
  }

  /** {@inheritDoc} */
  @Override
  public CNIndexData getCNIndexData(long changeNumber)
      throws ChangelogException
  {
    DraftCNDBCursor cursor = null;
    try
    {
      cursor = db.openReadCursor(changeNumber);
      return cursor.currentData();
    }
    finally
    {
      close(cursor);
    }
  }

}
