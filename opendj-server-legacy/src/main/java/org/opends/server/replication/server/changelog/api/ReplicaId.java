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
package org.opends.server.replication.server.changelog.api;

import org.forgerock.opendj.ldap.DN;

/** Replica identifier comprised of the domain baseDN and its serverId within this domain. */
public final class ReplicaId implements Comparable<ReplicaId>
{
  private final DN baseDN;
  private final int serverId;

  /**
   * Creates a ReplicaId with the provided parameters.
   *
   * @param baseDN
   *          domain baseDN, cannot be null
   * @param serverId
   *          serverId within the domain
   */
  private ReplicaId(DN baseDN, int serverId)
  {
    this.baseDN = baseDN;
    this.serverId = serverId;
  }

  /**
   * Creates a ReplicaId with the provided parameters.
   *
   * @param baseDN
   *          domain baseDN
   * @param serverId
   *          serverId within the domain
   * @return a new ReplicaId
   */
  public static ReplicaId of(DN baseDN, int serverId)
  {
    return new ReplicaId(baseDN, serverId);
  }

  /**
   * Returns the baseDN.
   * @return the baseDN
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Returns the serverId.
   * @return the serverId
   */
  public int getServerId()
  {
    return serverId;
  }

  @Override
  public int compareTo(ReplicaId o)
  {
    final int compareResult = baseDN.compareTo(o.baseDN);
    if (compareResult == 0)
    {
      return serverId - o.serverId;
    }
    return compareResult;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + (baseDN == null ? 0 : baseDN.hashCode());
    return prime * result + serverId;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    if (!(obj instanceof ReplicaId))
    {
      return false;
    }
    final ReplicaId other = (ReplicaId) obj;
    return serverId == other.serverId && baseDN.equals(other.baseDN);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "(" + baseDN + " " + serverId + ")";
  }
}
