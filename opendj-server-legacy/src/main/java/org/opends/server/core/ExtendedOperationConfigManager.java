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
import org.forgerock.opendj.server.config.meta.ExtendedOperationHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.ExtendedOperationHandlerCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the set of extended
 * operation handlers defined in the Directory Server.  It will initialize the
 * handlers when the server starts, and then will manage any additions,
 * removals, or modifications of any extended operation handlers while the
 * server is running.
 */
public class ExtendedOperationConfigManager implements
     ConfigurationChangeListener<ExtendedOperationHandlerCfg>,
     ConfigurationAddListener<ExtendedOperationHandlerCfg>,
     ConfigurationDeleteListener<ExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * A mapping between the DNs of the config entries and the associated extended
   * operation handlers.
   */
  private final ConcurrentHashMap<DN,ExtendedOperationHandler> handlers;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this extended operation config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public ExtendedOperationConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    handlers = new ConcurrentHashMap<>();
  }

  /**
   * Initializes all extended operation handlers currently defined in the
   * Directory Server configuration.  This should only be called at Directory
   * Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the extended
   *                           operation handler initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the extended operation handler that is
   *                                   not related to the server configuration.
   */
  public void initializeExtendedOperationHandlers()
         throws ConfigException, InitializationException
  {
    RootCfg root = serverContext.getRootConfig();
    root.addExtendedOperationHandlerAddListener(this);
    root.addExtendedOperationHandlerDeleteListener(this);

    // Initialize existing handlers.
    for (String name : root.listExtendedOperationHandlers())
    {
      // Get the handler's configuration.
      // This will decode and validate its properties.
      ExtendedOperationHandlerCfg config =
           root.getExtendedOperationHandler(name);

      // Register as a change listener for this handler so that we can be
      // notified when it is disabled or enabled.
      config.addChangeListener(this);

      // Ignore this handler if it is disabled.
      if (config.isEnabled())
      {
        // Load the handler's implementation class and initialize it.
        ExtendedOperationHandler handler = getHandler(config);

        // Put this handler in the hash map so that we will be able to find
        // it if it is deleted or disabled.
        handlers.put(config.dn(), handler);
      }
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
       ExtendedOperationHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    // See if the entry is registered as an extended operation handler.
    // If so, deregister it and finalize the handler.
    ExtendedOperationHandler handler = handlers.remove(configuration.dn());
    if (handler != null)
    {
      handler.finalizeExtendedOperationHandler();
    }
    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
       ExtendedOperationHandlerCfg configuration,
       List<LocalizableMessage> unacceptableReasons)
  {
    return !configuration.isEnabled()
        || isJavaClassAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
       ExtendedOperationHandlerCfg configuration)
  {
    // Attempt to get the existing handler. This will only
    // succeed if it was enabled.
    DN dn = configuration.dn();
    ExtendedOperationHandler handler = handlers.get(dn);

    final ConfigChangeResult ccr = new ConfigChangeResult();

    // See whether the handler should be enabled.
    if (handler == null) {
      if (configuration.isEnabled()) {
        // The handler needs to be enabled.
        try {
          handler = getHandler(configuration);

          // Put this handler in the hash so that we will
          // be able to find it if it is altered.
          handlers.put(dn, handler);
        } catch (ConfigException e) {
          logger.traceException(e);

          ccr.addMessage(e.getMessageObject());
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        } catch (Exception e) {
          logger.traceException(e);

          ccr.addMessage(ERR_CONFIG_EXTOP_INITIALIZATION_FAILED.get(
              configuration.getJavaClass(), dn, stackTraceToSingleLineString(e)));
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        }
      }
    } else {
      if (configuration.isEnabled()) {
        // The handler is currently active, so we don't
        // need to do anything. Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = configuration.getJavaClass();
        if (!className.equals(handler.getClass().getName())) {
          ccr.setAdminActionRequired(true);
        }
      } else {
        // We need to disable the connection handler.

        handlers.remove(dn);

        handler.finalizeExtendedOperationHandler();
      }
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationAddAcceptable(
       ExtendedOperationHandlerCfg configuration,
       List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
       ExtendedOperationHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Register as a change listener for this connection handler entry
    // so that we will be notified of any changes that may be made to
    // it.
    configuration.addChangeListener(this);

    // Ignore this connection handler if it is disabled.
    if (configuration.isEnabled())
    {
      // The connection handler needs to be enabled.
      DN dn = configuration.dn();
      try {
        ExtendedOperationHandler handler = getHandler(configuration);

        // Put this connection handler in the hash so that we will be
        // able to find it if it is altered.
        handlers.put(dn, handler);
      }
      catch (ConfigException e)
      {
        logger.traceException(e);

        ccr.addMessage(e.getMessageObject());
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
      catch (Exception e)
      {
        logger.traceException(e);

        ccr.addMessage(ERR_CONFIG_EXTOP_INITIALIZATION_FAILED.get(
            configuration.getJavaClass(), dn, stackTraceToSingleLineString(e)));
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
       ExtendedOperationHandlerCfg configuration,
       List<LocalizableMessage> unacceptableReasons)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }

  /** Load and initialize the handler named in the config. */
  private ExtendedOperationHandler getHandler(
      ExtendedOperationHandlerCfg config) throws ConfigException
  {
    String className = config.getJavaClass();
    ExtendedOperationHandlerCfgDefn d =
        ExtendedOperationHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    try
    {
      Class<? extends ExtendedOperationHandler> theClass =
          pd.loadClass(className, ExtendedOperationHandler.class);
      ExtendedOperationHandler extendedOperationHandler = theClass.newInstance();

      extendedOperationHandler.initializeExtendedOperationHandler(config);

      return extendedOperationHandler;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new ConfigException(ERR_CONFIG_EXTOP_INVALID_CLASS.get(className, config.dn(), e), e);
    }
  }

  /** Determines whether the new configuration's implementation class is acceptable. */
  private boolean isJavaClassAcceptable(ExtendedOperationHandlerCfg config,
                                        List<LocalizableMessage> unacceptableReasons)
  {
    String className = config.getJavaClass();
    ExtendedOperationHandlerCfgDefn d =
        ExtendedOperationHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    try {
      Class<? extends ExtendedOperationHandler> theClass =
          pd.loadClass(className, ExtendedOperationHandler.class);
      ExtendedOperationHandler extOpHandler = theClass.newInstance();

      return extOpHandler.isConfigurationAcceptable(config, unacceptableReasons);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      unacceptableReasons.add(ERR_CONFIG_EXTOP_INVALID_CLASS.get(className, config.dn(), e));
      return false;
    }
  }
}
