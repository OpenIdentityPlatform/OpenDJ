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
 * Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.common.CSN;

/**
 * Class useful for the case where DS/RS instances are collocated inside the
 * same JVM. It synchronizes the shutdown of the DS and RS sides.
 * <p>
 * More specifically, it ensures a ReplicaOfflineMsg sent by the DS is
 * relayed/forwarded by the collocated RS to the other RSs in the topology
 * before the whole process shuts down.
 * <p>
 * The state is kept per domain and per instance: the collocated DS and RS
 * sides coordinate through the single instance MultimasterReplication hands
 * to both of them.
 *
 * @since OPENDJ-1453
 */
public class DSRSShutdownSync
{
  /**
   * How long a ReplicaOfflineMsg may hold back the shutdown of the collocated
   * RS, in milliseconds, counted from the moment the message was sent.
   */
  public static final long REPLICA_OFFLINE_GRACE_PERIOD = 5000;

  private final long gracePeriod;

  /**
   * The ReplicaOfflineMsg which has not been forwarded yet, per domain and per
   * replica of that domain.
   * <p>
   * It is kept per domain because a domain sends this message whenever its
   * replication service is disabled - an online import, a restore, a
   * configuration change - and not only when the process shuts down. A single
   * entry for the whole process would be the one of the first such message and
   * would leave no grace period at all to the shutdown this class exists for.
   * <p>
   * It is kept per replica because the collocated RS relays the message of
   * every replica connected to it, and the forward of another replica's
   * message says nothing about this one.
   * <p>
   * Each entry knows the replication servers its message was queued for, because each of them is
   * served by its own writer: the forward of one of them says nothing about the others, whose
   * queue the shutdown is about to clear.
   */
  private final ConcurrentMap<DN, ConcurrentMap<Integer, PendingOfflineMsg>> replicaOfflineMsgs =
      new ConcurrentHashMap<>();
  /** Monitor notified whenever a ReplicaOfflineMsg has been forwarded. */
  private final Object forwardedMonitor = new Object();

  /** Creates a synchronization object using the default grace period. */
  public DSRSShutdownSync()
  {
    this(REPLICA_OFFLINE_GRACE_PERIOD);
  }

  /**
   * Creates a synchronization object using the provided grace period.
   *
   * @param gracePeriod
   *          how long a ReplicaOfflineMsg may hold back the shutdown, in milliseconds
   */
  DSRSShutdownSync(long gracePeriod)
  {
    this.gracePeriod = gracePeriod;
  }

  /**
   * Message has been sent.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   * @param offlineCSN
   *          the CSN of the message, which identifies both the replica which announced itself
   *          offline and the announcement being waited for
   */
  public void replicaOfflineMsgSent(DN baseDN, CSN offlineCSN)
  {
    replicaOfflineMsgs
        .computeIfAbsent(baseDN, dn -> new ConcurrentHashMap<Integer, PendingOfflineMsg>())
        .put(offlineCSN.getServerId(), new PendingOfflineMsg(offlineCSN, System.nanoTime()));
  }

