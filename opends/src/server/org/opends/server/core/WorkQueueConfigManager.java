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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.WorkQueueCfgDefn;
import org.opends.server.admin.std.server.WorkQueueCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.WorkQueue;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the Directory Server
 * work queue.
 */
public class WorkQueueConfigManager
       implements ConfigurationChangeListener<WorkQueueCfg>
{
  /**
   * Creates a new instance of this work queue config manager.
   */
  public WorkQueueConfigManager()
  {
    // No implementation is required.
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

      Method method = workQueue.getClass().getMethod("initializeWorkQueue",
           workQueueConfig.definition().getServerConfigurationClass());
      method.invoke(workQueue, workQueueConfig);

      return workQueue;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_WORK_QUEUE_INITIALIZATION_FAILED.
          get(workQueueConfig.getJavaClass(),
              String.valueOf(workQueueConfig.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(WorkQueueCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // Changes to the work queue configuration will always be acceptable to this
    // generic implementation.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(WorkQueueCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // If the work queue class has been changed, then we should warn the user
    // that it won't take effect until the server is restarted.
    WorkQueue workQueue = DirectoryServer.getWorkQueue();
    String workQueueClass = configuration.getJavaClass();
    if (! workQueueClass.equals(workQueue.getClass().getName()))
    {

      messages.add(INFO_CONFIG_WORK_QUEUE_CLASS_CHANGE_REQUIRES_RESTART.get(
              workQueue.getClass().getName(),
              workQueueClass));

      adminActionRequired = true;
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

