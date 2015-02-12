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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.forgerock.opendj.config.server.ConfigChangeResult;

/**
 * This class is used to create and object that can
 * register in the admin framework as a listener for changes, add and delete
 * on the ReplicationServer configuration objects.
 */
public class ReplicationServerListener
       implements ConfigurationAddListener<ReplicationServerCfg>,
       ConfigurationDeleteListener<ReplicationServerCfg>
{
  private final DSRSShutdownSync dsrsShutdownSync;
  private ReplicationServer replicationServer;

  /**
   * Build a ReplicationServer Listener from the given Multimaster
   * configuration.
   *
   * @param configuration The configuration that will be used to listen
   *                      for replicationServer configuration changes.
   * @param dsrsShutdownSync Synchronization object for shutdown of combined DS/RS instances.
   * @throws ConfigException if the ReplicationServerListener can't register for
   *                         listening to changes on the provided configuration
   *                         object.
   */
  public ReplicationServerListener(
      ReplicationSynchronizationProviderCfg configuration,
      DSRSShutdownSync dsrsShutdownSync) throws ConfigException
  {
    configuration.addReplicationServerAddListener(this);
    configuration.addReplicationServerDeleteListener(this);

    this.dsrsShutdownSync = dsrsShutdownSync;
    if (configuration.hasReplicationServer())
    {
      final ReplicationServerCfg cfg = configuration.getReplicationServer();
      replicationServer = new ReplicationServer(cfg, dsrsShutdownSync);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(ReplicationServerCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      replicationServer = new ReplicationServer(cfg, dsrsShutdownSync);
    }
    catch (ConfigException e)
    {
      // we should never get to this point because the configEntry has
      // already been validated in configAddisAcceptable
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
    }
    return ccr;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(
      ReplicationServerCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return ReplicationServer.isConfigurationAcceptable(cfg, unacceptableReasons);
  }

  /**
   * Shutdown the replication server.
   */
  public void shutdown()
  {
    if (replicationServer != null)
    {
      replicationServer.shutdown();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(ReplicationServerCfg cfg)
  {
    // There can be only one replicationServer, just shutdown the
    // replicationServer currently configured.
    if (replicationServer != null)
    {
      replicationServer.remove();
    }
    return new ConfigChangeResult();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
      ReplicationServerCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /**
   * Returns the associated Replication Server.
   * @return The replication server.
   */
  public ReplicationServer getReplicationServer()
  {
    return replicationServer;
  }
}
