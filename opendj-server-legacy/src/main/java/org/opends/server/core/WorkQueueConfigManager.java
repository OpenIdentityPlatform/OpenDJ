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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.WorkQueueCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.WorkQueueCfg;
import org.opends.server.api.WorkQueue;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the Directory Server
 * work queue.
 */
public class WorkQueueConfigManager
       implements ConfigurationChangeListener<WorkQueueCfg>
{
  private final ServerContext serverContext;

  /**
   * Creates a new instance of this work queue config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public WorkQueueConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }


  /**
   * Initializes the Directory Server's work queue.  This should only be called
   * at server startup.
   *
   * @return  WorkQueue  The initialized work queue that should be used by the
   *                     server.
   *
   * @throws  ConfigException  If a configuration problem causes the work queue
   *                           initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the work queue that is not related to the
   *                                   server configuration.
   */
  public WorkQueue initializeWorkQueue()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Get the work queue configuration and register with it as a change
    // listener.
    WorkQueueCfg workQueueConfig = rootConfiguration.getWorkQueue();
    workQueueConfig.addChangeListener(this);


    // Get the work queue class, and load and instantiate an instance of it
    // using that configuration.
    WorkQueueCfgDefn definition = WorkQueueCfgDefn.getInstance();
    ClassPropertyDefinition propertyDefinition =
         definition.getJavaClassPropertyDefinition();
    Class<? extends WorkQueue> workQueueClass =
         propertyDefinition.loadClass(workQueueConfig.getJavaClass(),
                                      WorkQueue.class);

    try
    {
      WorkQueue workQueue = workQueueClass.newInstance();

      workQueue.initializeWorkQueue(workQueueConfig);

      return workQueue;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_WORK_QUEUE_INITIALIZATION_FAILED.
          get(workQueueConfig.getJavaClass(), workQueueConfig.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(WorkQueueCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // Changes to the work queue configuration will always be acceptable to this
    // generic implementation.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(WorkQueueCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // If the work queue class has been changed, then we should warn the user
    // that it won't take effect until the server is restarted.
    WorkQueue workQueue = DirectoryServer.getWorkQueue();
    String workQueueClass = configuration.getJavaClass();
    if (! workQueueClass.equals(workQueue.getClass().getName()))
    {
      ccr.addMessage(INFO_CONFIG_WORK_QUEUE_CLASS_CHANGE_REQUIRES_RESTART.get(
              workQueue.getClass().getName(), workQueueClass));
      ccr.setAdminActionRequired(true);
    }
    return ccr;
  }
}
