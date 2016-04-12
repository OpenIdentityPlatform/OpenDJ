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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.AccountStatusNotificationHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.AccountStatusNotificationHandlerCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.types.InitializationException;

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
  /** A mapping between the DNs of the config entries and the associated notification handlers. */
  private final ConcurrentHashMap<DN, AccountStatusNotificationHandler<?>> notificationHandlers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this account status notification handler config
   * manager.
   * @param serverContext
   *            The server context.
   */
  public AccountStatusNotificationHandlerConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    notificationHandlers = new ConcurrentHashMap<>();
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
    RootCfg rootConfiguration = serverContext.getRootConfig();
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
        String className = config.getJavaClass();
        loadAndInstallNotificationHandler (className, config);
      }
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      AccountStatusNotificationHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // entry cache.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadNotificationHandler(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        status = false;
      }
    }

    return status;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      AccountStatusNotificationHandlerCfg configuration
      )
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // Get the configuration entry DN and the associated handler class.
    DN configEntryDN = configuration.dn();
    AccountStatusNotificationHandler<?> handler = notificationHandlers.get(configEntryDN);

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
    String newClassName = configuration.getJavaClass();
    if (handler != null)
    {
      String curClassName = handler.getClass().getName();
      boolean classIsNew = !newClassName.equals(curClassName);
      if (classIsNew)
      {
        changeResult.setAdminActionRequired (true);
      }
      return changeResult;
    }

    // New entry cache is enabled and there were no previous one.
    // Instantiate the new class and initialize it.
    try
    {
      loadAndInstallNotificationHandler (newClassName, configuration);
    }
    catch (InitializationException ie)
    {
      changeResult.addMessage (ie.getMessageObject());
      changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
      return changeResult;
    }

    return changeResult;
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      AccountStatusNotificationHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // returned status -- all is fine by default
    boolean status = true;

    // Make sure that no entry already exists with the specified DN.
    DN configEntryDN = configuration.dn();
    if (notificationHandlers.containsKey(configEntryDN))
    {
      unacceptableReasons.add(ERR_CONFIG_ACCTNOTHANDLER_EXISTS.get(configEntryDN));
      status = false;
    }
    // If configuration is enabled then check that notification class
    // can be instantiated.
    else if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // an entry cache.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadNotificationHandler (className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        status = false;
      }
    }

    return status;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
      AccountStatusNotificationHandlerCfg configuration
      )
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();

    // Register a change listener with it so we can be notified of changes
    // to it over time.
    configuration.addChangeListener(this);

    if (configuration.isEnabled())
    {
      // Instantiate the class as an entry cache and initialize it.
      String className = configuration.getJavaClass();
      try
      {
        loadAndInstallNotificationHandler (className, configuration);
      }
      catch (InitializationException ie)
      {
        changeResult.addMessage (ie.getMessageObject());
        changeResult.setResultCode (DirectoryServer.getServerErrorResultCode());
        return changeResult;
      }
    }

    return changeResult;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
      AccountStatusNotificationHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
      AccountStatusNotificationHandlerCfg configuration
      )
  {
    uninstallNotificationHandler (configuration.dn());
    return new ConfigChangeResult();
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
  private <T extends AccountStatusNotificationHandlerCfg>
      AccountStatusNotificationHandler<T> loadNotificationHandler(
      String className, T configuration, boolean initialize)
      throws InitializationException
  {
    try
    {
      final AccountStatusNotificationHandlerCfgDefn definition =
          AccountStatusNotificationHandlerCfgDefn.getInstance();
      final ClassPropertyDefinition propertyDefinition =
          definition.getJavaClassPropertyDefinition();
      final Class<? extends AccountStatusNotificationHandler> handlerClass =
          propertyDefinition.loadClass(className,
              AccountStatusNotificationHandler.class);
      final AccountStatusNotificationHandler<T> notificationHandler =
          handlerClass.newInstance();

      if (initialize)
      {
        notificationHandler.initializeStatusNotificationHandler(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!notificationHandler.isConfigurationAcceptable(configuration,
            unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_ACCTNOTHANDLER_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return notificationHandler;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_ACCTNOTHANDLER_INITIALIZATION_FAILED.get(
              className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }

  /**
   * Remove a notification handler that has been installed in the server.
   *
   * @param configEntryDN  the DN of the configuration entry associated to
   *                       the notification handler to remove
   */
  private void uninstallNotificationHandler(DN configEntryDN)
  {
    AccountStatusNotificationHandler<?> handler = notificationHandlers.remove(configEntryDN);
    if (handler != null)
    {
      DirectoryServer.deregisterAccountStatusNotificationHandler(configEntryDN);
      handler.finalizeStatusNotificationHandler();
    }
  }
}
