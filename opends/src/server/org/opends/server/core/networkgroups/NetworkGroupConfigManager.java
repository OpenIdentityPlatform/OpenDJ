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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.NetworkGroupCfg;
import org.opends.server.admin.std.server.NetworkGroupCriteriaCfg;
import
  org.opends.server.admin.std.server.NetworkGroupRequestFilteringPolicyCfg;
import org.opends.server.admin.std.server.NetworkGroupResourceLimitsCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;


/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of network groups defined in the Directory Server.
 * It will perform the necessary initialization of those network groups when
 * the server is first started, and then will manage any changes to them while
 * the server is running.
 */
public class NetworkGroupConfigManager
       implements ConfigurationChangeListener<NetworkGroupCfg>,
                  ConfigurationAddListener<NetworkGroupCfg>,
                  ConfigurationDeleteListener<NetworkGroupCfg>

{
  // A mapping between the DNs of the config entries and the associated
  // network groups.
  private ConcurrentHashMap<DN, NetworkGroup> networkGroups;



  /**
   * Creates a new instance of this network group config manager.
   */
  public NetworkGroupConfigManager()
  {
    networkGroups = new ConcurrentHashMap<DN, NetworkGroup>();
  }



  /**
   * Initializes all network groups currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the network
   *                           group initialization process to fail.
   */
  public void initializeNetworkGroups()
      throws ConfigException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any network group entries are added or removed.
    rootConfiguration.addNetworkGroupAddListener(this);
    rootConfiguration.addNetworkGroupDeleteListener(this);


    //Initialize the existing network groups.
    for (String networkGroupName : rootConfiguration.listNetworkGroups())
    {
      NetworkGroupCfg networkGroupConfiguration =
           rootConfiguration.getNetworkGroup(networkGroupName);
      networkGroupConfiguration.addChangeListener(this);

      if (networkGroupConfiguration.isEnabled())
      {
        try
        {
          createAndRegisterNetworkGroup(networkGroupConfiguration);
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
      NetworkGroupCfg configuration,
      List<Message>   unacceptableReasons)
  {
    // Nothing to check.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      NetworkGroupCfg configuration)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    // If the new network group is enabled then create it and register it.
    if (configuration.isEnabled())
    {
      try
      {
        createAndRegisterNetworkGroup(configuration);
      }
      catch (DirectoryException de)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
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
      NetworkGroupCfg configuration,
      List<Message>   unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      NetworkGroupCfg configuration)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    NetworkGroup networkGroup = networkGroups.remove(configuration.dn());
    if (networkGroup != null)
    {
      networkGroup.deregister();
      networkGroup.finalizeNetworkGroup();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      NetworkGroupCfg configuration,
      List<Message>   unacceptableReasons)
  {
    // Nothing to check.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      NetworkGroupCfg configuration)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
      new ConfigChangeResult(resultCode, adminActionRequired, messages);

    // Get the existing network group if it's already enabled.
    NetworkGroup existingNetworkGroup = networkGroups.get(configuration.dn());

    // If the new configuration has the network group disabled, then disable
    // it if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingNetworkGroup != null)
      {
        networkGroups.remove(configuration.dn());
        existingNetworkGroup.deregister();
        existingNetworkGroup.finalizeNetworkGroup();
      }

      return configChangeResult;
    }

    // If the network group is disabled then create it and register it.
    if (existingNetworkGroup == null)
    {
      try
      {
        createAndRegisterNetworkGroup(configuration);
      }
      catch (DirectoryException de)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        messages.add(de.getMessageObject());
      }
    } else {
      // The network group is already defined
      // Simply update the properties
      existingNetworkGroup.setNetworkGroupPriority(configuration.getPriority());

      // Check for workflows currently registered in the network group
      // but that must be removed
      SortedSet<String> configWorkflows = configuration.getWorkflow();
      for (String id : existingNetworkGroup.getRegisteredWorkflows()) {
        if (!configWorkflows.contains(id)) {
          existingNetworkGroup.deregisterWorkflow(id);
        }
      }

      // Check for newly defined workflows
      List<String> ngWorkflows = existingNetworkGroup.getRegisteredWorkflows();
      for (String id : configuration.getWorkflow()) {
        if (! ngWorkflows.contains(id)) {
          WorkflowImpl workflowImpl =
                  (WorkflowImpl) WorkflowImpl.getWorkflow(id);
          try {
            existingNetworkGroup.registerWorkflow(workflowImpl);
          } catch (DirectoryException de) {
            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = de.getResultCode();
            }
            messages.add(de.getMessageObject());
          }
        }
      }
    }

    configChangeResult =
      new ConfigChangeResult(resultCode, adminActionRequired, messages);

    return configChangeResult;
  }


  /**
   * Creates and registers a network group.
   *
   * @param networkGroupCfg  the network group configuration
   *
   * @throws DirectoryException If a problem occurs while trying to
   *                            register a network group.
   */
  private void createAndRegisterNetworkGroup(
      NetworkGroupCfg networkGroupCfg
      ) throws DirectoryException
  {
    // create the network group
    String networkGroupId = networkGroupCfg.getNetworkGroupId();
    NetworkGroup networkGroup = new NetworkGroup(networkGroupId);

    // register the workflows with the network group
    for (String workflowID: networkGroupCfg.getWorkflow())
    {
      WorkflowImpl workflowImpl =
        (WorkflowImpl) WorkflowImpl.getWorkflow(workflowID);
      if (workflowImpl == null)
      {
        // The workflow does not exist, log an error message
        // and skip the workflow
        Message message = INFO_ERR_WORKFLOW_DOES_NOT_EXIST.get(
          workflowID, networkGroupId);
        logError(message);
      }
      else
      {
        networkGroup.registerWorkflow(workflowImpl);
      }
    }

    // register the root DSE workflow with the network group
    WorkflowImpl rootDSEworkflow =
      (WorkflowImpl) WorkflowImpl.getWorkflow("__root.dse__#");
    networkGroup.registerWorkflow(rootDSEworkflow);

    // finally register the network group with the server
    networkGroups.put(networkGroupCfg.dn(), networkGroup);
    networkGroup.register();
    // Set the priority
    networkGroup.setNetworkGroupPriority(networkGroupCfg.getPriority());

    // Set the criteria
    NetworkGroupCriteriaCfg criteriaCfg;
    NetworkGroupCriteria criteria;
    try {
      criteriaCfg = networkGroupCfg.getNetworkGroupCriteria();
    } catch (ConfigException ce) {
      criteriaCfg = null;
    }

    criteria = new NetworkGroupCriteria(criteriaCfg);
    networkGroup.setCriteria(criteria);

    // Add a config listener on the criteria
    try {
      networkGroupCfg.addNetworkGroupCriteriaAddListener(criteria);
      networkGroupCfg.addNetworkGroupCriteriaDeleteListener(criteria);
    } catch (ConfigException ex) {
      throw new DirectoryException(ResultCode.UNDEFINED,
            ex.getMessageObject());
    }

    // Set the resource limits
    NetworkGroupResourceLimitsCfg limitsCfg;
    ResourceLimits limits;

    try {
      limitsCfg = networkGroupCfg.getNetworkGroupResourceLimits();
    } catch (ConfigException ex) {
      limitsCfg = null;
    }
    limits = new ResourceLimits(limitsCfg);
    networkGroup.setResourceLimits(limits);

    // Add a config listener on the resource limits
    try {
      networkGroupCfg.addNetworkGroupResourceLimitsAddListener(limits);
      networkGroupCfg.addNetworkGroupResourceLimitsDeleteListener(limits);
    } catch (ConfigException ex) {
      throw new DirectoryException(ResultCode.UNDEFINED,
              ex.getMessageObject());
    }

    // Set the request filtering policy
    NetworkGroupRequestFilteringPolicyCfg policyCfg;
    RequestFilteringPolicy policy;
    try {
      policyCfg = networkGroupCfg.getNetworkGroupRequestFilteringPolicy();
    } catch (ConfigException ex) {
      policyCfg = null;
    }
    policy = new RequestFilteringPolicy(policyCfg);
    networkGroup.setRequestFilteringPolicy(policy);

    // Add a config listener on the request filtering policy
    try {
      networkGroupCfg.addNetworkGroupRequestFilteringPolicyAddListener
              (policy);
      networkGroupCfg.addNetworkGroupRequestFilteringPolicyDeleteListener(
              policy);
    } catch (ConfigException ex) {
      throw new DirectoryException(ResultCode.UNDEFINED,
              ex.getMessageObject());
    }

  }

}

