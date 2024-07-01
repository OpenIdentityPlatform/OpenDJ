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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.HostPort;

/**
 * The object of this class represent a topology of replicas across servers that
 * have the same suffix DN. If there is more than one replica on the suffix, the
 * contents of the replicas are replicated.
 */
public class SuffixDescriptor implements Comparable<SuffixDescriptor>
{
  private DN suffixDN;
  private final Set<ReplicaDescriptor> replicas = new HashSet<>();

  /**
   * Builds a new SuffixDescriptor.
   *
   * @param suffixDn
   *          the suffix DN
   * @param replica
   *          the replica
   */
  public SuffixDescriptor(DN suffixDn, ReplicaDescriptor replica)
  {
    this.suffixDN = suffixDn;
    this.replicas.add(replica);
  }

  /**
   * Returns the DN associated with this suffix descriptor.
   *
   * @return the DN associated with this suffix descriptor.
   */
  public DN getDN()
  {
    return suffixDN;
  }

  /**
   * Sets the DN associated with this suffix descriptor.
   *
   * @param suffixDN
   *          the DN associated with this suffix descriptor.
   */
  public void setDN(DN suffixDN)
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
   * Associates the provided replica with this SuffixDescriptor.
   *
   * @param replica
   *          the replicate to associate with this SuffixDescriptor.
   */
  void addReplica(ReplicaDescriptor replica)
  {
    replicas.add(replica);
  }

  /**
   * Returns the Set of Replication servers for the whole suffix topology. The
   * servers are provided in their String representation.
   *
   * @return the Set of Replication servers for the whole suffix topology.
   */
  public Set<HostPort> getReplicationServers()
  {
    Set<HostPort> replicationServers = new HashSet<>();
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

  @Override
  public int compareTo(SuffixDescriptor o)
  {
    return getId().compareTo(o.getId());
  }

  @Override
  public String toString()
  {
    Set<String> replicaStrings = new TreeSet<>();
    for (ReplicaDescriptor replica : replicas)
    {
      replicaStrings.add(replica.getServer().getHostPort(true).toString());
    }
    return getClass().getSimpleName()
        + "(dn=" + suffixDN
        + ", replicas=" + replicaStrings
        + ")";
  }
}
