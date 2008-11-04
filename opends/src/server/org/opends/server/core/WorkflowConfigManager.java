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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import static org.opends.messages.CoreMessages.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.WorkflowCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.workflowelement.WorkflowElement;


/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of workflows defined in the Directory Server.  It will perform
 * the necessary initialization of those workflows when the server is first
 * started, and then will manage any changes to them while the server is
 * running.
 */
public class WorkflowConfigManager
       implements ConfigurationChangeListener<WorkflowCfg>,
                  ConfigurationAddListener<WorkflowCfg>,
                  ConfigurationDeleteListener<WorkflowCfg>

{
  // A mapping between the DNs of the config entries and the associated
  // workflows.
  private ConcurrentHashMap<DN, WorkflowImpl> workflows;



  /**
   * Creates a new instance of this workflow config manager.
   */
  public WorkflowConfigManager()
  {
    workflows = new ConcurrentHashMap<DN, WorkflowImpl>();
  }



  /**
   * Initializes all workflows currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the workflow
   *                           initialization process to fail.
   */
  public void initializeWorkflows()
      throws ConfigException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any workflow entries are added or removed.
    rootConfiguration.addWorkflowAddListener(this);
    rootConfiguration.addWorkflowDeleteListener(this);


    //Initialize the existing workflows.
    for (String workflowName : rootConfiguration.listWorkflows())
    {
      WorkflowCfg workflowConfiguration =
        rootConfiguration.getWorkflow(workflowName);
      workflowConfiguration.addChangeListener(this);

      if (workflowConfiguration.isEnabled())
      {
        try
        {
          createAndRegisterWorkflow(workflowConfiguration);
        }
        catch (DirectoryException de)
        {
          throw new ConfigException(de.getMessageObject());
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      WorkflowCfg   configuration,
      List<Message> unacceptableReasons)
  {
    // Nothing to check.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      WorkflowCfg configuration)
  {
    ResultCode    resultCode          = ResultCode.SUCCESS;
    boolean       adminActionRequired = false;
    List<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    // If the new network group is enabled then create it and register it.
    if (configuration.isEnabled())
    {
      try
      {
        createAndRegisterWorkflow(configuration);
      }
      catch (DirectoryException de)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = de.getResultCode();
        }

        messages.add(de.getMessageObject());
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      WorkflowCfg   configuration,
      List<Message> unacceptableReasons)
  {
    boolean acceptable = true;
    WorkflowImpl existingWorkflow = workflows.get(configuration.dn());
    if (existingWorkflow != null)
    {
      // check whether we can delete the workflow
      acceptable = checkReferenceCounter(
        existingWorkflow, unacceptableReasons);
    }

    return acceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      WorkflowCfg configuration)
  {
    ResultCode    resultCode          = ResultCode.SUCCESS;
    boolean       adminActionRequired = false;
    List<Message> messages            = new ArrayList<Message>();

    // check first whether we can remove the workflow
    WorkflowImpl workflow = workflows.remove(configuration.dn());
    if (workflow != null)
    {
      boolean acceptable = checkReferenceCounter(workflow, messages);
      if (acceptable)
      {
        // The workflow is not used anymore, we can remove it
        workflow.deregister();
        workflow.finalizeWorkflow();

        // Deregister the workflow with the internal network group
        NetworkGroup.getInternalNetworkGroup().deregisterWorkflow(
          workflow.getWorkflowId());

        // Deregister the workflow with the admin network group
        NetworkGroup.getAdminNetworkGroup().deregisterWorkflow(
          workflow.getWorkflowId());
      }
      else
      {
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      WorkflowCfg   configuration,
      List<Message> unacceptableReasons)
  {
    // Get the existing workflow if it's already enabled.
    WorkflowImpl existingWorkflow = workflows.get(configuration.dn());

    // Is this a request to disable the workflow?
    boolean acceptable = true;
    if (! configuration.isEnabled() && (existingWorkflow != null))
    {
      // check whether we can disable the workflow
      acceptable = checkReferenceCounter(
        existingWorkflow, unacceptableReasons);
    }

    return acceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      WorkflowCfg configuration)
  {
    ResultCode    resultCode          = ResultCode.SUCCESS;
    boolean       adminActionRequired = false;
    List<Message> messages            = new ArrayList<Message>();

    // Get the existing workflow if it's already enabled.
    WorkflowImpl existingWorkflow = workflows.get(configuration.dn());

    // If the new configuration has the workflow disabled, then disable it if
    // it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingWorkflow != null)
      {
        // check whether we can disable the workflow
        boolean acceptable = checkReferenceCounter(existingWorkflow, messages);
        if (acceptable)
        {
          // The workflow is not used anymore, we can remove it
          workflows.remove(configuration.dn());
          existingWorkflow.deregister();
          existingWorkflow.finalizeWorkflow();

          // Deregister the workflow with the internal network group
          NetworkGroup.getInternalNetworkGroup().deregisterWorkflow(
            existingWorkflow.getWorkflowId());

          // Deregister the workflow with the admin network group
          NetworkGroup.getAdminNetworkGroup().deregisterWorkflow(
            existingWorkflow.getWorkflowId());
        }
        else
        {
          resultCode = ResultCode.UNWILLING_TO_PERFORM;
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    // If the workflow is disabled then create and register it.
    if (existingWorkflow == null)
    {
      try
      {
        createAndRegisterWorkflow(configuration);
      }
      catch (DirectoryException de)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = de.getResultCode();
        }

        messages.add(de.getMessageObject());
      }
    }
    else
    {
      // The workflow already exist, just notify the changes to the workflow
      existingWorkflow.updateConfig(configuration);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }


  /**
   * Creates a workflow, registers the workflow with the server
   * and registers the workflow with the default network group.
   *
   * @param workflowCfg  the workflow configuration
   *
   * @throws DirectoryException If a problem occurs while trying to
   *                            decode a provided string as a DN or if
   *                            the workflow ID for a provided workflow
   *                            conflicts with the workflow ID of an existing
   *                            workflow during workflow registration.
   */
  private void createAndRegisterWorkflow(
      WorkflowCfg workflowCfg
      ) throws DirectoryException
  {
    // The ID of the workflow to create
    String workflowId = workflowCfg.getWorkflowId();

    // Create the root workflow element to associate with the workflow
    String rootWorkflowElementID = workflowCfg.getWorkflowElement();
    WorkflowElement<?> rootWorkflowElement =
      DirectoryServer.getWorkflowElement(rootWorkflowElementID);

    // Get the base DN targeted by the workflow
    DN baseDN = workflowCfg.getBaseDN();

    // Create the workflow and register it with the server
    WorkflowImpl workflowImpl =
      new WorkflowImpl(
        workflowId, baseDN, rootWorkflowElementID, rootWorkflowElement);
    workflows.put(workflowCfg.dn(), workflowImpl);
    workflowImpl.register();

    // Register the workflow with the internal network group
    NetworkGroup.getInternalNetworkGroup().registerWorkflow(workflowImpl);

    // Register the workflow with the admin network group
    NetworkGroup.getAdminNetworkGroup().registerWorkflow(workflowImpl);
  }


  /**
   * Checks whether a workflow is no more used so that we can delete
   * or disable it.
   *
   * @param workflow  the workflow to check
   * @param messages  a list of reasons that prevent the workflow to be
   *                  deleted or disabled
   * @return <code>true</code> when the workflow can be deleted or disabled
   */
  private boolean checkReferenceCounter(
      WorkflowImpl  workflow,
      List<Message> messages
      )
  {
    boolean acceptable = true;

    int refCounter = workflow.getReferenceCounter();
    if (refCounter != 0)
    {
      Message message = INFO_ERR_WORKFLOW_IN_USE.get(
        workflow.getWorkflowId(), workflow.getReferenceCounter());
      messages.add(message);
      acceptable = false;
    }

    return acceptable;
  }

}

