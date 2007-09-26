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



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.SASLMechanismHandlerCfgDefn;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;


import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;
import org.opends.server.loggers.ErrorLogger;


/**
 * This class defines a utility that will be used to manage the set of SASL
 * mechanism handlers defined in the Directory Server.  It will initialize the
 * handlers when the server starts, and then will manage any additions,
 * removals, or modifications to any SASL mechanism handlers while the server is
 * running.
 */
public class SASLConfigManager implements
    ConfigurationChangeListener<SASLMechanismHandlerCfg>,
    ConfigurationAddListener<SASLMechanismHandlerCfg>,
    ConfigurationDeleteListener<SASLMechanismHandlerCfg>

{
  // A mapping between the DNs of the config entries and the
  // associated SASL
  // mechanism handlers.
  private ConcurrentHashMap<DN,SASLMechanismHandler> handlers;



  /**
   * Creates a new instance of this SASL mechanism handler config manager.
   */
  public SASLConfigManager()
  {
    handlers = new ConcurrentHashMap<DN,SASLMechanismHandler>();
  }



  /**
   * Initializes all SASL mechanism hanlders currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the SASL
   *                           mechanism handler initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the SASL mechanism handlers that is not
   *                                   related to the server configuration.
   */
  public void initializeSASLMechanismHandlers()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any SASL mechanism handler entries are added or
    // removed.
    rootConfiguration.addSASLMechanismHandlerAddListener(this);
    rootConfiguration.addSASLMechanismHandlerDeleteListener(this);


    //Initialize the existing SASL mechanism handlers.
    for (String handlerName : rootConfiguration.listSASLMechanismHandlers())
    {
      SASLMechanismHandlerCfg handlerConfiguration =
           rootConfiguration.getSASLMechanismHandler(handlerName);
      handlerConfiguration.addChangeListener(this);

      if (handlerConfiguration.isEnabled())
      {
        String className = handlerConfiguration.getJavaClass();
        try
        {
          SASLMechanismHandler handler = loadHandler(className,
                                                     handlerConfiguration,
                                                     true);
          handlers.put(handlerConfiguration.dn(), handler);
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
                      SASLMechanismHandlerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a SASL
      // mechanism handler.
      String className = configuration.getJavaClass();
      try
      {
        loadHandler(className, configuration, false);
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
              SASLMechanismHandlerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    SASLMechanismHandler handler = null;

    // Get the name of the class and make sure we can instantiate it as a SASL
    // mechanism handler.
    String className = configuration.getJavaClass();
    try
    {
      handler = loadHandler(className, configuration, true);
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
      handlers.put(configuration.dn(), handler);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // SASL mechanism handler is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
              SASLMechanismHandlerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    SASLMechanismHandler handler = handlers.remove(configuration.dn());
    if (handler != null)
    {
      handler.finalizeSASLMechanismHandler();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a SASL
      // mechanism handler.
      String className = configuration.getJavaClass();
      try
      {
        loadHandler(className, configuration, false);
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
              SASLMechanismHandlerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing handler if it's already enabled.
    SASLMechanismHandler existingHandler = handlers.get(configuration.dn());


    // If the new configuration has the handler disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingHandler != null)
      {
        SASLMechanismHandler handler = handlers.remove(configuration.dn());
        if (handler != null)
        {
          handler.finalizeSASLMechanismHandler();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the SASL handler.  If the handler is already enabled,
    // then we shouldn't do anything with it although if the class has changed
    // then we'll at least need to indicate that administrative action is
    // required.  If the handler is disabled, then instantiate the class and
    // initialize and register it as a SASL mechanism handler.
    String className = configuration.getJavaClass();
    if (existingHandler != null)
    {
      if (! className.equals(existingHandler.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    SASLMechanismHandler handler = null;
    try
    {
      handler = loadHandler(className, configuration, true);
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
      handlers.put(configuration.dn(), handler);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a SASL mechanism hanlder, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the SASL mechanism
   *                        handler class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the handler.
   *                        It must not be {@code null}.
   * @param  initialize     Indicates whether the SASL mechanism handler
   *                        instance should be initialized.
   *
   * @return  The possibly initialized SASL mechanism handler.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the SASL mechanism handler.
   */
  private SASLMechanismHandler loadHandler(String className,
                                           SASLMechanismHandlerCfg
                                                configuration,
                                           boolean initialize)
          throws InitializationException
  {
    try
    {
      SASLMechanismHandlerCfgDefn definition =
           SASLMechanismHandlerCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends SASLMechanismHandler> handlerClass =
           propertyDefinition.loadClass(className, SASLMechanismHandler.class);
      SASLMechanismHandler handler = handlerClass.newInstance();

      if (initialize)
      {
        Method method =
             handler.getClass().getMethod("initializeSASLMechanismHandler",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(handler, configuration);
      }
      else
      {
        Method method =
             handler.getClass().getMethod("isConfigurationAcceptable",
                                          SASLMechanismHandlerCfg.class,
                                          List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(handler, configuration,
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

          Message message = ERR_CONFIG_SASL_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return handler;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_SASL_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

