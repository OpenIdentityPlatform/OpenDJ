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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static java.util.Collections.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.jcip.annotations.GuardedBy;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.types.Operation;

/**
 * This class is used to store the list of remote changes received
 * from a replication server and that are either currently being replayed
 * or that are waiting for being replayed.
 *
 * It is used to know when the ServerState must be updated and to compute
 * the dependencies between operations.
 *
 * One of this object is instantiated for each ReplicationDomain.
 */
final class RemotePendingChanges
{
  /** A map used to store the pending changes. */
  @GuardedBy("pendingChangesLock")
  private final SortedMap<CSN, PendingChange> pendingChanges = new TreeMap<>();

  /**
   * A sorted set containing the list of PendingChanges that have
   * not been replayed correctly because they are dependent on
   * another change to be completed.
   */
  @GuardedBy("dependentChangesLock")
  private final SortedSet<PendingChange> dependentChanges = new TreeSet<>();
  /**
   * Uncommitted changes, registered before enqueue. A queued change whose hand-off
   * fails must remain a predecessor: otherwise its DELETE can run before its ADD retry.
   */
  private final ConcurrentSkipListSet<PendingChange> replayDependencies = new ConcurrentSkipListSet<>();

  /**
   * The change each replay thread is replaying right now, read by the give-back on the way
   * out of a replay which was unwound.
   * <p>
   * It is an index of what {@link PendingChange#isOwnedBy(Thread)} already says rather than
   * a second copy of it: it is written in the same locked step wherever a change is taken
   * over or given back, and every road which acts on what it answers checks the ownership
   * of the change again. What it buys is the read. That read is made on a road an
   * {@code OutOfMemoryError} leads to, and looking the change up by walking the pending
   * changes takes two locks and allocates an iterator - an allocation a JVM which has just
   * refused one may well refuse again, and the change would then be left listed,
   * uncommitted and owned by a thread which is not replaying it anymore, which is the wedge
   * this issue is about (issue #922).
   * <p>
   * A thread is entered here when it takes a change over and removed when it gives it back,
   * applies it, or parks it as waiting for another change - a parked one is handed out by
   * {@link #getNextUpdate()} to whichever thread calls it first once the changes before it
   * are gone, which enters it here again, and giving it back is
   * {@link #releaseParkedChangesOwnedByCurrentThread()}, which reads the changes which are
   * waiting rather than this index (issue #954).
   * <p>
   * The entry of a thread is written by that thread and by nobody else, and that - not the
   * lock - is what keeps the writes apart: the park in {@link #addDependency(PendingChange)}
   * clears it under the read lock, where every other writer holds the write lock, and it
   * races nothing for it. {@link #clear()} is the one writer of every entry, and it holds
   * both locks.
   */
  private final ConcurrentMap<Thread, CSN> changeBeingReplayed;

  private final ReentrantReadWriteLock pendingChangesLock = new ReentrantReadWriteLock(true);
  private final ReentrantReadWriteLock.ReadLock pendingChangesReadLock = pendingChangesLock.readLock();
  private final ReentrantReadWriteLock.WriteLock pendingChangesWriteLock = pendingChangesLock.writeLock();
  private final ReentrantLock dependentChangesLock = new ReentrantLock();

  /** The ServerState that will be updated when LDAPUpdateMsg are fully replayed. */
  private final ServerState state;
  private final int localServerId;
  /** Own-origin replay progress; local publication must not cover restore catch-up. */
  @GuardedBy("pendingChangesLock")
  private CSN localReplayCSN;

  /** Whether a delivery needs replay or how its duplicate should be completed. */
  enum Admission
  {
    ACCEPTED, QUEUED_DUPLICATE, DUPLICATE
  }

  /**
   * How many of the changes listed here have a replay failure recorded against them.
   * <p>
   * A change is counted from its first failed replay until it leaves this map, and it is
   * what tells a change which can not be applied from a backend which is serving again:
   * an outage fails everything in flight, while a change which can never be applied here
   * fails alone, among changes which replay perfectly well. The session restart backoff
   * reads it, so that the successful replays which surround a failing change do not keep
   * resetting the wait this domain has reached on it (issue #889).
   */
  @GuardedBy("pendingChangesLock")
  private int failingChanges;

  /**
   * Creates a new RemotePendingChanges using the provided ServerState.
   *
   * @param state   The ServerState that will be updated when LDAPUpdateMsg
   *                have been fully replayed.
   */
  public RemotePendingChanges(ServerState state)
  {
    this(state, -1);
  }

