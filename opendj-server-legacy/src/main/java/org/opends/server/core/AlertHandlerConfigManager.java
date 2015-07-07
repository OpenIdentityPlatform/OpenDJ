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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.AlertHandlerCfgDefn;
import org.opends.server.admin.std.server.AlertHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.AlertHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the set of alert
 * handlers defined in the Directory Server.  It will initialize the alert
 * handlers when the server starts, and then will manage any additions,
 * removals, or modifications to any alert handlers while the server is running.
 */
public class AlertHandlerConfigManager
       implements ConfigurationChangeListener<AlertHandlerCfg>,
                  ConfigurationAddListener<AlertHandlerCfg>,
                  ConfigurationDeleteListener<AlertHandlerCfg>

{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated alert handlers. */
  private final ConcurrentHashMap<DN,AlertHandler> alertHandlers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this alert handler config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public AlertHandlerConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    alertHandlers = new ConcurrentHashMap<>();
  }

  /**
   * Initializes all alert handlers currently defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the alert
   *                           handler initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the alert handlers that is not related to
   *                                   the server configuration.
   */
  public void initializeAlertHandlers()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration = managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any alert handler entries are added or removed.
    rootConfiguration.addAlertHandlerAddListener(this);
    rootConfiguration.addAlertHandlerDeleteListener(this);


    //Initialize the existing alert handlers.
    for (String name : rootConfiguration.listAlertHandlers())
    {
      AlertHandlerCfg configuration = rootConfiguration.getAlertHandler(name);
      configuration.addChangeListener(this);

      if (configuration.isEnabled())
      {
        String className = configuration.getJavaClass();
        try
        {
          AlertHandler handler = loadHandler(className, configuration, true);
          alertHandlers.put(configuration.dn(), handler);
          DirectoryServer.registerAlertHandler(handler);
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
  @Override
  public boolean isConfigurationAddAcceptable(AlertHandlerCfg configuration,
                                              List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // alert handler.
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



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(AlertHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    AlertHandler alertHandler = null;

    // Get the name of the class and make sure we can instantiate it as an alert
    // handler.
    String className = configuration.getJavaClass();
    try
    {
      alertHandler = loadHandler(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      alertHandlers.put(configuration.dn(), alertHandler);
      DirectoryServer.registerAlertHandler(alertHandler);
    }

    return ccr;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
                      AlertHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // alert handler is in use.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 AlertHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    AlertHandler alertHandler = alertHandlers.remove(configuration.dn());
    if (alertHandler != null)
    {
      DirectoryServer.deregisterAlertHandler(alertHandler);
      alertHandler.finalizeAlertHandler();
    }

    return ccr;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(AlertHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // alert handler.
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



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 AlertHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // Get the existing alert handler if it's already enabled.
    AlertHandler existingHandler = alertHandlers.get(configuration.dn());


    // If the new configuration has the handler disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingHandler != null)
      {
        DirectoryServer.deregisterAlertHandler(existingHandler);

        AlertHandler alertHandler = alertHandlers.remove(configuration.dn());
        if (alertHandler != null)
        {
          alertHandler.finalizeAlertHandler();
        }
      }

      return ccr;
    }


    // Get the class for the alert handler.  If the handler is already enabled,
    // then we shouldn't do anything with it although if the class has changed
    // then we'll at least need to indicate that administrative action is
    // required.  If the handler is disabled, then instantiate the class and
    // initialize and register it as an alert handler.
    String className = configuration.getJavaClass();
    if (existingHandler != null)
    {
      if (! className.equals(existingHandler.getClass().getName()))
      {
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    AlertHandler alertHandler = null;
    try
    {
      alertHandler = loadHandler(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      alertHandlers.put(configuration.dn(), alertHandler);
      DirectoryServer.registerAlertHandler(alertHandler);
    }

    return ccr;
  }



  /**
   * Loads the specified class, instantiates it as an alert handler, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the alert handler class
   *                        to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the alert
   *                        handler.  It must not be {@code null}.
   * @param  initialize     Indicates whether the alert handler instance should
   *                        be initialized.
   *
   * @return  The possibly initialized alert handler.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the alert handler.
   */
  private AlertHandler loadHandler(String className,
                                   AlertHandlerCfg configuration,
                                   boolean initialize)
          throws InitializationException
  {
    try
    {
      AlertHandlerCfgDefn definition = AlertHandlerCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends AlertHandler> handlerClass =
           propertyDefinition.loadClass(className, AlertHandler.class);
      AlertHandler handler = handlerClass.newInstance();

      if (initialize)
      {
        handler.initializeAlertHandler(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!handler.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_ALERTHANDLER_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return handler;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_ALERTHANDLER_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

