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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DN;

public class MultimasterReplicationFakeConf implements
   ReplicationSynchronizationProviderCfg
{

  @Override
  public void addReplicationChangeListener(
      ConfigurationChangeListener<ReplicationSynchronizationProviderCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void addReplicationDomainAddListener(
      ConfigurationAddListener<ReplicationDomainCfg> listener)
      throws ConfigException
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void addReplicationDomainDeleteListener(
      ConfigurationDeleteListener<ReplicationDomainCfg> listener)
      throws ConfigException
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void addReplicationServerAddListener(
      ConfigurationAddListener<ReplicationServerCfg> listener)
      throws ConfigException
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void addReplicationServerDeleteListener(
      ConfigurationDeleteListener<ReplicationServerCfg> listener)
      throws ConfigException
  {
    // TODO Auto-generated method stub

  }

  @Override
  public Class<? extends ReplicationSynchronizationProviderCfg> configurationClass()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getJavaClass()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getNumUpdateReplayThreads()
  {
    return 1;
  }

  @Override
  public ReplicationDomainCfg getReplicationDomain(String name)
      throws ConfigException
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ReplicationServerCfg getReplicationServer() throws ConfigException
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean hasReplicationServer()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String[] listReplicationDomains()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void removeReplicationChangeListener(
      ConfigurationChangeListener<ReplicationSynchronizationProviderCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeReplicationDomainAddListener(
      ConfigurationAddListener<ReplicationDomainCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeReplicationDomainDeleteListener(
      ConfigurationDeleteListener<ReplicationDomainCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeReplicationServerAddListener(
      ConfigurationAddListener<ReplicationServerCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeReplicationServerDeleteListener(
      ConfigurationDeleteListener<ReplicationServerCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void addChangeListener(
      ConfigurationChangeListener<SynchronizationProviderCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isEnabled()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void removeChangeListener(
      ConfigurationChangeListener<SynchronizationProviderCfg> listener)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public DN dn()
  {
    // TODO Auto-generated method stub
    return null;
  }

}
