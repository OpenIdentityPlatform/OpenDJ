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
package org.opends.server.workflowelement;



import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.messages.ConfigMessages.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.WorkflowElementCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.WorkflowElementCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;


/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of workflow elements defined in the Directory Server.
 * It will perform the necessary initialization of those backends when the
 * server is first started, and then will manage any changes to them while
 * the server is running.
 */
public class WorkflowElementConfigManager
       implements ConfigurationChangeListener<WorkflowElementCfg>,
                  ConfigurationAddListener   <WorkflowElementCfg>,
                  ConfigurationDeleteListener<WorkflowElementCfg>

{
  // A mapping between the DNs of the config entries and the associated
  // workflow elements.
  private ConcurrentHashMap<DN, WorkflowElement> workflowElements;



  /**
   * Creates a new instance of this workflow config manager.
   */
  public WorkflowElementConfigManager()
  {
    workflowElements = new ConcurrentHashMap<DN, WorkflowElement>();
  }



  /**
   * Initializes all workflow elements currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the workflow
   *                           element initialization process to fail.
   * @throws InitializationException If a problem occurs while the workflow
   *                                 element is loaded and registered with
   *                                 the server
   */
  public void initializeWorkflowElements()
      throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any workflow element entries are added or removed.
    rootConfiguration.addWorkflowElementAddListener(this);
    rootConfiguration.addWorkflowElementDeleteListener(this);


    //Initialize the existing workflows.
    for (String workflowName : rootConfiguration.listWorkflowElements())
    {
      WorkflowElementCfg workflowConfiguration =
        rootConfiguration.getWorkflowElement(workflowName);
      workflowConfiguration.addChangeListener(this);

      if (workflowConfiguration.isEnabled())
      {
        loadAndRegisterWorkflowElement(workflowConfiguration);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      WorkflowElementCfg configuration,
      List<Message> unacceptableReasons)
  {
    boolean isAcceptable = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // a workflow element.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadWorkflowElement(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        isAcceptable = false;
      }
    }

    return isAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      WorkflowElementCfg configuration)
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

    configuration.addChangeListener(this);

    // If the new workflow element is enabled then create it and register it.
    if (configuration.isEnabled())
    {
      try
      {
        loadAndRegisterWorkflowElement(configuration);
      }
      catch (InitializationException de)
      {
        if (changeResult.getResultCode() == ResultCode.SUCCESS)
        {
          changeResult.setResultCode(
              DirectoryServer.getServerErrorResultCode());
        }
        changeResult.addMessage(de.getMessageObject());
      }
    }

    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      WorkflowElementCfg configuration,
      List<Message> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // worklfow element is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      WorkflowElementCfg configuration)
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );


    WorkflowElement workflowElement =
      workflowElements.remove(configuration.dn());
    if (workflowElement != null)
    {
      workflowElement.deregister();
      workflowElement.finalizeWorkflowElement();
    }


    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      WorkflowElementCfg configuration,
      List<Message> unacceptableReasons)
  {
    boolean isAcceptable = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // a workflow element.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadWorkflowElement(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        isAcceptable = false;
      }
    }

    return isAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      WorkflowElementCfg configuration)
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );


    // Get the existing workflow element if it's already enabled.
    WorkflowElement existingWorkflowElement =
      workflowElements.get(configuration.dn());

    // If the new configuration has the workflow element disabled,
    // then disable it if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingWorkflowElement != null)
      {
        workflowElements.remove(configuration.dn());
        existingWorkflowElement.deregister();
        existingWorkflowElement.finalizeWorkflowElement();
      }

      return changeResult;
    }

    // If the workflow element is disabled then create it and register it.
    if (existingWorkflowElement == null)
    {
      try
      {
        loadAndRegisterWorkflowElement(configuration);
      }
      catch (InitializationException de)
      {
        if (changeResult.getResultCode() == ResultCode.SUCCESS)
        {
          changeResult.setResultCode(
              DirectoryServer.getServerErrorResultCode());
        }
        changeResult.addMessage(de.getMessageObject());
      }
    }

    return changeResult;
  }


  /**
   * Loads a class and instanciates it as a workflow element. The workflow
   * element is initialized and registered with the server.
   *
   * @param workflowCfg  the workflow element configuration
   *
   * @throws InitializationException If a problem occurs while trying to
   *                            decode a provided string as a DN or if
   *                            the workflow element ID for a provided
   *                            workflow element conflicts with the workflow
   *                            ID of an existing workflow during workflow
   *                            registration.
   */
  private void loadAndRegisterWorkflowElement(
      WorkflowElementCfg workflowElementCfg
      ) throws InitializationException
  {
    // Load the workflow element class
    String className = workflowElementCfg.getJavaClass();
    WorkflowElement workflowElement =
      loadWorkflowElement(className, workflowElementCfg, true);

    try
    {
      // register the workflow element
      workflowElement.register();

      // keep the workflow element in the list of configured workflow
      // elements
      workflowElements.put(workflowElementCfg.dn(), workflowElement);
    }
    catch (ConfigException de)
    {
      throw new InitializationException(de.getMessageObject());
    }
  }


  /**
   * Loads a class and instanciates it as a workflow element. If requested
   * initializes the newly created instance.
   *
   * @param  className      The fully-qualified name of the workflow element
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the workflow
   *                        element.  It must not be {@code null}.
   * @param  initialize     Indicates whether the workflow element instance
   *                        should be initialized.
   *
   * @return  The possibly initialized workflow element.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the workflow element.
   */
  private WorkflowElement loadWorkflowElement(
      String className,
      WorkflowElementCfg configuration,
      boolean initialize
      ) throws InitializationException
  {
    try
    {
      WorkflowElementCfgDefn              definition;
      ClassPropertyDefinition             propertyDefinition;
      Class<? extends WorkflowElement>    workflowElementClass;
      WorkflowElement<? extends WorkflowElementCfg> workflowElement;

      definition = WorkflowElementCfgDefn.getInstance();
      propertyDefinition =
        definition.getJavaClassPropertyDefinition();
      workflowElementClass =
        propertyDefinition.loadClass(className, WorkflowElement.class);
      workflowElement =
        (WorkflowElement<? extends WorkflowElementCfg>)
          workflowElementClass.newInstance();

      if (initialize)
      {
        Method method = workflowElement.getClass().getMethod(
            "initializeWorkflowElement",
            configuration.definition().getServerConfigurationClass()
            );
        method.invoke(workflowElement, configuration);
      }
      else
      {
        Method method = workflowElement.getClass().getMethod(
            "isConfigurationAcceptable",
            WorkflowElementCfg.class,
            List.class);

        List<String> unacceptableReasons = new ArrayList<String>();
        Boolean acceptable = (Boolean) method.invoke(
            workflowElement, configuration, unacceptableReasons);

        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<String> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message =
            ERR_CONFIG_WORKFLOW_ELEMENT_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return workflowElement;
    }
    catch (Exception e)
    {
      Message message =
        ERR_CONFIG_WORKFLOW_ELEMENT_CANNOT_INITIALIZE.get(
            className, String.valueOf(configuration.dn()),
            stackTraceToSingleLineString(e));
      throw new InitializationException(message);
    }
  }

}

