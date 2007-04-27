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

import java.util.List;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.MultimasterSynchronizationProviderCfg;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;

/**
 * This class is used to create and object that can
 * register in the admin framework as a listener for changes, add and delete
 * on the ReplicationServer configuration objects.
 *
 */
public class ReplicationServerListener
       implements ConfigurationAddListener<ReplicationServerCfg>,
       ConfigurationDeleteListener<ReplicationServerCfg>
{
  ReplicationServer replicationServer = null;

  /**
   * Build a ReplicationServer Listener from the given Multimaster
   * configuration.
   *
   * @param configuration The configuration that will be used to listen
   *                      for replicationServer configuration changes.
   *
   * @throws ConfigException if the ReplicationServerListener can't register for
   *                         listening to changes on the provided configuration
   *                         object.
   */
  public ReplicationServerListener(
      MultimasterSynchronizationProviderCfg configuration)
      throws ConfigException
  {
    configuration.addReplicationServerAddListener(this);
    configuration.addReplicationServerDeleteListener(this);

    if (configuration.hasReplicationServer())
    {
      ReplicationServerCfg server = configuration.getReplicationServer();
      replicationServer = new ReplicationServer(server);
    }
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      ReplicationServerCfg configuration)
  {
    try
    {
      replicationServer = new ReplicationServer(configuration);
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    } catch (ConfigException e)
    {
      // we should never get to this point because the configEntry has
      // already been validated in configAddisAcceptable
      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      ReplicationServerCfg configuration, List<String> unacceptableReasons)
  {
    return ReplicationServer.isConfigurationAcceptable(
      configuration, unacceptableReasons);
  }

  /**
   * Shutdown the Replication servers.
   */
  public void shutdown()
  {
    if (replicationServer != null)
      replicationServer.shutdown();
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      ReplicationServerCfg configuration)
  {
    // There can be only one replicationServer, just shutdown the
    // replicationServer currently configured.
    if (replicationServer != null)
    {
      replicationServer.shutdown();
    }
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      ReplicationServerCfg configuration, List<String> unacceptableReasons)
  {
    return true;
  }
}
