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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opends.server.replication.common.CSN;
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
 */
public class ChangelogState
{

  private final Map<DN, Long> domainToGenerationId = new HashMap<DN, Long>();
  private final Map<DN, List<Integer>> domainToServerIds =
      new HashMap<DN, List<Integer>>();
  private final Map<DN, List<CSN>> offlineReplicas =
      new HashMap<DN, List<CSN>>();

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
    List<Integer> serverIds = domainToServerIds.get(baseDN);
    if (serverIds == null)
    {
      serverIds = new LinkedList<Integer>();
      domainToServerIds.put(baseDN, serverIds);
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
    List<CSN> offlineCSNs = offlineReplicas.get(baseDN);
    if (offlineCSNs == null)
    {
      offlineCSNs = new LinkedList<CSN>();
      offlineReplicas.put(baseDN, offlineCSNs);
    }
    offlineCSNs.add(offlineCSN);
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
  public Map<DN, List<Integer>> getDomainToServerIds()
  {
    return domainToServerIds;
  }

  /**
   * Returns the Map of domainBaseDN => List&lt;offlineCSN&gt;.
   *
   * @return a Map of domainBaseDN => List&lt;offlineCSN&gt;.
   */
  public Map<DN, List<CSN>> getOfflineReplicas()
  {
    return offlineReplicas;
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
