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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.AssuredType;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.config.ConfigException;
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

  // By default changeTimeHeartbeatInterval is set to 0 in order to disable
  // this feature and not kill the tests that expect to receive special
  // messages.
  private long changeTimeHeartbeatInterval = 0;

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

  private SortedSet<String> fractionalExcludes = new TreeSet<String>();
  private SortedSet<String> fractionalIncludes = new TreeSet<String>();

  private ExternalChangelogDomainCfg eclCfg =
    new ExternalChangelogDomainFakeCfg(true, null, null);

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
   * (with some fractional configuration provided)
   */
  public DomainFakeCfg(DN baseDn, int serverId, SortedSet<String> replServers,
    List<String> fractionalExcludes, List<String> fractionalIncludes)
  {
    this(baseDn, serverId, replServers);
    if (fractionalExcludes != null)
    {
      for (String str : fractionalExcludes)
      {
        this.fractionalExcludes.add(str);
      }
    }
    if (fractionalIncludes != null)
    {
      for (String str : fractionalIncludes)
      {
        this.fractionalIncludes.add(str);
      }
    }
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
   * Create a new Domain from the provided arguments.
   *
   * @param string         The baseDN in string form.
   * @param serverId       The serverID.
   * @param replServer     The replication Server that will be used.
   *
   * @throws DirectoryException  When the provided string is not a valid DN.
   */
  public DomainFakeCfg(String string, int serverId, String replServer)
         throws DirectoryException
  {
    this.replicationServers = new TreeSet<String>();
    this.replicationServers.add(replServer);
    this.baseDn = DN.decode(string);
    this.serverId = serverId;
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
  public long getChangetimeHeartbeatInterval()
  {
    return changeTimeHeartbeatInterval;
  }

  /**
   * {@inheritDoc}
   */
  public void setChangetimeHeartbeatInterval(long changeTimeHeartbeatInterval)
  {
    this.changeTimeHeartbeatInterval = changeTimeHeartbeatInterval;
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

  public SortedSet<String> getFractionalExclude()
  {
    return fractionalExcludes;
  }

  public SortedSet<String> getFractionalInclude()
  {
    return fractionalIncludes;
  }

  public boolean isSolveConflicts()
  {
    return true;
  }

  public long getInitializationHeartbeatInterval()
  {
    return 180;
  }


  public int getInitializationWindowSize()
  {
    return 100;
  }

  public boolean hasExternalChangelogDomain() { return true; }



  /**
   * Gets the ECL Domain if it is present.
   *
   * @return Returns the ECL Domain if it is present.
   * @throws ConfigException
   *           If the ECL Domain does not exist or it could not
   *           be successfully decoded.
   */
  public ExternalChangelogDomainCfg getExternalChangelogDomain()
  throws ConfigException
  { return eclCfg; }


  /**
   * Sets the ECL Domain if it is present.
   *
   * @return Returns the ECL Domain if it is present.
   * @throws ConfigException
   *           If the ECL Domain does not exist or it could not
   *           be successfully decoded.
   */
  public void  setExternalChangelogDomain(ExternalChangelogDomainCfg eclCfg)
  throws ConfigException
  { this.eclCfg=eclCfg;}



  /**
   * Registers to be notified when the ECL Domain is added.
   *
   * @param listener
   *          The ECL Domain configuration add listener.
   * @throws ConfigException
   *          If the add listener could not be registered.
   */
  public
  void addECLDomainAddListener(
      ConfigurationAddListener<ExternalChangelogDomainCfg> listener)
  throws ConfigException
  {}



  /**
   * Deregisters an existing ECL Domain configuration add listener.
   *
   * @param listener
   *          The ECL Domain configuration add listener.
   */
  public void removeECLDomainAddListener(
      ConfigurationAddListener<ExternalChangelogDomainCfg>
  listener)
  {}



  /**
   * Registers to be notified the ECL Domain is deleted.
   *
   * @param listener
   *          The ECL Domain configuration delete listener.
   * @throws ConfigException
   *          If the delete listener could not be registered.
   */
  public void
  addECLDomainDeleteListener(
      ConfigurationDeleteListener<ExternalChangelogDomainCfg> listener)
  throws ConfigException
  {}



  /**
   * Deregisters an existing ECL Domain configuration delete listener.
   *
   * @param listener
   *          The ECL Domain configuration delete listener.
   */
  public void
  removeECLDomainDeleteListener(
      ConfigurationDeleteListener<ExternalChangelogDomainCfg> listener)
  {}

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
   **/
  public long getConflictsHistoricalPurgeDelay()
  {
    return 1440;
  }

}
