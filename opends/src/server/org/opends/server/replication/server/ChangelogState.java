/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

  private final Map<String, Long> domainToGenerationId =
      new HashMap<String, Long>();
  private final Map<String, List<Integer>> domainToServerIds =
      new HashMap<String, List<Integer>>();

  /**
   * Sets the generationId for the supplied replication domain.
   *
   * @param baseDn
   *          the targeted replication domain baseDN
   * @param generationId
   *          the generation Id to set
   */
  public void setDomainGenerationId(String baseDn, long generationId)
  {
    domainToGenerationId.put(baseDn, generationId);
  }

  /**
   * Adds the serverId to the serverIds list of the supplied replication domain.
   *
   * @param serverId
   *          the serverId to add
   * @param baseDn
   *          the targeted replication domain baseDN
   */
  public void addServerIdToDomain(int serverId, String baseDn)
  {
    List<Integer> serverIds = domainToServerIds.get(baseDn);
    if (serverIds == null)
    {
      serverIds = new LinkedList<Integer>();
      domainToServerIds.put(baseDn, serverIds);
    }
    serverIds.add(serverId);
  }

  /**
   * Returns the Map of domainBaseDN => generationId.
   *
   * @return a Map of domainBaseDN => generationId
   */
  public Map<String, Long> getDomainToGenerationId()
  {
    return domainToGenerationId;
  }

  /**
   * Returns the Map of domainBaseDN => List&lt;serverId&gt;.
   *
   * @return a Map of domainBaseDN => List&lt;serverId&gt;.
   */
  public Map<String, List<Integer>> getDomainToServerIds()
  {
    return domainToServerIds;
  }
}
