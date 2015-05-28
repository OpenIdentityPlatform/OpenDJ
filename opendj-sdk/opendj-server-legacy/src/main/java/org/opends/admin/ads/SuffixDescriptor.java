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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */
package org.opends.admin.ads;

import java.util.HashSet;
import java.util.Set;

/**
 * The object of this class represent a topology of replicas across servers that
 * have the same suffix DN. If there is more than one replica on the suffix, the
 * contents of the replicas are replicated.
 */
public class SuffixDescriptor
{
  private String suffixDN;
  private final Set<ReplicaDescriptor> replicas = new HashSet<>();

  /**
   * Returns the DN associated with this suffix descriptor.
   *
   * @return the DN associated with this suffix descriptor.
   */
  public String getDN()
  {
    return suffixDN;
  }

  /**
   * Sets the DN associated with this suffix descriptor.
   *
   * @param suffixDN
   *          the DN associated with this suffix descriptor.
   */
  public void setDN(String suffixDN)
  {
    this.suffixDN = suffixDN;
  }

  /**
   * Returns the replicas associated with this SuffixDescriptor.
   *
   * @return a Set containing the replicas associated with this
   *         SuffixDescriptor.
   */
  public Set<ReplicaDescriptor> getReplicas()
  {
    return new HashSet<>(replicas);
  }

  /**
   * Sets the replicas associated with this SuffixDescriptor.
   *
   * @param replicas
   *          a Set containing the replicas associated with this
   *          SuffixDescriptor.
   */
  public void setReplicas(Set<ReplicaDescriptor> replicas)
  {
    this.replicas.clear();
    this.replicas.addAll(replicas);
  }

  /**
   * Returns the Set of Replication servers for the whole suffix topology. The
   * servers are provided in their String representation.
   *
   * @return the Set of Replication servers for the whole suffix topology.
   */
  public Set<String> getReplicationServers()
  {
    Set<String> replicationServers = new HashSet<>();
    for (ReplicaDescriptor replica : getReplicas())
    {
      replicationServers.addAll(replica.getReplicationServers());
    }
    return replicationServers;
  }

  @Override
  public int hashCode()
  {
    return getId().hashCode();
  }

  /**
   * Returns an Id that is unique for this suffix.
   *
   * @return an Id that is unique for this suffix.
   */
  public String getId()
  {
    StringBuilder buf = new StringBuilder();
    buf.append(getDN());
    for (ReplicaDescriptor replica : getReplicas())
    {
      buf.append("-").append(replica.getServer().getId());
    }

    return buf.toString();
  }
}
