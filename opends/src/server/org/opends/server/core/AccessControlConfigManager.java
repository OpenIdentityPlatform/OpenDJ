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

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
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
import org.opends.server.api.AccessControlProvider;
import org.opends.server.api.AlertGenerator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

/**
 * This class manages the application-wide access-control configuration.
 * <p>
 * When access control is disabled a default "permissive" access control
 * implementation is used, which permits all operations regardless of
 * the identity of the user.
 */
public final class AccessControlConfigManager
       implements AlertGenerator
{
  // Fully qualified class name.
  private static final String CLASS_NAME =
    "org.opends.server.core.AccessControlConfigManager";

  // The single application-wide instance.
  private static AccessControlConfigManager instance = null;

  // The active access control implementation.
  private AtomicReference<AccessControlProvider> accessControlProvider;

  // The current configuration.
  private PrivateACLConfiguration currentConfiguration;

  /**
   * Get the single application-wide access control manager instance.
   *
   * @return The access control manager.
   */
  public static AccessControlConfigManager getInstance() {

    if (instance == null) {
      instance = new AccessControlConfigManager();
    }
    return instance;
  }

  /**
   * Determine if access control is enabled according to the current
   * configuration.
   *
   * @return Returns <code>true</code> if access control is enabled,
   *         <code>false</code> otherwise.
   */
  public boolean isAccessControlEnabled() {
    return currentConfiguration.isEnabled();
  }

  /**
   * Get the active access control handler.
   * <p>
   * When access control is disabled, this method returns a default
   * access control implementation which permits all operations.
   *
   * @return Returns the active access control handler (never
   *         <code>null</code>).
   */
  public AccessControlHandler getAccessControlHandler() {
    return accessControlProvider.get().getInstance();
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
  void initializeAccessControl() throws ConfigException,
      InitializationException {

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

    // Parse the configuration entry.
    PrivateACLConfiguration configuration = PrivateACLConfiguration
        .readConfiguration(accessControlConfiguration);

    // We have a valid usable entry, so register a change listener in
    // order to handle configuration changes.
    accessControlConfiguration.addChangeListener(new ChangeListener());

    // The configuration looks valid, so install it.
    updateConfiguration(configuration);
  }

  /**
   * Creates a new instance of this access control configuration
   * manager.
   */
  private AccessControlConfigManager() {

    this.accessControlProvider = new AtomicReference<AccessControlProvider>(
        new DefaultAccessControlProvider());
    this.currentConfiguration = null;
  }

  /**
   * Updates the access control configuration based on the contents of a
   * valid configuration entry.
   *
   * @param newConfiguration
   *          The new configuration object.
   * @throws ConfigException
   *           If the access control configuration is invalid.
   * @throws InitializationException
   *           If the access control handler provider could not be
   *           instantiated.
   */
  private void updateConfiguration(PrivateACLConfiguration newConfiguration)
      throws ConfigException, InitializationException {

    DN configEntryDN = newConfiguration.getConfiguration().dn();
    Class<? extends AccessControlProvider> newHandlerClass = null;

    if (currentConfiguration == null) {
      // Initialization phase.
      if (newConfiguration.isEnabled()) {
        newHandlerClass = newConfiguration.getProviderClass();
      } else {
        newHandlerClass = DefaultAccessControlProvider.class;
      }
    } else {
      boolean enabledOld = currentConfiguration.isEnabled();
      boolean enabledNew = newConfiguration.isEnabled();

      if (enabledOld == false && enabledNew == true) {
        // Access control has been enabled - load new class.
        newHandlerClass = newConfiguration.getProviderClass();
      } else if (enabledOld == true && enabledNew == false) {
        // Access control has been disabled - load null handler.
        newHandlerClass = DefaultAccessControlProvider.class;
      } else if (enabledNew == true) {
        // Access control is enabled - load new class if it has changed.
        if (currentConfiguration.getProviderClass().equals(
            newConfiguration.getProviderClass()) == false) {
          newHandlerClass = newConfiguration.getProviderClass();
        }
      }
    }

    // If the access control handler provider class has changed,
    // finalize the old
    // one and instantiate the new.
    if (newHandlerClass != null) {
      AccessControlProvider<? extends AccessControlHandlerCfg> newHandler ;
      try {
        if (newConfiguration.isEnabled())
        {
          newHandler = loadProvider(newHandlerClass.getName(), newConfiguration
            .getConfiguration());
        }
        else
        {
          newHandler = new DefaultAccessControlProvider();
          newHandler.initializeAccessControlHandler(null);
        }
      } catch (Exception e) {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_INSTANTIATE_HANDLER;
        String message = getMessage(msgID, newHandlerClass.getName(),
            String.valueOf(configEntryDN.toString()),
            stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      // Switch the handlers without interfering with other threads.
      AccessControlProvider oldHandler = accessControlProvider
          .getAndSet(newHandler);

      if (oldHandler != null) {
        oldHandler.finalizeAccessControlHandler();
      }

      // If access control has been disabled put a warning in the log.
      if (newHandlerClass.equals(DefaultAccessControlProvider.class)) {
        int msgID = MSGID_CONFIG_AUTHZ_DISABLED;
        String message = getMessage(msgID);
        logError(ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        if (currentConfiguration != null)
        {
          DirectoryServer.sendAlertNotification(this,
              ALERT_TYPE_ACCESS_CONTROL_DISABLED, msgID, message);
        }
      } else {
        int msgID = MSGID_CONFIG_AUTHZ_ENABLED;
        String message = getMessage(msgID, newHandlerClass.getName());
        logError(ErrorLogCategory.CONFIGURATION,
            ErrorLogSeverity.NOTICE, message, msgID);
        if (currentConfiguration != null)
        {
          DirectoryServer.sendAlertNotification(this,
              ALERT_TYPE_ACCESS_CONTROL_ENABLED, msgID, message);
        }
      }
    }

    // Switch in the local configuration.
    //
    // TODO: possible race condition here - should be an atomic
    // reference and sync'ed with the handler reference. We can assume
    // that config changes won't happen that much though.
    currentConfiguration = newConfiguration;
  }

  /**
   * Internal class implementing the change listener interface.
   */
  private class ChangeListener implements
      ConfigurationChangeListener<AccessControlHandlerCfg>
  {

    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(
        AccessControlHandlerCfg configuration,
        List<String> unacceptableReasons)
    {
      try {
        // Parse the configuration entry.
        PrivateACLConfiguration.readConfiguration(configuration);
      } catch (ConfigException e) {
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

      try {
        // Parse the configuration entry.
        PrivateACLConfiguration newConfiguration = PrivateACLConfiguration
            .readConfiguration(configuration);

        // The configuration looks valid, so install it.
        updateConfiguration(newConfiguration);
      } catch (ConfigException e) {
        messages.add(e.getMessage());
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      } catch (InitializationException e) {
        messages.add(e.getMessage());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      return new ConfigChangeResult(resultCode, false, messages);
    }
  }

  /**
   * Internal class used to represent the parsed configuration entry.
   */
  private static class PrivateACLConfiguration {

    // Flag indicating whether or not access control is enabled.
    private boolean enabled;

    // The current access control provider class specified in
    // the configuration.
    private Class<? extends AccessControlProvider> providerClass;

    // The entry that this object is mapped to.
    private AccessControlHandlerCfg configuration;

    /**
     * Parses a configuration entry and, if it is valid, returns an
     * object representation of it.
     *
     * @param configuration
     *          The access control configuration entry.
     * @return An object representation of the parsed configuration.
     * @throws ConfigException
     *           If a the access control configuration is invalid.
     */
    public static PrivateACLConfiguration readConfiguration(
        AccessControlHandlerCfg configuration) throws ConfigException {

      // The access control configuration entry must have the correct
      // object class.
      if (configuration.getAclHandlerClass() == null) {
        int msgID = MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_HAVE_OBJECT_CLASS;
        String message = getMessage(msgID, configuration.toString());
        throw new ConfigException(msgID, message);
      }

      // Parse the attributes.
      boolean enabled = configuration.isEnabled() ;

      Class<? extends AccessControlProvider> providerClass =
        getClassAttribute(configuration);

      return new PrivateACLConfiguration(configuration, enabled, providerClass);
    }

    /**
     * Determine if access control is enabled according to the
     * configuration.
     *
     * @return Returns <code>true</code> if access control is enabled,
     *         <code>false</code> otherwise.
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Get the access control provider class specified in the
     * configuration.
     *
     * @return Returns the {@link AccessControlProvider} class.
     */
    public Class<? extends AccessControlProvider> getProviderClass() {
      return providerClass;
    }

    /**
     * Get the configuration entry associated with this configuration
     * object.
     *
     * @return Returns the configuration entry.
     */
    public AccessControlHandlerCfg getConfiguration() {
      return configuration;
    }

    /**
     * Construct a new configuration object with the specified parsed
     * attribute values.
     *
     * @param configuration
     *          The associated access control configuration entry.
     * @param enabled
     *          The value of the enabled attribute.
     * @param providerClass
     *          The access control provider class.
     */
    private PrivateACLConfiguration(
        AccessControlHandlerCfg configuration, boolean enabled,
        Class<? extends AccessControlProvider> providerClass) {

      this.configuration = configuration;
      this.enabled = enabled;
      this.providerClass = providerClass;
    }


    /**
     * Read the value of the attribute which indicates which access
     * control implementation class to use. This method checks the
     * validity of the class name.
     *
     * @param configuration
     *          The access control configuration.
     * @return The access control provider class.
     * @throws ConfigException
     *           If the class attribute could not be read or if it
     *           contains an invalid class name.
     */
    private static Class<? extends AccessControlProvider> getClassAttribute(
        AccessControlHandlerCfg configuration) throws ConfigException {

      // If access control is enabled then make sure that the class
      // attribute is present.
      try {
        // Load the access control implementation class.
        String className = configuration.getAclHandlerClass();
        try {
          return DirectoryServer.loadClass(className).asSubclass(
              AccessControlProvider.class);
        } catch (ClassNotFoundException e) {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_LOAD_CLASS;
          String message = getMessage(msgID, className, String
              .valueOf(configuration.dn().toString()),
              getExceptionMessage(e));
          throw new ConfigException(msgID, message, e);
        } catch (ClassCastException e) {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_AUTHZ_BAD_CLASS;
          String message = getMessage(msgID, className, String
              .valueOf(configuration.dn().toString()),
              AccessControlProvider.class.getName(),
              getExceptionMessage(e));
          throw new ConfigException(msgID, message, e);
        }
      } catch (ConfigException e) {
        int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_CLASS;
        String message = getMessage(msgID, configuration.dn()
            .toString(), getExceptionMessage(e));
        throw new ConfigException(msgID, message, e);
      }
    }
  }



  /**
   * Retrieves the DN of the configuration entry with which this alert
   * generator is associated.
   *
   * @return  The DN of the configuration entry with which this alert
   *          generator is associated.
   */
  public DN getComponentEntryDN()
  {
    return currentConfiguration.getConfiguration().dn();
  }



  /**
   * Retrieves the fully-qualified name of the Java class for this
   * alert generator implementation.
   *
   * @return  The fully-qualified name of the Java class for this
   *          alert generator implementation.
   */
  public String getClassName()
  {
    return CLASS_NAME;
  }



  /**
   * Retrieves information about the set of alerts that this generator
   * may produce.  The map returned should be between the notification
   * type for a particular notification and the human-readable
   * description for that notification.  This alert generator must not
   * generate any alerts with types that are not contained in this
   * list.
   *
   * @return  Information about the set of alerts that this generator
   *          may produce.
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
  private AccessControlProvider<? extends AccessControlHandlerCfg>
               loadProvider(String className,
                             AccessControlHandlerCfg configuration)
          throws InitializationException
  {
    try
    {
      AccessControlHandlerCfgDefn definition =
        AccessControlHandlerCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getAclHandlerClassPropertyDefinition();
      Class<? extends AccessControlProvider> providerClass =
           propertyDefinition.loadClass(className, AccessControlProvider.class);
      AccessControlProvider<? extends AccessControlHandlerCfg> provider =
           (AccessControlProvider<? extends AccessControlHandlerCfg>)
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

