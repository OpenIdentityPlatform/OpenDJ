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
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.ChangelogBackend;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.types.DN;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Thread responsible for inserting replicated changes into the ChangeNumber
 * Index DB (CNIndexDB for short).
 * <p>
 * Only changes older than the medium consistency point are inserted in the
 * CNIndexDB. As a consequence this class is also responsible for maintaining
 * the medium consistency point (indirectly through an
 * {@code ECLMultiDomainDBCursor}).
 */
public class ChangeNumberIndexer extends DirectoryThread
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * If it contains nothing, then the run method executes normally.
   * Otherwise, the {@link #run()} method must clear its state
   * for the supplied domain baseDNs. If a supplied domain is
   * {@link DN#NULL_DN}, then all domains will be cleared.
   */
  private final ConcurrentSkipListSet<DN> domainsToClear =
      new ConcurrentSkipListSet<DN>();
  private final ChangelogDB changelogDB;
  /** Only used for initialization, and then discarded. */
  private ChangelogState changelogState;
  private final ECLEnabledDomainPredicate predicate;

  /*
   * The following MultiDomainServerState fields must be thread safe, because
   * 1) initialization can happen while the replication server starts receiving
   * updates
   * 2) many updates can happen concurrently.
   */
  /**
   * Holds the last time each replica was seen alive, whether via updates or
   * heartbeat notifications, or offline notifications. Data is held for each
   * serverId cross domain.
   * <p>
   * Updates are persistent and stored in the replicaDBs, heartbeats are
   * transient and are easily constructed on normal operations.
   * <p>
   * Note: This object is updated by both heartbeats and changes/updates.
   */
  private final MultiDomainServerState lastAliveCSNs = new MultiDomainServerState();

  /** Note: This object is updated by replica offline messages. */
  private final MultiDomainServerState replicasOffline = new MultiDomainServerState();

  /**
   * Cursor across all the replicaDBs for all the replication domains. It is
   * positioned on the next change that needs to be inserted in the CNIndexDB.
   * <p>
   * Note: it is only accessed from the {@link #run()} method.
   *
   * @NonNull
   */
  private ECLMultiDomainDBCursor nextChangeForInsertDBCursor;

  /**
   * Builds a ChangeNumberIndexer object.
   *
   * @param changelogDB
   *          the changelogDB
   * @param changelogState
   *          the changelog state used for initialization
   */
  public ChangeNumberIndexer(ChangelogDB changelogDB, ChangelogState changelogState)
  {
    this(changelogDB, changelogState, new ECLEnabledDomainPredicate());
  }

  /**
   * Builds a ChangeNumberIndexer object.
   *
   * @param changelogDB
   *          the changelogDB
   * @param changelogState
   *          the changelog state used for initialization
   * @param predicate
   *          tells whether a domain is enabled for the external changelog
   */
  ChangeNumberIndexer(ChangelogDB changelogDB, ChangelogState changelogState,
      ECLEnabledDomainPredicate predicate)
  {
    super("Change number indexer");
    this.changelogDB = changelogDB;
    this.changelogState = changelogState;
    this.predicate = predicate;
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
    if (!predicate.isECLEnabledDomain(baseDN))
    {
      return;
    }

    final CSN oldestCSNBefore = getOldestLastAliveCSN();
    lastAliveCSNs.update(baseDN, heartbeatCSN);
    tryNotify(oldestCSNBefore);
  }

  /**
   * Indicates if the replica corresponding to provided domain DN and server id
   * is offline.
   *
   * @param domainDN
   *          base DN of the replica
   * @param serverId
   *          server id of the replica
   * @return {@code true} if replica is offline, {@code false} otherwise
   */
  public boolean isReplicaOffline(DN domainDN, int serverId)
  {
    return replicasOffline.getCSN(domainDN, serverId) != null;
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
    if (!predicate.isECLEnabledDomain(baseDN))
    {
      return;
    }

    final CSN oldestCSNBefore = getOldestLastAliveCSN();
    lastAliveCSNs.update(baseDN, updateMsg.getCSN());
    tryNotify(oldestCSNBefore);
  }

  /**
   * Signals a replica went offline.
   *
   * @param baseDN
   *          the replica's replication domain
   * @param offlineCSN
   *          the serverId and time of the replica that went offline
   */
  public void replicaOffline(DN baseDN, CSN offlineCSN)
  {
    if (!predicate.isECLEnabledDomain(baseDN))
    {
      return;
    }

    replicasOffline.update(baseDN, offlineCSN);
    final CSN oldestCSNBefore = getOldestLastAliveCSN();
    lastAliveCSNs.update(baseDN, offlineCSN);
    tryNotify(oldestCSNBefore);
  }

  private CSN getOldestLastAliveCSN()
  {
    return lastAliveCSNs.getOldestCSNExcluding(replicasOffline).getSecond();
  }

  /**
   * Notifies the Change number indexer thread if it will be able to do some
   * work.
   */
  private void tryNotify(final CSN oldestCSNBefore)
  {
    if (mightMoveForwardMediumConsistencyPoint(oldestCSNBefore))
    {
      synchronized (this)
      {
        notify();
      }
    }
  }

  /**
   * Used for waking up the {@link ChangeNumberIndexer} thread because it might
   * have some work to do.
   */
  private boolean mightMoveForwardMediumConsistencyPoint(CSN oldestCSNBefore)
  {
    final CSN oldestCSNAfter = getOldestLastAliveCSN();
    // ensure that all initial replicas alive information have been updated
    // with CSNs that are acceptable for moving the medium consistency forward
    return allInitialReplicasAreOfflineOrAlive()
        && oldestCSNBefore != null // then oldestCSNAfter cannot be null
        // has the oldest CSN changed?
        && oldestCSNBefore.isOlderThan(oldestCSNAfter);
  }

  /**
   * Used by the {@link ChangeNumberIndexer} thread to determine whether the CSN
   * must be persisted to the change number index DB.
   */
  private boolean canMoveForwardMediumConsistencyPoint(CSN nextCSNToPersist)
  {
    // ensure that all initial replicas alive information have been updated
    // with CSNs that are acceptable for moving the medium consistency forward
    return allInitialReplicasAreOfflineOrAlive()
        // can we persist the next CSN?
        && nextCSNToPersist.isOlderThanOrEqualTo(getOldestLastAliveCSN());
  }

  /**
   * Returns true only if the initial replicas known from the changelog state DB
   * are either:
   * <ul>
   * <li>offline, so do not wait for them in order to compute medium consistency
   * </li>
   * <li>alive, because we received heartbeats or changes (so their last alive
   * CSN has been updated to something past the oldest possible CSN), we have
   * enough info to compute medium consistency</li>
   * </ul>
   * In both cases, we have enough information to compute medium consistency
   * without waiting any further.
   */
  private boolean allInitialReplicasAreOfflineOrAlive()
  {
    for (DN baseDN : lastAliveCSNs)
    {
      for (CSN csn : lastAliveCSNs.getServerState(baseDN))
      {
        if (csn.getTime() == 0
            && replicasOffline.getCSN(baseDN, csn.getServerId()) == null)
        {
          // this is the oldest possible CSN, but the replica is not offline
          // we must wait for more up to date information from this replica
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Restores in memory data needed to build the CNIndexDB. In particular,
   * initializes the changes cursor to the medium consistency point.
   */
  private void initialize() throws ChangelogException
  {
    final ReplicationDomainDB domainDB = changelogDB.getReplicationDomainDB();

    initializeLastAliveCSNs(domainDB);
    initializeNextChangeCursor(domainDB);
    initializeOfflineReplicas();

    // this will not be used any more. Discard for garbage collection.
    this.changelogState = null;
  }

  private void initializeNextChangeCursor(final ReplicationDomainDB domainDB) throws ChangelogException
  {
    final MultiDomainServerState cookieWithNewestCSN = getCookieInitializedWithNewestCSN();

    MultiDomainDBCursor cursorInitializedToMediumConsistencyPoint =
        domainDB.getCursorFrom(cookieWithNewestCSN, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY);

    nextChangeForInsertDBCursor = new ECLMultiDomainDBCursor(predicate, cursorInitializedToMediumConsistencyPoint);
    nextChangeForInsertDBCursor.next();
  }

  /** Returns a cookie initialised with the newest CSN for each replica. */
  private MultiDomainServerState getCookieInitializedWithNewestCSN() throws ChangelogException
  {
    final ChangeNumberIndexRecord newestRecord = changelogDB.getChangeNumberIndexDB().getNewestRecord();
    final MultiDomainServerState cookieWithNewestCSN = new MultiDomainServerState();
    if (newestRecord != null)
    {
      final CSN newestCsn = newestRecord.getCSN();
      for (DN baseDN : changelogState.getDomainToServerIds().keySet())
      {
        cookieWithNewestCSN.update(baseDN, newestCsn);
      }
    }
    return cookieWithNewestCSN;
  }

  private void initializeLastAliveCSNs(final ReplicationDomainDB domainDB)
  {
    for (Entry<DN, Set<Integer>> entry : changelogState.getDomainToServerIds().entrySet())
    {
      final DN baseDN = entry.getKey();
      if (predicate.isECLEnabledDomain(baseDN))
      {
        for (Integer serverId : entry.getValue())
        {
          /*
           * initialize with the oldest possible CSN in order for medium
           * consistency to wait for all replicas to be alive before moving forward
           */
          lastAliveCSNs.update(baseDN, oldestPossibleCSN(serverId));
        }

        final ServerState latestKnownState = domainDB.getDomainNewestCSNs(baseDN);
        lastAliveCSNs.update(baseDN, latestKnownState);
      }
    }
  }

  private void initializeOfflineReplicas()
  {
    final MultiDomainServerState offlineReplicas = changelogState.getOfflineReplicas();
    for (DN baseDN : offlineReplicas)
    {
      for (CSN offlineCSN : offlineReplicas.getServerState(baseDN))
      {
        if (predicate.isECLEnabledDomain(baseDN))
        {
          replicasOffline.update(baseDN, offlineCSN);
          // a replica offline message could also be the very last time
          // we heard from this replica :)
          lastAliveCSNs.update(baseDN, offlineCSN);
        }
      }
    }
  }

  private CSN oldestPossibleCSN(int serverId)
  {
    return new CSN(0, 0, serverId);
  }

  /** {@inheritDoc} */
  @Override
  public void initiateShutdown()
  {
    super.initiateShutdown();
    synchronized (this)
    {
      notify();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    try
    {
      /*
       * initialize here to allow fast application start up and avoid errors due
       * cursors being created in a different thread to the one where they are used.
       */
      initialize();

      while (!isShutdownInitiated())
      {
        try
        {
          while (!domainsToClear.isEmpty())
          {
            final DN baseDNToClear = domainsToClear.first();
            nextChangeForInsertDBCursor.removeDomain(baseDNToClear);
            // Only release the waiting thread
            // once this domain's state has been cleared.
            domainsToClear.remove(baseDNToClear);
          }

          // Do not call DBCursor.next() here
          // because we might not have consumed the last record,
          // for example if we could not move the MCP forward
          final UpdateMsg msg = nextChangeForInsertDBCursor.getRecord();
          if (msg == null)
          {
            synchronized (this)
            {
              if (isShutdownInitiated())
              {
                continue;
              }
              wait();
            }
            // check whether new changes have been added to the ReplicaDBs
            nextChangeForInsertDBCursor.next();
            continue;
          }
          else if (msg instanceof ReplicaOfflineMsg)
          {
            nextChangeForInsertDBCursor.next();
            continue;
          }

          final CSN csn = msg.getCSN();
          final DN baseDN = nextChangeForInsertDBCursor.getData();
          // FIXME problem: what if the serverId is not part of the ServerState?
          // right now, change number will be blocked
          if (!canMoveForwardMediumConsistencyPoint(csn))
          {
            // the oldest record to insert is newer than the medium consistency
            // point. Let's wait for a change that can be published.
            synchronized (this)
            {
              // double check to protect against a missed call to notify()
              if (!canMoveForwardMediumConsistencyPoint(csn))
              {
                if (isShutdownInitiated())
                {
                  return;
                }
                wait();
                // loop to check if changes older than the medium consistency
                // point have been added to the ReplicaDBs
                continue;
              }
            }
          }

          // OK, the oldest change is older than the medium consistency point
          // let's publish it to the CNIndexDB.
          final long changeNumber = changelogDB.getChangeNumberIndexDB()
              .addRecord(new ChangeNumberIndexRecord(baseDN, csn));
          MultiDomainServerState cookie = nextChangeForInsertDBCursor.toCookie();
          notifyEntryAddedToChangelog(baseDN, changeNumber, cookie, msg);
          moveForwardMediumConsistencyPoint(csn, baseDN);
        }
        catch (InterruptedException ignored)
        {
          // was shutdown called? loop to figure it out.
          Thread.currentThread().interrupt();
        }
      }
    }
    catch (RuntimeException e)
    {
      logUnexpectedException(e);
      // Rely on the DirectoryThread uncaught exceptions handler for logging error + alert.
      throw e;
    }
    catch (Exception e)
    {
      logUnexpectedException(e);
      // Rely on the DirectoryThread uncaught exceptions handler for logging error + alert.
      throw new RuntimeException(e);
    }
    finally
    {
      nextChangeForInsertDBCursor.close();
      nextChangeForInsertDBCursor = null;
    }
  }

  /**
   * Notifies the {@link ChangelogBackend} that a new entry has been added.
   *
   * @param baseDN
   *          the baseDN of the newly added entry.
   * @param changeNumber
   *          the change number of the newly added entry. It will be greater
   *          than zero for entries added to the change number index and less
   *          than or equal to zero for entries added to any replica DB
   * @param cookie
   *          the cookie of the newly added entry. This is only meaningful for
   *          entries added to the change number index
   * @param msg
   *          the update message of the newly added entry
   * @throws ChangelogException
   *           If a problem occurs while notifying of the newly added entry.
   */
  protected void notifyEntryAddedToChangelog(DN baseDN, long changeNumber,
      MultiDomainServerState cookie, UpdateMsg msg) throws ChangelogException
  {
    ChangelogBackend.getInstance().notifyChangeNumberEntryAdded(baseDN, changeNumber, cookie.toString(), msg);
  }

  /**
   * Nothing can be done about it.
   * <p>
   * Rely on the DirectoryThread uncaught exceptions handler for logging error +
   * alert.
   * <p>
   * Message logged here gives corrective information to the administrator.
   */
  private void logUnexpectedException(Exception e)
  {
    logger.trace(ERR_CHANGE_NUMBER_INDEXER_UNEXPECTED_EXCEPTION,
        getClass().getSimpleName(), stackTraceToSingleLineString(e));
  }

  private void moveForwardMediumConsistencyPoint(final CSN mcCSN, final DN mcBaseDN) throws ChangelogException
  {
    final int mcServerId = mcCSN.getServerId();
    final CSN offlineCSN = replicasOffline.getCSN(mcBaseDN, mcServerId);
    final CSN lastAliveCSN = lastAliveCSNs.getCSN(mcBaseDN, mcServerId);
    if (offlineCSN != null)
    {
      if (lastAliveCSN != null && offlineCSN.isOlderThan(lastAliveCSN))
      {
        // replica is back online, we can forget the last time it was offline
        replicasOffline.removeCSN(mcBaseDN, offlineCSN);
      }
      else if (offlineCSN.isOlderThan(mcCSN))
      {
        /*
         * replica is not back online, Medium consistency point has gone past
         * its last offline time, and there are no more changes after the
         * offline CSN in the cursor: remove everything known about it
         * (offlineCSN from lastAliveCSN and remove all knowledge of this replica
         * from the medium consistency RUV).
         */
        lastAliveCSNs.removeCSN(mcBaseDN, offlineCSN);
      }
    }

    // advance the cursor we just read from,
    // success/failure will be checked later
    nextChangeForInsertDBCursor.next();
  }

  /**
   * Asks the current thread to clear its state for the specified domain.
   * <p>
   * Note: This method blocks the current thread until state is cleared.
   *
   * @param baseDN the baseDN to be cleared from this thread's state.
   *               {@code null} and {@link DN#NULL_DN} mean "clear all domains".
   */
  public void clear(DN baseDN)
  {
    // Use DN.NULL_DN to say "clear all domains"
    final DN baseDNToClear = baseDN != null ? baseDN : DN.NULL_DN;
    domainsToClear.add(baseDNToClear);
    while (domainsToClear.contains(baseDNToClear)
        && !State.TERMINATED.equals(getState()))
    {
      // wait until clear() has been done by thread, always waking it up
      synchronized (this)
      {
        notify();
      }
      // ensures thread wait that this thread's state is cleaned up
      Thread.yield();
    }
  }

}
