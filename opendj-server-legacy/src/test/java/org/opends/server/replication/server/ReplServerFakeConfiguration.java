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
package org.opends.server.replication.server;

import java.net.InetAddress;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.ReplicationServerCfg;

/**
 * This Class implements an object that can be used to instantiate
 * The ReplicationServer class for tests purpose.
 */
@SuppressWarnings("javadoc")
public class ReplServerFakeConfiguration implements ReplicationServerCfg
{
  private int port;
  private String dirName;
  private int purgeDelay;
  private int serverId;
  private int queueSize;
  private int windowSize;
  private SortedSet<String> servers;
  private boolean confidentialityEnabled;

  /*
   * Assured mode properties
   */
  /** Timeout (in milliseconds) when waiting for acknowledgments. */
  private long assuredTimeout = 1000;

  /** Group id. */
  private int groupId = 1;

  /** Threshold for status analyzers. */
  private int degradedStatusThreshold = 5000;

  /** The weight of the server. */
  private int weight = 1;

  /** The monitoring publisher period. */
  private long monitoringPeriod = 3000;
  private boolean computeChangenumber;

  /** Constructor without group id, assured info and weight. */
  public ReplServerFakeConfiguration(
      int port, String dirName, int purgeDelay, int serverId, int queueSize, int windowSize, SortedSet<String> servers)
  {
    this.port    = port;
    this.dirName = dirName != null ? dirName : "changelogDb";

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

    this.servers = servers != null ? servers : new TreeSet<String>();
  }

  /**
   * Constructor with group id and assured info.
   */
  public ReplServerFakeConfiguration(
      int port, String dirName, int purgeDelay, int serverId, int queueSize, int windowSize,
      SortedSet<String> servers, int groupId, long assuredTimeout, int degradedStatusThreshold)
  {
    this(port, dirName, purgeDelay, serverId, queueSize, windowSize, servers);
    this.groupId = groupId;
    this.assuredTimeout = assuredTimeout;
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /** Constructor with group id, assured info and weight. */
  public ReplServerFakeConfiguration(
      int port, String dirName, int purgeDelay, int serverId, int queueSize, int windowSize,
      SortedSet<String> servers, int groupId, long assuredTimeout, int degradedStatusThreshold, int weight)
  {
    this(port, dirName, purgeDelay, serverId, queueSize, windowSize,
      servers, groupId, assuredTimeout, degradedStatusThreshold);
    this.weight = weight;
  }

  @Override
  public void addChangeListener(
      ConfigurationChangeListener<ReplicationServerCfg> listener)
  {
    // not supported
  }

  @Override
  public Class<? extends ReplicationServerCfg> configurationClass()
  {
    return null;
  }

  @Override
  public String getReplicationDBDirectory()
  {
    return dirName;
  }

  @Override
  public int getReplicationPort()
  {
    return port;
  }

  @Override
  public long getReplicationPurgeDelay()
  {
    return purgeDelay;
  }

  @Override
  public SortedSet<String> getReplicationServer()
  {
     return servers;
  }

  @Override
  public int getReplicationServerId()
  {
    return serverId;
  }

  @Override
  public InetAddress getSourceAddress() { return null; }

  @Override
  public int getQueueSize()
  {
    return queueSize;
  }

  @Override
  public int getWindowSize()
  {
    return windowSize;
  }

  @Override
  public void removeChangeListener(ConfigurationChangeListener<ReplicationServerCfg> listener)
  {
    // not supported
  }

  @Override
  public DN dn()
  {
    return null;
  }

  public ServerManagedObject<? extends Configuration> managedObject() {
    return null;
  }

  @Override
  public int getGroupId()
  {
    return groupId;
  }

  @Override
  public boolean isConfidentialityEnabled()
  {
    return confidentialityEnabled;
  }

  @Override
  public long getAssuredTimeout()
  {
    return assuredTimeout;
  }

  @Override
  public int getCipherKeyLength()
  {
    return 128;
  }

  @Override
  public String getCipherTransformation()
  {
    return "AES/CBC/PKCS5Padding";
  }

  @Override
  public int getDegradedStatusThreshold()
  {
    return degradedStatusThreshold;
  }

  public void setDegradedStatusThreshold(int degradedStatusThreshold)
  {
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  @Override
  public int getWeight()
  {
    return weight;
  }

  @Override
  public long getMonitoringPeriod()
  {
    return monitoringPeriod;
  }

  public void setMonitoringPeriod(long monitoringPeriod)
  {
    this.monitoringPeriod = monitoringPeriod;
  }

  @Override
  public boolean isComputeChangeNumber()
  {
    return computeChangenumber;
  }

  public void setComputeChangeNumber(boolean computeChangenumber)
  {
    this.computeChangenumber = computeChangenumber;
  }

  public void setConfidentialityEnabled(boolean confidentialityEnabled)
  {
    this.confidentialityEnabled = confidentialityEnabled;
  }
}
