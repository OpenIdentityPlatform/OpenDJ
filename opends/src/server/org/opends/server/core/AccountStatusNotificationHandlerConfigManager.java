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



import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.AccountStatusNotificationHandlerCfgDefn;
import org.opends.server.admin.std.server.AccountStatusNotificationHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



/**
 * This class defines a utility that will be used to manage the set of account
 * status notification handlers defined in the Directory Server.  It will
 * initialize the handlers when the server starts, and then will manage any
 * additions, removals, or modifications to any notification handlers while the
 * server is running.
 */
public class AccountStatusNotificationHandlerConfigManager
       implements
          ConfigurationChangeListener <AccountStatusNotificationHandlerCfg>,
          ConfigurationAddListener    <AccountStatusNotificationHandlerCfg>,
          ConfigurationDeleteListener <AccountStatusNotificationHandlerCfg>
{

  // A mapping between the DNs of the config entries and the associated
  // notification handlers.
  private ConcurrentHashMap<DN,AccountStatusNotificationHandler>
          notificationHandlers;


  /**
   * Creates a new instance of this account status notification handler config
   * manager.
   */
  public AccountStatusNotificationHandlerConfigManager()
  {
    notificationHandlers =
         new ConcurrentHashMap<DN,AccountStatusNotificationHandler>();
  }



  /**
   * Initializes all account status notification handlers currently defined in
   * the Directory Server configuration.  This should only be called at
   * Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the
   *                           notification handler initialization process to
   *                           fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the account status notification handlers
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeNotificationHandlers()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
      ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
      managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root configuration so
    // we can be notified if any account status notification handler entry
    // is added or removed.
    rootConfiguration.addAccountStatusNotificationHandlerAddListener (this);
    rootConfiguration.addAccountStatusNotificationHandlerDeleteListener (this);

    // Initialize existing account status notification handlers.
    for (String handlerName:
         rootConfiguration.listAccountStatusNotificationHandlers())
    {
      // Get the account status notification handler's configuration.
      AccountStatusNotificationHandlerCfg config =
        rootConfiguration.getAccountStatusNotificationHandler (handlerName);

      // Register as a change listener for this notification handler
      // entry so that we will be notified of any changes that may be
      // made to it.
      config.addChangeListener (this);

      // Ignore this notification handler if it is disabled.
      if (config.isEnabled())
      {
        // Load the notification handler implementation class.
        String className = config.getNotificationHandlerClass();
        loadAndInstallNotificationHandler (className, config);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      AccountStatusNotificationHandlerCfg configuration,
      List<String>  unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // entry cache.
      String className = configuration.getNotificationHandlerClass();
      try
      {
        // Load the class but don't initialize it.
        loadNotificationHandler(className, configuration, true);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessage());
        status = false;
      }
    }

    return status;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      AccountStatusNotificationHandlerCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    // Get the configuration entry DN and the associated handler class.
    DN configEntryDN = configuration.dn();
    AccountStatusNotificationHandler handler = notificationHandlers.get(
        configEntryDN
        );

    // If the new configuration has the notification handler disabled,
    // then remove it from the mapping list and clean it.
    if (! configuration.isEnabled())
    {
      if (handler != null)
      {
        uninstallNotificationHandler (configEntryDN);
      }

      return changeResult;
    }

    // At this point, new configuration is enabled...
    // If the current notification handler is already enabled then we
    // don't do anything unless the class has changed in which case we
    // should indicate that administrative action is required.
    String newClassName = configuration.getNotificationHandlerClass();
    if (handler != null)
    {
      String curClassName = handler.getClass().getName();
      boolean classIsNew = (! newClassName.equals (curClassName));
      if (classIsNew)
      {
        changeResult.setAdminActionRequired (true);
      }
      return changeResult;
    }

    // New entry cache is enabled and there were no previous one.
    // Instantiate the new class and initalize it.
    try
    {
      loadAndInstallNotificationHandler (newClassName, configuration);
    }
    catch (InitializationException ie)
    {
      changeResult.addMessage (ie.getMessage());
      changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
      return changeResult;
    }

    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      AccountStatusNotificationHandlerCfg configuration,
      List<String> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    // Make sure that no entry already exists with the specified DN.
    DN configEntryDN = configuration.dn();
    if (notificationHandlers.containsKey(configEntryDN))
    {
      int    msgID   = MSGID_CONFIG_ACCTNOTHANDLER_EXISTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReasons.add (message);
      status = false;
    }
    // If configuration is enabled then check that notification class
    // can be instantiated.
    else if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // an entry cache.
      String className = configuration.getNotificationHandlerClass();
      try
      {
        // Load the class but don't initialize it.
        loadNotificationHandler (className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessage());
        status = false;
      }
    }

    return status;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      AccountStatusNotificationHandlerCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    // Register a change listener with it so we can be notified of changes
    // to it over time.
    configuration.addChangeListener(this);

    if (configuration.isEnabled())
    {
      // Instantiate the class as an entry cache and initialize it.
      String className = configuration.getNotificationHandlerClass();
      try
      {
        loadAndInstallNotificationHandler (className, configuration);
      }
      catch (InitializationException ie)
      {
        changeResult.addMessage (ie.getMessage());
        changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
        return changeResult;
      }
    }

    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      AccountStatusNotificationHandlerCfg configuration,
      List<String> unacceptableReasons
      )
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      AccountStatusNotificationHandlerCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    uninstallNotificationHandler (configuration.dn());

    return changeResult;
  }


  /**
   * Loads the specified class, instantiates it as a notification handler,
   * and optionally initializes that instance. Any initialized notification
   * handler is registered in the server.
   *
   * @param  className      The fully-qualified name of the notification handler
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        notification handler, or {@code null} if the
   *                        notification handler should not be initialized.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the notification handler.
   */
  private void loadAndInstallNotificationHandler(
       String className,
       AccountStatusNotificationHandlerCfg configuration
       )
       throws InitializationException
  {
    // Load the notification handler class...
    AccountStatusNotificationHandler
        <? extends AccountStatusNotificationHandlerCfg> handlerClass;
    handlerClass = loadNotificationHandler (className, configuration, true);

    // ... and install the entry cache in the server.
    DN configEntryDN = configuration.dn();
    notificationHandlers.put (configEntryDN, handlerClass);
    DirectoryServer.registerAccountStatusNotificationHandler(
        configEntryDN,
        handlerClass
        );
  }


  /**
   * Loads the specified class, instantiates it as a notification handler,
   * and optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the notification handler
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        notification handler.  It must not be {@code null}.
   * @param  initialize     Indicates whether the account status notification
   *                        handler instance should be initialized.
   *
   * @return  The possibly initialized notification handler.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the notification handler.
   */
  private
    AccountStatusNotificationHandler
       <? extends AccountStatusNotificationHandlerCfg>
    loadNotificationHandler(
       String className,
       AccountStatusNotificationHandlerCfg configuration,
       boolean initialize)
       throws InitializationException
  {
    try
    {
      AccountStatusNotificationHandlerCfgDefn definition;
      ClassPropertyDefinition propertyDefinition;
      Class<? extends AccountStatusNotificationHandler> handlerClass;
      AccountStatusNotificationHandler
         <? extends AccountStatusNotificationHandlerCfg> notificationHandler;

      definition = AccountStatusNotificationHandlerCfgDefn.getInstance();
      propertyDefinition =
          definition.getNotificationHandlerClassPropertyDefinition();
      handlerClass = propertyDefinition.loadClass(
          className,
          AccountStatusNotificationHandler.class
          );
      notificationHandler =
        (AccountStatusNotificationHandler
            <? extends AccountStatusNotificationHandlerCfg>)
        handlerClass.newInstance();

      if (initialize)
      {
        Method method = notificationHandler.getClass().getMethod(
            "initializeStatusNotificationHandler",
            configuration.definition().getServerConfigurationClass()
            );
        method.invoke(notificationHandler, configuration);
      }
      else
      {
        Method method =
             notificationHandler.getClass().getMethod(
                  "isConfigurationAcceptable",
                  AccountStatusNotificationHandlerCfg.class, List.class);

        List<String> unacceptableReasons = new ArrayList<String>();
        Boolean acceptable = (Boolean) method.invoke(notificationHandler,
                                                     configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<String> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          int    msgID   = MSGID_CONFIG_ACCTNOTHANDLER_CONFIG_NOT_ACCEPTABLE;
          String message = getMessage(msgID, String.valueOf(configuration.dn()),
                                      buffer.toString());
          throw new InitializationException(msgID, message);
        }
      }

      return notificationHandler;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_ACCTNOTHANDLER_INITIALIZATION_FAILED;
      String message = getMessage(
          msgID, className,
          String.valueOf(configuration.dn()),
          stackTraceToSingleLineString(e)
          );
      throw new InitializationException(msgID, message, e);
    }
  }


  /**
   * Remove a notification handler that has been installed in the server.
   *
   * @param configEntryDN  the DN of the configuration enry associated to
   *                       the notification handler to remove
   */
  private void uninstallNotificationHandler(
      DN configEntryDN
      )
  {
    AccountStatusNotificationHandler handler =
        notificationHandlers.remove (configEntryDN);
    if (handler != null)
    {
      DirectoryServer.deregisterAccountStatusNotificationHandler (
          configEntryDN
          );
      handler.finalizeStatusNotificationHandler();
    }
  }
}

