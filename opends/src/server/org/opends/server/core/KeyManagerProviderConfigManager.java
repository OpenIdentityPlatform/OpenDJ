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



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.KeyManagerCfgDefn;
import org.opends.server.admin.std.server.KeyManagerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of key
 * manager providers defined in the Directory Server.  It will initialize the
 * key manager providers when the server starts, and then will manage any
 * additions, removals, or modifications to any key manager providers while
 * the server is running.
 */
public class KeyManagerProviderConfigManager
       implements ConfigurationChangeListener<KeyManagerCfg>,
                  ConfigurationAddListener<KeyManagerCfg>,
                  ConfigurationDeleteListener<KeyManagerCfg>

{
  // A mapping between the DNs of the config entries and the associated key
  // manager providers.
  private ConcurrentHashMap<DN,KeyManagerProvider> providers;



  /**
   * Creates a new instance of this key manager provider config manager.
   */
  public KeyManagerProviderConfigManager()
  {
    providers = new ConcurrentHashMap<DN,KeyManagerProvider>();
  }



  /**
   * Initializes all key manager providers currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the key
   *                           manager provider initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the key manager providers that is not
   *                                   related to the server configuration.
   */
  public void initializeKeyManagerProviders()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any key manager provider entries are added or removed.
    rootConfiguration.addKeyManagerAddListener(this);
    rootConfiguration.addKeyManagerDeleteListener(this);


    //Initialize the existing key manager providers.
    for (String name : rootConfiguration.listKeyManagers())
    {
      KeyManagerCfg providerConfig = rootConfiguration.getKeyManager(name);
      providerConfig.addChangeListener(this);

      if (providerConfig.isEnabled())
      {
        String className = providerConfig.getJavaImplementationClass();
        try
        {
          KeyManagerProvider provider =
               loadProvider(className, providerConfig);
          providers.put(providerConfig.dn(), provider);
          DirectoryServer.registerKeyManagerProvider(providerConfig.dn(),
                                                     provider);
        }
        catch (InitializationException ie)
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   ie.getMessage(), ie.getMessageID());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(KeyManagerCfg configuration,
                                              List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // key manager provider.
      String className = configuration.getJavaImplementationClass();
      try
      {
        loadProvider(className, null);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessage());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(KeyManagerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    KeyManagerProvider provider = null;

    // Get the name of the class and make sure we can instantiate it as a key
    // manager provider.
    String className = configuration.getJavaImplementationClass();
    try
    {
      provider = loadProvider(className, configuration);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessage());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerKeyManagerProvider(configuration.dn(), provider);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(KeyManagerCfg configuration,
                      List<String> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // provider is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 KeyManagerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    DirectoryServer.deregisterKeyManagerProvider(configuration.dn());

    KeyManagerProvider provider = providers.remove(configuration.dn());
    if (provider != null)
    {
      provider.finalizeKeyManagerProvider();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(KeyManagerCfg configuration,
                      List<String> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a key
      // manager provider.
      String className = configuration.getJavaImplementationClass();
      try
      {
        loadProvider(className, null);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessage());
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
                                 KeyManagerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the existing provider if it's already enabled.
    KeyManagerProvider existingProvider = providers.get(configuration.dn());


    // If the new configuration has the provider disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingProvider != null)
      {
        DirectoryServer.deregisterKeyManagerProvider(configuration.dn());

        KeyManagerProvider provider = providers.remove(configuration.dn());
        if (provider != null)
        {
          provider.finalizeKeyManagerProvider();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the key manager provider.  If the provider is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the provider is disabled, then instantiate the class and
    // initialize and register it as a key manager provider.
    String className = configuration.getJavaImplementationClass();
    if (existingProvider != null)
    {
      if (! className.equals(existingProvider.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    KeyManagerProvider provider = null;
    try
    {
      provider = loadProvider(className, configuration);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessage());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerKeyManagerProvider(configuration.dn(), provider);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a key manager provider, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the key manager
   *                        provider class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the key
   *                        manager provider, or {@code null} if the provider
   *                        should not be initialized.
   *
   * @return  The possibly initialized key manager provider.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the key manager provider.
   */
  private KeyManagerProvider loadProvider(String className,
                                          KeyManagerCfg configuration)
          throws InitializationException
  {
    try
    {
      KeyManagerCfgDefn definition = KeyManagerCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaImplementationClassPropertyDefinition();
      Class<? extends KeyManagerProvider> providerClass =
           propertyDefinition.loadClass(className, KeyManagerProvider.class);
      KeyManagerProvider provider = providerClass.newInstance();

      if (configuration != null)
      {
        Method method =
             provider.getClass().getMethod("initializeKeyManagerProvider",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(provider, configuration);
      }

      return provider;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_KEYMANAGER_INITIALIZATION_FAILED;
      String message = getMessage(msgID, className,
                                  String.valueOf(configuration.dn()),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }
}

