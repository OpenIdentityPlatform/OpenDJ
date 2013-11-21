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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;

import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * Thread responsible for inserting replicated changes into the ChangeNumber
 * Index DB (CNIndexDB for short). Only changes older than the medium
 * consistency point are inserted in the CNIndexDB. As a consequence this class
 * is also responsible for maintaining the medium consistency point.
 */
public class ChangeNumberIndexer extends DirectoryThread
{
  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  private final ChangelogDB changelogDB;
  /** Only used for initialization, and then discarded. */
  private ChangelogState changelogState;

  /*
   * previousCookie and mediumConsistencyPoint must be thread safe, because
   * 1) initialization can happen while the replication server starts receiving
   * updates 2) many updates can happen concurrently. This solution also avoids
   * using a queue that could fill up before we have consumed all its content.
   */
  /**
   * Stores the value of the cookie before the change currently processed is
   * inserted in the DB. After insert, it is updated with the CSN of the change
   * currently processed (thus becoming the "current" cookie just before the
   * change is returned.
   */
  private final MultiDomainServerState previousCookie =
      new MultiDomainServerState();

  /**
   * Holds the medium consistency point for the current replication server.
   *
   * @see <a href=
   * "https://wikis.forgerock.org/confluence/display/OPENDJ/OpenDJ+Domain+Names"
   * >OpenDJ Domain Names for a description of what the medium consistency point
   * is</a>
   */
  private final MultiDomainServerState mediumConsistencyPoint =
      new MultiDomainServerState();

  /**
   * Composite cursor across all the replicaDBs for all the replication domains.
   * It is volatile to ensure it supports concurrent update. Each time it is
   * used more than once in a method, the method must take a local copy to
   * ensure the cursor does not get updated in the middle of the method.
   */
  private volatile CompositeDBCursor<DN> crossDomainDBCursor;

  /**
   * New cursors for this Map must be created from the {@link #run()} method,
   * i.e. from the same thread that will make use of them. If this rule is not
   * obeyed, then a JE exception will be thrown about
   * "Non-transactional Cursors may not be used in multiple threads;".
   */
  private Map<DN, Map<Integer, DBCursor<UpdateMsg>>> allCursors =
      new HashMap<DN, Map<Integer, DBCursor<UpdateMsg>>>();
  /** This map can be updated by multiple threads. */
  private ConcurrentMap<Integer, DN> newCursors =
      new ConcurrentSkipListMap<Integer, DN>();

  /**
   * Builds a ChangeNumberIndexer object.
   *
   * @param changelogDB
   *          the changelogDB
   * @param changelogState
   *          the changelog state used for initialization
   */
  ChangeNumberIndexer(ChangelogDB changelogDB, ChangelogState changelogState)
  {
    super("Change number indexer");
    this.changelogDB = changelogDB;
    this.changelogState = changelogState;
  }

  /**
   * Ensures the medium consistency point is updated by heartbeats.
   *
   * @param baseDN
   *          the baseDN of the domain for which the heartbeat is published
   * @param heartbeatCSN
   *          the CSN coming from the heartbeat
   */
  public void publishHeartbeat(DN baseDN, CSN heartbeatCSN)
  {
    mediumConsistencyPoint.update(baseDN, heartbeatCSN);
    final CompositeDBCursor<DN> localCursor = crossDomainDBCursor;
    final DN changeBaseDN = localCursor.getData();
    final CSN changeCSN = localCursor.getRecord().getCSN();
    tryNotify(changeBaseDN, changeCSN);
  }

  /**
   * Ensures the medium consistency point is updated by UpdateMsg.
   *
   * @param baseDN
   *          the baseDN of the domain for which the heartbeat is published
   * @param updateMsg
   *          the updateMsg that will update the medium consistency point
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void publishUpdateMsg(DN baseDN, UpdateMsg updateMsg)
      throws ChangelogException
  {
    final CSN csn = updateMsg.getCSN();
    mediumConsistencyPoint.update(baseDN, csn);
    newCursors.put(csn.getServerId(), baseDN);
    tryNotify(baseDN, csn);
  }

  /**
   * Notifies the Change number indexer thread if it will be able to do some
   * work.
   */
  private void tryNotify(final DN baseDN, final CSN csn)
  {
    if (mediumConsistencyPoint.cover(baseDN, csn))
    {
      synchronized (this)
      {
        notify();
      }
    }
  }

