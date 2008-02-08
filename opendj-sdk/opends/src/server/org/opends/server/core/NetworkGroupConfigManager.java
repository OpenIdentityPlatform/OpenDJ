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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.NetworkGroupCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
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
    }

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
      networkGroup.registerWorkflow(workflowImpl);
    }

    // finally register the network group with the server
    networkGroups.put(networkGroupCfg.dn(), networkGroup);
    networkGroup.register();
  }

}

