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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AccessControlProvider;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
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
  // Fully qualified class name for debugging purposes.
  private static final String CLASS_NAME =
    "org.opends.server.core.AccessControlConfigManager";

  // The single application-wide instance.
  private static AccessControlConfigManager instance = null;

  // The active access control implementation.
  private AtomicReference<AccessControlProvider> accessControlProvider;

  // The current configuration.
  private Configuration currentConfiguration;

  /**
   * Get the single application-wide access control manager instance.
   *
   * @return The access control manager.
   */
  public static AccessControlConfigManager getInstance() {
    assert debugEnter(CLASS_NAME, "getInstance");

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
    assert debugEnter(CLASS_NAME, "isEnabled");
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
    assert debugEnter(CLASS_NAME, "getAccessControlHandler");
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
    assert debugEnter(CLASS_NAME, "initializeAccessControl");

    // Get the access control handler configuration entry.
    ConfigEntry configEntry;
    try {
      DN configEntryDN = DN.decode(DN_AUTHZ_HANDLER_CONFIG);
      configEntry = DirectoryServer.getConfigEntry(configEntryDN);
    } catch (Exception e) {
      assert debugException(CLASS_NAME,
          "initializeAccessControlConfigManager", e);

      int msgID = MSGID_CONFIG_AUTHZ_CANNOT_GET_ENTRY;
      String message = getMessage(msgID,
          stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }

    // The access control handler entry must exist.
    if (configEntry == null) {
      int msgID = MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }

    // Parse the configuration entry.
    Configuration configuration = Configuration
        .readConfiguration(configEntry);

    // We have a valid usable entry, so register a change listener in
    // order to handle configuration changes.
    configEntry.registerChangeListener(new ChangeListener());

    // The configuration looks valid, so install it.
    updateConfiguration(configuration);
  }

  /**
   * Creates a new instance of this access control configuration
   * manager.
   */
  private AccessControlConfigManager() {
    assert debugConstructor(CLASS_NAME);

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
  private void updateConfiguration(Configuration newConfiguration)
      throws ConfigException, InitializationException {
    assert debugEnter(CLASS_NAME, "updateConfiguration");

    DN configEntryDN = newConfiguration.getConfigEntry().getDN();
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
      AccessControlProvider newHandler;
      try {
        newHandler = newHandlerClass.newInstance();
      } catch (Exception e) {
        assert debugException(CLASS_NAME, "updateConfiguration", e);

        int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_INSTANTIATE_HANDLER;
        String message = getMessage(msgID, newHandlerClass.getName(),
            String.valueOf(configEntryDN.toString()),
            stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      // Switch the handlers without interfering with other threads.
      newHandler.initializeAccessControlHandler(newConfiguration
          .getConfigEntry());

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
  private class ChangeListener implements ConfigChangeListener {
    // Fully qualified class name for debugging purposes.
    private static final String CLASS_NAME =
      "org.opends.server.core.AccessControlConfigManager.ChangeListener";

    /**
     * {@inheritDoc}
     */
    public boolean configChangeIsAcceptable(ConfigEntry configEntry,
        StringBuilder unacceptableReason) {
      assert debugEnter(CLASS_NAME, "configChangeIsAcceptable");

      try {
        // Parse the configuration entry.
        Configuration.readConfiguration(configEntry);
      } catch (ConfigException e) {
        unacceptableReason.append(e.getMessage());
        return false;
      }

      return true;
    }

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        ConfigEntry configEntry) {
      assert debugEnter(CLASS_NAME, "applyConfigurationChange", String
          .valueOf(configEntry));

      ResultCode resultCode = ResultCode.SUCCESS;
      ArrayList<String> messages = new ArrayList<String>();

      try {
        // Parse the configuration entry.
        Configuration newConfiguration = Configuration
            .readConfiguration(configEntry);

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
  private static class Configuration {
    // Fully qualified class name for debugging purposes.
    private static final String CLASS_NAME =
      "org.opends.server.core.AccessControlConfigManager.Configuration";

    // Flag indicating whether or not access control is enabled.
    private boolean enabled;

    // The current access control provider class specified in
    // the configuration.
    private Class<? extends AccessControlProvider> providerClass;

    // The entry that this object is mapped to.
    private ConfigEntry configEntry;

    /**
     * Parses a configuration entry and, if it is valid, returns an
     * object representation of it.
     *
     * @param configEntry
     *          The access control configuration entry.
     * @return An object representation of the parsed configuration.
     * @throws ConfigException
     *           If a the access control configuration is invalid.
     */
    public static Configuration readConfiguration(
        ConfigEntry configEntry) throws ConfigException {
      assert debugEnter(CLASS_NAME, "createConfiguration");

      // The access control configuration entry must have the correct
      // object class.
      if (configEntry.hasObjectClass(OC_AUTHZ_HANDLER_CONFIG) == false) {
        int msgID = MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_HAVE_OBJECT_CLASS;
        String message = getMessage(msgID, configEntry.toString());
        throw new ConfigException(msgID, message);
      }

      // Parse the attributes.
      boolean enabled = getEnabledAttribute(configEntry);
      Class<? extends AccessControlProvider> providerClass =
        getClassAttribute(configEntry);

      return new Configuration(configEntry, enabled, providerClass);
    }

    /**
     * Determine if access control is enabled according to the
     * configuration.
     *
     * @return Returns <code>true</code> if access control is enabled,
     *         <code>false</code> otherwise.
     */
    public boolean isEnabled() {
      assert debugEnter(CLASS_NAME, "isEnabled");
      return enabled;
    }

    /**
     * Get the access control provider class specified in the
     * configuration.
     *
     * @return Returns the {@link AccessControlProvider} class.
     */
    public Class<? extends AccessControlProvider> getProviderClass() {
      assert debugEnter(CLASS_NAME, "getProviderClass");
      return providerClass;
    }

    /**
     * Get the configuration entry associated with this configuration
     * object.
     *
     * @return Returns the configuration entry.
     */
    public ConfigEntry getConfigEntry() {
      assert debugEnter(CLASS_NAME, "getConfigEntry");
      return configEntry;
    }

    /**
     * Construct a new configuration object with the specified parsed
     * attribute values.
     *
     * @param configEntry
     *          The associated access control configuration entry.
     * @param enabled
     *          The value of the enabled attribute.
     * @param providerClass
     *          The access control provider class.
     */
    private Configuration(ConfigEntry configEntry, boolean enabled,
        Class<? extends AccessControlProvider> providerClass) {
      assert debugConstructor(CLASS_NAME);

      this.configEntry = configEntry;
      this.enabled = enabled;
      this.providerClass = providerClass;
    }

    /**
     * Read the value of the attribute which indicates whether or not
     * access control is enabled.
     *
     * @param configEntry
     *          The access control configuration entry.
     * @return The boolean value of the enabled attribute.
     * @throws ConfigException
     *           If the enabled attribute could not be read or if it
     *           contains an invalid value.
     */
    private static boolean getEnabledAttribute(ConfigEntry configEntry)
        throws ConfigException {
      assert debugEnter(CLASS_NAME, "getEnabledAttribute");

      // See if the entry contains an attribute that indicates whether
      // or not access control should be enabled.
      try {
        BooleanConfigAttribute enabledAttrStub = new BooleanConfigAttribute(
            ATTR_AUTHZ_HANDLER_ENABLED,
            getMessage(MSGID_CONFIG_AUTHZ_DESCRIPTION_ENABLED), false);

        BooleanConfigAttribute enabledAttr = (BooleanConfigAttribute)
          configEntry.getConfigAttribute(enabledAttrStub);

        if (enabledAttr == null) {
          int msgID = MSGID_CONFIG_AUTHZ_NO_ENABLED_ATTR;
          String message = getMessage(msgID, configEntry.getDN()
              .toString());
          throw new ConfigException(msgID, message);
        } else {
          // We have a valid attribute - return it.
          return enabledAttr.activeValue();
        }
      } catch (ConfigException e) {
        int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, configEntry.getDN()
            .toString(), stackTraceToSingleLineString(e));
        throw new ConfigException(msgID, message, e);
      }
    }

    /**
     * Read the value of the attribute which indicates which access
     * control implementation class to use. This method checks the
     * validity of the class name.
     *
     * @param configEntry
     *          The access control configuration entry.
     * @return The access control provider class.
     * @throws ConfigException
     *           If the class attribute could not be read or if it
     *           contains an invalid class name.
     */
    private static Class<? extends AccessControlProvider> getClassAttribute(
        ConfigEntry configEntry) throws ConfigException {
      assert debugEnter(CLASS_NAME, "getClassAttribute");

      // If access control is enabled then make sure that the class
      // attribute is present.
      try {
        StringConfigAttribute classAttrStub = new StringConfigAttribute(
            ATTR_AUTHZ_HANDLER_CLASS,
            getMessage(MSGID_CONFIG_AUTHZ_DESCRIPTION_CLASS), true,
            false, false);

        StringConfigAttribute classAttr = (StringConfigAttribute) configEntry
            .getConfigAttribute(classAttrStub);

        if (classAttr == null) {
          int msgID = MSGID_CONFIG_AUTHZ_NO_CLASS_ATTR;
          String message = getMessage(msgID, configEntry.getDN()
              .toString());
          throw new ConfigException(msgID, message);
        }

        // Load the access control implementation class.
        String className = classAttr.activeValue();
        try {
          return Class.forName(className).asSubclass(
              AccessControlProvider.class);
        } catch (ClassNotFoundException e) {
          assert debugException(CLASS_NAME, "updateConfiguration", e);

          int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_LOAD_CLASS;
          String message = getMessage(msgID, className, String
              .valueOf(configEntry.getDN().toString()),
              stackTraceToSingleLineString(e));
          throw new ConfigException(msgID, message, e);
        } catch (ClassCastException e) {
          assert debugException(CLASS_NAME, "updateConfiguration", e);

          int msgID = MSGID_CONFIG_AUTHZ_BAD_CLASS;
          String message = getMessage(msgID, className, String
              .valueOf(configEntry.getDN().toString()),
              AccessControlProvider.class.getName(),
              stackTraceToSingleLineString(e));
          throw new ConfigException(msgID, message, e);
        }
      } catch (ConfigException e) {
        int msgID = MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_CLASS;
        String message = getMessage(msgID, configEntry.getDN()
            .toString(), stackTraceToSingleLineString(e));
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
    assert debugEnter(CLASS_NAME, "getComponentEntryDN");

    return currentConfiguration.getConfigEntry().getDN();
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
    assert debugEnter(CLASS_NAME, "getClassName");

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
    assert debugEnter(CLASS_NAME, "getAlerts");

    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_ACCESS_CONTROL_DISABLED,
               ALERT_DESCRIPTION_ACCESS_CONTROL_DISABLED);
    alerts.put(ALERT_TYPE_ACCESS_CONTROL_ENABLED,
               ALERT_DESCRIPTION_ACCESS_CONTROL_ENABLED);

    return alerts;
  }
}

