/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.KeyManagerProviderCfgDefn;
import org.forgerock.opendj.server.config.server.KeyManagerProviderCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.KeyManagerProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the set of key
 * manager providers defined in the Directory Server.  It will initialize the
 * key manager providers when the server starts, and then will manage any
 * additions, removals, or modifications to any key manager providers while
 * the server is running.
 */
public class  KeyManagerProviderConfigManager
       implements ConfigurationChangeListener<KeyManagerProviderCfg>,
                  ConfigurationAddListener<KeyManagerProviderCfg>,
                  ConfigurationDeleteListener<KeyManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated key manager providers. */
  private final ConcurrentHashMap<DN,KeyManagerProvider> providers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this key manager provider config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public KeyManagerProviderConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    providers = new ConcurrentHashMap<>();
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
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addKeyManagerProviderAddListener(this);
    rootConfiguration.addKeyManagerProviderDeleteListener(this);

    //Initialize the existing key manager providers.
    for (String name : rootConfiguration.listKeyManagerProviders())
    {
      KeyManagerProviderCfg providerConfig =
              rootConfiguration.getKeyManagerProvider(name);
      providerConfig.addChangeListener(this);

      if (providerConfig.isEnabled())
      {
        String className = providerConfig.getJavaClass();
        try
        {
          KeyManagerProvider provider =
               loadProvider(className, providerConfig, true);
          providers.put(providerConfig.dn(), provider);
          DirectoryServer.registerKeyManagerProvider(providerConfig.dn(),
                                                     provider);
        }
        catch (InitializationException ie)
        {
          logger.error(ie.getMessageObject());
          continue;
        }
      }
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(
          KeyManagerProviderCfg configuration,
          List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // key manager provider.
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

  @Override
  public ConfigChangeResult applyConfigurationAdd(
          KeyManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    KeyManagerProvider provider = null;

    // Get the name of the class and make sure we can instantiate it as a key
    // manager provider.
    String className = configuration.getJavaClass();
    try
    {
      provider = loadProvider(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerKeyManagerProvider(configuration.dn(), provider);
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
                      KeyManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // provider is in use.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 KeyManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    DirectoryServer.deregisterKeyManagerProvider(configuration.dn());

    KeyManagerProvider provider = providers.remove(configuration.dn());
    if (provider != null)
    {
      provider.finalizeKeyManagerProvider();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      KeyManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a key
      // manager provider.
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

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 KeyManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

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

      return ccr;
    }

    // Get the class for the key manager provider.  If the provider is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the provider is disabled, then instantiate the class and
    // initialize and register it as a key manager provider.
    String className = configuration.getJavaClass();
    if (existingProvider != null)
    {
      if (! className.equals(existingProvider.getClass().getName()))
      {
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    KeyManagerProvider provider = null;
    try
    {
      provider = loadProvider(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerKeyManagerProvider(configuration.dn(), provider);
    }

    return ccr;
  }

  /**
   * Loads the specified class, instantiates it as a key manager provider, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the key manager
   *                        provider class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the key
   *                        manager provider.  It must not be {@code null}.
   * @param  initialize     Indicates whether the key manager provider instance
   *                        should be initialized.
   *
   * @return  The possibly initialized key manager provider.
   *
   * @throws  InitializationException  If the provided configuration is not
   *                                   acceptable, or if a problem occurred
   *                                   while attempting to initialize the key
   *                                   manager provider using that
   *                                   configuration.
   */
  private KeyManagerProvider loadProvider(String className,
                                          KeyManagerProviderCfg configuration,
                                          boolean initialize)
          throws InitializationException
  {
    try
    {
      KeyManagerProviderCfgDefn definition =
              KeyManagerProviderCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends KeyManagerProvider> providerClass =
           propertyDefinition.loadClass(className, KeyManagerProvider.class);
      KeyManagerProvider provider = providerClass.newInstance();

      if (initialize)
      {
        provider.initializeKeyManagerProvider(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!provider.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_KEYMANAGER_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return provider;
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_KEYMANAGER_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}
