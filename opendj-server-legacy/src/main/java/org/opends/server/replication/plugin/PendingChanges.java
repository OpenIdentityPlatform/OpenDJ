/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.annotations.VisibleForTesting;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.operation.PluginOperation;

/**
 * This class is used to store the list of local operations currently
 * in progress and not yet committed in the database.
 * <p>
 * It is used to make sure that operations are sent to the Replication Server
 * in the order defined by their CSN. It is also used to update the ServerState
 * at the appropriate time.
 * <p>
 * An object of this class is instantiated for each ReplicationDomain.
 */
class PendingChanges
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * How many local changes a recovery keeps at most, and how many bytes their messages take at
   * most: past either, it keeps none, and searches for them as for the changes done before it began.
   */
  static final int MAX_KEPT_CHANGES = 10000;
  static final long MAX_KEPT_BYTES = 32L * 1024 * 1024;

  /** A map used to store the pending changes. */
  private final TreeMap<CSN, PendingChange> pendingChanges = new TreeMap<>();

  /**
   * The {@link CSNGenerator} to use to create new unique CSNs
   * for each operation done on the replication domain.
   */
  private final CSNGenerator csnGenerator;

  /** The ReplicationDomain that will be used to send UpdateMsg. */
  private final ReplicationDomain domain;

  /** Told that the replica of this domain announces itself offline, or takes that back. */
  private final ReplicaOfflineAnnouncer replicaOfflineAnnouncer;

  /**
   * Changed under the lock the domain starts and ends a recovery under - and ended under the lock of
   * this list as well - and read without either by {@link #isRecovering()}.
   */
  private volatile boolean recoveringOldChanges;
  /** What the recovery which runs keeps of the local changes: {@code null} until it first needs it. */
  private Recovery recovery;
  /** How many local changes a recovery keeps at most: fewer in the tests which make it run over. */
  private int maxKeptChanges = MAX_KEPT_CHANGES;

  /**
   * Creates a new PendingChanges using the provided CSNGenerator.
   *
   * @param csnGenerator The CSNGenerator to use to create new unique CSNs.
   * @param domain  The ReplicationDomain that will be used to send UpdateMsg.
   * @param replicaOfflineAnnouncer Told that the replica of this domain announces itself
   *                  offline, before the message announcing it is published, and that it takes
   *                  the announcement back when the broker refused the message.
   */
  PendingChanges(CSNGenerator csnGenerator, ReplicationDomain domain,
      ReplicaOfflineAnnouncer replicaOfflineAnnouncer)
  {
    this.csnGenerator = csnGenerator;
    this.domain = domain;
    this.replicaOfflineAnnouncer = replicaOfflineAnnouncer;
  }

  /**
   * Remove and return an update form the pending changes list.
   *
   * @param csn
   *          The CSN of the update to remove.
   */
  synchronized void remove(CSN csn)
  {
    pendingChanges.remove(csn);
  }

  /**
   * Returns the number of update currently in the list.
   *
   * @return The number of update currently in the list.
   */
  int size()
  {
    return pendingChanges.size();
  }

  /**
   * Mark an update message as committed.
   *
   * @param csn The CSN of the update message that must be set as committed.
   * @param msg The message associated to the update.
   */
  private synchronized void commit(CSN csn, LDAPUpdateMsg msg)
  {
    final PendingChange curChange = pendingChanges.get(csn);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);
    curChange.setMsg(msg);
  }

  /**
   * Add a new UpdateMsg to the pending list from the provided local operation.
   *
   * @param operation The local operation for which an UpdateMsg must
   *                  be added in the pending list.
   * @return The CSN now associated to the operation.
   */
  synchronized CSN putLocalOperation(PluginOperation operation)
  {
    final CSN csn = csnGenerator.newCSN();
    if (!operation.isSynchronizationOperation())
    {
      pendingChanges.put(csn, new PendingChange(csn, operation, null));
    }
    return csn;
  }

  /**
   * Add a replica offline message to the pending list and publish it, if the changes which
   * come before it have all been published.
   * <p>
   * The message carries the newest CSN of the replica, so a change which is still in flight
   * holds it back - and there is nobody left to publish it afterwards: the caller announces
   * the replica offline while its service is being disabled, and the broker stops right after.
   * Such a message is given up on rather than left queued, so that it is neither reported as
   * sent nor published later on the session which follows.
   *
   * @return the CSN of the message which was published, or {@code null} if it could not be:
   *         a change which is still in flight holds it back, or the broker refused it
   */
  public synchronized CSN putReplicaOfflineMsg()
  {
    final CSN offlineCSN = csnGenerator.newCSN();
    final PendingChange pendingChange =
        new PendingChange(offlineCSN, null, new ReplicaOfflineMsg(offlineCSN));
    pendingChange.setCommitted(true);

    pendingChanges.put(offlineCSN, pendingChange);
    /*
     * The message is the last change of the queue, so a push which did not reach it, or which
     * the broker refused, reports another CSN or none.
     */
    final boolean published = offlineCSN.equals(pushCommittedChanges());
    // pushCommittedChanges() removes whatever it reached, so the message is still listed here
    // if and only if a change before it held it back - it is dropped rather than left queued.
    pendingChanges.remove(offlineCSN);
    return published ? offlineCSN : null;
  }

  /**
   * Push all committed local changes to the replicationServer service.
   *
   * @return the CSN of the last {@link ReplicaOfflineMsg} the replication service accepted, or
   *         {@code null} if none was pushed or the broker refused it. The announcement that a
   *         replica goes offline is the one message whose delivery the caller must know about:
   *         it is stored nowhere, so nothing publishes it again, while a change the broker
   *         refuses is republished from the historical information of its entry on the next
   *         session.
   */
  synchronized CSN pushCommittedChanges()
  {
    CSN publishedOfflineCSN = null;

    // peek the oldest change
    Entry<CSN, PendingChange> firstEntry = pendingChanges.firstEntry();
    if (firstEntry == null)
    {
      return null;
    }

    PendingChange firstChange = firstEntry.getValue();

    while (firstChange != null && firstChange.isCommitted())
    {
      final PluginOperation op = firstChange.getOp();
      final UpdateMsg msg = firstChange.getMsg();
      if (msg instanceof LDAPUpdateMsg
          && op != null
          && !op.isSynchronizationOperation())
      {
        if (!recoveringOldChanges)
        {
          domain.publish(msg);
        }
        else
        {
          // do not push updates until the RS catches up: the recovery publishes them.
          // Kept before the state takes it: the first change kept fixes where the search of the
          // recovery ends, at the last change pushed before it.
          // @see #setRecovering(boolean)
          recovery().keep((LDAPUpdateMsg) msg);
          domain.getServerState().update(msg.getCSN());
        }
      }
      else if (msg instanceof ReplicaOfflineMsg && recoveringOldChanges)
      {
        /*
         * The announcement carries the newest CSN of this replica, past the changes the recovery
         * has still to publish: the replication server would take this replica for offline from
         * there - its changelog index past the changes which reach it afterwards. It is given up
         * on, as one a change in flight holds back is, whatever the broker would refuse.
         */
        if (logger.isTraceEnabled())
        {
          logger.trace("The announcement that the replica goes offline at " + msg.getCSN()
              + " is given up on: a recovery has changes before it to publish");
        }
      }
      else if (msg instanceof ReplicaOfflineMsg)
      {
        /*
         * Announce the replica offline before the message reaches the wire, and not after:
         * a collocated replication server forwards it as soon as it has it, and a forward
         * which finds nothing announced leaves the shutdown waiting out the whole grace
         * period of a message the topology already has.
         */
        final CSN offlineCSN = msg.getCSN();
        replicaOfflineAnnouncer.announce(offlineCSN);
        if (domain.publish(msg))
        {
          publishedOfflineCSN = offlineCSN;
        }
        else
        {
          // The broker wrote it to no session, so nobody will forward what was announced.
          replicaOfflineAnnouncer.withdraw(offlineCSN);
        }
      }

      // false warning: firstEntry will not be null if firstChange is not null
      pendingChanges.remove(firstEntry.getKey());

      // peek the oldest change
      firstEntry = pendingChanges.firstEntry();
      firstChange = firstEntry != null ? firstEntry.getValue() : null;
    }
    return publishedOfflineCSN;
  }

  /**
   * Mark an update message as committed, then
   * push all committed local changes to the replicationServer service
   * in a single atomic operation.
   *
   * @param csn The CSN of the update message that must be set as committed.
   * @param msg The message associated to the update.
   */
  synchronized void commitAndPushCommittedChanges(CSN csn, LDAPUpdateMsg msg)
  {
    commit(csn, msg);
    pushCommittedChanges();
  }

  /**
   * Set the PendingChangesList structure in a mode where it is waiting for the
   * RS to receive all the previous changes to be sent before starting to
   * process the changes normally. In this mode, The Domain does not publish the
   * changes from the pendingChanges because there are older changes that need
   * to be published before.
   *
   * <p>
   * The local changes pushed meanwhile are kept for the recovery, which publishes them once it has
   * published the ones done before it began. A recovery which is already running goes on, with what
   * it kept: a session which starts while it runs starts no other.
   * <p>
   * It is set without the lock of this list, which the start of a session must not take, but under
   * the one the domain ends a recovery under as well: a recovery the session needs is not ended by
   * one which was about to end.
   *
   * @param recovering
   *          The recovering status that must be set.
   */
  public void setRecovering(boolean recovering)
  {
    recoveringOldChanges = recovering;
  }

  /**
   * Tells whether the local changes are left to a recovery, which publishes them.
   * <p>
   * It is read without the lock of this list, which the start of a session must not take: the
   * session only learns from it that it needs a missing change publisher to end the recovery,
   * and that one ends it under the lock.
   *
   * @return {@code true} if the local changes are left to a recovery
   */
  boolean isRecovering()
  {
    return recoveringOldChanges;
  }

  /**
   * Tells whether the recovery which runs keeps the local changes, and where its search of the
   * historical information ends then.
   *
   * @return the recovery which keeps the local changes, or {@code null} if its search has to go up
   *         to the last local change: no recovery runs, or it kept too many changes to keep any
   */
  synchronized KeptChanges keptChanges()
  {
    if (!recoveringOldChanges)
    {
      return null;
    }
    final Recovery running = recovery();
    return running.kept != null ? new KeptChanges(running.searchUpTo) : null;
  }

  /**
   * Returns the local changes the recovery which runs kept which come after the provided CSN, in
   * order.
   *
   * @param csn
   *          where the recovery has got to
   * @return the changes, or {@code null} if no recovery runs, or it kept too many changes to keep any
   */
  synchronized List<LDAPUpdateMsg> keptChangesAfter(CSN csn)
  {
    final KeptChanges keeping = keptChanges();
    return keeping != null ? new ArrayList<>(recovery.kept.tailMap(csn, false).values()) : null;
  }

  /**
   * Returns the local changes the recovery which runs kept which come after the provided CSN, in
   * order, whatever it searches for: the data the domain read its state from again may not reflect
   * them.
   *
   * @param newest
   *          the newest change of this replica the data holds, or {@code null} if it holds none
   * @return the changes, none if no recovery keeps any
   */
  synchronized List<LDAPUpdateMsg> keptChangesNewerThan(CSN newest)
  {
    if (recovery == null || recovery.kept == null)
    {
      return new ArrayList<>();
    }
    return new ArrayList<>((newest != null ? recovery.kept.tailMap(newest, false) : recovery.kept).values());
  }

  /**
   * Allows to update the recovery situation by comparing the CSN of the last
   * change that was sent to the ReplicationServer with the CSN of the last
   * operation that was taken out of the PendingChanges list. If the two match
   * then the recovery is completed and normal procedure can restart. Otherwise
   * the RSUpdater thread must continue to look for older changes and no changes
   * can be committed from the pendingChanges list.
   * <p>
   * The recovery ends at once for every change pushed after it: a change which comes here once
   * this list publishes again must find the broker taking it as well, or it is refused, while
   * the domain state records it as done and no recovery is left to publish it. The recovery is
   * therefore ended here, under the lock {@link #pushCommittedChanges()} takes, before any change
   * can be pushed. What it kept is dropped with it.
   *
   * @param recovered
   *          The CSN of the last change that was published to the
   *          ReplicationServer.
   * @param recoveryEnd
   *          Ends the recovery, unless a session which started meanwhile needs it, and has the
   *          broker take the changes again.
   * @return A boolean indicating if the recovery is completed (false) or must
   *         continue (true).
   */
  synchronized boolean recoveryUntil(CSN recovered, RecoveryEnd recoveryEnd)
  {
    final CSN lastLocalChange = domain.getLastLocalChange();
    if (recoveringOldChanges && recovered != null && recovered.isNewerThanOrEqualTo(lastLocalChange))
    {
      recoveryEnd.end(() ->
      {
        recoveringOldChanges = false;
        recovery = null;
      });
    }
    return recoveringOldChanges;
  }

  /**
   * Drops the provided local changes the recovery which runs kept: the data of the domain, read
   * again, does not hold them - it was restored or imported since they were done - and the replicas
   * would get changes this one does not hold.
   *
   * @param csns
   *          the CSNs of the changes to drop
   */
  synchronized void dropKeptChanges(Collection<CSN> csns)
  {
    if (recovery == null || recovery.kept == null || csns.isEmpty())
    {
      return;
    }
    recovery.kept.keySet().removeAll(csns);
    recovery.keptBytes = 0;
    for (LDAPUpdateMsg msg : recovery.kept.values())
    {
      recovery.keptBytes += msg.size();
    }
  }

  /**
   * Has the recovery which runs keep no more local changes, and drop the ones it kept: it searches
   * for them in the historical information instead, as one which kept too many does.
   */
  synchronized void keepNoMoreChanges()
  {
    if (recovery != null)
    {
      recovery.kept = null;
    }
  }

  /**
   * Returns what the recovery which runs keeps of the local changes, under the lock of this list.
   * It is made as it is first needed - as the recovery keeps its first change, or as its publisher
   * asks where its search ends - so the search ends at the last change pushed before any was kept.
   */
  private Recovery recovery()
  {
    if (recovery == null)
    {
      recovery = new Recovery(domain.getLastLocalChange());
    }
    return recovery;
  }

  /**
   * Sets how many local changes a recovery which starts from now on keeps at most: with none, it
   * searches for all of them in the historical information.
   *
   * @param maxKeptChanges
   *          how many local changes a recovery keeps at most
   */
  @VisibleForTesting
  synchronized void setMaxKeptChanges(int maxKeptChanges)
  {
    this.maxKeptChanges = maxKeptChanges;
  }

  /**
   * What a recovery keeps of the local changes pushed while it runs.
   * <p>
   * A recovery republishes what the replication server misses from the historical information of
   * the entries, and its search of it races the commits: a change committed while it searches is in
   * the domain state, and not in what it found. The changes pushed once it began are kept instead,
   * in order and with their messages, so that its search only has to cover the ones pushed before:
   * up to the last of them, which no commit moves. A change kept is published as it was done, where
   * a later change of the same values leaves nothing of it in the historical information, and a
   * delete kept does not depend on the deletes the domain keeps aside, which are capped.
   * <p>
   * What it keeps stays until it ends: a missing change publisher whose broker refused a change,
   * or whose session was replaced by one whose replication server holds less, leaves them to the
   * one which goes on, which may start from further back. Past {@link #maxKeptChanges} changes or
   * {@link #MAX_KEPT_BYTES} bytes it keeps none, and the recovery searches for them from where it
   * has got to, holding the local changes back at the end, as it does when nothing is kept.
   */
  private final class Recovery
  {
    /** The last local change pushed before this recovery kept any: its search ends there. */
    private final CSN searchUpTo;
    /** The local changes pushed since, in order; {@code null} once there were too many. */
    private TreeMap<CSN, LDAPUpdateMsg> kept;
    private long keptBytes;

    private Recovery(CSN searchUpTo)
    {
      this.searchUpTo = searchUpTo;
      this.kept = maxKeptChanges > 0 ? new TreeMap<CSN, LDAPUpdateMsg>() : null;
    }

    private void keep(LDAPUpdateMsg msg)
    {
      if (kept == null)
      {
        return;
      }
      /*
       * Published as a recovery publishes what it rebuilds: not assured. Its operation may still be
       * waiting for the acknowledgement when a round publishes it - nothing orders the two - and it
       * then gets none and times out, as it does when the round comes later: it waits as the domain
       * is configured to, on the CSN of the message, and not as the message says.
       */
      if (msg.isAssured())
      {
        msg.setNotAssured();
      }
      kept.put(msg.getCSN(), msg);
      keptBytes += msg.size();
      if (kept.size() > maxKeptChanges || keptBytes > MAX_KEPT_BYTES)
      {
        kept = null;
        if (logger.isDebugEnabled())
        {
          logger.debug(LocalizableMessage.raw("the recovery keeps no more local changes after " + msg.getCSN()
              + ": it searches for them in the historical information"));
        }
      }
    }
  }

  /** A recovery which keeps the local changes pushed while it runs. */
  static final class KeptChanges
  {
    /**
     * The last local change pushed before the recovery kept any: its search ends there, and the
     * changes it kept come next. {@code null} if the domain state held no change of this replica.
     */
    final CSN searchUpTo;

    private KeptChanges(CSN searchUpTo)
    {
      this.searchUpTo = searchUpTo;
    }
  }

  /** Ends a recovery the pending changes have caught up with. */
  interface RecoveryEnd
  {
    /**
     * Ends the recovery, unless a session which started meanwhile has given it changes of its own
     * to recover: runs {@code recovered} under the lock a recovery is started under, and has the
     * broker take the changes again. Called under the lock of the pending changes.
     *
     * @param recovered
     *          lets the pending changes publish the local changes again
     */
    void end(Runnable recovered);
  }

  /**
   * Runs the provided rounds of a recovery while no local change can be numbered or pushed.
   * <p>
   * A round publishes the changes done when it began, and a change done while it runs is left
   * to a round after it, so a recovery could chase the local changes for as long as they go on.
   * The rounds run here are the last ones: what they publish is all there is, and nothing can be
   * pushed between their end and the end of the recovery they decide. The local operations wait
   * for them - for their CSN and for their push - for as long as publishing the changes done while
   * the round before ran takes, where a publish in normal operation holds the same lock for one
   * change.
   *
   * @param <T>
   *          what the rounds return
   * @param round
   *          the rounds of the recovery
   * @return what the round returned
   * @throws Exception
   *           if the round failed
   */
  synchronized <T> T holdBackLocalChangesWhile(Callable<T> round) throws Exception
  {
    return round.call();
  }

  /**
   * Told that the replica of this domain announces itself offline, or takes that back.
   * <p>
   * A collocated replication server can forward a {@link ReplicaOfflineMsg} as soon as it is on
   * the wire, and its shutdown waits for that forward, so the announcement has to be in place
   * before the message is published: one made afterwards is one the forward found nothing to
   * clear, and the shutdown spends its whole grace period on a message which has already gone
   * out. The broker may still refuse the message once it is announced, and then the
   * announcement is withdrawn: what stays announced is what the broker reports as written.
   */
  interface ReplicaOfflineAnnouncer
  {
    /**
     * Announces that the replica goes offline at the provided CSN.
     *
     * @param offlineCSN
     *          the CSN of the ReplicaOfflineMsg which is about to be published
     */
    void announce(CSN offlineCSN);

    /**
     * Withdraws the announcement of a message the broker refused: it was written to no session,
     * so nobody will forward it.
     *
     * @param offlineCSN
     *          the CSN of the ReplicaOfflineMsg which was announced and not published
     */
    void withdraw(CSN offlineCSN);
  }
}
