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
import org.opends.messages.Message;



import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.SynchronizationProviderCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The mapping between configuration entry DNs and their corresponding
  // synchronization provider implementations.
  private ConcurrentHashMap<DN,
    SynchronizationProvider<SynchronizationProviderCfg>> registeredProviders =
      new ConcurrentHashMap<DN,
        SynchronizationProvider<SynchronizationProviderCfg>>();




  /**
   * Creates a new instance of this synchronization provider config manager.
   */
  public SynchronizationProviderConfigManager()
  {
    // No implementation is required.
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
    // Create an internal server management context and retrieve
    // the root configuration which has the synchronization provider relation.
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();

    // Register as an add and delete listener so that we can
    // be notified when new synchronization providers are added or existing
    // sycnhronization providers are removed.
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



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      SynchronizationProviderCfg configuration)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
            messages.add(e.getMessageObject());
            resultCode = DirectoryServer.getServerErrorResultCode();
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          messages.add(ERR_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER.get(
                  String.valueOf(configuration.getJavaImplementationClass()),
                  String.valueOf(configuration.dn())));
          resultCode = DirectoryServer.getServerErrorResultCode();
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
        String className = configuration.getJavaImplementationClass();
        if (!className.equals(provider.getClass().getName()))
        {
          adminActionRequired = true;
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
    // Return the configuration result.
    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      SynchronizationProviderCfg configuration,
      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // It's enabled so always validate the class.
      return isJavaClassAcceptable(configuration, unacceptableReasons);
    } else
    {
      // It's disabled so ignore it.
      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
    SynchronizationProviderCfg configuration)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
          messages.add(e.getMessageObject());
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(ERR_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER.get(
                String.valueOf(configuration.getJavaImplementationClass()),
                           String.valueOf(configuration.dn())));
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }

    // Return the configuration result.
    return new ConfigChangeResult(resultCode, adminActionRequired,
        messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      SynchronizationProviderCfg configuration,
      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // It's enabled so always validate the class.
      return isJavaClassAcceptable(configuration, unacceptableReasons);
    } else
    {
      // It's disabled so ignore it.
      return true;
    }
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
    String className = configuration.getJavaImplementationClass();
    SynchronizationProviderCfgDefn d =
      SynchronizationProviderCfgDefn.getInstance();
    ClassPropertyDefinition pd =
      d.getJavaImplementationClassPropertyDefinition();

    // Load the class
    Class<? extends SynchronizationProvider> theClass;
    SynchronizationProvider<SynchronizationProviderCfg> provider;
    try
    {
       theClass = pd.loadClass(className, SynchronizationProvider.class);
    } catch (Exception e)
    {
       // Handle the exception: put a message in the unacceptable reasons.
       Message message = ERR_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS.
           get(String.valueOf(className), String.valueOf(configuration.dn()),
               stackTraceToSingleLineString(e));
       throw new ConfigException(message, e);
    }
    try
    {
      // Instantiate the class.
      provider = theClass.newInstance();
    } catch (Exception e)
    {
      // Handle the exception: put a message in the unacceptable reasons.
      Message message = ERR_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER.
          get(String.valueOf(className), String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }
    try
    {
      // Initialize the Synchronization Provider.
      provider.initializeSynchronizationProvider(configuration);
    } catch (Exception e)
    {
      // Handle the exception: put a message in the unacceptable reasons.
      Message message = ERR_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER.get(
              String.valueOf(className), String.valueOf(configuration.dn()));
      throw new ConfigException(message, e);
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
      List<Message> unacceptableReasons)
  {
    String className = configuration.getJavaImplementationClass();
    SynchronizationProviderCfgDefn d =
      SynchronizationProviderCfgDefn.getInstance();
    ClassPropertyDefinition pd =
      d.getJavaImplementationClassPropertyDefinition();

    // Load the class and cast it to a synchronizationProvider.
    SynchronizationProvider provider = null;
    Class<? extends SynchronizationProvider> theClass;
    try
    {
       theClass = pd.loadClass(className, SynchronizationProvider.class);
       provider = theClass.newInstance();
    } catch (Exception e)
    {
       // Handle the exception: put a message in the unacceptable reasons.
       Message message = ERR_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS.get(
               String.valueOf(className),
               String.valueOf(configuration.dn()),
               stackTraceToSingleLineString(e));
       unacceptableReasons.add(message);
       return false;
    }
    // Check that the implementation class implements the correct interface.
    try
    {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("isConfigurationAcceptable",
                                         SynchronizationProviderCfg.class,
                                         List.class);
      Boolean acceptable = (Boolean) method.invoke(provider, configuration,
                                                   unacceptableReasons);

      if (! acceptable)
      {
        return false;
      }
    } catch (Exception e)
    {
      // Handle the exception: put a message in the unacceptable reasons.
      Message message = ERR_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER.get(
              String.valueOf(className),
              String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }

    // The class is valid as far as we can tell.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      SynchronizationProviderCfg configuration)
  {
    //  Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    // See if the entry is registered as a synchronization provider. If so,
    // deregister and stop it.
    DN dn = configuration.dn();
    SynchronizationProvider provider = registeredProviders.get(dn);
    if (provider != null)
    {
      DirectoryServer.deregisterSynchronizationProvider(provider);
      provider.finalizeSynchronizationProvider();
    }
    return new ConfigChangeResult(resultCode, adminActionRequired);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      SynchronizationProviderCfg configuration,
      List<Message> unacceptableReasons)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }
}




