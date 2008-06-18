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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.ExtensionCfgDefn;
import org.opends.server.admin.std.server.ExtensionCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.Extension;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of
 * extensions defined in the Directory Server.  It will initialize the
 * extensions when the server starts, and then will manage any
 * additions, removals, or modifications to any extensions while
 * the server is running.
 */
public class  ExtensionConfigManager
       implements ConfigurationChangeListener<ExtensionCfg>,
                  ConfigurationAddListener<ExtensionCfg>,
                  ConfigurationDeleteListener<ExtensionCfg>

{
  // A mapping between the DNs of the config entries and the associated
  // extensions.
  private ConcurrentHashMap<DN,Extension> extensions;



  /**
   * Creates a new instance of this extension config manager.
   */
  public ExtensionConfigManager()
  {
    extensions = new ConcurrentHashMap<DN,Extension>();
  }



  /**
   * Initializes all extensions currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the
   *                           extension initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the extensions that is not
   *                                   related to the server configuration.
   */
  public void initializeExtensions()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any extension entries are added or removed.
    rootConfiguration.addExtensionAddListener(this);
    rootConfiguration.addExtensionDeleteListener(this);


    //Initialize the existing extensions.
    for (String name : rootConfiguration.listExtensions())
    {
      ExtensionCfg extensionConfig =
              rootConfiguration.getExtension(name);
      extensionConfig.addChangeListener(this);

      if (extensionConfig.isEnabled())
      {
        String className = extensionConfig.getJavaClass();
        try
        {
          Extension extension =
               loadExtension(className, extensionConfig, true);
          extensions.put(extensionConfig.dn(), extension);
          DirectoryServer.registerExtension(extensionConfig.dn(),
                                                     extension);
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
          ExtensionCfg configuration,
          List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // extension.
      String className = configuration.getJavaClass();
      try
      {
        loadExtension(className, configuration, false);
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
          ExtensionCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    Extension extension = null;

    // Get the name of the class and make sure we can instantiate it as an
    // extension.
    String className = configuration.getJavaClass();
    try
    {
      extension = loadExtension(className, configuration, true);
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
      extensions.put(configuration.dn(), extension);
      DirectoryServer.registerExtension(configuration.dn(), extension);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      ExtensionCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 ExtensionCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    DirectoryServer.deregisterExtension(configuration.dn());

    Extension extension = extensions.remove(configuration.dn());
    if (extension != null)
    {
      extension.finalizeExtension();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      ExtensionCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // extension.
      String className = configuration.getJavaClass();
      try
      {
        loadExtension(className, configuration, false);
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
                                 ExtensionCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing extension if it's already enabled.
    Extension existingExtension = extensions.get(configuration.dn());


    // If the new configuration has the extension disabled, then disable it if
    // it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingExtension != null)
      {
        DirectoryServer.deregisterExtension(configuration.dn());

        Extension extension = extensions.remove(configuration.dn());
        if (extension != null)
        {
          extension.finalizeExtension();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the extension.  If the extension is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the extension is disabled, then instantiate the class
    // and initialize and register it as a extension.
    String className = configuration.getJavaClass();
    if (existingExtension != null)
    {
      if (! className.equals(existingExtension.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    Extension extension = null;
    try
    {
      extension = loadExtension(className, configuration, true);
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
      extensions.put(configuration.dn(), extension);
      DirectoryServer.registerExtension(configuration.dn(), extension);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a extension, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the extension
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        extension.  It must not be {@code null}.
   * @param  initialize     Indicates whether the extension instance
   *                        should be initialized.
   *
   * @return  The possibly initialized extension.
   *
   * @throws  InitializationException  If the provided configuration is not
   *                                   acceptable, or if a problem occurred
   *                                   while attempting to initialize the
   *                                   extension using that
   *                                   configuration.
   */
  private Extension loadExtension(String className,
                                          ExtensionCfg configuration,
                                          boolean initialize)
          throws InitializationException
  {
    try
    {
      ExtensionCfgDefn definition =
              ExtensionCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends Extension> extensionClass =
           propertyDefinition.loadClass(className, Extension.class);
      Extension extension = extensionClass.newInstance();


      if (initialize)
      {
        Method method = extension.getClass().getMethod(
            "initializeExtension", configuration.configurationClass());
        method.invoke(extension, configuration);
      }
      else
      {
        Method method =
             extension.getClass().getMethod("isConfigurationAcceptable",
                                           ExtensionCfg.class,
                                           List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(extension, configuration,
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

          Message message = ERR_CONFIG_EXTENSION_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return extension;
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_EXTENSION_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

