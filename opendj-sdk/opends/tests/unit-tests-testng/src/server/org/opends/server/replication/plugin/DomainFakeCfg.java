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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.SortedSet;

import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.client.MultimasterDomainCfgClient;
import org.opends.server.admin.std.server.MultimasterDomainCfg;
import org.opends.server.types.DN;

/**
 * This class implement a configuration object for the MultimasterDomain
 * that can be used in unit tests to instantiate ReplicationDomain.
 */
public class DomainFakeCfg implements MultimasterDomainCfg
{
  private DN baseDn;
  private int serverId;
  private SortedSet<String> replicationServers;
  private long heartbeatInterval = 1000;

  /**
   * Creates a new Domain with the provided information
   */
  public DomainFakeCfg(DN baseDn, int serverId, SortedSet<String> replServers)
  {
    this.baseDn = baseDn;
    this.serverId = serverId;
    this.replicationServers = replServers;
  }

  /**
   * {@inheritDoc}
   */
  public void addChangeListener(
      ConfigurationChangeListener<MultimasterDomainCfg> listener)
  {

  }

  /**
   * {@inheritDoc}
   */
  public ManagedObjectDefinition<? extends MultimasterDomainCfgClient,
      ? extends MultimasterDomainCfg> definition()
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
  public DN getReplicationDN()
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
      ConfigurationChangeListener<MultimasterDomainCfg> listener)
  {
  }

  /**
   * {@inheritDoc}
   */
  public DN dn()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public PropertyProvider properties()
  {
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

}
