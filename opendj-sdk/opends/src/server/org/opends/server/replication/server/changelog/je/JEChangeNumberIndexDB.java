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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
import org.opends.server.types.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class is used for managing the replicationServer database for each
 * server in the topology. It is responsible for efficiently saving the updates
 * that is received from each master server into stable storage. This class is
 * also able to generate a {@link DBCursor} that can be used to read all changes
 * from a given change number.
 * <p>
 * This class publishes some monitoring information below <code>
 * cn=monitor</code>.
 */
public class JEChangeNumberIndexDB implements ChangeNumberIndexDB, Runnable
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();
  private static int NO_KEY = 0;

  private DraftCNDB db;
  /** FIXME What is this field used for? */
  private volatile long oldestChangeNumber = NO_KEY;
  /**
   * The newest changenumber stored in the DB. It is used to avoid trimming the
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
   * <li>the current newest record ('recordA') being trimmed from the DB</li>
   * <li>'recordB' failing to be inserted in the DB</li>
   * </ol>
   */
  private final AtomicLong lastGeneratedChangeNumber;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  /**
   * A dedicated thread loops trim().
   * <p>
   * trim() : deletes from the DB a number of changes that are older than a
   * certain date.
   */
  private DirectoryThread trimmingThread;
  /**
   * The trim age in milliseconds. Changes record in the change DB that are
   * older than this age are removed.
   * <p>
   * FIXME it never gets updated even when the replication server purge delay is
   * updated
   */
  private volatile long trimAge;

  private ReplicationServer replicationServer;


  /**
   * Creates a new JEChangeNumberIndexDB associated to a given LDAP server.
   *
   * @param replicationServer The ReplicationServer that creates this instance.
   * @param dbenv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  public JEChangeNumberIndexDB(ReplicationServer replicationServer,
      ReplicationDbEnv dbenv) throws ChangelogException
  {
    this.replicationServer = replicationServer;
    this.trimAge = replicationServer.getTrimAge();

    // DB initialization
    db = new DraftCNDB(dbenv);
    final ChangeNumberIndexRecord oldestRecord = db.readFirstRecord();
    final ChangeNumberIndexRecord newestRecord = db.readLastRecord();
    oldestChangeNumber = getChangeNumber(oldestRecord);
    final long newestCN = getChangeNumber(newestRecord);
    newestChangeNumber = newestCN;
    // initialization of the lastGeneratedChangeNumber from the DB content
    // if DB is empty => last record does not exist => default to 0
    lastGeneratedChangeNumber = new AtomicLong(newestCN);

    // Monitoring registration
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  /**
   * Creates and starts the thread trimming the CNIndexDB.
   */
  public void startTrimmingThread()
  {
    trimmingThread =
        new DirectoryThread(this, "Replication ChangeNumberIndexDB Trimmer");
    trimmingThread.start();
  }

  private long getChangeNumber(ChangeNumberIndexRecord record)
      throws ChangelogException
  {
    if (record != null)
    {
      return record.getChangeNumber();
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public long addRecord(ChangeNumberIndexRecord record)
      throws ChangelogException
  {
    long changeNumber = nextChangeNumber();
    final ChangeNumberIndexRecord newRecord =
        new ChangeNumberIndexRecord(changeNumber, record.getPreviousCookie(),
            record.getBaseDN(), record.getCSN());
    db.addRecord(newRecord);
    newestChangeNumber = changeNumber;

    if (debugEnabled())
      TRACER.debugInfo("In JEChangeNumberIndexDB.add, added: " + newRecord);
    return changeNumber;
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexRecord getOldestRecord() throws ChangelogException
  {
    return db.readFirstRecord();
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexRecord getNewestRecord() throws ChangelogException
  {
    return db.readLastRecord();
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
   * @return Returns the number of changes.
   */
  public long count()
  {
    return db.count();
  }

  /**
   * Returns whether this database is empty.
   *
   * @return <code>true</code> if this database is empty, <code>false</code>
   *         otherwise
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  public boolean isEmpty() throws ChangelogException
  {
    return getNewestRecord() == null;
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<ChangeNumberIndexRecord> getCursorFrom(long startChangeNumber)
      throws ChangelogException
  {
    return new JEChangeNumberIndexDBCursor(db, startChangeNumber);
  }

  /**
   * Shutdown this DB.
   */
  public void shutdown()
  {
    if (shutdown.get())
    {
      return;
    }

    shutdown.set(true);
    synchronized (this)
    {
      notifyAll();
    }

    if (trimmingThread != null)
    {
      try
      {
        trimmingThread.join();
      }
      catch (InterruptedException ignored)
      {
        // Nothing can be done about it, just proceed
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
    while (!shutdown.get())
    {
      try {
        trim(shutdown);

        synchronized (this)
        {
          if (!shutdown.get())
          {
            try
            {
              wait(1000);
            }
            catch (InterruptedException e)
            {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
      catch (Exception end)
      {
        logError(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH
            .get(stackTraceToSingleLineString(end)));
        if (replicationServer != null)
        {
          replicationServer.shutdown();
        }
        break;
      }
    }
  }

  /**
   * Trim old changes from this database.
   *
   * @param shutdown
   *          AtomicBoolean telling whether the current run must be stopped
   * @throws ChangelogException
   *           In case of database problem.
   */
  public void trim(AtomicBoolean shutdown) throws ChangelogException
  {
    if (trimAge == 0)
      return;

    clear(null, shutdown);
  }

  /**
   * Clear the changes from this DB (from both memory cache and DB storage) for
   * the provided baseDN.
   *
   * @param baseDNToClear
   *          The baseDN for which we want to remove all records from this DB,
   *          null means all.
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  public void clear(DN baseDNToClear) throws ChangelogException
  {
    clear(baseDNToClear, null);
  }

  private void clear(DN baseDNToClear, AtomicBoolean shutdown)
      throws ChangelogException
  {
    if (isEmpty())
    {
      return;
    }

    for (int i = 0; i < 100; i++)
    {
      if (mustShutdown(shutdown))
      {
        return;
      }
      final DraftCNDBCursor cursor = db.openDeleteCursor();
      try
      {
        for (int j = 0; j < 50; j++)
        {
          // let's traverse the CNIndexDB
          if (mustShutdown(shutdown) || !cursor.next())
          {
            cursor.close();
            return;
          }

          final ChangeNumberIndexRecord record = cursor.currentRecord();
          if (record.getChangeNumber() != oldestChangeNumber)
          {
            oldestChangeNumber = record.getChangeNumber();
          }
          if (record.getChangeNumber() == newestChangeNumber)
          {
            // do not trim the newest record to avoid having the last generated
            // changenumber dropping back to 0 if the server restarts
            cursor.close();
            return;
          }

          if (baseDNToClear != null && baseDNToClear.equals(record.getBaseDN()))
          {
            cursor.delete();
            continue;
          }

          final ReplicationServerDomain domain =
              replicationServer.getReplicationServerDomain(record.getBaseDN());
          if (domain == null)
          {
            // the domain has been removed since the record was written in the
            // CNIndexDB, thus it makes no sense to keep this record in the DB.
            cursor.delete();
            continue;
          }

          // FIXME there is an opportunity for a phantom record in the CNIndexDB
          // if the replicaDB gets purged after call to domain.getOldestState().
          final CSN csn = record.getCSN();
          final ServerState oldestState = domain.getOldestState();
          final CSN fcsn = oldestState.getCSN(csn.getServerId());
          if (csn.isOlderThan(fcsn))
          {
            // This change which has already been purged from the corresponding
            // replicaDB => purge it from CNIndexDB
            cursor.delete();
            continue;
          }

          ServerState csnVector;
          try
          {
            Map<DN, ServerState> csnStartStates =
                MultiDomainServerState.splitGenStateToServerStates(
                        record.getPreviousCookie());
            csnVector = csnStartStates.get(record.getBaseDN());

            if (debugEnabled())
              TRACER.debugInfo("JEChangeNumberIndexDB:clear() - ChangeVector:"
                  + csnVector + " -- StartState:" + oldestState);
          }
          catch(Exception e)
          {
            // We could not parse the MultiDomainServerState from the record
            // FIXME this is quite an aggressive delete()
            cursor.delete();
            continue;
          }

          if (csnVector == null
              || (csnVector.getCSN(csn.getServerId()) != null
                    && !csnVector.cover(oldestState)))
          {
            cursor.delete();
            if (debugEnabled())
              TRACER.debugInfo("JEChangeNumberIndexDB:clear() - deleted " + csn
                  + "Not covering startState");
            continue;
          }

          oldestChangeNumber = record.getChangeNumber();
          cursor.close();
          return;
        }

        cursor.close();
      }
      catch (ChangelogException e)
      {
        cursor.abort();
        throw e;
      }
      catch (Exception e)
      {
        cursor.abort();
        throw new ChangelogException(e);
      }
    }
  }

  private boolean mustShutdown(AtomicBoolean shutdown)
  {
    return shutdown != null && shutdown.get();
  }

  /**
   * This internal class is used to implement the Monitoring capabilities of the
   * JEChangeNumberIndexDB.
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
      attributes.add(createChangeNumberAttribute(true));
      attributes.add(createChangeNumberAttribute(false));
      attributes.add(Attributes.create("count", Long.toString(count())));
      return attributes;
    }

    private Attribute createChangeNumberAttribute(boolean isFirst)
    {
      final String attributeName =
          isFirst ? "first-draft-changenumber" : "last-draft-changenumber";
      final String changeNumber = String.valueOf(getChangeNumber(isFirst));
      return Attributes.create(attributeName, changeNumber);
    }

    private long getChangeNumber(boolean isFirst)
    {
      try
      {
        final ChangeNumberIndexRecord record =
            isFirst ? db.readFirstRecord() : db.readLastRecord();
        if (record != null)
        {
          return record.getChangeNumber();
        }
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.WARNING, e);
      }
      return 0;
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
    return getClass().getSimpleName() + ": " + oldestChangeNumber + " "
        + newestChangeNumber;
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
   *
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  public void clear() throws ChangelogException
  {
    db.clear();
    oldestChangeNumber = getChangeNumber(db.readFirstRecord());
    newestChangeNumber = getChangeNumber(db.readLastRecord());
  }

}
