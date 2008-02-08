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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.MonitorProviderCfgDefn;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of monitor
 * providers defined in the Directory Server.  It will initialize the monitor
 * providers when the server starts, and then will manage any additions,
 * removals, or modifications to any providers while the server is running.
 */
public class MonitorConfigManager
       implements ConfigurationChangeListener<MonitorProviderCfg>,
                  ConfigurationAddListener<MonitorProviderCfg>,
                  ConfigurationDeleteListener<MonitorProviderCfg>

{
  // A mapping between the DNs of the config entries and the associated monitor
  // providers.
  private ConcurrentHashMap<DN,MonitorProvider> monitors;



  /**
   * Creates a new instance of this monitor provider config manager.
   */
  public MonitorConfigManager()
  {
    monitors = new ConcurrentHashMap<DN,MonitorProvider>();
  }



  /**
   * Initializes all monitor providers currently defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the monitor
   *                           provider initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the monitor providers that is not related
   *                                   to the server configuration.
   */
  public void initializeMonitorProviders()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any monitor provider entries are added or removed.
    rootConfiguration.addMonitorProviderAddListener(this);
    rootConfiguration.addMonitorProviderDeleteListener(this);


    //Initialize the existing monitor providers.
    for (String name : rootConfiguration.listMonitorProviders())
    {
      MonitorProviderCfg monitorConfig =
           rootConfiguration.getMonitorProvider(name);
      monitorConfig.addChangeListener(this);

      if (monitorConfig.isEnabled())
      {
        String className = monitorConfig.getJavaClass();
        try
        {
          MonitorProvider<? extends MonitorProviderCfg> monitor =
               loadMonitor(className, monitorConfig);
          monitors.put(monitorConfig.dn(), monitor);
          if (monitor.getUpdateInterval() > 0)
          {
            monitor.start();
          }
          DirectoryServer.registerMonitorProvider(monitor);
        }
        catch (InitializationException ie)
        {
          logError(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      MonitorProviderCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // monitor provider.
      String className = configuration.getJavaClass();
      try
      {
        loadMonitor(className, null);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
                                 MonitorProviderCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    MonitorProvider<? extends MonitorProviderCfg> monitor = null;

    // Get the name of the class and make sure we can instantiate it as a
    // monitor provider.
    String className = configuration.getJavaClass();
    try
    {
      monitor = loadMonitor(className, configuration);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      monitors.put(configuration.dn(), monitor);
      if (monitor.getUpdateInterval() > 0)
      {
        monitor.start();
      }
      DirectoryServer.registerMonitorProvider(monitor);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      MonitorProviderCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // It will always be acceptable to delete or disable a monitor provider.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 MonitorProviderCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    MonitorProvider monitor = monitors.remove(configuration.dn());
    if (monitor != null)
    {
      String lowerName = toLowerCase(monitor.getMonitorInstanceName());
      DirectoryServer.deregisterMonitorProvider(lowerName);
      monitor.finalizeMonitorProvider();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      MonitorProviderCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // monitor provider.
      String className = configuration.getJavaClass();
      try
      {
        loadMonitor(className, null);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 MonitorProviderCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing monitor provider if it's already enabled.
    MonitorProvider existingMonitor = monitors.get(configuration.dn());


    // If the new configuration has the monitor disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingMonitor != null)
      {
        String lowerName =
             toLowerCase(existingMonitor.getMonitorInstanceName());
        DirectoryServer.deregisterMonitorProvider(lowerName);

        MonitorProvider monitor = monitors.remove(configuration.dn());
        if (monitor != null)
        {
          monitor.finalizeMonitorProvider();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the monitor provider.  If the monitor is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the monitor is disabled, then instantiate the class and
    // initialize and register it as a monitor provider.
    String className = configuration.getJavaClass();
    if (existingMonitor != null)
    {
      if (! className.equals(existingMonitor.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    MonitorProvider<? extends MonitorProviderCfg> monitor = null;
    try
    {
      monitor = loadMonitor(className, configuration);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      monitors.put(configuration.dn(), monitor);
      if (monitor.getUpdateInterval() > 0)
      {
        monitor.start();
      }
      DirectoryServer.registerMonitorProvider(monitor);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a monitor provider, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the monitor provider
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the monitor
   *                        provider, or {@code null} if the monitor provider
   *                        should not be initialized.
   *
   * @return  The possibly initialized monitor provider.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the monitor provider.
   */
  private MonitorProvider<? extends MonitorProviderCfg>
               loadMonitor(String className, MonitorProviderCfg configuration)
          throws InitializationException
  {
    try
    {
      MonitorProviderCfgDefn definition =
           MonitorProviderCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends MonitorProvider> providerClass =
           propertyDefinition.loadClass(className, MonitorProvider.class);
      MonitorProvider monitor = providerClass.newInstance();

      if (configuration != null)
      {
        Method method = monitor.getClass().getMethod(
            "initializeMonitorProvider", configuration.configurationClass());
        method.invoke(monitor, configuration);
      }

      return (MonitorProvider<? extends MonitorProviderCfg>) monitor;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_MONITOR_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

