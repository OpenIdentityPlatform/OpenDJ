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
package org.opends.server.replication.server;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.types.DN;

/**
 * This is the changelog state stored in the changelogStateDB. For each
 * replication domain, it contains:
 * <ul>
 * <li>its generationId</li>
 * <li>the list of serverIds composing it</li>
 * </ul>
 * <p>
 * This class is used during replication initialization to decouple the code
 * that reads the changelogStateDB from the code that makes use of its data.
 *
 * @ThreadSafe
 */
public class ChangelogState
{
  private final ConcurrentSkipListMap<DN, Long> domainToGenerationId = new ConcurrentSkipListMap<>();
  private final ConcurrentSkipListMap<DN, Set<Integer>> domainToServerIds = new ConcurrentSkipListMap<>();
  private final MultiDomainServerState offlineReplicas = new MultiDomainServerState();

  /**
   * Sets the generationId for the supplied replication domain.
   *
   * @param baseDN
   *          the targeted replication domain baseDN
   * @param generationId
   *          the generation Id to set
   */
  public void setDomainGenerationId(DN baseDN, long generationId)
  {
    domainToGenerationId.put(baseDN, generationId);
  }

  /**
   * Adds the serverId to the serverIds list of the supplied replication domain.
   *
   * @param serverId
   *          the serverId to add
   * @param baseDN
   *          the targeted replication domain baseDN
   */
  public void addServerIdToDomain(int serverId, DN baseDN)
  {
    Set<Integer> serverIds = domainToServerIds.get(baseDN);
    if (serverIds == null)
    {
      serverIds = new HashSet<>();
      final Set<Integer> existingServerIds =
          domainToServerIds.putIfAbsent(baseDN, serverIds);
      if (existingServerIds != null)
      {
        serverIds = existingServerIds;
      }
    }
    serverIds.add(serverId);
  }

  /**
   * Adds the following replica information to the offline list.
   *
   * @param baseDN
   *          the baseDN of the offline replica
   * @param offlineCSN
   *          the CSN (serverId + timestamp) of the offline replica
   */
  public void addOfflineReplica(DN baseDN, CSN offlineCSN)
  {
    offlineReplicas.update(baseDN, offlineCSN);
  }

  /**
   * Removes the following replica information from the offline list.
   *
   * @param baseDN
   *          the baseDN of the offline replica
   * @param serverId
   *          the serverId that is not offline anymore
   */
  public void removeOfflineReplica(DN baseDN, int serverId)
  {
    CSN csn;
    do
    {
      csn = offlineReplicas.getCSN(baseDN, serverId);
    }
    while (csn != null && !offlineReplicas.removeCSN(baseDN, csn));
  }

  /**
   * Returns the Map of domainBaseDN => generationId.
   *
   * @return a Map of domainBaseDN => generationId
   */
  public Map<DN, Long> getDomainToGenerationId()
  {
    return domainToGenerationId;
  }

  /**
   * Returns the Map of domainBaseDN => List&lt;serverId&gt;.
   *
   * @return a Map of domainBaseDN => List&lt;serverId&gt;.
   */
  public Map<DN, Set<Integer>> getDomainToServerIds()
  {
    return domainToServerIds;
  }

  /**
   * Returns the internal MultiDomainServerState for offline replicas.
   *
   * @return the MultiDomainServerState for offline replicas.
   */
  public MultiDomainServerState getOfflineReplicas()
  {
    return offlineReplicas;
  }

  /**
   * Returns whether the current ChangelogState is equal to the provided
   * ChangelogState.
   * <p>
   * Note: Only use for tests!!<br>
   * This method should only be used by tests because it creates a lot of
   * intermediate objects which is not suitable for production.
   *
   * @param other
   *          the ChangelogState to compare with
   * @return true if the current ChangelogState is equal to the provided
   *         ChangelogState, false otherwise.
   */
  public boolean isEqualTo(ChangelogState other)
  {
    if (other == null)
    {
      return false;
    }
    if (this == other)
    {
      return true;
    }
    return domainToGenerationId.equals(other.domainToGenerationId)
        && domainToServerIds.equals(other.domainToServerIds)
        // Note: next line is not suitable for production
        // because it creates lots of Lists and Maps
        && offlineReplicas.getSnapshot().equals(other.offlineReplicas.getSnapshot());
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "domainToGenerationId=" + domainToGenerationId
        + ", domainToServerIds=" + domainToServerIds
        + ", offlineReplicas=" + offlineReplicas;
  }
}
