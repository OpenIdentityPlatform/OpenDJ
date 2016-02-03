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
 */
package org.opends.server.replication.service;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.opendj.ldap.DN;

/**
 * Class useful for the case where DS/RS instances are collocated inside the
 * same JVM. It synchronizes the shutdown of the DS and RS sides.
 * <p>
 * More specifically, it ensures a ReplicaOfflineMsg sent by the DS is
 * relayed/forwarded by the collocated RS to the other RSs in the topology
 * before the whole process shuts down.
 *
 * @since OPENDJ-1453
 */
public class DSRSShutdownSync
{
  private static final ConcurrentSkipListSet<DN> replicaOfflineMsgs = new ConcurrentSkipListSet<>();
  private static AtomicLong stopInstanceTimestamp = new AtomicLong();

  /**
   * Message has been sent.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   */
  public void replicaOfflineMsgSent(DN baseDN)
  {
    stopInstanceTimestamp.compareAndSet(0, System.currentTimeMillis());
    replicaOfflineMsgs.add(baseDN);
  }

  /**
   * Message has been forwarded.
   *
   * @param baseDN
   *          the domain for which the message has been sent
   */
  public void replicaOfflineMsgForwarded(DN baseDN)
  {
    replicaOfflineMsgs.remove(baseDN);
  }

  /**
   * Whether a ReplicationServer ServerReader or ServerWriter can proceed with
   * shutdown.
   *
   * @param baseDN
   *          the baseDN of the ServerReader or ServerWriter .
   * @return true if the caller can shutdown, false otherwise
   */
  public boolean canShutdown(DN baseDN)
  {
    return !replicaOfflineMsgs.contains(baseDN)
        || System.currentTimeMillis() - stopInstanceTimestamp.get() > 5000;
  }
}