  /**
   * Message has been queued for the replication servers which must forward it.
   * <p>
   * This must be called before the message is queued for any of them: a replication server can
   * forward it as soon as it is in its queue, and a forward which finds no recipient recorded
   * ends the wait at once.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   * @param offlineCSN
   *          the CSN of the message which is being queued
   * @param replicationServerIds
   *          the server ids of the replication servers the message is being queued for
   */
  public void replicaOfflineMsgDispatched(
      DN baseDN, CSN offlineCSN, Collection<Integer> replicationServerIds)
  {
    final ConcurrentMap<Integer, PendingOfflineMsg> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs == null)
    {
      return;
    }
    final int serverId = offlineCSN.getServerId();
    final PendingOfflineMsg pending = msgs.get(serverId);
    /*
     * The message being queued may be an older announcement of the same replica - one which was
     * queued behind a backlog since an earlier import. The replication servers it goes to say
     * nothing about the announcement the shutdown is waiting for.
     */
    if (pending != null && pending.csn.equals(offlineCSN)
        && pending.awaitForwardsFrom(replicationServerIds))
    {
      // queued for nobody: there is nothing to wait for
      msgs.remove(serverId, pending);
      notifyForwarded();
    }
  }

  /**
   * Message has been forwarded to one of the replication servers it was queued for.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   * @param forwardedCSN
   *          the CSN of the forwarded message
   * @param replicationServerId
   *          the server id of the replication server the message has been forwarded to
   */
  public void replicaOfflineMsgForwarded(DN baseDN, CSN forwardedCSN, int replicationServerId)
  {
    final ConcurrentMap<Integer, PendingOfflineMsg> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs != null)
    {
      final int serverId = forwardedCSN.getServerId();
      final PendingOfflineMsg pending = msgs.get(serverId);
      /*
       * A replica announces itself offline on every disableService(), so the message which is
       * forwarded now may be an older one - queued behind a backlog since an earlier import, or
       * synthesized from the offline CSN of the changelog for a server which is catching up.
       * Such a forward says nothing about the announcement the shutdown is waiting for, and must
       * not consume its grace period.
       */
      if (pending != null && pending.csn.isOlderThanOrEqualTo(forwardedCSN)
          && pending.forwardedBy(replicationServerId))
      {
        msgs.remove(serverId, pending);
      }
    }
    notifyForwarded();
  }

  /**
   * A replication server the message may have been queued for will not forward it: it is gone,
   * or the message was dropped on its way out.
   * <p>
   * Whatever it was given can no longer reach it, so the shutdown must not spend the rest of its
   * grace period waiting for it.
   *
   * @param baseDN
   *          the domain the replication server is connected to
   * @param replicationServerId
   *          the server id of the replication server which will not forward the message
   */
  public void replicaOfflineMsgNotForwarded(DN baseDN, int replicationServerId)
  {
    final ConcurrentMap<Integer, PendingOfflineMsg> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs != null)
    {
      for (Entry<Integer, PendingOfflineMsg> entry : msgs.entrySet())
      {
        final PendingOfflineMsg pending = entry.getValue();
        if (pending.giveUpOn(replicationServerId))
        {
          msgs.remove(entry.getKey(), pending);
        }
      }
    }
    notifyForwarded();
  }

  /** Wakes up the shutdown, which re-reads what is left to wait for. */
  private void notifyForwarded()
  {
    synchronized (forwardedMonitor)
    {
      forwardedMonitor.notifyAll();
    }
  }

  /**
   * Whether the shutdown of a domain can proceed, i.e. its ReplicaOfflineMsg
   * has been forwarded by every replication server it was queued for, or its
   * grace period has expired.
   * <p>
   * The shutdown itself blocks on {@link #awaitReplicaOfflineMsgsForwarded(Collection, long)}
   * rather than polling this; it is the same state, observable without waiting for it.
   *
   * @param baseDN
   *          the baseDN of the domain being shut down
   * @return true if the shutdown of this domain need not wait any longer, i.e. its message was
   *         forwarded or its grace period has expired, false otherwise
   */
  public boolean canShutdown(DN baseDN)
  {
    return remainingGracePeriod(baseDN) <= 0;
  }

  /**
   * Returns the time by which every wait of one shutdown must be over.
   * <p>
   * A process shuts its domains down one after the other and each of them may have a message
   * pending, so a deadline computed once and shared by all of them keeps the whole shutdown
   * bounded by one grace period instead of one per domain.
   *
   * @return the point in time, on the {@link System#nanoTime()} clock, by which the waits must
   *         be over
   */
  public long newShutdownDeadline()
  {
    return System.nanoTime() + MILLISECONDS.toNanos(gracePeriod);
  }

  /**
   * Waits for the ReplicaOfflineMsg of every provided domain to be forwarded, or for their grace
   * periods or the provided deadline to expire.
   * <p>
   * This must be called before the server handlers of those domains are stopped: stopping them
   * deactivates their consumer, clears their message queue and closes their session, after which
   * the message can no longer be forwarded.
   * <p>
   * All the domains of one shutdown wait together rather than one after the other, so that the
   * shutdown is bounded by one grace period without the wait of one domain spending the grace
   * period of the next.
   *
   * @param baseDNs
   *          the baseDNs of the domains whose messages must be forwarded
   * @param deadline
   *          the point in time, on the {@link System#nanoTime()} clock, by which this wait must
   *          be over whatever the domains announce in the meantime - see
   *          {@link #newShutdownDeadline()}. A deadline which is not in the future returns
   *          without waiting at all, for a caller which has nothing to wait for.
   */
  public void awaitReplicaOfflineMsgsForwarded(Collection<DN> baseDNs, long deadline)
  {
    if (deadline - System.nanoTime() <= 0)
    {
      return;
    }
    synchronized (forwardedMonitor)
    {
      while (true)
      {
        final long timeout = Math.min(remainingGracePeriod(baseDNs),
            NANOSECONDS.toMillis(deadline - System.nanoTime()));
        if (timeout <= 0)
        {
          return;
        }
        try
        {
          forwardedMonitor.wait(timeout);
        }
        catch (InterruptedException e)
        {
          /*
           * Give up waiting. The interrupt is deliberately not restored: what follows this call is
           * the rest of the shutdown - joining the reader and writer thread of every handler, then
           * closing the changelog DB - and an interrupt flag would make all of it give up too.
           */
          return;
        }
      }
    }
  }

  /**
   * Returns the time left, in milliseconds, to forward the ReplicaOfflineMsg of the replica of
   * the provided domains which has the longest to wait, zero or less if none of them has a
   * message pending.
   */
  private long remainingGracePeriod(Collection<DN> baseDNs)
  {
    long remaining = 0;
    for (DN baseDN : baseDNs)
    {
      remaining = Math.max(remaining, remainingGracePeriod(baseDN));
    }
    return remaining;
  }

  /**
   * Returns the time left, in milliseconds, to forward the ReplicaOfflineMsg of the replica of
   * this domain which has the longest to wait, zero or less if no message of this domain is
   * pending.
   */
  private long remainingGracePeriod(DN baseDN)
  {
    final ConcurrentMap<Integer, PendingOfflineMsg> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs == null)
    {
      return 0;
    }
    final long now = System.nanoTime();
    long remaining = 0;
    for (PendingOfflineMsg pending : msgs.values())
    {
      remaining = Math.max(remaining, gracePeriod - NANOSECONDS.toMillis(now - pending.sentTime));
    }
    return remaining;
  }

  /**
   * A ReplicaOfflineMsg a replica announced and which has not been forwarded yet.
   * <p>
   * This deliberately does not override {@code equals}: the two-argument
   * {@link ConcurrentMap#remove(Object, Object)} of the forward guard must match the very
   * announcement it read, not another one which happens to carry the same values.
   */
  private static final class PendingOfflineMsg
  {
    /** The CSN of the message, so that the forward of an older one is not taken for this one. */
    private final CSN csn;
    /** When the message was announced, on the {@link System#nanoTime()} clock. */
    private final long sentTime;
    /**
     * The replication servers the message was queued for and which have not forwarded it yet,
     * null as long as it has not been queued for anybody.
     */
    private volatile Set<Integer> awaitedForwarders;

    private PendingOfflineMsg(CSN csn, long sentTime)
    {
      this.csn = csn;
      this.sentTime = sentTime;
    }

    /**
     * Records the replication servers the message is being queued for, and returns whether there
     * is none of them, i.e. nothing left to wait for.
     */
    private boolean awaitForwardsFrom(Collection<Integer> replicationServerIds)
    {
      final Set<Integer> awaited = ConcurrentHashMap.newKeySet();
      awaited.addAll(replicationServerIds);
      awaitedForwarders = awaited;
      return awaited.isEmpty();
    }

    /**
     * Records the forward of one replication server, and returns whether nothing is left to wait
     * for.
     */
    private boolean forwardedBy(int replicationServerId)
    {
      final Set<Integer> awaited = awaitedForwarders;
      if (awaited == null)
      {
        /*
         * The message never went through the collocated RS - a replica which picked a remote one
         * announcing itself offline, or an announcement recorded after the message it belongs to
         * was already relayed. Nobody is known to owe a forward, so keep the behaviour the wait
         * had before the recipients were tracked: the first forward ends it.
         */
        return true;
      }
      awaited.remove(replicationServerId);
      return awaited.isEmpty();
    }

    /**
     * Gives up on the forward of one replication server, and returns whether nothing is left to
     * wait for. Unlike a forward, this releases nothing while no recipient is known: a peer going
     * away says nothing about a message it was never given.
     */
    private boolean giveUpOn(int replicationServerId)
    {
      final Set<Integer> awaited = awaitedForwarders;
      return awaited != null && awaited.remove(replicationServerId) && awaited.isEmpty();
    }

    @Override
    public String toString()
    {
      final Set<Integer> awaited = awaitedForwarders;
      return "PendingOfflineMsg(" + csn
          + (awaited != null ? ", awaiting the forward of " + awaited : "") + ")";
    }
  }
}
