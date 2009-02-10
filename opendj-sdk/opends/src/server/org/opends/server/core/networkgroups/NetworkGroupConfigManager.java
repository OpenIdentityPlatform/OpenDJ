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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

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
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;



/**
 * This class defines a utility that will be used to manage the
 * configuration for the set of network groups defined in the Directory
 * Server. It will perform the necessary initialization of those network
 * groups when the server is first started, and then will manage any
 * changes to them while the server is running.
 */
public class NetworkGroupConfigManager implements
    ConfigurationChangeListener<NetworkGroupCfg>,
    ConfigurationAddListener<NetworkGroupCfg>,
    ConfigurationDeleteListener<NetworkGroupCfg>

{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // A mapping between the DNs of the config entries and the associated
  // network groups.
  private final ConcurrentHashMap<DN, NetworkGroup> networkGroups;



  /**
   * Creates a new instance of this network group config manager.
   */
  public NetworkGroupConfigManager()
  {
    networkGroups = new ConcurrentHashMap<DN, NetworkGroup>();
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      NetworkGroupCfg configuration)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    List<Message> messages = new ArrayList<Message>();

    // Register to be notified of changes to the new network group.
    configuration.addChangeListener(this);

    // If the new network group is enabled then create it and register
    // it.
    if (configuration.isEnabled())
    {
      try
      {
        NetworkGroup networkGroup =
            NetworkGroup.createUserNetworkGroup(configuration);
        networkGroups.put(configuration.dn(), networkGroup);
      }
      catch (InitializationException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (ConfigException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      NetworkGroupCfg configuration)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    List<Message> messages = new ArrayList<Message>();

    // Enable / disable the network group as required.
    NetworkGroup networkGroup = networkGroups.get(configuration.dn());

    if (networkGroup != null && !configuration.isEnabled())
    {
      // The network group has been disabled.
      networkGroups.remove(configuration.dn());
      networkGroup.finalizeNetworkGroup();
    }
    else if (networkGroup == null && configuration.isEnabled())
    {
      // The network group has been enabled.
      try
      {
        networkGroup =
            NetworkGroup.createUserNetworkGroup(configuration);
        networkGroups.put(configuration.dn(), networkGroup);
      }
      catch (InitializationException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (ConfigException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      NetworkGroupCfg configuration)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    List<Message> messages = new ArrayList<Message>();

    NetworkGroup networkGroup =
        networkGroups.remove(configuration.dn());
    if (networkGroup != null)
    {
      networkGroup.finalizeNetworkGroup();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * Finalizes all network groups currently defined in the Directory
   * Server configuration. This should only be called at Directory
   * Server shutdown.
   */
  public void finalizeNetworkGroups()
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
        ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
        managementContext.getRootConfiguration();

    // Remove add / delete listeners.
    rootConfiguration.removeNetworkGroupAddListener(this);
    rootConfiguration.removeNetworkGroupDeleteListener(this);

    // Finalize the existing network groups.
    for (NetworkGroup networkGroup : networkGroups.values())
    {
      networkGroup.finalizeNetworkGroup();
    }

    // Clean up remaining state so that it is possible to reinitialize.
    networkGroups.clear();
  }



  /**
   * Initializes all network groups currently defined in the Directory
   * Server configuration. This should only be called at Directory
   * Server startup.
   *
   * @throws ConfigException
   *           If a critical configuration problem prevents the network
   *           group initialization from succeeding.
   * @throws InitializationException
   *           If a problem occurs while initializing the network groups
   *           that is not related to the server configuration.
   */
  public void initializeNetworkGroups() throws ConfigException,
      InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
        ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
        managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root
    // configuration so we can be notified if any network group entries
    // are added or removed.
    rootConfiguration.addNetworkGroupAddListener(this);
    rootConfiguration.addNetworkGroupDeleteListener(this);

    // Initialize the existing network groups.
    for (String networkGroupName : rootConfiguration
        .listNetworkGroups())
    {
      NetworkGroupCfg configuration =
          rootConfiguration.getNetworkGroup(networkGroupName);
      configuration.addChangeListener(this);

      List<Message> unacceptableReasons = new ArrayList<Message>();
      if (!NetworkGroup.isConfigurationAcceptable(configuration,
          unacceptableReasons))
      {
        Message message =
            ERR_CONFIG_NETWORK_GROUP_CONFIG_NOT_ACCEPTABLE.get(String
                .valueOf(configuration.dn()), StaticUtils.listToString(
                unacceptableReasons, ". "));
        throw new InitializationException(message);
      }

      if (configuration.isEnabled())
      {
        NetworkGroup networkGroup =
            NetworkGroup.createUserNetworkGroup(configuration);
        networkGroups.put(configuration.dn(), networkGroup);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      NetworkGroupCfg configuration, List<Message> unacceptableReasons)
  {
    return NetworkGroup.isConfigurationAcceptable(configuration,
        unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      NetworkGroupCfg configuration, List<Message> unacceptableReasons)
  {
    return NetworkGroup.isConfigurationAcceptable(configuration,
        unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      NetworkGroupCfg configuration, List<Message> unacceptableReasons)
  {
    // Always ok.
    return true;
  }

}
