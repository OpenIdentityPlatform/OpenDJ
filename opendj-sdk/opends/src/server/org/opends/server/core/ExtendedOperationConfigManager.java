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



import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.ExtendedOperationHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.meta.ExtendedOperationHandlerCfgDefn;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;



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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // A mapping between the DNs of the config entries and the associated extended
  // operation handlers.
  private ConcurrentHashMap<DN,ExtendedOperationHandler> handlers;



  /**
   * Creates a new instance of this extended operation config manager.
   */
  public ExtendedOperationConfigManager()
  {
    handlers = new ConcurrentHashMap<DN,ExtendedOperationHandler>();
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
    // Create an internal server management context and retrieve
    // the root configuration which has the extended operation handler relation.
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();

    // Register add and delete listeners.
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

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
       ExtendedOperationHandlerCfg configuration)
  {
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean    adminActionRequired = false;


    // See if the entry is registered as an extended operation handler.  If so,
    // deregister it and finalize the handler.
    ExtendedOperationHandler handler = handlers.remove(configuration.dn());
    if (handler != null)
    {
      handler.finalizeExtendedOperationHandler();
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       ExtendedOperationHandlerCfg configuration,
       List<String> unacceptableReasons)
  {
    if (configuration.isEnabled()) {
      // It's enabled so always validate the class.
      return isJavaClassAcceptable(configuration, unacceptableReasons);
    } else {
      // It's disabled so ignore it.
      return true;
    }
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
       ExtendedOperationHandlerCfg configuration)
  {
    // Attempt to get the existing handler. This will only
    // succeed if it was enabled.
    DN dn = configuration.dn();
    ExtendedOperationHandler handler = handlers.get(dn);

    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          messages.add(e.getMessage());
          resultCode = DirectoryServer.getServerErrorResultCode();
        } catch (Exception e) {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_EXTOP_INITIALIZATION_FAILED;
          messages.add(getMessage(msgID, String.valueOf(configuration
              .getJavaImplementationClass()), String.valueOf(dn),
              stackTraceToSingleLineString(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    } else {
      if (configuration.isEnabled()) {
        // The handler is currently active, so we don't
        // need to do anything. Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = configuration.getJavaImplementationClass();
        if (!className.equals(handler.getClass().getName())) {
          adminActionRequired = true;
        }
      } else {
        // We need to disable the connection handler.

        handlers.remove(dn);

        handler.finalizeExtendedOperationHandler();
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
       ExtendedOperationHandlerCfg configuration,
       List<String> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
       ExtendedOperationHandlerCfg configuration)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();

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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(e.getMessage());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_EXTOP_INITIALIZATION_FAILED;
        messages.add(getMessage(msgID, String.valueOf(configuration
            .getJavaImplementationClass()), String.valueOf(dn),
            stackTraceToSingleLineString(e)));
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
  public boolean isConfigurationDeleteAcceptable(
       ExtendedOperationHandlerCfg configuration,
       List<String> unacceptableReasons)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }

  // Load and initialize the handler named in the config.
  private ExtendedOperationHandler getHandler(
      ExtendedOperationHandlerCfg config) throws ConfigException
  {
    String className = config.getJavaImplementationClass();
    ExtendedOperationHandlerCfgDefn d =
      ExtendedOperationHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d
        .getJavaImplementationClassPropertyDefinition();

    // Load the class and cast it to an extended operation handler.
    Class<? extends ExtendedOperationHandler> theClass;
    ExtendedOperationHandler extendedOperationHandler;

    try
    {
      theClass = pd.loadClass(className, ExtendedOperationHandler.class);
      extendedOperationHandler = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod(
           "initializeExtendedOperationHandler",
           config.definition().getServerConfigurationClass());

      method.invoke(extendedOperationHandler, config);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_EXTOP_INVALID_CLASS;
      String message = getMessage(msgID, String.valueOf(className),
                                  String.valueOf(config.dn()),
                                  String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    // The handler has been successfully initialized.
    return extendedOperationHandler;
  }



  // Determines whether or not the new configuration's implementation
  // class is acceptable.
  private boolean isJavaClassAcceptable(ExtendedOperationHandlerCfg config,
                                        List<String> unacceptableReasons)
  {
    String className = config.getJavaImplementationClass();
    ExtendedOperationHandlerCfgDefn d =
      ExtendedOperationHandlerCfgDefn.getInstance();
    ClassPropertyDefinition pd = d
        .getJavaImplementationClassPropertyDefinition();

    // Load the class and cast it to an extended operation handler.
    Class<? extends ExtendedOperationHandler> theClass;
    try {
      theClass = pd.loadClass(className, ExtendedOperationHandler.class);
      ExtendedOperationHandler extOpHandler = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("isConfigurationAcceptable",
                                         ExtendedOperationHandlerCfg.class,
                                         List.class);
      Boolean acceptable = (Boolean) method.invoke(extOpHandler, config,
                                                   unacceptableReasons);

      if (! acceptable)
      {
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_EXTOP_INVALID_CLASS;
      unacceptableReasons.add(getMessage(msgID, className,
                              String.valueOf(config.dn()),
                              String.valueOf(e)));
      return false;
    }

    // The class is valid as far as we can tell.
    return true;
  }
}

