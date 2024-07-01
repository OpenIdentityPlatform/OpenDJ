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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import java.util.HashSet;
import java.util.Set;

import org.opends.server.types.HostPort;

/**
 * The object of this class represent a Replica, i.e. a suffix in a given server instance.
 * <p>
 * Note: this does not represent a replication server.
 */
public class ReplicaDescriptor
{
  private SuffixDescriptor suffix;
  /** Number of entries held by this replica. */
  private int nbEntries = -1;
  private ServerDescriptor server;
  private final Set<HostPort> replicationServers = new HashSet<>();
  /** @see InstallerHelper#getReplicationId(Set) */
  private int serverId = -1;
  private int missingChanges = -1;
  private long ageOfOldestMissingChange = -1;
  private String backendId;
  private Set<String> objectClasses;

  /**
   * Returns the number of entries contained in the replica.
   * @return the number of entries contained in the replica.
   */
  public int getEntries()
  {
    return nbEntries;
  }

  /**
   * Returns whether this replica is replicated or not.
   * @return {@code true} if the replica is replicated, {@code false} otherwise.
   */
  public boolean isReplicated()
  {
    return serverId != -1;
  }

  /**
   * Returns whether replication is replicated on this server or not.
   * @return {@code true} if replication is enabled, {@code false} otherwise.
   */
  public boolean isReplicationEnabled()
  {
    return server.isReplicationEnabled();
  }

  /**
   * Sets the number of entries contained in the replica.
   * @param nbEntries the number of entries contained in the replica.
   */
  public void setEntries(int nbEntries)
  {
    this.nbEntries = nbEntries;
  }

  /**
   * Returns the ServerDescriptor object associated with the server where this
   * replica is located.
   * @return the ServerDescriptor object associated with the server where this
   * replica is located.
   */
  public ServerDescriptor getServer()
  {
    return server;
  }

  /**
   * Sets the server where this replica is located.
   * @param server the ServerDescriptor object associated with the server where
   * this replica is located.
   */
  public void setServer(ServerDescriptor server)
  {
    this.server = server;
  }

  /**
   * Returns the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   * @return the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   */
  public SuffixDescriptor getSuffix()
  {
    return suffix;
  }

  /**
   * Sets the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   * @param suffix the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   */
  public void setSuffix(SuffixDescriptor suffix)
  {
    this.suffix = suffix;
  }

  /**
   * Returns a set containing the String representation of the replication
   * servers that are defined in the replication domain for this replica.
   * @return a set containing the String representation of the replication
   * servers that are defined in the replication domain for this replica.
   */
  public Set<HostPort> getReplicationServers()
  {
    return new HashSet<>(replicationServers);
  }

  /**
   * Sets the list of replication servers (in their String representation) that
   * are defined in the replication domain for this replica.
   * @param replicationServers the list of replication servers (in their String
   * representation) that are defined in the replication domain for this
   * replica.
   */
  public void setReplicationServers(Set<HostPort> replicationServers)
  {
    this.replicationServers.clear();
    this.replicationServers.addAll(replicationServers);
  }

  /**
   * Returns the server id for the replication domain associated with this replica.
   *
   * @return the server id for the replication domain associated with this replica.
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * Sets the server id for the replication domain associated with this replica.
   *
   * @param serverId
   *          the server id for the replication domain associated with this replica.
   */
  public void setServerId(int serverId)
  {
    this.serverId = serverId;
  }

  /**
   * Returns the age of the oldest missing change.
   * @return the age of the oldest missing change.
   */
  public long getAgeOfOldestMissingChange()
  {
    return ageOfOldestMissingChange;
  }

  /**
   * Sets the age of the oldest missing change.
   * @param ageOfOldestMissingChange the age of the oldest missing change.
   */
  public void setAgeOfOldestMissingChange(long ageOfOldestMissingChange)
  {
    this.ageOfOldestMissingChange = ageOfOldestMissingChange;
  }

  /**
   * Returns the number of missing changes.
   * @return the number of missing changes.
   */
  public int getMissingChanges()
  {
    return missingChanges;
  }

  /**
   * Sets the number of missing changes.
   * @param missingChanges the number of missing changes.
   */
  public void setMissingChanges(int missingChanges)
  {
    this.missingChanges = missingChanges;
  }

  /**
   * Returns the name of the backend where this replica is defined.
   * @return the name of the backend where this replica is defined.
   */
  public String getBackendId()
  {
    return backendId;
  }

  /**
   * Sets the name of the backend where this replica is defined.
   * @param backendId the name of the backend.
   */
  public void setBackendId(String backendId)
  {
    this.backendId = backendId;
  }

  /**
   * Returns object classes of the backend attached to this replica.
   *
   * @return object classes of the backend attached to this replica.
   */
  public Set<String> getObjectClasses()
  {
    return objectClasses;
  }

  /**
   * Sets the object classes of the backend attached to this replica.
   *
   * @param objectClasses
   *          object classes of the backend attached to this replica.
   */
  public void setObjectClasses(Set<String> objectClasses)
  {
    this.objectClasses = objectClasses;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + "(domain-name=" + suffix.getDN()
        + ", server-id=" + serverId
        + ", host-name=" + server.getReplicationServerHostPort()
        + ", nb-entries=" + nbEntries
        + ", rs-port=" + server.getReplicationServerPort()
        + ", missing-changes=" + missingChanges
        + ", age-of-oldest-missing-change=" + ageOfOldestMissingChange
        + ")";
  }
}
