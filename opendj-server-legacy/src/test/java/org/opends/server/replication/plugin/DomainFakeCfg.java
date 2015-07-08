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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.net.InetAddress;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

/**
 * This class implement a configuration object for the MultimasterDomain
 * that can be used in unit tests to instantiate ReplicationDomain.
 */
public class DomainFakeCfg implements ReplicationDomainCfg
{
  private final DN baseDN;
  private final int serverId;
  private final SortedSet<String> replicationServers;
  private long heartbeatInterval = 1000;

  /**
   * By default changeTimeHeartbeatInterval is set to 0 in order to disable this
   * feature and not kill the tests that expect to receive special messages.
   */
  private long changeTimeHeartbeatInterval;

  private IsolationPolicy policy = IsolationPolicy.REJECT_ALL_UPDATES;

  /** Assured sub mode (used when assured is true). */
  private AssuredType assuredType = AssuredType.NOT_ASSURED;
  /** Safe Data level (used when assuredType is safe data). */
  private int assuredSdLevel = 1;
  /** Timeout (in milliseconds) when waiting for acknowledgments. */
  private long assuredTimeout = 1000;
  /** Group id. */
  private final int groupId;
  /** Referrals urls to be published to other servers of the topology. */
  private SortedSet<String> refUrls = new TreeSet<>();

  private final SortedSet<String> fractionalExcludes = new TreeSet<>();
  private final SortedSet<String> fractionalIncludes = new TreeSet<>();

  private ExternalChangelogDomainCfg eclCfg =
    new ExternalChangelogDomainFakeCfg(true, null, null);
  private int windowSize = 100;

  /**
   * Creates a new Domain with the provided information
   * (assured mode disabled, default group id).
   */
  public DomainFakeCfg(DN baseDN, int serverId, SortedSet<String> replServers)
  {
    this(baseDN, serverId, replServers, -1);
  }

  /**
   * Creates a new Domain with the provided information
   * (assured mode disabled, group id provided).
   */
  public DomainFakeCfg(DN baseDN, int serverId, SortedSet<String> replServers,
    int groupId)
  {
    this.baseDN = baseDN;
    this.serverId = serverId;
    this.replicationServers = replServers;
    this.groupId = groupId;
  }

  /**
   * Creates a new Domain with the provided information
   * (assured mode info provided as well as group id).
   */
  public DomainFakeCfg(DN baseDN, int serverId, SortedSet<String> replServers,
    AssuredType assuredType, int assuredSdLevel, int groupId,
    long assuredTimeout, SortedSet<String> refUrls)
  {
    this(baseDN, serverId, replServers, groupId);
    switch(assuredType)
    {
      case NOT_ASSURED:
        break;
      case SAFE_DATA:
      case SAFE_READ:
        this.assuredType = assuredType;
        break;
    }
    this.assuredSdLevel = assuredSdLevel;
    this.assuredTimeout = assuredTimeout;
    if (refUrls != null)
    {
      this.refUrls = refUrls;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addChangeListener(
      ConfigurationChangeListener<ReplicationDomainCfg> listener)
  {
  }

  /** {@inheritDoc} */
  @Override
  public Class<? extends ReplicationDomainCfg> configurationClass()
  {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public long getHeartbeatInterval()
  {
    return heartbeatInterval ;
  }

  /** {@inheritDoc} */
  @Override
  public long getChangetimeHeartbeatInterval()
  {
    return changeTimeHeartbeatInterval;
  }

  public void setChangetimeHeartbeatInterval(long changeTimeHeartbeatInterval)
  {
    this.changeTimeHeartbeatInterval = changeTimeHeartbeatInterval;
  }

  /** {@inheritDoc} */
  @Override
  public DN getBaseDN()
  {
    return baseDN;
  }

  /** {@inheritDoc} */
  @Override
  public SortedSet<String> getReplicationServer()
  {
    return replicationServers;
  }

  /** {@inheritDoc} */
  @Override
  public InetAddress getSourceAddress() { return null; }

  /** {@inheritDoc} */
  @Override
  public int getServerId()
  {
    return serverId;
  }

  /** {@inheritDoc} */
  @Override
  public int getWindowSize()
  {
    return this.windowSize;
  }

  public void setWindowSize(int windowSize)
  {
    this.windowSize = windowSize;
  }

  /** {@inheritDoc} */
  @Override
  public void removeChangeListener(
      ConfigurationChangeListener<ReplicationDomainCfg> listener)
  {
  }

  /** {@inheritDoc} */
  @Override
  public DN dn()
  {
    try
    {
      return DN.valueOf("cn=domain, cn=domains,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");
    } catch (DirectoryException e)
    {
      return null;
    }
  }

  /**
   * Set the heartbeat interval.
   */
  public void setHeartbeatInterval(long interval)
  {
    heartbeatInterval = interval;
  }

  /**
   * Get the isolation policy.
   */
  @Override
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

  @Override
  public int getAssuredSdLevel()
  {
    return assuredSdLevel;
  }

  @Override
  public int getGroupId()
  {
    return groupId;
  }

  @Override
  public long getAssuredTimeout()
  {
    return assuredTimeout;
  }

  @Override
  public AssuredType getAssuredType()
  {
    return assuredType;
  }

  @Override
  public SortedSet<String> getReferralsUrl()
  {
    return refUrls;
  }

  @Override
  public SortedSet<String> getFractionalExclude()
  {
    return fractionalExcludes;
  }

  @Override
  public SortedSet<String> getFractionalInclude()
  {
    return fractionalIncludes;
  }

  @Override
  public boolean isSolveConflicts()
  {
    return true;
  }

  @Override
  public int getInitializationWindowSize()
  {
    return 100;
  }

  /**
   * Gets the ECL Domain if it is present.
   *
   * @return Returns the ECL Domain if it is present.
   * @throws ConfigException
   *           If the ECL Domain does not exist or it could not
   *           be successfully decoded.
   */
  @Override
  public ExternalChangelogDomainCfg getExternalChangelogDomain()
  throws ConfigException
  { return eclCfg; }


  /**
   * Sets the ECL Domain if it is present.
   *
   * @throws ConfigException
   *           If the ECL Domain does not exist or it could not
   *           be successfully decoded.
   */
  public void  setExternalChangelogDomain(ExternalChangelogDomainCfg eclCfg)
  throws ConfigException
  { this.eclCfg=eclCfg;}

  @Override
  public boolean isLogChangenumber()
  {
    return true;
  }

  /**
   * Gets the "conflicts-historical-purge-delay" property.
   * <p>
   * This delay indicates the time (in minutes) the domain keeps the
   * historical information necessary to solve conflicts.
   *
   * @return Returns the value of the "conflicts-historical-purge-delay" property.
   */
  @Override
  public long getConflictsHistoricalPurgeDelay()
  {
    return 1440;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + ", baseDN=" + baseDN + ", serverId="
        + serverId + ", replicationServers=" + replicationServers;
  }
}
