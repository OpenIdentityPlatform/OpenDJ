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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.replication.server.changelog.je.DraftCNDB.*;
import org.opends.server.types.*;

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
public class JEChangeNumberIndexDB implements ChangeNumberIndexDB
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final int NO_KEY = 0;

  private DraftCNDB db;
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
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private final AtomicBoolean shutdown = new AtomicBoolean(false);


  /**
   * Creates a new JEChangeNumberIndexDB associated to a given LDAP server.
   *
   * @param dbEnv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  public JEChangeNumberIndexDB(ReplicationDbEnv dbEnv) throws ChangelogException
  {
    db = new DraftCNDB(dbEnv);
    final ChangeNumberIndexRecord newestRecord = db.readLastRecord();
    newestChangeNumber = getChangeNumber(newestRecord);
    // initialization of the lastGeneratedChangeNumber from the DB content
    // if DB is empty => last record does not exist => default to 0
    lastGeneratedChangeNumber = new AtomicLong(newestChangeNumber);

    // Monitoring registration
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  private long getChangeNumber(ChangeNumberIndexRecord record)
      throws ChangelogException
  {
    if (record != null)
    {
      return record.getChangeNumber();
    }
    return NO_KEY;
  }

  /** {@inheritDoc} */
  @Override
  public long addRecord(ChangeNumberIndexRecord record)
      throws ChangelogException
  {
    long changeNumber = nextChangeNumber();
    final ChangeNumberIndexRecord newRecord =
        new ChangeNumberIndexRecord(changeNumber, record.getBaseDN(), record.getCSN());
    db.addRecord(newRecord);
    newestChangeNumber = changeNumber;

    logger.trace("In JEChangeNumberIndexDB.add, added: %s", newRecord);
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
    if (shutdown.compareAndSet(false, true))
    {
      db.shutdown();
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
  public CSN purgeUpTo(CSN purgeCSN) throws ChangelogException
  {
    if (isEmpty() || purgeCSN == null)
    {
      return null;
    }

    final DraftCNDBCursor cursor = db.openDeleteCursor();
    try
    {
      while (!mustShutdown(shutdown) && cursor.next())
      {
        final ChangeNumberIndexRecord record = cursor.currentRecord();
        if (record.getChangeNumber() != newestChangeNumber
            && record.getCSN().isOlderThan(purgeCSN))
        {
          cursor.delete();
        }
        else
        {
          // 1- Current record is not old enough to purge.
          // 2- Do not purge the newest record to avoid having the last
          // generated changenumber dropping back to 0 when the server restarts
          return record.getCSN();
        }
      }

      return null;
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
    finally
    {
      cursor.close();
    }
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
  public void removeDomain(DN baseDNToClear) throws ChangelogException
  {
    if (isEmpty())
    {
      return;
    }

    final DraftCNDBCursor cursor = db.openDeleteCursor();
    try
    {
      while (!mustShutdown(shutdown) && cursor.next())
      {
        final ChangeNumberIndexRecord record = cursor.currentRecord();
        if (record.getChangeNumber() == newestChangeNumber)
        {
          // do not purge the newest record to avoid having the last generated
          // changenumber dropping back to 0 if the server restarts
          return;
        }

        if (baseDNToClear == null || record.getBaseDN().equals(baseDNToClear))
        {
          cursor.delete();
        }
      }
    }
    catch (ChangelogException e)
    {
      cursor.abort();
      throw e;
    }
    finally
    {
      cursor.close();
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
    /** {@inheritDoc} */
    @Override
    public List<Attribute> getMonitorData()
    {
      List<Attribute> attributes = new ArrayList<>();
      attributes.add(createChangeNumberAttribute(true));
      attributes.add(createChangeNumberAttribute(false));
      attributes.add(Attributes.create("count", Long.toString(count())));
      return attributes;
    }

    private Attribute createChangeNumberAttribute(boolean isFirst)
    {
      final String attributeName =
          isFirst ? "first-draft-changenumber" : "last-draft-changenumber";
      final String changeNumber = String.valueOf(readChangeNumber(isFirst));
      return Attributes.create(attributeName, changeNumber);
    }

    private long readChangeNumber(boolean isFirst)
    {
      try
      {
        return getChangeNumber(
            isFirst ? db.readFirstRecord() : db.readLastRecord());
      }
      catch (ChangelogException e)
      {
        logger.traceException(e);
        return NO_KEY;
      }
    }

    /** {@inheritDoc} */
    @Override
    public String getMonitorInstanceName()
    {
      return "ChangeNumber Index Database";
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
    return getClass().getSimpleName() + ", newestChangeNumber="
        + newestChangeNumber;
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
    newestChangeNumber = NO_KEY;
  }

}
