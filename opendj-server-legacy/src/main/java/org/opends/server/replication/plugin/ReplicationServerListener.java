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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.server.ReplicationServerCfg;
import org.forgerock.opendj.server.config.server.ReplicationSynchronizationProviderCfg;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
    this.dsrsShutdownSync = dsrsShutdownSync;
    if (configuration.hasReplicationServer())
    {
      final ReplicationServerCfg cfg = configuration.getReplicationServer();
      // Registered only once the replication server exists: the listeners are held by the
      // server wide configuration repository and keyed by DN, so a listener registered by
      // a listener which is then discarded would be leaked until the server is restarted.
      replicationServer = new ReplicationServer(cfg, dsrsShutdownSync);
    }

    configuration.addReplicationServerAddListener(this);
    configuration.addReplicationServerDeleteListener(this);
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
      // The configEntry has already been validated in configAddisAcceptable, but the
      // listen port may have been taken since then: report why the creation failed.
      logger.error(e.getMessageObject());
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      ccr.addMessage(e.getMessageObject());
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
