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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.TrustManagerProviderCfgDefn;
import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;


import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;
import org.opends.server.loggers.ErrorLogger;


/**
 * This class defines a utility that will be used to manage the set of trust
 * manager providers defined in the Directory Server.  It will initialize the
 * trust manager providers when the server starts, and then will manage any
 * additions, removals, or modifications to any trust manager providers while
 * the server is running.
 */
public class TrustManagerProviderConfigManager
       implements ConfigurationChangeListener<TrustManagerProviderCfg>,
                  ConfigurationAddListener<TrustManagerProviderCfg>,
                  ConfigurationDeleteListener<TrustManagerProviderCfg>

{
  // A mapping between the DNs of the config entries and the associated trust
  // manager providers.
  private ConcurrentHashMap<DN,TrustManagerProvider> providers;



  /**
   * Creates a new instance of this trust manager provider config manager.
   */
  public TrustManagerProviderConfigManager()
  {
    providers = new ConcurrentHashMap<DN,TrustManagerProvider>();
  }



  /**
   * Initializes all trust manager providers currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the trust
   *                           manager provider initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the trust manager providers that is not
   *                                   related to the server configuration.
   */
  public void initializeTrustManagerProviders()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any trust manager provider entries are added or
    // removed.
    rootConfiguration.addTrustManagerProviderAddListener(this);
    rootConfiguration.addTrustManagerProviderDeleteListener(this);


    //Initialize the existing trust manager providers.
    for (String name : rootConfiguration.listTrustManagerProviders())
    {
      TrustManagerProviderCfg providerConfig =
              rootConfiguration.getTrustManagerProvider(name);
      providerConfig.addChangeListener(this);

      if (providerConfig.isEnabled())
      {
        String className = providerConfig.getJavaClass();
        try
        {
          TrustManagerProvider provider =
               loadProvider(className, providerConfig, true);
          providers.put(providerConfig.dn(), provider);
          DirectoryServer.registerTrustManagerProvider(providerConfig.dn(),
                                                       provider);
        }
        catch (InitializationException ie)
        {
          ErrorLogger.logError(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
          TrustManagerProviderCfg configuration,
          List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // trust manager provider.
      String className = configuration.getJavaClass();
      try
      {
        loadProvider(className, configuration, false);
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
                                  TrustManagerProviderCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    TrustManagerProvider provider = null;

    // Get the name of the class and make sure we can instantiate it as a trust
    // manager provider.
    String className = configuration.getJavaClass();
    try
    {
      provider = loadProvider(className, configuration, true);
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
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerTrustManagerProvider(configuration.dn(),
                                                   provider);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      TrustManagerProviderCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // provider is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 TrustManagerProviderCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    DirectoryServer.deregisterTrustManagerProvider(configuration.dn());

    TrustManagerProvider provider = providers.remove(configuration.dn());
    if (provider != null)
    {
      provider.finalizeTrustManagerProvider();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      TrustManagerProviderCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // trust manager provider.
      String className = configuration.getJavaClass();
      try
      {
        loadProvider(className, configuration, false);
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
                                 TrustManagerProviderCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing provider if it's already enabled.
    TrustManagerProvider existingProvider = providers.get(configuration.dn());


    // If the new configuration has the provider disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingProvider != null)
      {
        DirectoryServer.deregisterTrustManagerProvider(configuration.dn());

        TrustManagerProvider provider = providers.remove(configuration.dn());
        if (provider != null)
        {
          provider.finalizeTrustManagerProvider();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the trust manager provider.  If the provider is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the provider is disabled, then instantiate the class and
    // initialize and register it as a trust manager provider.
    String className = configuration.getJavaClass();
    if (existingProvider != null)
    {
      if (! className.equals(existingProvider.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    TrustManagerProvider provider = null;
    try
    {
      provider = loadProvider(className, configuration, true);
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
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerTrustManagerProvider(configuration.dn(),
                                                   provider);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a trust manager provider, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the trust manager
   *                        provider class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the trust
   *                        manager provider.  It must not be {@code null}.
   * @param  initialize     Indicates whether the trust manager provider
   *                        instance should be initialized.
   *
   * @return  The possibly initialized trust manager provider.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the trust manager provider.
   */
  private TrustManagerProvider loadProvider(
                                   String className,
                                   TrustManagerProviderCfg configuration,
                                   boolean initialize)
          throws InitializationException
  {
    try
    {
      TrustManagerProviderCfgDefn definition =
              TrustManagerProviderCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends TrustManagerProvider> providerClass =
           propertyDefinition.loadClass(className, TrustManagerProvider.class);
      TrustManagerProvider provider = providerClass.newInstance();

      if (initialize)
      {
        Method method = provider.getClass().getMethod(
            "initializeTrustManagerProvider",
            configuration.configurationClass());
        method.invoke(provider, configuration);
      }
      else
      {
        Method method =
             provider.getClass().getMethod("isConfigurationAcceptable",
                                           TrustManagerProviderCfg.class,
                                           List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(provider, configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<Message> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message = ERR_CONFIG_TRUSTMANAGER_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return provider;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_TRUSTMANAGER_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