  private void initialize() throws ChangelogException, DirectoryException
  {
    final ChangeNumberIndexRecord newestRecord =
        changelogDB.getChangeNumberIndexDB().getNewestRecord();
    if (newestRecord != null)
    {
      previousCookie.update(
          new MultiDomainServerState(newestRecord.getPreviousCookie()));
    }

    // initialize the cross domain DB cursor
    final ReplicationDomainDB domainDB = changelogDB.getReplicationDomainDB();
    for (Entry<DN, List<Integer>> entry
        : changelogState.getDomainToServerIds().entrySet())
    {
      final DN baseDN = entry.getKey();
      for (Integer serverId : entry.getValue())
      {
        final ServerState previousSS = previousCookie.get(baseDN);
        final CSN csn = previousSS != null ? previousSS.getCSN(serverId) : null;
        ensureCursorExists(baseDN, serverId, csn);
      }

      ServerState latestKnownState = domainDB.getDomainNewestCSNs(baseDN);
      mediumConsistencyPoint.update(baseDN, latestKnownState);
    }

    crossDomainDBCursor = newCompositeDBCursor();
    if (newestRecord != null)
    {
      // restore the "previousCookie" state before shutdown
      final UpdateMsg record = crossDomainDBCursor.getRecord();
      if (!record.getCSN().equals(newestRecord.getCSN()))
      {
        // TODO JNR remove
        throw new RuntimeException("They do not equal! recordCSN="
            + record.getCSN() + " newestRecordCSN=" + newestRecord.getCSN());
      }
      // TODO JNR is it possible to use the following line instead?
      // previousCookie.update(newestRecord.getBaseDN(), record.getCSN());
      // TODO JNR would this mean updating the if above?
      previousCookie.update(crossDomainDBCursor.getData(), record.getCSN());
      crossDomainDBCursor.next();
    }

    // this will not be used any more. Discard for garbage collection.
    this.changelogState = null;
  }

  private CompositeDBCursor<DN> newCompositeDBCursor() throws ChangelogException
  {
    final Map<DBCursor<UpdateMsg>, DN> cursors =
        new HashMap<DBCursor<UpdateMsg>, DN>();
    for (Entry<DN, Map<Integer, DBCursor<UpdateMsg>>> entry
        : this.allCursors.entrySet())
    {
      for (Entry<Integer, DBCursor<UpdateMsg>> entry2
          : entry.getValue().entrySet())
      {
        cursors.put(entry2.getValue(), entry.getKey());
      }
    }
    final CompositeDBCursor<DN> result = new CompositeDBCursor<DN>(cursors);
    result.next();
    return result;
  }

  private boolean ensureCursorExists(DN baseDN, Integer serverId, CSN csn)
      throws ChangelogException
  {
    Map<Integer, DBCursor<UpdateMsg>> map = allCursors.get(baseDN);
    if (map == null)
    {
      map = new ConcurrentSkipListMap<Integer, DBCursor<UpdateMsg>>();
      allCursors.put(baseDN, map);
    }
    DBCursor<UpdateMsg> cursor = map.get(serverId);
    if (cursor == null)
    {
      final ReplicationDomainDB domainDB = changelogDB.getReplicationDomainDB();
      cursor = domainDB.getCursorFrom(baseDN, serverId, csn);
      map.put(serverId, cursor);
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    try
    {
      /*
       * initialize here to allow fast application start up and avoid errors due
       * cursors being created in a different thread to the one where they are
       * used.
       */
      initialize();
    }
    catch (DirectoryException e)
    {
      // TODO JNR error message i18n
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      return;
    }
    catch (ChangelogException e)
    {
      // TODO Auto-generated catch block
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      return;
    }

    while (!isShutdownInitiated())
    {
      try
      {
        createNewCursors();

        final UpdateMsg msg = crossDomainDBCursor.getRecord();
        if (msg == null)
        {
          synchronized (this)
          {
            wait();
          }
          // advance cursor, success/failure will be checked later
          crossDomainDBCursor.next();
          // loop to check whether new changes have been added to the ReplicaDBs
          continue;
        }

        final CSN csn = msg.getCSN();
        final DN baseDN = crossDomainDBCursor.getData();
        // FIXME problem: what if the serverId is not part of the ServerState?
        // right now, thread will be blocked
        if (!mediumConsistencyPoint.cover(baseDN, csn))
        {
          // the oldest record to insert is newer than the medium consistency
          // point. Let's wait for a change that can be published.
          synchronized (this)
          {
            // double check to protect against a missed call to notify()
            if (!mediumConsistencyPoint.cover(baseDN, csn))
            {
              wait();
              // loop to check if changes older than the medium consistency
              // point have been added to the ReplicaDBs
              continue;
            }
          }
        }

        // OK, the oldest change is older than the medium consistency point
        // let's publish it to the CNIndexDB
        final ChangeNumberIndexRecord record =
            new ChangeNumberIndexRecord(previousCookie.toString(), baseDN, csn);
        changelogDB.getChangeNumberIndexDB().addRecord(record);
        // update, so it becomes the previous cookie for the next change
        previousCookie.update(baseDN, csn);

        // advance cursor, success/failure will be checked later
        crossDomainDBCursor.next();
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        // TODO JNR error message i18n
      }
      catch (InterruptedException ignored)
      {
        // was shutdown called?
      }
    }
  }

  private void createNewCursors() throws ChangelogException
  {
    if (!newCursors.isEmpty())
    {
      boolean newCursorAdded = false;
      for (Iterator<Entry<Integer, DN>> iter = newCursors.entrySet().iterator();
          iter.hasNext();)
      {
        final Entry<Integer, DN> entry = iter.next();
        if (!ensureCursorExists(entry.getValue(), entry.getKey(), null))
        {
          newCursorAdded = true;
        }
        iter.remove();
      }
      if (newCursorAdded)
      {
        crossDomainDBCursor = newCompositeDBCursor();
      }
    }
  }

}
