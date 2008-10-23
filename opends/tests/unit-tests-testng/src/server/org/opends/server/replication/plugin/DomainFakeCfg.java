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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.SortedSet;

import java.util.TreeSet;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

/**
 * This class implement a configuration object for the MultimasterDomain
 * that can be used in unit tests to instantiate ReplicationDomain.
 */
public class DomainFakeCfg implements ReplicationDomainCfg
{
  private DN baseDn;
  private int serverId;
  private SortedSet<String> replicationServers;
  private long heartbeatInterval = 1000;
  private IsolationPolicy policy = IsolationPolicy.REJECT_ALL_UPDATES;
  
  // Is assured mode enabled or not ?
  private boolean assured = false;
  // Assured sub mode (used when assured is true)
  private AssuredType assuredType = AssuredType.NOT_ASSURED;
  // Safe Data level (used when assuredType is safe data)
  private int assuredSdLevel = 1;
  // Timeout (in milliseconds) when waiting for acknowledgments
  private long assuredTimeout = 1000;
  // Group id
  private int groupId = 1;
  // Referrals urls to be published to other servers of the topology
  SortedSet<String> refUrls = new TreeSet<String>();

  /**
   * Creates a new Domain with the provided information
   * (assured mode disabled, default group id)
   */
  public DomainFakeCfg(DN baseDn, int serverId, SortedSet<String> replServers)
  {
    this.baseDn = baseDn;
    this.serverId = serverId;
    this.replicationServers = replServers;
  }
  
  /**
   * Creates a new Domain with the provided information
   * (assured mode disabled, group id provided)
   */
  public DomainFakeCfg(DN baseDn, int serverId, SortedSet<String> replServers,
    int groupId)
  {
    this(baseDn, serverId, replServers);
    this.groupId = groupId;
  }
  
  /**
   * Creates a new Domain with the provided information
   * (assured mode info provided as well as group id)
   */
  public DomainFakeCfg(DN baseDn, int serverId, SortedSet<String> replServers,
    AssuredType assuredType, int assuredSdLevel, int groupId,
    long assuredTimeout, SortedSet<String> refUrls)
  {
    this(baseDn, serverId, replServers);
    switch(assuredType)
    {
      case NOT_ASSURED:
        assured = false;
        break;
      case SAFE_DATA:
      case SAFE_READ:
        assured = true;
        this.assuredType = assuredType;
        break;
    }
    this.assuredSdLevel = assuredSdLevel;
    this.groupId = groupId;
    this.assuredTimeout = assuredTimeout;
    if (refUrls != null)
      this.refUrls = refUrls;
  }

  /**
   * {@inheritDoc}
   */
  public void addChangeListener(
      ConfigurationChangeListener<ReplicationDomainCfg> listener)
  {

  }

  /**
   * {@inheritDoc}
   */
  public Class<? extends ReplicationDomainCfg> configurationClass()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public long getHeartbeatInterval()
  {
    return heartbeatInterval ;
  }

  /**
   * {@inheritDoc}
   */
  public long getMaxReceiveDelay()
  {
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public int getMaxReceiveQueue()
  {
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public long getMaxSendDelay()
  {
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public int getMaxSendQueue()
  {
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public DN getBaseDN()
  {
    return baseDn;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getReplicationServer()
  {
    return replicationServers;
  }

  /**
   * {@inheritDoc}
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * {@inheritDoc}
   */
  public int getWindowSize()
  {
    return 100;
  }

  /**
   * {@inheritDoc}
   */
  public void removeChangeListener(
      ConfigurationChangeListener<ReplicationDomainCfg> listener)
  {
  }

  /**
   * {@inheritDoc}
   */
  public DN dn()
  {
    try
    {
      return DN.decode("cn=domain, cn=domains,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");
    } catch (DirectoryException e)
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public ServerManagedObject<? extends Configuration> managedObject() {
    return null;
  }

  /**
   * Set the heartbeat interval.
   *
   * @param interval
   */
  public void setHeartbeatInterval(long interval)
  {
    heartbeatInterval = interval;
  }

  /**
   * Get the isolation policy.
   */
  public IsolationPolicy getIsolationPolicy()
  {
    return policy;
  }

  /**
   * Set the isolation policy.
   *
   * @param policy the policy that must now be used.
   */
  public void setIsolationPolicy(IsolationPolicy policy)
  {
    this.policy = policy;
  }

  public int getAssuredSdLevel()
  {
    return assuredSdLevel;
  }

  public int getGroupId()
  {
    return groupId;
  }

  public long getAssuredTimeout()
  {
    return assuredTimeout;
  }

  public AssuredType getAssuredType()
  {
    return assuredType;
  }
  
  public boolean isAssured()
  {
    return assured;
  }

  public SortedSet<String> getReferralsUrl()
  {
    return refUrls;
  }
}