  RemotePendingChanges(ServerState state, int localServerId)
  {
    this(state, localServerId, new ConcurrentHashMap<Thread, CSN>());
  }

  RemotePendingChanges(ServerState state, int localServerId,
      ConcurrentMap<Thread, CSN> changeBeingReplayed)
  {
    this.state = state;
    this.localServerId = localServerId;
    this.changeBeingReplayed = changeBeingReplayed;
    resetReplayState();
  }

  /** Reloads the own-origin watermark only when the backend state is loaded. */
  void resetReplayState()
  {
    pendingChangesWriteLock.lock();
    try
    {
      localReplayCSN = state.getCSN(localServerId);
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Returns the number of changes waiting to be replayed.
   *
   * @return The number of changes waiting to be replayed
   */
  public int getQueueSize()
  {
    pendingChangesReadLock.lock();
    try
    {
      return pendingChanges.size();
    }
    finally
    {
      pendingChangesReadLock.unlock();
    }
  }

  /**
   * Returns the number of changes actively being replayed.
   * <p>
   * A change whose replay failed counts here until it is applied or given up on: it stays
   * a dependency of the changes which follow it, since a change which is not in the data
   * is exactly what they must wait for, and the delivery which comes next takes it over.
   *
   * @return the number of changes actively being replayed.
   */
  public int changesInProgressSize()
  {
    pendingChangesReadLock.lock();
    try
    {
      int count = 0;
      for (PendingChange change : replayDependencies)
      {
        if (change.hasStartedReplay())
        {
          count++;
        }
      }
      return count;
    }
    finally
    {
      pendingChangesReadLock.unlock();
    }
  }

  /**
   * Returns the number of changes depending on other changes.
   *
   * @return the number of changes depending on other changes.
   */
  public int getDependentChangesSize()
  {
    dependentChangesLock.lock();
    try
    {
      return dependentChanges.size();
    }
    finally
    {
      dependentChangesLock.unlock();
    }
  }

  /**
   * Add a new LDAPUpdateMsg that was received from the replication server
   * to the pendingList.
   * <p>
   * Changes covered by the applied ServerState are rejected even after they have left
   * the pending list. For this replica's own origin, only the state loaded from the backend
   * and subsequent replays count: a newer local publication does not prove that an older
   * change sent back after restore is in the data. A listed change is also left alone while its delivery is queued,
   * owned by a replay thread, or committed and waiting for earlier changes. Replacing a
   * queued delivery would move it behind newer changes: a DELETE could then be replayed
   * before its ADD, leaving the entry present once the ADD is replayed (issue #1269).
   * <p>
   * Only a delivery which was released after failed or abandoned replay, or cancelled
   * before being enqueued, may be replaced. It is deliberately left out of the
   * ServerState so the replication server sends it again over the restarted session
   * (issue #889). Neither an owner nor a queued delivery remains to finish it.
   * <p>
   * The failures the change went through are kept, whichever delivery replays it: they
   * are what has this replica eventually give up on a change it can not apply.
   *
   * @param update The LDAPUpdateMsg that was received from the replication
   *               server and that will be added to the pending list.
   * @return {@code false} if the change is already applied or has a delivery which is
   *         still queued, owned, or committed
   */
  public boolean putRemoteUpdate(LDAPUpdateMsg update)
  {
    return putRemoteUpdate(new UpdateToReplay(update, null)) == Admission.ACCEPTED;
  }

  Admission putRemoteUpdate(UpdateToReplay delivery)
  {
    final LDAPUpdateMsg update = delivery.getUpdateMessage();
    pendingChangesWriteLock.lock();
    try
    {
      final CSN csn = update.getCSN();
      if (csn.getServerId() == localServerId
          ? localReplayCSN != null && localReplayCSN.compareTo(csn) >= 0 : state.cover(csn))
      {
        return Admission.DUPLICATE;
      }
      final PendingChange listed = pendingChanges.get(csn);
      if (listed == null)
      {
        final PendingChange change = new PendingChange(csn, null, update);
        change.setDelivery(delivery);
        pendingChanges.put(csn, change);
        try
        {
          replayDependencies.add(change);
        }
        catch (RuntimeException | Error e)
        {
          // No other admission sees this partial registration while the write lock is held.
          pendingChanges.remove(csn);
          replayDependencies.remove(change);
          delivery.cancel();
          throw e;
        }
        return Admission.ACCEPTED;
      }
      if (listed.isCommitted() || listed.isOwned())
      {
        return Admission.DUPLICATE;
      }
      if (listed.isQueuedForReplay())
      {
        // Keep the queue position, but complete for the latest delivery's assured mode.
        final LDAPUpdateMsg accepted = listed.getLDAPUpdateMsg();
        accepted.setAssured(update.isAssured());
        accepted.setAssuredMode(update.getAssuredMode());
        accepted.setSafeDataLevel(update.getSafeDataLevel());
        return Admission.QUEUED_DUPLICATE;
      }
      listed.setDelivery(delivery);
      return Admission.ACCEPTED;
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Mark an update message as committed.
   * <p>
   * A change another replay thread owns is not this thread's to record: it is reported as
   * a change which is not here, the way one which is not listed anymore is. A thread
   * decides that a change is in the data, or that it is to be given up on, and records
   * that decision a turn of this lock later - long enough for the change to have been
   * handed back, delivered again and taken over in between. Recording it then would
   * advance the ServerState over a change which is not in the data yet (issue #889) and
   * have the thread which is applying it right now fail to commit.
   * <p>
   * The give-back on the way out of an unwound replay is what makes this reachable: it
   * runs wherever the replay was left, so the checks which keep it from taking a change
   * away from the thread which owns it now belong on every road which reads ownership,
   * not only on {@link #replayFailed(CSN)} (issue #922).
   *
   * @param csn
   *          The CSN of the update message that must be set as committed.
   * @throws NoSuchElementException
   *          if there is no change with that CSN for this thread to record: it is not
   *          listed as pending anymore, or another replay thread owns it
   */
  public void commit(CSN csn)
  {
    pendingChangesWriteLock.lock();
    try
    {
      PendingChange curChange = pendingChanges.get(csn);
      if (curChange == null
          || (curChange.isOwned() && !curChange.isOwnedBy(Thread.currentThread())))
      {
        throw new NoSuchElementException();
      }
      curChange.setCommitted(true);
      curChange.setOwner(null);
      changeBeingReplayed.remove(Thread.currentThread(), csn);
      replayDependencies.remove(curChange);

      final Iterator<PendingChange> it = pendingChanges.values().iterator();
      while (it.hasNext())
      {
        PendingChange pendingChange = it.next();
        if (!pendingChange.isCommitted())
        {
          break;
        }
        if (pendingChange.getMsg().contributesToDomainState())
        {
          state.update(pendingChange.getCSN());
          if (pendingChange.getCSN().getServerId() == localServerId)
          {
            localReplayCSN = pendingChange.getCSN();
          }
        }
        if (pendingChange.getReplayFailures() > 0)
        {
          // The change is leaving this map, so it is not one of the failing ones anymore.
          failingChanges--;
        }
        it.remove();
      }
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Gives up the ownership a replay thread had on the change with the provided CSN,
   * whose replay failed.
   * <p>
   * The change stays listed here and stays uncommitted: it is the barrier which keeps
   * the ServerState - and every change which follows it - from moving past a change
   * which is not in the data (issue #889), and it is what has the replication server
   * send it again over the restarted session. Only the mark which says a replay thread
   * owns it is dropped, so that {@link #putRemoteUpdate(LDAPUpdateMsg)} takes the next
   * delivery instead of discarding it as a duplicate.
   * <p>
   * It also stays listed among the changes the newer ones are checked against: a change
   * which is not in the data yet is exactly what the changes which follow it must depend
   * on, whether or not a replay thread owns it right now.
   * <p>
   * The changes another replay thread is applying right now are left alone: they are
   * about to commit, and forgetting them would have their {@code commit()} fail, the
   * ServerState stay behind them and the replication server replay them a second time.
   * <p>
   * Only the thread which owns the change gives it back. A release which arrives from
   * another one is a release of a change which has been taken over since - the failure of
   * a delivery is reported after the change it carried was handed to the delivery which
   * follows it - and taking the change away from the thread which is replaying it right
   * now is the double replay the ownership is there to prevent (issue #922).
   *
   * @param csn the CSN of the change whose replay failed
   * @return whether the change was given back: {@code false} when it is not listed as an
   *         uncommitted change anymore - the domain forgot its pending changes while it was
   *         being replayed - or when another thread owns it, in which case nothing was
   *         released here and there is no delivery for a session restart to ask for
   */
  public boolean replayFailed(CSN csn)
  {
    pendingChangesWriteLock.lock();
    try
    {
      final PendingChange change = pendingChanges.get(csn);
      if (change != null && !change.isCommitted() && change.isOwnedBy(Thread.currentThread()))
      {
        change.setOwner(null);
        changeBeingReplayed.remove(Thread.currentThread(), csn);
        return true;
      }
      return false;
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }

  /** How long, and how many times, the replay of one change has been failing. */
  static final class ReplayFailure
  {
    private final int attempts;
    private final long failingForMs;

    private ReplayFailure(int attempts, long failingForMs)
    {
      this.attempts = attempts;
      this.failingForMs = failingForMs;
    }

    /**
     * Returns how many deliveries of the change failed to replay it in a row. A delivery
     * is attempted several times in place before it is counted here, so this is a count
     * of deliveries rather than of attempts made on the backend.
     *
     * @return the number of failed deliveries, at least 1
     */
    int getAttempts()
    {
      return attempts;
    }

    /**
     * Returns how long the replay of the change has been failing, that is the time
     * between its first failure and the one which was just recorded.
     *
     * @return the duration in milliseconds, 0 for a first failure
     */
    long getFailingForMs()
    {
      return failingForMs;
    }
  }

  /**
   * Records that the replay of the change with the provided CSN failed once more.
   * <p>
   * The failures are kept on the change itself, which stays listed for as long as it has
   * not been applied, so a change which keeps failing keeps its give-up budget across the
   * deliveries which take over from one another, and a change which is replayed or given
   * up on takes its failures away with it (issue #889).
   *
   * @param csn
   *          the CSN of the change whose replay failed
   * @param nowMs
   *          when it failed, on a clock which only moves forward
   * @return the failures of the change, or {@code null} when there is no change left here
   *         for this thread to give up on: it is not listed as an uncommitted change
   *         anymore, which happens when the domain was disabled while it was being
   *         replayed, or another replay thread owns it, which is that change having been
   *         delivered again and taken over while this thread was on its way to reporting
   *         on it. Spending the give-up budget of a change another thread is applying
   *         would have this replica skip a change which is being written (issue #922)
   */
  public ReplayFailure recordReplayFailure(CSN csn, long nowMs)
  {
    pendingChangesWriteLock.lock();
    try
    {
      final PendingChange change = pendingChanges.get(csn);
      if (change == null || change.isCommitted()
          || (change.isOwned() && !change.isOwnedBy(Thread.currentThread())))
      {
        return null;
      }
      if (change.getReplayFailures() == 0)
      {
        // Its first failure: this change joins the ones which are failing right now.
        failingChanges++;
      }
      change.recordReplayFailure(nowMs);
      return new ReplayFailure(change.getReplayFailures(), change.getReplayFailingForMs(nowMs));
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Returns whether the replay of any change listed here is failing right now.
   * <p>
   * A change is failing from its first failed replay until it leaves this map, whether
   * it leaves it applied or given up on. The session restart backoff reads this: a
   * change which was replayed only says that this backend is serving again when it is
   * the last one which was failing, and the successful replays which surround a change
   * this replica can not apply must not keep resetting the wait it has reached on it.
   *
   * @return {@code true} while at least one listed change has a failed replay recorded
   *         against it
   */
  boolean hasFailingChanges()
  {
    pendingChangesReadLock.lock();
    try
    {
      return failingChanges > 0;
    }
    finally
    {
      pendingChangesReadLock.unlock();
    }
  }

  /**
   * Returns how many of the listed changes have a failed replay recorded against them.
   * <p>
   * A change is counted from its first failed replay until it leaves this map, whether it
   * leaves it applied or given up on. It is what tells that the replay of this domain is
   * stuck: a domain whose {@code replay-give-up-delay} is unlimited never gives up, so it
   * never counts a failed change either, and the changes it keeps asking for are only
   * visible here.
   *
   * @return the number of listed changes with a failed replay recorded against them
   */
  public int getFailingChangesSize()
  {
    pendingChangesReadLock.lock();
    try
    {
      return failingChanges;
    }
    finally
    {
      pendingChangesReadLock.unlock();
    }
  }

  /**
   * Forgets every change listed here, without updating the ServerState.
   * <p>
   * Called when the domain is disabled: its ServerState is saved and cleared from
   * memory, and it is loaded again from the backend when the domain is enabled back, so
   * the bookkeeping which goes with it must not outlive it. A change which stayed here
   * would be discarded as a duplicate when the replication server sends it again, and
   * nothing would ever replay it or record it in the ServerState.
   */
  public void clear()
  {
    pendingChangesWriteLock.lock();
    dependentChangesLock.lock();
    try
    {
      pendingChanges.clear();
      dependentChanges.clear();
      replayDependencies.clear();
      changeBeingReplayed.clear();
      failingChanges = 0;
    }
    finally
    {
      dependentChangesLock.unlock();
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Marks the change of the provided message as being replayed.
   *
   * @param msg
   *          the message whose change is being replayed
   * @return {@code false} if the change is already committed, the domain has forgotten
   *         it, the delivery was cancelled, or this is a stale delivery which a retry has replaced
   */
  public boolean markInProgress(LDAPUpdateMsg msg)
  {
    pendingChangesWriteLock.lock();
    try
    {
      final PendingChange change = pendingChanges.get(msg.getCSN());
      if (change == null || change.isCommitted() || change.isDeliveryCancelled()
          || change.getLDAPUpdateMsg() != msg)
      {
        return false;
      }
      // This delivery has left the queue. If an allocation below fails before it gets
      // an owner, a later delivery must be able to take it over.
      change.markDequeued();
      /*
       * The dependency was registered before enqueue, so a failed claim still holds back
       * its successors. Allocate the thread index before stamping the owner: the caller
       * reaches replay's give-back only once this method returns.
       */
      changeBeingReplayed.put(Thread.currentThread(), change.getCSN());
      change.setOwner(Thread.currentThread());
      return true;
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }
  /**
   * Returns the CSN of the change the calling thread is replaying, when it still owns one.
   * <p>
   * A thread owns the change it is replaying and the ones it parked as waiting for another
   * change. The parked ones are left out: they are handed out by {@link #getNextUpdate()}
   * to whichever thread calls it first once the changes before them are gone, and that
   * thread takes them over, so giving one back is more than dropping its owner - it has to
   * be unparked in the same step, or it would be handed out by two roads at once, which is
   * what {@link #releaseParkedChangesOwnedByCurrentThread()} does (issues #922 and #954).
   * <p>
   * It is a plain read of {@link #changeBeingReplayed}: no lock is taken and nothing is
   * allocated. This is what the give-back on the way out of an unwound replay asks first,
   * and it runs on a road an {@code OutOfMemoryError} leads to - a lookup which allocated
   * could be refused in its turn, and a give-back which does not know which change to give
   * back leaves it listed, uncommitted and owned by a thread which is not replaying it
   * anymore, which is the wedge this issue is about.
   * <p>
   * An answer which is out of date is safe: every road which acts on it - {@code commit()},
   * {@link #recordReplayFailure(CSN, long)} and {@link #replayFailed(CSN)} - checks the
   * ownership of the change again under the write lock, and is a no-op for a change this
   * thread does not own anymore.
   *
   * @return the CSN of the change this thread is replaying, or {@code null} when it does
   *         not own one anymore - the road its replay took gave it back, or it was applied
   */
  CSN getChangeOwnedByCurrentThread()
  {
    return changeBeingReplayed.get(Thread.currentThread());
  }

  /**
   * Gives back the changes the calling thread parked as waiting for another change, and
   * takes them out of the changes which are waiting in the same step.
   * <p>
   * A parked change stays owned by the thread which parked it while that thread goes on
   * to the changes which follow: {@link #getNextUpdate()} is what hands it out again, to
   * whichever thread calls it first once the change it was waiting for is gone - the
   * thread which cleared it, as a rule - and that thread takes it over. A replay which is
   * unwound leaves the thread which parked it without that road - it takes the next
   * delivery off the replay queue instead - so the change would be left owned by a thread
   * which is never coming back to it, and every redelivery of a change a replay thread
   * owns is refused as a duplicate (issue #954).
   * <p>
   * Unparking a change and giving it back is one step, under both locks, so that only one
   * road can hand it out: a change which was released while it is still listed as waiting
   * would be handed to the thread {@link #getNextUpdate()} gives it to and to the thread
   * which takes over the delivery which follows - the double replay the ownership is there
   * to prevent (OPENDJ-1115).
   * <p>
   * The changes stay listed and uncommitted, and stay among the changes the newer ones are
   * checked against, the way a change whose replay failed does: they are not in the data,
   * so they hold this domain's ServerState back and the changes which follow them keep
   * waiting for them.
   * <p>
   * The changes another thread parked are left alone: a change is given back by the thread
   * which owns it and by nobody else (issue #922). That thread may be inside the dependency
   * checks which parked it - they park a change once per dependency it has - so a change
   * released under it would be listed as waiting again a moment later, and handed out while
   * the delivery which took it over is being replayed.
   *
   * @return the CSNs of the changes it gave back, oldest first; empty when this thread owns
   *         no parked change - the changes a thread parked stay its own, whichever replay
   *         parked them, until {@link #getNextUpdate()} hands them to whichever thread
   *         calls it first once what they wait for is gone - the thread which cleared it,
   *         as a rule - or they are given back here
   */
  List<CSN> releaseParkedChangesOwnedByCurrentThread()
  {
    final Thread current = Thread.currentThread();
    /*
     * The second lock is taken inside the try of the first: taking a lock which is held
     * by another thread allocates the node this one waits on, and this runs on the road
     * out of a JVM which has just refused an allocation. A throw out of the second lock
     * would otherwise unwind past the first with that one held by a thread which is
     * ending, and every road which lists, commits or gives back a change would wait for
     * it for good.
     */
    pendingChangesWriteLock.lock();
    try
    {
      dependentChangesLock.lock();
      try
      {
        if (dependentChanges.isEmpty())
        {
          // Nothing is waiting, which is the state every replay but a handful leaves behind.
          return emptyList();
        }
        /*
         * Sized for every change which is waiting, so that the one allocation of this
         * method past the locks is made before anything is taken out of the set. The rule
         * getNextUpdate() states for itself holds here: an allocation which fails once a
         * change has been unparked and released - and this runs on the road out of a JVM
         * which has just refused one - would have moved that change out of the hands which
         * hand it out again, with the caller never told that it did.
         */
        final List<CSN> released = new ArrayList<>(dependentChanges.size());
        final Iterator<PendingChange> it = dependentChanges.iterator();
        while (it.hasNext())
        {
          final PendingChange change = it.next();
          if (change.isOwnedBy(current))
          {
            it.remove();
            change.setOwner(null);
            released.add(change.getCSN());
          }
        }
        return released;
      }
      finally
      {
        dependentChangesLock.unlock();
      }
    }
    finally
    {
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Get the first update in the list that have some dependencies cleared.
   * <p>
   * The change is handed to the calling thread, which owns it from then on: it is
   * replayed by whichever replay thread calls this first once the changes before it have
   * left - as a rule the one which cleared the change it was waiting for, though the one
   * which parked it comes through here on its own way out and takes it back itself when
   * the clearing happened in between - and a change is given back by the thread which
   * owns it and by nobody else (issue #922).
   *
   * @return The LDAPUpdateMsg to be handled.
   */
  public LDAPUpdateMsg getNextUpdate()
  {
    pendingChangesReadLock.lock();
    dependentChangesLock.lock();
    try
    {
      if (!hasChangeToHandOut())
      {
        /*
         * Nothing is waiting, or what waits is still held back by the changes before it.
         * This is called at the end of every replay, by every replay thread, so the answer
         * is looked for under the read lock: taking the write lock here would have a
         * backlog of waiting changes serialize the replay of the changes which have none.
         */
        return null;
      }
    }
    finally
    {
      dependentChangesLock.unlock();
      pendingChangesReadLock.unlock();
    }

    /*
     * There is one to hand out, and handing it out writes its owner, which is written
     * under the write lock as the rest of the state of a change is. It is looked for again
     * under that lock: another replay thread may have been handed it in between.
     */
    pendingChangesWriteLock.lock();
    dependentChangesLock.lock();
    try
    {
      if (hasChangeToHandOut())
      {
        final PendingChange firstDependentChange = dependentChanges.first();
        /*
         * Entered as the change this thread is replaying before it is taken out of the
         * ones which are waiting, for the same reason markInProgress() enters it before it
         * stamps the owner: an allocation which fails here must leave the change where it
         * was rather than take it out of the hands which would hand it out again.
         */
        changeBeingReplayed.put(Thread.currentThread(), firstDependentChange.getCSN());
        dependentChanges.remove(firstDependentChange);
        firstDependentChange.setOwner(Thread.currentThread());
        return firstDependentChange.getLDAPUpdateMsg();
      }
      return null;
    }
    finally
    {
      dependentChangesLock.unlock();
      pendingChangesWriteLock.unlock();
    }
  }

  /**
   * Returns whether the first change waiting for another one can be replayed now, that is
   * whether every change before it has left the pending changes.
   */
  @GuardedBy("pendingChangesLock, dependentChangesLock")
  private boolean hasChangeToHandOut()
  {
    return !dependentChanges.isEmpty()
        && !pendingChanges.isEmpty()
        && pendingChanges.firstKey().isNewerThanOrEqualTo(dependentChanges.first().getCSN());
  }

  /**
   * Mark the first pendingChange as dependent on the second PendingChange.
   * @param dependentChange The PendingChange that depend on the second
   *                        PendingChange.
   */
  private void addDependency(PendingChange dependentChange)
  {
    pendingChangesReadLock.lock();
    dependentChangesLock.lock();
    try
    {
      /*
       * A change which is not listed as pending anymore - the domain was disabled while
       * its dependencies were computed - must not be listed as dependent either:
       * getNextUpdate() reads both and would hand out a change nothing owns. The
       * replication server sends it again when the domain is enabled back.
       */
      if (pendingChanges.containsKey(dependentChange.getCSN()))
      {
        dependentChanges.add(dependentChange);
      }
      /*
       * Whichever of the two it was, this thread is not replaying that change anymore: a
       * parked one is handed to whichever thread calls getNextUpdate() first once the
       * changes before it are gone - the clearing thread as a rule, this one when the
       * clearing lands before it gets there - and one which is not listed here anymore is
       * gone with the pending changes of a domain which was disabled. The owner stays as it
       * is - it is what has getNextUpdate() hand the change over rather than leave it to
       * nobody - and the give-back of the change a replay was unwound on leaves it alone
       * (issue #922). What hands a parked change back is
       * releaseParkedChangesOwnedByCurrentThread(), which unparks it in the same step so
       * that the two roads can not hand it out at once (issue #954).
       */
      changeBeingReplayed.remove(Thread.currentThread(), dependentChange.getCSN());
    }
    finally
    {
      dependentChangesLock.unlock();
      pendingChangesReadLock.unlock();
    }
  }

  private PendingChange getPendingChange(CSN csn)
  {
    pendingChangesReadLock.lock();
    try
    {
      return pendingChanges.get(csn);
    }
    finally
    {
      pendingChangesReadLock.unlock();
    }
  }

  /**
   * Check if the given AddOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   * AddOperation depends on
   *
   * - DeleteOperation done on the same DN
   * - ModifyDnOperation with the same target DN as the ADD DN
   * - ModifyDnOperation with new DN equals to the ADD DN parent
   * - AddOperation done on the parent DN of the ADD DN
   *
   * @param op The AddOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public boolean checkDependencies(AddOperation op)
  {
    final CSN csn = OperationContext.getCSN(op);
    final PendingChange change = getPendingChange(csn);
    if (change == null)
    {
      return false;
    }

    boolean hasDependencies = false;
    final DN targetDN = op.getEntryDN();
    for (PendingChange pendingChange : replayDependencies)
    {
      if (pendingChange.getCSN().isNewerThanOrEqualTo(csn))
      {
        // From now on, the dependency should be for newer changes to be dependent on this one, so we can stop for now.
        break;
      }
      final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
      if (pendingMsg instanceof DeleteMsg)
      {
        if (pendingMsg.getDN().equals(targetDN))
        {
          // it is a deleteOperation on the same DN
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof AddMsg)
      {
        if (pendingMsg.getDN().isSuperiorOrEqualTo(targetDN))
        {
          // it is an addOperation on a parent of the current AddOperation
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof ModifyDNMsg)
      {
        // it is a ModifyDnOperation with the same target DN as the ADD DN
        // or a ModifyDnOperation with new DN equals to the ADD DN parent?
        if (pendingMsg.getDN().equals(targetDN))
        {
          hasDependencies = true;
          addDependency(change);
        }
        else
        {
          final ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingMsg;
          if (pendingModDn.newDNIsParent(targetDN))
          {
            hasDependencies = true;
            addDependency(change);
          }
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given ModifyOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * ModifyOperation depends on
   * - AddOperation done on the same DN
   * - ModifyDNOperation having newDN the same as targetDN
   *
   * @param op The ModifyOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public boolean checkDependencies(ModifyOperation op)
  {
    final CSN csn = OperationContext.getCSN(op);
    final PendingChange change = getPendingChange(csn);
    if (change == null)
    {
        return false;
    }

    boolean hasDependencies = false;
    final DN targetDN = change.getLDAPUpdateMsg().getDN();
    for (PendingChange pendingChange : replayDependencies)
    {
      if (pendingChange.getCSN().isNewerThanOrEqualTo(csn))
      {
        // From now on, the dependency should be for newer changes to be dependent on this one, so we can stop for now.
        break;
      }
      final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
      if (pendingMsg instanceof AddMsg)
      {
        if (pendingMsg.getDN().equals(targetDN))
        {
          // it is an addOperation on a same DN
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof ModifyDNMsg)
      {
        if (((ModifyDNMsg) pendingMsg).newDNIsEqual(targetDN))
        {
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof ModifyMsg)
      {
        if (pendingMsg.getDN().equals(targetDN))
        {
          // it is another modify on the same DN, they depend
          hasDependencies = true;
          addDependency(change);
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given ModifyDNMsg has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * Modify DN Operation depends on
   * - AddOperation done on the same DN as the target DN of the MODDN operation
   * - AddOperation done on the new parent of the MODDN  operation
   * - DeleteOperation done on the new DN of the MODDN operation
   * - ModifyDNOperation done from the new DN of the MODDN operation
   *
   * TODO: Consider cases where there is a rename A -> B then rename B -> C. Second change depends on first
   *
   * @param msg The ModifyDNMsg to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public boolean checkDependencies(ModifyDNMsg msg)
  {
    final CSN csn = msg.getCSN();
    final PendingChange change = getPendingChange(csn);
    if (change == null)
    {
      return false;
    }

    boolean hasDependencies = false;
    final DN targetDN = change.getLDAPUpdateMsg().getDN();
    for (PendingChange pendingChange : replayDependencies)
    {
      if (pendingChange.getCSN().isNewerThanOrEqualTo(csn))
      {
        // From now on, the dependency should be for newer changes to be dependent on this one, so we can stop for now.
        break;
      }
      final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
      if (pendingMsg instanceof DeleteMsg)
      {
        // Check if the target of the Delete is the same
        // as the new DN of this ModifyDN
        if (msg.newDNIsEqual(pendingMsg.getDN()))
        {
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof AddMsg)
      {
        // Check if the Add Operation was done on the new parent of
        // the MODDN  operation
        if (msg.newParentIsEqual(pendingMsg.getDN()))
        {
          hasDependencies = true;
          addDependency(change);
        }
        // Check if the AddOperation was done on the same DN as the
        // target DN of the MODDN operation
        if (pendingMsg.getDN().equals(targetDN))
        {
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof ModifyDNMsg)
      {
        if (msg.newDNIsEqual(pendingMsg.getDN()))
        {
          // the ModifyDNOperation was done from the new DN of the MODDN operation
          hasDependencies = true;
          addDependency(change);
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given DeleteOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * DeleteOperation depends on
   * - DeleteOperation done on children DN
   * - ModifyDnOperation with target DN that are children of the DEL DN
   * - AddOperation done on the same DN
   *
   *
   * @param op The DeleteOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public boolean checkDependencies(DeleteOperation op)
  {
    final CSN csn = OperationContext.getCSN(op);
    final PendingChange change = getPendingChange(csn);
    if (change == null)
    {
      return false;
    }

    boolean hasDependencies = false;
    final DN targetDN = op.getEntryDN();
    for (PendingChange pendingChange : replayDependencies)
    {
      if (pendingChange.getCSN().isNewerThanOrEqualTo(csn))
      {
        // From now on, the dependency should be for newer changes to be dependent on this one, so we can stop for now.
        break;
      }
      final LDAPUpdateMsg pendingMsg = pendingChange.getLDAPUpdateMsg();
      if (pendingMsg instanceof DeleteMsg)
      {
          /*
           * Check if the operation to be run is a deleteOperation on a
           * children of the current DeleteOperation.
           */
        if (pendingMsg.getDN().isSubordinateOrEqualTo(targetDN))
        {
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof AddMsg)
      {
          /*
           * Check if the operation to be run is an addOperation on a
           * parent of the current DeleteOperation.
           */
        if (pendingMsg.getDN().equals(targetDN))
        {
          hasDependencies = true;
          addDependency(change);
        }
      }
      else if (pendingMsg instanceof ModifyDNMsg)
      {
        final ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingMsg;
          /*
           * Check if the operation to be run is an ModifyDNOperation
           * on a children of the current DeleteOperation
           */
        if (pendingMsg.getDN().isSubordinateOrEqualTo(targetDN) || pendingModDn.newDNIsParent(targetDN))
        {
          hasDependencies = true;
          addDependency(change);
        }
      }
    }
    return hasDependencies;
  }

  /**
   * Check the dependencies of a given Operation/UpdateMsg.
   *
   * @param op   The Operation for which dependencies must be checked.
   * @param msg  The LocalizableMessage for which dependencies must be checked.
   * @return     A boolean indicating if an operation cannot be replayed
   *             because of dependencies.
   */
  public boolean checkDependencies(Operation op, LDAPUpdateMsg msg)
  {
    if (op instanceof ModifyOperation)
    {
      return checkDependencies((ModifyOperation) op);
    }
    else if (op instanceof DeleteOperation)
    {
      return checkDependencies((DeleteOperation) op);
    }
    else if (op instanceof AddOperation)
    {
      return checkDependencies((AddOperation) op);
    }
    else if (op instanceof ModifyDNOperationBasis)
    {
      return checkDependencies((ModifyDNMsg) msg);
    }
    else
    {
      return true;  // unknown type of operation ?!
    }
  }
}
