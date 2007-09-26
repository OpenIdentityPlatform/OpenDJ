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
package org.opends.server.replication.server;

import java.util.SortedSet;

import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.client.ReplicationServerCfgClient;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.types.DN;

/**
 * This Class implements an object that can be used to instantiate
 * The ReplicationServer class for tests purpose.
 */
public class ReplServerFakeConfiguration implements ReplicationServerCfg
{
  int port;
  String dirName;
  int purgeDelay;
  int serverId;
  int queueSize;
  int windowSize;
  private SortedSet<String> servers;

  public ReplServerFakeConfiguration(
      int port, String dirName, int purgeDelay, int serverId,
      int queueSize, int windowSize, SortedSet<String> servers)
  {
    this.port    = port;
    this.dirName = dirName;
    
    if (purgeDelay == 0)
    {
      this.purgeDelay = 24*60*60;
    }
    else
    {
      this.purgeDelay = purgeDelay;
    }

    this.serverId = serverId;
    
    if (queueSize == 0)
    {
      this.queueSize = 10000;
    }
    else
    {
      this.queueSize = queueSize;
    }

    if (windowSize == 0)
    {
      this.windowSize = 100;
    }
    else
    {
      this.windowSize = windowSize;
    }

    this.servers = servers;
  }

  /**
   * {@inheritDoc}
   */
  public void addChangeListener(
      ConfigurationChangeListener<ReplicationServerCfg> listener)
  {

  }

  /**
   * {@inheritDoc}
   */
  public ManagedObjectDefinition<? extends ReplicationServerCfgClient,
                                 ? extends ReplicationServerCfg> definition()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public String getReplicationDBDirectory()
  {
    return dirName;
  }

  /**
   * {@inheritDoc}
   */
  public int getReplicationPort()
  {
    return port;
  }

  /**
   * {@inheritDoc}
   */
  public long getReplicationPurgeDelay()
  {
    return purgeDelay;
  }

  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getReplicationServer()
  {
     return servers;
  }

  /**
   * {@inheritDoc}
   */
  public int getReplicationServerId()
  {
    return serverId;
  }

  /**
   * {@inheritDoc}
   */
  public int getQueueSize()
  {
    return queueSize;
  }

  /**
   * {@inheritDoc}
   */
  public int getWindowSize()
  {
    return windowSize;
  }

  /**
   * {@inheritDoc}
   */
  public void removeChangeListener(
      ConfigurationChangeListener<ReplicationServerCfg> listener)
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

}
