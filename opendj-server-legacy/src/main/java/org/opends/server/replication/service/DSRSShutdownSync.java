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
   * Message has been forwarded.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   * @param forwardedCSN
   *          the CSN of the forwarded message
   */
  public void replicaOfflineMsgForwarded(DN baseDN, CSN forwardedCSN)
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
      if (pending != null && pending.csn.isOlderThanOrEqualTo(forwardedCSN))
      {
        msgs.remove(serverId, pending);
      }
    }
    synchronized (forwardedMonitor)
    {
      forwardedMonitor.notifyAll();
    }
  }

  /**
   * Whether the shutdown of a domain can proceed, i.e. its ReplicaOfflineMsg
   * has been forwarded or its grace period has expired.
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

    private PendingOfflineMsg(CSN csn, long sentTime)
    {
      this.csn = csn;
      this.sentTime = sentTime;
    }

    @Override
    public String toString()
    {
      return "PendingOfflineMsg(" + csn + ")";
    }
  }
}
