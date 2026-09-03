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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.opendj.ldap.DN;

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
   * Time at which a ReplicaOfflineMsg was sent, per domain and per replica of
   * that domain, for the messages which have not been forwarded yet.
   * <p>
   * The time is kept per domain because a domain sends this message whenever
   * its replication service is disabled - an online import, a restore, a
   * configuration change - and not only when the process shuts down. A single
   * time for the whole process would be the time of the first such message and
   * would leave no grace period at all to the shutdown this class exists for.
   * <p>
   * It is kept per replica because the collocated RS relays the message of
   * every replica connected to it, and the forward of another replica's
   * message says nothing about this one.
   */
  private final ConcurrentMap<DN, ConcurrentMap<Integer, Long>> replicaOfflineMsgs =
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
   * @param serverId
   *          the replica which announced itself offline
   */
  public void replicaOfflineMsgSent(DN baseDN, int serverId)
  {
    ConcurrentMap<Integer, Long> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs == null)
    {
      msgs = new ConcurrentHashMap<>();
      final ConcurrentMap<Integer, Long> existing = replicaOfflineMsgs.putIfAbsent(baseDN, msgs);
      if (existing != null)
      {
        msgs = existing;
      }
    }
    msgs.put(serverId, System.currentTimeMillis());
  }

  /**
   * Message has been forwarded.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   * @param serverId
   *          the replica the forwarded message belongs to
   */
  public void replicaOfflineMsgForwarded(DN baseDN, int serverId)
  {
    final ConcurrentMap<Integer, Long> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs != null)
    {
      msgs.remove(serverId);
    }
    synchronized (forwardedMonitor)
    {
      forwardedMonitor.notifyAll();
    }
  }

  /**
   * Whether the shutdown of a domain can proceed, i.e. its ReplicaOfflineMsg
   * has been forwarded or its grace period has expired.
   *
   * @param baseDN
   *          the baseDN of the domain being shut down
   * @return true if the caller can shutdown, false otherwise
   */
  public boolean canShutdown(DN baseDN)
  {
    return remainingGracePeriod(baseDN) <= 0;
  }

  /**
   * Waits for the ReplicaOfflineMsg of the provided domain to be forwarded, or for its grace
   * period to expire.
   * <p>
   * This must be called before the server handlers of the domain are stopped: stopping them
   * deactivates their consumer, clears their message queue and closes their session, after which
   * the message can no longer be forwarded.
   *
   * @param baseDN
   *          the baseDN of the domain whose message must be forwarded
   */
  public void awaitReplicaOfflineMsgForwarded(DN baseDN)
  {
    // Bound the wait even if the domain keeps announcing itself offline while we are waiting.
    final long deadline = System.currentTimeMillis() + gracePeriod;
    synchronized (forwardedMonitor)
    {
      while (!canShutdown(baseDN))
      {
        final long timeout = Math.min(remainingGracePeriod(baseDN), deadline - System.currentTimeMillis());
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
   * Returns the time left to forward the ReplicaOfflineMsg of the replica of this domain which
   * has the longest to wait, zero or less if no message of this domain is pending.
   */
  private long remainingGracePeriod(DN baseDN)
  {
    final ConcurrentMap<Integer, Long> msgs = replicaOfflineMsgs.get(baseDN);
    if (msgs == null)
    {
      return 0;
    }
    final long now = System.currentTimeMillis();
    long remaining = 0;
    for (Long msgSentTime : msgs.values())
    {
      remaining = Math.max(remaining, msgSentTime + gracePeriod - now);
    }
    return remaining;
  }
}
