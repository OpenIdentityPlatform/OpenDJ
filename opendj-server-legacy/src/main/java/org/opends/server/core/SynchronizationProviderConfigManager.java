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

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.SynchronizationProviderCfgDefn;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.SynchronizationProviderCfg;
import org.opends.server.api.SynchronizationProvider;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of synchronization providers configured in the Directory Server.
 * It will perform the necessary initialization of those synchronization
 * providers when the server is first started, and then will manage any changes
 * to them while the server is running.
 */
public class SynchronizationProviderConfigManager
       implements ConfigurationChangeListener<SynchronizationProviderCfg>,
       ConfigurationAddListener<SynchronizationProviderCfg>,
       ConfigurationDeleteListener<SynchronizationProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The mapping between configuration entry DNs and their corresponding
   * synchronization provider implementations.
   */
  private final ConcurrentHashMap<DN,SynchronizationProvider<SynchronizationProviderCfg>> registeredProviders;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this synchronization provider config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public SynchronizationProviderConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    registeredProviders = new ConcurrentHashMap<>();
  }

  /**
   * Initializes the configuration associated with the Directory Server
   * synchronization providers.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a critical configuration problem prevents any
   *                           of the synchronization providers from starting
   *                           properly.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the synchronization providers that
   *                                   is not related to the Directory Server
   *                                   configuration.
   */
  public void initializeSynchronizationProviders()
         throws ConfigException, InitializationException
  {
    RootCfg root = serverContext.getRootConfig();
    root.addSynchronizationProviderAddListener(this);
    root.addSynchronizationProviderDeleteListener(this);

    // Initialize existing synchronization providers.
    for (String name : root.listSynchronizationProviders())
    {
      // Get the synchronization provider's configuration.
      // This will automatically decode and validate its properties.
      SynchronizationProviderCfg config = root.getSynchronizationProvider(name);

      // Register as a change listener for this synchronization provider
      // entry so that we can be notified when it is disabled or enabled.
      config.addChangeListener(this);

      // Ignore this synchronization provider if it is disabled.
      if (config.isEnabled())
      {
        // Perform initialization, load the synchronization provider's
        // implementation class and initialize it.
        SynchronizationProvider<SynchronizationProviderCfg> provider =
          getSynchronizationProvider(config);

        // Register the synchronization provider with the Directory Server.
        DirectoryServer.registerSynchronizationProvider(provider);

        // Put this synchronization provider in the hash map so that we will be
        // able to find it if it is deleted or disabled.
        registeredProviders.put(config.dn(), provider);
      }
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      SynchronizationProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Attempt to get the existing synchronization provider. This will only
    // succeed if it is currently enabled.
    DN dn = configuration.dn();
    SynchronizationProvider<SynchronizationProviderCfg> provider =
      registeredProviders.get(dn);

    // See whether the synchronization provider should be enabled.
    if (provider == null)
    {
      if (configuration.isEnabled())
      {
        // The synchronization provider needs to be enabled. Load, initialize,
        // and register the synchronization provider as per the add listener
        // method.
        try
        {
          // Perform initialization, load the synchronization provider's
          // implementation class and initialize it.
          provider = getSynchronizationProvider(configuration);

          // Register the synchronization provider with the Directory Server.
          DirectoryServer.registerSynchronizationProvider(provider);

          // Put this synchronization provider in the hash map so that we will
          // be able to find it if it is deleted or disabled.
          registeredProviders.put(configuration.dn(), provider);
        }
        catch (ConfigException e)
        {
          if (logger.isTraceEnabled())
          {
            logger.traceException(e);
            ccr.addMessage(e.getMessageObject());
            ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.addMessage(ERR_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER.get(configuration.dn(),
              stackTraceToSingleLineString(e)));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        }
      }
    }
    else
    {
      if (configuration.isEnabled())
      {
        // The synchronization provider is currently active, so we don't
        // need to do anything. Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = configuration.getJavaClass();
        if (!className.equals(provider.getClass().getName()))
        {
          ccr.setAdminActionRequired(true);
        }
      }
      else
      {
        // The connection handler is being disabled so remove it from
        // the DirectorySerevr list, shut it down and  remove it from the
        // hash map.
        DirectoryServer.deregisterSynchronizationProvider(provider);
        provider.finalizeSynchronizationProvider();
        registeredProviders.remove(dn);
      }
    }
    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      SynchronizationProviderCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return !configuration.isEnabled()
        || isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
    SynchronizationProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Register as a change listener for this synchronization provider entry
    // so that we will be notified if when it is disabled or enabled.
    configuration.addChangeListener(this);

    // Ignore this synchronization provider if it is disabled.
    if (configuration.isEnabled())
    {
      try
      {
        // Perform initialization, load the synchronization provider's
        // implementation class and initialize it.
        SynchronizationProvider<SynchronizationProviderCfg> provider =
          getSynchronizationProvider(configuration);

        // Register the synchronization provider with the Directory Server.
        DirectoryServer.registerSynchronizationProvider(provider);

        // Put this synchronization provider in the hash map so that we will be
        // able to find it if it is deleted or disabled.
        registeredProviders.put(configuration.dn(), provider);
      }
      catch (ConfigException e)
      {
        if (logger.isTraceEnabled())
        {
          logger.traceException(e);
          ccr.addMessage(e.getMessageObject());
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        ccr.addMessage(ERR_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER.get(configuration.dn(),
            stackTraceToSingleLineString(e)));
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      SynchronizationProviderCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return !configuration.isEnabled()
        || isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  /**
   * Check if the class provided in the configuration is an acceptable
   * java class for a synchronization provider.
   *
   * @param configuration       The configuration for which the class must be
   *                            checked.
   * @return                    true if the class is acceptable or false if not.
   */
  @SuppressWarnings("unchecked")
  private SynchronizationProvider<SynchronizationProviderCfg>
    getSynchronizationProvider(SynchronizationProviderCfg configuration)
    throws ConfigException
  {
    String className = configuration.getJavaClass();
    SynchronizationProviderCfgDefn d =
      SynchronizationProviderCfgDefn.getInstance();
    ClassPropertyDefinition pd =
      d.getJavaClassPropertyDefinition();

    // Load the class
    Class<? extends SynchronizationProvider> theClass;
    SynchronizationProvider<SynchronizationProviderCfg> provider;
    try
    {
       theClass = pd.loadClass(className, SynchronizationProvider.class);
    } catch (Exception e)
    {
       // Handle the exception: put a message in the unacceptable reasons.
       LocalizableMessage message = ERR_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS.
           get(className, configuration.dn(), stackTraceToSingleLineString(e));
       throw new ConfigException(message, e);
    }
    try
    {
      // Instantiate the class.
      provider = theClass.newInstance();
    } catch (Exception e)
    {
      // Handle the exception: put a message in the unacceptable reasons.
      LocalizableMessage message = ERR_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }
    try
    {
      // Initialize the Synchronization Provider.
      provider.initializeSynchronizationProvider(configuration);
    } catch (Exception e)
    {
      try
      {
        provider.finalizeSynchronizationProvider();
      }
      catch(Exception ce)
      {}

      // Handle the exception: put a message in the unacceptable reasons.
      throw new ConfigException(
          ERR_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER.get(configuration.dn(), stackTraceToSingleLineString(e)), e);
    }
    return provider;
  }

  /**
   * Check if the class provided in the configuration is an acceptable
   * java class for a synchronization provider.
   *
   * @param configuration       The configuration for which the class must be
   *                            checked.
   * @param unacceptableReasons A list containing the reasons why the class is
   *                            not acceptable.
   *
   * @return                    true if the class is acceptable or false if not.
   */
  private boolean isJavaClassAcceptable(
      SynchronizationProviderCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    String className = configuration.getJavaClass();
    SynchronizationProviderCfgDefn d =
      SynchronizationProviderCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    try
    {
      Class<? extends SynchronizationProvider> theClass =
          pd.loadClass(className, SynchronizationProvider.class);
      SynchronizationProvider provider = theClass.newInstance();

      return provider.isConfigurationAcceptable(configuration,
          unacceptableReasons);
    } catch (Exception e)
    {
      // Handle the exception: put a message in the unacceptable reasons.
      LocalizableMessage message = ERR_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER.get(
          className, configuration.dn(), stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
      SynchronizationProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // See if the entry is registered as a synchronization provider. If so,
    // deregister and stop it.
    DN dn = configuration.dn();
    SynchronizationProvider provider = registeredProviders.get(dn);
    if (provider != null)
    {
      DirectoryServer.deregisterSynchronizationProvider(provider);
      provider.finalizeSynchronizationProvider();
    }
    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
      SynchronizationProviderCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }
}
