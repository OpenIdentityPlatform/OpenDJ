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

import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.Callable;

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

  /** Changed under the lock of this list, except to start a recovery; read without it by {@link #isRecovering()}. */
  private volatile boolean recoveringOldChanges;

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
          // do not push updates until the RS catches up.
          // @see #setRecovering(boolean)
          domain.getServerState().update(msg.getCSN());
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
   * Allows to update the recovery situation by comparing the CSN of the last
   * change that was sent to the ReplicationServer with the CSN of the last
   * operation that was taken out of the PendingChanges list. If the two match
   * then the recovery is completed and normal procedure can restart. Otherwise
   * the RSUpdater thread must continue to look for older changes and no changes
   * can be committed from the pendingChanges list.
   * <p>
   * The recovery ends at once for every change pushed after it: a change which comes here once
   * this list publishes again must find the broker taking it as well, or it is refused, while
   * the domain state records it as done and no recovery is left to publish it.
   * {@code onRecovered} is therefore run here, under the lock {@link #pushCommittedChanges()}
   * takes, before any change can be pushed.
   *
   * @param recovered
   *          The CSN of the last change that was published to the
   *          ReplicationServer.
   * @param onRecovered
   *          Run when this call ends the recovery, before any change is pushed after it: it
   *          lets the broker take the changes again.
   * @return A boolean indicating if the recovery is completed (false) or must
   *         continue (true).
   */
  synchronized boolean recoveryUntil(CSN recovered, Runnable onRecovered)
  {
    final CSN lastLocalChange = domain.getLastLocalChange();
    if (recoveringOldChanges && recovered != null && recovered.isNewerThanOrEqualTo(lastLocalChange))
    {
      recoveringOldChanges = false;
      onRecovered.run();
    }
    return recoveringOldChanges;
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
