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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.plugin.PendingChanges.ReplicaOfflineAnnouncer;
import org.opends.server.replication.service.DSRSShutdownSync;

/**
 * Announces the replica of one domain offline to the {@link DSRSShutdownSync} the shutdown of a
 * collocated replication server waits on, and takes such an announcement back.
 * <p>
 * This is the announcer {@link LDAPReplicationDomain} hands its {@link PendingChanges}: the
 * announcement goes through {@link DSRSShutdownSync#replicaOfflineMsgSent(DN, CSN)} and the
 * withdrawal through {@link DSRSShutdownSync#replicaOfflineMsgNotSent(DN, CSN)}, for the domain
 * the announcer was built for.
 */
final class ShutdownSyncAnnouncer implements ReplicaOfflineAnnouncer
{
  private final DSRSShutdownSync shutdownSync;
  private final DN baseDN;

  /**
   * Creates an announcer for the replica of one domain.
   *
   * @param shutdownSync
   *          the synchronization object the collocated replication server's shutdown waits on
   * @param baseDN
   *          the domain whose replica announces itself
   */
  ShutdownSyncAnnouncer(DSRSShutdownSync shutdownSync, DN baseDN)
  {
    this.shutdownSync = shutdownSync;
    this.baseDN = baseDN;
  }

  @Override
  public void announce(CSN offlineCSN)
  {
    shutdownSync.replicaOfflineMsgSent(baseDN, offlineCSN);
  }

  @Override
  public void withdraw(CSN offlineCSN)
  {
    shutdownSync.replicaOfflineMsgNotSent(baseDN, offlineCSN);
  }
}
