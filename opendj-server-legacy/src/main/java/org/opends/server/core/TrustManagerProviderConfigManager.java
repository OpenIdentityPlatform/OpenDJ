/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Utils;

import java.util.ArrayList;
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
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

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

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * A mapping between the DNs of the config entries and the associated trust
   * manager providers.
   */
  private final ConcurrentHashMap<DN,TrustManagerProvider> providers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this trust manager provider config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public TrustManagerProviderConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    providers = new ConcurrentHashMap<>();
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
          logger.error(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /** {@inheritDoc} */
  public boolean isConfigurationAddAcceptable(
          TrustManagerProviderCfg configuration,
          List<LocalizableMessage> unacceptableReasons)
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



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationAdd(
                                  TrustManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
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
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      providers.put(configuration.dn(), provider);
      DirectoryServer.registerTrustManagerProvider(configuration.dn(),
                                                   provider);
    }

    return ccr;
  }



  /** {@inheritDoc} */
  public boolean isConfigurationDeleteAcceptable(
                      TrustManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // provider is in use.
    return true;
  }



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationDelete(
                                 TrustManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    DirectoryServer.deregisterTrustManagerProvider(configuration.dn());

    TrustManagerProvider provider = providers.remove(configuration.dn());
    if (provider != null)
    {
      provider.finalizeTrustManagerProvider();
    }

    return ccr;
  }



  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
                      TrustManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
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



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
                                 TrustManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


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

      return ccr;
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
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    TrustManagerProvider provider = null;
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
      DirectoryServer.registerTrustManagerProvider(configuration.dn(), provider);
    }

    return ccr;
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
  private <T extends TrustManagerProviderCfg> TrustManagerProvider<T> loadProvider(
                                   String className,
                                   T configuration,
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
      TrustManagerProvider<T> provider = providerClass.newInstance();

      if (initialize)
      {
        provider.initializeTrustManagerProvider(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!provider.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_TRUSTMANAGER_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return provider;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_TRUSTMANAGER_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

