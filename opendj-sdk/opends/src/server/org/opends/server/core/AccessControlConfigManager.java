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



import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.AccessControlHandlerCfgDefn;
import org.opends.server.admin.std.server.AccessControlHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AlertGenerator;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



/**
 * This class manages the application-wide access-control configuration.
 * <p>
 * When access control is disabled a default "permissive" access control
 * implementation is used, which permits all operations regardless of the
 * identity of the user.
 */
public final class AccessControlConfigManager
       implements AlertGenerator ,
                  ConfigurationChangeListener<AccessControlHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Fully qualified class name.
  private static final String CLASS_NAME =
    "org.opends.server.core.AccessControlConfigManager";

  // The single application-wide instance.
  private static AccessControlConfigManager instance = null;

  // The active access control implementation.
  private AtomicReference<AccessControlHandler> accessControlHandler;

  // The current configuration.
  private AccessControlHandlerCfg currentConfiguration;



  /**
   * Creates a new instance of this access control configuration
   * manager.
   */
  private AccessControlConfigManager()
  {
    this.accessControlHandler = new AtomicReference<AccessControlHandler>(
        new DefaultAccessControlHandler());
    this.currentConfiguration = null;
  }



  /**
   * Get the single application-wide access control manager instance.
   *
   * @return The access control manager.
   */
  public static AccessControlConfigManager getInstance()
  {
    if (instance == null)
    {
      instance = new AccessControlConfigManager();
    }

    return instance;
  }



  /**
   * Determine if access control is enabled according to the current
   * configuration.
   *
   * @return  {@code true} if access control is enabled, {@code false}
   *          otherwise.
   */
  public boolean isAccessControlEnabled()
  {
    return currentConfiguration.isEnabled();
  }



  /**
   * Get the active access control handler.
   * <p>
   * When access control is disabled, this method returns a default access
   * control implementation which permits all operations.
   *
   * @return   The active access control handler (never {@code null}).
   */
  public AccessControlHandler getAccessControlHandler()
  {
    return accessControlHandler.get();
  }



  /**
   * Initializes the access control sub-system. This should only be
   * called at Directory Server startup. If an error occurs then an
   * exception will be thrown and the Directory Server will fail to
   * start (this prevents accidental exposure of user data due to
   * misconfiguration).
   *
   * @throws ConfigException
   *           If an access control configuration error is detected.
   * @throws InitializationException
   *           If a problem occurs while initializing the access control
   *           handler that is not related to the Directory Server
   *           configuration.
   */
  public void initializeAccessControl()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();

    // Don't register as an add and delete listener with the root configuration
    // as we can have only one object at a given time.

    // //Initialize the current Access control.
    AccessControlHandlerCfg accessControlConfiguration =
           rootConfiguration.getAccessControlHandler();

    // We have a valid usable entry, so register a change listener in
    // order to handle configuration changes.
    accessControlConfiguration.addChangeListener(this);

    // The configuration looks valid, so install it.
    updateConfiguration(accessControlConfiguration);
  }



  /**
   * Updates the access control configuration based on the contents of a
   * valid configuration entry.
   *
   * @param  newConfiguration  The new configuration object.
   *
   * @throws  ConfigException If the access control configuration is invalid.
   *
   * @throws  InitializationException  If the access control handler provider
   *                                   could not be instantiated.
   */
  private void updateConfiguration(AccessControlHandlerCfg newConfiguration)
          throws ConfigException, InitializationException
  {
    DN configEntryDN = newConfiguration.dn();
    String newHandlerClass = null;

    if (currentConfiguration == null)
    {
      // Initialization phase.
      if (newConfiguration.isEnabled())
      {
        newHandlerClass = newConfiguration.getAclHandlerClass();
      }
      else
      {
        newHandlerClass = DefaultAccessControlHandler.class.getName();
      }
    }
    else
    {
      boolean enabledOld = currentConfiguration.isEnabled();
      boolean enabledNew = newConfiguration.isEnabled();

      if ((! enabledOld) && enabledNew)
      {
        // Access control has been enabled - get the new class name.
        newHandlerClass = newConfiguration.getAclHandlerClass();
      }
      else if (enabledOld && (! enabledNew))
      {
        // Access control has been disabled - get the default handler class
        // name.
        newHandlerClass = DefaultAccessControlHandler.class.getName();
      }
      else if (enabledNew)
      {
        // Access control is already enabled, but still get the handler class
        // name to see if it has changed.
        newHandlerClass = newConfiguration.getAclHandlerClass();
      }
    }

    // If the access control handler provider class has changed, finalize the
    // old one and instantiate the new one.
    if (newHandlerClass != null)
    {
      AccessControlHandler<? extends AccessControlHandlerCfg> newHandler;
      try
      {
        if (newConfiguration.isEnabled())
        {
          newHandler = loadHandler(newHandlerClass, newConfiguration);
        }
        else
        {
          newHandler = new DefaultAccessControlHandler();
          newHandler.initializeAccessControlHandler(null);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_INSTANTIATE_HANDLER;
        String message = getMessage(msgID, newHandlerClass,
                                    String.valueOf(configEntryDN.toString()),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      // Switch the handlers without interfering with other threads.
      AccessControlHandler oldHandler =
           accessControlHandler.getAndSet(newHandler);

      if (oldHandler != null)
      {
        oldHandler.finalizeAccessControlHandler();
      }

      // If access control has been disabled put a warning in the log.
      if (newHandlerClass.equals(DefaultAccessControlHandler.class))
      {
        int msgID = MSGID_CONFIG_AUTHZ_DISABLED;
        String message = getMessage(msgID);
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        if (currentConfiguration != null)
        {
          DirectoryServer.sendAlertNotification(this,
               ALERT_TYPE_ACCESS_CONTROL_DISABLED, msgID, message);
        }
      }
      else
      {
        int msgID = MSGID_CONFIG_AUTHZ_ENABLED;
        String message = getMessage(msgID, newHandlerClass);
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.NOTICE,
                 message, msgID);
        if (currentConfiguration != null)
        {
          DirectoryServer.sendAlertNotification(this,
               ALERT_TYPE_ACCESS_CONTROL_ENABLED, msgID, message);
        }
      }
    }

    // Switch in the local configuration.
    currentConfiguration = newConfiguration;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      AccessControlHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    try
    {
      // If the access control handler is disabled, we don't care about the
      // configuration.  If it is enabled, then all we care about is whether we
      // can load the access control handler class.
      if (configuration.isEnabled())
      {
        loadHandler(configuration.getAclHandlerClass(), null);
      }
    }
    catch (InitializationException e)
    {
      unacceptableReasons.add(e.getMessage());
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 AccessControlHandlerCfg configuration)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    ArrayList<String> messages = new ArrayList<String>();

    try
    {
      // Attempt to install the new configuration.
      updateConfiguration(configuration);
    }
    catch (ConfigException e)
    {
      messages.add(e.getMessage());
      resultCode = ResultCode.CONSTRAINT_VIOLATION;
    }
    catch (InitializationException e)
    {
      messages.add(e.getMessage());
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    return new ConfigChangeResult(resultCode, false, messages);
  }



  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN()
  {
    return currentConfiguration.dn();
  }



  /**
   * {@inheritDoc}
   */
  public String getClassName()
  {
    return CLASS_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_ACCESS_CONTROL_DISABLED,
               ALERT_DESCRIPTION_ACCESS_CONTROL_DISABLED);
    alerts.put(ALERT_TYPE_ACCESS_CONTROL_ENABLED,
               ALERT_DESCRIPTION_ACCESS_CONTROL_ENABLED);

    return alerts;
  }



  /**
   * Loads the specified class, instantiates it as a AccessControlProvider, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the Access Control
   *                        provider class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        Access Control Provider, or {@code null} if the
   *                        Access Control Provider should not be initialized.
   *
   * @return  The possibly initialized Access Control Provider.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the Access Control Provider.
   */
  private AccessControlHandler<? extends AccessControlHandlerCfg>
               loadHandler(String className,
                           AccessControlHandlerCfg configuration)
          throws InitializationException
  {
    try
    {
      AccessControlHandlerCfgDefn definition =
           AccessControlHandlerCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getAclHandlerClassPropertyDefinition();
      Class<? extends AccessControlHandler> providerClass =
           propertyDefinition.loadClass(className, AccessControlHandler.class);
      AccessControlHandler<? extends AccessControlHandlerCfg> provider =
          (AccessControlHandler<? extends AccessControlHandlerCfg>)
           providerClass.newInstance();

      if (configuration != null)
      {
        Method method =
          provider.getClass().getMethod("initializeAccessControlHandler",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(provider, configuration);
      }

      return provider;
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_INSTANTIATE_HANDLER;
      String message = getMessage(msgID, className,
                                  String.valueOf(configuration.dn()),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }
}

